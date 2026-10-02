import { beforeEach, describe, expect, it, vi } from 'vitest'
import apiClient from '../../lib/api/apiClient'
import type { AudienceSourceDescriptor, BroadcastMessage } from './types'
import { broadcastApi, isBroadcastAudiencePreviewStale } from './broadcastApi'

vi.mock('../../lib/api/apiClient', () => ({
  default: {
    get: vi.fn(),
    post: vi.fn(),
    patch: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
  },
}))

const descriptor: AudienceSourceDescriptor = {
  sourceType: 'LIST',
  sourceId: 'list-1',
  sourceVersion: '17',
  filterVersion: null,
  filterHash: null,
  contactSelectionHash: null,
  resolvedAt: '2026-10-02T06:00:00Z',
}

const message: BroadcastMessage = {
  id: 'broadcast-1',
  status: 'DRAFT',
  createdAt: '2026-10-02T06:00:00Z',
  updatedAt: '2026-10-02T06:00:00Z',
  stats: {
    total: 0,
    pending: 0,
    processing: 0,
    accepted: 0,
    failed: 0,
    bounced: 0,
    unknown: 0,
    skipped: 0,
  },
}

const ok = {
  data: {
    status: true,
    status_code: 200,
    data: message,
  },
}

describe('broadcastApi canonical preview binding', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(apiClient.post).mockResolvedValue(ok as never)
  })

  it('posts the exact expectedSourceDescriptor for canonical Send', async () => {
    await broadcastApi.send('company-1', 'broadcast-1', descriptor)

    expect(apiClient.post).toHaveBeenCalledWith(
      '/api/v1/companies/company-1/broadcast/messages/broadcast-1/send',
      { expectedSourceDescriptor: descriptor },
    )
    expect(vi.mocked(apiClient.post).mock.calls[0][1]).toEqual({
      expectedSourceDescriptor: descriptor,
    })
  })

  it('posts scheduledAt and the exact expectedSourceDescriptor for canonical Schedule', async () => {
    await broadcastApi.schedule(
      'company-1',
      'broadcast-1',
      '2026-10-03T06:00:00.000Z',
      descriptor,
    )

    expect(apiClient.post).toHaveBeenCalledWith(
      '/api/v1/companies/company-1/broadcast/messages/broadcast-1/schedule',
      {
        scheduledAt: '2026-10-03T06:00:00.000Z',
        expectedSourceDescriptor: descriptor,
      },
    )
  })

  it('keeps legacy group launch compatible without a descriptor', async () => {
    await broadcastApi.send('company-1', 'broadcast-1')
    await broadcastApi.schedule('company-1', 'broadcast-1', '2026-10-03T06:00:00.000Z')

    expect(apiClient.post).toHaveBeenNthCalledWith(
      1,
      '/api/v1/companies/company-1/broadcast/messages/broadcast-1/send',
      undefined,
    )
    expect(apiClient.post).toHaveBeenNthCalledWith(
      2,
      '/api/v1/companies/company-1/broadcast/messages/broadcast-1/schedule',
      { scheduledAt: '2026-10-03T06:00:00.000Z' },
    )
  })

  it('recognizes only the deterministic stale-preview 409', () => {
    expect(
      isBroadcastAudiencePreviewStale({
        isAxiosError: true,
        response: {
          status: 409,
          data: {
            status: false,
            status_code: 409,
            error_message: 'BROADCAST_AUDIENCE_PREVIEW_STALE',
          },
        },
      }),
    ).toBe(true)

    expect(
      isBroadcastAudiencePreviewStale({
        isAxiosError: true,
        response: {
          status: 409,
          data: { error_message: 'OTHER_CONFLICT' },
        },
      }),
    ).toBe(false)
  })
})
