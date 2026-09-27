import { describe, expect, it } from 'vitest'
import {
  isManagedSmsSender,
  managedSmsComposePolicy,
  managedSmsTemplateReadyForHeader,
  managedSmsVariableErrors,
} from './composePolicy'
import type { BroadcastSender } from '../broadcast/types'
import type { ManagedSmsTemplate } from './types'

const sender = (managementMode: BroadcastSender['managementMode']): BroadcastSender => ({
  id: 'sender-1',
  connectionId: 'connection-1',
  channelConnectionId: 'channel-1',
  provider: 'MSG91_SMS',
  managementMode,
  status: 'ACTIVE',
  fromAddress: 'ACMEIN',
  connectionName: 'SMS',
  region: '',
  productionAccessEnabled: true,
  sendingEnabled: true,
})

const template: ManagedSmsTemplate = {
  id: 'template-1',
  name: 'Order shipped',
  messageCategory: 'SERVICE',
  state: 'READY',
  activeVersion: {
    id: 'version-1',
    version: 1,
    dltTemplateId: '1107',
    bodyText: 'Order {#var#}',
    variables: [{ key: 'order_id', type: 'ALPHANUMERIC', maxLength: 5 }],
    allowedHeaderIds: ['header-1'],
    providerSyncState: 'READY',
  },
}

describe('managed SMS compose policy', () => {
  it('preserves scratch body editing for customer-managed MSG91', () => {
    expect(isManagedSmsSender(sender('CUSTOMER_MANAGED'))).toBe(false)
    expect(managedSmsComposePolicy(sender('CUSTOMER_MANAGED'))).toEqual({
      managed: false,
      templateRequired: false,
      bodyEditorVisible: true,
    })
  })

  it('requires exact template mode for managed MSG91 senders', () => {
    expect(isManagedSmsSender(sender('MESSAGING_MANAGED'))).toBe(true)
    expect(managedSmsComposePolicy(sender('MESSAGING_MANAGED'))).toEqual({
      managed: true,
      templateRequired: true,
      bodyEditorVisible: false,
    })
  })

  it('only exposes ready templates compatible with the selected header', () => {
    expect(managedSmsTemplateReadyForHeader(template, 'header-1')).toBe(true)
    expect(managedSmsTemplateReadyForHeader(template, 'other-header')).toBe(false)
    expect(
      managedSmsTemplateReadyForHeader({ ...template, state: 'PROVIDER_SYNCING' }, 'header-1'),
    ).toBe(false)
  })

  it('validates declared required values and max lengths', () => {
    expect(managedSmsVariableErrors(template, {})).toEqual({ order_id: 'Required' })
    expect(managedSmsVariableErrors(template, { order_id: '123456' })).toEqual({
      order_id: 'Maximum 5 characters',
    })
    expect(managedSmsVariableErrors(template, { order_id: 'A123' })).toEqual({})
  })
})
