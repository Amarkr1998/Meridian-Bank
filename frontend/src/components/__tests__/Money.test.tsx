import { describe, expect, it } from 'vitest';
import { formatMoney } from '../Money';

describe('formatMoney', () => {
  it('formats a positive amount as USD currency by default', () => {
    expect(formatMoney(1234.5)).toBe('$1,234.50');
  });

  it('formats using the given currency code', () => {
    expect(formatMoney(10, 'EUR')).toBe('€10.00');
  });

  it('renders an em dash for null or undefined rather than "$NaN"', () => {
    expect(formatMoney(null)).toBe('—');
    expect(formatMoney(undefined)).toBe('—');
  });

  it('formats zero correctly (not confused with "missing")', () => {
    expect(formatMoney(0)).toBe('$0.00');
  });
});
