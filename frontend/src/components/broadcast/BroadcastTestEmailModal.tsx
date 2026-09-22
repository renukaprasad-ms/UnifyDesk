import { useState, type FormEvent } from 'react'
import { FiCheckCircle, FiMail, FiSend, FiX } from 'react-icons/fi'
import { broadcastApi } from '../../features/broadcast/broadcastApi'
import type { BroadcastSender } from '../../features/broadcast/types'
import { apiErrorMessage } from '../../lib/api/apiClient'
import { useSnackbar } from '../feedback/SnackbarProvider'

type Props = {
  companyId: string
  sender: BroadcastSender
  onClose: () => void
  onDone: (recipient: string) => void
}

export default function BroadcastTestEmailModal({
  companyId,
  sender,
  onClose,
  onDone,
}: Props) {
  const snackbar = useSnackbar()
  const [recipient, setRecipient] = useState('')
  const [subject, setSubject] = useState('Messaging test email')
  const [bodyText, setBodyText] = useState(
    'This is a test email from Messaging. Your Broadcast provider connection is working.',
  )
  const [busy, setBusy] = useState(false)
  const [acceptedRecipient, setAcceptedRecipient] = useState<string | null>(null)

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    setBusy(true)
    try {
      const result = await broadcastApi.sendTestEmail(companyId, sender.id, {
        recipient: recipient.trim(),
        subject: subject.trim(),
        bodyText: bodyText.trim(),
      })
      setAcceptedRecipient(result.recipient)
    } catch (requestError) {
      snackbar.error(apiErrorMessage(requestError, 'Unable to send test email'))
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="mobile-modal-shell fixed inset-0 z-50 flex items-center justify-center bg-slate-950/40 p-4">
      <div className="mobile-sheet w-full max-w-xl rounded-2xl bg-white p-5 shadow-xl">
        <div className="flex items-start justify-between gap-4">
          <div className="flex items-start gap-3">
            <span className="flex size-10 shrink-0 items-center justify-center rounded-xl bg-emerald-50 text-emerald-600">
              <FiMail aria-hidden="true" />
            </span>
            <div>
              <p className="text-base font-bold text-slate-950">
                {acceptedRecipient ? 'Test email accepted' : 'Send a test email'}
              </p>
              <p className="mt-1 text-xs leading-5 text-slate-500">
                {sender.displayName
                  ? `${sender.displayName} <${sender.fromAddress}>`
                  : sender.fromAddress}
              </p>
            </div>
          </div>
          <button type="button" className="admin-back-button" onClick={onClose} aria-label="Close">
            <FiX aria-hidden="true" />
          </button>
        </div>

        {acceptedRecipient ? (
          <div className="mt-6">
            <div className="rounded-2xl border border-emerald-200 bg-emerald-50 p-5">
              <FiCheckCircle className="text-emerald-600" size={24} aria-hidden="true" />
              <p className="mt-3 text-sm font-bold text-emerald-900">
                The provider accepted the test message
              </p>
              <p className="mt-1 text-xs leading-5 text-emerald-700">
                Sent to {acceptedRecipient}. Provider acceptance confirms the connection can submit
                email; final inbox delivery still depends on the recipient provider and spam rules.
              </p>
            </div>
            <div className="mt-5 flex justify-end">
              <button
                type="button"
                className="primary-button"
                onClick={() => onDone(acceptedRecipient)}
              >
                Finish setup
              </button>
            </div>
          </div>
        ) : (
          <form className="mt-5" onSubmit={submit}>

            <label className="block text-xs font-semibold text-slate-700">
              Send test to
              <input
                type="email"
                className="form-input mt-2"
                value={recipient}
                onChange={(event) => setRecipient(event.target.value)}
                placeholder="you@example.com"
                required
              />
            </label>

            <label className="mt-4 block text-xs font-semibold text-slate-700">
              Subject
              <input
                className="form-input mt-2"
                value={subject}
                onChange={(event) => setSubject(event.target.value)}
                maxLength={998}
              />
            </label>

            <label className="mt-4 block text-xs font-semibold text-slate-700">
              Message
              <textarea
                className="form-input mt-2 min-h-28 resize-y"
                value={bodyText}
                onChange={(event) => setBodyText(event.target.value)}
                maxLength={20000}
              />
            </label>

            <div className="mt-4 rounded-xl bg-slate-50 p-3 text-[11px] leading-5 text-slate-500">
              This step is recommended, not automatic. Messaging sends only when you click
              <strong> Send test</strong>.
            </div>

            <div className="mt-6 flex flex-wrap justify-end gap-2">
              <button type="button" className="secondary-button" onClick={onClose} disabled={busy}>
                Skip for now
              </button>
              <button type="submit" className="primary-button inline-flex items-center gap-2" disabled={busy}>
                <FiSend aria-hidden="true" />
                {busy ? 'Sending…' : 'Send test'}
              </button>
            </div>
          </form>
        )}
      </div>
    </div>
  )
}
