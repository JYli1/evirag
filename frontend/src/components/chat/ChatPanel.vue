<template>
  <section class="chat-panel">
    <header class="chat-head">
      <div>
        <p>{{ knowledgeBaseName || '未选择知识库' }}</p>
        <h2>{{ sessionTitle || '新对话' }}</h2>
      </div>
      <div class="chat-actions">
        <button
          class="log-toggle"
          type="button"
          :class="{ active: showLogs }"
          @click="showLogs = !showLogs"
        >
          过程日志
        </button>
        <button type="button" :disabled="!canCreateSession" @click="$emit('createSession')">新建会话</button>
      </div>
    </header>

    <section v-if="showLogs" class="process-log">
      <article v-for="item in processLogs" :key="item.id" class="log-item" :class="item.stage.toLowerCase()">
        <span>{{ item.time }}</span>
        <strong>{{ item.title }}</strong>
        <p>{{ item.detail }}</p>
      </article>
      <p v-if="!processLogs.length" class="log-empty">暂无过程日志。</p>
    </section>

    <MessageList :messages="messages" :retrieval-text="retrievalText" />

    <ChatComposer :disabled="!canSend" :sending="sending" @send="$emit('send', $event)" />
  </section>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import type { ChatMessage } from '@/api/chat';
import ChatComposer from '@/components/chat/ChatComposer.vue';
import MessageList from '@/components/chat/MessageList.vue';

interface ProcessLogItem {
  id: string;
  time: string;
  stage: 'REQUEST' | 'RESPONSE' | 'UPLOAD' | 'PARSE' | 'EMBEDDING' | 'RETRIEVAL' | 'LLM' | 'DONE' | 'ERROR';
  title: string;
  detail: string;
}

defineProps<{
  knowledgeBaseName: string;
  sessionTitle: string;
  messages: ChatMessage[];
  retrievalText: string;
  processLogs: ProcessLogItem[];
  canSend: boolean;
  canCreateSession: boolean;
  sending: boolean;
}>();

defineEmits<{
  send: [content: string];
  createSession: [];
}>();

const showLogs = ref(false);
</script>

<style scoped>
.chat-panel {
  height: calc(100vh - 28px);
  display: grid;
  grid-template-rows: auto 1fr auto;
  overflow: hidden;
  border: 1px solid rgba(216, 224, 235, 0.82);
  border-radius: var(--radius-xl);
  background: rgba(255, 255, 255, 0.74);
  box-shadow: var(--shadow-card);
  backdrop-filter: blur(16px);
}

.chat-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  min-height: 68px;
  padding: 16px 22px;
  border-bottom: 1px solid var(--color-line);
  background: rgba(255, 255, 255, 0.82);
}

.chat-head p,
.chat-head h2 {
  margin: 0;
}

.chat-head p {
  max-width: 52vw;
  overflow: hidden;
  color: var(--color-muted);
  font-size: 12px;
  font-weight: 800;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.chat-head p::before {
  content: '';
  display: inline-block;
  width: 7px;
  height: 7px;
  margin-right: 8px;
  border-radius: 999px;
  background: var(--color-success);
  vertical-align: 1px;
}

.chat-head h2 {
  margin-top: 6px;
  color: var(--color-ink);
  font-size: 18px;
  font-weight: 700;
  letter-spacing: -0.3px;
}

.chat-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.chat-head button {
  min-width: 100px;
  height: 36px;
  padding: 0 20px;
  border: 1px solid var(--color-line);
  border-radius: 999px;
  background: var(--color-panel-muted);
  color: var(--color-brand-dark);
  font-weight: 700;
  font-size: 12px;
}

.chat-head .log-toggle {
  border-color: rgba(42, 118, 148, 0.22);
  color: var(--color-cyan);
}

.chat-head .log-toggle.active {
  background: rgba(42, 118, 148, 0.1);
  box-shadow: inset 0 0 0 1px rgba(42, 118, 148, 0.2);
}

.process-log {
  max-height: min(46vh, 460px);
  min-height: 260px;
  display: grid;
  gap: 8px;
  padding: 12px 20px;
  overflow-y: auto;
  border-bottom: 1px solid var(--color-line);
  background:
    linear-gradient(90deg, rgba(42, 118, 148, 0.07), transparent 44%),
    rgba(248, 251, 253, 0.92);
}

.log-item {
  display: grid;
  grid-template-columns: 7px 76px minmax(116px, auto) minmax(0, 1fr);
  align-items: start;
  gap: 10px;
  padding: 8px 10px;
  border: 1px solid rgba(216, 224, 235, 0.8);
  border-radius: var(--radius-md);
  background: rgba(255, 255, 255, 0.74);
}

.log-item span {
  color: var(--color-subtle);
  font-size: 11px;
  font-variant-numeric: tabular-nums;
}

.log-item strong {
  color: var(--color-ink);
  font-size: 12px;
}

.log-item p {
  min-width: 0;
  margin: 0;
  max-height: 180px;
  overflow: auto;
  color: var(--color-muted);
  font-family: "Cascadia Code", "JetBrains Mono", Consolas, monospace;
  font-size: 11px;
  line-height: 1.55;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.log-item::before {
  content: '';
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--color-cyan);
  box-shadow: 0 0 0 4px rgba(42, 118, 148, 0.12);
}

.log-item.done::before {
  background: var(--color-success);
}

.log-item.error::before {
  background: var(--color-danger);
  box-shadow: 0 0 0 4px rgba(180, 35, 58, 0.1);
}

.log-item.llm::before {
  background: var(--color-brand);
}

.log-item.request::before {
  background: var(--color-signal);
}

.log-item.response::before {
  background: var(--color-accent);
}

.log-empty {
  margin: 0;
  padding: 12px;
  color: var(--color-muted);
  text-align: center;
  font-size: 12px;
}

.chat-head button:hover:not(:disabled) {
  border-color: rgba(37, 90, 143, 0.34);
  background: #eef5fb;
}

.chat-head button:active:not(:disabled) {
  transform: scale(0.98);
}

.chat-head button:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}
</style>
