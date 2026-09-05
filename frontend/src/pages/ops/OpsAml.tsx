import { useState } from 'react';
import { Alert, Button, Dialog, DialogActions, DialogContent, DialogTitle, MenuItem, Paper, Table, TableBody, TableCell, TableContainer, TableHead, TableRow, TextField } from '@mui/material';
import { OpsLayout } from '../../layouts/OpsLayout';
import { PageHeader } from '../../components/PageHeader';
import { LoadingBlock, ErrorBlock, EmptyState } from '../../components/Feedback';
import { StatusChip } from '../../components/StatusChip';
import { Money } from '../../components/Money';
import { useAmlAlertActions, useAmlAlerts } from '../../api/fraud';
import { ApiError } from '../../api/client';
import type { AmlAlertResponse, AmlAlertStatus } from '../../types/domain';

const STATUS_OPTIONS: AmlAlertStatus[] = ['OPEN', 'IN_REVIEW', 'CLEARED', 'ESCALATED'];

export function OpsAml() {
  const [status, setStatus] = useState<AmlAlertStatus | 'ALL'>('OPEN');
  const { data: alerts, isLoading, error } = useAmlAlerts({ status: status === 'ALL' ? undefined : status });
  const { startReview, clear, escalate } = useAmlAlertActions();

  const [actionTarget, setActionTarget] = useState<{ alert: AmlAlertResponse; action: 'clear' | 'escalate' } | null>(null);
  const [notes, setNotes] = useState('');
  const [actionError, setActionError] = useState<string | null>(null);

  async function submitAction() {
    if (!actionTarget) return;
    setActionError(null);
    try {
      const mutation = actionTarget.action === 'clear' ? clear : escalate;
      await mutation.mutateAsync({ id: actionTarget.alert.id, notes: notes || undefined });
      setActionTarget(null);
      setNotes('');
    } catch (err) {
      setActionError(err instanceof ApiError ? err.message : 'Unable to record decision.');
    }
  }

  return (
    <OpsLayout>
      <PageHeader title="AML" subtitle="Anti-money-laundering signal review — compliance-only." />

      <Paper sx={{ p: 2, mb: 2 }}>
        <TextField select label="Status" value={status} onChange={(e) => setStatus(e.target.value as AmlAlertStatus | 'ALL')} size="small" sx={{ minWidth: 200 }}>
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
      {alerts && alerts.length === 0 && <EmptyState message="No AML alerts match this filter." />}

      {alerts && alerts.length > 0 && (
        <TableContainer component={Paper}>
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>Created</TableCell>
                <TableCell>Customer</TableCell>
                <TableCell>Signal</TableCell>
                <TableCell align="right">Amount</TableCell>
                <TableCell>Status</TableCell>
                <TableCell align="right">Actions</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {alerts.map((a) => (
                <TableRow key={a.id} hover>
                  <TableCell>{new Date(a.createdAt).toLocaleString()}</TableCell>
                  <TableCell sx={{ fontFamily: 'monospace', fontSize: 12 }}>{a.customerId}</TableCell>
                  <TableCell>{a.signalCode}</TableCell>
                  <TableCell align="right">
                    <Money amount={a.amount} currency={a.currency} />
                  </TableCell>
                  <TableCell>
                    <StatusChip status={a.status} />
                  </TableCell>
                  <TableCell align="right">
                    {a.status === 'OPEN' && (
                      <Button size="small" onClick={() => startReview.mutate(a.id)}>
                        Claim
                      </Button>
                    )}
                    {a.status === 'IN_REVIEW' && (
                      <>
                        <Button size="small" color="success" onClick={() => setActionTarget({ alert: a, action: 'clear' })}>
                          Clear
                        </Button>
                        <Button size="small" color="warning" onClick={() => setActionTarget({ alert: a, action: 'escalate' })}>
                          Escalate
                        </Button>
                      </>
                    )}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      )}

      <Dialog open={!!actionTarget} onClose={() => setActionTarget(null)} fullWidth maxWidth="xs">
        <DialogTitle>{actionTarget?.action === 'clear' ? 'Clear' : 'Escalate'} AML alert</DialogTitle>
        <DialogContent>
          {actionError && (
            <Alert severity="error" sx={{ mb: 2 }}>
              {actionError}
            </Alert>
          )}
          <TextField label="Notes (optional)" value={notes} onChange={(e) => setNotes(e.target.value)} fullWidth multiline minRows={2} sx={{ mt: 1 }} />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setActionTarget(null)}>Cancel</Button>
          <Button variant="contained" onClick={submitAction}>
            Confirm
          </Button>
        </DialogActions>
      </Dialog>
    </OpsLayout>
  );
}
