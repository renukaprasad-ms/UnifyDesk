import { useState, type FormEvent } from 'react'
import { FiX } from 'react-icons/fi'
import type { AdAccount, CreateGoogleKeywordInput } from '../../features/ads/types'

type Props = {
  account: AdAccount
  open: boolean
  saving: boolean
  onClose: () => void
  onCreate: (input: CreateGoogleKeywordInput) => Promise<void>
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

export default function GoogleKeywordModal({ account, open, saving, onClose, onCreate }: Props) {
  const [text, setText] = useState('')
  const [matchType, setMatchType] = useState<CreateGoogleKeywordInput['matchType']>('BROAD')
  const [cpcBid, setCpcBid] = useState('')
  const [negative, setNegative] = useState(false)

  if (!open) return null

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    await onCreate({
      text: text.trim(),
      matchType,
      cpcBidMinor: negative ? null : toMinor(cpcBid, account.currency),
      negative,
    })
  }

  return (
    <div className="mobile-modal-shell fixed inset-0 z-50 flex items-center justify-center bg-slate-950/40 p-4">
      <div className="mobile-sheet w-full max-w-xl rounded-2xl bg-white shadow-2xl">
        <div className="flex items-start justify-between border-b border-slate-100 px-6 py-5">
          <div><p className="eyebrow">Google Ads</p><h2 className="mt-1 text-xl font-bold text-slate-950">Add keyword</h2></div>
          <button type="button" className="rounded-lg p-2 text-slate-400 hover:bg-slate-100" onClick={onClose}><FiX size={18} /></button>
        </div>
        <form className="space-y-5 p-6" onSubmit={submit}>
          <label className="block text-xs font-semibold text-slate-700">
            Keyword
            <input className="form-input mt-2" value={text} maxLength={500} required autoFocus onChange={(e) => setText(e.target.value)} />
          </label>
          <div className="grid gap-4 sm:grid-cols-2">
            <label className="block text-xs font-semibold text-slate-700">
              Match type
              <select className="form-input mt-2" value={matchType} onChange={(e) => setMatchType(e.target.value as CreateGoogleKeywordInput['matchType'])}>
                <option value="BROAD">Broad</option><option value="PHRASE">Phrase</option><option value="EXACT">Exact</option>
              </select>
            </label>
            <label className="block text-xs font-semibold text-slate-700">
              CPC bid {account.currency ? `(${account.currency})` : ''}
              <input className="form-input mt-2" type="number" min="0.01" step="0.01" disabled={negative} value={cpcBid} placeholder="Optional" onChange={(e) => setCpcBid(e.target.value)} />
            </label>
          </div>
          <label className="flex items-center gap-3 text-sm font-medium text-slate-700">
            <input type="checkbox" checked={negative} onChange={(e) => setNegative(e.target.checked)} />
            Negative keyword
          </label>
          <div className="flex justify-end gap-2 border-t border-slate-100 pt-5">
            <button type="button" className="secondary-button" onClick={onClose} disabled={saving}>Cancel</button>
            <button type="submit" className="primary-button" disabled={saving || !text.trim()}>{saving ? 'Adding…' : 'Add keyword'}</button>
          </div>
        </form>
      </div>
    </div>
  )
}
