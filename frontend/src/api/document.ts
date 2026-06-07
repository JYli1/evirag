import { http, type ApiResponse } from './http';

export type DocumentStatus = 'PROCESSING' | 'READY' | 'FAILED' | string;

export interface KnowledgeDocument {
  id: number;
  knowledgeBaseId: number;
  originalFilename: string;
  storedPath: string;
  contentType: string | null;
  fileSizeBytes: number;
  sha256: string;
  parseStatus: DocumentStatus;
  errorStage: string | null;
  errorMessage: string | null;
  rawErrorSummary: string | null;
  chunkCount: number;
  createdAt: string;
  updatedAt: string;
}

export async function listDocuments(knowledgeBaseId: number) {
  const response = await http.get<ApiResponse<KnowledgeDocument[]>>(
    `/knowledge-bases/${knowledgeBaseId}/documents`,
  );
  return response.data.data ?? [];
}

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
