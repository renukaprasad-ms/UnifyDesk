import type { ManagedEmailDomainStatus } from './types'
import { nextPollingDelay } from './managedEmailUi'

const terminalStatuses = new Set<ManagedEmailDomainStatus>([
  'ACTIVE',
  'SUSPENDED_POLICY',
  'ERROR',
  'DELETED',
  'RETIRED',
])

export const shouldPollManagedEmailDomain = (status: ManagedEmailDomainStatus) =>
  !terminalStatuses.has(status)

export const managedEmailPollingDelay = (attempt: number) => nextPollingDelay(attempt)
