import { Navigate, Outlet } from 'react-router'
import WorkspaceIamBootstrap from '../../features/iam/WorkspaceIamBootstrap'
import { useAppSelector } from '../../store/hooks'
import { selectSelectedCompanyId } from '../../store/workspace/workspaceSlice'

export default function WorkspaceRoute() {
  const selectedCompanyId = useAppSelector(selectSelectedCompanyId)

  if (!selectedCompanyId) {
    return <Navigate to="/select-workspace" replace />
  }

  return (
    <WorkspaceIamBootstrap>
      <Outlet />
    </WorkspaceIamBootstrap>
  )
}
