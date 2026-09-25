import { useEffect, useState } from 'react'
import { Navigate, Outlet } from 'react-router'
import { platformApi } from '../../features/platform/platformApi'

const PlatformRoute = () => {
  const [allowed, setAllowed] = useState<boolean | null>(null)

  useEffect(() => {
    platformApi
      .me()
      .then((access) => setAllowed(access.platformAdmin))
      .catch(() => setAllowed(false))
  }, [])

  if (allowed === null) return <div className="p-6 text-sm text-slate-500">Loading…</div>
  return allowed ? <Outlet /> : <Navigate to="/" replace />
}

export default PlatformRoute
