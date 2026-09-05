import { Alert, Box, CircularProgress, Typography } from '@mui/material';
import { ApiError } from '../api/client';

export function LoadingBlock({ label }: { label?: string }) {
  return (
    <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5, py: 4, justifyContent: 'center' }}>
      <CircularProgress size={22} />
      {label && (
        <Typography variant="body2" color="text.secondary">
          {label}
        </Typography>
      )}
    </Box>
  );
}

export function ErrorBlock({ error }: { error: unknown }) {
  const message = error instanceof ApiError ? error.message : error instanceof Error ? error.message : 'Something went wrong.';
  return <Alert severity="error">{message}</Alert>;
}

export function EmptyState({ message }: { message: string }) {
  return (
    <Box sx={{ py: 6, textAlign: 'center' }}>
      <Typography variant="body2" color="text.secondary">
        {message}
      </Typography>
    </Box>
  );
}
