import { Navigate } from 'react-router-dom';
import { useAuth } from './auth/AuthContext';
import { isStaffRole } from './auth/RequireRole';

/** Sends "/" and any unknown path to the right home for who's asking. */
export function RootRedirect() {
  const { isAuthenticated, user } = useAuth();
  if (!isAuthenticated) return <Navigate to="/login" replace />;
  return <Navigate to={isStaffRole(user?.role) ? '/ops/dashboard' : '/dashboard'} replace />;
}
