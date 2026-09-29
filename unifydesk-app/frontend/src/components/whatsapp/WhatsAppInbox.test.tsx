import { describe, expect, it } from 'vitest'
import type { WhatsAppTemplate } from '../../features/whatsapp/types'
import { templateVariableCounts } from './WhatsAppInbox'

describe('WhatsApp Inbox template variables', () => {
  it('derives header, body and button variable counts from approved components', () => {
    const template: WhatsAppTemplate = {
      id: 'template-1',
      name: 'order_update',
      language: 'en_US',
      category: 'UTILITY',
      status: 'APPROVED',
      syncedAt: '2026-09-29T00:00:00Z',
      components: [
        { type: 'HEADER', text: 'Order {{1}}' },
        { type: 'BODY', text: 'Hello {{1}}, order {{2}} is {{3}}.' },
        { type: 'BUTTONS', text: 'Track {{1}}' },
      ],
    }

    expect(templateVariableCounts(template)).toEqual({
      header: 1,
      body: 3,
      buttons: 1,
    })
  })

  it('does not invent variables for static template components', () => {
    const template: WhatsAppTemplate = {
      id: 'template-2',
      name: 'static_notice',
      language: 'en_US',
      category: 'UTILITY',
      status: 'APPROVED',
      syncedAt: '2026-09-29T00:00:00Z',
      components: [{ type: 'BODY', text: 'Your order is ready.' }],
    }

    expect(templateVariableCounts(template)).toEqual({
      header: 0,
      body: 0,
      buttons: 0,
    })
  })
})
