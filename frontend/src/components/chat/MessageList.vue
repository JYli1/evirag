<template>
  <div class="message-list">
    <article v-for="message in messages" :key="message.id || `${message.role}-${message.content}`" class="message" :class="message.role.toLowerCase()">
      <div class="avatar">{{ message.role === 'USER' ? '我' : 'E' }}</div>
      <div class="bubble">
        <p>{{ message.content || (message.pending ? '正在生成回答...' : '') }}</p>
        <span v-if="message.lowConfidence" class="low-confidence">相关性较低</span>
      </div>
    </article>
    <div v-if="retrievalText" class="retrieval-state">{{ retrievalText }}</div>
  </div>
</template>

<script setup lang="ts">
import type { ChatMessage } from '@/api/chat';

defineProps<{
  messages: ChatMessage[];
  retrievalText: string;
}>();
</script>

<style scoped>
.message-list {
  display: grid;
  gap: 18px;
  align-content: start;
  padding: 28px min(6vw, 72px);
  overflow-y: auto;
}

.message {
  display: grid;
  grid-template-columns: 36px minmax(0, 1fr);
  gap: 12px;
  max-width: 920px;
}

.message.user {
  justify-self: end;
  grid-template-columns: minmax(0, 1fr) 36px;
}

.message.user .avatar {
  grid-column: 2;
  grid-row: 1;
}

.message.user .bubble {
  grid-column: 1;
  grid-row: 1;
  background: #e6f2ea;
}

.avatar {
  width: 36px;
  height: 36px;
  display: grid;
  place-items: center;
  border-radius: 50%;
  background: var(--color-brand-dark);
  color: #ffffff;
  font-size: 13px;
  font-weight: 900;
}

.bubble {
  padding: 13px 15px;
  border: 1px solid var(--color-line);
  border-radius: var(--radius-lg);
  background: #ffffff;
  color: var(--color-ink);
  box-shadow: 0 10px 30px rgba(31, 49, 38, 0.05);
}

.bubble p {
  margin: 0;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  line-height: 1.75;
}

.low-confidence {
  display: inline-block;
  margin-top: 9px;
  color: var(--color-accent);
  font-size: 12px;
  font-weight: 900;
}

.retrieval-state {
  justify-self: center;
  padding: 7px 12px;
  border: 1px solid var(--color-line);
  border-radius: var(--radius-sm);
  background: rgba(255, 255, 255, 0.82);
  color: var(--color-muted);
  font-size: 13px;
}
</style>
