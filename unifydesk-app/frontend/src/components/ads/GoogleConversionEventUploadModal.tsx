import PhoneInput from '../ui/PhoneInput'
import { useState, type FormEvent } from 'react'
import { FiUploadCloud, FiX } from 'react-icons/fi'
import type { AdConversion, GoogleConversionEventUploadInput } from '../../features/ads/types'

type Props = {
  conversion: AdConversion | null
  open: boolean
  saving: boolean
  defaultCurrency?: string | null
  onClose: () => void
  onUpload: (input: GoogleConversionEventUploadInput) => Promise<void>
}

export default function GoogleConversionEventUploadModal({
  conversion,
  open,
  saving,
  defaultCurrency,
  onClose,
  onUpload,
}: Props) {
  const [eventTimestamp, setEventTimestamp] = useState(() => new Date().toISOString().slice(0, 16))
  const [eventSource, setEventSource] = useState<
    'WEB' | 'APP' | 'IN_STORE' | 'PHONE' | 'MESSAGE' | 'OTHER'
  >('WEB')
  const [transactionId, setTransactionId] = useState('')
  const [value, setValue] = useState('')
  const [currency, setCurrency] = useState(defaultCurrency ?? '')
  const [gclid, setGclid] = useState('')
  const [gbraid, setGbraid] = useState('')
  const [wbraid, setWbraid] = useState('')
  const [email, setEmail] = useState('')
  const [phone, setPhone] = useState('')
  const [userId, setUserId] = useState('')
  const [adUserDataConsent, setAdUserDataConsent] = useState(false)
  const [adPersonalizationConsent, setAdPersonalizationConsent] = useState(false)
  const [validateOnly, setValidateOnly] = useState(false)

  if (!open || !conversion) return null

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    const timestamp = new Date(eventTimestamp)
    if (Number.isNaN(timestamp.getTime())) return

    await onUpload({
      events: [
        {
          eventTimestamp: timestamp.toISOString(),
          eventSource,
          transactionId: transactionId.trim() || null,
          conversionValue: value ? Number(value) : null,
          currency: currency.trim() || null,
          gclid: gclid.trim() || null,
          gbraid: gbraid.trim() || null,
          wbraid: wbraid.trim() || null,
          emails: email.trim() ? [email.trim()] : [],
          phoneNumbers: phone.trim() ? [phone.trim()] : [],
          userId: userId.trim() || null,
        },
      ],
      adUserDataConsent,
      adPersonalizationConsent,
      validateOnly,
    })
  }

  return (
    <div className="mobile-modal-shell fixed inset-0 z-50 flex items-center justify-center bg-slate-950/40 p-4">
      <div className="mobile-sheet max-h-[92vh] w-full max-w-3xl overflow-y-auto rounded-2xl bg-white shadow-2xl">
        <div className="sticky top-0 z-10 flex items-start justify-between border-b border-slate-100 bg-white px-6 py-5">
          <div>
            <p className="eyebrow">Google Data Manager</p>
            <h2 className="mt-1 text-xl font-bold text-slate-950">Upload conversion event</h2>
            <p className="mt-1 text-sm text-slate-500">{conversion.name}</p>
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
          <div className="grid gap-4 sm:grid-cols-2">
            <label className="text-xs font-semibold text-slate-700">
              Event time
              <input
                className="form-input mt-2"
                type="datetime-local"
                required
                value={eventTimestamp}
                onChange={(event) => setEventTimestamp(event.target.value)}
              />
            </label>
            <label className="text-xs font-semibold text-slate-700">
              Event source
              <select
                className="form-input mt-2"
                value={eventSource}
                onChange={(event) =>
                  setEventSource(
                    event.target.value as
                      'WEB' | 'APP' | 'IN_STORE' | 'PHONE' | 'MESSAGE' | 'OTHER',
                  )
                }
              >
                <option value="WEB">Web</option>
                <option value="APP">App</option>
                <option value="IN_STORE">In store</option>
                <option value="PHONE">Phone</option>
                <option value="MESSAGE">Message</option>
                <option value="OTHER">Other</option>
              </select>
            </label>
            <label className="text-xs font-semibold text-slate-700">
              Transaction ID
              <input
                className="form-input mt-2"
                value={transactionId}
                onChange={(event) => setTransactionId(event.target.value)}
              />
            </label>
            <label className="text-xs font-semibold text-slate-700">
              Conversion value
              <input
                className="form-input mt-2"
                type="number"
                step="0.01"
                value={value}
                onChange={(event) => setValue(event.target.value)}
              />
            </label>
            <label className="text-xs font-semibold text-slate-700">
              Currency
              <input
                className="form-input mt-2"
                maxLength={3}
                value={currency}
                onChange={(event) => setCurrency(event.target.value.toUpperCase())}
              />
            </label>
            <label className="text-xs font-semibold text-slate-700">
              User ID
              <input
                className="form-input mt-2"
                value={userId}
                onChange={(event) => setUserId(event.target.value)}
              />
            </label>
          </div>

          <div>
            <h3 className="text-sm font-bold text-slate-950">Attribution identifiers</h3>
            <div className="mt-3 grid gap-4 sm:grid-cols-3">
              <label className="text-xs font-semibold text-slate-700">
                GCLID
                <input
                  className="form-input mt-2"
                  value={gclid}
                  onChange={(event) => setGclid(event.target.value)}
                />
              </label>
              <label className="text-xs font-semibold text-slate-700">
                GBRAID
                <input
                  className="form-input mt-2"
                  value={gbraid}
                  onChange={(event) => setGbraid(event.target.value)}
                />
              </label>
              <label className="text-xs font-semibold text-slate-700">
                WBRAID
                <input
                  className="form-input mt-2"
                  value={wbraid}
                  onChange={(event) => setWbraid(event.target.value)}
                />
              </label>
            </div>
          </div>

          <div>
            <h3 className="text-sm font-bold text-slate-950">Enhanced conversion identifiers</h3>
            <div className="mt-3 grid gap-4 sm:grid-cols-2">
              <label className="text-xs font-semibold text-slate-700">
                Email
                <input
                  className="form-input mt-2"
                  type="email"
                  value={email}
                  onChange={(event) => setEmail(event.target.value)}
                />
              </label>
              <label className="text-xs font-semibold text-slate-700">
                Phone (E.164)
                <PhoneInput
                  className="form-input mt-2"
                  placeholder="+919876543210"
                  value={phone}
                  onChange={(value) => setPhone(value)}
                />
              </label>
            </div>
            <p className="mt-2 text-xs text-slate-400">
              Reachlyo normalizes and SHA-256 hashes email and phone identifiers before sending them
              to Google.
            </p>
          </div>

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
                onChange={(event) => setAdPersonalizationConsent(event.target.checked)}
              />
              Ad personalization consent granted
            </label>
            <label className="flex items-center gap-2 sm:col-span-2">
              <input
                type="checkbox"
                checked={validateOnly}
                onChange={(event) => setValidateOnly(event.target.checked)}
              />
              Validate only — do not ingest this event
            </label>
          </div>

          <div className="flex justify-end gap-2 border-t border-slate-100 pt-5">
            <button type="button" className="secondary-button" onClick={onClose} disabled={saving}>
              Cancel
            </button>
            <button
              type="submit"
              className="primary-button inline-flex items-center gap-2"
              disabled={saving}
            >
              <FiUploadCloud aria-hidden="true" />
              {saving ? 'Submitting…' : validateOnly ? 'Validate event' : 'Upload event'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}
