import { Link } from 'react-router'
import { FiArrowRight, FiUsers, FiTarget, FiUpload, FiBriefcase, FiCheckSquare, FiUserPlus } from 'react-icons/fi'
import { PERMISSIONS } from '../../features/iam/permissions'
import { useIam } from '../../features/iam/useIam'

export default function CrmOverview() {
  const { can } = useIam()
  const cards: Array<{
    label: string
    description: string
    to: string
    icon: React.ReactNode
    permission: (typeof PERMISSIONS)[keyof typeof PERMISSIONS]
  }> = [
    { label: 'Leads', description: 'Qualify and track customer relationships.', to: '/crm/leads', icon: <FiUserPlus />, permission: PERMISSIONS.CRM_LEAD_READ },
    { label: 'Companies', description: 'Manage customer organizations.', to: '/crm/companies', icon: <FiBriefcase />, permission: PERMISSIONS.CRM_ACCOUNT_READ },
    { label: 'Tasks', description: 'Manage customer follow-ups.', to: '/crm/tasks', icon: <FiCheckSquare />, permission: PERMISSIONS.CRM_TASK_READ },
    { label: 'Contacts', description: 'Review and manage customer profiles.', to: '/contacts', icon: <FiUsers />, permission: PERMISSIONS.CONTACT_READ },
    { label: 'Segments', description: 'Build dynamic customer audiences.', to: '/contacts/segments', icon: <FiTarget />, permission: PERMISSIONS.CONTACT_READ },
    { label: 'Imports', description: 'Upload and map contact CSV files.', to: '/contacts/imports', icon: <FiUpload />, permission: PERMISSIONS.CONTACT_IMPORT },
  ]
  return (
    <section className="w-full space-y-6 px-1 py-4">
      <div>
        <p className="text-xs font-bold uppercase tracking-wider text-indigo-600">Customer relationships</p>
        <h1 className="mt-2 text-3xl font-bold tracking-tight text-slate-950">CRM Overview</h1>
        <p className="mt-2 max-w-2xl text-sm text-slate-600">
          Manage leads, contacts, customer companies and follow-up tasks in one place. Sales pipelines and reports are planned for a later release.
        </p>
      </div>
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {cards.filter((item) => can(item.permission)).map((item) => (
          <Link key={item.to} to={item.to} className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm transition hover:border-indigo-300 hover:shadow-md">
            <span className="text-xl text-indigo-600" aria-hidden="true">{item.icon}</span>
            <h2 className="mt-3 text-base font-bold text-slate-950">{item.label}</h2>
            <p className="mt-2 text-sm text-slate-600">{item.description}</p>
            <span className="mt-4 inline-flex items-center gap-2 text-sm font-semibold text-indigo-600">Open <FiArrowRight aria-hidden="true" /></span>
          </Link>
        ))}
      </div>
    </section>
  )
}
