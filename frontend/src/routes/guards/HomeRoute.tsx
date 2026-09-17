import { useEffect, useState } from 'react'
import { Navigate } from 'react-router'
import { platformApi } from '../../features/platform/platformApi'
import { useAppSelector } from '../../store/hooks'
import {
  selectCompanies,
  selectSelectedCompanyId,
} from '../../store/workspace/workspaceSlice'

export default function HomeRoute() {
  const companies = useAppSelector(selectCompanies)
  const selectedCompanyId = useAppSelector(selectSelectedCompanyId)
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
  if (selectedCompanyId) return <Navigate to="/workspace" replace />
  if (companies.length > 0) return <Navigate to="/select-workspace" replace />
  return <Navigate to="/select-workspace" replace />
}
