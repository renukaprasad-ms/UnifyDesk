import { describe, expect, it } from 'vitest'
import { isManagedSmsTenantCurrent } from './tenantScope'

describe('managed SMS tenant scoping', () => {
  it('accepts results only for the company that initiated the request', () => {
    expect(isManagedSmsTenantCurrent('company-a', 'company-a')).toBe(true)
    expect(isManagedSmsTenantCurrent('company-a', 'company-b')).toBe(false)
    expect(isManagedSmsTenantCurrent('company-a', null)).toBe(false)
  })
})
