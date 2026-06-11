import axios, { type AxiosError } from 'axios';

export interface ApiResponse<T> {
  // 后端统一响应字段，true 表示业务成功。
  success: boolean;
  // 稳定错误码，例如 OK、UNAUTHORIZED、VALIDATION_FAILED。
  code: string;
  // 给前端弹窗或表单提示使用的文案。
  message: string;
  // 业务数据，具体类型由调用方传入泛型决定。
  data: T;
}

const TOKEN_KEY = 'evirag_token';

// 所有普通 REST 请求共用这个 axios 实例，baseURL 对应 Vite 代理到后端的 /api。
export const http = axios.create({
  baseURL: '/api',
  timeout: 30_000,
});

http.interceptors.request.use((config) => {
  // 请求发出前自动携带 JWT，页面组件就不用每次手动写 Authorization。
  const token = localStorage.getItem(TOKEN_KEY);
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

http.interceptors.response.use(
  (response) => response,
  (error: AxiosError<ApiResponse<unknown>>) => {
    if (error.response?.status === 401) {
      // 401 表示 token 过期或无效，清理本地登录态并回到登录页。
      localStorage.removeItem(TOKEN_KEY);
      localStorage.removeItem('evirag_user');
      if (window.location.pathname !== '/login') {
        window.location.assign('/login');
      }
    }
    return Promise.reject(error);
  },
);

export function tokenStorage() {
  // 封装 localStorage 键名，避免各处散落硬编码。
  return {
    key: TOKEN_KEY,
    get: () => localStorage.getItem(TOKEN_KEY),
    set: (token: string) => localStorage.setItem(TOKEN_KEY, token),
    clear: () => localStorage.removeItem(TOKEN_KEY),
  };
}

export function apiErrorMessage(error: unknown): string {
  // axios 错误优先取后端统一 message，取不到再退回浏览器错误信息。
  const axiosError = error as AxiosError<ApiResponse<unknown>>;
  return axiosError.response?.data?.message || axiosError.message || '请求失败';
}
