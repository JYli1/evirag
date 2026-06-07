<template>
  <aside class="evidence-panel">
    <header>
      <p>引用证据</p>
      <strong>{{ citations.length }} 条</strong>
    </header>

    <section v-if="query" class="query-box">
      <span>检索问题</span>
      <p>{{ query }}</p>
    </section>

    <article v-for="(citation, index) in citations" :key="citation.vectorId || index" class="citation">
      <div class="citation-head">
        <strong>#{{ index + 1 }} {{ citation.sourceTitle || '知识库片段' }}</strong>
        <span :class="{ low: citation.lowScore }">{{ Math.round(citation.score * 100) }}%</span>
      </div>
      <small>
        {{ citation.sourceLocation || `切片 ${citation.chunkIndex ?? '-'}` }}
        <b v-if="citation.lowScore">相关性较低</b>
      </small>
      <p>{{ citation.content }}</p>
    </article>

    <p v-if="!citations.length" class="empty">回答生成后会显示 Top-K 引用片段。</p>
  </aside>
</template>

<script setup lang="ts">
import type { RagCitation } from '@/api/chat';

defineProps<{
  citations: RagCitation[];
  query: string;
}>();
</script>

<style scoped>
.evidence-panel {
  min-height: 100vh;
  display: grid;
  grid-auto-rows: max-content;
  gap: 13px;
  padding: 18px;
  border-left: 1px solid var(--color-line);
  background: rgba(251, 253, 249, 0.92);
  overflow-y: auto;
}

header,
.citation-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

header p {
  margin: 0;
  color: var(--color-muted);
  font-size: 12px;
  font-weight: 900;
}

header strong {
  color: var(--color-ink);
  font-size: 18px;
}

.query-box,
.citation {
  border: 1px solid var(--color-line);
  border-radius: var(--radius-md);
  background: #ffffff;
}

.query-box {
  padding: 12px;
}

.query-box span {
  color: var(--color-muted);
  font-size: 12px;
  font-weight: 900;
}

.query-box p,
.citation p {
  margin: 7px 0 0;
  color: var(--color-ink);
  line-height: 1.65;
}

.citation {
  padding: 12px;
}

.citation-head strong {
  overflow-wrap: anywhere;
  font-size: 13px;
}

.citation-head span {
  color: var(--color-brand-dark);
  font-size: 13px;
  font-weight: 900;
}

.citation-head span.low {
  color: var(--color-accent);
}

.citation small {
  display: block;
  margin-top: 6px;
  color: var(--color-muted);
}

.citation b {
  margin-left: 6px;
  color: var(--color-accent);
}

.citation p {
  max-height: 178px;
  overflow: auto;
  font-size: 13px;
}

.empty {
  margin: 0;
  color: var(--color-muted);
  font-size: 13px;
  line-height: 1.6;
}
</style>
