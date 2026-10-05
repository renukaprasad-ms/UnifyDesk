import { useEffect, useState } from 'react'
import { FiChevronLeft, FiChevronRight, FiSearch, FiShield, FiUser } from 'react-icons/fi'
import { platformApi } from '../../features/platform/platformApi'
import type { Page, PlatformUser } from '../../features/platform/types'
import { TableSkeleton } from '../../components/ui/Skeleton'

const initials = (name: string) =>
  name
    .split(' ')
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase())
    .join('') || 'US'

export default function PlatformUsers() {
  const [data, setData] = useState<Page<PlatformUser> | null>(null)
  const [search, setSearch] = useState('')
  const [page, setPage] = useState(0)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  const load = async (targetPage = page) => {
    setLoading(true)
    setError('')
    try {
      setData(await platformApi.users(search.trim(), targetPage))
    } catch (loadError) {
      setError(loadError instanceof Error ? loadError.message : 'Unable to load users')
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

  const users = data?.content ?? []
  const totalPages = Math.max(data?.totalPages ?? 1, 1)

  return (
    <section className="px-4 py-6 sm:px-6 lg:px-8">
      <div className="mx-auto max-w-7xl">
        <div className="flex flex-col gap-2 sm:flex-row sm:items-end sm:justify-between">
          <div>
            <p className="eyebrow">Administration</p>
            <h1 className="mt-2 text-2xl font-bold text-slate-950">Users</h1>
            <p className="mt-1 text-sm text-slate-500">
              Review platform accounts, verification status and sign-in security.
            </p>
          </div>
          <div className="text-sm font-medium text-slate-500">
            {data ? `${data.totalElements} ${data.totalElements === 1 ? 'user' : 'users'}` : '—'}
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
                  placeholder="Search name, email or username"
                />
              </label>
              <button className="primary-button min-w-24" type="submit" disabled={loading}>
                Search
              </button>
            </form>
          </div>

          {error && (
            <div className="border-b border-rose-200 bg-rose-50 px-5 py-3 text-sm font-medium text-rose-700">
              {error}
            </div>
          )}

          <div className="overflow-x-auto">
            <table className="mobile-card-table min-w-full text-left">
              <thead className="bg-slate-50/80">
                <tr className="border-b border-slate-200 text-[11px] font-semibold uppercase tracking-wider text-slate-500">
                  <th className="px-5 py-3.5">User</th>
                  <th className="px-5 py-3.5">Username</th>
                  <th className="px-5 py-3.5">Verification</th>
                  <th className="px-5 py-3.5">Two-factor authentication</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {loading && users.length === 0 ? (
                  <tr>
                    <td colSpan={4} className="p-4"><TableSkeleton rows={7} columns={4} /></td>
                  </tr>
                ) : users.length === 0 ? (
                  <tr>
                    <td colSpan={4} className="px-5 py-16 text-center">
                      <span className="mx-auto flex size-10 items-center justify-center rounded-xl bg-slate-100 text-slate-500">
                        <FiUser aria-hidden="true" />
                      </span>
                      <p className="mt-3 text-sm font-semibold text-slate-800">No users found</p>
                      <p className="mt-1 text-sm text-slate-500">Try another search term.</p>
                    </td>
                  </tr>
                ) : (
                  users.map((user) => (
                    <tr key={user.id} className="transition-colors hover:bg-slate-50/60">
                      <td className="px-5 py-4" data-label="User">
                        <div className="flex items-center gap-3">
                          <span className="flex size-9 shrink-0 items-center justify-center rounded-full bg-indigo-50 text-xs font-bold text-indigo-600">
                            {initials(user.name)}
                          </span>
                          <div className="min-w-0">
                            <p className="truncate text-sm font-semibold text-slate-950">
                              {user.name}
                            </p>
                            <p className="mt-0.5 truncate text-xs text-slate-500">{user.email}</p>
                          </div>
                        </div>
                      </td>
                      <td
                        className="px-5 py-4 text-sm font-medium text-slate-700"
                        data-label="Username"
                      >
                        @{user.username}
                      </td>
                      <td className="px-5 py-4" data-label="Verification">
                        <span
                          className={`inline-flex rounded-full px-2.5 py-1 text-xs font-semibold ring-1 ring-inset ${
                            user.verified
                              ? 'bg-emerald-50 text-emerald-700 ring-emerald-600/20'
                              : 'bg-amber-50 text-amber-700 ring-amber-600/20'
                          }`}
                        >
                          {user.verified ? 'Verified' : 'Pending'}
                        </span>
                      </td>
                      <td className="px-5 py-4" data-label="2FA">
                        <span
                          className={`inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-xs font-semibold ring-1 ring-inset ${
                            user.twoFactorEnabled
                              ? 'bg-indigo-50 text-indigo-700 ring-indigo-600/20'
                              : 'bg-slate-100 text-slate-600 ring-slate-500/20'
                          }`}
                        >
                          <FiShield aria-hidden="true" className="size-3.5" />
                          {user.twoFactorEnabled ? 'Enabled' : 'Disabled'}
                        </span>
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
      </div>
    </section>
  )
}
