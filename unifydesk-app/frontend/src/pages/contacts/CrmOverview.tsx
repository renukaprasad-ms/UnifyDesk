import { useEffect, useState, type ReactNode } from 'react'
import { Link } from 'react-router'
import { FiArrowRight, FiUsers, FiTarget, FiUpload, FiBriefcase, FiCheckSquare, FiUserPlus } from 'react-icons/fi'
import { crmLeadsApi, type LeadSummary } from '../../features/contacts/crmLeadsApi'
import { crmWorkApi, type CrmTask } from '../../features/contacts/crmWorkApi'
import { PERMISSIONS } from '../../features/iam/permissions'
import { useIam } from '../../features/iam/useIam'
import { useWorkspaceEpochScope } from '../../features/workspace/useWorkspaceEpochScope'
import { useAppSelector } from '../../store/hooks'
import { selectSelectedCompanyId, selectWorkspaceEpoch } from '../../store/workspace/workspaceSlice'

const cards: Array<{
  label: string
  description: string
  to: string
  icon: ReactNode
  permission: (typeof PERMISSIONS)[keyof typeof PERMISSIONS]
}> = [
  { label: 'Leads', description: 'Qualify and track customer relationships.', to: '/crm/leads', icon: <FiUserPlus />, permission: PERMISSIONS.CRM_LEAD_READ },
  { label: 'Companies', description: 'Manage customer organizations.', to: '/crm/companies', icon: <FiBriefcase />, permission: PERMISSIONS.CRM_ACCOUNT_READ },
  { label: 'Tasks', description: 'Manage customer follow-ups.', to: '/crm/tasks', icon: <FiCheckSquare />, permission: PERMISSIONS.CRM_TASK_READ },
  { label: 'Contacts', description: 'Review and manage customer profiles.', to: '/contacts', icon: <FiUsers />, permission: PERMISSIONS.CONTACT_READ },
  { label: 'Segments', description: 'Build dynamic customer audiences.', to: '/contacts/segments', icon: <FiTarget />, permission: PERMISSIONS.CONTACT_READ },
  { label: 'Imports', description: 'Upload and map contact CSV files.', to: '/contacts/imports', icon: <FiUpload />, permission: PERMISSIONS.CONTACT_IMPORT },
]

export default function CrmOverview() {
  const companyId = useAppSelector(selectSelectedCompanyId)
  const epoch = useAppSelector(selectWorkspaceEpoch)
  const { capture, isCurrent } = useWorkspaceEpochScope(companyId, epoch)
  const { can } = useIam()
  const [summary, setSummary] = useState<LeadSummary | null>(null)
  const [tasks, setTasks] = useState<CrmTask[]>([])

  const canReadLeads = can(PERMISSIONS.CRM_LEAD_READ) && can(PERMISSIONS.CONTACT_READ)
  const canReadTasks = can(PERMISSIONS.CRM_TASK_READ) && can(PERMISSIONS.CONTACT_READ)
  useEffect(() => {
    if (!companyId) return
    let active = true
    const scope = capture()
    if (canReadLeads) {
      void crmLeadsApi.summary(companyId).then((result) => {
        if (active && isCurrent(scope)) setSummary(result)
      }).catch(() => { if (active && isCurrent(scope)) setSummary(null) })
    }
    if (canReadTasks) {
      void crmWorkApi.tasks(companyId).then((page) => {
        if (active && isCurrent(scope)) setTasks(page.items.slice(0, 5))
      }).catch(() => { if (active && isCurrent(scope)) setTasks([]) })
    }
    return () => { active = false }
  }, [companyId, epoch, capture, isCurrent, canReadLeads, canReadTasks])

  return <section className="w-full space-y-6 px-4 py-6 sm:px-6">
    <div>
      <p className="text-xs font-bold uppercase tracking-wider text-indigo-600">Customer relationships</p>
      <h1 className="mt-2 text-3xl font-bold tracking-tight text-slate-950">CRM Overview</h1>
      <p className="mt-2 max-w-2xl text-sm text-slate-600">
        Manage leads, contacts, customer companies and follow-ups. Sales pipelines and reports are planned for later releases.
      </p>
    </div>
    {canReadLeads && summary && <div className="grid gap-3 sm:grid-cols-3">
      {[
        { label: 'Total leads', count: summary.total },
        { label: 'Qualified leads', count: summary.qualified },
        { label: 'Converted leads', count: summary.converted },
      ].map((metric) => <div key={metric.label} className="rounded-xl border border-slate-200 bg-white p-4">
        <p className="text-sm text-slate-500">{metric.label}</p>
        <p className="mt-2 text-2xl font-bold text-slate-950">{metric.count.toLocaleString()}</p>
      </div>)}
    </div>}
    <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
      {cards.filter((item) => can(item.permission) &&
        (item.to !== '/crm/leads' || can(PERMISSIONS.CONTACT_READ)) &&
        (item.to !== '/crm/tasks' || can(PERMISSIONS.CONTACT_READ))).map((item) => (
        <Link key={item.to} to={item.to}
          className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm transition hover:border-indigo-300 hover:shadow-md">
          <span className="text-xl text-indigo-600" aria-hidden="true">{item.icon}</span>
          <h2 className="mt-3 text-base font-bold text-slate-950">{item.label}</h2>
          <p className="mt-2 text-sm text-slate-600">{item.description}</p>
          <span className="mt-4 inline-flex items-center gap-2 text-sm font-semibold text-indigo-600">
            Open <FiArrowRight aria-hidden="true" />
          </span>
        </Link>
      ))}
    </div>
    {canReadTasks && <div className="rounded-xl border border-slate-200 bg-white p-5">
      <div className="flex items-center justify-between">
        <h2 className="text-lg font-bold">Recent follow-ups</h2>
        <Link to="/crm/tasks" className="text-sm font-semibold text-indigo-600">View all</Link>
      </div>
      {!tasks.length ? <p className="mt-3 text-sm text-slate-500">No recent tasks.</p> :
        <ul className="mt-3 space-y-3">
          {tasks.map((task) => <li key={task.id} className="flex flex-wrap justify-between gap-2 text-sm">
            <span className="font-medium">{task.title}</span>
            <span className="text-slate-500">
              {task.dueAt ? new Date(task.dueAt).toLocaleDateString() : 'No due date'} · {task.status.replaceAll('_', ' ')}
            </span>
          </li>)}
        </ul>}
    </div>}
  </section>
}
