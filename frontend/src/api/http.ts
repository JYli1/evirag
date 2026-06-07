import axios, { type AxiosError } from 'axios';

export interface ApiResponse<T> {
  success: boolean;
  code: string;
  message: string;
  data: T;
}

const TOKEN_KEY = 'evirag_token';

export const http = axios.create({
  baseURL: '/api',
  timeout: 30_000,
});

http.interceptors.request.use((config) => {
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
  return {
    key: TOKEN_KEY,
    get: () => localStorage.getItem(TOKEN_KEY),
    set: (token: string) => localStorage.setItem(TOKEN_KEY, token),
    clear: () => localStorage.removeItem(TOKEN_KEY),
  };
}

export function apiErrorMessage(error: unknown): string {
  const axiosError = error as AxiosError<ApiResponse<unknown>>;
  return axiosError.response?.data?.message || axiosError.message || '请求失败';
}
