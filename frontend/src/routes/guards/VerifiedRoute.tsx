import { useSelector } from 'react-redux'
import { Navigate, Outlet } from 'react-router'
import type { RootState } from '../../store/store'

export default function VerifiedRoute() {
  const user = useSelector((state: RootState) => state.user.user)

  return user?.verified ? <Outlet /> : <Navigate to="/verifyotp" replace />
}
