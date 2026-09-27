import { beforeEach, describe, expect, it, vi } from 'vitest'
import apiClient from '../../lib/api/apiClient'
import { notificationApi } from './notificationApi'
import { companyNotification, notificationCount } from './notificationFixtures'

vi.mock('../../lib/api/apiClient', () => ({
  default: {
    get: vi.fn(),
    post: vi.fn(),
    patch: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
  },
}))

const ok = <T,>(data: T) => ({
  data: {
    status: true,
    status_code: 200,
    data,
  },
})

describe('notificationApi', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('passes the opaque keyset cursor through without parsing it', async () => {
    vi.mocked(apiClient.get).mockResolvedValue(
      ok({
        items: [companyNotification],
        nextCursor: 'opaque.next.cursor',
        hasMore: true,
      }) as never,
    )

    await notificationApi.list('922337203685470100', {
      limit: 20,
      cursor: 'opaque.current.cursor',
      unreadOnly: true,
    })

    expect(apiClient.get).toHaveBeenCalledWith('/api/v1/notifications', {
      params: {
        companyId: '922337203685470100',
        limit: 20,
        cursor: 'opaque.current.cursor',
        unreadOnly: true,
      },
    })
  })

  it('uses the frozen read-state request and response contract', async () => {
    vi.mocked(apiClient.patch).mockResolvedValue(
      ok({
        notification: { ...companyNotification, unread: false, readAt: '2026-09-27T07:00:00Z' },
        unreadCount: notificationCount,
      }) as never,
    )

    await notificationApi.setReadState(
      companyNotification.id,
      companyNotification.scope.companyId ?? null,
      true,
    )

    expect(apiClient.patch).toHaveBeenCalledWith(
      `/api/v1/notifications/${companyNotification.id}/read-state`,
      {
        companyId: '922337203685470100',
        read: true,
      },
    )
  })

  it('sends a bounded read-all cutoff rather than an unbounded mutation', async () => {
    vi.mocked(apiClient.post).mockResolvedValue(
      ok({
        updated: 2,
        cutoffAt: '2026-09-27T07:00:00Z',
        unreadCount: { account: 0, company: 1, total: 1 },
      }) as never,
    )

    await notificationApi.markAllRead(
      '922337203685470100',
      '2026-09-27T07:00:00Z',
    )

    expect(apiClient.post).toHaveBeenCalledWith('/api/v1/notifications/read-all', {
      companyId: '922337203685470100',
      before: '2026-09-27T07:00:00Z',
    })
  })
})
