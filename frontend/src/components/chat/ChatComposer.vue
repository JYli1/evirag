<template>
  <form class="composer" @submit.prevent="send">
    <textarea
      v-model.trim="content"
      rows="3"
      :disabled="disabled"
      placeholder="向当前知识库提问，也可以直接自由提问..."
      @keydown.enter.exact.prevent="send"
    />
    <button type="submit" :disabled="disabled || !content">
      {{ sending ? '生成中' : '发送' }}
    </button>
  </form>
</template>

<script setup lang="ts">
import { ref } from 'vue';

defineProps<{
  disabled: boolean;
  sending: boolean;
}>();

const emit = defineEmits<{
  send: [content: string];
}>();

const content = ref('');

function send() {
  if (!content.value) return;
  emit('send', content.value);
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
  background: rgba(255, 255, 255, 0.84);
}

textarea {
  width: 100%;
  min-height: 54px;
  max-height: 130px;
  resize: none;
  padding: 13px 14px;
  border: 1px solid var(--color-line);
  border-radius: var(--radius-lg);
  background: var(--color-panel);
  color: var(--color-ink);
  line-height: 1.6;
  outline: none;
}

textarea:focus {
  border-color: var(--color-brand);
  box-shadow: 0 0 0 4px rgba(37, 90, 143, 0.1);
}

button {
  align-self: end;
  width: 48px;
  height: 48px;
  border: 0;
  border-radius: var(--radius-lg);
  background: linear-gradient(135deg, var(--color-brand-dark), var(--color-brand));
  color: #ffffff;
  font-size: 0;
  font-weight: 900;
  box-shadow: var(--shadow-glow);
}

button:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: var(--shadow-glow-strong);
}

button::before {
  content: ">";
  font-size: 20px;
  line-height: 1;
}

button:disabled,
textarea:disabled {
  opacity: 0.62;
}

@media (max-width: 640px) {
  .composer {
    grid-template-columns: 1fr;
  }

  button {
    width: 100%;
    font-size: 14px;
  }

  button::before {
    content: "";
  }
}
</style>
