import { useState, type FormEvent } from 'react'
import { FiX } from 'react-icons/fi'
import type { AdAccount, CreateGoogleCampaignInput } from '../../features/ads/types'

type Props = {
  account: AdAccount
  open: boolean
  saving: boolean
  onClose: () => void
  onCreate: (input: CreateGoogleCampaignInput) => Promise<void>
}

const toMinor = (value: string, currency?: string | null) => {
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

export default function GoogleCampaignModal({
  account,
  open,
  saving,
  onClose,
  onCreate,
}: Props) {
  const [name, setName] = useState('')
  const [budget, setBudget] = useState('')
  const [bidStrategy, setBidStrategy] =
    useState<CreateGoogleCampaignInput['bidStrategy']>('MANUAL_CPC')
  const [startDate, setStartDate] = useState('')
  const [endDate, setEndDate] = useState('')

  if (!open) return null

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    const dailyBudgetMinor = toMinor(budget, account.currency)
    if (dailyBudgetMinor == null) return
    await onCreate({
      name: name.trim(),
      dailyBudgetMinor,
      bidStrategy,
      startDate: startDate || null,
      endDate: endDate || null,
    })
  }

  return (
    <div className="mobile-modal-shell fixed inset-0 z-50 flex items-center justify-center bg-slate-950/40 p-4">
      <div className="mobile-sheet w-full max-w-2xl rounded-2xl bg-white shadow-2xl">
        <div className="flex items-start justify-between border-b border-slate-100 px-6 py-5">
          <div>
            <p className="eyebrow">Google Ads</p>
            <h2 className="mt-1 text-xl font-bold text-slate-950">Create Search campaign</h2>
            <p className="mt-1 text-sm text-slate-500">
              {account.name}. New campaigns are created paused.
            </p>
          </div>
          <button type="button" className="rounded-lg p-2 text-slate-400 hover:bg-slate-100" onClick={onClose}>
            <FiX size={18} aria-hidden="true" />
          </button>
        </div>

        <form className="space-y-5 p-6" onSubmit={submit}>
          <label className="block text-xs font-semibold text-slate-700">
            Campaign name
            <input className="form-input mt-2" value={name} maxLength={255} required autoFocus onChange={(event) => setName(event.target.value)} />
          </label>

          <div className="grid gap-4 sm:grid-cols-2">
            <label className="block text-xs font-semibold text-slate-700">
              Daily budget {account.currency ? `(${account.currency})` : ''}
              <input className="form-input mt-2" type="number" min="0.01" step="0.01" value={budget} required onChange={(event) => setBudget(event.target.value)} />
            </label>

            <label className="block text-xs font-semibold text-slate-700">
              Bid strategy
              <select className="form-input mt-2" value={bidStrategy} onChange={(event) => setBidStrategy(event.target.value as CreateGoogleCampaignInput['bidStrategy'])}>
                <option value="MANUAL_CPC">Manual CPC</option>
                <option value="MAXIMIZE_CLICKS">Maximize clicks</option>
                <option value="MAXIMIZE_CONVERSIONS">Maximize conversions</option>
                <option value="MAXIMIZE_CONVERSION_VALUE">Maximize conversion value</option>
              </select>
            </label>

            <label className="block text-xs font-semibold text-slate-700">
              Start date
              <input className="form-input mt-2" type="date" value={startDate} onChange={(event) => setStartDate(event.target.value)} />
            </label>

            <label className="block text-xs font-semibold text-slate-700">
              End date
              <input className="form-input mt-2" type="date" value={endDate} onChange={(event) => setEndDate(event.target.value)} />
            </label>
          </div>

          <div className="rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-xs leading-5 text-amber-800">
            Reachlyo creates the campaign and its Google campaign budget in PAUSED state. Add an ad group, keywords, and an ad before enabling delivery.
          </div>

          <div className="flex justify-end gap-2 border-t border-slate-100 pt-5">
            <button type="button" className="secondary-button" onClick={onClose} disabled={saving}>Cancel</button>
            <button type="submit" className="primary-button" disabled={saving || !name.trim() || !budget}>
              {saving ? 'Creating…' : 'Create paused campaign'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}
