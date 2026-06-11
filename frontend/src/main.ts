import { createPinia } from 'pinia';
import { createApp } from 'vue';

import App from './App.vue';
import { router } from './router';
import './styles/tokens.css';

// 前端入口集中挂载 Pinia 和 Router；业务页面只通过后端 API 与服务端通信，保持前后端分离。
const app = createApp(App);

// Pinia 必须先注册，路由守卫里才能读取 authStore。
app.use(createPinia());
// Router 负责页面切换和登录/管理员权限守卫。
app.use(router);
// 挂载到 index.html 中的 #app。
app.mount('#app');
