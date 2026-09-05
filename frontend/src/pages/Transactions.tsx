import { useMemo, useState } from 'react';
import {
  MenuItem,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
  Paper,
} from '@mui/material';
import { AppLayout } from '../layouts/AppLayout';
import { PageHeader } from '../components/PageHeader';
import { LoadingBlock, ErrorBlock, EmptyState } from '../components/Feedback';
import { StatusChip } from '../components/StatusChip';
import { Money } from '../components/Money';
import { useAccounts } from '../api/accounts';
import { usePayments } from '../api/payments';
import type { TransactionStatus } from '../types/domain';

const STATUS_OPTIONS: TransactionStatus[] = ['SUCCESS', 'FAILED', 'PROCESSING', 'RISK_CHECK', 'VALIDATING', 'INITIATED'];

export function Transactions() {
  const [accountFilter, setAccountFilter] = useState('ALL');
  const [statusFilter, setStatusFilter] = useState<TransactionStatus | 'ALL'>('ALL');

  const { data: accounts } = useAccounts();
  const { data: payments, isLoading, error } = usePayments(statusFilter === 'ALL' ? undefined : statusFilter);

  const filtered = useMemo(() => {
    if (!payments) return [];
    if (accountFilter === 'ALL') return payments;
    return payments.filter((p) => p.sourceAccountId === accountFilter || p.destinationAccountId === accountFilter);
  }, [payments, accountFilter]);

  return (
    <AppLayout>
      <PageHeader title="Transactions" subtitle="Full history of your payments." />

      <Paper sx={{ p: 2, mb: 2, display: 'flex', gap: 2, flexWrap: 'wrap' }}>
        <TextField select label="Account" value={accountFilter} onChange={(e) => setAccountFilter(e.target.value)} sx={{ minWidth: 220 }} size="small">
          <MenuItem value="ALL">All accounts</MenuItem>
          {accounts?.map((a) => (
            <MenuItem key={a.id} value={a.id}>
              {a.accountType} · {a.maskedAccountNumber}
            </MenuItem>
          ))}
        </TextField>
        <TextField
          select
          label="Status"
          value={statusFilter}
          onChange={(e) => setStatusFilter(e.target.value as TransactionStatus | 'ALL')}
          sx={{ minWidth: 180 }}
          size="small"
        >
          <MenuItem value="ALL">All statuses</MenuItem>
          {STATUS_OPTIONS.map((s) => (
            <MenuItem key={s} value={s}>
              {s}
            </MenuItem>
          ))}
        </TextField>
      </Paper>

      {isLoading && <LoadingBlock />}
      {error && <ErrorBlock error={error} />}
      {filtered.length === 0 && !isLoading && <EmptyState message="No transactions match these filters." />}

      {filtered.length > 0 && (
        <TableContainer component={Paper}>
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
              {filtered.map((p) => (
                <TableRow key={p.id} hover>
                  <TableCell>{new Date(p.createdAt).toLocaleString()}</TableCell>
                  <TableCell>{p.purpose || '—'}</TableCell>
                  <TableCell align="right">
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
      )}
    </AppLayout>
  );
}
