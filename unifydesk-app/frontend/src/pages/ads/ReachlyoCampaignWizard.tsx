import { useSnackbarState } from '../../components/feedback/useSnackbarState'
import { useEffect, useMemo, useState } from 'react'
import type { ReactNode } from 'react'
import {
  FiArrowLeft,
  FiArrowRight,
  FiCheck,
  FiChevronRight,
  FiDollarSign,
  FiImage,
  FiPlay,
  FiPlus,
  FiRefreshCw,
  FiTarget,
  FiTrash2,
  FiUploadCloud,
  FiUsers,
  FiZap,
} from 'react-icons/fi'
import { Link, useNavigate, useParams } from 'react-router'
import ReachlyoLaunchRunPanel from '../../components/ads/ReachlyoLaunchRunPanel'
import ReachlyoCampaignAnalyticsPanel from '../../components/ads/ReachlyoCampaignAnalyticsPanel'
import ReachlyoCampaignRecommendationsPanel from '../../components/ads/ReachlyoCampaignRecommendationsPanel'
import ReachlyoProviderPlanReview from '../../components/ads/ReachlyoProviderPlanReview'
import ReachlyoProviderSettingsStep, {
  type GoogleAdScheduleDraft,
} from '../../components/ads/ReachlyoProviderSettingsStep'
import MetaCreativeVariantsEditor from '../../components/ads/MetaCreativeVariantsEditor'
import MetaDetailedTargetingEditor from '../../components/ads/MetaDetailedTargetingEditor'
import GoogleKeywordPlannerPanel from '../../components/ads/GoogleKeywordPlannerPanel'
import { adCenterApi } from '../../features/ads/adCenterApi'
import type {
  AdAccount,
  AdAccountResource,
  AdConversion,
  AdAudience,
  BudgetAllocationMode,
  BudgetPeriod,
  CampaignBusinessGoal,
  CampaignChannelType,
  LaunchRun,
  ProviderCampaignPlan,
  ReachlyoAudienceDefinition,
  ReachlyoAudienceLocationInput,
  CanonicalLocationSuggestion,
  ReachlyoCampaign,
  ReachlyoCampaignObjective,
  ReachlyoCreativePack,
  ReachlyoConversionGoalMapping,
  GoogleNegativeKeywordInput,
  GoogleBrandSuggestion,
  GoogleNegativeKeywordList,
  MetaTargetingInterest,
  GoogleMerchantAccount,
} from '../../features/ads/types'
import { mediaApi } from '../../features/media/mediaApi'
import { PERMISSIONS } from '../../features/iam/permissions'
import { useIam } from '../../features/iam/useIam'
import { apiErrorMessage } from '../../lib/api/apiClient'
import { useAppSelector } from '../../store/hooks'
import { selectSelectedCompany } from '../../store/workspace/workspaceSlice'
import { PageSkeleton } from '../../components/ui/Skeleton'

const STEPS = [
  { label: 'Goal', icon: FiTarget },
  { label: 'Channels', icon: FiZap },
  { label: 'Budget', icon: FiDollarSign },
  { label: 'Creative', icon: FiImage },
  { label: 'Audience', icon: FiUsers },
  { label: 'Provider settings', icon: FiCheck },
  { label: 'Review', icon: FiRefreshCw },
  { label: 'Launch', icon: FiPlay },
] as const

const CHANNELS: Array<{
  type: CampaignChannelType
  title: string
  provider: 'GOOGLE_ADS' | 'META_ADS'
  description: string
  executable: boolean
}> = [
  {
    type: 'GOOGLE_SEARCH',
    title: 'Google Search',
    provider: 'GOOGLE_ADS',
    description: 'Intent-led Search ads compiled from the shared campaign brief.',
    executable: true,
  },
  {
    type: 'META_ADS',
    title: 'Meta Ads',
    provider: 'META_ADS',
    description: 'Facebook and Instagram hierarchy compiled from the same brief.',
    executable: true,
  },
  {
    type: 'GOOGLE_PERFORMANCE_MAX',
    title: 'Google Performance Max',
    provider: 'GOOGLE_ADS',
    description:
      'Launch a paused Google Performance Max campaign from the shared brief and creative pack.',
    executable: true,
  },
  {
    type: 'GOOGLE_DEMAND_GEN',
    title: 'Google Demand Gen',
    provider: 'GOOGLE_ADS',
    description:
      'Launch a paused Google Demand Gen campaign and visual multi-asset ad from the shared brief.',
    executable: true,
  },
  {
    type: 'GOOGLE_DISPLAY',
    title: 'Google Display',
    provider: 'GOOGLE_ADS',
    description:
      'Launch a paused Google Display campaign and responsive display ad from the shared visual creative pack.',
    executable: true,
  },
  {
    type: 'GOOGLE_SHOPPING',
    title: 'Google Shopping',
    provider: 'GOOGLE_ADS',
    description:
      'Launch a paused Standard Shopping campaign from a connected Merchant Center catalog.',
    executable: true,
  },
]

const OBJECTIVES: ReachlyoCampaignObjective[] = [
  'SALES',
  'LEADS',
  'TRAFFIC',
  'AWARENESS',
  'APP_PROMOTION',
  'LOCAL_PROMOTION',
  'RETARGETING',
  'PRODUCT_SALES',
]

const BUSINESS_GOALS: CampaignBusinessGoal[] = [
  'PURCHASE',
  'LEAD',
  'QUALIFIED_LEAD',
  'SIGN_UP',
  'PHONE_CALL',
  'WHATSAPP_CONVERSATION',
  'APP_INSTALL',
  'TRAFFIC',
  'AWARENESS',
]

const GOAL_OPTIONS: Array<{
  objective: ReachlyoCampaignObjective
  goal: CampaignBusinessGoal
  title: string
  description: string
}> = [
  {
    objective: 'SALES',
    goal: 'PURCHASE',
    title: 'Get more sales',
    description: 'Optimize campaigns for purchases and revenue.',
  },
  {
    objective: 'LEADS',
    goal: 'LEAD',
    title: 'Get more leads',
    description: 'Collect enquiries and qualified prospects.',
  },
  {
    objective: 'LEADS',
    goal: 'WHATSAPP_CONVERSATION',
    title: 'Get WhatsApp conversations',
    description: 'Bring interested customers into a WhatsApp conversation.',
  },
  {
    objective: 'LEADS',
    goal: 'PHONE_CALL',
    title: 'Get more phone calls',
    description: 'Optimize campaigns for customers who want to call you.',
  },
  {
    objective: 'TRAFFIC',
    goal: 'TRAFFIC',
    title: 'Get more website visitors',
    description: 'Drive relevant people to your website or landing page.',
  },
  {
    objective: 'APP_PROMOTION',
    goal: 'APP_INSTALL',
    title: 'Get app installs',
    description: 'Promote your app and optimize for installs.',
  },
  {
    objective: 'AWARENESS',
    goal: 'AWARENESS',
    title: 'Build awareness',
    description: 'Reach more relevant people and grow brand visibility.',
  },
  {
    objective: 'PRODUCT_SALES',
    goal: 'PURCHASE',
    title: 'Sell products from a catalog',
    description: 'Promote products from Merchant Center with Google Shopping.',
  },
]

const recommendedChannelsForGoal = (
  goal: CampaignBusinessGoal,
  objective: ReachlyoCampaignObjective,
): CampaignChannelType[] => {
  if (objective === 'PRODUCT_SALES') return ['GOOGLE_SHOPPING', 'META_ADS']
  if (goal === 'AWARENESS') return ['META_ADS', 'GOOGLE_DEMAND_GEN']
  if (goal === 'APP_INSTALL') return ['META_ADS']
  return ['GOOGLE_SEARCH', 'META_ADS']
}

const pretty = (value: string) =>
  value
    .replaceAll('_', ' ')
    .toLowerCase()
    .replace(/\b\w/g, (character) => character.toUpperCase())

const parseObject = (value?: string | null) => {
  if (!value) return {} as Record<string, unknown>
  try {
    const parsed = JSON.parse(value)
    return parsed && typeof parsed === 'object' && !Array.isArray(parsed)
      ? (parsed as Record<string, unknown>)
      : {}
  } catch {
    return {}
  }
}

const parseArray = <T,>(value?: string | null): T[] => {
  if (!value) return []
  try {
    const parsed = JSON.parse(value)
    return Array.isArray(parsed) ? (parsed as T[]) : []
  } catch {
    return []
  }
}
const googleAudienceSearchEligible = (audience: AdAudience) => {
  if (!audience.accessible || !audience.deliveryStatusJson) return false
  try {
    const status = JSON.parse(audience.deliveryStatusJson) as Record<string, unknown>
    return (
      status.eligibleForSearch === true &&
      String(status.accountStatus ?? '').toUpperCase() !== 'DISABLED'
    )
  } catch {
    return false
  }
}

const googleAudienceDisplayEligible = (audience: AdAudience) => {
  if (!audience.accessible || !audience.deliveryStatusJson) return false
  try {
    const status = JSON.parse(audience.deliveryStatusJson) as Record<string, unknown>
    return (
      status.eligibleForDisplay === true &&
      String(status.accountStatus ?? '').toUpperCase() !== 'DISABLED'
    )
  } catch {
    return false
  }
}

const listValues = (value: string) =>
  value
    .split(/\r?\n|,/)
    .map((item) => item.trim())
    .filter(Boolean)

const requiresConversion = (goal: CampaignBusinessGoal) =>
  goal !== 'TRAFFIC' && goal !== 'AWARENESS'

const GOOGLE_GOAL_EVENTS: Partial<Record<CampaignBusinessGoal, string[]>> = {
  PURCHASE: ['PURCHASE'],
  LEAD: ['SUBMIT_LEAD_FORM', 'IMPORTED_LEAD', 'CONTACT', 'REQUEST_QUOTE'],
  QUALIFIED_LEAD: ['QUALIFIED_LEAD', 'CONVERTED_LEAD'],
  SIGN_UP: ['SIGNUP'],
  PHONE_CALL: ['PHONE_CALL_LEAD'],
  WHATSAPP_CONVERSATION: ['CONTACT', 'IMPORTED_LEAD'],
  APP_INSTALL: ['DOWNLOAD', 'DEFAULT'],
}

const META_GOAL_EVENTS: Partial<Record<CampaignBusinessGoal, string[]>> = {
  PURCHASE: ['PURCHASE'],
  LEAD: ['LEAD'],
  QUALIFIED_LEAD: ['LEAD'],
  SIGN_UP: ['COMPLETE_REGISTRATION'],
  PHONE_CALL: ['CONTACT', 'LEAD'],
  WHATSAPP_CONVERSATION: ['CONTACT', 'LEAD'],
  TRAFFIC: ['CONTENT_VIEW'],
}

const defaultMetaStandardEvent = (goal: CampaignBusinessGoal) => META_GOAL_EVENTS[goal]?.[0] ?? ''

const recommendedConversion = (
  conversions: AdConversion[],
  goal: CampaignBusinessGoal,
  provider: 'GOOGLE_ADS' | 'META_ADS',
) => {
  const events = provider === 'GOOGLE_ADS' ? GOOGLE_GOAL_EVENTS[goal] : META_GOAL_EVENTS[goal]
  if (!events?.length) return conversions[0]
  return (
    conversions.find((conversion) => {
      const event = conversion.customEventType?.trim().toUpperCase()
      return event ? events.includes(event) : false
    }) ?? conversions[0]
  )
}

const defaultGoal = (objective: ReachlyoCampaignObjective): CampaignBusinessGoal => {
  switch (objective) {
    case 'SALES':
    case 'PRODUCT_SALES':
      return 'PURCHASE'
    case 'LEADS':
      return 'LEAD'
    case 'APP_PROMOTION':
      return 'APP_INSTALL'
    case 'AWARENESS':
      return 'AWARENESS'
    default:
      return 'TRAFFIC'
  }
}

const providerForChannel = (type: CampaignChannelType) =>
  type === 'META_ADS' ? 'META_ADS' : 'GOOGLE_ADS'

const GOOGLE_VISUAL_AUDIENCE_CHANNELS: CampaignChannelType[] = [
  'GOOGLE_DISPLAY',
  'GOOGLE_DEMAND_GEN',
  'GOOGLE_PERFORMANCE_MAX',
]

const GOOGLE_PROVIDER_AUDIENCE_CHANNELS: CampaignChannelType[] = [
  'GOOGLE_SEARCH',
  ...GOOGLE_VISUAL_AUDIENCE_CHANNELS,
]

const currencyDigits = (currency: string) => {
  try {
    return (
      new Intl.NumberFormat(undefined, { style: 'currency', currency }).resolvedOptions()
        .maximumFractionDigits ?? 2
    )
  } catch {
    return 2
  }
}

const toMinor = (value: string, currency: string) => {
  const amount = Number(value)
  if (!Number.isFinite(amount)) return 0
  return Math.round(amount * 10 ** currencyDigits(currency))
}

const fromMinor = (value: number | null | undefined, currency: string) => {
  if (value == null) return ''
  return String(value / 10 ** currencyDigits(currency))
}

const toLocalInput = (value?: string | null) => {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ''
  const offset = date.getTimezoneOffset() * 60_000
  return new Date(date.getTime() - offset).toISOString().slice(0, 16)
}

const fromLocalInput = (value: string) => {
  if (!value) return null
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? null : date.toISOString()
}

const statusClass = (status: string) => {
  if (status === 'ACTIVE' || status === 'PAUSED' || status === 'PAUSED_READY') {
    return 'bg-emerald-50 text-emerald-700'
  }
  if (status === 'FAILED' || status === 'PARTIAL_FAILURE' || status === 'VALIDATION_FAILED') {
    return 'bg-red-50 text-red-700'
  }
  if (status === 'READY' || status === 'DEPLOYING') {
    return 'bg-indigo-50 text-indigo-700'
  }
  return 'bg-slate-100 text-slate-600'
}

const launchKey = () => {
  const random =
    typeof crypto !== 'undefined' && 'randomUUID' in crypto
      ? crypto.randomUUID()
      : Math.random().toString(36).slice(2)
  return 'reachlyo-' + Date.now() + '-' + random
}

export default function ReachlyoCampaignWizard() {
  const selectedCompany = useAppSelector(selectSelectedCompany)
  const params = useParams()
  const campaignId = params.campaignId
  const navigate = useNavigate()
  const { can } = useIam()
  const canEdit = can(PERMISSIONS.ADS_CREATE) || can(PERMISSIONS.ADS_MODIFY)
  const canSyncAnalytics = can(PERMISSIONS.ADS_MODIFY)

  const [step, setStep] = useState(0)
  const [campaign, setCampaign] = useState<ReachlyoCampaign | null>(null)
  const [accounts, setAccounts] = useState<AdAccount[]>([])
  const [creativePacks, setCreativePacks] = useState<ReachlyoCreativePack[]>([])
  const [audiences, setAudiences] = useState<ReachlyoAudienceDefinition[]>([])
  const [plans, setPlans] = useState<ProviderCampaignPlan[]>([])
  const [launchRuns, setLaunchRuns] = useState<LaunchRun[]>([])
  const [currentRun, setCurrentRun] = useState<LaunchRun | null>(null)
  const [resourcesByAccount, setResourcesByAccount] = useState<Record<string, AdAccountResource[]>>(
    {},
  )
  const [conversionsByAccount, setConversionsByAccount] = useState<Record<string, AdConversion[]>>(
    {},
  )
  const [audiencesByAccount, setAudiencesByAccount] = useState<Record<string, AdAudience[]>>({})
  const [googleAudienceIdsByChannel, setGoogleAudienceIdsByChannel] = useState<
    Partial<Record<CampaignChannelType, string[]>>
  >({})
  const [googleExcludedAudienceIdsByChannel, setGoogleExcludedAudienceIdsByChannel] = useState<
    Partial<Record<CampaignChannelType, string[]>>
  >({})

  const [loading, setLoading] = useState(Boolean(campaignId))
  const [busy, setBusy] = useState(false)
  const [reviewLoading, setReviewLoading] = useState(false)
  const [, setError] = useSnackbarState('', 'error')
  const [, setNotice] = useSnackbarState('', 'success')

  const [name, setName] = useState('')
  const [objective, setObjective] = useState<ReachlyoCampaignObjective>('LEADS')
  const [businessGoal, setBusinessGoal] = useState<CampaignBusinessGoal>('LEAD')
  const [currency, setCurrency] = useState('INR')
  const [landingPageUrl, setLandingPageUrl] = useState('')
  const [startAt, setStartAt] = useState('')
  const [endAt, setEndAt] = useState('')

  const [selectedChannels, setSelectedChannels] = useState<CampaignChannelType[]>([])
  const [accountByChannel, setAccountByChannel] = useState<Record<string, string>>({})

  const [budgetAmount, setBudgetAmount] = useState('')
  const [budgetPeriod, setBudgetPeriod] = useState<BudgetPeriod>('DAILY')
  const [allocationMode, setAllocationMode] = useState<BudgetAllocationMode>('PERCENTAGE')
  const [allocationByChannel, setAllocationByChannel] = useState<Record<string, string>>({})

  const [creativeName, setCreativeName] = useState('Main creative')
  const [brandName, setBrandName] = useState('')
  const [primaryMessage, setPrimaryMessage] = useState('')
  const [callToAction, setCallToAction] = useState('LEARN_MORE')
  const [creativeUrl, setCreativeUrl] = useState('')
  const [headlines, setHeadlines] = useState('')
  const [descriptions, setDescriptions] = useState('')
  const [creativeFile, setCreativeFile] = useState<File | null>(null)
  const [pmaxLandscapeFile, setPmaxLandscapeFile] = useState<File | null>(null)
  const [pmaxSquareFile, setPmaxSquareFile] = useState<File | null>(null)
  const [pmaxLogoFile, setPmaxLogoFile] = useState<File | null>(null)
  const [pmaxPortraitFile, setPmaxPortraitFile] = useState<File | null>(null)
  const [pmaxLandscapeLogoFile, setPmaxLandscapeLogoFile] = useState<File | null>(null)
  const [uploadProgress, setUploadProgress] = useState(0)

  const [audienceName, setAudienceName] = useState('Primary audience')
  const [audienceLocations, setAudienceLocations] = useState<ReachlyoAudienceLocationInput[]>([
    { countryCode: 'IN' },
  ])
  const [locationSearchByIndex, setLocationSearchByIndex] = useState<Record<number, string>>({})
  const [locationSuggestionsByIndex, setLocationSuggestionsByIndex] = useState<
    Record<number, CanonicalLocationSuggestion[]>
  >({})
  const [locationSearchErrorByIndex, setLocationSearchErrorByIndex] = useState<
    Record<number, string>
  >({})
  const [locationSearchBusyIndex, setLocationSearchBusyIndex] = useState<number | null>(null)
  const [languages, setLanguages] = useState('')
  const [ageMin, setAgeMin] = useState('')
  const [ageMax, setAgeMax] = useState('')
  const [genders, setGenders] = useState<string[]>([])
  const [interests, setInterests] = useState('')
  const [searchIntents, setSearchIntents] = useState('')

  const [googlePolitical, setGooglePolitical] = useState<'true' | 'false' | ''>('false')
  const [googleBidStrategy, setGoogleBidStrategy] = useState('MAXIMIZE_CONVERSIONS')
  const [pmaxBidStrategy, setPmaxBidStrategy] = useState<
    'MAXIMIZE_CONVERSIONS' | 'MAXIMIZE_CONVERSION_VALUE'
  >('MAXIMIZE_CONVERSIONS')
  const [pmaxYoutubeVideoIds, setPmaxYoutubeVideoIds] = useState('')
  const [pmaxBrandGuidelinesEnabled, setPmaxBrandGuidelinesEnabled] = useState(true)
  const [pmaxBrandMainColor, setPmaxBrandMainColor] = useState('')
  const [pmaxBrandAccentColor, setPmaxBrandAccentColor] = useState('')
  const [pmaxBrandFontFamily, setPmaxBrandFontFamily] = useState('')
  const [pmaxMerchantAccounts, setPmaxMerchantAccounts] = useState<GoogleMerchantAccount[]>([])
  const [pmaxMerchantId, setPmaxMerchantId] = useState('')
  const [pmaxFeedLabel, setPmaxFeedLabel] = useState('')
  const [pmaxLifecycleGoalType, setPmaxLifecycleGoalType] = useState<
    'NEW_CUSTOMER_ACQUISITION' | 'CUSTOMER_RETENTION' | 'LOYALTY_RETENTION' | ''
  >('')
  const [pmaxLifecycleTargetOption, setPmaxLifecycleTargetOption] = useState<
    'TARGET_ALL' | 'TARGET_SPECIFIC'
  >('TARGET_ALL')
  const [pmaxLifecycleAdditionalValue, setPmaxLifecycleAdditionalValue] = useState('')
  const [pmaxLifecycleHighLifetimeValue, setPmaxLifecycleHighLifetimeValue] = useState('')
  const [pmaxLifecycleValueMultiplier, setPmaxLifecycleValueMultiplier] = useState('')
  const [
    pmaxLifecycleEnableBidAdjustmentsForLoyaltyMembers,
    setPmaxLifecycleEnableBidAdjustmentsForLoyaltyMembers,
  ] = useState(false)
  const [
    pmaxLifecycleShowTargetedLoyaltyMemberBenefitsInPla,
    setPmaxLifecycleShowTargetedLoyaltyMemberBenefitsInPla,
  ] = useState(false)
  const [demandGenBidStrategy, setDemandGenBidStrategy] = useState<
    'MAXIMIZE_CLICKS' | 'MAXIMIZE_CONVERSIONS' | 'MAXIMIZE_CONVERSION_VALUE'
  >('MAXIMIZE_CONVERSIONS')
  const [demandGenChannelStrategy, setDemandGenChannelStrategy] = useState<
    'ALL_CHANNELS' | 'ALL_OWNED_AND_OPERATED_CHANNELS'
  >('ALL_CHANNELS')
  const [shoppingMerchantAccounts, setShoppingMerchantAccounts] = useState<GoogleMerchantAccount[]>(
    [],
  )
  const [shoppingMerchantId, setShoppingMerchantId] = useState('')
  const [shoppingPriority, setShoppingPriority] = useState<'0' | '1' | '2'>('0')
  const [shoppingEnableLocal, setShoppingEnableLocal] = useState(false)
  const [displayBidStrategy, setDisplayBidStrategy] = useState<
    'MANUAL_CPC' | 'MAXIMIZE_CONVERSIONS' | 'TARGET_CPA'
  >('MAXIMIZE_CONVERSIONS')
  const [displayTargetCpa, setDisplayTargetCpa] = useState('')
  const [displayCpcBid, setDisplayCpcBid] = useState('')
  const [keywordMatchType, setKeywordMatchType] = useState('BROAD')
  const [googleConversionId, setGoogleConversionId] = useState('')
  const [googleSearchPartners, setGoogleSearchPartners] = useState(true)
  const [googleSearchAudienceMode, setGoogleSearchAudienceMode] = useState<
    'OBSERVATION' | 'TARGETING'
  >('OBSERVATION')
  const [googleSearchInterestResources, setGoogleSearchInterestResources] = useState<string[]>([])
  const [googleSearchExcludedInterestResources, setGoogleSearchExcludedInterestResources] =
    useState<string[]>([])
  const [googleParentalStatusIncludes, setGoogleParentalStatusIncludes] = useState<string[]>([])
  const [googleParentalStatusExcludes, setGoogleParentalStatusExcludes] = useState<string[]>([])
  const [googleIncomeRangeIncludes, setGoogleIncomeRangeIncludes] = useState<string[]>([])
  const [googleIncomeRangeExcludes, setGoogleIncomeRangeExcludes] = useState<string[]>([])
  const [googleDetailedDemographicIds, setGoogleDetailedDemographicIds] = useState<string[]>([])
  const [googleExcludedDetailedDemographicIds, setGoogleExcludedDetailedDemographicIds] = useState<
    string[]
  >([])
  const [googleContentNetwork, setGoogleContentNetwork] = useState(false)
  const [googleGeoMode, setGoogleGeoMode] = useState<'PRESENCE' | 'PRESENCE_OR_INTEREST'>(
    'PRESENCE_OR_INTEREST',
  )
  const [googleDeviceBidModifiers, setGoogleDeviceBidModifiers] = useState<
    Record<'DESKTOP' | 'MOBILE' | 'TABLET', string>
  >({
    DESKTOP: '1',
    MOBILE: '1',
    TABLET: '1',
  })
  const [googleAiMaxEnabled, setGoogleAiMaxEnabled] = useState(false)
  const [googleAiMaxTextCustomization, setGoogleAiMaxTextCustomization] = useState(false)
  const [googleAiMaxFinalUrlExpansion, setGoogleAiMaxFinalUrlExpansion] = useState(false)
  const [googleAiMaxDisableSearchTermMatching, setGoogleAiMaxDisableSearchTermMatching] =
    useState(false)
  const [googleAiMaxTermExclusions, setGoogleAiMaxTermExclusions] = useState('')
  const [googleAiMaxMessagingRestrictions, setGoogleAiMaxMessagingRestrictions] = useState('')
  const [googleAiMaxUrlExclusions, setGoogleAiMaxUrlExclusions] = useState('')
  const [googleAiMaxUrlInclusions, setGoogleAiMaxUrlInclusions] = useState('')
  const [googleAiMaxBrandInclusions, setGoogleAiMaxBrandInclusions] = useState<
    GoogleBrandSuggestion[]
  >([])
  const [googleAiMaxBrandExclusions, setGoogleAiMaxBrandExclusions] = useState<
    GoogleBrandSuggestion[]
  >([])
  const [googleAdSchedules, setGoogleAdSchedules] = useState<GoogleAdScheduleDraft[]>([])
  const [googleNegativeKeywords, setGoogleNegativeKeywords] = useState<
    GoogleNegativeKeywordInput[]
  >([])
  const [googleNegativeKeywordLists, setGoogleNegativeKeywordLists] = useState<
    GoogleNegativeKeywordList[]
  >([])
  const [selectedGoogleNegativeKeywordListIds, setSelectedGoogleNegativeKeywordListIds] = useState<
    string[]
  >([])

  const [metaPageId, setMetaPageId] = useState('')
  const [metaInstagramId, setMetaInstagramId] = useState('')
  const [metaOptimizationGoal, setMetaOptimizationGoal] = useState('OFFSITE_CONVERSIONS')
  const [metaBillingEvent, setMetaBillingEvent] = useState('IMPRESSIONS')
  const [metaSpecialCategory, setMetaSpecialCategory] = useState('NONE')
  const [metaPixelId, setMetaPixelId] = useState('')
  const [metaConversionMode, setMetaConversionMode] = useState<'PROVIDER' | 'STANDARD'>('PROVIDER')
  const [metaConversionId, setMetaConversionId] = useState('')
  const [metaStandardEvent, setMetaStandardEvent] = useState('LEAD')
  const [metaPlacementMode, setMetaPlacementMode] = useState<'AUTO' | 'MANUAL'>('AUTO')
  const [metaPublisherPlatforms, setMetaPublisherPlatforms] = useState<string[]>([])
  const [metaFacebookPositions, setMetaFacebookPositions] = useState<string[]>([])
  const [metaInstagramPositions, setMetaInstagramPositions] = useState<string[]>([])
  const [metaMessengerPositions, setMetaMessengerPositions] = useState<string[]>([])
  const [metaAudienceNetworkPositions, setMetaAudienceNetworkPositions] = useState<string[]>([])
  const [metaAdvantageAudience, setMetaAdvantageAudience] = useState(false)
  const [metaAdvantageAgeSuggestion, setMetaAdvantageAgeSuggestion] = useState(false)
  const [metaAdvantageGenderSuggestion, setMetaAdvantageGenderSuggestion] = useState(false)
  const [metaAdvantagePlusCreative, setMetaAdvantagePlusCreative] = useState(false)
  const [metaGenerativeText, setMetaGenerativeText] = useState(false)
  const [metaImageExpansion, setMetaImageExpansion] = useState(false)
  const [metaCatalogEnabled, setMetaCatalogEnabled] = useState(false)
  const [metaBackgroundGeneration, setMetaBackgroundGeneration] = useState(false)
  const [metaProductSetId, setMetaProductSetId] = useState('')
  const [metaExistingPostId, setMetaExistingPostId] = useState('')
  const [metaDetailedInterests, setMetaDetailedInterests] = useState<MetaTargetingInterest[]>([])
  const [showAdvancedProviderSettings, setShowAdvancedProviderSettings] = useState(false)

  const managedAccounts = useMemo(
    () =>
      accounts.filter(
        (account) =>
          account.managed &&
          account.accessible &&
          account.status !== 'CLOSED' &&
          account.status !== 'DISABLED',
      ),
    [accounts],
  )

  const editableDefinition =
    campaign == null ||
    ((campaign.status === 'DRAFT' || campaign.status === 'READY') &&
      campaign.deployments.length === 0)

  const enabledChannels = useMemo(
    () => campaign?.channels.filter((channel) => channel.enabled) ?? [],
    [campaign],
  )
  const shoppingOnly =
    selectedChannels.length > 0 && selectedChannels.every((type) => type === 'GOOGLE_SHOPPING')

  useEffect(() => {
    if (!selectedCompany) return
    void load()
  }, [selectedCompany?.companyId, campaignId])

  useEffect(() => {
    const defaults = new Set(
      Object.values(META_GOAL_EVENTS)
        .flatMap((values) => values ?? [])
        .map((value) => value.toUpperCase()),
    )
    setMetaStandardEvent((current) => {
      const normalized = current.trim().toUpperCase()
      if (normalized && !defaults.has(normalized)) return current
      return defaultMetaStandardEvent(businessGoal)
    })
  }, [businessGoal])

  const load = async () => {
    if (!selectedCompany) return
    setLoading(Boolean(campaignId))
    setError('')
    try {
      const accountData = await adCenterApi.accounts(selectedCompany.companyId)
      setAccounts(accountData)
      if (!campaignId) return

      const result = await Promise.all([
        adCenterApi.reachlyoCampaign(selectedCompany.companyId, campaignId),
        adCenterApi.reachlyoCreativePacks(selectedCompany.companyId, campaignId),
        adCenterApi.reachlyoAudienceDefinitions(selectedCompany.companyId, campaignId),
        adCenterApi.reachlyoCampaignLaunchRuns(selectedCompany.companyId, campaignId),
        adCenterApi.reachlyoConversionGoalMappings(selectedCompany.companyId, campaignId),
      ])
      const campaignData = result[0]
      const packs = result[1]
      const audienceData = result[2]
      const runs = result[3]
      const mappings = result[4]

      setCampaign(campaignData)
      setCreativePacks(packs)
      setAudiences(audienceData)
      setLaunchRuns(runs)
      setCurrentRun(runs[0] ?? null)
      hydrateCampaign(campaignData, accountData)
      hydrateCreative(packs[0], campaignData)
      hydrateAudience(audienceData[0])
      hydrateConversionMappings(mappings, campaignData.businessGoal)
    } catch (loadError) {
      setError(apiErrorMessage(loadError, 'Unable to load unified campaign'))
    } finally {
      setLoading(false)
    }
  }

  const hydrateCampaign = (value: ReachlyoCampaign, accountData: AdAccount[]) => {
    setName(value.name)
    setObjective(value.objective)
    setBusinessGoal(value.businessGoal)
    setCurrency(value.currency)
    setLandingPageUrl(value.landingPageUrl ?? '')
    setStartAt(toLocalInput(value.startAt))
    setEndAt(toLocalInput(value.endAt))

    const enabled = value.channels.filter((channel) => channel.enabled)
    setSelectedChannels(enabled.map((channel) => channel.channelType))

    const accountSelections: Record<string, string> = {}
    const allocations: Record<string, string> = {}
    value.channels.forEach((channel) => {
      const override = parseObject(channel.providerOverrideJson)
      const storedAccount = String(override._reachlyoAdAccountId ?? '')
      const eligible = accountData.filter(
        (account) => account.provider === channel.provider && account.managed && account.accessible,
      )
      accountSelections[channel.channelType] =
        eligible.find((account) => account.id === storedAccount)?.id ??
        eligible.find((account) => account.primaryAccount)?.id ??
        eligible[0]?.id ??
        ''

      if (channel.budgetAllocationMinor != null) {
        allocations[channel.channelType] = fromMinor(channel.budgetAllocationMinor, value.currency)
      } else if (channel.allocationPercent != null) {
        allocations[channel.channelType] = String(channel.allocationPercent)
      }
    })
    setAccountByChannel(accountSelections)
    setAllocationByChannel(allocations)
    const nextGoogleAudienceIds: Partial<Record<CampaignChannelType, string[]>> = {}
    const nextGoogleExcludedAudienceIds: Partial<Record<CampaignChannelType, string[]>> = {}
    enabled.forEach((channel) => {
      if (!GOOGLE_PROVIDER_AUDIENCE_CHANNELS.includes(channel.channelType)) {
        return
      }
      const override = parseObject(channel.providerOverrideJson)
      nextGoogleAudienceIds[channel.channelType] = Array.isArray(override.googleAudienceIds)
        ? override.googleAudienceIds
            .map((value) => String(value))
            .filter((value) => /^\d+$/.test(value))
        : []
      nextGoogleExcludedAudienceIds[channel.channelType] = Array.isArray(
        override.googleExcludedAudienceIds,
      )
        ? override.googleExcludedAudienceIds
            .map((value) => String(value))
            .filter((value) => /^\d+$/.test(value))
        : []
    })
    setGoogleAudienceIdsByChannel(nextGoogleAudienceIds)
    setGoogleExcludedAudienceIdsByChannel(nextGoogleExcludedAudienceIds)

    if (value.budgetPlan) {
      setBudgetAmount(fromMinor(value.budgetPlan.totalBudgetMinor, value.currency))
      setBudgetPeriod(value.budgetPlan.budgetPeriod)
      setAllocationMode(value.budgetPlan.allocationMode)
    }

    const google = enabled.find((channel) => channel.channelType === 'GOOGLE_SEARCH')
    if (google) {
      const override = parseObject(google.providerOverrideJson)
      if (typeof override.containsEuPoliticalAdvertising === 'boolean') {
        setGooglePolitical(String(override.containsEuPoliticalAdvertising) as 'true' | 'false')
      }
      if (typeof override.bidStrategy === 'string') {
        setGoogleBidStrategy(override.bidStrategy)
      }
      if (typeof override.keywordMatchType === 'string') {
        setKeywordMatchType(override.keywordMatchType)
      }
      if (typeof override.targetSearchNetwork === 'boolean') {
        setGoogleSearchPartners(override.targetSearchNetwork)
      }
      if (
        override.googleSearchAudienceMode === 'OBSERVATION' ||
        override.googleSearchAudienceMode === 'TARGETING'
      ) {
        setGoogleSearchAudienceMode(override.googleSearchAudienceMode)
      }
      setGoogleSearchInterestResources(
        Array.isArray(override.googleSearchInterestResources)
          ? override.googleSearchInterestResources
              .map((value) => String(value))
              .filter((value) => /^customers\/\d+\/userInterests\/\d+$/.test(value))
          : [],
      )
      setGoogleSearchExcludedInterestResources(
        Array.isArray(override.googleSearchExcludedInterestResources)
          ? override.googleSearchExcludedInterestResources
              .map((value) => String(value))
              .filter((value) => /^customers\/\d+\/userInterests\/\d+$/.test(value))
          : [],
      )
      setGoogleParentalStatusIncludes(
        Array.isArray(override.googleParentalStatusIncludes)
          ? override.googleParentalStatusIncludes.map(String)
          : [],
      )
      setGoogleParentalStatusExcludes(
        Array.isArray(override.googleParentalStatusExcludes)
          ? override.googleParentalStatusExcludes.map(String)
          : [],
      )
      setGoogleIncomeRangeIncludes(
        Array.isArray(override.googleIncomeRangeIncludes)
          ? override.googleIncomeRangeIncludes.map(String)
          : [],
      )
      setGoogleIncomeRangeExcludes(
        Array.isArray(override.googleIncomeRangeExcludes)
          ? override.googleIncomeRangeExcludes.map(String)
          : [],
      )
      setGoogleDetailedDemographicIds(
        Array.isArray(override.googleDetailedDemographicIds)
          ? override.googleDetailedDemographicIds
              .map((value) => String(value))
              .filter((value) => /^\d+$/.test(value))
          : [],
      )
      setGoogleExcludedDetailedDemographicIds(
        Array.isArray(override.googleExcludedDetailedDemographicIds)
          ? override.googleExcludedDetailedDemographicIds
              .map((value) => String(value))
              .filter((value) => /^\d+$/.test(value))
          : [],
      )
      if (typeof override.targetContentNetwork === 'boolean') {
        setGoogleContentNetwork(override.targetContentNetwork)
      }
      if (
        override.positiveGeoTargetType === 'PRESENCE' ||
        override.positiveGeoTargetType === 'PRESENCE_OR_INTEREST'
      ) {
        setGoogleGeoMode(override.positiveGeoTargetType)
      }
      if (
        override.deviceBidModifiers &&
        typeof override.deviceBidModifiers === 'object' &&
        !Array.isArray(override.deviceBidModifiers)
      ) {
        const devices = override.deviceBidModifiers as Record<string, unknown>
        setGoogleDeviceBidModifiers({
          DESKTOP: typeof devices.DESKTOP === 'number' ? String(devices.DESKTOP) : '1',
          MOBILE: typeof devices.MOBILE === 'number' ? String(devices.MOBILE) : '1',
          TABLET: typeof devices.TABLET === 'number' ? String(devices.TABLET) : '1',
        })
      }
      setGoogleAiMaxEnabled(override.aiMaxEnabled === true)
      setGoogleAiMaxTextCustomization(override.aiMaxTextCustomizationEnabled === true)
      setGoogleAiMaxFinalUrlExpansion(override.aiMaxFinalUrlExpansionEnabled === true)
      setGoogleAiMaxDisableSearchTermMatching(override.aiMaxDisableSearchTermMatching === true)
      setGoogleAiMaxTermExclusions(
        Array.isArray(override.aiMaxTermExclusions)
          ? override.aiMaxTermExclusions
              .filter((value): value is string => typeof value === 'string')
              .join('\n')
          : '',
      )
      setGoogleAiMaxMessagingRestrictions(
        Array.isArray(override.aiMaxMessagingRestrictions)
          ? override.aiMaxMessagingRestrictions
              .filter((value): value is string => typeof value === 'string')
              .join('\n')
          : '',
      )
      setGoogleAiMaxUrlExclusions(
        Array.isArray(override.aiMaxUrlExclusions)
          ? override.aiMaxUrlExclusions
              .filter((value): value is string => typeof value === 'string')
              .join('\n')
          : '',
      )
      setGoogleAiMaxUrlInclusions(
        Array.isArray(override.aiMaxUrlInclusions)
          ? override.aiMaxUrlInclusions
              .filter((value): value is string => typeof value === 'string')
              .join('\n')
          : '',
      )
      setGoogleAiMaxBrandInclusions(
        Array.isArray(override.aiMaxBrandInclusions)
          ? override.aiMaxBrandInclusions.filter((value): value is GoogleBrandSuggestion =>
              Boolean(
                value &&
                typeof value === 'object' &&
                typeof (value as GoogleBrandSuggestion).id === 'string' &&
                typeof (value as GoogleBrandSuggestion).name === 'string',
              ),
            )
          : [],
      )
      setGoogleAiMaxBrandExclusions(
        Array.isArray(override.aiMaxBrandExclusions)
          ? override.aiMaxBrandExclusions.filter((value): value is GoogleBrandSuggestion =>
              Boolean(
                value &&
                typeof value === 'object' &&
                typeof (value as GoogleBrandSuggestion).id === 'string' &&
                typeof (value as GoogleBrandSuggestion).name === 'string',
              ),
            )
          : [],
      )
      const savedSchedules = Array.isArray(override.adSchedules) ? override.adSchedules : []
      setGoogleAdSchedules(
        savedSchedules.flatMap((value) => {
          if (!value || typeof value !== 'object' || Array.isArray(value)) return []
          const schedule = value as Record<string, unknown>
          if (
            typeof schedule.dayOfWeek !== 'string' ||
            typeof schedule.startTime !== 'string' ||
            typeof schedule.endTime !== 'string'
          ) {
            return []
          }
          return [
            {
              dayOfWeek: schedule.dayOfWeek,
              startTime: schedule.startTime,
              endTime: schedule.endTime,
            },
          ]
        }),
      )
      const savedNegativeKeywords = Array.isArray(override.negativeKeywords)
        ? override.negativeKeywords
        : []
      setGoogleNegativeKeywords(
        savedNegativeKeywords.flatMap((value) => {
          if (!value || typeof value !== 'object' || Array.isArray(value)) return []
          const keyword = value as Record<string, unknown>
          if (
            typeof keyword.text !== 'string' ||
            !['BROAD', 'PHRASE', 'EXACT'].includes(String(keyword.matchType))
          ) {
            return []
          }
          return [
            {
              text: keyword.text,
              matchType: String(keyword.matchType) as 'BROAD' | 'PHRASE' | 'EXACT',
            },
          ]
        }),
      )
      setSelectedGoogleNegativeKeywordListIds(
        Array.isArray(override.negativeKeywordSharedSetIds)
          ? override.negativeKeywordSharedSetIds.filter(
              (value): value is string => typeof value === 'string',
            )
          : [],
      )
    }

    const pmax = enabled.find((channel) => channel.channelType === 'GOOGLE_PERFORMANCE_MAX')
    if (pmax) {
      const override = parseObject(pmax.providerOverrideJson)
      if (typeof override.containsEuPoliticalAdvertising === 'boolean') {
        setGooglePolitical(String(override.containsEuPoliticalAdvertising) as 'true' | 'false')
      }
      if (
        override.bidStrategy === 'MAXIMIZE_CONVERSIONS' ||
        override.bidStrategy === 'MAXIMIZE_CONVERSION_VALUE'
      ) {
        setPmaxBidStrategy(override.bidStrategy)
      }
      setPmaxYoutubeVideoIds(
        Array.isArray(override.pmaxYoutubeVideoIds)
          ? override.pmaxYoutubeVideoIds
              .filter((value): value is string => typeof value === 'string')
              .join('\n')
          : '',
      )
      setPmaxBrandGuidelinesEnabled(
        typeof override.pmaxBrandGuidelinesEnabled === 'boolean'
          ? override.pmaxBrandGuidelinesEnabled
          : true,
      )
      setPmaxBrandMainColor(
        typeof override.pmaxBrandMainColor === 'string' ? override.pmaxBrandMainColor : '',
      )
      setPmaxBrandAccentColor(
        typeof override.pmaxBrandAccentColor === 'string' ? override.pmaxBrandAccentColor : '',
      )
      setPmaxBrandFontFamily(
        typeof override.pmaxBrandFontFamily === 'string' ? override.pmaxBrandFontFamily : '',
      )
      setPmaxMerchantId(typeof override.pmaxMerchantId === 'string' ? override.pmaxMerchantId : '')
      setPmaxFeedLabel(typeof override.pmaxFeedLabel === 'string' ? override.pmaxFeedLabel : '')
      setPmaxLifecycleGoalType(
        override.pmaxLifecycleGoalType === 'NEW_CUSTOMER_ACQUISITION' ||
          override.pmaxLifecycleGoalType === 'CUSTOMER_RETENTION' ||
          override.pmaxLifecycleGoalType === 'LOYALTY_RETENTION'
          ? override.pmaxLifecycleGoalType
          : '',
      )
      setPmaxLifecycleTargetOption(
        override.pmaxLifecycleTargetOption === 'TARGET_SPECIFIC' ? 'TARGET_SPECIFIC' : 'TARGET_ALL',
      )
      setPmaxLifecycleAdditionalValue(
        typeof override.pmaxLifecycleAdditionalValue === 'number'
          ? String(override.pmaxLifecycleAdditionalValue)
          : '',
      )
      setPmaxLifecycleHighLifetimeValue(
        typeof override.pmaxLifecycleHighLifetimeValue === 'number'
          ? String(override.pmaxLifecycleHighLifetimeValue)
          : '',
      )
      setPmaxLifecycleValueMultiplier(
        typeof override.pmaxLifecycleValueMultiplier === 'number'
          ? String(override.pmaxLifecycleValueMultiplier)
          : '',
      )
      setPmaxLifecycleEnableBidAdjustmentsForLoyaltyMembers(
        override.pmaxLifecycleEnableBidAdjustmentsForLoyaltyMembers === true,
      )
      setPmaxLifecycleShowTargetedLoyaltyMemberBenefitsInPla(
        override.pmaxLifecycleShowTargetedLoyaltyMemberBenefitsInPla === true,
      )
    }

    const demandGen = enabled.find((channel) => channel.channelType === 'GOOGLE_DEMAND_GEN')
    if (demandGen) {
      const override = parseObject(demandGen.providerOverrideJson)
      if (typeof override.containsEuPoliticalAdvertising === 'boolean') {
        setGooglePolitical(String(override.containsEuPoliticalAdvertising) as 'true' | 'false')
      }
      if (
        override.bidStrategy === 'MAXIMIZE_CLICKS' ||
        override.bidStrategy === 'MAXIMIZE_CONVERSIONS' ||
        override.bidStrategy === 'MAXIMIZE_CONVERSION_VALUE'
      ) {
        setDemandGenBidStrategy(override.bidStrategy)
      }
      if (
        override.demandGenChannelStrategy === 'ALL_CHANNELS' ||
        override.demandGenChannelStrategy === 'ALL_OWNED_AND_OPERATED_CHANNELS'
      ) {
        setDemandGenChannelStrategy(override.demandGenChannelStrategy)
      }
    }

    const shopping = enabled.find((channel) => channel.channelType === 'GOOGLE_SHOPPING')
    if (shopping) {
      const override = parseObject(shopping.providerOverrideJson)
      if (typeof override.containsEuPoliticalAdvertising === 'boolean') {
        setGooglePolitical(String(override.containsEuPoliticalAdvertising) as 'true' | 'false')
      }
      if (typeof override.merchantId === 'string') {
        setShoppingMerchantId(override.merchantId)
      }
      if (
        override.campaignPriority === 0 ||
        override.campaignPriority === 1 ||
        override.campaignPriority === 2
      ) {
        setShoppingPriority(String(override.campaignPriority) as '0' | '1' | '2')
      }
      setShoppingEnableLocal(override.enableLocal === true)
    }

    const display = enabled.find((channel) => channel.channelType === 'GOOGLE_DISPLAY')
    if (display) {
      const override = parseObject(display.providerOverrideJson)
      if (typeof override.containsEuPoliticalAdvertising === 'boolean') {
        setGooglePolitical(String(override.containsEuPoliticalAdvertising) as 'true' | 'false')
      }
      if (
        override.bidStrategy === 'MANUAL_CPC' ||
        override.bidStrategy === 'MAXIMIZE_CONVERSIONS' ||
        override.bidStrategy === 'TARGET_CPA'
      ) {
        setDisplayBidStrategy(override.bidStrategy)
      }
      if (typeof override.targetCpaMinor === 'number') {
        setDisplayTargetCpa(fromMinor(override.targetCpaMinor, value.currency))
      }
      if (typeof override.adGroupCpcBidMinor === 'number') {
        setDisplayCpcBid(fromMinor(override.adGroupCpcBidMinor, value.currency))
      }
    }

    const meta = enabled.find((channel) => channel.channelType === 'META_ADS')
    if (meta) {
      const override = parseObject(meta.providerOverrideJson)
      if (typeof override.pageId === 'string') setMetaPageId(override.pageId)
      if (typeof override.instagramActorId === 'string') {
        setMetaInstagramId(override.instagramActorId)
      }
      if (typeof override.optimizationGoal === 'string') {
        setMetaOptimizationGoal(override.optimizationGoal)
      }
      if (typeof override.billingEvent === 'string') {
        setMetaBillingEvent(override.billingEvent)
      }
      if (
        Array.isArray(override.specialAdCategories) &&
        typeof override.specialAdCategories[0] === 'string'
      ) {
        setMetaSpecialCategory(override.specialAdCategories[0])
      }
      if (typeof override.pixelId === 'string') setMetaPixelId(override.pixelId)
      const publisherPlatforms = Array.isArray(override.publisherPlatforms)
        ? override.publisherPlatforms.filter((value): value is string => typeof value === 'string')
        : []
      const advantagePlacements =
        typeof override.advantagePlacements === 'boolean'
          ? override.advantagePlacements
          : publisherPlatforms.length === 0
      setMetaPlacementMode(advantagePlacements ? 'AUTO' : 'MANUAL')
      setMetaPublisherPlatforms(publisherPlatforms)
      setMetaFacebookPositions(
        Array.isArray(override.facebookPositions)
          ? override.facebookPositions.filter((value): value is string => typeof value === 'string')
          : [],
      )
      setMetaInstagramPositions(
        Array.isArray(override.instagramPositions)
          ? override.instagramPositions.filter(
              (value): value is string => typeof value === 'string',
            )
          : [],
      )
      setMetaMessengerPositions(
        Array.isArray(override.messengerPositions)
          ? override.messengerPositions.filter(
              (value): value is string => typeof value === 'string',
            )
          : [],
      )
      setMetaAudienceNetworkPositions(
        Array.isArray(override.audienceNetworkPositions)
          ? override.audienceNetworkPositions.filter(
              (value): value is string => typeof value === 'string',
            )
          : [],
      )
      setMetaAdvantageAudience(override.advantageAudience === true)
      setMetaAdvantageAgeSuggestion(override.advantageAgeSuggestion === true)
      setMetaAdvantageGenderSuggestion(override.advantageGenderSuggestion === true)
      setMetaAdvantagePlusCreative(override.advantagePlusCreative === true)
      setMetaGenerativeText(override.generativeText === true)
      setMetaImageExpansion(override.imageExpansion === true)
      setMetaCatalogEnabled(
        typeof override.productSetId === 'string' && override.productSetId.length > 0,
      )
      setMetaBackgroundGeneration(override.backgroundGeneration === true)
      setMetaProductSetId(typeof override.productSetId === 'string' ? override.productSetId : '')
      setMetaExistingPostId(
        typeof override.existingPostId === 'string' ? override.existingPostId : '',
      )
      setMetaDetailedInterests(
        Array.isArray(override.detailedInterests)
          ? override.detailedInterests.flatMap((value) => {
              if (!value || typeof value !== 'object' || Array.isArray(value)) return []
              const interest = value as Record<string, unknown>
              return typeof interest.id === 'string' && typeof interest.name === 'string'
                ? [{ id: interest.id, name: interest.name }]
                : []
            })
          : [],
      )
    }
  }

  const hydrateConversionMappings = (
    mappings: ReachlyoConversionGoalMapping[],
    currentBusinessGoal: CampaignBusinessGoal,
  ) => {
    const current = mappings.filter((mapping) => mapping.businessGoal === currentBusinessGoal)
    const google = current.find(
      (mapping) =>
        mapping.provider === 'GOOGLE_ADS' && mapping.targetType === 'PROVIDER_CONVERSION',
    )
    setGoogleConversionId(google?.adConversionId ?? '')

    const meta = current.find((mapping) => mapping.provider === 'META_ADS')
    if (meta?.targetType === 'PROVIDER_CONVERSION') {
      setMetaConversionMode('PROVIDER')
      setMetaConversionId(meta.adConversionId ?? '')
    } else if (meta?.targetType === 'STANDARD_EVENT') {
      setMetaConversionMode('STANDARD')
      setMetaConversionId('')
      setMetaStandardEvent(
        meta.providerEventName ?? defaultMetaStandardEvent(currentBusinessGoal) ?? '',
      )
    } else {
      setMetaConversionMode('PROVIDER')
      setMetaConversionId('')
      setMetaStandardEvent(defaultMetaStandardEvent(currentBusinessGoal) ?? '')
    }
  }

  const hydrateCreative = (pack: ReachlyoCreativePack | undefined, value: ReachlyoCampaign) => {
    if (!pack) {
      setCreativeUrl(value.landingPageUrl ?? '')
      return
    }
    setCreativeName(pack.name)
    setBrandName(pack.businessName ?? '')
    setPrimaryMessage(pack.primaryMessage ?? '')
    setCallToAction(pack.callToAction ?? 'LEARN_MORE')
    setCreativeUrl(pack.destinationUrl ?? value.landingPageUrl ?? '')
    setHeadlines(parseArray<string>(pack.headlinesJson).join('\n'))
    setDescriptions(parseArray<string>(pack.descriptionsJson).join('\n'))
  }

  const hydrateAudience = (audience: ReachlyoAudienceDefinition | undefined) => {
    if (!audience) return
    setAudienceName(audience.name)
    const locations = parseArray<ReachlyoAudienceLocationInput>(audience.locationsJson)
    setAudienceLocations(locations.length > 0 ? locations : [{ countryCode: 'IN' }])
    setLanguages(parseArray<string>(audience.languagesJson).join(', '))
    setAgeMin(audience.ageMin == null ? '' : String(audience.ageMin))
    setAgeMax(audience.ageMax == null ? '' : String(audience.ageMax))
    setGenders(parseArray<string>(audience.gendersJson))
    setInterests(parseArray<string>(audience.interestsJson).join(', '))
    setSearchIntents(parseArray<string>(audience.searchIntentJson).join('\n'))
  }

  const saveBasics = async () => {
    if (!selectedCompany || !canEdit) return
    if (!name.trim()) {
      setError('Campaign name is required.')
      return
    }
    setBusy(true)
    setError('')
    setNotice('')
    try {
      const input = {
        name: name.trim(),
        objective,
        businessGoal,
        landingPageUrl: landingPageUrl.trim() || null,
        currency: currency.trim().toUpperCase(),
        startAt: fromLocalInput(startAt),
        endAt: fromLocalInput(endAt),
      }
      const saved = campaign
        ? await adCenterApi.updateReachlyoCampaign(selectedCompany.companyId, campaign.id, input)
        : await adCenterApi.createReachlyoCampaign(selectedCompany.companyId, input)
      setCampaign(saved)
      if (!campaign) {
        navigate('/ad-center/unified-campaigns/' + saved.id, { replace: true })
      }
      setNotice('Campaign goal saved.')
      setStep(1)
    } catch (saveError) {
      setError(apiErrorMessage(saveError, 'Unable to save campaign goal'))
    } finally {
      setBusy(false)
    }
  }

  const saveChannels = async () => {
    if (!selectedCompany || !campaign || !canEdit) return
    if (selectedChannels.length === 0) {
      setError('Select at least one channel.')
      return
    }
    for (const type of selectedChannels) {
      if (!accountByChannel[type]) {
        setError('Choose an advertising account for ' + pretty(type) + '.')
        return
      }
    }

    setBusy(true)
    setError('')
    setNotice('')
    try {
      let latest = campaign
      for (const type of selectedChannels) {
        const existing = latest.channels.find((channel) => channel.channelType === type)
        const override = {
          ...parseObject(existing?.providerOverrideJson),
          _reachlyoAdAccountId: accountByChannel[type],
        }
        latest = existing
          ? await adCenterApi.updateReachlyoCampaignChannel(
              selectedCompany.companyId,
              campaign.id,
              existing.id,
              {
                enabled: true,
                providerOverrideJson: JSON.stringify(override),
              },
            )
          : await adCenterApi.addReachlyoCampaignChannel(selectedCompany.companyId, campaign.id, {
              channelType: type,
              enabled: true,
              providerOverrideJson: JSON.stringify(override),
            })
      }

      for (const existing of latest.channels.filter(
        (channel) => channel.enabled && !selectedChannels.includes(channel.channelType),
      )) {
        latest = await adCenterApi.updateReachlyoCampaignChannel(
          selectedCompany.companyId,
          campaign.id,
          existing.id,
          { enabled: false },
        )
      }

      setCampaign(latest)
      setNotice('Channels and provider accounts saved.')
      setStep(2)
    } catch (saveError) {
      setError(apiErrorMessage(saveError, 'Unable to save campaign channels'))
    } finally {
      setBusy(false)
    }
  }

  const saveBudget = async () => {
    if (!selectedCompany || !campaign || !canEdit) return
    const totalBudgetMinor = toMinor(budgetAmount, campaign.currency)
    if (totalBudgetMinor <= 0) {
      setError('Enter a positive campaign budget.')
      return
    }

    setBusy(true)
    setError('')
    setNotice('')
    try {
      let latest = await adCenterApi.upsertReachlyoBudgetPlan(
        selectedCompany.companyId,
        campaign.id,
        {
          totalBudgetMinor,
          budgetPeriod,
          allocationMode,
        },
      )

      const enabled = latest.channels.filter((channel) => channel.enabled)
      const defaultPercent = enabled.length ? Math.floor(10000 / enabled.length) / 100 : 100
      const defaultFixed = enabled.length
        ? Math.floor(totalBudgetMinor / enabled.length)
        : totalBudgetMinor

      if (allocationMode !== 'AUTOMATIC') {
        for (const channel of enabled) {
          const raw = allocationByChannel[channel.channelType]
          const fallback =
            allocationMode === 'PERCENTAGE'
              ? String(defaultPercent)
              : fromMinor(defaultFixed, campaign.currency)
          const value = raw && Number(raw) > 0 ? raw : fallback
          latest = await adCenterApi.updateReachlyoCampaignChannel(
            selectedCompany.companyId,
            campaign.id,
            channel.id,
            allocationMode === 'PERCENTAGE'
              ? { allocationPercent: Number(value) }
              : {
                  budgetAllocationMinor: toMinor(value, campaign.currency),
                },
          )
        }
      }

      setCampaign(latest)
      hydrateCampaign(latest, accounts)
      setNotice('Campaign budget saved.')
      setStep(3)
    } catch (saveError) {
      setError(apiErrorMessage(saveError, 'Unable to save campaign budget'))
    } finally {
      setBusy(false)
    }
  }

  const saveCreative = async () => {
    if (!selectedCompany || !campaign || !canEdit) return
    if (shoppingOnly) {
      setError('')
      setNotice(
        'Google Shopping uses Merchant Center product data as creative; no Creative Pack is required.',
      )
      setStep(4)
      return
    }
    if (!creativeName.trim()) {
      setError('Creative pack name is required.')
      return
    }

    setBusy(true)
    setError('')
    setNotice('')
    try {
      let assets = creativePacks[0]?.assets.map((asset) => ({
        mediaId: asset.mediaId,
        role: asset.role,
        label: asset.label,
        sortOrder: asset.sortOrder,
      }))

      if (creativeFile) {
        setUploadProgress(1)
        const uploaded = await mediaApi.uploadAdCreative(
          creativeFile,
          selectedCompany.companyId,
          setUploadProgress,
        )
        assets = [
          ...(assets ?? []).filter((asset) => asset.role !== 'IMAGE'),
          {
            mediaId: uploaded.mediaId,
            role: 'IMAGE' as const,
            label: 'Primary image',
            sortOrder: 0,
          },
        ]
      }

      const uploadRole = async (
        file: File | null,
        role:
          | 'MARKETING_IMAGE'
          | 'SQUARE_MARKETING_IMAGE'
          | 'PORTRAIT_MARKETING_IMAGE'
          | 'LOGO'
          | 'LANDSCAPE_LOGO',
        label: string,
        sortOrder: number,
      ) => {
        if (!file) return
        setUploadProgress(1)
        const uploaded = await mediaApi.uploadAdCreative(
          file,
          selectedCompany.companyId,
          setUploadProgress,
        )
        assets = [
          ...(assets ?? []).filter((asset) => asset.role !== role),
          {
            mediaId: uploaded.mediaId,
            role,
            label,
            sortOrder,
          },
        ]
      }

      await uploadRole(pmaxLandscapeFile, 'MARKETING_IMAGE', 'PMax landscape image', 10)
      await uploadRole(pmaxSquareFile, 'SQUARE_MARKETING_IMAGE', 'PMax square image', 11)
      await uploadRole(pmaxLogoFile, 'LOGO', 'PMax square logo', 12)
      await uploadRole(pmaxPortraitFile, 'PORTRAIT_MARKETING_IMAGE', 'PMax portrait image', 13)
      await uploadRole(pmaxLandscapeLogoFile, 'LANDSCAPE_LOGO', 'PMax landscape logo', 14)

      const input = {
        name: creativeName.trim(),
        businessName: brandName.trim() || null,
        primaryMessage: primaryMessage.trim() || null,
        callToAction: callToAction || null,
        destinationUrl: creativeUrl.trim() || campaign.landingPageUrl || null,
        headlines: listValues(headlines),
        descriptions: listValues(descriptions),
        assets,
      }

      const pack = creativePacks[0]
        ? await adCenterApi.updateReachlyoCreativePack(
            selectedCompany.companyId,
            campaign.id,
            creativePacks[0].id,
            input,
          )
        : await adCenterApi.createReachlyoCreativePack(
            selectedCompany.companyId,
            campaign.id,
            input,
          )

      setCreativePacks([pack])
      setCreativeFile(null)
      setPmaxLandscapeFile(null)
      setPmaxSquareFile(null)
      setPmaxLogoFile(null)
      setPmaxPortraitFile(null)
      setPmaxLandscapeLogoFile(null)
      setUploadProgress(0)
      setNotice('Canonical creative pack saved.')
      setStep(4)
    } catch (saveError) {
      setError(apiErrorMessage(saveError, 'Unable to save creative pack'))
    } finally {
      setBusy(false)
    }
  }

  const saveAudience = async () => {
    if (!selectedCompany || !campaign || !canEdit) return
    if (!audienceName.trim()) {
      setError('Audience name is required.')
      return
    }

    const normalizedLocations = audienceLocations
      .map((location) => ({
        countryCode: location.countryCode.trim().toUpperCase(),
        region: location.region?.trim() || null,
        city: location.city?.trim() || null,
        postalCode: location.postalCode?.trim() || null,
        latitude: location.latitude ?? null,
        longitude: location.longitude ?? null,
        radiusKm: location.radiusKm ?? null,
      }))
      .filter((location) => Boolean(location.countryCode))

    if (normalizedLocations.length === 0) {
      setError('Add at least one audience location.')
      return
    }
    if (normalizedLocations.some((location) => !/^[A-Z]{2}$/.test(location.countryCode))) {
      setError('Audience country codes must use two-letter ISO codes such as IN or US.')
      return
    }
    if (
      normalizedLocations.some(
        (location) => (location.latitude == null) !== (location.longitude == null),
      )
    ) {
      setError('Latitude and longitude must be supplied together.')
      return
    }

    setBusy(true)
    setError('')
    setNotice('')
    try {
      const input = {
        name: audienceName.trim(),
        ageMin: ageMin ? Number(ageMin) : null,
        ageMax: ageMax ? Number(ageMax) : null,
        locations: normalizedLocations,
        languages: listValues(languages),
        genders,
        interests: listValues(interests),
        searchIntents: listValues(searchIntents),
      }

      const audience = audiences[0]
        ? await adCenterApi.updateReachlyoAudienceDefinition(
            selectedCompany.companyId,
            campaign.id,
            audiences[0].id,
            input,
          )
        : await adCenterApi.createReachlyoAudienceDefinition(
            selectedCompany.companyId,
            campaign.id,
            input,
          )

      setAudiences([audience])
      setNotice('Canonical audience saved.')
      setStep(5)
      await loadProviderData()
    } catch (saveError) {
      setError(apiErrorMessage(saveError, 'Unable to save audience'))
    } finally {
      setBusy(false)
    }
  }

  const loadProviderData = async (syncProvider = false) => {
    if (!selectedCompany) return
    const accountIds = [
      ...new Set(
        selectedChannels
          .map((type) => accountByChannel[type])
          .filter((value): value is string => Boolean(value)),
      ),
    ]

    const nextResources: Record<string, AdAccountResource[]> = {
      ...resourcesByAccount,
    }
    const nextConversions: Record<string, AdConversion[]> = {
      ...conversionsByAccount,
    }
    const nextAudiences: Record<string, AdAudience[]> = {
      ...audiencesByAccount,
    }
    let nextGoogleNegativeKeywordLists = googleNegativeKeywordLists

    await Promise.all(
      accountIds.map(async (accountId) => {
        const account = managedAccounts.find((item) => item.id === accountId)
        if (syncProvider && account?.provider === 'GOOGLE_ADS') {
          await Promise.all([
            adCenterApi.syncGoogleConversions(selectedCompany.companyId, accountId),
            adCenterApi.syncGoogleAudiences(selectedCompany.companyId, accountId),
          ])
        }
        if (syncProvider && account?.provider === 'META_ADS') {
          await Promise.all([
            adCenterApi.syncMetaResources(selectedCompany.companyId, accountId),
            adCenterApi.syncMetaConversions(selectedCompany.companyId, accountId),
          ])
        }

        const conversionData = await adCenterApi.conversions(selectedCompany.companyId, accountId)
        nextConversions[accountId] = conversionData.filter(
          (item) => item.accessible && !item.archived && !item.unavailable,
        )

        if (account?.provider === 'GOOGLE_ADS') {
          const [negativeKeywordLists, audienceLists] = await Promise.all([
            adCenterApi.googleNegativeKeywordLists(selectedCompany.companyId, accountId),
            adCenterApi.audiences(selectedCompany.companyId, accountId),
          ])
          nextGoogleNegativeKeywordLists = negativeKeywordLists
          nextAudiences[accountId] = audienceLists.filter((item) => item.accessible)
        }

        if (account?.provider === 'META_ADS') {
          const resourceData = await adCenterApi.resources(selectedCompany.companyId, accountId)
          nextResources[accountId] = resourceData.filter((item) => item.accessible)
        }
      }),
    )

    setResourcesByAccount(nextResources)
    setConversionsByAccount(nextConversions)
    setAudiencesByAccount(nextAudiences)
    setGoogleNegativeKeywordLists(nextGoogleNegativeKeywordLists)

    const googleAccount =
      accountByChannel.GOOGLE_SEARCH ??
      accountByChannel.GOOGLE_PERFORMANCE_MAX ??
      accountByChannel.GOOGLE_DEMAND_GEN ??
      accountByChannel.GOOGLE_SHOPPING ??
      accountByChannel.GOOGLE_DISPLAY
    if (googleAccount && !googleConversionId && nextConversions[googleAccount]?.length) {
      setGoogleConversionId(
        recommendedConversion(nextConversions[googleAccount], businessGoal, 'GOOGLE_ADS')?.id ?? '',
      )
    }

    const pmaxAccount = accountByChannel.GOOGLE_PERFORMANCE_MAX
    if (pmaxAccount) {
      try {
        const merchants = await adCenterApi.googleMerchantAccounts(
          selectedCompany.companyId,
          pmaxAccount,
        )
        setPmaxMerchantAccounts(merchants)
        setPmaxMerchantId((current) =>
          current && merchants.some((merchant) => merchant.accountId === current) ? current : '',
        )
      } catch {
        setPmaxMerchantAccounts([])
        setPmaxMerchantId('')
        setPmaxFeedLabel('')
      }
    } else {
      setPmaxMerchantAccounts([])
      setPmaxMerchantId('')
      setPmaxFeedLabel('')
    }

    const shoppingAccount = accountByChannel.GOOGLE_SHOPPING
    if (shoppingAccount) {
      try {
        const merchants = await adCenterApi.googleMerchantAccounts(
          selectedCompany.companyId,
          shoppingAccount,
        )
        setShoppingMerchantAccounts(merchants)
        setShoppingMerchantId((current) => current || merchants[0]?.accountId || '')
      } catch {
        setShoppingMerchantAccounts([])
      }
    } else {
      setShoppingMerchantAccounts([])
    }

    const metaAccount = accountByChannel.META_ADS
    if (metaAccount) {
      if (!metaConversionId && nextConversions[metaAccount]?.length) {
        setMetaConversionId(
          recommendedConversion(nextConversions[metaAccount], businessGoal, 'META_ADS')?.id ?? '',
        )
      }
      const resources = nextResources[metaAccount] ?? []
      if (!metaPageId) {
        setMetaPageId(
          resources.find((item) => item.resourceType === 'FACEBOOK_PAGE')?.externalResourceId ?? '',
        )
      }
      if (!metaInstagramId) {
        setMetaInstagramId(
          resources.find((item) => item.resourceType === 'INSTAGRAM_ACCOUNT')?.externalResourceId ??
            '',
        )
      }
      if (!metaPixelId) {
        setMetaPixelId(
          resources.find((item) => item.resourceType === 'META_PIXEL')?.externalResourceId ?? '',
        )
      }
      if (!metaProductSetId) {
        setMetaProductSetId(
          resources.find((item) => item.resourceType === 'META_PRODUCT_SET')?.externalResourceId ??
            '',
        )
      }
    }
  }

  const saveProviderSettings = async () => {
    if (!selectedCompany || !campaign || !canEdit) return
    setBusy(true)
    setError('')
    setNotice('')

    try {
      let latest = campaign
      for (const channel of latest.channels.filter((item) => item.enabled)) {
        const accountId = accountByChannel[channel.channelType]
        let override: Record<string, unknown> = {
          ...parseObject(channel.providerOverrideJson),
          _reachlyoAdAccountId: accountId,
        }

        if (GOOGLE_PROVIDER_AUDIENCE_CHANNELS.includes(channel.channelType)) {
          override = {
            ...override,
            googleAudienceIds: googleAudienceIdsByChannel[channel.channelType] ?? [],
            googleExcludedAudienceIds:
              channel.channelType === 'GOOGLE_PERFORMANCE_MAX'
                ? []
                : (googleExcludedAudienceIdsByChannel[channel.channelType] ?? []),
          }
        }

        if (channel.channelType === 'GOOGLE_DISPLAY') {
          if (!googlePolitical) {
            throw new Error('Choose the Google EU political advertising declaration.')
          }
          if (displayBidStrategy === 'TARGET_CPA' && !(Number(displayTargetCpa) > 0)) {
            throw new Error('Enter a positive Display target CPA.')
          }
          override = {
            ...override,
            containsEuPoliticalAdvertising: googlePolitical === 'true',
            bidStrategy: displayBidStrategy,
            targetCpaMinor:
              displayBidStrategy === 'TARGET_CPA'
                ? toMinor(displayTargetCpa, campaign.currency)
                : undefined,
            adGroupCpcBidMinor:
              displayBidStrategy === 'MANUAL_CPC' && Number(displayCpcBid) > 0
                ? toMinor(displayCpcBid, campaign.currency)
                : undefined,
          }
        }

        if (channel.channelType === 'GOOGLE_SHOPPING') {
          if (!googlePolitical) {
            throw new Error('Choose the Google EU political advertising declaration.')
          }
          if (!shoppingMerchantId) {
            throw new Error('Choose a Merchant Center account for Google Shopping.')
          }
          override = {
            ...override,
            containsEuPoliticalAdvertising: googlePolitical === 'true',
            merchantId: shoppingMerchantId,
            campaignPriority: Number(shoppingPriority),
            enableLocal: shoppingEnableLocal,
          }
        }

        if (channel.channelType === 'GOOGLE_PERFORMANCE_MAX') {
          if (!googlePolitical) {
            throw new Error('Choose the Google EU political advertising declaration.')
          }
          override = {
            ...override,
            containsEuPoliticalAdvertising: googlePolitical === 'true',
            bidStrategy: pmaxBidStrategy,
            pmaxYoutubeVideoIds: listValues(pmaxYoutubeVideoIds),
            pmaxBrandGuidelinesEnabled,
            pmaxBrandMainColor:
              pmaxBrandGuidelinesEnabled && pmaxBrandMainColor.trim()
                ? pmaxBrandMainColor.trim()
                : undefined,
            pmaxBrandAccentColor:
              pmaxBrandGuidelinesEnabled && pmaxBrandAccentColor.trim()
                ? pmaxBrandAccentColor.trim()
                : undefined,
            pmaxBrandFontFamily:
              pmaxBrandGuidelinesEnabled && pmaxBrandFontFamily ? pmaxBrandFontFamily : undefined,
            pmaxMerchantId: pmaxMerchantId || undefined,
            pmaxFeedLabel:
              pmaxMerchantId && pmaxFeedLabel.trim()
                ? pmaxFeedLabel.trim().toUpperCase()
                : undefined,
            pmaxLifecycleGoalType: pmaxLifecycleGoalType || undefined,
            pmaxLifecycleTargetOption:
              pmaxLifecycleGoalType && pmaxLifecycleGoalType !== 'LOYALTY_RETENTION'
                ? pmaxLifecycleTargetOption
                : undefined,
            pmaxLifecycleAdditionalValue:
              pmaxLifecycleGoalType &&
              pmaxLifecycleGoalType !== 'LOYALTY_RETENTION' &&
              pmaxLifecycleTargetOption === 'TARGET_ALL' &&
              pmaxLifecycleAdditionalValue.trim()
                ? Number(pmaxLifecycleAdditionalValue)
                : undefined,
            pmaxLifecycleHighLifetimeValue:
              pmaxLifecycleGoalType &&
              pmaxLifecycleGoalType !== 'LOYALTY_RETENTION' &&
              pmaxLifecycleTargetOption === 'TARGET_ALL' &&
              pmaxLifecycleHighLifetimeValue.trim()
                ? Number(pmaxLifecycleHighLifetimeValue)
                : undefined,
            pmaxLifecycleValueMultiplier:
              pmaxLifecycleGoalType === 'LOYALTY_RETENTION' && pmaxLifecycleValueMultiplier.trim()
                ? Number(pmaxLifecycleValueMultiplier)
                : undefined,
            pmaxLifecycleEnableBidAdjustmentsForLoyaltyMembers:
              pmaxLifecycleGoalType === 'LOYALTY_RETENTION'
                ? pmaxLifecycleEnableBidAdjustmentsForLoyaltyMembers
                : undefined,
            pmaxLifecycleShowTargetedLoyaltyMemberBenefitsInPla:
              pmaxLifecycleGoalType === 'LOYALTY_RETENTION'
                ? pmaxLifecycleShowTargetedLoyaltyMemberBenefitsInPla
                : undefined,
          }
        }

        if (channel.channelType === 'GOOGLE_DEMAND_GEN') {
          if (!googlePolitical) {
            throw new Error('Choose the Google EU political advertising declaration.')
          }
          override = {
            ...override,
            containsEuPoliticalAdvertising: googlePolitical === 'true',
            bidStrategy: demandGenBidStrategy,
            demandGenChannelStrategy,
          }
        }

        if (channel.channelType === 'META_ADS') {
          if (metaCatalogEnabled && !metaProductSetId.trim()) {
            throw new Error('Choose a synchronized Meta product set for the catalog ad.')
          }
          if (metaCatalogEnabled && metaExistingPostId.trim()) {
            throw new Error('Catalog ads and existing-post creatives cannot be combined.')
          }
          if (metaBackgroundGeneration && !metaCatalogEnabled) {
            throw new Error('Meta background generation requires catalog mode.')
          }
        }

        if (channel.channelType === 'GOOGLE_SEARCH') {
          if (!googlePolitical) {
            throw new Error('Choose the Google EU political advertising declaration.')
          }
          override = {
            ...override,
            containsEuPoliticalAdvertising: googlePolitical === 'true',
            bidStrategy: googleBidStrategy,
            keywordMatchType,
            targetSearchNetwork: googleSearchPartners,
            targetContentNetwork: googleContentNetwork,
            positiveGeoTargetType: googleGeoMode,
            googleSearchAudienceMode,
            googleSearchInterestResources,
            googleSearchExcludedInterestResources,
            googleParentalStatusIncludes,
            googleParentalStatusExcludes,
            googleIncomeRangeIncludes,
            googleIncomeRangeExcludes,
            googleDetailedDemographicIds,
            googleExcludedDetailedDemographicIds,
            deviceBidModifiers: Object.fromEntries(
              Object.entries(googleDeviceBidModifiers).map(([device, value]) => [
                device,
                Number(value),
              ]),
            ),
            aiMaxEnabled: googleAiMaxEnabled,
            aiMaxTextCustomizationEnabled: googleAiMaxEnabled && googleAiMaxTextCustomization,
            aiMaxFinalUrlExpansionEnabled: googleAiMaxEnabled && googleAiMaxFinalUrlExpansion,
            aiMaxDisableSearchTermMatching:
              googleAiMaxEnabled && googleAiMaxDisableSearchTermMatching,
            aiMaxTermExclusions: googleAiMaxEnabled ? listValues(googleAiMaxTermExclusions) : [],
            aiMaxMessagingRestrictions: googleAiMaxEnabled
              ? listValues(googleAiMaxMessagingRestrictions)
              : [],
            aiMaxUrlExclusions: googleAiMaxEnabled ? listValues(googleAiMaxUrlExclusions) : [],
            aiMaxUrlInclusions: googleAiMaxEnabled ? listValues(googleAiMaxUrlInclusions) : [],
            aiMaxBrandInclusions: googleAiMaxEnabled ? googleAiMaxBrandInclusions : [],
            aiMaxBrandExclusions: googleAiMaxEnabled ? googleAiMaxBrandExclusions : [],
            adSchedules: googleAdSchedules,
            negativeKeywords: googleNegativeKeywords
              .map((keyword) => ({
                text: keyword.text.trim(),
                matchType: keyword.matchType,
              }))
              .filter((keyword) => keyword.text),
            negativeKeywordSharedSetIds: selectedGoogleNegativeKeywordListIds,
          }
        }

        if (channel.channelType === 'META_ADS') {
          if (!metaPageId) throw new Error('Choose a Facebook Page for Meta.')
          override = {
            ...override,
            pageId: metaPageId,
            instagramActorId: metaInstagramId || undefined,
            optimizationGoal: metaOptimizationGoal,
            billingEvent: metaBillingEvent,
            specialAdCategories: [metaSpecialCategory],
            pixelId: metaPixelId || undefined,
            advantagePlacements: metaPlacementMode === 'AUTO',
            publisherPlatforms: metaPlacementMode === 'MANUAL' ? metaPublisherPlatforms : [],
            facebookPositions:
              metaPlacementMode === 'MANUAL' && metaPublisherPlatforms.includes('facebook')
                ? metaFacebookPositions
                : [],
            instagramPositions:
              metaPlacementMode === 'MANUAL' && metaPublisherPlatforms.includes('instagram')
                ? metaInstagramPositions
                : [],
            messengerPositions:
              metaPlacementMode === 'MANUAL' && metaPublisherPlatforms.includes('messenger')
                ? metaMessengerPositions
                : [],
            audienceNetworkPositions:
              metaPlacementMode === 'MANUAL' && metaPublisherPlatforms.includes('audience_network')
                ? metaAudienceNetworkPositions
                : [],
            advantageAudience: metaAdvantageAudience,
            advantageAgeSuggestion: metaAdvantageAudience && metaAdvantageAgeSuggestion,
            advantageGenderSuggestion: metaAdvantageAudience && metaAdvantageGenderSuggestion,
            advantagePlusCreative: metaAdvantagePlusCreative,
            generativeText: metaGenerativeText,
            imageExpansion: metaImageExpansion,
            productSetId: metaCatalogEnabled ? metaProductSetId.trim() : undefined,
            backgroundGeneration: metaCatalogEnabled && metaBackgroundGeneration,
            existingPostId: metaCatalogEnabled ? undefined : metaExistingPostId.trim() || undefined,
            detailedInterests: metaDetailedInterests,
          }
        }

        latest = await adCenterApi.updateReachlyoCampaignChannel(
          selectedCompany.companyId,
          campaign.id,
          channel.id,
          { providerOverrideJson: JSON.stringify(override) },
        )

        if (requiresConversion(campaign.businessGoal)) {
          if (channel.provider === 'GOOGLE_ADS') {
            if (!googleConversionId) {
              throw new Error('Choose a Google conversion action.')
            }
            const conversionId =
              channel.channelType === 'GOOGLE_SEARCH' ||
              channel.channelType === 'GOOGLE_PERFORMANCE_MAX' ||
              channel.channelType === 'GOOGLE_DEMAND_GEN' ||
              channel.channelType === 'GOOGLE_SHOPPING' ||
              channel.channelType === 'GOOGLE_DISPLAY'
                ? googleConversionId
                : (conversionsByAccount[accountId]?.[0]?.id ?? '')
            if (!conversionId) {
              throw new Error(
                'Choose or sync a Google conversion action for ' +
                  pretty(channel.channelType) +
                  '.',
              )
            }
            await adCenterApi.upsertReachlyoConversionGoalMapping(
              selectedCompany.companyId,
              campaign.id,
              {
                provider: 'GOOGLE_ADS',
                adAccountId: accountId,
                targetType: 'PROVIDER_CONVERSION',
                adConversionId: conversionId,
              },
            )
          } else if (metaConversionMode === 'PROVIDER') {
            if (!metaConversionId) {
              throw new Error('Choose a Meta conversion.')
            }
            await adCenterApi.upsertReachlyoConversionGoalMapping(
              selectedCompany.companyId,
              campaign.id,
              {
                provider: 'META_ADS',
                adAccountId: accountId,
                targetType: 'PROVIDER_CONVERSION',
                adConversionId: metaConversionId,
              },
            )
          } else {
            if (!metaStandardEvent.trim()) {
              throw new Error('Enter the Meta standard event.')
            }
            if (!metaPixelId) {
              throw new Error('Choose a Meta Pixel for the standard event.')
            }
            await adCenterApi.upsertReachlyoConversionGoalMapping(
              selectedCompany.companyId,
              campaign.id,
              {
                provider: 'META_ADS',
                adAccountId: accountId,
                targetType: 'STANDARD_EVENT',
                providerEventName: metaStandardEvent.trim().toUpperCase(),
              },
            )
          }
        }
      }

      setCampaign(latest)
      setNotice('Provider-specific settings saved.')
      setStep(6)
      await compilePlans(latest)
    } catch (saveError) {
      const fallback =
        saveError instanceof Error ? saveError.message : 'Unable to save provider settings'
      setError(apiErrorMessage(saveError, fallback))
    } finally {
      setBusy(false)
    }
  }

  const compilePlans = async (value = campaign) => {
    if (!selectedCompany || !value) return [] as ProviderCampaignPlan[]
    setReviewLoading(true)
    setError('')

    try {
      const packs =
        creativePacks.length > 0
          ? creativePacks
          : await adCenterApi.reachlyoCreativePacks(selectedCompany.companyId, value.id)
      const definitions =
        audiences.length > 0
          ? audiences
          : await adCenterApi.reachlyoAudienceDefinitions(selectedCompany.companyId, value.id)

      if (creativePacks.length === 0) setCreativePacks(packs)
      if (audiences.length === 0) setAudiences(definitions)

      const compiled: ProviderCampaignPlan[] = []
      for (const channel of value.channels.filter((item) => item.enabled)) {
        const override = parseObject(channel.providerOverrideJson)
        const accountId =
          accountByChannel[channel.channelType] || String(override._reachlyoAdAccountId ?? '')
        if (!accountId) {
          throw new Error('Choose an advertising account for ' + pretty(channel.channelType) + '.')
        }

        compiled.push(
          await adCenterApi.compileReachlyoCampaignChannel(
            selectedCompany.companyId,
            value.id,
            channel.id,
            {
              adAccountId: accountId,
              creativePackId: packs[0]?.id ?? null,
              audienceDefinitionId: definitions[0]?.id ?? null,
            },
          ),
        )
      }

      setPlans(compiled)
      return compiled
    } catch (compileError) {
      setError(apiErrorMessage(compileError, 'Unable to compile provider plans'))
      return []
    } finally {
      setReviewLoading(false)
    }
  }

  const prepareLaunch = async () => {
    if (!selectedCompany || !campaign) return
    if (
      currentRun &&
      ['READY', 'PAUSED_READY', 'FAILED', 'PARTIAL_FAILURE'].includes(currentRun.status)
    ) {
      setStep(7)
      setNotice(
        currentRun.status === 'READY'
          ? 'This campaign already has a prepared launch run.'
          : 'Open the existing launch run to continue or retry it.',
      )
      return
    }
    setBusy(true)
    setError('')
    setNotice('')

    try {
      const compiled = await compilePlans(campaign)
      if (compiled.length === 0) {
        throw new Error('No provider plans could be compiled.')
      }
      if (compiled.some((plan) => !plan.deployable)) {
        throw new Error('Resolve compiler errors before preparing the launch.')
      }

      const run = await adCenterApi.prepareReachlyoCampaignLaunch(
        selectedCompany.companyId,
        campaign.id,
        {
          idempotencyKey: launchKey(),
          channels: campaign.channels
            .filter((channel) => channel.enabled)
            .map((channel) => {
              const override = parseObject(channel.providerOverrideJson)
              return {
                channelId: channel.id,
                adAccountId:
                  accountByChannel[channel.channelType] ||
                  String(override._reachlyoAdAccountId ?? ''),
                creativePackId: creativePacks[0]?.id ?? null,
                audienceDefinitionId: audiences[0]?.id ?? null,
              }
            }),
        },
      )

      setCurrentRun(run)
      setLaunchRuns((current) => [run, ...current.filter((item) => item.id !== run.id)])
      setStep(7)

      if (run.status === 'READY') {
        setNotice('Launch preflight passed. Provider resources have not been created yet.')
      } else {
        setError('Launch preflight found issues. Review the validation details below.')
      }
    } catch (prepareError) {
      const fallback =
        prepareError instanceof Error ? prepareError.message : 'Unable to prepare launch'
      setError(apiErrorMessage(prepareError, fallback))
    } finally {
      setBusy(false)
    }
  }

  const executeRun = async (retry: boolean) => {
    if (!selectedCompany || !campaign || !currentRun) return
    setBusy(true)
    setError('')
    setNotice('')

    try {
      const run = retry
        ? await adCenterApi.retryReachlyoCampaignLaunch(
            selectedCompany.companyId,
            campaign.id,
            currentRun.id,
          )
        : await adCenterApi.executeReachlyoCampaignLaunch(
            selectedCompany.companyId,
            campaign.id,
            currentRun.id,
          )

      setCurrentRun(run)
      setLaunchRuns((current) => [run, ...current.filter((item) => item.id !== run.id)])

      const refreshed = await adCenterApi.reachlyoCampaign(selectedCompany.companyId, campaign.id)
      setCampaign(refreshed)

      setNotice(
        run.status === 'PAUSED_READY'
          ? 'Provider resources were created successfully and remain paused.'
          : 'Launch run finished. Review the provider steps below.',
      )
    } catch (launchError) {
      setError(apiErrorMessage(launchError, 'Unable to execute launch run'))
    } finally {
      setBusy(false)
    }
  }

  const refreshRun = async () => {
    if (!selectedCompany || !campaign || !currentRun) return
    try {
      const run = await adCenterApi.reachlyoCampaignLaunchRun(
        selectedCompany.companyId,
        campaign.id,
        currentRun.id,
      )
      setCurrentRun(run)
      setLaunchRuns((current) => [run, ...current.filter((item) => item.id !== run.id)])
    } catch (refreshError) {
      setError(apiErrorMessage(refreshError, 'Unable to refresh launch run'))
    }
  }

  const selectBusinessGoal = (
    nextObjective: ReachlyoCampaignObjective,
    nextGoal: CampaignBusinessGoal,
  ) => {
    setObjective(nextObjective)
    setBusinessGoal(nextGoal)

    if (nextGoal === 'TRAFFIC') {
      setGoogleBidStrategy('MAXIMIZE_CLICKS')
      setPmaxBidStrategy('MAXIMIZE_CONVERSIONS')
      setDemandGenBidStrategy('MAXIMIZE_CLICKS')
      setDisplayBidStrategy('MANUAL_CPC')
      setMetaOptimizationGoal('LANDING_PAGE_VIEWS')
    } else if (nextGoal === 'AWARENESS') {
      setGoogleBidStrategy('MAXIMIZE_CLICKS')
      setDemandGenBidStrategy('MAXIMIZE_CLICKS')
      setDisplayBidStrategy('MANUAL_CPC')
      setMetaOptimizationGoal('REACH')
    } else if (nextGoal === 'PURCHASE') {
      setGoogleBidStrategy('MAXIMIZE_CONVERSION_VALUE')
      setPmaxBidStrategy('MAXIMIZE_CONVERSION_VALUE')
      setDemandGenBidStrategy('MAXIMIZE_CONVERSION_VALUE')
      setDisplayBidStrategy('MAXIMIZE_CONVERSIONS')
      setMetaOptimizationGoal('VALUE')
    } else {
      setGoogleBidStrategy('MAXIMIZE_CONVERSIONS')
      setPmaxBidStrategy('MAXIMIZE_CONVERSIONS')
      setDemandGenBidStrategy('MAXIMIZE_CONVERSIONS')
      setDisplayBidStrategy('MAXIMIZE_CONVERSIONS')
      setMetaOptimizationGoal(
        nextGoal === 'LEAD' || nextGoal === 'QUALIFIED_LEAD' || nextGoal === 'SIGN_UP'
          ? 'LEAD_GENERATION'
          : 'OFFSITE_CONVERSIONS',
      )
    }
    setMetaStandardEvent(defaultMetaStandardEvent(nextGoal))
  }

  const applyRecommendedChannels = () => {
    const recommendations = recommendedChannelsForGoal(businessGoal, objective)
    setSelectedChannels(recommendations)
    recommendations.forEach((type) => {
      if (accountByChannel[type]) return
      const provider = providerForChannel(type)
      const account =
        managedAccounts.find((item) => item.provider === provider && item.primaryAccount) ??
        managedAccounts.find((item) => item.provider === provider)
      if (account) {
        setAccountByChannel((current) => ({
          ...current,
          [type]: account.id,
        }))
      }
    })
  }

  const toggleChannel = (type: CampaignChannelType, checked: boolean) => {
    setSelectedChannels((current) =>
      checked ? [...new Set([...current, type])] : current.filter((item) => item !== type),
    )

    if (checked && !accountByChannel[type]) {
      const provider = providerForChannel(type)
      const account =
        managedAccounts.find((item) => item.provider === provider && item.primaryAccount) ??
        managedAccounts.find((item) => item.provider === provider)
      if (account) {
        setAccountByChannel((current) => ({
          ...current,
          [type]: account.id,
        }))
      }
    }
  }

  const accountOptions = (type: CampaignChannelType) =>
    managedAccounts.filter((account) => account.provider === providerForChannel(type))
  const selectChannelAccount = (channelType: CampaignChannelType, accountId: string) => {
    setAccountByChannel((current) => ({
      ...current,
      [channelType]: accountId,
    }))
    if (GOOGLE_PROVIDER_AUDIENCE_CHANNELS.includes(channelType)) {
      setGoogleAudienceIdsByChannel((current) => ({
        ...current,
        [channelType]: [],
      }))
      setGoogleExcludedAudienceIdsByChannel((current) => ({
        ...current,
        [channelType]: [],
      }))
    }
  }

  const updateAudienceLocation = (index: number, patch: Partial<ReachlyoAudienceLocationInput>) => {
    setAudienceLocations((current) =>
      current.map((location, locationIndex) =>
        locationIndex === index ? { ...location, ...patch } : location,
      ),
    )
  }

  const requiresGoogleLocationSupport = selectedChannels.some(
    (type) => providerForChannel(type) === 'GOOGLE_ADS',
  )
  const requiresMetaLocationSupport = selectedChannels.includes('META_ADS')

  const googleLocationAccountId = selectedChannels
    .filter((type) => providerForChannel(type) === 'GOOGLE_ADS')
    .map((type) => accountByChannel[type])
    .find(Boolean)

  const metaLocationAccountId = requiresMetaLocationSupport ? accountByChannel.META_ADS : undefined

  const requiredLocationProviders = [
    ...(requiresGoogleLocationSupport ? (['GOOGLE_ADS'] as const) : []),
    ...(requiresMetaLocationSupport ? (['META_ADS'] as const) : []),
  ]

  const searchAudienceLocation = async (index: number) => {
    if (!selectedCompany) return
    const location = audienceLocations[index]
    const query = locationSearchByIndex[index]?.trim() ?? ''
    if (!query) {
      setLocationSearchErrorByIndex((current) => ({
        ...current,
        [index]: 'Enter a city, region or postal code.',
      }))
      return
    }
    if (!/^[A-Za-z]{2}$/.test(location.countryCode.trim())) {
      setLocationSearchErrorByIndex((current) => ({
        ...current,
        [index]: 'Enter a two-letter country code before searching.',
      }))
      return
    }
    if (
      (requiresGoogleLocationSupport && !googleLocationAccountId) ||
      (requiresMetaLocationSupport && !metaLocationAccountId)
    ) {
      setLocationSearchErrorByIndex((current) => ({
        ...current,
        [index]:
          'Choose an advertising account for every selected provider in the Channels step first.',
      }))
      return
    }

    setLocationSearchBusyIndex(index)
    setLocationSearchErrorByIndex((current) => ({ ...current, [index]: '' }))
    try {
      const suggestions = await adCenterApi.locationSuggestions(
        selectedCompany.companyId,
        query,
        location.countryCode.trim().toUpperCase(),
        googleLocationAccountId,
        metaLocationAccountId,
      )
      setLocationSuggestionsByIndex((current) => ({
        ...current,
        [index]: suggestions,
      }))
      if (suggestions.length === 0) {
        setLocationSearchErrorByIndex((current) => ({
          ...current,
          [index]: 'No provider-backed location matched this search.',
        }))
      }
    } catch (searchError) {
      setLocationSearchErrorByIndex((current) => ({
        ...current,
        [index]: apiErrorMessage(searchError, 'Unable to search provider locations'),
      }))
    } finally {
      setLocationSearchBusyIndex((current) => (current === index ? null : current))
    }
  }

  const suggestionSupportsSelectedProviders = (suggestion: CanonicalLocationSuggestion) =>
    requiredLocationProviders.every((provider) => suggestion.supportedProviders.includes(provider))

  const applyLocationSuggestion = (index: number, suggestion: CanonicalLocationSuggestion) => {
    if (!suggestionSupportsSelectedProviders(suggestion)) return
    updateAudienceLocation(index, {
      countryCode: suggestion.countryCode,
      region: suggestion.region ?? null,
      city: suggestion.city ?? null,
      postalCode: suggestion.postalCode ?? null,
      latitude: null,
      longitude: null,
      radiusKm: null,
    })
    setLocationSearchByIndex((current) => ({
      ...current,
      [index]: suggestion.displayName,
    }))
    setLocationSuggestionsByIndex((current) => ({
      ...current,
      [index]: [],
    }))
    setLocationSearchErrorByIndex((current) => ({
      ...current,
      [index]: '',
    }))
  }

  const toggleGoogleAudienceForChannel = (
    channelType: CampaignChannelType,
    audienceId: string,
    checked: boolean,
  ) => {
    setGoogleAudienceIdsByChannel((current) => {
      const existing = current[channelType] ?? []
      const next = checked
        ? [...new Set([...existing, audienceId])]
        : existing.filter((id) => id !== audienceId)
      return { ...current, [channelType]: next }
    })
    if (checked) {
      setGoogleExcludedAudienceIdsByChannel((current) => ({
        ...current,
        [channelType]: (current[channelType] ?? []).filter((id) => id !== audienceId),
      }))
    }
  }

  const toggleGoogleExcludedAudienceForChannel = (
    channelType: CampaignChannelType,
    audienceId: string,
    checked: boolean,
  ) => {
    setGoogleExcludedAudienceIdsByChannel((current) => {
      const existing = current[channelType] ?? []
      const next = checked
        ? [...new Set([...existing, audienceId])]
        : existing.filter((id) => id !== audienceId)
      return { ...current, [channelType]: next }
    })
    if (checked) {
      setGoogleAudienceIdsByChannel((current) => ({
        ...current,
        [channelType]: (current[channelType] ?? []).filter((id) => id !== audienceId),
      }))
    }
  }

  if (!selectedCompany) return null

  if (loading) {
    return (
      <section className="px-4 py-6 sm:px-6 lg:px-8">
        <div className="mx-auto max-w-7xl"><PageSkeleton /></div>
      </section>
    )
  }

  return (
    <section className="px-4 py-6 sm:px-6 lg:px-8">
      <div className="mx-auto max-w-7xl">
        <Link
          to="/ad-center/unified-campaigns"
          className="inline-flex items-center gap-2 text-sm font-semibold text-slate-500 hover:text-slate-900"
        >
          <FiArrowLeft aria-hidden="true" />
          Unified campaigns
        </Link>

        <header className="mt-5 flex flex-wrap items-end justify-between gap-4">
          <div>
            <p className="eyebrow">Unified campaign builder</p>
            <h1 className="mt-2 text-2xl font-bold tracking-tight text-slate-950 sm:text-3xl">
              {campaign?.name || 'Create a campaign once'}
            </h1>
            <p className="mt-2 max-w-3xl text-sm leading-6 text-slate-500">
              Build one canonical campaign, inspect provider translation, then create supported
              Google and Meta resources paused.
            </p>
          </div>
          {campaign && (
            <span
              className={
                'rounded-full px-3 py-1.5 text-xs font-semibold ' + statusClass(campaign.status)
              }
            >
              {pretty(campaign.status)}
            </span>
          )}
        </header>




        <div className="mt-6 grid gap-6 lg:grid-cols-[230px_minmax(0,1fr)]">
          <aside className="panel h-fit overflow-hidden lg:sticky lg:top-6">
            <div className="border-b border-slate-100 px-4 py-3">
              <p className="text-xs font-semibold uppercase tracking-wide text-slate-400">
                Campaign flow
              </p>
            </div>
            <div className="p-2">
              {STEPS.map((item, index) => {
                const Icon = item.icon
                const locked = index > 0 && !campaign
                return (
                  <button
                    key={item.label}
                    type="button"
                    disabled={locked}
                    onClick={() => {
                      setError('')
                      setNotice('')
                      setStep(index)
                      if (index === 5) void loadProviderData()
                      if (index === 6 && campaign) void compilePlans(campaign)
                    }}
                    className={
                      'flex w-full items-center gap-3 rounded-xl px-3 py-3 text-left text-sm font-semibold transition disabled:cursor-not-allowed disabled:opacity-40 ' +
                      (step === index
                        ? 'bg-indigo-50 text-indigo-700'
                        : 'text-slate-600 hover:bg-slate-50')
                    }
                  >
                    <span
                      className={
                        'flex h-8 w-8 items-center justify-center rounded-lg ' +
                        (step === index
                          ? 'bg-white text-indigo-600 shadow-sm'
                          : 'bg-slate-100 text-slate-500')
                      }
                    >
                      <Icon aria-hidden="true" />
                    </span>
                    <span className="flex-1">{item.label}</span>
                    <FiChevronRight className="text-slate-300" aria-hidden="true" />
                  </button>
                )
              })}
            </div>
          </aside>

          <main>
            {!editableDefinition && step < 6 && (
              <div className="mb-4 rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-800">
                This campaign is in {pretty(campaign?.status ?? '')}. Definition steps are
                read-only; review and launch history stay available.
              </div>
            )}

            {step === 0 && (
              <StepCard
                title="1. Goal & campaign brief"
                description="Start with the business outcome. Provider objectives are derived later."
              >
                <div className="grid gap-5 sm:grid-cols-2">
                  <Field label="Campaign name">
                    <input
                      className="form-input mt-2"
                      value={name}
                      disabled={!canEdit || !editableDefinition}
                      onChange={(event) => setName(event.target.value)}
                    />
                  </Field>
                  <Field label="Currency">
                    <input
                      className="form-input mt-2 uppercase"
                      value={currency}
                      disabled={
                        Boolean(campaign?.deployments.length) || !canEdit || !editableDefinition
                      }
                      maxLength={3}
                      onChange={(event) => setCurrency(event.target.value.toUpperCase())}
                    />
                  </Field>
                  <div className="sm:col-span-2">
                    <p className="text-xs font-semibold text-slate-700">
                      What do you want to achieve?
                    </p>
                    <p className="mt-1 text-xs leading-5 text-slate-500">
                      Choose the business outcome. UnifyDesk will use sensible Google and Meta
                      optimization defaults; provider terminology stays available under advanced
                      settings.
                    </p>
                    <div className="mt-3 grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
                      {GOAL_OPTIONS.map((option) => {
                        const selected =
                          option.objective === objective && option.goal === businessGoal
                        return (
                          <button
                            key={option.objective + '-' + option.goal}
                            type="button"
                            disabled={!canEdit || !editableDefinition}
                            onClick={() => selectBusinessGoal(option.objective, option.goal)}
                            className={
                              'rounded-2xl border p-4 text-left transition disabled:cursor-not-allowed disabled:opacity-50 ' +
                              (selected
                                ? 'border-indigo-300 bg-indigo-50 shadow-sm'
                                : 'border-slate-200 bg-white hover:border-indigo-200 hover:bg-indigo-50/30')
                            }
                          >
                            <span className="block text-sm font-bold text-slate-900">
                              {option.title}
                            </span>
                            <span className="mt-1 block text-xs leading-5 text-slate-500">
                              {option.description}
                            </span>
                          </button>
                        )
                      })}
                    </div>
                    <details className="mt-3 rounded-xl border border-slate-200 bg-slate-50 px-4 py-3">
                      <summary className="cursor-pointer text-xs font-semibold text-slate-700">
                        Advanced goal mapping
                      </summary>
                      <div className="mt-3 grid gap-3 sm:grid-cols-2">
                        <Field label="Provider-neutral objective">
                          <select
                            className="form-input mt-2"
                            value={objective}
                            disabled={!canEdit || !editableDefinition}
                            onChange={(event) => {
                              const next = event.target.value as ReachlyoCampaignObjective
                              selectBusinessGoal(next, defaultGoal(next))
                            }}
                          >
                            {OBJECTIVES.map((value) => (
                              <option key={value} value={value}>
                                {pretty(value)}
                              </option>
                            ))}
                          </select>
                        </Field>
                        <Field label="Business conversion goal">
                          <select
                            className="form-input mt-2"
                            value={businessGoal}
                            disabled={!canEdit || !editableDefinition}
                            onChange={(event) =>
                              selectBusinessGoal(
                                objective,
                                event.target.value as CampaignBusinessGoal,
                              )
                            }
                          >
                            {BUSINESS_GOALS.map((value) => (
                              <option key={value} value={value}>
                                {pretty(value)}
                              </option>
                            ))}
                          </select>
                        </Field>
                      </div>
                    </details>
                  </div>
                  <Field label="Landing page">
                    <input
                      className="form-input mt-2"
                      value={landingPageUrl}
                      disabled={!canEdit || !editableDefinition}
                      onChange={(event) => setLandingPageUrl(event.target.value)}
                      placeholder="https://example.com/offer"
                    />
                  </Field>
                  <div className="grid grid-cols-2 gap-3">
                    <Field label="Start">
                      <input
                        className="form-input mt-2"
                        type="datetime-local"
                        value={startAt}
                        disabled={!canEdit || !editableDefinition}
                        onChange={(event) => setStartAt(event.target.value)}
                      />
                    </Field>
                    <Field label="End">
                      <input
                        className="form-input mt-2"
                        type="datetime-local"
                        value={endAt}
                        disabled={!canEdit || !editableDefinition}
                        onChange={(event) => setEndAt(event.target.value)}
                      />
                    </Field>
                  </div>
                </div>
                <StepActions
                  busy={busy}
                  disabled={!canEdit || !editableDefinition}
                  onNext={() => void saveBasics()}
                  nextLabel={campaign ? 'Save & continue' : 'Create campaign'}
                />
              </StepCard>
            )}

            {step === 1 && (
              <StepCard
                title="2. Channels & accounts"
                description="Choose delivery channels and the managed account Reachlyo should use for each one."
              >
                <div className="mb-4 rounded-2xl border border-indigo-100 bg-indigo-50/50 p-4">
                  <div className="flex flex-wrap items-center justify-between gap-3">
                    <div>
                      <p className="text-sm font-bold text-slate-900">
                        Recommended for {pretty(businessGoal)}
                      </p>
                      <p className="mt-1 text-xs leading-5 text-slate-500">
                        Start with{' '}
                        {recommendedChannelsForGoal(businessGoal, objective)
                          .map(pretty)
                          .join(' + ')}
                        . You stay in control and can change the channel mix before launch.
                      </p>
                    </div>
                    <button
                      type="button"
                      className="secondary-button"
                      disabled={!canEdit || !editableDefinition}
                      onClick={applyRecommendedChannels}
                    >
                      Use recommended channels
                    </button>
                  </div>
                </div>

                <div className="grid gap-3">
                  {CHANNELS.map((option) => {
                    const selected = selectedChannels.includes(option.type)
                    return (
                      <div
                        key={option.type}
                        className={
                          'rounded-2xl border p-4 ' +
                          (selected ? 'border-indigo-200 bg-indigo-50/40' : 'border-slate-200')
                        }
                      >
                        <div className="flex items-start gap-3">
                          <input
                            type="checkbox"
                            className="mt-1 h-4 w-4"
                            checked={selected}
                            disabled={!canEdit || !editableDefinition}
                            onChange={(event) => toggleChannel(option.type, event.target.checked)}
                          />
                          <div className="min-w-0 flex-1">
                            <div className="flex flex-wrap items-center gap-2">
                              <p className="text-sm font-bold text-slate-900">{option.title}</p>
                              {recommendedChannelsForGoal(businessGoal, objective).includes(
                                option.type,
                              ) && (
                                <span className="rounded-full bg-indigo-100 px-2 py-0.5 text-[10px] font-bold text-indigo-700">
                                  Recommended
                                </span>
                              )}
                              <span
                                className={
                                  'rounded-full px-2 py-0.5 text-[10px] font-bold ' +
                                  (option.executable
                                    ? 'bg-emerald-50 text-emerald-700'
                                    : 'bg-amber-50 text-amber-700')
                                }
                              >
                                {option.executable ? 'Launch supported' : 'Plan preview only'}
                              </span>
                            </div>
                            <p className="mt-1 text-xs leading-5 text-slate-500">
                              {option.description}
                            </p>

                            {selected && (
                              <label className="mt-3 block max-w-md text-xs font-semibold text-slate-700">
                                Advertising account
                                <select
                                  className="form-input mt-2"
                                  value={accountByChannel[option.type] ?? ''}
                                  disabled={!canEdit || !editableDefinition}
                                  onChange={(event) =>
                                    selectChannelAccount(option.type, event.target.value)
                                  }
                                >
                                  <option value="">Select account</option>
                                  {accountOptions(option.type).map((account) => (
                                    <option key={account.id} value={account.id}>
                                      {account.name}
                                      {account.currency ? ' · ' + account.currency : ''}
                                    </option>
                                  ))}
                                </select>
                              </label>
                            )}
                          </div>
                        </div>
                      </div>
                    )
                  })}
                </div>
                <StepActions
                  busy={busy}
                  disabled={!canEdit || !editableDefinition}
                  onBack={() => setStep(0)}
                  onNext={() => void saveChannels()}
                />
              </StepCard>
            )}

            {step === 2 && (
              <StepCard
                title="3. Shared budget"
                description="Set one budget, then decide how it is allocated across channels."
              >
                <div className="grid gap-5 sm:grid-cols-3">
                  <Field label={'Total budget (' + (campaign?.currency ?? currency) + ')'}>
                    <input
                      className="form-input mt-2"
                      type="number"
                      min="0"
                      step="0.01"
                      value={budgetAmount}
                      disabled={!canEdit || !editableDefinition}
                      onChange={(event) => setBudgetAmount(event.target.value)}
                    />
                  </Field>
                  <Field label="Budget period">
                    <select
                      className="form-input mt-2"
                      value={budgetPeriod}
                      disabled={!canEdit || !editableDefinition}
                      onChange={(event) => setBudgetPeriod(event.target.value as BudgetPeriod)}
                    >
                      <option value="DAILY">Daily</option>
                      <option value="TOTAL">Total campaign budget</option>
                    </select>
                  </Field>
                  <Field label="Allocation">
                    <select
                      className="form-input mt-2"
                      value={allocationMode}
                      disabled={!canEdit || !editableDefinition}
                      onChange={(event) =>
                        setAllocationMode(event.target.value as BudgetAllocationMode)
                      }
                    >
                      <option value="PERCENTAGE">Percentage</option>
                      <option value="FIXED">Fixed amount</option>
                      <option value="AUTOMATIC">Automatic</option>
                    </select>
                  </Field>
                </div>

                {allocationMode !== 'AUTOMATIC' && (
                  <div className="mt-5 grid gap-3 sm:grid-cols-2">
                    {enabledChannels.map((channel) => (
                      <Field
                        key={channel.id}
                        label={
                          pretty(channel.channelType) +
                          ' · ' +
                          (allocationMode === 'PERCENTAGE' ? '%' : (campaign?.currency ?? currency))
                        }
                      >
                        <input
                          className="form-input mt-2"
                          type="number"
                          min="0"
                          step="0.01"
                          value={allocationByChannel[channel.channelType] ?? ''}
                          disabled={!canEdit || !editableDefinition}
                          placeholder={
                            allocationMode === 'PERCENTAGE'
                              ? String(100 / Math.max(1, enabledChannels.length))
                              : 'Equal split when blank'
                          }
                          onChange={(event) =>
                            setAllocationByChannel((current) => ({
                              ...current,
                              [channel.channelType]: event.target.value,
                            }))
                          }
                        />
                      </Field>
                    ))}
                  </div>
                )}

                {allocationMode === 'AUTOMATIC' && enabledChannels.length > 1 && (
                  <p className="mt-4 rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-xs leading-5 text-amber-800">
                    Automatic multi-channel allocation is stored but the current compiler
                    intentionally blocks deployment until the optimizer is implemented. Use
                    Percentage or Fixed for launchable campaigns today.
                  </p>
                )}

                <StepActions
                  busy={busy}
                  disabled={!canEdit || !editableDefinition}
                  onBack={() => setStep(1)}
                  onNext={() => void saveBudget()}
                />
              </StepCard>
            )}

            {step === 3 && (
              <StepCard
                title="4. Canonical creative"
                description="Write the message once. Reachlyo adapts it into provider-specific creative structures."
              >
                {shoppingOnly && (
                  <div className="rounded-2xl border border-emerald-200 bg-emerald-50 p-4 text-sm text-emerald-800">
                    <p className="font-bold">Merchant Center provides the Shopping creative.</p>
                    <p className="mt-1 text-xs leading-5">
                      Product title, image, price and landing page come from your Merchant catalog,
                      so a separate Creative Pack is not required for this Shopping-only campaign.
                    </p>
                  </div>
                )}
                <div className={shoppingOnly ? 'hidden' : 'grid gap-5 sm:grid-cols-2'}>
                  <Field label="Creative pack name">
                    <input
                      className="form-input mt-2"
                      value={creativeName}
                      disabled={!canEdit || !editableDefinition}
                      onChange={(event) => setCreativeName(event.target.value)}
                    />
                  </Field>
                  <Field label="Business / brand name">
                    <input
                      className="form-input mt-2"
                      value={brandName}
                      disabled={!canEdit || !editableDefinition}
                      onChange={(event) => setBrandName(event.target.value)}
                    />
                  </Field>
                  <Field label="Primary message">
                    <textarea
                      className="form-input mt-2 min-h-28"
                      value={primaryMessage}
                      disabled={!canEdit || !editableDefinition}
                      onChange={(event) => setPrimaryMessage(event.target.value)}
                    />
                  </Field>
                  <Field label="Destination URL">
                    <input
                      className="form-input mt-2"
                      value={creativeUrl}
                      disabled={!canEdit || !editableDefinition}
                      onChange={(event) => setCreativeUrl(event.target.value)}
                      placeholder={campaign?.landingPageUrl ?? 'https://'}
                    />
                  </Field>
                  <Field
                    label="Headlines"
                    hint="One per line. Google Search needs 3–15, max 30 characters each."
                  >
                    <textarea
                      className="form-input mt-2 min-h-36"
                      value={headlines}
                      disabled={!canEdit || !editableDefinition}
                      onChange={(event) => setHeadlines(event.target.value)}
                    />
                  </Field>
                  <Field
                    label="Descriptions"
                    hint="One per line. Google Search needs 2–4, max 90 characters each."
                  >
                    <textarea
                      className="form-input mt-2 min-h-36"
                      value={descriptions}
                      disabled={!canEdit || !editableDefinition}
                      onChange={(event) => setDescriptions(event.target.value)}
                    />
                  </Field>
                  <Field label="Call to action">
                    <select
                      className="form-input mt-2"
                      value={callToAction}
                      disabled={!canEdit || !editableDefinition}
                      onChange={(event) => setCallToAction(event.target.value)}
                    >
                      <option value="LEARN_MORE">Learn more</option>
                      <option value="SIGN_UP">Sign up</option>
                      <option value="SHOP_NOW">Shop now</option>
                      <option value="CONTACT_US">Contact us</option>
                      <option value="GET_QUOTE">Get quote</option>
                    </select>
                  </Field>
                  <Field
                    label="Primary image"
                    hint="Required by the current Meta executor. Existing image stays when no new file is selected."
                  >
                    <label className="mt-2 flex min-h-24 cursor-pointer items-center justify-center gap-2 rounded-xl border border-dashed border-slate-300 bg-slate-50 px-4 text-sm font-semibold text-slate-600 hover:border-indigo-300 hover:bg-indigo-50">
                      <FiUploadCloud aria-hidden="true" />
                      {creativeFile
                        ? creativeFile.name
                        : creativePacks[0]?.assets.some((asset) => asset.role === 'IMAGE')
                          ? 'Replace existing image'
                          : 'Choose image'}
                      <input
                        className="hidden"
                        type="file"
                        accept="image/*"
                        disabled={!canEdit || !editableDefinition}
                        onChange={(event) => setCreativeFile(event.target.files?.[0] ?? null)}
                      />
                    </label>
                    {uploadProgress > 0 && (
                      <p className="mt-2 text-xs text-slate-500">Upload {uploadProgress}%</p>
                    )}
                  </Field>
                </div>

                {(selectedChannels.includes('GOOGLE_PERFORMANCE_MAX') ||
                  selectedChannels.includes('GOOGLE_DEMAND_GEN') ||
                  selectedChannels.includes('GOOGLE_DISPLAY')) && (
                  <div className="mt-5 rounded-2xl border border-indigo-100 bg-indigo-50/30 p-4">
                    <p className="text-sm font-bold text-slate-900">Google visual asset bundle</p>
                    <p className="mt-1 text-xs leading-5 text-slate-500">
                      Performance Max, Demand Gen and Display use provider-valid visual aspect
                      ratios. Existing saved assets stay in the Creative Pack when you do not
                      replace them.
                    </p>
                    <div className="mt-4 grid gap-3 md:grid-cols-3">
                      {[
                        [
                          'Landscape 1.91:1',
                          pmaxLandscapeFile,
                          setPmaxLandscapeFile,
                          'MARKETING_IMAGE',
                        ],
                        ['Square 1:1', pmaxSquareFile, setPmaxSquareFile, 'SQUARE_MARKETING_IMAGE'],
                        ['Square logo 1:1', pmaxLogoFile, setPmaxLogoFile, 'LOGO'],
                        [
                          'Portrait 4:5 · optional',
                          pmaxPortraitFile,
                          setPmaxPortraitFile,
                          'PORTRAIT_MARKETING_IMAGE',
                        ],
                        [
                          'Landscape logo 4:1 · optional',
                          pmaxLandscapeLogoFile,
                          setPmaxLandscapeLogoFile,
                          'LANDSCAPE_LOGO',
                        ],
                      ].map(([label, file, setter, role]) => (
                        <label
                          key={role as string}
                          className="rounded-xl border border-dashed border-slate-300 bg-white p-3 text-xs font-semibold text-slate-700"
                        >
                          {label as string}
                          <input
                            className="mt-2 block w-full text-xs"
                            type="file"
                            accept="image/png,image/jpeg,image/gif"
                            disabled={!canEdit || !editableDefinition}
                            onChange={(event) =>
                              (setter as (value: File | null) => void)(
                                event.target.files?.[0] ?? null,
                              )
                            }
                          />
                          {creativePacks[0]?.assets.some((asset) => asset.role === role) &&
                            !(file as File | null) && (
                              <span className="mt-1 block font-normal text-emerald-600">
                                Existing asset saved
                              </span>
                            )}
                        </label>
                      ))}
                    </div>
                  </div>
                )}

                <StepActions
                  busy={busy}
                  disabled={!canEdit || !editableDefinition}
                  onBack={() => setStep(2)}
                  onNext={() => void saveCreative()}
                />
              </StepCard>
            )}

            {step === 4 && (
              <StepCard
                title="5. Canonical audience"
                description="Define the customer once. Mapping quality is shown per provider during review."
              >
                <div className="grid gap-5 sm:grid-cols-2">
                  <Field label="Audience name">
                    <input
                      className="form-input mt-2"
                      value={audienceName}
                      disabled={!canEdit || !editableDefinition}
                      onChange={(event) => setAudienceName(event.target.value)}
                    />
                  </Field>
                  <div className="sm:col-span-2">
                    <div className="flex flex-wrap items-start justify-between gap-3">
                      <div>
                        <p className="text-xs font-semibold text-slate-700">Locations</p>
                        <p className="mt-1 text-[11px] leading-4 text-slate-400">
                          Country, region, city and postal targets map to Google and Meta provider
                          geo targets during preflight. Radius targeting requires latitude and
                          longitude so both providers can map it safely.
                        </p>
                      </div>
                      <button
                        type="button"
                        className="secondary-button inline-flex items-center gap-2"
                        disabled={!canEdit || !editableDefinition}
                        onClick={() =>
                          setAudienceLocations((current) => [...current, { countryCode: 'IN' }])
                        }
                      >
                        <FiPlus aria-hidden="true" />
                        Add location
                      </button>
                    </div>

                    <div className="mt-3 grid gap-3">
                      {audienceLocations.map((location, index) => (
                        <div key={index} className="rounded-2xl border border-slate-200 p-4">
                          <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
                            <div className="sm:col-span-2 lg:col-span-4 rounded-xl border border-indigo-100 bg-indigo-50/30 p-3">
                              <div className="flex flex-wrap items-end gap-2">
                                <div className="min-w-[220px] flex-1">
                                  <label className="text-xs font-semibold text-slate-700">
                                    Search provider locations
                                  </label>
                                  <input
                                    className="form-input mt-2"
                                    value={locationSearchByIndex[index] ?? ''}
                                    disabled={!canEdit || !editableDefinition}
                                    onChange={(event) =>
                                      setLocationSearchByIndex((current) => ({
                                        ...current,
                                        [index]: event.target.value,
                                      }))
                                    }
                                    onKeyDown={(event) => {
                                      if (event.key === 'Enter') {
                                        event.preventDefault()
                                        void searchAudienceLocation(index)
                                      }
                                    }}
                                    placeholder="Bengaluru, Karnataka, 560001..."
                                  />
                                </div>
                                <button
                                  type="button"
                                  className="secondary-button"
                                  disabled={
                                    !canEdit ||
                                    !editableDefinition ||
                                    locationSearchBusyIndex === index ||
                                    (requiresGoogleLocationSupport && !googleLocationAccountId) ||
                                    (requiresMetaLocationSupport && !metaLocationAccountId)
                                  }
                                  onClick={() => void searchAudienceLocation(index)}
                                >
                                  {locationSearchBusyIndex === index ? 'Searching…' : 'Search'}
                                </button>
                              </div>
                              <p className="mt-2 text-[11px] leading-4 text-slate-500">
                                Results are verified against the ad providers selected for this
                                campaign. A result must support every selected provider before you
                                can use it.
                              </p>
                              {locationSearchErrorByIndex[index] && (
                                <p className="mt-2 text-xs font-medium text-red-600">
                                  {locationSearchErrorByIndex[index]}
                                </p>
                              )}
                              {(locationSuggestionsByIndex[index]?.length ?? 0) > 0 && (
                                <div className="mt-3 grid gap-2">
                                  {locationSuggestionsByIndex[index].map((suggestion) => {
                                    const supported =
                                      suggestionSupportsSelectedProviders(suggestion)
                                    return (
                                      <button
                                        key={suggestion.key}
                                        type="button"
                                        disabled={!supported || !canEdit || !editableDefinition}
                                        onClick={() => applyLocationSuggestion(index, suggestion)}
                                        className={
                                          'flex flex-wrap items-center justify-between gap-2 rounded-lg border px-3 py-2 text-left text-xs transition ' +
                                          (supported
                                            ? 'border-slate-200 bg-white hover:border-indigo-300 hover:bg-indigo-50'
                                            : 'cursor-not-allowed border-slate-100 bg-slate-50 text-slate-400')
                                        }
                                      >
                                        <span>
                                          <span className="font-semibold">
                                            {suggestion.displayName}
                                          </span>
                                          <span className="ml-2 text-[10px] uppercase tracking-wide text-slate-400">
                                            {suggestion.type}
                                          </span>
                                        </span>
                                        <span className="flex gap-1">
                                          {suggestion.supportedProviders.includes('GOOGLE_ADS') && (
                                            <span className="rounded-full bg-sky-50 px-2 py-0.5 text-[10px] font-semibold text-sky-700">
                                              Google
                                            </span>
                                          )}
                                          {suggestion.supportedProviders.includes('META_ADS') && (
                                            <span className="rounded-full bg-indigo-50 px-2 py-0.5 text-[10px] font-semibold text-indigo-700">
                                              Meta
                                            </span>
                                          )}
                                          {!supported && (
                                            <span className="rounded-full bg-amber-50 px-2 py-0.5 text-[10px] font-semibold text-amber-700">
                                              Not supported by all selected channels
                                            </span>
                                          )}
                                        </span>
                                      </button>
                                    )
                                  })}
                                </div>
                              )}
                            </div>
                            <Field label="Country code">
                              <input
                                className="form-input mt-2 uppercase"
                                value={location.countryCode}
                                maxLength={2}
                                disabled={!canEdit || !editableDefinition}
                                onChange={(event) => {
                                  updateAudienceLocation(index, {
                                    countryCode: event.target.value.toUpperCase(),
                                  })
                                  setLocationSuggestionsByIndex((current) => ({
                                    ...current,
                                    [index]: [],
                                  }))
                                  setLocationSearchErrorByIndex((current) => ({
                                    ...current,
                                    [index]: '',
                                  }))
                                }}
                                placeholder="IN"
                              />
                            </Field>
                            <Field label="Region / state">
                              <input
                                className="form-input mt-2"
                                value={location.region ?? ''}
                                disabled={!canEdit || !editableDefinition}
                                onChange={(event) =>
                                  updateAudienceLocation(index, {
                                    region: event.target.value || null,
                                  })
                                }
                                placeholder="Karnataka"
                              />
                            </Field>
                            <Field label="City">
                              <input
                                className="form-input mt-2"
                                value={location.city ?? ''}
                                disabled={!canEdit || !editableDefinition}
                                onChange={(event) =>
                                  updateAudienceLocation(index, {
                                    city: event.target.value || null,
                                  })
                                }
                                placeholder="Bengaluru"
                              />
                            </Field>
                            <Field label="Postal code">
                              <input
                                className="form-input mt-2"
                                value={location.postalCode ?? ''}
                                disabled={!canEdit || !editableDefinition}
                                onChange={(event) =>
                                  updateAudienceLocation(index, {
                                    postalCode: event.target.value || null,
                                  })
                                }
                              />
                            </Field>
                            <Field label="Radius (km)">
                              <input
                                className="form-input mt-2"
                                type="number"
                                min="0.1"
                                step="0.1"
                                value={location.radiusKm ?? ''}
                                disabled={!canEdit || !editableDefinition}
                                onChange={(event) =>
                                  updateAudienceLocation(index, {
                                    radiusKm: event.target.value
                                      ? Number(event.target.value)
                                      : null,
                                  })
                                }
                                placeholder="Optional"
                              />
                            </Field>
                            <div className="lg:col-span-2">
                              <details className="rounded-xl border border-slate-200 bg-slate-50 px-3 py-2">
                                <summary className="cursor-pointer text-[11px] font-semibold text-slate-600">
                                  Advanced radius coordinates
                                </summary>
                                <p className="mt-2 text-[11px] leading-4 text-slate-400">
                                  Only needed when you use radius targeting and a provider cannot
                                  resolve the place safely from the city/region fields.
                                </p>
                                <div className="mt-2 grid grid-cols-2 gap-3">
                                  <Field label="Latitude">
                                    <input
                                      className="form-input mt-2"
                                      type="number"
                                      min="-90"
                                      max="90"
                                      step="0.000001"
                                      value={location.latitude ?? ''}
                                      disabled={!canEdit || !editableDefinition}
                                      onChange={(event) =>
                                        updateAudienceLocation(index, {
                                          latitude: event.target.value
                                            ? Number(event.target.value)
                                            : null,
                                        })
                                      }
                                      placeholder="Optional"
                                    />
                                  </Field>
                                  <Field label="Longitude">
                                    <input
                                      className="form-input mt-2"
                                      type="number"
                                      min="-180"
                                      max="180"
                                      step="0.000001"
                                      value={location.longitude ?? ''}
                                      disabled={!canEdit || !editableDefinition}
                                      onChange={(event) =>
                                        updateAudienceLocation(index, {
                                          longitude: event.target.value
                                            ? Number(event.target.value)
                                            : null,
                                        })
                                      }
                                      placeholder="Optional"
                                    />
                                  </Field>
                                </div>
                              </details>
                            </div>
                            <div className="flex items-end">
                              <button
                                type="button"
                                className="secondary-button inline-flex w-full items-center justify-center gap-2"
                                disabled={
                                  !canEdit || !editableDefinition || audienceLocations.length === 1
                                }
                                onClick={() =>
                                  setAudienceLocations((current) =>
                                    current.filter((_, locationIndex) => locationIndex !== index),
                                  )
                                }
                              >
                                <FiTrash2 aria-hidden="true" />
                                Remove
                              </button>
                            </div>
                          </div>
                        </div>
                      ))}
                    </div>
                  </div>
                  <Field
                    label="Languages"
                    hint="Google targetable language code or full name, comma separated (for example: en, hi)."
                  >
                    <input
                      className="form-input mt-2"
                      value={languages}
                      disabled={!canEdit || !editableDefinition}
                      onChange={(event) => setLanguages(event.target.value)}
                      placeholder="en, hi"
                    />
                  </Field>
                  <div>
                    <p className="text-xs font-semibold text-slate-700">Age</p>
                    <div className="mt-2 flex flex-wrap gap-2">
                      {[
                        ['All ages', '', ''],
                        ['18–24', '18', '24'],
                        ['25–34', '25', '34'],
                        ['35–44', '35', '44'],
                        ['45–54', '45', '54'],
                        ['55+', '55', ''],
                      ].map(([label, min, max]) => {
                        const selected = ageMin === min && ageMax === max
                        return (
                          <button
                            key={label}
                            type="button"
                            disabled={!canEdit || !editableDefinition}
                            onClick={() => {
                              setAgeMin(min)
                              setAgeMax(max)
                            }}
                            className={
                              'rounded-full border px-3 py-1.5 text-xs font-semibold transition ' +
                              (selected
                                ? 'border-indigo-300 bg-indigo-50 text-indigo-700'
                                : 'border-slate-200 bg-white text-slate-600 hover:border-indigo-200')
                            }
                          >
                            {label}
                          </button>
                        )
                      })}
                    </div>
                    <details className="mt-3">
                      <summary className="cursor-pointer text-[11px] font-semibold text-slate-500">
                        Custom age range
                      </summary>
                      <div className="mt-2 grid grid-cols-2 gap-3">
                        <Field label="Minimum age">
                          <input
                            className="form-input mt-2"
                            type="number"
                            min="13"
                            value={ageMin}
                            disabled={!canEdit || !editableDefinition}
                            onChange={(event) => setAgeMin(event.target.value)}
                          />
                        </Field>
                        <Field label="Maximum age">
                          <input
                            className="form-input mt-2"
                            type="number"
                            min="13"
                            value={ageMax}
                            disabled={!canEdit || !editableDefinition}
                            onChange={(event) => setAgeMax(event.target.value)}
                          />
                        </Field>
                      </div>
                    </details>
                  </div>
                  <Field label="Gender">
                    <div className="mt-3 flex gap-4">
                      {['MALE', 'FEMALE'].map((value) => (
                        <label
                          key={value}
                          className="inline-flex items-center gap-2 text-sm text-slate-600"
                        >
                          <input
                            type="checkbox"
                            checked={genders.includes(value)}
                            disabled={!canEdit || !editableDefinition}
                            onChange={(event) =>
                              setGenders((current) =>
                                event.target.checked
                                  ? [...current, value]
                                  : current.filter((item) => item !== value),
                              )
                            }
                          />
                          {pretty(value)}
                        </label>
                      ))}
                    </div>
                  </Field>
                  <Field
                    label="Audience interests"
                    hint="Describe the audience in business terms. For Meta, choose verified provider interests in the next step so nothing is silently guessed."
                  >
                    <textarea
                      className="form-input mt-2 min-h-28"
                      value={interests}
                      disabled={!canEdit || !editableDefinition}
                      onChange={(event) => setInterests(event.target.value)}
                    />
                  </Field>
                  <Field
                    label="Search intent"
                    hint="One keyword/theme per line. Used to seed Google Search keywords."
                  >
                    <textarea
                      className="form-input mt-2 min-h-28"
                      value={searchIntents}
                      disabled={!canEdit || !editableDefinition}
                      onChange={(event) => setSearchIntents(event.target.value)}
                    />
                  </Field>
                  {selectedChannels.includes('GOOGLE_SEARCH') && accountByChannel.GOOGLE_SEARCH && (
                    <GoogleKeywordPlannerPanel
                      companyId={selectedCompany.companyId}
                      accountId={accountByChannel.GOOGLE_SEARCH}
                      landingPageUrl={
                        creativeUrl.trim() || campaign?.landingPageUrl || landingPageUrl
                      }
                      locations={audienceLocations}
                      language={listValues(languages)[0] ?? null}
                      includeSearchPartners={googleSearchPartners}
                      currentKeywords={listValues(searchIntents)}
                      disabled={!canEdit || !editableDefinition}
                      onApply={(keywords) => setSearchIntents(keywords.join('\n'))}
                    />
                  )}
                </div>

                <StepActions
                  busy={busy}
                  disabled={!canEdit || !editableDefinition}
                  onBack={() => setStep(3)}
                  onNext={() => void saveAudience()}
                />
              </StepCard>
            )}

            {step === 5 && (
              <StepCard
                title="6. Provider-only settings"
                description="Only settings that cannot be safely inferred belong here."
              >
                {GOOGLE_PROVIDER_AUDIENCE_CHANNELS.filter((channelType) =>
                  selectedChannels.includes(channelType),
                ).map((channelType) => {
                  const accountId = accountByChannel[channelType]
                  const options = (audiencesByAccount[accountId] ?? []).filter(
                    channelType === 'GOOGLE_SEARCH'
                      ? googleAudienceSearchEligible
                      : googleAudienceDisplayEligible,
                  )
                  const selected = googleAudienceIdsByChannel[channelType] ?? []
                  const excluded = googleExcludedAudienceIdsByChannel[channelType] ?? []
                  const pmax = channelType === 'GOOGLE_PERFORMANCE_MAX'

                  return (
                    <section
                      key={channelType}
                      className="mb-4 rounded-2xl border border-slate-200 bg-white p-5"
                    >
                      <div className="flex flex-wrap items-start justify-between gap-3">
                        <div>
                          <h3 className="text-sm font-bold text-slate-900">
                            {pretty(channelType)} audiences
                          </h3>
                          <p className="mt-1 max-w-3xl text-xs leading-5 text-slate-500">
                            {pmax
                              ? 'Google treats these lists as audience signals. They guide optimization but do not hard-restrict delivery.'
                              : channelType === 'GOOGLE_SEARCH'
                                ? 'Selected Search-eligible lists become real USER_LIST criteria. Observation measures/bids without narrowing reach; Targeting restricts delivery to included lists. Exclusions always remain hard exclusions.'
                                : channelType === 'GOOGLE_DEMAND_GEN'
                                  ? 'Selected lists are composed into a reusable Google Audience for grouped Demand Gen targeting.'
                                  : 'Selected lists become real Display ad-group USER_LIST criteria. Exclusions become negative USER_LIST criteria.'}
                          </p>
                        </div>
                        <span className="rounded-full bg-slate-100 px-2.5 py-1 text-[10px] font-semibold text-slate-600">
                          {options.length} eligible list{options.length === 1 ? '' : 's'}
                        </span>
                      </div>

                      {!accountId ? (
                        <p className="mt-4 rounded-xl border border-amber-200 bg-amber-50 px-3 py-2 text-xs text-amber-800">
                          Choose the Google Ads account for this channel first.
                        </p>
                      ) : options.length === 0 ? (
                        <p className="mt-4 rounded-xl border border-amber-200 bg-amber-50 px-3 py-2 text-xs leading-5 text-amber-800">
                          No {channelType === 'GOOGLE_SEARCH' ? 'Search' : 'Display'}-eligible
                          Google audience lists are available for this account. Use Refresh provider
                          resources after creating or updating Customer Match / remarketing lists in
                          Google Ads.
                        </p>
                      ) : (
                        <div className="mt-4 grid gap-2">
                          {options.map((audience) => {
                            const includeChecked = selected.includes(audience.id)
                            const excludeChecked = excluded.includes(audience.id)
                            return (
                              <div
                                key={audience.id}
                                className="rounded-xl border border-slate-200 px-3 py-3"
                              >
                                <div className="flex flex-wrap items-start justify-between gap-3">
                                  <div>
                                    <p className="text-xs font-semibold text-slate-800">
                                      {audience.name}
                                    </p>
                                    <p className="mt-1 text-[11px] text-slate-400">
                                      {audience.subtype
                                        ? pretty(audience.subtype)
                                        : 'Google audience'}
                                      {channelType === 'GOOGLE_SEARCH'
                                        ? audience.approximateCountLowerBound != null
                                          ? ' · Search size ' +
                                            audience.approximateCountLowerBound.toLocaleString()
                                          : ''
                                        : audience.approximateCountUpperBound != null
                                          ? ' · Display size ' +
                                            audience.approximateCountUpperBound.toLocaleString()
                                          : ''}
                                    </p>
                                  </div>
                                  <div className="flex flex-wrap gap-3">
                                    <label className="inline-flex items-center gap-2 text-xs font-medium text-slate-600">
                                      <input
                                        type="checkbox"
                                        checked={includeChecked}
                                        disabled={!canEdit || !editableDefinition}
                                        onChange={(event) =>
                                          toggleGoogleAudienceForChannel(
                                            channelType,
                                            audience.id,
                                            event.target.checked,
                                          )
                                        }
                                      />
                                      {pmax ? 'Use as signal' : 'Include'}
                                    </label>
                                    {!pmax && (
                                      <label className="inline-flex items-center gap-2 text-xs font-medium text-slate-600">
                                        <input
                                          type="checkbox"
                                          checked={excludeChecked}
                                          disabled={!canEdit || !editableDefinition}
                                          onChange={(event) =>
                                            toggleGoogleExcludedAudienceForChannel(
                                              channelType,
                                              audience.id,
                                              event.target.checked,
                                            )
                                          }
                                        />
                                        Exclude
                                      </label>
                                    )}
                                  </div>
                                </div>
                              </div>
                            )
                          })}
                        </div>
                      )}

                      {pmax && (
                        <p className="mt-3 text-[11px] leading-5 text-slate-400">
                          PMax exclusions are intentionally not offered here because audience
                          signals are not hard targeting controls.
                        </p>
                      )}
                    </section>
                  )
                })}

                {selectedChannels.includes('META_ADS') &&
                  campaign &&
                  creativePacks[0] &&
                  campaign.channels.find((channel) => channel.channelType === 'META_ADS') && (
                    <MetaCreativeVariantsEditor
                      companyId={selectedCompany.companyId}
                      campaignId={campaign.id}
                      channelId={
                        campaign.channels.find((channel) => channel.channelType === 'META_ADS')!.id
                      }
                      pack={creativePacks[0]}
                      selectedPositions={[
                        ...metaFacebookPositions,
                        ...metaInstagramPositions,
                        ...metaMessengerPositions,
                        ...metaAudienceNetworkPositions,
                      ]}
                      disabled={!canEdit || !editableDefinition}
                      onSaved={(pack) => setCreativePacks([pack])}
                      onError={setError}
                    />
                  )}

                {selectedChannels.includes('META_ADS') && accountByChannel.META_ADS && (
                  <MetaDetailedTargetingEditor
                    companyId={selectedCompany.companyId}
                    accountId={accountByChannel.META_ADS}
                    selected={metaDetailedInterests}
                    disabled={!canEdit || !editableDefinition}
                    onChange={setMetaDetailedInterests}
                  />
                )}

                <div className="mb-4 rounded-2xl border border-indigo-100 bg-indigo-50/40 p-4">
                  <div className="flex flex-wrap items-center justify-between gap-3">
                    <div>
                      <p className="text-sm font-bold text-slate-900">Smart provider defaults</p>
                      <p className="mt-1 text-xs leading-5 text-slate-500">
                        UnifyDesk selected goal-aware optimization defaults. Open advanced controls
                        only when you need provider-specific bidding, conversion, placement, AI Max,
                        identity, catalog, or creative automation settings.
                      </p>
                    </div>
                    <button
                      type="button"
                      className="secondary-button"
                      onClick={() => setShowAdvancedProviderSettings((value) => !value)}
                    >
                      {showAdvancedProviderSettings
                        ? 'Hide advanced settings'
                        : 'Advanced settings'}
                    </button>
                  </div>
                  <div className="mt-3 grid gap-2 text-xs text-slate-600 sm:grid-cols-2">
                    {selectedChannels.includes('GOOGLE_SEARCH') && (
                      <p>Google Search: {pretty(googleBidStrategy)}</p>
                    )}
                    {selectedChannels.includes('GOOGLE_PERFORMANCE_MAX') && (
                      <p>Performance Max: {pretty(pmaxBidStrategy)}</p>
                    )}
                    {selectedChannels.includes('GOOGLE_DEMAND_GEN') && (
                      <p>Demand Gen: {pretty(demandGenBidStrategy)}</p>
                    )}
                    {selectedChannels.includes('GOOGLE_SHOPPING') && (
                      <p>Shopping: Merchant catalog · priority {shoppingPriority}</p>
                    )}
                    {selectedChannels.includes('GOOGLE_DISPLAY') && (
                      <p>Display: {pretty(displayBidStrategy)}</p>
                    )}
                    {selectedChannels.includes('META_ADS') && (
                      <p>Meta: {pretty(metaOptimizationGoal)}</p>
                    )}
                  </div>
                </div>

                {showAdvancedProviderSettings && (
                  <ReachlyoProviderSettingsStep
                    companyId={selectedCompany.companyId}
                    businessGoal={campaign?.businessGoal ?? businessGoal}
                    currency={campaign?.currency ?? currency}
                    selectedChannels={selectedChannels}
                    accountByChannel={accountByChannel}
                    resourcesByAccount={resourcesByAccount}
                    conversionsByAccount={conversionsByAccount}
                    googlePolitical={googlePolitical}
                    setGooglePolitical={setGooglePolitical}
                    googleBidStrategy={googleBidStrategy}
                    setGoogleBidStrategy={setGoogleBidStrategy}
                    pmaxBidStrategy={pmaxBidStrategy}
                    setPmaxBidStrategy={setPmaxBidStrategy}
                    pmaxYoutubeVideoIds={pmaxYoutubeVideoIds}
                    setPmaxYoutubeVideoIds={setPmaxYoutubeVideoIds}
                    pmaxBrandGuidelinesEnabled={pmaxBrandGuidelinesEnabled}
                    setPmaxBrandGuidelinesEnabled={setPmaxBrandGuidelinesEnabled}
                    pmaxBrandMainColor={pmaxBrandMainColor}
                    setPmaxBrandMainColor={setPmaxBrandMainColor}
                    pmaxBrandAccentColor={pmaxBrandAccentColor}
                    setPmaxBrandAccentColor={setPmaxBrandAccentColor}
                    pmaxBrandFontFamily={pmaxBrandFontFamily}
                    setPmaxBrandFontFamily={setPmaxBrandFontFamily}
                    pmaxMerchantAccounts={pmaxMerchantAccounts}
                    pmaxMerchantId={pmaxMerchantId}
                    setPmaxMerchantId={setPmaxMerchantId}
                    pmaxFeedLabel={pmaxFeedLabel}
                    setPmaxFeedLabel={setPmaxFeedLabel}
                    pmaxLifecycleGoalType={pmaxLifecycleGoalType}
                    setPmaxLifecycleGoalType={setPmaxLifecycleGoalType}
                    pmaxLifecycleTargetOption={pmaxLifecycleTargetOption}
                    setPmaxLifecycleTargetOption={setPmaxLifecycleTargetOption}
                    pmaxLifecycleAdditionalValue={pmaxLifecycleAdditionalValue}
                    setPmaxLifecycleAdditionalValue={setPmaxLifecycleAdditionalValue}
                    pmaxLifecycleHighLifetimeValue={pmaxLifecycleHighLifetimeValue}
                    setPmaxLifecycleHighLifetimeValue={setPmaxLifecycleHighLifetimeValue}
                    pmaxLifecycleValueMultiplier={pmaxLifecycleValueMultiplier}
                    setPmaxLifecycleValueMultiplier={setPmaxLifecycleValueMultiplier}
                    pmaxLifecycleEnableBidAdjustmentsForLoyaltyMembers={
                      pmaxLifecycleEnableBidAdjustmentsForLoyaltyMembers
                    }
                    setPmaxLifecycleEnableBidAdjustmentsForLoyaltyMembers={
                      setPmaxLifecycleEnableBidAdjustmentsForLoyaltyMembers
                    }
                    pmaxLifecycleShowTargetedLoyaltyMemberBenefitsInPla={
                      pmaxLifecycleShowTargetedLoyaltyMemberBenefitsInPla
                    }
                    setPmaxLifecycleShowTargetedLoyaltyMemberBenefitsInPla={
                      setPmaxLifecycleShowTargetedLoyaltyMemberBenefitsInPla
                    }
                    demandGenBidStrategy={demandGenBidStrategy}
                    setDemandGenBidStrategy={setDemandGenBidStrategy}
                    demandGenChannelStrategy={demandGenChannelStrategy}
                    setDemandGenChannelStrategy={setDemandGenChannelStrategy}
                    shoppingMerchantAccounts={shoppingMerchantAccounts}
                    shoppingMerchantId={shoppingMerchantId}
                    setShoppingMerchantId={setShoppingMerchantId}
                    shoppingPriority={shoppingPriority}
                    setShoppingPriority={setShoppingPriority}
                    shoppingEnableLocal={shoppingEnableLocal}
                    setShoppingEnableLocal={setShoppingEnableLocal}
                    displayBidStrategy={displayBidStrategy}
                    setDisplayBidStrategy={setDisplayBidStrategy}
                    displayTargetCpa={displayTargetCpa}
                    setDisplayTargetCpa={setDisplayTargetCpa}
                    displayCpcBid={displayCpcBid}
                    setDisplayCpcBid={setDisplayCpcBid}
                    keywordMatchType={keywordMatchType}
                    setKeywordMatchType={setKeywordMatchType}
                    googleConversionId={googleConversionId}
                    setGoogleConversionId={setGoogleConversionId}
                    googleSearchPartners={googleSearchPartners}
                    setGoogleSearchPartners={setGoogleSearchPartners}
                    googleSearchAudienceMode={googleSearchAudienceMode}
                    setGoogleSearchAudienceMode={setGoogleSearchAudienceMode}
                    googleSearchInterestResources={googleSearchInterestResources}
                    setGoogleSearchInterestResources={setGoogleSearchInterestResources}
                    googleSearchExcludedInterestResources={googleSearchExcludedInterestResources}
                    setGoogleSearchExcludedInterestResources={
                      setGoogleSearchExcludedInterestResources
                    }
                    googleParentalStatusIncludes={googleParentalStatusIncludes}
                    setGoogleParentalStatusIncludes={setGoogleParentalStatusIncludes}
                    googleParentalStatusExcludes={googleParentalStatusExcludes}
                    setGoogleParentalStatusExcludes={setGoogleParentalStatusExcludes}
                    googleIncomeRangeIncludes={googleIncomeRangeIncludes}
                    setGoogleIncomeRangeIncludes={setGoogleIncomeRangeIncludes}
                    googleIncomeRangeExcludes={googleIncomeRangeExcludes}
                    setGoogleIncomeRangeExcludes={setGoogleIncomeRangeExcludes}
                    googleDetailedDemographicIds={googleDetailedDemographicIds}
                    setGoogleDetailedDemographicIds={setGoogleDetailedDemographicIds}
                    googleExcludedDetailedDemographicIds={googleExcludedDetailedDemographicIds}
                    setGoogleExcludedDetailedDemographicIds={
                      setGoogleExcludedDetailedDemographicIds
                    }
                    googleContentNetwork={googleContentNetwork}
                    setGoogleContentNetwork={setGoogleContentNetwork}
                    googleGeoMode={googleGeoMode}
                    setGoogleGeoMode={setGoogleGeoMode}
                    googleDeviceBidModifiers={googleDeviceBidModifiers}
                    setGoogleDeviceBidModifiers={setGoogleDeviceBidModifiers}
                    googleAiMaxEnabled={googleAiMaxEnabled}
                    setGoogleAiMaxEnabled={setGoogleAiMaxEnabled}
                    googleAiMaxTextCustomization={googleAiMaxTextCustomization}
                    setGoogleAiMaxTextCustomization={setGoogleAiMaxTextCustomization}
                    googleAiMaxFinalUrlExpansion={googleAiMaxFinalUrlExpansion}
                    setGoogleAiMaxFinalUrlExpansion={setGoogleAiMaxFinalUrlExpansion}
                    googleAiMaxDisableSearchTermMatching={googleAiMaxDisableSearchTermMatching}
                    setGoogleAiMaxDisableSearchTermMatching={
                      setGoogleAiMaxDisableSearchTermMatching
                    }
                    googleAiMaxTermExclusions={googleAiMaxTermExclusions}
                    setGoogleAiMaxTermExclusions={setGoogleAiMaxTermExclusions}
                    googleAiMaxMessagingRestrictions={googleAiMaxMessagingRestrictions}
                    setGoogleAiMaxMessagingRestrictions={setGoogleAiMaxMessagingRestrictions}
                    googleAiMaxUrlExclusions={googleAiMaxUrlExclusions}
                    setGoogleAiMaxUrlExclusions={setGoogleAiMaxUrlExclusions}
                    googleAiMaxUrlInclusions={googleAiMaxUrlInclusions}
                    setGoogleAiMaxUrlInclusions={setGoogleAiMaxUrlInclusions}
                    googleAiMaxBrandInclusions={googleAiMaxBrandInclusions}
                    setGoogleAiMaxBrandInclusions={setGoogleAiMaxBrandInclusions}
                    googleAiMaxBrandExclusions={googleAiMaxBrandExclusions}
                    setGoogleAiMaxBrandExclusions={setGoogleAiMaxBrandExclusions}
                    googleAdSchedules={googleAdSchedules}
                    setGoogleAdSchedules={setGoogleAdSchedules}
                    googleNegativeKeywords={googleNegativeKeywords}
                    setGoogleNegativeKeywords={setGoogleNegativeKeywords}
                    googleNegativeKeywordLists={googleNegativeKeywordLists}
                    selectedGoogleNegativeKeywordListIds={selectedGoogleNegativeKeywordListIds}
                    setSelectedGoogleNegativeKeywordListIds={
                      setSelectedGoogleNegativeKeywordListIds
                    }
                    metaPageId={metaPageId}
                    setMetaPageId={setMetaPageId}
                    metaInstagramId={metaInstagramId}
                    setMetaInstagramId={setMetaInstagramId}
                    metaOptimizationGoal={metaOptimizationGoal}
                    setMetaOptimizationGoal={setMetaOptimizationGoal}
                    metaBillingEvent={metaBillingEvent}
                    setMetaBillingEvent={setMetaBillingEvent}
                    metaSpecialCategory={metaSpecialCategory}
                    setMetaSpecialCategory={setMetaSpecialCategory}
                    metaPixelId={metaPixelId}
                    setMetaPixelId={setMetaPixelId}
                    metaConversionMode={metaConversionMode}
                    setMetaConversionMode={setMetaConversionMode}
                    metaConversionId={metaConversionId}
                    setMetaConversionId={setMetaConversionId}
                    metaStandardEvent={metaStandardEvent}
                    setMetaStandardEvent={setMetaStandardEvent}
                    metaPlacementMode={metaPlacementMode}
                    setMetaPlacementMode={setMetaPlacementMode}
                    metaPublisherPlatforms={metaPublisherPlatforms}
                    setMetaPublisherPlatforms={setMetaPublisherPlatforms}
                    metaFacebookPositions={metaFacebookPositions}
                    setMetaFacebookPositions={setMetaFacebookPositions}
                    metaInstagramPositions={metaInstagramPositions}
                    setMetaInstagramPositions={setMetaInstagramPositions}
                    metaMessengerPositions={metaMessengerPositions}
                    setMetaMessengerPositions={setMetaMessengerPositions}
                    metaAudienceNetworkPositions={metaAudienceNetworkPositions}
                    setMetaAudienceNetworkPositions={setMetaAudienceNetworkPositions}
                    metaAdvantageAudience={metaAdvantageAudience}
                    setMetaAdvantageAudience={setMetaAdvantageAudience}
                    metaAdvantageAgeSuggestion={metaAdvantageAgeSuggestion}
                    setMetaAdvantageAgeSuggestion={setMetaAdvantageAgeSuggestion}
                    metaAdvantageGenderSuggestion={metaAdvantageGenderSuggestion}
                    setMetaAdvantageGenderSuggestion={setMetaAdvantageGenderSuggestion}
                    metaAdvantagePlusCreative={metaAdvantagePlusCreative}
                    setMetaAdvantagePlusCreative={setMetaAdvantagePlusCreative}
                    metaGenerativeText={metaGenerativeText}
                    setMetaGenerativeText={setMetaGenerativeText}
                    metaImageExpansion={metaImageExpansion}
                    setMetaImageExpansion={setMetaImageExpansion}
                    metaCatalogEnabled={metaCatalogEnabled}
                    setMetaCatalogEnabled={setMetaCatalogEnabled}
                    metaBackgroundGeneration={metaBackgroundGeneration}
                    setMetaBackgroundGeneration={setMetaBackgroundGeneration}
                    metaProductSetId={metaProductSetId}
                    setMetaProductSetId={setMetaProductSetId}
                    metaExistingPostId={metaExistingPostId}
                    setMetaExistingPostId={setMetaExistingPostId}
                    disabled={!canEdit || !editableDefinition}
                    onRefresh={() => void loadProviderData(true)}
                  />
                )}

                {!showAdvancedProviderSettings && (
                  <p className="rounded-xl border border-slate-200 bg-slate-50 px-4 py-3 text-xs leading-5 text-slate-500">
                    Review will still validate required provider identities, conversion resources,
                    targeting compatibility and capabilities. If anything is missing, open Advanced
                    settings here before launch.
                  </p>
                )}

                <StepActions
                  busy={busy}
                  disabled={!canEdit || !editableDefinition}
                  onBack={() => setStep(4)}
                  onNext={() => void saveProviderSettings()}
                />
              </StepCard>
            )}

            {step === 6 && (
              <div className="grid gap-4">
                <div className="panel flex flex-wrap items-center justify-between gap-4 p-5">
                  <div>
                    <h2 className="text-lg font-bold text-slate-950">7. Provider plan review</h2>
                    <p className="mt-1 text-sm text-slate-500">
                      Compilation is side-effect free. Review exact operations, warnings and
                      audience mapping before preparing launch.
                    </p>
                  </div>
                  <button
                    type="button"
                    className="secondary-button inline-flex items-center gap-2"
                    disabled={reviewLoading}
                    onClick={() => void compilePlans()}
                  >
                    <FiRefreshCw aria-hidden="true" />
                    Recompile
                  </button>
                </div>

                <div className="panel p-5">
                  <p className="text-xs font-semibold uppercase tracking-wide text-slate-400">
                    Business campaign summary
                  </p>
                  <div className="mt-3 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
                    <div>
                      <p className="text-xs text-slate-400">Goal</p>
                      <p className="mt-1 text-sm font-bold text-slate-900">
                        {pretty(businessGoal)}
                      </p>
                    </div>
                    <div>
                      <p className="text-xs text-slate-400">Budget</p>
                      <p className="mt-1 text-sm font-bold text-slate-900">
                        {budgetAmount || '—'} {campaign?.currency ?? currency}
                      </p>
                    </div>
                    <div>
                      <p className="text-xs text-slate-400">Channels</p>
                      <p className="mt-1 text-sm font-bold text-slate-900">
                        {enabledChannels.map((channel) => pretty(channel.channelType)).join(', ') ||
                          '—'}
                      </p>
                    </div>
                    <div>
                      <p className="text-xs text-slate-400">Audience</p>
                      <p className="mt-1 text-sm font-bold text-slate-900">
                        {audienceLocations
                          .map((location) =>
                            [location.city, location.region, location.countryCode]
                              .filter(Boolean)
                              .join(', '),
                          )
                          .filter(Boolean)
                          .join(' · ') || 'Broad'}
                      </p>
                    </div>
                  </div>
                </div>

                <ReachlyoProviderPlanReview
                  plans={plans}
                  accounts={accounts}
                  loading={reviewLoading}
                />

                {selectedChannels.some(
                  (type) => !CHANNELS.find((option) => option.type === type)?.executable,
                ) && (
                  <div className="rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm leading-6 text-amber-800">
                    At least one selected channel is plan-preview only. Launch preparation will
                    block before any provider mutation until its paused executor is implemented.
                  </div>
                )}

                <div className="flex flex-wrap items-center justify-between gap-3">
                  <button type="button" className="secondary-button" onClick={() => setStep(5)}>
                    Back
                  </button>
                  <button
                    type="button"
                    className="primary-button inline-flex items-center gap-2"
                    disabled={busy || reviewLoading || plans.length === 0}
                    onClick={() => void prepareLaunch()}
                  >
                    <FiCheck aria-hidden="true" />
                    {busy ? 'Preparing…' : 'Prepare paused launch'}
                  </button>
                </div>
              </div>
            )}

            {step === 7 && (
              <div className="grid gap-4">
                <div className="panel p-5">
                  <h2 className="text-lg font-bold text-slate-950">8. Launch & retry</h2>
                  <p className="mt-1 text-sm leading-6 text-slate-500">
                    The persistent Saga creates supported provider resources paused. Partial
                    failures retain completed steps and retry only unfinished work.
                  </p>
                </div>

                {currentRun ? (
                  <ReachlyoLaunchRunPanel
                    run={currentRun}
                    busy={busy}
                    onRefresh={() => void refreshRun()}
                    onExecute={
                      currentRun.status === 'READY' ? () => void executeRun(false) : undefined
                    }
                    onRetry={
                      currentRun.status === 'FAILED' || currentRun.status === 'PARTIAL_FAILURE'
                        ? () => void executeRun(true)
                        : undefined
                    }
                  />
                ) : (
                  <div className="panel p-10 text-center">
                    <p className="text-sm text-slate-500">No launch run yet.</p>
                    <button
                      type="button"
                      className="primary-button mt-4"
                      onClick={() => setStep(6)}
                    >
                      Review provider plans
                    </button>
                  </div>
                )}

                {campaign && campaign.deployments.length > 0 && (
                  <>
                    <ReachlyoCampaignAnalyticsPanel
                      companyId={selectedCompany.companyId}
                      campaignId={campaign.id}
                      canSync={canSyncAnalytics}
                    />
                    <ReachlyoCampaignRecommendationsPanel
                      companyId={selectedCompany.companyId}
                      campaignId={campaign.id}
                    />
                  </>
                )}

                {launchRuns.length > 1 && (
                  <div className="panel overflow-hidden">
                    <div className="border-b border-slate-100 px-5 py-4">
                      <h3 className="text-base font-bold text-slate-950">Launch history</h3>
                    </div>
                    <div className="divide-y divide-slate-100">
                      {launchRuns.map((run) => (
                        <button
                          key={run.id}
                          type="button"
                          className="flex w-full items-center justify-between gap-3 px-5 py-3 text-left hover:bg-slate-50"
                          onClick={() => setCurrentRun(run)}
                        >
                          <div>
                            <p className="text-sm font-semibold text-slate-800">Run {run.id}</p>
                            <p className="mt-1 text-xs text-slate-400">
                              {new Date(run.createdAt).toLocaleString()}
                            </p>
                          </div>
                          <span
                            className={
                              'rounded-full px-2.5 py-1 text-[11px] font-semibold ' +
                              statusClass(run.status)
                            }
                          >
                            {pretty(run.status)}
                          </span>
                        </button>
                      ))}
                    </div>
                  </div>
                )}
              </div>
            )}
          </main>
        </div>
      </div>
    </section>
  )
}

function StepCard({
  title,
  description,
  children,
}: {
  title: string
  description: string
  children: ReactNode
}) {
  return (
    <div className="panel p-5 sm:p-6">
      <div className="border-b border-slate-100 pb-5">
        <h2 className="text-lg font-bold text-slate-950">{title}</h2>
        <p className="mt-1 max-w-3xl text-sm leading-6 text-slate-500">{description}</p>
      </div>
      <div className="pt-5">{children}</div>
    </div>
  )
}

function Field({ label, hint, children }: { label: string; hint?: string; children: ReactNode }) {
  return (
    <label className="block text-xs font-semibold text-slate-700">
      {label}
      {children}
      {hint && (
        <span className="mt-1.5 block text-[11px] font-normal leading-4 text-slate-400">
          {hint}
        </span>
      )}
    </label>
  )
}

function StepActions({
  busy,
  disabled,
  onBack,
  onNext,
  nextLabel = 'Save & continue',
}: {
  busy: boolean
  disabled?: boolean
  onBack?: () => void
  onNext: () => void
  nextLabel?: string
}) {
  return (
    <div className="mt-6 flex flex-wrap items-center justify-between gap-3 border-t border-slate-100 pt-5">
      <div>
        {onBack && (
          <button type="button" className="secondary-button" onClick={onBack}>
            Back
          </button>
        )}
      </div>
      <button
        type="button"
        className="primary-button inline-flex items-center gap-2"
        disabled={busy || disabled}
        onClick={onNext}
      >
        <FiArrowRight aria-hidden="true" />
        {busy ? 'Saving…' : nextLabel}
      </button>
    </div>
  )
}
