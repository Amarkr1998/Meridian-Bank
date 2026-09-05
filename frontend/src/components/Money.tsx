export function formatMoney(amount: number | undefined | null, currency = 'USD'): string {
  if (amount === undefined || amount === null) return '—';
  return new Intl.NumberFormat('en-US', { style: 'currency', currency }).format(amount);
}

export function Money({ amount, currency = 'USD' }: { amount: number | undefined | null; currency?: string }) {
  return <>{formatMoney(amount, currency)}</>;
}
