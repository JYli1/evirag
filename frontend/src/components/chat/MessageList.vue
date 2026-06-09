<template>
  <div class="message-list">
    <section v-if="!messages.length" class="empty-state">
      <div class="empty-mark">E</div>
      <h3>开始一次可追溯问答</h3>
      <p>选择知识库会优先检索文档；没有知识库时也可以直接自由提问。</p>
    </section>
    <article v-for="message in messages" :key="message.id || `${message.role}-${message.content}`" class="message" :class="message.role.toLowerCase()">
      <div class="avatar">{{ message.role === 'USER' ? '我' : 'E' }}</div>
      <div class="bubble">
        <p>{{ message.content || (message.pending ? '正在生成回答...' : '') }}</p>
        <span v-if="message.lowConfidence" class="low-confidence">相关性较低</span>
      </div>
    </article>
    <div v-if="retrievalText" class="retrieval-state">
      <span class="retrieval-spinner"></span>
      <span>{{ retrievalText }}</span>
    </div>
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
  gap: 20px;
  align-content: start;
  padding: 32px min(6vw, 80px);
  overflow-y: auto;
  background: var(--color-soft);
}

.empty-state {
  align-self: center;
  justify-self: center;
  width: min(100%, 560px);
  display: grid;
  justify-items: center;
  gap: 14px;
  margin-top: 16vh;
  padding: 40px;
  border: 2px dashed var(--color-line);
  border-radius: var(--radius-md);
  background: var(--color-panel);
  text-align: center;
  box-shadow: var(--shadow-md);
}

.empty-mark {
  width: 56px;
  height: 56px;
  display: grid;
  place-items: center;
  border: 2px solid var(--color-brand);
  border-radius: var(--radius-sm);
  background: rgba(14, 165, 233, 0.1);
  color: var(--color-brand);
  font-size: 24px;
  font-weight: 700;
  font-family: monospace;
}

.empty-state h3,
.empty-state p {
  margin: 0;
}

.empty-state h3 {
  color: var(--color-terminal-green);
  font-size: 20px;
  font-weight: 700;
  font-family: monospace;
}

.empty-state h3::before {
  content: '$ ';
  color: var(--color-brand);
}

.empty-state p {
  color: var(--color-muted);
  font-size: 14px;
  line-height: 1.7;
  max-width: 400px;
  font-family: monospace;
}

.empty-state p::before {
  content: '// ';
  color: var(--color-terminal-cyan);
}

.message {
  display: grid;
  grid-template-columns: 42px minmax(0, 1fr);
  gap: 14px;
  max-width: 960px;
  animation: slideIn 0.2s ease-out;
}

@keyframes slideIn {
  from {
    opacity: 0;
    transform: translateX(-10px);
  }
  to {
    opacity: 1;
    transform: translateX(0);
  }
}

.message.user {
  justify-self: end;
  grid-template-columns: minmax(0, 1fr) 42px;
}

.message.user .avatar {
  grid-column: 2;
  grid-row: 1;
}

.message.user .bubble {
  grid-column: 1;
  grid-row: 1;
}

.avatar {
  width: 42px;
  height: 42px;
  display: grid;
  place-items: center;
  border: 1px solid var(--color-line);
  border-radius: var(--radius-sm);
  background: var(--color-panel);
  color: var(--color-terminal-cyan);
  font-size: 14px;
  font-weight: 700;
  font-family: monospace;
  flex-shrink: 0;
}

.message.user .avatar {
  background: var(--color-soft);
  color: var(--color-terminal-yellow);
  border-color: var(--color-line);
}

.bubble {
  padding: 14px 16px;
  border: 1px solid var(--color-line);
  border-left: 3px solid var(--color-brand);
  border-radius: var(--radius-md);
  background: var(--color-panel);
  color: var(--color-ink);
  box-shadow: var(--shadow-sm);
  transition: all var(--transition-fast);
  font-family: monospace;
}

.bubble:hover {
  border-left-color: var(--color-terminal-cyan);
  box-shadow: var(--shadow-md);
}

.message.user .bubble {
  border-left: 3px solid var(--color-terminal-green);
  border-right: none;
  background: rgba(74, 222, 128, 0.05);
}

.message.user .bubble:hover {
  border-left-color: var(--color-terminal-green);
  box-shadow: var(--shadow-glow);
}

.bubble p {
  margin: 0;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  line-height: 1.75;
  font-size: 14px;
}

.low-confidence {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin-top: 10px;
  padding: 4px 10px;
  background: rgba(251, 191, 36, 0.1);
  color: var(--color-terminal-yellow);
  font-size: 11px;
  font-weight: 700;
  border-radius: var(--radius-sm);
  border: 1px solid var(--color-terminal-yellow);
  text-transform: uppercase;
  letter-spacing: 1px;
}

.low-confidence::before {
  content: '[!]';
  font-weight: 900;
}

.retrieval-state {
  justify-self: center;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 18px;
  border: 1px solid var(--color-brand);
  border-radius: var(--radius-sm);
  background: rgba(14, 165, 233, 0.1);
  color: var(--color-brand);
  font-size: 12px;
  font-weight: 600;
  font-family: monospace;
  box-shadow: var(--shadow-glow);
  animation: slideUp 0.2s ease-out;
}

.retrieval-state::before {
  content: '[EXEC]';
  font-weight: 700;
  color: var(--color-terminal-cyan);
  letter-spacing: 1px;
}

@keyframes slideUp {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.retrieval-spinner {
  width: 16px;
  height: 16px;
  border: 2px solid var(--color-line);
  border-top-color: var(--color-brand);
  border-radius: 50%;
  animation: spin 0.6s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}
</style>
