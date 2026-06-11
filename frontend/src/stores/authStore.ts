import { defineStore } from 'pinia';
import { computed, ref } from 'vue';

import type { AuthTokenResponse, AuthUser } from '@/api/auth';
import { tokenStorage } from '@/api/http';

const USER_KEY = 'evirag_user';

function readUser(): AuthUser | null {
  // 页面刷新后 Pinia 内存状态会丢失，因此从 localStorage 恢复用户信息。
  const raw = localStorage.getItem(USER_KEY);
  if (!raw) {
    return null;
  }
  try {
    return JSON.parse(raw) as AuthUser;
  } catch {
    // 本地数据损坏时清理掉，避免应用一直卡在解析错误。
    localStorage.removeItem(USER_KEY);
    return null;
  }
}

export const useAuthStore = defineStore('auth', () => {
  // token 和 user 同时存在才算真正登录。
  const token = ref<string | null>(tokenStorage().get());
  const user = ref<AuthUser | null>(readUser());

  const isAuthenticated = computed(() => Boolean(token.value && user.value));
  const isAdmin = computed(() => user.value?.role === 'ADMIN');

  /**
   * 登录或注册成功后写入 Pinia 和 localStorage。
   */
  function setAuth(response: AuthTokenResponse) {
    token.value = response.token;
    user.value = response.user;
    tokenStorage().set(response.token);
    localStorage.setItem(USER_KEY, JSON.stringify(response.user));
  }

  /**
   * 退出登录或收到 401 时清理所有本地登录态。
   */
  function clearAuth() {
    token.value = null;
    user.value = null;
    tokenStorage().clear();
    localStorage.removeItem(USER_KEY);
  }

  return {
    token,
    user,
    isAuthenticated,
    isAdmin,
    setAuth,
    clearAuth,
  };
});
