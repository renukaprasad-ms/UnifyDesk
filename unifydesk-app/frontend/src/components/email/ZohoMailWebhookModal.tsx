import { useEffect, useState } from 'react'
import { FiCheckCircle, FiCopy, FiExternalLink, FiRefreshCw, FiX } from 'react-icons/fi'
import { zohoMailApi } from '../../features/channel/zohoMailApi'
import { apiErrorMessage } from '../../lib/api/apiClient'
import { useSnackbar } from '../feedback/SnackbarProvider'

type Status = Awaited<ReturnType<typeof zohoMailApi.webhookStatus>>

export default function ZohoMailWebhookModal({
  companyId,
  connectionId,
  mailbox,
  onClose,
}: {
  companyId: string
  connectionId: string
  mailbox: string
  onClose: () => void
}) {
  const snackbar = useSnackbar()
  const [status, setStatus] = useState<Status | null>(null)
  const [webhookUrl, setWebhookUrl] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const [loading, setLoading] = useState(true)
  const [confirmAction, setConfirmAction] = useState<'rotate' | 'disable' | null>(null)

  const refreshStatus = async () => {
    try {
      setStatus(await zohoMailApi.webhookStatus(companyId, connectionId))
    } catch (error) {
      snackbar.error(apiErrorMessage(error, 'Unable to load Zoho webhook status'))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    let active = true
    zohoMailApi.webhookStatus(companyId, connectionId)
      .then((value) => {
        if (active) setStatus(value)
      })
      .catch((error) => {
        if (active) snackbar.error(apiErrorMessage(error, 'Unable to load webhook status'))
      })
      .finally(() => {
        if (active) setLoading(false)
      })
    return () => { active = false }
  }, [companyId, connectionId, snackbar])

  const generate = async () => {
    setBusy(true)
    try {
      const created = await zohoMailApi.setupWebhook(companyId, connectionId)
      setWebhookUrl(created.webhookUrl)
      setConfirmAction(null)
      await refreshStatus()
    } catch (error) {
      snackbar.error(apiErrorMessage(error, 'Unable to generate Zoho webhook URL'))
    } finally {
      setBusy(false)
    }
  }

  const disable = async () => {
    setBusy(true)
    try {
      await zohoMailApi.disableWebhook(companyId, connectionId)
      setWebhookUrl(null)
      setConfirmAction(null)
      await refreshStatus()
      snackbar.success('Zoho webhook disabled. Safety polling is still active.')
    } catch (error) {
      snackbar.error(apiErrorMessage(error, 'Unable to disable Zoho webhook'))
    } finally {
      setBusy(false)
    }
  }

  const copyUrl = async () => {
    if (!webhookUrl) return
    try {
      await navigator.clipboard.writeText(webhookUrl)
      snackbar.success('Webhook URL copied')
    } catch {
      snackbar.error('Unable to copy automatically. Select and copy the URL instead.')
    }
  }

  const active = status?.status === 'ACTIVE'
  const exists = status?.status === 'ACTIVE' || status?.status === 'AWAITING_HANDSHAKE'
  return (
    <div className="mobile-modal-shell fixed inset-0 z-[90] flex items-center justify-center bg-slate-950/50 p-4">
      <div
        className="w-full max-w-xl rounded-2xl border p-5 shadow-2xl sm:p-6"
        style={{ background: 'var(--theme-surface)', color: 'var(--theme-text)', borderColor: 'var(--theme-border)' }}
        role="dialog"
        aria-modal="true"
        aria-labelledby="zoho-webhook-heading"
      >
        <div className="flex items-start justify-between gap-4">
          <div>
            <p className="eyebrow">ZOHO MAIL</p>
            <h2 id="zoho-webhook-heading" className="mt-1 text-lg font-bold">Near-realtime inbox updates</h2>
            <p className="mt-1 text-sm opacity-70">{mailbox}</p>
          </div>
          <button type="button" className="secondary-button" aria-label="Close" onClick={onClose}>
            <FiX aria-hidden="true" />
          </button>
        </div>
        <div className="mt-4 rounded-xl border p-3 text-sm" style={{ borderColor: 'var(--theme-border)' }}>
          {loading ? 'Checking webhook status…' : (
            <>
              <div className="flex items-center gap-2 font-semibold">
                {active && <FiCheckCircle className="text-emerald-600" aria-hidden="true" />}
                {active ? 'Webhook receiving verified events'
                  : exists ? 'Waiting for first Zoho webhook request'
                    : status?.configured ? 'Webhook not configured' : 'Webhook feature disabled on backend'}
              </div>
              <p className="mt-1 text-xs opacity-70">
                New mail triggers a server-side sync. The existing 2-minute safety poll remains enabled.
              </p>
              {status?.lastReceivedAt && (
                <p className="mt-2 text-xs opacity-70">
                  Last notification: {new Date(status.lastReceivedAt).toLocaleString()}
                </p>
              )}
            </>
          )}
        </div>

        <div className="mt-4 space-y-3 text-sm">
          <p><strong>1.</strong> Generate a private callback URL for this mailbox.</p>
          <p><strong>2.</strong> In Zoho Mail, open Settings → Integrations → Developer Space → Outgoing Webhooks → Add configuration.</p>
          <p><strong>3.</strong> Set entity to <strong>Mail</strong>, choose incoming-mail conditions, and enable <strong>Limited Data List</strong>.</p>
          <p><strong>4.</strong> Paste the URL and Save. Zoho sends a signed test request; then return here and select <strong>Refresh status</strong>.</p>
        </div>

        {webhookUrl && (
          <div className="mt-4 rounded-xl border p-3" style={{ borderColor: 'var(--theme-border)' }}>
            <label htmlFor="zoho-webhook-url" className="text-xs font-semibold">Private webhook URL — copy now</label>
            <input
              id="zoho-webhook-url"
              type="text"
              readOnly
              value={webhookUrl}
              onFocus={(event) => event.target.select()}
              className="mt-2 w-full rounded-lg border px-3 py-2 text-xs"
              style={{ background: 'var(--theme-background)', color: 'var(--theme-text)', borderColor: 'var(--theme-border)' }}
            />
            <p className="mt-2 text-xs opacity-70">
              Treat this URL as a password. For security it cannot be retrieved after you close this dialog.
            </p>
            <button type="button" className="secondary-button mt-3 gap-2" onClick={() => void copyUrl()}>
              <FiCopy aria-hidden="true" /> Copy URL
            </button>
          </div>
        )}

        {confirmAction && (
          <div className="mt-4 rounded-xl border border-amber-400 p-3 text-sm">
            <p className="font-semibold">
              {confirmAction === 'rotate' ? 'Replace existing webhook URL?' : 'Disable this webhook?'}
            </p>
            <p className="mt-1 opacity-75">
              {confirmAction === 'rotate'
                ? 'The existing URL will stop working. You must update the outgoing webhook in Zoho Mail.'
                : 'Push notifications will stop, but automatic safety polling will continue.'}
            </p>
            <div className="mt-3 flex gap-2">
              <button type="button" className="primary-button" disabled={busy}
                onClick={() => void (confirmAction === 'rotate' ? generate() : disable())}>
                Yes, {confirmAction === 'rotate' ? 'replace URL' : 'disable'}
              </button>
              <button type="button" className="secondary-button" disabled={busy} onClick={() => setConfirmAction(null)}>
                Cancel
              </button>
            </div>
          </div>
        )}

        <div className="mt-6 flex flex-wrap items-center justify-between gap-2">
          <a className="secondary-button gap-2"
            href="https://www.zoho.com/mail/help/dev-platform/webhook.html"
            target="_blank" rel="noreferrer">
            Zoho setup guide <FiExternalLink aria-hidden="true" />
          </a>
          <div className="flex flex-wrap gap-2">
            {exists && (
              <button className="secondary-button text-rose-600" type="button"
                disabled={busy} onClick={() => setConfirmAction('disable')}>Disable</button>
            )}
            <button className="secondary-button gap-2" type="button"
              disabled={busy} onClick={() => void refreshStatus()}>
              <FiRefreshCw aria-hidden="true" /> Refresh status
            </button>
            <button className="primary-button" type="button"
              disabled={busy || loading || status?.configured === false}
              onClick={() => void (exists ? setConfirmAction('rotate') : generate())}>
              {busy ? 'Working…' : exists ? 'Regenerate URL' : 'Generate URL'}
            </button>
          </div>
        </div>
      </div>
    </div>
  )
}
