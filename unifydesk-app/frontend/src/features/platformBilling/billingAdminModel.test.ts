import { describe, expect, it } from 'vitest'
import {
  buildOverrideInput,
  buildPlanPriceInput,
  buildRateCardInput,
  effectiveAdminPrices,
  instantToLocalDateTime,
  localDateTimeToInstant,
  optionalInstant,
  planRequiresExplicitPrice,
} from './billingAdminModel'
import type { BillingPlan } from './types'

const paidPlan: BillingPlan = {
  planId: 'p',
  code: 'PRO',
  name: 'Pro',
  status: 'PUBLISHED',
  visibility: 'PUBLIC',
  billingCycle: 'MONTHLY',
  basePrice: 1000,
  currency: 'INR',
  version: 1,
  entitlements: [],
  prices: [
    { planPriceId: 'valid', planId: 'p', billingCycle: 'MONTHLY', amount: 1000, currency: 'INR', effectiveFrom: '2026-10-01T00:00:00Z', effectiveTo: null, status: 'ACTIVE' },
    { planPriceId: 'future', planId: 'p', billingCycle: 'MONTHLY', amount: 1200, currency: 'INR', effectiveFrom: '2026-11-01T00:00:00Z', status: 'ACTIVE' },
    { planPriceId: 'usd', planId: 'p', billingCycle: 'MONTHLY', amount: 20, currency: 'USD', effectiveFrom: '2026-10-01T00:00:00Z', status: 'ACTIVE' },
  ],
}

describe('admin effective-dated financial inputs', () => {
  it('converts an ISO Instant to datetime-local using local date/time fields', () => {
    const iso = '2026-10-03T10:00:00.000Z'
    const instant = new Date(iso)
    const expected = `${instant.getFullYear()}-${String(instant.getMonth() + 1).padStart(2, '0')}-${String(instant.getDate()).padStart(2, '0')}T${String(instant.getHours()).padStart(2, '0')}:${String(instant.getMinutes()).padStart(2, '0')}`
    expect(instantToLocalDateTime(iso)).toBe(expected)
  })

  it('canonicalizes local datetime input to an ISO Instant', () => {
    expect(localDateTimeToInstant('2026-10-03T10:00', 'Effective start')).toBe(new Date('2026-10-03T10:00').toISOString())
  })

  it('round-trips existing PlanPrice and RateCard minute-level Instants without timezone drift', () => {
    const iso = '2026-10-03T10:00:00.000Z'
    const local = instantToLocalDateTime(iso)
    expect(localDateTimeToInstant(local, 'Effective start')).toBe(iso)
    expect(buildPlanPriceInput({ billingCycle: 'MONTHLY', amount: 1, currency: 'INR' }, local, '').effectiveFrom).toBe(iso)
    expect(buildRateCardInput({ planId: 'p', channel: 'SMS', provider: 'MSG91_SMS', billingCategory: 'SMS_TRANSACTIONAL', direction: 'OUTBOUND', unit: 'SEGMENT', price: 1, currency: 'INR' }, local, '').effectiveFrom).toBe(iso)
  })

  it('keeps an absent optional effective end empty/null', () => {
    expect(instantToLocalDateTime(null)).toBe('')
    expect(optionalInstant('')).toBeNull()
  })

  it('requires and canonicalizes PlanPrice effective start/end', () => {
    expect(() => buildPlanPriceInput({ billingCycle: 'MONTHLY', amount: 1, currency: 'INR' }, '', '')).toThrow(/required/)
    expect(buildPlanPriceInput({ billingCycle: 'MONTHLY', amount: 1, currency: 'INR' }, '2026-10-03T10:00', '2026-11-03T10:00')).toMatchObject({ effectiveFrom: expect.stringMatching(/Z$/), effectiveTo: expect.stringMatching(/Z$/) })
  })

  it('requires RateCard start, preserves end/dimensions and canonical SMS SEGMENT', () => {
    expect(() => buildRateCardInput({ planId: 'p', channel: 'SMS', provider: 'MSG91_SMS', billingCategory: 'SMS_TRANSACTIONAL', direction: 'OUTBOUND', unit: 'SEGMENT', price: 1, currency: 'INR' }, '', '')).toThrow(/required/)
    const result = buildRateCardInput({ planId: 'p', channel: 'SMS', provider: 'MSG91_SMS', country: 'IN', carrier: 'Airtel', billingCategory: 'SMS_TRANSACTIONAL', direction: 'OUTBOUND', unit: 'SEGMENT', price: 1, currency: 'INR' }, '2026-10-03T10:00', '2026-11-03T10:00')
    expect(result).toMatchObject({ country: 'IN', carrier: 'Airtel', unit: 'SEGMENT', effectiveFrom: expect.stringMatching(/Z$/), effectiveTo: expect.stringMatching(/Z$/) })
  })

  it('requires override start and normalizes blank optional dimensions', () => {
    const base = { channel: 'SMS' as const, provider: 'MSG91_SMS' as const, billingCategory: 'SMS_OTP' as const, direction: 'OUTBOUND' as const, unit: 'SEGMENT' as const, price: 1, currency: 'INR', country: ' ', carrier: ' ' }
    expect(() => buildOverrideInput(base, '', '')).toThrow(/required/)
    expect(buildOverrideInput(base, '2026-10-03T10:00', '')).toMatchObject({ country: undefined, carrier: undefined, effectiveFrom: expect.stringMatching(/Z$/), effectiveTo: null })
  })

  it('manual paid grants expose only current ACTIVE INR prices', () => {
    expect(planRequiresExplicitPrice(paidPlan)).toBe(true)
    expect(effectiveAdminPrices(paidPlan, new Date('2026-10-03T00:00:00Z')).map((p) => p.planPriceId)).toEqual(['valid'])
  })
})
