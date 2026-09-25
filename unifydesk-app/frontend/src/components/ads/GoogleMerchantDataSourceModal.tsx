import { useState, type FormEvent } from 'react'
import { FiX } from 'react-icons/fi'
import type { CreateGoogleMerchantDataSourceInput } from '../../features/ads/types'

type Props = {
  open: boolean
  saving: boolean
  onClose: () => void
  onCreate: (input: CreateGoogleMerchantDataSourceInput) => Promise<void>
}

export default function GoogleMerchantDataSourceModal({
  open,
  saving,
  onClose,
  onCreate,
}: Props) {
  const [displayName, setDisplayName] = useState('Reachlyo API products')
  const [contentLanguage, setContentLanguage] = useState('en')
  const [feedLabel, setFeedLabel] = useState('IN')
  const [countries, setCountries] = useState('IN')

  if (!open) return null

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    await onCreate({
      displayName: displayName.trim(),
      contentLanguage: contentLanguage.trim().toLowerCase(),
      feedLabel: feedLabel.trim().toUpperCase(),
      countries: countries
        .split(/[\s,]+/)
        .map((value) => value.trim().toUpperCase())
        .filter(Boolean),
    })
  }

  return (
    <div className="mobile-modal-shell fixed inset-0 z-50 flex items-center justify-center bg-slate-950/40 p-4">
      <form
        className="mobile-sheet w-full max-w-lg rounded-2xl bg-white p-6 shadow-2xl"
        onSubmit={submit}
      >
        <div className="flex items-start justify-between">
          <div>
            <p className="eyebrow">Merchant Center</p>
            <h2 className="mt-1 text-xl font-bold text-slate-950">
              Create API data source
            </h2>
          </div>
          <button
            type="button"
            className="rounded-lg p-2 text-slate-400"
            onClick={onClose}
          >
            <FiX aria-hidden="true" />
          </button>
        </div>

        <div className="mt-5 space-y-4">
          <label className="block text-xs font-semibold text-slate-700">
            Display name
            <input
              className="form-input mt-2"
              value={displayName}
              required
              onChange={(event) => setDisplayName(event.target.value)}
            />
          </label>
          <div className="grid gap-4 sm:grid-cols-2">
            <label className="text-xs font-semibold text-slate-700">
              Content language
              <input
                className="form-input mt-2"
                value={contentLanguage}
                maxLength={3}
                required
                onChange={(event) => setContentLanguage(event.target.value)}
              />
            </label>
            <label className="text-xs font-semibold text-slate-700">
              Feed label
              <input
                className="form-input mt-2"
                value={feedLabel}
                maxLength={20}
                required
                onChange={(event) => setFeedLabel(event.target.value)}
              />
            </label>
          </div>
          <label className="block text-xs font-semibold text-slate-700">
            Countries
            <input
              className="form-input mt-2"
              value={countries}
              placeholder="IN, US"
              required
              onChange={(event) => setCountries(event.target.value)}
            />
            <span className="mt-1 block font-normal text-slate-400">
              Two-letter country codes separated by commas or spaces.
            </span>
          </label>
        </div>

        <div className="mt-6 flex justify-end gap-2 border-t border-slate-100 pt-5">
          <button type="button" className="secondary-button" onClick={onClose}>
            Cancel
          </button>
          <button type="submit" className="primary-button" disabled={saving}>
            {saving ? 'Creating…' : 'Create API source'}
          </button>
        </div>
      </form>
    </div>
  )
}
