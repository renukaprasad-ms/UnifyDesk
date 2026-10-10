import { useCallback, useEffect, useState } from 'react'
import { crmWorkApi, type CrmTask } from '../../features/contacts/crmWorkApi'
import { PERMISSIONS } from '../../features/iam/permissions'
import { useIam } from '../../features/iam/useIam'
import { useWorkspaceEpochScope } from '../../features/workspace/useWorkspaceEpochScope'
import { apiErrorMessage } from '../../lib/api/apiClient'
import { useAppSelector } from '../../store/hooks'
import { selectSelectedCompanyId, selectWorkspaceEpoch } from '../../store/workspace/workspaceSlice'

const statuses: CrmTask['status'][] = ['OPEN', 'IN_PROGRESS', 'DONE', 'CANCELLED']
export default function CrmTasksPage() {
  const companyId = useAppSelector(selectSelectedCompanyId)
  const epoch = useAppSelector(selectWorkspaceEpoch)
  const { capture, isCurrent } = useWorkspaceEpochScope(companyId, epoch)
  const { can } = useIam()
  const [tasks, setTasks] = useState<CrmTask[]>([])
  const [next, setNext] = useState<string | null>(null)
  const [title, setTitle] = useState('')
  const [dueDate, setDueDate] = useState('')
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  const refresh = useCallback(async () => {
    if (!companyId) return
    const token = capture()
    setLoading(true)
    try {
      const page = await crmWorkApi.tasks(companyId)
      if (isCurrent(token)) { setTasks(page.items); setNext(page.nextCursor); setError('') }
    } catch (err) { if (isCurrent(token)) setError(apiErrorMessage(err, 'Unable to load tasks')) }
    finally { if (isCurrent(token)) setLoading(false) }
  }, [companyId, capture, isCurrent])
  useEffect(() => { setTasks([]); setNext(null); void refresh() }, [refresh, epoch])

  const create = async () => {
    if (!companyId || !title.trim() || busy) return
    const token = capture()
    setBusy(true)
    try {
      await crmWorkApi.addTask(companyId, title.trim(), dueDate ? new Date(dueDate + 'T12:00:00').toISOString() : undefined)
      if (isCurrent(token)) { setTitle(''); setDueDate(''); await refresh() }
    } catch (err) { if (isCurrent(token)) setError(apiErrorMessage(err, 'Unable to create task')) }
    finally { if (isCurrent(token)) setBusy(false) }
  }

  const update = async (task: CrmTask, status: CrmTask['status']) => {
    if (!companyId || busy) return
    const token = capture()
    setBusy(true)
    try {
      const updated = await crmWorkApi.taskStatus(companyId, task, status)
      if (isCurrent(token)) setTasks((old) => old.map((current) => current.id === updated.id ? updated : current))
    } catch (err) {
      if (isCurrent(token)) { setError(apiErrorMessage(err, 'Unable to update task')); void refresh() }
    } finally { if (isCurrent(token)) setBusy(false) }
  }

  const more = async () => {
    if (!companyId || !next || busy) return
    const token = capture()
    setBusy(true)
    try {
      const page = await crmWorkApi.tasks(companyId, next)
      if (isCurrent(token)) { setTasks((old) => [...old, ...page.items]); setNext(page.nextCursor) }
    } catch (err) { if (isCurrent(token)) setError(apiErrorMessage(err, 'Unable to load more tasks')) }
    finally { if (isCurrent(token)) setBusy(false) }
  }

  return <section className="w-full space-y-5 px-4 py-6 sm:px-6">
    <header><p className="eyebrow">CRM</p><h1 className="mt-2 text-3xl font-bold">Tasks</h1>
      <p className="mt-2 text-sm text-slate-500">Follow-ups and internal work for your customer relationships.</p></header>
    {error && <p role="alert" className="rounded-lg bg-rose-50 p-3 text-sm text-rose-700">{error}</p>}
    {can(PERMISSIONS.CONTACT_MODIFY) && <form className="flex flex-wrap gap-2" onSubmit={(event) => { event.preventDefault(); void create() }}>
      <label htmlFor="crm-task-title" className="sr-only">Task title</label>
      <input id="crm-task-title" className="form-input min-w-52 flex-1" placeholder="New follow-up" value={title} maxLength={240} onChange={(event) => setTitle(event.target.value)} />
      <label htmlFor="crm-task-due" className="sr-only">Due date</label>
      <input id="crm-task-due" className="form-input" type="date" value={dueDate} onChange={(event) => setDueDate(event.target.value)} />
      <button type="submit" className="primary-button" disabled={busy || !title.trim()}>Create task</button>
    </form>}
    {loading ? <p role="status">Loading tasks…</p> : tasks.length === 0 ? (
      <div className="rounded-xl border border-dashed p-8 text-center text-sm text-slate-500">No tasks yet. Add a follow-up to get started.</div>
    ) : <div className="overflow-hidden rounded-xl border bg-white">{tasks.map((task) => <div key={task.id}
      className="flex flex-wrap items-center justify-between gap-3 border-b border-slate-100 px-4 py-3">
      <div><p className="font-semibold text-slate-900">{task.title}</p><p className="text-xs text-slate-500">
        {task.dueAt ? 'Due ' + new Date(task.dueAt).toLocaleDateString() : 'No due date'} · {task.priority}
      </p></div>
      {can(PERMISSIONS.CONTACT_MODIFY) ? <select className="form-input" aria-label={'Status for ' + task.title}
        value={task.status} disabled={busy} onChange={(event) => void update(task, event.target.value as CrmTask['status'])}>
        {statuses.map((status) => <option key={status} value={status}>{status.replaceAll('_', ' ')}</option>)}
      </select> : <span className="text-sm">{task.status.replaceAll('_', ' ')}</span>}
    </div>)}</div>}
    {next && <button type="button" className="secondary-button" disabled={busy} onClick={() => void more()}>Load more</button>}
  </section>
}
