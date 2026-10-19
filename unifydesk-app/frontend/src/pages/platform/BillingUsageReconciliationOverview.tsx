import { useSnackbarState } from '../../components/feedback/useSnackbarState'
import { useEffect, useMemo, useState } from 'react'
import { adminBillingApi } from '../../features/platformBilling/adminBillingApi'
import type { BillingUsageReconciliationReport } from '../../features/platformBilling/types'

type Props = { companyNames: Record<string, string> }
const amount = (value: number) => new Intl.NumberFormat('en-IN', {
  style: 'currency', currency: 'INR', maximumFractionDigits: 2,
}).format(value)
const int = (value: number) => new Intl.NumberFormat('en-IN').format(value)

export default function BillingUsageReconciliationOverview({ companyNames }: Props) {
  const [days, setDays] = useState(30)
  const [report, setReport] = useState<BillingUsageReconciliationReport | null>(null)
  const [loading, setLoading] = useState(true)
  const [, setError] = useSnackbarState('', 'error')
  const [reload, setReload] = useState(0)
  useEffect(() => {
    let active = true
    const to = new Date()
    const from = new Date(to.getTime() - days * 86400000)
    setLoading(true)
    setError('')
    adminBillingApi.usageReconciliation(from.toISOString(), to.toISOString())
      .then((data) => { if (active) setReport(data) })
      .catch((cause: unknown) => {
        if (active) { setReport(null); setError(cause instanceof Error ? cause.message : 'Unable to reconcile usage') }
      }).finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [days, reload])
  const anomalies = useMemo(() => {
    if (!report) return 0
    const invoiceIssues = report.companies.reduce((n, company) =>
      n + company.finalizedUnallocatedEvents + company.missingInvoiceLines + company.priceMathMismatches, 0)
    const sourceIssues = (report.sourceChecks ?? []).reduce((n, source) =>
      n + source.missingEmailFinancialEvents + source.missingSmsFinancialEvents + source.mismatchedSmsSegments, 0)
    return invoiceIssues + sourceIssues
  }, [report])
  const totalRated = useMemo(() => report?.companies.reduce((n, company) => n + company.ratedInr, 0) ?? 0, [report])
  return <section className="panel mt-6 p-5" aria-label="Usage to invoice reconciliation">
    <div className="flex flex-wrap items-start justify-between gap-3">
      <div>
        <h2 className="font-bold">Billing reconciliation</h2>
        <p className="mt-1 text-xs text-slate-500">Checks managed usage price arithmetic and invoice allocation. Open billing periods can legitimately have unallocated usage.</p>
      </div>
      <div className="flex gap-2">
        <select className="form-input" aria-label="Reconciliation reporting period" value={days} onChange={(e) => setDays(Number(e.target.value))}>
          <option value={7}>Last 7 days</option><option value={30}>Last 30 days</option><option value={90}>Last 90 days</option>
        </select>
        <button type="button" className="secondary-button" disabled={loading} onClick={() => setReload((n) => n + 1)}>Recheck</button>
      </div>
    </div>
    {loading && <p className="mt-4 text-sm" role="status">Reconciling...</p>}

    {!loading && report && <>
      <div className="mt-4 flex flex-wrap gap-4 text-sm">
        <p><strong>Rated managed usage:</strong> {amount(totalRated)}</p>
        <p className={anomalies > 0 ? 'font-semibold text-amber-700' : 'text-emerald-700'}>
          <strong>{int(anomalies)}</strong> detected exceptions
        </p>
      </div>
      <details className="mt-4" open={anomalies > 0}>
        <summary className="cursor-pointer text-sm font-semibold">Provider handoffs versus billable usage</summary>
        <p className="mt-2 text-xs text-slate-500">
          Accepted provider handoffs must have a matching canonical billing event. Missing events or
          mismatched SMS segment quantities require investigation before invoicing.
        </p>
        <div className="mt-3 overflow-x-auto">
          <table className="w-full min-w-[820px] text-left text-sm">
            <thead><tr className="border-b text-xs text-slate-500">
              <th className="py-3 pr-3">Company</th>
              <th className="px-2 text-right">Email handoffs</th>
              <th className="px-2 text-right">SMS recipients</th>
              <th className="px-2 text-right">Missing email events</th>
              <th className="px-2 text-right">Missing SMS events</th>
              <th className="px-2 text-right">Segment mismatches</th>
            </tr></thead>
            <tbody>{(report.sourceChecks ?? []).map((row) =>
              <tr key={row.companyId} className="border-b">
                <td className="py-3 pr-3">{companyNames[row.companyId] || `Company ${row.companyId}`}</td>
                <td className="px-2 text-right tabular-nums">{int(row.acceptedEmailHandoffs)}</td>
                <td className="px-2 text-right tabular-nums">{int(row.committedSmsRecipients)}</td>
                <td className="px-2 text-right tabular-nums">{int(row.missingEmailFinancialEvents)}</td>
                <td className="px-2 text-right tabular-nums">{int(row.missingSmsFinancialEvents)}</td>
                <td className="px-2 text-right tabular-nums">{int(row.mismatchedSmsSegments)}</td>
              </tr>)}</tbody>
          </table>
          {!(report.sourceChecks ?? []).length &&
            <p className="mt-3 text-sm text-slate-500">No committed provider handoffs in this period.</p>}
        </div>
      </details>
      <details className="mt-4" open={anomalies > 0}>
        <summary className="cursor-pointer text-sm font-semibold">Usage-to-invoice reconciliation</summary>
        <div className="mt-3 overflow-x-auto">
          <table className="w-full min-w-[780px] text-left text-sm">
            <thead><tr className="border-b text-xs text-slate-500">
              <th className="py-3 pr-3">Company</th><th className="px-2 text-right">Events</th>
              <th className="px-2 text-right">Rated</th><th className="px-2 text-right">Allocated</th>
              <th className="px-2 text-right">Finalized unallocated</th>
              <th className="px-2 text-right">Missing lines</th><th className="px-2 text-right">Price mismatch</th>
            </tr></thead>
            <tbody>{report.companies.map((row) => <tr className="border-b" key={row.companyId}>
              <td className="py-3 pr-3">{companyNames[row.companyId] || `Company ${row.companyId}`}</td>
              <td className="px-2 text-right tabular-nums">{int(row.events)}</td>
              <td className="px-2 text-right tabular-nums">{amount(row.ratedInr)}</td>
              <td className="px-2 text-right tabular-nums">{amount(row.allocatedInr)}</td>
              <td className="px-2 text-right tabular-nums">{int(row.finalizedUnallocatedEvents)}</td>
              <td className="px-2 text-right tabular-nums">{int(row.missingInvoiceLines)}</td>
              <td className="px-2 text-right tabular-nums">{int(row.priceMathMismatches)}</td>
            </tr>)}</tbody>
          </table>
        </div>
      </details>
    </>}
  </section>
}
