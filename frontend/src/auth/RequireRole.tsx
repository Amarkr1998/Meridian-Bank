import type { ReactNode } from 'react';
import { Navigate } from 'react-router-dom';
import { useAuth } from './AuthContext';
import { RequireAuth } from './RequireAuth';
import type { UserRole } from '../types/domain';

const STAFF_ROLES: UserRole[] = ['OPERATIONS', 'COMPLIANCE_OFFICER', 'RISK_ANALYST', 'AUDITOR', 'ADMIN'];

/** Gates every `/ops/*` route: must be authenticated AND hold a staff role. */
export function RequireStaff({ children }: { children: ReactNode }) {
  return (
    <RequireAuth>
      <StaffOnly>{children}</StaffOnly>
    </RequireAuth>
  );
}

function StaffOnly({ children }: { children: ReactNode }) {
  const { user } = useAuth();
  if (!user || !STAFF_ROLES.includes(user.role)) {
    return <Navigate to="/dashboard" replace />;
  }
  return <>{children}</>;
}

/** Gates every customer-portal route: staff accounts have no customer profile, so send them to their own portal instead. */
export function RequireCustomer({ children }: { children: ReactNode }) {
  return (
    <RequireAuth>
      <CustomerOnly>{children}</CustomerOnly>
    </RequireAuth>
  );
}

function CustomerOnly({ children }: { children: ReactNode }) {
  const { user } = useAuth();
  if (user && STAFF_ROLES.includes(user.role)) {
    return <Navigate to="/ops/dashboard" replace />;
  }
  return <>{children}</>;
}

export function isStaffRole(role: UserRole | undefined): boolean {
  return !!role && STAFF_ROLES.includes(role);
}
