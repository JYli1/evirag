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
        <div
          v-if="message.content"
          class="markdown-body"
          :class="{ typing: isTypingMessage(message) }"
          v-html="renderMessage(message)"
        ></div>
        <div v-else-if="message.pending" class="pending-indicator" aria-label="正在生成回答">
          <span></span>
          <span></span>
          <span></span>
        </div>
        <p v-else class="pending-text">暂无内容</p>
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
import { computed, onBeforeUnmount, ref, watch } from 'vue';
import DOMPurify from 'dompurify';
import { marked } from 'marked';

import type { ChatMessage } from '@/api/chat';

const props = defineProps<{
  messages: ChatMessage[];
  retrievalText: string;
}>();

const typedContent = ref<Record<string, string>>({});
let typingTimer: number | undefined;

marked.setOptions({
  breaks: true,
  gfm: true,
});

const latestTypingKey = computed(() => {
  // 只让最新一条正在生成的助手消息使用打字机效果。
  // 历史消息直接渲染，避免刷新长对话时从头逐字播放。
  const latest = [...props.messages].reverse().find((message) => message.role === 'ASSISTANT' && message.pending);
  return latest ? messageKey(latest) : '';
});

const messageTargets = computed(() => {
  const targets: Record<string, string> = {};
  const latestKey = latestTypingKey.value;
  if (!latestKey) {
    return targets;
  }
  const latest = props.messages.find((message) => messageKey(message) === latestKey);
  if (latest?.content) {
    targets[latestKey] = latest.content;
  }
  return targets;
});

watch(
  messageTargets,
  (targets) => {
    let needsTick = false;
    const next = { ...typedContent.value };
    for (const [key, target] of Object.entries(targets)) {
      if (!(key in next)) {
        next[key] = '';
      }
      if (next[key].length > target.length || !target.startsWith(next[key])) {
        next[key] = target;
      }
      if (next[key].length < target.length) {
        needsTick = true;
      }
    }
    for (const key of Object.keys(next)) {
      if (!(key in targets)) {
        delete next[key];
      }
    }
    typedContent.value = next;
    if (needsTick) {
      startTyping();
    }
  },
  { immediate: true },
);

onBeforeUnmount(() => {
  if (typingTimer) {
    window.clearInterval(typingTimer);
  }
});

function startTyping() {
  if (typingTimer) {
    return;
  }
  typingTimer = window.setInterval(() => {
    const targets = messageTargets.value;
    let hasPending = false;
    const next = { ...typedContent.value };
    for (const [key, target] of Object.entries(targets)) {
      const current = next[key] || '';
      if (current.length < target.length) {
        const step = target.length - current.length > 160 ? 8 : 2;
        next[key] = target.slice(0, current.length + step);
        hasPending = true;
      }
    }
    typedContent.value = next;
    if (!hasPending && typingTimer) {
      window.clearInterval(typingTimer);
      typingTimer = undefined;
    }
  }, 24);
}

function renderMessage(message: ChatMessage) {
  const key = messageKey(message);
  const content = isTypingMessage(message) ? typedContent.value[key] || '' : message.content || '';
  // marked 负责把 Markdown 转成 HTML，DOMPurify 负责清洗危险标签。
  // 因为模板里用了 v-html，这一步清洗不能省。
  const rawHtml = marked.parse(content, { async: false }) as string;
  return DOMPurify.sanitize(rawHtml);
}

function isTypingMessage(message: ChatMessage) {
  return messageKey(message) === latestTypingKey.value;
}

function messageKey(message: ChatMessage) {
  return String(message.id || `${message.role}-${message.createdAt || ''}-${message.content.slice(0, 24)}`);
}
</script>

<style scoped>
.message-list {
  display: grid;
  gap: 18px;
  align-content: start;
  padding: 34px min(6vw, 76px);
  overflow-y: auto;
  background:
    radial-gradient(circle at 16% 0%, rgba(37, 90, 143, 0.05), transparent 24%),
    transparent;
}

.empty-state {
  align-self: center;
  justify-self: center;
  width: min(100%, 560px);
  display: grid;
  justify-items: center;
  gap: 12px;
  margin-top: 16vh;
  padding: 34px;
  border: 1px solid var(--color-line);
  border-radius: var(--radius-xl);
  background: rgba(255, 255, 255, 0.78);
  box-shadow: var(--shadow-md);
  text-align: center;
}

.empty-mark {
  width: 48px;
  height: 48px;
  display: grid;
  place-items: center;
  border: 1px solid rgba(37, 90, 143, 0.18);
  border-radius: 50%;
  background: #eef5fb;
  color: var(--color-brand-dark);
  font-size: 20px;
  font-weight: 900;
}

.empty-state h3,
.empty-state p {
  margin: 0;
}

.empty-state h3 {
  color: var(--color-ink);
  font-size: 18px;
  font-weight: 700;
}

.empty-state p {
  color: var(--color-muted);
  font-size: 14px;
  line-height: 1.7;
  max-width: 400px;
}

.message {
  display: grid;
  grid-template-columns: 38px minmax(0, 1fr);
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
  grid-template-columns: minmax(0, 1fr) 38px;
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
  width: 38px;
  height: 38px;
  display: grid;
  place-items: center;
  border: 1px solid var(--color-line);
  border-radius: 50%;
  background: var(--color-panel);
  color: var(--color-brand-dark);
  font-size: 13px;
  font-weight: 900;
  flex-shrink: 0;
}

.message.user .avatar {
  background: #f7f2e8;
  color: var(--color-accent);
  border-color: rgba(163, 106, 31, 0.22);
}

.bubble {
  padding: 14px 16px;
  border: 1px solid var(--color-line);
  border-radius: 18px 18px 18px 6px;
  background: rgba(255, 255, 255, 0.9);
  color: var(--color-ink);
  box-shadow: var(--shadow-sm);
}

.bubble:hover {
  border-color: rgba(37, 90, 143, 0.18);
  box-shadow: var(--shadow-card);
}

.message.user .bubble {
  border-radius: 18px 18px 6px 18px;
  border-color: rgba(37, 90, 143, 0.22);
  background: linear-gradient(135deg, #173f68, #255a8f);
  color: #ffffff;
}

.message.user .bubble:hover {
  box-shadow: var(--shadow-glow);
}

.pending-text {
  margin: 0;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  line-height: 1.75;
  font-size: 14px;
}

.pending-indicator {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  min-height: 24px;
}

.pending-indicator span {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--color-cyan);
  animation: pendingPulse 1s ease-in-out infinite;
}

.pending-indicator span:nth-child(2) {
  animation-delay: 140ms;
}

.pending-indicator span:nth-child(3) {
  animation-delay: 280ms;
}

@keyframes pendingPulse {
  0%,
  70%,
  100% {
    opacity: 0.28;
    transform: translateY(0);
  }
  35% {
    opacity: 1;
    transform: translateY(-3px);
  }
}

.markdown-body {
  overflow-wrap: anywhere;
  line-height: 1.75;
  font-size: 14px;
}

.markdown-body.typing::after {
  content: '';
  display: inline-block;
  width: 7px;
  height: 1.2em;
  margin-left: 3px;
  border-radius: 999px;
  background: currentColor;
  vertical-align: -0.2em;
  animation: caretBlink 0.9s steps(2, start) infinite;
}

@keyframes caretBlink {
  50% {
    opacity: 0;
  }
}

.markdown-body :deep(p),
.markdown-body :deep(ul),
.markdown-body :deep(ol),
.markdown-body :deep(blockquote),
.markdown-body :deep(pre),
.markdown-body :deep(table) {
  margin: 0 0 10px;
}

.markdown-body :deep(p:last-child),
.markdown-body :deep(ul:last-child),
.markdown-body :deep(ol:last-child),
.markdown-body :deep(blockquote:last-child),
.markdown-body :deep(pre:last-child),
.markdown-body :deep(table:last-child) {
  margin-bottom: 0;
}

.markdown-body :deep(ul),
.markdown-body :deep(ol) {
  padding-left: 20px;
}

.markdown-body :deep(li + li) {
  margin-top: 4px;
}

.markdown-body :deep(code) {
  padding: 2px 6px;
  border-radius: 6px;
  background: rgba(23, 32, 51, 0.07);
  font-family: "Cascadia Code", "JetBrains Mono", Consolas, monospace;
  font-size: 0.92em;
}

.markdown-body :deep(pre) {
  max-width: 100%;
  overflow-x: auto;
  padding: 12px 14px;
  border: 1px solid rgba(216, 224, 235, 0.9);
  border-radius: var(--radius-md);
  background: #101828;
  color: #edf6ff;
}

.markdown-body :deep(pre code) {
  padding: 0;
  background: transparent;
  color: inherit;
}

.markdown-body :deep(blockquote) {
  padding: 8px 12px;
  border-left: 3px solid var(--color-brand-light);
  border-radius: 8px;
  background: rgba(37, 90, 143, 0.07);
}

.markdown-body :deep(a) {
  color: var(--color-cyan);
  font-weight: 700;
}

.markdown-body :deep(table) {
  display: block;
  max-width: 100%;
  overflow-x: auto;
  border-collapse: collapse;
}

.markdown-body :deep(th),
.markdown-body :deep(td) {
  padding: 7px 10px;
  border: 1px solid var(--color-line);
}

.message.user .markdown-body :deep(code) {
  background: rgba(255, 255, 255, 0.14);
}

.message.user .markdown-body :deep(pre) {
  border-color: rgba(255, 255, 255, 0.18);
  background: rgba(10, 20, 34, 0.48);
}

.message.user .markdown-body :deep(a) {
  color: #d7f6ff;
}

.low-confidence {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin-top: 10px;
  padding: 4px 10px;
  background: rgba(163, 106, 31, 0.1);
  color: var(--color-accent);
  font-size: 11px;
  font-weight: 700;
  border-radius: 999px;
  border: 1px solid rgba(163, 106, 31, 0.18);
}

.retrieval-state {
  justify-self: center;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 18px;
  border: 1px solid rgba(37, 90, 143, 0.18);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.86);
  color: var(--color-brand);
  font-size: 12px;
  font-weight: 600;
  box-shadow: var(--shadow-sm);
  animation: slideUp 0.2s ease-out;
}

.retrieval-state::before {
  content: '';
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
