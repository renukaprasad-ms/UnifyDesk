import { useState, type FormEvent } from 'react'
import { FiCheckCircle, FiSmartphone, FiX } from 'react-icons/fi'
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

export default function Msg91ConnectModal({ companyId, onClose, onConnected }: Props) {
  const snackbar = useSnackbar()
  const [name, setName] = useState('MSG91 SMS')
  const [authKey, setAuthKey] = useState('')
  const [flowId, setFlowId] = useState('')
  const [senderId, setSenderId] = useState('')
  const [messageVariable, setMessageVariable] = useState('VAR1')
  const [route, setRoute] = useState('4')
  const [busy, setBusy] = useState(false)

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    setBusy(true)
    try {
      const sender = await broadcastApi.connectMsg91(companyId, {
        name: name.trim(),
        authKey: authKey.trim(),
        flowId: flowId.trim(),
        senderId: senderId.trim(),
        messageVariable: messageVariable.trim() || 'VAR1',
        route: route.trim() || null,
      })
      onConnected(sender)
    } catch (requestError) {
      snackbar.error(apiErrorMessage(requestError, 'Unable to connect MSG91 SMS'))
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
            <span className="flex size-10 shrink-0 items-center justify-center rounded-xl bg-violet-50 text-violet-600">
              <FiSmartphone aria-hidden="true" />
            </span>
            <div>
              <p className="text-base font-bold text-slate-950">Connect MSG91 SMS</p>
              <p className="mt-1 text-xs leading-5 text-slate-500">
                Connect an approved MSG91 SMS Flow and Sender ID/Header for broadcasts.
              </p>
            </div>
          </div>
          <button type="button" className="admin-back-button" onClick={onClose} aria-label="Close">
            <FiX aria-hidden="true" />
          </button>
        </div>

        <div className="mt-5 rounded-xl border border-violet-100 bg-violet-50/60 p-4">
          <p className="text-xs font-bold text-slate-900">Template-based SMS</p>
          <p className="mt-1 text-xs leading-5 text-slate-600">
            The selected MSG91 Flow should contain one variable for the broadcast message body. By
            default Messaging sends the body into <strong>VAR1</strong>. Indian commercial SMS also
            needs the appropriate DLT-approved sender and template in MSG91.
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
            Sender ID / Header
            <input
              className="form-input mt-2"
              value={senderId}
              onChange={(event) => setSenderId(event.target.value)}
              placeholder="MYBRND"
              maxLength={32}
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
            Flow ID
            <input
              className="form-input mt-2"
              value={flowId}
              onChange={(event) => setFlowId(event.target.value)}
              required
            />
          </label>

          <label className="block text-xs font-semibold text-slate-700">
            Message variable
            <input
              className="form-input mt-2"
              value={messageVariable}
              onChange={(event) => setMessageVariable(event.target.value)}
              placeholder="VAR1"
              pattern="[A-Za-z0-9_]+"
              required
            />
          </label>

          <label className="block text-xs font-semibold text-slate-700 sm:col-span-2">
            Route
            <select
              className="form-input mt-2"
              value={route}
              onChange={(event) => setRoute(event.target.value)}
            >
              <option value="4">Transactional (4)</option>
              <option value="1">Promotional (1)</option>
              <option value="">Use Flow default</option>
            </select>
          </label>
        </div>

        <div className="mt-4 flex gap-2 rounded-xl border border-amber-200 bg-amber-50 px-3 py-2 text-[11px] leading-4 text-amber-800">
          <FiCheckCircle className="mt-0.5 shrink-0" aria-hidden="true" />
          <span>The MSG91 auth key is encrypted before storage.</span>
        </div>

        <div className="mt-6 flex flex-wrap justify-end gap-2">
          <button type="button" className="secondary-button" onClick={onClose} disabled={busy}>
            Cancel
          </button>
          <button type="submit" className="primary-button" disabled={busy}>
            {busy ? 'Connecting…' : 'Connect MSG91 SMS'}
          </button>
        </div>
      </form>
    </div>
  )
}
