import { useSnackbarState } from '../../components/feedback/useSnackbarState'
import { useEffect, useState } from 'react'
import {
  FiTarget,
  FiCalendar,
  FiRefreshCw,
  FiShare2,
  FiPieChart,
  FiDollarSign,
} from 'react-icons/fi'
import {
  crmSalesApi,
  dealMoney,
  salesLabel,
  type AttributionReport,
  type ChannelAttribution,
  type CampaignRoi,
} from './crmSalesApi'
import { apiErrorMessage } from '../../lib/api/apiClient'
import { PageSkeleton } from '../../components/ui/Skeleton'

interface CrmAttributionViewProps {
  companyId: string
  currency?: string
}

export default function CrmAttributionView({ companyId, currency = 'INR' }: CrmAttributionViewProps) {
  const [period, setPeriod] = useState<string>('all_time')
  const [report, setReport] = useState<AttributionReport | null>(null)
  const [loading, setLoading] = useState<boolean>(true)
  const [error, setError] = useSnackbarState<string>('', 'error')
  const [refreshKey, setRefreshKey] = useState<number>(0)
  const [activeDrilldownTab, setActiveDrilldownTab] = useState<'all' | 'broadcasts' | 'ads' | 'forms'>('all')

  useEffect(() => {
    if (!companyId) return
    setLoading(true)
    setError('')

    crmSalesApi
      .getAttributionReport(companyId, { period })
      .then((data) => {
        setReport(data)
      })
      .catch((err) => {
        setError(apiErrorMessage(err, 'Failed to load marketing attribution report'))
      })
      .finally(() => {
        setLoading(false)
      })
  }, [companyId, period, refreshKey])

  const channelIcon = (channel: string) => {
    switch (channel.toUpperCase()) {
      case 'BROADCAST':
        return '📢'
      case 'META_ADS':
        return '🎯'
      case 'GOOGLE_ADS':
        return '🌐'
      case 'WEB_FORM':
        return '📝'
      case 'DIRECT_SALES':
        return '💼'
      case 'REFERRAL':
        return '🤝'
      case 'ORGANIC':
        return '🌱'
      case 'EVENT_WEBINAR':
        return '🎤'
      case 'PARTNER':
        return '🏢'
      default:
        return '🏷️'
    }
  }

  const channelBadgeClass = (channel: string) => {
    switch (channel.toUpperCase()) {
      case 'BROADCAST':
        return 'bg-purple-50 text-purple-700 dark:bg-purple-950/60 dark:text-purple-300 border-purple-200 dark:border-purple-800'
      case 'META_ADS':
        return 'bg-blue-50 text-blue-700 dark:bg-blue-950/60 dark:text-blue-300 border-blue-200 dark:border-blue-800'
      case 'GOOGLE_ADS':
        return 'bg-emerald-50 text-emerald-700 dark:bg-emerald-950/60 dark:text-emerald-300 border-emerald-200 dark:border-emerald-800'
      case 'WEB_FORM':
        return 'bg-amber-50 text-amber-700 dark:bg-amber-950/60 dark:text-amber-300 border-amber-200 dark:border-amber-800'
      case 'DIRECT_SALES':
        return 'bg-slate-100 text-slate-700 dark:bg-slate-800 dark:text-slate-300 border-slate-200 dark:border-slate-700'
      default:
        return 'bg-indigo-50 text-indigo-700 dark:bg-indigo-950/60 dark:text-indigo-300 border-indigo-200 dark:border-indigo-800'
    }
  }

  if (loading) {
    return (
      <div className="space-y-6">
        <PageSkeleton />
      </div>
    )
  }

  if (error) {
    return (
      <div className="rounded-2xl border border-rose-200 bg-rose-50/70 p-6 text-center dark:border-rose-900/50 dark:bg-rose-950/20">
        <p className="text-sm font-semibold text-rose-700 dark:text-rose-400">{error}</p>
        <button
          onClick={() => setRefreshKey((k) => k + 1)}
          className="secondary-button mt-4 gap-2 text-xs"
        >
          <FiRefreshCw className="size-3.5" /> Retry
        </button>
      </div>
    )
  }

  if (!report) return null

  // Combined campaigns for "all" tab
  const allCampaigns: (CampaignRoi & { tag: string })[] = [
    ...(report.topBroadcasts || []).map((b) => ({ ...b, tag: 'Broadcast' })),
    ...(report.topAdCampaigns || []).map((a) => ({ ...a, tag: a.subtype || 'Ad Campaign' })),
    ...(report.topLeadForms || []).map((f) => ({ ...f, tag: 'Lead Form' })),
  ].sort((a, b) => (Number(b.wonAmount) || 0) - (Number(a.wonAmount) || 0))

  return (
    <div className="space-y-6">
      {/* Filter Bar */}
      <div className="flex flex-wrap items-center justify-between gap-4 rounded-2xl border border-slate-200/80 bg-white/70 p-4 shadow-xs backdrop-blur-md dark:border-slate-800 dark:bg-slate-900/60">
        <div className="flex flex-wrap items-center gap-3">
          <div className="flex items-center gap-2">
            <FiCalendar className="size-4 text-slate-500" />
            <select
              aria-label="Attribution period"
              className="form-input text-xs font-semibold py-1.5 px-3 rounded-xl border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 text-slate-800 dark:text-slate-200"
              value={period}
              onChange={(e) => setPeriod(e.target.value)}
            >
              <option value="this_month">This Month</option>
              <option value="this_quarter">This Quarter</option>
              <option value="this_year">This Year</option>
              <option value="all_time">All Time</option>
            </select>
          </div>
          <span className="text-xs text-slate-400 font-medium">
            Showing attribution for <strong className="text-slate-700 dark:text-slate-200">{report.periodLabel}</strong>
          </span>
        </div>

        <button
          onClick={() => setRefreshKey((k) => k + 1)}
          className="secondary-button gap-2 text-xs py-1.5 px-3"
          title="Refresh attribution report"
        >
          <FiRefreshCw className="size-3.5" />
          <span>Refresh</span>
        </button>
      </div>

      {/* Top 4 KPI Metric Cards */}
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        {/* Total Won Revenue */}
        <div className="panel p-5 relative overflow-hidden bg-gradient-to-br from-white to-slate-50/50 dark:from-slate-900 dark:to-slate-900/70">
          <div className="flex items-center justify-between text-slate-500 dark:text-slate-400 text-xs font-semibold">
            <span>Total Won Revenue</span>
            <div className="rounded-xl bg-slate-100 dark:bg-slate-800 p-2 text-slate-600 dark:text-slate-300">
              <FiDollarSign className="size-4" />
            </div>
          </div>
          <div className="mt-3 text-2xl font-black text-slate-950 dark:text-slate-100">
            {dealMoney(String(report.totalWonRevenue || '0'), currency)}
          </div>
          <p className="mt-1 text-xs text-slate-500 dark:text-slate-400">
            Across all channels ({report.periodLabel})
          </p>
        </div>

        {/* Marketing Attributed Revenue */}
        <div className="panel p-5 relative overflow-hidden bg-gradient-to-br from-indigo-50/40 to-white dark:from-indigo-950/20 dark:to-slate-900">
          <div className="flex items-center justify-between text-indigo-600 dark:text-indigo-400 text-xs font-semibold">
            <span>Marketing Attributed</span>
            <div className="rounded-xl bg-indigo-50 dark:bg-indigo-950/60 p-2 text-indigo-600 dark:text-indigo-400">
              <FiTarget className="size-4" />
            </div>
          </div>
          <div className="mt-3 text-2xl font-black text-indigo-600 dark:text-indigo-400">
            {dealMoney(String(report.marketingAttributedWon || '0'), currency)}
          </div>
          <p className="mt-1 text-xs text-slate-500 dark:text-slate-400">
            Closed deals linked to campaigns/forms
          </p>
        </div>

        {/* Marketing Contribution Share % */}
        <div className="panel p-5 relative overflow-hidden bg-gradient-to-br from-purple-50/40 to-white dark:from-purple-950/20 dark:to-slate-900">
          <div className="flex items-center justify-between text-purple-600 dark:text-purple-400 text-xs font-semibold">
            <span>Revenue Contribution</span>
            <div className="rounded-xl bg-purple-50 dark:bg-purple-950/60 p-2 text-purple-600 dark:text-purple-400">
              <FiPieChart className="size-4" />
            </div>
          </div>
          <div className="mt-3 text-2xl font-black text-purple-600 dark:text-purple-400">
            {report.marketingContributionPct.toFixed(1)}%
          </div>
          <div className="mt-2.5 h-1.5 w-full overflow-hidden rounded-full bg-purple-100 dark:bg-purple-950/50">
            <div
              className="h-full bg-purple-600 dark:bg-purple-500 rounded-full transition-all duration-500"
              style={{ width: `${Math.min(100, Math.max(0, report.marketingContributionPct))}%` }}
            />
          </div>
        </div>

        {/* Total Attributed Opportunities */}
        <div className="panel p-5 relative overflow-hidden bg-gradient-to-br from-emerald-50/40 to-white dark:from-emerald-950/20 dark:to-slate-900">
          <div className="flex items-center justify-between text-emerald-600 dark:text-emerald-400 text-xs font-semibold">
            <span>Attributed Deals</span>
            <div className="rounded-xl bg-emerald-50 dark:bg-emerald-950/60 p-2 text-emerald-600 dark:text-emerald-400">
              <FiShare2 className="size-4" />
            </div>
          </div>
          <div className="mt-3 text-2xl font-black text-emerald-600 dark:text-emerald-400">
            {report.totalAttributedDeals}
          </div>
          <p className="mt-1 text-xs text-slate-500 dark:text-slate-400">
            Deals with origin attribution
          </p>
        </div>
      </div>

      {/* Channel Performance Breakdown */}
      <div className="panel p-5">
        <div className="flex items-center justify-between">
          <div>
            <h2 className="text-base font-bold text-slate-900 dark:text-slate-100">
              Channel Attribution & Conversion
            </h2>
            <p className="mt-1 text-xs text-slate-500 dark:text-slate-400">
              Full lifecycle performance from lead source channel to closed-won deals
            </p>
          </div>
        </div>

        <div className="mt-4 overflow-x-auto">
          <table className="w-full text-left text-xs">
            <thead className="border-b border-slate-200 dark:border-slate-800 bg-slate-50/75 dark:bg-slate-800/40 text-slate-500 font-semibold">
              <tr>
                <th className="p-3">Source Channel</th>
                <th className="p-3 text-right">Total Deals</th>
                <th className="p-3 text-right">Open Pipeline</th>
                <th className="p-3 text-right">Won Deals</th>
                <th className="p-3 text-right">Won Revenue</th>
                <th className="p-3 text-right">Win Rate</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100 dark:divide-slate-800">
              {report.channels && report.channels.length > 0 ? (
                report.channels.map((chan: ChannelAttribution) => (
                  <tr key={chan.channel} className="hover:bg-slate-50/50 dark:hover:bg-slate-800/30 transition">
                    <td className="p-3 font-semibold text-slate-900 dark:text-slate-100">
                      <span
                        className={`inline-flex items-center gap-1.5 rounded-lg border px-2.5 py-1 text-xs font-semibold ${channelBadgeClass(
                          chan.channel,
                        )}`}
                      >
                        <span>{channelIcon(chan.channel)}</span>
                        <span>{chan.channelLabel || salesLabel(chan.channel)}</span>
                      </span>
                    </td>
                    <td className="p-3 text-right font-semibold text-slate-700 dark:text-slate-300">
                      {chan.dealsCount}
                    </td>
                    <td className="p-3 text-right text-slate-600 dark:text-slate-400">
                      {dealMoney(String(chan.openAmount || '0'), currency)}
                    </td>
                    <td className="p-3 text-right font-semibold text-emerald-600 dark:text-emerald-400">
                      {chan.wonDealsCount}
                    </td>
                    <td className="p-3 text-right font-bold text-slate-950 dark:text-slate-100">
                      {dealMoney(String(chan.wonAmount || '0'), currency)}
                    </td>
                    <td className="p-3 text-right">
                      <span className="inline-flex items-center rounded-md bg-slate-100 dark:bg-slate-800 px-2 py-0.5 text-xs font-bold text-slate-700 dark:text-slate-300">
                        {chan.winRatePct.toFixed(1)}%
                      </span>
                    </td>
                  </tr>
                ))
              ) : (
                <tr>
                  <td colSpan={6} className="p-6 text-center text-slate-400">
                    No deal data recorded for the selected period.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </div>

      {/* Top Campaigns & Sources Leaderboard */}
      <div className="panel p-5">
        <div className="flex flex-wrap items-center justify-between gap-4">
          <div>
            <h2 className="text-base font-bold text-slate-900 dark:text-slate-100">
              Campaign & Asset Leaderboard
            </h2>
            <p className="mt-1 text-xs text-slate-500 dark:text-slate-400">
              Specific Broadcast campaigns, Ads, and Inbound Web forms driving pipeline and revenue
            </p>
          </div>

          {/* Drilldown Subtabs */}
          <div className="flex items-center gap-1 rounded-xl bg-slate-100 dark:bg-slate-800 p-1">
            <button
              onClick={() => setActiveDrilldownTab('all')}
              className={`rounded-lg px-3 py-1.5 text-xs font-semibold transition ${
                activeDrilldownTab === 'all'
                  ? 'bg-white dark:bg-slate-900 text-indigo-600 dark:text-indigo-400 shadow-xs'
                  : 'text-slate-600 dark:text-slate-400'
              }`}
            >
              All Assets ({allCampaigns.length})
            </button>
            <button
              onClick={() => setActiveDrilldownTab('broadcasts')}
              className={`rounded-lg px-3 py-1.5 text-xs font-semibold transition ${
                activeDrilldownTab === 'broadcasts'
                  ? 'bg-white dark:bg-slate-900 text-indigo-600 dark:text-indigo-400 shadow-xs'
                  : 'text-slate-600 dark:text-slate-400'
              }`}
            >
              📢 Broadcasts ({report.topBroadcasts?.length || 0})
            </button>
            <button
              onClick={() => setActiveDrilldownTab('ads')}
              className={`rounded-lg px-3 py-1.5 text-xs font-semibold transition ${
                activeDrilldownTab === 'ads'
                  ? 'bg-white dark:bg-slate-900 text-indigo-600 dark:text-indigo-400 shadow-xs'
                  : 'text-slate-600 dark:text-slate-400'
              }`}
            >
              🎯 Ad Campaigns ({report.topAdCampaigns?.length || 0})
            </button>
            <button
              onClick={() => setActiveDrilldownTab('forms')}
              className={`rounded-lg px-3 py-1.5 text-xs font-semibold transition ${
                activeDrilldownTab === 'forms'
                  ? 'bg-white dark:bg-slate-900 text-indigo-600 dark:text-indigo-400 shadow-xs'
                  : 'text-slate-600 dark:text-slate-400'
              }`}
            >
              📝 Web Forms ({report.topLeadForms?.length || 0})
            </button>
          </div>
        </div>

        <div className="mt-4 overflow-x-auto">
          <table className="w-full text-left text-xs">
            <thead className="border-b border-slate-200 dark:border-slate-800 bg-slate-50/75 dark:bg-slate-800/40 text-slate-500 font-semibold">
              <tr>
                <th className="p-3">Campaign / Source Asset</th>
                <th className="p-3">Type</th>
                <th className="p-3 text-right">Deals Sourced</th>
                <th className="p-3 text-right">Open Pipeline</th>
                <th className="p-3 text-right">Closed-Won Deals</th>
                <th className="p-3 text-right">Revenue Generated</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100 dark:divide-slate-800">
              {(() => {
                let list: (CampaignRoi & { tag?: string })[] = []
                if (activeDrilldownTab === 'all') list = allCampaigns
                else if (activeDrilldownTab === 'broadcasts') list = report.topBroadcasts || []
                else if (activeDrilldownTab === 'ads') list = report.topAdCampaigns || []
                else if (activeDrilldownTab === 'forms') list = report.topLeadForms || []

                if (list.length === 0) {
                  return (
                    <tr>
                      <td colSpan={6} className="p-8 text-center text-slate-400">
                        No campaigns recorded under this category yet. When deals are associated with
                        broadcasts, ad campaigns, or web forms, their ROI will appear here.
                      </td>
                    </tr>
                  )
                }

                return list.map((item) => (
                  <tr key={item.id + item.campaignType} className="hover:bg-slate-50/50 dark:hover:bg-slate-800/30 transition">
                    <td className="p-3 font-semibold text-slate-900 dark:text-slate-100">
                      <div className="flex items-center gap-2">
                        <span>
                          {item.campaignType === 'BROADCAST'
                            ? '📢'
                            : item.campaignType === 'AD_CAMPAIGN'
                            ? '🎯'
                            : '📝'}
                        </span>
                        <span className="font-bold">{item.title}</span>
                      </div>
                    </td>
                    <td className="p-3">
                      <span className="inline-flex items-center rounded-md bg-slate-100 dark:bg-slate-800 px-2 py-0.5 text-[11px] font-medium text-slate-600 dark:text-slate-300">
                        {item.subtype || item.campaignType}
                      </span>
                    </td>
                    <td className="p-3 text-right font-semibold text-slate-700 dark:text-slate-300">
                      {item.dealsCount}
                    </td>
                    <td className="p-3 text-right text-slate-600 dark:text-slate-400">
                      {dealMoney(String(item.openAmount || '0'), currency)}
                    </td>
                    <td className="p-3 text-right font-semibold text-emerald-600 dark:text-emerald-400">
                      {item.wonDealsCount}
                    </td>
                    <td className="p-3 text-right font-bold text-emerald-600 dark:text-emerald-400">
                      {dealMoney(String(item.wonAmount || '0'), currency)}
                    </td>
                  </tr>
                ))
              })()}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  )
}
