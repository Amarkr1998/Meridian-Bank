import { useQuery } from '@tanstack/react-query';
import { apiClient } from './client';
import type { AuditEventResponse, Page } from '../types/domain';

export interface AuditSearchFilters {
  actorId?: string;
  action?: string;
  resourceType?: string;
  resourceId?: string;
  producedBy?: string;
  correlationId?: string;
}

export function useAuditEvents(filters: AuditSearchFilters = {}) {
  return useQuery({
    queryKey: ['ops', 'audit-events', filters],
    queryFn: async () => {
      const { data } = await apiClient.get<Page<AuditEventResponse>>('/api/v1/audit-events', {
        params: { size: 100, ...filters },
      });
      return data.content;
    },
  });
}
