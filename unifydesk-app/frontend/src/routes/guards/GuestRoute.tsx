import { Navigate, Outlet, useLocation } from 'react-router'
import { resolvePostAuthRedirect } from '../../features/company/invitationContinuation'
import { selectIsAuthenticated } from '../../store/auth/authSlice'
import { useAppSelector } from '../../store/hooks'

export default function GuestRoute() {
  const isAuthenticated = useAppSelector(selectIsAuthenticated)
  const location = useLocation()
  const redirectTo = resolvePostAuthRedirect(
    (location.state as { redirectTo?: string } | null)?.redirectTo,
  )

  return isAuthenticated ? <Navigate to={redirectTo} replace /> : <Outlet />
}
