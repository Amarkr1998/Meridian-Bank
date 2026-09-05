import { useState, type FormEvent } from 'react';
import { Alert, Box, Button, TextField, Typography } from '@mui/material';
import { useLocation, useNavigate, type Location } from 'react-router-dom';
import { AuthLayout } from '../layouts/AuthLayout';
import { useAuth } from '../auth/AuthContext';
import { isStaffRole } from '../auth/RequireRole';
import { ApiError } from '../api/client';

interface MfaLocationState {
  mfaChallengeId?: string;
  devOtp?: string;
  from?: Location;
}

export function MfaVerify() {
  const { verifyMfa } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const state = (location.state as MfaLocationState | null) ?? {};

  const [otp, setOtp] = useState(state.devOtp ?? '');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  if (!state.mfaChallengeId) {
    navigate('/login', { replace: true });
    return null;
  }

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      const authUser = await verifyMfa(state.mfaChallengeId!, otp);
      const defaultHome = isStaffRole(authUser.role) ? '/ops/dashboard' : '/dashboard';
      navigate(state.from?.pathname ?? defaultHome, { replace: true });
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Invalid or expired code.');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <AuthLayout title="Two-factor verification" subtitle="Enter the 6-digit code to finish signing in.">
      <Box component="form" onSubmit={onSubmit} noValidate>
        {error && (
          <Alert severity="error" sx={{ mb: 2 }}>
            {error}
          </Alert>
        )}
        {state.devOtp && (
          <Alert severity="info" sx={{ mb: 2 }}>
            Demo mode — verification code: <strong>{state.devOtp}</strong>
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
        <Button type="submit" variant="contained" fullWidth size="large" sx={{ mt: 2, py: 1.25 }} disabled={submitting || otp.length === 0}>
          Verify
        </Button>
        <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 2, textAlign: 'center' }}>
          This demo environment displays the code instead of sending a real SMS/email.
        </Typography>
      </Box>
    </AuthLayout>
  );
}
