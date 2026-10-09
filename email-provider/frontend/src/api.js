// Session tokens are held per-tab. Never store project API keys here.
const SESSION_KEY = 'unifydesk-email-console-session'

export function readSession() {
  try {
    const session = JSON.parse(sessionStorage.getItem(SESSION_KEY) || 'null')
    if (!session?.sessionToken || !session?.expiresAt || Date.parse(session.expiresAt) <= Date.now()) {
      sessionStorage.removeItem(SESSION_KEY)
      return null
    }
    return session
  } catch {
    sessionStorage.removeItem(SESSION_KEY)
    return null
  }
}

export function saveSession(session) {
  sessionStorage.setItem(SESSION_KEY, JSON.stringify(session))
}

export function clearSession() {
  sessionStorage.removeItem(SESSION_KEY)
}

export async function request(path, method = 'GET', body) {
  const session = readSession()
  const headers = { Accept: 'application/json' }
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  if (session) headers.Authorization = 'Bearer ' + session.sessionToken
  let response
  try {
    response = await fetch('/console/v1' + path, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
      cache: 'no-store',
      credentials: 'omit',
    })
  } catch {
    throw new Error('Cannot reach the Email API. Check that both Kubernetes services are running.')
  }
  const payload = await response.json().catch(() => null)
  if (!response.ok || (payload && payload.status === false)) {
    if (response.status === 401 && path !== '/sessions') {
      clearSession()
      window.dispatchEvent(new Event('unifydesk-email-session-expired'))
    }
    const message = payload?.error_message || payload?.message || ('Request failed (HTTP ' + response.status + ')')
    throw new Error(message)
  }
  return payload?.data ?? null
}
