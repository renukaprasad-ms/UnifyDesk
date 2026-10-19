import { useState, type FormEvent } from 'react'
import { FiCheckCircle, FiMail, FiX } from 'react-icons/fi'
import { broadcastApi } from '../../features/broadcast/broadcastApi'
import type { BroadcastSender } from '../../features/broadcast/types'
import { apiErrorMessage } from '../../lib/api/apiClient'
import { useSnackbar } from '../feedback/SnackbarProvider'
import PasswordInput from '../ui/PasswordInput'

type Props = {
  companyId: string
  onClose: () => void
  onConnected: (sender: BroadcastSender) => void
}

export default function Msg91EmailConnectModal({ companyId, onClose, onConnected }: Props) {
  const snackbar = useSnackbar()
  const [name, setName] = useState('MSG91 Email')
  const [authKey, setAuthKey] = useState('')
  const [domain, setDomain] = useState('')
  const [fromAddress, setFromAddress] = useState('')
  const [displayName, setDisplayName] = useState('')
  const [replyToAddress, setReplyToAddress] = useState('')
  const [busy, setBusy] = useState(false)

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    setBusy(true)
    try {
      const sender = await broadcastApi.connectMsg91Email(companyId, {
        name: name.trim(),
        authKey: authKey.trim(),
        domain: domain.trim().toLowerCase(),
        fromAddress: fromAddress.trim(),
        displayName: displayName.trim() || null,
        replyToAddress: replyToAddress.trim() || null,
      })
      onConnected(sender)
    } catch (requestError) {
      snackbar.error(apiErrorMessage(requestError, 'Unable to connect MSG91 Email'))
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
            <span className="flex size-10 shrink-0 items-center justify-center rounded-xl bg-indigo-50 text-indigo-600">
              <FiMail aria-hidden="true" />
            </span>
            <div>
              <p className="text-base font-bold text-slate-950">Connect MSG91 Email</p>
              <p className="mt-1 text-xs leading-5 text-slate-500">
                Connect the MSG91 account and verified sending domain. Email templates are synced
                separately from the Templates module.
              </p>
            </div>
          </div>
          <button type="button" className="admin-back-button" onClick={onClose} aria-label="Close">
            <FiX aria-hidden="true" />
          </button>
        </div>

        <div className="mt-5 rounded-xl border border-indigo-100 bg-indigo-50/60 p-4">
          <p className="text-xs font-bold text-slate-900">Account-level connection</p>
          <p className="mt-1 text-xs leading-5 text-slate-600">
            Messaging verifies the Auth Key against the MSG91 Email templates API. Provider template
            IDs are not stored on this connection because every published Email template version
            receives its own MSG91 mapping.
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
            Verified sending domain
            <input
              className="form-input mt-2"
              value={domain}
              onChange={(event) => setDomain(event.target.value)}
              placeholder="mailer.example.com"
              maxLength={255}
              required
            />
          </label>
        </div>

        <label className="mt-4 block text-xs font-semibold text-slate-700">
          Auth key
          <PasswordInput
            className="mt-2"
            value={authKey}
            onChange={(event) => setAuthKey(event.target.value)}
            autoComplete="new-password"
            required
            showLeftIcon
          />
        </label>

        <div className="mt-4 grid gap-4 sm:grid-cols-2">
          <label className="block text-xs font-semibold text-slate-700">
            From email
            <input
              type="email"
              className="form-input mt-2"
              value={fromAddress}
              onChange={(event) => setFromAddress(event.target.value)}
              placeholder="notifications@mailer.example.com"
              required
            />
          </label>

          <label className="block text-xs font-semibold text-slate-700">
            Display name
            <input
              className="form-input mt-2"
              value={displayName}
              onChange={(event) => setDisplayName(event.target.value)}
              placeholder="Company"
            />
          </label>

          <label className="block text-xs font-semibold text-slate-700 sm:col-span-2">
            Reply-To email
            <input
              type="email"
              className="form-input mt-2"
              value={replyToAddress}
              onChange={(event) => setReplyToAddress(event.target.value)}
              placeholder="support@example.com"
            />
          </label>
        </div>

        <div className="mt-4 flex gap-2 rounded-xl border border-amber-200 bg-amber-50 px-3 py-2 text-[11px] leading-4 text-amber-800">
          <FiCheckCircle className="mt-0.5 shrink-0" aria-hidden="true" />
          <span>
            MSG91 Email is intended for transactional email. Marketing templates remain available
            through direct-HTML providers such as SES, SendGrid and Azure Email.
          </span>
        </div>

        <div className="mt-6 flex flex-wrap justify-end gap-2">
          <button type="button" className="secondary-button" onClick={onClose} disabled={busy}>
            Cancel
          </button>
          <button type="submit" className="primary-button" disabled={busy}>
            {busy ? 'Validating…' : 'Connect MSG91 Email'}
          </button>
        </div>
      </form>
    </div>
  )
}
