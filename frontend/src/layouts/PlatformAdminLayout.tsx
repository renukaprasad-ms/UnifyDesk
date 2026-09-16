import { NavLink, Outlet } from 'react-router'

const links = [
  ['Dashboard', '/platform'],
  ['Companies', '/platform/companies'],
  ['Users', '/platform/users'],
  ['Plans', '/platform/plans'],
  ['Billing', '/platform/billing'],
  ['Admins', '/platform/admins'],
  ['Audit logs', '/platform/audit'],
] as const

const PlatformAdminLayout = () => (
  <div className="min-h-screen bg-slate-50 lg:flex">
    <aside className="border-b bg-slate-950 p-5 text-white lg:min-h-screen lg:w-64 lg:border-b-0">
      <div className="text-lg font-bold">Messaging Platform</div>
      <div className="mt-1 text-xs uppercase tracking-widest text-slate-400">Administration</div>
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
    </aside>
    <main className="min-w-0 flex-1 p-6 lg:p-8"><Outlet /></main>
  </div>
)

export default PlatformAdminLayout
