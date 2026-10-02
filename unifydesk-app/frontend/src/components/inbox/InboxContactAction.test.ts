import { describe, expect, it } from 'vitest'
import type { InboxParticipant } from '../../features/inbox/types'
import { inboxContactHref } from './InboxContactAction'

const participant = (overrides: Partial<InboxParticipant> = {}): InboxParticipant => ({
  address: 'customer@example.com',
  participantType: 'EMAIL_ADDRESS',
  contactResolution: 'UNMATCHED',
  ...overrides,
})

describe('Inbox Contact navigation', () => {
  it('links a matched participant to the canonical Contact', () => {
    expect(
      inboxContactHref(participant({ contactId: 'contact-1', contactResolution: 'MATCHED' })),
    ).toBe('/contacts/contact-1')
  })

  it('requires explicit Contact creation for an unmatched participant', () => {
    expect(inboxContactHref(participant())).toBe(
      '/contacts?new=1&email=customer%40example.com',
    )
  })

  it('routes ambiguous participants to Contact review instead of auto-creating', () => {
    expect(inboxContactHref(participant({ contactResolution: 'AMBIGUOUS' }))).toBe(
      '/contacts?q=customer%40example.com',
    )
  })
})
