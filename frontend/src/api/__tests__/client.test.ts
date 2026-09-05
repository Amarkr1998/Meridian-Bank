import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import axios from 'axios';
import MockAdapter from 'axios-mock-adapter';
import { ApiError, apiClient, setSessionExpiredHandler } from '../client';
import { loadSession, saveSession } from '../../auth/tokenStorage';

function authHeader(config: { headers?: unknown }): string | undefined {
  const headers = config.headers as Record<string, unknown> | { get?: (name: string) => unknown } | undefined;
  if (!headers) return undefined;
  if (typeof (headers as { get?: unknown }).get === 'function') {
    return (headers as { get: (name: string) => unknown }).get('Authorization') as string | undefined;
  }
  return (headers as Record<string, unknown>)['Authorization'] as string | undefined;
}

describe('apiClient', () => {
  let clientMock: MockAdapter;
  let axiosMock: MockAdapter;

  beforeEach(() => {
    clientMock = new MockAdapter(apiClient);
    axiosMock = new MockAdapter(axios);
    localStorage.clear();
    setSessionExpiredHandler(() => {});
  });

  afterEach(() => {
    clientMock.restore();
    axiosMock.restore();
  });

  it('attaches the Authorization header from the stored session', async () => {
    saveSession({ accessToken: 'tok-123', refreshToken: 'refresh-1', userId: 'u1', email: 'a@b.com', role: 'CUSTOMER' });
    clientMock.onGet('/api/v1/whoami').reply((config) => [200, { seen: authHeader(config) }]);

    const response = await apiClient.get('/api/v1/whoami');

    expect(response.data.seen).toBe('Bearer tok-123');
  });

  it('sends no Authorization header when there is no stored session', async () => {
    clientMock.onGet('/api/v1/public').reply((config) => [200, { seen: authHeader(config) }]);

    const response = await apiClient.get('/api/v1/public');

    expect(response.data.seen).toBeUndefined();
  });

  it('transparently refreshes an expired token once and retries the original request with it', async () => {
    saveSession({ accessToken: 'old-token', refreshToken: 'refresh-1', userId: 'u1', email: 'a@b.com', role: 'CUSTOMER' });
    let requestCount = 0;
    clientMock.onGet('/api/v1/protected').reply((config) => {
      requestCount += 1;
      const header = authHeader(config);
      if (header === 'Bearer old-token') return [401, { code: 'UNAUTHENTICATED', message: 'expired' }];
      if (header === 'Bearer new-token') return [200, { secret: true }];
      return [500, {}];
    });
    axiosMock.onPost(/\/api\/v1\/auth\/refresh$/).reply(200, {
      accessToken: 'new-token',
      refreshToken: 'refresh-2',
      userId: 'u1',
      email: 'a@b.com',
      role: 'CUSTOMER',
    });

    const response = await apiClient.get('/api/v1/protected');

    expect(response.data).toEqual({ secret: true });
    expect(requestCount).toBe(2);
    expect(loadSession()?.accessToken).toBe('new-token');
    expect(loadSession()?.refreshToken).toBe('refresh-2');
  });

  it('only refreshes once for two requests that fail concurrently (single in-flight refresh)', async () => {
    saveSession({ accessToken: 'old-token', refreshToken: 'refresh-1', userId: 'u1', email: 'a@b.com', role: 'CUSTOMER' });
    let refreshCalls = 0;
    clientMock.onGet('/api/v1/a').reply((config) => (authHeader(config) === 'Bearer old-token' ? [401, {}] : [200, {}]));
    clientMock.onGet('/api/v1/b').reply((config) => (authHeader(config) === 'Bearer old-token' ? [401, {}] : [200, {}]));
    axiosMock.onPost(/\/api\/v1\/auth\/refresh$/).reply(() => {
      refreshCalls += 1;
      return [200, { accessToken: 'new-token', refreshToken: 'refresh-2', userId: 'u1', email: 'a@b.com', role: 'CUSTOMER' }];
    });

    await Promise.all([apiClient.get('/api/v1/a'), apiClient.get('/api/v1/b')]);

    expect(refreshCalls).toBe(1);
  });

  it('clears the session and notifies exactly once when the refresh token itself is rejected', async () => {
    saveSession({ accessToken: 'old-token', refreshToken: 'bad-refresh', userId: 'u1', email: 'a@b.com', role: 'CUSTOMER' });
    const onExpired = vi.fn();
    setSessionExpiredHandler(onExpired);
    clientMock.onGet('/api/v1/protected').reply(401, { code: 'UNAUTHENTICATED', message: 'expired' });
    axiosMock.onPost(/\/api\/v1\/auth\/refresh$/).reply(401, { code: 'UNAUTHENTICATED', message: 'refresh token invalid' });

    await expect(apiClient.get('/api/v1/protected')).rejects.toBeTruthy();

    expect(loadSession()).toBeNull();
    expect(onExpired).toHaveBeenCalledTimes(1);
  });

  it('does not attempt a refresh at all when there is no session to refresh', async () => {
    clientMock.onGet('/api/v1/protected').reply(401, { code: 'UNAUTHENTICATED', message: 'expired' });
    let refreshAttempted = false;
    axiosMock.onPost(/\/api\/v1\/auth\/refresh$/).reply(() => {
      refreshAttempted = true;
      return [200, {}];
    });

    await expect(apiClient.get('/api/v1/protected')).rejects.toBeTruthy();

    expect(refreshAttempted).toBe(false);
  });

  it('wraps a standard error-envelope response in ApiError with the code/message/correlationId intact', async () => {
    clientMock.onGet('/api/v1/boom').reply(400, { code: 'VALIDATION_ERROR', message: 'bad input', correlationId: 'c-1' });

    const error = await apiClient.get('/api/v1/boom').catch((e: unknown) => e);

    expect(error).toBeInstanceOf(ApiError);
    const apiError = error as ApiError;
    expect(apiError.code).toBe('VALIDATION_ERROR');
    expect(apiError.message).toBe('bad input');
    expect(apiError.correlationId).toBe('c-1');
    expect(apiError.status).toBe(400);
  });
});
