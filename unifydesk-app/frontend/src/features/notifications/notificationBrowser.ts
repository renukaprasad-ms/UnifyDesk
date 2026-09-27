import axios from 'axios'
import { notificationApi } from './notificationApi'
import { isSafeNotificationOpenPath } from './notificationUtils'
import type { PushSubscriptionSummary } from './types'

const SERVER_SUBSCRIPTION_ID_KEY = 'unifydesk.notification.pushSubscriptionId'
const SERVICE_WORKER_PATH = '/notification-sw.js'

export type BrowserNotificationState = {
  supported: boolean
  permission: NotificationPermission | 'unsupported'
  locallySubscribed: boolean
}

export class PushRegistrationConflictError extends Error {
  constructor() {
    super(
      'This browser subscription belonged to another account. It was cleared for safety. Select Enable again to create a subscription for this account.',
    )
    this.name = 'PushRegistrationConflictError'
  }
}

const notificationConstructor = () =>
  typeof window !== 'undefined' && 'Notification' in window ? window.Notification : null

export const supportsBrowserPush = () =>
  typeof window !== 'undefined' &&
  Boolean(notificationConstructor()) &&
  'serviceWorker' in navigator &&
  'PushManager' in window

const storedServerSubscriptionId = () =>
  typeof window === 'undefined' ? null : window.localStorage.getItem(SERVER_SUBSCRIPTION_ID_KEY)

const saveServerSubscriptionId = (id: string) => {
  window.localStorage.setItem(SERVER_SUBSCRIPTION_ID_KEY, id)
}

const clearServerSubscriptionId = () => {
  if (typeof window !== 'undefined') {
    window.localStorage.removeItem(SERVER_SUBSCRIPTION_ID_KEY)
  }
}

const getRegistration = async () => {
  const existing = await navigator.serviceWorker.getRegistration('/')
  return existing ?? navigator.serviceWorker.register(SERVICE_WORKER_PATH, { scope: '/' })
}

export const registerNotificationServiceWorker = async () => {
  if (!supportsBrowserPush()) return null
  return getRegistration()
}

const getLocalSubscription = async () => {
  if (!supportsBrowserPush()) return null
  const registration = await getRegistration()
  return registration.pushManager.getSubscription()
}

const base64UrlToUint8Array = (value: string) => {
  const padding = '='.repeat((4 - (value.length % 4)) % 4)
  const base64 = (value + padding).replace(/-/g, '+').replace(/_/g, '/')
  const binary = window.atob(base64)
  return Uint8Array.from(binary, (character) => character.charCodeAt(0))
}

const deviceLabel = () => {
  const platform = navigator.platform || 'Browser'
  return `${platform} browser`.slice(0, 120)
}

const registrationPayload = (subscription: PushSubscription) => {
  const json = subscription.toJSON()
  const p256dh = json.keys?.p256dh
  const auth = json.keys?.auth
  if (!json.endpoint || !p256dh || !auth) {
    throw new Error('Browser push subscription did not include required keys')
  }

  return {
    endpoint: json.endpoint,
    expirationTime: subscription.expirationTime,
    keys: { p256dh, auth },
    deviceLabel: deviceLabel(),
  }
}

const isConflict = (error: unknown) =>
  axios.isAxiosError(error) && error.response?.status === 409

const handleRegistration = async (subscription: PushSubscription) => {
  try {
    const registered = await notificationApi.registerPushSubscription(
      registrationPayload(subscription),
    )
    saveServerSubscriptionId(registered.id)
    return registered
  } catch (error) {
    if (isConflict(error)) {
      await subscription.unsubscribe().catch(() => false)
      clearServerSubscriptionId()
      throw new PushRegistrationConflictError()
    }
    throw error
  }
}

export const getBrowserNotificationState = async (): Promise<BrowserNotificationState> => {
  const NotificationApi = notificationConstructor()
  if (!supportsBrowserPush() || !NotificationApi) {
    return { supported: false, permission: 'unsupported', locallySubscribed: false }
  }

  const subscription =
    NotificationApi.permission === 'granted' ? await getLocalSubscription().catch(() => null) : null

  return {
    supported: true,
    permission: NotificationApi.permission,
    locallySubscribed: Boolean(subscription),
  }
}

export const enableBrowserNotifications = async (): Promise<PushSubscriptionSummary | null> => {
  const NotificationApi = notificationConstructor()
  if (!supportsBrowserPush() || !NotificationApi) {
    throw new Error('Browser notifications are not supported in this browser or context')
  }

  let permission = NotificationApi.permission
  if (permission === 'default') {
    permission = await NotificationApi.requestPermission()
  }
  if (permission === 'denied') {
    return null
  }

  const capability = await notificationApi.pushCapability()
  if (!capability.configured || !capability.vapidPublicKey) {
    throw new Error('Browser notifications are not configured on this UnifyDesk environment')
  }

  const registration = await getRegistration()
  const existing = await registration.pushManager.getSubscription()
  const subscription =
    existing ??
    (await registration.pushManager.subscribe({
      userVisibleOnly: true,
      applicationServerKey: base64UrlToUint8Array(capability.vapidPublicKey),
    }))

  return handleRegistration(subscription)
}

export const disableBrowserNotifications = async () => {
  const serverId = storedServerSubscriptionId()
  const subscription = await getLocalSubscription().catch(() => null)

  try {
    if (serverId) await notificationApi.revokePushSubscription(serverId)
  } finally {
    if (subscription) await subscription.unsubscribe().catch(() => false)
    clearServerSubscriptionId()
  }
}

export const revokeServerSubscription = async (subscriptionId: string) => {
  if (subscriptionId === storedServerSubscriptionId()) {
    await disableBrowserNotifications()
    return
  }
  await notificationApi.revokePushSubscription(subscriptionId)
}

export const reconcileExistingBrowserSubscription = async () => {
  const NotificationApi = notificationConstructor()
  if (!supportsBrowserPush() || !NotificationApi || NotificationApi.permission !== 'granted') {
    return
  }

  const subscription = await getLocalSubscription().catch(() => null)
  if (!subscription) {
    clearServerSubscriptionId()
    return
  }

  const capability = await notificationApi.pushCapability().catch(() => null)
  if (!capability?.configured) return

  try {
    await handleRegistration(subscription)
  } catch (error) {
    if (!(error instanceof PushRegistrationConflictError)) throw error
  }
}

export const revokeCurrentBrowserSubscriptionBestEffort = async (timeoutMs = 1200) => {
  if (!supportsBrowserPush()) return

  await Promise.race([
    disableBrowserNotifications().catch(() => undefined),
    new Promise<void>((resolve) => window.setTimeout(resolve, timeoutMs)),
  ])
}

export const installNotificationServiceWorkerNavigationBridge = () => {
  if (!('serviceWorker' in navigator)) return () => undefined

  const listener = (event: MessageEvent) => {
    const data = event.data as { type?: string; openPath?: string } | null
    const openPath = data?.openPath
    if (
      data?.type === 'UNIFYDESK_NOTIFICATION_OPEN' &&
      openPath &&
      isSafeNotificationOpenPath(openPath)
    ) {
      window.location.assign(openPath)
    }
  }

  navigator.serviceWorker.addEventListener('message', listener)
  return () => navigator.serviceWorker.removeEventListener('message', listener)
}
