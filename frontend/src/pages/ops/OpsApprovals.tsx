import { useState } from 'react';
import {
  Alert,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Paper,
  Tab,
  Tabs,
  TextField,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
} from '@mui/material';
import { OpsLayout } from '../../layouts/OpsLayout';
import { PageHeader } from '../../components/PageHeader';
import { LoadingBlock, ErrorBlock, EmptyState } from '../../components/Feedback';
import { StatusChip } from '../../components/StatusChip';
import { GATING_SERVICES, useApprovalDecision, useApprovalQueue, type GatingService } from '../../api/opsApprovals';
import { ApiError } from '../../api/client';
import type { ApprovalRequestResponse } from '../../types/domain';

export function OpsApprovals() {
  const [service, setService] = useState<GatingService>('accounts');
  const { data: queue, isLoading, error } = useApprovalQueue(service, 'PENDING_APPROVAL');
  const { approve, reject } = useApprovalDecision(service);

  const [selected, setSelected] = useState<ApprovalRequestResponse | null>(null);
  const [decision, setDecision] = useState<'approve' | 'reject' | null>(null);
  const [notes, setNotes] = useState('');
  const [actionError, setActionError] = useState<string | null>(null);

  async function submitDecision() {
    if (!selected || !decision) return;
    setActionError(null);
    try {
      const mutation = decision === 'approve' ? approve : reject;
      await mutation.mutateAsync({ id: selected.id, notes: notes || undefined });
      setSelected(null);
      setDecision(null);
      setNotes('');
    } catch (err) {
      setActionError(err instanceof ApiError ? err.message : 'Unable to record decision.');
    }
  }

  return (
    <OpsLayout>
      <PageHeader
        title="Approvals"
        subtitle="Maker-checker review queues. You cannot approve a request you created yourself — the backend enforces this even if this UI didn't."
      />

      <Paper sx={{ mb: 2 }}>
        <Tabs value={service} onChange={(_, v) => setService(v)} variant="scrollable">
          {GATING_SERVICES.map((s) => (
            <Tab key={s.key} value={s.key} label={s.label} />
          ))}
        </Tabs>
      </Paper>

      {isLoading && <LoadingBlock />}
      {error && <ErrorBlock error={error} />}
      {queue && queue.length === 0 && <EmptyState message="Nothing pending in this queue." />}

      {queue && queue.length > 0 && (
        <TableContainer component={Paper}>
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>Action</TableCell>
                <TableCell>Resource</TableCell>
                <TableCell>Reason</TableCell>
                <TableCell>Requested</TableCell>
                <TableCell align="right">Decision</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {queue.map((request) => (
                <TableRow key={request.id} hover>
                  <TableCell>{request.actionType}</TableCell>
                  <TableCell sx={{ fontFamily: 'monospace', fontSize: 12 }}>{request.resourceId}</TableCell>
                  <TableCell>{request.reason || request.requestedStatus || '—'}</TableCell>
                  <TableCell>{new Date(request.requestedAt).toLocaleString()}</TableCell>
                  <TableCell align="right">
                    <Button
                      size="small"
                      color="success"
                      onClick={() => {
                        setSelected(request);
                        setDecision('approve');
                      }}
                    >
                      Approve
                    </Button>
                    <Button
                      size="small"
                      color="error"
                      onClick={() => {
                        setSelected(request);
                        setDecision('reject');
                      }}
                    >
                      Reject
                    </Button>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      )}

      <Dialog open={!!selected} onClose={() => setSelected(null)} fullWidth maxWidth="xs">
        <DialogTitle>{decision === 'approve' ? 'Approve' : 'Reject'} request</DialogTitle>
        <DialogContent>
          {actionError && (
            <Alert severity="error" sx={{ mb: 2 }}>
              {actionError}
            </Alert>
          )}
          {selected && (
            <Alert severity="info" sx={{ mb: 2 }}>
              {selected.actionType} on <StatusChip status={selected.status} />
            </Alert>
          )}
          <TextField
            label="Notes (optional)"
            value={notes}
            onChange={(e) => setNotes(e.target.value)}
            fullWidth
            multiline
            minRows={2}
            sx={{ mt: 1 }}
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setSelected(null)}>Cancel</Button>
          <Button variant="contained" onClick={submitDecision} disabled={approve.isPending || reject.isPending}>
            Confirm
          </Button>
        </DialogActions>
      </Dialog>
    </OpsLayout>
  );
}
