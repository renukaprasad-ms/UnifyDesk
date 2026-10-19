import { useSnackbarState } from '../feedback/useSnackbarState'
import {
  useEffect,
  useMemo,
  useState,
  type Dispatch,
  type FormEvent,
  type SetStateAction,
} from 'react'
import { FiPlus, FiRefreshCw, FiSave, FiTrash2 } from 'react-icons/fi'
import { adCenterApi } from '../../features/ads/adCenterApi'
import type {
  GoogleDemandGenAudienceOption,
  GoogleDemandGenChannelPerformance,
  GoogleDemandGenSelectedChannels,
  ReachlyoAudienceLocationInput,
} from '../../features/ads/types'
import { apiErrorMessage } from '../../lib/api/apiClient'

type Props = {
  companyId: string
  adGroupId: string
  currency?: string | null
  canModify: boolean
}

type ChannelMode =
  | 'ALL_CHANNELS'
  | 'ALL_OWNED_AND_OPERATED_CHANNELS'
  | 'INDIVIDUAL'

type LocationRow = ReachlyoAudienceLocationInput & { id: string }

const newLocation = (): LocationRow => ({
  id: crypto.randomUUID(),
  countryCode: 'IN',
  region: '',
  city: '',
  postalCode: '',
})

const CHANNELS: Array<{ key: keyof GoogleDemandGenSelectedChannels; label: string }> = [
  { key: 'youtubeInFeed', label: 'YouTube In-Feed' },
  { key: 'youtubeInStream', label: 'YouTube In-Stream' },
  { key: 'youtubeShorts', label: 'YouTube Shorts' },
  { key: 'discover', label: 'Discover' },
  { key: 'gmail', label: 'Gmail' },
  { key: 'display', label: 'Display Network' },
  { key: 'maps', label: 'Google Maps' },
]

const normalizeLocation = (row: LocationRow): ReachlyoAudienceLocationInput => ({
  countryCode: row.countryCode.trim().toUpperCase(),
  region: row.region?.trim() || null,
  city: row.city?.trim() || null,
  postalCode: row.postalCode?.trim() || null,
})

const formatMetric = (value: number) =>
  new Intl.NumberFormat(undefined, { maximumFractionDigits: 2 }).format(value)

const formatMoney = (micros: number, currency?: string | null) => {
  const value = micros / 1_000_000
  if (!currency) return formatMetric(value)
  try {
    return new Intl.NumberFormat(undefined, {
      style: 'currency',
      currency,
      maximumFractionDigits: 2,
    }).format(value)
  } catch {
    return `${formatMetric(value)} ${currency}`
  }
}

const dateRange = () => {
  const to = new Date()
  const from = new Date()
  from.setDate(from.getDate() - 29)
  const iso = (value: Date) => value.toISOString().slice(0, 10)
  return { from: iso(from), to: iso(to) }
}

export default function GoogleDemandGenTargetingPanel({
  companyId,
  adGroupId,
  currency,
  canModify,
}: Props) {
  const [channelMode, setChannelMode] = useState<ChannelMode>('ALL_CHANNELS')
  const [selectedChannels, setSelectedChannels] =
    useState<GoogleDemandGenSelectedChannels>({
      youtubeInFeed: true,
      youtubeInStream: true,
      youtubeShorts: true,
      discover: true,
      gmail: true,
      display: true,
      maps: true,
    })
  const [locations, setLocations] = useState<LocationRow[]>([])
  const [locationResources, setLocationResources] = useState<string[]>([])
  const [excludedLocations, setExcludedLocations] = useState<LocationRow[]>([])
  const [excludedLocationResources, setExcludedLocationResources] = useState<string[]>([])
  const [languages, setLanguages] = useState('')
  const [languageResources, setLanguageResources] = useState<string[]>([])
  const [audiences, setAudiences] = useState<GoogleDemandGenAudienceOption[]>([])
  const [selectedAudiences, setSelectedAudiences] = useState<string[]>([])
  const [performance, setPerformance] = useState<GoogleDemandGenChannelPerformance[]>([])
  const [audienceGrouped, setAudienceGrouped] = useState(true)
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [, setError] = useSnackbarState('', 'error')
  const [, setNotice] = useSnackbarState('', 'success')

  const range = useMemo(() => dateRange(), [])

  const load = async () => {
    setLoading(true)
    setError('')
    try {
      const [targeting, audienceOptions, channelPerformance] = await Promise.all([
        adCenterApi.googleDemandGenTargeting(companyId, adGroupId),
        adCenterApi.googleDemandGenAudiences(companyId, adGroupId),
        adCenterApi.googleDemandGenChannelPerformance(
          companyId,
          adGroupId,
          range.from,
          range.to,
        ),
      ])

      setAudienceGrouped(targeting.useAudienceGrouped)
      setAudiences(audienceOptions)
      setSelectedAudiences(targeting.audienceResources)
      setLocations([])
      setExcludedLocations([])
      setLanguages('')
      setLocationResources(targeting.locationResources)
      setExcludedLocationResources(targeting.excludedLocationResources)
      setLanguageResources(targeting.languageResources)

      if (targeting.channelConfig === 'SELECTED_CHANNELS') {
        setChannelMode('INDIVIDUAL')
        setSelectedChannels(targeting.selectedChannels ?? {})
      } else {
        setChannelMode(
          targeting.channelStrategy === 'ALL_OWNED_AND_OPERATED_CHANNELS'
            ? 'ALL_OWNED_AND_OPERATED_CHANNELS'
            : 'ALL_CHANNELS',
        )
      }

      setPerformance(channelPerformance)
    } catch (loadError) {
      setError(apiErrorMessage(loadError, 'Unable to load Demand Gen targeting'))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    void load()
  }, [companyId, adGroupId])

  const save = async (event: FormEvent) => {
    event.preventDefault()
    setSaving(true)
    setError('')
    setNotice('')
    try {
      const result = await adCenterApi.updateGoogleDemandGenTargeting(
        companyId,
        adGroupId,
        {
          channelStrategy:
            channelMode === 'INDIVIDUAL' ? null : channelMode,
          selectedChannels:
            channelMode === 'INDIVIDUAL' ? selectedChannels : null,
          locations: locations.map(normalizeLocation),
          locationResourceNames: locationResources,
          excludedLocations: excludedLocations.map(normalizeLocation),
          excludedLocationResourceNames: excludedLocationResources,
          languages: languages
            .split(',')
            .map((item) => item.trim())
            .filter(Boolean),
          languageResourceNames: languageResources,
          audienceResourceNames: selectedAudiences,
        },
      )
      setAudienceGrouped(result.useAudienceGrouped)
      setNotice('Demand Gen targeting saved to Google Ads.')
      setPerformance(
        await adCenterApi.googleDemandGenChannelPerformance(
          companyId,
          adGroupId,
          range.from,
          range.to,
        ),
      )
    } catch (saveError) {
      setError(apiErrorMessage(saveError, 'Unable to update Demand Gen targeting'))
    } finally {
      setSaving(false)
    }
  }

  const setLocation = (
    setter: Dispatch<SetStateAction<LocationRow[]>>,
    id: string,
    patch: Partial<LocationRow>,
  ) =>
    setter((current) =>
      current.map((item) => (item.id === id ? { ...item, ...patch } : item)),
    )

  return (
    <section className="panel mt-5 overflow-hidden">
      <div className="flex flex-wrap items-start justify-between gap-3 border-b border-slate-100 px-5 py-4">
        <div>
          <h2 className="text-base font-bold text-slate-950">
            Demand Gen targeting & channels
          </h2>
          <p className="mt-1 max-w-3xl text-xs leading-5 text-slate-500">
            Configure ad-group channels, locations, languages, reusable Audiences, exclusions,
            and inspect the last 30 days of Google channel delivery.
          </p>
        </div>
        <button
          type="button"
          className="secondary-button inline-flex items-center gap-2"
          disabled={loading || saving}
          onClick={() => void load()}
        >
          <FiRefreshCw className={loading ? 'animate-spin' : ''} aria-hidden="true" />
          Refresh
        </button>
      </div>



      <form className="space-y-6 p-5" onSubmit={save}>
        <div>
          <h3 className="text-sm font-bold text-slate-900">Channel controls</h3>
          <div className="mt-3 grid gap-2 sm:grid-cols-3">
            {([
              ['ALL_CHANNELS', 'All Demand Gen channels'],
              ['ALL_OWNED_AND_OPERATED_CHANNELS', 'Google-owned channels only'],
              ['INDIVIDUAL', 'Choose channels individually'],
            ] as const).map(([value, label]) => (
              <button
                key={value}
                type="button"
                className={
                  'rounded-xl border px-3 py-3 text-left text-xs font-semibold ' +
                  (channelMode === value
                    ? 'border-indigo-300 bg-indigo-50 text-indigo-700'
                    : 'border-slate-200 text-slate-600')
                }
                disabled={!canModify || loading || saving}
                onClick={() => setChannelMode(value)}
              >
                {label}
              </button>
            ))}
          </div>

          {channelMode === 'INDIVIDUAL' && (
            <div className="mt-3 flex flex-wrap gap-2">
              {CHANNELS.map((channel) => (
                <label
                  key={channel.key}
                  className="inline-flex items-center gap-2 rounded-full border border-slate-200 px-3 py-2 text-xs text-slate-600"
                >
                  <input
                    type="checkbox"
                    checked={selectedChannels[channel.key] === true}
                    disabled={!canModify || saving}
                    onChange={(event) =>
                      setSelectedChannels((current) => ({
                        ...current,
                        [channel.key]: event.target.checked,
                      }))
                    }
                  />
                  {channel.label}
                </label>
              ))}
            </div>
          )}
        </div>

        <ExistingResourceChips
          title="Existing included location targets"
          resources={locationResources}
          disabled={!canModify || saving}
          onRemove={(resource) =>
            setLocationResources((current) =>
              current.filter((item) => item !== resource),
            )
          }
        />

        <LocationEditor
          title="Add included locations"
          rows={locations}
          disabled={!canModify || saving}
          onAdd={() => setLocations((current) => [...current, newLocation()])}
          onRemove={(id) =>
            setLocations((current) => current.filter((item) => item.id !== id))
          }
          onChange={(id, patch) => setLocation(setLocations, id, patch)}
        />

        <ExistingResourceChips
          title="Existing excluded location targets"
          resources={excludedLocationResources}
          disabled={!canModify || saving}
          onRemove={(resource) =>
            setExcludedLocationResources((current) =>
              current.filter((item) => item !== resource),
            )
          }
        />

        <LocationEditor
          title="Add excluded locations"
          rows={excludedLocations}
          disabled={!canModify || saving}
          onAdd={() =>
            setExcludedLocations((current) => [...current, newLocation()])
          }
          onRemove={(id) =>
            setExcludedLocations((current) =>
              current.filter((item) => item.id !== id),
            )
          }
          onChange={(id, patch) =>
            setLocation(setExcludedLocations, id, patch)
          }
        />

        <ExistingResourceChips
          title="Existing language targets"
          resources={languageResources}
          disabled={!canModify || saving}
          onRemove={(resource) =>
            setLanguageResources((current) =>
              current.filter((item) => item !== resource),
            )
          }
        />

        <label className="block text-xs font-semibold text-slate-700">
          Add languages
          <input
            className="form-input mt-2"
            value={languages}
            disabled={!canModify || saving}
            onChange={(event) => setLanguages(event.target.value)}
            placeholder="en, hi"
          />
          <span className="mt-1 block font-normal text-slate-500">
            Enter Google language code or name, separated by commas.
          </span>
        </label>

        <div>
          <h3 className="text-sm font-bold text-slate-900">Reusable Google Audiences</h3>
          {!audienceGrouped && (
            <div className="mt-2 rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-xs leading-5 text-amber-800">
              This older ad group was created without grouped Audience targeting. Google makes
              that setting immutable, so create a new Demand Gen ad group to attach reusable
              Audience resources.
            </div>
          )}
          <div className="mt-3 grid gap-2 sm:grid-cols-2">
            {audiences.length === 0 ? (
              <p className="text-xs text-slate-500">No reusable Google Audience resources found.</p>
            ) : (
              audiences.map((audience) => (
                <label
                  key={audience.resourceName}
                  className="flex items-start gap-3 rounded-xl border border-slate-200 p-3"
                >
                  <input
                    type="checkbox"
                    className="mt-1"
                    checked={selectedAudiences.includes(audience.resourceName)}
                    disabled={!canModify || saving || !audienceGrouped}
                    onChange={(event) =>
                      setSelectedAudiences((current) =>
                        event.target.checked
                          ? [...current, audience.resourceName]
                          : current.filter((item) => item !== audience.resourceName),
                      )
                    }
                  />
                  <span>
                    <span className="block text-xs font-semibold text-slate-900">
                      {audience.name ?? audience.id ?? 'Google Audience'}
                    </span>
                    {audience.description && (
                      <span className="mt-1 block text-[11px] leading-5 text-slate-500">
                        {audience.description}
                      </span>
                    )}
                  </span>
                </label>
              ))
            )}
          </div>
        </div>

        {canModify && (
          <div className="flex justify-end border-t border-slate-100 pt-5">
            <button
              type="submit"
              className="primary-button inline-flex items-center gap-2"
              disabled={loading || saving}
            >
              <FiSave aria-hidden="true" />
              {saving ? 'Saving…' : 'Save Demand Gen targeting'}
            </button>
          </div>
        )}
      </form>

      <div className="border-t border-slate-100 px-5 py-5">
        <h3 className="text-sm font-bold text-slate-900">
          Channel performance · {range.from} to {range.to}
        </h3>
        <div className="mt-3 overflow-x-auto">
          <table className="min-w-full text-left text-xs">
            <thead className="border-b border-slate-200 text-slate-500">
              <tr>
                <th className="px-2 py-2">Channel</th>
                <th className="px-2 py-2 text-right">Impressions</th>
                <th className="px-2 py-2 text-right">Clicks</th>
                <th className="px-2 py-2 text-right">Spend</th>
                <th className="px-2 py-2 text-right">Conversions</th>
                <th className="px-2 py-2 text-right">Value</th>
              </tr>
            </thead>
            <tbody>
              {performance.length === 0 ? (
                <tr>
                  <td colSpan={6} className="px-2 py-5 text-center text-slate-400">
                    No Demand Gen channel delivery returned for this period.
                  </td>
                </tr>
              ) : (
                performance.map((row) => (
                  <tr key={row.channel} className="border-b border-slate-100">
                    <td className="px-2 py-2 font-semibold text-slate-800">
                      {row.channel.replaceAll('_', ' ')}
                    </td>
                    <td className="px-2 py-2 text-right">{formatMetric(row.impressions)}</td>
                    <td className="px-2 py-2 text-right">{formatMetric(row.clicks)}</td>
                    <td className="px-2 py-2 text-right">
                      {formatMoney(row.costMicros, currency)}
                    </td>
                    <td className="px-2 py-2 text-right">{formatMetric(row.conversions)}</td>
                    <td className="px-2 py-2 text-right">
                      {formatMetric(row.conversionsValue)}
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </div>
    </section>
  )
}

function ExistingResourceChips({
  title,
  resources,
  disabled,
  onRemove,
}: {
  title: string
  resources: string[]
  disabled: boolean
  onRemove: (resource: string) => void
}) {
  if (resources.length === 0) return null
  return (
    <div>
      <h3 className="text-sm font-bold text-slate-900">{title}</h3>
      <div className="mt-2 flex flex-wrap gap-2">
        {resources.map((resource) => (
          <span
            key={resource}
            className="inline-flex items-center gap-2 rounded-full border border-slate-200 bg-slate-50 px-3 py-1.5 text-xs text-slate-600"
          >
            {resource}
            <button
              type="button"
              className="text-slate-400 hover:text-red-600"
              disabled={disabled}
              title="Remove target"
              onClick={() => onRemove(resource)}
            >
              <FiTrash2 aria-hidden="true" />
            </button>
          </span>
        ))}
      </div>
    </div>
  )
}

function LocationEditor({
  title,
  rows,
  disabled,
  onAdd,
  onRemove,
  onChange,
}: {
  title: string
  rows: LocationRow[]
  disabled: boolean
  onAdd: () => void
  onRemove: (id: string) => void
  onChange: (id: string, patch: Partial<LocationRow>) => void
}) {
  return (
    <div>
      <div className="flex items-center justify-between gap-3">
        <div>
          <h3 className="text-sm font-bold text-slate-900">{title}</h3>
          <p className="mt-1 text-xs text-slate-500">
            Country is required; optionally narrow by region, city, or postal code.
          </p>
        </div>
        <button
          type="button"
          className="secondary-button inline-flex items-center gap-2 text-xs"
          disabled={disabled}
          onClick={onAdd}
        >
          <FiPlus aria-hidden="true" />
          Add location
        </button>
      </div>
      <div className="mt-3 space-y-2">
        {rows.map((row) => (
          <div
            key={row.id}
            className="grid gap-2 rounded-xl border border-slate-200 p-3 sm:grid-cols-[90px_1fr_1fr_1fr_auto]"
          >
            <input
              className="form-input"
              value={row.countryCode}
              maxLength={8}
              disabled={disabled}
              placeholder="IN"
              onChange={(event) =>
                onChange(row.id, { countryCode: event.target.value })
              }
            />
            <input
              className="form-input"
              value={row.region ?? ''}
              disabled={disabled}
              placeholder="Region / state"
              onChange={(event) => onChange(row.id, { region: event.target.value })}
            />
            <input
              className="form-input"
              value={row.city ?? ''}
              disabled={disabled}
              placeholder="City"
              onChange={(event) => onChange(row.id, { city: event.target.value })}
            />
            <input
              className="form-input"
              value={row.postalCode ?? ''}
              disabled={disabled}
              placeholder="Postal code"
              onChange={(event) =>
                onChange(row.id, { postalCode: event.target.value })
              }
            />
            <button
              type="button"
              className="rounded-lg p-2 text-slate-400 hover:bg-red-50 hover:text-red-600"
              disabled={disabled}
              onClick={() => onRemove(row.id)}
            >
              <FiTrash2 aria-hidden="true" />
            </button>
          </div>
        ))}
        {rows.length === 0 && (
          <p className="rounded-xl border border-dashed border-slate-200 px-4 py-4 text-xs text-slate-400">
            No explicit locations configured. Google account/campaign defaults apply.
          </p>
        )}
      </div>
    </div>
  )
}
