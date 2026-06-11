import { http, type ApiResponse } from './http';

export type DocumentStatus = 'PROCESSING' | 'READY' | 'FAILED' | string;

export interface KnowledgeDocument {
  // 文档主键。
  id: number;
  // 所属知识库。
  knowledgeBaseId: number;
  // 用户上传时的文件名。
  originalFilename: string;
  // 服务端保存路径，当前用于调试展示。
  storedPath: string;
  // 浏览器上传的 MIME 类型。
  contentType: string | null;
  // 文件大小，单位字节。
  fileSizeBytes: number;
  // 文件 SHA-256。
  sha256: string;
  // 处理状态，决定文档列表上的状态标识。
  parseStatus: DocumentStatus;
  // 失败阶段。
  errorStage: string | null;
  // 用户可读错误信息。
  errorMessage: string | null;
  // 更完整的脱敏错误摘要。
  rawErrorSummary: string | null;
  // 已生成切片数。
  chunkCount: number;
  // 上传时间。
  createdAt: string;
  // 更新时间。
  updatedAt: string;
}

export interface DocumentChunk {
  // 切片主键。
  id: number;
  // 所属文档。
  documentId: number;
  // 所属知识库。
  knowledgeBaseId: number;
  // 文档内切片顺序。
  chunkIndex: number;
  // 切片正文。
  content: string;
  // 来源标题。
  sourceTitle: string | null;
  // 页码或段落位置。
  sourceLocation: string | null;
  // 粗略 token 估算。
  tokenCount: number | null;
  // JSON 元数据字符串。
  metadata: string | null;
  // 切片创建时间。
  createdAt: string;
}

// 查询某个知识库下的文档列表。
export async function listDocuments(knowledgeBaseId: number) {
  const response = await http.get<ApiResponse<KnowledgeDocument[]>>(
    `/knowledge-bases/${knowledgeBaseId}/documents`,
  );
  return response.data.data ?? [];
}

// 查询单个文档的切片，用于前端预览。
export async function listDocumentChunks(documentId: number) {
  const response = await http.get<ApiResponse<DocumentChunk[]>>(`/documents/${documentId}/chunks`);
  return response.data.data ?? [];
}

// 上传文档必须使用 FormData，后端用 MultipartFile 接收 file 字段。
export async function uploadDocument(knowledgeBaseId: number, file: File) {
  const formData = new FormData();
  formData.append('file', file);
  const response = await http.post<ApiResponse<KnowledgeDocument>>(
    `/knowledge-bases/${knowledgeBaseId}/documents`,
    formData,
    { headers: { 'Content-Type': 'multipart/form-data' } },
  );
  return response.data.data;
}

// 删除文档，后端会清理 MySQL 切片、Chroma 向量和本地文件。
export async function deleteDocument(documentId: number) {
  await http.delete<ApiResponse<void>>(`/documents/${documentId}`);
}
