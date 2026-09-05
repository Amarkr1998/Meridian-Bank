import { useState } from 'react';
import {
  Alert,
  Button,
  Card,
  CardContent,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  MenuItem,
  Paper,
  Stack,
  TextField,
  Typography,
} from '@mui/material';
import { OpsLayout } from '../../layouts/OpsLayout';
import { PageHeader } from '../../components/PageHeader';
import { LoadingBlock, ErrorBlock, EmptyState } from '../../components/Feedback';
import { StatusChip } from '../../components/StatusChip';
import { useSupportDecision, useSupportQueue } from '../../api/support';
import { ApiError } from '../../api/client';
import type { SupportRequestStatus } from '../../types/domain';

const STATUS_OPTIONS: SupportRequestStatus[] = ['OPEN', 'IN_PROGRESS', 'RESOLVED'];

export function OpsSupport() {
  const [status, setStatus] = useState<SupportRequestStatus | 'ALL'>('OPEN');
  const { data: requests, isLoading, error } = useSupportQueue(status === 'ALL' ? undefined : status);
  const { startProgress, resolve } = useSupportDecision();

  const [resolveTarget, setResolveTarget] = useState<string | null>(null);
  const [notes, setNotes] = useState('');
  const [actionError, setActionError] = useState<string | null>(null);

  async function submitResolve() {
    if (!resolveTarget) return;
    setActionError(null);
    try {
      await resolve.mutateAsync({ id: resolveTarget, notes });
      setResolveTarget(null);
      setNotes('');
    } catch (err) {
      setActionError(err instanceof ApiError ? err.message : 'Unable to resolve.');
    }
  }

  return (
    <OpsLayout>
      <PageHeader title="Support" subtitle="Customer support ticket queue." />

      <Paper sx={{ p: 2, mb: 2 }}>
        <TextField select label="Status" value={status} onChange={(e) => setStatus(e.target.value as SupportRequestStatus | 'ALL')} size="small" sx={{ minWidth: 200 }}>
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
      {requests && requests.length === 0 && <EmptyState message="Nothing in this queue." />}

      <Stack spacing={2}>
        {requests?.map((r) => (
          <Card key={r.id}>
            <CardContent>
              <Stack direction="row" justifyContent="space-between" alignItems="flex-start">
                <Stack>
                  <Typography variant="subtitle1" fontWeight={600}>
                    {r.subject}
                  </Typography>
                  <Typography variant="caption" color="text.secondary">
                    {r.category} · {new Date(r.createdAt).toLocaleString()} · Customer {r.customerId}
                  </Typography>
                  <Typography variant="body2" sx={{ mt: 1 }}>
                    {r.description}
                  </Typography>
                </Stack>
                <Stack alignItems="flex-end" spacing={1}>
                  <StatusChip status={r.status} />
                  {r.status === 'OPEN' && (
                    <Button size="small" onClick={() => startProgress.mutate(r.id)}>
                      Start progress
                    </Button>
                  )}
                  {r.status === 'IN_PROGRESS' && (
                    <Button size="small" color="success" onClick={() => setResolveTarget(r.id)}>
                      Resolve
                    </Button>
                  )}
                </Stack>
              </Stack>
            </CardContent>
          </Card>
        ))}
      </Stack>

      <Dialog open={!!resolveTarget} onClose={() => setResolveTarget(null)} fullWidth maxWidth="sm">
        <DialogTitle>Resolve support request</DialogTitle>
        <DialogContent>
          {actionError && (
            <Alert severity="error" sx={{ mb: 2 }}>
              {actionError}
            </Alert>
          )}
          <TextField label="Resolution notes" value={notes} onChange={(e) => setNotes(e.target.value)} fullWidth multiline minRows={3} sx={{ mt: 1 }} required />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setResolveTarget(null)}>Cancel</Button>
          <Button variant="contained" onClick={submitResolve} disabled={!notes}>
            Resolve
          </Button>
        </DialogActions>
      </Dialog>
    </OpsLayout>
  );
}
