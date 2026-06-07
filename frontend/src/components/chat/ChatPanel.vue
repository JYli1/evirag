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
  min-height: 100vh;
  display: grid;
  grid-template-rows: auto 1fr auto;
  background: #f7faf7;
}

.chat-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  padding: 17px 22px;
  border-bottom: 1px solid var(--color-line);
  background: rgba(251, 253, 249, 0.96);
}

.chat-head p,
.chat-head h2 {
  margin: 0;
}

.chat-head p {
  color: var(--color-muted);
  font-size: 12px;
  font-weight: 900;
}

.chat-head h2 {
  margin-top: 4px;
  color: var(--color-ink);
  font-size: 20px;
  letter-spacing: 0;
}

.chat-head button {
  min-width: 88px;
  height: 36px;
  border: 1px solid var(--color-line);
  border-radius: var(--radius-sm);
  background: #ffffff;
  color: var(--color-brand-dark);
  font-weight: 900;
}
</style>
