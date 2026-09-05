import { useState } from 'react';
import {
  Alert,
  Button,
  Card,
  CardActionArea,
  CardContent,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  MenuItem,
  Stack,
  TextField,
  Typography,
} from '@mui/material';
import Grid from '@mui/material/Grid2';
import AddIcon from '@mui/icons-material/Add';
import { useNavigate } from 'react-router-dom';
import { AppLayout } from '../layouts/AppLayout';
import { PageHeader } from '../components/PageHeader';
import { LoadingBlock, ErrorBlock, EmptyState } from '../components/Feedback';
import { StatusChip } from '../components/StatusChip';
import { Money } from '../components/Money';
import { useAccounts, useMyAccountRequests, useOpenAccount } from '../api/accounts';
import { ApiError } from '../api/client';
import type { AccountType } from '../types/domain';

export function Accounts() {
  const navigate = useNavigate();
  const { data: accounts, isLoading, error } = useAccounts();
  const { data: requests } = useMyAccountRequests();
  const openAccount = useOpenAccount();

  const [dialogOpen, setDialogOpen] = useState(false);
  const [accountType, setAccountType] = useState<AccountType>('SAVINGS');
  const [formError, setFormError] = useState<string | null>(null);

  const pendingRequests = requests?.filter((r) => r.status === 'ACCOUNT_REQUESTED' || r.status === 'UNDER_REVIEW') ?? [];

  async function handleOpenAccount() {
    setFormError(null);
    try {
      await openAccount.mutateAsync(accountType);
      setDialogOpen(false);
    } catch (err) {
      setFormError(err instanceof ApiError ? err.message : 'Unable to submit account request.');
    }
  }

  return (
    <AppLayout>
      <PageHeader
        title="Accounts"
        subtitle="Your Meridian accounts and balances."
        action={
          <Button variant="contained" startIcon={<AddIcon />} onClick={() => setDialogOpen(true)}>
            Open account
          </Button>
        }
      />

      {pendingRequests.length > 0 && (
        <Alert severity="info" sx={{ mb: 3 }}>
          You have {pendingRequests.length} account {pendingRequests.length === 1 ? 'request' : 'requests'} pending review.
        </Alert>
      )}

      {isLoading && <LoadingBlock />}
      {error && <ErrorBlock error={error} />}
      {accounts && accounts.length === 0 && (
        <EmptyState message="You don't have any accounts yet. Open one to get started." />
      )}

      <Grid container spacing={2}>
        {accounts?.map((account) => (
          <Grid size={{ xs: 12, sm: 6, md: 4 }} key={account.id}>
            <Card>
              <CardActionArea onClick={() => navigate(`/accounts/${account.id}`)}>
                <CardContent>
                  <Stack direction="row" justifyContent="space-between" alignItems="flex-start">
                    <Typography variant="overline" color="text.secondary">
                      {account.accountType}
                    </Typography>
                    <StatusChip status={account.status} />
                  </Stack>
                  <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>
                    {account.maskedAccountNumber}
                  </Typography>
                  <Typography variant="h5" fontWeight={700} sx={{ mt: 2 }}>
                    <Money amount={account.availableBalance} currency={account.currency} />
                  </Typography>
                  <Typography variant="caption" color="text.secondary">
                    Available balance
                  </Typography>
                </CardContent>
              </CardActionArea>
            </Card>
          </Grid>
        ))}
      </Grid>

      <Dialog open={dialogOpen} onClose={() => setDialogOpen(false)} fullWidth maxWidth="xs">
        <DialogTitle>Open a new account</DialogTitle>
        <DialogContent>
          {formError && (
            <Alert severity="error" sx={{ mb: 2 }}>
              {formError}
            </Alert>
          )}
          <TextField
            select
            label="Account type"
            value={accountType}
            onChange={(e) => setAccountType(e.target.value as AccountType)}
            fullWidth
            sx={{ mt: 1 }}
          >
            <MenuItem value="SAVINGS">Savings</MenuItem>
            <MenuItem value="CURRENT">Current</MenuItem>
          </TextField>
          <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 2 }}>
            Requires a verified KYC record. If yours isn't verified yet, submit it from the Onboarding page first.
          </Typography>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDialogOpen(false)}>Cancel</Button>
          <Button variant="contained" onClick={handleOpenAccount} disabled={openAccount.isPending}>
            Submit request
          </Button>
        </DialogActions>
      </Dialog>
    </AppLayout>
  );
}
