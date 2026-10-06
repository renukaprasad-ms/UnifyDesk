import { useEffect } from 'react'
import { selectAuthUser } from '../../store/auth/authSlice'
import { useAppSelector } from '../../store/hooks'
import { selectSelectedCompany } from '../../store/workspace/workspaceSlice'
import { applyCompanyTheme, DEFAULT_BRAND_COLOR } from './companyTheme'

export default function CompanyThemeProvider() {
  const selectedCompany = useAppSelector(selectSelectedCompany)
  const user = useAppSelector(selectAuthUser)

  useEffect(() => {
    applyCompanyTheme(
      selectedCompany?.brandColor ?? DEFAULT_BRAND_COLOR,
      user?.appearanceMode ?? 'LIGHT',
    )
  }, [selectedCompany?.brandColor, user?.appearanceMode])

  return null
}
