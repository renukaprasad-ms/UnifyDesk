import { useState, type FormEvent } from 'react'
import { FiCheckCircle, FiMail, FiX } from 'react-icons/fi'
import { broadcastApi } from '../../features/broadcast/broadcastApi'
import type { BroadcastSender, SendGridRegion } from '../../features/broadcast/types'
import { apiErrorMessage } from '../../lib/api/apiClient'
import { useSnackbar } from '../feedback/SnackbarProvider'
import PasswordInput from '../ui/PasswordInput'

type Props = {
  companyId: string
  onClose: () => void
  onConnected: (sender: BroadcastSender) => void
}

export default function SendGridConnectModal({ companyId, onClose, onConnected }: Props) {
  const snackbar = useSnackbar()
  const [name, setName] = useState('SendGrid')
  const [region, setRegion] = useState<SendGridRegion>('GLOBAL')
  const [apiKey, setApiKey] = useState('')
  const [fromAddress, setFromAddress] = useState('')
  const [displayName, setDisplayName] = useState('')
  const [replyToAddress, setReplyToAddress] = useState('')
  const [busy, setBusy] = useState(false)

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    setBusy(true)
    try {
      const sender = await broadcastApi.connectSendGrid(companyId, {
        name: name.trim(),
        region,
        apiKey: apiKey.trim(),
        fromAddress: fromAddress.trim(),
        displayName: displayName.trim() || null,
        replyToAddress: replyToAddress.trim() || null,
      })
      onConnected(sender)
    } catch (requestError) {
      snackbar.error(apiErrorMessage(requestError, 'Unable to connect SendGrid'))
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="mobile-modal-shell fixed inset-0 z-50 flex items-center justify-center bg-slate-950/40 p-4">
      <form
        className="mobile-sheet max-h-[92vh] w-full max-w-2xl overflow-y-auto rounded-2xl bg-white p-5 shadow-xl"
        onSubmit={submit}
      >
        <div className="flex items-start justify-between gap-4">
          <div className="flex min-w-0 items-start gap-3">
            <span className="flex size-10 shrink-0 items-center justify-center rounded-xl bg-sky-50 text-sky-600">
              <FiMail aria-hidden="true" />
            </span>
            <div>
              <p className="text-base font-bold text-slate-950">Connect SendGrid</p>
              <p className="mt-1 text-xs leading-5 text-slate-500">
                Bring your own SendGrid account for high-volume email broadcasts.
              </p>
            </div>
          </div>
          <button type="button" className="admin-back-button" onClick={onClose} aria-label="Close">
            <FiX aria-hidden="true" />
          </button>
        </div>

        <div className="mt-5 rounded-xl border border-sky-100 bg-sky-50/60 p-4">
          <p className="text-xs font-bold text-slate-900">Required SendGrid API key permissions</p>
          <p className="mt-1 text-xs leading-5 text-slate-600">
            Enable Mail Send and read access to Sender Authentication. Messaging validates the
            selected From address or its authenticated domain before saving the connection.
          </p>
        </div>

        <div className="mt-5 grid gap-4 sm:grid-cols-2">
          <label className="block text-xs font-semibold text-slate-700">
            Connection name
            <input
              className="form-input mt-2"
              value={name}
              onChange={(event) => setName(event.target.value)}
              maxLength={160}
              required
            />
          </label>

          <label className="block text-xs font-semibold text-slate-700">
            SendGrid region
            <select
              className="form-input mt-2"
              value={region}
              onChange={(event) => setRegion(event.target.value as SendGridRegion)}
            >
              <option value="GLOBAL">Global</option>
              <option value="EU">EU regional subuser</option>
            </select>
          </label>
        </div>

        <label className="mt-4 block text-xs font-semibold text-slate-700">
          API key
          <PasswordInput
            className="mt-2"
            value={apiKey}
            onChange={(event) => setApiKey(event.target.value)}
            autoComplete="new-password"
            placeholder="SG..."
            required
            showLeftIcon
          />
        </label>

        <div className="mt-5 grid gap-4 sm:grid-cols-2">
          <label className="block text-xs font-semibold text-slate-700">
            Verified From email
            <input
              type="email"
              className="form-input mt-2"
              value={fromAddress}
              onChange={(event) => setFromAddress(event.target.value)}
              placeholder="marketing@company.com"
              required
            />
          </label>

          <label className="block text-xs font-semibold text-slate-700">
            Display name
            <input
              className="form-input mt-2"
              value={displayName}
              onChange={(event) => setDisplayName(event.target.value)}
              placeholder="Company Marketing"
            />
          </label>

          <label className="block text-xs font-semibold text-slate-700 sm:col-span-2">
            Reply-To email
            <input
              type="email"
              className="form-input mt-2"
              value={replyToAddress}
              onChange={(event) => setReplyToAddress(event.target.value)}
              placeholder="support@company.com (optional)"
            />
          </label>
        </div>

        <div className="mt-4 flex gap-2 rounded-xl border border-amber-200 bg-amber-50 px-3 py-2 text-[11px] leading-4 text-amber-800">
          <FiCheckCircle className="mt-0.5 shrink-0" aria-hidden="true" />
          <span>
            The API key is encrypted before storage and is never returned by Messaging APIs.
          </span>
        </div>

        <div className="mt-6 flex flex-wrap justify-end gap-2">
          <button type="button" className="secondary-button" onClick={onClose} disabled={busy}>
            Cancel
          </button>
          <button type="submit" className="primary-button" disabled={busy}>
            {busy ? 'Connecting…' : 'Connect SendGrid'}
          </button>
        </div>
      </form>
    </div>
  )
}
