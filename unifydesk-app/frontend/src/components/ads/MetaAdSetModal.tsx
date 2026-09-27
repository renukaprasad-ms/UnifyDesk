import { useEffect, useState, type FormEvent } from 'react'
import { FiX } from 'react-icons/fi'
import MetaGeoTargetingEditor from './MetaGeoTargetingEditor'
import type {
  AdAccount,
  AdAccountResource,
  AdAudience,
  AdCampaign,
  AdConversion,
  CreateMetaAdSetInput,
  MetaTargetingLocation,
} from '../../features/ads/types'

type Props = {
  companyId: string
  account: AdAccount
  campaign: AdCampaign
  resources: AdAccountResource[]
  audiences: AdAudience[]
  conversions: AdConversion[]
  open: boolean
  saving: boolean
  onClose: () => void
  onCreate: (input: CreateMetaAdSetInput) => Promise<void>
}

const META_PLACEMENTS = {
  facebook: [
    ['feed', 'Feed'],
    ['story', 'Stories'],
    ['reels', 'Reels'],
    ['marketplace', 'Marketplace'],
    ['video_feeds', 'Video feeds'],
    ['search', 'Search results'],
  ],
  instagram: [
    ['stream', 'Feed'],
    ['story', 'Stories'],
    ['reels', 'Reels'],
    ['explore', 'Explore'],
    ['explore_home', 'Explore home'],
    ['profile_feed', 'Profile feed'],
  ],
  messenger: [
    ['messenger_home', 'Inbox'],
    ['story', 'Stories'],
  ],
  audience_network: [
    ['classic', 'Native, banner and interstitial'],
    ['rewarded_video', 'Rewarded video'],
  ],
} as const

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

export default function MetaAdSetModal({
  companyId,
  account,
  campaign,
  resources,
  audiences,
  conversions,
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
  const [locations, setLocations] = useState<MetaTargetingLocation[]>([
    {
      countryCode: 'IN',
      type: 'COUNTRY',
      key: 'IN',
      name: 'IN',
      locationTypes: ['home', 'recent'],
    },
  ])
  const [ageMin, setAgeMin] = useState('18')
  const [ageMax, setAgeMax] = useState('65')
  const [gender, setGender] = useState<'ALL' | 'MEN' | 'WOMEN'>('ALL')
  const [placementMode, setPlacementMode] = useState<'AUTO' | 'MANUAL'>('AUTO')
  const [facebook, setFacebook] = useState(true)
  const [instagram, setInstagram] = useState(true)
  const [messenger, setMessenger] = useState(false)
  const [audienceNetwork, setAudienceNetwork] = useState(false)
  const [facebookPositions, setFacebookPositions] = useState<string[]>([])
  const [instagramPositions, setInstagramPositions] = useState<string[]>([])
  const [messengerPositions, setMessengerPositions] = useState<string[]>([])
  const [audienceNetworkPositions, setAudienceNetworkPositions] = useState<string[]>([])
  const [destinationType, setDestinationType] = useState('')
  const [startAt, setStartAt] = useState('')
  const [endAt, setEndAt] = useState('')
  const [pixelId, setPixelId] = useState('')
  const [customEventType, setCustomEventType] = useState('')
  const [customConversionId, setCustomConversionId] = useState('')
  const [customAudiences, setCustomAudiences] = useState<string[]>([])
  const [excludedAudiences, setExcludedAudiences] = useState<string[]>([])
  const [advantageAudience, setAdvantageAudience] = useState(false)
  const [advantageAgeSuggestion, setAdvantageAgeSuggestion] = useState(false)
  const [advantageGenderSuggestion, setAdvantageGenderSuggestion] = useState(false)

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
      locations,
      ageMin: Number(ageMin),
      ageMax: Number(ageMax),
      genders:
        gender === 'ALL' ? [] : gender === 'MEN' ? [1] : [2],
      publisherPlatforms,
      facebookPositions: placementMode === 'MANUAL' && facebook ? facebookPositions : [],
      instagramPositions: placementMode === 'MANUAL' && instagram ? instagramPositions : [],
      messengerPositions: placementMode === 'MANUAL' && messenger ? messengerPositions : [],
      audienceNetworkPositions:
        placementMode === 'MANUAL' && audienceNetwork ? audienceNetworkPositions : [],
      customAudienceIds: customAudiences,
      excludedCustomAudienceIds: excludedAudiences,
      advantageAudience,
      advantageAgeSuggestion: advantageAudience ? advantageAgeSuggestion : false,
      advantageGenderSuggestion: advantageAudience ? advantageGenderSuggestion : false,
      pixelId: customConversionId ? null : pixelId || null,
      customEventType: customConversionId ? null : customEventType || null,
      customConversionId: customConversionId || null,
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
                  {campaignHasBudget ? (
                    <option value="NONE">Use campaign budget</option>
                  ) : (
                    <>
                      <option value="DAILY">Daily ad-set budget</option>
                      <option value="LIFETIME">Lifetime ad-set budget</option>
                    </>
                  )}
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
              <MetaGeoTargetingEditor
                companyId={companyId}
                accountId={account.id}
                locations={locations}
                onChange={setLocations}
                disabled={saving}
              />

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

              <div className="sm:col-span-2 rounded-xl border border-indigo-200 bg-indigo-50 p-4">
                <label className="flex items-start gap-3">
                  <input
                    type="checkbox"
                    className="mt-1"
                    checked={advantageAudience}
                    onChange={(event) => setAdvantageAudience(event.target.checked)}
                  />
                  <span>
                    <span className="block text-sm font-bold text-indigo-950">
                      Advantage+ audience
                    </span>
                    <span className="mt-1 block text-xs leading-5 text-indigo-700">
                      Let Meta expand beyond audience suggestions while keeping location and
                      exclusions as controls.
                    </span>
                  </span>
                </label>

                {advantageAudience && (
                  <div className="mt-4 grid gap-3 sm:grid-cols-2">
                    <label className="flex items-start gap-2 rounded-lg bg-white/70 p-3 text-xs text-indigo-900">
                      <input
                        type="checkbox"
                        className="mt-0.5"
                        checked={advantageAgeSuggestion}
                        onChange={(event) =>
                          setAdvantageAgeSuggestion(event.target.checked)
                        }
                      />
                      Treat the selected age range as an audience suggestion.
                    </label>
                    <label className="flex items-start gap-2 rounded-lg bg-white/70 p-3 text-xs text-indigo-900">
                      <input
                        type="checkbox"
                        className="mt-0.5"
                        checked={advantageGenderSuggestion}
                        onChange={(event) =>
                          setAdvantageGenderSuggestion(event.target.checked)
                        }
                      />
                      Treat the selected gender as an audience suggestion.
                    </label>
                  </div>
                )}
              </div>

              <label className="block text-xs font-semibold text-slate-700">
                Include audiences
                <select
                  className="form-input mt-2 min-h-28"
                  multiple
                  value={customAudiences}
                  onChange={(e) =>
                    setCustomAudiences(
                      Array.from(e.target.selectedOptions, (option) => option.value),
                    )
                  }
                >
                  {audiences
                    .filter((audience) => audience.accessible)
                    .map((audience) => (
                      <option key={audience.id} value={audience.id}>
                        {audience.name} · {audience.subtype ?? 'Audience'}
                      </option>
                    ))}
                </select>
              </label>

              <label className="block text-xs font-semibold text-slate-700">
                Exclude audiences
                <select
                  className="form-input mt-2 min-h-28"
                  multiple
                  value={excludedAudiences}
                  onChange={(e) =>
                    setExcludedAudiences(
                      Array.from(e.target.selectedOptions, (option) => option.value),
                    )
                  }
                >
                  {audiences
                    .filter((audience) => audience.accessible)
                    .map((audience) => (
                      <option key={audience.id} value={audience.id}>
                        {audience.name} · {audience.subtype ?? 'Audience'}
                      </option>
                    ))}
                </select>
              </label>
            </div>
          </section>

          <section className="border-t border-slate-100 pt-6">
            <h3 className="text-sm font-bold text-slate-950">Placements</h3>
            <label className="mt-4 block text-xs font-semibold text-slate-700">
              Placement mode
              <select className="form-input mt-2" value={placementMode} onChange={(e) => setPlacementMode(e.target.value as 'AUTO' | 'MANUAL')}>
                <option value="AUTO">Advantage+ placements (recommended)</option>
                <option value="MANUAL">Manual placements</option>
              </select>
              <span className="mt-2 block text-[11px] font-normal leading-5 text-slate-500">
                Advantage+ placements lets Meta use all eligible placements by leaving manual
                publisher-platform restrictions unset.
              </span>
            </label>

            {placementMode === 'MANUAL' && (
              <div className="mt-4 space-y-3">
                {[
                  ['facebook', 'Facebook', facebook, setFacebook, facebookPositions, setFacebookPositions],
                  ['instagram', 'Instagram', instagram, setInstagram, instagramPositions, setInstagramPositions],
                  ['messenger', 'Messenger', messenger, setMessenger, messengerPositions, setMessengerPositions],
                  ['audience_network', 'Audience Network', audienceNetwork, setAudienceNetwork, audienceNetworkPositions, setAudienceNetworkPositions],
                ].map(([key, label, enabled, setEnabled, positions, setPositions]) => (
                  <div key={key as string} className="rounded-xl border border-slate-200 p-4">
                    <label className="flex items-center gap-3 text-sm font-semibold text-slate-800">
                      <input
                        type="checkbox"
                        checked={enabled as boolean}
                        onChange={(event) => (setEnabled as (value: boolean) => void)(event.target.checked)}
                      />
                      {label as string}
                    </label>
                    {enabled && (
                      <div className="mt-3 grid gap-2 sm:grid-cols-2">
                        {META_PLACEMENTS[key as keyof typeof META_PLACEMENTS].map(([value, title]) => (
                          <label key={value} className="flex items-center gap-2 rounded-lg bg-slate-50 px-3 py-2 text-xs text-slate-700">
                            <input
                              type="checkbox"
                              checked={(positions as string[]).includes(value)}
                              onChange={(event) =>
                                (setPositions as (value: string[]) => void)(
                                  event.target.checked
                                    ? [...new Set([...(positions as string[]), value])]
                                    : (positions as string[]).filter((item) => item !== value),
                                )
                              }
                            />
                            {title}
                          </label>
                        ))}
                      </div>
                    )}
                  </div>
                ))}
                <p className="text-xs leading-5 text-slate-500">
                  Leaving all positions unchecked for a selected platform lets Meta use every eligible placement on that platform.
                </p>
              </div>
            )}
          </section>

          <section className="border-t border-slate-100 pt-6">
            <h3 className="text-sm font-bold text-slate-950">Conversion</h3>
            <div className="mt-4 grid gap-4 sm:grid-cols-2">
              <label className="block text-xs font-semibold text-slate-700 sm:col-span-2">
                Custom conversion
                <select
                  className="form-input mt-2"
                  value={customConversionId}
                  onChange={(e) => setCustomConversionId(e.target.value)}
                >
                  <option value="">Use pixel + standard event</option>
                  {conversions
                    .filter((conversion) => conversion.accessible && !conversion.archived && !conversion.unavailable)
                    .map((conversion) => (
                      <option key={conversion.id} value={conversion.id}>
                        {conversion.name} · {conversion.customEventType ?? 'Conversion'}
                      </option>
                    ))}
                </select>
              </label>

              {!customConversionId && (
                <>
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
                      <option value="CONTENT_VIEW">View content</option>
                    </select>
                  </label>
                </>
              )}
            </div>
          </section>

          <div className="rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-xs leading-5 text-amber-800">
            This ad set is created PAUSED. Starting the campaign or ad set never happens implicitly from this form.
          </div>

          <div className="flex justify-end gap-2 border-t border-slate-100 pt-5">
            <button type="button" className="secondary-button" onClick={onClose} disabled={saving}>Cancel</button>
            <button type="submit" className="primary-button" disabled={saving || !name.trim() || locations.length === 0 || (budgetType !== 'NONE' && !budget)}>
              {saving ? 'Creating…' : 'Create paused ad set'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}
