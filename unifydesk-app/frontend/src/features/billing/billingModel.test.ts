import { describe, expect, it } from 'vitest'
import {
  activePlanPrices, downgradePlanPrices, isPlanPriceEffectiveAt, paymentReturnConfirmed,
  subscribedPlanPrice, upgradeRequiresCheckout,
} from './billingModel'
import type { CompanyPlanDetails, Plan, PlanPrice, SubscriptionChange } from './types'

const price = (overrides: Partial<PlanPrice> = {}): PlanPrice => ({
  planPriceId:'price-1', planId:'growth', billingCycle:'MONTHLY', amount:999,
  currency:'INR', effectiveFrom:'2026-10-01T00:00:00.000Z', effectiveTo:null, status:'ACTIVE', ...overrides,
})
const plan = (prices:PlanPrice[] = [price()]): Plan => ({ planId:'growth',code:'GROWTH',name:'Growth',status:'PUBLISHED',visibility:'PUBLIC',billingCycle:'MONTHLY',basePrice:123,currency:'INR',version:1,prices,entitlements:[] })

describe('PlanPrice half-open effectiveness', () => {
  it('rejects missing effectiveFrom', () => expect(isPlanPriceEffectiveAt(price({effectiveFrom:null}),new Date('2026-10-03T00:00:00Z'))).toBe(false))
  it('is effective exactly at effectiveFrom', () => expect(isPlanPriceEffectiveAt(price(),new Date('2026-10-01T00:00:00Z'))).toBe(true))
  it('is not effective exactly at effectiveTo', () => expect(isPlanPriceEffectiveAt(price({effectiveTo:'2026-11-01T00:00:00Z'}),new Date('2026-11-01T00:00:00Z'))).toBe(false))
  it('rejects future and expired prices now', () => { const now=new Date('2026-10-03T00:00:00Z'); expect(activePlanPrices(plan([price({effectiveFrom:'2026-11-01T00:00:00Z'})]),now)).toHaveLength(0); expect(activePlanPrices(plan([price({effectiveTo:'2026-10-03T00:00:00Z'})]),now)).toHaveLength(0) })
  it('uses currentPeriodEnd for downgrade selection', () => { const p=plan([price({effectiveFrom:'2026-11-01T00:00:00Z'})]); expect(downgradePlanPrices(p,'2026-11-01T00:00:00Z')).toHaveLength(1); expect(activePlanPrices(p,new Date('2026-10-03T00:00:00Z'))).toHaveLength(0) })
  it('rejects price expiring at/before downgrade boundary and missing boundary', () => { expect(downgradePlanPrices(plan([price({effectiveTo:'2026-11-01T00:00:00Z'})]),'2026-11-01T00:00:00Z')).toHaveLength(0); expect(downgradePlanPrices(plan(),null)).toHaveLength(0) })
})

describe('Billing authoritative state helpers', () => {
  it('uses subscription planPriceId instead of basePrice', () => { const p=plan([price({planPriceId:'monthly',amount:999}),price({planPriceId:'yearly',billingCycle:'YEARLY',amount:9999})]); const details={plan:p,subscription:{subscriptionId:'s',companyId:'c',planId:'growth',planPriceId:'yearly',planCode:'GROWTH',planName:'Growth',status:'ACTIVE',startedAt:'2026-10-01T00:00:00Z',cancelAtPeriodEnd:false,paymentPolicy:'CASHFREE'}} as CompanyPlanDetails; expect(subscribedPlanPrice(details)?.amount).toBe(9999); expect(subscribedPlanPrice(details)?.billingCycle).toBe('YEARLY'); expect(subscribedPlanPrice({...details,subscription:{...details.subscription!,planPriceId:'missing'}})).toBeNull() })
  it('uses canonical PENDING_PAYMENT and APPLIED upgrade states', () => { const base={changeId:'c',companyId:'x',subscriptionId:'s',fromPlanId:'a',toPlanId:'b',type:'UPGRADE',requestedAt:'2026-10-03T00:00:00Z'} as SubscriptionChange; expect(upgradeRequiresCheckout({...base,status:'PENDING_PAYMENT'})).toBe(true); expect(upgradeRequiresCheckout({...base,status:'APPLIED'})).toBe(false) })
  it('does not confirm a stale pre-existing target plan', () => { const details={plan:plan(),subscription:{subscriptionId:'s',companyId:'c',planId:'growth',planCode:'GROWTH',planName:'Growth',status:'ACTIVE',startedAt:'2026-09-01T00:00:00Z',cancelAtPeriodEnd:false,paymentPolicy:'CASHFREE'}} as CompanyPlanDetails; expect(paymentReturnConfirmed(details,{companyId:'c',targetPlanId:'growth',kind:'INITIAL',paymentId:'p',invoiceId:'i',createdAt:'2026-10-03T00:00:00Z'})).toBe(false); expect(paymentReturnConfirmed(details,{companyId:'c',targetPlanId:'growth',kind:'UPGRADE',paymentId:'p',invoiceId:'i',createdAt:'2026-08-01T00:00:00Z'})).toBe(false) })
})
