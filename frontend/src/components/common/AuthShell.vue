<template>
  <main class="auth-shell">
    <section class="brand-panel">
      <div class="particle-field" aria-hidden="true">
        <i v-for="index in 18" :key="index"></i>
      </div>
      <div class="brand-top">
        <EviRagLogo />
        <span>Evidence Intelligence Console</span>
      </div>
      <div class="brand-copy">
        <p class="kicker">证据增强文档问答系统</p>
        <h1>可检索、可引用、可管理的文档问答工作台</h1>
        <p>
          上传 PDF、DOC、DOCX、TXT、Markdown 后，EviRAG 会完成解析、切片、向量检索和引用展示，
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
  grid-template-columns: minmax(420px, 1.05fr) minmax(420px, 0.95fr);
  background:
    linear-gradient(rgba(18, 149, 190, 0.06) 1px, transparent 1px),
    linear-gradient(90deg, rgba(18, 149, 190, 0.05) 1px, transparent 1px),
    radial-gradient(circle at 12% 12%, rgba(18, 149, 190, 0.18), transparent 28%),
    linear-gradient(180deg, #fbfeff 0%, #edf8fc 100%);
  background-size: 38px 38px, 38px 38px, auto, auto;
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
    linear-gradient(120deg, rgba(18, 149, 190, 0.08), transparent 44%),
    radial-gradient(circle at 18% 16%, rgba(183, 232, 242, 0.5), transparent 28%),
    linear-gradient(135deg, rgba(255, 255, 255, 0.92), rgba(224, 247, 252, 0.82));
  border-right: 1px solid var(--color-line);
}

.brand-panel::before,
.brand-panel::after {
  content: "";
  position: absolute;
  pointer-events: none;
}

.brand-panel::before {
  inset: 108px 42px auto auto;
  width: 220px;
  height: 220px;
  border: 1px solid var(--color-strong-line);
  border-radius: 34px;
  transform: rotate(18deg);
}

.brand-panel::after {
  inset: auto -78px 76px auto;
  width: 290px;
  height: 290px;
  border: 1px solid rgba(18, 149, 190, 0.22);
  border-radius: 42px;
  transform: rotate(-12deg);
}

.brand-panel .particle-field {
  position: absolute;
  inset: 0;
  pointer-events: none;
  z-index: 0;
}

.particle-field i {
  position: absolute;
  width: 5px;
  height: 5px;
  border-radius: 999px;
  background: var(--color-brand);
  box-shadow: 0 0 18px rgba(18, 149, 190, 0.34);
  opacity: 0.34;
}

.particle-field i:nth-child(1) { left: 9%; top: 18%; animation-delay: -1s; }
.particle-field i:nth-child(2) { left: 24%; top: 12%; animation-delay: -3s; }
.particle-field i:nth-child(3) { left: 42%; top: 20%; animation-delay: -5s; }
.particle-field i:nth-child(4) { left: 72%; top: 18%; animation-delay: -2s; }
.particle-field i:nth-child(5) { left: 84%; top: 34%; animation-delay: -4s; }
.particle-field i:nth-child(6) { left: 14%; top: 46%; animation-delay: -6s; }
.particle-field i:nth-child(7) { left: 36%; top: 54%; animation-delay: -2.4s; }
.particle-field i:nth-child(8) { left: 58%; top: 48%; animation-delay: -5.4s; }
.particle-field i:nth-child(9) { left: 78%; top: 58%; animation-delay: -1.8s; }
.particle-field i:nth-child(10) { left: 18%; top: 72%; animation-delay: -3.8s; }
.particle-field i:nth-child(11) { left: 46%; top: 76%; animation-delay: -6.8s; }
.particle-field i:nth-child(12) { left: 68%; top: 80%; animation-delay: -1.2s; }
.particle-field i:nth-child(13) { left: 88%; top: 76%; animation-delay: -4.2s; }
.particle-field i:nth-child(14) { left: 31%; top: 32%; animation-delay: -7.2s; }
.particle-field i:nth-child(15) { left: 52%; top: 9%; animation-delay: -2.8s; }
.particle-field i:nth-child(16) { left: 63%; top: 66%; animation-delay: -5.8s; }
.particle-field i:nth-child(17) { left: 7%; top: 84%; animation-delay: -3.2s; }
.particle-field i:nth-child(18) { left: 91%; top: 13%; animation-delay: -6.2s; }

.brand-top {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.brand-top span {
  padding: 8px 12px;
  border: 1px solid var(--color-strong-line);
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
  max-width: 10.5em;
  color: var(--color-ink);
  font-size: clamp(42px, 5vw, 72px);
  line-height: 1.01;
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
  border: 1px solid var(--color-line);
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.72);
  box-shadow: var(--shadow-sm);
  backdrop-filter: blur(16px);
}

.pipeline-map span {
  display: grid;
  place-items: center;
  min-height: 30px;
  padding: 0 10px;
  border-radius: 999px;
  background: #e8f8fc;
  color: var(--color-brand-dark);
  font-size: 12px;
  font-weight: 900;
}

.pipeline-map i {
  height: 1px;
  background: linear-gradient(90deg, var(--color-brand-light), transparent);
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
    radial-gradient(circle at 12% 12%, rgba(18, 149, 190, 0.08), transparent 28%),
    rgba(251, 254, 255, 0.86);
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
