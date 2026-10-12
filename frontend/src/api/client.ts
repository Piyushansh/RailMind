import axios, { AxiosError, type InternalAxiosRequestConfig } from 'axios';
import { useAuthStore } from '@/store/authStore';

/**
 * Every request goes through the real backend at /api/v1/*. The dev server
 * proxies /api to Spring Boot (see vite.config.ts); in production this is
 * served behind the same origin/nginx, so no base URL is hardcoded here.
 */
export const api = axios.create({ baseURL: '/api/v1' });

api.interceptors.request.use((config) => {
  const token = useAuthStore.getState().accessToken;
  if (token) {
    config.headers = config.headers ?? {};
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// Refresh-on-401 with a single in-flight refresh shared across concurrent
// requests, so five simultaneous 401s don't fire five refresh calls and race
// each other (the backend rotates the refresh token on every use - a second
// concurrent refresh would be rejected as reuse and kill every session).
let refreshPromise: Promise<string | null> | null = null;

async function performRefresh(): Promise<string | null> {
  const refreshToken = useAuthStore.getState().refreshToken;
  if (!refreshToken) return null;
  try {
    const response = await axios.post('/api/v1/auth/refresh', { refreshToken });
    const { accessToken, refreshToken: newRefreshToken } = response.data;
    useAuthStore.getState().setTokens(accessToken, newRefreshToken);
    return accessToken as string;
  } catch {
    useAuthStore.getState().clear();
    return null;
  }
}

api.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const original = error.config as (InternalAxiosRequestConfig & { _retried?: boolean }) | undefined;
    const status = error.response?.status;

    if (status === 401 && original && !original._retried && !original.url?.includes('/auth/')) {
      original._retried = true;
      refreshPromise ??= performRefresh().finally(() => {
        refreshPromise = null;
      });
      const newAccessToken = await refreshPromise;
      if (newAccessToken) {
        original.headers = original.headers ?? {};
        original.headers.Authorization = `Bearer ${newAccessToken}`;
        return api(original);
      }
    }
    return Promise.reject(error);
  }
);

/** Shape of a backend error body - matches GlobalExceptionHandler's consistent error format. */
export interface ApiErrorBody {
  timestamp?: string;
  status: number;
  error: string;
  message: string;
  path?: string;
}

export function extractErrorMessage(error: unknown): string {
  if (axios.isAxiosError(error)) {
    const body = error.response?.data as ApiErrorBody | undefined;
    if (body?.message) return body.message;
    if (error.response?.status === 0 || !error.response) return 'Could not reach the server. Check your connection.';
  }
  return 'Something went wrong. Please try again.';
}
