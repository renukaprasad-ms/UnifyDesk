import { useEffect, useMemo, useState } from 'react'
import { FiArrowLeft, FiArchive, FiCheck, FiPlus, FiSave } from 'react-icons/fi'
import { useNavigate, useParams } from 'react-router'
import { adminBillingApi } from '../../features/platformBilling/adminBillingApi'
import { buildPlanPriceInput, buildRateCardInput } from '../../features/platformBilling/billingAdminModel'
import type {
  BillingCategory, BillingCycle, BillingPlan, ChannelType, CommunicationDirection,
  CreatePlanPriceInput, CreateRateCardInput, EntitlementDefinition, PlanPrice,
  PlanVisibility, ProviderType, RateCard, UsageUnit,
} from '../../features/platformBilling/types'

type Tab = 'overview' | 'pricing' | 'entitlements' | 'usage'
const providersByChannel: Record<ChannelType, ProviderType[]> = {
  EMAIL: ['AMAZON_SES', 'SENDGRID', 'AZURE_EMAIL', 'MSG91_EMAIL', 'GMAIL', 'MICROSOFT', 'IMAP_SMTP', 'SMTP'],
  WHATSAPP: ['META_WHATSAPP'], SMS: ['MSG91_SMS'],
}
const categoriesByChannel: Record<ChannelType, BillingCategory[]> = {
  EMAIL: ['EMAIL_OUTBOUND', 'EMAIL_INBOUND'],
  WHATSAPP: ['WHATSAPP_MARKETING', 'WHATSAPP_UTILITY', 'WHATSAPP_AUTHENTICATION', 'WHATSAPP_SERVICE'],
  SMS: ['SMS_PROMOTIONAL', 'SMS_TRANSACTIONAL', 'SMS_OTP'],
}
const blankPrice = { billingCycle: 'MONTHLY' as BillingCycle, amount: 0, currency: 'INR' }
const blankRate = (planId: string) => ({
  planId, channel: 'SMS' as ChannelType, provider: 'MSG91_SMS' as ProviderType,
  billingCategory: 'SMS_TRANSACTIONAL' as BillingCategory, direction: 'OUTBOUND' as CommunicationDirection,
  unit: 'SEGMENT' as UsageUnit, price: 0, currency: 'INR', country: '', carrier: '',
})
const money = (amount: number, currency: string) =>
  new Intl.NumberFormat('en-IN', { style: 'currency', currency, maximumFractionDigits: 6 }).format(amount)

export default function PlatformPlanDetails() {
  const { planId = '' } = useParams()
  const navigate = useNavigate()
  const [plan, setPlan] = useState<BillingPlan | null>(null)
  const [definitions, setDefinitions] = useState<EntitlementDefinition[]>([])
  const [prices, setPrices] = useState<PlanPrice[]>([])
  const [rateCards, setRateCards] = useState<RateCard[]>([])
  const [tab, setTab] = useState<Tab>('overview')
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const [priceForm, setPriceForm] = useState(blankPrice)
  const [priceFrom, setPriceFrom] = useState('')
  const [priceTo, setPriceTo] = useState('')
  const [rateForm, setRateForm] = useState(() => blankRate(planId))
  const [rateFrom, setRateFrom] = useState('')
  const [rateTo, setRateTo] = useState('')
  const [entitlementValues, setEntitlementValues] = useState<Record<string, string>>({})
  const [overview, setOverview] = useState({
    name: '', description: '', billingCycle: 'MONTHLY' as BillingCycle,
    basePrice: 0, currency: 'INR', visibility: 'PUBLIC' as PlanVisibility,
  })

  const load = async () => {
    if (!planId) return
    setLoading(true)
    setError('')
    try {
      const [plans, defs, loadedPrices, loadedRates] = await Promise.all([
        adminBillingApi.plans(), adminBillingApi.entitlementDefinitions(),
        adminBillingApi.planPrices(planId), adminBillingApi.rateCards(planId),
      ])
      const current = plans.find((item) => item.planId === planId)
      if (!current) throw new Error('Plan not found')
      setPlan(current)
      setDefinitions(defs.filter((item) => item.active))
      setPrices(loadedPrices)
      setRateCards(loadedRates)
      setOverview({
        name: current.name, description: current.description ?? '', billingCycle: current.billingCycle,
        basePrice: current.basePrice, currency: current.currency, visibility: current.visibility,
      })
      setEntitlementValues(Object.fromEntries(defs.map((d) => [d.key, current.entitlements.find((e) => e.key === d.key)?.value ?? d.defaultValue])))
      setPriceForm((value) => ({ ...value, currency: current.currency }))
      setRateForm((value) => ({ ...value, planId, currency: current.currency }))
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Unable to load plan')
    } finally { setLoading(false) }
  }

  useEffect(() => { void load() }, [planId])
  const mutable = plan?.status === 'DRAFT'
  const entitlementsByCategory = useMemo(() => definitions.reduce<Record<string, EntitlementDefinition[]>>((groups, item) => {
    ;(groups[item.category] ??= []).push(item)
    return groups
  }, {}), [definitions])

  const saveOverview = async (event: React.FormEvent) => {
    event.preventDefault()
    if (!mutable) return
    setSaving(true)
    try {
      await adminBillingApi.updatePlan(planId, { ...overview, name: overview.name.trim(), description: overview.description.trim(), currency: overview.currency.toUpperCase() })
      await load()
    } catch (cause) { setError(cause instanceof Error ? cause.message : 'Unable to update plan') }
    finally { setSaving(false) }
  }

  const setStatus = async (status: 'PUBLISHED' | 'RETIRED') => {
    setSaving(true)
    try { await adminBillingApi.updatePlan(planId, { status }); await load() }
    catch (cause) { setError(cause instanceof Error ? cause.message : 'Unable to update status') }
    finally { setSaving(false) }
  }

  const addPrice = async (event: React.FormEvent) => {
    event.preventDefault()
    if (!mutable) return
    setSaving(true); setError('')
    try {
      const input: CreatePlanPriceInput = buildPlanPriceInput(priceForm, priceFrom, priceTo)
      await adminBillingApi.createPlanPrice(planId, input)
      setPriceFrom(''); setPriceTo(''); await load()
    } catch (cause) { setError(cause instanceof Error ? cause.message : 'Unable to create price') }
    finally { setSaving(false) }
  }

  const addRate = async (event: React.FormEvent) => {
    event.preventDefault()
    if (!mutable) return
    setSaving(true); setError('')
    try {
      const input: CreateRateCardInput = buildRateCardInput(rateForm, rateFrom, rateTo)
      await adminBillingApi.createRateCard(input)
      setRateFrom(''); setRateTo(''); await load()
    } catch (cause) { setError(cause instanceof Error ? cause.message : 'Unable to create rate') }
    finally { setSaving(false) }
  }

  const saveEntitlement = async (definition: EntitlementDefinition) => {
    if (!mutable) return
    setSaving(true)
    try { await adminBillingApi.saveEntitlement(planId, definition.key, entitlementValues[definition.key] ?? definition.defaultValue); await load() }
    catch (cause) { setError(cause instanceof Error ? cause.message : 'Unable to save entitlement') }
    finally { setSaving(false) }
  }

  const changeRateChannel = (channel: ChannelType) => setRateForm((current) => ({
    ...current, channel, provider: providersByChannel[channel][0], billingCategory: categoriesByChannel[channel][0],
    unit: channel === 'SMS' ? 'SEGMENT' : 'MESSAGE',
  }))

  if (loading) return <div className="p-6 text-sm text-slate-500">Loading plan configuration…</div>
  if (!plan) return <div className="p-6 text-red-600">{error || 'Plan not found'}</div>

  return <section className="p-4 sm:p-6 lg:p-8"><div className="mx-auto max-w-7xl">
    <button className="mb-5 inline-flex items-center gap-2 text-xs font-semibold text-slate-500" onClick={() => navigate('/platform/plans')}><FiArrowLeft />Back to plans</button>
    <header className="flex flex-col gap-4 lg:flex-row lg:justify-between"><div><p className="eyebrow">{plan.code} · VERSION {plan.version}</p><h1 className="mt-2 text-2xl font-bold">{plan.name}</h1><p className="mt-1 text-sm text-slate-500">{plan.description || 'No plan description yet.'}</p></div><div>{plan.status === 'DRAFT' && <button className="primary-button gap-2" onClick={() => void setStatus('PUBLISHED')}><FiCheck />Publish</button>}{plan.status === 'PUBLISHED' && <button className="secondary-button gap-2" onClick={() => void setStatus('RETIRED')}><FiArchive />Retire</button>}</div></header>
    {!mutable && <div className="mt-5 rounded-xl border border-indigo-100 bg-indigo-50 p-4 text-sm text-indigo-900">Published commercial terms are immutable. Create a new plan version to change commercial terms.</div>}
    {error && <div className="mt-5 rounded-xl border border-red-200 bg-red-50 p-4 text-sm text-red-700">{error}</div>}
    <div className="mt-7 flex overflow-x-auto border-b border-slate-200">{([['overview','Overview'],['pricing','Subscription pricing'],['entitlements','Limits & features'],['usage','Managed-Service pricing']] as const).map(([key,label]) => <button key={key} className={`settings-tab whitespace-nowrap ${tab === key ? 'is-active' : ''}`} onClick={() => setTab(key)}>{label}</button>)}</div>

    {tab === 'overview' && <form onSubmit={saveOverview} className="panel mt-6 p-5"><fieldset disabled={!mutable || saving} className="grid gap-4 md:grid-cols-2"><label className="text-xs font-semibold">Name<input className="form-input mt-1.5" value={overview.name} onChange={(e) => setOverview({ ...overview, name: e.target.value })} /></label><label className="text-xs font-semibold">Visibility<select className="form-input mt-1.5" value={overview.visibility} onChange={(e) => setOverview({ ...overview, visibility: e.target.value as PlanVisibility })}><option>PUBLIC</option><option>PRIVATE</option><option>INTERNAL</option></select></label><label className="text-xs font-semibold">Cycle<select className="form-input mt-1.5" value={overview.billingCycle} onChange={(e) => setOverview({ ...overview, billingCycle: e.target.value as BillingCycle })}><option>FREE</option><option>MONTHLY</option><option>YEARLY</option></select></label><label className="text-xs font-semibold">Base price<input className="form-input mt-1.5" type="number" value={overview.basePrice} onChange={(e) => setOverview({ ...overview, basePrice: Number(e.target.value) })} /></label><label className="text-xs font-semibold">Currency<input className="form-input mt-1.5" value={overview.currency} onChange={(e) => setOverview({ ...overview, currency: e.target.value })} /></label><label className="text-xs font-semibold md:col-span-2">Description<textarea className="form-input mt-1.5" value={overview.description} onChange={(e) => setOverview({ ...overview, description: e.target.value })} /></label></fieldset>{mutable && <button className="primary-button mt-5 gap-2"><FiSave />Save draft</button>}</form>}

    {tab === 'pricing' && <div className="mt-6 grid gap-6 xl:grid-cols-[380px_1fr]"><form className="panel p-5" onSubmit={addPrice}><h2 className="font-bold">Add subscription price</h2><fieldset disabled={!mutable || saving} className="mt-4 space-y-3"><select className="form-input" value={priceForm.billingCycle} onChange={(e) => setPriceForm({ ...priceForm, billingCycle: e.target.value as BillingCycle })}><option>FREE</option><option>MONTHLY</option><option>YEARLY</option></select><input className="form-input" type="number" min="0" value={priceForm.amount} onChange={(e) => setPriceForm({ ...priceForm, amount: Number(e.target.value) })} /><label className="block text-xs font-semibold">Effective from *<input required type="datetime-local" className="form-input mt-1" value={priceFrom} onChange={(e) => setPriceFrom(e.target.value)} /></label><label className="block text-xs font-semibold">Effective to<input type="datetime-local" className="form-input mt-1" value={priceTo} onChange={(e) => setPriceTo(e.target.value)} /></label></fieldset>{mutable && <button className="primary-button mt-4 gap-2"><FiPlus />Add price</button>}</form><div className="panel">{prices.map((p) => <div key={p.planPriceId} className="flex justify-between border-b p-4"><span>{p.billingCycle} · {p.status}<small className="block text-slate-500">{p.effectiveFrom ?? 'Missing effective start'} → {p.effectiveTo ?? 'open'}</small></span><strong>{money(p.amount,p.currency)}</strong></div>)}</div></div>}

    {tab === 'entitlements' && <div className="mt-6 space-y-5">{Object.entries(entitlementsByCategory).map(([category,items]) => <div key={category} className="panel"><h2 className="border-b p-4 font-bold">{category}</h2>{items.map((d) => <div key={d.key} className="grid gap-3 border-b p-4 lg:grid-cols-[1fr_260px_auto]"><div><strong>{d.name}</strong><p className="text-xs text-slate-500">{d.description || d.key}</p></div><input disabled={!mutable} className="form-input" value={entitlementValues[d.key] ?? d.defaultValue} onChange={(e) => setEntitlementValues({ ...entitlementValues, [d.key]: e.target.value })} />{mutable && <button className="secondary-button" onClick={() => void saveEntitlement(d)}>Save</button>}</div>)}</div>)}</div>}

    {tab === 'usage' && <div className="mt-6 grid gap-6 xl:grid-cols-[400px_1fr]"><form className="panel p-5" onSubmit={addRate}><h2 className="font-bold">Add Managed-Service rate</h2><fieldset disabled={!mutable || saving} className="mt-4 space-y-3"><select className="form-input" value={rateForm.channel} onChange={(e) => changeRateChannel(e.target.value as ChannelType)}><option>SMS</option><option>WHATSAPP</option><option>EMAIL</option></select><select className="form-input" value={rateForm.provider} onChange={(e) => setRateForm({ ...rateForm, provider: e.target.value as ProviderType })}>{providersByChannel[rateForm.channel].map((p) => <option key={p}>{p}</option>)}</select><select className="form-input" value={rateForm.billingCategory} onChange={(e) => setRateForm({ ...rateForm, billingCategory: e.target.value as BillingCategory })}>{categoriesByChannel[rateForm.channel].map((c) => <option key={c}>{c}</option>)}</select><select className="form-input" value={rateForm.direction} onChange={(e) => setRateForm({ ...rateForm, direction: e.target.value as CommunicationDirection })}><option>OUTBOUND</option><option>INBOUND</option></select><select className="form-input" value={rateForm.unit} onChange={(e) => setRateForm({ ...rateForm, unit: e.target.value as UsageUnit })}><option>MESSAGE</option><option>SEGMENT</option><option>CONVERSATION</option></select><input className="form-input" placeholder="Country (optional)" value={rateForm.country ?? ''} onChange={(e) => setRateForm({ ...rateForm, country: e.target.value })} /><input className="form-input" placeholder="Carrier (optional)" value={rateForm.carrier ?? ''} onChange={(e) => setRateForm({ ...rateForm, carrier: e.target.value })} /><input className="form-input" type="number" min="0" value={rateForm.price} onChange={(e) => setRateForm({ ...rateForm, price: Number(e.target.value) })} /><label className="block text-xs font-semibold">Effective from *<input required type="datetime-local" className="form-input mt-1" value={rateFrom} onChange={(e) => setRateFrom(e.target.value)} /></label><label className="block text-xs font-semibold">Effective to<input type="datetime-local" className="form-input mt-1" value={rateTo} onChange={(e) => setRateTo(e.target.value)} /></label></fieldset>{mutable && <button className="primary-button mt-4 gap-2"><FiPlus />Add rate</button>}</form><div className="panel overflow-x-auto"><table className="mobile-card-table w-full min-w-[650px] text-left text-sm"><tbody>{rateCards.map((r) => <tr key={r.rateCardId} className="border-b"><td className="p-4"><strong>{r.channel} · {r.provider}</strong><small className="block text-slate-500">{r.country || 'all countries'} · {r.carrier || 'all carriers'}</small></td><td className="p-4">{r.billingCategory} · {r.direction}</td><td className="p-4">{r.unit}</td><td className="p-4 font-bold">{money(r.price,r.currency)}</td></tr>)}</tbody></table></div></div>}
  </div></section>
}
