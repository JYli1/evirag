<template>
  <aside class="kb-sidebar">
    <header class="sidebar-head">
      <EviRagLogo compact />
      <div class="account-popover">
        <button class="account-trigger" type="button" aria-label="账号信息">
          <span>{{ userInitial }}</span>
        </button>
        <div class="account-card">
          <small>当前账号</small>
          <strong>{{ user?.email || '未登录' }}</strong>
          <p>{{ user?.role === 'ADMIN' ? '管理员' : '普通用户' }} · ID {{ user?.id || '-' }}</p>
        </div>
      </div>
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
import { computed, ref } from 'vue';

import type { KnowledgeDocument } from '@/api/document';
import type { ChatSession } from '@/api/chat';
import type { KnowledgeBase } from '@/api/knowledge';
import type { AuthUser } from '@/api/auth';
import EviRagLogo from '@/assets/logo/EviRagLogo.vue';
import DocumentUploader from '@/components/document/DocumentUploader.vue';

const props = defineProps<{
  // 当前用户知识库列表。
  knowledgeBases: KnowledgeBase[];
  // 当前知识库文档列表。
  documents: KnowledgeDocument[];
  // 当前知识库或自由会话列表。
  sessions: ChatSession[];
  // 当前登录用户。
  user: AuthUser | null;
  // 当前选中的知识库。
  activeKnowledgeBaseId: number | null;
  // 当前选中的会话。
  activeSessionId: number | null;
  // 上传状态。
  uploading: boolean;
  // 删除中文档 ID。
  deletingDocumentId: number | null;
  // 是否展示管理员入口。
  isAdmin: boolean;
}>();

const emit = defineEmits<{
  // 选择知识库。
  selectKnowledgeBase: [id: number];
  // 创建知识库。
  createKnowledgeBase: [name: string];
  // 上传文档。
  uploadDocument: [file: File];
  // 删除文档。
  deleteDocument: [document: KnowledgeDocument];
  // 选择会话。
  selectSession: [id: number];
  // 创建会话。
  createSession: [];
  // 退出登录。
  logout: [];
}>();

// 新知识库名称输入框。
const newName = ref('');

// 账号头像显示邮箱首字母。
const userInitial = computed(() => (props.user?.email?.slice(0, 1) || 'U').toUpperCase());

function createKb() {
  if (!newName.value) return;
  emit('createKnowledgeBase', newName.value);
  // 创建请求交给父组件，输入框本地立即清空。
  newName.value = '';
}
</script>

<style scoped>
.kb-sidebar {
  height: calc(100vh - 28px);
  display: grid;
  grid-template-rows: auto auto minmax(120px, auto) auto minmax(80px, 200px);
  gap: 14px;
  padding: 18px;
  border-right: 1px solid var(--color-line);
  border: 1px solid var(--color-line);
  border-radius: var(--radius-xl);
  background: var(--color-crystal);
  overflow-y: auto;
  box-shadow: var(--shadow-card);
  backdrop-filter: blur(20px);
}

.sidebar-head,
.session-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.account-popover {
  position: relative;
  margin-left: auto;
}

.account-trigger {
  width: 34px;
  height: 34px;
  display: grid;
  place-items: center;
  border: 1px solid rgba(42, 118, 148, 0.28);
  border-radius: 50%;
  background:
    radial-gradient(circle at 30% 24%, rgba(255, 255, 255, 0.9), transparent 32%),
    linear-gradient(135deg, var(--color-brand-dark), var(--color-brand));
  color: #ffffff;
  font-size: 12px;
  font-weight: 900;
  box-shadow: var(--shadow-glow);
}

.account-card {
  position: absolute;
  top: 44px;
  left: 0;
  z-index: 20;
  width: 236px;
  display: grid;
  gap: 7px;
  padding: 15px;
  border: 1px solid rgba(42, 118, 148, 0.22);
  border-radius: var(--radius-lg);
  background:
    linear-gradient(135deg, rgba(255, 255, 255, 0.94), rgba(232, 248, 252, 0.92)),
    var(--color-panel);
  box-shadow: var(--shadow-lg);
  opacity: 0;
  pointer-events: none;
  transform: translateY(-8px) scale(0.98);
  transition:
    opacity 360ms ease,
    transform 360ms ease;
  backdrop-filter: blur(18px);
}

.account-card::before {
  content: '';
  position: absolute;
  top: -5px;
  left: 12px;
  width: 10px;
  height: 10px;
  border-left: 1px solid rgba(42, 118, 148, 0.22);
  border-top: 1px solid rgba(42, 118, 148, 0.22);
  background: rgba(255, 255, 255, 0.94);
  transform: rotate(45deg);
}

.account-popover:hover .account-card,
.account-popover:focus-within .account-card {
  opacity: 1;
  pointer-events: auto;
  transform: translateY(0) scale(1);
}

.account-card small {
  color: var(--color-cyan);
  font-size: 11px;
  font-weight: 900;
}

.account-card strong {
  overflow-wrap: anywhere;
  color: var(--color-ink);
  font-size: 13px;
  line-height: 1.35;
}

.account-card p {
  margin: 0;
  color: var(--color-muted);
  font-size: 12px;
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
  border: 1px solid var(--color-line);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.68);
  color: var(--color-brand-dark);
  font-size: 12px;
  font-weight: 700;
  text-decoration: none;
}

.ghost-link:hover {
  border-color: rgba(37, 90, 143, 0.34);
  background: #e8f8fc;
}

.ghost-button,
.session-head button,
.create-kb button {
  border: 1px solid var(--color-line);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.68);
  color: var(--color-ink);
  font-weight: 700;
}

.ghost-button:hover,
.session-head button:hover,
.create-kb button:hover {
  border-color: rgba(37, 90, 143, 0.34);
  background: #e8f8fc;
  color: var(--color-brand-dark);
}

.ghost-button {
  padding: 8px 12px;
  font-size: 12px;
}

.create-kb {
  display: grid;
  grid-template-columns: 1fr 40px;
  gap: 10px;
  padding: 12px;
  border: 1px solid var(--color-line);
  border-radius: var(--radius-lg);
  background:
    linear-gradient(90deg, rgba(18, 149, 190, 0.06), transparent),
    rgba(255, 255, 255, 0.58);
}

.create-kb input {
  min-width: 0;
  height: 40px;
  padding: 0 14px;
  border: 1px solid var(--color-line);
  border-radius: var(--radius-md);
  background: var(--color-panel);
  color: var(--color-ink);
  outline: none;
}

.create-kb input:focus {
  border-color: var(--color-brand);
  box-shadow: 0 0 0 3px rgba(37, 90, 143, 0.1);
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
  color: var(--color-muted);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0;
}

.section-title small {
  color: var(--color-muted);
  font-size: 11px;
  font-weight: 700;
  background: rgba(255, 255, 255, 0.62);
  padding: 2px 8px;
  border-radius: 999px;
  border: 1px solid var(--color-line);
}

.kb-item,
.session-item {
  width: 100%;
  text-align: left;
  border: 1px solid var(--color-line);
  border-radius: var(--radius-md);
  background: rgba(255, 255, 255, 0.66);
  color: var(--color-ink);
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
  font-weight: 800;
  color: var(--color-ink);
}

.kb-item small {
  color: var(--color-muted);
  font-size: 11px;
}

.session-item {
  padding: 10px 12px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 12px;
  font-weight: 500;
}

.kb-item.active,
.session-item.active {
  border-color: var(--color-strong-line);
  background: linear-gradient(135deg, rgba(255, 255, 255, 0.9), rgba(223, 245, 250, 0.76));
  box-shadow: inset 3px 0 0 var(--color-brand);
}

.kb-item.active::after,
.session-item.active::after {
  content: '';
  position: absolute;
  right: 12px;
  top: 50%;
  transform: translateY(-50%);
  width: 6px;
  height: 6px;
  border-radius: 999px;
  background: var(--color-success);
}

.kb-item:hover,
.session-item:hover {
  border-color: var(--color-strong-line);
  background: rgba(255, 255, 255, 0.82);
  transform: translateY(-2px);
  box-shadow: var(--shadow-sm);
}

.empty {
  margin: 0;
  padding: 12px;
  color: var(--color-muted);
  font-size: 12px;
  line-height: 1.6;
  text-align: center;
  background: rgba(255, 255, 255, 0.58);
  border-radius: var(--radius-md);
  border: 1px dashed var(--color-line);
}
</style>
