import type {
  ManagedEmailDeliverabilityStatus,
  ManagedEmailDomainStatus,
  ManagedEmailUsage,
} from './types'

export const normalizeBareDomain = (value: string) => value.trim().toLowerCase().replace(/\.$/, '')

export const isBareDomainInput = (value: string) => {
  const normalized = normalizeBareDomain(value)
  if (
    !normalized ||
    normalized.includes('://') ||
    normalized.includes('/') ||
    normalized.includes('@')
  ) {
    return false
  }
  return /^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?(?:\.[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?)+$/i.test(
    normalized,
  )
}

export const isValidSubdomain = (value: string) => {
  const trimmed = value.trim().toLowerCase()
  return (
    /^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$/.test(trimmed) &&
    trimmed.length >= 2 &&
    trimmed.length <= 63
  )
}

export const domainStatusLabel = (status: ManagedEmailDomainStatus) => {
  if (status === 'PENDING_APPROVAL') return 'Pending Approval'
  if (status === 'REJECTED') return 'Rejected'
  if (status === 'PENDING_DNS') return 'Waiting for DNS'
  if (status === 'SUSPENDED_VERIFICATION') return 'Verification needs attention'
  if (status === 'SUSPENDED_POLICY') return 'Sending suspended'
  return status
    .toLowerCase()
    .replaceAll('_', ' ')
    .replace(/\b\w/g, (char) => char.toUpperCase())
}

export const domainStatusTone = (status: ManagedEmailDomainStatus) => {
  if (status === 'ACTIVE') return 'bg-emerald-50 text-emerald-700'
  if (status === 'ERROR' || status === 'SUSPENDED_POLICY' || status === 'REJECTED') {
    return 'bg-rose-50 text-rose-700'
  }
  if (status === 'PENDING_APPROVAL') {
    return 'bg-blue-50 text-blue-700'
  }
  if (status === 'SUSPENDED_VERIFICATION' || status === 'PENDING_DNS') {
    return 'bg-amber-50 text-amber-700'
  }
  if (status === 'DELETED' || status === 'RETIRED') {
    return 'bg-slate-100 text-slate-500'
  }
  return 'bg-indigo-50 text-indigo-700'
}

export const deliverabilityCopy = (status: ManagedEmailDeliverabilityStatus) => {
  if (status === 'HEALTHY') return 'Sending quality looks healthy.'
  if (status === 'WARNING') return 'Some sending signals need attention.'
  if (status === 'THROTTLED') {
    return 'Sending is temporarily limited while quality recovers.'
  }
  if (status === 'SUSPENDED') return 'Managed Email sending is currently suspended.'
  return 'There is not enough sending history yet to assess deliverability.'
}

export const usageLimitLabel = (usage: ManagedEmailUsage) =>
  usage.limit === -1 ? 'Unlimited' : usage.limit.toLocaleString()

export const usageRemainingLabel = (usage: ManagedEmailUsage) =>
  usage.limit === -1 ? 'Unlimited' : Math.max(0, usage.remaining).toLocaleString()

export const isNearUsageLimit = (usage: ManagedEmailUsage) => {
  if (usage.limit <= 0 || usage.limit === -1 || usage.limitReached) return false
  return usage.remaining / usage.limit <= 0.1
}

export const managedSenderProviderLabel = (
  managementMode?: string | null,
  provider?: string | null,
) => {
  if (managementMode === 'MESSAGING_MANAGED') return 'UnifyDesk Managed Email'
  if (provider === 'AMAZON_SES') return 'Amazon SES'
  if (provider === 'SENDGRID') return 'SendGrid'
  if (provider === 'AZURE_EMAIL') return 'Azure Email'
  if (provider === 'MSG91_EMAIL') return 'MSG91 Email'
  return provider?.replaceAll('_', ' ') ?? 'Email'
}

export const nextPollingDelay = (attempt: number) => {
  if (attempt <= 0) return 4000
  if (attempt === 1) return 6000
  if (attempt === 2) return 9000
  if (attempt === 3) return 12000
  if (attempt === 4) return 16000
  return Math.min(30000, 16000 + (attempt - 4) * 3000)
}
