import { useMemo, useState } from 'react';
import {
  Accordion,
  AccordionDetails,
  AccordionSummary,
  Alert,
  MenuItem,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
  Typography,
  Paper,
} from '@mui/material';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import { AppLayout } from '../layouts/AppLayout';
import { PageHeader } from '../components/PageHeader';
import { LoadingBlock, ErrorBlock, EmptyState } from '../components/Feedback';
import { StatusChip } from '../components/StatusChip';
import { Money } from '../components/Money';
import { useAccounts } from '../api/accounts';
import { usePayments } from '../api/payments';
import type { PaymentResponse } from '../types/domain';

function monthKey(iso: string): string {
  const d = new Date(iso);
  return d.toLocaleString('en-US', { month: 'long', year: 'numeric' });
}

export function Statements() {
  const { data: accounts, isLoading: accountsLoading } = useAccounts();
  const { data: payments, isLoading: paymentsLoading, error } = usePayments();

  const [accountId, setAccountId] = useState<string>('');

  const activeAccountId = accountId || accounts?.[0]?.id || '';
  const account = accounts?.find((a) => a.id === activeAccountId);

  const months = useMemo(() => {
    if (!payments || !activeAccountId) return [];
    const relevant = payments.filter(
      (p) => p.sourceAccountId === activeAccountId || p.destinationAccountId === activeAccountId,
    );
    const grouped = new Map<string, PaymentResponse[]>();
    for (const p of relevant) {
      const key = monthKey(p.createdAt);
      grouped.set(key, [...(grouped.get(key) ?? []), p]);
    }
    return Array.from(grouped.entries());
  }, [payments, activeAccountId]);

  return (
    <AppLayout>
      <PageHeader title="Statements" subtitle="Your transaction history grouped by month, per account." />

      <Alert severity="info" sx={{ mb: 3 }}>
        This is a monthly view of your real transaction history. Downloadable PDF/CSV statement
        export isn't available in this demo yet.
      </Alert>

      {(accountsLoading || paymentsLoading) && <LoadingBlock />}
      {error && <ErrorBlock error={error} />}

      {accounts && accounts.length > 0 && (
        <TextField
          select
          label="Account"
          value={activeAccountId}
          onChange={(e) => setAccountId(e.target.value)}
          sx={{ mb: 3, minWidth: 280 }}
          size="small"
        >
          {accounts.map((a) => (
            <MenuItem key={a.id} value={a.id}>
              {a.accountType} · {a.maskedAccountNumber}
            </MenuItem>
          ))}
        </TextField>
      )}

      {months.length === 0 && !paymentsLoading && account && (
        <EmptyState message="No transaction history for this account yet." />
      )}

      <Stack spacing={1}>
        {months.map(([month, txns]) => (
          <Accordion key={month} disableGutters>
            <AccordionSummary expandIcon={<ExpandMoreIcon />}>
              <Typography fontWeight={600}>{month}</Typography>
              <Typography variant="body2" color="text.secondary" sx={{ ml: 2 }}>
                {txns.length} transaction{txns.length === 1 ? '' : 's'}
              </Typography>
            </AccordionSummary>
            <AccordionDetails>
              <TableContainer component={Paper} variant="outlined">
                <Table size="small">
                  <TableHead>
                    <TableRow>
                      <TableCell>Date</TableCell>
                      <TableCell>Purpose</TableCell>
                      <TableCell align="right">Amount</TableCell>
                      <TableCell>Status</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {txns.map((p) => (
                      <TableRow key={p.id}>
                        <TableCell>{new Date(p.createdAt).toLocaleDateString()}</TableCell>
                        <TableCell>{p.purpose || '—'}</TableCell>
                        <TableCell align="right">
                          {p.sourceAccountId === activeAccountId ? '-' : '+'}
                          <Money amount={p.amount} currency={p.currency} />
                        </TableCell>
                        <TableCell>
                          <StatusChip status={p.status} />
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </TableContainer>
            </AccordionDetails>
          </Accordion>
        ))}
      </Stack>
    </AppLayout>
  );
}
