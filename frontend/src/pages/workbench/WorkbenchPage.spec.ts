import { createPinia } from 'pinia';
import { describe, expect, it, vi } from 'vitest';
import { createApp } from 'vue';

import WorkbenchPage from './WorkbenchPage.vue';

vi.mock('vue-router', () => ({
  useRouter: () => ({
    push: vi.fn(),
  }),
}));

vi.mock('@/api/knowledge', () => ({
  listKnowledgeBases: vi.fn(async () => []),
  createKnowledgeBase: vi.fn(),
}));

vi.mock('@/api/document', () => ({
  listDocuments: vi.fn(async () => []),
  listDocumentChunks: vi.fn(async () => []),
  uploadDocument: vi.fn(),
  deleteDocument: vi.fn(),
}));

vi.mock('@/api/chat', () => ({
  listSessions: vi.fn(async () => []),
  createSession: vi.fn(),
  listMessages: vi.fn(async () => []),
  streamChatMessage: vi.fn(),
}));

describe('WorkbenchPage', () => {
  it('显示左侧知识库、中间聊天区和右侧引用证据区', async () => {
    const root = document.createElement('div');
    document.body.appendChild(root);
    const app = createApp(WorkbenchPage);
    app.use(createPinia());
    app.component('RouterLink', {
      props: ['to'],
      template: '<a><slot /></a>',
    });
    app.mount(root);
    await new Promise((resolve) => window.setTimeout(resolve));

    expect(root.textContent).toContain('知识库');
    expect(root.querySelector('textarea')?.getAttribute('placeholder')).toContain('向当前知识库提问');
    expect(root.textContent).toContain('引用证据');

    app.unmount();
    root.remove();
  });
});
