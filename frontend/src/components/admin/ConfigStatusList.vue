<template>
  <section class="config-list">
    <header>
      <div>
        <p>配置状态</p>
        <h2>{{ configStatus?.missingCount ?? 0 }} 项必填配置缺失</h2>
      </div>
      <span>密钥只展示是否配置</span>
    </header>

    <div class="config-items">
      <article v-for="item in configStatus?.items ?? []" :key="item.key" class="config-item">
        <div>
          <strong>{{ item.name }}</strong>
          <small>{{ item.key }} · {{ item.group }}</small>
        </div>
        <span class="pill" :class="{ ok: item.configured, miss: !item.configured && item.required }">
          {{ item.configured ? '已配置' : item.required ? '缺失' : '可选' }}
        </span>
        <p>{{ item.secret ? '敏感配置，前端不显示明文。' : item.message }}</p>
      </article>
    </div>
  </section>
</template>

<script setup lang="ts">
import type { AdminConfigStatus } from '@/api/admin';

defineProps<{
  configStatus: AdminConfigStatus | null;
}>();
</script>

<style scoped>
.config-list {
  display: grid;
  gap: 14px;
  padding: 16px;
  border: 1px solid var(--color-line);
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.88);
  box-shadow: var(--shadow-card);
}

header {
  display: flex;
  align-items: end;
  justify-content: space-between;
  gap: 12px;
}

header p,
header h2 {
  margin: 0;
}

header p {
  color: var(--color-muted);
  font-size: 12px;
  font-weight: 900;
}

header h2 {
  margin-top: 5px;
  color: var(--color-ink);
  font-size: 20px;
}

header span {
  color: var(--color-muted);
  font-size: 12px;
}

.config-items {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.config-item {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 8px 12px;
  padding: 12px;
  border: 1px solid var(--color-line);
  border-radius: 16px;
  background: var(--color-panel-muted);
}

.config-item strong,
.config-item small,
.config-item p {
  overflow-wrap: anywhere;
}

.config-item strong {
  color: var(--color-ink);
  font-size: 13px;
}

.config-item small {
  display: block;
  margin-top: 3px;
  color: var(--color-muted);
  font-size: 12px;
}

.config-item p {
  grid-column: 1 / -1;
  margin: 0;
  color: rgba(107, 120, 140, 0.88);
  font-size: 12px;
  line-height: 1.55;
}

.pill {
  align-self: start;
  padding: 4px 7px;
  border-radius: 999px;
  background: rgba(217, 119, 6, 0.12);
  color: var(--color-accent);
  font-size: 12px;
  font-weight: 900;
}

.pill.ok {
  background: rgba(15, 118, 110, 0.1);
  color: #0f766e;
}

.pill.miss {
  background: rgba(190, 18, 60, 0.1);
  color: var(--color-danger);
}

@media (max-width: 880px) {
  .config-items {
    grid-template-columns: 1fr;
  }
}
</style>
