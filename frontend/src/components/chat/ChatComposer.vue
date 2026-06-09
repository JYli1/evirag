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
  padding: 16px;
  border-top: 1px solid var(--color-line);
  background: rgba(255, 255, 255, 0.96);
}

textarea {
  width: 100%;
  min-height: 54px;
  max-height: 130px;
  resize: none;
  padding: 13px 14px;
  border: 1px solid var(--color-line);
  border-radius: 16px;
  background: #f8fbff;
  color: var(--color-ink);
  line-height: 1.6;
  outline: none;
}

textarea:focus {
  border-color: var(--color-brand);
  box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.12);
}

button {
  align-self: end;
  width: 48px;
  height: 48px;
  border: 0;
  border-radius: 16px;
  background: var(--color-brand-dark);
  color: #ffffff;
  font-size: 0;
  font-weight: 900;
  box-shadow: 0 12px 26px rgba(29, 78, 216, 0.2);
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
