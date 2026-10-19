import { useSnackbarState } from '../feedback/useSnackbarState'
import { useEffect, useState } from 'react'
import { FiRefreshCw, FiSave } from 'react-icons/fi'
import { adCenterApi } from '../../features/ads/adCenterApi'
import type { GooglePerformanceMaxBrandGuidelines } from '../../features/ads/types'
import { apiErrorMessage } from '../../lib/api/apiClient'
import { CardSkeleton } from '../ui/Skeleton'

type Props = {
  companyId: string
  campaignId: string
  canModify: boolean
}

const fonts = [
  'Open Sans',
  'Roboto',
  'Montserrat',
  'Poppins',
  'Lato',
  'Oswald',
  'Playfair Display',
  'Roboto Slab',
]

export default function GooglePerformanceMaxBrandGuidelinesPanel({
  companyId,
  campaignId,
  canModify,
}: Props) {
  const [value, setValue] = useState<GooglePerformanceMaxBrandGuidelines | null>(null)
  const [mainColor, setMainColor] = useState('')
  const [accentColor, setAccentColor] = useState('')
  const [fontFamily, setFontFamily] = useState('')
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [, setError] = useSnackbarState('', 'error')
  const [, setNotice] = useSnackbarState('', 'success')

  const apply = (next: GooglePerformanceMaxBrandGuidelines) => {
    setValue(next)
    setMainColor(next.mainColor ?? '')
    setAccentColor(next.accentColor ?? '')
    setFontFamily(next.fontFamily ?? '')
  }

  const load = async () => {
    setLoading(true)
    setError('')
    try {
      apply(await adCenterApi.googlePerformanceMaxBrandGuidelines(companyId, campaignId))
    } catch (loadError) {
      setError(apiErrorMessage(loadError, 'Unable to load Performance Max brand guidelines'))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    void load()
  }, [companyId, campaignId])

  const save = async () => {
    setSaving(true)
    setError('')
    setNotice('')
    try {
      const updated = await adCenterApi.updateGooglePerformanceMaxBrandGuidelines(
        companyId,
        campaignId,
        {
          enableBrandGuidelines: value?.enabled ? true : undefined,
          mainColor: mainColor.trim() || null,
          accentColor: accentColor.trim() || null,
          fontFamily: fontFamily || null,
        },
      )
      apply(updated)
      setNotice('Performance Max brand guidelines updated in Google Ads.')
    } catch (saveError) {
      setError(apiErrorMessage(saveError, 'Unable to update Performance Max brand guidelines'))
    } finally {
      setSaving(false)
    }
  }

  const enable = async () => {
    setSaving(true)
    setError('')
    setNotice('')
    try {
      const updated = await adCenterApi.updateGooglePerformanceMaxBrandGuidelines(
        companyId,
        campaignId,
        {
          enableBrandGuidelines: true,
          autoPopulateBrandAssets: true,
          mainColor: mainColor.trim() || null,
          accentColor: accentColor.trim() || null,
          fontFamily: fontFamily || null,
        },
      )
      apply(updated)
      setNotice(
        'Brand Guidelines enabled. Google selected top-performing existing brand assets for the migration.',
      )
    } catch (enableError) {
      setError(apiErrorMessage(enableError, 'Unable to enable Performance Max brand guidelines'))
    } finally {
      setSaving(false)
    }
  }

  return (
    <section className="panel mt-5 overflow-hidden">
      <div className="flex flex-wrap items-start justify-between gap-3 border-b border-slate-100 px-5 py-4">
        <div>
          <h2 className="font-bold text-slate-950">Performance Max brand guidelines</h2>
          <p className="mt-1 max-w-3xl text-xs leading-5 text-slate-500">
            Controls campaign-level brand assets and the colors/font Google can use in automatically
            generated Performance Max formats.
          </p>
        </div>
        <button
          type="button"
          className="secondary-button inline-flex items-center gap-2"
          disabled={loading || saving}
          onClick={() => void load()}
        >
          <FiRefreshCw aria-hidden="true" />
          Refresh
        </button>
      </div>

      <div className="p-5">


        {loading ? (
          <CardSkeleton rows={4} />
        ) : value ? (
          <>
            <div
              className={`rounded-xl border p-4 ${
                value.enabled ? 'border-emerald-200 bg-emerald-50' : 'border-amber-200 bg-amber-50'
              }`}
            >
              <p
                className={`text-sm font-bold ${
                  value.enabled ? 'text-emerald-800' : 'text-amber-800'
                }`}
              >
                {value.enabled ? 'Brand Guidelines enabled' : 'Brand Guidelines not enabled'}
              </p>
              <p
                className={`mt-1 text-xs leading-5 ${
                  value.enabled ? 'text-emerald-700' : 'text-amber-700'
                }`}
              >
                {value.enabled
                  ? 'Google requires the business name and logo assets at campaign level. This setting cannot be disabled after enablement.'
                  : 'This older campaign can be migrated using Google’s dedicated Brand Guidelines enable operation.'}
              </p>
            </div>

            <div className="mt-4 grid gap-4 sm:grid-cols-3">
              <label className="block text-xs font-semibold text-slate-700">
                Main color
                <input
                  className="form-input mt-2"
                  value={mainColor}
                  disabled={!canModify}
                  placeholder="#3366FF"
                  onChange={(event) => setMainColor(event.target.value)}
                />
              </label>
              <label className="block text-xs font-semibold text-slate-700">
                Accent color
                <input
                  className="form-input mt-2"
                  value={accentColor}
                  disabled={!canModify}
                  placeholder="#FFCC00"
                  onChange={(event) => setAccentColor(event.target.value)}
                />
              </label>
              <label className="block text-xs font-semibold text-slate-700">
                Brand font
                <select
                  className="form-input mt-2"
                  value={fontFamily}
                  disabled={!canModify}
                  onChange={(event) => setFontFamily(event.target.value)}
                >
                  <option value="">Google default</option>
                  {fonts.map((font) => (
                    <option key={font} value={font}>
                      {font}
                    </option>
                  ))}
                </select>
              </label>
            </div>

            {value.enabled && (
              <div className="mt-5">
                <p className="text-xs font-semibold uppercase tracking-wide text-slate-400">
                  Campaign-level brand assets
                </p>
                {value.brandAssets.length === 0 ? (
                  <p className="mt-3 rounded-xl border border-amber-200 bg-amber-50 px-3 py-2 text-xs text-amber-800">
                    Google reports Brand Guidelines enabled but no readable brand asset links were
                    returned. Refresh after provider processing completes.
                  </p>
                ) : (
                  <div className="mt-3 grid gap-3 sm:grid-cols-2">
                    {value.brandAssets.map((asset) => (
                      <div
                        key={asset.resourceName ?? `${asset.fieldType}-${asset.assetId}`}
                        className="rounded-xl border border-slate-200 p-3"
                      >
                        <div className="flex items-center justify-between gap-3">
                          <p className="text-xs font-bold text-slate-800">
                            {asset.fieldType?.replaceAll('_', ' ') ?? 'Brand asset'}
                          </p>
                          <span className="rounded-full bg-slate-100 px-2 py-1 text-[10px] font-semibold text-slate-600">
                            {asset.primaryStatus ?? asset.status ?? 'UNKNOWN'}
                          </span>
                        </div>
                        <p className="mt-2 text-xs text-slate-600">
                          {asset.text ?? asset.assetName ?? asset.assetId ?? 'Google asset'}
                        </p>
                        {asset.primaryStatusReasons.length > 0 && (
                          <p className="mt-2 text-[11px] leading-5 text-slate-500">
                            {asset.primaryStatusReasons
                              .map((reason) => reason.replaceAll('_', ' ').toLowerCase())
                              .join(', ')}
                          </p>
                        )}
                      </div>
                    ))}
                  </div>
                )}
              </div>
            )}

            {canModify && (
              <div className="mt-5 flex justify-end border-t border-slate-100 pt-5">
                {value.enabled ? (
                  <button
                    type="button"
                    className="primary-button inline-flex items-center gap-2"
                    disabled={saving}
                    onClick={() => void save()}
                  >
                    <FiSave aria-hidden="true" />
                    {saving ? 'Saving…' : 'Save brand guidelines'}
                  </button>
                ) : (
                  <button
                    type="button"
                    className="primary-button inline-flex items-center gap-2"
                    disabled={saving}
                    onClick={() => void enable()}
                  >
                    <FiSave aria-hidden="true" />
                    {saving ? 'Enabling…' : 'Enable brand guidelines'}
                  </button>
                )}
              </div>
            )}
          </>
        ) : null}
      </div>
    </section>
  )
}
