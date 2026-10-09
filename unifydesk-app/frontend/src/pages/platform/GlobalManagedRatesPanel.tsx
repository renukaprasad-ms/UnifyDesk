import { useEffect, useMemo, useState } from 'react'
import { adminBillingApi } from '../../features/platformBilling/adminBillingApi'
import type { GlobalManagedRate, GlobalManagedRateInput, ManagedBillingSku } from '../../features/platformBilling/types'

const SKUS: { sku: ManagedBillingSku; title: string; unit: string; description: string }[] = [
  { sku: 'MANAGED_EMAIL_OUTBOUND', title: 'Managed Email', unit: 'email', description: 'Per outbound message, for SES and other managed email providers' },
  { sku: 'MANAGED_SMS_SEGMENT', title: 'Managed SMS', unit: 'segment', description: 'Per billable segment; a multipart SMS may use more than one segment' },
]
const priceText = (value: number) => `₹${value.toFixed(6)}`
const when = (value: string | null) => value ? new Date(value).toLocaleString('en-IN') : 'Open ended'

export default function GlobalManagedRatesPanel() {
  const [rates, setRates] = useState<GlobalManagedRate[]>([])
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [editing, setEditing] = useState<ManagedBillingSku | null>(null)
  const [amount, setAmount] = useState('')
  const [start, setStart] = useState('')
  const [reason, setReason] = useState('')
  const [pending, setPending] = useState<GlobalManagedRateInput | null>(null)
  const current = useMemo(() => {
    const now = Date.now()
    return Object.fromEntries(SKUS.map(({ sku }) => [
      sku, rates.find((rate) => rate.billingSku === sku && new Date(rate.effectiveFrom).getTime() <= now &&
        (!rate.effectiveTo || new Date(rate.effectiveTo).getTime() > now)),
    ])) as Partial<Record<ManagedBillingSku, GlobalManagedRate>>
  }, [rates])
  const refresh = async () => {
    const response = await adminBillingApi.globalManagedRates()
    setRates(response)
  }
  useEffect(() => {
    let active = true
    adminBillingApi.globalManagedRates().then((data) => {
      if (active) setRates(data)
    }).catch((cause: unknown) => {
      if (active) setError(cause instanceof Error ? cause.message : 'Unable to load global rates')
    }).finally(() => {
      if (active) setLoading(false)
    })
    return () => { active = false }
  }, [])
  const edit = (sku: ManagedBillingSku) => {
    setEditing(sku)
    setAmount(String(current[sku]?.price ?? (sku === 'MANAGED_EMAIL_OUTBOUND' ? 0.012 : 0.25)))
    setStart('')
    setReason('')
    setError('')
    setNotice('')
  }
  const prepare = (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (!editing) return
    const price = Number(amount)
    if (amount.trim() === '' || !Number.isFinite(price) || price < 0 || !/^\d+(?:\.\d{1,6})?$/.test(amount.trim())) {
      setError('Enter a valid non-negative INR price with no more than six decimal places')
      return
    }
    const parsed = start ? new Date(start) : null
    if (parsed && (Number.isNaN(parsed.getTime()) || parsed.getTime() < Date.now())) {
      setError('A scheduled rate must start in the future; leave blank for immediate activation')
      return
    }
    setError('')
    setPending({
      billingSku: editing,
      price,
      effectiveFrom: parsed ? parsed.toISOString() : null,
      reason: reason.trim() || undefined,
    })
  }
  const confirm = async () => {
    if (!pending) return
    setSaving(true)
    setError('')
    try {
      await adminBillingApi.reviseGlobalManagedRate(pending)
      await refresh()
      setNotice('New global pricing revision saved. Existing financial usage retains its original rate.')
      setPending(null)
      setEditing(null)
    } catch (cause) {
      setPending(null)
      setError(cause instanceof Error ? cause.message : 'Unable to publish rate revision')
    } finally {
      setSaving(false)
    }
  }
  return <section className="panel mt-6 p-5" aria-label="Global managed-channel rates">
    <div className="flex flex-wrap items-start justify-between gap-3">
      <div>
        <h2 className="font-bold">Global managed-channel rates</h2>
        <p className="mt-1 text-xs text-slate-500">Fallback pricing when no company override or plan-specific rate applies. INR per native billing unit.</p>
      </div>
      <button type="button" className="secondary-button" disabled={loading || saving} onClick={() => { setLoading(true); refresh().catch((e: unknown) => setError(e instanceof Error ? e.message : 'Unable to refresh rates')).finally(() => setLoading(false)) }}>Refresh</button>
    </div>
    {error && <p className="mt-4 text-sm text-red-600" role="alert">{error}</p>}
    {notice && <p className="mt-4 text-sm text-emerald-700" role="status">{notice}</p>}
    {loading ? <p className="mt-4 text-sm">Loading global pricing...</p> : <div className="mt-5 grid gap-3 md:grid-cols-2">
      {SKUS.map(({ sku, title, unit, description }) => <div key={sku} className="rounded-xl border p-4">
        <div className="flex items-start justify-between gap-2">
          <div>
            <h3 className="text-sm font-bold">{title}</h3>
            <p className="mt-1 text-xs text-slate-500">{description}</p>
          </div>
          <button type="button" className="secondary-button" onClick={() => edit(sku)}>Revise</button>
        </div>
        <p className="mt-4 text-xl font-bold tabular-nums">{current[sku] ? priceText(current[sku]!.price) : 'No active rate'} <span className="text-sm font-normal">/{unit}</span></p>
        {current[sku] && <p className="mt-1 text-xs text-slate-500">Effective since {when(current[sku]!.effectiveFrom)}</p>}
      </div>)}
    </div>}
    {editing && <form className="mt-5 rounded-xl border p-4" onSubmit={prepare}>
      <h3 className="font-semibold">Publish a new {SKUS.find((s) => s.sku === editing)?.title} rate</h3>
      <div className="mt-3 grid gap-3 md:grid-cols-3">
        <label className="text-xs">INR per {editing === 'MANAGED_SMS_SEGMENT' ? 'segment' : 'email'}
          <input required autoFocus type="number" inputMode="decimal" min="0" step="0.000001" className="form-input mt-1" value={amount} onChange={(e) => setAmount(e.target.value)} />
        </label>
        <label className="text-xs">Activation date (optional — blank means now)
          <input type="datetime-local" className="form-input mt-1" value={start} onChange={(e) => setStart(e.target.value)} />
        </label>
        <label className="text-xs">Reason (audit trail)
          <input className="form-input mt-1" maxLength={500} value={reason} onChange={(e) => setReason(e.target.value)} placeholder="e.g. new commercial pricing" />
        </label>
      </div>
      <div className="mt-3 flex gap-2">
        <button type="submit" className="primary-button" disabled={saving}>Review change</button>
        <button type="button" className="secondary-button" onClick={() => setEditing(null)}>Cancel</button>
      </div>
    </form>}
    {pending && <div role="dialog" aria-modal="true" aria-label="Confirm global managed price" className="mt-4 rounded-xl border p-4">
      <h3 className="font-semibold">Confirm global rate revision</h3>
      <p className="mt-2 text-sm">Publish <strong>{priceText(pending.price)}</strong> per {pending.billingSku === 'MANAGED_SMS_SEGMENT' ? 'SMS segment' : 'email'} starting <strong>{when(pending.effectiveFrom)}</strong>?</p>
      <p className="mt-1 text-xs text-slate-500">Past billing snapshots are not changed. Overrides and plan rates retain priority.</p>
      <div className="mt-4 flex gap-2">
        <button type="button" className="primary-button" disabled={saving} onClick={() => void confirm()}>{saving ? 'Saving...' : 'Yes, publish'}</button>
        <button type="button" className="secondary-button" disabled={saving} onClick={() => setPending(null)}>No, go back</button>
      </div>
    </div>}
    <details className="mt-5">
      <summary className="cursor-pointer text-sm font-semibold">Rate history and scheduled revisions ({rates.length})</summary>
      <div className="mt-3 overflow-x-auto"><table className="w-full min-w-[650px] text-left text-xs">
        <thead><tr className="border-b"><th className="py-2">Channel / SKU</th><th className="px-2">Rate</th><th className="px-2">From</th><th className="px-2">To</th><th className="px-2">Reason</th></tr></thead>
        <tbody>{rates.map((rate) => <tr className="border-b" key={rate.rateId}><td className="py-3">{rate.billingSku}</td><td className="px-2 tabular-nums">{priceText(rate.price)}</td><td className="px-2">{when(rate.effectiveFrom)}</td><td className="px-2">{when(rate.effectiveTo)}</td><td className="px-2">{rate.reason || '—'}</td></tr>)}</tbody>
      </table></div>
    </details>
  </section>
}
