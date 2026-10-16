import { useEffect, useMemo, useRef, useState } from 'react'
import {
  FiAlertTriangle,
  FiCheckCircle,
  FiCreditCard,
  FiDollarSign,
  FiDownload,
  FiFileText,
  FiPieChart,
  FiPlus,
  FiPhone,
  FiRefreshCw,
  FiSave,
  FiShield,
  FiZap,
} from 'react-icons/fi'
import { billingApi } from '../../features/billing/billingApi'
import CountrySelect from '../../components/ui/CountrySelect'
import CitySelect from '../../components/ui/CitySelect'
import {
  customerVisiblePlans,
  defaultPlanPrice,
  money,
  subscribedPlanPrice,
} from '../../features/billing/billingModel'
import { launchCashfreeCheckout } from '../../features/billing/cashfreeCheckout'
import { savePendingBillingCheckout } from '../../features/billing/pendingCheckout'
import type {
  AutoPayDetails,
  BillingUsageSummary,
  CheckoutCustomer,
  CompanyBillingProfile,
  CompanyPlanDetails,
  CompanyWallet,
  Invoice,
  Plan,
  PlanPrice,
  WalletTransaction,
} from '../../features/billing/types'
import { autoPayStatusLabel } from '../../features/billing/types'
import { useWorkspaceEpochScope } from '../../features/workspace/useWorkspaceEpochScope'
import { apiErrorMessage } from '../../lib/api/apiClient'
import { useAppSelector } from '../../store/hooks'
import { selectSelectedCompany, selectWorkspaceEpoch } from '../../store/workspace/workspaceSlice'
import { PageSkeleton } from '../../components/ui/Skeleton'
import AutoPayModal from '../../components/billing/AutoPayModal'

type TabKey = 'subscription' | 'wallet' | 'usage' | 'invoices'

const dateFmt = (value?: string | null) =>
  value ? new Date(value).toLocaleDateString('en-IN', { dateStyle: 'medium' }) : '—'

const dateTimeFmt = (value?: string | null) =>
  value
    ? new Date(value).toLocaleString('en-IN', {
        dateStyle: 'medium',
        timeStyle: 'short',
      })
    : '—'

const idempotencyKey = (prefix: string) => `${prefix}:${crypto.randomUUID()}`

type ChangeMode = 'INITIAL' | 'UPGRADE' | 'DOWNGRADE'
type ChangeDraft = { plan: Plan; price: PlanPrice; mode: ChangeMode; commandKey: string }

type ChartSlice = {
  label: string
  value: number
  color: string
}

function DonutChart({
  title,
  subtitle,
  slices,
  centerPrimary,
  centerSecondary,
  size = 140,
  strokeWidth = 14,
}: {
  title: string
  subtitle?: string
  slices: ChartSlice[]
  centerPrimary: string
  centerSecondary: string
  size?: number
  strokeWidth?: number
}) {
  const total = slices.reduce((acc, s) => acc + Math.max(0, s.value), 0)
  const radius = (size - strokeWidth) / 2
  const circumference = 2 * Math.PI * radius

  return (
    <div className="bg-slate-900/30 dark:bg-slate-950/40 rounded-2xl p-5 sm:p-6 flex flex-col justify-between shadow-sm">
      <div>
        <h4 className="text-sm font-bold text-foreground">{title}</h4>
        {subtitle && <p className="text-xs text-muted-foreground mt-0.5">{subtitle}</p>}
      </div>

      <div className="my-5 flex flex-col items-center justify-center">
        <div className="relative" style={{ width: size, height: size }}>
          <svg width={size} height={size} viewBox={`0 0 ${size} ${size}`} className="-rotate-90">
            {/* Base background ring */}
            <circle
              cx={size / 2}
              cy={size / 2}
              r={radius}
              fill="none"
              stroke="currentColor"
              strokeWidth={strokeWidth}
              className="text-slate-800/60 dark:text-slate-800/40"
            />
            {/* Colored slices */}
            {total > 0 &&
              slices.map((slice, i) => {
                const sliceLength = (Math.max(0, slice.value) / total) * circumference
                const prevSum = slices.slice(0, i).reduce((sum, p) => sum + Math.max(0, p.value), 0)
                const offset = (prevSum / total) * circumference
                return (
                  <circle
                    key={slice.label}
                    cx={size / 2}
                    cy={size / 2}
                    r={radius}
                    fill="none"
                    stroke={slice.color}
                    strokeWidth={strokeWidth}
                    strokeDasharray={`${sliceLength} ${circumference - sliceLength}`}
                    strokeDashoffset={-offset}
                    strokeLinecap="round"
                    className="transition-all duration-500"
                  />
                )
              })}
          </svg>

          {/* Center stats */}
          <div className="absolute inset-0 flex flex-col items-center justify-center text-center px-2">
            <span className="text-2xl font-black text-foreground tracking-tight">
              {centerPrimary}
            </span>
            <span className="text-[10px] uppercase tracking-wider font-semibold text-muted-foreground mt-0.5">
              {centerSecondary}
            </span>
          </div>
        </div>
      </div>

      {/* Legend */}
      <div className="space-y-1.5 pt-2">
        {slices.map((slice) => {
          const pct = total > 0 ? Math.round((Math.max(0, slice.value) / total) * 100) : 0
          return (
            <div key={slice.label} className="flex items-center justify-between text-xs">
              <span className="flex items-center gap-2 text-muted-foreground">
                <span
                  className="w-2.5 h-2.5 rounded-full shrink-0"
                  style={{ backgroundColor: slice.color }}
                />
                {slice.label}
              </span>
              <span className="font-semibold text-foreground font-mono">
                {slice.value.toLocaleString()}{' '}
                <span className="text-muted-foreground font-normal">({pct}%)</span>
              </span>
            </div>
          )
        })}
      </div>
    </div>
  )
}

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

export default function BillingPage() {
  const selectedCompany = useAppSelector(selectSelectedCompany)
  const epoch = useAppSelector(selectWorkspaceEpoch)
  const { capture: captureScope, isCurrent: scopeIsCurrent } = useWorkspaceEpochScope(
    selectedCompany?.companyId,
    epoch,
  )

  const [activeTab, setActiveTab] = useState<TabKey>('subscription')
  const [billingInterval, setBillingInterval] = useState<'MONTHLY' | 'YEARLY'>('MONTHLY')
  const [loading, setLoading] = useState(true)
  const [refreshing, setRefreshing] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')

  // Subscription state
  const [details, setDetails] = useState<CompanyPlanDetails | null>(null)
  const [plans, setPlans] = useState<Plan[]>([])
  const [draft, setDraft] = useState<ChangeDraft | null>(null)
  const [customer, setCustomer] = useState<CheckoutCustomer>({
    name: selectedCompany?.name ?? '',
    email: selectedCompany?.businessEmail ?? '',
    phone: '',
  })
  const [cancelOpen, setCancelOpen] = useState(false)
  const [planMutationPending, setPlanMutationPending] = useState(false)

  // Wallet state
  const [wallet, setWallet] = useState<CompanyWallet | null>(null)
  const [transactions, setTransactions] = useState<WalletTransaction[]>([])
  const [txTotal, setTxTotal] = useState(0)
  const [topupAmount, setTopupAmount] = useState<number>(1000)
  const [customTopupInput, setCustomTopupInput] = useState<string>('1000')
  const [topupModalOpen, setTopupModalOpen] = useState(false)
  const [topupPending, setTopupPending] = useState(false)
  const [autoRechargeSaving, setAutoRechargeSaving] = useState(false)
  const [autoRechargeEnabled, setAutoRechargeEnabled] = useState(false)
  const [autoThreshold, setAutoThreshold] = useState<number>(500)
  const [autoAmount, setAutoAmount] = useState<number>(2000)

  // Smart AutoPay state
  const [autoPayDetails, setAutoPayDetails] = useState<AutoPayDetails | null>(null)
  const [autoPaySetupModalOpen, setAutoPaySetupModalOpen] = useState(false)
  const [autopayModalOpen, setAutopayModalOpen] = useState(false)
  const [autopayBannerDismissed, setAutopayBannerDismissed] = useState(false)
  const [autopayRenewPlan, setAutopayRenewPlan] = useState(true)
  const [autopayReplenishWallet, setAutopayReplenishWallet] = useState(true)
  const [autopayPaymentMethod, setAutopayPaymentMethod] = useState<'UPI' | 'CARD' | 'NETBANKING'>(
    'UPI',
  )

  // Usage state
  const [usage, setUsage] = useState<BillingUsageSummary | null>(null)

  // Invoices & Profile state
  const [invoices, setInvoices] = useState<Invoice[]>([])
  const [profile, setProfile] = useState<CompanyBillingProfile>({
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
  const [profileSaving, setProfileSaving] = useState(false)

  const requestRef = useRef(0)
  const companyId = selectedCompany?.companyId ?? null

  const loadAll = async (background = false) => {
    if (!companyId) {
      setLoading(false)
      return
    }
    const requestId = ++requestRef.current
    const scope = captureScope()
    background ? setRefreshing(true) : setLoading(true)
    setError('')

    try {
      const [planDetails, eligiblePlans, walletData, txData, usageData, invoicesData, profileData, autoPayData] =
        await Promise.all([
          billingApi.companyPlan(companyId).catch(() => ({ subscription: null, plan: null })),
          billingApi.eligiblePlans(companyId).catch(() => []),
          billingApi.getWallet(companyId).catch(() => null),
          billingApi
            .getTransactions(companyId, 0, 20)
            .catch(() => ({ content: [], totalElements: 0 })),
          billingApi.getUsage(companyId).catch(() => null),
          billingApi.getInvoices(companyId).catch(() => []),
          billingApi.getBillingProfile(companyId).catch(() => ({
            legalBusinessName: selectedCompany?.name ?? '',
            taxIdOrGstin: '',
            billingEmail: selectedCompany?.businessEmail ?? '',
            billingPhone: '',
            addressLine1: '',
            addressLine2: '',
            city: '',
            state: '',
            postalCode: '',
            country: 'India',
          })),
          billingApi.getAutoPayDetails(companyId).catch(() => null),
        ])

      if (requestRef.current !== requestId || !scopeIsCurrent(scope)) return

      setDetails(planDetails)
      setPlans(customerVisiblePlans(eligiblePlans))
      if (autoPayData) {
        setAutoPayDetails(autoPayData)
      }
      if (walletData) {
        setWallet(walletData)
        setAutoRechargeEnabled(walletData.autoRechargeEnabled)
        setAutoThreshold(walletData.autoRechargeThreshold || 500)
        setAutoAmount(walletData.autoRechargeAmount || 2000)
      }
      setTransactions(txData.content)
      setTxTotal(txData.totalElements)
      setUsage(usageData)
      setInvoices(invoicesData)
      if (profileData) {
        setProfile(profileData)
        setCustomer((curr) => ({
          name:
            profileData.legalBusinessName || curr.name || selectedCompany?.name || 'Company Admin',
          email: profileData.billingEmail || curr.email || selectedCompany?.businessEmail || '',
          phone: profileData.billingPhone || curr.phone || '',
        }))
      }
    } catch (cause) {
      if (requestRef.current === requestId && scopeIsCurrent(scope)) {
        setError(apiErrorMessage(cause, 'Unable to load Billing.'))
      }
    } finally {
      if (requestRef.current === requestId && scopeIsCurrent(scope)) {
        setLoading(false)
        setRefreshing(false)
      }
    }
  }

  useEffect(() => {
    setDraft(null)
    setCancelOpen(false)
    setNotice('')
    setCustomer({
      name: selectedCompany?.name ?? '',
      email: selectedCompany?.businessEmail ?? '',
      phone: '',
    })
    void loadAll()
  }, [companyId, epoch])

  const currentPlan = details?.plan ?? null
  const currentSubscription = details?.subscription ?? null
  const currentPrice = useMemo(() => subscribedPlanPrice(details), [details])

  // Wallet top-up action
  const handleTopup = async () => {
    if (!companyId) return
    const amountNum = parseFloat(customTopupInput) || topupAmount
    if (amountNum < 10) {
      setError('Minimum top-up amount is ₹10.00')
      return
    }

    const resolvedPhone = (customer.phone || '').trim() || (profile?.billingPhone || '').trim()
    if (!resolvedPhone) {
      setError('Please enter a billing mobile number to proceed with payment.')
      return
    }

    setTopupPending(true)
    setError('')
    try {
      const order = await billingApi.createTopupOrder(companyId, {
        amount: amountNum,
        currency: 'INR',
        customer: {
          name: customer.name || selectedCompany?.name || 'Company Admin',
          email: customer.email || selectedCompany?.businessEmail || 'billing@company.local',
          phone: resolvedPhone,
        },
      })

      if (order.paymentSessionId && !order.paymentSessionId.startsWith('simulated_')) {
        await launchCashfreeCheckout(order.paymentSessionId)
      } else {
        const updated = await billingApi.confirmTopup(companyId, order.orderId)
        setWallet(updated)
        setNotice(`Successfully added ₹${amountNum.toFixed(2)} to Communications Wallet!`)
        setTopupModalOpen(false)
        const [txData, usageData] = await Promise.all([
          billingApi.getTransactions(companyId, 0, 20),
          billingApi.getUsage(companyId),
        ])
        setTransactions(txData.content)
        setTxTotal(txData.totalElements)
        setUsage(usageData)
      }
    } catch (cause) {
      setError(apiErrorMessage(cause, 'Failed to process wallet top-up.'))
    } finally {
      setTopupPending(false)
    }
  }

  // Unified Smart AutoPay save handler
  const handleSaveAutopay = async (enabled: boolean) => {
    if (!companyId) return
    if (enabled && autoPayDetails?.status !== 'ACTIVE') {
      setAutopayModalOpen(false)
      setAutoPaySetupModalOpen(true)
      return
    }

    setAutoRechargeSaving(true)
    setError('')
    try {
      const updated = await billingApi.configureAutoRecharge(companyId, {
        enabled: enabled && autopayReplenishWallet,
        threshold: autoThreshold,
        amount: autoAmount,
      })
      setWallet(updated)
      setAutoRechargeEnabled(updated.autoRechargeEnabled)
      setNotice(
        enabled ? 'Smart AutoPay rules successfully activated!' : 'Smart AutoPay has been paused.',
      )
      setAutopayModalOpen(false)
    } catch (cause) {
      setError(apiErrorMessage(cause, 'Failed to update AutoPay settings.'))
    } finally {
      setAutoRechargeSaving(false)
    }
  }

  const handleCancelAutoPay = async () => {
    if (!companyId) return
    setAutoRechargeSaving(true)
    setError('')
    try {
      await billingApi.cancelAutoPay(companyId)
      await billingApi.configureAutoRecharge(companyId, { enabled: false })
      setAutoPayDetails({ status: 'CANCELLED', maxAmount: 0, currency: 'INR' })
      setAutoRechargeEnabled(false)
      setNotice('AutoPay mandate has been cancelled.')
    } catch (cause) {
      setError(apiErrorMessage(cause, 'Failed to cancel AutoPay.'))
    } finally {
      setAutoRechargeSaving(false)
    }
  }

  // Profile save
  const handleSaveProfile = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!companyId) return
    setProfileSaving(true)
    setError('')
    try {
      const updated = await billingApi.updateBillingProfile(companyId, profile)
      setProfile(updated)
      setNotice('Billing & tax profile updated successfully.')
    } catch (cause) {
      setError(apiErrorMessage(cause, 'Failed to update billing profile.'))
    } finally {
      setProfileSaving(false)
    }
  }

  // Plan actions
  const beginInitialSelection = (plan: Plan, price: PlanPrice) => {
    const mode = currentSubscription ? 'UPGRADE' : 'INITIAL'
    setDraft({
      plan,
      price,
      mode,
      commandKey: idempotencyKey(mode === 'UPGRADE' ? 'sub-upg' : 'sub-init'),
    })
    setCustomer({
      name: profile.legalBusinessName || selectedCompany?.name || 'Company Admin',
      email: profile.billingEmail || selectedCompany?.businessEmail || '',
      phone: profile.billingPhone || customer.phone || '',
    })
  }

  const submitInitialSelection = async () => {
    if (!companyId || !draft) return
    setPlanMutationPending(true)
    setError('')
    try {
      const resolvedPhone = (customer.phone || '').trim() || (profile.billingPhone || '').trim()

      // If paid checkout, validate contact and persist complete billing profile for GST invoicing
      if (draft.price.amount > 0) {
        if (!resolvedPhone) {
          setError('Please provide a valid billing mobile number to proceed.')
          setPlanMutationPending(false)
          return
        }

        try {
          const updatedProfile = await billingApi.updateBillingProfile(companyId, {
            ...profile,
            legalBusinessName: (profile.legalBusinessName || customer.name || '').trim(),
            billingEmail: (customer.email || profile.billingEmail || '').trim(),
            billingPhone: resolvedPhone,
          })
          setProfile(updatedProfile)
        } catch (profileErr) {
          console.warn('Could not persist billing profile before checkout:', profileErr)
        }
      }

      const checkoutCustomer = {
        name:
          profile.legalBusinessName || customer.name || selectedCompany?.name || 'Company Admin',
        email:
          customer.email ||
          profile.billingEmail ||
          selectedCompany?.businessEmail ||
          'billing@company.local',
        phone: resolvedPhone,
      }

      if (!currentSubscription) {
        // Initial plan selection flow (No active subscription exists)
        if (draft.plan.basePrice === 0) {
          await billingApi.selectPlan(companyId, draft.plan.planId)
          setNotice('Free plan activated successfully.')
          setDraft(null)
          await loadAll(true)
        } else {
          const order = await billingApi.initialCheckout(companyId, {
            planId: draft.plan.planId,
            planPriceId: draft.price.planPriceId,
            idempotencyKey: draft.commandKey,
            customer: checkoutCustomer,
          })
          savePendingBillingCheckout({
            companyId,
            targetPlanId: draft.plan.planId,
            kind: 'INITIAL',
            paymentId: order.paymentId,
            invoiceId: order.invoiceId,
            recurringAuthorizationActionUrl: order.recurringAuthorizationActionUrl ?? null,
            previousSubscriptionId: null,
            createdAt: new Date().toISOString(),
          })
          if (order.paymentSessionId) {
            await launchCashfreeCheckout(order.paymentSessionId)
          }
        }
      } else {
        // Subscription already exists: Upgrade or Downgrade flow
        if (draft.plan.basePrice === 0) {
          const change = await billingApi.scheduleDowngrade(companyId, {
            targetPlanId: draft.plan.planId,
            targetPlanPriceId: draft.price.planPriceId,
            idempotencyKey: draft.commandKey,
          })
          setNotice(
            `Downgrade scheduled${change.effectiveAt ? ` for ${dateFmt(change.effectiveAt)}` : ' for the next billing cycle'}.`,
          )
          setDraft(null)
          await loadAll(true)
        } else {
          // Request upgrade quote from backend
          const quote = await billingApi.requestUpgrade(companyId, {
            targetPlanId: draft.plan.planId,
            targetPlanPriceId: draft.price.planPriceId,
            idempotencyKey: draft.commandKey,
          })

          if (quote.status === 'APPLIED') {
            setNotice('Upgrade applied successfully; no additional payment required.')
            setDraft(null)
            await loadAll(true)
            return
          }

          if (!quote.quoteReference) {
            throw new Error('Upgrade quote did not include a valid quote reference.')
          }

          const prepared = await billingApi.upgradeCheckout(companyId, {
            changeId: quote.changeId,
            quoteReference: quote.quoteReference,
            customer: checkoutCustomer,
          })

          savePendingBillingCheckout({
            companyId,
            targetPlanId: draft.plan.planId,
            kind: 'UPGRADE',
            paymentId: prepared.paymentId,
            invoiceId: prepared.invoiceId,
            recurringAuthorizationActionUrl: prepared.recurringAuthorizationActionUrl ?? null,
            previousSubscriptionId: currentSubscription.subscriptionId,
            createdAt: new Date().toISOString(),
          })

          if (prepared.paymentSessionId) {
            await launchCashfreeCheckout(prepared.paymentSessionId)
          }
        }
      }
    } catch (cause) {
      setError(apiErrorMessage(cause, 'Unable to complete plan change.'))
    } finally {
      setPlanMutationPending(false)
    }
  }

  const cancelSubscription = async () => {
    if (!companyId) return
    setPlanMutationPending(true)
    setError('')
    try {
      await billingApi.cancelAtPeriodEnd(companyId)
      setNotice('Subscription scheduled for cancellation at the end of the billing period.')
      setCancelOpen(false)
      await loadAll(true)
    } catch (cause) {
      setError(apiErrorMessage(cause, 'Unable to cancel subscription.'))
    } finally {
      setPlanMutationPending(false)
    }
  }

  if (loading) {
    return <PageSkeleton />
  }

  return (
    <div className="container-page pb-16 space-y-7">
      {/* Header (clean, without harsh bottom line) */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 pt-2">
        <div>
          <div className="flex items-center gap-3">
            <h1 className="text-2xl font-extrabold tracking-tight text-foreground">
              Billing & Plans
            </h1>
            {currentSubscription && (
              <span
                className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-semibold ${
                  currentSubscription.status === 'ACTIVE'
                    ? 'bg-emerald-500/15 text-emerald-400'
                    : 'bg-amber-500/15 text-amber-400'
                }`}
              >
                {currentSubscription.status}
              </span>
            )}
          </div>
          <p className="text-sm text-muted-foreground mt-1">
            Manage your subscription, prepaid communications wallet, live usage quotas, and tax
            invoices.
          </p>
        </div>

        <div className="flex items-center gap-3">
          <button
            type="button"
            className="secondary-button inline-flex items-center gap-2 text-sm px-4 py-2"
            onClick={() => void loadAll(true)}
            disabled={refreshing}
          >
            <FiRefreshCw className={refreshing ? 'animate-spin' : ''} />
            {refreshing ? 'Syncing...' : 'Refresh'}
          </button>
        </div>
      </div>

      {/* Notices & Alerts */}
      {error && (
        <div className="p-4 rounded-xl bg-destructive/10 text-destructive text-sm flex items-start gap-3">
          <FiAlertTriangle className="mt-0.5 shrink-0 text-base" />
          <div className="flex-1">{error}</div>
          <button onClick={() => setError('')} className="text-xs font-medium underline">
            Dismiss
          </button>
        </div>
      )}

      {notice && (
        <div className="p-4 rounded-xl bg-emerald-500/10 text-emerald-400 text-sm flex items-start gap-3">
          <FiCheckCircle className="mt-0.5 shrink-0 text-base" />
          <div className="flex-1">{notice}</div>
          <button onClick={() => setNotice('')} className="text-xs font-medium underline">
            Dismiss
          </button>
        </div>
      )}

      {/* Optional AutoPay Onboarding Banner for Paid Subscriptions */}
      {currentPlan &&
        Number(currentPlan.basePrice) > 0 &&
        !autoRechargeEnabled &&
        !autopayBannerDismissed && (
          <div className="p-4 sm:p-5 rounded-2xl bg-gradient-to-r from-indigo-950/60 via-purple-950/40 to-slate-900/60 border border-indigo-500/25 flex flex-col sm:flex-row sm:items-center justify-between gap-4 shadow-xl">
            <div className="flex items-start sm:items-center gap-3.5">
              <div className="w-10 h-10 rounded-xl bg-indigo-500/20 text-indigo-400 flex items-center justify-center shrink-0 mt-0.5 sm:mt-0">
                <FiShield className="text-xl" />
              </div>
              <div>
                <div className="flex items-center gap-2">
                  <h4 className="text-sm font-bold text-foreground">
                    Protect your messaging service with Smart AutoPay
                  </h4>
                  <span className="text-[10px] font-semibold px-2 py-0.5 rounded-full bg-indigo-500/20 text-indigo-300">
                    Optional
                  </span>
                </div>
                <p className="text-xs text-muted-foreground mt-0.5 max-w-2xl leading-relaxed">
                  Avoid disruption by setting up hands-free plan renewals and auto-refilling your
                  communication wallet when balance runs low.
                </p>
              </div>
            </div>
            <div className="flex items-center gap-2.5 self-end sm:self-center shrink-0">
              <button
                type="button"
                onClick={() => setAutopayBannerDismissed(true)}
                className="text-xs text-muted-foreground hover:text-foreground px-3 py-1.5 transition-colors"
              >
                Maybe Later
              </button>
              <button
                type="button"
                onClick={() => setAutopayModalOpen(true)}
                className="primary-button text-xs px-4 py-2 rounded-xl inline-flex items-center gap-1.5 shadow-md"
              >
                <FiZap className="text-amber-400" /> Set Up AutoPay
              </button>
            </div>
          </div>
        )}

      {/* Modern Segmented Pill Tabs (no horizontal line across the screen) */}
      <div className="inline-flex p-1.5 rounded-2xl bg-slate-900/40 dark:bg-slate-950/40 backdrop-blur-md gap-1 overflow-x-auto max-w-full">
        <button
          onClick={() => setActiveTab('subscription')}
          className={`flex items-center gap-2 py-2 px-4 rounded-xl text-sm font-semibold transition-all whitespace-nowrap ${
            activeTab === 'subscription'
              ? 'bg-primary text-primary-foreground shadow-md shadow-primary/20'
              : 'text-muted-foreground hover:text-foreground'
          }`}
        >
          <FiCreditCard className="text-base" />
          Subscription & Plans
        </button>

        <button
          onClick={() => setActiveTab('wallet')}
          className={`flex items-center gap-2 py-2 px-4 rounded-xl text-sm font-semibold transition-all whitespace-nowrap ${
            activeTab === 'wallet'
              ? 'bg-primary text-primary-foreground shadow-md shadow-primary/20'
              : 'text-muted-foreground hover:text-foreground'
          }`}
        >
          <FiDollarSign className="text-base" />
          Communications Wallet
          {wallet && wallet.balance < 100 && <span className="w-2 h-2 rounded-full bg-amber-400" />}
        </button>

        <button
          onClick={() => setActiveTab('usage')}
          className={`flex items-center gap-2 py-2 px-4 rounded-xl text-sm font-semibold transition-all whitespace-nowrap ${
            activeTab === 'usage'
              ? 'bg-primary text-primary-foreground shadow-md shadow-primary/20'
              : 'text-muted-foreground hover:text-foreground'
          }`}
        >
          <FiPieChart className="text-base" />
          Live Quotas & Usage
        </button>

        <button
          onClick={() => setActiveTab('invoices')}
          className={`flex items-center gap-2 py-2 px-4 rounded-xl text-sm font-semibold transition-all whitespace-nowrap ${
            activeTab === 'invoices'
              ? 'bg-primary text-primary-foreground shadow-md shadow-primary/20'
              : 'text-muted-foreground hover:text-foreground'
          }`}
        >
          <FiFileText className="text-base" />
          Invoices & Tax Info
        </button>
      </div>

      {/* TAB 1: SUBSCRIPTION & PLANS */}
      {activeTab === 'subscription' && (
        <div className="space-y-6">
          {/* Active Plan Overview Card (Clean, borderless with depth) */}
          <div className="bg-slate-900/30 dark:bg-slate-950/40 backdrop-blur-md p-6 sm:p-7 rounded-2xl shadow-sm flex flex-col md:flex-row md:items-center justify-between gap-6">
            <div className="space-y-2">
              <div className="flex items-center gap-3">
                <span className="text-[11px] uppercase tracking-wider font-bold text-primary bg-primary/10 px-2.5 py-1 rounded-lg">
                  Current Workspace Plan
                </span>
                <h2 className="text-2xl font-black text-foreground">
                  {currentPlan ? currentPlan.name : 'No Active Plan'}
                </h2>
              </div>
              <p className="text-sm text-muted-foreground max-w-xl">
                {currentPlan?.description ||
                  'Select a plan below to activate CRM, collaborative inboxes, unified pipelines, and channels.'}
              </p>
              {currentSubscription && (
                <div className="flex flex-wrap gap-4 text-xs text-muted-foreground pt-2">
                  <div>
                    <span className="font-medium text-foreground">Billing Cycle:</span>{' '}
                    {currentSubscription.currentPeriodStart
                      ? `${dateFmt(currentSubscription.currentPeriodStart)} – ${dateFmt(
                          currentSubscription.currentPeriodEnd,
                        )}`
                      : (currentPlan?.billingCycle ?? 'MONTHLY')}
                  </div>
                  <div>
                    <span className="font-medium text-foreground">Next Renewal:</span>{' '}
                    {dateFmt(currentSubscription.currentPeriodEnd)}
                  </div>
                  {currentSubscription.cancelAtPeriodEnd && (
                    <span className="text-amber-400 font-semibold">
                      (Cancels on {dateFmt(currentSubscription.currentPeriodEnd)})
                    </span>
                  )}
                </div>
              )}
            </div>

            <div className="flex flex-col sm:flex-row items-end sm:items-center gap-5">
              <div className="text-right">
                <div className="text-3xl font-black text-foreground">
                  {currentPrice
                    ? money(currentPrice.amount, currentPrice.currency)
                    : currentPlan
                      ? money(currentPlan.basePrice, currentPlan.currency)
                      : '₹0'}
                </div>
                <div className="text-xs text-muted-foreground">
                  {currentPrice?.billingCycle === 'YEARLY' ? '/ year' : '/ month'}
                </div>
              </div>

              {currentSubscription && !currentSubscription.cancelAtPeriodEnd && (
                <button
                  type="button"
                  onClick={() => setCancelOpen(true)}
                  className="text-xs text-destructive hover:underline p-2"
                >
                  Cancel subscription
                </button>
              )}
            </div>
          </div>

          {/* Available Plans Catalog (Clean cards without heavy wireframe box borders) */}
          <div className="space-y-5 pt-2">
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
              <div>
                <h3 className="text-xl font-extrabold text-foreground tracking-tight">
                  Available Plans
                </h3>
                <p className="text-xs text-muted-foreground mt-0.5">
                  Predictable platform subscriptions bundled with zero-debt prepaid wallet
                  protections.
                </p>
              </div>

              {/* Monthly / Annual Toggle */}
              <div className="inline-flex items-center p-1 rounded-xl bg-slate-900/40 dark:bg-slate-950/40 self-start sm:self-auto">
                <button
                  type="button"
                  onClick={() => setBillingInterval('MONTHLY')}
                  className={`px-3.5 py-1.5 rounded-lg text-xs font-semibold transition-all ${
                    billingInterval === 'MONTHLY'
                      ? 'bg-primary text-primary-foreground shadow-sm'
                      : 'text-muted-foreground hover:text-foreground'
                  }`}
                >
                  Monthly
                </button>
                <button
                  type="button"
                  onClick={() => setBillingInterval('YEARLY')}
                  className={`flex items-center gap-1.5 px-3.5 py-1.5 rounded-lg text-xs font-semibold transition-all ${
                    billingInterval === 'YEARLY'
                      ? 'bg-primary text-primary-foreground shadow-sm'
                      : 'text-muted-foreground hover:text-foreground'
                  }`}
                >
                  <span>Annual</span>
                  <span className="bg-emerald-500/20 text-emerald-400 text-[10px] px-1.5 py-0.5 rounded-full font-bold">
                    Save 17%
                  </span>
                </button>
              </div>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-4 gap-6">
              {plans.map((plan) => {
                const isCurrent = currentPlan?.planId === plan.planId
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
                      isCurrent
                        ? 'bg-gradient-to-b from-primary/15 via-slate-900/40 to-slate-900/60 ring-2 ring-primary/50 shadow-lg shadow-primary/10'
                        : isGrowth
                          ? 'bg-slate-900/35 dark:bg-slate-950/35 ring-1 ring-purple-500/30 hover:bg-slate-900/50 shadow-md'
                          : 'bg-slate-900/25 dark:bg-slate-950/25 hover:bg-slate-900/45 shadow-sm'
                    }`}
                  >
                    <div className="space-y-5">
                      <div className="flex items-center justify-between min-h-[26px]">
                        {isCurrent ? (
                          <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-[11px] font-bold bg-primary text-primary-foreground shadow-sm">
                            <FiCheckCircle className="text-xs" /> Active Plan
                          </span>
                        ) : isGrowth ? (
                          <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-[11px] font-extrabold bg-gradient-to-r from-purple-500 to-indigo-600 text-white shadow-sm">
                            ⭐ Most Popular
                          </span>
                        ) : isStarter ? (
                          <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-[11px] font-semibold bg-indigo-500/15 text-indigo-400">
                            ⚡ Best for Startups
                          </span>
                        ) : isEnterprise ? (
                          <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-[11px] font-semibold bg-amber-500/15 text-amber-400">
                            💎 Scale & SLAs
                          </span>
                        ) : (
                          <span />
                        )}
                      </div>

                      <div>
                        <h4 className="text-xl font-extrabold text-foreground">{plan.name}</h4>
                        <p className="text-xs text-muted-foreground mt-1.5 min-h-[36px] leading-relaxed">
                          {plan.description ||
                            'Comprehensive workspace suite with channels and CRM.'}
                        </p>
                      </div>

                      <div>
                        <div className="text-3xl font-black text-foreground">
                          {money(priceAmount, priceCurrency)}
                        </div>
                        <div className="text-xs text-muted-foreground mt-0.5">
                          {priceAmount === 0
                            ? 'forever free'
                            : billingInterval === 'YEARLY'
                              ? `per year (billed annually · ₹${Math.round(priceAmount / 12).toLocaleString()}/mo)`
                              : 'per month'}
                        </div>
                      </div>

                      <div className="space-y-2.5 text-xs">
                        <div className="font-bold text-foreground text-xs uppercase tracking-wider text-muted-foreground/80 pt-1">
                          Included in plan:
                        </div>
                        {highlights.map((item, idx) => {
                          const isMainComms =
                            item.includes('Emails/mo') ||
                            item.includes('SMS/mo') ||
                            item.includes('WhatsApp')
                          return (
                            <div key={idx} className="flex items-start gap-2.5">
                              <FiCheckCircle className="text-emerald-500 shrink-0 text-sm mt-0.5" />
                              <span
                                className={`leading-tight text-xs ${
                                  isMainComms
                                    ? 'text-foreground font-bold'
                                    : 'text-muted-foreground font-medium'
                                }`}
                              >
                                {item}
                              </span>
                            </div>
                          )
                        })}
                      </div>
                    </div>

                    <div className="pt-6 mt-6">
                      {isCurrent ? (
                        <div className="w-full py-2.5 px-4 rounded-xl bg-slate-800/40 text-muted-foreground text-sm font-semibold text-center cursor-default">
                          Current Subscription
                        </div>
                      ) : (
                        <button
                          type="button"
                          onClick={() => matchedPrice && beginInitialSelection(plan, matchedPrice)}
                          className={`w-full justify-center text-sm py-2.5 rounded-xl shadow-md font-semibold transition-all ${
                            isGrowth
                              ? 'bg-gradient-to-r from-purple-600 to-indigo-600 hover:from-purple-500 hover:to-indigo-500 text-white shadow-purple-500/20'
                              : 'primary-button'
                          }`}
                        >
                          {priceAmount === 0 ? 'Select Free' : 'Choose Plan'}
                        </button>
                      )}
                    </div>
                  </div>
                )
              })}
            </div>
          </div>
        </div>
      )}

      {/* TAB 2: COMMUNICATIONS WALLET */}
      {activeTab === 'wallet' && (
        <div className="space-y-6">
          {/* Hero Balance & Auto-Recharge Cards */}
          <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
            <div className="md:col-span-2 bg-slate-900/30 dark:bg-slate-950/40 rounded-2xl p-6 sm:p-7 shadow-sm flex flex-col justify-between space-y-6">
              <div>
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-2 text-xs font-bold uppercase tracking-wider text-muted-foreground">
                    <FiDollarSign className="text-base text-primary" />
                    Prepaid Communications Balance
                  </div>
                  <span
                    className={`inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-semibold ${
                      (wallet?.balance ?? 0) >= 100
                        ? 'bg-emerald-500/15 text-emerald-400'
                        : 'bg-amber-500/15 text-amber-400'
                    }`}
                  >
                    <span
                      className={`w-2 h-2 rounded-full ${
                        (wallet?.balance ?? 0) >= 100
                          ? 'bg-emerald-400'
                          : 'bg-amber-400 animate-pulse'
                      }`}
                    />
                    {(wallet?.balance ?? 0) >= 100 ? 'Healthy Balance' : 'Low Balance'}
                  </span>
                </div>

                <div className="mt-4 flex items-baseline gap-3">
                  <span className="text-4xl sm:text-5xl font-black text-foreground">
                    {wallet ? money(wallet.balance, wallet.currency) : '₹0.00'}
                  </span>
                  <span className="text-sm font-semibold text-muted-foreground">
                    {wallet?.currency ?? 'INR'}
                  </span>
                </div>

                <p className="text-xs text-muted-foreground mt-2.5 max-w-lg leading-relaxed">
                  Used for variable SMS, WhatsApp messaging, and email overages once your monthly
                  plan allowance is exhausted. Zero carrier debt risk.
                </p>
              </div>

              {/* Fast Top-Up Chips */}
              <div className="space-y-3 pt-4">
                <div className="text-xs font-semibold text-muted-foreground">
                  Quick Top-Up Options:
                </div>
                <div className="flex flex-wrap items-center gap-3">
                  {[500, 1000, 2000, 5000].map((amt) => (
                    <button
                      key={amt}
                      type="button"
                      onClick={() => {
                        setTopupAmount(amt)
                        setCustomTopupInput(amt.toString())
                        setTopupModalOpen(true)
                      }}
                      className="px-4 py-2.5 rounded-xl bg-slate-800/40 hover:bg-slate-800/80 text-sm font-bold text-foreground transition-all flex items-center gap-1.5"
                    >
                      <FiPlus className="text-xs text-primary" />₹{amt.toLocaleString('en-IN')}
                    </button>
                  ))}
                  <button
                    type="button"
                    onClick={() => {
                      setTopupAmount(1000)
                      setCustomTopupInput('1000')
                      setTopupModalOpen(true)
                    }}
                    className="primary-button text-sm px-5 py-2.5 rounded-xl inline-flex items-center gap-2 shadow-md"
                  >
                    <FiPlus /> Custom Top-Up
                  </button>
                </div>
              </div>
            </div>

            {/* Unified Smart AutoPay & Buffer Protection Card */}
            <div className="bg-slate-900/30 dark:bg-slate-950/40 rounded-2xl p-6 sm:p-7 shadow-sm flex flex-col justify-between space-y-4">
              <div className="space-y-2">
                <div className="flex items-center justify-between">
                  <h4 className="text-base font-bold text-foreground flex items-center gap-2">
                    <FiShield className="text-indigo-400" /> Smart AutoPay
                  </h4>
                  <span className="text-[10px] font-semibold px-2.5 py-0.5 rounded-full bg-slate-800 text-slate-300 border border-slate-700/50">
                    Optional
                  </span>
                </div>
                <p className="text-xs text-muted-foreground leading-relaxed">
                  Automate wallet replenishment and next-cycle subscription renewals with a single
                  saved payment method.
                </p>
              </div>

              {autoPayDetails?.status === 'ACTIVE' ? (
                <div className="rounded-xl p-3.5 bg-emerald-500/10 border border-emerald-500/20 text-xs space-y-2">
                  <div className="flex items-center justify-between">
                    <span className="font-bold text-emerald-400 flex items-center gap-1.5">
                      <FiCheckCircle className="text-sm" /> Mandate Active
                    </span>
                    <span className="inline-flex items-center gap-1 text-[10px] font-semibold text-emerald-300 bg-emerald-500/20 px-2 py-0.5 rounded-md">
                      ● UPI AutoPay / e-Mandate
                    </span>
                  </div>
                  <div className="space-y-1.5 text-slate-300 text-[11px] pt-1">
                    <div className="flex justify-between">
                      <span className="text-muted-foreground">Mandate Limit:</span>
                      <span className="font-semibold text-foreground">Up to ₹15,000 / month</span>
                    </div>
                    <div className="flex justify-between">
                      <span className="text-muted-foreground">Auto-Recharge:</span>
                      <span className="font-semibold text-foreground">
                        {autoRechargeEnabled
                          ? `When < ₹${autoThreshold}, adds ₹${autoAmount}`
                          : 'Disabled'}
                      </span>
                    </div>
                    {wallet?.autoRechargePaused && (
                      <div className="p-2 rounded-lg bg-amber-500/15 border border-amber-500/30 text-amber-300 text-[10px]">
                        ⚠️ Auto-recharge paused after 3 failed attempts. Check payment method.
                      </div>
                    )}
                    {currentSubscription && (
                      <div className="flex justify-between">
                        <span className="text-muted-foreground">Plan Renewal:</span>
                        <span className="font-semibold text-foreground">
                          Auto-renews on {dateFmt(currentSubscription.currentPeriodEnd)}
                        </span>
                      </div>
                    )}
                  </div>

                  <div className="pt-2 flex items-center gap-2">
                    <button
                      type="button"
                      onClick={() => setAutopayModalOpen(true)}
                      className="flex-1 secondary-button text-xs py-1.5 justify-center"
                    >
                      Configure Rules
                    </button>
                    <button
                      type="button"
                      onClick={() => void handleCancelAutoPay()}
                      disabled={autoRechargeSaving}
                      className="text-[11px] text-rose-400 hover:text-rose-300 underline px-2 py-1"
                    >
                      Cancel Mandate
                    </button>
                  </div>
                </div>
              ) : (
                <div className="rounded-xl p-3.5 bg-slate-800/40 border border-slate-700/50 text-xs space-y-2.5">
                  <div className="flex items-center justify-between">
                    <span className="text-muted-foreground font-medium">Status:</span>
                    <span className="text-[10px] font-semibold px-2 py-0.5 rounded-md bg-slate-800 text-slate-400 border border-slate-700">
                      {autoPayDetails?.status
                        ? (autoPayStatusLabel[autoPayDetails.status] ?? autoPayDetails.status)
                        : 'Not Configured'}
                    </span>
                  </div>
                  <div className="space-y-1.5 text-[11px] text-muted-foreground leading-relaxed">
                    <div className="flex items-center gap-1.5">
                      <span className="text-emerald-400 font-bold">✓</span>
                      <span>
                        <strong>Zero Delivery Interruptions:</strong> Auto-tops up wallet buffer.
                      </span>
                    </div>
                    <div className="flex items-center gap-1.5">
                      <span className="text-indigo-400 font-bold">✓</span>
                      <span>
                        <strong>Next-Cycle Renewal:</strong> Renews subscription plan automatically.
                      </span>
                    </div>
                    <div className="flex items-center gap-1.5">
                      <span className="text-indigo-400 font-bold">✓</span>
                      <span>
                        <strong>RBI Tokenized:</strong> Zero card/bank credentials stored on UnifyDesk.
                      </span>
                    </div>
                  </div>

                  <button
                    type="button"
                    onClick={() => setAutoPaySetupModalOpen(true)}
                    className="w-full justify-center text-xs py-2.5 rounded-xl font-semibold inline-flex items-center gap-2 transition-all shadow-md bg-gradient-to-r from-indigo-600 to-purple-600 hover:from-indigo-500 hover:to-purple-500 text-white shadow-indigo-600/20 mt-1"
                  >
                    <FiZap className="text-amber-400" />
                    <span>Set Up AutoPay with Cashfree</span>
                  </button>
                </div>
              )}
            </div>
          </div>

          {/* Audit Ledger Table (Clean surface without harsh box lines) */}
          <div className="bg-slate-900/30 dark:bg-slate-950/40 rounded-2xl shadow-sm overflow-hidden">
            <div className="p-6 flex items-center justify-between">
              <div>
                <h3 className="text-base font-bold text-foreground">Transaction Audit Ledger</h3>
                <p className="text-xs text-muted-foreground mt-0.5">
                  Complete history of top-ups, channel communications, and balance adjustments.
                </p>
              </div>
              <span className="text-xs text-muted-foreground font-medium">
                {txTotal} transactions recorded
              </span>
            </div>

            <div className="overflow-x-auto">
              <table className="w-full text-left text-sm">
                <thead className="bg-slate-900/50 text-xs uppercase tracking-wider text-muted-foreground">
                  <tr>
                    <th className="py-3.5 px-5 font-semibold">Date & Time</th>
                    <th className="py-3.5 px-5 font-semibold">Type</th>
                    <th className="py-3.5 px-5 font-semibold">Description / Reference</th>
                    <th className="py-3.5 px-5 font-semibold text-right">Amount</th>
                    <th className="py-3.5 px-5 font-semibold text-right">Balance After</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-white/[0.03]">
                  {transactions.length === 0 ? (
                    <tr>
                      <td colSpan={5} className="py-10 text-center text-sm text-muted-foreground">
                        No transactions recorded yet. Top up your wallet to start sending variable
                        communications.
                      </td>
                    </tr>
                  ) : (
                    transactions.map((tx) => {
                      const isCredit = tx.amount > 0
                      return (
                        <tr key={tx.id} className="hover:bg-white/[0.02] transition-colors">
                          <td className="py-3.5 px-5 text-xs text-muted-foreground whitespace-nowrap">
                            {dateTimeFmt(tx.createdAt)}
                          </td>
                          <td className="py-3.5 px-5">
                            <span
                              className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-semibold ${
                                tx.type === 'TOPUP'
                                  ? 'bg-emerald-500/15 text-emerald-400'
                                  : tx.type === 'COMMUNICATION_CHARGE'
                                    ? 'bg-blue-500/15 text-blue-400'
                                    : 'bg-amber-500/15 text-amber-400'
                              }`}
                            >
                              {tx.type}
                            </span>
                          </td>
                          <td className="py-3.5 px-5 text-xs text-foreground max-w-xs truncate">
                            <div className="font-medium">{tx.description}</div>
                            {tx.referenceId && (
                              <div className="text-[10px] text-muted-foreground font-mono">
                                Ref: {tx.referenceId}
                              </div>
                            )}
                          </td>
                          <td
                            className={`py-3.5 px-5 text-right font-semibold text-sm ${
                              isCredit ? 'text-emerald-400' : 'text-foreground'
                            }`}
                          >
                            {isCredit
                              ? `+${money(tx.amount, tx.currency)}`
                              : money(tx.amount, tx.currency)}
                          </td>
                          <td className="py-3.5 px-5 text-right text-xs font-mono text-muted-foreground">
                            {money(tx.balanceAfter, tx.currency)}
                          </td>
                        </tr>
                      )
                    })
                  )}
                </tbody>
              </table>
            </div>
          </div>
        </div>
      )}

      {/* TAB 3: LIVE QUOTAS & USAGE WITH RICH PIE/DONUT CHARTS */}
      {activeTab === 'usage' && (
        <div className="space-y-6">
          {/* Banner */}
          <div className="p-5 rounded-2xl bg-primary/10 flex items-start gap-3.5">
            <FiShield className="text-primary text-xl shrink-0 mt-0.5" />
            <div className="text-sm">
              <h4 className="font-bold text-foreground">Zero Debt & Guaranteed Message Delivery</h4>
              <p className="text-muted-foreground text-xs mt-0.5 leading-relaxed">
                Your workspace includes plan-specific allowances for team seats, CRM contact
                storage, and monthly communications. Usage beyond free quotas is automatically
                deducted from your prepaid Communications Wallet with zero surprise debt.
              </p>
            </div>
          </div>

          {/* 4 Interactive Visual Donut / Pie Charts */}
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-6">
            {/* Chart 1: Workspace Seats */}
            <DonutChart
              title="Workspace Team Seats"
              subtitle="Active members collaborating"
              slices={[
                {
                  label: 'Seats in Use',
                  value: usage?.seatsUsed ?? 1,
                  color: '#3b82f6', // Blue
                },
                {
                  label: 'Available Slots',
                  value: Math.max(0, (usage?.seatsLimit ?? 5) - (usage?.seatsUsed ?? 1)),
                  color: '#1e293b', // Slate 800
                },
              ]}
              centerPrimary={`${usage?.seatsUsed ?? 1}`}
              centerSecondary={`of ${usage?.seatsLimit === -1 ? '∞' : (usage?.seatsLimit ?? 5)} Seats`}
            />

            {/* Chart 2: CRM Contacts */}
            <DonutChart
              title="CRM Contact Storage"
              subtitle="Customer & lead records"
              slices={[
                {
                  label: 'Stored Contacts',
                  value: usage?.contactsUsed ?? 0,
                  color: '#10b981', // Emerald
                },
                {
                  label: 'Remaining Limit',
                  value: Math.max(0, (usage?.contactsLimit ?? 1000) - (usage?.contactsUsed ?? 0)),
                  color: '#1e293b', // Slate 800
                },
              ]}
              centerPrimary={`${
                usage && usage.contactsLimit > 0
                  ? Math.round((usage.contactsUsed / usage.contactsLimit) * 100)
                  : 0
              }%`}
              centerSecondary="Capacity"
            />

            {/* Chart 3: Monthly Free Communications Breakdown */}
            <DonutChart
              title="Monthly Free Messaging"
              subtitle="Email & SMS combined quota"
              slices={[
                {
                  label: 'Emails Sent',
                  value: usage?.emailMonthlyUsed ?? 0,
                  color: '#06b6d4', // Cyan
                },
                {
                  label: 'SMS Sent',
                  value: usage?.smsMonthlyUsed ?? 0,
                  color: '#f59e0b', // Amber
                },
                {
                  label: 'Available Quota',
                  value: Math.max(
                    0,
                    (usage?.emailMonthlyLimit ?? 1000) -
                      (usage?.emailMonthlyUsed ?? 0) +
                      ((usage?.smsMonthlyLimit ?? 100) - (usage?.smsMonthlyUsed ?? 0)),
                  ),
                  color: '#1e293b', // Slate 800
                },
              ]}
              centerPrimary={`${(usage?.emailMonthlyUsed ?? 0) + (usage?.smsMonthlyUsed ?? 0)}`}
              centerSecondary="Messages"
            />

            {/* Chart 4: Communications Wallet Health */}
            <DonutChart
              title="Wallet Reserve Ratio"
              subtitle="Pre-funded buffer status"
              slices={[
                {
                  label: 'Current Balance',
                  value: Math.max(0, wallet?.balance ?? 0),
                  color: '#8b5cf6', // Purple
                },
                {
                  label: 'Target Buffer',
                  value: Math.max(100, wallet?.autoRechargeThreshold ?? 500),
                  color: '#334155', // Slate 700
                },
              ]}
              centerPrimary={wallet ? `₹${wallet.balance.toFixed(0)}` : '₹0'}
              centerSecondary={wallet && wallet.balance >= 100 ? 'Healthy' : 'Low Buffer'}
            />
          </div>
        </div>
      )}

      {/* TAB 4: INVOICES & TAX INFO */}
      {activeTab === 'invoices' && (
        <div className="space-y-6">
          {/* Legal Business & GST Profile */}
          <div className="bg-slate-900/30 dark:bg-slate-950/40 rounded-2xl p-6 sm:p-7 shadow-sm">
            <div className="flex items-center justify-between pb-4">
              <div>
                <h3 className="text-base font-bold text-foreground">Legal & Tax Profile</h3>
                <p className="text-xs text-muted-foreground mt-0.5">
                  Provide your registered business details and GSTIN so official tax invoices can be
                  generated.
                </p>
              </div>
            </div>

            <form onSubmit={handleSaveProfile} className="mt-4 space-y-4">
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <div>
                  <label className="block text-xs font-semibold text-foreground mb-1">
                    Legal Entity / Company Name
                  </label>
                  <input
                    type="text"
                    value={profile.legalBusinessName || ''}
                    onChange={(e) => setProfile({ ...profile, legalBusinessName: e.target.value })}
                    placeholder="e.g. Acme Technologies Pvt Ltd"
                    className="input-field w-full text-sm py-2 rounded-xl bg-slate-800/40"
                  />
                </div>

                <div>
                  <label className="block text-xs font-semibold text-foreground mb-1">
                    GSTIN / Tax ID
                  </label>
                  <input
                    type="text"
                    value={profile.taxIdOrGstin || ''}
                    onChange={(e) =>
                      setProfile({ ...profile, taxIdOrGstin: e.target.value.toUpperCase() })
                    }
                    placeholder="e.g. 29ABCDE1234F1Z5"
                    className="input-field w-full text-sm py-2 uppercase font-mono rounded-xl bg-slate-800/40"
                  />
                </div>

                <div>
                  <label className="block text-xs font-semibold text-foreground mb-1">
                    Billing Notification Email
                  </label>
                  <input
                    type="email"
                    value={profile.billingEmail || ''}
                    onChange={(e) => setProfile({ ...profile, billingEmail: e.target.value })}
                    placeholder="billing@company.com"
                    className="input-field w-full text-sm py-2 rounded-xl bg-slate-800/40"
                  />
                </div>

                <div>
                  <label className="block text-xs font-semibold text-foreground mb-1">
                    Billing Contact Phone
                  </label>
                  <input
                    type="tel"
                    value={profile.billingPhone || ''}
                    onChange={(e) => setProfile({ ...profile, billingPhone: e.target.value })}
                    placeholder="+91 9876543210"
                    className="input-field w-full text-sm py-2 rounded-xl bg-slate-800/40"
                  />
                </div>

                <div className="sm:col-span-2">
                  <label className="block text-xs font-semibold text-foreground mb-1">
                    Address Line 1
                  </label>
                  <input
                    type="text"
                    value={profile.addressLine1 || ''}
                    onChange={(e) => setProfile({ ...profile, addressLine1: e.target.value })}
                    placeholder="Building, Suite, Street name"
                    className="input-field w-full text-sm py-2 rounded-xl bg-slate-800/40"
                  />
                </div>

                <div>
                  <label className="block text-xs font-semibold text-foreground mb-1">City</label>
                  <CitySelect
                    country={profile.country}
                    value={profile.city || ''}
                    onChange={(city) => setProfile({ ...profile, city })}
                    placeholder="Select or enter city"
                    className="input-field w-full text-sm py-2 rounded-xl bg-slate-800/40"
                    showIcon
                  />
                </div>

                <div>
                  <label className="block text-xs font-semibold text-foreground mb-1">State</label>
                  <input
                    type="text"
                    value={profile.state || ''}
                    onChange={(e) => setProfile({ ...profile, state: e.target.value })}
                    placeholder="Karnataka"
                    className="input-field w-full text-sm py-2 rounded-xl bg-slate-800/40"
                  />
                </div>

                <div>
                  <label className="block text-xs font-semibold text-foreground mb-1">
                    Postal Code
                  </label>
                  <input
                    type="text"
                    value={profile.postalCode || ''}
                    onChange={(e) => setProfile({ ...profile, postalCode: e.target.value })}
                    placeholder="560001"
                    className="input-field w-full text-sm py-2 font-mono rounded-xl bg-slate-800/40"
                  />
                </div>

                <div>
                  <label className="block text-xs font-semibold text-foreground mb-1">
                    Country
                  </label>
                  <CountrySelect
                    value={profile.country || 'India'}
                    valueType="name"
                    onChange={(country) => setProfile({ ...profile, country })}
                    className="input-field w-full text-sm py-2 rounded-xl bg-slate-800/40"
                    showIcon
                  />
                </div>
              </div>

              <div className="pt-3 flex justify-end">
                <button
                  type="submit"
                  disabled={profileSaving}
                  className="primary-button inline-flex items-center gap-2 text-sm px-5 py-2.5 rounded-xl shadow-md"
                >
                  <FiSave />
                  {profileSaving ? 'Saving...' : 'Save Tax Profile'}
                </button>
              </div>
            </form>
          </div>

          {/* Past Invoices Table */}
          <div className="bg-slate-900/30 dark:bg-slate-950/40 rounded-2xl shadow-sm overflow-hidden">
            <div className="p-6">
              <h3 className="text-base font-bold text-foreground">Tax Invoices</h3>
              <p className="text-xs text-muted-foreground mt-0.5">
                Download detailed tax invoices with breakdown of GST and subscription charges.
              </p>
            </div>

            <div className="overflow-x-auto">
              <table className="w-full text-left text-sm">
                <thead className="bg-slate-900/50 text-xs uppercase tracking-wider text-muted-foreground">
                  <tr>
                    <th className="py-3.5 px-5 font-semibold">Invoice #</th>
                    <th className="py-3.5 px-5 font-semibold">Period</th>
                    <th className="py-3.5 px-5 font-semibold">Date Paid</th>
                    <th className="py-3.5 px-5 font-semibold">Status</th>
                    <th className="py-3.5 px-5 font-semibold text-right">Tax (GST)</th>
                    <th className="py-3.5 px-5 font-semibold text-right">Total Amount</th>
                    <th className="py-3.5 px-5 font-semibold text-center">Receipt</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-white/[0.03]">
                  {invoices.length === 0 ? (
                    <tr>
                      <td colSpan={7} className="py-10 text-center text-sm text-muted-foreground">
                        No invoices generated yet. Invoices appear here automatically after
                        subscription renewals or checkout.
                      </td>
                    </tr>
                  ) : (
                    invoices.map((inv) => (
                      <tr key={inv.invoiceId} className="hover:bg-white/[0.02] transition-colors">
                        <td className="py-3.5 px-5 text-xs font-mono font-medium text-foreground">
                          {inv.invoiceNumber}
                        </td>
                        <td className="py-3.5 px-5 text-xs text-muted-foreground whitespace-nowrap">
                          {inv.servicePeriodStart
                            ? `${dateFmt(inv.servicePeriodStart)} – ${dateFmt(inv.servicePeriodEnd)}`
                            : '—'}
                        </td>
                        <td className="py-3.5 px-5 text-xs text-muted-foreground whitespace-nowrap">
                          {dateFmt(inv.paidAt || inv.finalizedAt)}
                        </td>
                        <td className="py-3.5 px-5">
                          <span
                            className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-semibold ${
                              inv.status === 'PAID'
                                ? 'bg-emerald-500/15 text-emerald-400'
                                : 'bg-amber-500/15 text-amber-400'
                            }`}
                          >
                            {inv.status}
                          </span>
                        </td>
                        <td className="py-3.5 px-5 text-right text-xs text-muted-foreground">
                          {money(inv.taxAmount, inv.currency)}
                        </td>
                        <td className="py-3.5 px-5 text-right font-bold text-sm text-foreground">
                          {money(inv.total, inv.currency)}
                        </td>
                        <td className="py-3.5 px-5 text-center">
                          <button
                            type="button"
                            onClick={() => {
                              window.print()
                            }}
                            className="inline-flex items-center gap-1 text-xs text-primary hover:underline"
                          >
                            <FiDownload /> View / Print
                          </button>
                        </td>
                      </tr>
                    ))
                  )}
                </tbody>
              </table>
            </div>
          </div>
        </div>
      )}

      {/* TOP-UP MODAL */}
      {topupModalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm p-4">
          <div className="bg-slate-900 border border-slate-800 rounded-2xl p-6 max-w-md w-full shadow-2xl space-y-5">
            <div className="flex items-center justify-between pb-3">
              <h3 className="text-lg font-bold text-foreground flex items-center gap-2">
                <FiPlus className="text-primary" /> Top-Up Communications Wallet
              </h3>
              <button
                onClick={() => setTopupModalOpen(false)}
                className="text-muted-foreground hover:text-foreground text-sm"
              >
                ✕
              </button>
            </div>

            <div className="space-y-4">
              <div>
                <label className="block text-xs font-semibold text-muted-foreground mb-2">
                  Select Preset Amount:
                </label>
                <div className="grid grid-cols-4 gap-2">
                  {[500, 1000, 2000, 5000].map((preset) => (
                    <button
                      key={preset}
                      type="button"
                      onClick={() => {
                        setTopupAmount(preset)
                        setCustomTopupInput(preset.toString())
                      }}
                      className={`py-2 px-2 rounded-xl text-xs font-bold transition-all ${
                        customTopupInput === preset.toString()
                          ? 'bg-primary text-primary-foreground shadow-md shadow-primary/20'
                          : 'bg-slate-800/60 text-foreground hover:bg-slate-800'
                      }`}
                    >
                      ₹{preset.toLocaleString('en-IN')}
                    </button>
                  ))}
                </div>
              </div>

              <div>
                <label className="block text-xs font-semibold text-muted-foreground mb-1.5">
                  Or Enter Custom Amount (INR):
                </label>
                <div className="relative">
                  <span className="absolute left-3.5 top-2.5 text-muted-foreground font-bold">
                    ₹
                  </span>
                  <input
                    type="number"
                    min="10"
                    step="10"
                    value={customTopupInput}
                    onChange={(e) => setCustomTopupInput(e.target.value)}
                    className="input-field pl-8 py-2.5 w-full text-base font-bold rounded-xl bg-slate-800/40"
                  />
                </div>
                <p className="text-[11px] text-muted-foreground mt-1">
                  Minimum top-up: ₹10.00. Funds never expire.
                </p>
              </div>

              <div>
                <label className="block text-xs font-semibold text-muted-foreground mb-1.5">
                  Billing Mobile Number (for Payment Gateway):
                </label>
                <div className="relative">
                  <FiPhone className="absolute left-3.5 top-3 text-muted-foreground text-xs" />
                  <input
                    type="tel"
                    required
                    value={customer.phone}
                    onChange={(e) => setCustomer({ ...customer, phone: e.target.value })}
                    placeholder="9876543210"
                    className="input-field pl-9 py-2.5 w-full text-xs font-medium rounded-xl bg-slate-800/40"
                  />
                </div>
              </div>

              <div className="p-3.5 rounded-xl bg-slate-800/30 text-xs text-muted-foreground space-y-1.5">
                <div className="flex justify-between">
                  <span>Recharge Amount:</span>
                  <strong className="text-foreground">
                    ₹{parseFloat(customTopupInput || '0').toFixed(2)}
                  </strong>
                </div>
                <div className="flex justify-between">
                  <span>Current Wallet Balance:</span>
                  <span>{wallet ? money(wallet.balance, wallet.currency) : '₹0.00'}</span>
                </div>
                <div className="flex justify-between pt-1.5 text-primary font-bold">
                  <span>Balance After Recharge:</span>
                  <span>
                    ₹{((wallet?.balance ?? 0) + parseFloat(customTopupInput || '0')).toFixed(2)}
                  </span>
                </div>
              </div>
            </div>

            <div className="flex items-center justify-end gap-3 pt-2">
              <button
                type="button"
                onClick={() => setTopupModalOpen(false)}
                className="secondary-button text-sm px-4 py-2 rounded-xl"
              >
                Cancel
              </button>
              <button
                type="button"
                onClick={handleTopup}
                disabled={topupPending}
                className="primary-button text-sm px-5 py-2.5 rounded-xl inline-flex items-center gap-2 shadow-md"
              >
                {topupPending ? (
                  <>
                    <FiRefreshCw className="animate-spin" /> Processing...
                  </>
                ) : (
                  <>
                    <FiCreditCard /> Pay & Add Funds
                  </>
                )}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* PLAN SELECTION MODAL */}
      {draft && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm p-4">
          <div className="bg-slate-900 border border-slate-800 rounded-2xl p-6 max-w-lg w-full shadow-2xl space-y-5">
            <div className="flex items-center justify-between pb-3">
              <h3 className="text-lg font-bold text-foreground">Confirm Plan: {draft.plan.name}</h3>
              <button
                onClick={() => setDraft(null)}
                className="text-muted-foreground hover:text-foreground text-sm"
              >
                ✕
              </button>
            </div>

            <div className="space-y-3 text-sm">
              <div className="flex justify-between text-base font-semibold">
                <span>Plan Price:</span>
                <span className="text-primary font-extrabold">
                  {money(draft.price.amount, draft.price.currency)} /{' '}
                  {draft.price.billingCycle.toLowerCase()}
                </span>
              </div>
              <p className="text-xs text-muted-foreground leading-relaxed">
                Your company workspace will be activated with all plan entitlements including full
                CRM, unified pipelines, connected messaging channels, and monthly communication
                allowances.
              </p>

              {draft.price.amount > 0 && (
                <div className="space-y-3 pt-3 border-t border-slate-800">
                  <div className="flex items-center justify-between">
                    <span className="text-xs font-semibold text-foreground">
                      Billing & Tax Details:
                    </span>
                    <span className="text-[10px] text-muted-foreground">
                      Saved for invoices & future payments
                    </span>
                  </div>

                  <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                    <div>
                      <label className="block text-[11px] text-muted-foreground mb-1">
                        Legal Entity / Business Name
                      </label>
                      <input
                        type="text"
                        required
                        value={profile.legalBusinessName || customer.name || ''}
                        onChange={(e) => {
                          setProfile({ ...profile, legalBusinessName: e.target.value })
                          setCustomer({ ...customer, name: e.target.value })
                        }}
                        placeholder="Company Pvt Ltd"
                        className="input-field w-full text-xs py-2 rounded-xl bg-slate-800/40"
                      />
                    </div>
                    <div>
                      <label className="block text-[11px] text-muted-foreground mb-1">
                        GSTIN / Tax ID{' '}
                        <span className="text-slate-500 font-normal">(Optional)</span>
                      </label>
                      <input
                        type="text"
                        value={profile.taxIdOrGstin || ''}
                        onChange={(e) =>
                          setProfile({ ...profile, taxIdOrGstin: e.target.value.toUpperCase() })
                        }
                        placeholder="29ABCDE1234F1Z5"
                        className="input-field w-full text-xs py-2 uppercase font-mono rounded-xl bg-slate-800/40"
                      />
                    </div>
                  </div>

                  <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                    <div>
                      <label className="block text-[11px] text-muted-foreground mb-1">
                        Billing Email <span className="text-red-400">*</span>
                      </label>
                      <input
                        type="email"
                        required
                        value={customer.email || profile.billingEmail || ''}
                        onChange={(e) => {
                          setCustomer({ ...customer, email: e.target.value })
                          setProfile({ ...profile, billingEmail: e.target.value })
                        }}
                        placeholder="billing@company.com"
                        className="input-field w-full text-xs py-2 rounded-xl bg-slate-800/40"
                      />
                    </div>
                    <div>
                      <label className="block text-[11px] text-muted-foreground mb-1">
                        Billing Phone <span className="text-red-400">*</span>
                      </label>
                      <input
                        type="tel"
                        required
                        value={customer.phone || profile.billingPhone || ''}
                        onChange={(e) => {
                          setCustomer({ ...customer, phone: e.target.value })
                          setProfile({ ...profile, billingPhone: e.target.value })
                        }}
                        placeholder="9999999999"
                        className="input-field w-full text-xs py-2 rounded-xl bg-slate-800/40"
                      />
                    </div>
                  </div>

                  <div>
                    <label className="block text-[11px] text-muted-foreground mb-1">
                      Registered Address
                    </label>
                    <input
                      type="text"
                      value={profile.addressLine1 || ''}
                      onChange={(e) => setProfile({ ...profile, addressLine1: e.target.value })}
                      placeholder="Street address or office premises"
                      className="input-field w-full text-xs py-2 rounded-xl bg-slate-800/40"
                    />
                  </div>

                  <div className="grid grid-cols-3 gap-2">
                    <div>
                      <label className="block text-[11px] text-muted-foreground mb-1">City</label>
                      <CitySelect
                        country={profile.country}
                        value={profile.city || ''}
                        onChange={(city) => setProfile({ ...profile, city })}
                        placeholder="Bengaluru"
                        className="input-field w-full text-xs py-2 rounded-xl bg-slate-800/40"
                      />
                    </div>
                    <div>
                      <label className="block text-[11px] text-muted-foreground mb-1">State</label>
                      <input
                        type="text"
                        value={profile.state || ''}
                        onChange={(e) => setProfile({ ...profile, state: e.target.value })}
                        placeholder="Karnataka"
                        className="input-field w-full text-xs py-2 rounded-xl bg-slate-800/40"
                      />
                    </div>
                    <div>
                      <label className="block text-[11px] text-muted-foreground mb-1">PIN</label>
                      <input
                        type="text"
                        value={profile.postalCode || ''}
                        onChange={(e) => setProfile({ ...profile, postalCode: e.target.value })}
                        placeholder="560001"
                        className="input-field w-full text-xs py-2 rounded-xl bg-slate-800/40"
                      />
                    </div>
                  </div>
                </div>
              )}
            </div>

            <div className="flex items-center justify-end gap-3 pt-3">
              <button
                type="button"
                onClick={() => setDraft(null)}
                className="secondary-button text-sm px-4 py-2 rounded-xl"
              >
                Back
              </button>
              <button
                type="button"
                onClick={submitInitialSelection}
                disabled={planMutationPending}
                className="primary-button text-sm px-5 py-2.5 rounded-xl inline-flex items-center gap-2 shadow-md"
              >
                {planMutationPending ? (
                  <>
                    <FiRefreshCw className="animate-spin" /> Confirming...
                  </>
                ) : (
                  <>
                    <FiCheckCircle /> Confirm & Proceed
                  </>
                )}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* CANCELLATION MODAL */}
      {cancelOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm p-4">
          <div className="bg-slate-900 border border-slate-800 rounded-2xl p-6 max-w-md w-full shadow-2xl space-y-4">
            <h3 className="text-lg font-bold text-destructive flex items-center gap-2">
              <FiAlertTriangle /> Cancel Subscription?
            </h3>
            <p className="text-xs text-muted-foreground leading-relaxed">
              Your subscription will remain active until the end of the current billing cycle (
              {dateFmt(currentSubscription?.currentPeriodEnd)}). After this date, workspace
              privileges will revert to the Free tier.
            </p>
            <div className="flex justify-end gap-3 pt-2">
              <button
                type="button"
                onClick={() => setCancelOpen(false)}
                className="secondary-button text-sm px-4 py-2 rounded-xl"
              >
                Keep Subscription
              </button>
              <button
                type="button"
                onClick={cancelSubscription}
                disabled={planMutationPending}
                className="px-4 py-2 rounded-xl bg-destructive text-destructive-foreground text-sm font-semibold hover:bg-destructive/90 transition-colors"
              >
                {planMutationPending ? 'Cancelling...' : 'Confirm Cancellation'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* SMART AUTOPAY SETUP MODAL */}
      {autopayModalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 backdrop-blur-sm p-4">
          <div className="bg-slate-900 border border-slate-800 rounded-2xl p-6 max-w-lg w-full shadow-2xl space-y-5 max-h-[90vh] overflow-y-auto">
            <div className="flex items-center justify-between pb-2 border-b border-slate-800">
              <div className="flex items-center gap-2.5">
                <div className="w-9 h-9 rounded-xl bg-indigo-500/20 text-indigo-400 flex items-center justify-center shrink-0">
                  <FiShield className="text-lg" />
                </div>
                <div>
                  <h3 className="text-base font-bold text-foreground flex items-center gap-2">
                    Smart AutoPay Setup
                    <span className="text-[10px] font-normal px-2 py-0.5 rounded-full bg-slate-800 text-slate-400 border border-slate-700">
                      Optional
                    </span>
                  </h3>
                  <p className="text-[11px] text-muted-foreground">
                    Automate both plan renewals and wallet balance maintenance.
                  </p>
                </div>
              </div>
              <button
                onClick={() => setAutopayModalOpen(false)}
                className="text-muted-foreground hover:text-foreground text-sm p-1 rounded-lg hover:bg-slate-800 transition-colors"
              >
                ✕
              </button>
            </div>

            <div className="space-y-3.5 text-xs">
              {/* Card 1: Communications Wallet Auto-Replenishment */}
              <div className="p-4 rounded-xl bg-slate-800/40 border border-slate-700/50 space-y-3">
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-2 font-bold text-foreground text-sm">
                    <FiZap className="text-amber-400" />
                    <span>Communications Wallet Auto-Recharge</span>
                  </div>
                  <label className="relative inline-flex items-center cursor-pointer">
                    <input
                      type="checkbox"
                      checked={autopayReplenishWallet}
                      onChange={(e) => setAutopayReplenishWallet(e.target.checked)}
                      className="sr-only peer"
                    />
                    <div className="w-9 h-5 bg-muted peer-focus:outline-none rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:border-gray-300 after:border after:rounded-full after:h-4 after:w-4 after:transition-all peer-checked:bg-primary"></div>
                  </label>
                </div>
                <p className="text-[11px] text-muted-foreground leading-relaxed">
                  Automatically adds funds so WhatsApp, SMS, and high-volume email overages never
                  pause midway.
                </p>

                {autopayReplenishWallet && (
                  <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 pt-1">
                    <div>
                      <label className="block text-muted-foreground mb-1 font-medium">
                        When balance falls below:
                      </label>
                      <div className="relative">
                        <span className="absolute left-3 top-2 text-muted-foreground font-semibold">
                          ₹
                        </span>
                        <input
                          type="number"
                          min={100}
                          step={100}
                          value={autoThreshold}
                          onChange={(e) => setAutoThreshold(Number(e.target.value))}
                          className="input-field pl-7 py-2 text-sm w-full rounded-xl bg-slate-900/60"
                        />
                      </div>
                    </div>
                    <div>
                      <label className="block text-muted-foreground mb-1 font-medium">
                        Automatically add:
                      </label>
                      <div className="relative">
                        <span className="absolute left-3 top-2 text-muted-foreground font-semibold">
                          ₹
                        </span>
                        <input
                          type="number"
                          min={500}
                          step={500}
                          value={autoAmount}
                          onChange={(e) => setAutoAmount(Number(e.target.value))}
                          className="input-field pl-7 py-2 text-sm w-full rounded-xl bg-slate-900/60"
                        />
                      </div>
                    </div>
                  </div>
                )}
              </div>

              {/* Card 2: Next Month Plan Renewal */}
              <div className="p-4 rounded-xl bg-slate-800/40 border border-slate-700/50 space-y-2">
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-2 font-bold text-foreground text-sm">
                    <FiRefreshCw className="text-indigo-400" />
                    <span>Next Month Plan Auto-Renewal</span>
                  </div>
                  <label className="relative inline-flex items-center cursor-pointer">
                    <input
                      type="checkbox"
                      checked={autopayRenewPlan}
                      onChange={(e) => setAutopayRenewPlan(e.target.checked)}
                      className="sr-only peer"
                    />
                    <div className="w-9 h-5 bg-muted peer-focus:outline-none rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:border-gray-300 after:border after:rounded-full after:h-4 after:w-4 after:transition-all peer-checked:bg-primary"></div>
                  </label>
                </div>
                <p className="text-[11px] text-muted-foreground leading-relaxed">
                  Seamlessly bills your next cycle{' '}
                  {currentPrice
                    ? `(${money(currentPrice.amount, currentPrice.currency)}/${currentPrice.billingCycle.toLowerCase()})`
                    : ''}{' '}
                  at period end ({dateFmt(currentSubscription?.currentPeriodEnd)}) to prevent tier
                  lockouts.
                </p>
              </div>

              {/* Card 3: Payment Account / Mandate */}
              <div className="p-4 rounded-xl bg-slate-800/40 border border-slate-700/50 space-y-3">
                <div className="flex items-center justify-between">
                  <span className="font-bold text-foreground text-sm flex items-center gap-2">
                    <FiCreditCard className="text-primary" /> Payment Method for Auto-Debit
                  </span>
                  <span className="text-[10px] font-semibold text-amber-400 bg-amber-500/10 px-2 py-0.5 rounded border border-amber-500/20">
                    Mandate Required
                  </span>
                </div>
                <p className="text-[11px] text-muted-foreground leading-relaxed">
                  In compliance with RBI regulations, automated charges require a one-time recurring
                  mandate (e-Mandate / UPI AutoPay) authorization from your bank.
                </p>

                <div className="grid grid-cols-3 gap-2 pt-1">
                  <button
                    type="button"
                    onClick={() => setAutopayPaymentMethod('UPI')}
                    className={`p-2.5 rounded-xl border text-center transition-all ${
                      autopayPaymentMethod === 'UPI'
                        ? 'border-primary bg-primary/10 text-primary font-bold'
                        : 'border-slate-700 bg-slate-900/40 text-muted-foreground hover:bg-slate-800'
                    }`}
                  >
                    <div className="text-xs">UPI AutoPay</div>
                    <div className="text-[10px] text-muted-foreground mt-0.5">GPay, PhonePe</div>
                  </button>
                  <button
                    type="button"
                    onClick={() => setAutopayPaymentMethod('CARD')}
                    className={`p-2.5 rounded-xl border text-center transition-all ${
                      autopayPaymentMethod === 'CARD'
                        ? 'border-primary bg-primary/10 text-primary font-bold'
                        : 'border-slate-700 bg-slate-900/40 text-muted-foreground hover:bg-slate-800'
                    }`}
                  >
                    <div className="text-xs">Card Mandate</div>
                    <div className="text-[10px] text-muted-foreground mt-0.5">Debit / Credit</div>
                  </button>
                  <button
                    type="button"
                    onClick={() => setAutopayPaymentMethod('NETBANKING')}
                    className={`p-2.5 rounded-xl border text-center transition-all ${
                      autopayPaymentMethod === 'NETBANKING'
                        ? 'border-primary bg-primary/10 text-primary font-bold'
                        : 'border-slate-700 bg-slate-900/40 text-muted-foreground hover:bg-slate-800'
                    }`}
                  >
                    <div className="text-xs">e-NACH</div>
                    <div className="text-[10px] text-muted-foreground mt-0.5">Net Banking</div>
                  </button>
                </div>
              </div>
            </div>

            <div className="flex items-center justify-between pt-3 border-t border-slate-800">
              {autoRechargeEnabled ? (
                <button
                  type="button"
                  onClick={() => handleSaveAutopay(false)}
                  disabled={autoRechargeSaving}
                  className="text-xs text-rose-400 hover:text-rose-300 font-semibold underline"
                >
                  Pause AutoPay
                </button>
              ) : (
                <button
                  type="button"
                  onClick={() => setAutopayModalOpen(false)}
                  className="text-xs text-muted-foreground hover:text-foreground"
                >
                  Skip for Now
                </button>
              )}
              <div className="flex items-center gap-2">
                <button
                  type="button"
                  onClick={() => setAutopayModalOpen(false)}
                  className="secondary-button text-xs px-3.5 py-2 rounded-xl"
                >
                  Cancel
                </button>
                <button
                  type="button"
                  onClick={() => handleSaveAutopay(true)}
                  disabled={autoRechargeSaving}
                  className="primary-button text-xs px-4 py-2 rounded-xl inline-flex items-center gap-1.5 shadow-md"
                >
                  {autoRechargeSaving ? (
                    <>
                      <FiRefreshCw className="animate-spin" /> Saving...
                    </>
                  ) : (
                    <>
                      <FiCheckCircle /> Confirm & Authorize
                    </>
                  )}
                </button>
              </div>
            </div>
          </div>
        </div>
      )}

      {companyId && (
        <AutoPayModal
          isOpen={autoPaySetupModalOpen}
          companyId={companyId}
          companyName={selectedCompany?.name || ''}
          initialEmail={profile.billingEmail || selectedCompany?.businessEmail || ''}
          initialPhone={profile.billingPhone || ''}
          onClose={() => setAutoPaySetupModalOpen(false)}
          onSuccess={(details) => {
            setAutoPayDetails(details)
            if (details.status === 'ACTIVE') {
              setNotice('AutoPay mandate successfully activated!')
            }
          }}
        />
      )}
    </div>
  )
}
