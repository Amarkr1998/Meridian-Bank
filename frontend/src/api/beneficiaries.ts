import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiClient } from './client';
import type { AddBeneficiaryRequest, AddBeneficiaryResponse, BeneficiaryResponse, Page } from '../types/domain';

const LIST_KEY = ['beneficiaries'];

export function useBeneficiaries() {
  return useQuery({
    queryKey: LIST_KEY,
    queryFn: async () => {
      const { data } = await apiClient.get<Page<BeneficiaryResponse>>('/api/v1/beneficiaries', {
        params: { size: 100 },
      });
      return data.content;
    },
  });
}

export function useAddBeneficiary() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (request: AddBeneficiaryRequest) => {
      const { data } = await apiClient.post<AddBeneficiaryResponse>('/api/v1/beneficiaries', request);
      return data;
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: LIST_KEY }),
  });
}

export function useVerifyBeneficiary() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async ({ id, otp }: { id: string; otp: string }) => {
      const { data } = await apiClient.post<BeneficiaryResponse>(`/api/v1/beneficiaries/${id}/verify`, { otp });
      return data;
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: LIST_KEY }),
  });
}

export function useResendBeneficiaryVerification() {
  return useMutation({
    mutationFn: async (id: string) => {
      const { data } = await apiClient.post<{ expiresInSeconds: number; devOtp?: string }>(
        `/api/v1/beneficiaries/${id}/resend-verification`,
      );
      return data;
    },
  });
}

export function useDeleteBeneficiary() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (id: string) => {
      await apiClient.delete(`/api/v1/beneficiaries/${id}`);
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: LIST_KEY }),
  });
}

export function useSetBeneficiaryActive() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async ({ id, activate }: { id: string; activate: boolean }) => {
      const { data } = await apiClient.patch<BeneficiaryResponse>(
        `/api/v1/beneficiaries/${id}/${activate ? 'activate' : 'deactivate'}`,
      );
      return data;
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: LIST_KEY }),
  });
}
