import { Card, CardContent, Typography } from '@mui/material';
import Grid from '@mui/material/Grid2';
import { Link as RouterLink } from 'react-router-dom';
import { OpsLayout } from '../../layouts/OpsLayout';
import { PageHeader } from '../../components/PageHeader';
import { useAccountRequestsQueue } from '../../api/accounts';
import { useKycQueue } from '../../api/customers';
import { useFraudAlerts, useAmlAlerts } from '../../api/fraud';
import { useSupportQueue } from '../../api/support';
import { useReconciliationRecords } from '../../api/ledger';
import { useApprovalQueue } from '../../api/opsApprovals';

function SummaryCard({ label, value, to }: { label: string; value: number | undefined; to: string }) {
  return (
    <Grid size={{ xs: 12, sm: 6, md: 3 }}>
      <Card component={RouterLink} to={to} sx={{ textDecoration: 'none', display: 'block', height: '100%' }}>
        <CardContent>
          <Typography variant="body2" color="text.secondary">
            {label}
          </Typography>
          <Typography variant="h4" fontWeight={700} sx={{ mt: 1 }}>
            {value ?? '—'}
          </Typography>
        </CardContent>
      </Card>
    </Grid>
  );
}

export function OpsDashboard() {
  const { data: accountRequests } = useAccountRequestsQueue('ACCOUNT_REQUESTED');
  const { data: kycQueue } = useKycQueue('KYC_PENDING');
  const { data: fraudAlerts } = useFraudAlerts({ status: 'OPEN' });
  const { data: amlAlerts } = useAmlAlerts({ status: 'OPEN' });
  const { data: supportQueue } = useSupportQueue('OPEN');
  const { data: mismatched } = useReconciliationRecords('MISMATCHED');
  const accountApprovals = useApprovalQueue('accounts', 'PENDING_APPROVAL');
  const kycApprovals = useApprovalQueue('kyc', 'PENDING_APPROVAL');
  const fraudApprovals = useApprovalQueue('fraud', 'PENDING_APPROVAL');
  const paymentApprovals = useApprovalQueue('payments', 'PENDING_APPROVAL');
  const pendingApprovals =
    (accountApprovals.data?.length ?? 0) +
    (kycApprovals.data?.length ?? 0) +
    (fraudApprovals.data?.length ?? 0) +
    (paymentApprovals.data?.length ?? 0);

  return (
    <OpsLayout>
      <PageHeader title="Operations Dashboard" subtitle="What needs attention right now." />
      <Grid container spacing={2}>
        <SummaryCard label="Pending approvals" value={pendingApprovals} to="/ops/approvals" />
        <SummaryCard label="Account requests" value={accountRequests?.length} to="/ops/accounts" />
        <SummaryCard label="KYC submissions pending" value={kycQueue?.length} to="/ops/kyc" />
        <SummaryCard label="Open support tickets" value={supportQueue?.length} to="/ops/support" />
        <SummaryCard label="Open fraud alerts" value={fraudAlerts?.length} to="/ops/fraud" />
        <SummaryCard label="Open AML alerts" value={amlAlerts?.length} to="/ops/aml" />
        <SummaryCard label="Unresolved mismatches" value={mismatched?.length} to="/ops/reconciliation" />
      </Grid>
      <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 3 }}>
        Counts reflect up to 100 rows per queue — see each page for the full, paginated view.
      </Typography>
    </OpsLayout>
  );
}
