import { Navigate, Outlet } from 'react-router'
import type { PermissionKey } from '../../features/iam/permissions'
import { useIam } from '../../features/iam/useIam'

type PermissionRouteProps = {
  anyOf?: readonly PermissionKey[]
  allOf?: readonly PermissionKey[]
}

export default function PermissionRoute({ anyOf = [], allOf = [] }: PermissionRouteProps) {
  const { status, canAny, canAll } = useIam()

  if (status !== 'ready') {
    return (
      <div className="flex min-h-[calc(100dvh-72px)] items-center justify-center text-sm font-semibold text-slate-500">
        Loading permissions...
      </div>
    )
  }

  const allowedByAny = anyOf.length === 0 || canAny(anyOf)
  const allowedByAll = allOf.length === 0 || canAll(allOf)

  return allowedByAny && allowedByAll ? <Outlet /> : <Navigate to="/workspace" replace />
}
