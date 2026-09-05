import { useState } from 'react';
import { zodResolver } from '@hookform/resolvers/zod';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { Alert, Box, Button, Link as MuiLink, TextField, Typography } from '@mui/material';
import { Link as RouterLink, useLocation, useNavigate, type Location } from 'react-router-dom';
import { AuthLayout } from '../layouts/AuthLayout';
import { useAuth } from '../auth/AuthContext';
import { isStaffRole } from '../auth/RequireRole';
import { ApiError } from '../api/client';

const schema = z.object({
  email: z.string().email('Enter a valid email address'),
  password: z.string().min(1, 'Password is required'),
});

type FormValues = z.infer<typeof schema>;

export function Login() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [serverError, setServerError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({ resolver: zodResolver(schema) });

  async function onSubmit(values: FormValues) {
    setServerError(null);
    try {
      const result = await login(values.email, values.password);
      if (result.mfaRequired && result.challenge) {
        navigate('/mfa', {
          state: {
            mfaChallengeId: result.challenge.mfaChallengeId,
            devOtp: result.challenge.devOtp,
            from: (location.state as { from?: Location })?.from,
          },
        });
      } else {
        navigate(isStaffRole(result.user?.role) ? '/ops/dashboard' : '/dashboard', { replace: true });
      }
    } catch (err) {
      setServerError(err instanceof ApiError ? err.message : 'Unable to sign in. Please try again.');
    }
  }

  return (
    <AuthLayout title="Sign in" subtitle="Access your Meridian Digital Banking account.">
      <Box component="form" onSubmit={handleSubmit(onSubmit)} noValidate>
        {serverError && (
          <Alert severity="error" sx={{ mb: 2 }}>
            {serverError}
          </Alert>
        )}
        <TextField
          {...register('email')}
          label="Email address"
          type="email"
          fullWidth
          margin="normal"
          error={!!errors.email}
          helperText={errors.email?.message}
          autoFocus
        />
        <TextField
          {...register('password')}
          label="Password"
          type="password"
          fullWidth
          margin="normal"
          error={!!errors.password}
          helperText={errors.password?.message}
        />
        <Button type="submit" variant="contained" fullWidth size="large" sx={{ mt: 2, py: 1.25 }} disabled={isSubmitting}>
          Sign in
        </Button>
        <Typography variant="body2" sx={{ mt: 3, textAlign: 'center' }} color="text.secondary">
          New to Meridian?{' '}
          <MuiLink component={RouterLink} to="/register">
            Open an account
          </MuiLink>
        </Typography>
      </Box>
    </AuthLayout>
  );
}
