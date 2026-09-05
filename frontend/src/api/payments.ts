import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiClient } from './client';
import type { CreatePaymentRequest, Page, PaymentResponse, TransactionStatus } from '../types/domain';

export function usePayments(status?: TransactionStatus) {
  return useQuery({
    queryKey: ['payments', status ?? 'ALL'],
    queryFn: async () => {
      const { data } = await apiClient.get<Page<PaymentResponse>>('/api/v1/payments', {
        params: { size: 100, ...(status ? { status } : {}) },
      });
      return data.content;
    },
  });
}

export function usePayment(paymentId: string | undefined) {
  return useQuery({
    queryKey: ['payment', paymentId],
    queryFn: async () => {
      const { data } = await apiClient.get<PaymentResponse>(`/api/v1/payments/${paymentId}`);
      return data;
    },
    enabled: !!paymentId,
  });
}

// --- Ops (staff) ---

export function useOpsPayments(filters: { customerId?: string; status?: TransactionStatus } = {}) {
  return useQuery({
    queryKey: ['ops', 'payments', filters.customerId ?? 'ALL', filters.status ?? 'ALL'],
    queryFn: async () => {
      const { data } = await apiClient.get<Page<PaymentResponse>>('/api/v1/payments', {
        params: {
          size: 100,
          ...(filters.customerId ? { customerId: filters.customerId } : {}),
          ...(filters.status ? { status: filters.status } : {}),
        },
      });
      return data.content;
    },
  });
}

export function useCreatePayment() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (request: CreatePaymentRequest) => {
      const { data } = await apiClient.post<PaymentResponse>('/api/v1/payments', request, {
        headers: { 'Idempotency-Key': crypto.randomUUID() },
      });
      return data;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['payments'] });
      queryClient.invalidateQueries({ queryKey: ['accounts'] });
    },
  });
}
