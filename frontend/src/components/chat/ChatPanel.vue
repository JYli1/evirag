<template>
  <section class="chat-panel">
    <header class="chat-head">
      <div>
        <p>{{ knowledgeBaseName || '未选择知识库' }}</p>
        <h2>{{ sessionTitle || '新对话' }}</h2>
      </div>
      <button type="button" :disabled="!canCreateSession" @click="$emit('createSession')">新建会话</button>
    </header>

    <MessageList :messages="messages" :retrieval-text="retrievalText" />

    <ChatComposer :disabled="!canSend" :sending="sending" @send="$emit('send', $event)" />
  </section>
</template>

<script setup lang="ts">
import type { ChatMessage } from '@/api/chat';
import ChatComposer from '@/components/chat/ChatComposer.vue';
import MessageList from '@/components/chat/MessageList.vue';

defineProps<{
  knowledgeBaseName: string;
  sessionTitle: string;
  messages: ChatMessage[];
  retrievalText: string;
  canSend: boolean;
  canCreateSession: boolean;
  sending: boolean;
}>();

defineEmits<{
  send: [content: string];
  createSession: [];
}>();
</script>

<style scoped>
.chat-panel {
  height: 100vh;
  display: grid;
  grid-template-rows: auto 1fr auto;
  overflow: hidden;
  background: var(--color-soft);
}

.chat-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  min-height: 64px;
  padding: 16px 24px;
  border-bottom: 1px solid var(--color-line);
  background: var(--color-panel);
  box-shadow: var(--shadow-sm);
}

.chat-head::before {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  bottom: -1px;
  height: 1px;
  background: var(--color-brand);
  opacity: 0.3;
}

.chat-head p,
.chat-head h2 {
  margin: 0;
}

.chat-head p {
  color: var(--color-terminal-cyan);
  font-size: 11px;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 1.5px;
  font-family: monospace;
}

.chat-head p::before {
  content: '// ';
  color: var(--color-muted);
}

.chat-head h2 {
  margin-top: 6px;
  color: var(--color-terminal-green);
  font-size: 18px;
  font-weight: 700;
  letter-spacing: -0.3px;
  font-family: monospace;
}

.chat-head button {
  min-width: 100px;
  height: 36px;
  padding: 0 20px;
  border: 1px solid var(--color-brand);
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--color-brand);
  font-weight: 700;
  font-size: 12px;
  text-transform: uppercase;
  letter-spacing: 1px;
  transition: all var(--transition-fast);
  font-family: monospace;
}

.chat-head button:hover:not(:disabled) {
  background: var(--color-brand);
  color: var(--color-soft);
  box-shadow: var(--shadow-glow);
}

.chat-head button:active:not(:disabled) {
  transform: scale(0.98);
}

.chat-head button:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}
</style>
