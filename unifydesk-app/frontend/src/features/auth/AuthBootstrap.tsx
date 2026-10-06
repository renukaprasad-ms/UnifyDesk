import { useEffect } from 'react'
import Spinner from '../../components/ui/Spinner'
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
        const activeCompanies = companies.filter((company) => company.status === 'ACTIVE')
        const selectedCompany = activeCompanies.find(
          (company) => company.companyId === persistedCompanyId,
        )
        const nextCompanyId = selectedCompany?.companyId ?? activeCompanies[0]?.companyId
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
      <div
        className="flex min-h-dvh items-center justify-center bg-white"
        role="status"
        aria-live="polite"
        aria-label="Loading UnifyDesk"
      >
        <div className="flex flex-col items-center gap-3 text-slate-500">
          <Spinner size="lg" label="Loading UnifyDesk" className="text-blue-600" />
          <p className="text-sm font-semibold">Loading UnifyDesk…</p>
        </div>
      </div>
    )
  }

  return children
}
