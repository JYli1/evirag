<template>
  <main class="auth-shell">
    <section class="brand-panel">
      <EviRagLogo />
      <div class="brand-copy">
        <p class="kicker">证据增强文档问答系统</p>
        <h1>把课程资料、项目文档和知识库变成可追溯的问答工作台</h1>
        <p>
          上传 PDF、DOCX、TXT、Markdown 后，EviRAG 会完成解析、切片、向量检索和引用展示，
          让每次回答都有依据可查。
        </p>
      </div>
      <div class="brand-metrics" aria-label="系统能力">
        <span>多知识库</span>
        <span>引用证据</span>
        <span>SSE 流式回答</span>
      </div>
    </section>

    <section class="form-panel">
      <slot />
    </section>
  </main>
</template>

<script setup lang="ts">
import EviRagLogo from '@/assets/logo/EviRagLogo.vue';
</script>

<style scoped>
.auth-shell {
  min-height: 100vh;
  display: grid;
  grid-template-columns: minmax(360px, 0.92fr) minmax(420px, 1.08fr);
  background: var(--color-soft);
}

.brand-panel {
  position: relative;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  min-height: 100vh;
  padding: 42px;
  overflow: hidden;
  background:
    linear-gradient(140deg, rgba(31, 122, 87, 0.14), transparent 42%),
    linear-gradient(180deg, #fbfdf9 0%, #edf5ef 100%);
  border-right: 1px solid var(--color-line);
}

.brand-panel::after {
  content: "";
  position: absolute;
  inset: auto -80px 70px auto;
  width: 280px;
  height: 280px;
  border: 1px solid rgba(31, 122, 87, 0.28);
  transform: rotate(10deg);
}

.brand-copy {
  position: relative;
  max-width: 560px;
  margin: 80px 0;
  z-index: 1;
}

.kicker {
  margin: 0 0 14px;
  color: var(--color-brand-dark);
  font-size: 13px;
  font-weight: 800;
}

h1 {
  margin: 0;
  max-width: 13em;
  color: var(--color-ink);
  font-size: clamp(34px, 5vw, 64px);
  line-height: 1.08;
  letter-spacing: 0;
}

.brand-copy p:last-child {
  margin: 22px 0 0;
  max-width: 520px;
  color: var(--color-muted);
  font-size: 16px;
  line-height: 1.85;
}

.brand-metrics {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  position: relative;
  z-index: 1;
}

.brand-metrics span {
  padding: 8px 11px;
  border: 1px solid rgba(31, 122, 87, 0.26);
  border-radius: var(--radius-sm);
  background: rgba(255, 255, 255, 0.62);
  color: var(--color-brand-dark);
  font-size: 13px;
  font-weight: 700;
}

.form-panel {
  display: grid;
  place-items: center;
  min-height: 100vh;
  padding: 36px;
}

@media (max-width: 900px) {
  .auth-shell {
    grid-template-columns: 1fr;
  }

  .brand-panel {
    min-height: auto;
    padding: 26px;
    border-right: 0;
    border-bottom: 1px solid var(--color-line);
  }

  .brand-copy {
    margin: 36px 0 28px;
  }

  h1 {
    font-size: 34px;
  }

  .form-panel {
    min-height: auto;
    padding: 24px 16px 36px;
  }
}
</style>
