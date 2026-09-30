import { NavLink } from 'react-router'

const links = [
  ['/contacts', 'Contacts'],
  ['/contacts/lists', 'Lists'],
  ['/contacts/segments', 'Segments'],
  ['/contacts/imports', 'Imports'],
] as const

export default function ContactsNav() {
  return (
    <nav aria-label="Contacts navigation" className="mb-6 flex gap-2 overflow-x-auto pb-1">
      {links.map(([to, label]) => (
        <NavLink
          key={to}
          to={to}
          end={to === '/contacts'}
          className={({ isActive }) =>
            'whitespace-nowrap rounded-lg px-3 py-2 text-sm font-semibold ' +
            (isActive ? 'bg-indigo-50 text-indigo-700' : 'text-slate-600 hover:bg-slate-100')
          }
        >
          {label}
        </NavLink>
      ))}
    </nav>
  )
}
