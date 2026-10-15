import { useEffect, useState } from 'react'
import {
  FiTarget,
  FiCalendar,
  FiRefreshCw,
  FiAward,
  FiCheckCircle,
  FiUser,
  FiLayers,
  FiPercent,
  FiX,
  FiBriefcase,
} from 'react-icons/fi'
import {
  crmSalesApi,
  dealMoney,
  dealStageColor,
  type CrmForecastResponse,
  type CrmForecastDeal,
  type CrmForecastStage,
  type CrmForecastRep,
  type CrmForecastTimeline,
  type Pipeline,
} from './crmSalesApi'
import { apiErrorMessage } from '../../lib/api/apiClient'
import { PageSkeleton } from '../../components/ui/Skeleton'

interface CrmForecastViewProps {
  companyId: string
  currency?: string
}

export default function CrmForecastView({ companyId, currency = 'INR' }: CrmForecastViewProps) {
  const [period, setPeriod] = useState<string>('this_quarter')
  const [pipelineId, setPipelineId] = useState<string>('')
  const [ownerUserId, setOwnerUserId] = useState<string>('')
  const [pipelines, setPipelines] = useState<Pipeline[]>([])

  const [forecast, setForecast] = useState<CrmForecastResponse | null>(null)
  const [loading, setLoading] = useState<boolean>(true)
  const [error, setError] = useState<string>('')
  const [refreshKey, setRefreshKey] = useState<number>(0)

  // Quota Modal State
  const [quotaModalOpen, setQuotaModalOpen] = useState<boolean>(false)
  const [quotaSubmitting, setQuotaSubmitting] = useState<boolean>(false)
  const [quotaTargetUser, setQuotaTargetUser] = useState<string>('')
  const [quotaAmount, setQuotaAmount] = useState<string>('')
  const [quotaPeriodType, setQuotaPeriodType] = useState<string>('MONTHLY')
  const [quotaPeriodKey, setQuotaPeriodKey] = useState<string>(() => {
    const now = new Date()
    return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`
  })
  const [quotaMsg, setQuotaMsg] = useState<string>('')

  // Load pipelines for selector
  useEffect(() => {
    if (!companyId) return
    crmSalesApi
      .pipelines(companyId)
      .then((res: Pipeline[]) => setPipelines(res || []))
      .catch(() => {})
  }, [companyId])

  // Load Forecast data
  useEffect(() => {
    if (!companyId) return
    setLoading(true)
    setError('')

    const params: { period?: string; pipelineId?: string; ownerUserId?: string } = {
      period,
    }
    if (pipelineId) params.pipelineId = pipelineId
    if (ownerUserId) params.ownerUserId = ownerUserId

    crmSalesApi
      .getForecast(companyId, params)
      .then((data) => {
        setForecast(data)
      })
      .catch((err) => {
        setError(apiErrorMessage(err, 'Failed to calculate sales forecast'))
      })
      .finally(() => setLoading(false))
  }, [companyId, period, pipelineId, ownerUserId, refreshKey])

  async function handleSaveQuota(e: React.FormEvent) {
    e.preventDefault()
    if (!companyId || !quotaTargetUser || !quotaAmount) return

    setQuotaSubmitting(true)
    setQuotaMsg('')

    try {
      await crmSalesApi.setQuota(companyId, {
        userId: quotaTargetUser,
        periodType: quotaPeriodType,
        periodKey: quotaPeriodKey,
        targetAmount: Number(quotaAmount),
        currency: forecast?.summary.currency || currency,
      })
      setQuotaMsg('Quota saved successfully!')
      setTimeout(() => {
        setQuotaModalOpen(false)
        setQuotaMsg('')
        setRefreshKey((n) => n + 1)
      }, 700)
    } catch (err) {
      setQuotaMsg(apiErrorMessage(err, 'Failed to save sales quota'))
    } finally {
      setQuotaSubmitting(false)
    }
  }

  function openQuotaModalForUser(userId?: string) {
    if (userId) setQuotaTargetUser(userId)
    else if (forecast?.reps.length) setQuotaTargetUser(forecast.reps[0].userId)
    setQuotaAmount('')
    setQuotaModalOpen(true)
  }

  const s = forecast?.summary

  return (
    <div className="space-y-6">
      {/* Top Filter Bar */}
      <div className="flex flex-wrap items-center justify-between gap-4 rounded-2xl bg-white dark:bg-slate-900 p-4 border border-slate-200 dark:border-slate-800 shadow-sm">
        {/* Period Buttons */}
        <div className="flex flex-wrap items-center gap-1.5 p-1 rounded-xl bg-slate-100 dark:bg-slate-800/80">
          {[
            { id: 'this_month', label: 'This Month' },
            { id: 'next_month', label: 'Next Month' },
            { id: 'this_quarter', label: 'This Quarter' },
            { id: 'next_quarter', label: 'Next Quarter' },
            { id: 'this_year', label: 'This Year' },
            { id: 'all_open', label: 'All Open' },
          ].map((item) => (
            <button
              key={item.id}
              onClick={() => setPeriod(item.id)}
              className={`rounded-lg px-3 py-1.5 text-xs font-semibold transition ${
                period === item.id
                  ? 'bg-white dark:bg-slate-900 text-indigo-600 dark:text-indigo-400 shadow-sm'
                  : 'text-slate-600 dark:text-slate-400 hover:text-slate-900 dark:hover:text-slate-200'
              }`}
            >
              {item.label}
            </button>
          ))}
        </div>

        {/* Filter Dropdowns & Actions */}
        <div className="flex flex-wrap items-center gap-2.5">
          {/* Pipeline Selector */}
          <div className="flex items-center gap-1.5">
            <FiLayers className="size-3.5 text-slate-400" />
            <select
              value={pipelineId}
              onChange={(e) => setPipelineId(e.target.value)}
              className="rounded-xl border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 px-3 py-1.5 text-xs font-medium text-slate-800 dark:text-slate-200 focus:outline-none focus:ring-2 focus:ring-indigo-500/20"
            >
              <option value="">All Pipelines</option>
              {pipelines.map((p) => (
                <option key={p.id} value={p.id}>
                  {p.name} {p.isDefault ? '(Default)' : ''}
                </option>
              ))}
            </select>
          </div>

          {/* Rep Selector */}
          <div className="flex items-center gap-1.5">
            <FiUser className="size-3.5 text-slate-400" />
            <select
              value={ownerUserId}
              onChange={(e) => setOwnerUserId(e.target.value)}
              className="rounded-xl border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 px-3 py-1.5 text-xs font-medium text-slate-800 dark:text-slate-200 focus:outline-none focus:ring-2 focus:ring-indigo-500/20"
            >
              <option value="">All Sales Reps</option>
              {forecast?.reps.map((r) => (
                <option key={r.userId} value={r.userId}>
                  {r.userName}
                </option>
              ))}
            </select>
          </div>

          {/* Set Quota Button */}
          <button
            onClick={() => openQuotaModalForUser()}
            className="flex items-center gap-1.5 rounded-xl bg-slate-100 hover:bg-slate-200 dark:bg-slate-800 dark:hover:bg-slate-700 px-3 py-1.5 text-xs font-semibold text-slate-700 dark:text-slate-200 transition"
          >
            <FiTarget className="size-3.5 text-indigo-500" />
            Set Quotas
          </button>

          {/* Refresh */}
          <button
            onClick={() => setRefreshKey((n) => n + 1)}
            disabled={loading}
            className="rounded-xl p-2 text-slate-400 hover:text-slate-600 dark:hover:text-slate-200 hover:bg-slate-100 dark:hover:bg-slate-800 transition"
            title="Refresh forecast"
          >
            <FiRefreshCw className={`size-3.5 ${loading ? 'animate-spin' : ''}`} />
          </button>
        </div>
      </div>

      {loading && !forecast ? (
        <PageSkeleton />
      ) : error ? (
        <div className="rounded-xl bg-rose-50 dark:bg-rose-950/40 border border-rose-200 dark:border-rose-900 p-4 text-sm text-rose-700 dark:text-rose-300">
          {error}
        </div>
      ) : s && forecast ? (
        <>
          {/* Executive KPI Scorecards */}
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-5 gap-4">
            {/* Total Forecast (Committed) */}
            <div className="rounded-2xl bg-gradient-to-br from-indigo-500 to-indigo-700 text-white p-5 shadow-sm relative overflow-hidden">
              <div className="relative z-10">
                <span className="text-[11px] font-semibold uppercase tracking-wider text-indigo-100">
                  Total Forecast
                </span>
                <p className="mt-2 text-2xl font-black tracking-tight">
                  {dealMoney(String(s.totalForecast), s.currency)}
                </p>
                <div className="mt-2.5 flex items-center gap-1 text-[11px] text-indigo-100">
                  <FiCheckCircle className="size-3 shrink-0" />
                  <span>Won + Weighted Open Pipeline</span>
                </div>
              </div>
              <div className="absolute -right-3 -bottom-3 size-24 rounded-full bg-white/10 blur-xl pointer-events-none" />
            </div>

            {/* Weighted Open Pipeline */}
            <div className="rounded-2xl bg-white dark:bg-slate-900 p-5 border border-slate-200 dark:border-slate-800 shadow-sm">
              <span className="text-[11px] font-semibold uppercase tracking-wider text-slate-500">
                Weighted Pipeline
              </span>
              <p className="mt-2 text-2xl font-black text-indigo-600 dark:text-indigo-400 tracking-tight">
                {dealMoney(String(s.weightedPipeline), s.currency)}
              </p>
              <div className="mt-2.5 flex items-center gap-1.5 text-xs text-slate-500">
                <FiPercent className="size-3 text-indigo-500 shrink-0" />
                <span>Adjusted by win probabilities</span>
              </div>
            </div>

            {/* Unweighted Nominal Pipeline */}
            <div className="rounded-2xl bg-white dark:bg-slate-900 p-5 border border-slate-200 dark:border-slate-800 shadow-sm">
              <span className="text-[11px] font-semibold uppercase tracking-wider text-slate-500">
                Unweighted Pipeline
              </span>
              <p className="mt-2 text-2xl font-black text-slate-900 dark:text-slate-100 tracking-tight">
                {dealMoney(String(s.unweightedPipeline), s.currency)}
              </p>
              <p className="mt-2.5 text-xs text-slate-500">
                Across <span className="font-semibold text-slate-800 dark:text-slate-200">{s.totalOpenDeals}</span> open deals
              </p>
            </div>

            {/* Closed Won Revenue */}
            <div className="rounded-2xl bg-white dark:bg-slate-900 p-5 border border-slate-200 dark:border-slate-800 shadow-sm">
              <span className="text-[11px] font-semibold uppercase tracking-wider text-slate-500">
                Closed-Won Deals
              </span>
              <p className="mt-2 text-2xl font-black text-emerald-600 dark:text-emerald-400 tracking-tight">
                {dealMoney(String(s.wonAmount), s.currency)}
              </p>
              <div className="mt-2.5 flex items-center gap-2 text-xs text-slate-500">
                <span className="font-semibold text-emerald-600 dark:text-emerald-400">
                  {s.wonDealsCount} won
                </span>
                <span>·</span>
                <span>{s.winRatePct}% win rate</span>
              </div>
            </div>

            {/* Quota Attainment */}
            <div className="rounded-2xl bg-white dark:bg-slate-900 p-5 border border-slate-200 dark:border-slate-800 shadow-sm">
              <div className="flex items-center justify-between">
                <span className="text-[11px] font-semibold uppercase tracking-wider text-slate-500">
                  Target Quota
                </span>
                <span className="text-xs font-bold text-slate-800 dark:text-slate-200">
                  {s.quotaAttainmentPct}%
                </span>
              </div>
              <p className="mt-2 text-2xl font-black text-slate-900 dark:text-slate-100 tracking-tight">
                {Number(s.targetQuota) > 0 ? dealMoney(String(s.targetQuota), s.currency) : 'Not Set'}
              </p>
              <div className="mt-2.5">
                <div className="w-full h-1.5 rounded-full bg-slate-100 dark:bg-slate-800 overflow-hidden">
                  <div
                    className={`h-full rounded-full transition-all duration-500 ${
                      s.quotaAttainmentPct >= 100
                        ? 'bg-emerald-500'
                        : s.quotaAttainmentPct >= 70
                        ? 'bg-indigo-500'
                        : 'bg-amber-500'
                    }`}
                    style={{ width: `${Math.min(s.quotaAttainmentPct, 100)}%` }}
                  />
                </div>
                <p className="mt-1 text-[10px] text-slate-400">
                  Projected: {s.projectedAttainmentPct}%
                </p>
              </div>
            </div>
          </div>

          {/* Section 1: Stage Probability Matrix & Close Date Timeline */}
          <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
            {/* Stage Probability Funnel */}
            <div className="lg:col-span-2 rounded-2xl bg-white dark:bg-slate-900 p-6 border border-slate-200 dark:border-slate-800 shadow-sm">
              <div className="flex items-center justify-between mb-4">
                <div>
                  <h2 className="text-base font-bold text-slate-900 dark:text-slate-100">
                    Pipeline Stages & Probabilities
                  </h2>
                  <p className="text-xs text-slate-500">
                    How stage win percentages weight deal values for {forecast.periodLabel}
                  </p>
                </div>
              </div>

              <div className="space-y-3.5">
                {forecast.stages.map((stage: CrmForecastStage) => {
                  const maxAmt = Math.max(
                    ...forecast.stages.map((st) => Number(st.totalAmount) || 1),
                    1,
                  )
                  const pctWidth = Math.min(
                    100,
                    Math.max(4, Math.round(((Number(stage.totalAmount) || 0) / maxAmt) * 100)),
                  )

                  return (
                    <div
                      key={stage.stageKey}
                      className="rounded-xl p-3.5 bg-slate-50/70 dark:bg-slate-800/50 border border-slate-100 dark:border-slate-800/80 hover:border-slate-200 transition"
                    >
                      <div className="flex items-center justify-between gap-2 text-xs">
                        <div className="flex items-center gap-2">
                          <span
                            className={`rounded-md px-2 py-0.5 text-[11px] font-bold ${dealStageColor(
                              stage.stageKey,
                              stage.color,
                            )}`}
                          >
                            {stage.stageName}
                          </span>
                          <span className="rounded-full bg-slate-200/80 dark:bg-slate-700 px-2 py-0.5 text-[10px] font-semibold text-slate-600 dark:text-slate-300">
                            {stage.probability}% Win Prob
                          </span>
                        </div>
                        <div className="flex items-center gap-4 text-right">
                          <span className="text-slate-500 font-medium">
                            {stage.dealsCount} {stage.dealsCount === 1 ? 'deal' : 'deals'}
                          </span>
                          <div className="min-w-[120px]">
                            <span className="text-[11px] text-slate-400 line-through mr-1.5">
                              {dealMoney(String(stage.totalAmount), s.currency)}
                            </span>
                            <span className="font-bold text-slate-900 dark:text-slate-100">
                              {dealMoney(String(stage.weightedAmount), s.currency)}
                            </span>
                          </div>
                        </div>
                      </div>

                      {/* Visual bar */}
                      <div className="mt-2.5 w-full h-2 rounded-full bg-slate-200 dark:bg-slate-700/60 overflow-hidden flex">
                        <div
                          className="h-full bg-indigo-500 rounded-full transition-all duration-300"
                          style={{ width: `${pctWidth}%` }}
                        />
                      </div>
                    </div>
                  )
                })}
              </div>
            </div>

            {/* Expected Close Date Cohorts Timeline */}
            <div className="rounded-2xl bg-white dark:bg-slate-900 p-6 border border-slate-200 dark:border-slate-800 shadow-sm flex flex-col justify-between">
              <div>
                <div className="flex items-center justify-between mb-4">
                  <h2 className="text-base font-bold text-slate-900 dark:text-slate-100 flex items-center gap-2">
                    <FiCalendar className="text-indigo-500" />
                    Close-Date Timeline
                  </h2>
                </div>
                <p className="text-xs text-slate-500 mb-4">
                  Projected revenue distribution by close month
                </p>

                {!forecast.timeline.length ? (
                  <p className="text-xs text-slate-400 py-6 text-center">
                    No close dates scheduled in this timeframe.
                  </p>
                ) : (
                  <div className="space-y-3">
                    {forecast.timeline.map((cohort: CrmForecastTimeline) => (
                      <div
                        key={cohort.periodKey}
                        className="rounded-xl border border-slate-100 dark:border-slate-800 p-3 bg-slate-50/50 dark:bg-slate-800/40"
                      >
                        <div className="flex items-center justify-between text-xs font-semibold text-slate-800 dark:text-slate-200 mb-1.5">
                          <span>{cohort.periodLabel}</span>
                          <span className="text-indigo-600 dark:text-indigo-400 font-bold">
                            {dealMoney(String(cohort.weightedAmount), s.currency)}
                          </span>
                        </div>
                        <div className="flex items-center justify-between text-[11px] text-slate-500">
                          <span>{cohort.openDealsCount} open deals</span>
                          {Number(cohort.wonAmount) > 0 && (
                            <span className="text-emerald-600 dark:text-emerald-400 font-medium">
                              +{dealMoney(String(cohort.wonAmount), s.currency)} won
                            </span>
                          )}
                        </div>
                      </div>
                    ))}
                  </div>
                )}
              </div>

              <div className="mt-4 rounded-xl bg-indigo-50 dark:bg-indigo-950/40 p-3 text-xs text-indigo-700 dark:text-indigo-300">
                <p className="font-semibold mb-0.5">💡 Forecast Formula</p>
                <p className="text-[11px] text-indigo-600/90 dark:text-indigo-400">
                  Weighted Value = Deal Amount × Stage Win % (e.g., ₹1,00,000 at Proposal 50% = ₹50,000).
                </p>
              </div>
            </div>
          </div>

          {/* Section 2: Sales Rep Leaderboard & Quotas */}
          <div className="rounded-2xl bg-white dark:bg-slate-900 p-6 border border-slate-200 dark:border-slate-800 shadow-sm">
            <div className="flex flex-wrap items-center justify-between gap-4 mb-4">
              <div>
                <h2 className="text-base font-bold text-slate-900 dark:text-slate-100 flex items-center gap-2">
                  <FiAward className="text-amber-500" />
                  Sales Rep Quotas & Weighted Attainment
                </h2>
                <p className="text-xs text-slate-500">
                  Performance across active team members for {forecast.periodLabel}
                </p>
              </div>
              <button
                onClick={() => openQuotaModalForUser()}
                className="rounded-xl border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 px-3 py-1.5 text-xs font-semibold text-slate-700 dark:text-slate-200 hover:bg-slate-50 transition"
              >
                + Assign Target Quota
              </button>
            </div>

            {!forecast.reps.length ? (
              <p className="text-xs text-slate-400 py-6 text-center">
                No deals assigned to sales reps for this period.
              </p>
            ) : (
              <div className="overflow-x-auto">
                <table className="w-full text-left text-xs">
                  <thead className="bg-slate-50 dark:bg-slate-800/60 text-slate-500 font-semibold uppercase tracking-wider">
                    <tr>
                      <th className="p-3.5">Sales Rep</th>
                      <th className="p-3.5">Open Deals</th>
                      <th className="p-3.5">Unweighted Pipeline</th>
                      <th className="p-3.5">Weighted Pipeline</th>
                      <th className="p-3.5">Closed-Won</th>
                      <th className="p-3.5">Target Quota</th>
                      <th className="p-3.5">Attainment</th>
                      <th className="p-3.5 text-right">Actions</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-100 dark:divide-slate-800 font-medium">
                    {forecast.reps.map((rep: CrmForecastRep) => (
                      <tr key={rep.userId} className="hover:bg-slate-50/50 dark:hover:bg-slate-800/40">
                        <td className="p-3.5 font-bold text-slate-900 dark:text-slate-100">
                          {rep.userName}
                        </td>
                        <td className="p-3.5 text-slate-600 dark:text-slate-400">{rep.openDealsCount}</td>
                        <td className="p-3.5 text-slate-500 line-through">
                          {dealMoney(String(rep.unweightedAmount), s.currency)}
                        </td>
                        <td className="p-3.5 font-bold text-indigo-600 dark:text-indigo-400">
                          {dealMoney(String(rep.weightedAmount), s.currency)}
                        </td>
                        <td className="p-3.5 text-emerald-600 dark:text-emerald-400 font-semibold">
                          {dealMoney(String(rep.wonAmount), s.currency)} ({rep.wonDealsCount})
                        </td>
                        <td className="p-3.5 text-slate-700 dark:text-slate-300">
                          {Number(rep.targetQuota) > 0 ? dealMoney(String(rep.targetQuota), s.currency) : '—'}
                        </td>
                        <td className="p-3.5 min-w-[130px]">
                          {Number(rep.targetQuota) > 0 ? (
                            <div>
                              <div className="flex items-center justify-between text-[11px] font-bold mb-1">
                                <span
                                  className={
                                    rep.quotaAttainmentPct >= 100
                                      ? 'text-emerald-600'
                                      : rep.quotaAttainmentPct >= 70
                                      ? 'text-indigo-600'
                                      : 'text-amber-600'
                                  }
                                >
                                  {rep.quotaAttainmentPct}%
                                </span>
                                <span className="text-[10px] text-slate-400">
                                  Proj: {rep.projectedAttainmentPct}%
                                </span>
                              </div>
                              <div className="h-1.5 w-full rounded-full bg-slate-200 dark:bg-slate-700 overflow-hidden">
                                <div
                                  className={`h-full rounded-full ${
                                    rep.quotaAttainmentPct >= 100
                                      ? 'bg-emerald-500'
                                      : rep.quotaAttainmentPct >= 70
                                      ? 'bg-indigo-500'
                                      : 'bg-amber-500'
                                  }`}
                                  style={{ width: `${Math.min(rep.quotaAttainmentPct, 100)}%` }}
                                />
                              </div>
                            </div>
                          ) : (
                            <span className="text-slate-400">No quota set</span>
                          )}
                        </td>
                        <td className="p-3.5 text-right">
                          <button
                            onClick={() => openQuotaModalForUser(rep.userId)}
                            className="text-xs text-indigo-600 hover:text-indigo-800 dark:text-indigo-400 dark:hover:text-indigo-300 font-semibold"
                          >
                            Set Quota
                          </button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>

          {/* Section 3: Opportunities in this Forecast */}
          <div className="rounded-2xl bg-white dark:bg-slate-900 p-6 border border-slate-200 dark:border-slate-800 shadow-sm">
            <div className="flex items-center justify-between mb-4">
              <div>
                <h2 className="text-base font-bold text-slate-900 dark:text-slate-100 flex items-center gap-2">
                  <FiBriefcase className="text-indigo-500" />
                  Opportunities in this Forecast
                </h2>
                <p className="text-xs text-slate-500">
                  {forecast.deals.length} deals analyzed based on stage probability and close dates
                </p>
              </div>
            </div>

            {!forecast.deals.length ? (
              <p className="text-xs text-slate-400 py-6 text-center">
                No deals match the selected timeframe and filters.
              </p>
            ) : (
              <div className="overflow-x-auto">
                <table className="w-full text-left text-xs">
                  <thead className="bg-slate-50 dark:bg-slate-800/60 text-slate-500 font-semibold uppercase tracking-wider">
                    <tr>
                      <th className="p-3">Deal Title</th>
                      <th className="p-3">Account / Contact</th>
                      <th className="p-3">Stage</th>
                      <th className="p-3">Win %</th>
                      <th className="p-3">Expected Close</th>
                      <th className="p-3">Sales Rep</th>
                      <th className="p-3 text-right">Face Amount</th>
                      <th className="p-3 text-right">Weighted Amount</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-100 dark:divide-slate-800 font-medium">
                    {forecast.deals.map((deal: CrmForecastDeal) => (
                      <tr key={deal.id} className="hover:bg-slate-50/50 dark:hover:bg-slate-800/40">
                        <td className="p-3 font-bold text-slate-900 dark:text-slate-100">
                          {deal.title}
                        </td>
                        <td className="p-3 text-slate-600 dark:text-slate-400">
                          {deal.accountName || deal.contactName || '—'}
                        </td>
                        <td className="p-3">
                          <span
                            className={`rounded-md px-2 py-0.5 text-[10px] font-bold ${dealStageColor(
                              deal.stage,
                            )}`}
                          >
                            {deal.stageName}
                          </span>
                        </td>
                        <td className="p-3 font-semibold text-slate-700 dark:text-slate-300">
                          {deal.probability}%
                        </td>
                        <td className="p-3 text-slate-500">
                          {deal.expectedCloseDate || 'Not Set'}
                        </td>
                        <td className="p-3 text-slate-600 dark:text-slate-400">
                          {deal.ownerName}
                        </td>
                        <td className="p-3 text-right text-slate-600 dark:text-slate-400">
                          {dealMoney(String(deal.amount), deal.currency)}
                        </td>
                        <td className="p-3 text-right font-bold text-indigo-600 dark:text-indigo-400">
                          {dealMoney(String(deal.weightedAmount), deal.currency)}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        </>
      ) : null}

      {/* Set Quota Modal */}
      {quotaModalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/60 backdrop-blur-xs p-4 animate-in fade-in duration-150">
          <div className="w-full max-w-md rounded-2xl bg-white dark:bg-slate-900 p-6 shadow-2xl border border-slate-200 dark:border-slate-800">
            <div className="flex items-center justify-between pb-4 border-b border-slate-100 dark:border-slate-800">
              <div className="flex items-center gap-2">
                <FiTarget className="size-5 text-indigo-600" />
                <h3 className="text-base font-bold text-slate-900 dark:text-slate-100">
                  Assign Sales Quota Target
                </h3>
              </div>
              <button
                onClick={() => setQuotaModalOpen(false)}
                className="text-slate-400 hover:text-slate-600 dark:hover:text-slate-200"
              >
                <FiX className="size-5" />
              </button>
            </div>

            <form onSubmit={handleSaveQuota} className="mt-4 space-y-4">
              <div>
                <label className="block text-xs font-semibold uppercase tracking-wider text-slate-600 dark:text-slate-300 mb-1">
                  Sales Rep *
                </label>
                <select
                  required
                  value={quotaTargetUser}
                  onChange={(e) => setQuotaTargetUser(e.target.value)}
                  className="w-full rounded-xl border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-800 px-3 py-2 text-sm text-slate-900 dark:text-slate-100 focus:outline-none focus:ring-2 focus:ring-indigo-500/20"
                >
                  <option value="">Select teammate...</option>
                  {forecast?.reps.map((r) => (
                    <option key={r.userId} value={r.userId}>
                      {r.userName}
                    </option>
                  ))}
                </select>
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-xs font-semibold uppercase tracking-wider text-slate-600 dark:text-slate-300 mb-1">
                    Cadence
                  </label>
                  <select
                    value={quotaPeriodType}
                    onChange={(e) => {
                      const val = e.target.value
                      setQuotaPeriodType(val)
                      const now = new Date()
                      if (val === 'QUARTERLY') {
                        const q = Math.ceil((now.getMonth() + 1) / 3)
                        setQuotaPeriodKey(`${now.getFullYear()}-Q${q}`)
                      } else {
                        setQuotaPeriodKey(
                          `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`,
                        )
                      }
                    }}
                    className="w-full rounded-xl border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-800 px-3 py-2 text-sm text-slate-900 dark:text-slate-100 focus:outline-none focus:ring-2 focus:ring-indigo-500/20"
                  >
                    <option value="MONTHLY">Monthly</option>
                    <option value="QUARTERLY">Quarterly</option>
                  </select>
                </div>

                <div>
                  <label className="block text-xs font-semibold uppercase tracking-wider text-slate-600 dark:text-slate-300 mb-1">
                    Period Key (YYYY-MM or YYYY-Q#)
                  </label>
                  <input
                    type="text"
                    required
                    value={quotaPeriodKey}
                    onChange={(e) => setQuotaPeriodKey(e.target.value)}
                    placeholder="e.g. 2026-10"
                    className="w-full rounded-xl border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-800 px-3 py-2 text-sm text-slate-900 dark:text-slate-100 focus:outline-none focus:ring-2 focus:ring-indigo-500/20"
                  />
                </div>
              </div>

              <div>
                <label className="block text-xs font-semibold uppercase tracking-wider text-slate-600 dark:text-slate-300 mb-1">
                  Target Amount ({forecast?.summary.currency || currency}) *
                </label>
                <input
                  type="number"
                  min="0"
                  step="any"
                  required
                  value={quotaAmount}
                  onChange={(e) => setQuotaAmount(e.target.value)}
                  placeholder="e.g. 500000"
                  className="w-full rounded-xl border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-800 px-3 py-2 text-sm text-slate-900 dark:text-slate-100 focus:outline-none focus:ring-2 focus:ring-indigo-500/20"
                />
              </div>

              {quotaMsg && (
                <p
                  className={`text-xs ${
                    quotaMsg.includes('success')
                      ? 'text-emerald-600 dark:text-emerald-400'
                      : 'text-rose-600 dark:text-rose-400'
                  }`}
                >
                  {quotaMsg}
                </p>
              )}

              <div className="flex items-center justify-end gap-2.5 pt-3">
                <button
                  type="button"
                  onClick={() => setQuotaModalOpen(false)}
                  className="rounded-xl px-4 py-2 text-xs font-semibold text-slate-600 dark:text-slate-400 hover:bg-slate-100 dark:hover:bg-slate-800 transition"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={quotaSubmitting}
                  className="rounded-xl bg-indigo-600 hover:bg-indigo-700 text-white px-4 py-2 text-xs font-semibold shadow-sm transition disabled:opacity-50"
                >
                  {quotaSubmitting ? 'Saving...' : 'Save Target Quota'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  )
}
