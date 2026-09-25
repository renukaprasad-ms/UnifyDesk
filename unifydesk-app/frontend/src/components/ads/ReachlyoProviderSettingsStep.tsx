import type { ReactNode } from 'react'
import { FiClock, FiPlus, FiRefreshCw, FiTrash2 } from 'react-icons/fi'
import type {
  AdAccountResource,
  AdConversion,
  CampaignBusinessGoal,
  CampaignChannelType,
} from '../../features/ads/types'

export type GoogleAdScheduleDraft = {
  dayOfWeek: string
  startTime: string
  endTime: string
}

type Props = {
  businessGoal: CampaignBusinessGoal
  selectedChannels: CampaignChannelType[]
  accountByChannel: Record<string, string>
  resourcesByAccount: Record<string, AdAccountResource[]>
  conversionsByAccount: Record<string, AdConversion[]>
  googlePolitical: 'true' | 'false' | ''
  setGooglePolitical: (value: 'true' | 'false' | '') => void
  googleBidStrategy: string
  setGoogleBidStrategy: (value: string) => void
  keywordMatchType: string
  setKeywordMatchType: (value: string) => void
  googleConversionId: string
  setGoogleConversionId: (value: string) => void
  googleSearchPartners: boolean
  setGoogleSearchPartners: (value: boolean) => void
  googleContentNetwork: boolean
  setGoogleContentNetwork: (value: boolean) => void
  googleGeoMode: 'PRESENCE' | 'PRESENCE_OR_INTEREST'
  setGoogleGeoMode: (value: 'PRESENCE' | 'PRESENCE_OR_INTEREST') => void
  googleAdSchedules: GoogleAdScheduleDraft[]
  setGoogleAdSchedules: (value: GoogleAdScheduleDraft[]) => void
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
  disabled: boolean
  onRefresh: () => void
}

const requiresConversion = (goal: CampaignBusinessGoal) =>
  goal !== 'TRAFFIC' && goal !== 'AWARENESS'

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

const pretty = (value: string) =>
  value
    .replaceAll('_', ' ')
    .toLowerCase()
    .replace(/\b\w/g, (character) => character.toUpperCase())

export default function ReachlyoProviderSettingsStep(props: Props) {
  const googleAccount = props.accountByChannel.GOOGLE_SEARCH ?? ''
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
                disabled={props.disabled}
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
                    </option>
                  ))}
                </select>
              </Field>
            )}
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
                        </option>
                      ))}
                    </select>
                  </Field>
                ) : (
                  <Field label="Standard event">
                    <input
                      className="form-input mt-2 uppercase"
                      value={props.metaStandardEvent}
                      disabled={props.disabled}
                      onChange={(event) => props.setMetaStandardEvent(event.target.value)}
                      placeholder="PURCHASE"
                    />
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
  children,
}: {
  label: string
  children: ReactNode
}) {
  return (
    <label className="block text-xs font-semibold text-slate-700">
      {label}
      {children}
    </label>
  )
}
