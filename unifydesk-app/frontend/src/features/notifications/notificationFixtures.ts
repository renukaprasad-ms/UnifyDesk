import type {
  NotificationItem,
  NotificationPreferences,
  NotificationUnreadCount,
} from './types'

export const companyNotification: NotificationItem = {
  id: '922337203685470001',
  type: 'INBOX_ACTIVITY',
  category: 'INBOX',
  severity: 'NORMAL',
  scope: { kind: 'COMPANY', companyId: '922337203685470100' },
  title: 'New inbox activity',
  body: '3 new inbox messages',
  aggregateCount: 3,
  action: {
    kind: 'ROUTE',
    path: '/inbox/email?connectionId=922337203685470200',
  },
  firstEventAt: '2026-09-27T06:30:00Z',
  lastEventAt: '2026-09-27T06:33:00Z',
  seenAt: null,
  readAt: null,
  unread: true,
}

export const accountNotification: NotificationItem = {
  id: '922337203685470002',
  type: 'SECURITY_ACCOUNT_CRITICAL',
  category: 'SECURITY',
  severity: 'CRITICAL',
  scope: { kind: 'ACCOUNT', companyId: null },
  title: 'Security update',
  body: 'Your account security settings changed.',
  aggregateCount: 1,
  action: { kind: 'ROUTE', path: '/account/settings' },
  firstEventAt: '2026-09-27T05:30:00Z',
  lastEventAt: '2026-09-27T05:30:00Z',
  seenAt: '2026-09-27T05:31:00Z',
  readAt: '2026-09-27T05:31:00Z',
  unread: false,
}

export const notificationCount: NotificationUnreadCount = {
  account: 2,
  company: 7,
  total: 9,
}

export const notificationPreferencesFixture: NotificationPreferences = {
  scope: { companyId: '922337203685470100' },
  timezone: { value: 'Asia/Kolkata', source: 'COMPANY_DEFAULT' },
  quietHours: { enabled: true, start: '22:00', end: '08:00' },
  channels: { emailAvailable: true, browserPushConfigured: true },
  types: [
    {
      type: 'INBOX_ACTIVITY',
      label: 'Inbox activity',
      category: 'INBOX',
      mandatoryEmail: false,
      email: {
        allowedModes: ['OFF', 'DIGEST'],
        defaultMode: 'OFF',
        effectiveMode: 'DIGEST',
        overrideMode: 'DIGEST',
        locked: false,
      },
      browser: {
        defaultEnabled: false,
        effectiveEnabled: true,
        overrideEnabled: true,
        locked: false,
      },
    },
    {
      type: 'SECURITY_ACCOUNT_CRITICAL',
      label: 'Security alerts',
      category: 'SECURITY',
      mandatoryEmail: true,
      email: {
        allowedModes: ['IMMEDIATE'],
        defaultMode: 'IMMEDIATE',
        effectiveMode: 'IMMEDIATE',
        overrideMode: null,
        locked: true,
      },
      browser: {
        defaultEnabled: false,
        effectiveEnabled: false,
        overrideEnabled: null,
        locked: false,
      },
    },
  ],
}
