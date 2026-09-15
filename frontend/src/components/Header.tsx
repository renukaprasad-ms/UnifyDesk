import { FiMenu } from 'react-icons/fi'
import { useSelector } from 'react-redux'
import { useLocation } from 'react-router'
import type { RootState } from '../store/store'

export default function Header({
  menuOpen,
  onToggleMenu,
}: {
  menuOpen: boolean
  onToggleMenu: () => void
}) {
  const { pathname } = useLocation()
  const selectedCompany = useSelector((state: RootState) => state.user.selectedCompany)
  const title = pathname === '/auth' ? 'Auth layout' : 'Home'

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
        <div className="flex items-center gap-3">
          <span className="flex size-9 items-center justify-center rounded-[10px] bg-indigo-500 text-sm font-bold text-white">
            ...
          </span>
          <div>
            <p className="text-lg font-semibold tracking-tight">Messaging</p>
            <p className="mt-1 hidden text-[11px] text-slate-500 sm:block">{title}</p>
          </div>
        </div>
      </div>
      <div className="flex items-center gap-3">
        {selectedCompany && (
          <span className="hidden max-w-[220px] truncate rounded-[8px] border border-slate-200 px-3 py-2 text-xs font-semibold text-slate-700 sm:inline">
            {selectedCompany.name}
          </span>
        )}
        <span className="flex size-9 items-center justify-center rounded-full bg-slate-100 text-sm font-semibold text-slate-600">
          U
        </span>
      </div>
    </header>
  )
}
