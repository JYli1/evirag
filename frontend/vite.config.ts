import { fileURLToPath, URL } from 'node:url';

import vue from '@vitejs/plugin-vue';
import { defineConfig } from 'vitest/config';

// Vite 基础配置只负责启动 Vue3 单页应用，后续业务路由和 API 代理会在对应任务中补充。
export default defineConfig({
  plugins: [vue()],
  server: {
    proxy: {
      // 本地开发时前端运行在 3000，后端运行在 8080；这里把 /api 请求转发到后端，避免浏览器直接请求 Vite 导致 404。
      '/api': {
        target: 'http://127.0.0.1:8080',
        changeOrigin: true,
      },
    },
  },
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  test: {
    environment: 'jsdom',
  },
});
