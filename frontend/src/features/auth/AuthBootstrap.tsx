import { useEffect } from 'react'
import { useDispatch, useSelector } from 'react-redux'
import { authApi } from './authApi'
import {
  clearAuthUser,
  selectCompany,
  setAuthUser,
  setBootstrapped,
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
      .then((user) => {
        if (!active) return
        dispatch(setAuthUser(user))
        const selectedCompanyId = readSelectedCompanyId()
        if (user.hasCompanies && selectedCompanyId) {
          dispatch(selectCompany(selectedCompanyId))
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
