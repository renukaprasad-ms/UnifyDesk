import { Navigate, Outlet } from 'react-router'
import { useAppSelector } from '../../store/hooks'
import { selectAuthUser } from '../../store/auth/authSlice'

export default function VerifiedRoute() {
  const user = useAppSelector(selectAuthUser)
  return user?.verified ? <Outlet /> : <Navigate to="/verifyotp" replace />
}
