import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiClient } from './client';
import type {
  CustomerResponse,
  CustomerStatus,
  KycRecordResponse,
  KycStatus,
  Page,
  RegisterCustomerRequest,
  RegisterCustomerResponse,
  SubmitKycRequest,
  UpdateCustomerStatusRequest,
} from '../types/domain';

export async function registerCustomer(request: RegisterCustomerRequest): Promise<RegisterCustomerResponse> {
  const { data } = await apiClient.post<RegisterCustomerResponse>('/api/v1/customers/register', request);
  return data;
}

export async function verifyContact(customerId: string, otp: string): Promise<void> {
  await apiClient.post(`/api/v1/customers/${customerId}/verify-contact`, { otp });
}

export async function resendVerification(customerId: string): Promise<{ verificationExpiresInSeconds: number; devOtp?: string }> {
  const { data } = await apiClient.post(`/api/v1/customers/${customerId}/resend-verification`);
  return data;
}

export function useCustomer(customerId: string | undefined) {
  return useQuery({
    queryKey: ['customer', customerId],
    queryFn: async () => {
      const { data } = await apiClient.get<CustomerResponse>(`/api/v1/customers/${customerId}`);
      return data;
    },
    enabled: !!customerId,
  });
}

export interface UpdateCustomerProfileRequest {
  phone?: string;
  addressLine1?: string;
  addressLine2?: string;
  city?: string;
  state?: string;
  postalCode?: string;
  country?: string;
}

export function useUpdateCustomer(customerId: string | undefined) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (request: UpdateCustomerProfileRequest) => {
      const { data } = await apiClient.patch<CustomerResponse>(`/api/v1/customers/${customerId}`, request);
      return data;
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['customer', customerId] }),
  });
}

export function useKycHistory(customerId: string | undefined) {
  return useQuery({
    queryKey: ['kyc', customerId],
    queryFn: async () => {
      const { data } = await apiClient.get<KycRecordResponse[]>(`/api/v1/customers/${customerId}/kyc`);
      return data;
    },
    enabled: !!customerId,
  });
}

export function useSubmitKyc(customerId: string | undefined) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (request: SubmitKycRequest) => {
      const { data } = await apiClient.post<KycRecordResponse>(`/api/v1/customers/${customerId}/kyc`, request);
      return data;
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['kyc', customerId] }),
  });
}

// --- Ops (staff) ---

export function useCustomersQueue(status?: CustomerStatus) {
  return useQuery({
    queryKey: ['ops', 'customers', status ?? 'ALL'],
    queryFn: async () => {
      const { data } = await apiClient.get<Page<CustomerResponse>>('/api/v1/customers', {
        params: { size: 100, ...(status ? { status } : {}) },
      });
      return data.content;
    },
  });
}

export function useRequestCustomerStatusChange(customerId: string) {
  return useMutation({
    mutationFn: async (request: UpdateCustomerStatusRequest) => {
      const { data } = await apiClient.patch(`/api/v1/customers/${customerId}/status`, request);
      return data;
    },
  });
}

export function useKycQueue(status?: KycStatus) {
  return useQuery({
    queryKey: ['ops', 'kyc', status ?? 'ALL'],
    queryFn: async () => {
      const { data } = await apiClient.get<Page<KycRecordResponse>>('/api/v1/kyc', {
        params: { size: 100, ...(status ? { status } : {}) },
      });
      return data.content;
    },
  });
}

export function useKycRecord(kycId: string | undefined) {
  return useQuery({
    queryKey: ['ops', 'kyc-record', kycId],
    queryFn: async () => {
      const { data } = await apiClient.get<KycRecordResponse>(`/api/v1/kyc/${kycId}`);
      return data;
    },
    enabled: !!kycId,
  });
}

export function useKycDecision() {
  const queryClient = useQueryClient();
  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['ops', 'kyc'] });

  const startReview = useMutation({
    mutationFn: async (kycId: string) => apiClient.post(`/api/v1/kyc/${kycId}/start-review`),
    onSuccess: invalidate,
  });
  const approve = useMutation({
    mutationFn: async (kycId: string) => apiClient.post(`/api/v1/kyc/${kycId}/approve`),
    onSuccess: invalidate,
  });
  const reject = useMutation({
    mutationFn: async ({ kycId, reason }: { kycId: string; reason: string }) =>
      apiClient.post(`/api/v1/kyc/${kycId}/reject`, { reason }),
    onSuccess: invalidate,
  });

  return { startReview, approve, reject };
}
