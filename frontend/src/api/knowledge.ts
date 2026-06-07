import { http, type ApiResponse } from './http';

export interface KnowledgeBase {
  id: number;
  name: string;
  description: string | null;
  chromaCollection: string;
  status: string;
  createdAt: string;
  updatedAt: string;
}

export async function listKnowledgeBases() {
  const response = await http.get<ApiResponse<KnowledgeBase[]>>('/knowledge-bases');
  return response.data.data ?? [];
}

export async function createKnowledgeBase(payload: { name: string; description?: string }) {
  const response = await http.post<ApiResponse<KnowledgeBase>>('/knowledge-bases', payload);
  return response.data.data;
}
