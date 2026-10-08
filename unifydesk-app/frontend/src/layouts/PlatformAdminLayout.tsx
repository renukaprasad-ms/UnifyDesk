import { useState } from 'react'
import { NavLink, Outlet, useNavigate } from 'react-router'
import { useDispatch } from 'react-redux'
import { logout } from '../features/auth/logout'
import type { AppDispatch } from '../store/store'

const links = [
  ['Dashboard', '/platform'],
  ['Companies', '/platform/companies'],
  ['Users', '/platform/users'],
  ['Plans', '/platform/plans'],
  ['Billing', '/platform/billing'],
  ['Managed providers', '/platform/managed-providers'],
  ['Admins', '/platform/admins'],
  ['Audit logs', '/platform/audit'],
] as const

const PlatformAdminLayout = () => {
  const dispatch = useDispatch<AppDispatch>()
  const navigate = useNavigate()
  const [loggingOut, setLoggingOut] = useState(false)

  const onLogout = async () => {
    if (loggingOut) return
    setLoggingOut(true)
    await logout(dispatch)
    navigate('/login', { replace: true })
  }

  return (
    <div className="min-h-screen bg-slate-50 lg:flex">
      <aside className="flex border-b bg-slate-950 p-5 text-white lg:min-h-screen lg:w-64 lg:flex-col lg:border-b-0">
        <div>
          <div className="text-lg font-bold">Messaging Platform</div>
          <div className="mt-1 text-xs uppercase tracking-widest text-slate-400">
            Administration
          </div>
        </div>
        <nav className="mt-8 grid gap-1">
          {links.map(([label, to]) => (
            <NavLink
              key={to}
              to={to}
              end={to === '/platform'}
              className={({ isActive }) =>
                `rounded-lg px-3 py-2 text-sm ${isActive ? 'bg-white/10 font-semibold' : 'text-slate-300 hover:bg-white/5'}`
              }
            >
              {label}
            </NavLink>
          ))}
        </nav>
        <button
          type="button"
          className="mt-auto rounded-lg px-3 py-2 text-left text-sm text-slate-300 hover:bg-white/5"
          onClick={onLogout}
          disabled={loggingOut}
        >
          {loggingOut ? 'Logging out…' : 'Logout'}
        </button>
      </aside>
      <main className="app-route-content min-w-0 flex-1">
        <Outlet />
      </main>
    </div>
  )
}

export default PlatformAdminLayout
