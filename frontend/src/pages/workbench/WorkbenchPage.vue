<template>
  <main class="workbench-page">
    <KnowledgeBaseSidebar
      :knowledge-bases="knowledgeBases"
      :documents="documents"
      :sessions="sessions"
      :active-knowledge-base-id="activeKnowledgeBaseId"
      :active-session-id="activeSessionId"
      :uploading="uploading"
      :is-admin="authStore.isAdmin"
      @select-knowledge-base="selectKnowledgeBase"
      @create-knowledge-base="handleCreateKnowledgeBase"
      @upload-document="handleUploadDocument"
      @select-session="selectSession"
      @create-session="handleCreateSession"
      @logout="logout"
    />

    <ChatPanel
      :knowledge-base-name="activeKnowledgeBase?.name || ''"
      :session-title="activeSession?.title || ''"
      :messages="messages"
      :retrieval-text="retrievalText"
      :can-send="Boolean(activeKnowledgeBaseId) && !sending"
      :can-create-session="Boolean(activeKnowledgeBaseId) && !sending"
      :sending="sending"
      @send="sendQuestion"
      @create-session="handleCreateSession"
    />

    <EvidencePanel :citations="citations" :query="rewrittenQuery" />

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
import { listDocuments, uploadDocument, type KnowledgeDocument } from '@/api/document';
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
const sending = ref(false);

const activeKnowledgeBase = computed(() =>
  knowledgeBases.value.find((item) => item.id === activeKnowledgeBaseId.value),
);

const activeSession = computed(() =>
  sessions.value.find((item) => item.id === activeSessionId.value),
);

onMounted(async () => {
  await loadKnowledgeBases();
});

async function loadKnowledgeBases() {
  error.value = '';
  try {
    knowledgeBases.value = await listKnowledgeBases();
    if (knowledgeBases.value.length > 0) {
      await selectKnowledgeBase(knowledgeBases.value[0].id);
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

async function loadSessions(knowledgeBaseId: number) {
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
  } catch (err) {
    error.value = apiErrorMessage(err);
  } finally {
    uploading.value = false;
  }
}

async function handleCreateSession() {
  if (!activeKnowledgeBaseId.value) return;
  error.value = '';
  try {
    const session = await createSession(activeKnowledgeBaseId.value, `知识库对话 ${sessions.value.length + 1}`);
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
    throw new Error('请先选择知识库');
  }
  const session = await createSession(activeKnowledgeBaseId.value, content.slice(0, 28) || '新的知识库对话');
  sessions.value = [session, ...sessions.value];
  activeSessionId.value = session.id;
  return session.id;
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
  min-height: 100vh;
  display: grid;
  grid-template-columns: 292px minmax(0, 1fr) 340px;
  background: var(--color-soft);
}

.global-error {
  position: fixed;
  left: 50%;
  bottom: 18px;
  z-index: 10;
  max-width: min(680px, calc(100vw - 32px));
  margin: 0;
  padding: 10px 13px;
  transform: translateX(-50%);
  border: 1px solid rgba(178, 74, 63, 0.24);
  border-radius: var(--radius-md);
  background: rgba(255, 248, 246, 0.95);
  color: #7b342d;
  box-shadow: var(--shadow-soft);
  font-size: 13px;
  line-height: 1.6;
}

@media (max-width: 1120px) {
  .workbench-page {
    grid-template-columns: 260px minmax(0, 1fr);
  }
}

@media (max-width: 1120px) {
  .workbench-page :deep(.evidence-panel) {
    display: none;
  }
}

@media (max-width: 760px) {
  .workbench-page {
    grid-template-columns: 1fr;
  }

  .workbench-page :deep(.kb-sidebar) {
    min-height: auto;
    max-height: 48vh;
    border-right: 0;
    border-bottom: 1px solid var(--color-line);
  }

  .workbench-page :deep(.chat-panel) {
    min-height: 52vh;
  }
}
</style>
