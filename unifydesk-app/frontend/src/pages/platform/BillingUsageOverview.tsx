import { useEffect, useMemo, useState } from 'react'
import { adminBillingApi } from '../../features/platformBilling/adminBillingApi'
import { utcWeekStart } from '../../features/platformBilling/analyticsBucketing'
import type { ManagedUsageReport } from '../../features/platformBilling/types'

type Props = { companyNames: Record<string, string> }
const money = (amount: number) => new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(amount)
const number = (amount: number) => new Intl.NumberFormat('en-IN').format(amount)

export default function BillingUsageOverview({ companyNames }: Props) {
  const [days, setDays] = useState(7)
  const [groupBy, setGroupBy] = useState<'daily' | 'weekly'>('daily')
  const [report, setReport] = useState<ManagedUsageReport | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [reload, setReload] = useState(0)
  useEffect(() => {
    let active = true
    const to = new Date()
    const from = new Date(to.getTime() - days * 86400000)
    setLoading(true)
    setError('')
    adminBillingApi.managedUsage(from.toISOString(), to.toISOString())
      .then((value) => { if (active) setReport(value) })
      .catch((cause: unknown) => { if (active) { setReport(null); setError(cause instanceof Error ? cause.message : 'Unable to load managed usage') } })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [days, reload])
  const periodUsage = useMemo(() => {
    const grouped = new Map<string, { emailMessages: number; smsSegments: number; whatsappMessages: number; managedChargesInr: number }>()
    for (const row of report?.daily ?? []) {
      const key = groupBy === 'weekly' ? utcWeekStart(row.day) : row.day
      const current = grouped.get(key) ?? { emailMessages: 0, smsSegments: 0, whatsappMessages: 0, managedChargesInr: 0 }
      current.emailMessages += row.emailMessages
      current.smsSegments += row.smsSegments
      current.whatsappMessages += row.whatsappMessages
      current.managedChargesInr += row.managedChargesInr
      grouped.set(key, current)
    }
    return [...grouped].sort(([a], [b]) => a.localeCompare(b))
  }, [report, groupBy])
  const maxCount = Math.max(1, ...periodUsage.map(([, row]) => row.emailMessages + row.smsSegments + row.whatsappMessages))
  return (
    <section className="panel mt-6 p-5" aria-label="Managed service usage analytics">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h2 className="font-bold">Managed channel usage and charges</h2>
          <p className="mt-1 text-xs text-slate-500">Recorded, priced usage only. Charges are not subscription revenue or collected payments. Days use UTC.</p>
        </div>
        <div className="flex gap-2">
          <select aria-label="Usage reporting period" className="form-input" value={days} onChange={(event) => setDays(Number(event.target.value))}>
            <option value={7}>Last 7 days</option><option value={30}>Last 30 days</option><option value={90}>Last 90 days</option>
          </select>
          <select className="form-input" aria-label="Managed usage grouping" value={groupBy}
            onChange={(event) => setGroupBy(event.target.value as 'daily' | 'weekly')}>
            <option value="daily">Daily</option><option value="weekly">Weekly</option>
          </select>
          <button type="button" className="secondary-button" disabled={loading} onClick={() => setReload((v) => v + 1)}>Refresh</button>
        </div>
      </div>
      {loading && <p className="mt-5 text-sm" role="status">Loading usage...</p>}
      {error && <p className="mt-5 text-sm text-red-600" role="alert">{error}</p>}
      {!loading && report && (
        <>
          <div className="mt-5 grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
            {[
              ['Managed emails', number(report.totals.emailMessages), 'Messages'],
              ['Managed SMS', number(report.totals.smsSegments), 'Billable segments (not unique SMS)'],
              ['WhatsApp', number(report.totals.whatsappMessages), 'Recorded managed usage only'],
              ['Managed charges', money(report.totals.managedChargesInr), 'Not cash collected'],
            ].map(([label, value, subtitle]) => (
              <div className="rounded-xl border p-4" key={label}>
                <p className="text-xs text-slate-500">{label}</p>
                <p className="mt-2 text-xl font-bold tabular-nums">{value}</p>
                <p className="mt-1 text-xs text-slate-500">{subtitle}</p>
              </div>
            ))}
          </div>
          <div className="mt-5">
            <h3 className="text-sm font-semibold">{groupBy === 'weekly' ? 'Weekly' : 'Daily'} processed usage</h3>
            {periodUsage.length === 0 ? <p className="mt-3 text-sm text-slate-500">No rated managed usage in this period.</p> : (
              <div className="mt-3 space-y-2">
                {periodUsage.map(([day, row]) => {
                  const count = row.emailMessages + row.smsSegments + row.whatsappMessages
                  return <div key={day} className="flex items-center gap-3 text-xs">
                    <span className="w-24 shrink-0">{day}</span>
                    <div className="h-3 flex-1 rounded-full bg-slate-100"><div className="h-3 rounded-full bg-blue-500" style={{ width: `${count / maxCount * 100}%` }} /></div>
                    <span className="w-24 text-right tabular-nums">{number(count)} units</span>
                    <span className="w-24 text-right tabular-nums">{money(row.managedChargesInr)}</span>
                  </div>
                })}
              </div>
            )}
            <p className="mt-2 text-xs text-slate-500">Bar shows mixed channel billing units, for trend inspection only, not unique messages.</p>
          </div>
          <div className="mt-6 overflow-x-auto">
            <h3 className="mb-3 text-sm font-semibold">Company usage and managed charges</h3>
            <table className="w-full min-w-[650px] text-left text-sm">
              <thead><tr className="border-b text-xs text-slate-500"><th className="py-3 pr-3">Company</th><th className="px-3 text-right">Emails</th><th className="px-3 text-right">SMS segments</th><th className="px-3 text-right">WhatsApp</th><th className="px-3 text-right">Charges (INR)</th></tr></thead>
              <tbody>{report.companies.map((company) => <tr key={company.companyId} className="border-b">
                <td className="py-3 pr-3">{companyNames[company.companyId] ?? `Company ${company.companyId}`}</td>
                <td className="px-3 text-right tabular-nums">{number(company.emailMessages)}</td>
                <td className="px-3 text-right tabular-nums">{number(company.smsSegments)}</td>
                <td className="px-3 text-right tabular-nums">{number(company.whatsappMessages)}</td>
                <td className="px-3 text-right tabular-nums">{money(company.managedChargesInr)}</td>
              </tr>)}</tbody>
            </table>
            {report.companies.length === 0 && <p className="py-4 text-sm text-slate-500">No company usage recorded for the selected range.</p>}
          </div>
        </>
      )}
    </section>
  )
}
