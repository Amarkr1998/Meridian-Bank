import { Box, Container, Paper, Stack, Typography } from '@mui/material';
import type { ReactNode } from 'react';
import AccountBalanceIcon from '@mui/icons-material/AccountBalance';

export function AuthLayout({ title, subtitle, children }: { title: string; subtitle?: string; children: ReactNode }) {
  return (
    <Box
      sx={{
        minHeight: '100vh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        bgcolor: 'primary.main',
        backgroundImage: 'linear-gradient(160deg, #0B2545 0%, #133A6B 100%)',
        py: 6,
      }}
    >
      <Container maxWidth="sm">
        <Stack spacing={1} alignItems="center" sx={{ mb: 3, color: 'common.white' }}>
          <AccountBalanceIcon fontSize="large" />
          <Typography variant="h5" fontWeight={700}>
            Meridian Digital Banking
          </Typography>
          <Typography variant="body2" sx={{ opacity: 0.8 }}>
            Secure Banking. Trusted by Design.
          </Typography>
        </Stack>
        <Paper elevation={0} sx={{ p: { xs: 3, sm: 5 }, borderRadius: 2 }}>
          <Typography variant="h5" fontWeight={700} gutterBottom>
            {title}
          </Typography>
          {subtitle && (
            <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>
              {subtitle}
            </Typography>
          )}
          {children}
        </Paper>
      </Container>
    </Box>
  );
}
