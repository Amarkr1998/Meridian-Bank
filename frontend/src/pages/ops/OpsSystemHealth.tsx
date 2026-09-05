import { Card, CardContent, Chip, Stack, Typography } from '@mui/material';
import Grid from '@mui/material/Grid2';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import CancelIcon from '@mui/icons-material/Cancel';
import HelpIcon from '@mui/icons-material/Help';
import { OpsLayout } from '../../layouts/OpsLayout';
import { PageHeader } from '../../components/PageHeader';
import { LoadingBlock, ErrorBlock } from '../../components/Feedback';
import { useSystemHealth } from '../../api/systemHealth';

function statusIcon(status: string) {
  if (status === 'UP') return <CheckCircleIcon color="success" />;
  if (status === 'DOWN') return <CancelIcon color="error" />;
  return <HelpIcon color="warning" />;
}

export function OpsSystemHealth() {
  const { data: services, isLoading, error } = useSystemHealth();

  return (
    <OpsLayout>
      <PageHeader title="System Health" subtitle="Live status of every backend service, checked by the gateway." />

      {isLoading && <LoadingBlock />}
      {error && <ErrorBlock error={error} />}

      <Grid container spacing={2}>
        {services?.map((s) => (
          <Grid size={{ xs: 12, sm: 6, md: 3 }} key={s.service}>
            <Card>
              <CardContent>
                <Stack direction="row" justifyContent="space-between" alignItems="center">
                  <Typography variant="body2" fontWeight={600}>
                    {s.service}
                  </Typography>
                  {statusIcon(s.status)}
                </Stack>
                <Chip
                  size="small"
                  sx={{ mt: 1 }}
                  label={s.status}
                  color={s.status === 'UP' ? 'success' : s.status === 'DOWN' ? 'error' : 'warning'}
                  variant="outlined"
                />
              </CardContent>
            </Card>
          </Grid>
        ))}
      </Grid>
    </OpsLayout>
  );
}
