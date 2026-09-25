import { useMemo, useState, type FormEvent } from 'react'
import { FiX } from 'react-icons/fi'
import type {
  AdAccountResource,
  CreateMetaConversionInput,
} from '../../features/ads/types'

type Props = {
  resources: AdAccountResource[]
  open: boolean
  saving: boolean
  onClose: () => void
  onCreate: (input: CreateMetaConversionInput) => Promise<void>
}

const eventTypes = [
  'PURCHASE',
  'LEAD',
  'COMPLETE_REGISTRATION',
  'ADD_TO_CART',
  'ADD_PAYMENT_INFO',
  'ADD_TO_WISHLIST',
  'CONTACT',
  'CONTENT_VIEW',
  'INITIATED_CHECKOUT',
  'SEARCH',
  'START_TRIAL',
  'SUBMIT_APPLICATION',
  'SUBSCRIBE',
  'OTHER',
]

const label = (value: string) =>
  value.replaceAll('_', ' ').toLowerCase().replace(/\b\w/g, (c) => c.toUpperCase())

export default function MetaConversionModal({
  resources,
  open,
  saving,
  onClose,
  onCreate,
}: Props) {
  const pixels = useMemo(
    () =>
      resources.filter(
        (resource) => resource.resourceType === 'META_PIXEL' && resource.accessible,
      ),
    [resources],
  )

  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
  const [eventType, setEventType] = useState('PURCHASE')
  const [pixelId, setPixelId] = useState('')
  const [ruleJson, setRuleJson] = useState('{"url":{"i_contains":"/thank-you"}}')
  const [defaultValue, setDefaultValue] = useState('')

  if (!open) return null

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    const parsedValue = defaultValue.trim() ? Number(defaultValue) : null
    await onCreate({
      name: name.trim(),
      description: description.trim() || null,
      customEventType: eventType,
      pixelId,
      ruleJson: ruleJson.trim(),
      defaultConversionValue:
        parsedValue != null && Number.isFinite(parsedValue) ? parsedValue : null,
    })
  }

  return (
    <div className="mobile-modal-shell fixed inset-0 z-50 flex items-center justify-center bg-slate-950/40 p-4">
      <div className="mobile-sheet max-h-[92vh] w-full max-w-2xl overflow-y-auto rounded-2xl bg-white shadow-2xl">
        <div className="sticky top-0 z-10 flex items-start justify-between border-b border-slate-100 bg-white px-6 py-5">
          <div>
            <p className="eyebrow">Meta Ads</p>
            <h2 className="mt-1 text-xl font-bold text-slate-950">
              Create custom conversion
            </h2>
            <p className="mt-1 text-sm text-slate-500">
              Define a conversion from an existing Meta pixel and event rule.
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
            Conversion name
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
            Description
            <input
              className="form-input mt-2"
              value={description}
              onChange={(event) => setDescription(event.target.value)}
            />
          </label>

          <div className="grid gap-4 sm:grid-cols-2">
            <label className="block text-xs font-semibold text-slate-700">
              Pixel
              <select
                className="form-input mt-2"
                value={pixelId}
                required
                onChange={(event) => setPixelId(event.target.value)}
              >
                <option value="">Choose pixel</option>
                {pixels.map((pixel) => (
                  <option key={pixel.id} value={pixel.externalResourceId}>
                    {pixel.name || pixel.externalResourceId}
                  </option>
                ))}
              </select>
            </label>

            <label className="block text-xs font-semibold text-slate-700">
              Event category
              <select
                className="form-input mt-2"
                value={eventType}
                onChange={(event) => setEventType(event.target.value)}
              >
                {eventTypes.map((value) => (
                  <option key={value} value={value}>
                    {label(value)}
                  </option>
                ))}
              </select>
            </label>
          </div>

          <label className="block text-xs font-semibold text-slate-700">
            Conversion rule JSON
            <textarea
              className="form-input mt-2 min-h-32 font-mono text-xs"
              value={ruleJson}
              required
              spellCheck={false}
              onChange={(event) => setRuleJson(event.target.value)}
            />
            <span className="mt-2 block font-normal leading-5 text-slate-400">
              Define the URL/event rule that determines when the pixel event counts as this
              custom conversion.
            </span>
          </label>

          <label className="block text-xs font-semibold text-slate-700">
            Default conversion value
            <input
              className="form-input mt-2"
              type="number"
              min="0"
              step="0.01"
              value={defaultValue}
              placeholder="Optional"
              onChange={(event) => setDefaultValue(event.target.value)}
            />
          </label>

          {pixels.length === 0 && (
            <div className="rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-xs leading-5 text-amber-800">
              No accessible pixel is synced for this account. Sync Meta resources before creating
              a custom conversion.
            </div>
          )}

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
              disabled={saving || !name.trim() || !pixelId || !ruleJson.trim()}
            >
              {saving ? 'Creating…' : 'Create conversion'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}
