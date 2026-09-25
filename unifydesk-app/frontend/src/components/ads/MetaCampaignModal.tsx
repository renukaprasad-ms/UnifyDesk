import { useState, type FormEvent } from 'react'
import { FiX } from 'react-icons/fi'
import type {
  AdAccount,
  CreateMetaCampaignInput,
  MetaCampaignObjective,
} from '../../features/ads/types'

type Props = {
  account: AdAccount
  open: boolean
  saving: boolean
  onClose: () => void
  onCreate: (input: CreateMetaCampaignInput) => Promise<void>
}

const toMinor = (value: string, currency?: string | null) => {
  const numeric = Number(value)
  if (!Number.isFinite(numeric) || numeric <= 0) return null
  if (!currency) return Math.round(numeric)
  try {
    const digits = new Intl.NumberFormat(undefined, { style: 'currency', currency })
      .resolvedOptions()
      .maximumFractionDigits ?? 2
    return Math.round(numeric * 10 ** digits)
  } catch {
    return Math.round(numeric)
  }
}

const objectives: Array<{ value: MetaCampaignObjective; label: string }> = [
  { value: 'OUTCOME_AWARENESS', label: 'Awareness' },
  { value: 'OUTCOME_TRAFFIC', label: 'Traffic' },
  { value: 'OUTCOME_ENGAGEMENT', label: 'Engagement' },
  { value: 'OUTCOME_LEADS', label: 'Leads' },
  { value: 'OUTCOME_APP_PROMOTION', label: 'App promotion' },
  { value: 'OUTCOME_SALES', label: 'Sales' },
]

export default function MetaCampaignModal({
  account,
  open,
  saving,
  onClose,
  onCreate,
}: Props) {
  const [name, setName] = useState('')
  const [objective, setObjective] = useState<MetaCampaignObjective>('OUTCOME_TRAFFIC')
  const [buyingType, setBuyingType] = useState<'AUCTION' | 'RESERVED'>('AUCTION')
  const [bidStrategy, setBidStrategy] = useState('')
  const [budgetType, setBudgetType] = useState<'NONE' | 'DAILY' | 'LIFETIME'>('NONE')
  const [budget, setBudget] = useState('')
  const [specialCategory, setSpecialCategory] = useState('NONE')

  if (!open) return null

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    const minorBudget = toMinor(budget, account.currency)

    await onCreate({
      name: name.trim(),
      objective,
      buyingType,
      bidStrategy: bidStrategy || null,
      dailyBudgetMinor: budgetType === 'DAILY' ? minorBudget : null,
      lifetimeBudgetMinor: budgetType === 'LIFETIME' ? minorBudget : null,
      specialAdCategories: [specialCategory],
    })
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/40 p-4">
      <div className="max-h-[92vh] w-full max-w-2xl overflow-y-auto rounded-2xl bg-white shadow-2xl">
        <div className="flex items-start justify-between border-b border-slate-100 px-6 py-5">
          <div>
            <p className="eyebrow">Meta Ads</p>
            <h2 className="mt-1 text-xl font-bold text-slate-950">Create campaign</h2>
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
            <input
              className="form-input mt-2"
              value={name}
              onChange={(event) => setName(event.target.value)}
              maxLength={255}
              required
              autoFocus
            />
          </label>

          <div className="grid gap-4 sm:grid-cols-2">
            <label className="block text-xs font-semibold text-slate-700">
              Objective
              <select
                className="form-input mt-2"
                value={objective}
                onChange={(event) => setObjective(event.target.value as MetaCampaignObjective)}
              >
                {objectives.map((item) => (
                  <option key={item.value} value={item.value}>
                    {item.label}
                  </option>
                ))}
              </select>
            </label>

            <label className="block text-xs font-semibold text-slate-700">
              Buying type
              <select
                className="form-input mt-2"
                value={buyingType}
                onChange={(event) => setBuyingType(event.target.value as 'AUCTION' | 'RESERVED')}
              >
                <option value="AUCTION">Auction</option>
                <option value="RESERVED">Reserved</option>
              </select>
            </label>
          </div>

          <div className="grid gap-4 sm:grid-cols-2">
            <label className="block text-xs font-semibold text-slate-700">
              Campaign budget
              <select
                className="form-input mt-2"
                value={budgetType}
                onChange={(event) =>
                  setBudgetType(event.target.value as 'NONE' | 'DAILY' | 'LIFETIME')
                }
              >
                <option value="NONE">Budget at ad-set level</option>
                <option value="DAILY">Daily campaign budget</option>
                <option value="LIFETIME">Lifetime campaign budget</option>
              </select>
            </label>

            <label className="block text-xs font-semibold text-slate-700">
              Amount {account.currency ? `(${account.currency})` : ''}
              <input
                className="form-input mt-2"
                type="number"
                min="0.01"
                step="0.01"
                disabled={budgetType === 'NONE'}
                value={budget}
                onChange={(event) => setBudget(event.target.value)}
                required={budgetType !== 'NONE'}
              />
            </label>
          </div>

          <div className="grid gap-4 sm:grid-cols-2">
            <label className="block text-xs font-semibold text-slate-700">
              Bid strategy
              <select
                className="form-input mt-2"
                value={bidStrategy}
                onChange={(event) => setBidStrategy(event.target.value)}
              >
                <option value="">Meta default</option>
                <option value="LOWEST_COST_WITHOUT_CAP">Lowest cost</option>
                <option value="LOWEST_COST_WITH_BID_CAP">Bid cap</option>
                <option value="COST_CAP">Cost cap</option>
              </select>
            </label>

            <label className="block text-xs font-semibold text-slate-700">
              Special ad category
              <select
                className="form-input mt-2"
                value={specialCategory}
                onChange={(event) => setSpecialCategory(event.target.value)}
              >
                <option value="NONE">None</option>
                <option value="CREDIT">Credit</option>
                <option value="EMPLOYMENT">Employment</option>
                <option value="HOUSING">Housing</option>
              </select>
            </label>
          </div>

          <div className="rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-xs leading-5 text-amber-800">
            Creating this campaign does not start delivery. It is sent to Meta with PAUSED status,
            and you must explicitly start it after the ad set and ad are ready.
          </div>

          <div className="flex justify-end gap-2 border-t border-slate-100 pt-5">
            <button type="button" className="secondary-button" onClick={onClose} disabled={saving}>
              Cancel
            </button>
            <button type="submit" className="primary-button" disabled={saving || !name.trim()}>
              {saving ? 'Creating…' : 'Create paused campaign'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}
