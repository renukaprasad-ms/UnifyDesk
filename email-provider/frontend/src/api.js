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


/**
 * Download original untrusted RFC 5322 bytes as a file, never as an HTML
 * document. Browser navigation links cannot supply our session bearer token.
 */
export async function downloadMail(mailboxId, messageId) {
  for (const value of [mailboxId, messageId]) {
    if (!/^[1-9][0-9]*$/.test(String(value))) throw new Error('Invalid message identifier')
  }
  const session = readSession()
  if (!session) throw new Error('Session expired. Please sign in.')
  const path = '/console/v1/mail/mailboxes/' + mailboxId + '/messages/' + messageId + '/raw'
  let response
  try {
    response = await fetch(path, {
      headers: { Authorization: 'Bearer ' + session.sessionToken, Accept: 'application/octet-stream' },
      credentials: 'omit',
      cache: 'no-store',
    })
  } catch {
    throw new Error('Unable to reach the mail service')
  }
  if (response.status === 401) {
    clearSession()
    window.dispatchEvent(new Event('unifydesk-email-session-expired'))
    throw new Error('Your session expired')
  }
  if (!response.ok) throw new Error('Message download failed (HTTP ' + response.status + ')')
  const file = await response.blob()
  if (file.size > 10485760) throw new Error('Message exceeded the configured download limit')
  const objectUrl = URL.createObjectURL(new Blob([file], { type: 'application/octet-stream' }))
  try {
    const link = document.createElement('a')
    link.href = objectUrl
    link.download = 'unifydesk-message-' + messageId + '.eml'
    document.body.appendChild(link)
    link.click()
    link.remove()
  } finally {
    URL.revokeObjectURL(objectUrl)
  }
}
