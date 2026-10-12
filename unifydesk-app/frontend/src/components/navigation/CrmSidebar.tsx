import { FiBarChart2, FiBriefcase, FiCheckSquare, FiClock, FiGrid, FiTarget, FiUserPlus, FiUsers } from 'react-icons/fi'
import { NavLink } from 'react-router'
import { PERMISSIONS } from '../../features/iam/permissions'
import { useIam } from '../../features/iam/useIam'

const currentItems = [
  { label: 'Overview', to: '/crm/overview', icon: <FiGrid /> },
  { label: 'Leads', to: '/crm/leads', icon: <FiUserPlus /> },
  { label: 'Contacts', to: '/contacts', icon: <FiUsers /> },
  { label: 'Companies', to: '/crm/companies', icon: <FiBriefcase /> },
  { label: 'Tasks', to: '/crm/tasks', icon: <FiCheckSquare /> },
  { label: 'Lists', to: '/contacts/lists', icon: <FiBriefcase /> },
  { label: 'Segments', to: '/contacts/segments', icon: <FiTarget /> },
]

const plannedItems = [
  { label: 'Deals', icon: <FiBarChart2 /> },
  { label: 'Activities', icon: <FiClock /> },
  { label: 'Reports', icon: <FiBarChart2 /> },
]

export default function CrmSidebar({ onNavigate }: { onNavigate: () => void }) {
  const { can } = useIam()
  const links = currentItems.filter((item) => {
    if (item.to === '/crm/leads') return can(PERMISSIONS.CRM_LEAD_READ) && can(PERMISSIONS.CONTACT_READ)
    if (item.to === '/crm/companies') return can(PERMISSIONS.CRM_ACCOUNT_READ)
    if (item.to === '/crm/tasks') return can(PERMISSIONS.CRM_TASK_READ) && can(PERMISSIONS.CONTACT_READ)
    if (item.to === '/crm/overview') return true
    return can(PERMISSIONS.CONTACT_READ)
  })
  return (
    <div className="flex min-h-full flex-col">
      <div className="px-3 pb-4 pt-2"><p className="sidebar-label">CRM</p></div>
      <nav aria-label="CRM navigation" className="space-y-1 px-2">
        {links.map((item) => (
          <NavLink
            key={item.to}
            to={item.to}
            end={item.to === '/crm/overview' || item.to === '/contacts'}
            className={({ isActive }) => `sidebar-link ${isActive ? 'sidebar-link-active' : ''}`}
            onClick={onNavigate}
          >
            <span className="sidebar-icon" aria-hidden="true">{item.icon}</span>
            <span>{item.label}</span>
          </NavLink>
        ))}
      </nav>
      <div className="px-3 pb-2 pt-6"><p className="sidebar-label">PLANNED</p></div>
      <div aria-label="Upcoming CRM capabilities" className="space-y-1 px-2">
        {plannedItems.map((item) => (
          <div key={item.label} className="sidebar-link cursor-default opacity-50" aria-disabled="true">
            <span className="sidebar-icon" aria-hidden="true">{item.icon}</span>
            <span>{item.label}</span>
            <span className="ml-auto text-[10px]">Soon</span>
          </div>
        ))}
      </div>
    </div>
  )
}
