<template>
  <form class="composer" @submit.prevent="send">
    <textarea
      v-model.trim="content"
      rows="3"
      :disabled="disabled"
      placeholder="向当前知识库提问..."
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
  grid-template-columns: 1fr 82px;
  gap: 10px;
  padding: 14px;
  border-top: 1px solid var(--color-line);
  background: rgba(251, 253, 249, 0.94);
}

textarea {
  width: 100%;
  min-height: 72px;
  resize: none;
  padding: 12px;
  border: 1px solid var(--color-line);
  border-radius: var(--radius-md);
  background: #ffffff;
  color: var(--color-ink);
  line-height: 1.6;
  outline: none;
}

textarea:focus {
  border-color: var(--color-brand);
  box-shadow: 0 0 0 3px rgba(31, 122, 87, 0.12);
}

button {
  align-self: end;
  height: 44px;
  border: 0;
  border-radius: var(--radius-md);
  background: var(--color-brand-dark);
  color: #ffffff;
  font-weight: 900;
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
  }
}
</style>
