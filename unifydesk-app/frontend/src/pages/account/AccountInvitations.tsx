import { useEffect, useMemo, useState } from 'react'
import { FiCheckCircle, FiClock, FiMail, FiRefreshCw } from 'react-icons/fi'
import { useNavigate } from 'react-router'
import { companyApi } from '../../features/company/companyApi'
import type { CompanyInvitation } from '../../features/company/types'
import { workspaceApi } from '../../features/workspace/workspaceApi'
import { apiErrorMessage } from '../../lib/api/apiClient'
import { useAppDispatch } from '../../store/hooks'
import { selectCompany, setCompanies } from '../../store/workspace/workspaceSlice'
import { ListSkeleton } from '../../components/ui/Skeleton'

const statusClass: Record<CompanyInvitation['status'], string> = {
  PENDING: 'bg-amber-50 text-amber-700',
  ACCEPTED: 'bg-emerald-50 text-emerald-700',
  REVOKED: 'bg-slate-100 text-slate-600',
  EXPIRED: 'bg-rose-50 text-rose-700',
}

export default function AccountInvitations() {
  const dispatch = useAppDispatch()
  const navigate = useNavigate()
  const [invitations, setInvitations] = useState<CompanyInvitation[]>([])
  const [loading, setLoading] = useState(true)
  const [workingId, setWorkingId] = useState<string | null>(null)
  const [error, setError] = useState('')

  const load = async () => {
    setLoading(true)
    setError('')
    try {
      setInvitations(await companyApi.myInvitations())
    } catch (loadError) {
      setError(apiErrorMessage(loadError, 'Unable to load your invitations'))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    void load()
  }, [])

  const actionable = useMemo(
    () => invitations.filter((invitation) => invitation.status === 'PENDING'),
    [invitations],
  )
  const history = useMemo(
    () => invitations.filter((invitation) => invitation.status !== 'PENDING'),
    [invitations],
  )

  const accept = async (invitation: CompanyInvitation) => {
    setWorkingId(invitation.invitationId)
    setError('')
    try {
      const accepted = await companyApi.acceptMyInvitation(invitation.invitationId)
      const companies = await workspaceApi.list()
      dispatch(setCompanies(companies))
      dispatch(selectCompany(accepted.companyId))
      navigate('/workspace', { replace: true })
    } catch (acceptError) {
      setError(apiErrorMessage(acceptError, 'Unable to accept invitation'))
      await load()
    } finally {
      setWorkingId(null)
    }
  }

  const renderInvitation = (invitation: CompanyInvitation, actionableItem: boolean) => (
    <div
      key={invitation.invitationId}
      className="flex flex-col gap-4 px-5 py-4 sm:flex-row sm:items-center sm:justify-between"
    >
      <div className="min-w-0">
        <div className="flex flex-wrap items-center gap-2">
          <p className="text-sm font-bold text-slate-950">{invitation.companyName}</p>
          <span
            className={`rounded-full px-2.5 py-1 text-[11px] font-semibold ${statusClass[invitation.status]}`}
          >
            {invitation.status}
          </span>
        </div>
        <p className="mt-1 text-xs text-slate-500">{invitation.email}</p>
        <div className="mt-2 flex flex-wrap gap-1.5">
          {invitation.roles.length === 0 ? (
            <span className="text-xs text-slate-500">Default member access</span>
          ) : (
            invitation.roles.map((role) => (
              <span
                key={role.roleId}
                className="rounded-full bg-violet-50 px-2.5 py-1 text-xs font-semibold text-violet-700"
              >
                {role.name}
              </span>
            ))
          )}
        </div>
        <p className="mt-2 inline-flex items-center gap-1.5 text-xs text-slate-500">
          <FiClock aria-hidden="true" />
          {invitation.status === 'EXPIRED' ? 'Expired' : 'Expires'}{' '}
          {new Date(invitation.expiresAt).toLocaleString()}
        </p>
      </div>
      {actionableItem && (
        <button
          type="button"
          className="primary-button inline-flex shrink-0 items-center gap-2"
          disabled={workingId === invitation.invitationId}
          onClick={() => void accept(invitation)}
        >
          <FiCheckCircle aria-hidden="true" />
          {workingId === invitation.invitationId ? 'Accepting…' : 'Accept invitation'}
        </button>
      )}
    </div>
  )

  return (
    <section className="px-4 py-8 sm:px-6 lg:px-8">
      <div className="mx-auto max-w-4xl">
        <header className="flex flex-wrap items-end justify-between gap-4">
          <div>
            <p className="eyebrow">Account</p>
            <h1 className="mt-2 text-3xl font-bold tracking-tight text-slate-950">Invitations</h1>
            <p className="mt-2 max-w-2xl text-sm leading-6 text-slate-500">
              Workspace invitations sent to your verified email appear here.
            </p>
          </div>
          <button
            type="button"
            className="secondary-button inline-flex items-center gap-2"
            onClick={() => void load()}
          >
            <FiRefreshCw aria-hidden="true" />
            Refresh
          </button>
        </header>

        {error && (
          <div className="mt-6 rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm font-medium text-red-700">
            {error}
          </div>
        )}

        <div className="panel mt-7 overflow-hidden">
          <div className="border-b border-slate-100 px-5 py-4">
            <h2 className="text-sm font-bold text-slate-950">Pending invitations</h2>
          </div>
          {loading ? (
            <div className="p-4"><ListSkeleton rows={5} /></div>
          ) : actionable.length === 0 ? (
            <div className="px-5 py-10 text-center">
              <FiMail className="mx-auto text-slate-400" size={24} />
              <p className="mt-3 text-sm font-semibold text-slate-800">No pending invitations</p>
              <p className="mt-1 text-xs text-slate-500">
                New workspace invitations for this account will appear here.
              </p>
            </div>
          ) : (
            <div className="divide-y divide-slate-100">
              {actionable.map((invitation) => renderInvitation(invitation, true))}
            </div>
          )}
        </div>

        {!loading && history.length > 0 && (
          <div className="panel mt-5 overflow-hidden">
            <div className="border-b border-slate-100 px-5 py-4">
              <h2 className="text-sm font-bold text-slate-950">Invitation history</h2>
            </div>
            <div className="divide-y divide-slate-100">
              {history.map((invitation) => renderInvitation(invitation, false))}
            </div>
          </div>
        )}
      </div>
    </section>
  )
}
