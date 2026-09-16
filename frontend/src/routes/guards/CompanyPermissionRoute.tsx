import { Navigate, Outlet } from 'react-router'
import { useCompanyPermissions } from '../../features/company/useCompanyPermissions'
import type { CompanyPermission } from '../../features/company/types'

type CompanyPermissionRouteProps = {
  permissions: CompanyPermission[]
}

export default function CompanyPermissionRoute({ permissions }: CompanyPermissionRouteProps) {
  const { loading, canAny } = useCompanyPermissions()

  if (loading) {
    return (
      <div className="flex min-h-[calc(100dvh-72px)] items-center justify-center text-sm font-semibold text-slate-500">
        Loading permissions...
      </div>
    )
  }

  if (!canAny(permissions)) {
    return <Navigate to="/" replace />
  }

  return <Outlet />
}
