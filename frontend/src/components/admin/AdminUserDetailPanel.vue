<template>
  <section class="user-detail-panel">
    <header>
      <div>
        <p>用户详情</p>
        <h2>{{ detail?.user.email || '选择一个用户' }}</h2>
      </div>
      <span v-if="loading">加载中</span>
      <span v-else-if="detail">{{ detail.user.role }} · {{ statusText(detail.user.status) }}</span>
    </header>

    <p v-if="loading" class="empty">正在读取用户统计...</p>
    <p v-else-if="!detail" class="empty">点击左侧用户行查看单独统计。</p>

    <template v-else>
      <div class="identity-strip">
        <div>
          <small>用户名</small>
          <strong>{{ detail.user.username }}</strong>
        </div>
        <div>
          <small>创建时间</small>
          <strong>{{ formatDate(detail.user.createdAt) }}</strong>
        </div>
      </div>

      <div class="detail-metrics">
        <article>
          <span>知识库</span>
          <strong>{{ detail.knowledgeBaseCount }}</strong>
        </article>
        <article>
          <span>文档</span>
          <strong>{{ detail.documentCount }}</strong>
        </article>
        <article>
          <span>切片</span>
          <strong>{{ detail.chunkCount }}</strong>
        </article>
        <article>
          <span>问题</span>
          <strong>{{ detail.questionCount }}</strong>
        </article>
        <article>
          <span>回答</span>
          <strong>{{ detail.assistantMessageCount }}</strong>
        </article>
        <article>
          <span>估算 Token</span>
          <strong>{{ formatNumber(detail.estimatedTotalTokens) }}</strong>
        </article>
      </div>

      <section class="visual-panel">
        <div class="visual-block">
          <div class="visual-title">
            <span>文档状态</span>
            <small>{{ detail.readyDocumentCount }} 就绪 / {{ detail.failedDocumentCount }} 失败</small>
          </div>
          <div class="stack-bar" aria-label="文档状态占比">
            <i class="ready" :style="{ width: documentPercent(detail.readyDocumentCount) }"></i>
            <i class="failed" :style="{ width: documentPercent(detail.failedDocumentCount) }"></i>
          </div>
        </div>

        <div class="visual-block">
          <div class="visual-title">
            <span>Token 来源</span>
            <small>估算值</small>
          </div>
          <div class="bar-row">
            <span>文档</span>
            <div><i :style="tokenBarStyle(detail.estimatedDocumentTokens)"></i></div>
            <strong>{{ formatNumber(detail.estimatedDocumentTokens) }}</strong>
          </div>
          <div class="bar-row">
            <span>对话</span>
            <div><i class="chat" :style="tokenBarStyle(detail.estimatedChatTokens)"></i></div>
            <strong>{{ formatNumber(detail.estimatedChatTokens) }}</strong>
          </div>
        </div>
      </section>

      <div class="recent-grid">
        <section>
          <h3>近期文档</h3>
          <article v-for="document in detail.recentDocuments" :key="document.id" class="recent-item">
            <strong>{{ document.originalFilename }}</strong>
            <small>{{ statusText(document.parseStatus) }} · {{ document.chunkCount }} 个切片 · {{ formatDate(document.createdAt) }}</small>
          </article>
          <p v-if="!detail.recentDocuments.length" class="empty slim">暂无文档。</p>
        </section>

        <section>
          <h3>近期消息</h3>
          <article v-for="message in detail.recentMessages" :key="message.id" class="recent-item">
            <strong>{{ roleText(message.role) }} #{{ message.sessionId }}</strong>
            <small>{{ message.preview || '空消息' }}</small>
          </article>
          <p v-if="!detail.recentMessages.length" class="empty slim">暂无消息。</p>
        </section>
      </div>
    </template>
  </section>
</template>

<script setup lang="ts">
import type { CSSProperties } from 'vue';

import type { AdminUserDetail } from '@/api/admin';

const props = defineProps<{
  detail: AdminUserDetail | null;
  loading: boolean;
}>();

function documentPercent(value: number) {
  const total = props.detail?.documentCount ?? 0;
  if (!total) return '0%';
  return `${Math.round((value / total) * 100)}%`;
}

function tokenBarStyle(value: number): CSSProperties {
  const max = Math.max(props.detail?.estimatedDocumentTokens ?? 0, props.detail?.estimatedChatTokens ?? 0, 1);
  const width = value <= 0 ? 0 : Math.max(6, Math.round((value / max) * 100));
  return { width: `${width}%` };
}

function statusText(status: string) {
  if (status === 'ACTIVE') return '启用';
  if (status === 'DISABLED') return '禁用';
  if (status === 'READY') return '已就绪';
  if (status === 'PROCESSING') return '处理中';
  if (status === 'FAILED') return '失败';
  return status;
}

function roleText(role: string) {
  if (role === 'USER') return '用户';
  if (role === 'ASSISTANT') return '助手';
  return role;
}

function formatNumber(value: number) {
  return new Intl.NumberFormat('zh-CN').format(value);
}

function formatDate(value: string) {
  return new Date(value).toLocaleString('zh-CN');
}
</script>

<style scoped>
.user-detail-panel {
  display: grid;
  gap: 14px;
  padding: 16px;
  border: 1px solid var(--color-line);
  border-radius: 18px;
  background: #ffffff;
  box-shadow: var(--shadow-card);
}

header {
  display: flex;
  align-items: start;
  justify-content: space-between;
  gap: 12px;
}

header p,
header h2,
h3,
.empty {
  margin: 0;
}

header p,
header span,
.identity-strip small,
.detail-metrics span,
.visual-title small,
.recent-item small {
  color: var(--color-muted);
  font-size: 12px;
  font-weight: 900;
}

header h2 {
  margin-top: 5px;
  color: var(--color-ink);
  font-size: 20px;
  line-height: 1.25;
  overflow-wrap: anywhere;
}

.identity-strip {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(160px, auto);
  gap: 12px;
  padding: 12px;
  border: 1px solid var(--color-line);
  border-radius: 14px;
  background: #f8fbff;
}

.identity-strip div {
  display: grid;
  gap: 5px;
  min-width: 0;
}

.identity-strip strong {
  overflow-wrap: anywhere;
  color: var(--color-ink);
  font-size: 13px;
}

.detail-metrics {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}

.detail-metrics article {
  display: grid;
  gap: 5px;
  min-height: 74px;
  padding: 10px;
  border: 1px solid var(--color-line);
  border-radius: 14px;
  background: #f8fbff;
}

.detail-metrics strong {
  color: var(--color-ink);
  font-size: 22px;
  line-height: 1.1;
  overflow-wrap: anywhere;
}

.visual-panel {
  display: grid;
  gap: 14px;
  padding: 13px;
  border: 1px solid rgba(37, 99, 235, 0.14);
  border-radius: 14px;
  background: linear-gradient(180deg, #f8fbff 0%, #ffffff 100%);
}

.visual-block {
  display: grid;
  gap: 9px;
}

.visual-title {
  display: flex;
  justify-content: space-between;
  gap: 12px;
}

.visual-title span {
  color: var(--color-ink);
  font-size: 13px;
  font-weight: 900;
}

.stack-bar {
  height: 14px;
  display: flex;
  overflow: hidden;
  border-radius: 999px;
  background: #edf2f7;
}

.stack-bar i {
  display: block;
  min-width: 0;
}

.stack-bar .ready {
  background: #0f766e;
}

.stack-bar .failed {
  background: #be123c;
}

.bar-row {
  display: grid;
  grid-template-columns: 42px minmax(0, 1fr) 82px;
  align-items: center;
  gap: 9px;
}

.bar-row span,
.bar-row strong {
  color: var(--color-muted);
  font-size: 12px;
  font-weight: 900;
}

.bar-row strong {
  color: var(--color-ink);
  text-align: right;
}

.bar-row div {
  height: 10px;
  overflow: hidden;
  border-radius: 999px;
  background: #edf2f7;
}

.bar-row i {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: #2563eb;
}

.bar-row i.chat {
  background: #d97706;
}

.recent-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.recent-grid section {
  display: grid;
  align-content: start;
  gap: 9px;
}

h3 {
  color: var(--color-ink);
  font-size: 14px;
}

.recent-item {
  display: grid;
  gap: 4px;
  padding: 9px 0;
  border-top: 1px solid var(--color-line);
}

.recent-item strong {
  color: var(--color-ink);
  font-size: 13px;
  line-height: 1.35;
  overflow-wrap: anywhere;
}

.recent-item small {
  font-weight: 700;
  line-height: 1.55;
  overflow-wrap: anywhere;
}

.empty {
  color: var(--color-muted);
  font-size: 13px;
  line-height: 1.6;
}

.empty.slim {
  font-size: 12px;
}

@media (max-width: 760px) {
  .identity-strip,
  .recent-grid {
    grid-template-columns: 1fr;
  }

  .detail-metrics {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
