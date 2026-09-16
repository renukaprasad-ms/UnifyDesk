import { useEffect, useMemo, useState } from 'react'
import { useSelector } from 'react-redux'
import { apiErrorMessage } from '../../lib/api/apiClient'
import { selectSelectedCompany } from '../../store/user/userSlice'
import { companyApi } from './companyApi'
import type { CompanyPermission } from './types'

export const useCompanyPermissions = () => {
  const selectedCompany = useSelector(selectSelectedCompany)
  const [permissions, setPermissions] = useState<CompanyPermission[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  useEffect(() => {
    let active = true

    if (!selectedCompany) {
      setPermissions([])
      setLoading(false)
      return
    }

    setLoading(true)
    setError('')
    companyApi
      .permissions(selectedCompany.companyId)
      .then((response) => {
        if (active) setPermissions(response.permissions)
      })
      .catch((loadError) => {
        if (active) {
          setPermissions([])
          setError(apiErrorMessage(loadError, 'Unable to load permissions'))
        }
      })
      .finally(() => {
        if (active) setLoading(false)
      })

    return () => {
      active = false
    }
  }, [selectedCompany?.companyId])

  const permissionSet = useMemo(() => new Set(permissions), [permissions])
  const can = (permission: CompanyPermission) => permissionSet.has(permission)
  const canAny = (requiredPermissions: CompanyPermission[]) =>
    requiredPermissions.some((permission) => permissionSet.has(permission))

  return { permissions, loading, error, can, canAny }
}
