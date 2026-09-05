import { useState } from 'react';
import { MenuItem, Paper, Table, TableBody, TableCell, TableContainer, TableHead, TableRow, TextField } from '@mui/material';
import { OpsLayout } from '../../layouts/OpsLayout';
import { PageHeader } from '../../components/PageHeader';
import { LoadingBlock, ErrorBlock, EmptyState } from '../../components/Feedback';
import { StatusChip } from '../../components/StatusChip';
import { Money } from '../../components/Money';
import { useOpsPayments } from '../../api/payments';
import type { TransactionStatus } from '../../types/domain';

const STATUS_OPTIONS: TransactionStatus[] = ['SUCCESS', 'FAILED', 'PROCESSING', 'RISK_CHECK', 'VALIDATING', 'INITIATED'];

export function OpsTransactions() {
  const [customerId, setCustomerId] = useState('');
  const [status, setStatus] = useState<TransactionStatus | 'ALL'>('ALL');

  const { data: payments, isLoading, error } = useOpsPayments({
    customerId: customerId.trim() || undefined,
    status: status === 'ALL' ? undefined : status,
  });

  return (
    <OpsLayout>
      <PageHeader title="Transactions" subtitle="All customer payments, across every account." />

      <Paper sx={{ p: 2, mb: 2, display: 'flex', gap: 2, flexWrap: 'wrap' }}>
        <TextField
          label="Customer ID"
          value={customerId}
          onChange={(e) => setCustomerId(e.target.value)}
          size="small"
          sx={{ minWidth: 300 }}
          placeholder="Filter by customer UUID"
        />
        <TextField
          select
          label="Status"
          value={status}
          onChange={(e) => setStatus(e.target.value as TransactionStatus | 'ALL')}
          size="small"
          sx={{ minWidth: 180 }}
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
      {payments && payments.length === 0 && <EmptyState message="No transactions match these filters." />}

      {payments && payments.length > 0 && (
        <TableContainer component={Paper}>
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>Date</TableCell>
                <TableCell>Customer/Source Account</TableCell>
                <TableCell>Purpose</TableCell>
                <TableCell align="right">Amount</TableCell>
                <TableCell>Status</TableCell>
                <TableCell>Failure</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {payments.map((p) => (
                <TableRow key={p.id} hover>
                  <TableCell>{new Date(p.createdAt).toLocaleString()}</TableCell>
                  <TableCell sx={{ fontFamily: 'monospace', fontSize: 12 }}>{p.sourceAccountId}</TableCell>
                  <TableCell>{p.purpose || '—'}</TableCell>
                  <TableCell align="right">
                    <Money amount={p.amount} currency={p.currency} />
                  </TableCell>
                  <TableCell>
                    <StatusChip status={p.status} />
                  </TableCell>
                  <TableCell>{p.failureCode || '—'}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      )}
    </OpsLayout>
  );
}
