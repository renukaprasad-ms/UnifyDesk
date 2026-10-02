import { describe, expect, it } from 'vitest'
import { buildAudienceSelector } from './CanonicalBroadcastJourney'

describe('canonical Broadcast audience selection', () => {
  it('selects Contact Lists without creating Broadcast groups', () => {
    expect(buildAudienceSelector('CONTACT_LIST', 'list-1', [])).toEqual({
      sourceType: 'CONTACT_LIST',
      sourceId: 'list-1',
    })
  })

  it('selects Segments without creating Broadcast groups', () => {
    expect(buildAudienceSelector('SEGMENT', 'segment-1', [])).toEqual({
      sourceType: 'SEGMENT',
      sourceId: 'segment-1',
    })
  })

  it('supports a bounded explicit Contact selection', () => {
    expect(buildAudienceSelector('CONTACTS', '', ['contact-1', 'contact-2'])).toEqual({
      sourceType: 'CONTACTS',
      contactIds: ['contact-1', 'contact-2'],
    })
  })

  it('does not create an empty audience selector', () => {
    expect(buildAudienceSelector('CONTACT_LIST', '', [])).toBeNull()
    expect(buildAudienceSelector('CONTACTS', '', [])).toBeNull()
  })
})
