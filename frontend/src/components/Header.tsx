import { useEffect, useState } from 'react'
import { FiMenu } from 'react-icons/fi'
import { useSelector } from 'react-redux'
import { mediaApi } from '../features/media/mediaApi'
import type { RootState } from '../store/store'

export default function Header({
  menuOpen,
  onToggleMenu,
}: {
  menuOpen: boolean
  onToggleMenu: () => void
}) {
  const user = useSelector((state: RootState) => state.user.user)
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

  return (
    <header className="app-header">
      <div className="flex min-w-0 items-center gap-3">
        <button
          onClick={onToggleMenu}
          aria-expanded={menuOpen}
          aria-controls="main-navigation"
          aria-label="Toggle navigation"
          className="secondary-button lg:hidden"
        >
          <FiMenu aria-hidden="true" />
        </button>
      </div>
      <div className="ml-auto flex min-w-0 items-center gap-3">
        <span className="flex size-9 shrink-0 items-center justify-center overflow-hidden rounded-full bg-slate-100 text-sm font-semibold text-slate-600">
          {profilePictureUrl ? (
            <img src={profilePictureUrl} alt="" className="h-full w-full object-cover" />
          ) : (
            initials || 'U'
          )}
        </span>
        <div className="hidden min-w-0 text-right sm:block">
          <p className="truncate text-sm font-semibold text-slate-950">{displayName}</p>
          <p className="mt-0.5 truncate text-[11px] text-slate-500">{user?.email}</p>
        </div>
      </div>
    </header>
  )
}
