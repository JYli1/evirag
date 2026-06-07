<template>
  <AuthShell>
    <form class="auth-card" @submit.prevent="submit">
      <div class="form-head">
        <p>找回密码</p>
        <h2>通过邮箱验证码重置登录密码</h2>
      </div>

      <label>
        <span>邮箱</span>
        <input v-model.trim="email" type="email" autocomplete="email" placeholder="name@example.com" required />
      </label>

      <div class="code-row">
        <label>
          <span>验证码</span>
          <input v-model.trim="code" inputmode="numeric" maxlength="6" placeholder="6 位数字" required />
        </label>
        <button class="secondary-action" type="button" :disabled="codeLoading || countdown > 0" @click="sendCode">
          {{ codeButtonText }}
        </button>
      </div>

      <label>
        <span>新密码</span>
        <input v-model="newPassword" type="password" autocomplete="new-password" placeholder="至少 8 位" required />
      </label>

      <p v-if="message" class="notice">{{ message }}</p>
      <p v-if="error" class="error-detail">{{ error }}</p>

      <button class="primary-action" type="submit" :disabled="loading">
        {{ loading ? '提交中...' : '重置密码' }}
      </button>

      <RouterLink class="back-link" to="/login">返回登录</RouterLink>
    </form>
  </AuthShell>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue';
import { useRouter } from 'vue-router';

import { apiErrorMessage } from '@/api/http';
import { resetPassword, sendPasswordResetCode } from '@/api/auth';
import AuthShell from '@/components/common/AuthShell.vue';

const router = useRouter();

const email = ref('');
const code = ref('');
const newPassword = ref('');
const loading = ref(false);
const codeLoading = ref(false);
const countdown = ref(0);
const error = ref('');
const message = ref('');
let timer: number | undefined;

const codeButtonText = computed(() => {
  if (codeLoading.value) return '发送中...';
  if (countdown.value > 0) return `${countdown.value}s`;
  return '发送验证码';
});

async function sendCode() {
  codeLoading.value = true;
  error.value = '';
  message.value = '';
  try {
    await sendPasswordResetCode(email.value);
    message.value = '验证码已发送，请查看邮箱。';
    startCountdown();
  } catch (err) {
    error.value = apiErrorMessage(err);
  } finally {
    codeLoading.value = false;
  }
}

async function submit() {
  loading.value = true;
  error.value = '';
  try {
    await resetPassword({ email: email.value, newPassword: newPassword.value, code: code.value });
    message.value = '密码已重置，请使用新密码登录。';
    await router.push('/login');
  } catch (err) {
    error.value = apiErrorMessage(err);
  } finally {
    loading.value = false;
  }
}

function startCountdown() {
  countdown.value = 60;
  window.clearInterval(timer);
  timer = window.setInterval(() => {
    countdown.value -= 1;
    if (countdown.value <= 0) {
      window.clearInterval(timer);
    }
  }, 1000);
}

onBeforeUnmount(() => window.clearInterval(timer));
</script>

<style scoped>
@import './auth-form.css';

.code-row {
  display: grid;
  grid-template-columns: 1fr 120px;
  gap: 10px;
  align-items: end;
}

.secondary-action {
  min-height: 46px;
  border: 1px solid var(--color-strong-line);
  border-radius: var(--radius-sm);
  background: #f7faf7;
  color: var(--color-brand-dark);
  font-weight: 900;
}

.notice {
  margin: 0;
  padding: 10px 12px;
  border-left: 3px solid var(--color-info);
  background: rgba(72, 111, 141, 0.08);
  color: #2f536e;
  font-size: 13px;
  line-height: 1.6;
}

@media (max-width: 520px) {
  .code-row {
    grid-template-columns: 1fr;
  }
}
</style>
