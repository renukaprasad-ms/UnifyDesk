import { Navigate, Outlet } from 'react-router'
import { useAppSelector } from '../../store/hooks'
import { selectIsAuthenticated } from '../../store/auth/authSlice'

export default function GuestRoute() {
  const isAuthenticated = useAppSelector(selectIsAuthenticated)
  return isAuthenticated ? <Navigate to="/" replace /> : <Outlet />
}
