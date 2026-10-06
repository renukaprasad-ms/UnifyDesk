import { useEffect, useMemo, useState } from 'react'
import { FiActivity, FiBarChart2, FiClock, FiRefreshCw, FiTrendingUp } from 'react-icons/fi'
import { Link } from 'react-router'
import AdFeedbackAlert from '../../components/ads/AdFeedbackAlert'
import AdOptimizationWorkspace from '../../components/ads/AdOptimizationWorkspace'
import GoogleExperimentsPanel from '../../components/ads/GoogleExperimentsPanel'
import GoogleProviderReportingPanel from '../../components/ads/GoogleProviderReportingPanel'
import { showGlobalSnackbar } from '../../components/feedback/SnackbarProvider'
import { adCenterApi } from '../../features/ads/adCenterApi'
import type {
  AdAccount,
  AdAnalytics as AdAnalyticsData,
  AdAnalyticsBreakdown,
  AdAnalyticsBreakdownRow,
  AdAnalyticsDailyPoint,
  AdInsightBreakdownType,
  AdInsightLevel,
} from '../../features/ads/types'
import { PERMISSIONS } from '../../features/iam/permissions'
import { useIam } from '../../features/iam/useIam'
import { apiErrorMessage } from '../../lib/api/apiClient'
import { useAppSelector } from '../../store/hooks'
import { selectSelectedCompany } from '../../store/workspace/workspaceSlice'
import { PageSkeleton, TableSkeleton } from '../../components/ui/Skeleton'

const isoDate = (value: Date) => {
  const local = new Date(value.getTime() - value.getTimezoneOffset() * 60_000)
  return local.toISOString().slice(0, 10)
}

const rangeForDays = (days: number) => {
  const to = new Date()
  const from = new Date()
  from.setDate(from.getDate() - (days - 1))
  return { from: isoDate(from), to: isoDate(to) }
}

const emptyAnalytics: AdAnalyticsData = {
  summary: {
    spend: 0,
    impressions: 0,
    reach: 0,
    clicks: 0,
    conversions: 0,
    conversionValue: 0,
    leads: 0,
    purchases: 0,
  },
  daily: [],
  entities: [],
  exactRangeAvailable: false,
  lastSyncedAt: null,
}

const formatCompact = (value: number) =>
  new Intl.NumberFormat(undefined, {
    notation: value >= 10000 ? 'compact' : 'standard',
    maximumFractionDigits: 1,
  }).format(value || 0)

const formatNumber = (value?: number | null, digits = 2) =>
  value == null
    ? '—'
    : new Intl.NumberFormat(undefined, {
        maximumFractionDigits: digits,
      }).format(value)

const formatCurrency = (value: number, currency?: string | null) => {
  if (!currency) return formatNumber(value)
  try {
    return new Intl.NumberFormat(undefined, {
      style: 'currency',
      currency,
      maximumFractionDigits: 2,
    }).format(value)
  } catch {
    return `${formatNumber(value)} ${currency}`
  }
}

const entityLink = (level: AdInsightLevel, id?: string | null) => {
  if (!id) return null
  if (level === 'CAMPAIGN') return `/ad-center/campaigns/${id}`
  if (level === 'AD_SET') return `/ad-center/ad-sets/${id}`
  return `/ad-center/ads/${id}`
}

const breakdownOptions: Array<{ value: AdInsightBreakdownType; label: string; group: string }> = [
  { value: 'AGE', label: 'Age', group: 'Demographics' },
  { value: 'GENDER', label: 'Gender', group: 'Demographics' },
  { value: 'COUNTRY', label: 'Country', group: 'Geography' },
  { value: 'REGION', label: 'Region', group: 'Geography' },
  { value: 'PUBLISHER_PLATFORM', label: 'Publisher', group: 'Delivery' },
  { value: 'PLATFORM_POSITION', label: 'Placement', group: 'Delivery' },
  { value: 'IMPRESSION_DEVICE', label: 'Impression device', group: 'Device' },
  { value: 'DEVICE_PLATFORM', label: 'Device platform', group: 'Device' },
  { value: 'CONVERSION_DESTINATION', label: 'Conversion destination', group: 'Conversion' },
  { value: 'ACTION_TYPE', label: 'Action type', group: 'Conversion' },
  { value: 'ACTION_DEVICE', label: 'Action device', group: 'Conversion' },
]

const emptyBreakdown = (type: AdInsightBreakdownType): AdAnalyticsBreakdown => ({
  breakdown: type,
  rows: [],
  lastSyncedAt: null,
})

const prettyDimension = (value: string) =>
  value.replaceAll('_', ' ').replace(/\b\w/g, (character) => character.toUpperCase())

const metricRoas = (row: AdAnalyticsBreakdownRow) =>
  row.spend > 0 ? row.conversionValue / row.spend : null

const metricCpa = (row: AdAnalyticsBreakdownRow) =>
  row.conversions > 0 ? row.spend / row.conversions : null

function TrendChart({
  points,
  currency,
}: {
  points: AdAnalyticsDailyPoint[]
  currency?: string | null
}) {
  if (points.length === 0) {
    return (
      <div className="flex h-64 items-center justify-center text-sm text-slate-400">
        Sync advertising analytics to populate the daily trend.
      </div>
    )
  }

  const width = 1000
  const height = 240
  const paddingX = 24
  const paddingY = 24
  const plotWidth = width - paddingX * 2
  const plotHeight = height - paddingY * 2
  const maxSpend = Math.max(...points.map((point) => point.spend), 1)
  const maxConversions = Math.max(...points.map((point) => point.conversions), 1)
  const x = (index: number) =>
    paddingX + (points.length === 1 ? plotWidth / 2 : (index / (points.length - 1)) * plotWidth)
  const ySpend = (value: number) => paddingY + plotHeight - (value / maxSpend) * plotHeight
  const yConversions = (value: number) =>
    paddingY + plotHeight - (value / maxConversions) * plotHeight

  const spendPath = points
    .map((point, index) => `${index === 0 ? 'M' : 'L'} ${x(index)} ${ySpend(point.spend)}`)
    .join(' ')
  const conversionPath = points
    .map(
      (point, index) => `${index === 0 ? 'M' : 'L'} ${x(index)} ${yConversions(point.conversions)}`,
    )
    .join(' ')

  return (
    <div>
      <div className="flex flex-wrap items-center justify-between gap-3 px-1 pb-4">
        <div className="flex flex-wrap gap-4 text-xs font-semibold text-slate-500">
          <span className="inline-flex items-center gap-2">
            <span className="h-2.5 w-2.5 rounded-full bg-indigo-500" />
            Spend
          </span>
          <span className="inline-flex items-center gap-2">
            <span className="h-2.5 w-2.5 rounded-full bg-emerald-500" />
            Conversions
          </span>
        </div>
        <span className="text-xs text-slate-400">
          Peak spend {formatCurrency(maxSpend, currency)}
        </span>
      </div>

      <div className="overflow-hidden rounded-xl border border-slate-100 bg-slate-50/60">
        <svg
          role="img"
          aria-label="Daily advertising spend and conversion trend"
          viewBox={`0 0 ${width} ${height}`}
          className="h-64 w-full"
          preserveAspectRatio="none"
        >
          {[0.25, 0.5, 0.75].map((ratio) => (
            <line
              key={ratio}
              x1={paddingX}
              x2={width - paddingX}
              y1={paddingY + plotHeight * ratio}
              y2={paddingY + plotHeight * ratio}
              stroke="currentColor"
              className="text-slate-200"
              strokeWidth="1"
            />
          ))}
          <path
            d={spendPath}
            fill="none"
            stroke="currentColor"
            className="text-indigo-500"
            strokeWidth="4"
            vectorEffect="non-scaling-stroke"
          />
          <path
            d={conversionPath}
            fill="none"
            stroke="currentColor"
            className="text-emerald-500"
            strokeWidth="3"
            strokeDasharray="8 6"
            vectorEffect="non-scaling-stroke"
          />
        </svg>
      </div>

      <div className="mt-2 flex justify-between text-[11px] font-medium text-slate-400">
        <span>{points[0]?.date}</span>
        <span>{points.at(-1)?.date}</span>
      </div>
    </div>
  )
}

function BreakdownPanel({
  breakdown,
  type,
  loading,
  currency,
  onSelect,
}: {
  breakdown: AdAnalyticsBreakdown
  type: AdInsightBreakdownType
  loading: boolean
  currency?: string | null
  onSelect: (type: AdInsightBreakdownType) => void
}) {
  const isActionBreakdown = type === 'ACTION_DEVICE' || type === 'ACTION_TYPE'
  const maxPrimary = Math.max(
    ...breakdown.rows.map((row) => (isActionBreakdown ? row.actionValue : row.spend)),
    1,
  )

  return (
    <div className="panel mt-6 overflow-hidden">
      <div className="border-b border-slate-100 px-5 py-4">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div>
            <div className="flex items-center gap-2">
              <FiActivity className="text-indigo-500" aria-hidden="true" />
              <h2 className="text-base font-bold text-slate-950">Audience & delivery breakdowns</h2>
            </div>
            <p className="mt-1 text-xs text-slate-500">
              Inspect who saw the ads, where delivery happened, and which devices produced actions.
            </p>
          </div>
          {breakdown.lastSyncedAt && (
            <span className="text-xs text-slate-400">
              Synced {new Date(breakdown.lastSyncedAt).toLocaleString()}
            </span>
          )}
        </div>

        <div className="mt-4 flex gap-2 overflow-x-auto pb-1">
          {breakdownOptions.map((option) => (
            <button
              key={option.value}
              type="button"
              className={`shrink-0 rounded-xl px-3 py-2 text-xs font-semibold transition ${
                type === option.value
                  ? 'bg-slate-950 text-white'
                  : 'bg-slate-100 text-slate-600 hover:bg-slate-200'
              }`}
              title={option.group}
              onClick={() => onSelect(option.value)}
            >
              {option.label}
            </button>
          ))}
        </div>
      </div>

      {loading ? (
        <div className="p-4"><TableSkeleton rows={6} columns={5} /></div>
      ) : breakdown.rows.length === 0 ? (
        <div className="p-10 text-center">
          <FiBarChart2 className="mx-auto text-3xl text-slate-300" aria-hidden="true" />
          <h3 className="mt-3 text-base font-bold text-slate-950">
            No {prettyDimension(type).toLowerCase()} data
          </h3>
          <p className="mt-2 text-sm text-slate-500">
            Sync analytics for this date range. The selected provider may not return every breakdown
            for every account or campaign mix.
          </p>
        </div>
      ) : isActionBreakdown ? (
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm">
            <thead className="bg-slate-50 text-xs font-semibold uppercase tracking-wide text-slate-500">
              <tr>
                <th className="px-5 py-3">{type === 'ACTION_DEVICE' ? 'Device' : 'Action type'}</th>
                {type === 'ACTION_DEVICE' && <th className="px-5 py-3">Action</th>}
                <th className="px-5 py-3 text-right">Count</th>
                <th className="px-5 py-3 text-right">Attributed value</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {breakdown.rows.map((row, index) => (
                <tr key={`${row.dimension}-${row.actionType ?? 'action'}-${index}`}>
                  <td className="px-5 py-4">
                    <div className="min-w-44">
                      <div className="flex items-center justify-between gap-3">
                        <span className="font-semibold text-slate-950">
                          {prettyDimension(row.dimension)}
                        </span>
                        <span className="text-xs text-slate-400">
                          {formatNumber((row.actionValue / maxPrimary) * 100, 1)}%
                        </span>
                      </div>
                      <div className="mt-2 h-1.5 overflow-hidden rounded-full bg-slate-100">
                        <div
                          className="h-full rounded-full bg-indigo-500"
                          style={{ width: `${Math.max(2, (row.actionValue / maxPrimary) * 100)}%` }}
                        />
                      </div>
                    </div>
                  </td>
                  {type === 'ACTION_DEVICE' && (
                    <td className="px-5 py-4 text-slate-600">
                      {prettyDimension(row.actionType ?? 'Unknown')}
                    </td>
                  )}
                  <td className="px-5 py-4 text-right font-semibold text-slate-700">
                    {formatNumber(row.actionValue)}
                  </td>
                  <td className="px-5 py-4 text-right text-slate-600">
                    {row.conversionValue > 0
                      ? formatCurrency(row.conversionValue, row.currency ?? currency)
                      : '—'}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : (
        <>
          <div className="hidden overflow-x-auto lg:block">
            <table className="w-full text-left text-sm">
              <thead className="bg-slate-50 text-xs font-semibold uppercase tracking-wide text-slate-500">
                <tr>
                  <th className="px-5 py-3">Dimension</th>
                  <th className="px-5 py-3 text-right">Spend</th>
                  <th className="px-5 py-3 text-right">Impressions</th>
                  <th className="px-5 py-3 text-right">Reach</th>
                  <th className="px-5 py-3 text-right">Clicks</th>
                  <th className="px-5 py-3 text-right">CTR</th>
                  <th className="px-5 py-3 text-right">Conversions</th>
                  <th className="px-5 py-3 text-right">CPA</th>
                  <th className="px-5 py-3 text-right">ROAS</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {breakdown.rows.map((row, index) => {
                  const cpa = metricCpa(row)
                  const roas = metricRoas(row)
                  return (
                    <tr key={`${row.dimension}-${index}`}>
                      <td className="px-5 py-4">
                        <div className="min-w-48">
                          <div className="flex items-center justify-between gap-3">
                            <span className="font-semibold text-slate-950">
                              {prettyDimension(row.dimension)}
                            </span>
                            <span className="text-xs text-slate-400">
                              {formatNumber((row.spend / maxPrimary) * 100, 1)}%
                            </span>
                          </div>
                          <div className="mt-2 h-1.5 overflow-hidden rounded-full bg-slate-100">
                            <div
                              className="h-full rounded-full bg-indigo-500"
                              style={{ width: `${Math.max(2, (row.spend / maxPrimary) * 100)}%` }}
                            />
                          </div>
                        </div>
                      </td>
                      <td className="px-5 py-4 text-right font-medium text-slate-700">
                        {formatCurrency(row.spend, row.currency ?? currency)}
                      </td>
                      <td className="px-5 py-4 text-right text-slate-600">
                        {formatCompact(row.impressions)}
                      </td>
                      <td className="px-5 py-4 text-right text-slate-600">
                        {formatCompact(row.reach)}
                      </td>
                      <td className="px-5 py-4 text-right text-slate-600">
                        {formatCompact(row.clicks)}
                      </td>
                      <td className="px-5 py-4 text-right text-slate-600">
                        {row.ctr == null ? '—' : `${formatNumber(row.ctr)}%`}
                      </td>
                      <td className="px-5 py-4 text-right text-slate-600">
                        {formatNumber(row.conversions)}
                      </td>
                      <td className="px-5 py-4 text-right text-slate-600">
                        {cpa == null ? '—' : formatCurrency(cpa, row.currency ?? currency)}
                      </td>
                      <td className="px-5 py-4 text-right font-semibold text-slate-700">
                        {roas == null ? '—' : `${formatNumber(roas)}×`}
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>

          <div className="divide-y divide-slate-100 lg:hidden">
            {breakdown.rows.map((row, index) => (
              <div key={`${row.dimension}-mobile-${index}`} className="p-5">
                <div className="flex items-start justify-between gap-3">
                  <div>
                    <p className="font-semibold text-slate-950">{prettyDimension(row.dimension)}</p>
                    <p className="mt-1 text-xs text-slate-400">
                      {formatCompact(row.impressions)} impressions · {formatCompact(row.reach)}{' '}
                      reach
                    </p>
                  </div>
                  <p className="font-bold text-slate-950">
                    {formatCurrency(row.spend, row.currency ?? currency)}
                  </p>
                </div>
                <div className="mt-4 grid grid-cols-3 gap-3 text-xs">
                  <div>
                    <p className="text-slate-400">Clicks</p>
                    <p className="mt-1 font-semibold text-slate-700">{formatCompact(row.clicks)}</p>
                  </div>
                  <div>
                    <p className="text-slate-400">Conversions</p>
                    <p className="mt-1 font-semibold text-slate-700">
                      {formatNumber(row.conversions)}
                    </p>
                  </div>
                  <div>
                    <p className="text-slate-400">ROAS</p>
                    <p className="mt-1 font-semibold text-slate-700">
                      {metricRoas(row) == null ? '—' : `${formatNumber(metricRoas(row))}×`}
                    </p>
                  </div>
                </div>
              </div>
            ))}
          </div>
        </>
      )}
    </div>
  )
}

export default function AdAnalytics() {
  const selectedCompany = useAppSelector(selectSelectedCompany)
  const { can } = useIam()
  const initialRange = useMemo(() => rangeForDays(30), [])

  const [accounts, setAccounts] = useState<AdAccount[]>([])
  const [selectedAccountId, setSelectedAccountId] = useState('')
  const [level, setLevel] = useState<AdInsightLevel>('CAMPAIGN')
  const [breakdownType, setBreakdownType] = useState<AdInsightBreakdownType>('AGE')
  const [breakdown, setBreakdown] = useState<AdAnalyticsBreakdown>(emptyBreakdown('AGE'))
  const [breakdownLoading, setBreakdownLoading] = useState(false)
  const [from, setFrom] = useState(initialRange.from)
  const [to, setTo] = useState(initialRange.to)
  const [analytics, setAnalytics] = useState<AdAnalyticsData>(emptyAnalytics)
  const [loading, setLoading] = useState(true)
  const [syncing, setSyncing] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')

  const managedAccounts = useMemo(
    () => accounts.filter((account) => account.managed && account.accessible),
    [accounts],
  )

  const selectedAccount =
    managedAccounts.find((account) => account.id === selectedAccountId) ??
    managedAccounts.find((account) => account.primaryAccount) ??
    managedAccounts[0] ??
    null

  const loadAnalytics = async (
    accountId: string,
    rangeFrom = from,
    rangeTo = to,
    entityLevel = level,
  ) => {
    if (!selectedCompany) return
    const data = await adCenterApi.analytics(
      selectedCompany.companyId,
      accountId,
      rangeFrom,
      rangeTo,
      entityLevel,
    )
    setAnalytics(data)
  }

  const loadBreakdown = async (
    accountId: string,
    rangeFrom = from,
    rangeTo = to,
    type = breakdownType,
  ) => {
    if (!selectedCompany) return
    const data = await adCenterApi.analyticsBreakdown(
      selectedCompany.companyId,
      accountId,
      rangeFrom,
      rangeTo,
      type,
    )
    setBreakdown(data)
  }

  const load = async () => {
    if (!selectedCompany) return
    setLoading(true)
    setError('')
    try {
      const accountData = await adCenterApi.accounts(selectedCompany.companyId)
      setAccounts(accountData)
      const eligible = accountData.filter((account) => account.managed && account.accessible)
      const preferred = eligible.find((account) => account.primaryAccount) ?? eligible[0]
      if (preferred) {
        setSelectedAccountId(preferred.id)
        await Promise.all([loadAnalytics(preferred.id), loadBreakdown(preferred.id)])
      } else {
        setAnalytics(emptyAnalytics)
        setBreakdown(emptyBreakdown(breakdownType))
      }
    } catch (loadError) {
      setError(apiErrorMessage(loadError, 'Unable to load advertising analytics'))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    void load()
  }, [selectedCompany?.companyId])

  const refreshStored = async (
    accountId = selectedAccount?.id,
    rangeFrom = from,
    rangeTo = to,
    entityLevel = level,
  ) => {
    if (!accountId) return
    setLoading(true)
    setError('')
    try {
      await Promise.all([
        loadAnalytics(accountId, rangeFrom, rangeTo, entityLevel),
        loadBreakdown(accountId, rangeFrom, rangeTo, breakdownType),
      ])
    } catch (loadError) {
      setError(apiErrorMessage(loadError, 'Unable to load advertising analytics'))
    } finally {
      setLoading(false)
    }
  }

  const chooseAccount = async (accountId: string) => {
    setSelectedAccountId(accountId)
    setNotice('')
    await refreshStored(accountId)
  }

  const chooseLevel = async (next: AdInsightLevel) => {
    setLevel(next)
    await refreshStored(selectedAccount?.id, from, to, next)
  }

  const chooseBreakdown = async (next: AdInsightBreakdownType) => {
    setBreakdownType(next)
    if (!selectedAccount) {
      setBreakdown(emptyBreakdown(next))
      return
    }
    setBreakdownLoading(true)
    setError('')
    try {
      await loadBreakdown(selectedAccount.id, from, to, next)
    } catch (loadError) {
      setError(apiErrorMessage(loadError, 'Unable to load analytics breakdown'))
      setBreakdown(emptyBreakdown(next))
    } finally {
      setBreakdownLoading(false)
    }
  }

  const applyPreset = async (days: number) => {
    const range = rangeForDays(days)
    setFrom(range.from)
    setTo(range.to)
    setNotice('')
    await refreshStored(selectedAccount?.id, range.from, range.to)
  }

  const sync = async () => {
    if (!selectedCompany || !selectedAccount) return
    if (!from || !to || to < from) {
      const message = 'Choose a valid analytics date range.'
      setError(message)
      showGlobalSnackbar(message, { variant: 'warning', title: 'Check date range' })
      return
    }

    setSyncing(true)
    setError('')
    setNotice('')
    try {
      if (selectedAccount.provider === 'GOOGLE_ADS') {
        const [result, breakdownResult] = await Promise.all([
          adCenterApi.syncGoogleInsights(selectedCompany.companyId, selectedAccount.id, from, to),
          adCenterApi.syncGoogleInsightBreakdowns(
            selectedCompany.companyId,
            selectedAccount.id,
            from,
            to,
          ),
        ])
        await Promise.all([loadAnalytics(selectedAccount.id), loadBreakdown(selectedAccount.id)])
        const failed = breakdownResult.failedBreakdowns ?? []
        setNotice(
          failed.length === 0
            ? `Synced Google Ads analytics: ${result.campaignRows} campaign, ${result.adGroupRows} ad group, ${result.adRows} ad row(s), plus all supported reporting breakdowns.`
            : `Google Ads core analytics synchronized. Breakdown sync completed with ${failed.length} unavailable dimension(s): ${failed.join(', ')}.`,
        )
      } else {
        const result = await adCenterApi.syncMetaInsights(
          selectedCompany.companyId,
          selectedAccount.id,
          from,
          to,
        )
        const breakdownResult = await adCenterApi.syncMetaInsightBreakdowns(
          selectedCompany.companyId,
          selectedAccount.id,
          from,
          to,
        )
        await Promise.all([loadAnalytics(selectedAccount.id), loadBreakdown(selectedAccount.id)])
        const failed = breakdownResult.failedBreakdowns ?? []
        setNotice(
          failed.length === 0
            ? `Synced Meta analytics: ${result.campaignRows} campaign, ${result.adSetRows} ad set, ${result.adRows} ad, plus all reporting breakdowns.`
            : `Core analytics synchronized. Breakdown sync completed with ${failed.length} unavailable dimension(s): ${failed.join(', ')}.`,
        )
      }
    } catch (syncError) {
      setError(apiErrorMessage(syncError, 'Unable to synchronize advertising analytics'))
    } finally {
      setSyncing(false)
    }
  }

  if (!selectedCompany) return null

  const summary = analytics.summary
  const currency = summary.currency ?? selectedAccount?.currency
  const kpis = [
    {
      label: 'Spend',
      value: formatCurrency(summary.spend, currency),
      hint: 'Total media spend',
    },
    {
      label: 'Impressions',
      value: formatCompact(summary.impressions),
      hint: 'Ad impressions',
    },
    {
      label: 'Reach',
      value: analytics.exactRangeAvailable ? formatCompact(summary.reach) : '—',
      hint: analytics.exactRangeAvailable ? 'Unique people reached' : 'Sync for exact reach',
    },
    {
      label: 'Clicks',
      value: formatCompact(summary.clicks),
      hint: 'All reported clicks',
    },
    {
      label: 'CTR',
      value: summary.ctr == null ? '—' : `${formatNumber(summary.ctr)}%`,
      hint: 'Clicks ÷ impressions',
    },
    {
      label: 'CPC',
      value: summary.cpc == null ? '—' : formatCurrency(summary.cpc, currency),
      hint: 'Average cost per click',
    },
    {
      label: 'CPM',
      value: summary.cpm == null ? '—' : formatCurrency(summary.cpm, currency),
      hint: 'Cost per 1,000 impressions',
    },
    {
      label: 'Conversions',
      value: formatNumber(summary.conversions),
      hint: 'Normalized provider conversions',
    },
    {
      label: 'CPA',
      value: summary.cpa == null ? '—' : formatCurrency(summary.cpa, currency),
      hint: 'Spend ÷ conversions',
    },
    {
      label: 'ROAS',
      value: summary.roas == null ? '—' : `${formatNumber(summary.roas)}×`,
      hint: `Value ${formatCurrency(summary.conversionValue, currency)}`,
    },
    {
      label: 'Leads',
      value: formatNumber(summary.leads),
      hint: 'Provider attributed leads',
    },
    {
      label: 'Purchases',
      value: formatNumber(summary.purchases),
      hint: 'Provider attributed purchases',
    },
  ]

  return (
    <section className="px-4 py-6 sm:px-6 lg:px-8">
      <div className="mx-auto max-w-7xl">
        <header className="flex flex-wrap items-end justify-between gap-4">
          <div>
            <p className="eyebrow">Ad Center</p>
            <h1 className="mt-2 text-2xl font-bold tracking-tight text-slate-950 sm:text-3xl">
              Analytics
            </h1>
            <p className="mt-2 max-w-2xl text-sm leading-6 text-slate-500">
              Provider performance from account totals down to campaigns, delivery groups, and ads.
            </p>
          </div>

          {can(PERMISSIONS.ADS_MODIFY) && (
            <button
              type="button"
              className="primary-button inline-flex items-center gap-2"
              disabled={!selectedAccount || syncing}
              onClick={() => void sync()}
            >
              <FiRefreshCw aria-hidden="true" />
              {syncing
                ? 'Syncing…'
                : `Sync ${selectedAccount?.provider === 'GOOGLE_ADS' ? 'Google' : 'Meta'} analytics`}
            </button>
          )}
        </header>

        <AdFeedbackAlert error={error} notice={notice} />

        <div className="panel mt-6 p-5">
          <div className="grid gap-4 lg:grid-cols-[minmax(220px,1fr)_auto_auto] lg:items-end">
            <label className="block text-xs font-semibold text-slate-700">
              Ad account
              <select
                className="form-input mt-2"
                value={selectedAccount?.id ?? ''}
                disabled={!managedAccounts.length}
                onChange={(event) => void chooseAccount(event.target.value)}
              >
                {managedAccounts.length === 0 ? (
                  <option value="">No managed ad account</option>
                ) : (
                  managedAccounts.map((account) => (
                    <option key={account.id} value={account.id}>
                      {account.name}
                    </option>
                  ))
                )}
              </select>
            </label>

            <div className="flex flex-wrap gap-2">
              {[7, 30, 90].map((days) => (
                <button
                  key={days}
                  type="button"
                  className="secondary-button"
                  disabled={!selectedAccount}
                  onClick={() => void applyPreset(days)}
                >
                  {days}D
                </button>
              ))}
            </div>

            <div className="grid grid-cols-2 gap-2">
              <label className="block text-xs font-semibold text-slate-700">
                From
                <input
                  className="form-input mt-2"
                  type="date"
                  value={from}
                  max={to}
                  onChange={(event) => setFrom(event.target.value)}
                  onBlur={() => void refreshStored()}
                />
              </label>
              <label className="block text-xs font-semibold text-slate-700">
                To
                <input
                  className="form-input mt-2"
                  type="date"
                  value={to}
                  min={from}
                  max={isoDate(new Date())}
                  onChange={(event) => setTo(event.target.value)}
                  onBlur={() => void refreshStored()}
                />
              </label>
            </div>
          </div>

          <div className="mt-4 flex flex-wrap items-center gap-3 border-t border-slate-100 pt-4 text-xs text-slate-500">
            <FiClock aria-hidden="true" />
            <span>
              {analytics.lastSyncedAt
                ? `Last synchronized ${new Date(analytics.lastSyncedAt).toLocaleString()}`
                : 'This range has not been synchronized yet.'}
            </span>
            {!analytics.exactRangeAvailable && selectedAccount && (
              <span className="rounded-full bg-amber-50 px-2.5 py-1 font-semibold text-amber-700">
                Sync provider for exact range totals
              </span>
            )}
          </div>
        </div>

        {managedAccounts.length === 0 ? (
          <div className="panel mt-6 p-10 text-center text-sm text-slate-500">
            Connect and manage an ad account before using analytics.
          </div>
        ) : loading ? (
          <div className="mt-6"><PageSkeleton /></div>
        ) : (
          <>
            <div className="mt-6 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
              {kpis.map((kpi) => (
                <article key={kpi.label} className="panel p-5">
                  <p className="text-xs font-semibold uppercase tracking-wide text-slate-400">
                    {kpi.label}
                  </p>
                  <p className="mt-2 text-2xl font-bold tracking-tight text-slate-950">
                    {kpi.value}
                  </p>
                  <p className="mt-2 text-xs text-slate-500">{kpi.hint}</p>
                </article>
              ))}
            </div>

            <div className="panel mt-6 p-5 sm:p-6">
              <div className="flex flex-wrap items-center justify-between gap-3">
                <div>
                  <div className="flex items-center gap-2">
                    <FiTrendingUp className="text-indigo-500" aria-hidden="true" />
                    <h2 className="text-base font-bold text-slate-950">Performance trend</h2>
                  </div>
                  <p className="mt-1 text-xs text-slate-500">
                    Daily spend and attributed conversions for the selected account.
                  </p>
                </div>
                <span className="rounded-full bg-slate-100 px-2.5 py-1 text-xs font-semibold text-slate-600">
                  {from} → {to}
                </span>
              </div>
              <div className="mt-5">
                <TrendChart
                  points={analytics.daily}
                  currency={summary.currency ?? selectedAccount?.currency}
                />
              </div>
            </div>

            <BreakdownPanel
              breakdown={breakdown}
              type={breakdownType}
              loading={breakdownLoading}
              currency={currency}
              onSelect={(next) => void chooseBreakdown(next)}
            />

            {selectedAccount?.provider === 'GOOGLE_ADS' && (
              <GoogleExperimentsPanel
                companyId={selectedCompany.companyId}
                accountId={selectedAccount.id}
                currency={currency}
                canCreate={can(PERMISSIONS.ADS_CREATE)}
                canModify={can(PERMISSIONS.ADS_MODIFY)}
              />
            )}

            {selectedAccount && (
              <AdOptimizationWorkspace
                companyId={selectedCompany.companyId}
                selectedAccount={selectedAccount}
                accounts={managedAccounts}
                from={from}
                to={to}
                canCreate={can(PERMISSIONS.ADS_CREATE)}
                canModify={can(PERMISSIONS.ADS_MODIFY)}
              />
            )}

            {selectedAccount?.provider === 'GOOGLE_ADS' && (
              <GoogleProviderReportingPanel
                companyId={selectedCompany.companyId}
                accountId={selectedAccount.id}
                from={from}
                to={to}
                currency={currency}
              />
            )}

            <div className="panel mt-6 overflow-hidden">
              <div className="flex flex-wrap items-center justify-between gap-3 border-b border-slate-100 px-5 py-4">
                <div>
                  <div className="flex items-center gap-2">
                    <FiBarChart2 className="text-indigo-500" aria-hidden="true" />
                    <h2 className="text-base font-bold text-slate-950">Performance breakdown</h2>
                  </div>
                  <p className="mt-1 text-xs text-slate-500">
                    Exact synchronized range totals for each provider object.
                  </p>
                </div>

                <div className="flex rounded-xl bg-slate-100 p-1">
                  {(
                    [
                      ['CAMPAIGN', 'Campaigns'],
                      [
                        'AD_SET',
                        selectedAccount?.provider === 'GOOGLE_ADS' ? 'Ad groups' : 'Ad sets',
                      ],
                      ['AD', 'Ads'],
                    ] as const
                  ).map(([value, label]) => (
                    <button
                      key={value}
                      type="button"
                      className={`rounded-lg px-3 py-2 text-xs font-semibold transition ${
                        level === value
                          ? 'bg-white text-slate-950 shadow-sm'
                          : 'text-slate-500 hover:text-slate-800'
                      }`}
                      onClick={() => void chooseLevel(value)}
                    >
                      {label}
                    </button>
                  ))}
                </div>
              </div>

              {analytics.entities.length === 0 ? (
                <div className="p-10 text-center">
                  <FiActivity className="mx-auto text-3xl text-slate-300" aria-hidden="true" />
                  <h3 className="mt-3 text-base font-bold text-slate-950">
                    No performance rows for this range
                  </h3>
                  <p className="mt-2 text-sm text-slate-500">
                    Sync Meta analytics or choose another date range.
                  </p>
                </div>
              ) : (
                <>
                  <div className="hidden overflow-x-auto lg:block">
                    <table className="w-full text-left text-sm">
                      <thead className="bg-slate-50 text-xs font-semibold uppercase tracking-wide text-slate-500">
                        <tr>
                          <th className="px-5 py-3">Name</th>
                          <th className="px-5 py-3 text-right">Spend</th>
                          <th className="px-5 py-3 text-right">Impressions</th>
                          <th className="px-5 py-3 text-right">Reach</th>
                          <th className="px-5 py-3 text-right">Clicks</th>
                          <th className="px-5 py-3 text-right">CTR</th>
                          <th className="px-5 py-3 text-right">Conversions</th>
                          <th className="px-5 py-3 text-right">CPA</th>
                          <th className="px-5 py-3 text-right">ROAS</th>
                        </tr>
                      </thead>
                      <tbody className="divide-y divide-slate-100">
                        {analytics.entities.map((row) => {
                          const link = entityLink(level, row.entityId)
                          return (
                            <tr key={row.entityRef}>
                              <td className="px-5 py-4">
                                {link ? (
                                  <Link
                                    to={link}
                                    className="font-semibold text-slate-950 hover:text-indigo-600"
                                  >
                                    {row.entityName || row.entityRef}
                                  </Link>
                                ) : (
                                  <p className="font-semibold text-slate-950">
                                    {row.entityName || row.entityRef}
                                  </p>
                                )}
                                <p className="mt-1 text-xs text-slate-400">{row.entityRef}</p>
                              </td>
                              <td className="px-5 py-4 text-right font-medium text-slate-700">
                                {formatCurrency(
                                  row.spend,
                                  summary.currency ?? selectedAccount?.currency,
                                )}
                              </td>
                              <td className="px-5 py-4 text-right text-slate-600">
                                {formatCompact(row.impressions)}
                              </td>
                              <td className="px-5 py-4 text-right text-slate-600">
                                {formatCompact(row.reach)}
                              </td>
                              <td className="px-5 py-4 text-right text-slate-600">
                                {formatCompact(row.clicks)}
                              </td>
                              <td className="px-5 py-4 text-right text-slate-600">
                                {row.ctr == null ? '—' : `${formatNumber(row.ctr)}%`}
                              </td>
                              <td className="px-5 py-4 text-right text-slate-600">
                                {formatNumber(row.conversions)}
                              </td>
                              <td className="px-5 py-4 text-right text-slate-600">
                                {row.cpa == null
                                  ? '—'
                                  : formatCurrency(
                                      row.cpa,
                                      summary.currency ?? selectedAccount?.currency,
                                    )}
                              </td>
                              <td className="px-5 py-4 text-right font-semibold text-slate-700">
                                {row.roas == null ? '—' : `${formatNumber(row.roas)}×`}
                              </td>
                            </tr>
                          )
                        })}
                      </tbody>
                    </table>
                  </div>

                  <div className="divide-y divide-slate-100 lg:hidden">
                    {analytics.entities.map((row) => {
                      const link = entityLink(level, row.entityId)
                      return (
                        <div key={row.entityRef} className="p-5">
                          <div className="flex items-start justify-between gap-3">
                            <div className="min-w-0">
                              {link ? (
                                <Link
                                  to={link}
                                  className="font-semibold text-slate-950 hover:text-indigo-600"
                                >
                                  {row.entityName || row.entityRef}
                                </Link>
                              ) : (
                                <p className="font-semibold text-slate-950">
                                  {row.entityName || row.entityRef}
                                </p>
                              )}
                              <p className="mt-1 truncate text-xs text-slate-400">
                                {row.entityRef}
                              </p>
                            </div>
                            <p className="font-bold text-slate-950">
                              {formatCurrency(
                                row.spend,
                                summary.currency ?? selectedAccount?.currency,
                              )}
                            </p>
                          </div>
                          <div className="mt-4 grid grid-cols-3 gap-3 text-xs">
                            <div>
                              <p className="text-slate-400">Clicks</p>
                              <p className="mt-1 font-semibold text-slate-700">
                                {formatCompact(row.clicks)}
                              </p>
                            </div>
                            <div>
                              <p className="text-slate-400">Conversions</p>
                              <p className="mt-1 font-semibold text-slate-700">
                                {formatNumber(row.conversions)}
                              </p>
                            </div>
                            <div>
                              <p className="text-slate-400">ROAS</p>
                              <p className="mt-1 font-semibold text-slate-700">
                                {row.roas == null ? '—' : `${formatNumber(row.roas)}×`}
                              </p>
                            </div>
                          </div>
                        </div>
                      )
                    })}
                  </div>
                </>
              )}
            </div>
          </>
        )}
      </div>
    </section>
  )
}
