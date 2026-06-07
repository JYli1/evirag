import { createPinia } from 'pinia';
import { describe, expect, it } from 'vitest';
import { createApp } from 'vue';

import { router } from '@/router';
import LoginPage from './LoginPage.vue';

describe('LoginPage', () => {
  it('显示 EviRAG Logo、邮箱输入框、密码输入框和登录按钮', async () => {
    const root = document.createElement('div');
    document.body.appendChild(root);
    const app = createApp(LoginPage);
    app.use(createPinia());
    app.use(router);
    app.mount(root);
    await router.isReady();

    expect(root.textContent).toContain('EviRAG');
    expect(root.querySelector('input[type="email"]')).not.toBeNull();
    expect(root.querySelector('input[type="password"]')).not.toBeNull();
    expect(root.textContent).toContain('登录');

    app.unmount();
    root.remove();
  });
});
