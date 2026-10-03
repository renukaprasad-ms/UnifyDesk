import type { BillingPlan, CreateCompanyRateOverrideInput, CreatePlanPriceInput, CreateRateCardInput, PlanPrice } from './types'

export const requiredInstant = (value: string, label: string) => {
  if (!value.trim()) throw new Error(`${label} is required.`)
  const instant = new Date(value)
  if (Number.isNaN(instant.getTime())) throw new Error(`${label} is invalid.`)
  return instant.toISOString()
}

export const optionalInstant = (value: string) => {
  if (!value.trim()) return null
  const instant = new Date(value)
  if (Number.isNaN(instant.getTime())) throw new Error('Effective end is invalid.')
  return instant.toISOString()
}

export const normalizeDimension = (value: string) => value.trim() || undefined

export const buildPlanPriceInput = (
  base: Omit<CreatePlanPriceInput, 'effectiveFrom' | 'effectiveTo'>,
  effectiveFrom: string,
  effectiveTo: string,
): CreatePlanPriceInput => ({
  ...base,
  effectiveFrom: requiredInstant(effectiveFrom, 'Effective start'),
  effectiveTo: optionalInstant(effectiveTo),
})

export const buildRateCardInput = (
  base: Omit<CreateRateCardInput, 'effectiveFrom' | 'effectiveTo'>,
  effectiveFrom: string,
  effectiveTo: string,
): CreateRateCardInput => ({
  ...base,
  country: normalizeDimension(base.country ?? ''),
  carrier: normalizeDimension(base.carrier ?? ''),
  effectiveFrom: requiredInstant(effectiveFrom, 'Effective start'),
  effectiveTo: optionalInstant(effectiveTo),
})

export const buildOverrideInput = (
  base: Omit<CreateCompanyRateOverrideInput, 'effectiveFrom' | 'effectiveTo'>,
  effectiveFrom: string,
  effectiveTo: string,
): CreateCompanyRateOverrideInput => ({
  ...base,
  country: normalizeDimension(base.country ?? ''),
  carrier: normalizeDimension(base.carrier ?? ''),
  effectiveFrom: requiredInstant(effectiveFrom, 'Effective start'),
  effectiveTo: optionalInstant(effectiveTo),
})

export const effectiveAdminPrices = (plan: BillingPlan, pricingAt: Date): PlanPrice[] =>
  plan.prices.filter((price) => {
    if (price.status !== 'ACTIVE' || price.currency !== 'INR' || !price.effectiveFrom) return false
    const start = new Date(price.effectiveFrom)
    if (Number.isNaN(start.getTime()) || start > pricingAt) return false
    if (!price.effectiveTo) return true
    const end = new Date(price.effectiveTo)
    return !Number.isNaN(end.getTime()) && pricingAt < end
  })

export const planRequiresExplicitPrice = (plan: BillingPlan) =>
  plan.billingCycle !== 'FREE' || Number(plan.basePrice) > 0
