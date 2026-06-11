<template>
  <aside class="evidence-panel">
    <header class="evidence-head">
      <div>
        <p>引用证据</p>
        <strong>{{ citations.length }} 条</strong>
      </div>
      <span>Cosine</span>
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
  // 当前回答召回的 Top-K 证据片段。
  citations: RagCitation[];
  // 改写后的检索问题。
  query: string;
}>();
</script>

<style scoped>
.evidence-panel {
  height: calc(100vh - 28px);
  display: grid;
  grid-auto-rows: max-content;
  gap: 16px;
  padding: 20px;
  border: 1px solid var(--color-line);
  border-radius: var(--radius-xl);
  background: var(--color-crystal);
  overflow-y: auto;
  box-shadow: var(--shadow-card);
  backdrop-filter: blur(20px);
}

.evidence-head,
.citation-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.evidence-head p {
  margin: 0;
  color: var(--color-brand);
  font-size: 12px;
  font-weight: 800;
}

.evidence-head strong {
  display: block;
  margin-top: 6px;
  color: var(--color-ink);
  font-size: 22px;
  font-weight: 700;
}

.evidence-head span {
  padding: 6px 12px;
  border: 1px solid var(--color-line);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.68);
  color: var(--color-brand-dark);
  font-size: 11px;
  font-weight: 800;
}

.query-box,
.citation {
  border: 1px solid var(--color-line);
  border-radius: var(--radius-xl);
  background: rgba(255, 255, 255, 0.72);
  box-shadow: var(--shadow-sm);
}

.query-box {
  padding: 14px 16px;
  border-left: 4px solid var(--color-brand);
  background: linear-gradient(135deg, rgba(255, 255, 255, 0.84), rgba(223, 245, 250, 0.76));
}

.query-box span {
  color: var(--color-brand-dark);
  font-size: 12px;
  font-weight: 800;
}

.query-box p,
.citation p {
  margin: 8px 0 0;
  color: var(--color-ink);
  line-height: 1.7;
  font-size: 14px;
}

.citation {
  padding: 14px 16px;
}

.citation:hover {
  transform: translateY(-2px);
  border-color: var(--color-strong-line);
  box-shadow: var(--shadow-card);
}

.citation-head strong {
  overflow-wrap: anywhere;
  font-size: 13px;
  font-weight: 700;
  color: var(--color-ink);
}

.citation-head span {
  padding: 4px 10px;
  border-radius: 999px;
  background: rgba(22, 116, 91, 0.1);
  color: var(--color-success);
  font-size: 13px;
  font-weight: 800;
}

.citation-head span.low {
  background: rgba(163, 106, 31, 0.12);
  color: var(--color-accent);
}

.citation small {
  display: block;
  margin-top: 8px;
  color: var(--color-muted);
  font-size: 12px;
}

.citation b {
  margin-left: 8px;
  padding: 2px 8px;
  border-radius: 999px;
  background: rgba(163, 106, 31, 0.12);
  color: var(--color-accent);
  font-weight: 700;
  font-size: 11px;
}

.citation p {
  max-height: 200px;
  overflow: auto;
  font-size: 13px;
  padding: 12px;
  border-radius: var(--radius-md);
  background: rgba(255, 255, 255, 0.62);
  border: 1px solid var(--color-line);
}

.empty {
  margin: 0;
  padding: 20px;
  color: var(--color-muted);
  font-size: 13px;
  line-height: 1.6;
  text-align: center;
  background: rgba(255, 255, 255, 0.58);
  border-radius: var(--radius-lg);
  border: 1px dashed var(--color-line);
}
</style>
