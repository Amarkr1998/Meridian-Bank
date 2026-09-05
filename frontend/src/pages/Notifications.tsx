import { Card, CardActionArea, CardContent, Stack, Typography } from '@mui/material';
import CircleIcon from '@mui/icons-material/Circle';
import { AppLayout } from '../layouts/AppLayout';
import { PageHeader } from '../components/PageHeader';
import { LoadingBlock, ErrorBlock, EmptyState } from '../components/Feedback';
import { useMarkNotificationRead, useNotifications } from '../api/notifications';

export function Notifications() {
  const { data: notifications, isLoading, error } = useNotifications();
  const markRead = useMarkNotificationRead();

  return (
    <AppLayout>
      <PageHeader title="Notifications" subtitle="Alerts about your account, payments, and support requests." />

      {isLoading && <LoadingBlock />}
      {error && <ErrorBlock error={error} />}
      {notifications && notifications.length === 0 && <EmptyState message="You're all caught up." />}

      <Stack spacing={1.5}>
        {notifications?.map((n) => (
          <Card key={n.id} sx={{ bgcolor: n.read ? 'background.paper' : '#EEF3F9' }}>
            <CardActionArea onClick={() => !n.read && markRead.mutate(n.id)} sx={{ cursor: n.read ? 'default' : 'pointer' }}>
              <CardContent>
                <Stack direction="row" spacing={1.5} alignItems="flex-start">
                  {!n.read && <CircleIcon sx={{ fontSize: 10, color: 'secondary.main', mt: 0.7 }} />}
                  <Stack sx={{ flex: 1 }}>
                    <Typography variant="subtitle2" fontWeight={n.read ? 500 : 700}>
                      {n.title}
                    </Typography>
                    <Typography variant="body2" color="text.secondary">
                      {n.body}
                    </Typography>
                    <Typography variant="caption" color="text.secondary" sx={{ mt: 0.5 }}>
                      {new Date(n.createdAt).toLocaleString()}
                    </Typography>
                  </Stack>
                </Stack>
              </CardContent>
            </CardActionArea>
          </Card>
        ))}
      </Stack>
    </AppLayout>
  );
}
