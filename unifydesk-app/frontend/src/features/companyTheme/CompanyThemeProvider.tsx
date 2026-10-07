import { useLayoutEffect } from 'react'
import { selectAuthUser } from '../../store/auth/authSlice'
import { useAppSelector } from '../../store/hooks'
import { selectSelectedCompany } from '../../store/workspace/workspaceSlice'
import { applyCompanyTheme, DEFAULT_BRAND_COLOR, readStoredAppearanceMode } from './companyTheme'

export default function CompanyThemeProvider() {
  const selectedCompany = useAppSelector(selectSelectedCompany)
  const user = useAppSelector(selectAuthUser)
  const appearanceMode = user?.appearanceMode ?? readStoredAppearanceMode() ?? 'LIGHT'

  useLayoutEffect(() => {
    applyCompanyTheme(
      selectedCompany?.brandColor ?? DEFAULT_BRAND_COLOR,
      appearanceMode,
    )
  }, [appearanceMode, selectedCompany?.brandColor])

  return null
}
