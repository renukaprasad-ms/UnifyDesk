export type NotificationSeverity = 'CRITICAL' | 'HIGH' | 'NORMAL' | 'LOW'
export type NotificationScopeKind = 'ACCOUNT' | 'COMPANY'
export type NotificationEmailMode = 'OFF' | 'IMMEDIATE' | 'DELAYED' | 'DIGEST'

export type NotificationItem = {
  id: string
  type: string
  category: string
  severity: NotificationSeverity
  scope: {
    kind: NotificationScopeKind
    companyId?: string | null
  }
  title: string
  body: string
  aggregateCount: number
  action: {
    kind: 'ROUTE'
    path: string
  } | null
  firstEventAt: string
  lastEventAt: string
  seenAt: string | null
  readAt: string | null
  unread: boolean
}

export type NotificationPage = {
  items: NotificationItem[]
  nextCursor: string | null
  hasMore: boolean
}

export type NotificationUnreadCount = {
  account: number
  company: number
  total: number
}

export type NotificationPreferences = {
  scope: {
    companyId: string | null
  }
  timezone: {
    value: string
    source: string
  }
  quietHours: {
    enabled: boolean
    start: string
    end: string
  }
  channels: {
    emailAvailable: boolean
    browserPushConfigured: boolean
  }
  types: NotificationPreferenceType[]
}

export type NotificationPreferenceType = {
  type: string
  label: string
  category: string
  mandatoryEmail: boolean
  email: {
    allowedModes: NotificationEmailMode[]
    defaultMode: NotificationEmailMode
    effectiveMode: NotificationEmailMode
    overrideMode: NotificationEmailMode | null
    locked: boolean
  }
  browser: {
    defaultEnabled: boolean
    effectiveEnabled: boolean
    overrideEnabled: boolean | null
    locked: boolean
  }
}

export type UpdateNotificationPreferences = {
  timezone: string
  quietHours: {
    enabled: boolean
    start: string
    end: string
  }
  types: Array<{
    type: string
    emailMode: NotificationEmailMode
    browserEnabled: boolean
  }>
}

export type PushCapability = {
  configured: boolean
  vapidPublicKey: string | null
  maxPayloadBytes: number
}

export type PushSubscriptionSummary = {
  id: string
  status: 'ACTIVE' | 'REVOKED' | 'EXPIRED' | 'INVALID'
  deviceLabel: string | null
  createdAt: string
  lastSuccessAt: string | null
}

export type RegisterPushSubscription = {
  endpoint: string
  expirationTime: number | null
  keys: {
    p256dh: string
    auth: string
  }
  deviceLabel: string
}

export type NotificationChangedPayload = {
  reason:
    | 'CREATED'
    | 'COALESCED'
    | 'READ_STATE'
    | 'SEEN_STATE'
    | 'PREFERENCE_RELEVANCE'
    | 'COUNTER_RECONCILED'
  notificationId: string
  companyId: string | null
  accountUnread: number
  companyUnread: number
  totalUnread: number
}
