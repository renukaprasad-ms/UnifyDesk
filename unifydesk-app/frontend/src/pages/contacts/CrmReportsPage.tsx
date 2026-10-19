import { PageSkeleton } from '../../components/ui/Skeleton'
import FilterPopover from '../../components/ui/FilterPopover'
import { useEffect, useState } from 'react'
import { FiBarChart2, FiDownload, FiRefreshCw, FiTrendingUp, FiPieChart, FiTarget } from 'react-icons/fi'
import { BreakdownChart } from '../../components/ui/OverviewCharts'
import {
  crmSalesApi,
  dealMoney,
  salesLabel,
  type CrmReport,
} from '../../features/contacts/crmSalesApi'
import { useWorkspaceEpochScope } from '../../features/workspace/useWorkspaceEpochScope'
import { apiErrorMessage } from '../../lib/api/apiClient'
import { useAppSelector } from '../../store/hooks'
import { selectSelectedCompanyId, selectWorkspaceEpoch } from '../../store/workspace/workspaceSlice'
import CrmForecastView from '../../features/contacts/CrmForecastView'
import CrmAttributionView from '../../features/contacts/CrmAttributionView'

function exportReport(report: CrmReport) {
  const safe = (value: string | number) =>
    '"' +
    String(value)
      .replace(/^[=+@\-\t\r]/, "'$&")
      .replaceAll('"', '""') +
    '"'
  const rows: (string | number)[][] = [['Section', 'Metric', 'Value', 'Currency', 'Period / date']]
  for (const section of ['leads', 'deals', 'tasks'] as const)
    for (const item of report[section])
      rows.push([section, item.label, item.value, '', 'Current snapshot'])
  rows.push(['tasks', 'Overdue', report.overdueTasks, '', 'Current snapshot'])
  for (const value of report.values) {
    rows.push(['pipeline', 'Open value', value.openValue, value.currency, 'Current snapshot'])
    rows.push(['pipeline', 'Won value', value.wonValue, value.currency, 'Current snapshot'])
    rows.push(['pipeline', 'Lost value', value.lostValue, value.currency, 'Current snapshot'])
  }
  for (const point of report.trend) {
    rows.push(['trend', 'New leads', point.leads, '', point.date])
    rows.push(['trend', 'New deals', point.deals, '', point.date])
    rows.push(['trend', 'Task completions', point.completedTasks, '', point.date])
  }
  for (const user of report.team)
    rows.push(['team', user.name, user.actions, '', `Last ${report.days} days`])
  const url = URL.createObjectURL(
    new Blob(['\ufeff' + rows.map((r) => r.map(safe).join(',')).join('\r\n')], {
      type: 'text/csv;charset=utf-8',
    }),
  )
  const link = document.createElement('a')
  link.href = url
  link.download = `crm-report-${report.days}d.csv`
  link.click()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}

export default function CrmReportsPage() {
  const companyId = useAppSelector(selectSelectedCompanyId),
    epoch = useAppSelector(selectWorkspaceEpoch)
  const { capture, isCurrent } = useWorkspaceEpochScope(companyId, epoch)
  const [activeTab, setActiveTab] = useState<'forecast' | 'attribution' | 'overview'>('forecast')
  const [days, setDays] = useState(30),
    [reload, setReload] = useState(0)
  const key = `${companyId}:${epoch}:${days}:${reload}`
  const [result, setResult] = useState<{
    key: string
    report: CrmReport | null
    error: string
  } | null>(null)
  const loading = result?.key !== key,
    report = !loading ? result.report : null
  useEffect(() => {
    if (result?.key === key && result.error) showGlobalSnackbar(result.error, { variant: 'error' })
  }, [result, key])
  useEffect(() => {
    if (!companyId) return
    let active = true
    const scope = capture()
    void crmSalesApi
      .report(companyId, days)
      .then((report) => {
        if (active && isCurrent(scope)) setResult({ key, report, error: '' })
      })
      .catch((e) => {
        if (active && isCurrent(scope))
          setResult({ key, report: null, error: apiErrorMessage(e, 'Unable to load CRM reports') })
      })
    return () => {
      active = false
    }
  }, [companyId, days, key, capture, isCurrent])
  const count = (items: CrmReport['leads']) => items.reduce((sum, item) => sum + item.value, 0)
  const totalLeads = report ? count(report.leads) : 0
  const converted = report?.leads.find((l) => l.label === 'CONVERTED')?.value || 0
  const openDeals =
    report?.deals
      .filter((d) => !['WON', 'LOST'].includes(d.label))
      .reduce((sum, d) => sum + d.value, 0) || 0
  const weekly = report
    ? Array.from({ length: Math.ceil(report.trend.length / (days <= 7 ? 1 : 7)) }, (_, index) => {
        const bucket = days <= 7 ? 1 : 7
        const points = report.trend.slice(index * bucket, index * bucket + bucket)
        return {
          label:
            bucket === 1 ? points[0].date : `${points[0].date} – ${points[points.length - 1].date}`,
          value: points.reduce((sum, p) => sum + p.leads, 0),
        }
      })
    : []
  return (
    <section className="space-y-5 px-4 py-6 sm:px-6">
      <header className="flex flex-wrap items-center justify-between gap-4">
        <div>
          <p className="eyebrow">CRM INTELLIGENCE</p>
          <h1 className="mt-2 text-3xl font-bold text-slate-950 dark:text-slate-100">Sales Intelligence & Reports</h1>
          <p className="mt-1 text-sm text-slate-500">
            Weighted pipeline forecasting, closed-loop campaign attribution, conversion, and team activity.
          </p>
        </div>

        {/* View Switcher Tabs */}
        <div className="flex items-center gap-1 rounded-xl bg-slate-100 dark:bg-slate-800 p-1">
          <button
            onClick={() => setActiveTab('forecast')}
            className={`flex items-center gap-2 rounded-lg px-3.5 py-2 text-xs font-semibold transition ${
              activeTab === 'forecast'
                ? 'bg-white dark:bg-slate-900 text-indigo-600 dark:text-indigo-400 shadow-sm'
                : 'text-slate-600 dark:text-slate-400 hover:text-slate-900 dark:hover:text-slate-200'
            }`}
          >
            <FiTrendingUp className="size-4" />
            <span>Sales Forecast & Quotas</span>
          </button>
          <button
            onClick={() => setActiveTab('attribution')}
            className={`flex items-center gap-2 rounded-lg px-3.5 py-2 text-xs font-semibold transition ${
              activeTab === 'attribution'
                ? 'bg-white dark:bg-slate-900 text-indigo-600 dark:text-indigo-400 shadow-sm'
                : 'text-slate-600 dark:text-slate-400 hover:text-slate-900 dark:hover:text-slate-200'
            }`}
          >
            <FiTarget className="size-4" />
            <span>Campaign Attribution & ROI</span>
          </button>
          <button
            onClick={() => setActiveTab('overview')}
            className={`flex items-center gap-2 rounded-lg px-3.5 py-2 text-xs font-semibold transition ${
              activeTab === 'overview'
                ? 'bg-white dark:bg-slate-900 text-indigo-600 dark:text-indigo-400 shadow-sm'
                : 'text-slate-600 dark:text-slate-400 hover:text-slate-900 dark:hover:text-slate-200'
            }`}
          >
            <FiPieChart className="size-4" />
            <span>Activity Overview</span>
          </button>
        </div>
      </header>

      {activeTab === 'forecast' ? (
        companyId ? (
          <CrmForecastView companyId={companyId} />
        ) : null
      ) : activeTab === 'attribution' ? (
        companyId ? (
          <CrmAttributionView companyId={companyId} />
        ) : null
      ) : (
        <>
          <div className="flex justify-end gap-2">
            <FilterPopover activeCount={Number(days !== 30)} onReset={() => setDays(30)}>
              <label className="text-xs font-semibold">
                Activity period{' '}
                <select
                  aria-label="Report activity period"
                  className="form-input"
                  value={days}
                  onChange={(e) => setDays(Number(e.target.value))}
                >
                  {[7, 30, 90].map((d) => (
                    <option key={d} value={d}>
                      Last {d} days
                    </option>
                  ))}
                </select>
              </label>
            </FilterPopover>
            <button
              className="secondary-button gap-2"
              disabled={loading}
              onClick={() => setReload((n) => n + 1)}
            >
              <FiRefreshCw />
              Refresh
            </button>
            <button
              className="secondary-button gap-2"
              disabled={!report}
              onClick={() => report && exportReport(report)}
            >
              <FiDownload />
              Export CSV
            </button>
          </div>
      {loading ? (
        <PageSkeleton />
      ) : result?.error ? (
        <p role="alert" className="rounded-xl bg-rose-50 p-4 text-sm text-rose-700">
          {result.error}
        </p>
      ) : (
        report && (
          <>
            <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
              {[
                {
                  title: 'Current leads',
                  value: totalLeads.toLocaleString(),
                  note: 'Active contacts across all stages',
                },
                {
                  title: 'Lead conversion',
                  value: `${totalLeads ? ((converted / totalLeads) * 100).toFixed(1) : '0'}%`,
                  note: `${converted.toLocaleString()} currently converted`,
                },
                {
                  title: 'Open opportunities',
                  value: openDeals.toLocaleString(),
                  note: 'Excludes won and lost deals',
                },
                {
                  title: 'Overdue follow-ups',
                  value: report.overdueTasks.toLocaleString(),
                  note: 'Open or in progress past their due date',
                },
              ].map((kpi) => (
                <div key={kpi.title} className="panel p-5">
                  <p className="text-xs font-semibold text-slate-500">{kpi.title}</p>
                  <p className="mt-4 text-3xl font-bold text-slate-950">{kpi.value}</p>
                  <p className="mt-2 text-xs text-slate-500">{kpi.note}</p>
                </div>
              ))}
            </div>
            <p className="text-xs text-slate-500">
              Stage counts and values are current snapshots. Growth and team activity cover the
              selected period; daily buckets use UTC.
            </p>
            <div className="grid gap-4 xl:grid-cols-3">
              <BreakdownChart
                title="Lead stages"
                description="Current lead distribution"
                donut
                items={['NEW', 'CONTACTED', 'QUALIFIED', 'UNQUALIFIED', 'CONVERTED'].map((s) => ({
                  label: salesLabel(s),
                  value: report.leads.find((l) => l.label === s)?.value || 0,
                }))}
              />
              <BreakdownChart
                title="Deal pipeline"
                description="Current opportunity counts by stage"
                items={['NEW', 'QUALIFIED', 'PROPOSAL', 'NEGOTIATION', 'WON', 'LOST'].map((s) => ({
                  label: salesLabel(s),
                  value: report.deals.find((d) => d.label === s)?.value || 0,
                }))}
              />
              <BreakdownChart
                title="Follow-up status"
                description="Current task workload"
                items={['OPEN', 'IN_PROGRESS', 'DONE', 'CANCELLED'].map((s) => ({
                  label: salesLabel(s),
                  value: report.tasks.find((t) => t.label === s)?.value || 0,
                }))}
              />
            </div>
            <section className="panel overflow-hidden">
              <div className="p-5">
                <h2 className="text-base font-bold text-slate-950">Pipeline value by currency</h2>
                <p className="mt-1 text-xs text-slate-500">
                  Opportunity values, not payments or recognized revenue. Currencies are kept
                  separate.
                </p>
              </div>
              {!report.values.length ? (
                <p className="px-5 pb-6 text-sm text-slate-500">
                  Create your first deal to see pipeline value.
                </p>
              ) : (
                <div className="overflow-x-auto">
                  <table className="w-full text-left text-sm">
                    <thead className="bg-slate-50 text-xs text-slate-500">
                      <tr>
                        {[
                          'Currency',
                          'Open pipeline',
                          'Won opportunities',
                          'Lost opportunities',
                        ].map((h) => (
                          <th key={h} className="p-4">
                            {h}
                          </th>
                        ))}
                      </tr>
                    </thead>
                    <tbody>
                      {report.values.map((v) => (
                        <tr key={v.currency} className="border-t border-slate-100">
                          <td className="p-4 font-bold">{v.currency}</td>
                          <td className="p-4">{dealMoney(v.openValue, v.currency)}</td>
                          <td className="p-4 text-emerald-600">
                            {dealMoney(v.wonValue, v.currency)}
                          </td>
                          <td className="p-4 text-slate-500">
                            {dealMoney(v.lostValue, v.currency)}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </section>
            <div className="grid gap-4 xl:grid-cols-2">
              <BreakdownChart
                title="New leads over time"
                description={`New leads in ${days <= 7 ? 'daily' : 'weekly'} buckets · last ${days} days`}
                items={weekly}
              />
              <BreakdownChart
                title="Team activity"
                description={`Top 20 teammates by recorded lead, task, deal and note changes · last ${days} days`}
                items={report.team.map((u) => ({
                  label: u.name || `User ${u.userId}`,
                  value: u.actions,
                }))}
              />
            </div>
            <section className="panel p-5">
              <h2 className="flex items-center gap-2 text-base font-bold text-slate-950">
                <FiBarChart2 />
                Activity in the selected period
              </h2>
              <div className="mt-4 grid gap-4 sm:grid-cols-3">
                {[
                  { label: 'New leads', value: report.trend.reduce((s, p) => s + p.leads, 0) },
                  {
                    label: 'New opportunities',
                    value: report.trend.reduce((s, p) => s + p.deals, 0),
                  },
                  {
                    label: 'Task completion events',
                    value: report.trend.reduce((s, p) => s + p.completedTasks, 0),
                  },
                ].map((item) => (
                  <div key={item.label} className="rounded-xl bg-slate-50 p-4">
                    <p className="text-xs text-slate-500">{item.label}</p>
                    <p className="mt-2 text-2xl font-bold text-slate-950">
                      {item.value.toLocaleString()}
                    </p>
                  </div>
                ))}
              </div>
              <p className="mt-4 text-xs text-slate-500">
                Generated {new Date(report.generatedAt).toLocaleString()}. Task history metrics
                include changes recorded after task activity tracking was enabled.
              </p>
            </section>
          </>
        )
      )}
        </>
      )}
    </section>
  )
}
import { showGlobalSnackbar } from '../../components/feedback/SnackbarProvider'
