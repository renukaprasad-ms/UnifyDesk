const NOTIFICATION_OPEN_PATH = '/notification-open'

const safeOpenPath = (value) => {
  if (typeof value !== 'string' || !value.startsWith('/') || value.startsWith('//')) return null
  if (value.includes('\\') || /[\u0000-\u001f\u007f]/.test(value)) return null

  try {
    const url = new URL(value, self.location.origin)
    if (url.origin !== self.location.origin || url.pathname !== NOTIFICATION_OPEN_PATH) return null
    const notificationId = url.searchParams.get('n')
    const companyId = url.searchParams.get('c')
    if (!notificationId || !/^\d+$/.test(notificationId)) return null
    if (companyId !== null && !/^\d+$/.test(companyId)) return null
    return `${url.pathname}${url.search}`
  } catch {
    return null
  }
}

self.addEventListener('install', () => {
  self.skipWaiting()
})

self.addEventListener('activate', (event) => {
  event.waitUntil(self.clients.claim())
})

self.addEventListener('push', (event) => {
  event.waitUntil(
    (async () => {
      let payload
      try {
        payload = event.data?.json()
      } catch {
        return
      }

      if (!payload || payload.v !== 1 || !/^\d+$/.test(String(payload.notificationId ?? ''))) {
        return
      }

      const companyId =
        payload.companyId === null || /^\d+$/.test(String(payload.companyId ?? ''))
          ? payload.companyId ?? null
          : null
      const windows = await self.clients.matchAll({ type: 'window' })
      const focused = windows.filter(
        (client) => client.visibilityState === 'visible' && client.focused,
      )

      if (focused.length > 0) {
        focused.forEach((client) =>
          client.postMessage({
            type: 'UNIFYDESK_NOTIFICATION_PUSH',
            notificationId: String(payload.notificationId),
            companyId: companyId === null ? null : String(companyId),
          }),
        )
        return
      }

      const openPath = safeOpenPath(payload.openPath)
      const title =
        typeof payload.title === 'string' && payload.title.trim()
          ? payload.title.slice(0, 120)
          : 'UnifyDesk'
      const body =
        typeof payload.body === 'string' && payload.body.trim()
          ? payload.body.slice(0, 240)
          : 'You have new activity in UnifyDesk.'
      const tag =
        typeof payload.tag === 'string' && payload.tag.length <= 180
          ? payload.tag
          : `ud:notification:${payload.notificationId}`

      await self.registration.showNotification(title, {
        body,
        tag,
        data: { openPath },
      })
    })(),
  )
})

self.addEventListener('notificationclick', (event) => {
  event.notification.close()
  const openPath = safeOpenPath(event.notification.data?.openPath)
  if (!openPath) return

  event.waitUntil(
    (async () => {
      const windows = await self.clients.matchAll({ type: 'window', includeUncontrolled: true })
      const existing = windows[0]

      if (existing) {
        await existing.focus()
        existing.postMessage({
          type: 'UNIFYDESK_NOTIFICATION_OPEN',
          openPath,
        })
        return
      }

      await self.clients.openWindow(openPath)
    })(),
  )
})
