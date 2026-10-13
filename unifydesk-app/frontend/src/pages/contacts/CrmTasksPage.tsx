import AssigneeDialog from '../../components/ui/AssigneeDialog'
import BoardViewToggle from '../../components/ui/BoardViewToggle'
import { BoardSkeleton, TableSkeleton } from '../../components/ui/Skeleton'
import WorkspaceDialog from '../../components/ui/WorkspaceDialog'
import FilterPopover from '../../components/ui/FilterPopover'
import { useCallback, useEffect, useRef, useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import { FiClock, FiPlus, FiRefreshCw, FiUser } from 'react-icons/fi'
import TaskBoard from '../../components/contacts/TaskBoard'
import TaskSteps from '../../components/contacts/TaskSteps'
import TaskHistoryPanel from '../../components/contacts/TaskHistoryPanel'
import { taskStatusClass } from '../../features/contacts/taskPresentation'
import { companyApi } from '../../features/company/companyApi'
import { crmWorkApi, type CrmTaskColumn, type CrmTask } from '../../features/contacts/crmWorkApi'
import { PERMISSIONS } from '../../features/iam/permissions'
import { useIam } from '../../features/iam/useIam'
import { useWorkspaceEpochScope } from '../../features/workspace/useWorkspaceEpochScope'
import { apiErrorMessage } from '../../lib/api/apiClient'
import { useAppSelector } from '../../store/hooks'
import { selectSelectedCompanyId, selectWorkspaceEpoch } from '../../store/workspace/workspaceSlice'

const statuses: CrmTask['status'][] = ['OPEN', 'IN_PROGRESS', 'DONE', 'CANCELLED']
const priorities: CrmTask['priority'][] = ['LOW', 'NORMAL', 'HIGH']
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
  const { capture, isCurrent } = useWorkspaceEpochScope(companyId, epoch)
  const { can } = useIam()
  const [view, setView] = useState<'board' | 'list'>('board')
  const [columns, setColumns] = useState<CrmTaskColumn[]>([])
  const [manageSteps, setManageSteps] = useState(false)
  const [createColumn, setCreateColumn] = useState('OPEN')
  const [tasks, setTasks] = useState<CrmTask[]>([])
  const [next, setNext] = useState<string | null>(null)
  const [members, setMembers] = useState<Array<{ userId: string; name: string }>>([])
  const [accounts, setAccounts] = useState<Array<{ id: string; name: string }>>([])
  const [statusFilter, setStatusFilter] = useState<CrmTask['status'] | ''>('')
  const [assigneeFilter, setAssigneeFilter] = useState('')
  const [title, setTitle] = useState('')
  const [details, setDetails] = useState('')
  const [dueDate, setDueDate] = useState('')
  const [priority, setPriority] = useState<CrmTask['priority']>('NORMAL')
  const [assignee, setAssignee] = useState('')
  const [accountId, setAccountId] = useState('')
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
  const [detail, setDetail] = useState<{ scope: string; taskId: string } | null>(null)
  const scopeKey = `${companyId}:${epoch}`
  const [params, setParams] = useSearchParams()
  const [linkedTask, setLinkedTask] = useState<CrmTask | null>(null)
  const selectedTask =
    detail?.scope === scopeKey
      ? tasks.find((task) => task.id === detail.taskId) ||
        (linkedTask?.id === detail.taskId ? linkedTask : null)
      : null
  useEffect(() => {
    const id = params.get('taskId')
    if (!companyId || !id) return
    let active = true
    const scope = capture()
    const found = tasks.find((task) => task.id === id)
    void (found ? Promise.resolve(found) : crmWorkApi.task(companyId, id))
      .then((task) => {
        if (active && isCurrent(scope)) {
          setLinkedTask(task)
          setDetail({ scope: scopeKey, taskId: id })
        }
      })
      .catch((error) => {
        if (active && isCurrent(scope)) setError(apiErrorMessage(error, 'Unable to open task'))
      })
    return () => {
      active = false
    }
  }, [companyId, params, tasks, scopeKey, capture, isCurrent])
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
    setLinkedTask((old) => (old?.id === task.id ? optimistic : old))
    try {
      const saved = await crmWorkApi.taskColumn(companyId, task, key)
      if (!isCurrent(scope)) return
      setTasks((old) =>
        statusFilter && saved.status !== statusFilter
          ? old.filter((t) => t.id !== task.id)
          : old.map((t) => (t.id === task.id ? saved : t)),
      )
      setLinkedTask((old) => (old?.id === task.id ? saved : old))
    } catch (err) {
      if (!isCurrent(scope)) return
      setTasks((old) => old.map((t) => (t.id === task.id ? task : t)))
      setLinkedTask((old) => (old?.id === task.id ? task : old))
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
    setDetail({ scope: scopeKey, taskId: task.id })
    setParams({ taskId: task.id })
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
        assigneeUserId: assignee || undefined,
        accountId: accountId || undefined,
      })
      if (!isCurrent(scope)) return
      setTitle('')
      setDetails('')
      setDueDate('')
      setPriority('NORMAL')
      setAssignee('')
      setAccountId('')
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
          {can(PERMISSIONS.CRM_TASK_MODIFY) && (
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
      {showCreate && can(PERMISSIONS.CRM_TASK_MODIFY) && (
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
          <div
            className={
              selectedTask
                ? 'grid h-full min-h-0 grid-rows-[minmax(0,1fr)_minmax(0,1fr)] gap-5 xl:grid-cols-[minmax(0,1fr)_380px] xl:grid-rows-[minmax(0,1fr)]'
                : 'h-full min-h-0 min-w-0'
            }
          >
            <div className="h-full min-h-0 min-w-0">
              <TaskBoard
                tasks={tasks}
                columns={columns}
                busy={busy}
                pendingIds={pendingIds}
                canMove={can(PERMISSIONS.CRM_TASK_MODIFY)}
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
            {selectedTask && companyId && (
              <div className="min-h-0 overflow-y-auto">
                <TaskHistoryPanel
                  key={scopeKey + ':' + selectedTask.id}
                  companyId={companyId}
                  task={selectedTask}
                  stepName={stepName(selectedTask)}
                  assigneeName={assigneeName(selectedTask)}
                  onClose={() => {
                    setDetail(null)
                    setParams({})
                  }}
                />
              </div>
            )}
          </div>
        ) : tasks.length === 0 ? (
          <div className="rounded-xl border border-dashed p-8 text-center text-sm text-slate-500">
            No tasks match this filter.
          </div>
        ) : (
          <div
            className={`grid items-start gap-5 ${selectedTask ? 'xl:grid-cols-[minmax(0,1fr)_380px]' : ''}`}
          >
            <div className="panel overflow-hidden">
              {tasks.map((task) => (
                <div
                  key={task.id}
                  className={`border-b border-slate-100 p-5 ${selectedTask?.id === task.id ? 'bg-indigo-50' : ''}`}
                >
                  <div className="flex flex-wrap items-center justify-between gap-3">
                    <div>
                      <p className="font-semibold text-slate-900">{task.title}</p>
                      <p className="text-xs text-slate-500">
                        {task.dueAt
                          ? 'Due ' + new Date(task.dueAt).toLocaleString()
                          : 'No due date'}{' '}
                        · {label(task.priority)}
                      </p>
                      <p className="mt-2 flex items-center gap-2 text-xs text-slate-500">
                        <FiUser />
                        {assigneeName(task)}
                        {task.priority === 'HIGH' && (
                          <span className="rounded-full bg-amber-50 px-2 py-0.5 font-semibold text-amber-700">
                            High priority
                          </span>
                        )}
                      </p>
                      <div className="mt-1 flex gap-3 text-xs">
                        {task.contactId && (
                          <Link to={'/contacts/' + task.contactId} className="text-indigo-600">
                            Contact
                          </Link>
                        )}
                        {task.accountId && (
                          <Link to={'/crm/companies/' + task.accountId} className="text-indigo-600">
                            Company
                          </Link>
                        )}
                      </div>
                    </div>
                    <div className="flex items-center gap-2">
                      {can(PERMISSIONS.CRM_TASK_MODIFY) ? (
                        <>
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
                          <button
                            className="secondary-button"
                            type="button"
                            disabled={busy || moving}
                            onClick={() => startEdit(task)}
                          >
                            Edit
                          </button>
                        </>
                      ) : (
                        <span
                          className={`rounded-full px-3 py-1 text-xs font-semibold ${taskStatusClass(task.status)}`}
                        >
                          {stepName(task)}
                        </span>
                      )}
                      <button
                        className="secondary-button gap-2"
                        type="button"
                        aria-expanded={selectedTask?.id === task.id}
                        aria-label={`View activity history for ${task.title}`}
                        onClick={() => {
                          setDetail({ scope: scopeKey, taskId: task.id })
                          setParams({ taskId: task.id })
                        }}
                      >
                        <FiClock />
                        View history
                      </button>
                    </div>
                  </div>
                </div>
              ))}
            </div>
            {selectedTask && companyId && (
              <TaskHistoryPanel
                key={`${scopeKey}:${selectedTask.id}`}
                companyId={companyId}
                stepName={stepName(selectedTask)}
                task={selectedTask}
                assigneeName={assigneeName(selectedTask)}
                onClose={() => {
                  setDetail(null)
                  setParams({})
                }}
              />
            )}
          </div>
        )}
        {(loading || (view === 'list' && tasks.length === 0)) && selectedTask && companyId && (
          <TaskHistoryPanel
            key={`${scopeKey}:${selectedTask.id}`}
            companyId={companyId}
            task={selectedTask}
            assigneeName={assigneeName(selectedTask)}
            onClose={() => {
              setDetail(null)
              setParams({})
            }}
          />
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
