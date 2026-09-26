import { describe, expect, it } from 'vitest'
import {
  deliverabilityCopy,
  isBareDomainInput,
  isNearUsageLimit,
  managedSenderProviderLabel,
  nextPollingDelay,
  usageLimitLabel,
  usageRemainingLabel,
} from './managedEmailUi'
import { shouldPollManagedEmailDomain } from './polling'
import type { ManagedEmailUsage } from './types'

const usage = (overrides: Partial<ManagedEmailUsage> = {}): ManagedEmailUsage => ({
  periodStart: '2026-09-01T00:00:00Z',
  periodEndExclusive: '2026-10-01T00:00:00Z',
  accepted: 100,
  reserved: 0,
  adjustments: 0,
  billable: 100,
  limit: 1000,
  remaining: 900,
  limitReached: false,
  ...overrides,
})

describe('Managed Email UI helpers', () => {
  it('accepts bare domains and rejects URLs, paths and email addresses', () => {
    expect(isBareDomainInput('xyz.com')).toBe(true)
    expect(isBareDomainInput('mail.xyz.co.in')).toBe(true)
    expect(isBareDomainInput('https://xyz.com')).toBe(false)
    expect(isBareDomainInput('xyz.com/path')).toBe(false)
    expect(isBareDomainInput('admin@xyz.com')).toBe(false)
  })

  it('presents unlimited usage without exposing -1', () => {
    const unlimited = usage({ limit: -1, remaining: -1 })
    expect(usageLimitLabel(unlimited)).toBe('Unlimited')
    expect(usageRemainingLabel(unlimited)).toBe('Unlimited')
    expect(isNearUsageLimit(unlimited)).toBe(false)
  })

  it('detects near-limit and limit-reached usage safely', () => {
    expect(isNearUsageLimit(usage({ limit: 1000, remaining: 75 }))).toBe(true)
    expect(
      isNearUsageLimit(usage({ limit: 1000, remaining: 0, limitReached: true })),
    ).toBe(false)
  })

  it('labels managed SES senders as UnifyDesk Managed Email', () => {
    expect(managedSenderProviderLabel('MESSAGING_MANAGED', 'AMAZON_SES')).toBe(
      'UnifyDesk Managed Email',
    )
    expect(managedSenderProviderLabel('CUSTOMER_MANAGED', 'AMAZON_SES')).toBe(
      'Amazon SES',
    )
  })

  it('supports every frozen deliverability status with customer-safe copy', () => {
    expect(deliverabilityCopy('INSUFFICIENT_DATA')).toMatch(/not enough sending history/i)
    expect(deliverabilityCopy('HEALTHY')).toMatch(/healthy/i)
    expect(deliverabilityCopy('WARNING')).toMatch(/attention/i)
    expect(deliverabilityCopy('THROTTLED')).toMatch(/temporarily limited/i)
    expect(deliverabilityCopy('SUSPENDED')).toMatch(/suspended/i)
  })

  it('backs polling off and stops on active or terminal states', () => {
    expect(nextPollingDelay(0)).toBe(4000)
    expect(nextPollingDelay(5)).toBeGreaterThanOrEqual(16000)
    expect(nextPollingDelay(20)).toBeLessThanOrEqual(30000)
    expect(shouldPollManagedEmailDomain('PROVISIONING')).toBe(true)
    expect(shouldPollManagedEmailDomain('PENDING_DNS')).toBe(true)
    expect(shouldPollManagedEmailDomain('VERIFYING')).toBe(true)
    expect(shouldPollManagedEmailDomain('ACTIVE')).toBe(false)
    expect(shouldPollManagedEmailDomain('SUSPENDED_POLICY')).toBe(false)
    expect(shouldPollManagedEmailDomain('ERROR')).toBe(false)
    expect(shouldPollManagedEmailDomain('RETIRED')).toBe(false)
  })
})
