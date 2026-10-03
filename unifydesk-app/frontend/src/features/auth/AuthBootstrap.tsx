import { useEffect } from 'react'
import { useLocation } from 'react-router'
import { workspaceApi } from '../workspace/workspaceApi'
import { authApi } from './authApi'
import { useAppDispatch, useAppSelector } from '../../store/hooks'
import {
  selectAuthBootstrapped,
  sessionCleared,
  setAuthUser,
  setBootstrapped,
} from '../../store/auth/authSlice'
import { readSelectedCompanyId } from '../../store/store'
import { selectCompany, setCompanies } from '../../store/workspace/workspaceSlice'

export default function AuthBootstrap({ children }: { children: React.ReactNode }) {
  const dispatch = useAppDispatch()
  const bootstrapped = useAppSelector(selectAuthBootstrapped)
  const location = useLocation()
  const publicRecipientAction = location.pathname === '/unsubscribe'

  useEffect(() => {
    let active = true

    if (publicRecipientAction) return

    const bootstrap = async () => {
      try {
        const user = await authApi.refresh()
        if (!active) return
        dispatch(setAuthUser(user))

        const companies = await workspaceApi.list()
        if (!active) return
        dispatch(setCompanies(companies))

        const persistedCompanyId = readSelectedCompanyId()
        const selectedCompany = companies.find(
          (company) => company.companyId === persistedCompanyId,
        )
        const nextCompanyId = selectedCompany?.companyId ?? companies[0]?.companyId
        if (nextCompanyId) dispatch(selectCompany(nextCompanyId))
      } catch {
        if (active) dispatch(sessionCleared())
      } finally {
        if (active) dispatch(setBootstrapped())
      }
    }

    void bootstrap()
    return () => {
      active = false
    }
  }, [dispatch, publicRecipientAction])

  if (publicRecipientAction) return children

  if (!bootstrapped) {
    return (
      <div className="flex min-h-dvh items-center justify-center bg-white text-sm font-semibold text-slate-500">
        Loading...
      </div>
    )
  }

  return children
}
