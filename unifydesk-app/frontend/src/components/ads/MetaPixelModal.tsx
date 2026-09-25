import { useState, type FormEvent } from 'react'
import { FiX } from 'react-icons/fi'

type Props = {
  open: boolean
  saving: boolean
  onClose: () => void
  onCreate: (name: string) => Promise<void>
}

export default function MetaPixelModal({ open, saving, onClose, onCreate }: Props) {
  const [name, setName] = useState('')

  if (!open) return null

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    await onCreate(name.trim())
  }

  return (
    <div className="mobile-modal-shell fixed inset-0 z-50 flex items-center justify-center bg-slate-950/40 p-4">
      <div className="mobile-sheet w-full max-w-lg rounded-2xl bg-white shadow-2xl">
        <div className="flex items-start justify-between border-b border-slate-100 px-6 py-5">
          <div>
            <p className="eyebrow">Meta Ads</p>
            <h2 className="mt-1 text-xl font-bold text-slate-950">Create pixel</h2>
            <p className="mt-1 text-sm text-slate-500">
              Create a Meta Pixel in the selected ad account.
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
            Pixel name
            <input
              className="form-input mt-2"
              value={name}
              maxLength={255}
              required
              autoFocus
              placeholder="Website Pixel"
              onChange={(event) => setName(event.target.value)}
            />
          </label>

          <div className="rounded-xl border border-slate-200 bg-slate-50 px-4 py-3 text-xs leading-5 text-slate-600">
            After creation, the pixel is synchronized into Ad Center and becomes available for
            website audiences and custom conversions.
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
              className="primary-button"
              disabled={saving || !name.trim()}
            >
              {saving ? 'Creating…' : 'Create pixel'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}
