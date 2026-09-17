import { useEffect, useMemo, useState } from 'react'
import { FiRefreshCw, FiShield, FiUsers } from 'react-icons/fi'
import { companyApi } from '../../features/company/companyApi'
import type { CompanyUser } from '../../features/company/types'
import { apiErrorMessage } from '../../lib/api/apiClient'
import { useAppSelector } from '../../store/hooks'
import { selectSelectedCompany } from '../../store/workspace/workspaceSlice'

const roleClass: Record<CompanyUser['role'], string> = {
  OWNER: 'bg-indigo-50 text-indigo-700',
  MANAGER: 'bg-sky-50 text-sky-700',
  MEMBER: 'bg-slate-100 text-slate-700',
}

export default function CompanyUsers() {
  const selectedCompany = useAppSelector(selectSelectedCompany)
  const [users, setUsers] = useState<CompanyUser[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  const sortedUsers = useMemo(
    () =>
      [...users].sort((first, second) => {
        const roleOrder = { OWNER: 0, MANAGER: 1, MEMBER: 2 }
        return roleOrder[first.role] - roleOrder[second.role] || first.name.localeCompare(second.name)
      }),
    [users],
  )

  const loadUsers = async () => {
    if (!selectedCompany) return
    setError('')
    setLoading(true)
    try {
      setUsers(await companyApi.users(selectedCompany.companyId))
    } catch (loadError) {
      setError(apiErrorMessage(loadError, 'Unable to load company users'))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    void loadUsers()
  }, [selectedCompany?.companyId])

  return (
    <section className="px-4 py-6 sm:px-6 lg:px-8">
      <div className="mx-auto max-w-6xl">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div><p className="eyebrow">Company</p><h1 className="mt-2 text-2xl font-bold text-slate-950">Users</h1></div>
          <button type="button" className="secondary-button inline-flex" onClick={loadUsers}><FiRefreshCw aria-hidden="true" /><span className="ml-2">Refresh</span></button>
        </div>
        {error && <div className="mt-4 rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">{error}</div>}
        <div className="mt-5 grid gap-4 sm:grid-cols-3">
          <div className="panel p-5"><p className="text-xs font-semibold text-slate-500">Total users</p><p className="mt-2 text-3xl font-bold text-slate-950">{users.length}</p></div>
          <div className="panel p-5"><p className="text-xs font-semibold text-slate-500">Verified</p><p className="mt-2 text-3xl font-bold text-slate-950">{users.filter((user) => user.verified).length}</p></div>
          <div className="panel p-5"><p className="text-xs font-semibold text-slate-500">2FA enabled</p><p className="mt-2 text-3xl font-bold text-slate-950">{users.filter((user) => user.twoFactorEnabled).length}</p></div>
        </div>
        <div className="panel mt-5 overflow-hidden">
          <div className="overflow-x-auto">
            <table className="min-w-full divide-y divide-slate-200 text-left text-sm">
              <thead className="bg-slate-50 text-xs font-semibold uppercase tracking-wide text-slate-500"><tr><th className="px-4 py-3">User</th><th className="px-4 py-3">Role</th><th className="px-4 py-3">Security</th><th className="px-4 py-3">Joined</th></tr></thead>
              <tbody className="divide-y divide-slate-100 bg-white">
                {loading ? <tr><td className="px-4 py-8 text-center text-slate-500" colSpan={4}>Loading users</td></tr> : sortedUsers.length === 0 ? <tr><td className="px-4 py-8 text-center text-slate-500" colSpan={4}>No users found</td></tr> : sortedUsers.map((user) => (
                  <tr key={user.memberId}>
                    <td className="px-4 py-3"><div className="flex items-center gap-3"><span className="flex size-9 items-center justify-center rounded-lg bg-indigo-50 text-xs font-bold text-indigo-500">{user.name.slice(0, 2).toUpperCase()}</span><span className="min-w-0"><span className="block font-semibold text-slate-950">{user.name}</span><span className="block truncate text-xs text-slate-500">@{user.username} / {user.email}</span></span></div></td>
                    <td className="px-4 py-3"><span className={`rounded-full px-2.5 py-1 text-xs font-semibold ${roleClass[user.role]}`}>{user.role}</span></td>
                    <td className="px-4 py-3"><div className="flex flex-wrap gap-2"><span className={`inline-flex items-center gap-1 rounded-full px-2.5 py-1 text-xs font-semibold ${user.verified ? 'bg-emerald-50 text-emerald-700' : 'bg-amber-50 text-amber-700'}`}><FiUsers aria-hidden="true" />{user.verified ? 'Verified' : 'Pending'}</span><span className={`inline-flex items-center gap-1 rounded-full px-2.5 py-1 text-xs font-semibold ${user.twoFactorEnabled ? 'bg-emerald-50 text-emerald-700' : 'bg-slate-100 text-slate-600'}`}><FiShield aria-hidden="true" />2FA {user.twoFactorEnabled ? 'On' : 'Off'}</span></div></td>
                    <td className="px-4 py-3 text-slate-500">{new Date(user.joinedAt).toLocaleDateString()}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      </div>
    </section>
  )
}
