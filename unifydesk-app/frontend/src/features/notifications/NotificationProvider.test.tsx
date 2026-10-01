import { useState } from 'react'
import { act, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { Provider } from 'react-redux'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { store } from '../../store/store'
import {
  clearWorkspace,
  selectCompany,
} from '../../store/workspace/workspaceSlice'
import { companyNotification } from './notificationFixtures'
import { notificationApi } from './notificationApi'
import NotificationProvider from './NotificationProvider'
import { useNotifications } from './notificationContext'
import type { NotificationUnreadCount } from './types'

const COMPANY_ID = '922337203685470100'

const realtimeHarness = vi.hoisted(() => ({
  listeners: new Map<string, (event: unknown) => void>(),
  connectionListener: null as ((connected: boolean) => void) | null,
}))

vi.mock('../realtime/realtimeClient', () => ({
  realtimeClient: {
    isConnected: vi.fn(() => true),
    subscribe: vi.fn((type: string, listener: (event: unknown) => void) => {
      realtimeHarness.listeners.set(type, listener)
      return () => realtimeHarness.listeners.delete(type)
    }),
    subscribeConnection: vi.fn((listener: (connected: boolean) => void) => {
      realtimeHarness.connectionListener = listener
      listener(true)
      return () => {
        if (realtimeHarness.connectionListener === listener) {
          realtimeHarness.connectionListener = null
        }
      }
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

class FakeBroadcastChannel {
  static instances: FakeBroadcastChannel[] = []

  readonly name: string
  onmessage: ((event: MessageEvent) => void) | null = null
  postMessage = vi.fn()
  close = vi.fn()

  constructor(name: string) {
    this.name = name
    FakeBroadcastChannel.instances.push(this)
  }
}

const deferred = <T,>() => {
  let resolve!: (value: T | PromiseLike<T>) => void
  const promise = new Promise<T>((resolver) => {
    resolve = resolver
  })
  return { promise, resolve }
}

function CountProbe() {
  const notifications = useNotifications()
  const { unreadCount, companyId } = notifications
  const [resolvedPath, setResolvedPath] = useState('')

  return (
    <div>
      <span data-testid="scope">{companyId ?? 'account'}</span>
      <span data-testid="account-count">{unreadCount.account}</span>
      <span data-testid="company-count">{unreadCount.company}</span>
      <span data-testid="count">{unreadCount.total}</span>
      <span data-testid="resolved-path">{resolvedPath}</span>
      <button
        type="button"
        onClick={() => {
          void notifications
            .openNotification(companyNotification)
            .then((path) => setResolvedPath(path ?? ''))
        }}
      >
        Open notification probe
      </button>
    </div>
  )
}

const renderProvider = () =>
  render(
    <Provider store={store}>
      <NotificationProvider>
        <CountProbe />
      </NotificationProvider>
    </Provider>,
  )

const emitRealtime = (payload: {
  companyId: string | null
  accountUnread: number
  companyUnread: number
  totalUnread: number
}) => {
  const listener = realtimeHarness.listeners.get('notification.changed.v1')
  expect(listener).toBeTruthy()

  act(() => {
    listener?.({
      type: 'notification.changed.v1',
      occurredAt: '2026-09-27T07:00:00Z',
      payload: {
        reason: 'CREATED',
        notificationId: '123',
        ...payload,
      },
    })
  })
}

describe('NotificationProvider recovery and realtime flow', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    realtimeHarness.listeners.clear()
    realtimeHarness.connectionListener = null
    FakeBroadcastChannel.instances = []
    Object.defineProperty(window, 'BroadcastChannel', {
      configurable: true,
      value: FakeBroadcastChannel,
    })
    Object.defineProperty(document, 'visibilityState', {
      configurable: true,
      value: 'visible',
    })
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

  it('returns the notification route without waiting for read-state persistence', async () => {
    store.dispatch(selectCompany(COMPANY_ID))
    const readState = deferred<{
      notification: typeof companyNotification
      unreadCount: NotificationUnreadCount
    }>()

    vi.mocked(notificationApi.setReadState).mockReturnValue(readState.promise)

    renderProvider()

    fireEvent.click(
      await screen.findByRole('button', { name: 'Open notification probe' }),
    )

    await waitFor(() =>
      expect(screen.getByTestId('resolved-path').textContent).toBe(
        companyNotification.action?.path,
      ),
    )
    expect(notificationApi.setReadState).toHaveBeenCalledWith(
      companyNotification.id,
      COMPANY_ID,
      true,
    )

    readState.resolve({
      notification: {
        ...companyNotification,
        unread: false,
        readAt: '2026-09-28T10:30:00Z',
      },
      unreadCount: { account: 1, company: 0, total: 1 },
    })
  })

  it('refetches authoritative state when the selected workspace changes', async () => {
    renderProvider()

    await waitFor(() => expect(screen.getByTestId('scope').textContent).toBe('account'))

    act(() => {
      store.dispatch(selectCompany(COMPANY_ID))
    })

    await waitFor(() =>
      expect(notificationApi.unreadCount).toHaveBeenCalledWith(COMPANY_ID),
    )
    expect(screen.getByTestId('scope').textContent).toBe(COMPANY_ID)
  })

  it(
    'merges account-scope realtime counts without refetching the selected workspace',
    async () => {
      store.dispatch(selectCompany(COMPANY_ID))
      vi.mocked(notificationApi.unreadCount).mockResolvedValue({
        account: 1,
        company: 7,
        total: 8,
      })

      renderProvider()

      await waitFor(() => expect(screen.getByTestId('count').textContent).toBe('8'))
      expect(screen.getByTestId('company-count').textContent).toBe('7')

      emitRealtime({
        companyId: null,
        accountUnread: 2,
        companyUnread: 0,
        totalUnread: 2,
      })

      await waitFor(() => expect(screen.getByTestId('count').textContent).toBe('9'))
      expect(screen.getByTestId('account-count').textContent).toBe('2')
      expect(screen.getByTestId('company-count').textContent).toBe('7')
      expect(notificationApi.unreadCount).toHaveBeenCalledTimes(1)
      expect(notificationApi.list).toHaveBeenCalledTimes(1)
    },
  )

  it(
    'applies matching company-scope realtime counts without notification API polling',
    async () => {
      store.dispatch(selectCompany(COMPANY_ID))
      vi.mocked(notificationApi.unreadCount).mockResolvedValue({
        account: 1,
        company: 7,
        total: 8,
      })

      renderProvider()

      await waitFor(() => expect(screen.getByTestId('count').textContent).toBe('8'))

      emitRealtime({
        companyId: COMPANY_ID,
        accountUnread: 1,
        companyUnread: 8,
        totalUnread: 9,
      })

      await waitFor(() => expect(screen.getByTestId('count').textContent).toBe('9'))
      expect(screen.getByTestId('company-count').textContent).toBe('8')
      expect(notificationApi.unreadCount).toHaveBeenCalledTimes(1)
      expect(notificationApi.list).toHaveBeenCalledTimes(1)
    },
  )

  it('refetches after realtime reconnect', async () => {
    store.dispatch(selectCompany(COMPANY_ID))
    renderProvider()

    await waitFor(() => expect(notificationApi.unreadCount).toHaveBeenCalledTimes(1))

    act(() => {
      realtimeHarness.connectionListener?.(false)
      realtimeHarness.connectionListener?.(true)
    })

    await waitFor(() => expect(notificationApi.unreadCount).toHaveBeenCalledTimes(2))
  })

  it('refetches on focus after a meaningful hidden interval', async () => {
    store.dispatch(selectCompany(COMPANY_ID))
    renderProvider()

    await waitFor(() => expect(notificationApi.unreadCount).toHaveBeenCalledTimes(1))
    const now = vi.spyOn(Date, 'now')

    now.mockReturnValue(1_000)
    Object.defineProperty(document, 'visibilityState', {
      configurable: true,
      value: 'hidden',
    })
    act(() => {
      document.dispatchEvent(new Event('visibilitychange'))
    })

    now.mockReturnValue(17_001)
    Object.defineProperty(document, 'visibilityState', {
      configurable: true,
      value: 'visible',
    })
    act(() => {
      document.dispatchEvent(new Event('visibilitychange'))
    })

    await waitFor(() => expect(notificationApi.unreadCount).toHaveBeenCalledTimes(2))
    now.mockRestore()
  })

  it('refetches on same-workspace BroadcastChannel hints from another tab', async () => {
    store.dispatch(selectCompany(COMPANY_ID))
    renderProvider()

    await waitFor(() => expect(notificationApi.unreadCount).toHaveBeenCalledTimes(1))
    const channel = FakeBroadcastChannel.instances[0]
    expect(channel?.name).toBe('unifydesk-notifications')

    act(() => {
      channel?.onmessage?.({
        data: { type: 'REFRESH', companyId: COMPANY_ID },
      } as MessageEvent)
    })

    await waitFor(() => expect(notificationApi.unreadCount).toHaveBeenCalledTimes(2))
  })
})
