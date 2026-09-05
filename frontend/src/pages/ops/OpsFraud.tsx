import { useState } from 'react';
import {
  Alert,
  Button,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  MenuItem,
  Paper,
  Switch,
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
import { useFraudAlertActions, useFraudAlerts, useFraudRules, useRequestFraudRuleUpdate } from '../../api/fraud';
import { ApiError } from '../../api/client';
import type { FraudAlertResponse, FraudAlertStatus, FraudRuleResponse } from '../../types/domain';

const STATUS_OPTIONS: FraudAlertStatus[] = ['OPEN', 'IN_REVIEW', 'CLEARED', 'ESCALATED', 'CONFIRMED_FRAUD'];

export function OpsFraud() {
  const [tab, setTab] = useState<'alerts' | 'rules'>('alerts');

  return (
    <OpsLayout>
      <PageHeader title="Fraud" subtitle="Alert review queue and fraud rule configuration." />
      <Paper sx={{ mb: 2 }}>
        <Tabs value={tab} onChange={(_, v) => setTab(v)}>
          <Tab value="alerts" label="Alerts" />
          <Tab value="rules" label="Rules" />
        </Tabs>
      </Paper>
      {tab === 'alerts' ? <AlertsTab /> : <RulesTab />}
    </OpsLayout>
  );
}

function AlertsTab() {
  const [status, setStatus] = useState<FraudAlertStatus | 'ALL'>('OPEN');
  const { data: alerts, isLoading, error } = useFraudAlerts({ status: status === 'ALL' ? undefined : status });
  const { startReview, clear, escalate, confirm } = useFraudAlertActions();

  const [actionTarget, setActionTarget] = useState<{ alert: FraudAlertResponse; action: 'clear' | 'escalate' | 'confirm' } | null>(null);
  const [notes, setNotes] = useState('');
  const [actionError, setActionError] = useState<string | null>(null);

  async function submitAction() {
    if (!actionTarget) return;
    setActionError(null);
    try {
      const mutation = { clear, escalate, confirm }[actionTarget.action];
      await mutation.mutateAsync({ id: actionTarget.alert.id, notes: notes || undefined });
      setActionTarget(null);
      setNotes('');
    } catch (err) {
      setActionError(err instanceof ApiError ? err.message : 'Unable to record decision.');
    }
  }

  return (
    <>
      <Paper sx={{ p: 2, mb: 2 }}>
        <TextField
          select
          label="Status"
          value={status}
          onChange={(e) => setStatus(e.target.value as FraudAlertStatus | 'ALL')}
          size="small"
          sx={{ minWidth: 200 }}
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
      {alerts && alerts.length === 0 && <EmptyState message="No alerts match this filter." />}

      {alerts && alerts.length > 0 && (
        <TableContainer component={Paper}>
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>Created</TableCell>
                <TableCell>Customer</TableCell>
                <TableCell align="right">Amount</TableCell>
                <TableCell>Score</TableCell>
                <TableCell>Decision</TableCell>
                <TableCell>Status</TableCell>
                <TableCell align="right">Actions</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {alerts.map((a) => (
                <TableRow key={a.id} hover>
                  <TableCell>{new Date(a.createdAt).toLocaleString()}</TableCell>
                  <TableCell sx={{ fontFamily: 'monospace', fontSize: 12 }}>{a.customerId}</TableCell>
                  <TableCell align="right">
                    <Money amount={a.amount} currency={a.currency} />
                  </TableCell>
                  <TableCell>{a.score}</TableCell>
                  <TableCell>
                    <Chip size="small" label={a.decision} color={a.decision === 'BLOCK' ? 'error' : a.decision === 'REVIEW' ? 'warning' : 'default'} />
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
                        <Button size="small" color="error" onClick={() => setActionTarget({ alert: a, action: 'confirm' })}>
                          Confirm fraud
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
        <DialogTitle>Submit for approval</DialogTitle>
        <DialogContent>
          {actionError && (
            <Alert severity="error" sx={{ mb: 2 }}>
              {actionError}
            </Alert>
          )}
          <Alert severity="info" sx={{ mb: 2 }}>
            This creates a maker-checker request — a different staff member must approve it under
            Approvals before it takes effect.
          </Alert>
          <TextField label="Notes (optional)" value={notes} onChange={(e) => setNotes(e.target.value)} fullWidth multiline minRows={2} />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setActionTarget(null)}>Cancel</Button>
          <Button variant="contained" onClick={submitAction}>
            Submit
          </Button>
        </DialogActions>
      </Dialog>
    </>
  );
}

function RulesTab() {
  const { data: rules, isLoading, error } = useFraudRules();
  const requestUpdate = useRequestFraudRuleUpdate();
  const [editing, setEditing] = useState<FraudRuleResponse | null>(null);
  const [weight, setWeight] = useState(0);
  const [threshold, setThreshold] = useState(0);
  const [enabled, setEnabled] = useState(true);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [submitted, setSubmitted] = useState(false);

  function openEdit(rule: FraudRuleResponse) {
    setEditing(rule);
    setWeight(rule.weight);
    setThreshold(rule.thresholdNumeric ?? 0);
    setEnabled(rule.enabled);
    setSubmitError(null);
    setSubmitted(false);
  }

  async function submit() {
    if (!editing) return;
    setSubmitError(null);
    try {
      await requestUpdate.mutateAsync({
        id: editing.id,
        request: { weight, thresholdNumeric: threshold, enabled },
      });
      setSubmitted(true);
    } catch (err) {
      setSubmitError(err instanceof ApiError ? err.message : 'Unable to submit change.');
    }
  }

  return (
    <>
      {isLoading && <LoadingBlock />}
      {error && <ErrorBlock error={error} />}
      {rules && rules.length > 0 && (
        <TableContainer component={Paper}>
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>Rule</TableCell>
                <TableCell>Category</TableCell>
                <TableCell align="right">Weight</TableCell>
                <TableCell align="right">Threshold</TableCell>
                <TableCell>Enabled</TableCell>
                <TableCell align="right" />
              </TableRow>
            </TableHead>
            <TableBody>
              {rules.map((r) => (
                <TableRow key={r.id} hover>
                  <TableCell>
                    {r.ruleCode}
                    <br />
                    <span style={{ opacity: 0.6, fontSize: 12 }}>{r.description}</span>
                  </TableCell>
                  <TableCell>{r.category}</TableCell>
                  <TableCell align="right">{r.weight}</TableCell>
                  <TableCell align="right">{r.thresholdNumeric ?? '—'}</TableCell>
                  <TableCell>{r.enabled ? 'Yes' : 'No'}</TableCell>
                  <TableCell align="right">
                    <Button size="small" onClick={() => openEdit(r)}>
                      Request change
                    </Button>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      )}

      <Dialog open={!!editing} onClose={() => setEditing(null)} fullWidth maxWidth="xs">
        <DialogTitle>Request rule change: {editing?.ruleCode}</DialogTitle>
        <DialogContent>
          {submitError && (
            <Alert severity="error" sx={{ mb: 2 }}>
              {submitError}
            </Alert>
          )}
          {submitted ? (
            <Alert severity="success">
              Change submitted for approval — a different COMPLIANCE_OFFICER/ADMIN must approve it
              under Approvals before it takes effect.
            </Alert>
          ) : (
            <>
              <TextField
                label="Weight"
                type="number"
                value={weight}
                onChange={(e) => setWeight(Number(e.target.value))}
                fullWidth
                sx={{ mt: 1, mb: 2 }}
              />
              <TextField
                label="Threshold"
                type="number"
                value={threshold}
                onChange={(e) => setThreshold(Number(e.target.value))}
                fullWidth
                sx={{ mb: 2 }}
              />
              <Switch checked={enabled} onChange={(e) => setEnabled(e.target.checked)} /> Enabled
            </>
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setEditing(null)}>{submitted ? 'Close' : 'Cancel'}</Button>
          {!submitted && (
            <Button variant="contained" onClick={submit} disabled={requestUpdate.isPending}>
              Submit
            </Button>
          )}
        </DialogActions>
      </Dialog>
    </>
  );
}
