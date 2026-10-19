import { useSnackbarState } from '../feedback/useSnackbarState'
import { useEffect, useMemo, useState } from 'react'
import { FiRefreshCw, FiSave } from 'react-icons/fi'
import { adCenterApi } from '../../features/ads/adCenterApi'
import type {
  AdAudience,
  GoogleSearchAudienceMode,
  GoogleSearchAudienceTargeting,
} from '../../features/ads/types'
import { apiErrorMessage } from '../../lib/api/apiClient'
import { CardSkeleton } from '../ui/Skeleton'

type Props = {
  companyId: string
  accountId: string
  adGroupId: string
  canModify: boolean
}

const eligibleForSearch = (audience: AdAudience) => {
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

const pretty = (value: string) =>
  value
    .replaceAll('_', ' ')
    .toLowerCase()
    .replace(/\b\w/g, (character) => character.toUpperCase())

export default function GoogleSearchAudienceTargetingPanel({
  companyId,
  accountId,
  adGroupId,
  canModify,
}: Props) {
  const [snapshot, setSnapshot] = useState<GoogleSearchAudienceTargeting | null>(null)
  const [audiences, setAudiences] = useState<AdAudience[]>([])
  const [mode, setMode] = useState<GoogleSearchAudienceMode>('OBSERVATION')
  const [included, setIncluded] = useState<string[]>([])
  const [excluded, setExcluded] = useState<string[]>([])
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [syncing, setSyncing] = useState(false)
  const [, setError] = useSnackbarState('', 'error')
  const [, setNotice] = useSnackbarState('', 'success')

  const options = useMemo(() => audiences.filter(eligibleForSearch), [audiences])

  const applySnapshot = (value: GoogleSearchAudienceTargeting) => {
    setSnapshot(value)
    setMode(value.mode)
    setIncluded(value.includedAudienceIds.map(String))
    setExcluded(value.excludedAudienceIds.map(String))
  }

  const load = async () => {
    setLoading(true)
    setError('')
    try {
      const [targeting, audienceData] = await Promise.all([
        adCenterApi.googleSearchAudienceTargeting(companyId, adGroupId),
        adCenterApi.audiences(companyId, accountId),
      ])
      setAudiences(audienceData)
      applySnapshot(targeting)
    } catch (loadError) {
      setError(apiErrorMessage(loadError, 'Unable to load Google Search audience targeting'))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    void load()
  }, [companyId, accountId, adGroupId])

  const toggleIncluded = (audienceId: string, checked: boolean) => {
    setIncluded((current) =>
      checked ? [...new Set([...current, audienceId])] : current.filter((id) => id !== audienceId),
    )
    if (checked) {
      setExcluded((current) => current.filter((id) => id !== audienceId))
    }
  }

  const toggleExcluded = (audienceId: string, checked: boolean) => {
    setExcluded((current) =>
      checked ? [...new Set([...current, audienceId])] : current.filter((id) => id !== audienceId),
    )
    if (checked) {
      setIncluded((current) => current.filter((id) => id !== audienceId))
    }
  }

  const save = async () => {
    setSaving(true)
    setError('')
    setNotice('')
    try {
      const updated = await adCenterApi.updateGoogleSearchAudienceTargeting(companyId, adGroupId, {
        mode,
        includedAudienceIds: included,
        excludedAudienceIds: excluded,
      })
      applySnapshot(updated)
      setNotice('Search audience targeting updated in Google Ads.')
    } catch (saveError) {
      setError(apiErrorMessage(saveError, 'Unable to update Google Search audience targeting'))
    } finally {
      setSaving(false)
    }
  }

  const syncProviderAudiences = async () => {
    setSyncing(true)
    setError('')
    setNotice('')
    try {
      await adCenterApi.syncGoogleAudiences(companyId, accountId)
      await load()
      setNotice('Google audience lists refreshed.')
    } catch (syncError) {
      setError(apiErrorMessage(syncError, 'Unable to refresh Google audience lists'))
    } finally {
      setSyncing(false)
    }
  }

  return (
    <section className="panel mt-5 overflow-hidden">
      <div className="flex flex-wrap items-start justify-between gap-3 border-b border-slate-100 px-5 py-4">
        <div>
          <h2 className="font-bold text-slate-950">Search audience targeting</h2>
          <p className="mt-1 max-w-3xl text-xs leading-5 text-slate-500">
            Read and edit the real Google Search ad-group audience criteria. Observation keeps
            normal Search reach; Targeting restricts delivery to the included audience lists.
          </p>
        </div>
        <button
          type="button"
          className="secondary-button inline-flex items-center gap-2"
          disabled={syncing || loading}
          onClick={() => void syncProviderAudiences()}
        >
          <FiRefreshCw aria-hidden="true" />
          {syncing ? 'Refreshing…' : 'Refresh audiences'}
        </button>
      </div>

      <div className="p-5">


        {loading ? (
          <CardSkeleton rows={5} />
        ) : (
          <>
            <div className="grid gap-4 sm:grid-cols-2">
              <label className="block text-xs font-semibold text-slate-700">
                Audience mode
                <select
                  className="form-input mt-2"
                  value={mode}
                  disabled={!canModify}
                  onChange={(event) => setMode(event.target.value as GoogleSearchAudienceMode)}
                >
                  <option value="OBSERVATION">Observation</option>
                  <option value="TARGETING">Targeting</option>
                </select>
                <span className="mt-1 block text-[11px] font-normal leading-5 text-slate-500">
                  Observation measures selected audiences without narrowing Search traffic.
                </span>
              </label>

              <div className="rounded-xl border border-slate-200 bg-slate-50 p-4">
                <p className="text-xs font-semibold text-slate-700">Provider state</p>
                <p className="mt-2 text-xs text-slate-500">
                  {included.length} included · {excluded.length} excluded · {options.length}{' '}
                  Search-eligible synchronized lists
                </p>
                {snapshot?.preservedTargetingDimensions.length ? (
                  <p className="mt-2 text-[11px] leading-5 text-slate-500">
                    Other Google targeting restrictions preserved:{' '}
                    {snapshot.preservedTargetingDimensions.map(pretty).join(', ')}.
                  </p>
                ) : null}
              </div>
            </div>

            {snapshot?.providerOnlyIncludedExternalIds.length ||
            snapshot?.providerOnlyExcludedExternalIds.length ? (
              <div className="mt-4 rounded-xl border border-amber-200 bg-amber-50 p-4 text-xs leading-5 text-amber-800">
                Google currently has audience criteria that are not mapped to a synchronized local
                audience. UnifyDesk preserves them when you save. Included provider IDs:{' '}
                {snapshot.providerOnlyIncludedExternalIds.join(', ') || 'none'}; excluded provider
                IDs: {snapshot.providerOnlyExcludedExternalIds.join(', ') || 'none'}.
              </div>
            ) : null}

            {options.length === 0 ? (
              <div className="mt-4 rounded-xl border border-amber-200 bg-amber-50 p-4 text-sm text-amber-800">
                No Search-eligible Google audience lists are synchronized for this account.
              </div>
            ) : (
              <div className="mt-5 space-y-2">
                {options.map((audience) => {
                  const includeChecked = included.includes(audience.id)
                  const excludeChecked = excluded.includes(audience.id)
                  return (
                    <div
                      key={audience.id}
                      className="flex flex-wrap items-start justify-between gap-3 rounded-xl border border-slate-200 px-4 py-3"
                    >
                      <div>
                        <p className="text-sm font-semibold text-slate-900">{audience.name}</p>
                        <p className="mt-1 text-[11px] text-slate-500">
                          {audience.subtype ? pretty(audience.subtype) : 'Google audience'}
                          {audience.approximateCountLowerBound != null
                            ? ` · Search size ${audience.approximateCountLowerBound.toLocaleString()}`
                            : ''}
                        </p>
                      </div>
                      <div className="flex flex-wrap gap-4">
                        <label className="inline-flex items-center gap-2 text-xs font-medium text-slate-600">
                          <input
                            type="checkbox"
                            checked={includeChecked}
                            disabled={!canModify}
                            onChange={(event) => toggleIncluded(audience.id, event.target.checked)}
                          />
                          Include
                        </label>
                        <label className="inline-flex items-center gap-2 text-xs font-medium text-slate-600">
                          <input
                            type="checkbox"
                            checked={excludeChecked}
                            disabled={!canModify}
                            onChange={(event) => toggleExcluded(audience.id, event.target.checked)}
                          />
                          Exclude
                        </label>
                      </div>
                    </div>
                  )
                })}
              </div>
            )}

            {canModify && (
              <div className="mt-5 flex justify-end border-t border-slate-100 pt-5">
                <button
                  type="button"
                  className="primary-button inline-flex items-center gap-2"
                  disabled={saving}
                  onClick={() => void save()}
                >
                  <FiSave aria-hidden="true" />
                  {saving ? 'Saving…' : 'Save audience targeting'}
                </button>
              </div>
            )}
          </>
        )}
      </div>
    </section>
  )
}
