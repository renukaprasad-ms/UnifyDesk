import type { NotificationItem } from './types'

export type NotificationPresentation = {
  headline: string
  subject: string | null
  preview: string | null
  source: string | null
}

export const notificationPresentation = (
  notification: NotificationItem,
): NotificationPresentation => {
  if (notification.type !== 'INBOX_ACTIVITY' || !notification.body.includes('\n')) {
    return {
      headline: notification.title,
      subject: null,
      preview: notification.body || null,
      source: null,
    }
  }

  const [subject = '', preview = '', source = ''] = notification.body.split('\n')
  return {
    headline:
      notification.aggregateCount > 1
        ? `${notification.aggregateCount} new emails`
        : notification.title,
    subject: subject.trim() || '(No subject)',
    preview: preview.trim() || null,
    source: source.trim() || null,
  }
}
