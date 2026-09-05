import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiClient } from './client';
import type {
  AmlAlertResponse,
  AmlAlertStatus,
  FraudAlertResponse,
  FraudAlertStatus,
  FraudRuleResponse,
  Page,
  UpdateFraudRuleRequest,
} from '../types/domain';

export function useFraudAlerts(filters: { status?: FraudAlertStatus; customerId?: string } = {}) {
  return useQuery({
    queryKey: ['ops', 'fraud-alerts', filters.status ?? 'ALL', filters.customerId ?? 'ALL'],
    queryFn: async () => {
      const { data } = await apiClient.get<Page<FraudAlertResponse>>('/api/v1/fraud-alerts', {
        params: {
          size: 100,
          ...(filters.status ? { status: filters.status } : {}),
          ...(filters.customerId ? { customerId: filters.customerId } : {}),
        },
      });
      return data.content;
    },
  });
}

export function useFraudAlertActions() {
  const queryClient = useQueryClient();
  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['ops', 'fraud-alerts'] });

  const startReview = useMutation({
    mutationFn: async (id: string) => apiClient.patch(`/api/v1/fraud-alerts/${id}/start-review`),
    onSuccess: invalidate,
  });
  const clear = useMutation({
    mutationFn: async ({ id, notes }: { id: string; notes?: string }) =>
      apiClient.patch(`/api/v1/fraud-alerts/${id}/clear`, { notes }),
    onSuccess: invalidate,
  });
  const escalate = useMutation({
    mutationFn: async ({ id, notes }: { id: string; notes?: string }) =>
      apiClient.patch(`/api/v1/fraud-alerts/${id}/escalate`, { notes }),
    onSuccess: invalidate,
  });
  const confirm = useMutation({
    mutationFn: async ({ id, notes }: { id: string; notes?: string }) =>
      apiClient.patch(`/api/v1/fraud-alerts/${id}/confirm`, { notes }),
    onSuccess: invalidate,
  });

  return { startReview, clear, escalate, confirm };
}

export function useAmlAlerts(filters: { status?: AmlAlertStatus; customerId?: string } = {}) {
  return useQuery({
    queryKey: ['ops', 'aml-alerts', filters.status ?? 'ALL', filters.customerId ?? 'ALL'],
    queryFn: async () => {
      const { data } = await apiClient.get<Page<AmlAlertResponse>>('/api/v1/aml-alerts', {
        params: {
          size: 100,
          ...(filters.status ? { status: filters.status } : {}),
          ...(filters.customerId ? { customerId: filters.customerId } : {}),
        },
      });
      return data.content;
    },
  });
}

export function useAmlAlertActions() {
  const queryClient = useQueryClient();
  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['ops', 'aml-alerts'] });

  const startReview = useMutation({
    mutationFn: async (id: string) => apiClient.patch(`/api/v1/aml-alerts/${id}/start-review`),
    onSuccess: invalidate,
  });
  const clear = useMutation({
    mutationFn: async ({ id, notes }: { id: string; notes?: string }) =>
      apiClient.patch(`/api/v1/aml-alerts/${id}/clear`, { notes }),
    onSuccess: invalidate,
  });
  const escalate = useMutation({
    mutationFn: async ({ id, notes }: { id: string; notes?: string }) =>
      apiClient.patch(`/api/v1/aml-alerts/${id}/escalate`, { notes }),
    onSuccess: invalidate,
  });

  return { startReview, clear, escalate };
}

export function useFraudRules() {
  return useQuery({
    queryKey: ['ops', 'fraud-rules'],
    queryFn: async () => {
      const { data } = await apiClient.get<FraudRuleResponse[]>('/api/v1/fraud-rules');
      return data;
    },
  });
}

export function useRequestFraudRuleUpdate() {
  return useMutation({
    mutationFn: async ({ id, request }: { id: string; request: UpdateFraudRuleRequest }) =>
      apiClient.patch(`/api/v1/fraud-rules/${id}`, request),
  });
}
