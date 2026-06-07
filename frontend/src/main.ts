import { createPinia } from 'pinia';
import { createApp } from 'vue';

import App from './App.vue';
import { router } from './router';
import './styles/tokens.css';

// 前端入口集中挂载 Pinia 和 Router；业务页面只通过后端 API 与服务端通信，保持前后端分离。
const app = createApp(App);

app.use(createPinia());
app.use(router);
app.mount('#app');
