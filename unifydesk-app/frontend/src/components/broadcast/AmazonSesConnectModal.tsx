import { useState, type FormEvent } from 'react'
import { FiCheckCircle, FiCloud, FiShield, FiX } from 'react-icons/fi'
import { broadcastApi } from '../../features/broadcast/broadcastApi'
import type { BroadcastSender, BroadcastSenderAuthMode } from '../../features/broadcast/types'
import { apiErrorMessage } from '../../lib/api/apiClient'
import { useSnackbar } from '../feedback/SnackbarProvider'
import PasswordInput from '../ui/PasswordInput'

type Props = {
  companyId: string
  onClose: () => void
  onConnected: (sender: BroadcastSender) => void
}

export default function AmazonSesConnectModal({ companyId, onClose, onConnected }: Props) {
  const snackbar = useSnackbar()
  const [name, setName] = useState('Amazon SES')
  const [region, setRegion] = useState('ap-south-1')
  const [authMode, setAuthMode] = useState<BroadcastSenderAuthMode>('ACCESS_KEYS')
  const [roleArn, setRoleArn] = useState('')
  const [externalId, setExternalId] = useState('')
  const [accessKeyId, setAccessKeyId] = useState('')
  const [secretAccessKey, setSecretAccessKey] = useState('')
  const [fromAddress, setFromAddress] = useState('')
  const [displayName, setDisplayName] = useState('')
  const [replyToAddress, setReplyToAddress] = useState('')
  const [busy, setBusy] = useState(false)

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    setBusy(true)
    try {
      const sender = await broadcastApi.connectAmazonSes(companyId, {
        name: name.trim(),
        region: region.trim(),
        authMode,
        roleArn: authMode === 'IAM_ROLE' ? roleArn.trim() : null,
        externalId: authMode === 'IAM_ROLE' ? externalId.trim() || null : null,
        accessKeyId: authMode === 'ACCESS_KEYS' ? accessKeyId.trim() : null,
        secretAccessKey: authMode === 'ACCESS_KEYS' ? secretAccessKey : null,
        fromAddress: fromAddress.trim(),
        displayName: displayName.trim() || null,
        replyToAddress: replyToAddress.trim() || null,
      })
      onConnected(sender)
    } catch (requestError) {
      snackbar.error(apiErrorMessage(requestError, 'Unable to connect Amazon SES'))
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
            <span className="flex size-10 shrink-0 items-center justify-center rounded-xl bg-orange-50 text-orange-600">
              <FiCloud aria-hidden="true" />
            </span>
            <div>
              <p className="text-base font-bold text-slate-950">Connect Amazon SES</p>
              <p className="mt-1 text-xs leading-5 text-slate-500">
                Bring your own AWS SES account for high-volume email broadcasts. AWS bills your AWS
                account directly.
              </p>
            </div>
          </div>
          <button type="button" className="admin-back-button" onClick={onClose} aria-label="Close">
            <FiX aria-hidden="true" />
          </button>
        </div>

        <div className="mt-5 rounded-xl border border-indigo-100 bg-indigo-50/60 p-4">
          <p className="text-xs font-bold text-slate-900">Phase 1: customer-managed SES</p>
          <p className="mt-1 text-xs leading-5 text-slate-600">
            Your AWS account owns the quota, sender reputation and SES charges. Managed SES inside
            Messaging will be added later.
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
            AWS region
            <input
              className="form-input mt-2"
              value={region}
              onChange={(event) => setRegion(event.target.value)}
              placeholder="ap-south-1"
              required
            />
          </label>
        </div>

        <div className="mt-5">
          <p className="text-xs font-semibold text-slate-700">Authentication</p>
          <div className="mt-2 grid gap-2 sm:grid-cols-2">
            <button
              type="button"
              className={
                authMode === 'IAM_ROLE'
                  ? 'rounded-xl border border-indigo-300 bg-indigo-50 p-3 text-left'
                  : 'rounded-xl border border-slate-200 p-3 text-left hover:bg-slate-50'
              }
              onClick={() => setAuthMode('IAM_ROLE')}
            >
              <span className="flex items-center gap-2 text-xs font-bold text-slate-900">
                <FiShield aria-hidden="true" />
                IAM Role
              </span>
              <span className="mt-1 block text-[11px] leading-4 text-slate-500">
                Recommended. Uses temporary AWS credentials through STS.
              </span>
            </button>
            <button
              type="button"
              className={
                authMode === 'ACCESS_KEYS'
                  ? 'rounded-xl border border-indigo-300 bg-indigo-50 p-3 text-left'
                  : 'rounded-xl border border-slate-200 p-3 text-left hover:bg-slate-50'
              }
              onClick={() => setAuthMode('ACCESS_KEYS')}
            >
              <span className="text-xs font-bold text-slate-900">Access keys</span>
              <span className="mt-1 block text-[11px] leading-4 text-slate-500">
                Easier for self-hosting. Secrets are encrypted before storage.
              </span>
            </button>
          </div>
        </div>

        {authMode === 'IAM_ROLE' ? (
          <div className="mt-4 grid gap-4 sm:grid-cols-2">
            <label className="block text-xs font-semibold text-slate-700 sm:col-span-2">
              IAM Role ARN
              <input
                className="form-input mt-2"
                value={roleArn}
                onChange={(event) => setRoleArn(event.target.value)}
                placeholder="arn:aws:iam::123456789012:role/MessagingSesSender"
                required
              />
            </label>
            <label className="block text-xs font-semibold text-slate-700 sm:col-span-2">
              External ID
              <input
                className="form-input mt-2"
                value={externalId}
                onChange={(event) => setExternalId(event.target.value)}
                placeholder="Optional but strongly recommended"
              />
            </label>
          </div>
        ) : (
          <div className="mt-4 grid gap-4 sm:grid-cols-2">
            <label className="block text-xs font-semibold text-slate-700">
              AWS Access Key ID
              <input
                className="form-input mt-2"
                value={accessKeyId}
                onChange={(event) => setAccessKeyId(event.target.value)}
                autoComplete="off"
                required
              />
            </label>
            <label className="block text-xs font-semibold text-slate-700">
              AWS Secret Access Key
              <PasswordInput
                className="mt-2"
                value={secretAccessKey}
                onChange={(event) => setSecretAccessKey(event.target.value)}
                autoComplete="new-password"
                required
                showLeftIcon
              />
            </label>
          </div>
        )}

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

        <div className="mt-5 rounded-xl border border-slate-200 bg-slate-50 p-4">
          <p className="text-xs font-bold text-slate-900">AWS SES Essentials reference pricing</p>
          <div className="mt-2 grid gap-1 text-[11px] text-slate-600">
            <span>$0.16 USD / 1,000 emails for 0–10M emails/month</span>
            <span>$0.14 USD / 1,000 emails for 10–100M emails/month</span>
            <span>$0.11 USD / 1,000 emails above 100M emails/month</span>
          </div>
          <p className="mt-2 text-[11px] leading-4 text-slate-500">
            Reference pricing from the AWS plan screen you provided. AWS pricing can change; this is
            informational and is not used by Messaging for billing.
          </p>
        </div>

        <div className="mt-4 flex gap-2 rounded-xl border border-amber-200 bg-amber-50 px-3 py-2 text-[11px] leading-4 text-amber-800">
          <FiCheckCircle className="mt-0.5 shrink-0" aria-hidden="true" />
          <span>
            Messaging validates the SES account, reads its current quota/send rate, and verifies the
            From email or its domain before saving this sender.
          </span>
        </div>

        <div className="mt-6 flex flex-wrap justify-end gap-2">
          <button type="button" className="secondary-button" onClick={onClose} disabled={busy}>
            Cancel
          </button>
          <button type="submit" className="primary-button" disabled={busy}>
            {busy ? 'Connecting…' : 'Connect Amazon SES'}
          </button>
        </div>
      </form>
    </div>
  )
}
