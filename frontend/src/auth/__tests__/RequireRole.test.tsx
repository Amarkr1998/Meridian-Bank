import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { RequireStaff, RequireCustomer, isStaffRole } from '../RequireRole';
import { useAuth } from '../AuthContext';
import type { UserRole } from '../../types/domain';

vi.mock('../AuthContext', () => ({
  useAuth: vi.fn(),
}));

const mockUseAuth = vi.mocked(useAuth);

function renderGuarded(guard: 'staff' | 'customer', role: UserRole | undefined) {
  mockUseAuth.mockReturnValue({
    user: role ? { userId: 'u1', email: 'x@example.com', role } : null,
    isAuthenticated: role !== undefined,
    isCustomer: role === 'CUSTOMER',
    login: vi.fn(),
    verifyMfa: vi.fn(),
    logout: vi.fn(),
  });

  const Guard = guard === 'staff' ? RequireStaff : RequireCustomer;

  return render(
    <MemoryRouter initialEntries={['/protected']}>
      <Routes>
        <Route path="/protected" element={<Guard>Protected content</Guard>} />
        <Route path="/login" element={<div>Login page</div>} />
        <Route path="/dashboard" element={<div>Customer dashboard</div>} />
        <Route path="/ops/dashboard" element={<div>Ops dashboard</div>} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('isStaffRole', () => {
  it('is true for every staff role', () => {
    (['OPERATIONS', 'COMPLIANCE_OFFICER', 'RISK_ANALYST', 'AUDITOR', 'ADMIN'] as UserRole[]).forEach((role) =>
      expect(isStaffRole(role)).toBe(true),
    );
  });

  it('is false for CUSTOMER and undefined', () => {
    expect(isStaffRole('CUSTOMER')).toBe(false);
    expect(isStaffRole(undefined)).toBe(false);
  });
});

describe('RequireStaff', () => {
  it('renders its children for a staff role', () => {
    renderGuarded('staff', 'OPERATIONS');
    expect(screen.getByText('Protected content')).toBeInTheDocument();
  });

  it('redirects a customer to their own dashboard rather than showing staff content', () => {
    renderGuarded('staff', 'CUSTOMER');
    expect(screen.getByText('Customer dashboard')).toBeInTheDocument();
    expect(screen.queryByText('Protected content')).not.toBeInTheDocument();
  });

  it('redirects an unauthenticated user to login', () => {
    renderGuarded('staff', undefined);
    expect(screen.getByText('Login page')).toBeInTheDocument();
  });
});

describe('RequireCustomer', () => {
  it('renders its children for a customer', () => {
    renderGuarded('customer', 'CUSTOMER');
    expect(screen.getByText('Protected content')).toBeInTheDocument();
  });

  it('redirects a staff role to the ops portal rather than showing customer content', () => {
    renderGuarded('customer', 'RISK_ANALYST');
    expect(screen.getByText('Ops dashboard')).toBeInTheDocument();
    expect(screen.queryByText('Protected content')).not.toBeInTheDocument();
  });

  it('redirects an unauthenticated user to login', () => {
    renderGuarded('customer', undefined);
    expect(screen.getByText('Login page')).toBeInTheDocument();
  });
});
