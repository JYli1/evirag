import { http, type ApiResponse } from './http';

export interface KnowledgeBase {
  // 知识库主键。
  id: number;
  // 展示名称。
  name: string;
  // 可选描述。
  description: string | null;
  // 对应 Chroma collection 名称，主要用于调试。
  chromaCollection: string;
  // 知识库状态。
  status: string;
  // 创建时间，后端返回 ISO 字符串。
  createdAt: string;
  // 更新时间。
  updatedAt: string;
}

// 获取当前用户的知识库列表。
export async function listKnowledgeBases() {
  const response = await http.get<ApiResponse<KnowledgeBase[]>>('/knowledge-bases');
  return response.data.data ?? [];
}

// 创建知识库；userId 不从前端传，后端从 JWT 读取。
export async function createKnowledgeBase(payload: { name: string; description?: string }) {
  const response = await http.post<ApiResponse<KnowledgeBase>>('/knowledge-bases', payload);
  return response.data.data;
}
