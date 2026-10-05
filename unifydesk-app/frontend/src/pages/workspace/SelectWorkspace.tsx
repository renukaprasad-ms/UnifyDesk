import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router'
import { FiMail, FiPlus } from 'react-icons/fi'
import { companyApi } from '../../features/company/companyApi'
import type { Company } from '../../features/company/types'
import { platformApi } from '../../features/platform/platformApi'
import { workspaceApi } from '../../features/workspace/workspaceApi'
import { apiErrorMessage } from '../../lib/api/apiClient'
import { useAppDispatch, useAppSelector } from '../../store/hooks'
import { selectCompanies, selectCompany, setCompanies } from '../../store/workspace/workspaceSlice'
import { Skeleton } from '../../components/ui/Skeleton'

const mergeCompanies = (memberCompanies: Company[], adminCompanies: Company[]) => {
  const byId = new Map<string, Company>()
  adminCompanies.forEach((company) => byId.set(company.companyId, company))
  memberCompanies.forEach((company) => byId.set(company.companyId, company))
  return [...byId.values()].sort((a, b) => a.name.localeCompare(b.name))
}

export default function SelectWorkspace() {
  const dispatch = useAppDispatch()
  const navigate = useNavigate()
  const companies = useAppSelector(selectCompanies)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  useEffect(() => {
    let active = true

    const load = async () => {
      try {
        const memberCompanies = await workspaceApi.list()
        let adminCompanies: Company[] = []
        try {
          const access = await platformApi.me()
          if (access.platformAdmin) adminCompanies = await workspaceApi.adminVisible()
        } catch {
          adminCompanies = []
        }
        const items = mergeCompanies(memberCompanies, adminCompanies)
        if (!active) return
        dispatch(setCompanies(items))
        if (items.length === 1) {
          dispatch(selectCompany(items[0].companyId))
          navigate('/workspace', { replace: true })
          return
        }
        if (items.length === 0) {
          const invitations = await companyApi.myInvitations().catch(() => [])
          if (!active) return
          if (invitations.some((invitation) => invitation.status === 'PENDING')) {
            navigate('/account/invitations', { replace: true })
          } else {
            navigate('/create-workspace', { replace: true })
          }
        }
      } catch (loadError) {
        if (active) setError(apiErrorMessage(loadError, 'Unable to load workspaces'))
      } finally {
        if (active) setLoading(false)
      }
    }

    void load()
    return () => {
      active = false
    }
  }, [dispatch, navigate])

  return (
    <div className="min-h-dvh bg-white px-6 py-12">
      <div className="mx-auto w-full max-w-[420px]">
        <h1 className="text-[26px] font-bold leading-tight text-slate-950">Select workspace</h1>
        <p className="mt-2 text-sm text-slate-500">
          Choose the company context you want to work in.
        </p>
        {loading && (
          <div className="mt-7 space-y-3" role="status" aria-label="Loading workspaces">
            {Array.from({ length: 3 }).map((_, index) => (
              <div key={index} className="flex min-h-[58px] items-center justify-between rounded-[8px] border border-slate-200 px-4">
                <div className="flex-1">
                  <Skeleton className="h-4 w-40" />
                  <Skeleton className="mt-2 h-3 w-24" />
                </div>
                <Skeleton className="h-4 w-10" />
              </div>
            ))}
          </div>
        )}
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
                  navigate('/workspace', { replace: true })
                }}
              >
                <span>
                  <span className="block text-sm font-semibold text-slate-950">{company.name}</span>
                  <span className="mt-1 block text-xs text-slate-500">
                    {company.role ?? 'Platform access'}
                  </span>
                </span>
                <span className="text-xs font-semibold text-indigo-500">Open</span>
              </button>
            ))}
          </div>
        )}
        <button
          type="button"
          className="mt-5 flex w-full items-center justify-center gap-2 rounded-[8px] border border-slate-200 px-4 py-3 text-sm font-semibold text-indigo-500 hover:bg-indigo-50"
          onClick={() => navigate('/account/invitations')}
        >
          <FiMail aria-hidden="true" />
          View invitations
        </button>
        <button
          type="button"
          className="mt-3 flex w-full items-center justify-center gap-2 rounded-[8px] border border-slate-200 px-4 py-3 text-sm font-semibold text-indigo-500 hover:bg-indigo-50"
          onClick={() => navigate('/create-workspace')}
        >
          <FiPlus aria-hidden="true" />
          Create workspace
        </button>
      </div>
    </div>
  )
}
