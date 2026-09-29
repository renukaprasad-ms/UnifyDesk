import { describe, expect, it } from 'vitest'
import type { InboxMessage } from './types'

describe('Inbox message Round-2 compatibility', () => {
  it('keeps the generic email message shape valid without whatsappStatus', () => {
    const emailMessage: InboxMessage = {
      id: 'email-message-1',
      conversationId: 'email-conversation-1',
      externalMessageId: 'provider-email-1',
      direction: 'OUTBOUND',
      status: 'SENT',
      bodyText: 'Email body',
      to: ['recipient@example.test'],
      cc: [],
      bcc: [],
      read: true,
      attachments: [],
    }

    expect(emailMessage.status).toBe('SENT')
    expect('whatsappStatus' in emailMessage).toBe(false)
  })
})
