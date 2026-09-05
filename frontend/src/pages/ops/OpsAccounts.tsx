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
  Tab,
  Tabs,
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
import { useAccountActions, useAccountRequestDecision, useAccountRequestsQueue, useAccountsQueue } from '../../api/accounts';
import { ApiError } from '../../api/client';
import type { AccountOpeningStatus, AccountResponse, AccountStatus } from '../../types/domain';

export function OpsAccounts() {
  const [tab, setTab] = useState<'accounts' | 'requests'>('requests');
  return (
    <OpsLayout>
      <PageHeader title="Accounts" subtitle="Account opening requests and lifecycle management." />
      <Paper sx={{ mb: 2 }}>
        <Tabs value={tab} onChange={(_, v) => setTab(v)}>
          <Tab value="requests" label="Opening requests" />
          <Tab value="accounts" label="All accounts" />
        </Tabs>
      </Paper>
      {tab === 'requests' ? <RequestsTab /> : <AccountsTab />}
    </OpsLayout>
  );
}

function RequestsTab() {
  const [status, setStatus] = useState<AccountOpeningStatus | 'ALL'>('ACCOUNT_REQUESTED');
  const { data: requests, isLoading, error } = useAccountRequestsQueue(status === 'ALL' ? undefined : status);
  const { startReview, approve, reject } = useAccountRequestDecision();
  const [rejectTarget, setRejectTarget] = useState<string | null>(null);
  const [reason, setReason] = useState('');
  const [actionError, setActionError] = useState<string | null>(null);

  async function submitReject() {
    if (!rejectTarget) return;
    setActionError(null);
    try {
      await reject.mutateAsync({ id: rejectTarget, reason });
      setRejectTarget(null);
      setReason('');
    } catch (err) {
      setActionError(err instanceof ApiError ? err.message : 'Unable to reject.');
    }
  }

  return (
    <>
      <Paper sx={{ p: 2, mb: 2 }}>
        <TextField select label="Status" value={status} onChange={(e) => setStatus(e.target.value as AccountOpeningStatus | 'ALL')} size="small" sx={{ minWidth: 200 }}>
          <MenuItem value="ALL">All statuses</MenuItem>
          {(['ACCOUNT_REQUESTED', 'UNDER_REVIEW', 'APPROVED', 'REJECTED'] as AccountOpeningStatus[]).map((s) => (
            <MenuItem key={s} value={s}>
              {s}
            </MenuItem>
          ))}
        </TextField>
      </Paper>

      {isLoading && <LoadingBlock />}
      {error && <ErrorBlock error={error} />}
      {requests && requests.length === 0 && <EmptyState message="Nothing in this queue." />}

      {requests && requests.length > 0 && (
        <TableContainer component={Paper}>
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>Requested</TableCell>
                <TableCell>Customer</TableCell>
                <TableCell>Type</TableCell>
                <TableCell>Status</TableCell>
                <TableCell align="right">Actions</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {requests.map((r) => (
                <TableRow key={r.id} hover>
                  <TableCell>{new Date(r.requestedAt).toLocaleString()}</TableCell>
                  <TableCell sx={{ fontFamily: 'monospace', fontSize: 12 }}>{r.customerId}</TableCell>
                  <TableCell>{r.accountType}</TableCell>
                  <TableCell>
                    <StatusChip status={r.status} />
                  </TableCell>
                  <TableCell align="right">
                    {r.status === 'ACCOUNT_REQUESTED' && (
                      <Button size="small" onClick={() => startReview.mutate(r.id)}>
                        Claim
                      </Button>
                    )}
                    {r.status === 'UNDER_REVIEW' && (
                      <>
                        <Button size="small" color="success" onClick={() => approve.mutate(r.id)}>
                          Approve
                        </Button>
                        <Button size="small" color="error" onClick={() => setRejectTarget(r.id)}>
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
        <DialogTitle>Reject account request</DialogTitle>
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
    </>
  );
}

function AccountsTab() {
  const [status, setStatus] = useState<AccountStatus | 'ALL'>('ALL');
  const { data: accounts, isLoading, error } = useAccountsQueue(status === 'ALL' ? undefined : status);
  const { freeze, unfreeze, close, requestBlock, updateLimits } = useAccountActions();

  const [reasonTarget, setReasonTarget] = useState<{ account: AccountResponse; action: 'freeze' | 'unfreeze' | 'close' | 'block' } | null>(null);
  const [reason, setReason] = useState('');
  const [limitsTarget, setLimitsTarget] = useState<AccountResponse | null>(null);
  const [perTxn, setPerTxn] = useState(0);
  const [daily, setDaily] = useState(0);
  const [actionError, setActionError] = useState<string | null>(null);

  async function submitReasonAction() {
    if (!reasonTarget) return;
    setActionError(null);
    try {
      const mutation = { freeze, unfreeze, close, block: requestBlock }[reasonTarget.action];
      await mutation.mutateAsync({ id: reasonTarget.account.id, reason });
      setReasonTarget(null);
      setReason('');
    } catch (err) {
      setActionError(err instanceof ApiError ? err.message : 'Unable to complete action.');
    }
  }

  async function submitLimits() {
    if (!limitsTarget) return;
    setActionError(null);
    try {
      await updateLimits.mutateAsync({ id: limitsTarget.id, request: { perTransactionLimit: perTxn, dailyLimit: daily } });
      setLimitsTarget(null);
    } catch (err) {
      setActionError(err instanceof ApiError ? err.message : 'Unable to update limits.');
    }
  }

  return (
    <>
      <Paper sx={{ p: 2, mb: 2 }}>
        <TextField select label="Status" value={status} onChange={(e) => setStatus(e.target.value as AccountStatus | 'ALL')} size="small" sx={{ minWidth: 200 }}>
          <MenuItem value="ALL">All statuses</MenuItem>
          {(['ACTIVE', 'FROZEN', 'BLOCKED', 'CLOSED'] as AccountStatus[]).map((s) => (
            <MenuItem key={s} value={s}>
              {s}
            </MenuItem>
          ))}
        </TextField>
      </Paper>

      {isLoading && <LoadingBlock />}
      {error && <ErrorBlock error={error} />}
      {accounts && accounts.length === 0 && <EmptyState message="No accounts match this filter." />}

      {accounts && accounts.length > 0 && (
        <TableContainer component={Paper}>
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>Account</TableCell>
                <TableCell>Type</TableCell>
                <TableCell>Status</TableCell>
                <TableCell align="right">Per-txn limit</TableCell>
                <TableCell align="right">Daily limit</TableCell>
                <TableCell align="right">Actions</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {accounts.map((a) => (
                <TableRow key={a.id} hover>
                  <TableCell>{a.maskedAccountNumber}</TableCell>
                  <TableCell>{a.accountType}</TableCell>
                  <TableCell>
                    <StatusChip status={a.status} />
                  </TableCell>
                  <TableCell align="right">
                    <Money amount={a.perTransactionLimit} currency={a.currency} />
                  </TableCell>
                  <TableCell align="right">
                    <Money amount={a.dailyLimit} currency={a.currency} />
                  </TableCell>
                  <TableCell align="right">
                    {a.status === 'ACTIVE' && (
                      <>
                        <Button size="small" onClick={() => setReasonTarget({ account: a, action: 'freeze' })}>
                          Freeze
                        </Button>
                        <Button size="small" color="error" onClick={() => setReasonTarget({ account: a, action: 'block' })}>
                          Block
                        </Button>
                      </>
                    )}
                    {a.status === 'FROZEN' && (
                      <Button size="small" onClick={() => setReasonTarget({ account: a, action: 'unfreeze' })}>
                        Unfreeze
                      </Button>
                    )}
                    {(a.status === 'ACTIVE' || a.status === 'FROZEN') && (
                      <Button
                        size="small"
                        onClick={() => {
                          setLimitsTarget(a);
                          setPerTxn(a.perTransactionLimit);
                          setDaily(a.dailyLimit);
                        }}
                      >
                        Limits
                      </Button>
                    )}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      )}

      <Dialog open={!!reasonTarget} onClose={() => setReasonTarget(null)} fullWidth maxWidth="xs">
        <DialogTitle>{reasonTarget?.action} account</DialogTitle>
        <DialogContent>
          {actionError && (
            <Alert severity="error" sx={{ mb: 2 }}>
              {actionError}
            </Alert>
          )}
          {reasonTarget?.action === 'block' && (
            <Alert severity="info" sx={{ mb: 2 }}>
              Blocking is maker-checker gated — this creates a request under Approvals.
            </Alert>
          )}
          <TextField label="Reason" value={reason} onChange={(e) => setReason(e.target.value)} fullWidth multiline minRows={2} sx={{ mt: 1 }} required />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setReasonTarget(null)}>Cancel</Button>
          <Button variant="contained" onClick={submitReasonAction} disabled={!reason}>
            Confirm
          </Button>
        </DialogActions>
      </Dialog>

      <Dialog open={!!limitsTarget} onClose={() => setLimitsTarget(null)} fullWidth maxWidth="xs">
        <DialogTitle>Update limits</DialogTitle>
        <DialogContent>
          {actionError && (
            <Alert severity="error" sx={{ mb: 2 }}>
              {actionError}
            </Alert>
          )}
          <TextField label="Per-transaction limit" type="number" value={perTxn} onChange={(e) => setPerTxn(Number(e.target.value))} fullWidth sx={{ mt: 1, mb: 2 }} />
          <TextField label="Daily limit" type="number" value={daily} onChange={(e) => setDaily(Number(e.target.value))} fullWidth />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setLimitsTarget(null)}>Cancel</Button>
          <Button variant="contained" onClick={submitLimits}>
            Save
          </Button>
        </DialogActions>
      </Dialog>
    </>
  );
}
