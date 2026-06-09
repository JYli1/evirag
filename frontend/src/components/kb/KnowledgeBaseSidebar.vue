<template>
  <aside class="kb-sidebar">
    <header class="sidebar-head">
      <EviRagLogo compact />
      <div class="head-actions">
        <RouterLink v-if="isAdmin" class="ghost-link" to="/admin">管理</RouterLink>
        <button class="ghost-button" type="button" @click="$emit('logout')" title="退出登录">退出</button>
      </div>
    </header>

    <form class="create-kb" @submit.prevent="createKb">
      <input v-model.trim="newName" placeholder="新知识库名称" aria-label="新知识库名称" />
      <button type="submit" title="新建知识库">+</button>
    </form>

    <section class="sidebar-section">
      <div class="section-title">
        <span>知识库</span>
        <small>{{ knowledgeBases.length }}</small>
      </div>
      <button
        v-for="kb in knowledgeBases"
        :key="kb.id"
        class="kb-item"
        :class="{ active: kb.id === activeKnowledgeBaseId }"
        type="button"
        @click="$emit('selectKnowledgeBase', kb.id)"
      >
        <strong>{{ kb.name }}</strong>
        <small>{{ kb.description || kb.status }}</small>
      </button>
      <p v-if="!knowledgeBases.length" class="empty">创建知识库后上传文档。</p>
    </section>

    <DocumentUploader
      :knowledge-base-id="activeKnowledgeBaseId"
      :documents="documents"
      :uploading="uploading"
      :deleting-document-id="deletingDocumentId"
      @upload="$emit('uploadDocument', $event)"
      @delete="$emit('deleteDocument', $event)"
    />

    <section class="sidebar-section">
      <div class="session-head">
        <span>会话</span>
        <button type="button" title="新建会话" @click="$emit('createSession')">+</button>
      </div>
      <button
        v-for="session in sessions"
        :key="session.id"
        class="session-item"
        :class="{ active: session.id === activeSessionId }"
        type="button"
        @click="$emit('selectSession', session.id)"
      >
        {{ session.title }}
      </button>
      <p v-if="!sessions.length" class="empty">暂无会话。</p>
    </section>
  </aside>
</template>

<script setup lang="ts">
import { ref } from 'vue';

import type { KnowledgeDocument } from '@/api/document';
import type { ChatSession } from '@/api/chat';
import type { KnowledgeBase } from '@/api/knowledge';
import EviRagLogo from '@/assets/logo/EviRagLogo.vue';
import DocumentUploader from '@/components/document/DocumentUploader.vue';

defineProps<{
  knowledgeBases: KnowledgeBase[];
  documents: KnowledgeDocument[];
  sessions: ChatSession[];
  activeKnowledgeBaseId: number | null;
  activeSessionId: number | null;
  uploading: boolean;
  deletingDocumentId: number | null;
  isAdmin: boolean;
}>();

const emit = defineEmits<{
  selectKnowledgeBase: [id: number];
  createKnowledgeBase: [name: string];
  uploadDocument: [file: File];
  deleteDocument: [document: KnowledgeDocument];
  selectSession: [id: number];
  createSession: [];
  logout: [];
}>();

const newName = ref('');

function createKb() {
  if (!newName.value) return;
  emit('createKnowledgeBase', newName.value);
  newName.value = '';
}
</script>

<style scoped>
.kb-sidebar {
  height: 100vh;
  display: grid;
  grid-template-rows: auto auto minmax(120px, auto) auto minmax(80px, 200px);
  gap: 16px;
  padding: 20px;
  border-right: 1px solid var(--color-line);
  background: var(--color-panel);
  overflow-y: auto;
  box-shadow: var(--shadow-md);
}

.sidebar-head,
.session-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.head-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.ghost-link {
  display: inline-grid;
  place-items: center;
  min-height: 32px;
  padding: 0 12px;
  border: 1px solid var(--color-brand);
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--color-brand);
  font-size: 12px;
  font-weight: 700;
  text-decoration: none;
  text-transform: uppercase;
  letter-spacing: 1px;
  transition: all var(--transition-fast);
}

.ghost-link:hover {
  background: var(--color-brand);
  color: var(--color-soft);
  box-shadow: var(--shadow-glow);
}

.ghost-button,
.session-head button,
.create-kb button {
  border: 1px solid var(--color-line);
  border-radius: var(--radius-sm);
  background: var(--color-soft);
  color: var(--color-ink);
  font-weight: 700;
  transition: all var(--transition-fast);
}

.ghost-button:hover,
.session-head button:hover,
.create-kb button:hover {
  border-color: var(--color-brand);
  background: var(--color-brand);
  color: var(--color-soft);
  box-shadow: var(--shadow-glow);
}

.ghost-button {
  padding: 8px 12px;
  font-size: 12px;
  text-transform: uppercase;
}

.create-kb {
  display: grid;
  grid-template-columns: 1fr 40px;
  gap: 10px;
  padding: 12px;
  border: 1px solid var(--color-line);
  border-radius: var(--radius-md);
  background: var(--color-soft);
}

.create-kb input {
  min-width: 0;
  height: 40px;
  padding: 0 14px;
  border: 1px solid var(--color-line);
  border-radius: var(--radius-sm);
  background: var(--color-panel);
  color: var(--color-ink);
  outline: none;
  transition: all var(--transition-fast);
  font-family: monospace;
}

.create-kb input:focus {
  border-color: var(--color-brand);
  box-shadow: 0 0 0 2px rgba(14, 165, 233, 0.2);
}

.create-kb input::placeholder {
  color: var(--color-muted);
}

.sidebar-section {
  display: grid;
  gap: 8px;
  overflow-y: auto;
  max-height: 100%;
}

.section-title,
.session-head span {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  color: var(--color-terminal-cyan);
  font-size: 11px;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 1.5px;
  font-family: monospace;
}

.section-title::before,
.session-head span::before {
  content: '>';
  color: var(--color-brand);
  margin-right: 4px;
}

.section-title small {
  color: var(--color-muted);
  font-size: 11px;
  font-weight: 700;
  background: var(--color-soft);
  padding: 2px 8px;
  border-radius: var(--radius-sm);
  border: 1px solid var(--color-line);
}

.kb-item,
.session-item {
  width: 100%;
  text-align: left;
  border: 1px solid var(--color-line);
  border-radius: var(--radius-sm);
  background: var(--color-soft);
  color: var(--color-ink);
  transition: all var(--transition-fast);
  position: relative;
}

.kb-item {
  display: grid;
  gap: 6px;
  padding: 12px;
}

.kb-item strong {
  overflow-wrap: anywhere;
  font-size: 13px;
  font-weight: 600;
  color: var(--color-terminal-green);
  font-family: monospace;
}

.kb-item small {
  color: var(--color-muted);
  font-size: 11px;
  font-family: monospace;
}

.session-item {
  padding: 10px 12px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 12px;
  font-weight: 500;
  font-family: monospace;
}

.kb-item.active,
.session-item.active {
  border-color: var(--color-brand);
  background: rgba(14, 165, 233, 0.1);
  box-shadow: inset 3px 0 0 var(--color-brand);
}

.kb-item.active::after,
.session-item.active::after {
  content: '●';
  position: absolute;
  right: 12px;
  top: 50%;
  transform: translateY(-50%);
  color: var(--color-terminal-green);
  font-size: 8px;
  animation: blink 1.5s ease-in-out infinite;
}

.kb-item:hover,
.session-item:hover {
  border-color: var(--color-brand);
  background: rgba(14, 165, 233, 0.15);
  transform: translateX(2px);
  box-shadow: var(--shadow-glow);
}

.empty {
  margin: 0;
  padding: 12px;
  color: var(--color-muted);
  font-size: 12px;
  line-height: 1.6;
  text-align: center;
  background: var(--color-soft);
  border-radius: var(--radius-sm);
  border: 1px dashed var(--color-line);
  font-family: monospace;
}

.empty::before {
  content: '// ';
  color: var(--color-terminal-cyan);
}
</style>
