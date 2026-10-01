import { useMemo, useState, type FormEvent } from 'react'
import { FiX } from 'react-icons/fi'
import type {
  AdAccountResource,
  AdAudience,
  CreateMetaAudienceInput,
} from '../../features/ads/types'

type Props = {
  audiences: AdAudience[]
  resources: AdAccountResource[]
  open: boolean
  saving: boolean
  onClose: () => void
  onCreate: (input: CreateMetaAudienceInput) => Promise<void>
}

export default function MetaAudienceModal({
  audiences,
  resources,
  open,
  saving,
  onClose,
  onCreate,
}: Props) {
  const pixels = useMemo(
    () =>
      resources.filter((resource) => resource.resourceType === 'META_PIXEL' && resource.accessible),
    [resources],
  )
  const sourceAudiences = useMemo(
    () => audiences.filter((audience) => audience.accessible),
    [audiences],
  )

  const [name, setName] = useState('')
  const [type, setType] = useState<'WEBSITE' | 'LOOKALIKE' | 'CUSTOMER_LIST'>('WEBSITE')
  const [description, setDescription] = useState('')
  const [pixelId, setPixelId] = useState('')
  const [ruleJson, setRuleJson] = useState('{"url":{"i_contains":""}}')
  const [retentionDays, setRetentionDays] = useState('30')
  const [originAudienceId, setOriginAudienceId] = useState('')
  const [country, setCountry] = useState('IN')
  const [ratioPercent, setRatioPercent] = useState('1')
  const [customerFileSource, setCustomerFileSource] = useState<
    'USER_PROVIDED_ONLY' | 'PARTNER_PROVIDED_ONLY' | 'BOTH_USER_AND_PARTNER_PROVIDED'
  >('USER_PROVIDED_ONLY')

  if (!open) return null

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    await onCreate({
      name: name.trim(),
      type,
      description: description.trim() || null,
      pixelId: type === 'WEBSITE' ? pixelId : null,
      ruleJson: type === 'WEBSITE' ? ruleJson.trim() : null,
      retentionDays: type === 'WEBSITE' ? Number(retentionDays || 30) : null,
      originAudienceId: type === 'LOOKALIKE' ? originAudienceId : null,
      country: type === 'LOOKALIKE' ? country.trim().toUpperCase() : null,
      ratio: type === 'LOOKALIKE' ? Number(ratioPercent || 1) / 100 : null,
      customerFileSource: type === 'CUSTOMER_LIST' ? customerFileSource : null,
    })
  }

  return (
    <div className="mobile-modal-shell fixed inset-0 z-50 flex items-center justify-center bg-slate-950/40 p-4">
      <div className="mobile-sheet max-h-[92vh] w-full max-w-2xl overflow-y-auto rounded-2xl bg-white shadow-2xl">
        <div className="sticky top-0 z-10 flex items-start justify-between border-b border-slate-100 bg-white px-6 py-5">
          <div>
            <p className="eyebrow">Meta Ads</p>
            <h2 className="mt-1 text-xl font-bold text-slate-950">Create audience</h2>
            <p className="mt-1 text-sm text-slate-500">
              Create a website audience, customer-list Custom Audience, or lookalike.
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
            Audience name
            <input
              className="form-input mt-2"
              value={name}
              maxLength={255}
              required
              autoFocus
              onChange={(event) => setName(event.target.value)}
            />
          </label>

          <div className="grid gap-4 sm:grid-cols-2">
            <label className="block text-xs font-semibold text-slate-700">
              Audience type
              <select
                className="form-input mt-2"
                value={type}
                onChange={(event) =>
                  setType(event.target.value as 'WEBSITE' | 'LOOKALIKE' | 'CUSTOMER_LIST')
                }
              >
                <option value="WEBSITE">Website custom audience</option>
                <option value="CUSTOMER_LIST">Customer-list Custom Audience</option>
                <option value="LOOKALIKE">Lookalike audience</option>
              </select>
            </label>

            <label className="block text-xs font-semibold text-slate-700">
              Description
              <input
                className="form-input mt-2"
                value={description}
                onChange={(event) => setDescription(event.target.value)}
              />
            </label>
          </div>

          {type === 'WEBSITE' ? (
            <>
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
                  Retention days
                  <input
                    className="form-input mt-2"
                    type="number"
                    min="1"
                    max="180"
                    value={retentionDays}
                    required
                    onChange={(event) => setRetentionDays(event.target.value)}
                  />
                </label>
              </div>

              <label className="block text-xs font-semibold text-slate-700">
                Meta audience rule JSON
                <textarea
                  className="form-input mt-2 min-h-32 font-mono text-xs"
                  value={ruleJson}
                  required
                  spellCheck={false}
                  onChange={(event) => setRuleJson(event.target.value)}
                />
                <span className="mt-2 block font-normal leading-5 text-slate-400">
                  The default rule includes all website URLs. Advanced URL/event rules can be
                  entered using Meta's rule JSON syntax.
                </span>
              </label>

              {pixels.length === 0 && (
                <div className="rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-xs leading-5 text-amber-800">
                  No accessible pixel is synced for this ad account. Sync account resources first.
                </div>
              )}
            </>
          ) : type === 'CUSTOMER_LIST' ? (
            <div className="space-y-4">
              <label className="block text-xs font-semibold text-slate-700">
                Customer file source
                <select
                  className="form-input mt-2"
                  value={customerFileSource}
                  onChange={(event) =>
                    setCustomerFileSource(
                      event.target.value as
                        | 'USER_PROVIDED_ONLY'
                        | 'PARTNER_PROVIDED_ONLY'
                        | 'BOTH_USER_AND_PARTNER_PROVIDED',
                    )
                  }
                >
                  <option value="USER_PROVIDED_ONLY">
                    First-party data collected directly by this business
                  </option>
                  <option value="PARTNER_PROVIDED_ONLY">
                    Data provided by an authorized partner
                  </option>
                  <option value="BOTH_USER_AND_PARTNER_PROVIDED">
                    Combination of first-party and authorized partner data
                  </option>
                </select>
              </label>
              <div className="rounded-xl border border-indigo-200 bg-indigo-50 px-4 py-3 text-xs leading-5 text-indigo-800">
                This creates an empty Meta Custom Audience. Add members after creation using the
                Upload members action. Customer identifiers are not stored by UnifyDesk.
              </div>
            </div>
          ) : (
            <div className="grid gap-4 sm:grid-cols-3">
              <label className="block text-xs font-semibold text-slate-700 sm:col-span-3">
                Source audience
                <select
                  className="form-input mt-2"
                  value={originAudienceId}
                  required
                  onChange={(event) => setOriginAudienceId(event.target.value)}
                >
                  <option value="">Choose source audience</option>
                  {sourceAudiences.map((audience) => (
                    <option key={audience.id} value={audience.id}>
                      {audience.name} · {audience.subtype ?? 'Audience'}
                    </option>
                  ))}
                </select>
              </label>

              <label className="block text-xs font-semibold text-slate-700">
                Country
                <input
                  className="form-input mt-2"
                  value={country}
                  maxLength={2}
                  required
                  onChange={(event) => setCountry(event.target.value)}
                />
              </label>

              <label className="block text-xs font-semibold text-slate-700 sm:col-span-2">
                Audience size
                <div className="mt-2 flex items-center gap-3">
                  <input
                    className="form-input"
                    type="range"
                    min="1"
                    max="20"
                    step="1"
                    value={ratioPercent}
                    onChange={(event) => setRatioPercent(event.target.value)}
                  />
                  <span className="w-12 text-sm font-bold text-slate-900">{ratioPercent}%</span>
                </div>
              </label>
            </div>
          )}

          <div className="flex justify-end gap-2 border-t border-slate-100 pt-5">
            <button type="button" className="secondary-button" onClick={onClose} disabled={saving}>
              Cancel
            </button>
            <button
              type="submit"
              className="primary-button"
              disabled={
                saving ||
                !name.trim() ||
                (type === 'WEBSITE' && (!pixelId || !ruleJson.trim())) ||
                (type === 'LOOKALIKE' && (!originAudienceId || !country.trim() || !ratioPercent)) ||
                (type === 'CUSTOMER_LIST' && !customerFileSource)
              }
            >
              {saving ? 'Creating…' : 'Create audience'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}
