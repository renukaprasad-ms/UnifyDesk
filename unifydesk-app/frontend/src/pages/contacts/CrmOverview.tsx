import { PageSkeleton, ListSkeleton, CardSkeleton } from '../../components/ui/Skeleton'
import { useEffect, useState, type ReactNode } from 'react'
import { Link } from 'react-router'
import {
  FiArrowRight,
  FiUsers,
  FiTarget,
  FiUpload,
  FiBriefcase,
  FiCheckSquare,
  FiUserPlus,
  FiActivity,
  FiBarChart2,
} from 'react-icons/fi'
import { crmLeadsApi, type LeadSummary } from '../../features/contacts/crmLeadsApi'
import { crmWorkApi, type CrmTask } from '../../features/contacts/crmWorkApi'
import { PERMISSIONS } from '../../features/iam/permissions'
import { useIam } from '../../features/iam/useIam'
import { useWorkspaceEpochScope } from '../../features/workspace/useWorkspaceEpochScope'
import { useAppSelector } from '../../store/hooks'
import { selectSelectedCompanyId, selectWorkspaceEpoch } from '../../store/workspace/workspaceSlice'
import { BreakdownChart } from '../../components/ui/OverviewCharts'

const cards: Array<{
  label: string
  description: string
  to: string
  icon: ReactNode
  permission: (typeof PERMISSIONS)[keyof typeof PERMISSIONS]
}> = [
  {
    label: 'Deals',
    description: 'Track opportunities through your sales pipeline.',
    to: '/crm/deals',
    icon: <FiBriefcase />,
    permission: PERMISSIONS.CRM_DEAL_READ,
  },
  {
    label: 'Activities',
    description: 'Review changes, conversations and internal notes.',
    to: '/crm/activities',
    icon: <FiActivity />,
    permission: PERMISSIONS.CRM_ACTIVITY_READ,
  },
  {
    label: 'Reports',
    description: 'Understand pipeline value and team progress.',
    to: '/crm/reports',
    icon: <FiBarChart2 />,
    permission: PERMISSIONS.CRM_REPORT_READ,
  },
  {
    label: 'Leads',
    description: 'Qualify and track customer relationships.',
    to: '/crm/leads',
    icon: <FiUserPlus />,
    permission: PERMISSIONS.CRM_LEAD_READ,
  },
  {
    label: 'Companies',
    description: 'Manage customer organizations.',
    to: '/crm/companies',
    icon: <FiBriefcase />,
    permission: PERMISSIONS.CRM_ACCOUNT_READ,
  },
  {
    label: 'Tasks',
    description: 'Manage customer follow-ups.',
    to: '/crm/tasks',
    icon: <FiCheckSquare />,
    permission: PERMISSIONS.CRM_TASK_READ,
  },
  {
    label: 'Contacts',
    description: 'Review and manage customer profiles.',
    to: '/contacts',
    icon: <FiUsers />,
    permission: PERMISSIONS.CONTACT_READ,
  },
  {
    label: 'Segments',
    description: 'Build dynamic customer audiences.',
    to: '/contacts/segments',
    icon: <FiTarget />,
    permission: PERMISSIONS.CONTACT_READ,
  },
  {
    label: 'Imports',
    description: 'Upload and map contact CSV files.',
    to: '/contacts/imports',
    icon: <FiUpload />,
    permission: PERMISSIONS.CONTACT_IMPORT,
  },
]

export default function CrmOverview() {
  const companyId = useAppSelector(selectSelectedCompanyId)
  const epoch = useAppSelector(selectWorkspaceEpoch)
  const { capture, isCurrent } = useWorkspaceEpochScope(companyId, epoch)
  const { can } = useIam()
  const scopeKey = `${companyId}:${epoch}`
  const [summaryState, setSummary] = useState<{ scope: string; data: LeadSummary | null } | null>(
    null,
  )
  const [taskState, setTasks] = useState<{
    scope: string
    items: CrmTask[]
    hasMore: boolean
    failed: boolean
  } | null>(null)
  const summary = summaryState?.scope === scopeKey ? summaryState.data : null
  const tasks = taskState?.scope === scopeKey ? taskState.items : []
  const tasksReady = taskState?.scope === scopeKey && !taskState.failed

  const canReadLeads = can(PERMISSIONS.CRM_LEAD_READ) && can(PERMISSIONS.CONTACT_READ)
  const canReadTasks = can(PERMISSIONS.CRM_TASK_READ)
  useEffect(() => {
    if (!companyId) return
    let active = true
    const scope = capture()
    if (canReadLeads) {
      void crmLeadsApi
        .summary(companyId)
        .then((result) => {
          if (active && isCurrent(scope)) setSummary({ scope: scopeKey, data: result })
        })
        .catch(() => {
          if (active && isCurrent(scope)) setSummary({ scope: scopeKey, data: null })
        })
    }
    if (canReadTasks) {
      void crmWorkApi
        .tasks(companyId)
        .then((page) => {
          if (active && isCurrent(scope))
            setTasks({ scope: scopeKey, items: page.items, hasMore: page.hasMore, failed: false })
        })
        .catch(() => {
          if (active && isCurrent(scope))
            setTasks({ scope: scopeKey, items: [], hasMore: false, failed: true })
        })
    }
    return () => {
      active = false
    }
  }, [companyId, epoch, scopeKey, capture, isCurrent, canReadLeads, canReadTasks])

  return (
    <section className="w-full space-y-6 px-4 py-6 sm:px-6">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <p className="text-xs font-bold uppercase tracking-wider text-indigo-600">
            Customer relationships
          </p>
          <h1 className="mt-2 text-3xl font-bold tracking-tight text-slate-950">CRM Overview</h1>
          <p className="mt-2 max-w-2xl text-sm text-slate-600">
            A clear view of your customer relationships, lead progress and next steps.
          </p>
        </div>
        {canReadLeads && (
          <Link to="/crm/leads" className="primary-button gap-2">
            <FiUserPlus /> Manage leads
          </Link>
        )}
      </div>
      {canReadLeads && summary && (
        <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
          {[
            {
              label: 'Total leads',
              value: summary.total.toLocaleString(),
              detail: 'Across all stages',
              icon: <FiUsers />,
            },
            {
              label: 'New leads',
              value: summary.newLeads.toLocaleString(),
              detail: 'Ready for a first conversation',
              icon: <FiUserPlus />,
            },
            {
              label: 'Qualified leads',
              value: summary.qualified.toLocaleString(),
              detail: 'Current qualified stage',
              icon: <FiTarget />,
            },
            {
              label: 'Conversion rate',
              value: `${summary.total ? ((summary.converted / summary.total) * 100).toFixed(1) : '0'}%`,
              detail: `${summary.converted.toLocaleString()} converted leads`,
              icon: <FiCheckSquare />,
            },
          ].map((metric) => (
            <div key={metric.label} className="panel p-5">
              <div className="flex items-center justify-between">
                <p className="text-xs font-semibold text-slate-500">{metric.label}</p>
                <span className="flex size-9 items-center justify-center rounded-xl bg-indigo-50 text-indigo-500">
                  {metric.icon}
                </span>
              </div>
              <p className="mt-3 text-3xl font-bold tracking-tight text-slate-950">
                {metric.value}
              </p>
              <p className="mt-2 text-xs text-slate-500">{metric.detail}</p>
            </div>
          ))}
        </div>
      )}
      <div className="grid gap-4 xl:grid-cols-2">
        {canReadLeads && summary && (
          <BreakdownChart
            title="Lead stage distribution"
            description="Where your leads stand today"
            donut
            items={[
              { label: 'New', value: summary.newLeads },
              { label: 'Qualified', value: summary.qualified },
              { label: 'Converted', value: summary.converted },
              {
                label: 'Other stages',
                value: Math.max(
                  0,
                  summary.total - summary.newLeads - summary.qualified - summary.converted,
                ),
              },
            ]}
          />
        )}
        {canReadTasks && tasksReady && (
          <BreakdownChart
            title="Follow-up workload"
            description={`${tasks.length} ${taskState?.hasMore ? 'latest loaded' : 'loaded'} tasks · current status`}
            items={[
              { label: 'Open', value: tasks.filter((task) => task.status === 'OPEN').length },
              {
                label: 'In progress',
                value: tasks.filter((task) => task.status === 'IN_PROGRESS').length,
              },
              { label: 'Done', value: tasks.filter((task) => task.status === 'DONE').length },
              {
                label: 'Cancelled',
                value: tasks.filter((task) => task.status === 'CANCELLED').length,
              },
            ]}
          />
        )}
      </div>
      {canReadTasks && !tasksReady && taskState?.scope !== scopeKey && <CardSkeleton rows={6} />}
      {canReadLeads &&
        !summary &&
        (summaryState?.scope === scopeKey ? (
          <p className="panel p-5 text-sm text-slate-500">
            Lead insights are unavailable. Reload to try again.
          </p>
        ) : (
          <PageSkeleton />
        ))}
      <div className="flex items-center justify-between">
        <h2 className="text-base font-bold text-slate-950">Your CRM workspace</h2>
        <span className="text-xs text-slate-500">Explore & manage</span>
      </div>
      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
        {cards
          .filter(
            (item) =>
              can(item.permission) &&
              (item.to !== '/crm/leads' || can(PERMISSIONS.CONTACT_READ)) &&
              (item.to !== '/crm/deals' ||
                (can(PERMISSIONS.CONTACT_READ) && can(PERMISSIONS.CRM_ACCOUNT_READ))) &&
              (item.to !== '/crm/activities' || can(PERMISSIONS.CONTACT_READ)) &&
              (item.to !== '/crm/reports' ||
                [
                  PERMISSIONS.CRM_LEAD_READ,
                  PERMISSIONS.CRM_TASK_READ,
                  PERMISSIONS.CRM_DEAL_READ,
                  PERMISSIONS.CONTACT_READ,
                ].every(can)),
          )
          .map((item) => (
            <Link
              key={item.to}
              to={item.to}
              className="panel group flex items-center gap-4 p-4 transition hover:border-indigo-300 hover:shadow-md"
            >
              <span
                className="flex size-10 shrink-0 items-center justify-center rounded-xl bg-indigo-50 text-lg text-indigo-600"
                aria-hidden="true"
              >
                {item.icon}
              </span>
              <div className="min-w-0 flex-1">
                <h3 className="text-sm font-bold text-slate-950">{item.label}</h3>
                <p className="mt-1 text-xs leading-5 text-slate-500">{item.description}</p>
              </div>
              <FiArrowRight
                className="shrink-0 text-slate-400 group-hover:text-indigo-500"
                aria-hidden="true"
              />
            </Link>
          ))}
      </div>
      {canReadTasks && (
        <div className="rounded-xl border border-slate-200 bg-white p-5">
          <div className="flex items-center justify-between">
            <h2 className="text-lg font-bold">Recent follow-ups</h2>
            <Link to="/crm/tasks" className="text-sm font-semibold text-indigo-600">
              View all
            </Link>
          </div>
          {!tasksReady ? (
            taskState?.scope === scopeKey ? (
              <p className="mt-3 text-sm text-slate-500">
                Follow-ups are unavailable. Reload to try again.
              </p>
            ) : (
              <ListSkeleton rows={3} />
            )
          ) : !tasks.length ? (
            <div className="py-8 text-center">
              <FiCheckSquare className="mx-auto text-3xl text-indigo-400" />
              <p className="mt-3 text-sm font-semibold text-slate-900">Make your next move</p>
              <p className="mt-1 text-xs text-slate-500">
                Add a follow-up to keep a customer conversation moving.
              </p>
              <Link
                to="/crm/tasks"
                className="mt-4 inline-flex items-center gap-2 text-xs font-semibold text-indigo-600"
              >
                Manage tasks <FiArrowRight />
              </Link>
            </div>
          ) : (
            <ul className="mt-3 space-y-3">
              {tasks.slice(0, 5).map((task) => (
                <li
                  key={task.id}
                  className="flex flex-wrap justify-between gap-2 rounded-lg bg-slate-50 p-3 text-sm"
                >
                  <span className="font-medium">{task.title}</span>
                  <span className="text-slate-500">
                    {task.dueAt ? new Date(task.dueAt).toLocaleDateString() : 'No due date'} ·{' '}
                    {task.status.replaceAll('_', ' ')}
                  </span>
                </li>
              ))}
            </ul>
          )}
        </div>
      )}
    </section>
  )
}
