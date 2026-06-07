import { defineStore } from 'pinia';
import { computed, ref } from 'vue';

import type { AuthTokenResponse, AuthUser } from '@/api/auth';
import { tokenStorage } from '@/api/http';

const USER_KEY = 'evirag_user';

function readUser(): AuthUser | null {
  const raw = localStorage.getItem(USER_KEY);
  if (!raw) {
    return null;
  }
  try {
    return JSON.parse(raw) as AuthUser;
  } catch {
    localStorage.removeItem(USER_KEY);
    return null;
  }
}

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(tokenStorage().get());
  const user = ref<AuthUser | null>(readUser());

  const isAuthenticated = computed(() => Boolean(token.value && user.value));
  const isAdmin = computed(() => user.value?.role === 'ADMIN');

  function setAuth(response: AuthTokenResponse) {
    token.value = response.token;
    user.value = response.user;
    tokenStorage().set(response.token);
    localStorage.setItem(USER_KEY, JSON.stringify(response.user));
  }

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
