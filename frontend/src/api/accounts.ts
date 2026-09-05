import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiClient } from './client';
import type {
  AccountOpeningStatus,
  AccountRequestResponse,
  AccountResponse,
  AccountStatus,
  AccountType,
  Page,
  UpdateAccountLimitsRequest,
} from '../types/domain';

export function useAccounts() {
  return useQuery({
    queryKey: ['accounts'],
    queryFn: async () => {
      const { data } = await apiClient.get<Page<AccountResponse>>('/api/v1/accounts', {
        params: { size: 50 },
      });
      return data.content;
    },
  });
}

export function useAccount(accountId: string | undefined) {
  return useQuery({
    queryKey: ['account', accountId],
    queryFn: async () => {
      const { data } = await apiClient.get<AccountResponse>(`/api/v1/accounts/${accountId}`);
      return data;
    },
    enabled: !!accountId,
    refetchInterval: 15_000,
  });
}

export function useMyAccountRequests() {
  return useQuery({
    queryKey: ['accountRequests', 'mine'],
    queryFn: async () => {
      const { data } = await apiClient.get<AccountRequestResponse[]>('/api/v1/accounts/requests/mine');
      return data;
    },
  });
}

export function useOpenAccount() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (accountType: AccountType) => {
      const { data } = await apiClient.post<AccountRequestResponse>('/api/v1/accounts/requests', { accountType });
      return data;
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['accountRequests', 'mine'] }),
  });
}

// --- Ops (staff) ---

export function useAccountsQueue(status?: AccountStatus) {
  return useQuery({
    queryKey: ['ops', 'accounts', status ?? 'ALL'],
    queryFn: async () => {
      const { data } = await apiClient.get<Page<AccountResponse>>('/api/v1/accounts', {
        params: { size: 100, ...(status ? { status } : {}) },
      });
      return data.content;
    },
  });
}

export function useAccountRequestsQueue(status?: AccountOpeningStatus) {
  return useQuery({
    queryKey: ['ops', 'accountRequests', status ?? 'ALL'],
    queryFn: async () => {
      const { data } = await apiClient.get<Page<AccountRequestResponse>>('/api/v1/accounts/requests', {
        params: { size: 100, ...(status ? { status } : {}) },
      });
      return data.content;
    },
  });
}

export function useAccountRequestDecision() {
  const queryClient = useQueryClient();
  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['ops', 'accountRequests'] });

  const startReview = useMutation({
    mutationFn: async (id: string) => apiClient.post(`/api/v1/accounts/requests/${id}/start-review`),
    onSuccess: invalidate,
  });
  const approve = useMutation({
    mutationFn: async (id: string) => apiClient.post(`/api/v1/accounts/requests/${id}/approve`),
    onSuccess: invalidate,
  });
  const reject = useMutation({
    mutationFn: async ({ id, reason }: { id: string; reason: string }) =>
      apiClient.post(`/api/v1/accounts/requests/${id}/reject`, { reason }),
    onSuccess: invalidate,
  });

  return { startReview, approve, reject };
}

export function useAccountActions() {
  const queryClient = useQueryClient();
  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['ops', 'accounts'] });

  const freeze = useMutation({
    mutationFn: async ({ id, reason }: { id: string; reason: string }) =>
      apiClient.patch(`/api/v1/accounts/${id}/freeze`, { reason }),
    onSuccess: invalidate,
  });
  const unfreeze = useMutation({
    mutationFn: async ({ id, reason }: { id: string; reason: string }) =>
      apiClient.patch(`/api/v1/accounts/${id}/unfreeze`, { reason }),
    onSuccess: invalidate,
  });
  const close = useMutation({
    mutationFn: async ({ id, reason }: { id: string; reason: string }) =>
      apiClient.patch(`/api/v1/accounts/${id}/close`, { reason }),
    onSuccess: invalidate,
  });
  const requestBlock = useMutation({
    mutationFn: async ({ id, reason }: { id: string; reason: string }) =>
      apiClient.patch(`/api/v1/accounts/${id}/block`, { reason }),
  });
  const updateLimits = useMutation({
    mutationFn: async ({ id, request }: { id: string; request: UpdateAccountLimitsRequest }) =>
      apiClient.patch(`/api/v1/accounts/${id}/limits`, request),
    onSuccess: invalidate,
  });

  return { freeze, unfreeze, close, requestBlock, updateLimits };
}
