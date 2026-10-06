import { useEffect } from 'react'
import { useAppSelector } from '../../store/hooks'
import { selectSelectedCompanyId } from '../../store/workspace/workspaceSlice'
import { applyCompanyTheme, readCompanyTheme } from './companyTheme'

export default function CompanyThemeProvider() {
  const selectedCompanyId = useAppSelector(selectSelectedCompanyId)

  useEffect(() => {
    applyCompanyTheme(readCompanyTheme(selectedCompanyId))
  }, [selectedCompanyId])

  return null
}
