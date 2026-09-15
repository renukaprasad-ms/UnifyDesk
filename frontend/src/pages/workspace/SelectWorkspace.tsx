import { useEffect, useState } from 'react'
import { useDispatch, useSelector } from 'react-redux'
import { Navigate, useNavigate } from 'react-router'
import { FiPlus } from 'react-icons/fi'
import type { AppDispatch, RootState } from '../../store/store'
import { selectCompany, setCompanies } from '../../store/user/userSlice'
import { companyApi } from '../../features/company/companyApi'
import { apiErrorMessage } from '../../lib/api/apiClient'

export default function SelectWorkspace() {
  const dispatch = useDispatch<AppDispatch>()
  const navigate = useNavigate()
  const user = useSelector((state: RootState) => state.user.user)
  const companies = useSelector((state: RootState) => state.user.companies)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  useEffect(() => {
    let active = true

    companyApi
      .myCompanies()
      .then((items) => {
        if (!active) return
        dispatch(setCompanies(items))
        if (items.length === 1) {
          dispatch(selectCompany(items[0].companyId))
          navigate('/', { replace: true })
        }
      })
      .catch((loadError) => {
        if (active) setError(apiErrorMessage(loadError, 'Unable to load workspaces'))
      })
      .finally(() => {
        if (active) setLoading(false)
      })

    return () => {
      active = false
    }
  }, [dispatch, navigate])

  if (!user?.hasCompanies) {
    return <Navigate to="/create-workspace" replace />
  }

  return (
    <div className="min-h-dvh bg-white px-6 py-12">
      <div className="mx-auto w-full max-w-[420px]">
        <h1 className="text-[26px] font-bold leading-tight text-slate-950">Select workspace</h1>
        <p className="mt-2 text-sm text-slate-500">
          Choose the company context you want to work in.
        </p>

        {loading && <p className="mt-7 text-sm font-semibold text-slate-500">Loading...</p>}
        {error && <p className="mt-7 text-sm font-semibold text-red-600">{error}</p>}

        {!loading && !error && (
          <div className="mt-7 space-y-3">
            {companies.map((company) => (
              <button
                key={company.companyId}
                type="button"
                className="flex min-h-[58px] w-full items-center justify-between rounded-[8px] border border-slate-200 bg-white px-4 text-left hover:border-indigo-500 hover:bg-indigo-50"
                onClick={() => {
                  dispatch(selectCompany(company.companyId))
                  navigate('/', { replace: true })
                }}
              >
                <span>
                  <span className="block text-sm font-semibold text-slate-950">{company.name}</span>
                  <span className="mt-1 block text-xs text-slate-500">{company.role}</span>
                </span>
                <span className="text-xs font-semibold text-indigo-500">Open</span>
              </button>
            ))}
          </div>
        )}

        <button
          type="button"
          className="mt-5 flex w-full items-center justify-center gap-2 rounded-[8px] border border-slate-200 px-4 py-3 text-sm font-semibold text-indigo-500 hover:bg-indigo-50"
          onClick={() => navigate('/create-workspace')}
        >
          <FiPlus aria-hidden="true" />
          Create workspace
        </button>
      </div>
    </div>
  )
}
