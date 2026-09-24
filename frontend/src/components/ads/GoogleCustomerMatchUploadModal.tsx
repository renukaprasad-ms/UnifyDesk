import { useMemo, useState, type FormEvent } from 'react'
import { FiUploadCloud, FiX } from 'react-icons/fi'
import type {
  AdAudience,
  GoogleCustomerMatchUploadInput,
} from '../../features/ads/types'

type IdentifierType = 'EMAIL' | 'PHONE' | 'USER_ID' | 'MOBILE_ID'

type Props = {
  audience: AdAudience | null
  open: boolean
  saving: boolean
  onClose: () => void
  onUpload: (input: GoogleCustomerMatchUploadInput) => Promise<void>
}

export default function GoogleCustomerMatchUploadModal({
  audience,
  open,
  saving,
  onClose,
  onUpload,
}: Props) {
  const [identifierType, setIdentifierType] = useState<IdentifierType>('EMAIL')
  const [values, setValues] = useState('')
  const [adUserDataConsent, setAdUserDataConsent] = useState(true)
  const [adPersonalizationConsent, setAdPersonalizationConsent] = useState(true)
  const [termsAccepted, setTermsAccepted] = useState(false)
  const [validateOnly, setValidateOnly] = useState(false)

  const rows = useMemo(
    () =>
      values
        .split(/\r?\n/)
        .map((value) => value.trim())
        .filter(Boolean),
    [values],
  )

  if (!open || !audience) return null

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    if (!rows.length || !termsAccepted) return

    await onUpload({
      members: rows.map((value) => {
        if (identifierType === 'EMAIL') return { emails: [value] }
        if (identifierType === 'PHONE') return { phoneNumbers: [value] }
        if (identifierType === 'MOBILE_ID') return { mobileIds: [value] }
        return { userId: value }
      }),
      adUserDataConsent,
      adPersonalizationConsent,
      customerMatchTermsAccepted: termsAccepted,
      validateOnly,
    })
  }

  return (
    <div className="mobile-modal-shell fixed inset-0 z-50 flex items-center justify-center bg-slate-950/40 p-4">
      <div className="mobile-sheet w-full max-w-2xl rounded-2xl bg-white shadow-2xl">
        <div className="flex items-start justify-between border-b border-slate-100 px-6 py-5">
          <div>
            <p className="eyebrow">Google Customer Match</p>
            <h2 className="mt-1 text-xl font-bold text-slate-950">
              Upload audience members
            </h2>
            <p className="mt-1 text-sm text-slate-500">{audience.name}</p>
          </div>
          <button
            type="button"
            className="rounded-lg p-2 text-slate-400 hover:bg-slate-100"
            onClick={onClose}
          >
            <FiX size={18} aria-hidden="true" />
          </button>
        </div>

        <form className="space-y-5 p-6" onSubmit={submit}>
          <label className="block text-xs font-semibold text-slate-700">
            Identifier type
            <select
              className="form-input mt-2"
              value={identifierType}
              onChange={(event) =>
                setIdentifierType(event.target.value as IdentifierType)
              }
            >
              <option value="EMAIL">Email address</option>
              <option value="PHONE">Phone number (E.164)</option>
              <option value="USER_ID">Advertiser user ID</option>
              <option value="MOBILE_ID">Mobile advertising ID</option>
            </select>
          </label>

          <label className="block text-xs font-semibold text-slate-700">
            Members
            <textarea
              className="form-input mt-2 min-h-48 font-mono text-xs"
              placeholder={
                identifierType === 'EMAIL'
                  ? 'customer@example.com'
                  : identifierType === 'PHONE'
                    ? '+919876543210'
                    : 'one identifier per line'
              }
              value={values}
              onChange={(event) => setValues(event.target.value)}
            />
            <span className="mt-1 block font-normal text-slate-400">
              {rows.length} member(s). Emails and phone numbers are normalized and SHA-256 hashed by Reachlyo before upload.
            </span>
          </label>

          <div className="grid gap-3 rounded-xl border border-slate-200 bg-slate-50 p-4 text-xs text-slate-600 sm:grid-cols-2">
            <label className="flex items-center gap-2">
              <input
                type="checkbox"
                checked={adUserDataConsent}
                onChange={(event) => setAdUserDataConsent(event.target.checked)}
              />
              Ad user data consent granted
            </label>
            <label className="flex items-center gap-2">
              <input
                type="checkbox"
                checked={adPersonalizationConsent}
                onChange={(event) =>
                  setAdPersonalizationConsent(event.target.checked)
                }
              />
              Ad personalization consent granted
            </label>
            <label className="flex items-start gap-2 sm:col-span-2">
              <input
                className="mt-0.5"
                type="checkbox"
                checked={termsAccepted}
                onChange={(event) => setTermsAccepted(event.target.checked)}
              />
              <span>
                I confirm the Google Customer Match terms have been accepted for this account and that I am authorized to upload this first-party data.
              </span>
            </label>
            <label className="flex items-center gap-2 sm:col-span-2">
              <input
                type="checkbox"
                checked={validateOnly}
                onChange={(event) => setValidateOnly(event.target.checked)}
              />
              Validate only — do not ingest members
            </label>
          </div>

          <div className="flex justify-end gap-2 border-t border-slate-100 pt-5">
            <button
              type="button"
              className="secondary-button"
              onClick={onClose}
              disabled={saving}
            >
              Cancel
            </button>
            <button
              type="submit"
              className="primary-button inline-flex items-center gap-2"
              disabled={saving || !rows.length || !termsAccepted}
            >
              <FiUploadCloud aria-hidden="true" />
              {saving
                ? 'Submitting…'
                : validateOnly
                  ? 'Validate members'
                  : 'Upload members'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}
