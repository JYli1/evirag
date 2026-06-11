<template>
  <form class="composer" @submit.prevent="send">
    <div class="input-shell">
      <textarea
        v-model.trim="content"
        rows="3"
        :disabled="disabled"
        placeholder="向当前知识库提问，也可以直接自由提问..."
        @keydown.enter.exact.prevent="send"
      />
      <button
        class="web-search-toggle"
        type="button"
        :class="{ active: webSearchEnabled }"
        :aria-pressed="webSearchEnabled"
        :disabled="disabled"
        @click="webSearchEnabled = !webSearchEnabled"
      >
        <span></span>
        {{ webSearchEnabled ? '搜索已开' : '开启搜索' }}
      </button>
    </div>
    <button class="send-button" type="submit" :disabled="disabled || !content">
      {{ sending ? '生成中' : '发送' }}
    </button>
  </form>
</template>

<script setup lang="ts">
import { ref } from 'vue';

defineProps<{
  // true 时禁用输入框，常见场景是没有会话或正在生成回答。
  disabled: boolean;
  // 控制按钮文案显示“生成中”。
  sending: boolean;
}>();

const emit = defineEmits<{
  // 子组件只把用户输入和本次搜索开关抛给父组件，真正调用后端由 WorkbenchPage 完成。
  send: [payload: { content: string; webSearchEnabled: boolean }];
}>();

// 当前输入框内容。
const content = ref('');
const webSearchEnabled = ref(false);

function send() {
  // 空内容不触发发送，避免后端收到无意义请求。
  if (!content.value) return;
  emit('send', { content: content.value, webSearchEnabled: webSearchEnabled.value });
  // 发送后立即清空输入框，流式回答由消息列表展示。
  content.value = '';
}
</script>

<style scoped>
.composer {
  display: grid;
  grid-template-columns: 1fr 48px;
  gap: 10px;
  padding: 16px 18px;
  border-top: 1px solid var(--color-line);
  background:
    linear-gradient(90deg, rgba(18, 149, 190, 0.08), transparent 44%),
    rgba(255, 255, 255, 0.84);
}

.input-shell {
  position: relative;
  min-width: 0;
}

textarea {
  width: 100%;
  min-height: 54px;
  max-height: 130px;
  resize: none;
  padding: 13px 14px 46px;
  border: 1px solid var(--color-line);
  border-radius: var(--radius-lg);
  background: rgba(255, 255, 255, 0.88);
  color: var(--color-ink);
  line-height: 1.6;
  outline: none;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.82);
}

textarea:focus {
  border-color: var(--color-brand);
  box-shadow: 0 0 0 4px var(--color-cyan-halo);
}

.send-button {
  align-self: end;
  width: 48px;
  height: 48px;
  border: 0;
  border-radius: 16px;
  background: linear-gradient(135deg, var(--color-brand-dark), var(--color-brand));
  color: #ffffff;
  font-size: 0;
  font-weight: 900;
  box-shadow: var(--shadow-glow);
}

.send-button:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: var(--shadow-glow-strong);
}

.send-button::before {
  content: ">";
  font-size: 20px;
  line-height: 1;
}

.web-search-toggle {
  position: absolute;
  left: 10px;
  bottom: 10px;
  display: inline-flex;
  align-items: center;
  gap: 7px;
  height: 26px;
  padding: 0 10px;
  border: 1px solid rgba(42, 118, 148, 0.18);
  border-radius: 999px;
  background: rgba(248, 253, 255, 0.92);
  color: var(--color-muted);
  font-size: 12px;
  font-weight: 800;
  line-height: 1;
  box-shadow: 0 8px 18px rgba(22, 63, 107, 0.08);
}

.web-search-toggle span {
  width: 7px;
  height: 7px;
  border-radius: 999px;
  background: var(--color-subtle);
  box-shadow: 0 0 0 4px rgba(137, 150, 166, 0.1);
}

.web-search-toggle.active {
  border-color: rgba(18, 149, 190, 0.35);
  background: #e8f8fc;
  color: var(--color-brand-dark);
}

.web-search-toggle.active span {
  background: var(--color-cyan);
  box-shadow: 0 0 0 4px rgba(18, 149, 190, 0.14);
}

.web-search-toggle:hover:not(:disabled) {
  transform: translateY(-1px);
  border-color: rgba(18, 149, 190, 0.45);
}

.send-button:disabled,
.web-search-toggle:disabled,
textarea:disabled {
  opacity: 0.62;
}

@media (max-width: 640px) {
  .composer {
    grid-template-columns: 1fr;
  }

  .send-button {
    width: 100%;
    font-size: 14px;
  }

  .send-button::before {
    content: "";
  }
}
</style>
