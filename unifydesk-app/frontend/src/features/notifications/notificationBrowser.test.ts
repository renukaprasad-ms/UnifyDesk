import axios from 'axios'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { notificationApi } from './notificationApi'
import {
  PushRegistrationConflictError,
  enableBrowserNotifications,
  getBrowserNotificationState,
} from './notificationBrowser'

vi.mock('./notificationApi', () => ({
  notificationApi: {
    pushCapability: vi.fn(),
    registerPushSubscription: vi.fn(),
    revokePushSubscription: vi.fn(),
  },
}))

type FakePermission = NotificationPermission

const setNotificationApi = (
  permission: FakePermission,
  requestPermission = vi.fn<() => Promise<NotificationPermission>>(),
) => {
  Object.defineProperty(window, 'Notification', {
    configurable: true,
    value: {
      permission,
      requestPermission,
    },
  })
  return requestPermission
}

const installPushEnvironment = (subscription: PushSubscription | null) => {
  const subscribe = vi.fn().mockResolvedValue(subscription)
  const getSubscription = vi.fn().mockResolvedValue(subscription)
  const registration = {
    pushManager: { subscribe, getSubscription },
  } as unknown as ServiceWorkerRegistration

  Object.defineProperty(navigator, 'serviceWorker', {
    configurable: true,
    value: {
      getRegistration: vi.fn().mockResolvedValue(registration),
      register: vi.fn().mockResolvedValue(registration),
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
    },
  })
  Object.defineProperty(window, 'PushManager', {
    configurable: true,
    value: function PushManager() {},
  })

  return { subscribe, getSubscription }
}

const subscription = (unsubscribe = vi.fn().mockResolvedValue(true)) =>
  ({
    endpoint: 'https://push.example/subscription',
    expirationTime: null,
    toJSON: () => ({
      endpoint: 'https://push.example/subscription',
      expirationTime: null,
      keys: { p256dh: 'p256dh', auth: 'auth' },
    }),
    unsubscribe,
  }) as unknown as PushSubscription

describe('notification browser permission flow', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    window.localStorage.clear()
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('does not request permission while merely reading browser state', async () => {
    const requestPermission = setNotificationApi('default')
    installPushEnvironment(null)

    await getBrowserNotificationState()

    expect(requestPermission).not.toHaveBeenCalled()
  })

  it('requests permission only from the explicit enable flow and registers push', async () => {
    const requestPermission = setNotificationApi('default', vi.fn().mockResolvedValue('granted'))
    const localSubscription = subscription()
    const { subscribe } = installPushEnvironment(localSubscription)
    vi.mocked(notificationApi.pushCapability).mockResolvedValue({
      configured: true,
      vapidPublicKey: 'AQ',
      maxPayloadBytes: 4096,
    })
    vi.mocked(notificationApi.registerPushSubscription).mockResolvedValue({
      id: '123',
      status: 'ACTIVE',
      deviceLabel: 'Browser',
      createdAt: '2026-09-27T07:00:00Z',
      lastSuccessAt: null,
    })

    const result = await enableBrowserNotifications()

    expect(requestPermission).toHaveBeenCalledTimes(1)
    expect(subscribe).not.toHaveBeenCalled()
    expect(notificationApi.registerPushSubscription).toHaveBeenCalledTimes(1)
    expect(result?.id).toBe('123')
  })

  it('clears a cross-account subscription conflict instead of rebinding it', async () => {
    setNotificationApi('granted')
    const unsubscribe = vi.fn().mockResolvedValue(true)
    installPushEnvironment(subscription(unsubscribe))
    vi.mocked(notificationApi.pushCapability).mockResolvedValue({
      configured: true,
      vapidPublicKey: 'AQ',
      maxPayloadBytes: 4096,
    })
    vi.mocked(notificationApi.registerPushSubscription).mockRejectedValue({
      response: { status: 409 },
    })
    vi.spyOn(axios, 'isAxiosError').mockReturnValue(true)

    await expect(enableBrowserNotifications()).rejects.toBeInstanceOf(PushRegistrationConflictError)
    expect(unsubscribe).toHaveBeenCalledTimes(1)
    expect(window.localStorage.getItem('unifydesk.notification.pushSubscriptionId')).toBeNull()
  })
})
