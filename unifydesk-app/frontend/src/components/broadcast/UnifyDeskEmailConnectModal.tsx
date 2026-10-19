import { useState, type FormEvent } from 'react'
import { FiKey, FiMail, FiX } from 'react-icons/fi'
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

export default function UnifyDeskEmailConnectModal({
  companyId,
  onClose,
  onConnected,
}: Props) {
  const snackbar = useSnackbar()
  const [name, setName] = useState('UnifyDesk Email')
  const [apiKey, setApiKey] = useState('')
  const [domainId, setDomainId] = useState('')
  const [fromAddress, setFromAddress] = useState('')
  const [displayName, setDisplayName] = useState('')
  const [replyToAddress, setReplyToAddress] = useState('')
  const [busy, setBusy] = useState(false)

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    setBusy(true)
    try {
      const sender = await broadcastApi.connectUnifyDeskEmail(companyId, {
        name: name.trim(),
        apiKey: apiKey.trim(),
        sendingDomainId: domainId.trim(),
        fromAddress: fromAddress.trim(),
        displayName: displayName.trim() || null,
        replyToAddress: replyToAddress.trim() || null,
      })
      setApiKey('')
      onConnected(sender)
    } catch (error) {
      snackbar.error(apiErrorMessage(error, 'Unable to connect UnifyDesk Email'))
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="mobile-modal-shell fixed inset-0 z-50 flex items-center justify-center bg-slate-950/50 p-4">
      <form
        className="mobile-sheet max-h-[92vh] w-full max-w-2xl overflow-y-auto rounded-2xl bg-white p-6 shadow-xl"
        onSubmit={submit}
      >
        <div className="flex items-start justify-between gap-4">
          <div className="flex items-center gap-3">
            <span className="flex size-11 items-center justify-center rounded-xl bg-blue-50 text-blue-600">
              <FiMail size={22} aria-hidden="true" />
            </span>
            <div>
              <h2 className="text-lg font-bold text-slate-950">Connect UnifyDesk Email</h2>
              <p className="mt-1 text-xs text-slate-500">
                Use an independent UnifyDesk Email project as your broadcast provider.
              </p>
            </div>
          </div>
          <button type="button" className="admin-back-button" onClick={onClose} aria-label="Close">
            <FiX />
          </button>
        </div>

        <div className="mt-5 rounded-xl border border-blue-100 bg-blue-50 p-4 text-xs leading-5 text-slate-700">
          <FiKey className="mr-2 inline text-blue-600" aria-hidden="true" />
          Create a project in UnifyDesk Email and verify its sender domain first.
          Generate a project API key with both <b>SEND</b> and <b>DOMAINS</b> permissions.
          Your workspace stores it encrypted; it will never appear in connection responses.
        </div>

        <div className="mt-5 grid gap-4 sm:grid-cols-2">
          <label className="block text-xs font-semibold text-slate-700">
            Connection name
            <input className="form-input mt-2" value={name}
              onChange={(e) => setName(e.target.value)} required maxLength={160} />
          </label>
          <label className="block text-xs font-semibold text-slate-700">
            Sending domain ID
            <input className="form-input mt-2" value={domainId}
              inputMode="numeric" pattern="[0-9]+" placeholder="From UnifyDesk Email console"
              onChange={(e) => setDomainId(e.target.value)} required />
          </label>
        </div>

        <label className="mt-4 block text-xs font-semibold text-slate-700">
          Project API key
          <PasswordInput
            className="mt-2"
            value={apiKey}
            placeholder="ude_live_..."
            autoComplete="new-password"
            onChange={(e) => setApiKey(e.target.value)}
            required
            showLeftIcon
          />
        </label>

        <div className="mt-4 grid gap-4 sm:grid-cols-2">
          <label className="block text-xs font-semibold text-slate-700">
            Verified From address
            <input className="form-input mt-2" type="email"
              placeholder="news@yourdomain.com" value={fromAddress}
              onChange={(e) => setFromAddress(e.target.value)} required />
          </label>
          <label className="block text-xs font-semibold text-slate-700">
            Display name
            <input className="form-input mt-2" value={displayName}
              onChange={(e) => setDisplayName(e.target.value)} maxLength={255} />
          </label>
          <label className="block text-xs font-semibold text-slate-700 sm:col-span-2">
            Reply-To address (optional)
            <input className="form-input mt-2" type="email" value={replyToAddress}
              onChange={(e) => setReplyToAddress(e.target.value)} />
          </label>
        </div>

        <div className="mt-6 flex justify-end gap-3">
          <button type="button" className="secondary-button" onClick={onClose} disabled={busy}>Cancel</button>
          <button type="submit" className="primary-button" disabled={busy}>
            {busy ? 'Connecting…' : 'Connect email project'}
          </button>
        </div>
      </form>
    </div>
  )
}
