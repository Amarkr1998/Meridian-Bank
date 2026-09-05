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
import { useKycDecision, useKycQueue } from '../../api/customers';
import { ApiError } from '../../api/client';
import type { KycRecordResponse, KycStatus } from '../../types/domain';

const STATUS_OPTIONS: KycStatus[] = ['KYC_PENDING', 'KYC_IN_REVIEW', 'KYC_VERIFIED', 'KYC_REJECTED'];

export function OpsKyc() {
  const [status, setStatus] = useState<KycStatus | 'ALL'>('KYC_PENDING');
  const { data: queue, isLoading, error } = useKycQueue(status === 'ALL' ? undefined : status);
  const { startReview, approve, reject } = useKycDecision();

  const [rejectTarget, setRejectTarget] = useState<KycRecordResponse | null>(null);
  const [reason, setReason] = useState('');
  const [actionError, setActionError] = useState<string | null>(null);

  async function submitReject() {
    if (!rejectTarget) return;
    setActionError(null);
    try {
      await reject.mutateAsync({ kycId: rejectTarget.id, reason });
      setRejectTarget(null);
      setReason('');
    } catch (err) {
      setActionError(err instanceof ApiError ? err.message : 'Unable to reject.');
    }
  }

  return (
    <OpsLayout>
      <PageHeader title="KYC Review" subtitle="Identity verification submissions awaiting a decision." />

      <Paper sx={{ p: 2, mb: 2 }}>
        <TextField select label="Status" value={status} onChange={(e) => setStatus(e.target.value as KycStatus | 'ALL')} size="small" sx={{ minWidth: 200 }}>
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
      {queue && queue.length === 0 && <EmptyState message="Nothing in this queue." />}

      {queue && queue.length > 0 && (
        <TableContainer component={Paper}>
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>Submitted</TableCell>
                <TableCell>Customer</TableCell>
                <TableCell>Nationality</TableCell>
                <TableCell>Occupation</TableCell>
                <TableCell>Documents</TableCell>
                <TableCell>Status</TableCell>
                <TableCell align="right">Actions</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {queue.map((k) => (
                <TableRow key={k.id} hover>
                  <TableCell>{new Date(k.submittedAt).toLocaleString()}</TableCell>
                  <TableCell sx={{ fontFamily: 'monospace', fontSize: 12 }}>{k.customerId}</TableCell>
                  <TableCell>{k.nationality}</TableCell>
                  <TableCell>{k.occupation}</TableCell>
                  <TableCell>{k.documents.map((d) => d.documentType).join(', ')}</TableCell>
                  <TableCell>
                    <StatusChip status={k.status} />
                  </TableCell>
                  <TableCell align="right">
                    {k.status === 'KYC_PENDING' && (
                      <Button size="small" onClick={() => startReview.mutate(k.id)}>
                        Claim
                      </Button>
                    )}
                    {k.status === 'KYC_IN_REVIEW' && (
                      <>
                        <Button size="small" color="success" onClick={() => approve.mutate(k.id)}>
                          Approve
                        </Button>
                        <Button size="small" color="error" onClick={() => setRejectTarget(k)}>
                          Reject
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

      <Dialog open={!!rejectTarget} onClose={() => setRejectTarget(null)} fullWidth maxWidth="xs">
        <DialogTitle>Reject KYC submission</DialogTitle>
        <DialogContent>
          {actionError && (
            <Alert severity="error" sx={{ mb: 2 }}>
              {actionError}
            </Alert>
          )}
          <TextField label="Reason" value={reason} onChange={(e) => setReason(e.target.value)} fullWidth multiline minRows={2} sx={{ mt: 1 }} required />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setRejectTarget(null)}>Cancel</Button>
          <Button variant="contained" color="error" onClick={submitReject} disabled={!reason}>
            Reject
          </Button>
        </DialogActions>
      </Dialog>
    </OpsLayout>
  );
}
