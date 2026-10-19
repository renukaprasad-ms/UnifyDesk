import { useSnackbarState } from '../../components/feedback/useSnackbarState'
import { useEffect, useState } from 'react'
import { platformApi } from '../../features/platform/platformApi'
import type { ManagedProviderRoute } from '../../features/platform/types'
import { CardSkeleton } from '../../components/ui/Skeleton'

export default function PlatformManagedProviders() {
  const [routes, setRoutes] = useState<ManagedProviderRoute[]>([])
  const [companyId,setCompanyId]=useState('')
  const [peId,setPeId]=useState('')
  const [evidence,setEvidence]=useState('')
  const [approvalReason,setApprovalReason]=useState('')
  const [companyApproval,setCompanyApproval]=useState<'approve'|'revoke'|''>('')
  const [companyProvider,setCompanyProvider]=useState('')
  const [companyProviderReason,setCompanyProviderReason]=useState('')
  const [companyRouteBusy,setCompanyRouteBusy]=useState(false)
  const [companyLookupBusy,setCompanyLookupBusy]=useState(false)
  const [selected, setSelected] = useState<Record<string,string>>({})
  const [reason, setReason] = useState<Record<string,string>>({})
  const [busy, setBusy] = useState('')
  const [loading, setLoading] = useState(true)
  const [, setError] = useSnackbarState('', 'error')
  const [, setNotice] = useSnackbarState('', 'success')

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

  async function changeApproval(active: boolean) {
    if (!companyId.match(/^[0-9]+$/) || !window.confirm(
      active ? 'Confirm that this company and PE are authorised under the MetaReach TM account?' :
        'Revoke this company’s MetaReach sending authorisation?')) return
    setCompanyApproval(active?'approve':'revoke')
    setError('')
    setNotice('')
    try {
      await platformApi.changeMetaReachCompanyApproval(companyId,{
        active,peId,evidenceReference:evidence,reason:approvalReason.trim(),
      })
      setNotice(active?'MetaReach company mapping approved.':'MetaReach company mapping revoked.')
      setApprovalReason('')
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Unable to update company mapping')
    } finally {setCompanyApproval('')}
  }

  async function loadCompanyMapping() {
    if (!companyId.match(/^[0-9]+$/)) return
    setCompanyLookupBusy(true)
    setError('')
    try {
      const [provider,approval]=await Promise.all([
        platformApi.managedSmsCompanyProvider(companyId),
        platformApi.metaReachCompanyApproval(companyId)
      ])
      setCompanyProvider(provider ?? '')
      setPeId(approval?.peId ?? '')
      setEvidence(approval?.evidenceReference ?? '')
      setNotice(approval?.active
        ? 'MetaReach company mapping is currently approved.'
        : 'MetaReach company mapping has not been approved.')
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Unable to load company mapping')
    } finally {setCompanyLookupBusy(false)}
  }

  async function saveCompanyRoute() {
    if (!companyId.match(/^[0-9]+$/) || companyProviderReason.trim().length<8) return
    if (!window.confirm('Update the provider for future Managed SMS runs in this company? In-flight runs will not be rerouted.')) return
    setCompanyRouteBusy(true)
    setError('')
    setNotice('')
    try {
      await platformApi.changeManagedSmsCompanyProvider(companyId,{
        provider:companyProvider || null,reason:companyProviderReason.trim()
      })
      setNotice('Company SMS provider override saved. Refresh company Managed SMS status for readiness.')
      setCompanyProviderReason('')
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Unable to update provider override')
    } finally {setCompanyRouteBusy(false)}
  }

  return <section className="w-full space-y-5 p-4 sm:p-6 lg:p-8">
    <header>
      <p className="eyebrow">PLATFORM / SUPER ADMIN</p>
      <h1 className="mt-2 text-2xl font-bold text-slate-950">Managed providers</h1>
      <p className="mt-2 max-w-3xl text-sm text-slate-600">Choose the internal provider used for future UnifyDesk managed messages. Existing campaigns keep their original provider. Provider credentials remain backend-only.</p>
    </header>


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
    <div className="panel space-y-4 p-5">
      <div>
        <h2 className="text-lg font-bold text-slate-950">Company provider override</h2>
        <p className="mt-1 text-xs text-slate-600">Roll out MetaReach company by company. Approve the PE/TM mapping below before assigning MetaReach.</p>
      </div>
      <div className="grid gap-3 md:grid-cols-3">
        <label className="text-xs font-semibold text-slate-700">Company ID
          <input className="form-input mt-1" value={companyId} onChange={e=>setCompanyId(e.target.value)} placeholder="Numeric company ID"/>
        </label>
        <label className="text-xs font-semibold text-slate-700">New SMS provider
          <select className="form-input mt-1" value={companyProvider} onChange={e=>setCompanyProvider(e.target.value)}>
            <option value="">Use global default</option>
            <option value="MSG91_SMS">MSG91</option>
            <option value="METAREACH_SMS">MetaReach (approved companies only)</option>
          </select>
        </label>
        <label className="text-xs font-semibold text-slate-700">Change reason
          <input className="form-input mt-1" value={companyProviderReason} onChange={e=>setCompanyProviderReason(e.target.value)} maxLength={400} placeholder="Reason required (8+ characters)"/>
        </label>
      </div>
      <button type="button" className="secondary-button" disabled={companyLookupBusy || !companyId.match(/^[0-9]+$/)}
        onClick={()=>void loadCompanyMapping()}>{companyLookupBusy?'Loading…':'Load company mapping'}</button>
      <button type="button" className="primary-button" disabled={companyRouteBusy || !companyId.match(/^[0-9]+$/) || companyProviderReason.trim().length<8}
        onClick={()=>void saveCompanyRoute()}>{companyRouteBusy?'Saving…':'Save company provider'}</button>
    </div>
    <div className="panel space-y-4 p-5">
      <div>
        <h2 className="text-lg font-bold text-slate-950">MetaReach company authorisation</h2>
        <p className="mt-1 text-xs text-slate-600">Super Admin must verify the company DLT Principal Entity, PE–TM chain, and provider account evidence before MetaReach sending. Never paste an API key here.</p>
      </div>
      <div className="grid gap-3 md:grid-cols-3">
        <label className="text-xs font-semibold text-slate-700">Company ID
          <input className="form-input mt-1" value={companyId} onChange={e=>setCompanyId(e.target.value)} placeholder="Numeric company ID"/>
        </label>
        <label className="text-xs font-semibold text-slate-700">Verified DLT PE ID
          <input className="form-input mt-1" value={peId} onChange={e=>setPeId(e.target.value)} placeholder="Principal Entity ID"/>
        </label>
        <label className="text-xs font-semibold text-slate-700">Provider account evidence reference
          <input className="form-input mt-1" value={evidence} onChange={e=>setEvidence(e.target.value)} placeholder="Provider-approved mapping reference"/>
        </label>
      </div>
      <label className="block text-xs font-semibold text-slate-700">Review reason
        <textarea className="form-input mt-1 w-full" rows={2} maxLength={500}
          value={approvalReason} onChange={e=>setApprovalReason(e.target.value)}
          placeholder="How was the provider DLT mapping verified?"/>
      </label>
      <div className="flex flex-wrap gap-2">
        <button type="button" className="primary-button" disabled={companyApproval!=='' || !companyId.match(/^[0-9]+$/) || !peId || !evidence || approvalReason.trim().length<8}
          onClick={()=>void changeApproval(true)}>
          {companyApproval==='approve'?'Saving…':'Approve MetaReach mapping'}
        </button>
        <button type="button" className="secondary-button" disabled={companyApproval!=='' || !companyId.match(/^[0-9]+$/) || approvalReason.trim().length<8}
          onClick={()=>void changeApproval(false)}>
          {companyApproval==='revoke'?'Revoking…':'Revoke mapping'}
        </button>
      </div>
    </div>
  </section>
}
