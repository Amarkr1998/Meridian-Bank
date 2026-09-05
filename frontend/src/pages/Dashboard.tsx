import { Box, Button, Card, CardContent, Stack, Typography } from '@mui/material';
import Grid from '@mui/material/Grid2';
import SendIcon from '@mui/icons-material/Send';
import AddCardIcon from '@mui/icons-material/AddCard';
import { Link as RouterLink } from 'react-router-dom';
import { AppLayout } from '../layouts/AppLayout';
import { PageHeader } from '../components/PageHeader';
import { LoadingBlock, ErrorBlock, EmptyState } from '../components/Feedback';
import { StatusChip } from '../components/StatusChip';
import { Money } from '../components/Money';
import { useAccounts } from '../api/accounts';
import { usePayments } from '../api/payments';
import { useAuth } from '../auth/AuthContext';

export function Dashboard() {
  const { user } = useAuth();
  const { data: accounts, isLoading: accountsLoading, error: accountsError } = useAccounts();
  const { data: payments, isLoading: paymentsLoading } = usePayments();

  const totalAvailable = accounts?.reduce((sum, a) => sum + (a.availableBalance ?? 0), 0);

  return (
    <AppLayout>
      <PageHeader title={`Welcome back, ${user?.email.split('@')[0]}`} subtitle="Here's what's happening with your accounts." />

      <Grid container spacing={3} sx={{ mb: 3 }}>
        <Grid size={{ xs: 12, md: 4 }}>
          <Card>
            <CardContent>
              <Typography variant="body2" color="text.secondary">
                Accounts
              </Typography>
              <Typography variant="h4" fontWeight={700} sx={{ mt: 1 }}>
                {accounts?.length ?? '—'}
              </Typography>
              <Button component={RouterLink} to="/accounts" size="small" sx={{ mt: 1, px: 0 }}>
                View accounts
              </Button>
            </CardContent>
          </Card>
        </Grid>
        <Grid size={{ xs: 12, md: 4 }}>
          <Card>
            <CardContent>
              <Typography variant="body2" color="text.secondary">
                Total available balance
              </Typography>
              <Typography variant="h4" fontWeight={700} sx={{ mt: 1 }}>
                {totalAvailable !== undefined ? <Money amount={totalAvailable} /> : '—'}
              </Typography>
              <Typography variant="caption" color="text.secondary">
                Across all active accounts
              </Typography>
            </CardContent>
          </Card>
        </Grid>
        <Grid size={{ xs: 12, md: 4 }}>
          <Card>
            <CardContent>
              <Typography variant="body2" color="text.secondary" gutterBottom>
                Quick actions
              </Typography>
              <Stack direction="row" spacing={1}>
                <Button component={RouterLink} to="/payments" variant="contained" size="small" startIcon={<SendIcon />}>
                  Send money
                </Button>
                <Button component={RouterLink} to="/accounts" variant="outlined" size="small" startIcon={<AddCardIcon />}>
                  Open account
                </Button>
              </Stack>
            </CardContent>
          </Card>
        </Grid>
      </Grid>

      <Card>
        <CardContent>
          <Typography variant="subtitle1" fontWeight={600} gutterBottom>
            Recent transactions
          </Typography>
          {(accountsLoading || paymentsLoading) && <LoadingBlock />}
          {accountsError && <ErrorBlock error={accountsError} />}
          {payments && payments.length === 0 && <EmptyState message="No transactions yet." />}
          {payments && payments.length > 0 && (
            <Stack divider={<Box sx={{ borderBottom: '1px solid #E2E6EB' }} />}>
              {payments.slice(0, 6).map((p) => (
                <Stack key={p.id} direction="row" justifyContent="space-between" alignItems="center" sx={{ py: 1.5 }}>
                  <Box>
                    <Typography variant="body2" fontWeight={600}>
                      {p.purpose || 'Transfer'}
                    </Typography>
                    <Typography variant="caption" color="text.secondary">
                      {new Date(p.createdAt).toLocaleString()}
                    </Typography>
                  </Box>
                  <Stack direction="row" spacing={2} alignItems="center">
                    <Typography variant="body2" fontWeight={600}>
                      <Money amount={p.amount} currency={p.currency} />
                    </Typography>
                    <StatusChip status={p.status} />
                  </Stack>
                </Stack>
              ))}
            </Stack>
          )}
        </CardContent>
      </Card>
    </AppLayout>
  );
}
