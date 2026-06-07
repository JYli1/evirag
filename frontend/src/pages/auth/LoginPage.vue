<template>
  <AuthShell>
    <form class="auth-card" @submit.prevent="submit">
      <div class="form-head">
        <p>登录 EviRAG</p>
        <h2>继续进入文档问答工作台</h2>
      </div>

      <label>
        <span>邮箱</span>
        <input v-model.trim="email" type="email" autocomplete="email" placeholder="name@example.com" required />
      </label>

      <label>
        <span>密码</span>
        <input v-model="password" type="password" autocomplete="current-password" placeholder="输入登录密码" required />
      </label>

      <p v-if="error" class="error-detail">{{ error }}</p>

      <button class="primary-action" type="submit" :disabled="loading">
        {{ loading ? '登录中...' : '登录' }}
      </button>

      <div class="form-links">
        <RouterLink to="/register">注册账号</RouterLink>
        <RouterLink to="/reset-password">忘记密码</RouterLink>
      </div>
    </form>
  </AuthShell>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { login } from '@/api/auth';
import { apiErrorMessage } from '@/api/http';
import AuthShell from '@/components/common/AuthShell.vue';
import { useAuthStore } from '@/stores/authStore';

const router = useRouter();
const route = useRoute();
const authStore = useAuthStore();

const email = ref('');
const password = ref('');
const loading = ref(false);
const error = ref('');

async function submit() {
  loading.value = true;
  error.value = '';
  try {
    const response = await login({ email: email.value, password: password.value });
    authStore.setAuth(response.data.data);
    await router.push((route.query.redirect as string) || '/workbench');
  } catch (err) {
    error.value = apiErrorMessage(err);
  } finally {
    loading.value = false;
  }
}
</script>

<style scoped>
.auth-card {
  width: min(100%, 430px);
  display: grid;
  gap: 18px;
  padding: 30px;
  border: 1px solid var(--color-line);
  border-radius: var(--radius-lg);
  background: rgba(255, 255, 255, 0.9);
  box-shadow: var(--shadow-soft);
}

.form-head {
  display: grid;
  gap: 6px;
  margin-bottom: 6px;
}

.form-head p {
  margin: 0;
  color: var(--color-brand);
  font-size: 13px;
  font-weight: 800;
}

.form-head h2 {
  margin: 0;
  color: var(--color-ink);
  font-size: 28px;
  line-height: 1.25;
  letter-spacing: 0;
}

label {
  display: grid;
  gap: 8px;
  color: var(--color-ink);
  font-size: 14px;
  font-weight: 800;
}

input {
  width: 100%;
  min-height: 46px;
  padding: 0 13px;
  border: 1px solid var(--color-line);
  border-radius: var(--radius-sm);
  background: #fbfdf9;
  color: var(--color-ink);
  box-shadow: var(--shadow-field);
  outline: none;
}

input:focus {
  border-color: var(--color-brand);
  box-shadow: 0 0 0 3px rgba(31, 122, 87, 0.14);
}

.primary-action {
  min-height: 48px;
  border: 0;
  border-radius: var(--radius-sm);
  background: var(--color-brand-dark);
  color: #ffffff;
  font-weight: 900;
}

.primary-action:disabled {
  opacity: 0.68;
}

.error-detail {
  margin: 0;
  padding: 10px 12px;
  border-left: 3px solid var(--color-danger);
  background: rgba(178, 74, 63, 0.08);
  color: #7b342d;
  font-size: 13px;
  line-height: 1.6;
}

.form-links {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  color: var(--color-brand-dark);
  font-size: 14px;
  font-weight: 800;
}
</style>
