import { useMemo, useState, type FormEvent } from 'react'
import { FiX } from 'react-icons/fi'
import type { AdCreative, CreateMetaAdInput } from '../../features/ads/types'

type Props = {
  creatives: AdCreative[]
  open: boolean
  saving: boolean
  onClose: () => void
  onCreate: (input: CreateMetaAdInput) => Promise<void>
}

export default function MetaAdModal({ creatives, open, saving, onClose, onCreate }: Props) {
  const availableCreatives = useMemo(
    () => creatives.filter((creative) => creative.accessible),
    [creatives],
  )
  const [name, setName] = useState('')
  const [creativeId, setCreativeId] = useState('')

  if (!open) return null

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    await onCreate({ name: name.trim(), creativeId })
  }

  return (
    <div className="mobile-modal-shell fixed inset-0 z-50 flex items-center justify-center bg-slate-950/40 p-4">
      <div className="mobile-sheet w-full max-w-xl rounded-2xl bg-white shadow-2xl">
        <div className="flex items-start justify-between border-b border-slate-100 px-6 py-5">
          <div>
            <p className="eyebrow">Meta ad</p>
            <h2 className="mt-1 text-xl font-bold text-slate-950">Create ad</h2>
            <p className="mt-1 text-sm text-slate-500">
              Choose a reusable creative for this ad set.
            </p>
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
            Ad name
            <input
              className="form-input mt-2"
              value={name}
              maxLength={255}
              required
              autoFocus
              onChange={(event) => setName(event.target.value)}
            />
          </label>

          <label className="block text-xs font-semibold text-slate-700">
            Creative
            <select
              className="form-input mt-2"
              value={creativeId}
              required
              onChange={(event) => setCreativeId(event.target.value)}
            >
              <option value="">Choose creative</option>
              {availableCreatives.map((creative) => (
                <option key={creative.id} value={creative.id}>
                  {creative.name} · {creative.creativeFormat ?? 'Creative'}
                </option>
              ))}
            </select>
          </label>

          {availableCreatives.length === 0 && (
            <div className="rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-xs leading-5 text-amber-800">
              No reusable creative is available for this account. Create or sync one in Ad Center →
              Creatives first.
            </div>
          )}

          <div className="rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-xs leading-5 text-amber-800">
            The ad is created PAUSED. Delivery starts only after you explicitly activate it.
          </div>

          <div className="flex justify-end gap-2 border-t border-slate-100 pt-5">
            <button type="button" className="secondary-button" onClick={onClose} disabled={saving}>
              Cancel
            </button>
            <button
              type="submit"
              className="primary-button"
              disabled={saving || !name.trim() || !creativeId}
            >
              {saving ? 'Creating…' : 'Create paused ad'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}
