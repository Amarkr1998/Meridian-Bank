import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiClient } from './client';
import type { Page, ReconciliationRecordResponse, ReconciliationStatus } from '../types/domain';

export function useReconciliationRecords(status?: ReconciliationStatus) {
  return useQuery({
    queryKey: ['ops', 'reconciliation', status ?? 'ALL'],
    queryFn: async () => {
      const { data } = await apiClient.get<Page<ReconciliationRecordResponse>>('/api/v1/ledger/reconciliation-records', {
        params: { size: 100, ...(status ? { status } : {}) },
      });
      return data.content;
    },
    refetchInterval: 30_000,
  });
}

export function useReconciliationActions() {
  const queryClient = useQueryClient();
  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['ops', 'reconciliation'] });

  const startInvestigation = useMutation({
    mutationFn: async (id: string) => apiClient.patch(`/api/v1/ledger/reconciliation-records/${id}/start-investigation`),
    onSuccess: invalidate,
  });
  const resolve = useMutation({
    mutationFn: async ({ id, notes }: { id: string; notes?: string }) =>
      apiClient.patch(`/api/v1/ledger/reconciliation-records/${id}/resolve`, { notes }),
    onSuccess: invalidate,
  });

  return { startInvestigation, resolve };
}
