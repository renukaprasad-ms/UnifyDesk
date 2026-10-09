import { useEffect, useMemo, useState } from 'react'
import { adminBillingApi } from '../../features/platformBilling/adminBillingApi'
import type { BillingFinancialReport } from '../../features/platformBilling/types'

type Props = { companyNames: Record<string, string> }
const inr = (value: number) => new Intl.NumberFormat('en-IN', {
  style: 'currency', currency: 'INR', minimumFractionDigits: 2, maximumFractionDigits: 2,
}).format(value)

export default function BillingFinancialOverview({ companyNames }: Props) {
  const [days, setDays] = useState(30)
  const [reload, setReload] = useState(0)
  const [report, setReport] = useState<BillingFinancialReport | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  useEffect(() => {
    let active = true
    const to = new Date()
    const from = new Date(to.getTime() - days * 86400000)
    setLoading(true)
    setError('')
    adminBillingApi.financialAnalytics(from.toISOString(), to.toISOString())
      .then((data) => { if (active) setReport(data) })
      .catch((cause: unknown) => {
        if (active) {
          setReport(null)
          setError(cause instanceof Error ? cause.message : 'Unable to load financial records')
        }
      }).finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [days, reload])
  const maxBilled = useMemo(() => Math.max(1, ...(report?.daily ?? []).map((day) => day.invoicedInr)), [report])
  const exportRows = () => {
    if (!report) return
    const headers = ['company_id', 'company_name', 'invoiced_inr', 'collected_inr', 'refunded_inr', 'current_open_balance_inr']
    const escapeCsv = (value: string | number) => `"${String(value).replace(/"/g, '""')}"`
    const rows = report.companies.map((company) => [
      company.companyId, companyNames[company.companyId] || 'Unknown company',
      company.invoicedInr, company.collectedInr, company.refundedInr, company.openBalanceInr,
    ])
    const csv = [headers, ...rows].map((row) => row.map(escapeCsv).join(',')).join('\r\n')
    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' })
    const url = URL.createObjectURL(blob)
    try {
      const anchor = document.createElement('a')
      anchor.href = url
      anchor.download = `unifydesk-company-finances-${days}-days.csv`
      anchor.click()
    } finally { URL.revokeObjectURL(url) }
  }
  return <section className="panel mt-6 p-5" aria-label="Company financial analytics">
    <div className="flex flex-wrap items-start justify-between gap-3">
      <div>
        <h2 className="font-bold">Company revenue and payments</h2>
        <p className="mt-1 text-xs text-slate-500">Invoice totals and confirmed money movement. Not an accrual revenue recognition report. Open balances are current, independent of the date range.</p>
      </div>
      <div className="flex flex-wrap gap-2">
        <select aria-label="Financial reporting period" className="form-input" value={days} onChange={(event) => setDays(Number(event.target.value))}>
          <option value={7}>Last 7 days</option><option value={30}>Last 30 days</option><option value={90}>Last 90 days</option>
        </select>
        <button type="button" className="secondary-button" disabled={loading || !report} onClick={exportRows}>Export CSV</button>
        <button type="button" className="secondary-button" disabled={loading} onClick={() => setReload((value) => value + 1)}>Refresh</button>
      </div>
    </div>
    {loading && <p className="mt-5 text-sm" role="status">Loading financial data...</p>}
    {error && <p className="mt-5 text-sm text-red-600" role="alert">{error}</p>}
    {!loading && report && <>
      <div className="mt-5 grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
        {[
          ['Invoices issued', inr(report.totals.invoicedInr), 'Finalized invoices (tax included)'],
          ['Payments collected', inr(report.totals.collectedInr), 'Succeeded Cashfree payments'],
          ['Refunds paid', inr(report.totals.refundedInr), 'Succeeded refunds'],
          ['Current open balance', inr(report.totals.openBalanceInr), 'OPEN invoices, all periods'],
        ].map(([title, value, subtitle]) =>
          <div key={title} className="rounded-xl border p-4">
            <p className="text-xs text-slate-500">{title}</p>
            <p className="mt-2 text-xl font-bold tabular-nums">{value}</p>
            <p className="mt-1 text-xs text-slate-500">{subtitle}</p>
          </div>,
        )}
      </div>
      <p className="mt-3 text-sm font-semibold">Net confirmed payments after refunds: {inr(report.totals.collectedInr - report.totals.refundedInr)}</p>
      <div className="mt-6">
        <h3 className="font-semibold text-sm">Daily invoiced amounts (UTC)</h3>
        {report.daily.length ? <div className="mt-3 space-y-2">
          {report.daily.map((day) => <div key={day.day} className="flex items-center gap-3 text-xs">
            <span className="w-24 shrink-0">{day.day}</span>
            <div className="h-3 flex-1 rounded-full bg-slate-100">
              <div className="h-3 rounded-full bg-blue-500" style={{ width: `${day.invoicedInr / maxBilled * 100}%` }} />
            </div>
            <span className="w-28 text-right tabular-nums">{inr(day.invoicedInr)}</span>
          </div>)}
        </div> : <p className="mt-3 text-sm text-slate-500">No finalized invoices or payment activity in this period.</p>}
      </div>
      <div className="mt-6 overflow-x-auto">
        <h3 className="mb-3 font-semibold text-sm">Company financial breakdown</h3>
        <table className="w-full min-w-[680px] text-left text-sm">
          <thead><tr className="border-b text-xs text-slate-500">
            <th className="py-3 pr-3">Company</th><th className="px-3 text-right">Invoiced</th>
            <th className="px-3 text-right">Collected</th><th className="px-3 text-right">Refunded</th>
            <th className="px-3 text-right">Current open</th>
          </tr></thead>
          <tbody>{report.companies.map((company) => <tr key={company.companyId} className="border-b">
            <td className="py-3 pr-3">{companyNames[company.companyId] ?? `Company ${company.companyId}`}</td>
            <td className="px-3 text-right tabular-nums">{inr(company.invoicedInr)}</td>
            <td className="px-3 text-right tabular-nums">{inr(company.collectedInr)}</td>
            <td className="px-3 text-right tabular-nums">{inr(company.refundedInr)}</td>
            <td className="px-3 text-right tabular-nums">{inr(company.openBalanceInr)}</td>
          </tr>)}</tbody>
        </table>
        {!report.companies.length && <p className="mt-3 text-sm text-slate-500">No financial activity or open invoices.</p>}
      </div>
    </>}
  </section>
}
