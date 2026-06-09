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
  citations: RagCitation[];
  query: string;
}>();
</script>

<style scoped>
.evidence-panel {
  height: 100vh;
  display: grid;
  grid-auto-rows: max-content;
  gap: 16px;
  padding: 24px 20px;
  border-left: 1px solid var(--color-line);
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.98) 0%, rgba(248, 250, 252, 0.95) 100%);
  overflow-y: auto;
  box-shadow: -4px 0 24px rgba(0, 0, 0, 0.04);
  backdrop-filter: blur(12px);
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
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.evidence-head strong {
  display: block;
  margin-top: 6px;
  color: var(--color-ink);
  font-size: 24px;
  font-weight: 700;
  background: linear-gradient(135deg, var(--color-ink) 0%, var(--color-brand) 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.evidence-head span {
  padding: 6px 12px;
  border: 1px solid var(--color-line);
  border-radius: 999px;
  background: linear-gradient(135deg, #f0f9ff 0%, #e0f2fe 100%);
  color: var(--color-brand-dark);
  font-size: 11px;
  font-weight: 700;
  box-shadow: var(--shadow-xs);
}

.query-box,
.citation {
  border: 1px solid var(--color-line);
  border-radius: var(--radius-xl);
  background: linear-gradient(135deg, #ffffff 0%, #f8fafc 100%);
  box-shadow: var(--shadow-md);
  transition: all var(--transition-base);
}

.query-box {
  padding: 14px 16px;
  border-left: 3px solid var(--color-brand);
  background: linear-gradient(135deg, #eff6ff 0%, #dbeafe 100%);
}

.query-box span {
  color: var(--color-brand-dark);
  font-size: 12px;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.5px;
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
  border-color: var(--color-brand-light);
  box-shadow: var(--shadow-lg);
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
  background: linear-gradient(135deg, #dcfce7 0%, #bbf7d0 100%);
  color: #065f46;
  font-size: 13px;
  font-weight: 700;
  box-shadow: var(--shadow-xs);
}

.citation-head span.low {
  background: linear-gradient(135deg, #fed7aa 0%, #fdba74 100%);
  color: #92400e;
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
  background: linear-gradient(135deg, #fed7aa 0%, #fdba74 100%);
  color: #92400e;
  font-weight: 700;
  font-size: 11px;
}

.citation p {
  max-height: 200px;
  overflow: auto;
  font-size: 13px;
  padding: 12px;
  border-radius: var(--radius-md);
  background: linear-gradient(135deg, #f8fafc 0%, #f1f5f9 100%);
  border: 1px solid var(--color-line);
}

.empty {
  margin: 0;
  padding: 20px;
  color: var(--color-muted);
  font-size: 13px;
  line-height: 1.6;
  text-align: center;
  background: linear-gradient(135deg, #f8fafc 0%, #ffffff 100%);
  border-radius: var(--radius-lg);
  border: 1px dashed var(--color-line);
}
</style>
