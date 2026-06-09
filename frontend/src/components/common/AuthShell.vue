<template>
  <main class="auth-shell">
    <section class="brand-panel">
      <div class="brand-top">
        <EviRagLogo />
        <span>RAG Console</span>
      </div>
      <div class="brand-copy">
        <p class="kicker">证据增强文档问答系统</p>
        <h1>可检索、可引用、可管理的文档问答工作台</h1>
        <p>
          上传 PDF、DOCX、TXT、Markdown 后，EviRAG 会完成解析、切片、向量检索和引用展示，
          让每次回答都有依据可查。
        </p>
      </div>
      <div class="pipeline-map" aria-label="RAG 工作流">
        <span>上传</span>
        <i></i>
        <span>切片</span>
        <i></i>
        <span>检索</span>
        <i></i>
        <span>回答</span>
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
  grid-template-columns: minmax(360px, 0.9fr) minmax(420px, 1.1fr);
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
    radial-gradient(circle at 18% 16%, rgba(37, 90, 143, 0.1), transparent 28%),
    linear-gradient(180deg, #ffffff 0%, #eef2f7 100%);
  border-right: 1px solid var(--color-line);
}

.brand-panel::before,
.brand-panel::after {
  content: "";
  position: absolute;
  pointer-events: none;
}

.brand-panel::before {
  inset: 112px 42px auto auto;
  width: 190px;
  height: 190px;
  border: 1px solid rgba(37, 99, 235, 0.16);
  border-radius: 18px;
  transform: rotate(12deg);
}

.brand-panel::after {
  inset: auto -72px 78px auto;
  width: 260px;
  height: 260px;
  border: 1px solid rgba(15, 118, 110, 0.18);
  border-radius: 28px;
  transform: rotate(-12deg);
}

.brand-top {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.brand-top span {
  padding: 7px 10px;
  border: 1px solid rgba(37, 90, 143, 0.16);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.76);
  color: var(--color-brand-dark);
  font-size: 12px;
  font-weight: 900;
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
  font-size: 12px;
  font-weight: 900;
  letter-spacing: 0;
}

h1 {
  margin: 0;
  max-width: 12em;
  color: var(--color-ink);
  font-size: clamp(34px, 5vw, 64px);
  line-height: 1.08;
  letter-spacing: 0;
}

.brand-copy p:last-child {
  margin: 22px 0 0;
  max-width: 520px;
  color: var(--color-muted);
  font-size: 15px;
  line-height: 1.82;
}

.pipeline-map {
  position: relative;
  z-index: 1;
  display: grid;
  grid-template-columns: auto 1fr auto 1fr auto 1fr auto;
  align-items: center;
  gap: 10px;
  width: min(100%, 520px);
  padding: 12px;
  border: 1px solid rgba(198, 211, 225, 0.72);
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.72);
  box-shadow: var(--shadow-sm);
}

.pipeline-map span {
  display: grid;
  place-items: center;
  min-height: 30px;
  padding: 0 10px;
  border-radius: 999px;
  background: var(--color-panel-muted);
  color: var(--color-brand-dark);
  font-size: 12px;
  font-weight: 900;
}

.pipeline-map i {
  height: 1px;
  background: linear-gradient(90deg, rgba(37, 90, 143, 0.28), rgba(22, 116, 91, 0.16));
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
  border: 1px solid rgba(37, 90, 143, 0.16);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.72);
  color: var(--color-brand-dark);
  font-size: 13px;
  font-weight: 900;
}

.form-panel {
  display: grid;
  place-items: center;
  min-height: 100vh;
  padding: 36px;
  background:
    radial-gradient(circle at 12% 12%, rgba(37, 90, 143, 0.06), transparent 28%),
    #f7f9fc;
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

  .pipeline-map {
    margin-bottom: 20px;
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
