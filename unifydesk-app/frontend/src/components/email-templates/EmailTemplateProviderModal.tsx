import { useCallback, useEffect, useMemo, useState } from 'react'
import { FiRefreshCw, FiServer, FiUploadCloud, FiX } from 'react-icons/fi'
import { broadcastApi } from '../../features/broadcast/broadcastApi'
import type { BroadcastSender } from '../../features/broadcast/types'
import { emailTemplateApi } from '../../features/emailTemplate/emailTemplateApi'
import type {
  EmailTemplateProviderBinding,
  EmailTemplateSummary,
} from '../../features/emailTemplate/types'
import { apiErrorMessage } from '../../lib/api/apiClient'
import { useSnackbar } from '../feedback/SnackbarProvider'
import { ListSkeleton } from '../ui/Skeleton'

type Props = {
  companyId: string
  template: EmailTemplateSummary
  canModify: boolean
  onClose: () => void
}

const statusClass: Record<string, string> = {
  READY: 'bg-emerald-50 text-emerald-700',
  SYNCED: 'bg-blue-50 text-blue-700',
  PENDING_REVIEW: 'bg-amber-50 text-amber-700',
  SYNCING: 'bg-blue-50 text-blue-700',
  REJECTED: 'bg-rose-50 text-rose-700',
  FAILED: 'bg-rose-50 text-rose-700',
}

const statusLabel = (value?: string | null) =>
  value ? value.toLowerCase().replaceAll('_', ' ') : 'not synced'

export default function EmailTemplateProviderModal({
  companyId,
  template,
  canModify,
  onClose,
}: Props) {
  const snackbar = useSnackbar()
  const [senders, setSenders] = useState<BroadcastSender[]>([])
  const [bindings, setBindings] = useState<EmailTemplateProviderBinding[]>([])
  const [loading, setLoading] = useState(true)
  const [workingId, setWorkingId] = useState<string | null>(null)
  const publishedPurpose = template.publishedPurpose ?? template.purpose

  const load = useCallback(async () => {
    setLoading(true)
    try {
      const [allSenders, allBindings] = await Promise.all([
        broadcastApi.senders(companyId),
        emailTemplateApi.providerBindings(companyId, { templateId: template.id }),
      ])
      setSenders(
        allSenders.filter(
          (sender) =>
            sender.provider === 'MSG91_EMAIL' &&
            sender.status === 'ACTIVE' &&
            sender.sendingEnabled,
        ),
      )
      let nextBindings = allBindings

      if (
        canModify &&
        template.publishedVersion !== null &&
        template.publishedVersion !== undefined
      ) {
        const refreshable = allBindings.filter(
          (binding) =>
            binding.templateVersion === template.publishedVersion &&
            (binding.status === 'SYNCED' || binding.status === 'PENDING_REVIEW'),
        )
        if (refreshable.length > 0) {
          const refreshed = await Promise.allSettled(
            refreshable.map((binding) =>
              emailTemplateApi.refreshProviderBinding(companyId, binding.id),
            ),
          )
          const updates = new Map(
            refreshed
              .filter(
                (result): result is PromiseFulfilledResult<EmailTemplateProviderBinding> =>
                  result.status === 'fulfilled',
              )
              .map((result) => [result.value.id, result.value]),
          )
          nextBindings = allBindings.map((binding) => updates.get(binding.id) ?? binding)
        }
      }

      setBindings(nextBindings)
    } catch (error) {
      snackbar.error(apiErrorMessage(error, 'Unable to load provider status'))
    } finally {
      setLoading(false)
    }
  }, [canModify, companyId, snackbar, template.id, template.publishedVersion])

  useEffect(() => {
    void load()
  }, [load])

  const currentBindings = useMemo(() => {
    const byConnection = new Map<string, EmailTemplateProviderBinding>()
    bindings.forEach((binding) => {
      if (binding.templateVersion !== template.publishedVersion) return
      byConnection.set(binding.connectionId, binding)
    })
    return byConnection
  }, [bindings, template.publishedVersion])

  const sync = async (connectionId: string) => {
    setWorkingId(connectionId)
    try {
      const binding = await emailTemplateApi.syncMsg91(companyId, template.id, connectionId)
      setBindings((current) => [binding, ...current.filter((item) => item.id !== binding.id)])
      snackbar.success('Template synced to MSG91.')
    } catch (error) {
      snackbar.error(apiErrorMessage(error, 'Unable to sync template to MSG91'))
      await load()
    } finally {
      setWorkingId(null)
    }
  }

  const refresh = async (binding: EmailTemplateProviderBinding) => {
    setWorkingId(binding.connectionId)
    try {
      const updated = await emailTemplateApi.refreshProviderBinding(companyId, binding.id)
      setBindings((current) => current.map((item) => (item.id === updated.id ? updated : item)))
      snackbar.success('MSG91 template status refreshed.')
    } catch (error) {
      snackbar.error(apiErrorMessage(error, 'Unable to refresh MSG91 status'))
    } finally {
      setWorkingId(null)
    }
  }

  return (
    <div className="mobile-modal-shell fixed inset-0 z-50 flex items-center justify-center bg-slate-950/45 p-4">
      <div className="mobile-sheet max-h-[92vh] w-full max-w-2xl overflow-y-auto rounded-2xl bg-white p-5 shadow-xl">
        <div className="flex items-start justify-between gap-4">
          <div>
            <p className="text-base font-bold text-slate-950">Provider availability</p>
            <p className="mt-1 text-xs leading-5 text-slate-500">
              {template.name} · published v{template.publishedVersion ?? '—'}
            </p>
          </div>
          <button type="button" className="admin-back-button" onClick={onClose} aria-label="Close">
            <FiX />
          </button>
        </div>

        {publishedPurpose !== 'TRANSACTIONAL' && (
          <div className="mt-5 rounded-xl border border-amber-200 bg-amber-50 p-4">
            <p className="text-xs font-bold text-amber-900">MSG91 Email is transactional-only</p>
            <p className="mt-1 text-xs leading-5 text-amber-800">
              The published version is marked Marketing, so Messaging will not sync or send it
              through MSG91. Change the purpose only when the message is genuinely transactional.
            </p>
          </div>
        )}

        <div className="mt-5 rounded-xl border border-slate-200 bg-slate-50 p-4">
          <p className="text-xs font-bold text-slate-900">Direct HTML providers</p>
          <p className="mt-1 text-xs leading-5 text-slate-500">
            Amazon SES, SendGrid and Azure Email use the published HTML directly and do not need a
            provider-side template sync.
          </p>
        </div>

        <div className="mt-5">
          <div className="flex items-center gap-2">
            <FiServer className="text-indigo-500" />
            <p className="text-xs font-bold uppercase tracking-[0.08em] text-slate-500">
              MSG91 Email connections
            </p>
          </div>

          {loading ? (
            <div className="py-4"><ListSkeleton rows={3} /></div>
          ) : senders.length === 0 ? (
            <div className="mt-3 rounded-xl border border-dashed border-slate-300 p-5 text-center">
              <p className="text-xs font-semibold text-slate-700">
                No active MSG91 Email connection
              </p>
              <p className="mt-1 text-[11px] text-slate-500">
                Connect MSG91 Email from Channel Connections first.
              </p>
            </div>
          ) : (
            <div className="mt-3 grid gap-3">
              {senders.map((sender) => {
                const binding = currentBindings.get(sender.connectionId)
                const busy = workingId === sender.connectionId
                return (
                  <div key={sender.id} className="rounded-xl border border-slate-200 bg-white p-4">
                    <div className="flex flex-wrap items-start justify-between gap-3">
                      <div>
                        <p className="text-sm font-bold text-slate-900">{sender.connectionName}</p>
                        <p className="mt-1 text-xs text-slate-500">
                          {sender.displayName
                            ? `${sender.displayName} <${sender.fromAddress}>`
                            : sender.fromAddress}
                        </p>
                      </div>
                      <span
                        className={`rounded-full px-2.5 py-1 text-[10px] font-bold ${
                          binding
                            ? (statusClass[binding.status] ?? 'bg-slate-100 text-slate-600')
                            : 'bg-slate-100 text-slate-600'
                        }`}
                      >
                        {binding?.status === 'SYNCED'
                          ? 'synced · readiness unconfirmed'
                          : statusLabel(binding?.status)}
                      </span>
                    </div>

                    {binding?.providerTemplateId && (
                      <p className="mt-3 break-all text-[10px] text-slate-400">
                        Provider template: {binding.providerTemplateId}
                      </p>
                    )}
                    {binding?.providerStatus && (
                      <p className="mt-1 text-[10px] text-slate-400">
                        MSG91 status: {binding.providerStatus}
                      </p>
                    )}
                    {binding?.errorMessage && (
                      <p className="mt-3 rounded-lg bg-rose-50 px-3 py-2 text-[11px] leading-5 text-rose-700">
                        {binding.errorMessage}
                      </p>
                    )}

                    <div className="mt-4 flex flex-wrap justify-end gap-2">
                      {binding && canModify && (
                        <button
                          type="button"
                          className="secondary-button inline-flex items-center gap-2"
                          disabled={busy}
                          onClick={() => void refresh(binding)}
                        >
                          <FiRefreshCw /> {busy ? 'Checking…' : 'Refresh status'}
                        </button>
                      )}
                      {canModify &&
                        (!binding ||
                          (binding.status === 'FAILED' && !binding.providerTemplateId)) && (
                          <button
                            type="button"
                            className="primary-button inline-flex items-center gap-2"
                            disabled={busy || publishedPurpose !== 'TRANSACTIONAL'}
                            onClick={() => void sync(sender.connectionId)}
                          >
                            <FiUploadCloud />
                            {binding ? 'Retry sync' : 'Sync to MSG91'}
                          </button>
                        )}
                    </div>
                  </div>
                )
              })}
            </div>
          )}
        </div>
      </div>
    </div>
  )
}
