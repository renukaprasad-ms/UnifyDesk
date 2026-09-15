import { useSelector } from 'react-redux'
import { Navigate, Outlet } from 'react-router'
import type { RootState } from '../../store/store'

export default function WorkspaceRoute() {
  const user = useSelector((state: RootState) => state.user.user)
  const selectedCompany = useSelector((state: RootState) => state.user.selectedCompany)

  if (!user?.hasCompanies) {
    return <Navigate to="/create-workspace" replace />
  }

  if (!selectedCompany) {
    return <Navigate to="/select-workspace" replace />
  }

  return <Outlet />
}
