import { http, tokenStorage, type ApiResponse } from './http';

export interface ChatSession {
  // 会话主键。
  id: number;
  // 为空表示自由会话，有值表示知识库会话。
  knowledgeBaseId: number | null;
  // 会话标题。
  title: string;
  // 创建时间。
  createdAt: string;
  // 更新时间，发送消息后会刷新。
  updatedAt: string;
}

export interface ChatMessage {
  // 历史消息是数字 ID；前端临时 pending 消息会用字符串 ID。
  id?: number | string;
  // USER 或 ASSISTANT。
  role: 'USER' | 'ASSISTANT' | string;
  // 消息正文。
  content: string;
  // 引用证据 JSON 字符串。
  citations?: string | null;
  // 低置信标记。
  lowConfidence?: boolean | null;
  // 创建时间。
  createdAt?: string;
  // true 表示这条助手消息正在接收流式增量。
  pending?: boolean;
}

export interface RagCitation {
  // Chroma 向量 ID。
  vectorId: string;
  // 召回切片文本。
  content: string;
  // 相似度分数。
  score: number;
  // 是否低于低相关阈值。
  lowScore: boolean;
  // 来源文档 ID。
  documentId: number | null;
  // 来源切片 ID。
  chunkId: number | null;
  // 来源切片序号。
  chunkIndex: number | null;
  // 来源标题。
  sourceTitle: string | null;
  // 来源位置。
  sourceLocation: string | null;
  // 后端保留的 metadata。
  metadata: Record<string, unknown>;
}

export interface RagAnswerDone {
  // 完整答案。
  answer: string;
  // 用于检索的改写问题。
  rewrittenQuery: string;
  // 引用证据。
  citations: RagCitation[];
  // 是否低置信。
  lowConfidence: boolean;
}

export interface RagStreamHandlers {
  // 前端发起请求时记录日志。
  onClientRequest?: (payload: { method: string; url: string; body: Record<string, unknown> }) => void;
  // 后端返回 HTTP 响应头后记录日志。
  onClientResponse?: (payload: { status: number; ok: boolean }) => void;
  // 后端开始检索。
  onRetrievalStart?: (payload: { query: string }) => void;
  // 后端完成检索。
  onRetrievalDone?: (payload: { citations: RagCitation[] }) => void;
  // 后端透出的调试日志，例如 LLM 请求摘要。
  onDebugLog?: (payload: { direction: string; title: string; detail: string }) => void;
  // LLM 回答增量。
  onAnswerDelta?: (payload: { delta: string }) => void;
  // 完整回答完成。
  onAnswerDone?: (payload: RagAnswerDone) => void;
  // 后端错误事件。
  onError?: (payload: { stage?: string; message?: string; rawSummary?: string }) => void;
}

export interface StreamChatMessageOptions {
  // true 表示本次请求由后端先调用 Tavily Search/Extract，再把网页资料拼入 LLM prompt。
  webSearchEnabled?: boolean;
}

// 查询某个知识库会话或自由会话列表。
export async function listSessions(knowledgeBaseId?: number | null) {
  const url = knowledgeBaseId ? `/kbs/${knowledgeBaseId}/sessions` : '/sessions';
  const response = await http.get<ApiResponse<ChatSession[]>>(url);
  return response.data.data ?? [];
}

// 创建知识库会话或自由会话。
export async function createSession(knowledgeBaseId?: number | null, title?: string) {
  const url = knowledgeBaseId ? `/kbs/${knowledgeBaseId}/sessions` : '/sessions';
  const response = await http.post<ApiResponse<ChatSession>>(url, { title });
  return response.data.data;
}

// 查询会话历史消息。
export async function listMessages(sessionId: number) {
  const response = await http.get<ApiResponse<ChatMessage[]>>(`/sessions/${sessionId}/messages`);
  return response.data.data ?? [];
}

export async function streamChatMessage(
  sessionId: number,
  content: string,
  handlers: RagStreamHandlers,
): Promise<void>;
export async function streamChatMessage(
  sessionId: number,
  content: string,
  options: StreamChatMessageOptions,
  handlers: RagStreamHandlers,
): Promise<void>;
export async function streamChatMessage(
  sessionId: number,
  content: string,
  optionsOrHandlers: StreamChatMessageOptions | RagStreamHandlers,
  maybeHandlers?: RagStreamHandlers,
) {
  const options = maybeHandlers ? (optionsOrHandlers as StreamChatMessageOptions) : {};
  const handlers = maybeHandlers ?? (optionsOrHandlers as RagStreamHandlers);
  const token = tokenStorage().get();
  const url = `/api/sessions/${sessionId}/messages/stream`;
  const body = { content, webSearchEnabled: Boolean(options.webSearchEnabled) };
  // 这些回调不是发给后端的业务参数，而是给工作台“过程日志”用的：
  // 用户能看到前端发了什么请求、后端是否接收成功、后续检索和 LLM 调用走到哪一步。
  handlers.onClientRequest?.({ method: 'POST', url, body });
  // axios 更适合普通 JSON 请求；SSE/ReadableStream 需要边收到边读取，所以这里直接使用 fetch。
  const response = await fetch(url, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: JSON.stringify(body),
  });
  handlers.onClientResponse?.({ status: response.status, ok: response.ok });

  if (!response.ok || !response.body) {
    throw new Error(`SSE 请求失败：HTTP ${response.status}`);
  }

  const decoder = new TextDecoder('utf-8');
  const reader = response.body.getReader();
  let buffer = '';

  while (true) {
    const { done, value } = await reader.read();
    if (done) {
      break;
    }
    buffer += decoder.decode(value, { stream: true });
    // 网络分包不一定刚好按一个 SSE 事件切开，buffer 会保留未读完整的半个事件。
    buffer = consumeEvents(buffer, handlers);
  }
  consumeEvents(buffer + '\n\n', handlers);
}

function consumeEvents(buffer: string, handlers: RagStreamHandlers) {
  let pending = buffer;
  // 后端 SseEmitter 输出的是标准 SSE 格式：多个字段行组成一个事件，事件之间用空行分隔。
  let boundary = pending.indexOf('\n\n');
  while (boundary >= 0) {
    const block = pending.slice(0, boundary);
    pending = pending.slice(boundary + 2);
    dispatchEventBlock(block, handlers);
    boundary = pending.indexOf('\n\n');
  }
  return pending;
}

function dispatchEventBlock(block: string, handlers: RagStreamHandlers) {
  if (!block.trim()) {
    return;
  }
  let eventName = 'message';
  const dataLines: string[] = [];
  // 一个 SSE 事件通常长这样：
  // event: answer_delta
  // data: {"delta":"一小段回答"}
  for (const rawLine of block.split(/\r?\n/)) {
    const line = rawLine.trimEnd();
    if (line.startsWith('event:')) {
      eventName = line.slice('event:'.length).trim();
    } else if (line.startsWith('data:')) {
      dataLines.push(line.slice('data:'.length).trimStart());
    }
  }
  const payload = parsePayload(dataLines.join('\n'));
  // 事件名是前后端约定：后端只负责发事件，前端在这里分发给页面状态和日志面板。
  if (eventName === 'retrieval_start') handlers.onRetrievalStart?.(payload as { query: string });
  if (eventName === 'retrieval_done') handlers.onRetrievalDone?.(payload as { citations: RagCitation[] });
  if (eventName === 'debug_log') handlers.onDebugLog?.(payload as { direction: string; title: string; detail: string });
  if (eventName === 'answer_delta') handlers.onAnswerDelta?.(payload as { delta: string });
  if (eventName === 'answer_done') handlers.onAnswerDone?.(payload as RagAnswerDone);
  if (eventName === 'error') handlers.onError?.(payload as { stage?: string; message?: string; rawSummary?: string });
}

function parsePayload(raw: string) {
  if (!raw) {
    return {};
  }
  try {
    // 正常情况下 data 是 JSON。
    return JSON.parse(raw);
  } catch {
    // 如果服务端临时发了纯文本，也把它当作 delta 处理，避免直接丢失。
    return { delta: raw };
  }
}
