import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { companyNotification } from './notificationFixtures'
import NotificationBell from './NotificationBell'
import { notificationApi } from './notificationApi'
import { useNotifications } from './notificationContext'

vi.mock('./notificationApi', () => ({
  notificationApi: {
    markSeen: vi.fn(),
  },
}))

vi.mock('./notificationContext', () => ({
  useNotifications: vi.fn(),
}))

describe('NotificationBell', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    Object.defineProperty(window, 'matchMedia', {
      configurable: true,
      value: vi.fn().mockReturnValue({
        matches: false,
        addEventListener: vi.fn(),
        removeEventListener: vi.fn(),
      }),
    })
    vi.mocked(notificationApi.markSeen).mockResolvedValue({ updated: 1 })
    vi.mocked(useNotifications).mockReturnValue({
      companyId: '922337203685470100',
      unreadCount: { account: 0, company: 120, total: 120 },
      recent: [companyNotification],
      loadingRecent: false,
      connected: true,
      refresh: vi.fn(),
      setRead: vi.fn(),
      markAllRead: vi.fn(),
      openNotification: vi.fn().mockResolvedValue(companyNotification.action?.path ?? null),
      broadcastRefresh: vi.fn(),
      refreshVersion: 0,
    })
  })

  it('shows the capped badge and marks only rendered popover items seen', async () => {
    render(
      <MemoryRouter>
        <NotificationBell />
      </MemoryRouter>,
    )

    expect(screen.getByText('99+')).toBeTruthy()
    expect(notificationApi.markSeen).not.toHaveBeenCalled()

    fireEvent.click(screen.getByRole('button', { name: /Notifications, 120 unread/i }))

    expect(await screen.findByText('3 new emails')).toBeTruthy()
    expect(screen.getByText('Quarterly update')).toBeTruthy()
    expect(screen.getByText('Here is the latest update')).toBeTruthy()
    expect(screen.getByText('Sales Inbox')).toBeTruthy()
    await waitFor(() =>
      expect(notificationApi.markSeen).toHaveBeenCalledWith('922337203685470100', [
        companyNotification.id,
      ]),
    )
  })

  it('opens the notification popover when the bell is hovered on pointer devices', async () => {
    vi.mocked(window.matchMedia).mockImplementation((query: string) => ({
      matches: query.includes('(hover: hover)'),
      media: query,
      onchange: null,
      addListener: vi.fn(),
      removeListener: vi.fn(),
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
      dispatchEvent: vi.fn(),
    }))

    render(
      <MemoryRouter>
        <NotificationBell />
      </MemoryRouter>,
    )

    const bell = screen.getByRole('button', { name: /Notifications, 120 unread/i })
    fireEvent.mouseEnter(bell.parentElement as HTMLElement)

    expect(await screen.findByRole('dialog', { name: 'Recent notifications' })).toBeTruthy()
    expect(screen.getByText('Quarterly update')).toBeTruthy()
  })

  it('closes on Escape and restores focus to the bell', async () => {
    render(
      <MemoryRouter>
        <NotificationBell />
      </MemoryRouter>,
    )

    const bell = screen.getByRole('button', { name: /Notifications, 120 unread/i })
    fireEvent.click(bell)
    await screen.findByRole('dialog', { name: 'Recent notifications' })

    fireEvent.keyDown(document, { key: 'Escape' })

    expect(screen.queryByRole('dialog', { name: 'Recent notifications' })).toBeNull()
    expect(document.activeElement).toBe(bell)
  })
})
