import { useState } from 'react';
import {
  Alert,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  MenuItem,
  Paper,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
} from '@mui/material';
import { OpsLayout } from '../../layouts/OpsLayout';
import { PageHeader } from '../../components/PageHeader';
import { LoadingBlock, ErrorBlock, EmptyState } from '../../components/Feedback';
import { StatusChip } from '../../components/StatusChip';
import { Money } from '../../components/Money';
import { useReconciliationActions, useReconciliationRecords } from '../../api/ledger';
import { ApiError } from '../../api/client';
import type { ReconciliationStatus } from '../../types/domain';

const STATUS_OPTIONS: ReconciliationStatus[] = ['MATCHED', 'MISMATCHED', 'PENDING', 'INVESTIGATION', 'RESOLVED'];

export function OpsReconciliation() {
  const [status, setStatus] = useState<ReconciliationStatus | 'ALL'>('MISMATCHED');
  const { data: records, isLoading, error } = useReconciliationRecords(status === 'ALL' ? undefined : status);
  const { startInvestigation, resolve } = useReconciliationActions();

  const [resolveTarget, setResolveTarget] = useState<string | null>(null);
  const [notes, setNotes] = useState('');
  const [actionError, setActionError] = useState<string | null>(null);

  async function submitResolve() {
    if (!resolveTarget) return;
    setActionError(null);
    try {
      await resolve.mutateAsync({ id: resolveTarget, notes: notes || undefined });
      setResolveTarget(null);
      setNotes('');
    } catch (err) {
      setActionError(err instanceof ApiError ? err.message : 'Unable to resolve.');
    }
  }

  return (
    <OpsLayout>
      <PageHeader title="Reconciliation" subtitle="Ledger transactions matched against the external feed." />

      <Paper sx={{ p: 2, mb: 2 }}>
        <TextField select label="Status" value={status} onChange={(e) => setStatus(e.target.value as ReconciliationStatus | 'ALL')} size="small" sx={{ minWidth: 200 }}>
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
      {records && records.length === 0 && <EmptyState message="Nothing matches this filter." />}

      {records && records.length > 0 && (
        <TableContainer component={Paper}>
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>Transaction</TableCell>
                <TableCell align="right">Internal</TableCell>
                <TableCell align="right">External</TableCell>
                <TableCell>Status</TableCell>
                <TableCell>Mismatch reason</TableCell>
                <TableCell align="right">Actions</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {records.map((r) => (
                <TableRow key={r.id} hover>
                  <TableCell sx={{ fontFamily: 'monospace', fontSize: 12 }}>{r.transactionId}</TableCell>
                  <TableCell align="right">
                    <Money amount={r.internalAmount} currency={r.internalCurrency} />
                  </TableCell>
                  <TableCell align="right">
                    {r.externalAmount !== undefined ? <Money amount={r.externalAmount} currency={r.externalCurrency} /> : '—'}
                  </TableCell>
                  <TableCell>
                    <StatusChip status={r.status} />
                  </TableCell>
                  <TableCell>{r.mismatchReason || '—'}</TableCell>
                  <TableCell align="right">
                    {r.status === 'MISMATCHED' && (
                      <Button size="small" onClick={() => startInvestigation.mutate(r.id)}>
                        Investigate
                      </Button>
                    )}
                    {r.status === 'INVESTIGATION' && (
                      <Button size="small" color="success" onClick={() => setResolveTarget(r.id)}>
                        Resolve
                      </Button>
                    )}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      )}

      <Dialog open={!!resolveTarget} onClose={() => setResolveTarget(null)} fullWidth maxWidth="xs">
        <DialogTitle>Resolve mismatch</DialogTitle>
        <DialogContent>
          {actionError && (
            <Alert severity="error" sx={{ mb: 2 }}>
              {actionError}
            </Alert>
          )}
          <TextField label="Resolution notes" value={notes} onChange={(e) => setNotes(e.target.value)} fullWidth multiline minRows={2} sx={{ mt: 1 }} />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setResolveTarget(null)}>Cancel</Button>
          <Button variant="contained" onClick={submitResolve}>
            Resolve
          </Button>
        </DialogActions>
      </Dialog>
    </OpsLayout>
  );
}
