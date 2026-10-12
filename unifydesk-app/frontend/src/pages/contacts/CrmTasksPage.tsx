import { useCallback, useEffect, useRef, useState } from 'react'
import { Link } from 'react-router'
import { companyApi } from '../../features/company/companyApi'
import { crmWorkApi, type CrmTask } from '../../features/contacts/crmWorkApi'
import { PERMISSIONS } from '../../features/iam/permissions'
import { useIam } from '../../features/iam/useIam'
import { useWorkspaceEpochScope } from '../../features/workspace/useWorkspaceEpochScope'
import { apiErrorMessage } from '../../lib/api/apiClient'
import { useAppSelector } from '../../store/hooks'
import { selectSelectedCompanyId, selectWorkspaceEpoch } from '../../store/workspace/workspaceSlice'

const statuses: CrmTask['status'][] = ['OPEN', 'IN_PROGRESS', 'DONE', 'CANCELLED']
const priorities: CrmTask['priority'][] = ['LOW', 'NORMAL', 'HIGH']
const label = (value: string) => value.replaceAll('_', ' ').toLowerCase().replace(/^./, (char) => char.toUpperCase())
const toLocal = (value: string | null) => {
  if (!value) return ''
  const date = new Date(value)
  return new Date(date.getTime() - date.getTimezoneOffset() * 60000).toISOString().slice(0, 16)
}
const asInstant = (value: string) => value ? new Date(value).toISOString() : null

export default function CrmTasksPage() {
  const companyId = useAppSelector(selectSelectedCompanyId)
  const epoch = useAppSelector(selectWorkspaceEpoch)
  const { capture, isCurrent } = useWorkspaceEpochScope(companyId, epoch)
  const { can } = useIam()
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
  const [editing, setEditing] = useState<CrmTask | null>(null)
  const [editTitle, setEditTitle] = useState('')
  const [editDetails, setEditDetails] = useState('')
  const [editDate, setEditDate] = useState('')
  const [editPriority, setEditPriority] = useState<CrmTask['priority']>('NORMAL')
  const [editAssignee, setEditAssignee] = useState('')
  const requestNumber = useRef(0)
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

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
      const page = await crmWorkApi.tasks(companyId, undefined, {
        status: statusFilter || undefined,
        assigneeUserId: assigneeFilter || undefined,
      })
      if (isCurrent(token) && request === requestNumber.current) { setTasks(page.items); setNext(page.nextCursor); setError('') }
    } catch (err) {
      if (isCurrent(token) && request === requestNumber.current) setError(apiErrorMessage(err, 'Unable to load tasks'))
    } finally { if (isCurrent(token) && request === requestNumber.current) setLoading(false) }
  }, [companyId, statusFilter, assigneeFilter, capture, isCurrent])

  useEffect(() => { setTasks([]); setNext(null); void refresh() }, [refresh, epoch])
  useEffect(() => {
    if (!companyId) return
    const scope = capture()
    let active = true
    if (can(PERMISSIONS.USER_READ)) {
      void companyApi.users(companyId).then((users) => {
        if (active && isCurrent(scope)) setMembers(users.filter((user) => user.status === 'ACTIVE')
          .map((user) => ({ userId: user.userId, name: user.name || user.email })))
      }).catch(() => { /* Self-assignment continues without directory access. */ })
    }
    if (can(PERMISSIONS.CRM_ACCOUNT_READ)) {
      void crmWorkApi.accounts(companyId).then((page) => {
        if (active && isCurrent(scope)) setAccounts(page.items)
      }).catch(() => { /* Unlinked tasks remain available. */ })
    }
    return () => { active = false }
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
        setError(apiErrorMessage(err, 'Unable to update task; latest data reloaded.'))
        await refresh()
      }
    } finally { if (isCurrent(token)) setBusy(false) }
  }

  const create = async () => {
    if (!companyId || !title.trim()) return
    await run(async () => {
      await crmWorkApi.addTask(companyId, title.trim(), asInstant(dueDate) || undefined, {
        details: details.trim(), priority, assigneeUserId: assignee || undefined,
        accountId: accountId || undefined,
      })
      setTitle('')
      setDetails('')
      setDueDate('')
      setPriority('NORMAL')
      setAssignee('')
      setAccountId('')
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
    await run(async () => {
      await crmWorkApi.updateTask(companyId, task, {
        title: editTitle.trim(), details: editDetails.trim() || null,
        priority: editPriority, dueAt: asInstant(editDate),
        assigneeUserId: editAssignee,
        contactId: task.contactId, accountId: task.accountId,
      })
      setEditing(null)
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
    } finally { if (isCurrent(scope)) setBusy(false) }
  }

  return <section className="w-full space-y-5 px-4 py-6 sm:px-6">
    <header><p className="eyebrow">CRM</p><h1 className="mt-2 text-3xl font-bold">Tasks</h1>
      <p className="mt-2 text-sm text-slate-500">Follow-ups, assignments and due dates for customer relationships.</p></header>
    {error && <p role="alert" className="rounded-lg bg-rose-50 p-3 text-sm text-rose-700">{error}</p>}
    {can(PERMISSIONS.CRM_TASK_MODIFY) && <form className="space-y-3 rounded-xl border border-slate-200 bg-white p-4"
      onSubmit={(event) => { event.preventDefault(); void create() }}>
      <h2 className="font-semibold">New follow-up</h2>
      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
        <label className="text-sm">Title
          <input className="form-input mt-1 w-full" value={title} maxLength={240} required
            placeholder="Follow up with customer" onChange={(event) => setTitle(event.target.value)} />
        </label>
        <label className="text-sm">Due date and time
          <input className="form-input mt-1 w-full" type="datetime-local"
            value={dueDate} onChange={(event) => setDueDate(event.target.value)} />
        </label>
        <label className="text-sm">Priority
          <select className="form-input mt-1 w-full" value={priority} onChange={(event) => setPriority(event.target.value as CrmTask['priority'])}>
            {priorities.map((item) => <option key={item} value={item}>{label(item)}</option>)}
          </select>
        </label>
        {can(PERMISSIONS.USER_READ) && <label className="text-sm">Assign to
          <select className="form-input mt-1 w-full" value={assignee} onChange={(event) => setAssignee(event.target.value)}>
            <option value="">Myself</option>
            {members.map((member) => <option key={member.userId} value={member.userId}>{member.name}</option>)}
          </select>
        </label>}
        {can(PERMISSIONS.CRM_ACCOUNT_READ) && <label className="text-sm">Customer company
          <select className="form-input mt-1 w-full" value={accountId} onChange={(event) => setAccountId(event.target.value)}>
            <option value="">No company</option>
            {accounts.map((account) => <option key={account.id} value={account.id}>{account.name}</option>)}
          </select>
        </label>}
        <label className="text-sm sm:col-span-2">Details
          <textarea rows={2} className="form-input mt-1 w-full" maxLength={4000}
            value={details} onChange={(event) => setDetails(event.target.value)} />
        </label>
      </div>
      <button className="primary-button" type="submit" disabled={busy || !title.trim()}>Create task</button>
    </form>}
    <div className="flex flex-wrap items-center gap-3">
      <label className="text-sm font-medium">Status
        <select className="form-input ml-2" aria-label="Filter task status" value={statusFilter}
          onChange={(event) => setStatusFilter(event.target.value as CrmTask['status'] | '')}>
          <option value="">All statuses</option>
          {statuses.map((status) => <option key={status} value={status}>{label(status)}</option>)}
        </select>
      </label>
      {can(PERMISSIONS.USER_READ) && <label className="text-sm font-medium">Assignee
        <select className="form-input ml-2" aria-label="Filter by assignee" value={assigneeFilter}
          onChange={(event) => setAssigneeFilter(event.target.value)}>
          <option value="">Everyone</option>
          {members.map((member) => <option key={member.userId} value={member.userId}>{member.name}</option>)}
        </select>
      </label>}
    </div>
    {loading ? <p role="status">Loading tasks…</p> : tasks.length === 0 ? (
      <div className="rounded-xl border border-dashed p-8 text-center text-sm text-slate-500">No tasks match this filter.</div>
    ) : <div className="overflow-hidden rounded-xl border bg-white">
      {tasks.map((task) => <div key={task.id} className="border-b border-slate-100 p-4">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div>
            <p className="font-semibold text-slate-900">{task.title}</p>
            <p className="text-xs text-slate-500">
              {task.dueAt ? 'Due ' + new Date(task.dueAt).toLocaleString() : 'No due date'} · {label(task.priority)}
            </p>
            <div className="mt-1 flex gap-3 text-xs">
              {task.contactId && <Link to={'/contacts/' + task.contactId} className="text-indigo-600">Contact</Link>}
              {task.accountId && <Link to={'/crm/companies/' + task.accountId} className="text-indigo-600">Company</Link>}
            </div>
          </div>
          <div className="flex items-center gap-2">
            {can(PERMISSIONS.CRM_TASK_MODIFY) ? <>
              <select className="form-input" aria-label={'Status for ' + task.title}
                value={task.status} disabled={busy}
                onChange={(event) => void run(async () => {
                  await crmWorkApi.taskStatus(companyId!, task, event.target.value as CrmTask['status'])
                })}>
                {statuses.map((status) => <option key={status} value={status}>{label(status)}</option>)}
              </select>
              <button className="secondary-button" type="button" disabled={busy} onClick={() => startEdit(task)}>Edit</button>
            </> : <span className="text-sm">{label(task.status)}</span>}
          </div>
        </div>
        {editing?.id === task.id && <form className="mt-4 grid gap-3 rounded-lg bg-slate-50 p-4 sm:grid-cols-2"
          onSubmit={(event) => { event.preventDefault(); void saveEdit() }}>
          <label className="text-sm">Title
            <input className="form-input mt-1 w-full" maxLength={240} required value={editTitle}
              onChange={(event) => setEditTitle(event.target.value)} />
          </label>
          <label className="text-sm">Due
            <input className="form-input mt-1 w-full" type="datetime-local" value={editDate}
              onChange={(event) => setEditDate(event.target.value)} />
          </label>
          <label className="text-sm">Priority
            <select className="form-input mt-1 w-full" value={editPriority}
              onChange={(event) => setEditPriority(event.target.value as CrmTask['priority'])}>
              {priorities.map((item) => <option key={item} value={item}>{label(item)}</option>)}
            </select>
          </label>
          {can(PERMISSIONS.USER_READ) && <label className="text-sm">Assignee
            <select className="form-input mt-1 w-full" value={editAssignee}
              onChange={(event) => setEditAssignee(event.target.value)}>
              {!members.some((person) => person.userId === editAssignee) &&
                <option value={editAssignee}>Current assignee</option>}
              {members.map((person) => <option key={person.userId} value={person.userId}>{person.name}</option>)}
            </select>
          </label>}
          <label className="text-sm sm:col-span-2">Details
            <textarea className="form-input mt-1 w-full" value={editDetails} maxLength={4000} rows={3}
              onChange={(event) => setEditDetails(event.target.value)} />
          </label>
          <div className="flex gap-2 sm:col-span-2">
            <button className="primary-button" type="submit" disabled={busy || !editTitle.trim()}>Save task</button>
            <button className="secondary-button" type="button" disabled={busy} onClick={() => setEditing(null)}>Cancel</button>
          </div>
        </form>}
      </div>)}
    </div>}
    {next && <button type="button" className="secondary-button" disabled={busy} onClick={() => void loadMore()}>Load more</button>}
  </section>
}
