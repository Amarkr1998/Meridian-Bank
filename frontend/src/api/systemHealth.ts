import { useQuery } from '@tanstack/react-query';
import { apiClient } from './client';
import type { ServiceHealth } from '../types/domain';

export function useSystemHealth() {
  return useQuery({
    queryKey: ['ops', 'system-health'],
    queryFn: async () => {
      const { data } = await apiClient.get<ServiceHealth[]>('/api/v1/ops/system-health');
      return data;
    },
    refetchInterval: 15_000,
  });
}
