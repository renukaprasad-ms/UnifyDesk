import { describe, expect, it } from 'vitest'
import type { WhatsAppTemplate } from '../../features/whatsapp/types'
import { templateVariableCounts } from './WhatsAppInbox'

describe('WhatsApp Round-1 QA contract regressions', () => {
  it('derives URL button variables from the provider BUTTONS structure', () => {
    const template = {
      id: 'template-url-button',
      name: 'track_order',
      language: 'en_US',
      category: 'UTILITY',
      status: 'APPROVED',
      syncedAt: '2026-09-29T00:00:00Z',
      components: [
        {
          type: 'BODY',
          text: 'Track order {{1}}',
        },
        {
          type: 'BUTTONS',
          buttons: [
            {
              type: 'URL',
              text: 'Track order',
              url: 'https://example.test/orders/{{1}}',
            },
          ],
        },
      ],
    } as unknown as WhatsAppTemplate

    expect(templateVariableCounts(template)).toEqual({
      header: 0,
      body: 1,
      buttons: 1,
    })
  })
})
