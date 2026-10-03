import type {
  CompanyPlanDetails,
  CompanySubscription,
  Plan,
  PlanPrice,
  SubscriptionChange,
} from './types'

export const customerVisiblePlans = (plans: Plan[]) =>
  plans.filter((plan) => plan.visibility !== 'INTERNAL')

export const activePlanPrices = (plan: Plan, now = new Date()) =>
  plan.prices.filter((price) => {
    if (price.status !== 'ACTIVE') return false
    const starts = price.effectiveFrom ? new Date(price.effectiveFrom) : null
    const ends = price.effectiveTo ? new Date(price.effectiveTo) : null
    return (!starts || starts <= now) && (!ends || now < ends)
  })

export const defaultPlanPrice = (plan: Plan, now = new Date()): PlanPrice | null => {
  const prices = activePlanPrices(plan, now)
  return prices.find((price) => price.billingCycle === plan.billingCycle) ?? prices[0] ?? null
}

export const money = (amount: number | null | undefined, currency = 'INR') => {
  if (amount === null || amount === undefined) return '—'
  try {
    return new Intl.NumberFormat('en-IN', {
      style: 'currency',
      currency,
      maximumFractionDigits: 2,
    }).format(amount)
  } catch {
    return `${currency} ${amount}`
  }
}

export const subscriptionStatusMessage = (subscription: CompanySubscription) => {
  switch (subscription.status) {
    case 'PAST_DUE':
      return 'Payment overdue — service is in the billing grace period.'
    case 'SUSPENDED':
      return 'Billing is unresolved. Some paid operations are restricted.'
    case 'TRIALING':
      return 'This workspace is currently in its trial period.'
    case 'CANCELLED':
      return 'This subscription has been cancelled.'
    case 'EXPIRED':
      return 'This subscription has reached its scheduled end.'
    default:
      return 'Your platform subscription is active.'
  }
}

export const upgradeQuoteRows = (change: SubscriptionChange) =>
  [
    ['Current-plan credit', change.unusedCurrentCredit],
    ['Target prorated charge', change.targetProratedCharge],
    ['Amount due', change.amountDue],
  ] as const

export const paymentReturnConfirmed = (
  details: CompanyPlanDetails,
  targetPlanId: string | null,
) =>
  Boolean(
    targetPlanId &&
      details.subscription &&
      details.subscription.planId === targetPlanId &&
      ['ACTIVE', 'TRIALING'].includes(details.subscription.status),
  )

export const DOWNGRADE_BOUNDARY_COPY =
  'Your current plan remains active until the next billing-period boundary. Lower limits apply after that date; existing resources are not automatically deleted.'

export const CANCELLATION_COPY =
  'Cancellation takes effect at the end of the current billing period. Your subscription remains usable until the scheduled boundary according to backend state. Cancellation does not automatically create a refund.'

export const MANAGED_SERVICES_COPY =
  'Managed Email and Managed SMS are usage-based charges separate from the platform plan price. R1 does not include a free Managed Services allowance.'
