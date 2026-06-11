import { createRouter, createWebHistory } from 'vue-router';

import { useAuthStore } from '@/stores/authStore';

// 页面使用动态 import，Vite 会把这些页面拆成独立 chunk，首屏登录页加载更轻。
const LoginPage = () => import('@/pages/auth/LoginPage.vue');
const RegisterPage = () => import('@/pages/auth/RegisterPage.vue');
const ResetPasswordPage = () => import('@/pages/auth/ResetPasswordPage.vue');
const WorkbenchPage = () => import('@/pages/workbench/WorkbenchPage.vue');
const AdminDashboardPage = () => import('@/pages/admin/AdminDashboardPage.vue');

export const router = createRouter({
  // createWebHistory 使用正常 URL，例如 /login；开发环境由 Vite 处理回退。
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/login' },
    // public 表示不需要登录的页面。
    { path: '/login', name: 'login', component: LoginPage, meta: { public: true } },
    { path: '/register', name: 'register', component: RegisterPage, meta: { public: true } },
    { path: '/reset-password', name: 'reset-password', component: ResetPasswordPage, meta: { public: true } },
    {
      path: '/workbench',
      name: 'workbench',
      component: WorkbenchPage,
      meta: { requiresAuth: true },
    },
    {
      path: '/admin',
      name: 'admin',
      component: AdminDashboardPage,
      meta: { requiresAuth: true, requiresAdmin: true },
    },
  ],
});

router.beforeEach((to) => {
  const authStore = useAuthStore();
  // 需要登录但没有 token/user 时，跳转登录页，并记录原始目标地址。
  if (to.meta.requiresAuth && !authStore.isAuthenticated) {
    return { name: 'login', query: { redirect: to.fullPath } };
  }
  // 管理员页面需要 ADMIN 角色，普通用户回到工作台。
  if (to.meta.requiresAdmin && !authStore.isAdmin) {
    return { name: 'workbench' };
  }
  // 已登录用户访问注册或找回密码页时，直接回工作台。
  if (to.meta.public && authStore.isAuthenticated && to.name !== 'login') {
    return { name: 'workbench' };
  }
  return true;
});
