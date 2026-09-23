import { useState, type FormEvent } from 'react'
import { FiX } from 'react-icons/fi'
import type { AdAccount, AdCampaign, CreateGoogleAdGroupInput } from '../../features/ads/types'

type Props = {
  account: AdAccount
  campaign: AdCampaign
  open: boolean
  saving: boolean
  onClose: () => void
  onCreate: (input: CreateGoogleAdGroupInput) => Promise<void>
}

const toMinor = (value: string, currency?: string | null) => {
  if (!value.trim()) return null
  const numeric = Number(value)
  if (!Number.isFinite(numeric) || numeric <= 0) return null
  if (!currency) return Math.round(numeric)
  try {
    const digits =
      new Intl.NumberFormat(undefined, { style: 'currency', currency })
        .resolvedOptions().maximumFractionDigits ?? 2
    return Math.round(numeric * 10 ** digits)
  } catch {
    return Math.round(numeric)
  }
}

export default function GoogleAdGroupModal({ account, campaign, open, saving, onClose, onCreate }: Props) {
  const [name, setName] = useState('')
  const [cpcBid, setCpcBid] = useState('')

  if (!open) return null

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    await onCreate({
      name: name.trim(),
      cpcBidMinor: toMinor(cpcBid, account.currency),
    })
  }

  return (
    <div className="mobile-modal-shell fixed inset-0 z-50 flex items-center justify-center bg-slate-950/40 p-4">
      <div className="mobile-sheet w-full max-w-xl rounded-2xl bg-white shadow-2xl">
        <div className="flex items-start justify-between border-b border-slate-100 px-6 py-5">
          <div>
            <p className="eyebrow">Google Ads</p>
            <h2 className="mt-1 text-xl font-bold text-slate-950">Create ad group</h2>
            <p className="mt-1 text-sm text-slate-500">{campaign.name}</p>
          </div>
          <button type="button" className="rounded-lg p-2 text-slate-400 hover:bg-slate-100" onClick={onClose}><FiX size={18} /></button>
        </div>
        <form className="space-y-5 p-6" onSubmit={submit}>
          <label className="block text-xs font-semibold text-slate-700">
            Ad group name
            <input className="form-input mt-2" value={name} maxLength={255} required autoFocus onChange={(e) => setName(e.target.value)} />
          </label>
          <label className="block text-xs font-semibold text-slate-700">
            Default CPC bid {account.currency ? `(${account.currency})` : ''}
            <input className="form-input mt-2" type="number" min="0.01" step="0.01" value={cpcBid} placeholder="Optional" onChange={(e) => setCpcBid(e.target.value)} />
          </label>
          <div className="rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-xs leading-5 text-amber-800">
            New ad groups are created PAUSED so keywords and ads can be reviewed first.
          </div>
          <div className="flex justify-end gap-2 border-t border-slate-100 pt-5">
            <button type="button" className="secondary-button" onClick={onClose} disabled={saving}>Cancel</button>
            <button type="submit" className="primary-button" disabled={saving || !name.trim()}>{saving ? 'Creating…' : 'Create ad group'}</button>
          </div>
        </form>
      </div>
    </div>
  )
}
