import { Chip } from '@mui/material';

const COLOR_MAP: Record<string, 'success' | 'warning' | 'error' | 'info' | 'default'> = {
  // accounts
  ACTIVE: 'success',
  FROZEN: 'warning',
  BLOCKED: 'error',
  CLOSED: 'default',
  // account opening / kyc / support
  APPROVED: 'success',
  ACCOUNT_REQUESTED: 'info',
  UNDER_REVIEW: 'info',
  REJECTED: 'error',
  KYC_PENDING: 'info',
  KYC_IN_REVIEW: 'info',
  KYC_VERIFIED: 'success',
  KYC_REJECTED: 'error',
  OPEN: 'info',
  IN_PROGRESS: 'warning',
  RESOLVED: 'success',
  // beneficiary
  PENDING: 'warning',
  INACTIVE: 'default',
  // payments
  INITIATED: 'info',
  VALIDATING: 'info',
  RISK_CHECK: 'warning',
  PROCESSING: 'info',
  SUCCESS: 'success',
  FAILED: 'error',
};

const LABEL_OVERRIDES: Record<string, string> = {
  ACCOUNT_REQUESTED: 'Requested',
  UNDER_REVIEW: 'Under review',
  KYC_PENDING: 'Pending',
  KYC_IN_REVIEW: 'In review',
  KYC_VERIFIED: 'Verified',
  KYC_REJECTED: 'Rejected',
  IN_PROGRESS: 'In progress',
  RISK_CHECK: 'Risk check',
};

function toLabel(status: string): string {
  if (LABEL_OVERRIDES[status]) return LABEL_OVERRIDES[status];
  return status.charAt(0) + status.slice(1).toLowerCase().replace(/_/g, ' ');
}

export function StatusChip({ status }: { status: string }) {
  return <Chip size="small" label={toLabel(status)} color={COLOR_MAP[status] ?? 'default'} variant="outlined" />;
}
