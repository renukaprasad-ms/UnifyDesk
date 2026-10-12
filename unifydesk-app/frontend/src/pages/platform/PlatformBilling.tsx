import { useEffect, useMemo, useState } from 'react'
import {
  FiAlertTriangle,
  FiCreditCard,
  FiGift,
  FiPlus,
  FiRefreshCw,
  FiShield,
  FiSliders,
} from 'react-icons/fi'
import { billingApi } from '../../features/billing/billingApi'
import type { CompanyPlanDetails } from '../../features/billing/types'
import type { Company } from '../../features/company/types'
import { platformApi } from '../../features/platform/platformApi'
import { adminBillingApi } from '../../features/platformBilling/adminBillingApi'
import {
  buildOverrideInput,
  effectiveAdminPrices,
  optionalInstant,
  planRequiresExplicitPrice,
  requiredInstant,
} from '../../features/platformBilling/billingAdminModel'
import type {
  BillingCategory,
  BillingPlan,
  ChannelType,
  CompanyRateOverride,
  CommunicationDirection,
  PaymentPolicy,
  PlanAssignment,
  ProviderType,
  UsageUnit,
} from '../../features/platformBilling/types'
import { workspaceApi } from '../../features/workspace/workspaceApi'
import { PageSkeleton } from '../../components/ui/Skeleton'
import BillingUsageOverview from './BillingUsageOverview'
import GlobalManagedRatesPanel from './GlobalManagedRatesPanel'
import BillingFinancialOverview from './BillingFinancialOverview'
import BillingChannelTrafficOverview from './BillingChannelTrafficOverview'
import BillingUsageReconciliationOverview from './BillingUsageReconciliationOverview'
import BillingMailProjectLinks from './BillingMailProjectLinks'

const providersByChannel: Record<ChannelType, ProviderType[]> = {
  EMAIL: ['AMAZON_SES', 'SENDGRID', 'AZURE_EMAIL', 'MSG91_EMAIL', 'UNIFYDESK_EMAIL', 'GMAIL', 'MICROSOFT', 'ZOHO_MAIL', 'IMAP_SMTP', 'SMTP'],
  WHATSAPP: ['META_WHATSAPP'],
  SMS: ['MSG91_SMS'],
}

const categoriesByChannel: Record<ChannelType, BillingCategory[]> = {
  EMAIL: ['EMAIL_OUTBOUND', 'EMAIL_INBOUND'],
  WHATSAPP: ['WHATSAPP_MARKETING', 'WHATSAPP_UTILITY', 'WHATSAPP_AUTHENTICATION', 'WHATSAPP_SERVICE'],
  SMS: ['SMS_PROMOTIONAL', 'SMS_TRANSACTIONAL', 'SMS_OTP'],
}

const blankOverride = () => ({
  channel: 'SMS' as ChannelType,
  provider: 'MSG91_SMS' as ProviderType,
  billingCategory: 'SMS_TRANSACTIONAL' as BillingCategory,
  direction: 'OUTBOUND' as CommunicationDirection,
  unit: 'SEGMENT' as UsageUnit,
  price: 0,
  currency: 'INR',
  reason: '',
  country: '',
  carrier: '',
})

const date = (value?: string | null) => value ? new Date(value).toLocaleString('en-IN') : '—'

export default function PlatformBilling() {
  const [companies, setCompanies] = useState<Company[]>([])
  const [plans, setPlans] = useState<BillingPlan[]>([])
  const [companyId, setCompanyId] = useState('')
  const [details, setDetails] = useState<CompanyPlanDetails | null>(null)
  const [overrides, setOverrides] = useState<CompanyRateOverride[]>([])
  const [assignments, setAssignments] = useState<PlanAssignment[]>([])
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [superAdmin, setSuperAdmin] = useState<boolean | null>(null)
  const [grantPlanId, setGrantPlanId] = useState('')
  const [grantPlanPriceId, setGrantPlanPriceId] = useState('')
  const [grantPolicy, setGrantPolicy] = useState<Extract<PaymentPolicy, 'COMPED' | 'OFFLINE_CONTRACT'>>('COMPED')
  const [grantReason, setGrantReason] = useState('')
  const [grantReference, setGrantReference] = useState('')
  const [grantConfirm, setGrantConfirm] = useState(false)
  const [privatePlanId, setPrivatePlanId] = useState('')
  const [assignmentFrom, setAssignmentFrom] = useState('')
  const [assignmentTo, setAssignmentTo] = useState('')
  const [assignmentReason, setAssignmentReason] = useState('')
  const [revokeReason, setRevokeReason] = useState<Record<string, string>>({})
  const [cancelReason, setCancelReason] = useState('')
  const [immediateConfirm, setImmediateConfirm] = useState(false)
  const [overrideForm, setOverrideForm] = useState(blankOverride)
  const [overrideFrom, setOverrideFrom] = useState('')
  const [overrideTo, setOverrideTo] = useState('')
  const [showOverrideForm, setShowOverrideForm] = useState(false)
  const [activeTab, setActiveTab] = useState<'overview' | 'rates' | 'companies' | 'reconciliation' | 'mail'>('overview')

  const privatePlans = useMemo(
    () => plans.filter((plan) => plan.status === 'PUBLISHED' && plan.visibility === 'PRIVATE'),
    [plans],
  )
  const grantPlans = useMemo(() => plans.filter((plan) => plan.status === 'PUBLISHED'), [plans])
  const selectedGrantPlan = useMemo(
    () => plans.find((plan) => plan.planId === grantPlanId) ?? null,
    [plans, grantPlanId],
  )
  const validGrantPrices = useMemo(
    () => selectedGrantPlan ? effectiveAdminPrices(selectedGrantPlan, new Date()) : [],
    [selectedGrantPlan],
  )
  const grantNeedsPrice = selectedGrantPlan ? planRequiresExplicitPrice(selectedGrantPlan) : false

  useEffect(() => {
    void (async () => {
      setLoading(true)
      try {
        const access = await platformApi.me()
        const allowed = access.role === 'SUPER_ADMIN'
        setSuperAdmin(allowed)
        if (!allowed) return
        const [visibleCompanies, loadedPlans] = await Promise.all([
          workspaceApi.adminVisible(),
          adminBillingApi.plans(),
        ])
        setCompanies(visibleCompanies)
        setPlans(loadedPlans)
        setCompanyId(visibleCompanies[0]?.companyId ?? '')
      } catch (cause) {
        setError(cause instanceof Error ? cause.message : 'Unable to load billing operations')
      } finally {
        setLoading(false)
      }
    })()
  }, [])

  const loadCompany = async (id: string) => {
    if (!id || !superAdmin) return
    try {
      const assignmentGroups = await Promise.all(
        privatePlans.map((plan) => adminBillingApi.planAssignments(plan.planId)),
      )
      const [loadedOverrides, loadedDetails] = await Promise.allSettled([
        adminBillingApi.companyRateOverrides(id),
        billingApi.companyPlan(id),
      ])
      setAssignments(assignmentGroups.flat().filter((assignment) => assignment.companyId === id))
      setOverrides(loadedOverrides.status === 'fulfilled' ? loadedOverrides.value : [])
      setDetails(loadedDetails.status === 'fulfilled' ? loadedDetails.value : null)
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Unable to load company billing')
    }
  }

  useEffect(() => {
    if (companyId && superAdmin && activeTab === 'companies') void loadCompany(companyId)
  }, [companyId, superAdmin, privatePlans.length, activeTab])

  const reset = () => {
    setError('')
    setNotice('')
  }

  const grant = async (event: React.FormEvent) => {
    event.preventDefault()
    if (!companyId || !selectedGrantPlan || !grantReason.trim() || !grantConfirm) return
    if (grantNeedsPrice && !grantPlanPriceId) {
      setError('A valid explicit PlanPrice is required for a paid manual grant.')
      return
    }
    if (grantPlanPriceId && !validGrantPrices.some((price) => price.planPriceId === grantPlanPriceId)) {
      setError('The selected PlanPrice is not effective, ACTIVE, INR, or does not belong to this plan.')
      return
    }
    setSaving(true)
    reset()
    try {
      await adminBillingApi.grantSubscription(companyId, {
        planId: selectedGrantPlan.planId,
        planPriceId: grantPlanPriceId || null,
        status: 'ACTIVE',
        paymentPolicy: grantPolicy,
        reason: grantReason.trim(),
        externalContractReference: grantReference.trim() || undefined,
      })
      setNotice(`${grantPolicy} subscription granted without creating a provider payment.`)
      setGrantReason('')
      setGrantReference('')
      setGrantConfirm(false)
      await loadCompany(companyId)
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Unable to grant subscription')
    } finally {
      setSaving(false)
    }
  }

  const assign = async (event: React.FormEvent) => {
    event.preventDefault()
    if (!companyId || !privatePlanId) return
    setSaving(true)
    reset()
    try {
      await adminBillingApi.assignPrivatePlan(privatePlanId, companyId, {
        effectiveFrom: assignmentFrom ? requiredInstant(assignmentFrom, 'Effective start') : null,
        effectiveTo: optionalInstant(assignmentTo),
        reason: assignmentReason.trim() || undefined,
      })
      setNotice('Private plan visibility assigned. Existing subscription state was not changed.')
      await loadCompany(companyId)
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Unable to assign private plan')
    } finally {
      setSaving(false)
    }
  }

  const revoke = async (assignment: PlanAssignment) => {
    const reason = revokeReason[assignment.assignmentId]?.trim()
    if (!reason) {
      setError('A reason is required to revoke private plan visibility.')
      return
    }
    await adminBillingApi.revokePrivatePlan(assignment.assignmentId, reason)
    setNotice('Private plan visibility revoked. Existing subscription state was not cancelled.')
    await loadCompany(companyId)
  }

  const cancelNow = async () => {
    if (!companyId || !cancelReason.trim() || !immediateConfirm) return
    await adminBillingApi.cancelImmediately(companyId, cancelReason.trim())
    setNotice('Subscription cancelled immediately by Super Admin. No refund was created automatically.')
    await loadCompany(companyId)
  }

  const createOverride = async (event: React.FormEvent) => {
    event.preventDefault()
    if (!companyId) return
    setSaving(true)
    reset()
    try {
      await adminBillingApi.createCompanyRateOverride(
        companyId,
        buildOverrideInput(overrideForm, overrideFrom, overrideTo),
      )
      setOverrideForm(blankOverride())
      setOverrideFrom('')
      setOverrideTo('')
      setNotice('Rate override revision created. Historical rated usage is unchanged.')
      await loadCompany(companyId)
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Unable to create rate override')
    } finally {
      setSaving(false)
    }
  }

  if (loading) return <div className="p-6"><PageSkeleton /></div>
  if (superAdmin === false) {
    return <section className="p-6"><div className="panel p-6"><h1 className="font-bold">Super Admin access required</h1></div></section>
  }

  const sub = details?.subscription
  return (
    <section className="p-4 sm:p-6 lg:p-8">
      <div className="w-full">
        <header className="flex flex-col gap-4 lg:flex-row lg:justify-between">
          <div><p className="eyebrow">Platform Finance</p><h1 className="mt-2 text-2xl font-bold">Billing &amp; Revenue</h1><p className="mt-1 text-sm text-slate-500">Subscription and managed-channel activity, customer charges and cash collection.</p></div>
          {activeTab === 'companies' && <select aria-label="Company billing operations" className="form-input max-w-sm" value={companyId} onChange={(e) => setCompanyId(e.target.value)}>
            {companies.map((company) => <option key={company.companyId} value={company.companyId}>{company.name}</option>)}
          </select>}
        </header>

        <nav aria-label="Billing sections" className="mt-6 flex flex-wrap gap-2 border-b pb-3">
          {([
            ['overview', 'Dashboard'],
            ['rates', 'Managed rates'],
            ['companies', 'Company billing'],
            ['reconciliation', 'Reconciliation'],
            ['mail', 'Mail integration'],
          ] as const).map(([tab, label]) => (
            <button key={tab} type="button" onClick={() => setActiveTab(tab)}
              aria-current={activeTab === tab ? 'page' : undefined}
              className={`rounded-xl px-4 py-2 text-sm font-semibold transition-colors ${activeTab === tab ? 'bg-blue-600 text-white' : 'bg-slate-100 text-slate-700 hover:bg-slate-200'}`}>
              {label}
            </button>
          ))}
        </nav>

        {(error || notice) && (
          <div className={`mt-5 rounded-xl border p-4 text-sm ${error ? 'border-red-200 bg-red-50 text-red-700' : 'border-emerald-200 bg-emerald-50 text-emerald-700'}`}>
            {error || notice}
          </div>
        )}

        {activeTab === 'overview' && <>
          <BillingChannelTrafficOverview companyNames={Object.fromEntries(companies.map((company) => [company.companyId, company.name]))} />
          <BillingFinancialOverview companyNames={Object.fromEntries(companies.map((company) => [company.companyId, company.name]))} />
          <BillingUsageOverview companyNames={Object.fromEntries(companies.map((company) => [company.companyId, company.name]))} />
        </>}

        {activeTab === 'rates' && <GlobalManagedRatesPanel />}
        {activeTab === 'mail' && <BillingMailProjectLinks companies={companies} />}
        {activeTab === 'reconciliation' && <BillingUsageReconciliationOverview
          companyNames={Object.fromEntries(companies.map((company) => [company.companyId, company.name]))} />}

        {activeTab === 'companies' && <>
        <div className="mt-6 grid gap-5 xl:grid-cols-3">
          <article className="panel p-5 xl:col-span-2">
            <div className="flex gap-3"><FiCreditCard /><div><h2 className="font-bold">Current subscription</h2><p className="text-xs text-slate-500">Backend effective subscription.</p></div><button className="secondary-button ml-auto" onClick={() => void loadCompany(companyId)}><FiRefreshCw /></button></div>
            {sub ? <div className="mt-5 rounded-xl bg-slate-50 p-4"><strong>{sub.planName}</strong><p className="text-xs">{sub.status} · {sub.paymentPolicy} · period end {date(sub.currentPeriodEnd)}</p></div> : <p className="mt-5 text-sm">No effective subscription.</p>}
          </article>
          <article className="panel p-5">
            <div className="flex gap-3"><FiAlertTriangle /><h2 className="font-bold">Immediate cancellation</h2></div>
            <textarea className="form-input mt-4" placeholder="Required reason" value={cancelReason} onChange={(e) => setCancelReason(e.target.value)} />
            <label className="mt-3 flex gap-2 text-xs"><input type="checkbox" checked={immediateConfirm} onChange={(e) => setImmediateConfirm(e.target.checked)} />Immediate and audited.</label>
            <button className="mt-3 rounded-lg bg-rose-600 px-4 py-2 text-xs font-bold text-white" disabled={!immediateConfirm || !cancelReason.trim()} onClick={() => void cancelNow()}>Cancel immediately</button>
          </article>
        </div>

        <div className="mt-6 grid gap-6 xl:grid-cols-2">
          <form className="panel p-5" onSubmit={grant}>
            <div className="flex gap-3"><FiGift /><div><h2 className="font-bold">Manual subscription grant</h2><p className="text-xs">COMPED or OFFLINE_CONTRACT; no Cashfree payment.</p></div></div>
            <select className="form-input mt-4" value={grantPlanId} onChange={(e) => { setGrantPlanId(e.target.value); setGrantPlanPriceId('') }}><option value="">Select plan</option>{grantPlans.map((plan) => <option key={plan.planId} value={plan.planId}>{plan.name} · {plan.visibility}</option>)}</select>
            <select className="form-input mt-3" required={grantNeedsPrice} value={grantPlanPriceId} onChange={(e) => setGrantPlanPriceId(e.target.value)}><option value="">{grantNeedsPrice ? 'Select required effective INR price' : 'No explicit price (free plan only)'}</option>{validGrantPrices.map((price) => <option key={price.planPriceId} value={price.planPriceId}>{price.billingCycle} · INR {price.amount}</option>)}</select>
            <select className="form-input mt-3" value={grantPolicy} onChange={(e) => setGrantPolicy(e.target.value as typeof grantPolicy)}><option>COMPED</option><option>OFFLINE_CONTRACT</option></select>
            <input className="form-input mt-3" placeholder="External contract/reference" value={grantReference} onChange={(e) => setGrantReference(e.target.value)} />
            <textarea className="form-input mt-3" placeholder="Required reason" value={grantReason} onChange={(e) => setGrantReason(e.target.value)} />
            <label className="mt-3 flex gap-2 text-xs"><input type="checkbox" checked={grantConfirm} onChange={(e) => setGrantConfirm(e.target.checked)} />No provider payment should be fabricated.</label>
            <button className="primary-button mt-4" disabled={saving || !grantConfirm || (grantNeedsPrice && !grantPlanPriceId)}>Grant subscription</button>
          </form>

          <form className="panel p-5" onSubmit={assign}>
            <div className="flex gap-3"><FiShield /><h2 className="font-bold">Private plan assignment</h2></div>
            <select className="form-input mt-4" value={privatePlanId} onChange={(e) => setPrivatePlanId(e.target.value)}><option value="">Select PRIVATE plan</option>{privatePlans.map((plan) => <option key={plan.planId} value={plan.planId}>{plan.name}</option>)}</select>
            <div className="mt-3 grid grid-cols-2 gap-3"><input type="datetime-local" className="form-input" value={assignmentFrom} onChange={(e) => setAssignmentFrom(e.target.value)} /><input type="datetime-local" className="form-input" value={assignmentTo} onChange={(e) => setAssignmentTo(e.target.value)} /></div>
            <input className="form-input mt-3" placeholder="Reason" value={assignmentReason} onChange={(e) => setAssignmentReason(e.target.value)} />
            <button className="primary-button mt-4">Assign private plan</button>
            {assignments.map((assignment) => (
              <div key={assignment.assignmentId} className="mt-3 rounded-lg bg-slate-50 p-3">
                <strong>{plans.find((plan) => plan.planId === assignment.planId)?.name ?? assignment.planId}</strong>
                {!assignment.revokedAt && <div className="mt-2 flex gap-2"><input className="form-input" placeholder="Reason to revoke" value={revokeReason[assignment.assignmentId] ?? ''} onChange={(e) => setRevokeReason((current) => ({ ...current, [assignment.assignmentId]: e.target.value }))} /><button type="button" className="secondary-button" onClick={() => void revoke(assignment)}>Revoke</button></div>}
              </div>
            ))}
          </form>
        </div>

        <div className="panel mt-6">
          <div className="flex items-center justify-between border-b p-5">
            <div className="flex gap-3"><FiSliders /><div><h2 className="font-bold">Managed-Service rate overrides</h2><p className="text-xs">Effective-dated historical pricing truth.</p></div></div>
            <button className="secondary-button gap-2" onClick={() => setShowOverrideForm((value) => !value)}><FiPlus /> New revision</button>
          </div>
          {showOverrideForm && (
            <form className="grid gap-3 bg-slate-50 p-5 md:grid-cols-3" onSubmit={createOverride}>
              <select className="form-input" value={overrideForm.channel} onChange={(e) => { const channel = e.target.value as ChannelType; setOverrideForm({ ...overrideForm, channel, provider: providersByChannel[channel][0], billingCategory: categoriesByChannel[channel][0], unit: channel === 'SMS' ? 'SEGMENT' : 'MESSAGE' }) }}><option>SMS</option><option>WHATSAPP</option><option>EMAIL</option></select>
              <select className="form-input" value={overrideForm.provider} onChange={(e) => setOverrideForm({ ...overrideForm, provider: e.target.value as ProviderType })}>{providersByChannel[overrideForm.channel].map((provider) => <option key={provider}>{provider}</option>)}</select>
              <select className="form-input" value={overrideForm.billingCategory} onChange={(e) => setOverrideForm({ ...overrideForm, billingCategory: e.target.value as BillingCategory })}>{categoriesByChannel[overrideForm.channel].map((category) => <option key={category}>{category}</option>)}</select>
              <select className="form-input" value={overrideForm.direction} onChange={(e) => setOverrideForm({ ...overrideForm, direction: e.target.value as CommunicationDirection })}><option>OUTBOUND</option><option>INBOUND</option></select>
              <select className="form-input" value={overrideForm.unit} onChange={(e) => setOverrideForm({ ...overrideForm, unit: e.target.value as UsageUnit })}><option>MESSAGE</option><option>SEGMENT</option><option>CONVERSATION</option></select>
              <input className="form-input" placeholder="Country (optional)" value={overrideForm.country} onChange={(e) => setOverrideForm({ ...overrideForm, country: e.target.value })} />
              <input className="form-input" placeholder="Carrier (optional)" value={overrideForm.carrier} onChange={(e) => setOverrideForm({ ...overrideForm, carrier: e.target.value })} />
              <input className="form-input" type="number" min="0" value={overrideForm.price} onChange={(e) => setOverrideForm({ ...overrideForm, price: Number(e.target.value) })} />
              <input required type="datetime-local" className="form-input" value={overrideFrom} onChange={(e) => setOverrideFrom(e.target.value)} />
              <input type="datetime-local" className="form-input" value={overrideTo} onChange={(e) => setOverrideTo(e.target.value)} />
              <input className="form-input" placeholder="Reason" value={overrideForm.reason} onChange={(e) => setOverrideForm({ ...overrideForm, reason: e.target.value })} />
              <button className="primary-button">Create effective revision</button>
            </form>
          )}
          <div className="divide-y">
            {overrides.map((override) => (
              <div key={override.overrideId} className="p-4 text-sm">
                <strong>{override.channel} · {override.provider} · {override.billingCategory}</strong>
                <p>{override.country || 'all countries'} · {override.carrier || 'all carriers'} · {override.unit} · {override.currency} {override.price}</p>
                <p className="text-xs text-slate-500">{date(override.effectiveFrom)} → {date(override.effectiveTo)}</p>
              </div>
            ))}
          </div>
        </div>
        </>}
      </div>
    </section>
  )
}
