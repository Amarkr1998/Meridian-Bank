import { useState } from 'react';
import { Paper, Stack, Table, TableBody, TableCell, TableContainer, TableHead, TableRow, TextField } from '@mui/material';
import { OpsLayout } from '../../layouts/OpsLayout';
import { PageHeader } from '../../components/PageHeader';
import { LoadingBlock, ErrorBlock, EmptyState } from '../../components/Feedback';
import { useAuditEvents, type AuditSearchFilters } from '../../api/audit';

export function OpsAudit() {
  const [filters, setFilters] = useState<AuditSearchFilters>({});
  const { data: events, isLoading, error } = useAuditEvents(filters);

  function update(field: keyof AuditSearchFilters, value: string) {
    setFilters((prev) => ({ ...prev, [field]: value || undefined }));
  }

  return (
    <OpsLayout>
      <PageHeader title="Audit Trail" subtitle="Append-only record of every sensitive action across the platform." />

      <Paper sx={{ p: 2, mb: 2 }}>
        <Stack direction="row" spacing={2} flexWrap="wrap" useFlexGap>
          <TextField label="Actor ID" size="small" onChange={(e) => update('actorId', e.target.value)} sx={{ minWidth: 260 }} />
          <TextField label="Action" size="small" onChange={(e) => update('action', e.target.value)} sx={{ minWidth: 200 }} />
          <TextField label="Resource type" size="small" onChange={(e) => update('resourceType', e.target.value)} sx={{ minWidth: 180 }} />
          <TextField label="Resource ID" size="small" onChange={(e) => update('resourceId', e.target.value)} sx={{ minWidth: 260 }} />
          <TextField label="Produced by" size="small" onChange={(e) => update('producedBy', e.target.value)} sx={{ minWidth: 180 }} />
          <TextField label="Correlation ID" size="small" onChange={(e) => update('correlationId', e.target.value)} sx={{ minWidth: 260 }} />
        </Stack>
      </Paper>

      {isLoading && <LoadingBlock />}
      {error && <ErrorBlock error={error} />}
      {events && events.length === 0 && <EmptyState message="No audit events match these filters." />}

      {events && events.length > 0 && (
        <TableContainer component={Paper}>
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>Occurred</TableCell>
                <TableCell>Produced by</TableCell>
                <TableCell>Action</TableCell>
                <TableCell>Resource</TableCell>
                <TableCell>Actor</TableCell>
                <TableCell>Result</TableCell>
                <TableCell>Detail</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {events.map((e) => (
                <TableRow key={e.id} hover>
                  <TableCell>{new Date(e.occurredAt).toLocaleString()}</TableCell>
                  <TableCell>{e.producedBy}</TableCell>
                  <TableCell>{e.action}</TableCell>
                  <TableCell sx={{ fontFamily: 'monospace', fontSize: 12 }}>
                    {e.resourceType}
                    {e.resourceId ? ` / ${e.resourceId}` : ''}
                  </TableCell>
                  <TableCell sx={{ fontFamily: 'monospace', fontSize: 12 }}>
                    {e.actorId ?? '—'} {e.actorRole ? `(${e.actorRole})` : ''}
                  </TableCell>
                  <TableCell>{e.result ?? '—'}</TableCell>
                  <TableCell>{e.detail ?? '—'}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      )}
    </OpsLayout>
  );
}
