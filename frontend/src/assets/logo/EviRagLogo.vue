<template>
  <div class="evirag-logo" :class="{ compact }" aria-label="EviRAG">
    <img class="mark" :src="logoUrl" alt="" aria-hidden="true" />
  </div>
</template>

<script setup lang="ts">
import logoUrl from './evirag-logo.png';

defineProps<{
  compact?: boolean;
}>();
</script>

<style scoped>
.evirag-logo {
  display: inline-flex;
  align-items: center;
  flex: 0 0 auto;
  line-height: 0;
}

.mark {
  display: block;
  width: 132px;
  height: auto;
  flex: 0 0 auto;
  filter: drop-shadow(0 10px 18px rgba(37, 90, 143, 0.12));
}

.compact .mark {
  width: 68px;
}
</style>
