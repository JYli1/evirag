import { http, type ApiResponse } from './http';

export interface AdminDashboard {
  totalUsers: number;
  activeUsers: number;
  disabledUsers: number;
  totalKnowledgeBases: number;
  totalDocuments: number;
  readyDocuments: number;
  failedDocuments: number;
  questionCount: number;
  todayUploadCount: number;
  missingConfigCount: number;
}

export interface AdminUser {
  id: number;
  username: string;
  email: string;
  role: string;
  status: 'ACTIVE' | 'DISABLED' | string;
  createdAt: string;
  updatedAt: string;
}

export interface ConfigStatusItem {
  key: string;
  name: string;
  group: string;
  required: boolean;
  secret: boolean;
  configured: boolean;
  message: string;
}

export interface AdminConfigStatus {
  missingCount: number;
  items: ConfigStatusItem[];
}

export interface AdminAuditLog {
  id: number;
  adminUserId: number;
  action: string;
  targetType: string;
  targetId: number | null;
  detail: string | null;
  ipAddress: string | null;
  userAgent: string | null;
  createdAt: string;
}

export async function getAdminDashboard() {
  const response = await http.get<ApiResponse<AdminDashboard>>('/admin/dashboard');
  return response.data.data;
}

export async function listAdminUsers() {
  const response = await http.get<ApiResponse<AdminUser[]>>('/admin/users');
  return response.data.data ?? [];
}

export async function updateAdminUserStatus(userId: number, status: 'ACTIVE' | 'DISABLED') {
  const response = await http.put<ApiResponse<AdminUser>>(`/admin/users/${userId}/status`, { status });
  return response.data.data;
}

export async function getAdminConfigStatus() {
  const response = await http.get<ApiResponse<AdminConfigStatus>>('/admin/system/config-status');
  return response.data.data;
}

export async function listAdminAuditLogs() {
  const response = await http.get<ApiResponse<AdminAuditLog[]>>('/admin/audit-logs');
  return response.data.data ?? [];
}
