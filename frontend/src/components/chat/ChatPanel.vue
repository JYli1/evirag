<template>
  <section class="chat-panel" :class="{ 'with-logs': showLogs }">
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
  // 日志项唯一 ID，用于 v-for key。
  id: string;
  // 展示时间，通常是 HH:mm:ss。
  time: string;
  // 阶段名，用于颜色和分类。
  stage: 'REQUEST' | 'RESPONSE' | 'UPLOAD' | 'PARSE' | 'EMBEDDING' | 'RETRIEVAL' | 'LLM' | 'DONE' | 'ERROR';
  // 日志标题。
  title: string;
  // 日志详情，可能是 JSON 字符串。
  detail: string;
}

defineProps<{
  // 当前知识库名称，空时显示未选择知识库。
  knowledgeBaseName: string;
  // 当前会话标题。
  sessionTitle: string;
  // 消息列表。
  messages: ChatMessage[];
  // 检索中的状态文案。
  retrievalText: string;
  // 过程日志列表。
  processLogs: ProcessLogItem[];
  // 是否允许发送消息。
  canSend: boolean;
  // 是否允许创建新会话。
  canCreateSession: boolean;
  // 是否正在等待后端流式回答。
  sending: boolean;
}>();

defineEmits<{
  // 父组件收到输入内容和搜索开关后调用 streamChatMessage。
  send: [payload: { content: string; webSearchEnabled: boolean }];
  // 父组件负责真正创建会话并刷新列表。
  createSession: [];
}>();

// 过程日志默认折叠，避免占用聊天区域。
const showLogs = ref(false);
</script>

<style scoped>
.chat-panel {
  height: calc(100vh - 28px);
  display: grid;
  grid-template-rows: auto minmax(0, 1fr) auto;
  overflow: hidden;
  border: 1px solid var(--color-line);
  border-radius: var(--radius-xl);
  background: var(--color-crystal);
  box-shadow: var(--shadow-card);
  backdrop-filter: blur(20px);
}

.chat-panel.with-logs {
  grid-template-rows: auto auto minmax(0, 1fr) auto;
}

.chat-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  min-height: 68px;
  padding: 16px 22px;
  border-bottom: 1px solid var(--color-line);
  background: rgba(255, 255, 255, 0.84);
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
  box-shadow: 0 0 0 5px rgba(18, 149, 190, 0.08);
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
  background: rgba(255, 255, 255, 0.68);
  color: var(--color-brand-dark);
  font-weight: 700;
  font-size: 12px;
}

.chat-head .log-toggle {
  border-color: rgba(42, 118, 148, 0.22);
  color: var(--color-cyan);
}

.chat-head .log-toggle.active {
  background: #e8f8fc;
  box-shadow: inset 0 0 0 1px var(--color-strong-line);
}

.process-log {
  max-height: min(46vh, 440px);
  min-height: 168px;
  padding: 4px 18px;
  overflow: auto;
  overscroll-behavior: contain;
  border-bottom: 1px solid var(--color-line);
  background: rgba(248, 253, 255, 0.94);
}

.log-item {
  position: relative;
  overflow: visible;
  display: grid;
  grid-template-columns: 8px 76px minmax(120px, 180px) minmax(0, 1fr);
  align-items: start;
  gap: 10px;
  padding: 10px 0;
  border-bottom: 1px solid rgba(42, 118, 148, 0.12);
  background: transparent;
}

.log-item span {
  color: var(--color-subtle);
  font-size: 11px;
  font-variant-numeric: tabular-nums;
}

.log-item strong {
  color: var(--color-ink);
  font-size: 12px;
  line-height: 1.45;
}

.log-item p {
  min-width: 0;
  margin: 0;
  overflow: visible;
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
  background: #e8f8fc;
}

.chat-head button:active:not(:disabled) {
  transform: scale(0.98);
}

.chat-head button:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}
</style>
