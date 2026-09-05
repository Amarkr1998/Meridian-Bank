import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { StatusChip } from '../StatusChip';

describe('StatusChip', () => {
  it('renders a known status with its human-readable label', () => {
    render(<StatusChip status="UNDER_REVIEW" />);
    expect(screen.getByText('Under review')).toBeInTheDocument();
  });

  it('title-cases an unrecognized status rather than crashing', () => {
    render(<StatusChip status="SOMETHING_NEW" />);
    expect(screen.getByText('Something new')).toBeInTheDocument();
  });

  it('renders the KYC status overrides correctly', () => {
    render(<StatusChip status="KYC_IN_REVIEW" />);
    expect(screen.getByText('In review')).toBeInTheDocument();
  });
});
