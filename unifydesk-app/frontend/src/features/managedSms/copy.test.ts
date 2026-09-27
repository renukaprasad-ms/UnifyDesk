import { describe, expect, it } from 'vitest'
import { managedSmsStateDescription, managedSmsStateLabel } from './copy'
import type { ManagedSmsCustomerState } from './types'

const states: ManagedSmsCustomerState[] = [
  'ACTION_REQUIRED',
  'WAITING_DLT_APPROVAL',
  'WAITING_UNIFYDESK_REVIEW',
  'PROVIDER_SYNCING',
  'READY',
  'REJECTED',
  'SUSPENDED',
]

describe('managed SMS customer states', () => {
  it('renders safe copy for every frozen public state', () => {
    for (const state of states) {
      expect(managedSmsStateLabel(state)).not.toBe('Setup in progress')
      expect(managedSmsStateDescription(state).length).toBeGreaterThan(10)
      expect(managedSmsStateDescription(state).toLowerCase()).not.toContain('msg91')
      expect(managedSmsStateDescription(state).toLowerCase()).not.toContain('authkey')
      expect(managedSmsStateDescription(state).toLowerCase()).not.toContain('flow id')
    }
  })
})
