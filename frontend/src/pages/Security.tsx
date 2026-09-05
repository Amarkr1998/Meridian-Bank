import { useState } from 'react';
import {
  Alert,
  Button,
  Card,
  CardContent,
  Chip,
  Divider,
  IconButton,
  Stack,
  TextField,
  Typography,
} from '@mui/material';
import DeleteIcon from '@mui/icons-material/Delete';
import { AppLayout } from '../layouts/AppLayout';
import { PageHeader } from '../components/PageHeader';
import { LoadingBlock, ErrorBlock, EmptyState } from '../components/Feedback';
import {
  confirmPasswordReset,
  requestPasswordReset,
  useMe,
  useRevokeAllSessions,
  useRevokeSession,
  useSessions,
} from '../api/auth';
import { useAuth } from '../auth/AuthContext';
import { ApiError } from '../api/client';

export function Security() {
  const { user, logout } = useAuth();
  const { data: me } = useMe();
  const { data: sessions, isLoading, error } = useSessions();
  const revokeSession = useRevokeSession();
  const revokeAll = useRevokeAllSessions();

  const [resetRequested, setResetRequested] = useState(false);
  const [devResetToken, setDevResetToken] = useState<string | undefined>();
  const [token, setToken] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [passwordError, setPasswordError] = useState<string | null>(null);
  const [passwordSuccess, setPasswordSuccess] = useState(false);

  async function handleRequestReset() {
    if (!user) return;
    const result = await requestPasswordReset(user.email);
    setResetRequested(true);
    setDevResetToken(result.devResetToken);
  }

  async function handleConfirmReset() {
    setPasswordError(null);
    try {
      await confirmPasswordReset(token, newPassword);
      setPasswordSuccess(true);
      setResetRequested(false);
      setToken('');
      setNewPassword('');
    } catch (err) {
      setPasswordError(err instanceof ApiError ? err.message : 'Unable to reset password.');
    }
  }

  async function handleRevokeAll() {
    await revokeAll.mutateAsync();
    await logout();
  }

  return (
    <AppLayout>
      <PageHeader title="Security" subtitle="Manage your login sessions and password." />

      <Stack spacing={3} sx={{ maxWidth: 640 }}>
        <Card>
          <CardContent>
            <Stack direction="row" justifyContent="space-between" alignItems="center">
              <Typography variant="subtitle1" fontWeight={600}>
                Two-factor authentication
              </Typography>
              <Chip
                size="small"
                label={me?.mfaEnabled ? 'Enabled' : 'Disabled'}
                color={me?.mfaEnabled ? 'success' : 'default'}
                variant="outlined"
              />
            </Stack>
            <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>
              A verification code is required on every sign-in when enabled.
            </Typography>
          </CardContent>
        </Card>

        <Card>
          <CardContent>
            <Typography variant="subtitle1" fontWeight={600} gutterBottom>
              Active sessions
            </Typography>
            {isLoading && <LoadingBlock />}
            {error && <ErrorBlock error={error} />}
            {sessions && sessions.length === 0 && <EmptyState message="No active sessions." />}
            <Stack divider={<Divider />}>
              {sessions?.map((s) => (
                <Stack key={s.id} direction="row" justifyContent="space-between" alignItems="center" sx={{ py: 1.5 }}>
                  <Stack>
                    <Typography variant="body2">IP {s.createdByIp}</Typography>
                    <Typography variant="caption" color="text.secondary">
                      Issued {new Date(s.issuedAt).toLocaleString()} · Expires {new Date(s.expiresAt).toLocaleString()}
                    </Typography>
                  </Stack>
                  <IconButton size="small" onClick={() => revokeSession.mutate(s.id)}>
                    <DeleteIcon fontSize="small" />
                  </IconButton>
                </Stack>
              ))}
            </Stack>
            {sessions && sessions.length > 0 && (
              <Button color="error" size="small" sx={{ mt: 2 }} onClick={handleRevokeAll}>
                Sign out of all sessions
              </Button>
            )}
          </CardContent>
        </Card>

        <Card>
          <CardContent>
            <Typography variant="subtitle1" fontWeight={600} gutterBottom>
              Change password
            </Typography>
            {passwordSuccess && (
              <Alert severity="success" sx={{ mb: 2 }}>
                Password updated. Use it next time you sign in.
              </Alert>
            )}
            {passwordError && (
              <Alert severity="error" sx={{ mb: 2 }}>
                {passwordError}
              </Alert>
            )}
            {!resetRequested ? (
              <Button variant="outlined" onClick={handleRequestReset}>
                Send reset code to my email
              </Button>
            ) : (
              <Stack spacing={2} sx={{ mt: 1 }}>
                {devResetToken && (
                  <Alert severity="info">
                    Demo mode — reset token: <strong>{devResetToken}</strong>
                  </Alert>
                )}
                <TextField label="Reset token" value={token} onChange={(e) => setToken(e.target.value)} fullWidth />
                <TextField
                  label="New password"
                  type="password"
                  value={newPassword}
                  onChange={(e) => setNewPassword(e.target.value)}
                  fullWidth
                  helperText="At least 8 characters, mixing case, a digit, and a symbol"
                />
                <Button variant="contained" onClick={handleConfirmReset} disabled={!token || !newPassword}>
                  Update password
                </Button>
              </Stack>
            )}
          </CardContent>
        </Card>
      </Stack>
    </AppLayout>
  );
}
