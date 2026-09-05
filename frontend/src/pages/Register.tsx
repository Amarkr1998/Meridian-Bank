import { useState } from 'react';
import { zodResolver } from '@hookform/resolvers/zod';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { Alert, Box, Button, Link as MuiLink, TextField, Typography } from '@mui/material';
import Grid from '@mui/material/Grid2';
import { Link as RouterLink, useNavigate } from 'react-router-dom';
import { AuthLayout } from '../layouts/AuthLayout';
import { registerCustomer, verifyContact } from '../api/customers';
import { ApiError } from '../api/client';

const schema = z.object({
  firstName: z.string().min(1, 'Required').max(100),
  lastName: z.string().min(1, 'Required').max(100),
  email: z.string().email('Enter a valid email address'),
  password: z
    .string()
    .min(8, 'At least 8 characters')
    .regex(/[a-z]/, 'Include a lowercase letter')
    .regex(/[A-Z]/, 'Include an uppercase letter')
    .regex(/\d/, 'Include a digit')
    .regex(/[^a-zA-Z0-9]/, 'Include a special character'),
  dateOfBirth: z.string().min(1, 'Required'),
  phone: z.string().min(1, 'Required').max(30),
  addressLine1: z.string().min(1, 'Required').max(200),
  addressLine2: z.string().max(200).optional(),
  city: z.string().min(1, 'Required').max(100),
  state: z.string().min(1, 'Required').max(100),
  postalCode: z.string().min(1, 'Required').max(20),
  country: z.string().min(1, 'Required').max(100),
});

type FormValues = z.infer<typeof schema>;

export function Register() {
  const navigate = useNavigate();
  const [serverError, setServerError] = useState<string | null>(null);
  const [pendingCustomerId, setPendingCustomerId] = useState<string | null>(null);
  const [devOtp, setDevOtp] = useState<string | undefined>();
  const [otp, setOtp] = useState('');
  const [verifying, setVerifying] = useState(false);
  const [verifyError, setVerifyError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({ resolver: zodResolver(schema) });

  async function onSubmit(values: FormValues) {
    setServerError(null);
    try {
      const result = await registerCustomer(values);
      setPendingCustomerId(result.customerId);
      setDevOtp(result.devOtp);
    } catch (err) {
      setServerError(err instanceof ApiError ? err.message : 'Unable to register. Please try again.');
    }
  }

  async function onVerify() {
    if (!pendingCustomerId) return;
    setVerifyError(null);
    setVerifying(true);
    try {
      await verifyContact(pendingCustomerId, otp);
      navigate('/login', { state: { registered: true } });
    } catch (err) {
      setVerifyError(err instanceof ApiError ? err.message : 'Invalid or expired code.');
    } finally {
      setVerifying(false);
    }
  }

  if (pendingCustomerId) {
    return (
      <AuthLayout title="Verify your contact details" subtitle="Enter the code to activate your new account.">
        {verifyError && (
          <Alert severity="error" sx={{ mb: 2 }}>
            {verifyError}
          </Alert>
        )}
        {devOtp && (
          <Alert severity="info" sx={{ mb: 2 }}>
            Demo mode — verification code: <strong>{devOtp}</strong>
          </Alert>
        )}
        <TextField
          label="Verification code"
          value={otp}
          onChange={(e) => setOtp(e.target.value)}
          fullWidth
          margin="normal"
          autoFocus
          inputProps={{ maxLength: 6, inputMode: 'numeric' }}
        />
        <Button variant="contained" fullWidth size="large" sx={{ mt: 2, py: 1.25 }} disabled={verifying || !otp} onClick={onVerify}>
          Verify and continue
        </Button>
      </AuthLayout>
    );
  }

  return (
    <AuthLayout title="Open an account" subtitle="Tell us a bit about yourself to get started.">
      <Box component="form" onSubmit={handleSubmit(onSubmit)} noValidate>
        {serverError && (
          <Alert severity="error" sx={{ mb: 2 }}>
            {serverError}
          </Alert>
        )}
        <Grid container spacing={2}>
          <Grid size={6}>
            <TextField {...register('firstName')} label="First name" fullWidth error={!!errors.firstName} helperText={errors.firstName?.message} />
          </Grid>
          <Grid size={6}>
            <TextField {...register('lastName')} label="Last name" fullWidth error={!!errors.lastName} helperText={errors.lastName?.message} />
          </Grid>
          <Grid size={12}>
            <TextField {...register('email')} label="Email address" type="email" fullWidth error={!!errors.email} helperText={errors.email?.message} />
          </Grid>
          <Grid size={12}>
            <TextField
              {...register('password')}
              label="Password"
              type="password"
              fullWidth
              error={!!errors.password}
              helperText={errors.password?.message ?? 'At least 8 characters, mixing case, a digit, and a symbol'}
            />
          </Grid>
          <Grid size={6}>
            <TextField
              {...register('dateOfBirth')}
              label="Date of birth"
              type="date"
              fullWidth
              slotProps={{ inputLabel: { shrink: true } }}
              error={!!errors.dateOfBirth}
              helperText={errors.dateOfBirth?.message}
            />
          </Grid>
          <Grid size={6}>
            <TextField {...register('phone')} label="Phone number" fullWidth error={!!errors.phone} helperText={errors.phone?.message} />
          </Grid>
          <Grid size={12}>
            <TextField {...register('addressLine1')} label="Address line 1" fullWidth error={!!errors.addressLine1} helperText={errors.addressLine1?.message} />
          </Grid>
          <Grid size={12}>
            <TextField {...register('addressLine2')} label="Address line 2 (optional)" fullWidth />
          </Grid>
          <Grid size={4}>
            <TextField {...register('city')} label="City" fullWidth error={!!errors.city} helperText={errors.city?.message} />
          </Grid>
          <Grid size={4}>
            <TextField {...register('state')} label="State" fullWidth error={!!errors.state} helperText={errors.state?.message} />
          </Grid>
          <Grid size={4}>
            <TextField {...register('postalCode')} label="Postal code" fullWidth error={!!errors.postalCode} helperText={errors.postalCode?.message} />
          </Grid>
          <Grid size={12}>
            <TextField {...register('country')} label="Country" fullWidth error={!!errors.country} helperText={errors.country?.message} />
          </Grid>
        </Grid>
        <Button type="submit" variant="contained" fullWidth size="large" sx={{ mt: 3, py: 1.25 }} disabled={isSubmitting}>
          Create account
        </Button>
        <Typography variant="body2" sx={{ mt: 3, textAlign: 'center' }} color="text.secondary">
          Already have an account?{' '}
          <MuiLink component={RouterLink} to="/login">
            Sign in
          </MuiLink>
        </Typography>
      </Box>
    </AuthLayout>
  );
}
