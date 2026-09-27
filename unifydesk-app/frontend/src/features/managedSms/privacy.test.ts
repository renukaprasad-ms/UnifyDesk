import { describe, expect, it } from 'vitest'
import {
  sanitizeManagedSmsDeliveryHealth,
  sanitizeManagedSmsStatus,
  sanitizeManagedSmsTemplate,
  sanitizeManagedSmsUsage,
} from './privacy'

describe('managed SMS customer-safe payload sanitization', () => {
  it('drops provider topology and secret-like fields from status payloads', () => {
    const result = sanitizeManagedSmsStatus({
      enabled: true,
      state: 'READY',
      nextAction: null,
      providerAccountRef: 'child-123',
      providerClientRef: 'client-123',
      authKey: 'secret',
      flowId: 'flow-123',
      route: '4',
      sharedBalance: 99999,
      dltProfile: {
        id: 'profile-1',
        peIdMasked: '******7890',
        state: 'ACTIVE',
        chainState: 'ACTIVE',
        providerClientRef: 'never-render',
      },
      readyHeaderCount: 1,
      readyTemplateCount: 2,
      sendingEnabled: true,
      usage: {
        acceptedSegments: 25,
        limit: 1000,
        sharedWalletBalance: 500000,
      },
    })

    expect(result).toEqual({
      enabled: true,
      state: 'READY',
      nextAction: null,
      dltProfile: {
        id: 'profile-1',
        peIdMasked: '******7890',
        state: 'ACTIVE',
        chainState: 'ACTIVE',
      },
      readyHeaderCount: 1,
      readyTemplateCount: 2,
      sendingEnabled: true,
      usage: {
        periodStart: null,
        periodEnd: null,
        acceptedSegments: 25,
        limit: 1000,
      },
    })
    expect(JSON.stringify(result)).not.toContain('secret')
    expect(JSON.stringify(result)).not.toContain('flow-123')
    expect(JSON.stringify(result)).not.toContain('child-123')
  })

  it('drops provider binding details from templates', () => {
    const result = sanitizeManagedSmsTemplate({
      id: 'template-1',
      name: 'Receipt',
      messageCategory: 'TRANSACTIONAL',
      state: 'READY',
      providerTemplateRef: 'private-provider-template',
      activeVersion: {
        id: 'version-1',
        version: 2,
        dltTemplateId: '1107',
        bodyText: 'Receipt {#var#}',
        variables: [{ key: 'receipt_id', type: 'ALPHANUMERIC', maxLength: 20 }],
        allowedHeaderIds: ['header-1'],
        providerSyncState: 'READY',
        providerAccountRef: 'private-account',
        flowId: 'private-flow',
        route: 'private-route',
      },
    })

    expect(JSON.stringify(result)).not.toContain('private-')
    expect(result.activeVersion?.dltTemplateId).toBe('1107')
  })

  it('only retains customer-safe usage and aggregate health fields', () => {
    const usage = sanitizeManagedSmsUsage({
      acceptedSegments: 5,
      reservedSegments: 2,
      unknownSegments: 1,
      limit: 100,
      remaining: 92,
      sendingEnabled: true,
      providerWallet: 12345,
      globalCapacity: 999,
    })
    const health = sanitizeManagedSmsDeliveryHealth({
      status: 'HEALTHY',
      delivered: 4,
      failed: 1,
      rejected: 0,
      unknown: 0,
      providerAccountRef: 'hidden',
    })

    expect(usage).toEqual({
      periodStart: null,
      periodEnd: null,
      acceptedSegments: 5,
      reservedSegments: 2,
      unknownSegments: 1,
      limit: 100,
      remaining: 92,
      sendingEnabled: true,
    })
    expect(health).toEqual({
      status: 'HEALTHY',
      delivered: 4,
      failed: 1,
      rejected: 0,
      unknown: 0,
      actionRequired: null,
    })
  })
})
