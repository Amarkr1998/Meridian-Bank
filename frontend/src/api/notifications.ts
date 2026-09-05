import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiClient } from './client';
import type { NotificationResponse, Page } from '../types/domain';

const LIST_KEY = ['notifications'];

export function useNotifications() {
  return useQuery({
    queryKey: LIST_KEY,
    queryFn: async () => {
      const { data } = await apiClient.get<Page<NotificationResponse>>('/api/v1/notifications', {
        params: { size: 50 },
      });
      return data.content;
    },
    refetchInterval: 20_000,
  });
}

export function useMarkNotificationRead() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (id: string) => {
      const { data } = await apiClient.patch<NotificationResponse>(`/api/v1/notifications/${id}/read`);
      return data;
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: LIST_KEY }),
  });
}
