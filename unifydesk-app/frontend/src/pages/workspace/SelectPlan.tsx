import { useSnackbarState } from '../../components/feedback/useSnackbarState'
import StateSelect from '../../components/ui/StateSelect'
import CountrySelect from '../../components/ui/CountrySelect'
import PhoneInput from '../../components/ui/PhoneInput'
import { useEffect, useMemo, useState, type FormEvent } from 'react'
import { FiCheckCircle, FiCreditCard, FiX } from 'react-icons/fi'
import { Navigate, useNavigate, useParams } from 'react-router'
import { billingApi } from '../../features/billing/billingApi'
import {
  customerVisiblePlans,
  defaultPlanPrice,
  money,
  upgradeQuoteRows,
  upgradeRequiresCheckout,
} from '../../features/billing/billingModel'
import { launchRazorpayCheckout, type RazorpayPaymentSuccess } from '../../features/billing/razorpayCheckout'
import PaymentVerificationAndAutoPayModal from '../../components/billing/PaymentVerificationAndAutoPayModal'
import { savePendingBillingCheckout } from '../../features/billing/pendingCheckout'
import type {
  CheckoutResponse,
  CompanyBillingProfile,
  CompanyPlanDetails,
  Plan,
  PlanPrice,
  SubscriptionChange,
} from '../../features/billing/types'
import type { Company } from '../../features/company/types'
import { workspaceApi } from '../../features/workspace/workspaceApi'
import { apiErrorMessage } from '../../lib/api/apiClient'
import { CardSkeleton } from '../../components/ui/Skeleton'
import CitySelect from '../../components/ui/CitySelect'
import { useAppDispatch } from '../../store/hooks'
import { selectCompany, setCompanies } from '../../store/workspace/workspaceSlice'

const idempotencyKey = () => `onboarding-upgrade:${crypto.randomUUID()}`

const getPlanHighlights = (plan: Plan): string[] => {
  const code = (plan.code || '').toUpperCase()
  if (code === 'FREE') {
    return [
      '1 Workspace user seat',
      '500 CRM Contacts',
      '250 Free Included Emails/mo',
      '0 Free SMS (Pay-as-you-go)',
      'Prepaid Wallet for WhatsApp & SMS',
      '1 GB Workspace Storage',
      'Audience Segments (up to 5)',
    ]
  }
  if (code === 'STARTER') {
    return [
      '3 Workspace user seats',
      '5,000 CRM Contacts',
      '3,000 Free Included Emails/mo',
      '150 Free Included SMS/mo',
      'Prepaid Wallet for WhatsApp & Overages',
      '5 GB Workspace Storage',
      'Scheduled Broadcasts & Auto-Reply',
      '2 WhatsApp Numbers & Email Accounts',
    ]
  }
  if (code === 'GROWTH') {
    return [
      '8 Workspace user seats',
      '25,000 CRM Contacts',
      '15,000 Free Included Emails/mo',
      '750 Free Included SMS/mo',
      'Prepaid Wallet for WhatsApp & Overages',
      '25 GB Workspace Storage',
      'Developer API Access & Webhooks',
      '5 WhatsApp Numbers & Email Accounts',
    ]
  }
  if (code === 'ENTERPRISE') {
    return [
      '25 Workspace user seats',
      '100,000 CRM Contacts',
      '50,000 Free Included Emails/mo',
      '3,000 Free Included SMS/mo',
      'Prepaid Wallet for WhatsApp & Overages',
      '100 GB Workspace Storage',
      'Dedicated Account Manager & 24/7 SLA',
      '10 WhatsApp Numbers & 10 SMS Senders',
    ]
  }
  return [
    'Full Unified CRM & Kanban Task Board',
    'Multi-inbox Email & WhatsApp channels',
    'Zero debt prepaid communications protection',
  ]
}

export default function SelectPlan() {
  const { companyId } = useParams()
  const dispatch = useAppDispatch()
  const navigate = useNavigate()
  const [company, setCompany] = useState<Company | null>(null)
  const [details, setDetails] = useState<CompanyPlanDetails | null>(null)
  const [plans, setPlans] = useState<Plan[]>([])
  const [billingInterval, setBillingInterval] = useState<'MONTHLY' | 'YEARLY'>('MONTHLY')
  const [loading, setLoading] = useState(true)
  const [selectedPlan, setSelectedPlan] = useState<Plan | null>(null)
  const [selectedPrice, setSelectedPrice] = useState<PlanPrice | null>(null)
  const [commandKey, setCommandKey] = useState('')
  const [quote, setQuote] = useState<SubscriptionChange | null>(null)
  const [checkout, setCheckout] = useState<CheckoutResponse | null>(null)
  const [billingEmail, setBillingEmail] = useState('')
  const [billingPhone, setBillingPhone] = useState('')
  const [billingProfile, setBillingProfile] = useState<CompanyBillingProfile>({
    legalBusinessName: '',
    taxIdOrGstin: '',
    billingEmail: '',
    billingPhone: '',
    addressLine1: '',
    addressLine2: '',
    city: '',
    state: '',
    postalCode: '',
    country: 'India',
  })
  const [submitting, setSubmitting] = useState(false)
  const [, setError] = useSnackbarState('', 'error')
  const [verificationResult, setVerificationResult] = useState<RazorpayPaymentSuccess | null>(null)
  const [verificationOpen, setVerificationOpen] = useState(false)

  useEffect(() => {
    if (!companyId) return
    let active = true
    setLoading(true)
    Promise.all([
      workspaceApi.list(),
      billingApi.eligiblePlans(companyId),
      billingApi.companyPlan(companyId),
      billingApi.getBillingProfile(companyId).catch(() => null),
    ])
      .then(([companies, eligible, planDetails, prof]) => {
        if (!active) return
        const currentCompany = companies.find((item) => item.companyId === companyId) ?? null
        setCompany(currentCompany)
        setDetails(planDetails)
        setPlans(customerVisiblePlans(eligible).filter((plan) => plan.status === 'PUBLISHED'))
        const defaultEmail = prof?.billingEmail || currentCompany?.businessEmail || ''
        const defaultPhone = prof?.billingPhone || ''
        setBillingEmail(defaultEmail)
        setBillingPhone(defaultPhone)
        if (prof) {
          setBillingProfile({
            legalBusinessName: prof.legalBusinessName || currentCompany?.name || '',
            taxIdOrGstin: prof.taxIdOrGstin || '',
            billingEmail: defaultEmail,
            billingPhone: defaultPhone,
            addressLine1: prof.addressLine1 || '',
            addressLine2: prof.addressLine2 || '',
            city: prof.city || '',
            state: prof.state || '',
            postalCode: prof.postalCode || '',
            country: prof.country || 'India',
          })
        } else {
          setBillingProfile((prev) => ({
            ...prev,
            legalBusinessName: currentCompany?.name || '',
            billingEmail: defaultEmail,
          }))
        }
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

  const beginPaidSelection = (plan: Plan, preferredPrice?: PlanPrice | null) => {
    let price: PlanPrice | null | undefined = preferredPrice
    if (!price) {
      price =
        (billingInterval === 'YEARLY'
          ? plan.prices?.find((p) => p.billingCycle === 'YEARLY' && p.status === 'ACTIVE')
          : plan.prices?.find((p) => p.billingCycle === 'MONTHLY' && p.status === 'ACTIVE')) ??
        defaultPlanPrice(plan)
    }

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
    setBillingEmail(billingProfile.billingEmail || company?.businessEmail || '')
    setBillingPhone(billingProfile.billingPhone || '')
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

      const profileToSave: CompanyBillingProfile = {
        ...billingProfile,
        legalBusinessName: (billingProfile.legalBusinessName || company?.name || '').trim(),
        billingEmail: (billingEmail || billingProfile.billingEmail || '').trim(),
        billingPhone: (billingPhone || billingProfile.billingPhone || '').trim(),
        taxIdOrGstin: (billingProfile.taxIdOrGstin || '').trim(),
        addressLine1: (billingProfile.addressLine1 || '').trim(),
        addressLine2: (billingProfile.addressLine2 || '').trim(),
        city: (billingProfile.city || '').trim(),
        state: (billingProfile.state || '').trim(),
        postalCode: (billingProfile.postalCode || '').trim(),
        country: billingProfile.country || 'India',
      }
      try {
        await billingApi.updateBillingProfile(companyId, profileToSave)
        setBillingProfile(profileToSave)
      } catch (profError) {
        showGlobalSnackbar(apiErrorMessage(profError, 'Could not save your billing profile. Checkout will continue with the details you entered.'), { variant: 'warning' })
      }

      const prepared = await billingApi.upgradeCheckout(companyId, {
        changeId: currentQuote.changeId,
        quoteReference: currentQuote.quoteReference,
        customer: {
          name: profileToSave.legalBusinessName || company?.name?.trim() || null,
          email: profileToSave.billingEmail || billingEmail.trim(),
          phone: profileToSave.billingPhone || billingPhone.trim(),
        },
      })
      if (!prepared.paymentSessionId && !prepared.razorpayOrderId) {
        throw new Error('Backend checkout did not include an order session.')
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
      const res = await launchRazorpayCheckout({
        key: prepared.keyId || '',
        orderId: prepared.razorpayOrderId || prepared.paymentSessionId || prepared.providerOrderId || '',
        amount: prepared.amount,
        amountInPaise: prepared.amountInPaise,
        customer: {
          name: profileToSave.legalBusinessName || company?.name?.trim() || undefined,
          email: profileToSave.billingEmail || billingEmail.trim(),
          phone: profileToSave.billingPhone || billingPhone.trim(),
        },
      })
      setVerificationResult(res)
      setVerificationOpen(true)
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
      const res = await launchRazorpayCheckout({
        key: checkout.keyId || '',
        orderId: checkout.razorpayOrderId || checkout.paymentSessionId || checkout.providerOrderId || '',
        amount: checkout.amount,
        amountInPaise: checkout.amountInPaise,
        customer: {
          name: billingProfile.legalBusinessName || company?.name?.trim() || undefined,
          email: billingProfile.billingEmail || billingEmail.trim(),
          phone: billingProfile.billingPhone || billingPhone.trim(),
        },
      })
      setVerificationResult(res)
      setVerificationOpen(true)
    } catch (paymentError) {
      setError(
        paymentError instanceof Error ? paymentError.message : 'Unable to open Razorpay checkout',
      )
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="min-h-dvh bg-slate-950 text-slate-100 px-4 py-8 sm:px-8 sm:py-12 lg:px-12 w-full">
      <main className="mx-auto w-full max-w-[1720px]">
        {/* Header with Monthly / Annual Switcher */}
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 pb-4">
          <div>
            <h1 className="text-2xl sm:text-3xl font-extrabold tracking-tight text-white">
              Available Plans
            </h1>
            <p className="mt-1 text-xs sm:text-sm text-slate-400">
              Predictable platform subscriptions bundled with zero-debt prepaid wallet protections.
            </p>
          </div>

          {/* Monthly / Annual Toggle */}
          <div className="inline-flex items-center p-1 rounded-xl bg-slate-900/60 border border-slate-800/80 self-start sm:self-auto shadow-sm">
            <button
              type="button"
              onClick={() => setBillingInterval('MONTHLY')}
              className={`px-4 py-1.5 rounded-lg text-xs font-semibold transition-all ${
                billingInterval === 'MONTHLY'
                  ? 'bg-blue-600 text-white shadow-sm'
                  : 'text-slate-400 hover:text-white'
              }`}
            >
              Monthly
            </button>
            <button
              type="button"
              onClick={() => setBillingInterval('YEARLY')}
              className={`flex items-center gap-1.5 px-4 py-1.5 rounded-lg text-xs font-semibold transition-all ${
                billingInterval === 'YEARLY'
                  ? 'bg-blue-600 text-white shadow-sm'
                  : 'text-slate-400 hover:text-white'
              }`}
            >
              <span>Annual</span>
              <span className="bg-emerald-500/20 text-emerald-400 text-[10px] px-1.5 py-0.5 rounded-full font-bold">
                Save 17%
              </span>
            </button>
          </div>
        </div>

        {loading && (
          <div className="mt-8 grid gap-6 sm:grid-cols-2 lg:grid-cols-4">
            <CardSkeleton rows={6} />
            <CardSkeleton rows={6} />
            <CardSkeleton rows={6} />
            <CardSkeleton rows={6} />
          </div>
        )}



        {!loading && availablePlans.length === 0 && (
          <div className="mt-8 rounded-2xl bg-slate-900/30 border border-slate-800/60 p-8 text-center text-sm text-slate-400">
            No customer-selectable plans are currently published.
          </div>
        )}

        {/* 4-Tier Full-Screen Plan Cards */}
        {!loading && availablePlans.length > 0 && (
          <div className="mt-6 grid grid-cols-1 md:grid-cols-2 xl:grid-cols-4 gap-6 items-stretch">
            {availablePlans.map((plan) => {
              const current = details?.plan?.planId === plan.planId
              const isGrowth = plan.code?.toUpperCase() === 'GROWTH'
              const isStarter = plan.code?.toUpperCase() === 'STARTER'
              const isEnterprise = plan.code?.toUpperCase() === 'ENTERPRISE'

              const matchedPrice =
                (billingInterval === 'YEARLY'
                  ? plan.prices?.find((p) => p.billingCycle === 'YEARLY' && p.status === 'ACTIVE')
                  : plan.prices?.find(
                      (p) => p.billingCycle === 'MONTHLY' && p.status === 'ACTIVE',
                    )) ?? defaultPlanPrice(plan)

              const priceAmount = matchedPrice ? matchedPrice.amount : plan.basePrice
              const priceCurrency = matchedPrice ? matchedPrice.currency : plan.currency
              const highlights = getPlanHighlights(plan)

              return (
                <div
                  key={plan.planId}
                  className={`rounded-2xl p-6 sm:p-7 flex flex-col justify-between transition-all duration-200 relative ${
                    current
                      ? 'border-2 border-white/90 bg-slate-900/50 shadow-2xl ring-1 ring-white/20'
                      : isGrowth
                        ? 'border border-purple-500/30 bg-slate-900/35 hover:bg-slate-900/50 shadow-lg shadow-purple-500/5'
                        : 'border border-slate-800/80 bg-slate-900/30 hover:bg-slate-900/45 shadow-sm'
                  }`}
                >
                  <div className="space-y-5">
                    {/* Header Badges */}
                    <div className="flex items-center justify-between min-h-[26px]">
                      {current ? (
                        <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-semibold bg-white/10 text-white border border-white/20">
                          <FiCheckCircle className="text-xs" /> Active Plan
                        </span>
                      ) : isGrowth ? (
                        <span className="inline-flex items-center px-3 py-1 rounded-full text-xs font-bold bg-gradient-to-r from-blue-600 to-indigo-600 text-white shadow-sm">
                          ⭐ Most Popular
                        </span>
                      ) : isStarter ? (
                        <span className="inline-flex items-center px-3 py-1 rounded-full text-xs font-semibold bg-indigo-500/15 text-indigo-400">
                          ⚡ Best for Startups
                        </span>
                      ) : isEnterprise ? (
                        <span className="inline-flex items-center px-3 py-1 rounded-full text-xs font-semibold bg-amber-500/10 text-amber-300 border border-amber-500/20">
                          💎 Scale & SLAs
                        </span>
                      ) : (
                        <span />
                      )}
                    </div>

                    {/* Plan Name & Description */}
                    <div>
                      <h2 className="text-xl sm:text-2xl font-bold text-white tracking-tight">
                        {plan.name}
                      </h2>
                      <p className="text-xs text-slate-400 mt-2 min-h-[36px] leading-relaxed">
                        {plan.description ||
                          'Essential tools for solopreneurs & small teams starting out with CRM and communication'}
                      </p>
                    </div>

                    {/* Pricing */}
                    <div>
                      <div className="text-3xl font-extrabold text-white tracking-tight">
                        {priceAmount === 0 ? '₹0.00' : money(priceAmount, priceCurrency)}
                      </div>
                      <div className="text-xs text-slate-400 mt-1">
                        {priceAmount === 0
                          ? 'forever free'
                          : billingInterval === 'YEARLY'
                            ? `per year (billed annually · ₹${Math.round(priceAmount / 12).toLocaleString()}/mo)`
                            : 'per month'}
                      </div>
                    </div>

                    {/* Included Features List */}
                    <div className="space-y-3 text-xs pt-2">
                      <div className="font-bold text-slate-400 text-xs uppercase tracking-wider pb-1">
                        INCLUDED IN PLAN:
                      </div>
                      {highlights.map((item, idx) => {
                        const isMainComms =
                          item.includes('Emails/mo') ||
                          item.includes('SMS/mo') ||
                          item.includes('WhatsApp')
                        return (
                          <div key={idx} className="flex items-start gap-2.5">
                            <FiCheckCircle className="text-teal-400 shrink-0 text-sm mt-0.5" />
                            <span
                              className={`leading-tight text-xs ${
                                isMainComms
                                  ? 'text-white font-semibold'
                                  : 'text-slate-200 font-medium'
                              }`}
                            >
                              {item}
                            </span>
                          </div>
                        )
                      })}
                    </div>
                  </div>

                  {/* Action Button */}
                  <div className="pt-6 mt-6">
                    {current ? (
                      <button
                        type="button"
                        className="w-full py-3 px-4 rounded-xl bg-slate-800/40 border border-slate-700/60 hover:bg-slate-800/60 text-slate-300 font-semibold text-sm text-center transition"
                        onClick={continueToWorkspace}
                      >
                        Current Subscription
                      </button>
                    ) : isGrowth ? (
                      <button
                        type="button"
                        className="w-full py-3 px-4 rounded-xl bg-gradient-to-r from-purple-600 to-blue-600 hover:from-purple-500 hover:to-blue-500 text-white font-semibold text-sm transition shadow-lg shadow-purple-600/25"
                        onClick={() => beginPaidSelection(plan, matchedPrice)}
                      >
                        Choose Plan
                      </button>
                    ) : priceAmount === 0 ? (
                      <button
                        type="button"
                        className="w-full py-3 px-4 rounded-xl bg-blue-600 hover:bg-blue-500 text-white font-semibold text-sm transition shadow-lg shadow-blue-600/20"
                        onClick={continueToWorkspace}
                      >
                        Select Free
                      </button>
                    ) : (
                      <button
                        type="button"
                        className="w-full py-3 px-4 rounded-xl bg-blue-600 hover:bg-blue-500 text-white font-semibold text-sm transition shadow-lg shadow-blue-600/20"
                        onClick={() => beginPaidSelection(plan, matchedPrice)}
                      >
                        Choose Plan
                      </button>
                    )}
                  </div>
                </div>
              )
            })}
          </div>
        )}

        {/* Modal for Checkout Confirmation */}
        {selectedPlan && selectedPrice && (
          <div className="fixed inset-0 z-50 flex items-end justify-center bg-slate-950/80 backdrop-blur-sm p-4 sm:items-center">
            <div className="panel w-full max-w-xl p-6 sm:p-7 bg-slate-900 border border-slate-800 shadow-2xl rounded-2xl">
              <div className="flex items-start justify-between gap-4">
                <div>
                  <p className="text-xs font-bold uppercase tracking-wider text-primary">
                    Plan checkout
                  </p>
                  <h2 className="mt-1 text-2xl font-black text-white">
                    Choose {selectedPlan.name}
                  </h2>
                  <p className="mt-2 text-sm text-slate-400">
                    {money(selectedPrice.amount, selectedPrice.currency)} ·{' '}
                    {selectedPrice.billingCycle.toLowerCase()}
                  </p>
                </div>
                <button
                  type="button"
                  className="secondary-button p-2 text-slate-400 hover:text-white"
                  aria-label="Close checkout"
                  disabled={submitting}
                  onClick={closeCheckout}
                >
                  <FiX className="text-lg" />
                </button>
              </div>

              <form
                className="mt-6 space-y-4 max-h-[75vh] overflow-y-auto pr-1"
                onSubmit={preparePaidSelection}
              >
                <div className="bg-slate-950/40 p-4 rounded-xl border border-slate-800/60 space-y-3.5">
                  <div className="text-xs font-bold uppercase tracking-wider text-primary">
                    Billing & Tax Details
                  </div>
                  <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                    <div>
                      <label className="text-xs font-semibold text-slate-300">
                        Legal Business Name
                      </label>
                      <input
                        required
                        type="text"
                        className="form-input mt-1 w-full bg-slate-950 border-slate-800 text-white text-xs py-2 rounded-xl"
                        placeholder="Acme Corp Pvt Ltd"
                        value={billingProfile.legalBusinessName || ''}
                        onChange={(event) =>
                          setBillingProfile({
                            ...billingProfile,
                            legalBusinessName: event.target.value,
                          })
                        }
                      />
                    </div>
                    <div>
                      <label className="text-xs font-semibold text-slate-300">
                        GSTIN / Tax ID{' '}
                        <span className="text-slate-500 font-normal">(Optional)</span>
                      </label>
                      <input
                        type="text"
                        className="form-input mt-1 w-full bg-slate-950 border-slate-800 text-white text-xs py-2 rounded-xl uppercase font-mono"
                        placeholder="29AAAAA0000A1Z5"
                        value={billingProfile.taxIdOrGstin || ''}
                        onChange={(event) =>
                          setBillingProfile({
                            ...billingProfile,
                            taxIdOrGstin: event.target.value.toUpperCase(),
                          })
                        }
                      />
                    </div>
                  </div>

                  <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                    <div>
                      <label className="text-xs font-semibold text-slate-300">Billing Email</label>
                      <input
                        required
                        type="email"
                        className="form-input mt-1 w-full bg-slate-950 border-slate-800 text-white text-xs py-2 rounded-xl"
                        placeholder="billing@company.com"
                        value={billingEmail}
                        onChange={(event) => {
                          setBillingEmail(event.target.value)
                          setBillingProfile({ ...billingProfile, billingEmail: event.target.value })
                        }}
                      />
                    </div>
                    <div>
                      <label className="text-xs font-semibold text-slate-300">Billing Phone</label>
                      <PhoneInput
                        required
                        className="form-input mt-1 w-full bg-slate-950 border-slate-800 text-white text-xs py-2 rounded-xl font-mono"
                        placeholder="9876543210"
                        value={billingPhone}
                        onChange={(value) => {
                          setBillingPhone(value)
                          setBillingProfile({ ...billingProfile, billingPhone: value })
                        }}
                      />
                    </div>
                  </div>

                  <div>
                    <label className="text-xs font-semibold text-slate-300">Street Address</label>
                    <input
                      required
                      type="text"
                      className="form-input mt-1 w-full bg-slate-950 border-slate-800 text-white text-xs py-2 rounded-xl"
                      placeholder="123 Tech Park, 4th Floor"
                      value={billingProfile.addressLine1 || ''}
                      onChange={(event) =>
                        setBillingProfile({ ...billingProfile, addressLine1: event.target.value })
                      }
                    />
                  </div>

                  <div>
                    <label className="text-xs font-semibold text-slate-300">Country</label>
                    <CountrySelect value={billingProfile.country ?? ''} valueType="name" required
                      onChange={country => setBillingProfile({ ...billingProfile, country, state: '', city: '' })} />
                  </div>
                  <div className="grid grid-cols-3 gap-2 sm:gap-3">
                    <div>
                      <label className="text-xs font-semibold text-slate-300">State</label>
                      <StateSelect country={billingProfile.country}
                        required
                        className="form-input mt-1 w-full bg-slate-950 border-slate-800 text-white text-xs py-2 rounded-xl"
                        placeholder="Select state"
                        value={billingProfile.state || ''}
                        onChange={(state) =>
                          setBillingProfile({ ...billingProfile, state, city: '' })
                        }
                      />
                    </div>
                    <div>
                      <label className="text-xs font-semibold text-slate-300">City</label>
                      <CitySelect
                        state={billingProfile.state}
                        country={billingProfile.country || 'India'}
                        value={billingProfile.city || ''}
                        onChange={(city) => setBillingProfile({ ...billingProfile, city })}
                        placeholder="Select city"
                        className="mt-1 w-full !bg-slate-950 !border-slate-800 !text-white text-xs py-2 rounded-xl"
                        required
                      />
                    </div>
                    <div>
                      <label className="text-xs font-semibold text-slate-300">PIN Code</label>
                      <input
                        required
                        type="text"
                        className="form-input mt-1 w-full bg-slate-950 border-slate-800 text-white text-xs py-2 rounded-xl font-mono"
                        placeholder="560001"
                        value={billingProfile.postalCode || ''}
                        onChange={(event) =>
                          setBillingProfile({ ...billingProfile, postalCode: event.target.value })
                        }
                      />
                    </div>
                  </div>
                  <p className="text-[11px] text-slate-400">
                    Saved to your company profile for GST tax invoices and future recurring
                    payments.
                  </p>
                </div>

                {quote && (
                  <div className="rounded-xl bg-slate-950/60 border border-slate-800/80 p-4 space-y-2">
                    {upgradeQuoteRows(quote).map(([label, value]) => (
                      <div key={label} className="flex justify-between gap-4 py-1 text-sm">
                        <span className="text-slate-400">{label}</span>
                        <strong className="text-white font-mono">
                          {money(value, quote.currency ?? selectedPrice.currency)}
                        </strong>
                      </div>
                    ))}
                  </div>
                )}

                <div className="pt-2">
                  {checkout ? (
                    <button
                      type="button"
                      className="primary-button w-full justify-center text-sm py-3 rounded-xl shadow-lg cursor-pointer"
                      disabled={submitting}
                      onClick={() => void pay()}
                    >
                      <FiCreditCard className="mr-2" /> Pay with Razorpay ·{' '}
                      {money(checkout.amount, checkout.currency)}
                    </button>
                  ) : (
                    <button
                      className="primary-button w-full justify-center text-sm py-3 rounded-xl shadow-lg font-semibold cursor-pointer"
                      disabled={submitting}
                    >
                      {submitting
                        ? 'Opening Razorpay…'
                        : quote
                          ? `Pay with Razorpay · ${money(quote.amountDue, quote.currency ?? selectedPrice.currency)}`
                          : 'Review upgrade amount'}
                    </button>
                  )}
                </div>
              </form>
            </div>
          </div>
        )}

        {companyId && (
          <PaymentVerificationAndAutoPayModal
            isOpen={verificationOpen}
            companyId={companyId}
            companyName={company?.name || billingProfile.legalBusinessName || ''}
            paymentResult={verificationResult}
            customerEmail={billingProfile.billingEmail || billingEmail}
            customerPhone={billingProfile.billingPhone || billingPhone}
            onDone={() => {
              setVerificationOpen(false)
              navigate('/workspace')
            }}
          />
        )}
      </main>
    </div>
  )
}
import { showGlobalSnackbar } from '../../components/feedback/SnackbarProvider'
