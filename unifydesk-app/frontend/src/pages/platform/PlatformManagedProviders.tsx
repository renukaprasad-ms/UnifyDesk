import { useEffect, useState } from 'react'
import { platformApi } from '../../features/platform/platformApi'
import type { ManagedProviderRoute } from '../../features/platform/types'
import { CardSkeleton } from '../../components/ui/Skeleton'

export default function PlatformManagedProviders() {
  const [routes, setRoutes] = useState<ManagedProviderRoute[]>([])
  const [selected, setSelected] = useState<Record<string,string>>({})
  const [reason, setReason] = useState<Record<string,string>>({})
  const [busy, setBusy] = useState('')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')

  async function reload() {
    setLoading(true)
    try {
      const data = await platformApi.managedProviders()
      setRoutes(data)
      setSelected(Object.fromEntries(data.map((r) => [r.channel, r.provider])))
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Unable to load providers')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { void reload() }, [])

  async function save(route: ManagedProviderRoute) {
    const provider = selected[route.channel]
    if (!provider || !window.confirm(`Change the default ${route.channel} provider to ${provider}? Existing runs must not be rerouted.`)) return
    setBusy(route.channel)
    setError('')
    setNotice('')
    try {
      const result = await platformApi.changeManagedProvider(route.channel, {
        provider, expectedRevision: route.revision, reason: reason[route.channel]?.trim() ?? '',
      })
      setRoutes((prev) => prev.map((r) => r.channel === route.channel ? result : r))
      setReason((prev) => ({ ...prev, [route.channel]: '' }))
      setNotice('Provider default saved. Only newly launched managed runs use the selected provider.')
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Unable to update provider')
    } finally {
      setBusy('')
    }
  }

  return <section className="w-full space-y-5 p-4 sm:p-6 lg:p-8">
    <header>
      <p className="eyebrow">PLATFORM / SUPER ADMIN</p>
      <h1 className="mt-2 text-2xl font-bold text-slate-950">Managed providers</h1>
      <p className="mt-2 max-w-3xl text-sm text-slate-600">Choose the internal provider used for future UnifyDesk managed messages. Existing campaigns keep their original provider. Provider credentials remain backend-only.</p>
    </header>
    {error && <p role="alert" className="rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-700">{error}</p>}
    {notice && <p role="status" className="rounded-lg border border-green-200 bg-green-50 p-3 text-sm text-green-700">{notice}</p>}
    {loading ? <CardSkeleton rows={5}/> : <div className="grid gap-5 lg:grid-cols-2">
      {routes.map((route) => <div key={route.channel} className="panel space-y-4 p-5">
        <div className="flex items-center justify-between gap-3">
          <h2 className="text-lg font-bold text-slate-950">Managed {route.channel === 'SMS' ? 'SMS' : 'Email'}</h2>
          <span className="rounded-full bg-slate-100 px-3 py-1 text-xs font-semibold text-slate-700">Current: {route.provider}</span>
        </div>
        <fieldset className="space-y-2" disabled={busy !== ''}>
          <legend className="mb-2 text-xs font-semibold text-slate-700">Default provider for new runs</legend>
          {route.options.map((option) => <label key={option.code} className={`flex cursor-pointer gap-3 rounded-xl border p-3 ${!option.available ? 'cursor-not-allowed opacity-60' : 'border-slate-200'}`}>
            <input type="radio" name={route.channel} className="mt-1" disabled={!option.available}
              checked={selected[route.channel] === option.code} onChange={() => setSelected((prev) => ({...prev,[route.channel]:option.code}))}/>
            <span><span className="block text-sm font-semibold text-slate-900">{option.name}</span><span className="mt-1 block text-xs text-slate-500">{option.note}</span></span>
          </label>)}
        </fieldset>
        <label className="block text-xs font-semibold text-slate-700">Reason for switching
          <textarea className="form-input mt-1 w-full" rows={3} maxLength={500} placeholder="Required for audit trail (at least 8 characters)"
            value={reason[route.channel] ?? ''} onChange={(e) => setReason((prev) => ({...prev,[route.channel]:e.target.value}))}/>
        </label>
        <button type="button" className="primary-button" disabled={busy !== '' || selected[route.channel] === route.provider || (reason[route.channel]?.trim().length ?? 0) < 8}
          onClick={() => void save(route)}>{busy === route.channel ? 'Saving…' : 'Switch provider'}</button>
        <p className="text-xs text-slate-500">Revision {route.revision}. New launches only; delivery reports and in-flight sends stay with their frozen provider.</p>
      </div>)}
    </div>}
  </section>
}
