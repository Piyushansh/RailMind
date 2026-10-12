import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { useAuthStore } from '@/store/authStore';

export function ProtectedRoute() {
  const accessToken = useAuthStore((s) => s.accessToken);
  const location = useLocation();

  if (!accessToken) {
    return <Navigate to="/login" state={{ from: location }} replace />;
  }
  return <Outlet />;
}

/** Backend is still the authoritative enforcement (SecurityConfig's hasRole checks) -
 * this only prevents rendering a UI the user's own token can't actually call. */
export function RoleRoute({ role }: { role: string }) {
  const hasRole = useAuthStore((s) => s.hasRole);
  if (!hasRole(role)) {
    return <Navigate to="/dashboard" replace />;
  }
  return <Outlet />;
}
