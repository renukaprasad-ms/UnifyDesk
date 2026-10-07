import { describe, expect, expectTypeOf, it } from 'vitest'
import type {
  AudienceSourceDescriptor,
  RecipientResolutionPreview,
} from '../../features/broadcast/types'
import type { ManagedSmsTemplate } from '../../features/managedSms/types'
import {
  buildAudienceSelector,
  buildCanonicalBroadcastPayload,
  canonicalSmsContentReady,
} from './CanonicalBroadcastJourney'

const emptyWhatsAppParameters = { header: [], body: [], buttons: [] }

const readyManagedTemplate: ManagedSmsTemplate = {
  id: 'managed-template-1',
  name: 'Order update',
  messageCategory: 'TRANSACTIONAL',
  state: 'READY',
  activeVersion: {
    id: 'managed-template-version-1',
    version: 1,
    dltTemplateId: 'dlt-template-1',
    bodyText: 'Order {{order_id}} is ready',
    variables: [{ key: 'order_id', type: 'TEXT', maxLength: 20 }],
    allowedHeaderIds: ['header-1'],
    providerSyncState: 'READY',
  },
}

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

  it('keeps recipient preview sourceDescriptor on the canonical typed contract', () => {
    expectTypeOf<RecipientResolutionPreview['sourceDescriptor']>().toEqualTypeOf<AudienceSourceDescriptor>()
  })

  it('uses only WhatsApp template fields for WhatsApp creation', () => {
    const payload = buildCanonicalBroadcastPayload({
      source: { sourceType: 'LIST', sourceId: 'list-1' },
      channel: 'WHATSAPP',
      senderId: 'sender-1',
      subject: '',
      body: 'must not be sent as free-form WhatsApp',
      whatsappTemplateId: 'wa-template-1',
      whatsappTemplateParameters: {
        header: [],
        body: ['Renuka'],
        buttons: [],
      },
    })

    expect(payload.templateId).toBeNull()
    expect(payload.managedSmsTemplateId).toBeNull()
    expect(payload.whatsappTemplateId).toBe('wa-template-1')
    expect(payload.whatsappTemplateParameters).toEqual({
      header: [],
      body: ['Renuka'],
      buttons: [],
    })
    expect(payload.bodyText).toBeNull()
    expect(payload.templateId).not.toBe(payload.whatsappTemplateId)
  })

  it('submits a published Email template without scratch content', () => {
    const payload = buildCanonicalBroadcastPayload({
      source: { sourceType: 'LIST', sourceId: 'list-1' },
      channel: 'EMAIL',
      senderId: 'sender-1',
      subject: 'Scratch subject should not be used',
      body: 'Scratch body should not be used',
      whatsappTemplateParameters: emptyWhatsAppParameters,
      emailTemplateId: 'email-template-1',
      templateVariables: { first_name: 'Friend' },
    })

    expect(payload.templateId).toBe('email-template-1')
    expect(payload.subject).toBeNull()
    expect(payload.bodyText).toBeNull()
    expect(payload.templateVariables).toEqual({ first_name: 'Friend' })
  })

  it('requires a ready Managed SMS template and submits its id and variables', () => {
    const sender = {
      provider: 'MSG91_SMS' as const,
      managementMode: 'MESSAGING_MANAGED' as const,
    }

    expect(
      canonicalSmsContentReady({
        sender,
        body: 'unsupported scratch body',
        managedSmsHeaderId: 'header-1',
        templates: [readyManagedTemplate],
        managedSmsTemplateId: '',
        templateVariables: {},
      }),
    ).toBe(false)

    expect(
      canonicalSmsContentReady({
        sender,
        body: 'unsupported scratch body',
        managedSmsHeaderId: 'header-1',
        templates: [readyManagedTemplate],
        managedSmsTemplateId: 'managed-template-1',
        templateVariables: { order_id: 'ORDER-42' },
      }),
    ).toBe(true)

    const payload = buildCanonicalBroadcastPayload({
      source: { sourceType: 'SEGMENT', sourceId: 'segment-1' },
      channel: 'SMS',
      senderId: 'managed-sender-1',
      subject: '',
      body: 'unsupported scratch body',
      whatsappTemplateParameters: emptyWhatsAppParameters,
      managedSmsTemplateId: 'managed-template-1',
      templateVariables: { order_id: 'ORDER-42' },
    })

    expect(payload.templateId).toBeNull()
    expect(payload.managedSmsTemplateId).toBe('managed-template-1')
    expect(payload.templateVariables).toEqual({ order_id: 'ORDER-42' })
    expect(payload.bodyText).toBeNull()
  })

  it('keeps customer-managed SMS scratch composition unchanged', () => {
    const sender = {
      provider: 'MSG91_SMS' as const,
      managementMode: 'CUSTOMER_MANAGED' as const,
    }

    expect(
      canonicalSmsContentReady({
        sender,
        body: 'Customer managed SMS body',
        managedSmsHeaderId: null,
        templates: [],
        managedSmsTemplateId: '',
        templateVariables: {},
      }),
    ).toBe(true)

    const payload = buildCanonicalBroadcastPayload({
      source: { sourceType: 'CONTACTS', contactIds: ['contact-1'] },
      channel: 'SMS',
      senderId: 'customer-sender-1',
      subject: '',
      body: 'Customer managed SMS body',
      whatsappTemplateParameters: emptyWhatsAppParameters,
      managedSmsTemplateId: null,
    })

    expect(payload.bodyText).toBe('Customer managed SMS body')
    expect(payload.managedSmsTemplateId).toBeNull()
    expect(payload.templateVariables).toBeUndefined()
  })
})
