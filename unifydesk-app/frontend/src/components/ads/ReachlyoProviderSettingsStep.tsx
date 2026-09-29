import type { ReactNode } from 'react'
import { FiClock, FiPlus, FiRefreshCw, FiTrash2 } from 'react-icons/fi'
import GoogleAiMaxBrandSelector from './GoogleAiMaxBrandSelector'
import GoogleSearchInterestSelector from './GoogleSearchInterestSelector'
import GoogleSearchDemographicSelector from './GoogleSearchDemographicSelector'
import GooglePerformanceMaxLifecycleLaunchSettings from './GooglePerformanceMaxLifecycleLaunchSettings'
import type {
  AdAccountResource,
  AdConversion,
  GoogleNegativeKeywordInput,
  GoogleNegativeKeywordList,
  GoogleBrandSuggestion,
  CampaignBusinessGoal,
  CampaignChannelType,
  GoogleMerchantAccount,
  GoogleLifecycleGoalType,
} from '../../features/ads/types'

export type GoogleAdScheduleDraft = {
  dayOfWeek: string
  startTime: string
  endTime: string
}

type Props = {
  companyId: string
  businessGoal: CampaignBusinessGoal
  currency: string
  selectedChannels: CampaignChannelType[]
  accountByChannel: Record<string, string>
  resourcesByAccount: Record<string, AdAccountResource[]>
  conversionsByAccount: Record<string, AdConversion[]>
  googlePolitical: 'true' | 'false' | ''
  setGooglePolitical: (value: 'true' | 'false' | '') => void
  googleBidStrategy: string
  setGoogleBidStrategy: (value: string) => void
  pmaxBidStrategy: 'MAXIMIZE_CONVERSIONS' | 'MAXIMIZE_CONVERSION_VALUE'
  setPmaxBidStrategy: (
    value: 'MAXIMIZE_CONVERSIONS' | 'MAXIMIZE_CONVERSION_VALUE',
  ) => void
  pmaxYoutubeVideoIds: string
  setPmaxYoutubeVideoIds: (value: string) => void
  pmaxBrandGuidelinesEnabled: boolean
  setPmaxBrandGuidelinesEnabled: (value: boolean) => void
  pmaxBrandMainColor: string
  setPmaxBrandMainColor: (value: string) => void
  pmaxBrandAccentColor: string
  setPmaxBrandAccentColor: (value: string) => void
  pmaxBrandFontFamily: string
  setPmaxBrandFontFamily: (value: string) => void
  pmaxLifecycleGoalType: GoogleLifecycleGoalType | ''
  setPmaxLifecycleGoalType: (value: GoogleLifecycleGoalType | '') => void
  pmaxLifecycleTargetOption: 'TARGET_ALL' | 'TARGET_SPECIFIC'
  setPmaxLifecycleTargetOption: (value: 'TARGET_ALL' | 'TARGET_SPECIFIC') => void
  pmaxLifecycleAdditionalValue: string
  setPmaxLifecycleAdditionalValue: (value: string) => void
  pmaxLifecycleHighLifetimeValue: string
  setPmaxLifecycleHighLifetimeValue: (value: string) => void
  pmaxLifecycleValueMultiplier: string
  setPmaxLifecycleValueMultiplier: (value: string) => void
  pmaxLifecycleEnableBidAdjustmentsForLoyaltyMembers: boolean
  setPmaxLifecycleEnableBidAdjustmentsForLoyaltyMembers: (value: boolean) => void
  pmaxLifecycleShowTargetedLoyaltyMemberBenefitsInPla: boolean
  setPmaxLifecycleShowTargetedLoyaltyMemberBenefitsInPla: (value: boolean) => void
  demandGenBidStrategy:
    | 'MAXIMIZE_CLICKS'
    | 'MAXIMIZE_CONVERSIONS'
    | 'MAXIMIZE_CONVERSION_VALUE'
  setDemandGenBidStrategy: (
    value:
      | 'MAXIMIZE_CLICKS'
      | 'MAXIMIZE_CONVERSIONS'
      | 'MAXIMIZE_CONVERSION_VALUE',
  ) => void
  demandGenChannelStrategy: 'ALL_CHANNELS' | 'ALL_OWNED_AND_OPERATED_CHANNELS'
  setDemandGenChannelStrategy: (
    value: 'ALL_CHANNELS' | 'ALL_OWNED_AND_OPERATED_CHANNELS',
  ) => void
  shoppingMerchantAccounts: GoogleMerchantAccount[]
  shoppingMerchantId: string
  setShoppingMerchantId: (value: string) => void
  shoppingPriority: '0' | '1' | '2'
  setShoppingPriority: (value: '0' | '1' | '2') => void
  shoppingEnableLocal: boolean
  setShoppingEnableLocal: (value: boolean) => void
  displayBidStrategy: 'MANUAL_CPC' | 'MAXIMIZE_CONVERSIONS' | 'TARGET_CPA'
  setDisplayBidStrategy: (
    value: 'MANUAL_CPC' | 'MAXIMIZE_CONVERSIONS' | 'TARGET_CPA',
  ) => void
  displayTargetCpa: string
  setDisplayTargetCpa: (value: string) => void
  displayCpcBid: string
  setDisplayCpcBid: (value: string) => void
  keywordMatchType: string
  setKeywordMatchType: (value: string) => void
  googleConversionId: string
  setGoogleConversionId: (value: string) => void
  googleSearchPartners: boolean
  setGoogleSearchPartners: (value: boolean) => void
  googleSearchAudienceMode: 'OBSERVATION' | 'TARGETING'
  setGoogleSearchAudienceMode: (value: 'OBSERVATION' | 'TARGETING') => void
  googleSearchInterestResources: string[]
  setGoogleSearchInterestResources: (value: string[]) => void
  googleSearchExcludedInterestResources: string[]
  setGoogleSearchExcludedInterestResources: (value: string[]) => void
  googleParentalStatusIncludes: string[]
  setGoogleParentalStatusIncludes: (value: string[]) => void
  googleParentalStatusExcludes: string[]
  setGoogleParentalStatusExcludes: (value: string[]) => void
  googleIncomeRangeIncludes: string[]
  setGoogleIncomeRangeIncludes: (value: string[]) => void
  googleIncomeRangeExcludes: string[]
  setGoogleIncomeRangeExcludes: (value: string[]) => void
  googleDetailedDemographicIds: string[]
  setGoogleDetailedDemographicIds: (value: string[]) => void
  googleExcludedDetailedDemographicIds: string[]
  setGoogleExcludedDetailedDemographicIds: (value: string[]) => void
  googleContentNetwork: boolean
  setGoogleContentNetwork: (value: boolean) => void
  googleGeoMode: 'PRESENCE' | 'PRESENCE_OR_INTEREST'
  setGoogleGeoMode: (value: 'PRESENCE' | 'PRESENCE_OR_INTEREST') => void
  googleDeviceBidModifiers: Record<'DESKTOP' | 'MOBILE' | 'TABLET', string>
  setGoogleDeviceBidModifiers: (
    value: Record<'DESKTOP' | 'MOBILE' | 'TABLET', string>,
  ) => void
  googleAiMaxEnabled: boolean
  setGoogleAiMaxEnabled: (value: boolean) => void
  googleAiMaxTextCustomization: boolean
  setGoogleAiMaxTextCustomization: (value: boolean) => void
  googleAiMaxFinalUrlExpansion: boolean
  setGoogleAiMaxFinalUrlExpansion: (value: boolean) => void
  googleAiMaxDisableSearchTermMatching: boolean
  setGoogleAiMaxDisableSearchTermMatching: (value: boolean) => void
  googleAiMaxTermExclusions: string
  setGoogleAiMaxTermExclusions: (value: string) => void
  googleAiMaxMessagingRestrictions: string
  setGoogleAiMaxMessagingRestrictions: (value: string) => void
  googleAiMaxUrlExclusions: string
  setGoogleAiMaxUrlExclusions: (value: string) => void
  googleAiMaxUrlInclusions: string
  setGoogleAiMaxUrlInclusions: (value: string) => void
  googleAiMaxBrandInclusions: GoogleBrandSuggestion[]
  setGoogleAiMaxBrandInclusions: (value: GoogleBrandSuggestion[]) => void
  googleAiMaxBrandExclusions: GoogleBrandSuggestion[]
  setGoogleAiMaxBrandExclusions: (value: GoogleBrandSuggestion[]) => void
  googleAdSchedules: GoogleAdScheduleDraft[]
  setGoogleAdSchedules: (value: GoogleAdScheduleDraft[]) => void
  googleNegativeKeywords: GoogleNegativeKeywordInput[]
  setGoogleNegativeKeywords: (value: GoogleNegativeKeywordInput[]) => void
  googleNegativeKeywordLists: GoogleNegativeKeywordList[]
  selectedGoogleNegativeKeywordListIds: string[]
  setSelectedGoogleNegativeKeywordListIds: (value: string[]) => void
  metaPageId: string
  setMetaPageId: (value: string) => void
  metaInstagramId: string
  setMetaInstagramId: (value: string) => void
  metaOptimizationGoal: string
  setMetaOptimizationGoal: (value: string) => void
  metaBillingEvent: string
  setMetaBillingEvent: (value: string) => void
  metaSpecialCategory: string
  setMetaSpecialCategory: (value: string) => void
  metaPixelId: string
  setMetaPixelId: (value: string) => void
  metaConversionMode: 'PROVIDER' | 'STANDARD'
  setMetaConversionMode: (value: 'PROVIDER' | 'STANDARD') => void
  metaConversionId: string
  setMetaConversionId: (value: string) => void
  metaStandardEvent: string
  setMetaStandardEvent: (value: string) => void
  metaPlacementMode: 'AUTO' | 'MANUAL'
  setMetaPlacementMode: (value: 'AUTO' | 'MANUAL') => void
  metaPublisherPlatforms: string[]
  setMetaPublisherPlatforms: (value: string[]) => void
  metaFacebookPositions: string[]
  setMetaFacebookPositions: (value: string[]) => void
  metaInstagramPositions: string[]
  setMetaInstagramPositions: (value: string[]) => void
  metaMessengerPositions: string[]
  setMetaMessengerPositions: (value: string[]) => void
  metaAudienceNetworkPositions: string[]
  setMetaAudienceNetworkPositions: (value: string[]) => void
  metaAdvantageAudience: boolean
  setMetaAdvantageAudience: (value: boolean) => void
  metaAdvantageAgeSuggestion: boolean
  setMetaAdvantageAgeSuggestion: (value: boolean) => void
  metaAdvantageGenderSuggestion: boolean
  setMetaAdvantageGenderSuggestion: (value: boolean) => void
  metaAdvantagePlusCreative: boolean
  setMetaAdvantagePlusCreative: (value: boolean) => void
  metaGenerativeText: boolean
  setMetaGenerativeText: (value: boolean) => void
  metaImageExpansion: boolean
  setMetaImageExpansion: (value: boolean) => void
  metaCatalogEnabled: boolean
  setMetaCatalogEnabled: (value: boolean) => void
  metaBackgroundGeneration: boolean
  setMetaBackgroundGeneration: (value: boolean) => void
  metaProductSetId: string
  setMetaProductSetId: (value: string) => void
  metaExistingPostId: string
  setMetaExistingPostId: (value: string) => void
  disabled: boolean
  onRefresh: () => void
}

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

const isRecommendedConversion = (
  conversion: AdConversion,
  goal: CampaignBusinessGoal,
  provider: 'GOOGLE_ADS' | 'META_ADS',
) => {
  const event = conversion.customEventType?.trim().toUpperCase()
  if (!event) return false
  const recommended = provider === 'GOOGLE_ADS' ? GOOGLE_GOAL_EVENTS[goal] : META_GOAL_EVENTS[goal]
  return recommended?.includes(event) ?? false
}

const recommendedMetaEvent = (goal: CampaignBusinessGoal) =>
  META_GOAL_EVENTS[goal]?.[0] ?? ''

const META_STANDARD_EVENTS = [
  'ACHIEVEMENT_UNLOCKED',
  'ADD_PAYMENT_INFO',
  'ADD_TO_CART',
  'ADD_TO_WISHLIST',
  'AD_IMPRESSION',
  'COMPLETE_REGISTRATION',
  'CONTACT',
  'CONTENT_VIEW',
  'CUSTOMIZE_PRODUCT',
  'D2_RETENTION',
  'D7_RETENTION',
  'DONATE',
  'FIND_LOCATION',
  'INITIATED_CHECKOUT',
  'LEAD',
  'LEVEL_ACHIEVED',
  'LISTING_INTERACTION',
  'MESSAGING_CONVERSATION_STARTED_7D',
  'OTHER',
  'PURCHASE',
  'RATE',
  'SCHEDULE',
  'SEARCH',
  'SERVICE_BOOKING_REQUEST',
  'SPENT_CREDITS',
  'START_TRIAL',
  'SUBMIT_APPLICATION',
  'SUBSCRIBE',
  'TUTORIAL_COMPLETION',
] as const

const DAYS = [
  'MONDAY',
  'TUESDAY',
  'WEDNESDAY',
  'THURSDAY',
  'FRIDAY',
  'SATURDAY',
  'SUNDAY',
]

const quarterHours = (includeEndOfDay: boolean) => {
  const values: string[] = []
  const max = includeEndOfDay ? 96 : 95
  for (let index = 0; index <= max; index += 1) {
    const totalMinutes = index * 15
    const hour = Math.floor(totalMinutes / 60)
    const minute = totalMinutes % 60
    values.push(
      String(hour).padStart(2, '0') + ':' + String(minute).padStart(2, '0'),
    )
  }
  return values
}

const START_TIMES = quarterHours(false)
const END_TIMES = quarterHours(true)

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

const pretty = (value: string) =>
  value
    .replaceAll('_', ' ')
    .toLowerCase()
    .replace(/\b\w/g, (character) => character.toUpperCase())

export default function ReachlyoProviderSettingsStep(props: Props) {
  const googleAccount =
    props.accountByChannel.GOOGLE_SEARCH ??
    props.accountByChannel.GOOGLE_PERFORMANCE_MAX ??
    props.accountByChannel.GOOGLE_DEMAND_GEN ??
    props.accountByChannel.GOOGLE_SHOPPING ??
    props.accountByChannel.GOOGLE_DISPLAY ??
    ''
  const metaAccount = props.accountByChannel.META_ADS ?? ''
  const googleConversions = props.conversionsByAccount[googleAccount] ?? []
  const metaConversions = props.conversionsByAccount[metaAccount] ?? []
  const metaResources = props.resourcesByAccount[metaAccount] ?? []
  const conversionRequired = requiresConversion(props.businessGoal)

  return (
    <div className="grid gap-4">
      <div className="flex justify-end">
        <button
          type="button"
          className="secondary-button inline-flex items-center gap-2"
          onClick={props.onRefresh}
        >
          <FiRefreshCw aria-hidden="true" />
          Refresh provider resources
        </button>
      </div>

      {props.selectedChannels.includes('GOOGLE_DISPLAY') && (
        <section className="rounded-2xl border border-slate-200 p-5">
          <h3 className="text-base font-bold text-slate-950">Google Display</h3>
          <p className="mt-1 text-xs leading-5 text-slate-500">
            UnifyDesk creates a paused Display campaign, paused Display ad group,
            and responsive display ad from the shared visual Creative Pack.
          </p>
          <div className="mt-4 grid gap-4 sm:grid-cols-2">
            <Field label="EU political advertising">
              <select
                className="form-input mt-2"
                value={props.googlePolitical}
                disabled={props.disabled}
                onChange={(event) =>
                  props.setGooglePolitical(
                    event.target.value as 'true' | 'false' | '',
                  )
                }
              >
                <option value="">Choose</option>
                <option value="false">
                  Does not contain EU political advertising
                </option>
                <option value="true">
                  Contains EU political advertising
                </option>
              </select>
            </Field>
            <Field label="Bid strategy">
              <select
                className="form-input mt-2"
                value={props.displayBidStrategy}
                disabled={props.disabled}
                onChange={(event) =>
                  props.setDisplayBidStrategy(
                    event.target.value as
                      | 'MANUAL_CPC'
                      | 'MAXIMIZE_CONVERSIONS'
                      | 'TARGET_CPA',
                  )
                }
              >
                <option value="MANUAL_CPC">Manual CPC</option>
                <option value="MAXIMIZE_CONVERSIONS">
                  Maximize conversions
                </option>
                <option value="TARGET_CPA">Target CPA</option>
              </select>
            </Field>
            {props.displayBidStrategy === 'MANUAL_CPC' && (
              <Field
                label={'Ad-group CPC bid (' + props.currency + ')'}
                hint="Optional. If blank, UnifyDesk derives a conservative CPC from the daily budget."
              >
                <input
                  className="form-input mt-2"
                  type="number"
                  min="0"
                  step="0.01"
                  value={props.displayCpcBid}
                  disabled={props.disabled}
                  onChange={(event) =>
                    props.setDisplayCpcBid(event.target.value)
                  }
                />
              </Field>
            )}
            {props.displayBidStrategy === 'TARGET_CPA' && (
              <Field label={'Target CPA (' + props.currency + ')'}>
                <input
                  className="form-input mt-2"
                  type="number"
                  min="0"
                  step="0.01"
                  value={props.displayTargetCpa}
                  disabled={props.disabled}
                  onChange={(event) =>
                    props.setDisplayTargetCpa(event.target.value)
                  }
                />
              </Field>
            )}
            {conversionRequired && (
              <Field label="Conversion action">
                <select
                  className="form-input mt-2"
                  value={props.googleConversionId}
                  disabled={props.disabled}
                  onChange={(event) =>
                    props.setGoogleConversionId(event.target.value)
                  }
                >
                  <option value="">Select conversion</option>
                  {googleConversions.map((conversion) => (
                    <option key={conversion.id} value={conversion.id}>
                      {conversion.name}
                      {conversion.customEventType
                        ? ' · ' + pretty(conversion.customEventType)
                        : ''}
                      {isRecommendedConversion(
                        conversion,
                        props.businessGoal,
                        'GOOGLE_ADS',
                      )
                        ? ' · Recommended'
                        : ''}
                    </option>
                  ))}
                </select>
              </Field>
            )}
          </div>
        </section>
      )}

      {props.selectedChannels.includes('GOOGLE_SHOPPING') && (
        <section className="rounded-2xl border border-slate-200 p-5">
          <h3 className="text-base font-bold text-slate-950">Google Shopping</h3>
          <p className="mt-1 text-xs leading-5 text-slate-500">
            Shopping uses your Merchant Center catalog instead of a Creative Pack.
            UnifyDesk creates the budget, paused Shopping campaign, paused product
            ad group, paused product ad, and root listing group together.
          </p>
          <div className="mt-4 grid gap-4 sm:grid-cols-2">
            <Field label="Merchant Center account">
              <select
                className="form-input mt-2"
                value={props.shoppingMerchantId}
                disabled={props.disabled}
                onChange={(event) =>
                  props.setShoppingMerchantId(event.target.value)
                }
              >
                <option value="">Choose Merchant Center</option>
                {props.shoppingMerchantAccounts.map((merchant) => (
                  <option key={merchant.accountId} value={merchant.accountId}>
                    {(merchant.accountName || 'Merchant Center') +
                      ' · ' +
                      merchant.accountId}
                  </option>
                ))}
              </select>
            </Field>
            <Field
              label="Campaign priority"
              hint="0 = low, 1 = medium, 2 = high. Google uses this when the same product can serve from multiple Standard Shopping campaigns."
            >
              <select
                className="form-input mt-2"
                value={props.shoppingPriority}
                disabled={props.disabled}
                onChange={(event) =>
                  props.setShoppingPriority(
                    event.target.value as '0' | '1' | '2',
                  )
                }
              >
                <option value="0">Low · 0</option>
                <option value="1">Medium · 1</option>
                <option value="2">High · 2</option>
              </select>
            </Field>
            <Field label="EU political advertising">
              <select
                className="form-input mt-2"
                value={props.googlePolitical}
                disabled={props.disabled}
                onChange={(event) =>
                  props.setGooglePolitical(
                    event.target.value as 'true' | 'false' | '',
                  )
                }
              >
                <option value="">Choose</option>
                <option value="false">
                  Does not contain EU political advertising
                </option>
                <option value="true">
                  Contains EU political advertising
                </option>
              </select>
            </Field>
            {conversionRequired && (
              <Field label="Purchase conversion">
                <select
                  className="form-input mt-2"
                  value={props.googleConversionId}
                  disabled={props.disabled}
                  onChange={(event) =>
                    props.setGoogleConversionId(event.target.value)
                  }
                >
                  <option value="">Select conversion</option>
                  {googleConversions.map((conversion) => (
                    <option key={conversion.id} value={conversion.id}>
                      {conversion.name}
                      {conversion.customEventType
                        ? ' · ' + pretty(conversion.customEventType)
                        : ''}
                      {isRecommendedConversion(
                        conversion,
                        props.businessGoal,
                        'GOOGLE_ADS',
                      )
                        ? ' · Recommended'
                        : ''}
                    </option>
                  ))}
                </select>
              </Field>
            )}
            <label className="flex items-start gap-3 rounded-xl border border-slate-200 p-4 sm:col-span-2">
              <input
                type="checkbox"
                className="mt-1"
                checked={props.shoppingEnableLocal}
                disabled={props.disabled}
                onChange={(event) =>
                  props.setShoppingEnableLocal(event.target.checked)
                }
              />
              <span>
                <span className="block text-xs font-semibold text-slate-800">
                  Enable local inventory
                </span>
                <span className="mt-1 block text-[11px] leading-5 text-slate-500">
                  Enable only when this Merchant Center account is configured for
                  eligible local inventory products.
                </span>
              </span>
            </label>
          </div>
          {props.shoppingMerchantAccounts.length === 0 && (
            <p className="mt-4 rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-xs leading-5 text-amber-800">
              No accessible Merchant Center account was returned for the selected
              Google Ads connection. Open Merchant Center in Ad Center or reconnect
              Google if Merchant API permission was added after the connection.
            </p>
          )}
        </section>
      )}

      {props.selectedChannels.includes('GOOGLE_DEMAND_GEN') && (
        <section className="rounded-2xl border border-slate-200 p-5">
          <h3 className="text-base font-bold text-slate-950">Google Demand Gen</h3>
          <p className="mt-1 text-xs leading-5 text-slate-500">
            Demand Gen uses a paused campaign and paused ad group. Shared locations and
            languages map to ad-group targeting; the creative step supplies the visual
            multi-asset ad.
          </p>
          <div className="mt-4 grid gap-4 sm:grid-cols-2">
            <Field label="EU political advertising">
              <select
                className="form-input mt-2"
                value={props.googlePolitical}
                disabled={props.disabled}
                onChange={(event) =>
                  props.setGooglePolitical(
                    event.target.value as 'true' | 'false' | '',
                  )
                }
              >
                <option value="">Choose</option>
                <option value="false">
                  Does not contain EU political advertising
                </option>
                <option value="true">
                  Contains EU political advertising
                </option>
              </select>
            </Field>
            <Field label="Bid strategy">
              <select
                className="form-input mt-2"
                value={props.demandGenBidStrategy}
                disabled={props.disabled}
                onChange={(event) =>
                  props.setDemandGenBidStrategy(
                    event.target.value as
                      | 'MAXIMIZE_CLICKS'
                      | 'MAXIMIZE_CONVERSIONS'
                      | 'MAXIMIZE_CONVERSION_VALUE',
                  )
                }
              >
                <option value="MAXIMIZE_CLICKS">Maximize clicks</option>
                <option value="MAXIMIZE_CONVERSIONS">Maximize conversions</option>
                <option value="MAXIMIZE_CONVERSION_VALUE">
                  Maximize conversion value
                </option>
              </select>
            </Field>
            <Field label="Channel coverage">
              <select
                className="form-input mt-2"
                value={props.demandGenChannelStrategy}
                disabled={props.disabled}
                onChange={(event) =>
                  props.setDemandGenChannelStrategy(
                    event.target.value as
                      | 'ALL_CHANNELS'
                      | 'ALL_OWNED_AND_OPERATED_CHANNELS',
                  )
                }
              >
                <option value="ALL_CHANNELS">All Demand Gen channels</option>
                <option value="ALL_OWNED_AND_OPERATED_CHANNELS">
                  Google-owned channels only
                </option>
              </select>
            </Field>
            {conversionRequired && (
              <Field label="Conversion action">
                <select
                  className="form-input mt-2"
                  value={props.googleConversionId}
                  disabled={props.disabled}
                  onChange={(event) =>
                    props.setGoogleConversionId(event.target.value)
                  }
                >
                  <option value="">Select conversion</option>
                  {googleConversions.map((conversion) => (
                    <option key={conversion.id} value={conversion.id}>
                      {conversion.name}
                      {conversion.customEventType
                        ? ' · ' + pretty(conversion.customEventType)
                        : ''}
                      {isRecommendedConversion(
                        conversion,
                        props.businessGoal,
                        'GOOGLE_ADS',
                      )
                        ? ' · Recommended'
                        : ''}
                    </option>
                  ))}
                </select>
              </Field>
            )}
          </div>
        </section>
      )}

      {props.selectedChannels.includes('GOOGLE_PERFORMANCE_MAX') && (
        <section className="rounded-2xl border border-slate-200 p-5">
          <h3 className="text-base font-bold text-slate-950">Google Performance Max</h3>
          <p className="mt-1 text-xs leading-5 text-slate-500">
            UnifyDesk creates the budget, paused Performance Max campaign, required assets,
            and first asset group atomically. Search intent from the shared audience becomes
            Performance Max search-theme signals.
          </p>
          <div className="mt-4 grid gap-4 sm:grid-cols-2">
            <Field label="EU political advertising">
              <select
                className="form-input mt-2"
                value={props.googlePolitical}
                disabled={props.disabled}
                onChange={(event) =>
                  props.setGooglePolitical(
                    event.target.value as 'true' | 'false' | '',
                  )
                }
              >
                <option value="">Choose</option>
                <option value="false">
                  Does not contain EU political advertising
                </option>
                <option value="true">
                  Contains EU political advertising
                </option>
              </select>
            </Field>
            <Field label="Bid strategy">
              <select
                className="form-input mt-2"
                value={props.pmaxBidStrategy}
                disabled={props.disabled}
                onChange={(event) =>
                  props.setPmaxBidStrategy(
                    event.target.value as
                      | 'MAXIMIZE_CONVERSIONS'
                      | 'MAXIMIZE_CONVERSION_VALUE',
                  )
                }
              >
                <option value="MAXIMIZE_CONVERSIONS">Maximize conversions</option>
                <option value="MAXIMIZE_CONVERSION_VALUE">
                  Maximize conversion value
                </option>
              </select>
            </Field>
            <div className="rounded-xl border border-indigo-100 bg-indigo-50/40 p-4 sm:col-span-2">
              <div className="flex flex-wrap items-start justify-between gap-3">
                <div>
                  <p className="text-xs font-semibold text-slate-700">Brand guidelines</p>
                  <p className="mt-1 text-[11px] leading-5 text-slate-500">
                    Recommended. Brand name and logos are linked at campaign level when enabled.
                  </p>
                </div>
                <label className="inline-flex items-center gap-2 text-xs font-semibold text-slate-700">
                  <input
                    type="checkbox"
                    checked={props.pmaxBrandGuidelinesEnabled}
                    disabled={props.disabled}
                    onChange={(event) =>
                      props.setPmaxBrandGuidelinesEnabled(event.target.checked)
                    }
                  />
                  Enabled
                </label>
              </div>
              {props.pmaxBrandGuidelinesEnabled && (
                <div className="mt-3 grid gap-3 sm:grid-cols-3">
                  <Field label="Main color">
                    <input
                      className="form-input mt-2"
                      value={props.pmaxBrandMainColor}
                      disabled={props.disabled}
                      placeholder="#3366FF"
                      onChange={(event) => props.setPmaxBrandMainColor(event.target.value)}
                    />
                  </Field>
                  <Field label="Accent color">
                    <input
                      className="form-input mt-2"
                      value={props.pmaxBrandAccentColor}
                      disabled={props.disabled}
                      placeholder="#FFCC00"
                      onChange={(event) => props.setPmaxBrandAccentColor(event.target.value)}
                    />
                  </Field>
                  <Field label="Brand font">
                    <select
                      className="form-input mt-2"
                      value={props.pmaxBrandFontFamily}
                      disabled={props.disabled}
                      onChange={(event) => props.setPmaxBrandFontFamily(event.target.value)}
                    >
                      <option value="">Google default</option>
                      <option value="Open Sans">Open Sans</option>
                      <option value="Roboto">Roboto</option>
                      <option value="Montserrat">Montserrat</option>
                      <option value="Poppins">Poppins</option>
                      <option value="Lato">Lato</option>
                      <option value="Oswald">Oswald</option>
                      <option value="Playfair Display">Playfair Display</option>
                      <option value="Roboto Slab">Roboto Slab</option>
                    </select>
                  </Field>
                </div>
              )}
            </div>

            <GooglePerformanceMaxLifecycleLaunchSettings
              companyId={props.companyId}
              accountId={googleAccount}
              goalType={props.pmaxLifecycleGoalType}
              setGoalType={props.setPmaxLifecycleGoalType}
              targetOption={props.pmaxLifecycleTargetOption}
              setTargetOption={props.setPmaxLifecycleTargetOption}
              additionalValue={props.pmaxLifecycleAdditionalValue}
              setAdditionalValue={props.setPmaxLifecycleAdditionalValue}
              highLifetimeValue={props.pmaxLifecycleHighLifetimeValue}
              setHighLifetimeValue={props.setPmaxLifecycleHighLifetimeValue}
              valueMultiplier={props.pmaxLifecycleValueMultiplier}
              setValueMultiplier={props.setPmaxLifecycleValueMultiplier}
              enableBidAdjustmentsForLoyaltyMembers={
                props.pmaxLifecycleEnableBidAdjustmentsForLoyaltyMembers
              }
              setEnableBidAdjustmentsForLoyaltyMembers={
                props.setPmaxLifecycleEnableBidAdjustmentsForLoyaltyMembers
              }
              showTargetedLoyaltyMemberBenefitsInPla={
                props.pmaxLifecycleShowTargetedLoyaltyMemberBenefitsInPla
              }
              setShowTargetedLoyaltyMemberBenefitsInPla={
                props.setPmaxLifecycleShowTargetedLoyaltyMemberBenefitsInPla
              }
              disabled={props.disabled}
            />

            {conversionRequired && (
              <Field label="Conversion action">
                <select
                  className="form-input mt-2"
                  value={props.googleConversionId}
                  disabled={props.disabled}
                  onChange={(event) =>
                    props.setGoogleConversionId(event.target.value)
                  }
                >
                  <option value="">Select conversion</option>
                  {googleConversions.map((conversion) => (
                    <option key={conversion.id} value={conversion.id}>
                      {conversion.name}
                      {conversion.customEventType
                        ? ' · ' + pretty(conversion.customEventType)
                        : ''}
                      {isRecommendedConversion(
                        conversion,
                        props.businessGoal,
                        'GOOGLE_ADS',
                      )
                        ? ' · Recommended'
                        : ''}
                    </option>
                  ))}
                </select>
              </Field>
            )}
            <Field
              label="YouTube video IDs"
              hint="Optional. One video ID per line or comma-separated, up to 15."
            >
              <textarea
                className="form-input mt-2 min-h-24"
                value={props.pmaxYoutubeVideoIds}
                disabled={props.disabled}
                onChange={(event) =>
                  props.setPmaxYoutubeVideoIds(event.target.value)
                }
                placeholder={'dQw4w9WgXcQ\nvideoIdTwo'}
              />
            </Field>
          </div>
        </section>
      )}

      {props.selectedChannels.includes('GOOGLE_SEARCH') && (
        <section className="rounded-2xl border border-slate-200 p-5">
          <h3 className="text-base font-bold text-slate-950">Google Search</h3>
          <p className="mt-1 text-xs leading-5 text-slate-500">
            Keep provider-only declarations here; the core campaign remains provider-neutral.
          </p>
          <div className="mt-4 grid gap-4 sm:grid-cols-2">
            <Field label="EU political advertising">
              <select
                className="form-input mt-2"
                value={props.googlePolitical}
                disabled={props.disabled}
                onChange={(event) =>
                  props.setGooglePolitical(event.target.value as 'true' | 'false' | '')
                }
              >
                <option value="">Choose</option>
                <option value="false">Does not contain EU political advertising</option>
                <option value="true">Contains EU political advertising</option>
              </select>
            </Field>
            <Field label="Bid strategy">
              <select
                className="form-input mt-2"
                value={props.googleBidStrategy}
                disabled={props.disabled}
                onChange={(event) => props.setGoogleBidStrategy(event.target.value)}
              >
                <option value="MANUAL_CPC">Manual CPC</option>
                <option value="MAXIMIZE_CLICKS">Maximize clicks</option>
                <option value="MAXIMIZE_CONVERSIONS">Maximize conversions</option>
                <option value="MAXIMIZE_CONVERSION_VALUE">Maximize conversion value</option>
              </select>
            </Field>
            <Field label="Keyword match type">
              <select
                className="form-input mt-2"
                value={props.keywordMatchType}
                disabled={
                  props.disabled ||
                  (props.googleAiMaxEnabled &&
                    !props.googleAiMaxDisableSearchTermMatching)
                }
                onChange={(event) => props.setKeywordMatchType(event.target.value)}
              >
                <option value="BROAD">Broad</option>
                <option value="PHRASE">Phrase</option>
                <option value="EXACT">Exact</option>
              </select>
            </Field>
            {conversionRequired && (
              <Field label="Conversion action">
                <select
                  className="form-input mt-2"
                  value={props.googleConversionId}
                  disabled={props.disabled}
                  onChange={(event) => props.setGoogleConversionId(event.target.value)}
                >
                  <option value="">Select conversion</option>
                  {googleConversions.map((conversion) => (
                    <option key={conversion.id} value={conversion.id}>
                      {conversion.name}
                      {conversion.customEventType ? ` · ${pretty(conversion.customEventType)}` : ''}
                      {isRecommendedConversion(conversion, props.businessGoal, 'GOOGLE_ADS')
                        ? ' · Recommended'
                        : ''}
                    </option>
                  ))}
                </select>
              </Field>
            )}
            <Field
              label="Audience mode"
              hint="Observation keeps normal Search reach while measuring selected audiences. Targeting restricts delivery to included audience lists."
            >
              <select
                className="form-input mt-2"
                value={props.googleSearchAudienceMode}
                disabled={props.disabled}
                onChange={(event) =>
                  props.setGoogleSearchAudienceMode(
                    event.target.value as 'OBSERVATION' | 'TARGETING',
                  )
                }
              >
                <option value="OBSERVATION">Observation (recommended)</option>
                <option value="TARGETING">Targeting (restrict reach)</option>
              </select>
            </Field>
            <Field label="Location matching">
              <select
                className="form-input mt-2"
                value={props.googleGeoMode}
                disabled={props.disabled}
                onChange={(event) =>
                  props.setGoogleGeoMode(
                    event.target.value as 'PRESENCE' | 'PRESENCE_OR_INTEREST',
                  )
                }
              >
                <option value="PRESENCE_OR_INTEREST">Presence or interest</option>
                <option value="PRESENCE">Presence only</option>
              </select>
            </Field>
            <div className="sm:col-span-2">
              <GoogleSearchDemographicSelector
                companyId={props.companyId}
                accountId={googleAccount}
                parentalStatusIncludes={props.googleParentalStatusIncludes}
                parentalStatusExcludes={props.googleParentalStatusExcludes}
                incomeRangeIncludes={props.googleIncomeRangeIncludes}
                incomeRangeExcludes={props.googleIncomeRangeExcludes}
                detailedDemographicIds={props.googleDetailedDemographicIds}
                excludedDetailedDemographicIds={
                  props.googleExcludedDetailedDemographicIds
                }
                setParentalStatusIncludes={props.setGoogleParentalStatusIncludes}
                setParentalStatusExcludes={props.setGoogleParentalStatusExcludes}
                setIncomeRangeIncludes={props.setGoogleIncomeRangeIncludes}
                setIncomeRangeExcludes={props.setGoogleIncomeRangeExcludes}
                setDetailedDemographicIds={props.setGoogleDetailedDemographicIds}
                setExcludedDetailedDemographicIds={
                  props.setGoogleExcludedDetailedDemographicIds
                }
                disabled={props.disabled}
              />
            </div>

            <div className="sm:col-span-2">
              <GoogleSearchInterestSelector
                companyId={props.companyId}
                accountId={googleAccount}
                includedResourceNames={props.googleSearchInterestResources}
                excludedResourceNames={props.googleSearchExcludedInterestResources}
                setIncludedResourceNames={props.setGoogleSearchInterestResources}
                setExcludedResourceNames={props.setGoogleSearchExcludedInterestResources}
                disabled={props.disabled}
              />
            </div>

            <div className="rounded-xl border border-slate-100 p-4 sm:col-span-2">
              <p className="text-xs font-semibold text-slate-700">Device bid adjustments</p>
              <p className="mt-1 text-[11px] leading-5 text-slate-500">
                Leave 1.0 for normal bidding, use 0 to exclude a device, or set 0.1–10.0 as a bid multiplier.
              </p>
              <div className="mt-3 grid gap-3 sm:grid-cols-3">
                {(['DESKTOP', 'MOBILE', 'TABLET'] as const).map((device) => (
                  <Field key={device} label={pretty(device)}>
                    <input
                      className="form-input mt-2"
                      type="number"
                      min="0"
                      max="10"
                      step="0.1"
                      value={props.googleDeviceBidModifiers[device]}
                      disabled={props.disabled}
                      onChange={(event) =>
                        props.setGoogleDeviceBidModifiers({
                          ...props.googleDeviceBidModifiers,
                          [device]: event.target.value,
                        })
                      }
                    />
                  </Field>
                ))}
              </div>
            </div>

            <div className="rounded-xl border border-indigo-100 bg-indigo-50/40 p-4 sm:col-span-2">
              <div className="flex flex-wrap items-start justify-between gap-3">
                <div>
                  <p className="text-sm font-bold text-slate-900">AI Max for Search</p>
                  <p className="mt-1 max-w-3xl text-xs leading-5 text-slate-500">
                    AI Max expands Search matching and can customize text and landing pages.
                    Google treats keywords as broad match while AI Max search-term matching is
                    active, so individual keyword match types apply only when matching is disabled.
                  </p>
                </div>
                <label className="inline-flex items-center gap-2 text-xs font-semibold text-slate-700">
                  <input
                    type="checkbox"
                    checked={props.googleAiMaxEnabled}
                    disabled={props.disabled}
                    onChange={(event) => {
                      props.setGoogleAiMaxEnabled(event.target.checked)
                      if (!event.target.checked) {
                        props.setGoogleAiMaxTextCustomization(false)
                        props.setGoogleAiMaxFinalUrlExpansion(false)
                        props.setGoogleAiMaxDisableSearchTermMatching(false)
                      }
                    }}
                  />
                  Enable AI Max
                </label>
              </div>

              {props.googleAiMaxEnabled && (
                <>
                <div className="mt-4 grid gap-3 sm:grid-cols-3">
                  <label className="rounded-xl border border-slate-200 bg-white p-3 text-xs text-slate-600">
                    <span className="flex items-center gap-2 font-semibold text-slate-800">
                      <input
                        type="checkbox"
                        checked={props.googleAiMaxTextCustomization}
                        disabled={props.disabled}
                        onChange={(event) =>
                          props.setGoogleAiMaxTextCustomization(event.target.checked)
                        }
                      />
                      Text customization
                    </span>
                    <span className="mt-1 block leading-5">
                      Allow Google to tailor headlines and descriptions from campaign and landing-page context.
                    </span>
                  </label>
                  <label className="rounded-xl border border-slate-200 bg-white p-3 text-xs text-slate-600">
                    <span className="flex items-center gap-2 font-semibold text-slate-800">
                      <input
                        type="checkbox"
                        checked={props.googleAiMaxFinalUrlExpansion}
                        disabled={props.disabled}
                        onChange={(event) =>
                          props.setGoogleAiMaxFinalUrlExpansion(event.target.checked)
                        }
                      />
                      Final URL expansion
                    </span>
                    <span className="mt-1 block leading-5">
                      Let Google choose a more relevant landing page when eligible. This can also generate matching text assets.
                    </span>
                  </label>
                  <label className="rounded-xl border border-slate-200 bg-white p-3 text-xs text-slate-600">
                    <span className="flex items-center gap-2 font-semibold text-slate-800">
                      <input
                        type="checkbox"
                        checked={props.googleAiMaxDisableSearchTermMatching}
                        disabled={props.disabled}
                        onChange={(event) =>
                          props.setGoogleAiMaxDisableSearchTermMatching(event.target.checked)
                        }
                      />
                      Preserve keyword match types
                    </span>
                    <span className="mt-1 block leading-5">
                      Disable AI Max search-term matching for the created ad group so Exact/Phrase/Broad keyword settings apply normally.
                    </span>
                  </label>
                </div>
                <div className="mt-4 grid gap-3 sm:grid-cols-2">
                  <Field
                    label="Generated-text exclusions"
                    hint="One exact word or phrase per line. Up to 25 values, maximum 30 characters each."
                  >
                    <textarea
                      className="form-input mt-2 min-h-28"
                      value={props.googleAiMaxTermExclusions}
                      disabled={props.disabled}
                      onChange={(event) =>
                        props.setGoogleAiMaxTermExclusions(event.target.value)
                      }
                      placeholder={'free\ncheap\ncompetitor phrase'}
                    />
                  </Field>
                  <Field
                    label="AI messaging restrictions"
                    hint="One instruction per line. Up to 40 instructions, maximum 300 characters each."
                  >
                    <textarea
                      className="form-input mt-2 min-h-28"
                      value={props.googleAiMaxMessagingRestrictions}
                      disabled={props.disabled}
                      onChange={(event) =>
                        props.setGoogleAiMaxMessagingRestrictions(event.target.value)
                      }
                      placeholder="Do not imply a guaranteed outcome"
                    />
                  </Field>
                  <Field
                    label="Landing-page URL exclusions"
                    hint="One absolute URL or path per line. Excluded pages will not be selected by AI Max final URL expansion."
                  >
                    <textarea
                      className="form-input mt-2 min-h-28"
                      value={props.googleAiMaxUrlExclusions}
                      disabled={props.disabled}
                      onChange={(event) =>
                        props.setGoogleAiMaxUrlExclusions(event.target.value)
                      }
                      placeholder={'/careers/\n/privacy-policy\nhttps://example.com/old-page'}
                    />
                  </Field>
                  <Field
                    label="Landing-page URL inclusions"
                    hint="One absolute URL per line. UnifyDesk creates/reuses a Google page feed and limits this ad group to those page-feed URLs. Requires text customization, Final URL expansion, and AI Max search-term matching."
                  >
                    <textarea
                      className="form-input mt-2 min-h-28"
                      value={props.googleAiMaxUrlInclusions}
                      disabled={props.disabled}
                      onChange={(event) =>
                        props.setGoogleAiMaxUrlInclusions(event.target.value)
                      }
                      placeholder={'https://example.com/product\nhttps://example.com/pricing'}
                    />
                  </Field>
                </div>
                {googleAccount && (
                  <GoogleAiMaxBrandSelector
                    companyId={props.companyId}
                    accountId={googleAccount}
                    inclusions={props.googleAiMaxBrandInclusions}
                    exclusions={props.googleAiMaxBrandExclusions}
                    setInclusions={props.setGoogleAiMaxBrandInclusions}
                    setExclusions={props.setGoogleAiMaxBrandExclusions}
                    disabled={props.disabled}
                  />
                )}
                </>
              )}
            </div>

            <div className="rounded-xl border border-slate-100 p-3">
              <p className="text-xs font-semibold text-slate-700">Search networks</p>
              <div className="mt-3 grid gap-2">
                <label className="inline-flex items-center gap-2 text-sm text-slate-600">
                  <input
                    type="checkbox"
                    checked
                    disabled
                  />
                  Google Search
                </label>
                <label className="inline-flex items-center gap-2 text-sm text-slate-600">
                  <input
                    type="checkbox"
                    checked={props.googleSearchPartners}
                    disabled={props.disabled}
                    onChange={(event) => props.setGoogleSearchPartners(event.target.checked)}
                  />
                  Google Search Partners
                </label>
                <label className="inline-flex items-center gap-2 text-sm text-slate-600">
                  <input
                    type="checkbox"
                    checked={props.googleContentNetwork}
                    disabled={props.disabled}
                    onChange={(event) => props.setGoogleContentNetwork(event.target.checked)}
                  />
                  Google Display Network expansion
                </label>
              </div>
            </div>
          </div>

          <div className="mt-5 border-t border-slate-100 pt-5">
            <div className="flex flex-wrap items-start justify-between gap-3">
              <div>
                <div className="flex items-center gap-2">
                  <FiClock className="text-slate-400" aria-hidden="true" />
                  <h4 className="text-sm font-bold text-slate-900">Ad schedule</h4>
                </div>
                <p className="mt-1 text-xs leading-5 text-slate-500">
                  Optional dayparting in the Google Ads account timezone. Times use 15-minute boundaries.
                </p>
              </div>
              <button
                type="button"
                className="secondary-button inline-flex items-center gap-2"
                disabled={props.disabled}
                onClick={() =>
                  props.setGoogleAdSchedules([
                    ...props.googleAdSchedules,
                    { dayOfWeek: 'MONDAY', startTime: '09:00', endTime: '18:00' },
                  ])
                }
              >
                <FiPlus aria-hidden="true" />
                Add window
              </button>
            </div>

            {props.googleAdSchedules.length === 0 ? (
              <p className="mt-4 rounded-xl bg-slate-50 px-4 py-3 text-xs text-slate-500">
                No dayparting. Google can serve throughout the day.
              </p>
            ) : (
              <div className="mt-4 grid gap-2">
                {props.googleAdSchedules.map((schedule, index) => (
                  <div
                    key={schedule.dayOfWeek + '-' + schedule.startTime + '-' + index}
                    className="grid gap-2 rounded-xl border border-slate-100 p-3 sm:grid-cols-[1fr_1fr_1fr_auto]"
                  >
                    <select
                      className="form-input"
                      value={schedule.dayOfWeek}
                      disabled={props.disabled}
                      onChange={(event) =>
                        props.setGoogleAdSchedules(
                          props.googleAdSchedules.map((item, itemIndex) =>
                            itemIndex === index
                              ? { ...item, dayOfWeek: event.target.value }
                              : item,
                          ),
                        )
                      }
                    >
                      {DAYS.map((day) => (
                        <option key={day} value={day}>
                          {pretty(day)}
                        </option>
                      ))}
                    </select>
                    <select
                      className="form-input"
                      value={schedule.startTime}
                      disabled={props.disabled}
                      onChange={(event) =>
                        props.setGoogleAdSchedules(
                          props.googleAdSchedules.map((item, itemIndex) =>
                            itemIndex === index
                              ? { ...item, startTime: event.target.value }
                              : item,
                          ),
                        )
                      }
                    >
                      {START_TIMES.map((time) => (
                        <option key={time} value={time}>{time}</option>
                      ))}
                    </select>
                    <select
                      className="form-input"
                      value={schedule.endTime}
                      disabled={props.disabled}
                      onChange={(event) =>
                        props.setGoogleAdSchedules(
                          props.googleAdSchedules.map((item, itemIndex) =>
                            itemIndex === index
                              ? { ...item, endTime: event.target.value }
                              : item,
                          ),
                        )
                      }
                    >
                      {END_TIMES.map((time) => (
                        <option key={time} value={time}>{time}</option>
                      ))}
                    </select>
                    <button
                      type="button"
                      className="secondary-button inline-flex items-center justify-center"
                      disabled={props.disabled}
                      aria-label="Remove schedule window"
                      onClick={() =>
                        props.setGoogleAdSchedules(
                          props.googleAdSchedules.filter((_, itemIndex) => itemIndex !== index),
                        )
                      }
                    >
                      <FiTrash2 aria-hidden="true" />
                    </button>
                  </div>
                ))}
              </div>
            )}
          </div>

          <div className="mt-5 border-t border-slate-100 pt-5">
            <div className="flex flex-wrap items-start justify-between gap-3">
              <div>
                <h4 className="text-sm font-bold text-slate-900">Negative keywords</h4>
                <p className="mt-1 text-xs leading-5 text-slate-500">
                  Campaign negatives apply to every ad group in this Search campaign. Reusable lists stay managed in Google Ads and can be attached here.
                </p>
              </div>
              <button
                type="button"
                className="secondary-button inline-flex items-center gap-2"
                disabled={props.disabled}
                onClick={() =>
                  props.setGoogleNegativeKeywords([
                    ...props.googleNegativeKeywords,
                    { text: '', matchType: 'PHRASE' },
                  ])
                }
              >
                <FiPlus aria-hidden="true" />
                Add negative
              </button>
            </div>

            {props.googleNegativeKeywords.length === 0 ? (
              <p className="mt-4 rounded-xl bg-slate-50 px-4 py-3 text-xs text-slate-500">
                No campaign-level negative keywords configured.
              </p>
            ) : (
              <div className="mt-4 grid gap-2">
                {props.googleNegativeKeywords.map((keyword, index) => (
                  <div
                    key={index}
                    className="grid gap-2 rounded-xl border border-slate-100 p-3 sm:grid-cols-[1fr_150px_auto]"
                  >
                    <input
                      className="form-input"
                      value={keyword.text}
                      maxLength={80}
                      disabled={props.disabled}
                      placeholder="free, jobs, support…"
                      onChange={(event) =>
                        props.setGoogleNegativeKeywords(
                          props.googleNegativeKeywords.map((item, itemIndex) =>
                            itemIndex === index
                              ? { ...item, text: event.target.value }
                              : item,
                          ),
                        )
                      }
                    />
                    <select
                      className="form-input"
                      value={keyword.matchType}
                      disabled={props.disabled}
                      onChange={(event) =>
                        props.setGoogleNegativeKeywords(
                          props.googleNegativeKeywords.map((item, itemIndex) =>
                            itemIndex === index
                              ? {
                                  ...item,
                                  matchType: event.target.value as
                                    | 'BROAD'
                                    | 'PHRASE'
                                    | 'EXACT',
                                }
                              : item,
                          ),
                        )
                      }
                    >
                      <option value="BROAD">Broad</option>
                      <option value="PHRASE">Phrase</option>
                      <option value="EXACT">Exact</option>
                    </select>
                    <button
                      type="button"
                      className="secondary-button inline-flex items-center justify-center"
                      disabled={props.disabled}
                      aria-label="Remove negative keyword"
                      onClick={() =>
                        props.setGoogleNegativeKeywords(
                          props.googleNegativeKeywords.filter(
                            (_, itemIndex) => itemIndex !== index,
                          ),
                        )
                      }
                    >
                      <FiTrash2 aria-hidden="true" />
                    </button>
                  </div>
                ))}
              </div>
            )}

            <div className="mt-5">
              <p className="text-xs font-semibold text-slate-700">
                Reusable negative keyword lists
              </p>
              {props.googleNegativeKeywordLists.length === 0 ? (
                <p className="mt-2 rounded-xl bg-slate-50 px-4 py-3 text-xs text-slate-500">
                  No reusable negative keyword lists were found on this Google Ads account.
                </p>
              ) : (
                <div className="mt-3 grid gap-2 sm:grid-cols-2">
                  {props.googleNegativeKeywordLists.map((list) => {
                    const checked =
                      props.selectedGoogleNegativeKeywordListIds.includes(list.id)
                    return (
                      <label
                        key={list.id}
                        className={
                          'flex cursor-pointer items-start gap-3 rounded-xl border p-3 ' +
                          (checked
                            ? 'border-indigo-200 bg-indigo-50/50'
                            : 'border-slate-200')
                        }
                      >
                        <input
                          className="mt-1"
                          type="checkbox"
                          checked={checked}
                          disabled={props.disabled}
                          onChange={(event) =>
                            props.setSelectedGoogleNegativeKeywordListIds(
                              event.target.checked
                                ? [
                                    ...new Set([
                                      ...props.selectedGoogleNegativeKeywordListIds,
                                      list.id,
                                    ]),
                                  ]
                                : props.selectedGoogleNegativeKeywordListIds.filter(
                                    (id) => id !== list.id,
                                  ),
                            )
                          }
                        />
                        <span>
                          <span className="block text-sm font-semibold text-slate-800">
                            {list.name}
                          </span>
                          <span className="mt-1 block text-xs text-slate-500">
                            {list.memberCount} keyword{list.memberCount === 1 ? '' : 's'} · used by {list.referenceCount} campaign{list.referenceCount === 1 ? '' : 's'}
                          </span>
                        </span>
                      </label>
                    )
                  })}
                </div>
              )}
            </div>
          </div>
        </section>
      )}

      {props.selectedChannels.includes('META_ADS') && (
        <section className="rounded-2xl border border-slate-200 p-5">
          <h3 className="text-base font-bold text-slate-950">Meta Ads</h3>
          <p className="mt-1 text-xs leading-5 text-slate-500">
            Identity, optimization and conversion settings are required by Meta at creation time.
          </p>
          <div className="mt-4 grid gap-4 sm:grid-cols-2">
            <Field label="Facebook Page">
              <select
                className="form-input mt-2"
                value={props.metaPageId}
                disabled={props.disabled}
                onChange={(event) => props.setMetaPageId(event.target.value)}
              >
                <option value="">Select Page</option>
                {metaResources
                  .filter((resource) => resource.resourceType === 'FACEBOOK_PAGE')
                  .map((resource) => (
                    <option key={resource.id} value={resource.externalResourceId}>
                      {resource.name ?? resource.externalResourceId}
                    </option>
                  ))}
              </select>
            </Field>
            <Field label="Instagram account">
              <select
                className="form-input mt-2"
                value={props.metaInstagramId}
                disabled={props.disabled}
                onChange={(event) => props.setMetaInstagramId(event.target.value)}
              >
                <option value="">None</option>
                {metaResources
                  .filter((resource) => resource.resourceType === 'INSTAGRAM_ACCOUNT')
                  .map((resource) => (
                    <option key={resource.id} value={resource.externalResourceId}>
                      {resource.name ?? resource.externalResourceId}
                    </option>
                  ))}
              </select>
            </Field>
            <Field label="Optimization goal">
              <select
                className="form-input mt-2"
                value={props.metaOptimizationGoal}
                disabled={props.disabled}
                onChange={(event) => props.setMetaOptimizationGoal(event.target.value)}
              >
                <option value="OFFSITE_CONVERSIONS">Offsite conversions</option>
                <option value="LINK_CLICKS">Link clicks</option>
                <option value="LANDING_PAGE_VIEWS">Landing page views</option>
                <option value="IMPRESSIONS">Impressions</option>
                <option value="REACH">Reach</option>
                <option value="LEAD_GENERATION">Lead generation</option>
                <option value="VALUE">Conversion value</option>
                <option value="THRUPLAY">ThruPlay</option>
              </select>
            </Field>
            <Field label="Billing event">
              <select
                className="form-input mt-2"
                value={props.metaBillingEvent}
                disabled={props.disabled}
                onChange={(event) => props.setMetaBillingEvent(event.target.value)}
              >
                <option value="IMPRESSIONS">Impressions</option>
                <option value="LINK_CLICKS">Link clicks</option>
                <option value="THRUPLAY">ThruPlay</option>
              </select>
            </Field>
            <Field label="Special ad category">
              <select
                className="form-input mt-2"
                value={props.metaSpecialCategory}
                disabled={props.disabled}
                onChange={(event) => props.setMetaSpecialCategory(event.target.value)}
              >
                <option value="NONE">None</option>
                <option value="CREDIT">Credit</option>
                <option value="EMPLOYMENT">Employment</option>
                <option value="HOUSING">Housing</option>
                <option value="ISSUES_ELECTIONS_POLITICS">Issues, elections or politics</option>
              </select>
            </Field>
            <div className="sm:col-span-2 rounded-xl border border-indigo-200 bg-indigo-50 p-4">
              <label className="flex items-start gap-3">
                <input
                  type="checkbox"
                  className="mt-1"
                  checked={props.metaAdvantageAudience}
                  disabled={props.disabled}
                  onChange={(event) =>
                    props.setMetaAdvantageAudience(event.target.checked)
                  }
                />
                <span>
                  <span className="block text-sm font-bold text-indigo-950">
                    Advantage+ audience
                  </span>
                  <span className="mt-1 block text-xs leading-5 text-indigo-700">
                    Meta may expand beyond audience suggestions. Location and explicit exclusions
                    remain controls.
                  </span>
                </span>
              </label>
              {props.metaAdvantageAudience && (
                <div className="mt-4 grid gap-3 sm:grid-cols-2">
                  <label className="rounded-lg bg-white/70 p-3 text-xs text-indigo-900">
                    <span className="flex items-start gap-2">
                      <input
                        type="checkbox"
                        className="mt-0.5"
                        checked={props.metaAdvantageAgeSuggestion}
                        disabled={props.disabled}
                        onChange={(event) =>
                          props.setMetaAdvantageAgeSuggestion(event.target.checked)
                        }
                      />
                      Treat age as a suggestion.
                    </span>
                  </label>
                  <label className="rounded-lg bg-white/70 p-3 text-xs text-indigo-900">
                    <span className="flex items-start gap-2">
                      <input
                        type="checkbox"
                        className="mt-0.5"
                        checked={props.metaAdvantageGenderSuggestion}
                        disabled={props.disabled}
                        onChange={(event) =>
                          props.setMetaAdvantageGenderSuggestion(event.target.checked)
                        }
                      />
                      Treat gender as a suggestion.
                    </span>
                  </label>
                </div>
              )}
            </div>
            <div className="sm:col-span-2 rounded-xl border border-indigo-200 bg-indigo-50 p-4">
              <label className="flex items-start gap-3">
                <input
                  type="checkbox"
                  className="mt-1"
                  checked={props.metaAdvantagePlusCreative}
                  disabled={props.disabled}
                  onChange={(event) => props.setMetaAdvantagePlusCreative(event.target.checked)}
                />
                <span>
                  <span className="block text-sm font-bold text-indigo-950">
                    Advantage+ creative
                  </span>
                  <span className="mt-1 block text-xs leading-5 text-indigo-700">
                    Opt in to Meta standard creative enhancements. Text generation, image
                    expansion and background generation remain separate features.
                  </span>
                </span>
              </label>
            </div>
            <div className="sm:col-span-2 rounded-xl border border-violet-200 bg-violet-50 p-4">
              <label className="flex items-start gap-3">
                <input
                  type="checkbox"
                  className="mt-1"
                  checked={props.metaGenerativeText}
                  disabled={props.disabled}
                  onChange={(event) => props.setMetaGenerativeText(event.target.checked)}
                />
                <span>
                  <span className="block text-sm font-bold text-violet-950">
                    AI text generation
                  </span>
                  <span className="mt-1 block text-xs leading-5 text-violet-700">
                    Allow Meta to generate eligible text variations from your source copy.
                    Generated copy must be reviewed from provider preview/read-back before activation.
                  </span>
                </span>
              </label>
            </div>
            <div className="sm:col-span-2 rounded-xl border border-cyan-200 bg-cyan-50 p-4">
              <label className="flex items-start gap-3">
                <input
                  type="checkbox"
                  className="mt-1"
                  checked={props.metaImageExpansion}
                  disabled={props.disabled}
                  onChange={(event) => props.setMetaImageExpansion(event.target.checked)}
                />
                <span>
                  <span className="block text-sm font-bold text-cyan-950">
                    Image expansion
                  </span>
                  <span className="mt-1 block text-xs leading-5 text-cyan-700">
                    Allow Meta to extend image boundaries for eligible placements. The selected
                    creative pack must include an image and the provider preview should be reviewed
                    before activation.
                  </span>
                </span>
              </label>
            </div>
            <div className="sm:col-span-2 rounded-xl border border-orange-200 bg-orange-50 p-4">
              <label className="flex items-start gap-3">
                <input
                  type="checkbox"
                  className="mt-1"
                  checked={props.metaCatalogEnabled}
                  disabled={props.disabled || Boolean(props.metaExistingPostId)}
                  onChange={(event) => {
                    props.setMetaCatalogEnabled(event.target.checked)
                    if (!event.target.checked) props.setMetaBackgroundGeneration(false)
                  }}
                />
                <span className="flex-1">
                  <span className="block text-sm font-bold text-orange-950">
                    Catalog / dynamic product ad
                  </span>
                  <span className="mt-1 block text-xs leading-5 text-orange-700">
                    Use a synchronized Meta product set and Meta's catalog-ad creative path.
                  </span>
                  {props.metaCatalogEnabled && (
                    <select
                      className="form-input mt-3"
                      value={props.metaProductSetId}
                      disabled={props.disabled}
                      onChange={(event) => props.setMetaProductSetId(event.target.value)}
                    >
                      <option value="">Select synced Meta product set</option>
                      {metaResources
                        .filter((resource) => resource.resourceType === 'META_PRODUCT_SET')
                        .map((resource) => (
                          <option key={resource.id} value={resource.externalResourceId}>
                            {resource.name ?? resource.externalResourceId}
                          </option>
                        ))}
                    </select>
                  )}
                  {props.metaCatalogEnabled &&
                    !metaResources.some(
                      (resource) => resource.resourceType === 'META_PRODUCT_SET',
                    ) && (
                      <span className="mt-2 block text-[11px] leading-5 text-orange-700">
                        No product sets are synchronized. Refresh provider resources after connecting
                        a Meta business catalog.
                      </span>
                    )}
                </span>
              </label>
            </div>
            <div className="sm:col-span-2 rounded-xl border border-amber-200 bg-amber-50 p-4">
              <label className="flex items-start gap-3">
                <input
                  type="checkbox"
                  className="mt-1"
                  checked={props.metaBackgroundGeneration}
                  disabled={props.disabled || !props.metaCatalogEnabled}
                  onChange={(event) => props.setMetaBackgroundGeneration(event.target.checked)}
                />
                <span>
                  <span className="block text-sm font-bold text-amber-950">
                    AI background generation
                  </span>
                  <span className="mt-1 block text-xs leading-5 text-amber-700">
                    Optional catalog enhancement. Meta generates eligible product-image backgrounds;
                    review provider previews before activation.
                  </span>
                </span>
              </label>
            </div>
            <div className="sm:col-span-2 rounded-xl border border-emerald-200 bg-emerald-50 p-4">
              <p className="text-xs font-semibold text-emerald-950">Existing post creative</p>
              <p className="mt-1 text-xs leading-5 text-emerald-700">
                Reuse an eligible Facebook or Instagram post by object story ID. This is mutually
                exclusive with catalog ads.
              </p>
              <input
                className="form-input mt-3"
                value={props.metaExistingPostId}
                disabled={props.disabled || props.metaCatalogEnabled}
                onChange={(event) => props.setMetaExistingPostId(event.target.value)}
                placeholder="Optional object story ID, e.g. PAGEID_POSTID"
              />
            </div>
            <div className="sm:col-span-2 rounded-xl border border-slate-200 bg-slate-50 p-4">
              <p className="text-sm font-bold text-slate-900">WhatsApp Status ads</p>
              <p className="mt-1 text-xs leading-5 text-slate-600">
                Provider-hosted for now. Meta account eligibility may expose WhatsApp Status in Ads
                Manager, but UnifyDesk does not send an unverified Marketing API placement field.
                Configure this placement in Meta until Meta publishes a stable public API contract.
              </p>
              <a
                className="mt-2 inline-flex text-xs font-semibold text-indigo-600 hover:text-indigo-700"
                href="https://www.facebook.com/adsmanager/manage/campaigns"
                target="_blank"
                rel="noreferrer"
              >
                Open Meta Ads Manager
              </a>
            </div>
            <div className="sm:col-span-2 rounded-xl border border-slate-100 p-4">
              <p className="text-xs font-semibold text-slate-700">Placements</p>
              <select
                className="form-input mt-2"
                value={props.metaPlacementMode}
                disabled={props.disabled}
                onChange={(event) =>
                  props.setMetaPlacementMode(event.target.value as 'AUTO' | 'MANUAL')
                }
              >
                <option value="AUTO">Advantage+ placements (recommended)</option>
                <option value="MANUAL">Manual placements</option>
              </select>
              <p className="mt-2 text-[11px] leading-5 text-slate-500">
                Advantage+ placements leaves publisher-platform and position restrictions unset so
                Meta can distribute delivery across eligible placements. Manual mode limits delivery
                to the placement tree you choose below.
              </p>
              {props.metaPlacementMode === 'MANUAL' && (
                <div className="mt-3 space-y-3">
                  {META_PLACEMENT_GROUPS.map((group) => {
                    const selected = props.metaPublisherPlatforms.includes(group.key)
                    const positions =
                      group.key === 'facebook'
                        ? props.metaFacebookPositions
                        : group.key === 'instagram'
                          ? props.metaInstagramPositions
                          : group.key === 'messenger'
                            ? props.metaMessengerPositions
                            : props.metaAudienceNetworkPositions
                    const setPositions =
                      group.key === 'facebook'
                        ? props.setMetaFacebookPositions
                        : group.key === 'instagram'
                          ? props.setMetaInstagramPositions
                          : group.key === 'messenger'
                            ? props.setMetaMessengerPositions
                            : props.setMetaAudienceNetworkPositions
                    return (
                      <div key={group.key} className="rounded-lg border border-slate-200 p-3">
                        <label className="flex items-center gap-2 text-sm font-semibold text-slate-800">
                          <input
                            type="checkbox"
                            checked={selected}
                            disabled={props.disabled}
                            onChange={(event) =>
                              props.setMetaPublisherPlatforms(
                                event.target.checked
                                  ? [...new Set([...props.metaPublisherPlatforms, group.key])]
                                  : props.metaPublisherPlatforms.filter((item) => item !== group.key),
                              )
                            }
                          />
                          {group.label}
                        </label>
                        {selected && (
                          <div className="mt-3 grid gap-2 sm:grid-cols-2">
                            {group.positions.map(([value, label]) => (
                              <label key={value} className="flex items-center gap-2 text-xs text-slate-600">
                                <input
                                  type="checkbox"
                                  checked={positions.includes(value)}
                                  disabled={props.disabled}
                                  onChange={(event) =>
                                    setPositions(
                                      event.target.checked
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
                  <p className="text-xs leading-5 text-slate-500">
                    If a selected platform has no individual positions checked, Meta can use all eligible positions on that platform.
                  </p>
                </div>
              )}
            </div>
            <Field label="Meta Pixel">
              <select
                className="form-input mt-2"
                value={props.metaPixelId}
                disabled={props.disabled}
                onChange={(event) => props.setMetaPixelId(event.target.value)}
              >
                <option value="">Select Pixel</option>
                {metaResources
                  .filter((resource) => resource.resourceType === 'META_PIXEL')
                  .map((resource) => (
                    <option key={resource.id} value={resource.externalResourceId}>
                      {resource.name ?? resource.externalResourceId}
                    </option>
                  ))}
              </select>
            </Field>

            {conversionRequired && (
              <>
                <Field label="Conversion mapping">
                  <select
                    className="form-input mt-2"
                    value={props.metaConversionMode}
                    disabled={props.disabled}
                    onChange={(event) =>
                      props.setMetaConversionMode(event.target.value as 'PROVIDER' | 'STANDARD')
                    }
                  >
                    <option value="PROVIDER">Synced Meta conversion</option>
                    <option value="STANDARD">Standard event + Pixel</option>
                  </select>
                </Field>
                {props.metaConversionMode === 'PROVIDER' ? (
                  <Field label="Meta conversion">
                    <select
                      className="form-input mt-2"
                      value={props.metaConversionId}
                      disabled={props.disabled}
                      onChange={(event) => props.setMetaConversionId(event.target.value)}
                    >
                      <option value="">Select conversion</option>
                      {metaConversions.map((conversion) => (
                        <option key={conversion.id} value={conversion.id}>
                          {conversion.name}
                          {conversion.customEventType ? ` · ${pretty(conversion.customEventType)}` : ''}
                          {isRecommendedConversion(conversion, props.businessGoal, 'META_ADS')
                            ? ' · Recommended'
                            : ''}
                        </option>
                      ))}
                    </select>
                  </Field>
                ) : (
                  <Field label="Standard event">
                    <select
                      className="form-input mt-2"
                      value={props.metaStandardEvent}
                      disabled={props.disabled}
                      onChange={(event) => props.setMetaStandardEvent(event.target.value)}
                    >
                      <option value="">Select standard event</option>
                      {META_STANDARD_EVENTS.map((eventName) => (
                        <option key={eventName} value={eventName}>
                          {pretty(eventName)}
                          {eventName === recommendedMetaEvent(props.businessGoal)
                            ? ' · Recommended'
                            : ''}
                        </option>
                      ))}
                    </select>
                    {recommendedMetaEvent(props.businessGoal) && (
                      <span className="mt-1 block text-[11px] font-normal text-slate-500">
                        Suggested for {pretty(props.businessGoal)}: {pretty(recommendedMetaEvent(props.businessGoal))}
                      </span>
                    )}
                  </Field>
                )}
              </>
            )}
          </div>
        </section>
      )}
    </div>
  )
}

function Field({
  label,
  hint,
  children,
}: {
  label: string
  hint?: string
  children: ReactNode
}) {
  return (
    <label className="block text-xs font-semibold text-slate-700">
      {label}
      {children}
      {hint && (
        <span className="mt-1 block text-[11px] font-normal leading-5 text-slate-500">
          {hint}
        </span>
      )}
    </label>
  )
}
