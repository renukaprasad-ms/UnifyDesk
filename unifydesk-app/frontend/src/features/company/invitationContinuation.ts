const PENDING_INVITATION_KEY = 'unifydesk.pendingInvitationPath'
const LEGACY_PENDING_INVITATION_KEY = 'messaging.pendingInvitationPath'

const isSafeInternalPath = (path: string | null | undefined): path is string =>
  Boolean(path && path.startsWith('/') && !path.startsWith('//'))

export const isInvitationPath = (path: string | null | undefined): path is string =>
  Boolean(isSafeInternalPath(path) && /^\/invite\/[^/?#]+$/.test(path))

export const rememberInvitationPath = (path: string) => {
  if (!isInvitationPath(path) || typeof window === 'undefined') return
  window.sessionStorage.setItem(PENDING_INVITATION_KEY, path)
}

export const pendingInvitationPath = () => {
  if (typeof window === 'undefined') return null
  const current = window.sessionStorage.getItem(PENDING_INVITATION_KEY)
  if (isInvitationPath(current)) return current

  const legacy = window.sessionStorage.getItem(LEGACY_PENDING_INVITATION_KEY)
  if (isInvitationPath(legacy)) {
    window.sessionStorage.setItem(PENDING_INVITATION_KEY, legacy)
    window.sessionStorage.removeItem(LEGACY_PENDING_INVITATION_KEY)
    return legacy
  }

  window.sessionStorage.removeItem(PENDING_INVITATION_KEY)
  window.sessionStorage.removeItem(LEGACY_PENDING_INVITATION_KEY)
  window.sessionStorage.removeItem(LEGACY_PENDING_INVITATION_KEY)
  return null
}

export const clearPendingInvitationPath = () => {
  if (typeof window === 'undefined') return
  window.sessionStorage.removeItem(PENDING_INVITATION_KEY)
}

export const resolvePostAuthRedirect = (requestedPath?: string | null) => {
  if (isSafeInternalPath(requestedPath)) {
    if (isInvitationPath(requestedPath)) rememberInvitationPath(requestedPath)
    return requestedPath
  }
  return pendingInvitationPath() ?? '/'
}
