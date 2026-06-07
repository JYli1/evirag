import { createPinia } from 'pinia';
import { createApp } from 'vue';

import App from './App.vue';

// 前端入口只挂载 Vue 应用和 Pinia 状态容器；路由、接口和页面会在后续任务中逐步加入。
const app = createApp(App);

app.use(createPinia());
app.mount('#app');
