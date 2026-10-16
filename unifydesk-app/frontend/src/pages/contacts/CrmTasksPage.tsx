import AssigneeDialog from '../../components/ui/AssigneeDialog'
import BoardViewToggle from '../../components/ui/BoardViewToggle'
import { BoardSkeleton, TableSkeleton } from '../../components/ui/Skeleton'
import WorkspaceDialog from '../../components/ui/WorkspaceDialog'
import FilterPopover from '../../components/ui/FilterPopover'
import { useCallback, useEffect, useRef, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router'
import { FiPlus, FiRefreshCw, FiUser, FiArrowRight } from 'react-icons/fi'
import TaskBoard from '../../components/contacts/TaskBoard'
import TaskSteps from '../../components/contacts/TaskSteps'
import { taskStatusClass } from '../../features/contacts/taskPresentation'
import { companyApi } from '../../features/company/companyApi'
import { crmWorkApi, type CrmTaskColumn, type CrmTask } from '../../features/contacts/crmWorkApi'
import { crmLeadsApi } from '../../features/contacts/crmLeadsApi'
import { crmSalesApi } from '../../features/contacts/crmSalesApi'
import { taskTypeConfig } from './CrmTaskDetailPage'
import { PERMISSIONS } from '../../features/iam/permissions'
import { useIam } from '../../features/iam/useIam'
import { useWorkspaceEpochScope } from '../../features/workspace/useWorkspaceEpochScope'
import { apiErrorMessage } from '../../lib/api/apiClient'
import { selectAuthUser } from '../../store/auth/authSlice'
import { useAppSelector } from '../../store/hooks'
import { selectSelectedCompanyId, selectWorkspaceEpoch } from '../../store/workspace/workspaceSlice'

const statuses: CrmTask['status'][] = ['OPEN', 'IN_PROGRESS', 'DONE', 'CANCELLED']
const priorities: CrmTask['priority'][] = ['LOW', 'NORMAL', 'HIGH']
const taskTypes = [
  { key: 'TODO', label: 'General To-Do', icon: '📝' },
  { key: 'CALL', label: 'Phone Call', icon: '📞' },
  { key: 'MEETING', label: 'Meeting', icon: '👥' },
  { key: 'EMAIL', label: 'Email', icon: '✉️' },
  { key: 'FOLLOW_UP', label: 'Follow-up', icon: '📋' },
  { key: 'LEAD', label: 'Lead Task', icon: '🎯' },
  { key: 'COMPANY', label: 'Company Task', icon: '🏢' },
]
const label = (value: string) =>
  value
    .replaceAll('_', ' ')
    .toLowerCase()
    .replace(/^./, (char) => char.toUpperCase())
const toLocal = (value: string | null) => {
  if (!value) return ''
  const date = new Date(value)
  return new Date(date.getTime() - date.getTimezoneOffset() * 60000).toISOString().slice(0, 16)
}
const asInstant = (value: string) => (value ? new Date(value).toISOString() : null)

export default function CrmTasksPage() {
  const companyId = useAppSelector(selectSelectedCompanyId)
  const epoch = useAppSelector(selectWorkspaceEpoch)
  const currentUser = useAppSelector(selectAuthUser)
  const currentUserId = currentUser ? String(currentUser.userId) : null
  const { capture, isCurrent } = useWorkspaceEpochScope(companyId, epoch)
  const { can } = useIam()
  const navigate = useNavigate()

  const canCreate = can(PERMISSIONS.CRM_TASK_CREATE) || can(PERMISSIONS.CRM_TASK_MODIFY)
  const canEdit = can(PERMISSIONS.CRM_TASK_EDIT) || can(PERMISSIONS.CRM_TASK_MODIFY)
  const canMoveAny =
    can(PERMISSIONS.CRM_TASK_STATUS) ||
    can(PERMISSIONS.CRM_TASK_MODIFY) ||
    canEdit

  const canMoveTask = useCallback(
    (task: CrmTask) =>
      canMoveAny ||
      (currentUserId !== null &&
        (task.assigneeUserId === currentUserId || task.reporterUserId === currentUserId)),
    [canMoveAny, currentUserId],
  )

  const [view, setView] = useState<'board' | 'list'>('board')
  const [columns, setColumns] = useState<CrmTaskColumn[]>([])
  const [manageSteps, setManageSteps] = useState(false)
  const [createColumn, setCreateColumn] = useState('OPEN')
  const [tasks, setTasks] = useState<CrmTask[]>([])
  const [next, setNext] = useState<string | null>(null)
  const [members, setMembers] = useState<Array<{ userId: string; name: string }>>([])
  const [accounts, setAccounts] = useState<Array<{ id: string; name: string }>>([])
  const [leads, setLeads] = useState<Array<{ id: string; name: string }>>([])
  const [deals, setDeals] = useState<Array<{ id: string; title: string }>>([])
  const [statusFilter, setStatusFilter] = useState<CrmTask['status'] | ''>('')
  const [assigneeFilter, setAssigneeFilter] = useState('')
  const [title, setTitle] = useState('')
  const [details, setDetails] = useState('')
  const [taskType, setTaskType] = useState('TODO')
  const [dueDate, setDueDate] = useState('')
  const [priority, setPriority] = useState<CrmTask['priority']>('NORMAL')
  const [assignee, setAssignee] = useState('')
  const [contactId, setContactId] = useState('')
  const [accountId, setAccountId] = useState('')
  const [dealId, setDealId] = useState('')
  const [estimatedMinutes, setEstimatedMinutes] = useState('')
  const [assigning, setAssigning] = useState<CrmTask | null>(null)
  const [editing, setEditing] = useState<CrmTask | null>(null)
  const [editTitle, setEditTitle] = useState('')
  const [editDetails, setEditDetails] = useState('')
  const [editDate, setEditDate] = useState('')
  const [editPriority, setEditPriority] = useState<CrmTask['priority']>('NORMAL')
  const [editAssignee, setEditAssignee] = useState('')
  const pendingMoves = useRef(new Set<string>())
  const [pendingTasks, setPendingTasks] = useState<Set<string>>(new Set())
  const requestNumber = useRef(0)
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [showCreate, setShowCreate] = useState(false)
  const scopeKey = `${companyId}:${epoch}`
  const [params] = useSearchParams()

  useEffect(() => {
    const id = params.get('taskId')
    if (id) {
      navigate('/crm/tasks/' + id, { replace: true })
    }
  }, [params, navigate])
  const assigneeName = (task: CrmTask) =>
    members.find((member) => member.userId === task.assigneeUserId)?.name || 'Assigned teammate'

  const filters = {
    status: statusFilter || undefined,
    assigneeUserId: assigneeFilter || undefined,
  }
  const refresh = useCallback(async () => {
    if (!companyId) return
    const token = capture()
    const request = ++requestNumber.current
    setLoading(true)
    try {
      const [page, steps] = await Promise.all([
        crmWorkApi.tasks(companyId, undefined, {
          status: statusFilter || undefined,
          assigneeUserId: assigneeFilter || undefined,
        }),
        crmWorkApi.taskColumns(companyId),
      ])
      if (isCurrent(token) && request === requestNumber.current) {
        setColumns(steps)
        setTasks(page.items)
        setNext(page.nextCursor)
        setError('')
      }
    } catch (err) {
      if (isCurrent(token) && request === requestNumber.current)
        setError(apiErrorMessage(err, 'Unable to load tasks'))
    } finally {
      if (isCurrent(token) && request === requestNumber.current) setLoading(false)
    }
  }, [companyId, statusFilter, assigneeFilter, capture, isCurrent])

  useEffect(() => {
    setColumns([])
    setManageSteps(false)
    setShowCreate(false)
    setCreateColumn('OPEN')
    setTasks([])
    setNext(null)
    void refresh()
  }, [refresh, epoch])
  useEffect(() => {
    if (!companyId) return
    const scope = capture()
    let active = true
    if (can(PERMISSIONS.USER_READ)) {
      void companyApi
        .users(companyId)
        .then((users) => {
          if (active && isCurrent(scope))
            setMembers(
              users
                .filter((user) => user.status === 'ACTIVE')
                .map((user) => ({ userId: user.userId, name: user.name || user.email })),
            )
        })
        .catch(() => {
          /* Self-assignment continues without directory access. */
        })
    }
    if (can(PERMISSIONS.CRM_ACCOUNT_READ)) {
      void crmWorkApi
        .accounts(companyId)
        .then((page) => {
          if (active && isCurrent(scope)) setAccounts(page.items)
        })
        .catch(() => {
          /* Unlinked tasks remain available. */
        })
    }
    if (can(PERMISSIONS.CONTACT_READ) && typeof crmLeadsApi?.list === 'function') {
      void crmLeadsApi
        .list(companyId)
        .then((page) => {
          if (active && isCurrent(scope))
            setLeads(
              (page?.items || []).map((lead) => ({
                id: lead.contactId,
                name: lead.displayName || lead.organization || 'Lead',
              })),
            )
        })
        .catch(() => {
          /* Leads list optional */
        })
    }
    if (can(PERMISSIONS.CRM_DEAL_READ) && typeof crmSalesApi?.deals === 'function') {
      void crmSalesApi
        .deals(companyId)
        .then((page) => {
          if (active && isCurrent(scope))
            setDeals(
              (page?.items || []).map((deal) => ({
                id: deal.id,
                title: deal.title,
              })),
            )
        })
        .catch(() => {
          /* Deals list optional */
        })
    }
    return () => {
      active = false
    }
  }, [companyId, epoch, capture, isCurrent, can])

  const run = async (action: () => Promise<unknown>) => {
    if (busy) return
    const token = capture()
    setBusy(true)
    setError('')
    try {
      await action()
      if (isCurrent(token)) await refresh()
    } catch (err) {
      if (isCurrent(token)) {
        await refresh()
        if (isCurrent(token))
          setError(apiErrorMessage(err, 'Unable to update task; latest data reloaded.'))
      }
    } finally {
      if (isCurrent(token)) setBusy(false)
    }
  }

  const configure = async (action: () => Promise<CrmTaskColumn[]>) => {
    if (busy) return false
    const scope = capture()
    setBusy(true)
    setError('')
    try {
      const steps = await action()
      if (!isCurrent(scope)) return false
      setColumns(steps)
      return true
    } catch (err) {
      if (isCurrent(scope)) {
        await refresh()
        if (isCurrent(scope))
          setError(apiErrorMessage(err, 'Unable to update steps. Reloaded latest configuration.'))
      }
      return false
    } finally {
      if (isCurrent(scope)) setBusy(false)
    }
  }
  const moveTask = async (task: CrmTask, key: string) => {
    const operation = scopeKey + ':' + task.id
    const column = columns.find((c) => c.key === key)
    if (
      !companyId ||
      busy ||
      loading ||
      !column ||
      pendingMoves.current.has(operation) ||
      (task.columnKey || task.status) === key
    )
      return
    const scope = capture()
    pendingMoves.current.add(operation)
    setPendingTasks(new Set(pendingMoves.current))
    setError('')
    const optimistic = { ...task, columnKey: key, status: column.statusGroup }
    setTasks((old) => old.map((t) => (t.id === task.id ? optimistic : t)))
    try {
      const saved = await crmWorkApi.taskColumn(companyId, task, key)
      if (!isCurrent(scope)) return
      setTasks((old) =>
        statusFilter && saved.status !== statusFilter
          ? old.filter((t) => t.id !== task.id)
          : old.map((t) => (t.id === task.id ? saved : t)),
      )
    } catch (err) {
      if (!isCurrent(scope)) return
      setTasks((old) => old.map((t) => (t.id === task.id ? task : t)))
      setError(
        apiErrorMessage(err, 'Task could not be moved. Its previous step has been restored.'),
      )
    } finally {
      pendingMoves.current.delete(operation)
      if (isCurrent(scope)) setPendingTasks(new Set(pendingMoves.current))
    }
  }
  const pendingIds = new Set(
    tasks.filter((t) => pendingTasks.has(scopeKey + ':' + t.id)).map((t) => t.id),
  )
  const moving = pendingIds.size > 0
  const openTask = (task: CrmTask) => {
    navigate('/crm/tasks/' + task.id)
  }
  const stepName = (task: CrmTask) =>
    columns.find((c) => c.key === (task.columnKey || task.status))?.name || label(task.status)
  const create = async () => {
    if (!companyId || !title.trim()) return
    const scope = capture()
    await run(async () => {
      await crmWorkApi.addTask(companyId, title.trim(), asInstant(dueDate) || undefined, {
        columnKey: createColumn,
        details: details.trim(),
        priority,
        taskType,
        assigneeUserId: assignee || undefined,
        contactId: contactId || undefined,
        accountId: accountId || undefined,
        dealId: dealId || undefined,
        estimatedMinutes: estimatedMinutes ? parseInt(estimatedMinutes, 10) : undefined,
      })
      if (!isCurrent(scope)) return
      setTitle('')
      setDetails('')
      setDueDate('')
      setPriority('NORMAL')
      setTaskType('TODO')
      setAssignee('')
      setContactId('')
      setAccountId('')
      setDealId('')
      setEstimatedMinutes('')
      setShowCreate(false)
    })
  }
  const startEdit = (task: CrmTask) => {
    setEditing(task)
    setEditTitle(task.title)
    setEditDetails(task.details || '')
    setEditPriority(task.priority)
    setEditDate(toLocal(task.dueAt))
    setEditAssignee(task.assigneeUserId)
  }
  const saveEdit = async () => {
    if (!companyId || !editing || !editTitle.trim()) return
    const task = editing
    const scope = capture()
    await run(async () => {
      await crmWorkApi.updateTask(companyId, task, {
        title: editTitle.trim(),
        details: editDetails.trim() || null,
        priority: editPriority,
        dueAt: asInstant(editDate),
        assigneeUserId: editAssignee,
        contactId: task.contactId,
        accountId: task.accountId,
      })
      if (isCurrent(scope)) setEditing(null)
    })
  }
  const loadMore = async () => {
    if (!companyId || !next || busy) return
    const scope = capture()
    setBusy(true)
    try {
      const page = await crmWorkApi.tasks(companyId, next, filters)
      if (isCurrent(scope)) {
        setTasks((old) => [...old, ...page.items])
        setNext(page.nextCursor)
      }
    } catch (err) {
      if (isCurrent(scope)) setError(apiErrorMessage(err, 'Unable to load more tasks'))
    } finally {
      if (isCurrent(scope)) setBusy(false)
    }
  }

  return (
    <section className="flex h-[calc(100dvh-72px-2*var(--app-page-gutter-y,1.5rem))] min-h-[480px] w-full flex-col gap-4 px-4 py-6 sm:px-6 lg:px-8">
      <header className="flex shrink-0 flex-wrap items-center justify-between gap-4">
        <div>
          <p className="eyebrow">CRM</p>
          <h1 className="mt-2 text-3xl font-bold">Tasks</h1>
          <p className="mt-2 text-sm text-slate-500">
            Plan your work, move tasks through your workflow and keep every change in view.
          </p>
        </div>
        <div className="flex flex-wrap gap-2">
          {can(PERMISSIONS.CRM_TASK_WORKFLOW_MODIFY) && (
            <button
              type="button"
              className="secondary-button"
              disabled={busy || moving || !columns.length}
              aria-expanded={manageSteps}
              onClick={() => setManageSteps((v) => !v)}
            >
              Manage steps
            </button>
          )}
          <button
            type="button"
            className="secondary-button gap-2"
            disabled={loading || busy || moving}
            onClick={() => void refresh()}
          >
            <FiRefreshCw />
            Refresh
          </button>
          {canCreate && (
            <button
              type="button"
              className="primary-button gap-2"
              disabled={busy || moving}
              aria-expanded={showCreate}
              onClick={() => {
                setCreateColumn('OPEN')
                setShowCreate((value) => !value)
              }}
            >
              <FiPlus />
              New task
            </button>
          )}
        </div>
      </header>
      {error && !showCreate && !editing && !assigning && !manageSteps && (
        <p role="alert" className="rounded-lg bg-rose-50 p-3 text-sm text-rose-700">
          {error}
        </p>
      )}
      {manageSteps && companyId && can(PERMISSIONS.CRM_TASK_WORKFLOW_MODIFY) && (
        <WorkspaceDialog
          title="Manage task steps"
          wide
          busy={busy}
          error={error}
          onClose={() => setManageSteps(false)}
        >
          <TaskSteps
            key={columns.map((c) => c.key + ':' + c.version).join(',')}
            columns={columns}
            busy={busy}
            onCreate={(name, group) =>
              configure(() => crmWorkApi.createTaskColumn(companyId, name, group))
            }
            onRename={(column, name) =>
              void configure(() => crmWorkApi.renameTaskColumn(companyId, column, name))
            }
            onReorder={(steps) =>
              void configure(() => crmWorkApi.reorderTaskColumns(companyId, steps))
            }
            onRemove={(column) =>
              void configure(() => crmWorkApi.removeTaskColumn(companyId, column))
            }
          />
        </WorkspaceDialog>
      )}
      {showCreate && canCreate && (
        <WorkspaceDialog
          title="New task"
          busy={busy}
          error={error}
          onClose={() => setShowCreate(false)}
        >
          <form
            className="space-y-4"
            onSubmit={(event) => {
              event.preventDefault()
              void create()
            }}
          >
            <label className="text-sm">
              Step
              <select
                aria-label="New task step"
                className="form-input mt-1 block w-full"
                value={createColumn}
                onChange={(e) => setCreateColumn(e.target.value)}
              >
                {columns.map((c) => (
                  <option key={c.key} value={c.key}>
                    {c.name}
                  </option>
                ))}
              </select>
            </label>
            <div className="grid gap-3 sm:grid-cols-2">
              <label className="text-sm">
                Title
                <input
                  className="form-input mt-1 w-full"
                  value={title}
                  maxLength={240}
                  required
                  placeholder="Follow up with customer"
                  onChange={(event) => setTitle(event.target.value)}
                />
              </label>
              <label className="text-sm">
                Due date and time
                <input
                  className="form-input mt-1 w-full"
                  type="datetime-local"
                  value={dueDate}
                  onChange={(event) => setDueDate(event.target.value)}
                />
              </label>
              <label className="text-sm">
                Priority
                <select
                  className="form-input mt-1 w-full"
                  value={priority}
                  onChange={(event) => setPriority(event.target.value as CrmTask['priority'])}
                >
                  {priorities.map((item) => (
                    <option key={item} value={item}>
                      {label(item)}
                    </option>
                  ))}
                </select>
              </label>
              <label className="text-sm">
                Task type
                <select
                  aria-label="New task type"
                  className="form-input mt-1 w-full"
                  value={taskType}
                  onChange={(event) => setTaskType(event.target.value)}
                >
                  {taskTypes.map((item) => (
                    <option key={item.key} value={item.key}>
                      {item.icon} {item.label}
                    </option>
                  ))}
                </select>
              </label>
              {can(PERMISSIONS.USER_READ) && (
                <label className="text-sm">
                  Assign to
                  <select
                    className="form-input mt-1 w-full"
                    value={assignee}
                    onChange={(event) => setAssignee(event.target.value)}
                  >
                    <option value="">Myself</option>
                    {members.map((member) => (
                      <option key={member.userId} value={member.userId}>
                        {member.name}
                      </option>
                    ))}
                  </select>
                </label>
              )}
              {can(PERMISSIONS.CONTACT_READ) && (
                <label className="text-sm">
                  Connected lead
                  <select
                    className="form-input mt-1 w-full"
                    value={contactId}
                    onChange={(event) => setContactId(event.target.value)}
                  >
                    <option value="">No lead</option>
                    {leads.map((l) => (
                      <option key={l.id} value={l.id}>
                        {l.name}
                      </option>
                    ))}
                  </select>
                </label>
              )}
              {can(PERMISSIONS.CRM_ACCOUNT_READ) && (
                <label className="text-sm">
                  Customer company
                  <select
                    className="form-input mt-1 w-full"
                    value={accountId}
                    onChange={(event) => setAccountId(event.target.value)}
                  >
                    <option value="">No company</option>
                    {accounts.map((account) => (
                      <option key={account.id} value={account.id}>
                        {account.name}
                      </option>
                    ))}
                  </select>
                </label>
              )}
              {can(PERMISSIONS.CRM_DEAL_READ) && (
                <label className="text-sm">
                  Connected deal
                  <select
                    className="form-input mt-1 w-full"
                    value={dealId}
                    onChange={(event) => setDealId(event.target.value)}
                  >
                    <option value="">No deal</option>
                    {deals.map((deal) => (
                      <option key={deal.id} value={deal.id}>
                        {deal.title}
                      </option>
                    ))}
                  </select>
                </label>
              )}
              <label className="text-sm">
                Estimated time (minutes)
                <input
                  type="number"
                  min="0"
                  step="15"
                  placeholder="e.g. 120"
                  className="form-input mt-1 w-full"
                  value={estimatedMinutes}
                  onChange={(event) => setEstimatedMinutes(event.target.value)}
                />
              </label>
              <label className="text-sm sm:col-span-2">
                Details
                <textarea
                  rows={2}
                  className="form-input mt-1 w-full"
                  maxLength={4000}
                  value={details}
                  onChange={(event) => setDetails(event.target.value)}
                />
              </label>
            </div>
            <div className="flex justify-end gap-2 border-t border-slate-200 pt-4">
              <button
                type="button"
                className="secondary-button"
                disabled={busy}
                onClick={() => setShowCreate(false)}
              >
                Cancel
              </button>
              <button className="primary-button" type="submit" disabled={busy || !title.trim()}>
                {busy ? 'Saving...' : 'Create task'}
              </button>
            </div>
          </form>
        </WorkspaceDialog>
      )}
      <div className="flex shrink-0 flex-wrap items-center gap-3">
        <BoardViewToggle value={view} onChange={setView} />
        <span className="text-xs text-slate-500">
          {tasks.length} loaded{next ? ' ? load more below' : ''}
        </span>
        <FilterPopover
          className="ml-auto"
          disabled={moving}
          activeCount={Number(!!statusFilter) + Number(!!assigneeFilter)}
          onReset={() => {
            setStatusFilter('')
            setAssigneeFilter('')
          }}
        >
          {' '}
          <label className="text-sm font-medium">
            Status
            <select
              className="form-input ml-2"
              aria-label="Filter task status"
              disabled={moving}
              value={statusFilter}
              onChange={(event) => setStatusFilter(event.target.value as CrmTask['status'] | '')}
            >
              <option value="">All statuses</option>
              {statuses.map((status) => (
                <option key={status} value={status}>
                  {label(status)}
                </option>
              ))}
            </select>
          </label>
          {can(PERMISSIONS.USER_READ) && (
            <label className="text-sm font-medium">
              Assignee
              <select
                className="form-input ml-2"
                aria-label="Filter by assignee"
                disabled={moving}
                value={assigneeFilter}
                onChange={(event) => setAssigneeFilter(event.target.value)}
              >
                <option value="">Everyone</option>
                {members.map((member) => (
                  <option key={member.userId} value={member.userId}>
                    {member.name}
                  </option>
                ))}
              </select>
            </label>
          )}
          <p className="text-xs text-slate-500">Filters apply to both Board and List.</p>
        </FilterPopover>
      </div>
      <div className="min-h-0 flex-1 overflow-auto">
        {loading ? (
          view === 'board' ? (
            <BoardSkeleton columns={columns.length || 4} />
          ) : (
            <TableSkeleton rows={5} columns={4} />
          )
        ) : view === 'board' && columns.length > 0 ? (
          <div className="h-full min-h-0 min-w-0">
            <TaskBoard
              tasks={tasks}
              columns={columns}
              busy={busy}
              pendingIds={pendingIds}
              canMove={canMoveTask}
              assigneeName={assigneeName}
              onMove={moveTask}
              onOpen={openTask}
              onEdit={startEdit}
              onAssign={can(PERMISSIONS.USER_READ) ? setAssigning : undefined}
              onNew={(key) => {
                setCreateColumn(key)
                setShowCreate(true)
              }}
            />
          </div>
        ) : tasks.length === 0 ? (
          <div className="rounded-xl border border-dashed p-8 text-center text-sm text-slate-500">
            No tasks match this filter.
          </div>
        ) : (
          <div className="panel overflow-hidden">
            {tasks.map((task) => {
              const typeInfo = taskTypeConfig(task.taskType)
              return (
                <div
                  key={task.id}
                  onClick={() => openTask(task)}
                  className="group flex flex-wrap items-center justify-between gap-4 border-b border-slate-100 p-4 transition-colors hover:bg-slate-50/80 dark:border-slate-800 dark:hover:bg-slate-800/40 cursor-pointer"
                >
                  <div className="min-w-0 flex-1">
                    <div className="flex flex-wrap items-center gap-2">
                      <span
                        className={`rounded px-1.5 py-0.5 text-[10px] font-semibold border ${typeInfo.color}`}
                      >
                        <span className="mr-1">{typeInfo.icon}</span>
                        {typeInfo.label}
                      </span>
                      <p className="font-semibold text-slate-900 group-hover:text-indigo-600 dark:text-slate-100 dark:group-hover:text-indigo-400 transition-colors">
                        {task.title}
                      </p>
                    </div>
                    {task.details && (
                      <p className="mt-1 line-clamp-1 text-xs text-slate-500 dark:text-slate-400">
                        {task.details}
                      </p>
                    )}
                    <div className="mt-2 flex flex-wrap items-center gap-3 text-xs text-slate-500 dark:text-slate-400">
                      <span className="flex items-center gap-1 font-medium">
                        <FiUser className="text-slate-400" />
                        {assigneeName(task)}
                      </span>
                      <span className="text-slate-300 dark:text-slate-700">·</span>
                      <span>
                        {task.dueAt
                          ? 'Due ' + new Date(task.dueAt).toLocaleDateString()
                          : 'No due date'}
                      </span>
                      <span className="text-slate-300 dark:text-slate-700">·</span>
                      <span
                        className={`rounded px-1.5 py-0.5 text-[10px] font-bold ${task.priority === 'HIGH' ? 'bg-rose-50 text-rose-600 dark:bg-rose-950/50 dark:text-rose-400' : task.priority === 'LOW' ? 'bg-slate-100 text-slate-500 dark:bg-slate-800 dark:text-slate-400' : 'bg-indigo-50 text-indigo-600 dark:bg-indigo-950/50 dark:text-indigo-400'}`}
                      >
                        {label(task.priority)} priority
                      </span>
                      {task.contactName && (
                        <span className="inline-flex items-center gap-1 rounded bg-sky-50 px-2 py-0.5 text-[11px] font-medium text-sky-700 dark:bg-sky-950/40 dark:text-sky-300">
                          <span>👤</span>
                          {task.contactName}
                        </span>
                      )}
                      {task.accountName && (
                        <span className="inline-flex items-center gap-1 rounded bg-violet-50 px-2 py-0.5 text-[11px] font-medium text-violet-700 dark:bg-violet-950/40 dark:text-violet-300">
                          <span>🏢</span>
                          {task.accountName}
                        </span>
                      )}
                    </div>
                  </div>
                  <div
                    className="flex items-center gap-2 shrink-0"
                    onClick={(e) => e.stopPropagation()}
                  >
                    {can(PERMISSIONS.CRM_TASK_MODIFY) ? (
                      <select
                        className={`form-input text-xs font-semibold ${taskStatusClass(task.status)}`}
                        aria-label={'Status for ' + task.title}
                        value={task.columnKey || task.status}
                        disabled={busy || pendingIds.has(task.id)}
                        onChange={(event) => void moveTask(task, event.target.value)}
                      >
                        {columns.map((column) => (
                          <option key={column.key} value={column.key}>
                            {column.name}
                          </option>
                        ))}
                      </select>
                    ) : (
                      <span
                        className={`rounded-full px-3 py-1 text-xs font-semibold ${taskStatusClass(task.status)}`}
                      >
                        {stepName(task)}
                      </span>
                    )}
                    <button
                      className="secondary-button gap-1.5 text-xs"
                      type="button"
                      aria-label={`View details for ${task.title}`}
                      onClick={() => openTask(task)}
                    >
                      <FiArrowRight />
                      Details
                    </button>
                  </div>
                </div>
              )
            })}
          </div>
        )}
      </div>
      {assigning && (
        <AssigneeDialog
          title="Assign task"
          currentId={assigning.assigneeUserId}
          currentName={assigneeName(assigning)}
          people={members.map((m) => ({ id: m.userId, name: m.name }))}
          busy={busy}
          error={error}
          onClose={() => setAssigning(null)}
          onSave={(id) => {
            if (!companyId || !id) return
            const task = assigning,
              scope = capture()
            void run(async () => {
              await crmWorkApi.updateTask(companyId, task, {
                title: task.title,
                details: task.details,
                priority: task.priority,
                dueAt: task.dueAt,
                assigneeUserId: id,
                contactId: task.contactId,
                accountId: task.accountId,
              })
              if (isCurrent(scope)) setAssigning(null)
            })
          }}
        />
      )}
      {editing && (
        <WorkspaceDialog
          title="Edit task"
          busy={busy}
          error={error}
          onClose={() => setEditing(null)}
        >
          <form
            className="grid gap-4 sm:grid-cols-2"
            onSubmit={(event) => {
              event.preventDefault()
              void saveEdit()
            }}
          >
            <label className="text-sm">
              Title
              <input
                className="form-input mt-1 w-full"
                maxLength={240}
                required
                value={editTitle}
                onChange={(event) => setEditTitle(event.target.value)}
              />
            </label>
            <label className="text-sm">
              Due
              <input
                className="form-input mt-1 w-full"
                type="datetime-local"
                value={editDate}
                onChange={(event) => setEditDate(event.target.value)}
              />
            </label>
            <label className="text-sm">
              Priority
              <select
                className="form-input mt-1 w-full"
                value={editPriority}
                onChange={(event) => setEditPriority(event.target.value as CrmTask['priority'])}
              >
                {priorities.map((item) => (
                  <option key={item} value={item}>
                    {label(item)}
                  </option>
                ))}
              </select>
            </label>
            {can(PERMISSIONS.USER_READ) && (
              <label className="text-sm">
                Assignee
                <select
                  className="form-input mt-1 w-full"
                  value={editAssignee}
                  onChange={(event) => setEditAssignee(event.target.value)}
                >
                  {!members.some((person) => person.userId === editAssignee) && (
                    <option value={editAssignee}>Current assignee</option>
                  )}
                  {members.map((person) => (
                    <option key={person.userId} value={person.userId}>
                      {person.name}
                    </option>
                  ))}
                </select>
              </label>
            )}
            <label className="text-sm sm:col-span-2">
              Details
              <textarea
                className="form-input mt-1 w-full"
                value={editDetails}
                maxLength={4000}
                rows={3}
                onChange={(event) => setEditDetails(event.target.value)}
              />
            </label>
            <div className="flex gap-2 sm:col-span-2">
              <button className="primary-button" type="submit" disabled={busy || !editTitle.trim()}>
                Save task
              </button>
              <button
                className="secondary-button"
                type="button"
                disabled={busy}
                onClick={() => setEditing(null)}
              >
                Cancel
              </button>
            </div>
          </form>
        </WorkspaceDialog>
      )}
      {next && (
        <button
          type="button"
          className="secondary-button"
          disabled={busy || moving}
          onClick={() => void loadMore()}
        >
          Load more
        </button>
      )}
    </section>
  )
}
