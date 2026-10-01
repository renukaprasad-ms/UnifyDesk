import { describe, expect, it } from 'vitest'
import { formatUnreadBadge, isSafeAppRoute, isSafeNotificationOpenPath } from './notificationUtils'

describe('notificationUtils', () => {
  it('caps visual unread badges without changing the source count', () => {
    expect(formatUnreadBadge(0)).toBeNull()
    expect(formatUnreadBadge(1)).toBe('1')
    expect(formatUnreadBadge(99)).toBe('99')
    expect(formatUnreadBadge(100)).toBe('99+')
  })

  it('accepts only same-origin relative application routes', () => {
    expect(isSafeAppRoute('/inbox/email?connectionId=123')).toBe(true)
    expect(isSafeAppRoute('//evil.example/path')).toBe(false)
    expect(isSafeAppRoute('https://evil.example/path')).toBe(false)
    expect(isSafeAppRoute('/safe\\evil')).toBe(false)
    expect(isSafeAppRoute('/safe\u0000evil')).toBe(false)
  })

  it('only accepts the notification-open bridge with string ids', () => {
    expect(isSafeNotificationOpenPath('/notification-open?n=123&c=456')).toBe(true)
    expect(isSafeNotificationOpenPath('/notification-open?n=123')).toBe(true)
    expect(isSafeNotificationOpenPath('/notification-open?n=abc&c=456')).toBe(false)
    expect(isSafeNotificationOpenPath('/inbox/email?n=123&c=456')).toBe(false)
  })
})
