import { useState, type FormEvent } from 'react'
import { FiCheckCircle, FiInfo, FiMail, FiX } from 'react-icons/fi'
import { broadcastApi } from '../../features/broadcast/broadcastApi'
import type { BroadcastSender } from '../../features/broadcast/types'
import { apiErrorMessage } from '../../lib/api/apiClient'

type Props = {
  companyId: string
  onClose: () => void
  onConnected: (sender: BroadcastSender) => void
}

export default function Msg91EmailConnectModal({ companyId, onClose, onConnected }: Props) {
  const [name, setName] = useState('MSG91 Email')
  const [authKey, setAuthKey] = useState('')
  const [domain, setDomain] = useState('')
  const [templateId, setTemplateId] = useState('')
  const [fromAddress, setFromAddress] = useState('')
  const [displayName, setDisplayName] = useState('')
  const [replyToAddress, setReplyToAddress] = useState('')
  const [messageVariable, setMessageVariable] = useState('message')
  const [subjectVariable, setSubjectVariable] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    setBusy(true)
    setError(null)
    try {
      const sender = await broadcastApi.connectMsg91Email(companyId, {
        name: name.trim(),
        authKey: authKey.trim(),
        domain: domain.trim().toLowerCase(),
        templateId: templateId.trim(),
        fromAddress: fromAddress.trim(),
        displayName: displayName.trim() || null,
        replyToAddress: replyToAddress.trim() || null,
        messageVariable: messageVariable.trim() || 'message',
        subjectVariable: subjectVariable.trim() || null,
      })
      onConnected(sender)
    } catch (requestError) {
      setError(apiErrorMessage(requestError, 'Unable to connect MSG91 Email'))
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
                Use an MSG91 verified domain and template for email broadcasts.
              </p>
            </div>
          </div>
          <button type="button" className="admin-back-button" onClick={onClose} aria-label="Close">
            <FiX aria-hidden="true" />
          </button>
        </div>

        {error && (
          <div className="mt-4 flex gap-2 rounded-xl border border-rose-200 bg-rose-50 px-3 py-2 text-xs text-rose-700">
            <FiInfo className="mt-0.5 shrink-0" aria-hidden="true" />
            <span>{error}</span>
          </div>
        )}

        <div className="mt-5 rounded-xl border border-indigo-100 bg-indigo-50/60 p-4">
          <p className="text-xs font-bold text-slate-900">Template-based Email API</p>
          <p className="mt-1 text-xs leading-5 text-slate-600">
            Create and verify the sending domain and email template in MSG91 first. Messaging sends
            the broadcast body into the configured template variable and can optionally send the
            broadcast subject into a second template variable.
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
            Verified domain
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
          <input
            type="password"
            className="form-input mt-2"
            value={authKey}
            onChange={(event) => setAuthKey(event.target.value)}
            autoComplete="new-password"
            required
          />
        </label>

        <div className="mt-4 grid gap-4 sm:grid-cols-2">
          <label className="block text-xs font-semibold text-slate-700">
            Template ID
            <input
              className="form-input mt-2"
              value={templateId}
              onChange={(event) => setTemplateId(event.target.value)}
              placeholder="welcome-template"
              maxLength={255}
              required
            />
          </label>

          <label className="block text-xs font-semibold text-slate-700">
            Body variable
            <input
              className="form-input mt-2"
              value={messageVariable}
              onChange={(event) => setMessageVariable(event.target.value)}
              placeholder="message"
              pattern="[A-Za-z0-9_]+"
              required
            />
          </label>

          <label className="block text-xs font-semibold text-slate-700">
            Subject variable
            <input
              className="form-input mt-2"
              value={subjectVariable}
              onChange={(event) => setSubjectVariable(event.target.value)}
              placeholder="subject (optional)"
              pattern="[A-Za-z0-9_]+"
            />
          </label>

          <label className="block text-xs font-semibold text-slate-700">
            From email
            <input
              type="email"
              className="form-input mt-2"
              value={fromAddress}
              onChange={(event) => setFromAddress(event.target.value)}
              placeholder="marketing@mailer.example.com"
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

          <label className="block text-xs font-semibold text-slate-700">
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
          <span>The MSG91 auth key is encrypted before storage and is never returned by the API.</span>
        </div>

        <div className="mt-6 flex flex-wrap justify-end gap-2">
          <button type="button" className="secondary-button" onClick={onClose} disabled={busy}>
            Cancel
          </button>
          <button type="submit" className="primary-button" disabled={busy}>
            {busy ? 'Connecting…' : 'Connect MSG91 Email'}
          </button>
        </div>
      </form>
    </div>
  )
}
