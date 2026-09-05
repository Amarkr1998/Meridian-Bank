import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiClient } from './client';
import type { CreateSupportRequestRequest, Page, SupportRequestResponse, SupportRequestStatus } from '../types/domain';

const LIST_KEY = ['supportRequests'];

export function useSupportRequests() {
  return useQuery({
    queryKey: LIST_KEY,
    queryFn: async () => {
      const { data } = await apiClient.get<Page<SupportRequestResponse>>('/api/v1/support-requests', {
        params: { size: 50 },
      });
      return data.content;
    },
  });
}

export function useSupportRequest(id: string | undefined) {
  return useQuery({
    queryKey: ['supportRequest', id],
    queryFn: async () => {
      const { data } = await apiClient.get<SupportRequestResponse>(`/api/v1/support-requests/${id}`);
      return data;
    },
    enabled: !!id,
  });
}

export function useCreateSupportRequest() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (request: CreateSupportRequestRequest) => {
      const { data } = await apiClient.post<SupportRequestResponse>('/api/v1/support-requests', request);
      return data;
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: LIST_KEY }),
  });
}

// --- Ops (staff) — omitting customerId returns the full staff queue, per SupportRequestController ---

export function useSupportQueue(status?: SupportRequestStatus) {
  return useQuery({
    queryKey: ['ops', 'support', status ?? 'ALL'],
    queryFn: async () => {
      const { data } = await apiClient.get<Page<SupportRequestResponse>>('/api/v1/support-requests', {
        params: { size: 100, ...(status ? { status } : {}) },
      });
      return data.content;
    },
  });
}

export function useSupportDecision() {
  const queryClient = useQueryClient();
  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['ops', 'support'] });

  const startProgress = useMutation({
    mutationFn: async (id: string) => apiClient.patch(`/api/v1/support-requests/${id}/start-progress`),
    onSuccess: invalidate,
  });
  const resolve = useMutation({
    mutationFn: async ({ id, notes }: { id: string; notes: string }) =>
      apiClient.patch(`/api/v1/support-requests/${id}/resolve`, { notes }),
    onSuccess: invalidate,
  });

  return { startProgress, resolve };
}
