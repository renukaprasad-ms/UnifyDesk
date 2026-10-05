import { useEffect, useMemo, useState, type FormEvent } from 'react'
import { FiCheck, FiCreditCard, FiX } from 'react-icons/fi'
import { Navigate, useNavigate, useParams } from 'react-router'
import { billingApi } from '../../features/billing/billingApi'
import {
  activePlanPrices,
  customerVisiblePlans,
  defaultPlanPrice,
  money,
  upgradeQuoteRows,
  upgradeRequiresCheckout,
} from '../../features/billing/billingModel'
import { launchCashfreeCheckout } from '../../features/billing/cashfreeCheckout'
import { savePendingBillingCheckout } from '../../features/billing/pendingCheckout'
import type {
  CheckoutResponse,
  CompanyPlanDetails,
  Plan,
  PlanPrice,
  SubscriptionChange,
} from '../../features/billing/types'
import type { Company } from '../../features/company/types'
import { workspaceApi } from '../../features/workspace/workspaceApi'
import { apiErrorMessage } from '../../lib/api/apiClient'
import { useAppDispatch } from '../../store/hooks'
import { selectCompany, setCompanies } from '../../store/workspace/workspaceSlice'

const COLLAPSED_ENTITLEMENT_COUNT = 7
const idempotencyKey = () => `onboarding-upgrade:${crypto.randomUUID()}`

const entitlementPriority = [
  'MAX_WORKSPACE_USERS',
  'CONTACT_LIMIT',
  'CONTACT_LIST_LIMIT',
  'CONTACT_IMPORT_ROWS_LIMIT',
  'SEGMENT_LIMIT',
  'STORAGE_GB',
  'BROADCAST_BATCH_LIMIT',
  'BROADCAST_RECIPIENTS_PER_MONTH',
  'BROADCASTS_PER_MONTH',
  'EMAIL_ACCOUNTS',
  'WHATSAPP_NUMBERS',
  'SMS_SENDER_IDS',
  'EMAIL_TEMPLATE_LIMIT',
  'WHATSAPP_TEMPLATE_LIMIT',
  'SMS_TEMPLATE_LIMIT',
  'MANAGED_EMAIL_ENABLED',
  'MANAGED_EMAIL_DOMAINS',
  'MANAGED_EMAIL_SENDERS',
  'SCHEDULED_BROADCASTS_ENABLED',
  'AUTO_REPLY_ENABLED',
  'USER_SIGNATURE_ENABLED',
  'API_ACCESS_ENABLED',
]

const pluralize = (value: string, singular: string, plural = `${singular}s`) =>
  `${value} ${value === '1' ? singular : plural}`

const entitlementLabel = (key: string, value: string) => {
  const unlimited = value === '-1' || value.toLowerCase() === 'unlimited'
  const labels: Record<string, string> = {
    MAX_WORKSPACE_USERS: unlimited ? 'Unlimited workspace users' : pluralize(value, 'workspace user'),
    CONTACT_LIMIT: unlimited ? 'Unlimited contacts' : pluralize(value, 'contact'),
    CONTACT_LIST_LIMIT: unlimited ? 'Unlimited contact lists' : pluralize(value, 'contact list'),
    CONTACT_IMPORT_ROWS_LIMIT: unlimited ? 'Unlimited rows per import' : `${value} rows per contact import`,
    SEGMENT_LIMIT: unlimited ? 'Unlimited segments' : pluralize(value, 'segment'),
    STORAGE_GB: unlimited ? 'Unlimited workspace storage' : `${value} GiB workspace storage`,
    BROADCAST_BATCH_LIMIT: unlimited ? 'Unlimited recipients per broadcast' : `${value} recipients per broadcast`,
    BROADCASTS_PER_DAY: unlimited ? 'Unlimited broadcasts per day' : `${value} broadcasts per day`,
    BROADCASTS_PER_MONTH: unlimited ? 'Unlimited broadcasts per month' : `${value} broadcasts per month`,
    BROADCAST_RECIPIENTS_PER_DAY: unlimited ? 'Unlimited broadcast recipients per day' : `${value} broadcast recipients per day`,
    BROADCAST_RECIPIENTS_PER_MONTH: unlimited ? 'Unlimited broadcast recipients per month' : `${value} broadcast recipients per month`,
    EMAIL_ACCOUNTS: unlimited ? 'Unlimited email connections' : pluralize(value, 'email connection'),
    WHATSAPP_ACCOUNTS: unlimited ? 'Unlimited WhatsApp accounts' : pluralize(value, 'WhatsApp account'),
    WHATSAPP_NUMBERS: unlimited ? 'Unlimited WhatsApp numbers' : pluralize(value, 'WhatsApp number'),
    SMS_ACCOUNTS: unlimited ? 'Unlimited SMS connections' : pluralize(value, 'SMS connection'),
    SMS_SENDER_IDS: unlimited ? 'Unlimited SMS sender IDs' : pluralize(value, 'SMS sender ID'),
    EMAIL_TEMPLATE_LIMIT: unlimited ? 'Unlimited email templates' : pluralize(value, 'email template'),
    WHATSAPP_TEMPLATE_LIMIT: unlimited ? 'Unlimited WhatsApp templates' : pluralize(value, 'WhatsApp template'),
    SMS_TEMPLATE_LIMIT: unlimited ? 'Unlimited SMS templates' : pluralize(value, 'SMS template'),
    MANAGED_EMAIL_DOMAINS: unlimited ? 'Unlimited Managed Email domains' : pluralize(value, 'Managed Email domain'),
    MANAGED_EMAIL_SENDERS: unlimited ? 'Unlimited Managed Email senders' : pluralize(value, 'Managed Email sender'),
    MANAGED_EMAIL_MESSAGES_MONTHLY: unlimited ? 'Unlimited Managed Email messages' : `${value} Managed Email messages/month`,
    MANAGED_SMS_SEGMENTS_MONTHLY: unlimited ? 'Unlimited Managed SMS segments' : `${value} Managed SMS segments/month`,
    SCHEDULED_BROADCASTS_ENABLED: value === 'true' ? 'Scheduled broadcasts included' : 'Scheduled broadcasts not included',
    AUTO_REPLY_ENABLED: value === 'true' ? 'Auto reply included' : 'Auto reply not included',
    USER_SIGNATURE_ENABLED: value === 'true' ? 'User signatures included' : 'User signatures not included',
    API_ACCESS_ENABLED: value === 'true' ? 'API access included' : 'API access not included',
    MANAGED_EMAIL_ENABLED: value === 'true' ? 'Managed Email available (usage billed separately)' : 'Managed Email not available',
  }
  return labels[key] ?? `${key.replaceAll('_', ' ').toLowerCase()}: ${value}`
}

const orderedEntitlements = (plan: Plan) =>
  [...plan.entitlements].sort((left, right) => {
    const leftIndex = entitlementPriority.indexOf(left.key)
    const rightIndex = entitlementPriority.indexOf(right.key)
    const leftRank = leftIndex === -1 ? entitlementPriority.length : leftIndex
    const rightRank = rightIndex === -1 ? entitlementPriority.length : rightIndex
    return leftRank - rightRank
  })

export default function SelectPlan() {
  const { companyId } = useParams()
  const dispatch = useAppDispatch()
  const navigate = useNavigate()
  const [company, setCompany] = useState<Company | null>(null)
  const [details, setDetails] = useState<CompanyPlanDetails | null>(null)
  const [plans, setPlans] = useState<Plan[]>([])
  const [expandedPlans, setExpandedPlans] = useState<Record<string, boolean>>({})
  const [loading, setLoading] = useState(true)
  const [selectedPlan, setSelectedPlan] = useState<Plan | null>(null)
  const [selectedPrice, setSelectedPrice] = useState<PlanPrice | null>(null)
  const [commandKey, setCommandKey] = useState('')
  const [quote, setQuote] = useState<SubscriptionChange | null>(null)
  const [checkout, setCheckout] = useState<CheckoutResponse | null>(null)
  const [billingEmail, setBillingEmail] = useState('')
  const [billingPhone, setBillingPhone] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => {
    if (!companyId) return
    let active = true
    setLoading(true)
    Promise.all([
      workspaceApi.list(),
      billingApi.eligiblePlans(companyId),
      billingApi.companyPlan(companyId),
    ])
      .then(([companies, eligible, planDetails]) => {
        if (!active) return
        const currentCompany = companies.find((item) => item.companyId === companyId) ?? null
        setCompany(currentCompany)
        setDetails(planDetails)
        setPlans(customerVisiblePlans(eligible).filter((plan) => plan.status === 'PUBLISHED'))
        setBillingEmail(currentCompany?.businessEmail ?? '')
        dispatch(setCompanies(companies))
        if (currentCompany) dispatch(selectCompany(companyId))
      })
      .catch((loadError) => {
        if (active) setError(apiErrorMessage(loadError, 'Unable to load available plans'))
      })
      .finally(() => {
        if (active) setLoading(false)
      })
    return () => {
      active = false
    }
  }, [companyId, dispatch])

  const availablePlans = useMemo(
    () => [...plans].sort((left, right) => Number(left.basePrice) - Number(right.basePrice)),
    [plans],
  )

  if (!companyId) return <Navigate to="/select-workspace" replace />

  const continueToWorkspace = () => {
    dispatch(selectCompany(companyId))
    navigate('/workspace', { replace: true })
  }

  const beginPaidSelection = (plan: Plan) => {
    const price = defaultPlanPrice(plan)
    if (!price || Number(price.amount) <= 0) {
      setError('This plan does not have a currently effective paid price.')
      return
    }
    setError('')
    setSelectedPlan(plan)
    setSelectedPrice(price)
    setCommandKey(idempotencyKey())
    setQuote(null)
    setCheckout(null)
    setBillingEmail(company?.businessEmail ?? '')
    setBillingPhone('')
  }

  const closeCheckout = () => {
    if (submitting) return
    setSelectedPlan(null)
    setSelectedPrice(null)
    setCommandKey('')
    setQuote(null)
    setCheckout(null)
  }

  const preparePaidSelection = async (event: FormEvent) => {
    event.preventDefault()
    if (!selectedPlan || !selectedPrice || !details?.subscription || submitting) return
    setSubmitting(true)
    setError('')
    try {
      let currentQuote = quote
      if (!currentQuote) {
        currentQuote = await billingApi.requestUpgrade(companyId, {
          targetPlanId: selectedPlan.planId,
          targetPlanPriceId: selectedPrice.planPriceId,
          idempotencyKey: commandKey,
        })
        if (currentQuote.status === 'APPLIED') {
          continueToWorkspace()
          return
        }
        setQuote(currentQuote)
        return
      }

      if (!upgradeRequiresCheckout(currentQuote)) {
        throw new Error('The backend upgrade state does not require payment checkout.')
      }
      if (!currentQuote.quoteReference) {
        throw new Error('The backend upgrade quote did not include a quote reference.')
      }
      const prepared = await billingApi.upgradeCheckout(companyId, {
        changeId: currentQuote.changeId,
        quoteReference: currentQuote.quoteReference,
        customer: {
          name: company?.name?.trim() || null,
          email: billingEmail.trim(),
          phone: billingPhone.trim(),
        },
      })
      if (!prepared.paymentSessionId) {
        throw new Error('Backend checkout did not include a payment session.')
      }
      savePendingBillingCheckout({
        companyId,
        targetPlanId: selectedPlan.planId,
        kind: 'UPGRADE',
        paymentId: prepared.paymentId,
        invoiceId: prepared.invoiceId,
        recurringAuthorizationActionUrl: prepared.recurringAuthorizationActionUrl,
        previousSubscriptionId: details.subscription.subscriptionId,
        createdAt: new Date().toISOString(),
      })
      setCheckout(prepared)
      await launchCashfreeCheckout(prepared.paymentSessionId)
    } catch (selectionError) {
      setError(apiErrorMessage(selectionError, 'Unable to prepare plan checkout'))
    } finally {
      setSubmitting(false)
    }
  }

  const pay = async () => {
    if (!selectedPlan || !checkout || !details?.subscription || submitting) return
    setSubmitting(true)
    setError('')
    try {
      if (!checkout.paymentSessionId) {
        throw new Error('Backend checkout did not include a payment session.')
      }
      savePendingBillingCheckout({
        companyId,
        targetPlanId: selectedPlan.planId,
        kind: 'UPGRADE',
        paymentId: checkout.paymentId,
        invoiceId: checkout.invoiceId,
        recurringAuthorizationActionUrl: checkout.recurringAuthorizationActionUrl,
        previousSubscriptionId: details.subscription.subscriptionId,
        createdAt: new Date().toISOString(),
      })
      await launchCashfreeCheckout(checkout.paymentSessionId)
    } catch (paymentError) {
      setError(paymentError instanceof Error ? paymentError.message : 'Unable to open Cashfree checkout')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="min-h-dvh bg-white px-4 py-10 sm:px-6 sm:py-16">
      <main className="mx-auto w-full max-w-[1080px]">
        <p className="text-xs font-semibold uppercase tracking-wide text-indigo-500">Workspace setup</p>
        <h1 className="mt-2 text-[28px] font-bold leading-tight text-slate-950">Choose your plan</h1>
        <p className="mt-2 max-w-2xl text-sm leading-6 text-slate-500">
          Your workspace starts safely on Free while you choose. Paid plans activate only after
          confirmed checkout. Managed Email and SMS provider usage is billed separately.
        </p>

        {loading && <p className="mt-8 text-sm font-semibold text-slate-500">Loading plans…</p>}
        {error && (
          <div className="mt-6 rounded-xl border border-red-200 bg-red-50 p-4 text-sm font-semibold text-red-700">
            {error}
          </div>
        )}

        {!loading && availablePlans.length === 0 && (
          <div className="mt-8 rounded-xl border border-slate-200 p-6 text-sm text-slate-600">
            No customer-selectable plans are currently published.
          </div>
        )}

        {!loading && availablePlans.length > 0 && (
          <div className="mt-8 grid items-start gap-4 md:grid-cols-2 xl:grid-cols-3">
            {availablePlans.map((plan) => {
              const entitlements = orderedEntitlements(plan)
              const expanded = Boolean(expandedPlans[plan.planId])
              const visibleEntitlements = expanded
                ? entitlements
                : entitlements.slice(0, COLLAPSED_ENTITLEMENT_COUNT)
              const hiddenCount = Math.max(entitlements.length - COLLAPSED_ENTITLEMENT_COUNT, 0)
              const current = details?.plan?.planId === plan.planId
              const prices = activePlanPrices(plan)
              const price = defaultPlanPrice(plan)
              const paid = Boolean(price && Number(price.amount) > 0)

              return (
                <article
                  key={plan.planId}
                  className={`rounded-xl border p-5 ${current ? 'border-indigo-300 bg-indigo-50/40' : 'border-slate-200'}`}
                >
                  <div className="flex items-start justify-between gap-3">
                    <div>
                      <p className="text-xs font-semibold uppercase text-indigo-500">{plan.code}</p>
                      <h2 className="mt-2 text-xl font-bold text-slate-950">{plan.name}</h2>
                    </div>
                    {current && (
                      <span className="rounded-full bg-indigo-100 px-2.5 py-1 text-[10px] font-bold uppercase text-indigo-700">
                        Current
                      </span>
                    )}
                  </div>
                  <p className="mt-3 text-2xl font-bold text-slate-950">
                    {!price || Number(price.amount) === 0
                      ? 'Free'
                      : `${money(price.amount, price.currency)}/${price.billingCycle.toLowerCase()}`}
                  </p>
                  {prices.length === 0 && (
                    <p className="mt-2 text-xs text-amber-700">No currently effective price.</p>
                  )}
                  {plan.description && (
                    <p className="mt-2 text-xs leading-5 text-slate-500">{plan.description}</p>
                  )}

                  <ul className="mt-5 space-y-2">
                    {visibleEntitlements.map((entitlement) => (
                      <li
                        key={entitlement.entitlementId}
                        className="flex gap-2 text-xs leading-5 text-slate-600"
                      >
                        <FiCheck aria-hidden="true" className="mt-0.5 shrink-0 text-indigo-500" />
                        {entitlementLabel(entitlement.key, entitlement.value)}
                      </li>
                    ))}
                  </ul>

                  {hiddenCount > 0 && (
                    <button
                      type="button"
                      className="mt-3 text-xs font-semibold text-indigo-600 hover:text-indigo-700"
                      onClick={() =>
                        setExpandedPlans((currentExpanded) => ({
                          ...currentExpanded,
                          [plan.planId]: !currentExpanded[plan.planId],
                        }))
                      }
                    >
                      {expanded ? 'Show less' : `+ ${hiddenCount} more`}
                    </button>
                  )}

                  {current ? (
                    <button
                      type="button"
                      className="primary-button mt-6 w-full justify-center"
                      onClick={continueToWorkspace}
                    >
                      Continue with {plan.name}
                    </button>
                  ) : paid ? (
                    <button
                      type="button"
                      className="primary-button mt-6 w-full justify-center"
                      disabled={!price}
                      onClick={() => beginPaidSelection(plan)}
                    >
                      <FiCreditCard /> Choose {plan.name}
                    </button>
                  ) : (
                    <button
                      type="button"
                      className="secondary-button mt-6 w-full justify-center"
                      disabled
                    >
                      Free-plan switching unavailable during setup
                    </button>
                  )}
                </article>
              )
            })}
          </div>
        )}

        {selectedPlan && selectedPrice && (
          <div className="fixed inset-0 z-50 flex items-end justify-center bg-slate-950/50 p-4 sm:items-center">
            <div className="panel w-full max-w-xl p-6">
              <div className="flex items-start justify-between gap-4">
                <div>
                  <p className="text-xs font-semibold uppercase text-indigo-500">Plan checkout</p>
                  <h2 className="mt-1 text-xl font-bold">Choose {selectedPlan.name}</h2>
                  <p className="mt-2 text-sm text-slate-500">
                    {money(selectedPrice.amount, selectedPrice.currency)} · {selectedPrice.billingCycle}
                  </p>
                </div>
                <button
                  type="button"
                  className="secondary-button"
                  aria-label="Close checkout"
                  disabled={submitting}
                  onClick={closeCheckout}
                >
                  <FiX />
                </button>
              </div>

              <form className="mt-5 space-y-3" onSubmit={preparePaidSelection}>
                <input
                  required
                  type="email"
                  className="form-input"
                  placeholder="Billing email"
                  value={billingEmail}
                  onChange={(event) => setBillingEmail(event.target.value)}
                />
                <input
                  required
                  className="form-input"
                  placeholder="Billing phone"
                  value={billingPhone}
                  onChange={(event) => setBillingPhone(event.target.value)}
                />

                {quote && (
                  <div className="rounded-xl bg-slate-50 p-4">
                    {upgradeQuoteRows(quote).map(([label, value]) => (
                      <div key={label} className="flex justify-between gap-4 py-1 text-sm">
                        <span>{label}</span>
                        <strong>{money(value, quote.currency ?? selectedPrice.currency)}</strong>
                      </div>
                    ))}
                  </div>
                )}

                {checkout ? (
                  <button
                    type="button"
                    className="primary-button w-full justify-center"
                    disabled={submitting}
                    onClick={() => void pay()}
                  >
                    Continue to Cashfree · {money(checkout.amount, checkout.currency)}
                  </button>
                ) : (
                  <button className="primary-button w-full justify-center" disabled={submitting}>
                    {submitting
                      ? 'Opening Cashfree…'
                      : quote
                        ? `Continue to Cashfree · ${money(quote.amountDue, quote.currency ?? selectedPrice.currency)}`
                        : 'Review upgrade amount'}
                  </button>
                )}
              </form>
            </div>
          </div>
        )}
      </main>
    </div>
  )
}
