import { useSnackbarState } from '../../components/feedback/useSnackbarState'
import { useEffect, useState } from 'react'
import { FiBriefcase, FiChevronLeft, FiChevronRight, FiSearch } from 'react-icons/fi'
import { platformApi } from '../../features/platform/platformApi'
import type { Page, PlatformCompany } from '../../features/platform/types'
import ConfirmDialog from '../../components/ui/ConfirmDialog'
import StatusBadge from '../../components/ui/StatusBadge'
import { TableSkeleton } from '../../components/ui/Skeleton'

const companyInitials = (name: string) =>
  name
    .split(' ')
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase())
    .join('') || 'CO'

export default function PlatformCompanies() {
  const [data, setData] = useState<Page<PlatformCompany> | null>(null)
  const [search, setSearch] = useState('')
  const [page, setPage] = useState(0)
  const [loading, setLoading] = useState(true)
  const [changingId, setChangingId] = useState<string | null>(null)
  const [, setError] = useSnackbarState('', 'error')
  const [pendingStatusCompany, setPendingStatusCompany] = useState<PlatformCompany | null>(null)

  const load = async (targetPage = page) => {
    setLoading(true)
    setError('')
    try {
      setData(await platformApi.companies(search.trim(), targetPage))
    } catch (loadError) {
      setError(loadError instanceof Error ? loadError.message : 'Unable to load companies')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    void load(page)
  }, [page])

  const submitSearch = (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (page === 0) void load(0)
    else setPage(0)
  }

  const toggleCompanyStatus = async (company: PlatformCompany) => {
    const nextStatus = company.status === 'ACTIVE' ? 'SUSPENDED' : 'ACTIVE'
    const verb = nextStatus === 'SUSPENDED' ? 'suspend' : 'activate'

    setChangingId(company.id)
    setError('')
    try {
      await platformApi.updateCompanyStatus(company.id, nextStatus)
      await load(page)
    } catch (updateError) {
      setError(updateError instanceof Error ? updateError.message : `Unable to ${verb} company`)
    } finally {
      setChangingId(null)
      setPendingStatusCompany(null)
    }
  }

  const companies = data?.content ?? []
  const totalPages = Math.max(data?.totalPages ?? 1, 1)

  return (
    <section className="px-4 py-6 sm:px-6 lg:px-8">
      <div className="mx-auto max-w-7xl">
        <div className="flex flex-col gap-2 sm:flex-row sm:items-end sm:justify-between">
          <div>
            <p className="eyebrow">Administration</p>
            <h1 className="mt-2 text-2xl font-bold text-slate-950">Companies</h1>
            <p className="mt-1 text-sm text-slate-500">
              Manage workspaces, account status and platform access.
            </p>
          </div>
          <div className="text-sm font-medium text-slate-500">
            {data
              ? `${data.totalElements} ${data.totalElements === 1 ? 'company' : 'companies'}`
              : '—'}
          </div>
        </div>

        <div className="mt-6 overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm shadow-slate-200/40">
          <div className="border-b border-slate-200 p-4 sm:p-5">
            <form className="flex flex-col gap-3 sm:flex-row" onSubmit={submitSearch}>
              <label className="relative block w-full max-w-xl">
                <FiSearch
                  aria-hidden="true"
                  className="pointer-events-none absolute left-3.5 top-1/2 -translate-y-1/2 text-slate-400"
                />
                <input
                  className="form-input w-full pl-10"
                  value={search}
                  onChange={(event) => setSearch(event.target.value)}
                  placeholder="Search company name or business email"
                />
              </label>
              <button className="primary-button min-w-24" type="submit" disabled={loading}>
                Search
              </button>
            </form>
          </div>


          {loading && companies.length === 0 && (
            <div className="p-4 sm:p-5">
              <TableSkeleton rows={7} columns={5} />
            </div>
          )}

          <div className="overflow-x-auto">
            <table className="mobile-card-table min-w-full text-left">
              <thead className="bg-slate-50/80">
                <tr className="border-b border-slate-200 text-[11px] font-semibold uppercase tracking-wider text-slate-500">
                  <th className="px-5 py-3.5">Company</th>
                  <th className="px-5 py-3.5">Business email</th>
                  <th className="px-5 py-3.5">Country</th>
                  <th className="px-5 py-3.5">Status</th>
                  <th className="px-5 py-3.5 text-right">Action</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {loading && companies.length === 0 ? (
                  <tr className="hidden"><td>Loading</td></tr>
                ) : companies.length === 0 ? (
                  <tr>
                    <td colSpan={5} className="px-5 py-16 text-center">
                      <span className="mx-auto flex size-10 items-center justify-center rounded-xl bg-slate-100 text-slate-500">
                        <FiBriefcase aria-hidden="true" />
                      </span>
                      <p className="mt-3 text-sm font-semibold text-slate-800">
                        No companies found
                      </p>
                      <p className="mt-1 text-sm text-slate-500">Try another search term.</p>
                    </td>
                  </tr>
                ) : (
                  companies.map((company) => (
                    <tr key={company.id} className="transition-colors hover:bg-slate-50/60">
                      <td className="px-5 py-4" data-label="Company">
                        <div className="flex items-center gap-3">
                          <span className="flex size-9 shrink-0 items-center justify-center rounded-xl bg-indigo-50 text-xs font-bold text-indigo-600">
                            {companyInitials(company.name)}
                          </span>
                          <div className="min-w-0">
                            <p className="truncate text-sm font-semibold text-slate-950">
                              {company.name}
                            </p>
                            <p className="mt-0.5 truncate text-xs text-slate-500">{company.slug}</p>
                          </div>
                        </div>
                      </td>
                      <td className="px-5 py-4 text-sm text-slate-600" data-label="Email">
                        {company.businessEmail || '—'}
                      </td>
                      <td className="px-5 py-4 text-sm text-slate-600" data-label="Country">
                        {company.country || '—'}
                      </td>
                      <td className="px-5 py-4" data-label="Status">
                        <StatusBadge status={company.status}>
                          {company.status.charAt(0) + company.status.slice(1).toLowerCase()}
                        </StatusBadge>
                      </td>
                      <td className="mobile-action-cell px-5 py-4 text-right" data-label="Action">
                        {(company.status === 'ACTIVE' || company.status === 'SUSPENDED') && (
                          <button
                            type="button"
                            disabled={changingId === company.id}
                            onClick={() => setPendingStatusCompany(company)}
                            className={`rounded-lg px-3 py-2 text-xs font-semibold transition-colors disabled:cursor-not-allowed disabled:opacity-50 ${
                              company.status === 'ACTIVE'
                                ? 'text-rose-600 hover:bg-rose-50'
                                : 'text-emerald-700 hover:bg-emerald-50'
                            }`}
                          >
                            {changingId === company.id
                              ? 'Updating…'
                              : company.status === 'ACTIVE'
                                ? 'Suspend'
                                : 'Activate'}
                          </button>
                        )}
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>

          <div className="flex flex-col gap-3 border-t border-slate-200 px-5 py-4 sm:flex-row sm:items-center sm:justify-between">
            <p className="text-sm text-slate-500">
              Page <span className="font-semibold text-slate-800">{(data?.number ?? 0) + 1}</span>{' '}
              of <span className="font-semibold text-slate-800">{totalPages}</span>
            </p>
            <div className="flex items-center gap-2">
              <button
                type="button"
                disabled={!data || data.first || loading}
                onClick={() => setPage((current) => Math.max(0, current - 1))}
                className="secondary-button inline-flex items-center gap-2 disabled:cursor-not-allowed disabled:opacity-40"
              >
                <FiChevronLeft aria-hidden="true" />
                Previous
              </button>
              <button
                type="button"
                disabled={!data || data.last || loading}
                onClick={() => setPage((current) => current + 1)}
                className="secondary-button inline-flex items-center gap-2 disabled:cursor-not-allowed disabled:opacity-40"
              >
                Next
                <FiChevronRight aria-hidden="true" />
              </button>
            </div>
          </div>
        </div>
        <ConfirmDialog
          open={Boolean(pendingStatusCompany)}
          title={pendingStatusCompany?.status === 'ACTIVE' ? 'Suspend this company?' : 'Activate this company?'}
          description={
            pendingStatusCompany?.status === 'ACTIVE'
              ? `Users may lose access to ${pendingStatusCompany?.name ?? 'this company'} until it is activated again.`
              : `Restore access to ${pendingStatusCompany?.name ?? 'this company'}?`
          }
          confirmLabel={pendingStatusCompany?.status === 'ACTIVE' ? 'Yes, suspend' : 'Yes, activate'}
          cancelLabel="No, cancel"
          tone={pendingStatusCompany?.status === 'ACTIVE' ? 'warning' : 'default'}
          working={Boolean(pendingStatusCompany && changingId === pendingStatusCompany.id)}
          onCancel={() => setPendingStatusCompany(null)}
          onConfirm={() => pendingStatusCompany && void toggleCompanyStatus(pendingStatusCompany)}
        />
      </div>
    </section>
  )
}
