import { useSnackbarState } from '../feedback/useSnackbarState'
import { useEffect, useMemo, useState } from 'react'
import { FiBarChart2, FiRefreshCw } from 'react-icons/fi'
import { adCenterApi } from '../../features/ads/adCenterApi'
import type { ReachlyoCampaignAnalytics } from '../../features/ads/types'
import { apiErrorMessage } from '../../lib/api/apiClient'
import { PageSkeleton } from '../ui/Skeleton'

type Props = {
  companyId: string
  campaignId: string
  canSync: boolean
}

const isoDate = (date: Date) => {
  const offset = date.getTimezoneOffset() * 60_000
  return new Date(date.getTime() - offset).toISOString().slice(0, 10)
}

const defaultRange = () => {
  const to = new Date()
  const from = new Date(to)
  from.setDate(from.getDate() - 29)
  return { from: isoDate(from), to: isoDate(to) }
}

const number = (value: number | null | undefined, digits = 0) =>
  new Intl.NumberFormat(undefined, {
    maximumFractionDigits: digits,
  }).format(value ?? 0)

const pretty = (value: string | null | undefined) =>
  value
    ? value
        .replaceAll('_', ' ')
        .toLowerCase()
        .replace(/\b\w/g, (character) => character.toUpperCase())
    : 'Unknown'

export default function ReachlyoCampaignAnalyticsPanel({
  companyId,
  campaignId,
  canSync,
}: Props) {
  const initial = useMemo(() => defaultRange(), [])
  const [from, setFrom] = useState(initial.from)
  const [to, setTo] = useState(initial.to)
  const [analytics, setAnalytics] = useState<ReachlyoCampaignAnalytics | null>(null)
  const [loading, setLoading] = useState(true)
  const [syncing, setSyncing] = useState(false)
  const [, setError] = useSnackbarState('', 'error')
  const [, setNotice] = useSnackbarState('', 'success')
  const [syncFailures, setSyncFailures] = useState<
    Array<{ provider: string; adAccountId: string; message: string }>
  >([])

  const money = (value: number | null | undefined) => {
    try {
      return new Intl.NumberFormat(undefined, {
        style: 'currency',
        currency: analytics?.currency || 'INR',
        maximumFractionDigits: 2,
      }).format(value ?? 0)
    } catch {
      return number(value, 2)
    }
  }

  const load = async () => {
    setLoading(true)
    setError('')
    setNotice('')
    setSyncFailures([])
    try {
      setAnalytics(
        await adCenterApi.reachlyoCampaignAnalytics(
          companyId,
          campaignId,
          from,
          to,
        ),
      )
    } catch (loadError) {
      setError(apiErrorMessage(loadError, 'Unable to load campaign analytics'))
    } finally {
      setLoading(false)
    }
  }

  const sync = async () => {
    setSyncing(true)
    setError('')
    setNotice('')
    setSyncFailures([])
    try {
      const result = await adCenterApi.syncReachlyoCampaignAnalytics(
        companyId,
        campaignId,
        from,
        to,
      )
      setAnalytics(result.analytics)
      if (result.failures.length > 0) {
        setSyncFailures(result.failures)
        setNotice(
          `Synced ${result.synchronizedAccounts} account(s). ${result.failures.length} provider account(s) could not refresh; stored analytics are still shown.`,
        )
      } else {
        setNotice(
          `Analytics refreshed across ${result.synchronizedAccounts} deployed account(s).`,
        )
      }
    } catch (syncError) {
      setError(apiErrorMessage(syncError, 'Unable to refresh campaign analytics'))
    } finally {
      setSyncing(false)
    }
  }

  useEffect(() => {
    void load()
  }, [companyId, campaignId])

  const maxDailySpend = Math.max(
    0,
    ...(analytics?.daily.map((point) => point.spend) ?? []),
  )

  return (
    <section className="panel overflow-hidden">
      <div className="flex flex-wrap items-start justify-between gap-4 border-b border-slate-100 px-5 py-4">
        <div>
          <div className="flex items-center gap-2">
            <FiBarChart2 className="text-indigo-600" aria-hidden="true" />
            <h3 className="text-base font-bold text-slate-950">
              Campaign performance
            </h3>
          </div>
          <p className="mt-1 max-w-2xl text-xs leading-5 text-slate-500">
            Unified Google + Meta reporting for provider campaigns created by this
            campaign. Reach is intentionally not combined across providers because
            users cannot be de-duplicated cross-platform.
          </p>
        </div>
        <div className="flex flex-wrap items-end gap-2">
          <label className="text-[11px] font-semibold text-slate-600">
            From
            <input
              type="date"
              className="form-input mt-1 w-36"
              value={from}
              max={to}
              onChange={(event) => setFrom(event.target.value)}
            />
          </label>
          <label className="text-[11px] font-semibold text-slate-600">
            To
            <input
              type="date"
              className="form-input mt-1 w-36"
              value={to}
              min={from}
              max={isoDate(new Date())}
              onChange={(event) => setTo(event.target.value)}
            />
          </label>
          <button
            type="button"
            className="secondary-button"
            disabled={loading || !from || !to}
            onClick={() => void load()}
          >
            Apply
          </button>
          {canSync && (
            <button
              type="button"
              className="primary-button inline-flex items-center gap-2"
              disabled={syncing || loading || !from || !to}
              onClick={() => void sync()}
            >
              <FiRefreshCw
                className={syncing ? 'animate-spin' : ''}
                aria-hidden="true"
              />
              {syncing ? 'Refreshing…' : 'Refresh providers'}
            </button>
          )}
        </div>
      </div>




      {syncFailures.length > 0 && (
        <div className="border-b border-amber-100 bg-amber-50 px-5 py-3">
          <p className="text-xs font-semibold text-amber-900">
            Provider refresh issues
          </p>
          <ul className="mt-1 grid gap-1 text-xs text-amber-800">
            {syncFailures.map((failure) => (
              <li key={failure.provider + '-' + failure.adAccountId}>
                {pretty(failure.provider)} account {failure.adAccountId}: {failure.message}
              </li>
            ))}
          </ul>
        </div>
      )}

      {loading ? (
        <div className="p-5"><PageSkeleton /></div>
      ) : analytics ? (
        <div className="grid gap-5 p-5">
          <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 xl:grid-cols-6">
            <Metric label="Spend" value={money(analytics.summary.spend)} />
            <Metric
              label="Conversions"
              value={number(analytics.summary.conversions, 2)}
            />
            <Metric
              label="Conversion value"
              value={money(analytics.summary.conversionValue)}
            />
            <Metric
              label="CPA"
              value={
                analytics.summary.cpa == null
                  ? '—'
                  : money(analytics.summary.cpa)
              }
            />
            <Metric
              label="ROAS"
              value={
                analytics.summary.roas == null
                  ? '—'
                  : number(analytics.summary.roas, 2) + '×'
              }
            />
            <Metric label="Clicks" value={number(analytics.summary.clicks)} />
          </div>

          <div className="flex flex-wrap items-center gap-2 text-[11px] text-slate-500">
            <span>
              {analytics.exactRangeAvailable
                ? 'Exact provider range rollups available'
                : 'Using daily rollup fallback for at least one deployment'}
            </span>
            {analytics.lastSyncedAt && (
              <>
                <span aria-hidden="true">•</span>
                <span>
                  Last synced {new Date(analytics.lastSyncedAt).toLocaleString()}
                </span>
              </>
            )}
          </div>

          <div>
            <h4 className="text-sm font-bold text-slate-900">Provider / channel split</h4>
            {analytics.deployments.length === 0 ? (
              <div className="mt-3 rounded-xl border border-dashed border-slate-200 p-6 text-center text-sm text-slate-500">
                No provider deployments exist yet.
              </div>
            ) : (
              <div className="mt-3 overflow-x-auto rounded-xl border border-slate-200">
                <table className="min-w-[860px] w-full text-left text-xs">
                  <thead className="bg-slate-50 text-slate-500">
                    <tr>
                      <th className="px-4 py-3 font-semibold">Channel</th>
                      <th className="px-4 py-3 font-semibold">Status</th>
                      <th className="px-4 py-3 text-right font-semibold">Spend</th>
                      <th className="px-4 py-3 text-right font-semibold">Share</th>
                      <th className="px-4 py-3 text-right font-semibold">Impressions</th>
                      <th className="px-4 py-3 text-right font-semibold">Clicks</th>
                      <th className="px-4 py-3 text-right font-semibold">Conversions</th>
                      <th className="px-4 py-3 text-right font-semibold">CPA</th>
                      <th className="px-4 py-3 text-right font-semibold">ROAS</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-100">
                    {analytics.deployments.map((deployment) => (
                      <tr key={deployment.deploymentId}>
                        <td className="px-4 py-3">
                          <p className="font-semibold text-slate-800">
                            {pretty(deployment.channelType ?? deployment.provider)}
                          </p>
                          <p className="mt-0.5 text-[11px] text-slate-400">
                            {pretty(deployment.provider)}
                          </p>
                        </td>
                        <td className="px-4 py-3 text-slate-600">
                          {pretty(deployment.providerStatus ?? deployment.deploymentStatus)}
                        </td>
                        <td className="px-4 py-3 text-right font-semibold text-slate-800">
                          {money(deployment.summary.spend)}
                        </td>
                        <td className="px-4 py-3 text-right text-slate-600">
                          {number(deployment.spendSharePercent, 1)}%
                        </td>
                        <td className="px-4 py-3 text-right text-slate-600">
                          {number(deployment.summary.impressions)}
                        </td>
                        <td className="px-4 py-3 text-right text-slate-600">
                          {number(deployment.summary.clicks)}
                        </td>
                        <td className="px-4 py-3 text-right text-slate-600">
                          {number(deployment.summary.conversions, 2)}
                        </td>
                        <td className="px-4 py-3 text-right text-slate-600">
                          {deployment.summary.cpa == null
                            ? '—'
                            : money(deployment.summary.cpa)}
                        </td>
                        <td className="px-4 py-3 text-right text-slate-600">
                          {deployment.summary.roas == null
                            ? '—'
                            : number(deployment.summary.roas, 2) + '×'}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>

          <div>
            <h4 className="text-sm font-bold text-slate-900">Daily trend</h4>
            {analytics.daily.length === 0 ? (
              <div className="mt-3 rounded-xl border border-dashed border-slate-200 p-6 text-center text-sm text-slate-500">
                No campaign-level insight rows are stored for this date range yet.
                Refresh providers after the campaign begins delivering.
              </div>
            ) : (
              <div className="mt-3 grid gap-2">
                {analytics.daily.map((point) => {
                  const width =
                    maxDailySpend <= 0
                      ? 0
                      : Math.max(3, (point.spend / maxDailySpend) * 100)
                  return (
                    <div
                      key={point.date}
                      className="grid gap-2 rounded-xl border border-slate-100 px-3 py-2 sm:grid-cols-[96px_minmax(120px,1fr)_110px_90px]"
                    >
                      <span className="text-xs font-semibold text-slate-600">
                        {new Date(point.date + 'T00:00:00').toLocaleDateString()}
                      </span>
                      <div className="self-center">
                        <div className="h-2 overflow-hidden rounded-full bg-slate-100">
                          <div
                            className="h-full rounded-full bg-indigo-500"
                            style={{ width: `${width}%` }}
                          />
                        </div>
                      </div>
                      <span className="text-xs font-semibold text-slate-700 sm:text-right">
                        {money(point.spend)}
                      </span>
                      <span className="text-xs text-slate-500 sm:text-right">
                        {number(point.conversions, 2)} conv.
                      </span>
                    </div>
                  )
                })}
              </div>
            )}
          </div>
        </div>
      ) : null}
    </section>
  )
}

function Metric({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-xl border border-slate-200 bg-white p-3">
      <p className="text-[11px] font-semibold uppercase tracking-wide text-slate-400">
        {label}
      </p>
      <p className="mt-1 text-lg font-bold text-slate-900">{value}</p>
    </div>
  )
}
