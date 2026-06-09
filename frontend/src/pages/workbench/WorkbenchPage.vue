<template>
  <main class="workbench-page">
    <div class="resizable-sidebar" :style="{ width: leftWidth + 'px' }">
      <KnowledgeBaseSidebar
        :knowledge-bases="knowledgeBases"
        :documents="documents"
        :sessions="sessions"
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
let documentPollingTimer: number | undefined;
const notifiedFailedDocumentIds = new Set<number>();

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
  try {
    const document = await uploadDocument(activeKnowledgeBaseId.value, file);
    documents.value = [document, ...documents.value.filter((item) => item.id !== document.id)];
    scheduleDocumentPolling(activeKnowledgeBaseId.value);
  } catch (err) {
    const message = apiErrorMessage(err);
    error.value = message;
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
  } catch (err) {
    const message = apiErrorMessage(err);
    error.value = message;
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
  if (documentPollingTimer) {
    window.clearInterval(documentPollingTimer);
  }
  let remainingTicks = 30;
  documentPollingTimer = window.setInterval(async () => {
    remainingTicks -= 1;
    const previousDocuments = documents.value;
    await loadDocuments(knowledgeBaseId);
    notifyNewFailedDocuments(previousDocuments, documents.value);
    const hasProcessing = documents.value.some((item) => item.parseStatus === 'PROCESSING');
    if (!hasProcessing || remainingTicks <= 0) {
      window.clearInterval(documentPollingTimer);
      documentPollingTimer = undefined;
    }
  }, 2000);
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
  window.alert(`文档处理失败：${failedDocument.originalFilename}\n${detail}`);
}

async function sendQuestion(content: string) {
  sending.value = true;
  error.value = '';
  retrievalText.value = '正在准备检索知识库';
  citations.value = [];
  rewrittenQuery.value = '';

  const userMessage: ChatMessage = {
    id: `local-user-${Date.now()}`,
    role: 'USER',
    content,
    pending: false,
  };
  const assistantMessage: ChatMessage = {
    id: `local-assistant-${Date.now()}`,
    role: 'ASSISTANT',
    content: '',
    pending: true,
    lowConfidence: false,
  };
  messages.value = [...messages.value, userMessage, assistantMessage];

  try {
    const sessionId = await ensureSession(content);
    await streamChatMessage(sessionId, content, {
      onRetrievalStart(payload) {
        rewrittenQuery.value = payload.query;
        retrievalText.value = `正在检索：${payload.query}`;
      },
      onRetrievalDone(payload) {
        citations.value = payload.citations || [];
        retrievalText.value = `已找到 ${citations.value.length} 条引用证据，正在生成回答`;
      },
      onAnswerDelta(payload) {
        assistantMessage.pending = false;
        assistantMessage.content += payload.delta || '';
      },
      onAnswerDone(payload) {
        assistantMessage.pending = false;
        assistantMessage.content = payload.answer || assistantMessage.content;
        assistantMessage.lowConfidence = payload.lowConfidence;
        citations.value = payload.citations || citations.value;
        rewrittenQuery.value = payload.rewrittenQuery || rewrittenQuery.value;
        retrievalText.value = '';
      },
      onError(payload) {
        assistantMessage.pending = false;
        assistantMessage.content = payload.message || '问答生成失败';
        error.value = `${payload.stage || 'RAG'}：${payload.rawSummary || payload.message || '未知错误'}`;
        retrievalText.value = '';
      },
    });
  } catch (err) {
    assistantMessage.pending = false;
    assistantMessage.content = '问答生成失败，请查看错误详情。';
    error.value = apiErrorMessage(err);
    retrievalText.value = '';
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

function parseCitations(raw: string | null | undefined) {
  if (!raw) return [];
  try {
    return JSON.parse(raw) as RagCitation[];
  } catch {
    return [];
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
  overflow: hidden;
  background: var(--color-soft);
}

.workbench-page::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 1px;
  background: var(--color-brand);
  box-shadow: 0 0 10px var(--color-brand);
}

.resizable-sidebar,
.resizable-evidence {
  flex-shrink: 0;
}

.resizable-chat {
  flex: 1;
  min-width: 0;
}

.resizer {
  width: 12px;
  flex-shrink: 0;
  cursor: col-resize;
  background: transparent;
  position: relative;
  transition: all var(--transition-fast);
}

.resizer::before {
  content: '';
  position: absolute;
  top: 0;
  left: 50%;
  transform: translateX(-50%);
  width: 1px;
  height: 100%;
  background: var(--color-line);
  transition: all var(--transition-base);
}

.resizer:hover::before {
  background: var(--color-brand);
  box-shadow: 0 0 8px var(--color-brand);
  width: 2px;
}

.resizer:hover {
  background: linear-gradient(90deg,
    transparent 0%,
    rgba(14, 165, 233, 0.1) 50%,
    transparent 100%);
}

.resizer:active::before {
  background: var(--color-terminal-cyan);
  box-shadow: 0 0 12px var(--color-terminal-cyan);
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
  border-left: 3px solid var(--color-danger);
  border-radius: var(--radius-sm);
  background: rgba(30, 41, 59, 0.95);
  color: var(--color-terminal-red);
  box-shadow: var(--shadow-md), 0 0 20px rgba(239, 68, 68, 0.3);
  font-size: 13px;
  font-weight: 500;
  line-height: 1.6;
  font-family: monospace;
  animation: slideUpError 0.3s ease-out;
}

.global-error::before {
  content: '[ERROR] ';
  color: var(--color-danger);
  font-weight: 700;
}

@keyframes slideUpError {
  0% {
    opacity: 0;
    transform: translateX(-50%) translateY(20px);
  }
  100% {
    opacity: 1;
    transform: translateX(-50%) translateY(0);
  }
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
