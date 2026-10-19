import { useSnackbarState } from '../feedback/useSnackbarState'
import { useEffect, useMemo, useState } from 'react'
import { FiRefreshCw } from 'react-icons/fi'
import { adCenterApi } from '../../features/ads/adCenterApi'
import type { GoogleProviderReport } from '../../features/ads/types'
import { apiErrorMessage } from '../../lib/api/apiClient'
import { TableSkeleton } from '../ui/Skeleton'

type Tab = 'LANDING' | 'PLACEMENT' | 'AUCTION' | 'CONVERSION' | 'CHANGES'

type Props = {
  companyId: string
  accountId: string
  from: string
  to: string
  currency?: string | null
}

const emptyReport: GoogleProviderReport = {
  landingPages: [],
  placements: [],
  auctionInsights: [],
  conversionAttribution: [],
  changeEvents: [],
}

const pretty = (value?: string | null) =>
  value
    ? value
        .replaceAll('_', ' ')
        .toLowerCase()
        .replace(/\b\w/g, (c) => c.toUpperCase())
    : '—'

const number = (value?: number | null, digits = 2) =>
  value == null
    ? '—'
    : new Intl.NumberFormat(undefined, { maximumFractionDigits: digits }).format(value)

const moneyMicros = (micros: number, currency?: string | null) => {
  const value = micros / 1_000_000
  if (!currency) return number(value)
  try {
    return new Intl.NumberFormat(undefined, {
      style: 'currency',
      currency,
      maximumFractionDigits: 2,
    }).format(value)
  } catch {
    return `${number(value)} ${currency}`
  }
}

const percent = (value?: number | null) => (value == null ? '—' : `${number(value * 100, 1)}%`)

export default function GoogleProviderReportingPanel({
  companyId,
  accountId,
  from,
  to,
  currency,
}: Props) {
  const [tab, setTab] = useState<Tab>('LANDING')
  const [report, setReport] = useState<GoogleProviderReport>(emptyReport)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useSnackbarState('', 'error')

  const load = async () => {
    setLoading(true)
    setError('')
    try {
      setReport(await adCenterApi.googleProviderReport(companyId, accountId, from, to))
    } catch (loadError) {
      setError(apiErrorMessage(loadError, 'Unable to load Google provider drill-down'))
      setReport(emptyReport)
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    void load()
  }, [companyId, accountId, from, to])

  const tabs = useMemo(
    () =>
      [
        ['LANDING', 'Landing pages', report.landingPages.length],
        ['PLACEMENT', 'Placements', report.placements.length],
        ['AUCTION', 'Auction Insights', report.auctionInsights.length],
        ['CONVERSION', 'Conversions', report.conversionAttribution.length],
        ['CHANGES', 'Change history', report.changeEvents.length],
      ] as const,
    [report],
  )

  return (
    <section className="panel mt-6 overflow-hidden">
      <div className="flex flex-wrap items-start justify-between gap-3 border-b border-slate-100 px-5 py-4">
        <div>
          <h2 className="text-base font-bold text-slate-950">Google provider drill-down</h2>
          <p className="mt-1 text-xs leading-5 text-slate-500">
            Provider-native reports that do not fit the normalized cross-provider layer.
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

      <div className="flex gap-2 overflow-x-auto border-b border-slate-100 px-5 py-3">
        {tabs.map(([value, label, count]) => (
          <button
            key={value}
            type="button"
            className={`shrink-0 rounded-xl px-3 py-2 text-xs font-semibold ${
              tab === value
                ? 'bg-slate-950 text-white'
                : 'bg-slate-100 text-slate-600 hover:bg-slate-200'
            }`}
            onClick={() => setTab(value)}
          >
            {label} · {count}
          </button>
        ))}
      </div>

      {error ? (
        <div className="m-5 rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
          {error}
        </div>
      ) : loading ? (
        <div className="p-4"><TableSkeleton rows={6} columns={6} /></div>
      ) : (
        <div className="overflow-x-auto">
          {tab === 'LANDING' && (
            <table className="w-full min-w-[900px] text-left text-sm">
              <thead className="bg-slate-50 text-xs uppercase text-slate-500">
                <tr>
                  <th className="px-5 py-3">Landing page</th>
                  <th className="px-5 py-3">Campaign / ad group</th>
                  <th className="px-5 py-3 text-right">Spend</th>
                  <th className="px-5 py-3 text-right">Impressions</th>
                  <th className="px-5 py-3 text-right">Clicks</th>
                  <th className="px-5 py-3 text-right">Conversions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {report.landingPages.map((row, index) => (
                  <tr key={`${row.url}-${index}`}>
                    <td className="max-w-sm break-all px-5 py-4 font-semibold text-slate-900">
                      {row.url}
                    </td>
                    <td className="px-5 py-4 text-slate-600">
                      {row.campaignName ?? row.campaignId ?? '—'}
                      <div className="text-xs text-slate-400">
                        {row.adGroupName ?? row.adGroupId ?? '—'}
                      </div>
                    </td>
                    <td className="px-5 py-4 text-right">
                      {moneyMicros(row.costMicros, currency)}
                    </td>
                    <td className="px-5 py-4 text-right">{number(row.impressions, 0)}</td>
                    <td className="px-5 py-4 text-right">{number(row.clicks, 0)}</td>
                    <td className="px-5 py-4 text-right">{number(row.conversions)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}

          {tab === 'PLACEMENT' && (
            <table className="w-full min-w-[1000px] text-left text-sm">
              <thead className="bg-slate-50 text-xs uppercase text-slate-500">
                <tr>
                  <th className="px-5 py-3">Placement</th>
                  <th className="px-5 py-3">Type / network</th>
                  <th className="px-5 py-3">Campaign</th>
                  <th className="px-5 py-3 text-right">Spend</th>
                  <th className="px-5 py-3 text-right">Impressions</th>
                  <th className="px-5 py-3 text-right">Clicks</th>
                  <th className="px-5 py-3 text-right">Conversions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {report.placements.map((row, index) => (
                  <tr key={`${row.placement}-${index}`}>
                    <td className="px-5 py-4">
                      <p className="font-semibold text-slate-900">
                        {row.displayName ?? row.placement}
                      </p>
                      <p className="max-w-sm break-all text-xs text-slate-400">
                        {row.targetUrl ?? row.placement}
                      </p>
                    </td>
                    <td className="px-5 py-4 text-slate-600">
                      {pretty(row.placementType)}
                      <div className="text-xs text-slate-400">{pretty(row.adNetworkType)}</div>
                    </td>
                    <td className="px-5 py-4 text-slate-600">
                      {row.campaignName ?? row.campaignId ?? '—'}
                    </td>
                    <td className="px-5 py-4 text-right">
                      {moneyMicros(row.costMicros, currency)}
                    </td>
                    <td className="px-5 py-4 text-right">{number(row.impressions, 0)}</td>
                    <td className="px-5 py-4 text-right">{number(row.clicks, 0)}</td>
                    <td className="px-5 py-4 text-right">{number(row.conversions)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}

          {tab === 'AUCTION' && (
            <table className="w-full min-w-[900px] text-left text-sm">
              <thead className="bg-slate-50 text-xs uppercase text-slate-500">
                <tr>
                  <th className="px-5 py-3">Domain</th>
                  <th className="px-5 py-3">Campaign</th>
                  <th className="px-5 py-3 text-right">Impression share</th>
                  <th className="px-5 py-3 text-right">Overlap</th>
                  <th className="px-5 py-3 text-right">Above rate</th>
                  <th className="px-5 py-3 text-right">Top</th>
                  <th className="px-5 py-3 text-right">Abs. top</th>
                  <th className="px-5 py-3 text-right">Outranking</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {report.auctionInsights.map((row, index) => (
                  <tr key={`${row.domain}-${row.campaignId ?? index}`}>
                    <td className="px-5 py-4 font-semibold text-slate-900">{row.domain}</td>
                    <td className="px-5 py-4 text-slate-600">
                      {row.campaignName ?? row.campaignId ?? '—'}
                    </td>
                    <td className="px-5 py-4 text-right">{percent(row.impressionShare)}</td>
                    <td className="px-5 py-4 text-right">{percent(row.overlapRate)}</td>
                    <td className="px-5 py-4 text-right">{percent(row.positionAboveRate)}</td>
                    <td className="px-5 py-4 text-right">{percent(row.topImpressionPercentage)}</td>
                    <td className="px-5 py-4 text-right">
                      {percent(row.absoluteTopImpressionPercentage)}
                    </td>
                    <td className="px-5 py-4 text-right">{percent(row.outrankingShare)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}

          {tab === 'CONVERSION' && (
            <table className="w-full min-w-[900px] text-left text-sm">
              <thead className="bg-slate-50 text-xs uppercase text-slate-500">
                <tr>
                  <th className="px-5 py-3">Conversion action</th>
                  <th className="px-5 py-3">Campaign</th>
                  <th className="px-5 py-3">Category / source</th>
                  <th className="px-5 py-3 text-right">Conversions</th>
                  <th className="px-5 py-3 text-right">Value</th>
                  <th className="px-5 py-3 text-right">All conv.</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {report.conversionAttribution.map((row, index) => (
                  <tr key={`${row.conversionActionName}-${row.campaignId ?? index}`}>
                    <td className="px-5 py-4 font-semibold text-slate-900">
                      {row.conversionActionName}
                    </td>
                    <td className="px-5 py-4 text-slate-600">
                      {row.campaignName ?? row.campaignId ?? '—'}
                    </td>
                    <td className="px-5 py-4 text-slate-600">
                      {pretty(row.conversionActionCategory)}
                      <div className="text-xs text-slate-400">
                        {pretty(row.externalConversionSource)}
                      </div>
                    </td>
                    <td className="px-5 py-4 text-right">{number(row.conversions)}</td>
                    <td className="px-5 py-4 text-right">{number(row.conversionValue)}</td>
                    <td className="px-5 py-4 text-right">{number(row.allConversions)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}

          {tab === 'CHANGES' && (
            <table className="w-full min-w-[900px] text-left text-sm">
              <thead className="bg-slate-50 text-xs uppercase text-slate-500">
                <tr>
                  <th className="px-5 py-3">When</th>
                  <th className="px-5 py-3">Change</th>
                  <th className="px-5 py-3">Resource</th>
                  <th className="px-5 py-3">Changed fields</th>
                  <th className="px-5 py-3">Actor / client</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {report.changeEvents.map((row, index) => (
                  <tr key={`${row.changeDateTime ?? 'change'}-${index}`}>
                    <td className="px-5 py-4 text-slate-600">
                      {row.changeDateTime
                        ? new Date(row.changeDateTime.replace(' ', 'T')).toLocaleString()
                        : '—'}
                    </td>
                    <td className="px-5 py-4 font-semibold text-slate-900">
                      {pretty(row.operation)} · {pretty(row.resourceType)}
                    </td>
                    <td className="max-w-xs break-all px-5 py-4 text-slate-600">
                      {row.changeResourceName ?? '—'}
                    </td>
                    <td className="px-5 py-4 text-xs text-slate-500">
                      {row.changedFields.join(', ') || '—'}
                    </td>
                    <td className="px-5 py-4 text-slate-600">
                      {row.userEmail ?? 'Google / system'}
                      <div className="text-xs text-slate-400">{pretty(row.clientType)}</div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}

          {((tab === 'LANDING' && report.landingPages.length === 0) ||
            (tab === 'PLACEMENT' && report.placements.length === 0) ||
            (tab === 'AUCTION' && report.auctionInsights.length === 0) ||
            (tab === 'CONVERSION' && report.conversionAttribution.length === 0) ||
            (tab === 'CHANGES' && report.changeEvents.length === 0)) && (
            <div className="p-10 text-center text-sm text-slate-500">
              No rows were returned for this report and date range.
            </div>
          )}
        </div>
      )}
    </section>
  )
}
