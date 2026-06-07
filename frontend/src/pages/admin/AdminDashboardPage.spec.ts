import { createPinia } from 'pinia';
import { describe, expect, it, vi } from 'vitest';
import { createApp } from 'vue';

import AdminDashboardPage from './AdminDashboardPage.vue';

vi.mock('vue-router', () => ({
  useRouter: () => ({
    push: vi.fn(),
  }),
  RouterLink: {
    props: ['to'],
    template: '<a><slot /></a>',
  },
}));

vi.mock('@/api/admin', () => ({
  getAdminDashboard: vi.fn(async () => ({
    totalUsers: 3,
    activeUsers: 2,
    disabledUsers: 1,
    totalKnowledgeBases: 4,
    totalDocuments: 7,
    readyDocuments: 6,
    failedDocuments: 1,
    questionCount: 12,
    todayUploadCount: 2,
    missingConfigCount: 1,
  })),
  listAdminUsers: vi.fn(async () => [
    {
      id: 1,
      username: 'admin@example.com',
      email: 'admin@example.com',
      role: 'ADMIN',
      status: 'ACTIVE',
      createdAt: '2026-06-08T00:00:00Z',
      updatedAt: '2026-06-08T00:00:00Z',
    },
  ]),
  getAdminConfigStatus: vi.fn(async () => ({
    missingCount: 1,
    items: [
      {
        key: 'LLM_API_KEY',
        name: 'LLM API Key',
        group: '大模型',
        required: true,
        secret: true,
        configured: false,
        message: '缺失',
      },
    ],
  })),
  listAdminAuditLogs: vi.fn(async () => []),
  updateAdminUserStatus: vi.fn(),
}));

describe('AdminDashboardPage', () => {
  it('显示用户数、知识库数、文档数、问答次数和配置状态', async () => {
    const root = document.createElement('div');
    document.body.appendChild(root);
    const app = createApp(AdminDashboardPage);
    app.use(createPinia());
    app.component('RouterLink', {
      props: ['to'],
      template: '<a><slot /></a>',
    });
    app.mount(root);
    await new Promise((resolve) => window.setTimeout(resolve));

    expect(root.textContent).toContain('用户数');
    expect(root.textContent).toContain('知识库数');
    expect(root.textContent).toContain('文档数');
    expect(root.textContent).toContain('问答次数');
    expect(root.textContent).toContain('配置状态');

    app.unmount();
    root.remove();
  });
});
