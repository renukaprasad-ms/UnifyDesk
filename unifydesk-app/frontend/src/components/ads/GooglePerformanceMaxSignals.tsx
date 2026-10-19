import { useSnackbarState } from '../feedback/useSnackbarState'
import { useEffect, useMemo, useState } from 'react'
import { FiPlus, FiRefreshCw } from 'react-icons/fi'
import { adCenterApi } from '../../features/ads/adCenterApi'
import type { AdAudience, GoogleAssetGroup, GoogleAssetGroupSignal } from '../../features/ads/types'
import { apiErrorMessage } from '../../lib/api/apiClient'

type Props = {
  companyId: string
  campaignId: string
  assetGroup: GoogleAssetGroup
  audiences: AdAudience[]
  canModify: boolean
}

export default function GooglePerformanceMaxSignals({
  companyId,
  campaignId,
  assetGroup,
  audiences,
  canModify,
}: Props) {
  const [signals, setSignals] = useState<GoogleAssetGroupSignal[]>([])
  const [themes, setThemes] = useState('')
  const [selectedAudienceIds, setSelectedAudienceIds] = useState<string[]>([])
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [, setError] = useSnackbarState('', 'error')
  const [, setNotice] = useSnackbarState('', 'success')

  const eligibleAudiences = useMemo(
    () => audiences.filter((audience) => audience.accessible),
    [audiences],
  )

  const load = async () => {
    if (!assetGroup.id) return
    setLoading(true)
    setError('')
    try {
      setSignals(await adCenterApi.googleAssetGroupSignals(companyId, campaignId, assetGroup.id))
    } catch (loadError) {
      setError(apiErrorMessage(loadError, 'Unable to load Performance Max signals'))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    void load()
  }, [companyId, campaignId, assetGroup.id])

  const addSignals = async () => {
    if (!assetGroup.id) return
    const searchThemes = themes
      .split(/[\n,]/)
      .map((value) => value.trim())
      .filter(Boolean)

    if (searchThemes.length === 0 && selectedAudienceIds.length === 0) return

    setSaving(true)
    setError('')
    setNotice('')
    try {
      const updated = await adCenterApi.addGoogleAssetGroupSignals(
        companyId,
        campaignId,
        assetGroup.id,
        {
          searchThemes,
          audienceIds: selectedAudienceIds,
        },
      )
      setSignals(updated)
      setThemes('')
      setSelectedAudienceIds([])
      setNotice('Performance Max signals updated.')
    } catch (saveError) {
      setError(apiErrorMessage(saveError, 'Unable to update Performance Max signals'))
    } finally {
      setSaving(false)
    }
  }

  const searchThemeSignals = signals.filter((signal) => signal.type === 'SEARCH_THEME')
  const audienceSignals = signals.filter((signal) => signal.type === 'AUDIENCE')

  return (
    <div className="border-t border-slate-100 bg-slate-50/70 px-5 py-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <p className="text-sm font-bold text-slate-900">Audience & search signals</p>
          <p className="mt-1 text-xs text-slate-500">
            Signals guide Google AI without restricting Performance Max to only these users or
            themes.
          </p>
        </div>
        <button
          type="button"
          className="secondary-button inline-flex items-center gap-2 text-xs"
          disabled={loading}
          onClick={() => void load()}
        >
          <FiRefreshCw aria-hidden="true" />
          Refresh
        </button>
      </div>



      <div className="mt-4 flex flex-wrap gap-2">
        {searchThemeSignals.map((signal) => (
          <span
            key={signal.resourceName ?? signal.value}
            className="rounded-full bg-indigo-50 px-2.5 py-1 text-xs font-semibold text-indigo-700"
          >
            {signal.value}
          </span>
        ))}
        {audienceSignals.map((signal) => (
          <span
            key={signal.resourceName ?? signal.value}
            className="rounded-full bg-emerald-50 px-2.5 py-1 text-xs font-semibold text-emerald-700"
          >
            Audience signal
          </span>
        ))}
        {!loading && signals.length === 0 && (
          <span className="text-xs text-slate-400">No signals configured.</span>
        )}
      </div>

      {canModify && (
        <div className="mt-4 grid gap-4 lg:grid-cols-2">
          <label className="text-xs font-semibold text-slate-700">
            Search themes
            <textarea
              className="form-input mt-2 min-h-28"
              placeholder={'marketing automation\ncustomer engagement\nomnichannel messaging'}
              value={themes}
              onChange={(event) => setThemes(event.target.value)}
            />
            <span className="mt-1 block font-normal text-slate-400">
              One per line or comma-separated. Google allows up to 25 per Asset Group.
            </span>
          </label>

          <div>
            <p className="text-xs font-semibold text-slate-700">Customer Match audience signals</p>
            <div className="mt-2 max-h-36 space-y-2 overflow-y-auto rounded-xl border border-slate-200 bg-white p-3">
              {eligibleAudiences.length === 0 ? (
                <p className="text-xs text-slate-400">
                  Create or sync a Google Customer Match audience first.
                </p>
              ) : (
                eligibleAudiences.map((audience) => (
                  <label
                    key={audience.id}
                    className="flex items-center gap-2 text-xs text-slate-600"
                  >
                    <input
                      type="checkbox"
                      checked={selectedAudienceIds.includes(audience.id)}
                      onChange={(event) =>
                        setSelectedAudienceIds((current) =>
                          event.target.checked
                            ? [...current, audience.id]
                            : current.filter((id) => id !== audience.id),
                        )
                      }
                    />
                    <span>{audience.name}</span>
                  </label>
                ))
              )}
            </div>
          </div>

          <div className="lg:col-span-2">
            <button
              type="button"
              className="primary-button inline-flex items-center gap-2"
              disabled={saving || (!themes.trim() && selectedAudienceIds.length === 0)}
              onClick={() => void addSignals()}
            >
              <FiPlus aria-hidden="true" />
              {saving ? 'Adding signals…' : 'Add signals'}
            </button>
          </div>
        </div>
      )}
    </div>
  )
}
