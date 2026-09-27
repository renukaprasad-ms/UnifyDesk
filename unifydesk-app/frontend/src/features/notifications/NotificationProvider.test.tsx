import { act, render, screen, waitFor } from '@testing-library/react'
import { Provider } from 'react-redux'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { store } from '../../store/store'
import {
  clearWorkspace,
  selectCompany,
} from '../../store/workspace/workspaceSlice'
import { companyNotification } from './notificationFixtures'
import { notificationApi } from './notificationApi'
import NotificationProvider, { useNotifications } from './NotificationProvider'

const realtimeHarness = vi.hoisted(() => ({
  listeners: new Map<string, (event: unknown) => void>(),
}))

vi.mock('../realtime/realtimeClient', () => ({
  realtimeClient: {
    isConnected: vi.fn(() => true),
    subscribe: vi.fn((type: string, listener: (event: unknown) => void) => {
      realtimeHarness.listeners.set(type, listener)
      return () => realtimeHarness.listeners.delete(type)
    }),
    subscribeConnection: vi.fn((listener: (connected: boolean) => void) => {
      listener(true)
      return () => undefined
    }),
  },
}))

vi.mock('./notificationApi', () => ({
  notificationApi: {
    unreadCount: vi.fn(),
    list: vi.fn(),
    setReadState: vi.fn(),
    markAllRead: vi.fn(),
  },
}))

vi.mock('./notificationBrowser', () => ({
  registerNotificationServiceWorker: vi.fn().mockResolvedValue(null),
  reconcileExistingBrowserSubscription: vi.fn().mockResolvedValue(undefined),
  installNotificationServiceWorkerNavigationBridge: vi.fn(() => () => undefined),
}))

function CountProbe() {
  const { unreadCount, companyId } = useNotifications()
  return (
    <div>
      <span data-testid="scope">{companyId ?? 'account'}</span>
      <span data-testid="count">{unreadCount.total}</span>
    </div>
  )
}

describe('NotificationProvider recovery and realtime flow', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    realtimeHarness.listeners.clear()
    store.dispatch(clearWorkspace())
    vi.mocked(notificationApi.unreadCount).mockResolvedValue({
      account: 1,
      company: 0,
      total: 1,
    })
    vi.mocked(notificationApi.list).mockResolvedValue({
      items: [companyNotification],
      nextCursor: null,
      hasMore: false,
    })
  })

  it('refetches authoritative state when the selected workspace changes', async () => {
    render(
      <Provider store={store}>
        <NotificationProvider>
          <CountProbe />
        </NotificationProvider>
      </Provider>,
    )

    await waitFor(() => expect(screen.getByTestId('scope').textContent).toBe('account'))

    act(() => {
      store.dispatch(selectCompany('922337203685470100'))
    })

    await waitFor(() =>
      expect(notificationApi.unreadCount).toHaveBeenCalledWith('922337203685470100'),
    )
    expect(screen.getByTestId('scope').textContent).toBe('922337203685470100')
  })

  it('treats realtime events as hints and follows them with a durable refetch', async () => {
    render(
      <Provider store={store}>
        <NotificationProvider>
          <CountProbe />
        </NotificationProvider>
      </Provider>,
    )

    await waitFor(() => expect(notificationApi.unreadCount).toHaveBeenCalledTimes(1))
    const baselineCalls = vi.mocked(notificationApi.unreadCount).mock.calls.length
    const listener = realtimeHarness.listeners.get('notification.changed.v1')
    expect(listener).toBeTruthy()

    act(() => {
      listener?.({
        type: 'notification.changed.v1',
        occurredAt: '2026-09-27T07:00:00Z',
        payload: {
          reason: 'CREATED',
          notificationId: '123',
          companyId: null,
          accountUnread: 2,
          companyUnread: 0,
          totalUnread: 2,
        },
      })
    })

    await waitFor(() =>
      expect(notificationApi.unreadCount).toHaveBeenCalledTimes(baselineCalls + 1),
    )
  })
})
