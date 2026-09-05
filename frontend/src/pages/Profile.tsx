import { useEffect, useState } from 'react';
import { Alert, Button, Card, CardContent, Stack, TextField, Typography } from '@mui/material';
import Grid from '@mui/material/Grid2';
import { AppLayout } from '../layouts/AppLayout';
import { PageHeader } from '../components/PageHeader';
import { LoadingBlock, ErrorBlock } from '../components/Feedback';
import { useAuth } from '../auth/AuthContext';
import { useCustomer, useUpdateCustomer } from '../api/customers';
import { ApiError } from '../api/client';

export function Profile() {
  const { user } = useAuth();
  const { data: customer, isLoading, error } = useCustomer(user?.userId);
  const updateCustomer = useUpdateCustomer(user?.userId);

  const [form, setForm] = useState({
    phone: '',
    addressLine1: '',
    addressLine2: '',
    city: '',
    state: '',
    postalCode: '',
    country: '',
  });
  const [saveError, setSaveError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);

  useEffect(() => {
    if (customer) {
      setForm({
        phone: customer.phone,
        addressLine1: customer.addressLine1,
        addressLine2: customer.addressLine2 ?? '',
        city: customer.city,
        state: customer.state,
        postalCode: customer.postalCode,
        country: customer.country,
      });
    }
  }, [customer]);

  async function handleSave() {
    setSaveError(null);
    setSaved(false);
    try {
      await updateCustomer.mutateAsync(form);
      setSaved(true);
    } catch (err) {
      setSaveError(err instanceof ApiError ? err.message : 'Unable to save changes.');
    }
  }

  return (
    <AppLayout>
      <PageHeader title="Profile" subtitle="Your personal and contact details." />

      {isLoading && <LoadingBlock />}
      {error && <ErrorBlock error={error} />}

      {customer && (
        <Card sx={{ maxWidth: 640 }}>
          <CardContent>
            <Stack direction="row" spacing={4} sx={{ mb: 3 }}>
              <Stack>
                <Typography variant="caption" color="text.secondary">
                  Full name
                </Typography>
                <Typography variant="body1" fontWeight={600}>
                  {customer.firstName} {customer.lastName}
                </Typography>
              </Stack>
              <Stack>
                <Typography variant="caption" color="text.secondary">
                  Email
                </Typography>
                <Typography variant="body1" fontWeight={600}>
                  {customer.email}
                </Typography>
              </Stack>
              <Stack>
                <Typography variant="caption" color="text.secondary">
                  Date of birth
                </Typography>
                <Typography variant="body1" fontWeight={600}>
                  {customer.dateOfBirth}
                </Typography>
              </Stack>
            </Stack>

            {saved && (
              <Alert severity="success" sx={{ mb: 2 }}>
                Profile updated.
              </Alert>
            )}
            {saveError && (
              <Alert severity="error" sx={{ mb: 2 }}>
                {saveError}
              </Alert>
            )}

            <Grid container spacing={2}>
              <Grid size={12}>
                <TextField label="Phone" fullWidth value={form.phone} onChange={(e) => setForm({ ...form, phone: e.target.value })} />
              </Grid>
              <Grid size={12}>
                <TextField
                  label="Address line 1"
                  fullWidth
                  value={form.addressLine1}
                  onChange={(e) => setForm({ ...form, addressLine1: e.target.value })}
                />
              </Grid>
              <Grid size={12}>
                <TextField
                  label="Address line 2"
                  fullWidth
                  value={form.addressLine2}
                  onChange={(e) => setForm({ ...form, addressLine2: e.target.value })}
                />
              </Grid>
              <Grid size={4}>
                <TextField label="City" fullWidth value={form.city} onChange={(e) => setForm({ ...form, city: e.target.value })} />
              </Grid>
              <Grid size={4}>
                <TextField label="State" fullWidth value={form.state} onChange={(e) => setForm({ ...form, state: e.target.value })} />
              </Grid>
              <Grid size={4}>
                <TextField
                  label="Postal code"
                  fullWidth
                  value={form.postalCode}
                  onChange={(e) => setForm({ ...form, postalCode: e.target.value })}
                />
              </Grid>
              <Grid size={12}>
                <TextField label="Country" fullWidth value={form.country} onChange={(e) => setForm({ ...form, country: e.target.value })} />
              </Grid>
            </Grid>

            <Button variant="contained" sx={{ mt: 3 }} onClick={handleSave} disabled={updateCustomer.isPending}>
              Save changes
            </Button>
          </CardContent>
        </Card>
      )}
    </AppLayout>
  );
}
