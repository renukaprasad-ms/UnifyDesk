const hasControlCharacter = (value: string) =>
  Array.from(value).some((character) => {
    const code = character.charCodeAt(0)
    return code <= 0x1f || code === 0x7f
  })
const SNOWFLAKE_STRING = /^\d+$/

export const formatUnreadBadge = (count: number) => {
  if (!Number.isFinite(count) || count <= 0) return null
  return count > 99 ? '99+' : String(Math.trunc(count))
}

export const isSafeAppRoute = (path: string | null | undefined): path is string => {
  if (!path || !path.startsWith('/') || path.startsWith('//')) return false
  if (path.includes('\\') || hasControlCharacter(path)) return false

  try {
    const url = new URL(path, window.location.origin)
    return url.origin === window.location.origin && url.pathname.startsWith('/')
  } catch {
    return false
  }
}

export const isSafeNotificationOpenPath = (path: string | null | undefined) => {
  if (!isSafeAppRoute(path)) return false

  try {
    const url = new URL(path, window.location.origin)
    if (url.pathname !== '/notification-open') return false

    const notificationId = url.searchParams.get('n')
    const companyId = url.searchParams.get('c')
    if (!notificationId || !SNOWFLAKE_STRING.test(notificationId)) return false
    if (companyId !== null && !SNOWFLAKE_STRING.test(companyId)) return false

    return true
  } catch {
    return false
  }
}

export const isStringId = (value: string | null | undefined): value is string =>
  Boolean(value && SNOWFLAKE_STRING.test(value))
