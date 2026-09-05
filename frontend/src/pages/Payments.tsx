import { useState } from 'react';
import { zodResolver } from '@hookform/resolvers/zod';
import { Controller, useForm } from 'react-hook-form';
import { z } from 'zod';
import { Alert, Box, Button, Card, CardContent, MenuItem, Stack, TextField, Typography } from '@mui/material';
import Grid from '@mui/material/Grid2';
import { AppLayout } from '../layouts/AppLayout';
import { PageHeader } from '../components/PageHeader';
import { LoadingBlock } from '../components/Feedback';
import { StatusChip } from '../components/StatusChip';
import { useAccounts } from '../api/accounts';
import { useBeneficiaries } from '../api/beneficiaries';
import { useCreatePayment } from '../api/payments';
import { ApiError } from '../api/client';
import type { PaymentResponse } from '../types/domain';

const schema = z.object({
  sourceAccountId: z.string().min(1, 'Select an account'),
  beneficiaryId: z.string().min(1, 'Select a beneficiary'),
  amount: z.number().positive('Enter an amount greater than zero'),
  purpose: z.string().max(200).optional(),
});

type FormValues = z.infer<typeof schema>;

export function Payments() {
  const { data: accounts, isLoading: accountsLoading } = useAccounts();
  const { data: beneficiaries, isLoading: beneficiariesLoading } = useBeneficiaries();
  const createPayment = useCreatePayment();

  const [serverError, setServerError] = useState<string | null>(null);
  const [result, setResult] = useState<PaymentResponse | null>(null);

  const activeAccounts = accounts?.filter((a) => a.status === 'ACTIVE') ?? [];
  const activeBeneficiaries = beneficiaries?.filter((b) => b.status === 'ACTIVE') ?? [];

  const {
    control,
    register,
    handleSubmit,
    reset,
    watch,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({ resolver: zodResolver(schema) });

  const sourceAccountId = watch('sourceAccountId');
  const selectedAccount = accounts?.find((a) => a.id === sourceAccountId);

  async function onSubmit(values: FormValues) {
    setServerError(null);
    setResult(null);
    try {
      const payment = await createPayment.mutateAsync({
        sourceAccountId: values.sourceAccountId,
        beneficiaryId: values.beneficiaryId,
        amount: values.amount,
        currency: selectedAccount?.currency ?? 'USD',
        purpose: values.purpose,
      });
      setResult(payment);
      reset();
    } catch (err) {
      setServerError(err instanceof ApiError ? err.message : 'Unable to submit payment.');
    }
  }

  return (
    <AppLayout>
      <PageHeader title="Send money" subtitle="Transfer funds to one of your beneficiaries." />

      {(accountsLoading || beneficiariesLoading) && <LoadingBlock />}

      <Card sx={{ maxWidth: 560 }}>
        <CardContent>
          {result && (
            <Alert severity={result.status === 'SUCCESS' ? 'success' : 'error'} sx={{ mb: 2 }}>
              <Stack direction="row" spacing={1} alignItems="center">
                <span>
                  {result.status === 'SUCCESS'
                    ? 'Payment completed.'
                    : result.failureReason ?? 'Payment could not be completed.'}
                </span>
                <StatusChip status={result.status} />
              </Stack>
            </Alert>
          )}
          {serverError && (
            <Alert severity="error" sx={{ mb: 2 }}>
              {serverError}
            </Alert>
          )}

          {activeAccounts.length === 0 && !accountsLoading && (
            <Alert severity="warning" sx={{ mb: 2 }}>
              You need at least one active account to send a payment.
            </Alert>
          )}
          {activeBeneficiaries.length === 0 && !beneficiariesLoading && (
            <Alert severity="warning" sx={{ mb: 2 }}>
              Add and verify a beneficiary before sending a payment.
            </Alert>
          )}

          <Box component="form" onSubmit={handleSubmit(onSubmit)} noValidate>
            <Grid container spacing={2}>
              <Grid size={12}>
                <Controller
                  name="sourceAccountId"
                  control={control}
                  defaultValue=""
                  render={({ field }) => (
                    <TextField {...field} select label="From account" fullWidth error={!!errors.sourceAccountId} helperText={errors.sourceAccountId?.message}>
                      {activeAccounts.map((a) => (
                        <MenuItem key={a.id} value={a.id}>
                          {a.accountType} · {a.maskedAccountNumber}
                        </MenuItem>
                      ))}
                    </TextField>
                  )}
                />
              </Grid>
              <Grid size={12}>
                <Controller
                  name="beneficiaryId"
                  control={control}
                  defaultValue=""
                  render={({ field }) => (
                    <TextField {...field} select label="To beneficiary" fullWidth error={!!errors.beneficiaryId} helperText={errors.beneficiaryId?.message}>
                      {activeBeneficiaries.map((b) => (
                        <MenuItem key={b.id} value={b.id}>
                          {b.nickname} · {b.maskedBeneficiaryAccountNumber}
                        </MenuItem>
                      ))}
                    </TextField>
                  )}
                />
              </Grid>
              <Grid size={6}>
                <TextField
                  {...register('amount', { valueAsNumber: true })}
                  label="Amount"
                  type="number"
                  fullWidth
                  error={!!errors.amount}
                  helperText={errors.amount?.message}
                  slotProps={{ htmlInput: { step: '0.01', min: '0.01' } }}
                />
              </Grid>
              <Grid size={6}>
                <TextField label="Currency" value={selectedAccount?.currency ?? '—'} fullWidth disabled />
              </Grid>
              <Grid size={12}>
                <TextField {...register('purpose')} label="Purpose (optional)" fullWidth />
              </Grid>
            </Grid>
            <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 2 }}>
              Every payment is screened for fraud/risk automatically before it completes.
            </Typography>
            <Button
              type="submit"
              variant="contained"
              size="large"
              sx={{ mt: 2 }}
              disabled={isSubmitting || activeAccounts.length === 0 || activeBeneficiaries.length === 0}
            >
              Send payment
            </Button>
          </Box>
        </CardContent>
      </Card>
    </AppLayout>
  );
}
