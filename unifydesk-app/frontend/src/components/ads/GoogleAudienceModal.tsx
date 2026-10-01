import { useState, type FormEvent } from 'react'
import { FiX } from 'react-icons/fi'
import type { CreateGoogleAudienceInput } from '../../features/ads/types'

type Props = {
  open: boolean
  saving: boolean
  onClose: () => void
  onCreate: (input: CreateGoogleAudienceInput) => Promise<void>
}

export default function GoogleAudienceModal({ open, saving, onClose, onCreate }: Props) {
  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
  const [membershipLifeSpan, setMembershipLifeSpan] = useState('30')
  const [uploadKeyType, setUploadKeyType] =
    useState<CreateGoogleAudienceInput['uploadKeyType']>('CONTACT_INFO')
  if (!open) return null
  const submit = async (event: FormEvent) => {
    event.preventDefault()
    await onCreate({
      name: name.trim(),
      description: description.trim() || null,
      membershipLifeSpan: Number(membershipLifeSpan),
      uploadKeyType,
    })
  }
  return (
    <div className="mobile-modal-shell fixed inset-0 z-50 flex items-center justify-center bg-slate-950/40 p-4">
      <div className="mobile-sheet w-full max-w-xl rounded-2xl bg-white shadow-2xl">
        <div className="flex items-start justify-between border-b border-slate-100 px-6 py-5">
          <div>
            <p className="eyebrow">Google Ads</p>
            <h2 className="mt-1 text-xl font-bold text-slate-950">
              Create Customer Match audience
            </h2>
          </div>
          <button
            type="button"
            className="rounded-lg p-2 text-slate-400 hover:bg-slate-100"
            onClick={onClose}
          >
            <FiX size={18} />
          </button>
        </div>
        <form className="space-y-5 p-6" onSubmit={submit}>
          <label className="block text-xs font-semibold text-slate-700">
            Audience name
            <input
              className="form-input mt-2"
              value={name}
              required
              maxLength={255}
              onChange={(e) => setName(e.target.value)}
            />
          </label>
          <label className="block text-xs font-semibold text-slate-700">
            Description
            <input
              className="form-input mt-2"
              value={description}
              onChange={(e) => setDescription(e.target.value)}
            />
          </label>
          <div className="grid gap-4 sm:grid-cols-2">
            <label className="block text-xs font-semibold text-slate-700">
              Membership days
              <input
                className="form-input mt-2"
                type="number"
                min="0"
                max="540"
                value={membershipLifeSpan}
                onChange={(e) => setMembershipLifeSpan(e.target.value)}
              />
            </label>
            <label className="block text-xs font-semibold text-slate-700">
              Upload key
              <select
                className="form-input mt-2"
                value={uploadKeyType}
                onChange={(e) =>
                  setUploadKeyType(e.target.value as CreateGoogleAudienceInput['uploadKeyType'])
                }
              >
                <option value="CONTACT_INFO">Contact info</option>
                <option value="CRM_ID">CRM ID</option>
                <option value="MOBILE_ADVERTISING_ID">Mobile advertising ID</option>
              </select>
            </label>
          </div>
          <div className="rounded-xl border border-slate-200 bg-slate-50 px-4 py-3 text-xs leading-5 text-slate-600">
            This creates the Google Ads audience list. Customer-member ingestion is handled
            separately from the audience definition.
          </div>
          <div className="flex justify-end gap-2 border-t border-slate-100 pt-5">
            <button type="button" className="secondary-button" onClick={onClose} disabled={saving}>
              Cancel
            </button>
            <button type="submit" className="primary-button" disabled={saving || !name.trim()}>
              {saving ? 'Creating…' : 'Create audience'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}
