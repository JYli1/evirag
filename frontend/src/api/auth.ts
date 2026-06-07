import { http, type ApiResponse } from './http';

export interface AuthUser {
  id: number;
  email: string;
  role: 'USER' | 'ADMIN' | string;
}

export interface AuthTokenResponse {
  token: string;
  expiresAt: number;
  user: AuthUser;
}

export function sendRegisterCode(email: string) {
  return http.post<ApiResponse<void>>('/auth/register/send-code', { email });
}

export function register(payload: { email: string; password: string; code: string }) {
  return http.post<ApiResponse<AuthTokenResponse>>('/auth/register', payload);
}

export function login(payload: { email: string; password: string }) {
  return http.post<ApiResponse<AuthTokenResponse>>('/auth/login', payload);
}

export function sendPasswordResetCode(email: string) {
  return http.post<ApiResponse<void>>('/auth/password/send-code', { email });
}

export function resetPassword(payload: { email: string; newPassword: string; code: string }) {
  return http.post<ApiResponse<void>>('/auth/password/reset', payload);
}
