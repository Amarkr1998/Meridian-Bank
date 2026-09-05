import { Card, CardContent, Divider, Stack, Typography } from '@mui/material';
import { useParams } from 'react-router-dom';
import { AppLayout } from '../layouts/AppLayout';
import { PageHeader } from '../components/PageHeader';
import { LoadingBlock, ErrorBlock } from '../components/Feedback';
import { StatusChip } from '../components/StatusChip';
import { useSupportRequest } from '../api/support';

export function SupportDetail() {
  const { requestId } = useParams<{ requestId: string }>();
  const { data: request, isLoading, error } = useSupportRequest(requestId);

  return (
    <AppLayout>
      <PageHeader title="Support request" subtitle={request?.subject} />
      {isLoading && <LoadingBlock />}
      {error && <ErrorBlock error={error} />}

      {request && (
        <Card sx={{ maxWidth: 640 }}>
          <CardContent>
            <Stack direction="row" justifyContent="space-between" alignItems="center">
              <Typography variant="overline" color="text.secondary">
                {request.category}
              </Typography>
              <StatusChip status={request.status} />
            </Stack>
            <Typography variant="h6" sx={{ mt: 1 }}>
              {request.subject}
            </Typography>
            <Typography variant="caption" color="text.secondary">
              Submitted {new Date(request.createdAt).toLocaleString()}
            </Typography>
            <Divider sx={{ my: 2 }} />
            <Typography variant="body2">{request.description}</Typography>

            {request.status === 'RESOLVED' && request.resolutionNotes && (
              <>
                <Divider sx={{ my: 2 }} />
                <Typography variant="subtitle2" gutterBottom>
                  Resolution
                </Typography>
                <Typography variant="body2">{request.resolutionNotes}</Typography>
                <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 1 }}>
                  Resolved {request.resolvedAt && new Date(request.resolvedAt).toLocaleString()}
                </Typography>
              </>
            )}
          </CardContent>
        </Card>
      )}
    </AppLayout>
  );
}
