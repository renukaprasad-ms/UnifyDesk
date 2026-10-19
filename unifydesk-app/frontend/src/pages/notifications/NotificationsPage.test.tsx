import { SnackbarProvider } from '../../components/feedback/SnackbarProviderComponent'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { notificationApi } from '../../features/notifications/notificationApi'
import {
  accountNotification,
  companyNotification,
} from '../../features/notifications/notificationFixtures'
import { useNotifications } from '../../features/notifications/notificationContext'
import NotificationsPage from './NotificationsPage'

vi.mock('../../features/notifications/notificationApi', () => ({
  notificationApi: {
    list: vi.fn(),
    markSeen: vi.fn(),
  },
}))

vi.mock('../../features/notifications/notificationContext', () => ({
  useNotifications: vi.fn(),
}))

const providerValue = {
  companyId: '922337203685470100',
  unreadCount: { account: 1, company: 1, total: 2 },
  recent: [],
  loadingRecent: false,
  connected: true,
  refresh: vi.fn(),
  setRead: vi.fn(),
  markAllRead: vi.fn(),
  openNotification: vi.fn(),
  broadcastRefresh: vi.fn(),
  refreshVersion: 0,
}

describe('NotificationsPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(useNotifications).mockReturnValue(providerValue)
    vi.mocked(notificationApi.markSeen).mockResolvedValue({ updated: 1 })
  })

  it('keeps server cursors opaque while loading the next page', async () => {
    vi.mocked(notificationApi.list)
      .mockResolvedValueOnce({
        items: [companyNotification],
        nextCursor: 'opaque.cursor.next',
        hasMore: true,
      })
      .mockResolvedValueOnce({
        items: [accountNotification],
        nextCursor: null,
        hasMore: false,
      })

    render(
      <SnackbarProvider>{<MemoryRouter>
        <NotificationsPage />
      </MemoryRouter>}</SnackbarProvider>,
    )

    await screen.findByText('3 new emails')
    fireEvent.click(screen.getByRole('button', { name: 'Load more' }))

    await screen.findByText('Security update')
    expect(notificationApi.list).toHaveBeenNthCalledWith(2, '922337203685470100', {
      limit: 20,
      cursor: 'opaque.cursor.next',
      unreadOnly: false,
    })
  })

  it('rolls an optimistic read change back when the mutation fails', async () => {
    vi.mocked(notificationApi.list).mockResolvedValue({
      items: [companyNotification],
      nextCursor: null,
      hasMore: false,
    })
    vi.mocked(useNotifications).mockReturnValue({
      ...providerValue,
      setRead: vi.fn().mockRejectedValue(new Error('failed')),
    })

    render(
      <SnackbarProvider>{<MemoryRouter>
        <NotificationsPage />
      </MemoryRouter>}</SnackbarProvider>,
    )

    await screen.findByText('3 new emails')
    fireEvent.click(screen.getByRole('button', { name: 'Mark read' }))

    await waitFor(() => expect(screen.getByText('Unread')).toBeTruthy())
    expect(screen.getByRole('alert').textContent).toContain('Unable to update notification')
  })
})
