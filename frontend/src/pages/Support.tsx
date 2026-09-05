import { useState } from 'react';
import {
  Alert,
  Button,
  Card,
  CardActionArea,
  CardContent,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  MenuItem,
  Stack,
  TextField,
  Typography,
} from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import { useNavigate } from 'react-router-dom';
import { AppLayout } from '../layouts/AppLayout';
import { PageHeader } from '../components/PageHeader';
import { LoadingBlock, ErrorBlock, EmptyState } from '../components/Feedback';
import { StatusChip } from '../components/StatusChip';
import { useCreateSupportRequest, useSupportRequests } from '../api/support';
import { ApiError } from '../api/client';
import type { SupportRequestCategory } from '../types/domain';

const CATEGORIES: SupportRequestCategory[] = ['ACCOUNT', 'PAYMENT', 'KYC', 'FRAUD', 'GENERAL'];

export function Support() {
  const navigate = useNavigate();
  const { data: requests, isLoading, error } = useSupportRequests();
  const createRequest = useCreateSupportRequest();

  const [open, setOpen] = useState(false);
  const [category, setCategory] = useState<SupportRequestCategory>('GENERAL');
  const [subject, setSubject] = useState('');
  const [description, setDescription] = useState('');
  const [formError, setFormError] = useState<string | null>(null);

  async function handleCreate() {
    setFormError(null);
    try {
      await createRequest.mutateAsync({ category, subject, description });
      setOpen(false);
      setSubject('');
      setDescription('');
      setCategory('GENERAL');
    } catch (err) {
      setFormError(err instanceof ApiError ? err.message : 'Unable to submit your request.');
    }
  }

  return (
    <AppLayout>
      <PageHeader
        title="Support"
        subtitle="Get help from our team."
        action={
          <Button variant="contained" startIcon={<AddIcon />} onClick={() => setOpen(true)}>
            New request
          </Button>
        }
      />

      {isLoading && <LoadingBlock />}
      {error && <ErrorBlock error={error} />}
      {requests && requests.length === 0 && <EmptyState message="You haven't raised any support requests yet." />}

      <Stack spacing={2}>
        {requests?.map((r) => (
          <Card key={r.id}>
            <CardActionArea onClick={() => navigate(`/support/${r.id}`)}>
              <CardContent>
                <Stack direction="row" justifyContent="space-between" alignItems="center">
                  <Stack>
                    <Typography variant="subtitle1" fontWeight={600}>
                      {r.subject}
                    </Typography>
                    <Typography variant="caption" color="text.secondary">
                      {r.category} · {new Date(r.createdAt).toLocaleString()}
                    </Typography>
                  </Stack>
                  <StatusChip status={r.status} />
                </Stack>
              </CardContent>
            </CardActionArea>
          </Card>
        ))}
      </Stack>

      <Dialog open={open} onClose={() => setOpen(false)} fullWidth maxWidth="sm">
        <DialogTitle>New support request</DialogTitle>
        <DialogContent>
          {formError && (
            <Alert severity="error" sx={{ mb: 2 }}>
              {formError}
            </Alert>
          )}
          <Stack spacing={2} sx={{ mt: 1 }}>
            <TextField select label="Category" value={category} onChange={(e) => setCategory(e.target.value as SupportRequestCategory)} fullWidth>
              {CATEGORIES.map((c) => (
                <MenuItem key={c} value={c}>
                  {c}
                </MenuItem>
              ))}
            </TextField>
            <TextField label="Subject" value={subject} onChange={(e) => setSubject(e.target.value)} fullWidth />
            <TextField
              label="Description"
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              fullWidth
              multiline
              minRows={4}
            />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setOpen(false)}>Cancel</Button>
          <Button variant="contained" onClick={handleCreate} disabled={createRequest.isPending || !subject || !description}>
            Submit
          </Button>
        </DialogActions>
      </Dialog>
    </AppLayout>
  );
}
