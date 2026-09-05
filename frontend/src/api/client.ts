import axios, { AxiosError, type InternalAxiosRequestConfig } from 'axios';
import { clearSession, loadSession, saveSession } from '../auth/tokenStorage';
import type { ApiErrorBody, TokenResponse } from '../types/domain';

export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080';

export const apiClient = axios.create({
  baseURL: API_BASE_URL,
  headers: { 'Content-Type': 'application/json' },
});

apiClient.interceptors.request.use((config) => {
  const session = loadSession();
  if (session?.accessToken) {
    config.headers.set('Authorization', `Bearer ${session.accessToken}`);
  }
  return config;
});

/** Raised whenever the API returns the standard error envelope, so callers can show `message`. */
export class ApiError extends Error {
  readonly code: string;
  readonly correlationId?: string;
  readonly status?: number;

  constructor(body: ApiErrorBody, status?: number) {
    super(body.message);
    this.code = body.code;
    this.correlationId = body.correlationId;
    this.status = status;
  }
}

let refreshPromise: Promise<string> | null = null;

async function refreshAccessToken(): Promise<string> {
  const session = loadSession();
  if (!session) throw new Error('No session to refresh');

  const response = await axios.post<TokenResponse>(`${API_BASE_URL}/api/v1/auth/refresh`, {
    refreshToken: session.refreshToken,
  });
  saveSession({
    accessToken: response.data.accessToken,
    refreshToken: response.data.refreshToken,
    userId: response.data.userId,
    email: response.data.email,
    role: response.data.role,
  });
  return response.data.accessToken;
}

let onSessionExpired: (() => void) | null = null;

/** Called once from AuthProvider so this module can force a logout without importing React state. */
export function setSessionExpiredHandler(handler: () => void): void {
  onSessionExpired = handler;
}

apiClient.interceptors.response.use(
  (response) => response,
  async (error: AxiosError<ApiErrorBody>) => {
    const original = error.config as (InternalAxiosRequestConfig & { _retried?: boolean }) | undefined;

    if (error.response?.status === 401 && original && !original._retried && loadSession()) {
      original._retried = true;
      try {
        refreshPromise ??= refreshAccessToken().finally(() => {
          refreshPromise = null;
        });
        const newToken = await refreshPromise;
        original.headers.set('Authorization', `Bearer ${newToken}`);
        return apiClient(original);
      } catch {
        clearSession();
        onSessionExpired?.();
        return Promise.reject(error);
      }
    }

    if (error.response?.data?.code) {
      return Promise.reject(new ApiError(error.response.data, error.response.status));
    }
    return Promise.reject(error);
  },
);
