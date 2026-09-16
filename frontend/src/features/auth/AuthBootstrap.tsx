import { useEffect } from 'react'
import { useDispatch, useSelector } from 'react-redux'
import { companyApi } from '../company/companyApi'
import { authApi } from './authApi'
import {
  clearAuthUser,
  selectCompany,
  setAuthUser,
  setBootstrapped,
  setCompanies,
} from '../../store/user/userSlice'
import { readSelectedCompanyId } from '../../store/store'
import type { AppDispatch, RootState } from '../../store/store'

export default function AuthBootstrap({ children }: { children: React.ReactNode }) {
  const dispatch = useDispatch<AppDispatch>()
  const bootstrapped = useSelector((state: RootState) => state.user.bootstrapped)

  useEffect(() => {
    let active = true

    authApi
      .refresh()
      .then(async (user) => {
        if (!active) return
        dispatch(setAuthUser(user))

        if (user.hasCompanies) {
          const companies = await companyApi.myCompanies()
          if (!active) return

          dispatch(setCompanies(companies))
          const selectedCompanyId = readSelectedCompanyId()
          const selectedCompany = companies.find((company) => company.companyId === selectedCompanyId)
          dispatch(selectCompany(selectedCompany?.companyId ?? companies[0]?.companyId))
        }
      })
      .catch(() => {
        if (active) dispatch(clearAuthUser())
      })
      .finally(() => {
        if (active) dispatch(setBootstrapped())
      })

    return () => {
      active = false
    }
  }, [dispatch])

  if (!bootstrapped) {
    return (
      <div className="flex min-h-dvh items-center justify-center bg-white text-sm font-semibold text-slate-500">
        Loading...
      </div>
    )
  }

  return children
}
