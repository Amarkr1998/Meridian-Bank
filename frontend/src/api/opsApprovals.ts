import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiClient } from './client';
import type { ApprovalRequestResponse, ApprovalStatus, Page } from '../types/domain';

/**
 * The four gating services (account/kyc/fraud/payment) each own an identical-shaped, entirely
 * separate `/api/v1/approvals` queue — see ADR-0011. The gateway exposes each under its own alias
 * (`/api/v1/ops/approvals/<service>`) since the real path collides across all four — see
 * services/api-gateway/README.md's routing table.
 */
export type GatingService = 'accounts' | 'kyc' | 'fraud' | 'payments';

export const GATING_SERVICES: { key: GatingService; label: string }[] = [
  { key: 'accounts', label: 'Account actions' },
  { key: 'kyc', label: 'Customer status' },
  { key: 'fraud', label: 'Fraud & rules' },
  { key: 'payments', label: 'Payment release' },
];

export function useApprovalQueue(service: GatingService, status?: ApprovalStatus) {
  return useQuery({
    queryKey: ['ops', 'approvals', service, status ?? 'ALL'],
    queryFn: async () => {
      const { data } = await apiClient.get<Page<ApprovalRequestResponse>>(`/api/v1/ops/approvals/${service}`, {
        params: { size: 100, ...(status ? { status } : {}) },
      });
      return data.content;
    },
  });
}

export function useApprovalDecision(service: GatingService) {
  const queryClient = useQueryClient();
  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['ops', 'approvals', service] });

  const approve = useMutation({
    mutationFn: async ({ id, notes }: { id: string; notes?: string }) =>
      apiClient.patch(`/api/v1/ops/approvals/${service}/${id}/approve`, { notes }),
    onSuccess: invalidate,
  });
  const reject = useMutation({
    mutationFn: async ({ id, notes }: { id: string; notes?: string }) =>
      apiClient.patch(`/api/v1/ops/approvals/${service}/${id}/reject`, { notes }),
    onSuccess: invalidate,
  });

  return { approve, reject };
}
