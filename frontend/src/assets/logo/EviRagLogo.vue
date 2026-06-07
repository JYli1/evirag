<template>
  <div class="evirag-logo" :class="{ compact }" aria-label="EviRAG">
    <svg class="mark" viewBox="0 0 64 64" role="img" aria-hidden="true">
      <path class="sheet" d="M14 8h27l9 9v39H14z" />
      <path class="fold" d="M41 8v10h10" />
      <path class="e-line top" d="M23 24h22" />
      <path class="e-line mid" d="M23 33h17" />
      <path class="e-line bot" d="M23 42h22" />
      <path class="quote" d="M30 20c-5 2-8 6-8 11h7c0-4 1-7 4-10z" />
      <path class="search" d="M45 43l8 8" />
      <circle class="search-circle" cx="40" cy="38" r="9" />
    </svg>
    <span v-if="!compact" class="word">
      <strong>EviRAG</strong>
      <small>证据增强文档问答</small>
    </span>
  </div>
</template>

<script setup lang="ts">
defineProps<{
  compact?: boolean;
}>();
</script>

<style scoped>
.evirag-logo {
  display: inline-flex;
  align-items: center;
  gap: 12px;
  color: var(--color-ink);
}

.mark {
  width: 48px;
  height: 48px;
  flex: 0 0 auto;
}

.compact .mark {
  width: 34px;
  height: 34px;
}

.sheet {
  fill: #fbfdf9;
  stroke: #173f31;
  stroke-width: 2.2;
}

.fold {
  fill: none;
  stroke: #173f31;
  stroke-width: 2.2;
  stroke-linejoin: round;
}

.e-line {
  fill: none;
  stroke: #1f7a57;
  stroke-width: 3.6;
  stroke-linecap: round;
}

.quote {
  fill: rgba(181, 138, 42, 0.95);
}

.search-circle,
.search {
  fill: none;
  stroke: #0d4f39;
  stroke-width: 3.2;
  stroke-linecap: round;
}

.word {
  display: grid;
  gap: 3px;
  line-height: 1;
}

.word strong {
  font-size: 24px;
  letter-spacing: 0;
}

.word small {
  color: var(--color-muted);
  font-size: 12px;
  font-weight: 700;
}
</style>
