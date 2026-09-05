import { Card, CardContent, Stack, Typography } from '@mui/material';
import Grid from '@mui/material/Grid2';
import { useParams } from 'react-router-dom';
import { AppLayout } from '../layouts/AppLayout';
import { PageHeader } from '../components/PageHeader';
import { LoadingBlock, ErrorBlock, EmptyState } from '../components/Feedback';
import { StatusChip } from '../components/StatusChip';
import { Money } from '../components/Money';
import { useAccount } from '../api/accounts';
import { usePayments } from '../api/payments';

export function AccountDetail() {
  const { accountId } = useParams<{ accountId: string }>();
  const { data: account, isLoading, error } = useAccount(accountId);
  const { data: payments } = usePayments();

  const accountPayments = payments?.filter(
    (p) => p.sourceAccountId === accountId || p.destinationAccountId === accountId,
  );

  return (
    <AppLayout>
      <PageHeader title="Account details" subtitle={account?.maskedAccountNumber} />

      {isLoading && <LoadingBlock />}
      {error && <ErrorBlock error={error} />}

      {account && (
        <>
          <Grid container spacing={3} sx={{ mb: 3 }}>
            <Grid size={{ xs: 12, md: 4 }}>
              <Card>
                <CardContent>
                  <Stack direction="row" justifyContent="space-between" alignItems="center">
                    <Typography variant="body2" color="text.secondary">
                      Status
                    </Typography>
                    <StatusChip status={account.status} />
                  </Stack>
                  <Typography variant="body2" color="text.secondary" sx={{ mt: 2 }}>
                    Type
                  </Typography>
                  <Typography variant="body1" fontWeight={600}>
                    {account.accountType}
                  </Typography>
                </CardContent>
              </Card>
            </Grid>
            <Grid size={{ xs: 12, md: 4 }}>
              <Card>
                <CardContent>
                  <Typography variant="body2" color="text.secondary">
                    Available balance
                  </Typography>
                  <Typography variant="h5" fontWeight={700}>
                    <Money amount={account.availableBalance} currency={account.currency} />
                  </Typography>
                  <Typography variant="body2" color="text.secondary" sx={{ mt: 2 }}>
                    Ledger balance
                  </Typography>
                  <Typography variant="body1" fontWeight={600}>
                    <Money amount={account.ledgerBalance} currency={account.currency} />
                  </Typography>
                </CardContent>
              </Card>
            </Grid>
            <Grid size={{ xs: 12, md: 4 }}>
              <Card>
                <CardContent>
                  <Typography variant="body2" color="text.secondary">
                    Per-transaction limit
                  </Typography>
                  <Typography variant="body1" fontWeight={600}>
                    <Money amount={account.perTransactionLimit} currency={account.currency} />
                  </Typography>
                  <Typography variant="body2" color="text.secondary" sx={{ mt: 2 }}>
                    Daily limit
                  </Typography>
                  <Typography variant="body1" fontWeight={600}>
                    <Money amount={account.dailyLimit} currency={account.currency} />
                  </Typography>
                </CardContent>
              </Card>
            </Grid>
          </Grid>

          <Card>
            <CardContent>
              <Typography variant="subtitle1" fontWeight={600} gutterBottom>
                Transactions on this account
              </Typography>
              {accountPayments && accountPayments.length === 0 && <EmptyState message="No transactions on this account yet." />}
              <Stack divider={<div style={{ borderBottom: '1px solid #E2E6EB' }} />}>
                {accountPayments?.map((p) => (
                  <Stack key={p.id} direction="row" justifyContent="space-between" alignItems="center" sx={{ py: 1.5 }}>
                    <Stack>
                      <Typography variant="body2" fontWeight={600}>
                        {p.purpose || 'Transfer'}
                      </Typography>
                      <Typography variant="caption" color="text.secondary">
                        {new Date(p.createdAt).toLocaleString()}
                      </Typography>
                    </Stack>
                    <Stack direction="row" spacing={2} alignItems="center">
                      <Typography variant="body2" fontWeight={600}>
                        {p.sourceAccountId === accountId ? '-' : '+'}
                        <Money amount={p.amount} currency={p.currency} />
                      </Typography>
                      <StatusChip status={p.status} />
                    </Stack>
                  </Stack>
                ))}
              </Stack>
            </CardContent>
          </Card>
        </>
      )}
    </AppLayout>
  );
}
