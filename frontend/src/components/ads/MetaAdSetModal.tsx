import { useEffect, useState, type FormEvent } from 'react'
import { FiX } from 'react-icons/fi'
import type {
  AdAccount,
  AdAccountResource,
  AdCampaign,
  CreateMetaAdSetInput,
} from '../../features/ads/types'

type Props = {
  account: AdAccount
  campaign: AdCampaign
  resources: AdAccountResource[]
  open: boolean
  saving: boolean
  onClose: () => void
  onCreate: (input: CreateMetaAdSetInput) => Promise<void>
}

const toMinor = (value: string, currency?: string | null) => {
  const numeric = Number(value)
  if (!Number.isFinite(numeric) || numeric <= 0) return null
  if (!currency) return Math.round(numeric)
  try {
    const digits = new Intl.NumberFormat(undefined, { style: 'currency', currency })
      .resolvedOptions()
      .maximumFractionDigits
    return Math.round(numeric * 10 ** digits)
  } catch {
    return Math.round(numeric)
  }
}

const csv = (value: string) =>
  value
    .split(',')
    .map((item) => item.trim())
    .filter(Boolean)

export default function MetaAdSetModal({
  account,
  campaign,
  resources,
  open,
  saving,
  onClose,
  onCreate,
}: Props) {
  const campaignHasBudget =
    campaign.dailyBudgetMinor != null || campaign.lifetimeBudgetMinor != null
  const pixels = resources.filter(
    (resource) => resource.resourceType === 'META_PIXEL' && resource.accessible,
  )

  const [name, setName] = useState('')
  const [optimizationGoal, setOptimizationGoal] = useState('LINK_CLICKS')
  const [billingEvent, setBillingEvent] = useState('IMPRESSIONS')
  const [bidStrategy, setBidStrategy] = useState('LOWEST_COST_WITHOUT_CAP')
  const [bidAmount, setBidAmount] = useState('')
  const [budgetType, setBudgetType] = useState<'NONE' | 'DAILY' | 'LIFETIME'>(
    campaignHasBudget ? 'NONE' : 'DAILY',
  )
  const [budget, setBudget] = useState('')
  const [countries, setCountries] = useState('IN')
  const [ageMin, setAgeMin] = useState('18')
  const [ageMax, setAgeMax] = useState('65')
  const [gender, setGender] = useState<'ALL' | 'MEN' | 'WOMEN'>('ALL')
  const [placementMode, setPlacementMode] = useState<'AUTO' | 'MANUAL'>('AUTO')
  const [facebook, setFacebook] = useState(true)
  const [instagram, setInstagram] = useState(true)
  const [messenger, setMessenger] = useState(false)
  const [audienceNetwork, setAudienceNetwork] = useState(false)
  const [destinationType, setDestinationType] = useState('')
  const [startAt, setStartAt] = useState('')
  const [endAt, setEndAt] = useState('')
  const [pixelId, setPixelId] = useState('')
  const [customEventType, setCustomEventType] = useState('')
  const [customAudiences, setCustomAudiences] = useState('')
  const [excludedAudiences, setExcludedAudiences] = useState('')

  useEffect(() => {
    setBudgetType(campaignHasBudget ? 'NONE' : 'DAILY')
  }, [campaignHasBudget])

  if (!open) return null

  const submit = async (event: FormEvent) => {
    event.preventDefault()

    const publisherPlatforms =
      placementMode === 'AUTO'
        ? []
        : [
            ...(facebook ? ['facebook'] : []),
            ...(instagram ? ['instagram'] : []),
            ...(messenger ? ['messenger'] : []),
            ...(audienceNetwork ? ['audience_network'] : []),
          ]

    await onCreate({
      name: name.trim(),
      optimizationGoal,
      billingEvent,
      bidStrategy: bidStrategy || null,
      bidAmountMinor: toMinor(bidAmount, account.currency),
      dailyBudgetMinor:
        budgetType === 'DAILY' ? toMinor(budget, account.currency) : null,
      lifetimeBudgetMinor:
        budgetType === 'LIFETIME' ? toMinor(budget, account.currency) : null,
      startAt: startAt ? new Date(startAt).toISOString() : null,
      endAt: endAt ? new Date(endAt).toISOString() : null,
      destinationType: destinationType || null,
      locations: csv(countries).map((countryCode) => ({
        countryCode,
        locationTypes: ['home', 'recent'],
      })),
      ageMin: Number(ageMin),
      ageMax: Number(ageMax),
      genders:
        gender === 'ALL' ? [] : gender === 'MEN' ? [1] : [2],
      publisherPlatforms,
      customAudienceIds: csv(customAudiences),
      excludedCustomAudienceIds: csv(excludedAudiences),
      pixelId: pixelId || null,
      customEventType: customEventType || null,
    })
  }

  return (
    <div className="mobile-modal-shell fixed inset-0 z-50 flex items-center justify-center bg-slate-950/40 p-4">
      <div className="mobile-sheet max-h-[92vh] w-full max-w-3xl overflow-y-auto rounded-2xl bg-white shadow-2xl">
        <div className="sticky top-0 z-10 flex items-start justify-between border-b border-slate-100 bg-white px-6 py-5">
          <div>
            <p className="eyebrow">Meta campaign</p>
            <h2 className="mt-1 text-xl font-bold text-slate-950">Create ad set</h2>
            <p className="mt-1 text-sm text-slate-500">{campaign.name}</p>
          </div>
          <button type="button" className="rounded-lg p-2 text-slate-400 hover:bg-slate-100" onClick={onClose}>
            <FiX size={18} aria-hidden="true" />
          </button>
        </div>

        <form className="space-y-6 p-6" onSubmit={submit}>
          <section>
            <h3 className="text-sm font-bold text-slate-950">Delivery</h3>
            <div className="mt-4 grid gap-4 sm:grid-cols-2">
              <label className="block text-xs font-semibold text-slate-700 sm:col-span-2">
                Ad set name
                <input className="form-input mt-2" value={name} maxLength={255} required autoFocus onChange={(e) => setName(e.target.value)} />
              </label>

              <label className="block text-xs font-semibold text-slate-700">
                Optimization goal
                <select className="form-input mt-2" value={optimizationGoal} onChange={(e) => setOptimizationGoal(e.target.value)}>
                  <option value="LINK_CLICKS">Link clicks</option>
                  <option value="LANDING_PAGE_VIEWS">Landing page views</option>
                  <option value="IMPRESSIONS">Impressions</option>
                  <option value="REACH">Reach</option>
                  <option value="LEAD_GENERATION">Leads</option>
                  <option value="OFFSITE_CONVERSIONS">Conversions</option>
                  <option value="VALUE">Conversion value</option>
                  <option value="THRUPLAY">ThruPlay</option>
                </select>
              </label>

              <label className="block text-xs font-semibold text-slate-700">
                Billing event
                <select className="form-input mt-2" value={billingEvent} onChange={(e) => setBillingEvent(e.target.value)}>
                  <option value="IMPRESSIONS">Impressions</option>
                  <option value="LINK_CLICKS">Link clicks</option>
                  <option value="THRUPLAY">ThruPlay</option>
                </select>
              </label>

              <label className="block text-xs font-semibold text-slate-700">
                Bid strategy
                <select className="form-input mt-2" value={bidStrategy} onChange={(e) => setBidStrategy(e.target.value)}>
                  <option value="LOWEST_COST_WITHOUT_CAP">Lowest cost</option>
                  <option value="LOWEST_COST_WITH_BID_CAP">Bid cap</option>
                  <option value="COST_CAP">Cost cap</option>
                </select>
              </label>

              <label className="block text-xs font-semibold text-slate-700">
                Bid amount {account.currency ? `(${account.currency})` : ''}
                <input className="form-input mt-2" type="number" min="0.01" step="0.01" value={bidAmount} disabled={bidStrategy === 'LOWEST_COST_WITHOUT_CAP'} onChange={(e) => setBidAmount(e.target.value)} />
              </label>

              <label className="block text-xs font-semibold text-slate-700">
                Destination
                <select className="form-input mt-2" value={destinationType} onChange={(e) => setDestinationType(e.target.value)}>
                  <option value="">Meta default</option>
                  <option value="WEBSITE">Website</option>
                  <option value="APP">App</option>
                  <option value="MESSENGER">Messenger</option>
                  <option value="WHATSAPP">WhatsApp</option>
                  <option value="ON_AD">On ad</option>
                </select>
              </label>
            </div>
          </section>

          <section className="border-t border-slate-100 pt-6">
            <h3 className="text-sm font-bold text-slate-950">Budget & schedule</h3>
            <div className="mt-4 grid gap-4 sm:grid-cols-2">
              <label className="block text-xs font-semibold text-slate-700">
                Budget source
                <select className="form-input mt-2" value={budgetType} onChange={(e) => setBudgetType(e.target.value as 'NONE' | 'DAILY' | 'LIFETIME')}>
                  {campaignHasBudget && <option value="NONE">Use campaign budget</option>}
                  <option value="DAILY">Daily ad-set budget</option>
                  <option value="LIFETIME">Lifetime ad-set budget</option>
                </select>
              </label>

              <label className="block text-xs font-semibold text-slate-700">
                Amount {account.currency ? `(${account.currency})` : ''}
                <input className="form-input mt-2" type="number" min="0.01" step="0.01" value={budget} disabled={budgetType === 'NONE'} required={budgetType !== 'NONE'} onChange={(e) => setBudget(e.target.value)} />
              </label>

              <label className="block text-xs font-semibold text-slate-700">
                Start
                <input className="form-input mt-2" type="datetime-local" value={startAt} onChange={(e) => setStartAt(e.target.value)} />
              </label>

              <label className="block text-xs font-semibold text-slate-700">
                End
                <input className="form-input mt-2" type="datetime-local" value={endAt} onChange={(e) => setEndAt(e.target.value)} />
              </label>
            </div>
          </section>

          <section className="border-t border-slate-100 pt-6">
            <h3 className="text-sm font-bold text-slate-950">Audience</h3>
            <div className="mt-4 grid gap-4 sm:grid-cols-2">
              <label className="block text-xs font-semibold text-slate-700 sm:col-span-2">
                Countries
                <input className="form-input mt-2" value={countries} required placeholder="IN, US" onChange={(e) => setCountries(e.target.value)} />
                <span className="mt-1 block font-normal text-slate-400">ISO country codes separated by commas.</span>
              </label>

              <label className="block text-xs font-semibold text-slate-700">
                Minimum age
                <input className="form-input mt-2" type="number" min="13" max="65" value={ageMin} onChange={(e) => setAgeMin(e.target.value)} />
              </label>
              <label className="block text-xs font-semibold text-slate-700">
                Maximum age
                <input className="form-input mt-2" type="number" min="13" max="65" value={ageMax} onChange={(e) => setAgeMax(e.target.value)} />
              </label>

              <label className="block text-xs font-semibold text-slate-700">
                Gender
                <select className="form-input mt-2" value={gender} onChange={(e) => setGender(e.target.value as 'ALL' | 'MEN' | 'WOMEN')}>
                  <option value="ALL">All</option>
                  <option value="MEN">Men</option>
                  <option value="WOMEN">Women</option>
                </select>
              </label>

              <label className="block text-xs font-semibold text-slate-700">
                Custom audience IDs
                <input className="form-input mt-2" value={customAudiences} placeholder="123, 456" onChange={(e) => setCustomAudiences(e.target.value)} />
              </label>

              <label className="block text-xs font-semibold text-slate-700 sm:col-span-2">
                Excluded custom audience IDs
                <input className="form-input mt-2" value={excludedAudiences} placeholder="789" onChange={(e) => setExcludedAudiences(e.target.value)} />
              </label>
            </div>
          </section>

          <section className="border-t border-slate-100 pt-6">
            <h3 className="text-sm font-bold text-slate-950">Placements</h3>
            <label className="mt-4 block text-xs font-semibold text-slate-700">
              Placement mode
              <select className="form-input mt-2" value={placementMode} onChange={(e) => setPlacementMode(e.target.value as 'AUTO' | 'MANUAL')}>
                <option value="AUTO">Advantage+ / automatic placements</option>
                <option value="MANUAL">Manual placements</option>
              </select>
            </label>

            {placementMode === 'MANUAL' && (
              <div className="mt-3 grid gap-2 sm:grid-cols-2">
                <label className="flex items-center gap-3 rounded-xl border border-slate-200 px-4 py-3 text-sm font-medium text-slate-700"><input type="checkbox" checked={facebook} onChange={(e) => setFacebook(e.target.checked)} />Facebook</label>
                <label className="flex items-center gap-3 rounded-xl border border-slate-200 px-4 py-3 text-sm font-medium text-slate-700"><input type="checkbox" checked={instagram} onChange={(e) => setInstagram(e.target.checked)} />Instagram</label>
                <label className="flex items-center gap-3 rounded-xl border border-slate-200 px-4 py-3 text-sm font-medium text-slate-700"><input type="checkbox" checked={messenger} onChange={(e) => setMessenger(e.target.checked)} />Messenger</label>
                <label className="flex items-center gap-3 rounded-xl border border-slate-200 px-4 py-3 text-sm font-medium text-slate-700"><input type="checkbox" checked={audienceNetwork} onChange={(e) => setAudienceNetwork(e.target.checked)} />Audience Network</label>
              </div>
            )}
          </section>

          <section className="border-t border-slate-100 pt-6">
            <h3 className="text-sm font-bold text-slate-950">Conversion</h3>
            <div className="mt-4 grid gap-4 sm:grid-cols-2">
              <label className="block text-xs font-semibold text-slate-700">
                Pixel
                <select className="form-input mt-2" value={pixelId} onChange={(e) => setPixelId(e.target.value)}>
                  <option value="">No pixel selected</option>
                  {pixels.map((pixel) => (
                    <option key={pixel.id} value={pixel.externalResourceId}>
                      {pixel.name || pixel.externalResourceId}
                    </option>
                  ))}
                </select>
              </label>

              <label className="block text-xs font-semibold text-slate-700">
                Conversion event
                <select className="form-input mt-2" value={customEventType} disabled={!pixelId} onChange={(e) => setCustomEventType(e.target.value)}>
                  <option value="">Meta default</option>
                  <option value="PURCHASE">Purchase</option>
                  <option value="LEAD">Lead</option>
                  <option value="COMPLETE_REGISTRATION">Complete registration</option>
                  <option value="ADD_TO_CART">Add to cart</option>
                  <option value="INITIATED_CHECKOUT">Initiated checkout</option>
                  <option value="VIEW_CONTENT">View content</option>
                </select>
              </label>
            </div>
          </section>

          <div className="rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-xs leading-5 text-amber-800">
            This ad set is created PAUSED. Starting the campaign or ad set never happens implicitly from this form.
          </div>

          <div className="flex justify-end gap-2 border-t border-slate-100 pt-5">
            <button type="button" className="secondary-button" onClick={onClose} disabled={saving}>Cancel</button>
            <button type="submit" className="primary-button" disabled={saving || !name.trim() || !countries.trim() || (budgetType !== 'NONE' && !budget)}>
              {saving ? 'Creating…' : 'Create paused ad set'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}
