import type { BroadcastSender } from '../broadcast/types'

export type ManagedEmailServiceStatus =
  'NOT_CONFIGURED' | 'PROVISIONING' | 'ACTIVE' | 'SUSPENDED' | 'ERROR' | 'DISABLED'

export type ManagedEmailSendingStatus = 'ENABLED' | 'DISABLED' | 'PROVIDER_DISABLED'

export type ManagedEmailDomainType = 'CUSTOMER_DOMAIN' | 'UNIFYDESK_HOSTED'

export type ManagedEmailDomainStatus =
  | 'PENDING_APPROVAL'
  | 'PROVISIONING'
  | 'PENDING_DNS'
  | 'VERIFYING'
  | 'ACTIVE'
  | 'SUSPENDED_VERIFICATION'
  | 'SUSPENDED_POLICY'
  | 'ERROR'
  | 'DELETING'
  | 'DELETED'
  | 'RETIRED'
  | 'REJECTED'

export type ManagedEmailDnsRecord = {
  id: string
  purpose: string
  type: string
  name: string
  value: string
  priority?: number | null
  required: boolean
  status: string
}

export type ManagedEmailDomain = {
  id: string
  type: ManagedEmailDomainType
  domain: string
  status: ManagedEmailDomainStatus
  verificationStatus?: string | null
  dkimStatus?: string | null
  dmarcStatus?: string | null
  dmarcPolicy?: string | null
  dnsRecords: ManagedEmailDnsRecord[]
  activatedAt?: string | null
  lastCheckedAt?: string | null
  requestedSubdomain?: string | null
  rejectionReason?: string | null
  adminAssignedAlternative?: boolean | null
}

export type ManagedEmailOverview = {
  enabled: boolean
  serviceStatus: ManagedEmailServiceStatus
  sendingStatus: ManagedEmailSendingStatus
  regionLabel: string
  domainLimit: number
  senderLimit: number
  globalNotice?: string | null
}

export type ManagedEmailUsage = {
  periodStart: string
  periodEndExclusive: string
  accepted: number
  reserved: number
  adjustments: number
  billable: number
  limit: number
  remaining: number
  limitReached: boolean
}

export type ManagedEmailDeliverabilityStatus =
  'INSUFFICIENT_DATA' | 'HEALTHY' | 'WARNING' | 'THROTTLED' | 'SUSPENDED'

export type ManagedEmailDeliverability = {
  status: ManagedEmailDeliverabilityStatus
  windowStart: string
  windowEnd: string
  accepted: number
  hardBounces: number
  complaints: number
  hardBounceRate: number
  complaintRate: number
  sendingRestricted: boolean
  notice?: string | null
}

export type ManagedEmailSender = BroadcastSender & {
  managedDomainId?: string | null
  managedDomain?: string | null
}

export type CreateCustomerManagedEmailDomainInput = {
  domain: string
  idempotencyKey: string
}

export type CreateHostedManagedEmailDomainInput = {
  idempotencyKey: string
  desiredSubdomain: string
}

export type SubdomainAvailability = {
  available: boolean
  subdomain: string
  domain?: string | null
  message: string
}

export type CreateManagedEmailSenderInput = {
  domainId: string
  localPart: string
  displayName?: string | null
  replyToAddress?: string | null
}
