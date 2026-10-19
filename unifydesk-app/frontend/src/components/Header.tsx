import { useEffect, useState } from 'react'
import { FiInbox, FiMenu, FiMoon, FiSun } from 'react-icons/fi'
import { mediaApi } from '../features/media/mediaApi'
import NotificationBell from '../features/notifications/NotificationBell'
import { selectAuthUser, setAppearanceMode } from '../store/auth/authSlice'
import { useAppDispatch, useAppSelector } from '../store/hooks'
import { selectSelectedCompany } from '../store/workspace/workspaceSlice'
import { applyCompanyTheme, DEFAULT_BRAND_COLOR } from '../features/companyTheme/companyTheme'
import { userApi } from '../features/user/userApi'

type HeaderProps = {
  menuOpen: boolean
  onToggleMenu: () => void
  contextAvailable?: boolean
  contextOpen?: boolean
  contextLabel?: string
  onToggleContext?: () => void
  notificationPath?: string
}

export default function Header({
  menuOpen,
  onToggleMenu,
  contextAvailable = false,
  contextOpen = false,
  contextLabel = 'Channel',
  onToggleContext,
  notificationPath = '/notifications',
}: HeaderProps) {
  const dispatch = useAppDispatch()
  const user = useAppSelector(selectAuthUser)
  const selectedCompany = useAppSelector(selectSelectedCompany)
  const [profilePictureUrl, setProfilePictureUrl] = useState('')
  const displayName = user?.name || user?.username || 'User'
  const initials = displayName
    .split(' ')
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase())
    .join('')
    .slice(0, 2)

  useEffect(() => {
    let active = true

    if (!user?.profilePictureMediaId) {
      setProfilePictureUrl('')
      return
    }

    mediaApi
      .access(user.profilePictureMediaId)
      .then((access) => {
        if (active) setProfilePictureUrl(access.url)
      })
      .catch(() => {
        if (active) setProfilePictureUrl('')
      })

    return () => {
      active = false
    }
  }, [user?.profilePictureMediaId])

  const currentAppearance = user?.appearanceMode ?? 'SYSTEM'
  const isDark =
    currentAppearance === 'DARK' ||
    (currentAppearance === 'SYSTEM' &&
      typeof window !== 'undefined' &&
      window.matchMedia?.('(prefers-color-scheme: dark)').matches)

  const toggleTheme = () => {
    const nextMode = isDark ? 'LIGHT' : 'DARK'
    const brandColor = selectedCompany?.brandColor ?? DEFAULT_BRAND_COLOR
    applyCompanyTheme(brandColor, nextMode)
    dispatch(setAppearanceMode(nextMode))
    void userApi.updatePreferences(nextMode).catch(() => {})
  }

  return (
    <header className="app-header">
      <div className="flex min-w-0 items-center gap-2">
        <button
          type="button"
          onClick={onToggleMenu}
          aria-expanded={menuOpen}
          aria-controls="main-navigation"
          aria-label="Open app navigation"
          className="mobile-header-button lg:hidden"
        >
          <FiMenu aria-hidden="true" />
        </button>

        {contextAvailable && onToggleContext && (
          <button
            type="button"
            onClick={onToggleContext}
            aria-expanded={contextOpen}
            aria-controls="channel-navigation"
            className="mobile-context-button lg:hidden"
          >
            <FiInbox aria-hidden="true" />
            <span>{contextLabel}</span>
          </button>
        )}
      </div>

      <div className="ml-auto flex min-w-0 items-center gap-3">
        <button
          type="button"
          onClick={toggleTheme}
          title={isDark ? 'Switch to light mode' : 'Switch to dark mode'}
          aria-label={isDark ? 'Switch to light mode' : 'Switch to dark mode'}
          className="flex size-9 items-center justify-center rounded-xl border border-[var(--theme-border)] bg-[var(--theme-surface)] text-[var(--theme-muted)] hover:text-[var(--theme-primary)] hover:border-[var(--theme-primary-ring)] transition-colors cursor-pointer"
        >
          {isDark ? <FiSun className="text-base text-amber-400" /> : <FiMoon className="text-base" />}
        </button>

        <NotificationBell notificationPath={notificationPath} />
        <span className="flex size-9 shrink-0 items-center justify-center overflow-hidden rounded-full bg-slate-100 dark:bg-slate-800 text-sm font-semibold text-slate-600 dark:text-slate-300">
          {profilePictureUrl ? (
            <img src={profilePictureUrl} alt="" className="h-full w-full object-cover" />
          ) : (
            initials || 'U'
          )}
        </span>
        <div className="hidden min-w-0 text-right sm:block">
          <p className="truncate text-sm font-semibold text-[var(--theme-text)]">{displayName}</p>
          <p className="mt-0.5 truncate text-[11px] text-[var(--theme-muted)]">{user?.email}</p>
        </div>
      </div>
    </header>
  )
}
