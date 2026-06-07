<template>
  <main class="app-shell">
    <section class="intro">
      <p class="eyebrow">证据增强文档问答系统</p>
      <h1>EviRAG</h1>
      <p class="summary">当前为基础脚手架，后续将接入认证、知识库、文档索引和 RAG 问答能力。</p>
    </section>
  </main>
</template>

<script setup lang="ts">
// 根组件暂时只提供中文最小入口，避免在 Task 1 提前引入业务页面和路由。
</script>

<style scoped>
.app-shell {
  min-height: 100vh;
  display: grid;
  place-items: center;
  padding: 32px;
  background: #f6f8f7;
  color: #13201b;
  font-family:
    Inter,
    "Microsoft YaHei",
    "PingFang SC",
    sans-serif;
}

.intro {
  max-width: 560px;
  text-align: center;
}

.eyebrow {
  margin: 0 0 12px;
  color: #2f6f56;
  font-size: 15px;
  font-weight: 700;
}

h1 {
  margin: 0;
  font-size: 48px;
  line-height: 1.1;
}

.summary {
  margin: 18px 0 0;
  color: #52635b;
  font-size: 17px;
  line-height: 1.7;
}
</style>
