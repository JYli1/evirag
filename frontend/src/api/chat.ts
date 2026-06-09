import { http, tokenStorage, type ApiResponse } from './http';

export interface ChatSession {
  id: number;
  knowledgeBaseId: number | null;
  title: string;
  createdAt: string;
  updatedAt: string;
}

export interface ChatMessage {
  id?: number | string;
  role: 'USER' | 'ASSISTANT' | string;
  content: string;
  citations?: string | null;
  lowConfidence?: boolean | null;
  createdAt?: string;
  pending?: boolean;
}

export interface RagCitation {
  vectorId: string;
  content: string;
  score: number;
  lowScore: boolean;
  documentId: number | null;
  chunkId: number | null;
  chunkIndex: number | null;
  sourceTitle: string | null;
  sourceLocation: string | null;
  metadata: Record<string, unknown>;
}

export interface RagAnswerDone {
  answer: string;
  rewrittenQuery: string;
  citations: RagCitation[];
  lowConfidence: boolean;
}

export interface RagStreamHandlers {
  onClientRequest?: (payload: { method: string; url: string; body: Record<string, unknown> }) => void;
  onClientResponse?: (payload: { status: number; ok: boolean }) => void;
  onRetrievalStart?: (payload: { query: string }) => void;
  onRetrievalDone?: (payload: { citations: RagCitation[] }) => void;
  onDebugLog?: (payload: { direction: string; title: string; detail: string }) => void;
  onAnswerDelta?: (payload: { delta: string }) => void;
  onAnswerDone?: (payload: RagAnswerDone) => void;
  onError?: (payload: { stage?: string; message?: string; rawSummary?: string }) => void;
}

export async function listSessions(knowledgeBaseId?: number | null) {
  const url = knowledgeBaseId ? `/kbs/${knowledgeBaseId}/sessions` : '/sessions';
  const response = await http.get<ApiResponse<ChatSession[]>>(url);
  return response.data.data ?? [];
}

export async function createSession(knowledgeBaseId?: number | null, title?: string) {
  const url = knowledgeBaseId ? `/kbs/${knowledgeBaseId}/sessions` : '/sessions';
  const response = await http.post<ApiResponse<ChatSession>>(url, { title });
  return response.data.data;
}

export async function listMessages(sessionId: number) {
  const response = await http.get<ApiResponse<ChatMessage[]>>(`/sessions/${sessionId}/messages`);
  return response.data.data ?? [];
}

export async function streamChatMessage(sessionId: number, content: string, handlers: RagStreamHandlers) {
  const token = tokenStorage().get();
  const url = `/api/sessions/${sessionId}/messages/stream`;
  const body = { content };
  handlers.onClientRequest?.({ method: 'POST', url, body });
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
    buffer = consumeEvents(buffer, handlers);
  }
  consumeEvents(buffer + '\n\n', handlers);
}

function consumeEvents(buffer: string, handlers: RagStreamHandlers) {
  let pending = buffer;
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
  for (const rawLine of block.split(/\r?\n/)) {
    const line = rawLine.trimEnd();
    if (line.startsWith('event:')) {
      eventName = line.slice('event:'.length).trim();
    } else if (line.startsWith('data:')) {
      dataLines.push(line.slice('data:'.length).trimStart());
    }
  }
  const payload = parsePayload(dataLines.join('\n'));
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
    return JSON.parse(raw);
  } catch {
    return { delta: raw };
  }
}
