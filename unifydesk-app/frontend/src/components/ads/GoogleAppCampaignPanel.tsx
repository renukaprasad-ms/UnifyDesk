import { useSnackbarState } from '../feedback/useSnackbarState'
import { useEffect, useMemo, useState } from 'react'
import { FiRefreshCw } from 'react-icons/fi'
import { adCenterApi } from '../../features/ads/adCenterApi'
import type {
  GoogleAppCampaignPerformance,
  GoogleAppCampaignSettings,
} from '../../features/ads/types'
import { apiErrorMessage } from '../../lib/api/apiClient'

type Props = {
  companyId: string
  campaignId: string
  currency?: string | null
}

const range = () => {
  const to = new Date()
  const from = new Date()
  from.setDate(from.getDate() - 29)
  const iso = (value: Date) => value.toISOString().slice(0, 10)
  return { from: iso(from), to: iso(to) }
}

const pretty = (value?: string | null) =>
  (value ?? '—').replaceAll('_', ' ').toLowerCase().replace(/\b\w/g, (c) => c.toUpperCase())

const money = (micros: number, currency?: string | null) => {
  const value = micros / 1_000_000
  if (!currency) return value.toFixed(2)
  try {
    return new Intl.NumberFormat(undefined, { style: 'currency', currency }).format(value)
  } catch {
    return `${value.toFixed(2)} ${currency}`
  }
}

export default function GoogleAppCampaignPanel({
  companyId,
  campaignId,
  currency,
}: Props) {
  const dates = useMemo(() => range(), [])
  const [settings, setSettings] = useState<GoogleAppCampaignSettings | null>(null)
  const [performance, setPerformance] =
    useState<GoogleAppCampaignPerformance | null>(null)
  const [loading, setLoading] = useState(true)
  const [, setError] = useSnackbarState('', 'error')

  const load = async () => {
    setLoading(true)
    setError('')
    try {
      const [nextSettings, nextPerformance] = await Promise.all([
        adCenterApi.googleAppCampaignSettings(companyId, campaignId),
        adCenterApi.googleAppCampaignPerformance(
          companyId,
          campaignId,
          dates.from,
          dates.to,
        ),
      ])
      setSettings(nextSettings)
      setPerformance(nextPerformance)
    } catch (loadError) {
      setError(apiErrorMessage(loadError, 'Unable to load Google App campaign details'))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    void load()
  }, [companyId, campaignId])

  return (
    <section className="panel mt-5 overflow-hidden">
      <div className="flex flex-wrap items-start justify-between gap-3 border-b border-slate-100 px-5 py-4">
        <div>
          <h2 className="text-base font-bold text-slate-950">App campaign configuration</h2>
          <p className="mt-1 text-xs leading-5 text-slate-500">
            App identity, store, subtype, and optimization goal are provider-owned settings.
            Performance covers the last 30 days.
          </p>
        </div>
        <button
          type="button"
          className="secondary-button inline-flex items-center gap-2"
          disabled={loading}
          onClick={() => void load()}
        >
          <FiRefreshCw className={loading ? 'animate-spin' : ''} aria-hidden="true" />
          Refresh
        </button>
      </div>



      {settings && (
        <div className="grid gap-3 p-5 sm:grid-cols-2 lg:grid-cols-4">
          {[
            ['App', settings.appId],
            ['Store', pretty(settings.appStore)],
            ['Subtype', pretty(settings.advertisingChannelSubType)],
            ['Optimization', pretty(settings.biddingStrategyGoalType)],
          ].map(([label, value]) => (
            <div key={label} className="rounded-xl border border-slate-200 bg-slate-50 p-3">
              <p className="text-[11px] font-semibold uppercase tracking-wide text-slate-400">
                {label}
              </p>
              <p className="mt-1 break-words text-sm font-semibold text-slate-800">{value}</p>
            </div>
          ))}
          <div className="sm:col-span-2 lg:col-span-4 rounded-xl border border-slate-200 p-3 text-xs leading-5 text-slate-500">
            {settings.conversionActionResources.length > 0
              ? `Optimizing toward ${settings.conversionActionResources.length} selected App conversion action(s).`
              : settings.advertisingChannelSubType === 'APP_CAMPAIGN_FOR_PRE_REGISTRATION'
                ? 'Google manages the pre-registration conversion action automatically.'
                : 'No selective App conversion action is configured.'}
            {' · '}
            {settings.locationResources.length} included location target(s),{' '}
            {settings.excludedLocationResources.length} exclusion(s), and{' '}
            {settings.languageResources.length} language target(s).
          </div>
        </div>
      )}

      {performance && (
        <>
          <div className="grid gap-3 border-t border-slate-100 p-5 sm:grid-cols-3 lg:grid-cols-6">
            {[
              ['Impressions', performance.impressions.toLocaleString()],
              ['Clicks', performance.clicks.toLocaleString()],
              ['Spend', money(performance.costMicros, currency)],
              ['Conversions', performance.conversions.toLocaleString()],
              ['All conversions', performance.allConversions.toLocaleString()],
              [
                'SKAN installs',
                performance.skAdNetworkInstalls.toLocaleString(),
              ],
            ].map(([label, value]) => (
              <div key={label}>
                <p className="text-[11px] font-semibold uppercase tracking-wide text-slate-400">
                  {label}
                </p>
                <p className="mt-1 text-sm font-bold text-slate-900">{value}</p>
              </div>
            ))}
          </div>

          <div className="border-t border-slate-100 px-5 py-5">
            <h3 className="text-sm font-bold text-slate-900">Creative asset performance</h3>
            {performance.assetPerformance.length === 0 ? (
              <p className="mt-3 text-xs text-slate-500">
                No App asset delivery was returned for this period.
              </p>
            ) : (
              <div className="mt-3 overflow-x-auto">
                <table className="min-w-full text-left text-xs">
                  <thead className="border-b border-slate-200 text-slate-500">
                    <tr>
                      <th className="px-2 py-2">Asset</th>
                      <th className="px-2 py-2 text-right">Impressions</th>
                      <th className="px-2 py-2 text-right">Clicks</th>
                    </tr>
                  </thead>
                  <tbody>
                    {performance.assetPerformance.map((asset) => (
                      <tr key={asset.assetResourceName} className="border-b border-slate-100">
                        <td className="px-2 py-2">
                          <p className="font-semibold text-slate-800">
                            {asset.assetName ?? 'Google App asset'}
                          </p>
                          <p className="mt-1 break-all text-[10px] text-slate-400">
                            {asset.assetResourceName}
                          </p>
                        </td>
                        <td className="px-2 py-2 text-right">
                          {asset.impressions.toLocaleString()}
                        </td>
                        <td className="px-2 py-2 text-right">
                          {asset.clicks.toLocaleString()}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        </>
      )}
    </section>
  )
}
