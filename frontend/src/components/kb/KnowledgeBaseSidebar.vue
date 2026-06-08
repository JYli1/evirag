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
      <button type="submit">+</button>
    </form>

    <section class="sidebar-section">
      <div class="section-title">知识库</div>
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
        <button type="button" @click="$emit('createSession')">+</button>
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
  min-height: 100vh;
  display: grid;
  grid-template-rows: auto auto minmax(120px, auto) auto 1fr;
  gap: 18px;
  padding: 16px;
  border-right: 1px solid var(--color-line);
  background: rgba(251, 253, 249, 0.92);
  overflow-y: auto;
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
  padding: 0 9px;
  border: 1px solid var(--color-line);
  border-radius: var(--radius-sm);
  background: #ffffff;
  color: var(--color-brand-dark);
  font-size: 12px;
  font-weight: 900;
  text-decoration: none;
}

.ghost-button,
.session-head button,
.create-kb button {
  border: 1px solid var(--color-line);
  border-radius: var(--radius-sm);
  background: #ffffff;
  color: var(--color-brand-dark);
  font-weight: 900;
}

.ghost-button {
  padding: 7px 9px;
  font-size: 12px;
}

.create-kb {
  display: grid;
  grid-template-columns: 1fr 36px;
  gap: 8px;
}

.create-kb input {
  min-width: 0;
  height: 38px;
  padding: 0 10px;
  border: 1px solid var(--color-line);
  border-radius: var(--radius-sm);
  background: #ffffff;
}

.sidebar-section {
  display: grid;
  gap: 8px;
}

.section-title,
.session-head span {
  color: var(--color-muted);
  font-size: 12px;
  font-weight: 900;
}

.kb-item,
.session-item {
  width: 100%;
  text-align: left;
  border: 1px solid transparent;
  border-radius: var(--radius-md);
  background: transparent;
  color: var(--color-ink);
}

.kb-item {
  display: grid;
  gap: 4px;
  padding: 11px;
}

.kb-item strong {
  overflow-wrap: anywhere;
  font-size: 14px;
}

.kb-item small {
  color: var(--color-muted);
  font-size: 12px;
}

.session-item {
  padding: 9px 10px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 13px;
}

.kb-item.active,
.session-item.active {
  border-color: rgba(31, 122, 87, 0.28);
  background: rgba(31, 122, 87, 0.08);
}

.empty {
  margin: 0;
  color: var(--color-muted);
  font-size: 12px;
  line-height: 1.55;
}
</style>
