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
  IconButton,
  Menu,
  MenuItem,
  Stack,
  TextField,
  Typography,
} from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import MoreVertIcon from '@mui/icons-material/MoreVert';
import { AppLayout } from '../layouts/AppLayout';
import { PageHeader } from '../components/PageHeader';
import { LoadingBlock, ErrorBlock, EmptyState } from '../components/Feedback';
import { StatusChip } from '../components/StatusChip';
import {
  useAddBeneficiary,
  useBeneficiaries,
  useDeleteBeneficiary,
  useResendBeneficiaryVerification,
  useSetBeneficiaryActive,
  useVerifyBeneficiary,
} from '../api/beneficiaries';
import { ApiError } from '../api/client';
import type { BeneficiaryResponse } from '../types/domain';

export function Beneficiaries() {
  const { data: beneficiaries, isLoading, error } = useBeneficiaries();
  const addBeneficiary = useAddBeneficiary();
  const verifyBeneficiary = useVerifyBeneficiary();
  const resendVerification = useResendBeneficiaryVerification();
  const deleteBeneficiary = useDeleteBeneficiary();
  const setActive = useSetBeneficiaryActive();

  const [addOpen, setAddOpen] = useState(false);
  const [nickname, setNickname] = useState('');
  const [beneficiaryName, setBeneficiaryName] = useState('');
  const [accountNumber, setAccountNumber] = useState('');
  const [formError, setFormError] = useState<string | null>(null);

  const [pendingVerify, setPendingVerify] = useState<{ id: string; devOtp?: string } | null>(null);
  const [otp, setOtp] = useState('');
  const [verifyError, setVerifyError] = useState<string | null>(null);

  const [menuFor, setMenuFor] = useState<{ el: HTMLElement; beneficiary: BeneficiaryResponse } | null>(null);

  async function handleAdd() {
    setFormError(null);
    try {
      const result = await addBeneficiary.mutateAsync({
        nickname,
        beneficiaryName,
        beneficiaryAccountNumber: accountNumber,
      });
      setAddOpen(false);
      setNickname('');
      setBeneficiaryName('');
      setAccountNumber('');
      setPendingVerify({ id: result.beneficiary.id, devOtp: result.devOtp });
    } catch (err) {
      setFormError(err instanceof ApiError ? err.message : 'Unable to add beneficiary.');
    }
  }

  async function handleVerify() {
    if (!pendingVerify) return;
    setVerifyError(null);
    try {
      await verifyBeneficiary.mutateAsync({ id: pendingVerify.id, otp });
      setPendingVerify(null);
      setOtp('');
    } catch (err) {
      setVerifyError(err instanceof ApiError ? err.message : 'Invalid or expired code.');
    }
  }

  return (
    <AppLayout>
      <PageHeader
        title="Beneficiaries"
        subtitle="People and accounts you send payments to."
        action={
          <Button variant="contained" startIcon={<AddIcon />} onClick={() => setAddOpen(true)}>
            Add beneficiary
          </Button>
        }
      />

      {isLoading && <LoadingBlock />}
      {error && <ErrorBlock error={error} />}
      {beneficiaries && beneficiaries.length === 0 && <EmptyState message="No beneficiaries yet." />}

      <Stack spacing={2}>
        {beneficiaries?.map((b) => (
          <Card key={b.id}>
            <CardContent>
              <Stack direction="row" justifyContent="space-between" alignItems="center">
                <Stack>
                  <Typography variant="subtitle1" fontWeight={600}>
                    {b.nickname}
                  </Typography>
                  <Typography variant="body2" color="text.secondary">
                    {b.beneficiaryName} · {b.maskedBeneficiaryAccountNumber}
                  </Typography>
                </Stack>
                <Stack direction="row" spacing={1} alignItems="center">
                  <StatusChip status={b.status} />
                  <IconButton size="small" onClick={(e) => setMenuFor({ el: e.currentTarget, beneficiary: b })}>
                    <MoreVertIcon fontSize="small" />
                  </IconButton>
                </Stack>
              </Stack>
            </CardContent>
          </Card>
        ))}
      </Stack>

      <Menu anchorEl={menuFor?.el} open={!!menuFor} onClose={() => setMenuFor(null)}>
        {menuFor?.beneficiary.status === 'PENDING' && (
          <MenuItem
            onClick={async () => {
              const b = menuFor.beneficiary;
              setMenuFor(null);
              const result = await resendVerification.mutateAsync(b.id);
              setPendingVerify({ id: b.id, devOtp: result.devOtp });
            }}
          >
            Resend verification code
          </MenuItem>
        )}
        {menuFor?.beneficiary.status === 'ACTIVE' && (
          <MenuItem
            onClick={async () => {
              await setActive.mutateAsync({ id: menuFor.beneficiary.id, activate: false });
              setMenuFor(null);
            }}
          >
            Deactivate
          </MenuItem>
        )}
        {menuFor?.beneficiary.status === 'INACTIVE' && (
          <MenuItem
            onClick={async () => {
              await setActive.mutateAsync({ id: menuFor.beneficiary.id, activate: true });
              setMenuFor(null);
            }}
          >
            Reactivate
          </MenuItem>
        )}
        <MenuItem
          onClick={async () => {
            const b = menuFor?.beneficiary;
            setMenuFor(null);
            if (b) await deleteBeneficiary.mutateAsync(b.id);
          }}
        >
          Remove
        </MenuItem>
      </Menu>

      <Dialog open={addOpen} onClose={() => setAddOpen(false)} fullWidth maxWidth="xs">
        <DialogTitle>Add a beneficiary</DialogTitle>
        <DialogContent>
          {formError && (
            <Alert severity="error" sx={{ mb: 2 }}>
              {formError}
            </Alert>
          )}
          <Stack spacing={2} sx={{ mt: 1 }}>
            <TextField label="Nickname" value={nickname} onChange={(e) => setNickname(e.target.value)} fullWidth />
            <TextField
              label="Beneficiary name"
              value={beneficiaryName}
              onChange={(e) => setBeneficiaryName(e.target.value)}
              fullWidth
            />
            <TextField
              label="Account number"
              value={accountNumber}
              onChange={(e) => setAccountNumber(e.target.value)}
              fullWidth
              helperText="10 digits"
              slotProps={{ htmlInput: { maxLength: 10 } }}
            />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setAddOpen(false)}>Cancel</Button>
          <Button variant="contained" onClick={handleAdd} disabled={addBeneficiary.isPending}>
            Add
          </Button>
        </DialogActions>
      </Dialog>

      <Dialog open={!!pendingVerify} onClose={() => setPendingVerify(null)} fullWidth maxWidth="xs">
        <DialogTitle>Verify beneficiary</DialogTitle>
        <DialogContent>
          {verifyError && (
            <Alert severity="error" sx={{ mb: 2 }}>
              {verifyError}
            </Alert>
          )}
          {pendingVerify?.devOtp && (
            <Alert severity="info" sx={{ mb: 2 }}>
              Demo mode — verification code: <strong>{pendingVerify.devOtp}</strong>
            </Alert>
          )}
          <TextField
            label="Verification code"
            value={otp}
            onChange={(e) => setOtp(e.target.value)}
            fullWidth
            autoFocus
            inputProps={{ maxLength: 6, inputMode: 'numeric' }}
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setPendingVerify(null)}>Cancel</Button>
          <Button variant="contained" onClick={handleVerify} disabled={verifyBeneficiary.isPending || !otp}>
            Verify
          </Button>
        </DialogActions>
      </Dialog>
    </AppLayout>
  );
}
