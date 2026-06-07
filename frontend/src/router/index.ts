import { createRouter, createWebHistory } from 'vue-router';

import { useAuthStore } from '@/stores/authStore';

const LoginPage = () => import('@/pages/auth/LoginPage.vue');
const RegisterPage = () => import('@/pages/auth/RegisterPage.vue');
const ResetPasswordPage = () => import('@/pages/auth/ResetPasswordPage.vue');
const WorkbenchPage = () => import('@/pages/workbench/WorkbenchPage.vue');

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/login' },
    { path: '/login', name: 'login', component: LoginPage, meta: { public: true } },
    { path: '/register', name: 'register', component: RegisterPage, meta: { public: true } },
    { path: '/reset-password', name: 'reset-password', component: ResetPasswordPage, meta: { public: true } },
    {
      path: '/workbench',
      name: 'workbench',
      component: WorkbenchPage,
      meta: { requiresAuth: true },
    },
  ],
});

router.beforeEach((to) => {
  const authStore = useAuthStore();
  if (to.meta.requiresAuth && !authStore.isAuthenticated) {
    return { name: 'login', query: { redirect: to.fullPath } };
  }
  if (to.meta.public && authStore.isAuthenticated && to.name !== 'login') {
    return { name: 'workbench' };
  }
  return true;
});
