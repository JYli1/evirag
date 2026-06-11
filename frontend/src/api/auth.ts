import { http, type ApiResponse } from './http';

export interface AuthUser {
  // 后端 users 表主键。
  id: number;
  // 登录邮箱。
  email: string;
  // USER 或 ADMIN；保留 string 是为了兼容后续扩展角色。
  role: 'USER' | 'ADMIN' | string;
}

export interface AuthTokenResponse {
  // JWT，后续放到 Authorization 请求头。
  token: string;
  // 秒级过期时间戳。
  expiresAt: number;
  // 当前登录用户信息。
  user: AuthUser;
}

// 注册前发送验证码。
export function sendRegisterCode(email: string) {
  return http.post<ApiResponse<void>>('/auth/register/send-code', { email });
}

// 提交邮箱、密码、验证码完成注册。
export function register(payload: { email: string; password: string; code: string }) {
  return http.post<ApiResponse<AuthTokenResponse>>('/auth/register', payload);
}

// 邮箱密码登录。
export function login(payload: { email: string; password: string }) {
  return http.post<ApiResponse<AuthTokenResponse>>('/auth/login', payload);
}

// 找回密码前发送验证码。
export function sendPasswordResetCode(email: string) {
  return http.post<ApiResponse<void>>('/auth/password/send-code', { email });
}

// 使用验证码设置新密码。
export function resetPassword(payload: { email: string; newPassword: string; code: string }) {
  return http.post<ApiResponse<void>>('/auth/password/reset', payload);
}
