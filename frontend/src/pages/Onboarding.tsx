import { useState } from 'react';
import { zodResolver } from '@hookform/resolvers/zod';
import { useFieldArray, useForm } from 'react-hook-form';
import { z } from 'zod';
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Divider,
  IconButton,
  MenuItem,
  Stack,
  TextField,
  Typography,
} from '@mui/material';
import Grid from '@mui/material/Grid2';
import AddIcon from '@mui/icons-material/Add';
import DeleteIcon from '@mui/icons-material/Delete';
import { AppLayout } from '../layouts/AppLayout';
import { PageHeader } from '../components/PageHeader';
import { LoadingBlock, ErrorBlock } from '../components/Feedback';
import { StatusChip } from '../components/StatusChip';
import { useAuth } from '../auth/AuthContext';
import {
  resendVerification,
  useCustomer,
  useKycHistory,
  useSubmitKyc,
  verifyContact,
} from '../api/customers';
import { ApiError } from '../api/client';
import type { DocumentType } from '../types/domain';

const DOCUMENT_TYPES: DocumentType[] = ['NATIONAL_ID', 'PASSPORT', 'DRIVERS_LICENSE', 'PROOF_OF_ADDRESS'];

const schema = z.object({
  nationality: z.string().min(1, 'Required').max(100),
  occupation: z.string().min(1, 'Required').max(150),
  documents: z
    .array(
      z.object({
        documentType: z.enum(['NATIONAL_ID', 'PASSPORT', 'DRIVERS_LICENSE', 'PROOF_OF_ADDRESS']),
        documentReference: z.string().min(1, 'Required').max(100),
      }),
    )
    .min(1, 'Add at least one document'),
});

type FormValues = z.infer<typeof schema>;

export function Onboarding() {
  const { user } = useAuth();
  const { data: customer, refetch: refetchCustomer } = useCustomer(user?.userId);
  const { data: history, isLoading, error } = useKycHistory(user?.userId);
  const submitKyc = useSubmitKyc(user?.userId);
  const [serverError, setServerError] = useState<string | null>(null);
  const [contactOtp, setContactOtp] = useState('');
  const [devOtp, setDevOtp] = useState<string | undefined>();
  const [contactError, setContactError] = useState<string | null>(null);
  const [resending, setResending] = useState(false);
  const [verifyingContact, setVerifyingContact] = useState(false);

  const {
    register,
    control,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { nationality: '', occupation: '', documents: [{ documentType: 'PASSPORT', documentReference: '' }] },
  });
  const { fields, append, remove } = useFieldArray({ control, name: 'documents' });

  const latest = history?.[0];
  const canSubmit = !latest || latest.status === 'KYC_REJECTED';

  async function onSubmit(values: FormValues) {
    setServerError(null);
    try {
      await submitKyc.mutateAsync(values);
    } catch (err) {
      setServerError(err instanceof ApiError ? err.message : 'Unable to submit KYC information.');
    }
  }

  async function onResendVerification() {
    if (!user?.userId) return;
    setContactError(null);
    setResending(true);
    try {
      const result = await resendVerification(user.userId);
      setDevOtp(result.devOtp);
    } catch (err) {
      setContactError(err instanceof ApiError ? err.message : 'Unable to send a verification code.');
    } finally {
      setResending(false);
    }
  }

  async function onVerifyContact() {
    if (!user?.userId) return;
    setContactError(null);
    setVerifyingContact(true);
    try {
      await verifyContact(user.userId, contactOtp);
      setContactOtp('');
      setDevOtp(undefined);
      await refetchCustomer();
    } catch (err) {
      setContactError(err instanceof ApiError ? err.message : 'Invalid or expired verification code.');
    } finally {
      setVerifyingContact(false);
    }
  }

  return (
    <AppLayout>
      <PageHeader title="Identity verification (KYC)" subtitle="Required once before you can open an account." />

      {isLoading && <LoadingBlock />}
      {error && <ErrorBlock error={error} />}

      {customer && !customer.contactVerified && (
        <Card sx={{ mb: 3 }}>
          <CardContent>
            <Typography variant="subtitle1" fontWeight={600} gutterBottom>
              Verify your contact details first
            </Typography>
            <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
              KYC submission is available after your registration contact code is verified.
            </Typography>
            {contactError && <Alert severity="error" sx={{ mb: 2 }}>{contactError}</Alert>}
            {devOtp && (
              <Alert severity="info" sx={{ mb: 2 }}>
                Demo mode — verification code: <strong>{devOtp}</strong>
              </Alert>
            )}
            <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
              <Button variant="outlined" onClick={onResendVerification} disabled={resending}>
                {resending ? 'Sending…' : 'Send verification code'}
              </Button>
              <TextField
                label="Verification code"
                value={contactOtp}
                onChange={(event) => setContactOtp(event.target.value)}
                inputProps={{ maxLength: 6, inputMode: 'numeric' }}
                size="small"
              />
              <Button
                variant="contained"
                onClick={onVerifyContact}
                disabled={verifyingContact || contactOtp.length !== 6}
              >
                {verifyingContact ? 'Verifying…' : 'Verify'}
              </Button>
            </Stack>
          </CardContent>
        </Card>
      )}

      {latest && (
        <Card sx={{ mb: 3 }}>
          <CardContent>
            <Stack direction="row" justifyContent="space-between" alignItems="center">
              <Box>
                <Typography variant="subtitle1" fontWeight={600}>
                  Latest submission
                </Typography>
                <Typography variant="body2" color="text.secondary">
                  Submitted {new Date(latest.submittedAt).toLocaleString()}
                </Typography>
              </Box>
              <StatusChip status={latest.status} />
            </Stack>
            {latest.status === 'KYC_REJECTED' && latest.rejectionReason && (
              <Alert severity="warning" sx={{ mt: 2 }}>
                {latest.rejectionReason}
              </Alert>
            )}
            {latest.status === 'KYC_VERIFIED' && (
              <Alert severity="success" sx={{ mt: 2 }}>
                You're verified — you can now open an account.
              </Alert>
            )}
            {(latest.status === 'KYC_PENDING' || latest.status === 'KYC_IN_REVIEW') && (
              <Alert severity="info" sx={{ mt: 2 }}>
                Our compliance team is reviewing your submission.
              </Alert>
            )}
          </CardContent>
        </Card>
      )}

      {customer?.contactVerified && canSubmit && (
        <Card>
          <CardContent>
            <Box component="form" onSubmit={handleSubmit(onSubmit)} noValidate>
              {serverError && (
                <Alert severity="error" sx={{ mb: 2 }}>
                  {serverError}
                </Alert>
              )}
              <Grid container spacing={2}>
                <Grid size={6}>
                  <TextField
                    {...register('nationality')}
                    label="Nationality"
                    fullWidth
                    error={!!errors.nationality}
                    helperText={errors.nationality?.message}
                  />
                </Grid>
                <Grid size={6}>
                  <TextField
                    {...register('occupation')}
                    label="Occupation"
                    fullWidth
                    error={!!errors.occupation}
                    helperText={errors.occupation?.message}
                  />
                </Grid>
              </Grid>

              <Divider sx={{ my: 3 }} />
              <Typography variant="subtitle2" gutterBottom>
                Supporting documents (synthetic reference numbers only — never a real document)
              </Typography>

              <Stack spacing={2}>
                {fields.map((field, index) => (
                  <Stack direction="row" spacing={2} key={field.id} alignItems="flex-start">
                    <TextField
                      {...register(`documents.${index}.documentType` as const)}
                      select
                      label="Document type"
                      defaultValue={field.documentType}
                      sx={{ minWidth: 200 }}
                    >
                      {DOCUMENT_TYPES.map((type) => (
                        <MenuItem key={type} value={type}>
                          {type.replace(/_/g, ' ')}
                        </MenuItem>
                      ))}
                    </TextField>
                    <TextField
                      {...register(`documents.${index}.documentReference` as const)}
                      label="Reference number"
                      fullWidth
                      error={!!errors.documents?.[index]?.documentReference}
                      helperText={errors.documents?.[index]?.documentReference?.message}
                    />
                    <IconButton onClick={() => remove(index)} disabled={fields.length === 1} sx={{ mt: 1 }}>
                      <DeleteIcon fontSize="small" />
                    </IconButton>
                  </Stack>
                ))}
              </Stack>
              <Button
                startIcon={<AddIcon />}
                onClick={() => append({ documentType: 'PASSPORT', documentReference: '' })}
                sx={{ mt: 2 }}
              >
                Add another document
              </Button>

              <Box sx={{ mt: 3 }}>
                <Button type="submit" variant="contained" size="large" disabled={isSubmitting}>
                  Submit for review
                </Button>
              </Box>
            </Box>
          </CardContent>
        </Card>
      )}
    </AppLayout>
  );
}
