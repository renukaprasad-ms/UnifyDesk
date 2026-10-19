import { useSnackbarState } from '../../components/feedback/useSnackbarState'
import { useCallback, useEffect, useState } from 'react'
import { FiCheck, FiCornerDownRight, FiMail, FiRefreshCw, FiX } from 'react-icons/fi'
import { CardSkeleton } from '../../components/ui/Skeleton'
import { platformApi } from '../../features/platform/platformApi'
import type { ManagedEmailDomainReviewItem } from '../../features/platform/types'

export default function PlatformManagedEmailReviews() {
  const [items, setItems] = useState<ManagedEmailDomainReviewItem[]>([])
  const [chosen, setChosen] = useState<ManagedEmailDomainReviewItem | null>(null)
  const [modalMode, setModalMode] = useState<'approve' | 'alternative' | 'reject' | null>(null)
  const [alternativeSubdomain, setAlternativeSubdomain] = useState('')
  const [rejectionReason, setRejectionReason] = useState('')
  const [busy, setBusy] = useState(false)
  const [loading, setLoading] = useState(true)
  const [, setError] = useSnackbarState('', 'error')
  const [, setNotice] = useSnackbarState('', 'success')

  const refresh = useCallback(async () => {
    setLoading(true)
    setError('')
    try {
      const data = await platformApi.managedEmailReviewQueue()
      setItems(data)
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Unable to fetch email review queue')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void refresh()
  }, [refresh])

  function openAction(item: ManagedEmailDomainReviewItem, mode: 'approve' | 'alternative' | 'reject') {
    setChosen(item)
    setModalMode(mode)
    setAlternativeSubdomain(item.requestedSubdomain || '')
    setRejectionReason('')
    setError('')
    setNotice('')
  }

  function closeModal() {
    setChosen(null)
    setModalMode(null)
    setAlternativeSubdomain('')
    setRejectionReason('')
  }

  async function handleDecision() {
    if (!chosen || !modalMode) return

    setBusy(true)
    setError('')
    setNotice('')

    try {
      if (modalMode === 'approve') {
        await platformApi.decideManagedEmailDomain(chosen.domainId, {
          action: 'APPROVE',
        })
        setNotice(`Subdomain "${chosen.requestedSubdomain}" approved for ${chosen.companyName}!`)
      } else if (modalMode === 'alternative') {
        const clean = alternativeSubdomain.trim().toLowerCase()
        if (!clean) {
          setError('Alternative subdomain is required')
          setBusy(false)
          return
        }
        await platformApi.decideManagedEmailDomain(chosen.domainId, {
          action: 'ASSIGN_ALTERNATIVE',
          alternativeSubdomain: clean,
        })
        setNotice(`Alternative subdomain "${clean}" assigned and approved for ${chosen.companyName}!`)
      } else if (modalMode === 'reject') {
        const reason = rejectionReason.trim()
        if (!reason) {
          setError('Rejection reason is required')
          setBusy(false)
          return
        }
        await platformApi.decideManagedEmailDomain(chosen.domainId, {
          action: 'REJECT',
          reason,
        })
        setNotice(`Subdomain request rejected for ${chosen.companyName}.`)
      }

      closeModal()
      await refresh()
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Failed to apply decision')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="space-y-6 p-6">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div>
          <h1 className="text-xl font-bold text-slate-900">Managed Email Subdomain Review Queue</h1>
          <p className="text-xs text-slate-500">
            Review and approve customer-requested UnifyDesk subdomains before automated DNS and SES provisioning.
          </p>
        </div>
        <button
          type="button"
          onClick={() => void refresh()}
          disabled={loading}
          className="inline-flex items-center gap-2 rounded-xl border border-slate-200 bg-white px-3 py-1.5 text-xs font-semibold text-slate-700 hover:bg-slate-50"
        >
          <FiRefreshCw className={loading ? 'animate-spin' : ''} />
          Refresh
        </button>
      </div>





      {loading ? (
        <CardSkeleton rows={4} />
      ) : items.length === 0 ? (
        <div className="rounded-2xl border border-dashed border-slate-200 bg-slate-50/50 p-12 text-center">
          <FiMail className="mx-auto size-8 text-slate-400" />
          <p className="mt-2 text-sm font-semibold text-slate-700">No pending domain requests</p>
          <p className="text-xs text-slate-500">All customer hosted domain requests have been reviewed.</p>
        </div>
      ) : (
        <div className="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm">
          <table className="min-w-full divide-y divide-slate-200 text-left text-xs">
            <thead className="bg-slate-50 text-[11px] font-bold uppercase tracking-wider text-slate-500">
              <tr>
                <th className="px-4 py-3">Company</th>
                <th className="px-4 py-3">Requested Subdomain</th>
                <th className="px-4 py-3">Target Domain</th>
                <th className="px-4 py-3">Requested By</th>
                <th className="px-4 py-3">Date</th>
                <th className="px-4 py-3 text-right">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100 font-medium text-slate-700">
              {items.map((item) => (
                <tr key={item.domainId} className="hover:bg-slate-50/80">
                  <td className="px-4 py-3">
                    <p className="font-bold text-slate-900">{item.companyName}</p>
                    <p className="text-[10px] text-slate-400">ID: {item.companyId}</p>
                  </td>
                  <td className="px-4 py-3">
                    <span className="rounded-md bg-indigo-50 px-2 py-0.5 font-mono font-bold text-indigo-700">
                      {item.requestedSubdomain}
                    </span>
                  </td>
                  <td className="px-4 py-3 font-mono text-slate-600">
                    {item.displayDomain}
                  </td>
                  <td className="px-4 py-3 text-slate-500">
                    {item.userEmail || `User #${item.requestedByUserId}`}
                  </td>
                  <td className="px-4 py-3 text-slate-500">
                    {new Date(item.requestedAt).toLocaleString()}
                  </td>
                  <td className="px-4 py-3 text-right">
                    <div className="flex items-center justify-end gap-2">
                      <button
                        type="button"
                        onClick={() => openAction(item, 'approve')}
                        className="inline-flex items-center gap-1 rounded-lg bg-emerald-600 px-2.5 py-1 text-xs font-semibold text-white shadow-sm hover:bg-emerald-700"
                      >
                        <FiCheck className="size-3.5" /> Approve
                      </button>
                      <button
                        type="button"
                        onClick={() => openAction(item, 'alternative')}
                        className="inline-flex items-center gap-1 rounded-lg border border-amber-300 bg-amber-50 px-2.5 py-1 text-xs font-semibold text-amber-800 hover:bg-amber-100"
                      >
                        <FiCornerDownRight className="size-3.5" /> Assign Alternative
                      </button>
                      <button
                        type="button"
                        onClick={() => openAction(item, 'reject')}
                        className="inline-flex items-center gap-1 rounded-lg border border-rose-200 bg-white px-2.5 py-1 text-xs font-semibold text-rose-700 hover:bg-rose-50"
                      >
                        <FiX className="size-3.5" /> Reject
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {modalMode && chosen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 p-4 backdrop-blur-sm">
          <div className="w-full max-w-md rounded-2xl bg-white p-6 shadow-xl">
            <h2 className="text-base font-bold text-slate-900">
              {modalMode === 'approve' && 'Approve Domain Request'}
              {modalMode === 'alternative' && 'Assign Alternative Subdomain'}
              {modalMode === 'reject' && 'Reject Domain Request'}
            </h2>
            <p className="mt-1 text-xs text-slate-500">
              Company: <span className="font-semibold text-slate-700">{chosen.companyName}</span> ({chosen.requestedSubdomain}.unifydesk.email)
            </p>

            {modalMode === 'approve' && (
              <p className="mt-4 text-xs leading-5 text-slate-600">
                Approving this request will immediately begin automated DNS configuration on Cloudflare and domain verification on AWS SES.
              </p>
            )}

            {modalMode === 'alternative' && (
              <div className="mt-4 space-y-2">
                <label className="block text-xs font-semibold text-slate-700">
                  Alternative Subdomain
                </label>
                <div className="flex items-center rounded-xl border border-slate-300 px-3">
                  <input
                    type="text"
                    value={alternativeSubdomain}
                    onChange={(e) => setAlternativeSubdomain(e.target.value.toLowerCase().trim())}
                    className="w-full border-0 py-2 text-xs font-mono focus:outline-none"
                    placeholder="e.g. acme-corp"
                    autoFocus
                  />
                  <span className="text-xs text-slate-400">.unifydesk.email</span>
                </div>
                <p className="text-[11px] text-slate-500">
                  The domain will be updated to this alternative and provisioned automatically.
                </p>
              </div>
            )}

            {modalMode === 'reject' && (
              <div className="mt-4 space-y-2">
                <label className="block text-xs font-semibold text-slate-700">
                  Rejection Reason
                </label>
                <textarea
                  rows={3}
                  value={rejectionReason}
                  onChange={(e) => setRejectionReason(e.target.value)}
                  placeholder="e.g. Subdomain conflicts with trademark or policy guidelines"
                  className="w-full rounded-xl border border-slate-300 p-2.5 text-xs focus:border-indigo-500 focus:outline-none"
                  autoFocus
                />
                <p className="text-[11px] text-slate-500">
                  This reason will be displayed to the customer in their domain settings.
                </p>
              </div>
            )}

            <div className="mt-6 flex justify-end gap-2">
              <button
                type="button"
                onClick={closeModal}
                disabled={busy}
                className="rounded-xl border border-slate-200 px-4 py-2 text-xs font-semibold text-slate-700 hover:bg-slate-50"
              >
                Cancel
              </button>
              <button
                type="button"
                onClick={() => void handleDecision()}
                disabled={busy}
                className={`rounded-xl px-4 py-2 text-xs font-semibold text-white shadow-sm ${
                  modalMode === 'reject'
                    ? 'bg-rose-600 hover:bg-rose-700'
                    : modalMode === 'alternative'
                      ? 'bg-amber-600 hover:bg-amber-700'
                      : 'bg-emerald-600 hover:bg-emerald-700'
                }`}
              >
                {busy ? 'Processing...' : modalMode === 'reject' ? 'Confirm Rejection' : modalMode === 'alternative' ? 'Assign & Approve' : 'Confirm Approval'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
