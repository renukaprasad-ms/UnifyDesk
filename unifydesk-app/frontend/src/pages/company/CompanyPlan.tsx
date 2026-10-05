import { useEffect, useMemo, useRef, useState } from 'react'
import { FiAlertTriangle, FiCreditCard, FiRefreshCw } from 'react-icons/fi'
import { billingApi } from '../../features/billing/billingApi'
import {
  CANCELLATION_COPY,
  DOWNGRADE_BOUNDARY_COPY,
  MANAGED_SERVICES_COPY,
  activePlanPrices,
  customerVisiblePlans,
  defaultPlanPrice,
  downgradePlanPrices,
  money,
  subscribedPlanPrice,
  subscriptionStatusMessage,
  upgradeQuoteRows,
  upgradeRequiresCheckout,
} from '../../features/billing/billingModel'
import { launchCashfreeCheckout } from '../../features/billing/cashfreeCheckout'
import { savePendingBillingCheckout } from '../../features/billing/pendingCheckout'
import type {
  CheckoutCustomer,
  CheckoutResponse,
  CompanyPlanDetails,
  Plan,
  PlanPrice,
  SubscriptionChange,
} from '../../features/billing/types'
import { PERMISSIONS } from '../../features/iam/permissions'
import { useIam } from '../../features/iam/useIam'
import { useWorkspaceEpochScope } from '../../features/workspace/useWorkspaceEpochScope'
import { apiErrorMessage } from '../../lib/api/apiClient'
import { useAppSelector } from '../../store/hooks'
import { selectSelectedCompany, selectWorkspaceEpoch } from '../../store/workspace/workspaceSlice'

const date = (value?: string | null) =>
  value ? new Date(value).toLocaleDateString('en-IN', { dateStyle: 'medium' }) : '—'
const idempotencyKey = (prefix: string) => `${prefix}:${crypto.randomUUID()}`
type ChangeMode = 'INITIAL' | 'UPGRADE' | 'DOWNGRADE'
type ChangeDraft = { plan: Plan; price: PlanPrice; mode: ChangeMode; commandKey: string }

export default function CompanyPlan() {
  const selectedCompany = useAppSelector(selectSelectedCompany)
  const epoch = useAppSelector(selectWorkspaceEpoch)
  const { can } = useIam()
  const { capture: captureScope, isCurrent: scopeIsCurrent } = useWorkspaceEpochScope(
    selectedCompany?.companyId,
    epoch,
  )
  const [details, setDetails] = useState<CompanyPlanDetails | null>(null)
  const [plans, setPlans] = useState<Plan[]>([])
  const [loading, setLoading] = useState(true)
  const [refreshing, setRefreshing] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [draft, setDraft] = useState<ChangeDraft | null>(null)
  const [quote, setQuote] = useState<SubscriptionChange | null>(null)
  const [checkout, setCheckout] = useState<CheckoutResponse | null>(null)
  const [customer, setCustomer] = useState<CheckoutCustomer>({
    name: selectedCompany?.name ?? '', email: selectedCompany?.businessEmail ?? '', phone: '',
  })
  const [mutationPending, setMutationPending] = useState(false)
  const [cancelOpen, setCancelOpen] = useState(false)
  const requestRef = useRef(0)
  const companyId = selectedCompany?.companyId ?? null

  const load = async (background = false) => {
    if (!companyId) {
      setDetails(null); setPlans([]); setLoading(false); return
    }
    const requestId = ++requestRef.current
    const scope = captureScope()
    background ? setRefreshing(true) : setLoading(true)
    setError('')
    try {
      const [planDetails, eligible] = await Promise.all([
        billingApi.companyPlan(companyId).catch(() => ({ subscription: null, plan: null })),
        billingApi.eligiblePlans(companyId),
      ])
      if (requestRef.current !== requestId || !scopeIsCurrent(scope)) return
      setDetails(planDetails)
      setPlans(customerVisiblePlans(eligible))
    } catch (cause) {
      if (requestRef.current === requestId && scopeIsCurrent(scope)) {
        setError(apiErrorMessage(cause, 'Unable to load Billing.'))
      }
    } finally {
      if (requestRef.current === requestId && scopeIsCurrent(scope)) {
        setLoading(false); setRefreshing(false)
      }
    }
  }

  useEffect(() => {
    setDraft(null); setQuote(null); setCheckout(null); setCancelOpen(false); setNotice('')
    setCustomer({ name: selectedCompany?.name ?? '', email: selectedCompany?.businessEmail ?? '', phone: '' })
    void load()
  }, [companyId, epoch])

  const currentPlan = details?.plan ?? null
  const subscription = details?.subscription ?? null
  const currentPrice = subscribedPlanPrice(details)
  const canModify = can(PERMISSIONS.BILLING_SUBSCRIPTION_MODIFY)
  const canCancel = can(PERMISSIONS.BILLING_SUBSCRIPTION_CANCEL)
  const availablePlans = useMemo(() => plans.filter((plan) => plan.status === 'PUBLISHED'), [plans])

  const beginChange = (plan: Plan, mode: ChangeMode, price?: PlanPrice) => {
    let selectedPrice = price ?? null
    if (!selectedPrice && mode === 'DOWNGRADE') {
      if (!subscription?.currentPeriodEnd) {
        setError('Downgrade is unavailable because the backend subscription has no current-period boundary.')
        return
      }
      selectedPrice = defaultPlanPrice(plan, new Date(subscription.currentPeriodEnd))
    }
    if (!selectedPrice) selectedPrice = defaultPlanPrice(plan)
    if (!selectedPrice) {
      setError(mode === 'DOWNGRADE' ? 'This plan has no effective price at the next billing boundary.' : 'This plan has no currently effective price.')
      return
    }
    setError(''); setNotice(''); setQuote(null); setCheckout(null)
    setDraft({ plan, price: selectedPrice, mode, commandKey: idempotencyKey(mode.toLowerCase()) })
  }

  const prepareCheckout = async (event: React.FormEvent) => {
    event.preventDefault()
    if (!companyId || !draft || draft.mode === 'DOWNGRADE' || mutationPending) return
    const scope = captureScope()
    setMutationPending(true); setError('')
    try {
      if (draft.mode === 'INITIAL') {
        const prepared = await billingApi.initialCheckout(companyId, {
          planId: draft.plan.planId,
          planPriceId: draft.price.planPriceId,
          idempotencyKey: draft.commandKey,
          invoiceIdentity: {
            companyName: selectedCompany?.name ?? null,
            businessEmail: selectedCompany?.businessEmail ?? null,
            country: selectedCompany?.country ?? null,
          },
          customer: { name: customer.name?.trim() || null, email: customer.email.trim(), phone: customer.phone.trim() },
        })
        if (scopeIsCurrent(scope)) setCheckout(prepared)
        return
      }

      let currentQuote = quote
      if (!currentQuote) {
        currentQuote = await billingApi.requestUpgrade(companyId, {
          targetPlanId: draft.plan.planId,
          targetPlanPriceId: draft.price.planPriceId,
          idempotencyKey: draft.commandKey,
        })
        if (!scopeIsCurrent(scope)) return
        if (currentQuote.status === 'APPLIED') {
          setDraft(null); setQuote(null); setCheckout(null)
          setNotice('Upgrade applied by backend; no payment collection was required.')
          await load(true)
          return
        }
        setQuote(currentQuote)
        return
      }

      if (!upgradeRequiresCheckout(currentQuote)) throw new Error('Backend upgrade state does not require payment checkout.')
      if (!currentQuote.quoteReference) throw new Error('Backend upgrade quote did not include a quote reference')
      const prepared = await billingApi.upgradeCheckout(companyId, {
        changeId: currentQuote.changeId,
        quoteReference: currentQuote.quoteReference,
        customer: { name: customer.name?.trim() || null, email: customer.email.trim(), phone: customer.phone.trim() },
      })
      if (scopeIsCurrent(scope)) setCheckout(prepared)
    } catch (cause) {
      if (scopeIsCurrent(scope)) setError(apiErrorMessage(cause, 'Unable to prepare Billing checkout.'))
    } finally {
      if (scopeIsCurrent(scope)) setMutationPending(false)
    }
  }

  const pay = async () => {
    if (!companyId || !draft || draft.mode === 'DOWNGRADE' || !checkout || mutationPending) return
    const scope = captureScope()
    setMutationPending(true); setError('')
    try {
      savePendingBillingCheckout({
        companyId,
        targetPlanId: draft.plan.planId,
        kind: draft.mode,
        paymentId: checkout.paymentId,
        invoiceId: checkout.invoiceId,
        recurringAuthorizationActionUrl: checkout.recurringAuthorizationActionUrl,
        previousSubscriptionId: subscription?.subscriptionId ?? null,
        createdAt: new Date().toISOString(),
      })
      if (!scopeIsCurrent(scope)) return
      if (!checkout.paymentSessionId) throw new Error('Backend checkout did not include a payment session')
      await launchCashfreeCheckout(checkout.paymentSessionId)
    } catch (cause) {
      if (scopeIsCurrent(scope)) setError(cause instanceof Error ? cause.message : 'Unable to open payment checkout.')
    } finally {
      if (scopeIsCurrent(scope)) setMutationPending(false)
    }
  }

  const scheduleDowngrade = async () => {
    if (!companyId || !draft || draft.mode !== 'DOWNGRADE' || mutationPending) return
    const scope = captureScope()
    setMutationPending(true); setError('')
    try {
      const change = await billingApi.scheduleDowngrade(companyId, {
        targetPlanId: draft.plan.planId,
        targetPlanPriceId: draft.price.planPriceId,
        idempotencyKey: draft.commandKey,
      })
      if (!scopeIsCurrent(scope)) return
      setNotice(`Downgrade scheduled${change.effectiveAt ? ` for ${date(change.effectiveAt)}` : ' for the next billing boundary'}.`)
      setDraft(null)
      await load(true)
    } catch (cause) {
      if (scopeIsCurrent(scope)) setError(apiErrorMessage(cause, 'Unable to schedule downgrade.'))
    } finally {
      if (scopeIsCurrent(scope)) setMutationPending(false)
    }
  }

  const cancelAtPeriodEnd = async () => {
    if (!companyId || mutationPending) return
    const scope = captureScope()
    setMutationPending(true); setError('')
    try {
      const updated = await billingApi.cancelAtPeriodEnd(companyId)
      if (!scopeIsCurrent(scope)) return
      setDetails((current) => current ? { ...current, subscription: updated } : current)
      setNotice('Cancellation scheduled at the backend billing-period boundary. No refund was created automatically.')
      setCancelOpen(false)
    } catch (cause) {
      if (scopeIsCurrent(scope)) setError(apiErrorMessage(cause, 'Unable to schedule cancellation.'))
    } finally {
      if (scopeIsCurrent(scope)) setMutationPending(false)
    }
  }

  if (!selectedCompany) return <section className="p-8"><div className="panel p-8 text-center">Select a workspace to view Billing.</div></section>
  if (loading) return <section className="p-8"><div className="panel p-8">Loading Billing…</div></section>

  return (
    <section className="px-4 py-6 sm:px-6 lg:px-8">
      <div className="mx-auto max-w-6xl">
        <header className="flex justify-between gap-4">
          <div><p className="eyebrow">Company</p><h1 className="mt-2 text-2xl font-bold">Billing</h1><p className="mt-1 text-sm text-slate-500">Manage the platform subscription. Managed Services usage is billed separately.</p></div>
          <button className="secondary-button" disabled={refreshing} onClick={() => void load(true)}><FiRefreshCw className={refreshing ? 'animate-spin' : ''} /> Refresh</button>
        </header>
        {(error || notice) && <div className={`mt-5 rounded-xl border p-4 text-sm ${error ? 'border-red-200 bg-red-50 text-red-700' : 'border-emerald-200 bg-emerald-50 text-emerald-700'}`}>{error || notice}</div>}

        <div className="mt-6 grid gap-5 lg:grid-cols-[1fr_340px]">
          <article className="panel p-5">
            <div className="flex gap-3"><FiCreditCard /><div><p className="text-xs text-indigo-500">Current subscription</p><h2 className="text-2xl font-bold">{currentPlan?.name ?? 'No active plan'}</h2>{subscription && <p className="mt-2 text-sm">{subscriptionStatusMessage(subscription)}</p>}</div></div>
            {currentPlan && subscription && <div className="mt-6 grid gap-3 sm:grid-cols-3"><div className="rounded-xl bg-slate-50 p-4"><p className="text-xs">Platform price</p>{currentPrice ? <><p className="font-bold">{money(currentPrice.amount, currentPrice.currency)}</p><p className="text-xs text-slate-500">{currentPrice.billingCycle.toLowerCase()}</p></> : <p className="font-bold text-slate-500">Price unavailable</p>}</div><div className="rounded-xl bg-slate-50 p-4"><p className="text-xs">Current period</p><p className="font-bold">{date(subscription.currentPeriodStart)} – {date(subscription.currentPeriodEnd)}</p></div><div className="rounded-xl bg-slate-50 p-4"><p className="text-xs">Payment policy</p><p className="font-bold">{subscription.paymentPolicy}</p></div></div>}
            {subscription?.status === 'PAST_DUE' && <div className="mt-5 flex gap-3 rounded-xl bg-amber-50 p-4 text-sm text-amber-900"><FiAlertTriangle />Payment is overdue; backend grace/dunning state remains authoritative.</div>}
          </article>
          <aside className="panel p-5"><h2 className="font-bold">Managed Services</h2><p className="mt-2 text-xs text-slate-500">{MANAGED_SERVICES_COPY}</p>{subscription && canCancel && !subscription.cancelAtPeriodEnd && <button className="mt-5 text-xs font-semibold text-rose-600" onClick={() => setCancelOpen(true)}>Cancel at period end</button>}</aside>
        </div>

        <section className="mt-7">
          <h2 className="text-lg font-bold">Eligible platform plans</h2>
          <div className="mt-4 grid gap-4 lg:grid-cols-2 xl:grid-cols-3">
            {availablePlans.map((plan) => {
              const immediatePrices = activePlanPrices(plan)
              const downgradePrices = downgradePlanPrices(plan, subscription?.currentPeriodEnd)
              const current = currentPlan?.planId === plan.planId
              return (
                <article key={plan.planId} className="panel p-5">
                  <h3 className="text-lg font-bold">{plan.name}</h3><p className="text-xs text-slate-500">{plan.code} · {plan.visibility}</p>
                  <div className="mt-3">{immediatePrices.map((price) => <p key={price.planPriceId} className="text-sm">{money(price.amount, price.currency)} · {price.billingCycle}</p>)}</div>
                  {!current && canModify && <div className="mt-4 flex flex-wrap gap-2">{!subscription ? immediatePrices.map((price) => Number(price.amount) === 0 ? <button key={price.planPriceId} className="secondary-button text-xs" onClick={() => void billingApi.selectPlan(companyId!, plan.planId).then(() => load(true))}>Select free plan</button> : <button key={price.planPriceId} className="primary-button text-xs" onClick={() => beginChange(plan, 'INITIAL', price)}>Choose {price.billingCycle.toLowerCase()}</button>) : <><button className="primary-button text-xs" disabled={immediatePrices.length === 0} onClick={() => beginChange(plan, 'UPGRADE')}>Upgrade now</button><button className="secondary-button text-xs" disabled={!subscription.currentPeriodEnd || downgradePrices.length === 0} onClick={() => beginChange(plan, 'DOWNGRADE', downgradePrices[0])}>Schedule downgrade</button></>}</div>}
                  {subscription && !subscription.currentPeriodEnd && !current && <p className="mt-2 text-xs text-amber-700">Downgrade unavailable: current period boundary is missing.</p>}
                </article>
              )
            })}
          </div>
        </section>

        {draft && <div className="fixed inset-0 z-50 flex items-end justify-center bg-slate-950/50 p-4 sm:items-center"><div className="panel w-full max-w-xl p-6"><h2 className="text-xl font-bold">{draft.mode === 'DOWNGRADE' ? 'Schedule downgrade' : draft.mode === 'UPGRADE' ? 'Upgrade plan' : 'Start subscription'}</h2><p className="mt-2 text-sm text-slate-500">{draft.plan.name} · {money(draft.price.amount, draft.price.currency)} · {draft.price.billingCycle}</p>{draft.mode === 'DOWNGRADE' ? <><p className="mt-4 text-sm">{DOWNGRADE_BOUNDARY_COPY}</p><button className="primary-button mt-5" disabled={mutationPending} onClick={() => void scheduleDowngrade()}>Schedule at period boundary</button></> : <form className="mt-4 space-y-3" onSubmit={prepareCheckout}><input required type="email" className="form-input" placeholder="Billing email" value={customer.email} onChange={(e) => setCustomer({ ...customer, email: e.target.value })} /><input required className="form-input" placeholder="Billing phone" value={customer.phone} onChange={(e) => setCustomer({ ...customer, phone: e.target.value })} />{quote && <div className="rounded-xl bg-slate-50 p-4">{upgradeQuoteRows(quote).map(([label, value]) => <div key={label} className="flex justify-between text-sm"><span>{label}</span><strong>{money(value, quote.currency ?? draft.price.currency)}</strong></div>)}</div>}{checkout ? <button type="button" className="primary-button" onClick={() => void pay()}>Continue to Cashfree · {money(checkout.amount, checkout.currency)}</button> : <button className="primary-button" disabled={mutationPending}>{quote ? 'Prepare backend checkout' : draft.mode === 'UPGRADE' ? 'Request backend upgrade quote' : 'Prepare backend checkout'}</button>}</form>}<button className="secondary-button mt-3" onClick={() => { setDraft(null); setQuote(null); setCheckout(null) }}>Close</button></div></div>}
        {cancelOpen && <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/50 p-4"><div className="panel max-w-md p-6"><h2 className="text-xl font-bold">Cancel subscription</h2><p className="mt-3 text-sm">{CANCELLATION_COPY}</p><button className="mt-5 rounded-lg bg-rose-600 px-4 py-2 text-white" onClick={() => void cancelAtPeriodEnd()}>Confirm period-end cancellation</button><button className="secondary-button ml-2" onClick={() => setCancelOpen(false)}>Keep subscription</button></div></div>}
      </div>
    </section>
  )
}
