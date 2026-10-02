import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import type {
  RecipientResolutionPreview,
  SenderReadiness,
} from '../../features/broadcast/types'
import {
  AudienceResolutionPreviewCard,
  deliveryCapabilityCopy,
} from './BroadcastJourneyCards'

const sender = (overrides: Partial<SenderReadiness['capabilities']> = {}): SenderReadiness => ({
  senderId: 'sender-1',
  label: 'UnifyDesk sender',
  state: 'READY',
  capabilities: {
    providerAcceptance: true,
    deliveryEvents: false,
    readEvents: false,
    inboundReplies: false,
    templateRequired: false,
    freeFormSessionSupported: false,
    ...overrides,
  },
})

describe('Broadcast journey cards', () => {
  it('renders selected, eligible, excluded and actionable exclusion reasons', () => {
    const preview: RecipientResolutionPreview = {
      channel: 'EMAIL',
      purpose: 'MARKETING',
      selectedCount: 10,
      eligibleCount: 6,
      excludedCount: 4,
      exclusions: {
        OPTED_OUT: 2,
        SUPPRESSED: 1,
        MISSING_IDENTITY: 1,
      },
      sampleEligible: [],
      sampleExcluded: [],
      computedAt: new Date(0).toISOString(),
    }

    render(<AudienceResolutionPreviewCard preview={preview} loading={false} />)

    expect(screen.getByText('Selected')).toBeTruthy()
    expect(screen.getByText('Eligible')).toBeTruthy()
    expect(screen.getByText('Excluded')).toBeTruthy()
    expect(screen.getByText('Opted out')).toBeTruthy()
    expect(screen.getByText('Suppressed')).toBeTruthy()
    expect(screen.getByText('Missing channel identity')).toBeTruthy()
  })

  it('keeps provider acceptance distinct from final delivery and read events', () => {
    expect(deliveryCapabilityCopy(sender())).toContain('Provider acceptance only')
    expect(deliveryCapabilityCopy(sender({ deliveryEvents: true }))).toContain(
      'delivery status available',
    )
    expect(
      deliveryCapabilityCopy(sender({ deliveryEvents: true, readEvents: true })),
    ).toContain('read status available')
  })

  it('keeps exclusion reasons single-column until the small-screen breakpoint', () => {
    const preview: RecipientResolutionPreview = {
      channel: 'SMS',
      purpose: 'MARKETING',
      selectedCount: 2,
      eligibleCount: 1,
      excludedCount: 1,
      exclusions: { OPTED_OUT: 1 },
      sampleEligible: [],
      sampleExcluded: [],
      computedAt: new Date(0).toISOString(),
    }

    render(<AudienceResolutionPreviewCard preview={preview} loading={false} />)

    const reasonsGrid = screen.getByText('Opted out').closest('.grid')
    expect(reasonsGrid?.className).toContain('sm:grid-cols-2')
    expect(reasonsGrid?.className.split(' ')).not.toContain('grid-cols-2')
  })
})
