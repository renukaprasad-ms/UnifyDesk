import { useEffect, useMemo, useState } from 'react'
import { adminBillingApi } from '../../features/platformBilling/adminBillingApi'
import type { BillingChannelTrafficReport } from '../../features/platformBilling/types'

type Props = { companyNames: Record<string, string> }
type Counts = { email: number; sms: number; whatsapp: number; inbound: number; outbound: number }
const zero = (): Counts => ({ email: 0, sms: 0, whatsapp: 0, inbound: 0, outbound: 0 })
const count = (v: number) => new Intl.NumberFormat('en-IN').format(v)
const add = (value: Counts, channel: string, direction: string, n: number) => {
  if (channel === 'EMAIL') value.email += n
  else if (channel === 'SMS') value.sms += n
  else if (channel === 'WHATSAPP') value.whatsapp += n
  if (direction === 'INBOUND') value.inbound += n
  if (direction === 'OUTBOUND') value.outbound += n
}
export default function BillingChannelTrafficOverview({ companyNames }: Props) {
  const [days, setDays] = useState(7)
  const [reload, setReload] = useState(0)
  const [report, setReport] = useState<BillingChannelTrafficReport | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  useEffect(() => {
    let active = true
    const to = new Date()
    const from = new Date(to.getTime() - days * 86400000)
    setLoading(true)
    setError('')
    adminBillingApi.channelTraffic(from.toISOString(), to.toISOString())
      .then((data) => { if (active) setReport(data) })
      .catch((cause: unknown) => {
        if (active) { setReport(null); setError(cause instanceof Error ? cause.message : 'Unable to load channel traffic') }
      }).finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [days, reload])

  const aggregations = useMemo(() => {
    const totals = zero()
    const companies = new Map<string, Counts>()
    const daily = new Map<string, Counts>()
    for (const point of report?.points ?? []) {
      add(totals, point.channel, point.direction, point.messages)
      const company = companies.get(point.companyId) ?? zero()
      add(company, point.channel, point.direction, point.messages)
      companies.set(point.companyId, company)
      const day = daily.get(point.day) ?? zero()
      add(day, point.channel, point.direction, point.messages)
      daily.set(point.day, day)
    }
    return { totals, companies: [...companies].sort((a, b) => b[1].email + b[1].sms + b[1].whatsapp - a[1].email - a[1].sms - a[1].whatsapp), daily: [...daily].sort((a, b) => a[0].localeCompare(b[0])) }
  }, [report])
  const peak = Math.max(1, ...aggregations.daily.map(([, row]) => row.email + row.sms + row.whatsapp))

  return <section className="panel mt-6 p-5" aria-label="Channel message traffic">
    <div className="flex flex-wrap items-start justify-between gap-3">
      <div>
        <h2 className="font-bold">Message processing — channel activity</h2>
        <p className="mt-1 text-xs text-slate-500">Observed message records, not billed segments or provider webhook counts. Includes connected inbox email, legacy managed broadcasts and Meta WhatsApp. The separate UnifyDesk Mail service is not yet connected.</p>
      </div>
      <div className="flex gap-2">
        <select aria-label="Channel traffic period" className="form-input" value={days} onChange={(e) => setDays(Number(e.target.value))}>
          <option value={7}>Last 7 days</option><option value={30}>Last 30 days</option><option value={90}>Last 90 days</option>
        </select>
        <button type="button" className="secondary-button" disabled={loading} onClick={() => setReload((v) => v + 1)}>Refresh</button>
      </div>
    </div>
    {loading && <p className="mt-5 text-sm" role="status">Loading message activity...</p>}
    {error && <p className="mt-5 text-sm text-red-600" role="alert">{error}</p>}
    {!loading && report && <>
      <div className="mt-5 grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
        {[
          ['Email messages', aggregations.totals.email, 'Connected inbox + managed delivery'],
          ['SMS messages', aggregations.totals.sms, 'Accepted recipients, not segments'],
          ['WhatsApp messages', aggregations.totals.whatsapp, 'Recorded accepted/sent messages'],
          ['All observed messages', aggregations.totals.email + aggregations.totals.sms + aggregations.totals.whatsapp, `${count(aggregations.totals.inbound)} inbound · ${count(aggregations.totals.outbound)} outbound`],
        ].map(([title, value, sub]) => <div className="rounded-xl border p-4" key={title}>
          <p className="text-xs text-slate-500">{title}</p>
          <p className="mt-2 text-xl font-bold tabular-nums">{count(Number(value))}</p>
          <p className="mt-1 text-xs text-slate-500">{sub}</p>
        </div>)}
      </div>
      <div className="mt-6">
        <h3 className="font-semibold text-sm">Daily processed messages (UTC)</h3>
        {aggregations.daily.length ? <div className="mt-3 space-y-2">
          {aggregations.daily.map(([day, row]) => {
            const width = (n: number) => `${n / peak * 100}%`
            return <div className="flex items-center gap-3 text-xs" key={day}>
              <span className="w-24 shrink-0">{day}</span>
              <div className="flex h-3 flex-1 overflow-hidden rounded-full bg-slate-100">
                <div className="bg-blue-600" title={`Email ${row.email}`} style={{ width: width(row.email) }} />
                <div className="bg-amber-500" title={`SMS ${row.sms}`} style={{ width: width(row.sms) }} />
                <div className="bg-emerald-500" title={`WhatsApp ${row.whatsapp}`} style={{ width: width(row.whatsapp) }} />
              </div>
              <span className="w-20 text-right tabular-nums">{count(row.email + row.sms + row.whatsapp)}</span>
            </div>
          })}
          <p className="text-xs text-slate-500">Blue: email · Amber: SMS · Green: WhatsApp</p>
        </div> : <p className="mt-3 text-sm text-slate-500">No observed message records in this period.</p>}
      </div>
      <div className="mt-6 overflow-x-auto">
        <h3 className="mb-3 font-semibold text-sm">Messages by company</h3>
        <table className="w-full min-w-[690px] text-left text-sm">
          <thead><tr className="border-b text-xs text-slate-500">
            <th className="py-3 pr-3">Company</th><th className="px-3 text-right">Email</th>
            <th className="px-3 text-right">SMS</th><th className="px-3 text-right">WhatsApp</th>
            <th className="px-3 text-right">Inbound</th><th className="px-3 text-right">Outbound</th>
          </tr></thead>
          <tbody>{aggregations.companies.map(([id, row]) => <tr key={id} className="border-b">
            <td className="py-3 pr-3">{companyNames[id] || `Company ${id}`}</td>
            <td className="px-3 text-right tabular-nums">{count(row.email)}</td>
            <td className="px-3 text-right tabular-nums">{count(row.sms)}</td>
            <td className="px-3 text-right tabular-nums">{count(row.whatsapp)}</td>
            <td className="px-3 text-right tabular-nums">{count(row.inbound)}</td>
            <td className="px-3 text-right tabular-nums">{count(row.outbound)}</td>
          </tr>)}</tbody>
        </table>
      </div>
    </>}
  </section>
}
