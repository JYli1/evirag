<template>
  <section class="metric-grid" aria-label="管理员统计">
    <article v-for="metric in metrics" :key="metric.label" class="metric-card">
      <span>{{ metric.label }}</span>
      <strong>{{ metric.value }}</strong>
      <small>{{ metric.hint }}</small>
    </article>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue';

import type { AdminDashboard } from '@/api/admin';

const props = defineProps<{
  dashboard: AdminDashboard | null;
}>();

const metrics = computed(() => [
  { label: '用户数', value: props.dashboard?.totalUsers ?? 0, hint: `${props.dashboard?.activeUsers ?? 0} 个活跃账号` },
  { label: '知识库数', value: props.dashboard?.totalKnowledgeBases ?? 0, hint: '所有用户知识库总量' },
  { label: '文档数', value: props.dashboard?.totalDocuments ?? 0, hint: `${props.dashboard?.readyDocuments ?? 0} 个已就绪` },
  { label: '问答次数', value: props.dashboard?.questionCount ?? 0, hint: '按用户消息统计' },
  { label: '估算 Token', value: props.dashboard?.estimatedTotalTokens ?? 0, hint: '文档切片 + 对话内容估算' },
  { label: '今日上传', value: props.dashboard?.todayUploadCount ?? 0, hint: '按文档创建时间统计' },
  { label: '配置缺失', value: props.dashboard?.missingConfigCount ?? 0, hint: '只统计必填配置' },
]);
</script>

<style scoped>
.metric-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(156px, 1fr));
  gap: 14px;
}

.metric-card {
  position: relative;
  overflow: hidden;
  display: grid;
  gap: 8px;
  min-height: 126px;
  padding: 16px;
  border: 1px solid var(--color-line);
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.74);
  box-shadow: var(--shadow-card);
  backdrop-filter: blur(18px);
  transition:
    transform 0.16s ease,
    border-color 0.16s ease,
    box-shadow 0.16s ease;
}

.metric-card::after {
  content: '';
  position: absolute;
  right: -28px;
  bottom: -38px;
  width: 104px;
  height: 104px;
  border: 1px solid var(--color-strong-line);
  border-radius: 26px;
  transform: rotate(18deg);
  opacity: 0.6;
}

.metric-card:hover {
  transform: translateY(-3px);
  border-color: var(--color-strong-line);
  box-shadow: var(--shadow-glow);
}

.metric-card span {
  color: var(--color-muted);
  font-size: 12px;
  font-weight: 900;
}

.metric-card strong {
  position: relative;
  z-index: 1;
  color: var(--color-brand-dark);
  font-size: 32px;
  line-height: 1;
}

.metric-card small {
  color: var(--color-muted);
  font-size: 12px;
  line-height: 1.45;
}

@media (max-width: 680px) {
  .metric-grid {
    grid-template-columns: repeat(2, minmax(120px, 1fr));
  }
}
</style>
