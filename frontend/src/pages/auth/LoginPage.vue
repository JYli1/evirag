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
@import './auth-form.css';

.form-links {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  color: var(--color-brand-dark);
  font-size: 14px;
  font-weight: 900;
}
</style>
