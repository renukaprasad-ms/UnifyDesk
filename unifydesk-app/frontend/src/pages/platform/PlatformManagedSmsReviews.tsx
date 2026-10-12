import { useCallback, useEffect, useState } from 'react'
import { platformApi } from '../../features/platform/platformApi'
import type { ManagedSmsReviewItem } from '../../features/platform/types'
import { CardSkeleton } from '../../components/ui/Skeleton'

export default function PlatformManagedSmsReviews() {
  const [items,setItems]=useState<ManagedSmsReviewItem[]>([])
  const [chosen,setChosen]=useState<ManagedSmsReviewItem | null>(null)
  const [busy,setBusy]=useState(false)
  const [loading,setLoading]=useState(true)
  const [error,setError]=useState('')
  const [notice,setNotice]=useState('')
  const [reason,setReason]=useState('')
  const [accountRef,setAccountRef]=useState('')
  const [clientRef,setClientRef]=useState('')
  const [mappingRef,setMappingRef]=useState('')
  const [templateRef,setTemplateRef]=useState('')
  const [flowRef,setFlowRef]=useState('')
  const [ctaPolicy,setCtaPolicy]=useState('{}')

  const refresh=useCallback(async()=>{
    setLoading(true)
    try {setItems(await platformApi.managedSmsReviewQueue())}
    catch (cause) {setError(cause instanceof Error?cause.message:'Unable to fetch review queue')}
    finally {setLoading(false)}
  },[])
  useEffect(()=>{void refresh()},[refresh])

  function select(item:ManagedSmsReviewItem) {
    setChosen(item)
    setReason('')
    setAccountRef('')
    setClientRef('')
    setMappingRef('')
    setTemplateRef('')
    setFlowRef('')
    setCtaPolicy('{}')
    setNotice('')
    setError('')
  }

  async function decide(decision:'APPROVE'|'REJECT') {
    if (!chosen || reason.trim().length<8) return
    if (!window.confirm(`${decision} ${chosen.resourceType} ${chosen.id} for company ${chosen.companyId}? Verify all external DLT/provider evidence first.`)) return
    setBusy(true)
    setError('')
    setNotice('')
    try {
      let parsed:unknown
      if (chosen.resourceType==='TEMPLATE_VERSION' && decision==='APPROVE') {
        parsed=JSON.parse(ctaPolicy)
        if (typeof parsed!=='object' || parsed===null || Array.isArray(parsed))
          throw new Error('CTA policy must be a JSON object')
      }
      await platformApi.decideManagedSmsReview(chosen,{
        decision,version:chosen.version,reason:reason.trim(),
        providerAccountRef:accountRef.trim()||null,
        providerClientRef:clientRef.trim()||null,
        providerTemplateRef:templateRef.trim()||null,
        providerFlowRef:flowRef.trim()||null,
        providerMappingRef:mappingRef.trim()||null,
        ctaPolicy:parsed,
      })
      setNotice(`${chosen.resourceType} ${decision.toLowerCase()} decision saved.`)
      setChosen(null)
      await refresh()
    } catch (cause) {
      setError(cause instanceof Error?cause.message:'Unable to apply review decision')
    } finally {setBusy(false)}
  }

  return <section className="w-full space-y-5 p-4 sm:p-6 lg:p-8">
    <header className="flex flex-wrap items-start justify-between gap-3">
      <div>
        <p className="eyebrow">SUPER ADMIN / PROVIDER COMPLIANCE</p>
        <h1 className="mt-2 text-2xl font-bold text-slate-950">Managed SMS reviews</h1>
        <p className="mt-2 text-sm text-slate-600">Approve verified DLT PE–TM chains, Headers and immutable approved templates. For MetaReach, use the exact DLT template ID and a real provider mapping evidence reference; no MSG91 Flow is required.</p>
      </div>
      <button type="button" className="secondary-button" onClick={()=>void refresh()} disabled={busy||loading}>Refresh queue</button>
    </header>
    {error && <div role="alert" className="rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-700">{error}</div>}
    {notice && <div role="status" className="rounded-lg border border-green-200 bg-green-50 p-3 text-sm text-green-700">{notice}</div>}
    {loading?<CardSkeleton rows={5}/>:<div className="grid gap-4 lg:grid-cols-2">
      <div className="panel p-4">
        <h2 className="text-sm font-bold text-slate-900">Pending reviews ({items.length})</h2>
        {items.length===0?<p className="mt-3 text-sm text-slate-500">No pending reviews.</p>:
        <div className="mt-3 max-h-[650px] space-y-2 overflow-auto">
          {items.map(item=><button type="button" key={item.resourceType+item.id} disabled={busy}
            onClick={()=>select(item)}
            className={`w-full rounded-xl border p-3 text-left text-xs transition-colors ${chosen?.id===item.id?'border-indigo-400 bg-indigo-50':'border-slate-200 hover:border-indigo-300'}`}>
            <span className="block font-bold text-slate-900">{item.resourceType} · {item.id}</span>
            <span className="mt-1 block text-slate-600">Company {item.companyId} · {item.state} · revision {item.version}</span>
            <span className="mt-1 block text-slate-500">{item.summary}</span>
          </button>)}
        </div>}
      </div>
      <div className="panel space-y-3 p-4">
        <h2 className="text-sm font-bold text-slate-900">Compliance decision</h2>
        {!chosen?<p className="text-sm text-slate-500">Select an item from the review queue. Never approve without the provider/DLT evidence.</p>:<>
          <p className="text-xs font-semibold text-slate-700">{chosen.resourceType} · {chosen.id} · Company {chosen.companyId}</p>
          <label className="block text-xs font-semibold text-slate-700">Review reason (required)
            <textarea className="form-input mt-1 w-full" rows={2} maxLength={500} value={reason}
              onChange={e=>setReason(e.target.value)} placeholder="Document the external verification evidence"/>
          </label>
          {chosen.resourceType==='DLT_PROFILE' && <div className="grid gap-2 sm:grid-cols-2">
            <label className="text-xs font-semibold text-slate-700">Provider account reference
              <input className="form-input mt-1" maxLength={255} value={accountRef} onChange={e=>setAccountRef(e.target.value)}/>
            </label>
            <label className="text-xs font-semibold text-slate-700">Provider client reference
              <input className="form-input mt-1" maxLength={255} value={clientRef} onChange={e=>setClientRef(e.target.value)}/>
            </label>
          </div>}
          {(chosen.resourceType==='HEADER'||chosen.resourceType==='TEMPLATE_VERSION') && <label className="block text-xs font-semibold text-slate-700">Verified provider mapping evidence reference
            <input className="form-input mt-1" maxLength={255} value={mappingRef} onChange={e=>setMappingRef(e.target.value)}/>
          </label>}
          {chosen.resourceType==='TEMPLATE_VERSION' && <>
            <label className="block text-xs font-semibold text-slate-700">Provider template reference (DLT ID for MetaReach)
              <input className="form-input mt-1" maxLength={255} value={templateRef} onChange={e=>setTemplateRef(e.target.value)}/>
            </label>
            <label className="block text-xs font-semibold text-slate-700">MSG91 Flow reference (MSG91 only)
              <input className="form-input mt-1" maxLength={255} value={flowRef} onChange={e=>setFlowRef(e.target.value)}/>
            </label>
            <label className="block text-xs font-semibold text-slate-700">Approved CTA policy JSON
              <textarea className="form-input mt-1 w-full font-mono" rows={4} value={ctaPolicy} onChange={e=>setCtaPolicy(e.target.value)}/>
            </label>
          </>}
          <div className="flex gap-2">
            <button type="button" className="primary-button" disabled={busy||reason.trim().length<8}
              onClick={()=>void decide('APPROVE')}>{busy?'Saving…':'Approve'}</button>
            <button type="button" className="secondary-button" disabled={busy||reason.trim().length<8}
              onClick={()=>void decide('REJECT')}>Reject</button>
          </div>
        </>}
      </div>
    </div>}
  </section>
}
