import { useEffect, useState } from 'react'
import { useSelector } from 'react-redux'
import { Navigate } from 'react-router'
import { platformApi } from '../../features/platform/platformApi'
import type { RootState } from '../../store/store'

export default function HomeRoute() {
  const user = useSelector((state: RootState) => state.user.user)
  const selectedCompanyId = useSelector((state: RootState) => state.user.selectedCompanyId)
  const [platformAdmin, setPlatformAdmin] = useState<boolean | null>(null)

  useEffect(() => {
    let active = true
    platformApi
      .me()
      .then((access) => {
        if (active) setPlatformAdmin(access.platformAdmin)
      })
      .catch(() => {
        if (active) setPlatformAdmin(false)
      })
    return () => {
      active = false
    }
  }, [])

  if (platformAdmin === null) {
    return <div className="p-6 text-sm text-slate-500">Loading…</div>
  }
  if (platformAdmin) return <Navigate to="/platform" replace />
  if (!user?.hasCompanies) return <Navigate to="/create-workspace" replace />
  if (!selectedCompanyId) return <Navigate to="/select-workspace" replace />
  return <Navigate to="/workspace" replace />
}
