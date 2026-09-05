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
import { useCustomersQueue, useRequestCustomerStatusChange } from '../../api/customers';
import { ApiError } from '../../api/client';
import type { CustomerResponse, CustomerStatus } from '../../types/domain';

const STATUS_OPTIONS: CustomerStatus[] = ['ACTIVE', 'INACTIVE', 'BLOCKED', 'SUSPENDED'];

export function OpsCustomers() {
  const [filter, setFilter] = useState<CustomerStatus | 'ALL'>('ALL');
  const { data: customers, isLoading, error } = useCustomersQueue(filter === 'ALL' ? undefined : filter);
  const [target, setTarget] = useState<CustomerResponse | null>(null);

  return (
    <OpsLayout>
      <PageHeader title="Customers" subtitle="Customer directory and status management." />

      <Paper sx={{ p: 2, mb: 2 }}>
        <TextField select label="Status" value={filter} onChange={(e) => setFilter(e.target.value as CustomerStatus | 'ALL')} size="small" sx={{ minWidth: 200 }}>
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
      {customers && customers.length === 0 && <EmptyState message="No customers match this filter." />}

      {customers && customers.length > 0 && (
        <TableContainer component={Paper}>
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>Name</TableCell>
                <TableCell>Email</TableCell>
                <TableCell>Contact verified</TableCell>
                <TableCell>Status</TableCell>
                <TableCell>Since</TableCell>
                <TableCell align="right">Actions</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {customers.map((c) => (
                <TableRow key={c.id} hover>
                  <TableCell>
                    {c.firstName} {c.lastName}
                  </TableCell>
                  <TableCell>{c.email}</TableCell>
                  <TableCell>{c.contactVerified ? 'Yes' : 'No'}</TableCell>
                  <TableCell>
                    <StatusChip status={c.status} />
                  </TableCell>
                  <TableCell>{new Date(c.createdAt).toLocaleDateString()}</TableCell>
                  <TableCell align="right">
                    <Button size="small" onClick={() => setTarget(c)}>
                      Change status
                    </Button>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      )}

      {target && <StatusChangeDialog customer={target} onClose={() => setTarget(null)} />}
    </OpsLayout>
  );
}

function StatusChangeDialog({ customer, onClose }: { customer: CustomerResponse; onClose: () => void }) {
  const [newStatus, setNewStatus] = useState<CustomerStatus>(customer.status);
  const [reason, setReason] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitted, setSubmitted] = useState(false);
  const requestChange = useRequestCustomerStatusChange(customer.id);

  async function submit() {
    setError(null);
    try {
      await requestChange.mutateAsync({ status: newStatus, reason });
      setSubmitted(true);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Unable to submit request.');
    }
  }

  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="xs">
      <DialogTitle>
        Change status: {customer.firstName} {customer.lastName}
      </DialogTitle>
      <DialogContent>
        {error && (
          <Alert severity="error" sx={{ mb: 2 }}>
            {error}
          </Alert>
        )}
        {submitted ? (
          <Alert severity="success">
            Submitted for approval — a different OPERATIONS/ADMIN staff member must approve it
            under Approvals before it takes effect.
          </Alert>
        ) : (
          <>
            <TextField
              select
              label="New status"
              value={newStatus}
              onChange={(e) => setNewStatus(e.target.value as CustomerStatus)}
              fullWidth
              sx={{ mt: 1, mb: 2 }}
            >
              {(['ACTIVE', 'INACTIVE', 'BLOCKED', 'SUSPENDED'] as CustomerStatus[]).map((s) => (
                <MenuItem key={s} value={s}>
                  {s}
                </MenuItem>
              ))}
            </TextField>
            <TextField label="Reason" value={reason} onChange={(e) => setReason(e.target.value)} fullWidth multiline minRows={2} required />
          </>
        )}
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>{submitted ? 'Close' : 'Cancel'}</Button>
        {!submitted && (
          <Button variant="contained" onClick={submit} disabled={!reason || newStatus === customer.status}>
            Submit
          </Button>
        )}
      </DialogActions>
    </Dialog>
  );
}
