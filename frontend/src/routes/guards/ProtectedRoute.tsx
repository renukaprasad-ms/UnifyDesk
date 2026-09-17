import { Navigate, Outlet } from 'react-router'
import { useAppSelector } from '../../store/hooks'
import { selectIsAuthenticated } from '../../store/auth/authSlice'

export default function ProtectedRoute() {
  const isAuthenticated = useAppSelector(selectIsAuthenticated)
  return isAuthenticated ? <Outlet /> : <Navigate to="/login" replace />
}
