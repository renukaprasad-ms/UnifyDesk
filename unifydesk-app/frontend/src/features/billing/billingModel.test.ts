import { describe, expect, it } from 'vitest'
import {
  activePlanPrices,
  downgradePlanPrices,
  isPlanPriceEffectiveAt,
  paymentReturnConfirmed,
  subscribedPlanPrice,
  upgradeRequiresCheckout,
} from './billingModel'
import type { PendingBillingCheckout } from './pendingCheckout'
import type { CompanyPlanDetails, Plan, PlanPrice, SubscriptionChange } from './types'

const price = (overrides: Partial<PlanPrice> = {}): PlanPrice => ({
  planPriceId: 'price-1',
  planId: 'growth',
  billingCycle: 'MONTHLY',
  amount: 999,
  currency: 'INR',
  effectiveFrom: '2026-10-01T00:00:00.000Z',
  effectiveTo: null,
  status: 'ACTIVE',
  ...overrides,
})
const plan = (prices: PlanPrice[] = [price()]): Plan => ({
  planId: 'growth', code: 'GROWTH', name: 'Growth', status: 'PUBLISHED', visibility: 'PUBLIC',
  billingCycle: 'MONTHLY', basePrice: 123, currency: 'INR', version: 1, prices, entitlements: [],
})
const activeDetails = (overrides: Partial<NonNullable<CompanyPlanDetails['subscription']>> = {}): CompanyPlanDetails => ({
  plan: plan(),
  subscription: {
    subscriptionId: 'new-subscription', companyId: 'company-a', planId: 'growth', planCode: 'GROWTH',
    planName: 'Growth', status: 'ACTIVE', startedAt: '2026-09-01T00:00:00Z',
    cancelAtPeriodEnd: false, paymentPolicy: 'CASHFREE', ...overrides,
  },
})
const initialContext = (overrides: Partial<PendingBillingCheckout> = {}): PendingBillingCheckout => ({
  companyId: 'company-a', targetPlanId: 'growth', kind: 'INITIAL', paymentId: 'payment-1',
  invoiceId: 'invoice-1', previousSubscriptionId: null, createdAt: '2026-10-03T00:00:00Z', ...overrides,
})

describe('PlanPrice half-open effectiveness', () => {
  it('rejects missing effectiveFrom', () => expect(isPlanPriceEffectiveAt(price({ effectiveFrom: null }), new Date('2026-10-03T00:00:00Z'))).toBe(false))
  it('is effective exactly at effectiveFrom', () => expect(isPlanPriceEffectiveAt(price(), new Date('2026-10-01T00:00:00Z'))).toBe(true))
  it('is not effective exactly at effectiveTo', () => expect(isPlanPriceEffectiveAt(price({ effectiveTo: '2026-11-01T00:00:00Z' }), new Date('2026-11-01T00:00:00Z'))).toBe(false))
  it('rejects future and expired prices now', () => {
    const now = new Date('2026-10-03T00:00:00Z')
    expect(activePlanPrices(plan([price({ effectiveFrom: '2026-11-01T00:00:00Z' })]), now)).toHaveLength(0)
    expect(activePlanPrices(plan([price({ effectiveTo: '2026-10-03T00:00:00Z' })]), now)).toHaveLength(0)
  })
  it('uses currentPeriodEnd for downgrade selection', () => {
    const target = plan([price({ effectiveFrom: '2026-11-01T00:00:00Z' })])
    expect(downgradePlanPrices(target, '2026-11-01T00:00:00Z')).toHaveLength(1)
    expect(activePlanPrices(target, new Date('2026-10-03T00:00:00Z'))).toHaveLength(0)
  })
  it('rejects price expiring at/before downgrade boundary and missing boundary', () => {
    expect(downgradePlanPrices(plan([price({ effectiveTo: '2026-11-01T00:00:00Z' })]), '2026-11-01T00:00:00Z')).toHaveLength(0)
    expect(downgradePlanPrices(plan(), null)).toHaveLength(0)
  })
})

describe('Billing authoritative state helpers', () => {
  it('uses subscription planPriceId instead of basePrice', () => {
    const target = plan([price({ planPriceId: 'monthly', amount: 999 }), price({ planPriceId: 'yearly', billingCycle: 'YEARLY', amount: 9999 })])
    const details = activeDetails({ planPriceId: 'yearly' })
    details.plan = target
    expect(subscribedPlanPrice(details)?.amount).toBe(9999)
    expect(subscribedPlanPrice(details)?.billingCycle).toBe('YEARLY')
    expect(subscribedPlanPrice({ ...details, subscription: { ...details.subscription!, planPriceId: 'missing' } })).toBeNull()
  })
  it('uses canonical PENDING_PAYMENT and APPLIED upgrade states', () => {
    const base = { changeId: 'c', companyId: 'x', subscriptionId: 's', fromPlanId: 'a', toPlanId: 'b', type: 'UPGRADE', requestedAt: '2026-10-03T00:00:00Z' } as SubscriptionChange
    expect(upgradeRequiresCheckout({ ...base, status: 'PENDING_PAYMENT' })).toBe(true)
    expect(upgradeRequiresCheckout({ ...base, status: 'APPLIED' })).toBe(false)
  })
})

describe('payment return INITIAL transition correlation', () => {
  it('confirms active target after a no-subscription baseline without comparing browser/server clocks', () => {
    expect(paymentReturnConfirmed(activeDetails({ startedAt: '2020-01-01T00:00:00Z' }), initialContext({ createdAt: '2030-01-01T00:00:00Z' }), 'company-a')).toBe(true)
  })
  it('rejects a stale pre-existing target subscription baseline', () => {
    expect(paymentReturnConfirmed(activeDetails({ subscriptionId: 'existing-sub' }), initialContext({ previousSubscriptionId: 'existing-sub' }), 'company-a')).toBe(false)
  })
  it('rejects wrong company, wrong target, and missing pending context', () => {
    expect(paymentReturnConfirmed(activeDetails(), initialContext(), 'company-b')).toBe(false)
    expect(paymentReturnConfirmed(activeDetails(), initialContext({ targetPlanId: 'pro' }), 'company-a')).toBe(false)
    expect(paymentReturnConfirmed(activeDetails(), null, 'company-a')).toBe(false)
  })
  it('keeps upgrade return conservative even when the active plan matches', () => {
    expect(paymentReturnConfirmed(activeDetails(), initialContext({ kind: 'UPGRADE', previousSubscriptionId: 'old-sub' }), 'company-a')).toBe(false)
  })
})
