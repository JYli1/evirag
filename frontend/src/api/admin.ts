import { http, type ApiResponse } from './http';

export interface AdminDashboard {
  // 注册用户总数。
  totalUsers: number;
  // 可登录用户数量。
  activeUsers: number;
  // 被禁用用户数量。
  disabledUsers: number;
  // 全站知识库总数。
  totalKnowledgeBases: number;
  // 全站文档总数。
  totalDocuments: number;
  // 已成功索引文档数。
  readyDocuments: number;
  // 处理失败文档数。
  failedDocuments: number;
  // 用户提问次数。
  questionCount: number;
  // 今日上传量。
  todayUploadCount: number;
  // 粗略 token 估算，不是服务商账单。
  estimatedTotalTokens: number;
  // 必填配置缺失数量。
  missingConfigCount: number;
}

export interface AdminUser {
  // 用户主键。
  id: number;
  // 当前默认等于邮箱，预留给昵称。
  username: string;
  // 登录邮箱。
  email: string;
  // USER 或 ADMIN。
  role: string;
  // ACTIVE 或 DISABLED。
  status: 'ACTIVE' | 'DISABLED' | string;
  // 注册时间。
  createdAt: string;
  // 更新时间。
  updatedAt: string;
}

export interface ConfigStatusItem {
  // 配置键，例如 LLM_API_KEY。
  key: string;
  // 中文名称。
  name: string;
  // 配置分组。
  group: string;
  // 是否必填。
  required: boolean;
  // 是否敏感值，敏感值只展示是否配置。
  secret: boolean;
  // 当前是否已配置。
  configured: boolean;
  // 展示说明。
  message: string;
}

export interface AdminConfigStatus {
  missingCount: number;
  items: ConfigStatusItem[];
}

export interface AdminAuditLog {
  // 日志主键。
  id: number;
  // 操作管理员 ID。
  adminUserId: number;
  // 动作名称。
  action: string;
  // 操作对象类型。
  targetType: string;
  // 操作对象 ID。
  targetId: number | null;
  // JSON 详情。
  detail: string | null;
  // 来源 IP。
  ipAddress: string | null;
  // 浏览器标识。
  userAgent: string | null;
  // 操作时间。
  createdAt: string;
}

export interface AdminRecentDocument {
  // 最近文档 ID。
  id: number;
  // 所属知识库。
  knowledgeBaseId: number;
  // 原文件名。
  originalFilename: string;
  // 处理状态。
  parseStatus: string;
  // 切片数量。
  chunkCount: number;
  // 上传时间。
  createdAt: string;
}

export interface AdminRecentMessage {
  // 最近消息 ID。
  id: number;
  // 所属会话。
  sessionId: number;
  // USER 或 ASSISTANT。
  role: string;
  // 裁剪后的消息预览。
  preview: string;
  // 消息时间。
  createdAt: string;
}

export interface AdminUserDetail {
  // 用户基本信息。
  user: AdminUser;
  // 知识库数量。
  knowledgeBaseCount: number;
  // 文档数量。
  documentCount: number;
  // 已成功文档数量。
  readyDocumentCount: number;
  // 失败文档数量。
  failedDocumentCount: number;
  // 切片数量。
  chunkCount: number;
  // 用户问题数。
  questionCount: number;
  // 助手消息数。
  assistantMessageCount: number;
  // 文档 token 估算。
  estimatedDocumentTokens: number;
  // 聊天 token 估算。
  estimatedChatTokens: number;
  // 总 token 估算。
  estimatedTotalTokens: number;
  // 最近文档。
  recentDocuments: AdminRecentDocument[];
  // 最近消息。
  recentMessages: AdminRecentMessage[];
}

// 管理员首页汇总指标。
export async function getAdminDashboard() {
  const response = await http.get<ApiResponse<AdminDashboard>>('/admin/dashboard');
  return response.data.data;
}

// 管理员用户列表。
export async function listAdminUsers() {
  const response = await http.get<ApiResponse<AdminUser[]>>('/admin/users');
  return response.data.data ?? [];
}

// 单个用户详情。
export async function getAdminUserDetail(userId: number) {
  const response = await http.get<ApiResponse<AdminUserDetail>>(`/admin/users/${userId}`);
  return response.data.data;
}

// 启用或禁用用户。
export async function updateAdminUserStatus(userId: number, status: 'ACTIVE' | 'DISABLED') {
  const response = await http.put<ApiResponse<AdminUser>>(`/admin/users/${userId}/status`, { status });
  return response.data.data;
}

// 系统配置状态，不返回真实密钥。
export async function getAdminConfigStatus() {
  const response = await http.get<ApiResponse<AdminConfigStatus>>('/admin/system/config-status');
  return response.data.data;
}

// 最近管理员操作日志。
export async function listAdminAuditLogs() {
  const response = await http.get<ApiResponse<AdminAuditLog[]>>('/admin/audit-logs');
  return response.data.data ?? [];
}
