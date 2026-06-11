<template>
  <main class="workbench-page">
    <div class="resizable-sidebar" :style="{ width: leftWidth + 'px' }">
      <KnowledgeBaseSidebar
        :knowledge-bases="knowledgeBases"
        :documents="documents"
        :sessions="sessions"
        :user="authStore.user"
        :active-knowledge-base-id="activeKnowledgeBaseId"
        :active-session-id="activeSessionId"
        :uploading="uploading"
        :deleting-document-id="deletingDocumentId"
        :is-admin="authStore.isAdmin"
        @select-knowledge-base="selectKnowledgeBase"
        @create-knowledge-base="handleCreateKnowledgeBase"
        @upload-document="handleUploadDocument"
        @delete-document="handleDeleteDocument"
        @select-session="selectSession"
        @create-session="handleCreateSession"
        @logout="logout"
      />
    </div>

    <div
      class="resizer resizer-left"
      @mousedown="startResize($event, 'left')"
      title="拖动调整侧边栏宽度"
    ></div>

    <div class="resizable-chat">
      <ChatPanel
        :knowledge-base-name="activeKnowledgeBase?.name || ''"
        :session-title="activeSession?.title || ''"
        :messages="messages"
        :retrieval-text="retrievalText"
        :process-logs="processLogs"
        :can-send="!sending"
        :can-create-session="!sending"
        :sending="sending"
        @send="sendQuestion"
        @create-session="handleCreateSession"
      />
    </div>

    <div
      class="resizer resizer-right"
      @mousedown="startResize($event, 'right')"
      title="拖动调整证据面板宽度"
    ></div>

    <div class="resizable-evidence" :style="{ width: rightWidth + 'px' }">
      <EvidencePanel :citations="citations" :query="rewrittenQuery" />
    </div>

    <p v-if="error" class="global-error">{{ error }}</p>
  </main>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';

import {
  createSession,
  listMessages,
  listSessions,
  streamChatMessage,
  type ChatMessage,
  type ChatSession,
  type RagCitation,
} from '@/api/chat';
import { apiErrorMessage } from '@/api/http';
import { deleteDocument, listDocuments, uploadDocument, type KnowledgeDocument } from '@/api/document';
import { createKnowledgeBase, listKnowledgeBases, type KnowledgeBase } from '@/api/knowledge';
import ChatPanel from '@/components/chat/ChatPanel.vue';
import EvidencePanel from '@/components/evidence/EvidencePanel.vue';
import KnowledgeBaseSidebar from '@/components/kb/KnowledgeBaseSidebar.vue';
import { useAuthStore } from '@/stores/authStore';

const router = useRouter();
const authStore = useAuthStore();

const knowledgeBases = ref<KnowledgeBase[]>([]);
const documents = ref<KnowledgeDocument[]>([]);
const sessions = ref<ChatSession[]>([]);
const messages = ref<ChatMessage[]>([]);
const citations = ref<RagCitation[]>([]);
const activeKnowledgeBaseId = ref<number | null>(null);
const activeSessionId = ref<number | null>(null);
const rewrittenQuery = ref('');
const retrievalText = ref('');
const error = ref('');
const uploading = ref(false);
const deletingDocumentId = ref<number | null>(null);
const sending = ref(false);
const processLogs = ref<ProcessLogItem[]>([]);
let documentPollingTimer: number | undefined;
const notifiedFailedDocumentIds = new Set<number>();

interface ProcessLogItem {
  id: string;
  time: string;
  stage: 'REQUEST' | 'RESPONSE' | 'UPLOAD' | 'PARSE' | 'EMBEDDING' | 'RETRIEVAL' | 'LLM' | 'DONE' | 'ERROR';
  title: string;
  detail: string;
}

interface SendQuestionPayload {
  content: string;
  webSearchEnabled: boolean;
}

const activeKnowledgeBase = computed(() =>
  knowledgeBases.value.find((item) => item.id === activeKnowledgeBaseId.value),
);

const activeSession = computed(() =>
  sessions.value.find((item) => item.id === activeSessionId.value),
);

// 可调整大小的面板宽度
const leftWidth = ref(304);
const rightWidth = ref(360);
const minPanelWidth = 240;
const maxLeftWidth = 500;
const maxRightWidth = 600;

let isResizing = false;
let resizeSide: 'left' | 'right' = 'left';
let startX = 0;
let startWidth = 0;

function startResize(event: MouseEvent, side: 'left' | 'right') {
  isResizing = true;
  resizeSide = side;
  startX = event.clientX;
  startWidth = side === 'left' ? leftWidth.value : rightWidth.value;

  document.addEventListener('mousemove', handleResize);
  document.addEventListener('mouseup', stopResize);
  document.body.style.cursor = 'col-resize';
  document.body.style.userSelect = 'none';
}

function handleResize(event: MouseEvent) {
  if (!isResizing) return;

  const delta = event.clientX - startX;

  if (resizeSide === 'left') {
    const newWidth = Math.max(minPanelWidth, Math.min(maxLeftWidth, startWidth + delta));
    leftWidth.value = newWidth;
  } else {
    const newWidth = Math.max(minPanelWidth, Math.min(maxRightWidth, startWidth - delta));
    rightWidth.value = newWidth;
  }
}

function stopResize() {
  isResizing = false;
  document.removeEventListener('mousemove', handleResize);
  document.removeEventListener('mouseup', stopResize);
  document.body.style.cursor = '';
  document.body.style.userSelect = '';
}

onMounted(async () => {
  await loadKnowledgeBases();
});

async function loadKnowledgeBases() {
  error.value = '';
  try {
    addProcessLog('DONE', '工作台初始化', '正在读取知识库与最近会话');
    knowledgeBases.value = await listKnowledgeBases();
    if (knowledgeBases.value.length > 0) {
      await selectKnowledgeBase(knowledgeBases.value[0].id);
    } else {
      await loadSessions(null);
    }
  } catch (err) {
    error.value = apiErrorMessage(err);
  }
}

async function selectKnowledgeBase(id: number) {
  // 切换知识库时，要清空当前会话、引用证据和检索状态。
  // 否则用户会看到上一个知识库留下来的消息或证据。
  activeKnowledgeBaseId.value = id;
  activeSessionId.value = null;
  messages.value = [];
  citations.value = [];
  rewrittenQuery.value = '';
  retrievalText.value = '';
  await Promise.all([loadDocuments(id), loadSessions(id)]);
}

async function loadDocuments(knowledgeBaseId: number) {
  try {
    documents.value = await listDocuments(knowledgeBaseId);
  } catch (err) {
    error.value = apiErrorMessage(err);
  }
}

async function loadSessions(knowledgeBaseId: number | null) {
  try {
    sessions.value = await listSessions(knowledgeBaseId);
    if (sessions.value.length > 0) {
      await selectSession(sessions.value[0].id);
    }
  } catch (err) {
    error.value = apiErrorMessage(err);
  }
}

async function selectSession(id: number) {
  activeSessionId.value = id;
  citations.value = [];
  rewrittenQuery.value = '';
  retrievalText.value = '';
  try {
    messages.value = (await listMessages(id)).map(normalizeMessage);
    const lastAssistant = [...messages.value].reverse().find((item) => item.role === 'ASSISTANT');
    citations.value = parseCitations(lastAssistant?.citations);
  } catch (err) {
    error.value = apiErrorMessage(err);
  }
}

async function handleCreateKnowledgeBase(name: string) {
  error.value = '';
  try {
    const knowledgeBase = await createKnowledgeBase({ name, description: '通过前端工作台创建' });
    knowledgeBases.value = [knowledgeBase, ...knowledgeBases.value];
    await selectKnowledgeBase(knowledgeBase.id);
  } catch (err) {
    error.value = apiErrorMessage(err);
  }
}

async function handleUploadDocument(file: File) {
  if (!activeKnowledgeBaseId.value) return;
  uploading.value = true;
  error.value = '';
  addProcessLog('UPLOAD', '上传文档', file.name);
  try {
    const document = await uploadDocument(activeKnowledgeBaseId.value, file);
    documents.value = [document, ...documents.value.filter((item) => item.id !== document.id)];
    addProcessLog('PARSE', '进入解析队列', `${document.originalFilename}，等待切片和嵌入`);
    scheduleDocumentPolling(activeKnowledgeBaseId.value);
  } catch (err) {
    const message = apiErrorMessage(err);
    error.value = message;
    addProcessLog('ERROR', '上传失败', message);
    window.alert(`上传失败：${message}`);
  } finally {
    uploading.value = false;
  }
}

async function handleDeleteDocument(document: KnowledgeDocument) {
  deletingDocumentId.value = document.id;
  error.value = '';
  try {
    await deleteDocument(document.id);
    documents.value = documents.value.filter((item) => item.id !== document.id);
    notifiedFailedDocumentIds.delete(document.id);
    addProcessLog('DONE', '删除文档', document.originalFilename);
  } catch (err) {
    const message = apiErrorMessage(err);
    error.value = message;
    addProcessLog('ERROR', '删除失败', message);
    window.alert(`删除文档失败：${message}`);
  } finally {
    deletingDocumentId.value = null;
  }
}

async function handleCreateSession() {
  error.value = '';
  try {
    const titlePrefix = activeKnowledgeBaseId.value ? '知识库对话' : '自由对话';
    const session = await createSession(activeKnowledgeBaseId.value, `${titlePrefix} ${sessions.value.length + 1}`);
    sessions.value = [session, ...sessions.value];
    activeSessionId.value = session.id;
    messages.value = [];
    citations.value = [];
    rewrittenQuery.value = '';
  } catch (err) {
    error.value = apiErrorMessage(err);
  }
}

async function ensureSession(content: string) {
  if (activeSessionId.value) {
    return activeSessionId.value;
  }
  if (!activeKnowledgeBaseId.value) {
    const session = await createSession(null, content.slice(0, 28) || '新的自由对话');
    sessions.value = [session, ...sessions.value];
    activeSessionId.value = session.id;
    return session.id;
  }
  const session = await createSession(activeKnowledgeBaseId.value, content.slice(0, 28) || '新的知识库对话');
  sessions.value = [session, ...sessions.value];
  activeSessionId.value = session.id;
  return session.id;
}

function scheduleDocumentPolling(knowledgeBaseId: number) {
  // 上传后文档会异步索引，前端不知道后端何时完成。
  // 这里每 2 秒查一次文档列表，用来把 PROCESSING 更新成 READY 或 FAILED。
  if (documentPollingTimer) {
    window.clearInterval(documentPollingTimer);
  }
  let remainingTicks = 30;
  documentPollingTimer = window.setInterval(async () => {
    remainingTicks -= 1;
    const previousDocuments = documents.value;
    await loadDocuments(knowledgeBaseId);
    notifyNewFailedDocuments(previousDocuments, documents.value);
    notifyReadyDocuments(previousDocuments, documents.value);
    const hasProcessing = documents.value.some((item) => item.parseStatus === 'PROCESSING');
    if (!hasProcessing || remainingTicks <= 0) {
      window.clearInterval(documentPollingTimer);
      documentPollingTimer = undefined;
    }
  }, 2000);
}

function notifyReadyDocuments(previousDocuments: KnowledgeDocument[], nextDocuments: KnowledgeDocument[]) {
  const previousProcessingIds = new Set(
    previousDocuments.filter((item) => item.parseStatus === 'PROCESSING').map((item) => item.id),
  );
  const readyDocument = nextDocuments.find(
    (item) => item.parseStatus === 'READY' && previousProcessingIds.has(item.id),
  );
  if (!readyDocument) {
    return;
  }
  addProcessLog('EMBEDDING', '文档索引完成', `${readyDocument.originalFilename}，${readyDocument.chunkCount} 个切片已写入向量库`);
}

function notifyNewFailedDocuments(previousDocuments: KnowledgeDocument[], nextDocuments: KnowledgeDocument[]) {
  const previousProcessingIds = new Set(
    previousDocuments.filter((item) => item.parseStatus === 'PROCESSING').map((item) => item.id),
  );
  const failedDocument = nextDocuments.find(
    (item) =>
      item.parseStatus === 'FAILED' &&
      previousProcessingIds.has(item.id) &&
      !notifiedFailedDocumentIds.has(item.id),
  );
  if (!failedDocument) {
    return;
  }
  notifiedFailedDocumentIds.add(failedDocument.id);
  const detail = failedDocument.rawErrorSummary || failedDocument.errorMessage || '请删除该文档后重新上传。';
  addProcessLog('ERROR', '文档处理失败', `${failedDocument.originalFilename}：${detail}`);
  window.alert(`文档处理失败：${failedDocument.originalFilename}\n${detail}`);
}

async function sendQuestion(payload: SendQuestionPayload) {
  const { content, webSearchEnabled } = payload;
  // 一次发送会先在前端插入用户消息和一个 pending 的助手消息。
  // 后端通过 SSE 一段段返回答案，前端再按 id 更新这条助手消息。
  sending.value = true;
  error.value = '';
  retrievalText.value = '正在准备检索知识库';
  citations.value = [];
  rewrittenQuery.value = '';
  addProcessLog('RETRIEVAL', '收到问题', content);
  if (webSearchEnabled) {
    addProcessLog('RETRIEVAL', '联网搜索已开启', '后端会先识别 URL；存在 URL 时先 Tavily Extract，再 Tavily Search，最后把网页资料发给 LLM。');
  }

  const userMessage: ChatMessage = {
    id: `local-user-${Date.now()}`,
    role: 'USER',
    content,
    pending: false,
  };
  const assistantMessageId = `local-assistant-${Date.now()}`;
  const assistantMessage: ChatMessage = {
    id: assistantMessageId,
    role: 'ASSISTANT',
    content: '',
    pending: true,
    lowConfidence: false,
  };
  messages.value = [...messages.value, userMessage, assistantMessage];

  try {
    const sessionId = await ensureSession(content);
    addProcessLog('RETRIEVAL', '会话确认', `sessionId=${sessionId}`);
    await streamChatMessage(sessionId, content, { webSearchEnabled }, {
      onClientRequest(payload) {
        addProcessLog('REQUEST', `${payload.method} ${payload.url}`, JSON.stringify(payload.body, null, 2));
      },
      onClientResponse(payload) {
        addProcessLog('RESPONSE', `后端响应 HTTP ${payload.status}`, `ok=${payload.ok}`);
      },
      onRetrievalStart(payload) {
        rewrittenQuery.value = payload.query;
        retrievalText.value = `正在检索：${payload.query}`;
        addProcessLog('RETRIEVAL', '开始检索', payload.query);
      },
      onRetrievalDone(payload) {
        citations.value = payload.citations || [];
        retrievalText.value = `已找到 ${citations.value.length} 条引用证据，正在生成回答`;
        addProcessLog('LLM', '请求 LLM', `携带 ${citations.value.length} 条证据片段`);
      },
      onDebugLog(payload) {
        const stage = payload.direction.includes('->BACKEND') ? 'RESPONSE' : 'REQUEST';
        addProcessLog(stage, `${payload.direction} ${payload.title}`, prettyDetail(payload.detail));
      },
      onAnswerDelta(payload) {
        // 不能直接修改最开始创建的 assistantMessage 普通对象。
        // 必须替换 messages.value 数组里的对应项，Vue 才能稳定触发子组件重新渲染。
        updateAssistantMessage(assistantMessageId, (message) => ({
          ...message,
          pending: true,
          content: message.content + (payload.delta || ''),
        }));
      },
      onAnswerDone(payload) {
        const finalAnswer = payload.answer || currentAssistantContent(assistantMessageId);
        updateAssistantMessage(assistantMessageId, (message) => ({
          ...message,
          pending: false,
          content: finalAnswer,
          lowConfidence: payload.lowConfidence,
        }));
        citations.value = payload.citations || citations.value;
        rewrittenQuery.value = payload.rewrittenQuery || rewrittenQuery.value;
        retrievalText.value = '';
        addProcessLog('DONE', '收到 LLM 响应', `${finalAnswer.length} 个字符，低置信=${payload.lowConfidence ? '是' : '否'}`);
      },
      onError(payload) {
        updateAssistantMessage(assistantMessageId, (message) => ({
          ...message,
          pending: false,
          content: payload.message || '问答生成失败',
        }));
        error.value = `${payload.stage || 'RAG'}：${payload.rawSummary || payload.message || '未知错误'}`;
        retrievalText.value = '';
        addProcessLog('ERROR', payload.stage || 'RAG 错误', payload.rawSummary || payload.message || '未知错误');
      },
    });
  } catch (err) {
    updateAssistantMessage(assistantMessageId, (message) => ({
      ...message,
      pending: false,
      content: '问答生成失败，请查看错误详情。',
    }));
    error.value = apiErrorMessage(err);
    retrievalText.value = '';
    addProcessLog('ERROR', '请求失败', error.value);
  } finally {
    sending.value = false;
  }
}

function normalizeMessage(message: ChatMessage) {
  return {
    ...message,
    lowConfidence: Boolean(message.lowConfidence),
    pending: false,
  };
}

function updateAssistantMessage(id: string, updater: (message: ChatMessage) => ChatMessage) {
  // Vue 更容易追踪“数组被替换”的变化，所以这里使用 map 生成新数组。
  // 这比直接 message.content += delta 更稳定，尤其是流式输出时。
  messages.value = messages.value.map((message) => {
    if (String(message.id) !== id) {
      return message;
    }
    return updater(message);
  });
}

function currentAssistantContent(id: string) {
  return messages.value.find((message) => String(message.id) === id)?.content || '';
}

function parseCitations(raw: string | null | undefined) {
  if (!raw) return [];
  try {
    return JSON.parse(raw) as RagCitation[];
  } catch {
    return [];
  }
}

function addProcessLog(stage: ProcessLogItem['stage'], title: string, detail: string) {
  processLogs.value = [
    {
      id: `${Date.now()}-${Math.random().toString(16).slice(2)}`,
      time: new Date().toLocaleTimeString('zh-CN', { hour12: false }),
      stage,
      title,
      detail,
    },
    ...processLogs.value,
  ];
}

function prettyDetail(detail: string) {
  if (!detail) {
    return '';
  }
  try {
    return JSON.stringify(JSON.parse(detail), null, 2);
  } catch {
    return detail;
  }
}

async function logout() {
  authStore.clearAuth();
  await router.push('/login');
}
</script>

<style scoped>
.workbench-page {
  position: relative;
  height: 100vh;
  display: flex;
  gap: 0;
  overflow: hidden;
  padding: 14px;
  background:
    linear-gradient(115deg, transparent 0 48%, rgba(18, 149, 190, 0.08) 48.5%, transparent 52%),
    radial-gradient(circle at 9% 18%, rgba(18, 149, 190, 0.16), transparent 25%),
    radial-gradient(circle at 92% 6%, rgba(22, 63, 107, 0.12), transparent 27%),
    linear-gradient(135deg, rgba(255, 255, 255, 0.74), rgba(237, 248, 252, 0.88)),
    var(--color-soft);
}

.workbench-page::before {
  content: '';
  position: absolute;
  inset: 0;
  pointer-events: none;
  background:
    linear-gradient(90deg, rgba(18, 149, 190, 0.07) 1px, transparent 1px),
    linear-gradient(0deg, rgba(18, 149, 190, 0.05) 1px, transparent 1px);
  background-size: 36px 36px;
  mask-image: linear-gradient(180deg, rgba(0, 0, 0, 0.55), transparent 70%);
}

.resizable-sidebar,
.resizable-evidence {
  flex-shrink: 0;
  position: relative;
  z-index: 1;
}

.resizable-chat {
  flex: 1;
  min-width: 0;
  position: relative;
  z-index: 1;
  overflow: hidden;
}

.resizer {
  width: 14px;
  flex-shrink: 0;
  cursor: col-resize;
  background: transparent;
  position: relative;
  z-index: 2;
}

.resizer::before {
  content: '';
  position: absolute;
  top: 0;
  left: 50%;
  transform: translateX(-50%);
  width: 1px;
  height: calc(100% - 18px);
  top: 9px;
  background: rgba(184, 196, 212, 0.74);
  transition: all var(--transition-base);
}

.resizer:hover::before {
  background: var(--color-brand);
  width: 2px;
}

.resizer:hover {
  background: linear-gradient(90deg, transparent 0%, rgba(37, 90, 143, 0.06) 50%, transparent 100%);
}

.resizer:active::before {
  background: var(--color-brand-dark);
}

.global-error {
  position: fixed;
  left: 50%;
  bottom: 24px;
  z-index: 10;
  max-width: min(680px, calc(100vw - 32px));
  margin: 0;
  padding: 12px 16px;
  transform: translateX(-50%);
  border: 1px solid var(--color-danger);
  border-left: 4px solid var(--color-danger);
  border-radius: var(--radius-md);
  background: rgba(255, 248, 249, 0.96);
  color: #8f1f2f;
  box-shadow: var(--shadow-md);
  font-size: 13px;
  line-height: 1.6;
}

.global-error::before {
  content: '错误：';
  color: var(--color-danger);
  font-weight: 700;
}

@media (max-width: 1120px) {
  .resizable-evidence,
  .resizer-right {
    display: none;
  }
}

@media (max-width: 760px) {
  .workbench-page {
    flex-direction: column;
    padding: 0;
  }

  .resizable-sidebar {
    width: 100% !important;
  }

  .resizer-left {
    display: none;
  }

  .workbench-page :deep(.kb-sidebar) {
    min-height: auto;
    max-height: 50vh;
    border-right: 0;
    border-bottom: 1px solid var(--color-line);
  }

  .workbench-page :deep(.chat-panel) {
    min-height: 52vh;
  }
}
</style>
