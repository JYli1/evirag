<template>
  <main class="workbench-placeholder">
    <EviRagLogo />
    <section>
      <p>工作台准备中</p>
      <h1>知识库、文档和对话界面将在这里展开</h1>
      <span>当前已完成品牌、认证和接口基础；三栏 RAG 工作台会在下一阶段接入。</span>
    </section>
  </main>
</template>

<script setup lang="ts">
import EviRagLogo from '@/assets/logo/EviRagLogo.vue';
</script>

<style scoped>
.workbench-placeholder {
  min-height: 100vh;
  display: grid;
  place-items: center;
  gap: 22px;
  padding: 28px;
  text-align: center;
  background: var(--color-soft);
}

section {
  max-width: 520px;
}

p {
  margin: 0 0 10px;
  color: var(--color-brand);
  font-size: 13px;
  font-weight: 900;
}

h1 {
  margin: 0;
  color: var(--color-ink);
  font-size: 30px;
  line-height: 1.32;
  letter-spacing: 0;
}

span {
  display: block;
  margin-top: 16px;
  color: var(--color-muted);
  line-height: 1.7;
}
</style>
