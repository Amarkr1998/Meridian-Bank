import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiClient } from './client';
import type { MeResponse, SessionResponse } from '../types/domain';

export function useMe() {
  return useQuery({
    queryKey: ['me'],
    queryFn: async () => {
      const { data } = await apiClient.get<MeResponse>('/api/v1/auth/me');
      return data;
    },
  });
}

export function useSessions() {
  return useQuery({
    queryKey: ['sessions'],
    queryFn: async () => {
      const { data } = await apiClient.get<SessionResponse[]>('/api/v1/auth/sessions');
      return data;
    },
  });
}

export function useRevokeSession() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (sessionId: string) => {
      await apiClient.delete(`/api/v1/auth/sessions/${sessionId}`);
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['sessions'] }),
  });
}

export function useRevokeAllSessions() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async () => {
      await apiClient.delete('/api/v1/auth/sessions');
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['sessions'] }),
  });
}

export async function requestPasswordReset(email: string): Promise<{ message: string; devResetToken?: string }> {
  const { data } = await apiClient.post('/api/v1/auth/password-reset/request', { email });
  return data;
}

export async function confirmPasswordReset(token: string, newPassword: string): Promise<void> {
  await apiClient.post('/api/v1/auth/password-reset/confirm', { token, newPassword });
}
