import type { PendingBillingCheckout } from './pendingCheckout'
import type { CompanyPlanDetails, CompanySubscription, Plan, PlanPrice, SubscriptionChange } from './types'

export const customerVisiblePlans = (plans: Plan[]) =>
  plans.filter((plan) => plan.visibility !== 'INTERNAL')

export const isPlanPriceEffectiveAt = (price: PlanPrice, pricingAt: Date) => {
  if (price.status !== 'ACTIVE' || !price.effectiveFrom) return false
  const starts = new Date(price.effectiveFrom)
  if (Number.isNaN(starts.getTime()) || starts > pricingAt) return false
  if (!price.effectiveTo) return true
  const ends = new Date(price.effectiveTo)
  return !Number.isNaN(ends.getTime()) && pricingAt < ends
}

export const activePlanPrices = (plan: Plan, pricingAt = new Date()) =>
  plan.prices.filter((price) => isPlanPriceEffectiveAt(price, pricingAt))

export const defaultPlanPrice = (plan: Plan, pricingAt = new Date()): PlanPrice | null => {
  const prices = activePlanPrices(plan, pricingAt)
  return prices.find((price) => price.billingCycle === plan.billingCycle) ?? prices[0] ?? null
}

export const downgradePlanPrices = (plan: Plan, currentPeriodEnd?: string | null) => {
  if (!currentPeriodEnd) return []
  const boundary = new Date(currentPeriodEnd)
  if (Number.isNaN(boundary.getTime())) return []
  return activePlanPrices(plan, boundary)
}

export const subscribedPlanPrice = (
  details: CompanyPlanDetails | null,
): PlanPrice | null => {
  const id = details?.subscription?.planPriceId
  if (!id || !details?.plan) return null
  return details.plan.prices.find((price) => price.planPriceId === id) ?? null
}

export const upgradeRequiresCheckout = (change: SubscriptionChange) =>
  change.status === 'PENDING_PAYMENT'

export const money = (amount: number | null | undefined, currency = 'INR') => {
  if (amount === null || amount === undefined) return '—'
  try {
    return new Intl.NumberFormat('en-IN', {
      style: 'currency', currency, maximumFractionDigits: 2,
    }).format(amount)
  } catch {
    return `${currency} ${amount}`
  }
}

export const subscriptionStatusMessage = (subscription: CompanySubscription) => {
  switch (subscription.status) {
    case 'PAST_DUE': return 'Payment overdue — service is in the billing grace period.'
    case 'SUSPENDED': return 'Billing is unresolved. Some paid operations are restricted.'
    case 'TRIALING': return 'This workspace is currently in its trial period.'
    case 'CANCELLED': return 'This subscription has been cancelled.'
    case 'EXPIRED': return 'This subscription has reached its scheduled end.'
    default: return 'Your platform subscription is active.'
  }
}

export const upgradeQuoteRows = (change: SubscriptionChange) => [
  ['Current-plan credit', change.unusedCurrentCredit],
  ['Target prorated charge', change.targetProratedCharge],
  ['Amount due', change.amountDue],
] as const

export const paymentReturnConfirmed = (
  details: CompanyPlanDetails,
  context: PendingBillingCheckout | null,
) => {
  if (!context || !details.subscription || details.subscription.planId !== context.targetPlanId) return false
  if (!['ACTIVE', 'TRIALING'].includes(details.subscription.status)) return false
  // With no normalized payment-status GET, never infer an upgrade payment from an already-active plan.
  if (context.kind === 'UPGRADE') return false
  const checkoutCreatedAt = new Date(context.createdAt).getTime()
  const subscriptionStartedAt = new Date(details.subscription.startedAt).getTime()
  return Number.isFinite(checkoutCreatedAt) && Number.isFinite(subscriptionStartedAt) && subscriptionStartedAt >= checkoutCreatedAt
}

export const DOWNGRADE_BOUNDARY_COPY =
  'Your current plan remains active until the next billing-period boundary. Lower limits apply after that date; existing resources are not automatically deleted.'
export const CANCELLATION_COPY =
  'Cancellation takes effect at the end of the current billing period. Your subscription remains usable until the scheduled boundary according to backend state. Cancellation does not automatically create a refund.'
export const MANAGED_SERVICES_COPY =
  'Managed Email and Managed SMS are usage-based charges separate from the platform plan price. R1 does not include a free Managed Services allowance.'
