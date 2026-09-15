import { useSelector } from 'react-redux'
import { Navigate, Outlet } from 'react-router'
import type { RootState } from '../../store/store'

export default function WorkspaceRoute() {
  const user = useSelector((state: RootState) => state.user.user)
  const selectedCompanyId = useSelector((state: RootState) => state.user.selectedCompanyId)

  if (!user?.hasCompanies) {
    return <Navigate to="/create-workspace" replace />
  }

  if (!selectedCompanyId) {
    return <Navigate to="/select-workspace" replace />
  }

  return <Outlet />
}
