import { useEffect, useMemo, useState, type FormEvent } from 'react'
import { FiArrowLeft, FiPause, FiPlay, FiPlus, FiRefreshCw, FiSave } from 'react-icons/fi'
import { Link, useParams } from 'react-router'
import MetaAdModal from '../../components/ads/MetaAdModal'
import MetaGeoTargetingEditor from '../../components/ads/MetaGeoTargetingEditor'
import GoogleAdGroupDetails from '../../components/ads/GoogleAdGroupDetails'
import { adCenterApi } from '../../features/ads/adCenterApi'
import type {
  Ad,
  AdAccount,
  AdAccountResource,
  AdAudience,
  AdCampaign,
  AdConversion,
  AdCreative,
  AdSet,
  CreateMetaAdInput,
  UpdateMetaAdSetInput,
  MetaTargetingLocation,
} from '../../features/ads/types'
import { PERMISSIONS } from '../../features/iam/permissions'
import { useIam } from '../../features/iam/useIam'
import { apiErrorMessage } from '../../lib/api/apiClient'
import { useAppSelector } from '../../store/hooks'
import { selectSelectedCompany } from '../../store/workspace/workspaceSlice'
import { PageSkeleton } from '../../components/ui/Skeleton'

type ProviderGeoTarget = {
  key?: string
  name?: string
  country?: string
  country_code?: string
  latitude?: number | string
  longitude?: number | string
  radius?: number | string
}

type TargetingSnapshot = {
  geo_locations?: {
    countries?: string[]
    regions?: ProviderGeoTarget[]
    cities?: ProviderGeoTarget[]
    zips?: ProviderGeoTarget[]
    custom_locations?: ProviderGeoTarget[]
    location_types?: Array<'home' | 'recent'>
  }
  age_min?: number
  age_max?: number
  genders?: number[]
  publisher_platforms?: string[]
  facebook_positions?: string[]
  instagram_positions?: string[]
  messenger_positions?: string[]
  audience_network_positions?: string[]
  custom_audiences?: Array<{ id?: string }>
  excluded_custom_audiences?: Array<{ id?: string }>
  targeting_automation?: {
    advantage_audience?: number | boolean
    individual_setting?: {
      age?: number | boolean
      gender?: number | boolean
    }
  }
}

type PromotedObjectSnapshot = {
  pixel_id?: string
  custom_event_type?: string
  custom_conversion_id?: string
}

const META_PLACEMENT_GROUPS = [
  {
    key: 'facebook',
    label: 'Facebook',
    positions: [
      ['feed', 'Feed'],
      ['story', 'Stories'],
      ['reels', 'Reels'],
      ['marketplace', 'Marketplace'],
      ['video_feeds', 'Video feeds'],
      ['search', 'Search results'],
    ],
  },
  {
    key: 'instagram',
    label: 'Instagram',
    positions: [
      ['stream', 'Feed'],
      ['story', 'Stories'],
      ['reels', 'Reels'],
      ['explore', 'Explore'],
      ['explore_home', 'Explore home'],
      ['profile_feed', 'Profile feed'],
    ],
  },
  {
    key: 'messenger',
    label: 'Messenger',
    positions: [
      ['messenger_home', 'Inbox'],
      ['story', 'Stories'],
    ],
  },
  {
    key: 'audience_network',
    label: 'Audience Network',
    positions: [
      ['classic', 'Native, banner and interstitial'],
      ['rewarded_video', 'Rewarded video'],
    ],
  },
] as const

const parseJson = <T,>(value?: string | null): T | null => {
  if (!value) return null
  try {
    return JSON.parse(value) as T
  } catch {
    return null
  }
}

const displayValue = (value?: string | null) =>
  value
    ? value
        .replaceAll('_', ' ')
        .toLowerCase()
        .replace(/\b\w/g, (c) => c.toUpperCase())
    : '—'

const formatMinor = (value: number | null | undefined, currency?: string | null) => {
  if (value == null) return '—'
  if (!currency) return String(value)
  try {
    const formatter = new Intl.NumberFormat(undefined, { style: 'currency', currency })
    const digits = formatter.resolvedOptions().maximumFractionDigits ?? 2
    return formatter.format(value / 10 ** digits)
  } catch {
    return `${value} ${currency}`
  }
}

const toMajor = (value: number | null | undefined, currency?: string | null) => {
  if (value == null) return ''
  if (!currency) return String(value)
  try {
    const digits =
      new Intl.NumberFormat(undefined, { style: 'currency', currency }).resolvedOptions()
        .maximumFractionDigits ?? 2
    return String(value / 10 ** digits)
  } catch {
    return String(value)
  }
}

const toMinor = (value: string, currency?: string | null) => {
  const numeric = Number(value)
  if (!Number.isFinite(numeric) || numeric <= 0) return undefined
  if (!currency) return Math.round(numeric)
  try {
    const digits =
      new Intl.NumberFormat(undefined, { style: 'currency', currency }).resolvedOptions()
        .maximumFractionDigits ?? 2
    return Math.round(numeric * 10 ** digits)
  } catch {
    return Math.round(numeric)
  }
}

const toLocalInput = (value?: string | null) => {
  if (!value) return ''
  const date = new Date(value)
  const local = new Date(date.getTime() - date.getTimezoneOffset() * 60_000)
  return local.toISOString().slice(0, 16)
}

const metaLocationsFromTargeting = (
  targeting: TargetingSnapshot | null,
): MetaTargetingLocation[] => {
  const geo = targeting?.geo_locations
  if (!geo) return []

  const locationTypes = geo.location_types?.filter(
    (value): value is 'home' | 'recent' => value === 'home' || value === 'recent',
  ) ?? ['home', 'recent']

  const providerLocations = (
    values: ProviderGeoTarget[] | undefined,
    type: 'REGION' | 'CITY' | 'ZIP',
  ): MetaTargetingLocation[] =>
    (values ?? [])
      .filter((value) => Boolean(value.key))
      .map((value) => ({
        countryCode: value.country_code ?? value.country ?? null,
        type,
        key: value.key ?? null,
        name: value.name ?? value.key ?? null,
        locationTypes,
      }))

  const customLocations: MetaTargetingLocation[] = (geo.custom_locations ?? [])
    .filter((value) => value.latitude != null && value.longitude != null && value.radius != null)
    .map((value) => ({
      type: 'CUSTOM',
      name: value.name ?? null,
      latitude: Number(value.latitude),
      longitude: Number(value.longitude),
      radiusKm: Number(value.radius),
      locationTypes,
    }))

  return [
    ...(geo.countries ?? []).map((countryCode) => ({
      countryCode,
      type: 'COUNTRY' as const,
      key: countryCode,
      name: countryCode,
      locationTypes,
    })),
    ...providerLocations(geo.regions, 'REGION'),
    ...providerLocations(geo.cities, 'CITY'),
    ...providerLocations(geo.zips, 'ZIP'),
    ...customLocations,
  ]
}

export default function AdSetDetails() {
  const { adSetId = '' } = useParams()
  const selectedCompany = useAppSelector(selectSelectedCompany)
  const { can } = useIam()

  const [adSet, setAdSet] = useState<AdSet | null>(null)
  const [campaign, setCampaign] = useState<AdCampaign | null>(null)
  const [accounts, setAccounts] = useState<AdAccount[]>([])
  const [resources, setResources] = useState<AdAccountResource[]>([])
  const [audiences, setAudiences] = useState<AdAudience[]>([])
  const [conversions, setConversions] = useState<AdConversion[]>([])
  const [creatives, setCreatives] = useState<AdCreative[]>([])
  const [ads, setAds] = useState<Ad[]>([])
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [statusWorking, setStatusWorking] = useState(false)
  const [syncingAds, setSyncingAds] = useState(false)
  const [creatingAd, setCreatingAd] = useState(false)
  const [workingAdId, setWorkingAdId] = useState<string | null>(null)
  const [adModalOpen, setAdModalOpen] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')

  const [name, setName] = useState('')
  const [optimizationGoal, setOptimizationGoal] = useState('')
  const [billingEvent, setBillingEvent] = useState('')
  const [bidStrategy, setBidStrategy] = useState('')
  const [bidAmount, setBidAmount] = useState('')
  const [budget, setBudget] = useState('')
  const [destinationType, setDestinationType] = useState('')
  const [startAt, setStartAt] = useState('')
  const [endAt, setEndAt] = useState('')
  const [locations, setLocations] = useState<MetaTargetingLocation[]>([])
  const [ageMin, setAgeMin] = useState('18')
  const [ageMax, setAgeMax] = useState('65')
  const [gender, setGender] = useState<'ALL' | 'MEN' | 'WOMEN'>('ALL')
  const [placementMode, setPlacementMode] = useState<'AUTO' | 'MANUAL'>('AUTO')
  const [facebook, setFacebook] = useState(false)
  const [instagram, setInstagram] = useState(false)
  const [messenger, setMessenger] = useState(false)
  const [audienceNetwork, setAudienceNetwork] = useState(false)
  const [facebookPositions, setFacebookPositions] = useState<string[]>([])
  const [instagramPositions, setInstagramPositions] = useState<string[]>([])
  const [messengerPositions, setMessengerPositions] = useState<string[]>([])
  const [audienceNetworkPositions, setAudienceNetworkPositions] = useState<string[]>([])
  const [customAudiences, setCustomAudiences] = useState<string[]>([])
  const [excludedAudiences, setExcludedAudiences] = useState<string[]>([])
  const [advantageAudience, setAdvantageAudience] = useState(false)
  const [advantageAgeSuggestion, setAdvantageAgeSuggestion] = useState(false)
  const [advantageGenderSuggestion, setAdvantageGenderSuggestion] = useState(false)
  const [pixelId, setPixelId] = useState('')
  const [customEventType, setCustomEventType] = useState('')
  const [customConversionId, setCustomConversionId] = useState('')

  const account = useMemo(
    () => accounts.find((item) => item.id === adSet?.adAccountId) ?? null,
    [accounts, adSet?.adAccountId],
  )

  const pixels = resources.filter(
    (resource) => resource.resourceType === 'META_PIXEL' && resource.accessible,
  )

  const syncForm = (
    value: AdSet,
    accountValue?: AdAccount | null,
    audienceValues: AdAudience[] = audiences,
    conversionValues: AdConversion[] = conversions,
  ) => {
    const targeting = parseJson<TargetingSnapshot>(value.targetingJson)
    const promotedObject = parseJson<PromotedObjectSnapshot>(value.promotedObjectJson)
    const platforms = targeting?.publisher_platforms ?? []

    setName(value.name)
    setOptimizationGoal(value.optimizationGoal ?? '')
    setBillingEvent(value.billingEvent ?? '')
    setBidStrategy(value.bidStrategy ?? '')
    setBidAmount(toMajor(value.bidAmountMinor, accountValue?.currency ?? account?.currency))
    setBudget(
      toMajor(
        value.dailyBudgetMinor ?? value.lifetimeBudgetMinor,
        accountValue?.currency ?? account?.currency,
      ),
    )
    setDestinationType(value.destinationType ?? '')
    setStartAt(toLocalInput(value.startAt))
    setEndAt(toLocalInput(value.endAt))
    setLocations(metaLocationsFromTargeting(targeting))
    setAgeMin(String(targeting?.age_min ?? 18))
    setAgeMax(String(targeting?.age_max ?? 65))
    setGender(
      targeting?.genders?.includes(1) ? 'MEN' : targeting?.genders?.includes(2) ? 'WOMEN' : 'ALL',
    )
    setPlacementMode(platforms.length ? 'MANUAL' : 'AUTO')
    setFacebook(platforms.includes('facebook'))
    setInstagram(platforms.includes('instagram'))
    setMessenger(platforms.includes('messenger'))
    setAudienceNetwork(platforms.includes('audience_network'))
    setFacebookPositions(targeting?.facebook_positions ?? [])
    setInstagramPositions(targeting?.instagram_positions ?? [])
    setMessengerPositions(targeting?.messenger_positions ?? [])
    setAudienceNetworkPositions(targeting?.audience_network_positions ?? [])
    const includedExternalIds = new Set(
      (targeting?.custom_audiences ?? []).map((item) => item.id).filter(Boolean),
    )
    const excludedExternalIds = new Set(
      (targeting?.excluded_custom_audiences ?? []).map((item) => item.id).filter(Boolean),
    )
    setCustomAudiences(
      audienceValues
        .filter((audience) => includedExternalIds.has(audience.externalAudienceId))
        .map((audience) => audience.id),
    )
    setExcludedAudiences(
      audienceValues
        .filter((audience) => excludedExternalIds.has(audience.externalAudienceId))
        .map((audience) => audience.id),
    )
    const targetingAutomation = targeting?.targeting_automation
    setAdvantageAudience(
      targetingAutomation?.advantage_audience === 1 ||
        targetingAutomation?.advantage_audience === true,
    )
    setAdvantageAgeSuggestion(
      targetingAutomation?.individual_setting?.age === 1 ||
        targetingAutomation?.individual_setting?.age === true,
    )
    setAdvantageGenderSuggestion(
      targetingAutomation?.individual_setting?.gender === 1 ||
        targetingAutomation?.individual_setting?.gender === true,
    )
    setPixelId(promotedObject?.pixel_id ?? '')
    setCustomEventType(promotedObject?.custom_event_type ?? '')
    setCustomConversionId(
      conversionValues.find(
        (conversion) => conversion.externalConversionId === promotedObject?.custom_conversion_id,
      )?.id ?? '',
    )
  }

  const load = async () => {
    if (!selectedCompany || !adSetId) return
    setLoading(true)
    setError('')
    try {
      const [adSetData, accountData] = await Promise.all([
        adCenterApi.adSet(selectedCompany.companyId, adSetId),
        adCenterApi.accounts(selectedCompany.companyId),
      ])
      const [campaignData, resourceData, audienceData, conversionData, adData, creativeData] =
        await Promise.all([
          adCenterApi.campaign(selectedCompany.companyId, adSetData.campaignId),
          adCenterApi.resources(selectedCompany.companyId, adSetData.adAccountId),
          adCenterApi.audiences(selectedCompany.companyId, adSetData.adAccountId),
          adCenterApi.conversions(selectedCompany.companyId, adSetData.adAccountId),
          adCenterApi.ads(selectedCompany.companyId, adSetData.id),
          adCenterApi.creatives(selectedCompany.companyId, adSetData.adAccountId),
        ])
      setAdSet(adSetData)
      setCampaign(campaignData)
      setAccounts(accountData)
      setResources(resourceData)
      setAudiences(audienceData)
      setConversions(conversionData)
      setAds(adData)
      setCreatives(creativeData)
      syncForm(
        adSetData,
        accountData.find((item) => item.id === adSetData.adAccountId),
        audienceData,
        conversionData,
      )
    } catch (loadError) {
      setError(apiErrorMessage(loadError, 'Unable to load ad set'))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    void load()
  }, [selectedCompany?.companyId, adSetId])

  const save = async (event: FormEvent) => {
    event.preventDefault()
    if (!selectedCompany || !adSet) return
    setSaving(true)
    setError('')
    setNotice('')

    const publisherPlatforms =
      placementMode === 'AUTO'
        ? []
        : [
            ...(facebook ? ['facebook'] : []),
            ...(instagram ? ['instagram'] : []),
            ...(messenger ? ['messenger'] : []),
            ...(audienceNetwork ? ['audience_network'] : []),
          ]

    const input: UpdateMetaAdSetInput = {
      name: name.trim(),
      optimizationGoal,
      billingEvent,
      bidStrategy: bidStrategy || undefined,
      bidAmountMinor: toMinor(bidAmount, account?.currency),
      startAt: startAt ? new Date(startAt).toISOString() : undefined,
      endAt: endAt ? new Date(endAt).toISOString() : undefined,
      destinationType: destinationType || undefined,
      locations,
      ageMin: Number(ageMin),
      ageMax: Number(ageMax),
      genders: gender === 'ALL' ? [] : gender === 'MEN' ? [1] : [2],
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
      pixelId: customConversionId ? undefined : pixelId || undefined,
      customEventType: customConversionId ? undefined : customEventType || undefined,
      customConversionId: customConversionId || undefined,
    }

    const minorBudget = toMinor(budget, account?.currency)
    if (minorBudget != null) {
      if (adSet.lifetimeBudgetMinor != null) input.lifetimeBudgetMinor = minorBudget
      else if (adSet.dailyBudgetMinor != null) input.dailyBudgetMinor = minorBudget
    }

    try {
      const updated = await adCenterApi.updateMetaAdSet(selectedCompany.companyId, adSet.id, input)
      setAdSet(updated)
      syncForm(updated, account, audiences, conversions)
      setNotice('Ad-set settings updated in Meta.')
    } catch (saveError) {
      setError(apiErrorMessage(saveError, 'Unable to update Meta ad set'))
    } finally {
      setSaving(false)
    }
  }

  const syncAds = async () => {
    if (!selectedCompany || !adSet) return
    setSyncingAds(true)
    setError('')
    setNotice('')
    try {
      const result = await adCenterApi.syncMetaAds(selectedCompany.companyId, adSet.id)
      const [adData, creativeData] = await Promise.all([
        adCenterApi.ads(selectedCompany.companyId, adSet.id),
        adCenterApi.creatives(selectedCompany.companyId, adSet.adAccountId),
      ])
      setAds(adData)
      setCreatives(creativeData)
      setNotice(`Synchronized ${result.synchronizedCount} ad(s) from Meta.`)
    } catch (syncError) {
      setError(apiErrorMessage(syncError, 'Unable to synchronize Meta ads'))
    } finally {
      setSyncingAds(false)
    }
  }

  const createAd = async (input: CreateMetaAdInput) => {
    if (!selectedCompany || !adSet) return
    setCreatingAd(true)
    setError('')
    setNotice('')
    try {
      const created = await adCenterApi.createMetaAd(selectedCompany.companyId, adSet.id, input)
      setAds((current) => [created, ...current.filter((item) => item.id !== created.id)])
      setAdModalOpen(false)
      setNotice(`${created.name} was created in paused state.`)
    } catch (createError) {
      setError(apiErrorMessage(createError, 'Unable to create Meta ad'))
    } finally {
      setCreatingAd(false)
    }
  }

  const setAdStatus = async (ad: Ad, status: 'ACTIVE' | 'PAUSED') => {
    if (!selectedCompany) return
    setWorkingAdId(ad.id)
    setError('')
    setNotice('')
    try {
      const updated = await adCenterApi.setMetaAdStatus(selectedCompany.companyId, ad.id, status)
      setAds((current) => current.map((item) => (item.id === updated.id ? updated : item)))
      setNotice(`${updated.name} is now ${status.toLowerCase()}.`)
    } catch (statusError) {
      setError(apiErrorMessage(statusError, 'Unable to update ad status'))
    } finally {
      setWorkingAdId(null)
    }
  }

  const setStatus = async (status: 'ACTIVE' | 'PAUSED') => {
    if (!selectedCompany || !adSet) return
    setStatusWorking(true)
    setError('')
    setNotice('')
    try {
      const updated = await adCenterApi.setMetaAdSetStatus(
        selectedCompany.companyId,
        adSet.id,
        status,
      )
      setAdSet(updated)
      setNotice(`Ad set is now ${status.toLowerCase()}.`)
    } catch (statusError) {
      setError(apiErrorMessage(statusError, 'Unable to update ad-set status'))
    } finally {
      setStatusWorking(false)
    }
  }

  if (!selectedCompany) return null

  if (!loading && adSet?.provider === 'GOOGLE_ADS') {
    return (
      <GoogleAdGroupDetails
        companyId={selectedCompany.companyId}
        initialAdGroup={adSet}
        campaign={campaign}
        account={account}
        initialAds={ads}
      />
    )
  }

  return (
    <section className="px-4 py-6 sm:px-6 lg:px-8">
      <div className="mx-auto max-w-5xl">
        <Link
          to={campaign ? `/ad-center/campaigns/${campaign.id}` : '/ad-center/campaigns'}
          className="inline-flex items-center gap-2 text-sm font-semibold text-slate-500 hover:text-slate-900"
        >
          <FiArrowLeft aria-hidden="true" />
          Back to campaign
        </Link>

        {loading ? (
          <div className="mt-6"><PageSkeleton /></div>
        ) : adSet ? (
          <>
            <header className="mt-5 flex flex-wrap items-end justify-between gap-4">
              <div>
                <p className="eyebrow">Meta ad set</p>
                <h1 className="mt-2 text-2xl font-bold tracking-tight text-slate-950 sm:text-3xl">
                  {adSet.name}
                </h1>
                <p className="mt-2 text-sm text-slate-500">
                  {campaign?.name ?? 'Campaign'} · {adSet.externalAdSetId}
                </p>
              </div>
              {can(PERMISSIONS.ADS_MODIFY) &&
                (adSet.providerStatus === 'ACTIVE' ? (
                  <button
                    type="button"
                    className="secondary-button inline-flex items-center gap-2"
                    disabled={statusWorking}
                    onClick={() => void setStatus('PAUSED')}
                  >
                    <FiPause aria-hidden="true" />
                    {statusWorking ? 'Updating…' : 'Pause'}
                  </button>
                ) : (
                  <button
                    type="button"
                    className="primary-button inline-flex items-center gap-2"
                    disabled={statusWorking || adSet.providerStatus === 'ARCHIVED'}
                    onClick={() => void setStatus('ACTIVE')}
                  >
                    <FiPlay aria-hidden="true" />
                    {statusWorking ? 'Updating…' : 'Start ad set'}
                  </button>
                ))}
            </header>

            {(error || notice) && (
              <div
                className={`mt-6 rounded-xl border px-4 py-3 text-sm font-medium ${
                  error
                    ? 'border-red-200 bg-red-50 text-red-700'
                    : 'border-emerald-200 bg-emerald-50 text-emerald-700'
                }`}
              >
                {error || notice}
              </div>
            )}

            <div className="mt-6 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
              <div className="panel p-5">
                <p className="text-xs font-semibold text-slate-400">Configured</p>
                <p className="mt-2 text-lg font-bold text-slate-950">
                  {adSet.providerStatus ?? adSet.status}
                </p>
              </div>
              <div className="panel p-5">
                <p className="text-xs font-semibold text-slate-400">Effective</p>
                <p className="mt-2 text-lg font-bold text-slate-950">
                  {adSet.effectiveStatus ?? '—'}
                </p>
              </div>
              <div className="panel p-5">
                <p className="text-xs font-semibold text-slate-400">Optimization</p>
                <p className="mt-2 text-lg font-bold text-slate-950">
                  {displayValue(adSet.optimizationGoal)}
                </p>
              </div>
              <div className="panel p-5">
                <p className="text-xs font-semibold text-slate-400">Budget</p>
                <p className="mt-2 text-lg font-bold text-slate-950">
                  {adSet.dailyBudgetMinor != null
                    ? `${formatMinor(adSet.dailyBudgetMinor, account?.currency)} / day`
                    : adSet.lifetimeBudgetMinor != null
                      ? formatMinor(adSet.lifetimeBudgetMinor, account?.currency)
                      : 'Campaign budget'}
                </p>
              </div>
            </div>

            <form className="panel mt-5 p-6" onSubmit={save}>
              <div>
                <h2 className="text-base font-bold text-slate-950">Delivery & bidding</h2>
                <p className="mt-1 text-xs text-slate-500">
                  Changes are sent to Meta and then re-read before updating local state.
                </p>
              </div>

              <div className="mt-5 grid gap-5 sm:grid-cols-2">
                <label className="block text-xs font-semibold text-slate-700">
                  Name
                  <input
                    className="form-input mt-2"
                    value={name}
                    onChange={(e) => setName(e.target.value)}
                  />
                </label>
                <label className="block text-xs font-semibold text-slate-700">
                  Destination
                  <select
                    className="form-input mt-2"
                    value={destinationType}
                    onChange={(e) => setDestinationType(e.target.value)}
                  >
                    <option value="">Meta default</option>
                    <option value="WEBSITE">Website</option>
                    <option value="APP">App</option>
                    <option value="MESSENGER">Messenger</option>
                    <option value="WHATSAPP">WhatsApp</option>
                    <option value="ON_AD">On ad</option>
                  </select>
                </label>
                <label className="block text-xs font-semibold text-slate-700">
                  Optimization goal
                  <select
                    className="form-input mt-2"
                    value={optimizationGoal}
                    onChange={(e) => setOptimizationGoal(e.target.value)}
                  >
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
                  <select
                    className="form-input mt-2"
                    value={billingEvent}
                    onChange={(e) => setBillingEvent(e.target.value)}
                  >
                    <option value="IMPRESSIONS">Impressions</option>
                    <option value="LINK_CLICKS">Link clicks</option>
                    <option value="THRUPLAY">ThruPlay</option>
                  </select>
                </label>
                <label className="block text-xs font-semibold text-slate-700">
                  Bid strategy
                  <select
                    className="form-input mt-2"
                    value={bidStrategy}
                    onChange={(e) => setBidStrategy(e.target.value)}
                  >
                    <option value="">Meta default</option>
                    <option value="LOWEST_COST_WITHOUT_CAP">Lowest cost</option>
                    <option value="LOWEST_COST_WITH_BID_CAP">Bid cap</option>
                    <option value="COST_CAP">Cost cap</option>
                  </select>
                </label>
                <label className="block text-xs font-semibold text-slate-700">
                  Bid amount {account?.currency ? `(${account.currency})` : ''}
                  <input
                    className="form-input mt-2"
                    type="number"
                    min="0.01"
                    step="0.01"
                    value={bidAmount}
                    onChange={(e) => setBidAmount(e.target.value)}
                  />
                </label>
                <label className="block text-xs font-semibold text-slate-700">
                  {adSet.lifetimeBudgetMinor != null
                    ? 'Lifetime budget'
                    : adSet.dailyBudgetMinor != null
                      ? 'Daily budget'
                      : 'Campaign budget'}
                  <input
                    className="form-input mt-2"
                    type="number"
                    min="0.01"
                    step="0.01"
                    value={budget}
                    disabled={adSet.dailyBudgetMinor == null && adSet.lifetimeBudgetMinor == null}
                    onChange={(e) => setBudget(e.target.value)}
                  />
                </label>
                <div className="grid grid-cols-2 gap-3">
                  <label className="block text-xs font-semibold text-slate-700">
                    Start
                    <input
                      className="form-input mt-2"
                      type="datetime-local"
                      value={startAt}
                      onChange={(e) => setStartAt(e.target.value)}
                    />
                  </label>
                  <label className="block text-xs font-semibold text-slate-700">
                    End
                    <input
                      className="form-input mt-2"
                      type="datetime-local"
                      value={endAt}
                      onChange={(e) => setEndAt(e.target.value)}
                    />
                  </label>
                </div>
              </div>

              <div className="mt-6 border-t border-slate-100 pt-6">
                <h2 className="text-base font-bold text-slate-950">Audience</h2>
                <div className="mt-4 grid gap-5 sm:grid-cols-2">
                  {account && (
                    <MetaGeoTargetingEditor
                      companyId={selectedCompany.companyId}
                      accountId={account.id}
                      locations={locations}
                      onChange={setLocations}
                      disabled={saving}
                    />
                  )}
                  <label className="block text-xs font-semibold text-slate-700">
                    Minimum age
                    <input
                      className="form-input mt-2"
                      type="number"
                      min="13"
                      max="65"
                      value={ageMin}
                      onChange={(e) => setAgeMin(e.target.value)}
                    />
                  </label>
                  <label className="block text-xs font-semibold text-slate-700">
                    Maximum age
                    <input
                      className="form-input mt-2"
                      type="number"
                      min="13"
                      max="65"
                      value={ageMax}
                      onChange={(e) => setAgeMax(e.target.value)}
                    />
                  </label>
                  <label className="block text-xs font-semibold text-slate-700">
                    Gender
                    <select
                      className="form-input mt-2"
                      value={gender}
                      onChange={(e) => setGender(e.target.value as 'ALL' | 'MEN' | 'WOMEN')}
                    >
                      <option value="ALL">All</option>
                      <option value="MEN">Men</option>
                      <option value="WOMEN">Women</option>
                    </select>
                  </label>
                  <label className="block text-xs font-semibold text-slate-700">
                    Placement mode
                    <select
                      className="form-input mt-2"
                      value={placementMode}
                      onChange={(e) => setPlacementMode(e.target.value as 'AUTO' | 'MANUAL')}
                    >
                      <option value="AUTO">Advantage+ placements (recommended)</option>
                      <option value="MANUAL">Manual</option>
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
                          Included audiences, age, and gender can be used as suggestions while
                          location and explicit exclusions remain controls.
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
                            onChange={(event) => setAdvantageAgeSuggestion(event.target.checked)}
                          />
                          Age range is a suggestion.
                        </label>
                        <label className="flex items-start gap-2 rounded-lg bg-white/70 p-3 text-xs text-indigo-900">
                          <input
                            type="checkbox"
                            className="mt-0.5"
                            checked={advantageGenderSuggestion}
                            onChange={(event) => setAdvantageGenderSuggestion(event.target.checked)}
                          />
                          Gender is a suggestion.
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

                {placementMode === 'MANUAL' && (
                  <div className="mt-4 space-y-3">
                    {META_PLACEMENT_GROUPS.map((group) => {
                      const enabled =
                        group.key === 'facebook'
                          ? facebook
                          : group.key === 'instagram'
                            ? instagram
                            : group.key === 'messenger'
                              ? messenger
                              : audienceNetwork
                      const setEnabled =
                        group.key === 'facebook'
                          ? setFacebook
                          : group.key === 'instagram'
                            ? setInstagram
                            : group.key === 'messenger'
                              ? setMessenger
                              : setAudienceNetwork
                      const positions =
                        group.key === 'facebook'
                          ? facebookPositions
                          : group.key === 'instagram'
                            ? instagramPositions
                            : group.key === 'messenger'
                              ? messengerPositions
                              : audienceNetworkPositions
                      const setPositions =
                        group.key === 'facebook'
                          ? setFacebookPositions
                          : group.key === 'instagram'
                            ? setInstagramPositions
                            : group.key === 'messenger'
                              ? setMessengerPositions
                              : setAudienceNetworkPositions
                      return (
                        <div key={group.key} className="rounded-xl border border-slate-200 p-4">
                          <label className="flex items-center gap-2 text-sm font-semibold text-slate-800">
                            <input
                              type="checkbox"
                              checked={enabled}
                              onChange={(e) => setEnabled(e.target.checked)}
                            />
                            {group.label}
                          </label>
                          {enabled && (
                            <div className="mt-3 grid gap-2 sm:grid-cols-2 lg:grid-cols-3">
                              {group.positions.map(([value, label]) => (
                                <label
                                  key={value}
                                  className="flex items-center gap-2 text-xs text-slate-600"
                                >
                                  <input
                                    type="checkbox"
                                    checked={positions.includes(value)}
                                    onChange={(e) =>
                                      setPositions(
                                        e.target.checked
                                          ? [...new Set([...positions, value])]
                                          : positions.filter((item) => item !== value),
                                      )
                                    }
                                  />
                                  {label}
                                </label>
                              ))}
                            </div>
                          )}
                        </div>
                      )
                    })}
                    <p className="text-xs text-slate-500">
                      Leave a platform's individual positions empty to allow all eligible positions
                      on that platform.
                    </p>
                  </div>
                )}
              </div>

              <div className="mt-6 border-t border-slate-100 pt-6">
                <h2 className="text-base font-bold text-slate-950">Conversion</h2>
                <div className="mt-4 grid gap-5 sm:grid-cols-2">
                  <label className="block text-xs font-semibold text-slate-700 sm:col-span-2">
                    Custom conversion
                    <select
                      className="form-input mt-2"
                      value={customConversionId}
                      onChange={(e) => setCustomConversionId(e.target.value)}
                    >
                      <option value="">Use pixel + standard event</option>
                      {conversions
                        .filter(
                          (conversion) =>
                            conversion.accessible &&
                            !conversion.archived &&
                            !conversion.unavailable,
                        )
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
                        <select
                          className="form-input mt-2"
                          value={pixelId}
                          onChange={(e) => setPixelId(e.target.value)}
                        >
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
                        <select
                          className="form-input mt-2"
                          value={customEventType}
                          disabled={!pixelId}
                          onChange={(e) => setCustomEventType(e.target.value)}
                        >
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
              </div>

              {can(PERMISSIONS.ADS_MODIFY) && (
                <div className="mt-6 flex justify-end border-t border-slate-100 pt-5">
                  <button
                    type="submit"
                    className="primary-button inline-flex items-center gap-2"
                    disabled={saving || !name.trim() || locations.length === 0}
                  >
                    <FiSave aria-hidden="true" />
                    {saving ? 'Saving…' : 'Save to Meta'}
                  </button>
                </div>
              )}
            </form>

            <div className="panel mt-5 overflow-hidden">
              <div className="flex flex-wrap items-center justify-between gap-3 border-b border-slate-100 px-5 py-4">
                <div>
                  <h2 className="text-base font-bold text-slate-950">Ads</h2>
                  <p className="mt-1 text-xs text-slate-500">
                    Ads combine this ad set with a reusable creative.
                  </p>
                </div>
                <div className="flex flex-wrap gap-2">
                  {can(PERMISSIONS.ADS_MODIFY) && (
                    <button
                      type="button"
                      className="secondary-button inline-flex items-center gap-2"
                      disabled={syncingAds}
                      onClick={() => void syncAds()}
                    >
                      <FiRefreshCw aria-hidden="true" />
                      {syncingAds ? 'Syncing…' : 'Sync ads'}
                    </button>
                  )}
                  {can(PERMISSIONS.ADS_CREATE) && (
                    <button
                      type="button"
                      className="primary-button inline-flex items-center gap-2"
                      onClick={() => setAdModalOpen(true)}
                    >
                      <FiPlus aria-hidden="true" />
                      Create ad
                    </button>
                  )}
                </div>
              </div>

              {ads.length === 0 ? (
                <div className="p-8 text-center text-sm text-slate-500">
                  No ads are stored for this ad set yet. Sync Meta or create the first paused ad.
                </div>
              ) : (
                <div className="divide-y divide-slate-100">
                  {ads.map((ad) => {
                    const creative = creatives.find((item) => item.id === ad.creativeId)
                    return (
                      <div
                        key={ad.id}
                        className="flex flex-wrap items-center justify-between gap-4 px-5 py-4"
                      >
                        <div className="min-w-0">
                          <Link
                            to={`/ad-center/ads/${ad.id}`}
                            className="font-semibold text-slate-950 hover:text-indigo-600"
                          >
                            {ad.name}
                          </Link>
                          <p className="mt-1 text-xs text-slate-500">
                            {creative?.name ?? ad.externalCreativeId ?? 'Creative'} · Meta ad{' '}
                            {ad.externalAdId}
                          </p>
                        </div>

                        <div className="flex flex-wrap items-center justify-end gap-2">
                          <span className="rounded-full bg-slate-100 px-2.5 py-1 text-xs font-semibold text-slate-600">
                            {ad.providerStatus ?? ad.status}
                          </span>
                          {ad.effectiveStatus && ad.effectiveStatus !== ad.providerStatus && (
                            <span className="rounded-full bg-slate-100 px-2.5 py-1 text-xs font-semibold text-slate-500">
                              {ad.effectiveStatus}
                            </span>
                          )}
                          {can(PERMISSIONS.ADS_MODIFY) &&
                            (ad.providerStatus === 'ACTIVE' ? (
                              <button
                                type="button"
                                className="secondary-button inline-flex items-center gap-2"
                                disabled={workingAdId !== null}
                                onClick={() => void setAdStatus(ad, 'PAUSED')}
                              >
                                <FiPause aria-hidden="true" />
                                Pause
                              </button>
                            ) : (
                              <button
                                type="button"
                                className="secondary-button inline-flex items-center gap-2"
                                disabled={
                                  workingAdId !== null ||
                                  ad.providerStatus === 'ARCHIVED' ||
                                  ad.status === 'DISABLED'
                                }
                                onClick={() => void setAdStatus(ad, 'ACTIVE')}
                              >
                                <FiPlay aria-hidden="true" />
                                Start
                              </button>
                            ))}
                        </div>
                      </div>
                    )
                  })}
                </div>
              )}
            </div>

            <MetaAdModal
              creatives={creatives}
              open={adModalOpen}
              saving={creatingAd}
              onClose={() => setAdModalOpen(false)}
              onCreate={createAd}
            />
          </>
        ) : (
          error && (
            <div className="mt-6 rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
              {error}
            </div>
          )
        )}
      </div>
    </section>
  )
}
