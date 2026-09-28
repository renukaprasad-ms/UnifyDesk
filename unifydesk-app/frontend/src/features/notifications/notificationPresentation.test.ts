import { describe, expect, it } from 'vitest'
import { companyNotification } from './notificationFixtures'
import { notificationPresentation } from './notificationPresentation'

describe('notificationPresentation', () => {
  it('shows latest email context for grouped inbox activity', () => {
    const presentation = notificationPresentation(companyNotification)

    expect(presentation.headline).toBe('3 new emails')
    expect(presentation.subject).toBe('Quarterly update')
    expect(presentation.preview).toBe('Here is the latest update')
    expect(presentation.source).toBe('Sales Inbox')
  })

  it('keeps legacy inbox notifications readable', () => {
    const presentation = notificationPresentation({
      ...companyNotification,
      title: 'New inbox activity',
      body: '3 new inbox messages',
    })

    expect(presentation.headline).toBe('New inbox activity')
    expect(presentation.subject).toBeNull()
    expect(presentation.preview).toBe('3 new inbox messages')
    expect(presentation.source).toBeNull()
  })
})
