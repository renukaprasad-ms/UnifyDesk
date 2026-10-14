import { useCallback, useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import {
  FiArrowLeft,
  FiCheck,
  FiClock,
  FiEdit2,
  FiMessageSquare,
  FiRefreshCw,
  FiSend,
  FiTrash2,
  FiX,
} from 'react-icons/fi'
import { companyApi } from '../../features/company/companyApi'
import {
  crmWorkApi,
  type CrmAccount,
  type CrmComment,
  type CrmTask,
  type CrmTaskColumn,
  type CrmTaskEvent,
} from '../../features/contacts/crmWorkApi'
import { crmLeadsApi, type Lead } from '../../features/contacts/crmLeadsApi'
import { PERMISSIONS } from '../../features/iam/permissions'
import { useIam } from '../../features/iam/useIam'
import { useWorkspaceEpochScope } from '../../features/workspace/useWorkspaceEpochScope'
import { apiErrorMessage } from '../../lib/api/apiClient'
import { useAppSelector } from '../../store/hooks'
import { selectSelectedCompanyId, selectWorkspaceEpoch } from '../../store/workspace/workspaceSlice'

export function taskTypeConfig(type?: string) {
  switch (type?.toUpperCase()) {
    case 'CALL':
      return { label: 'Phone Call', icon: '📞', color: 'bg-emerald-50 text-emerald-700 dark:bg-emerald-950/60 dark:text-emerald-300 border-emerald-200' }
    case 'MEETING':
      return { label: 'Meeting', icon: '👥', color: 'bg-blue-50 text-blue-700 dark:bg-blue-950/60 dark:text-blue-300 border-blue-200' }
    case 'EMAIL':
      return { label: 'Email', icon: '✉️', color: 'bg-purple-50 text-purple-700 dark:bg-purple-950/60 dark:text-purple-300 border-purple-200' }
    case 'FOLLOW_UP':
      return { label: 'Follow-up', icon: '📋', color: 'bg-amber-50 text-amber-700 dark:bg-amber-950/60 dark:text-amber-300 border-amber-200' }
    case 'LEAD':
      return { label: 'Lead Task', icon: '🎯', color: 'bg-sky-50 text-sky-700 dark:bg-sky-950/60 dark:text-sky-300 border-sky-200' }
    case 'COMPANY':
    case 'ACCOUNT':
      return { label: 'Company Task', icon: '🏢', color: 'bg-violet-50 text-violet-700 dark:bg-violet-950/60 dark:text-violet-300 border-violet-200' }
    default:
      return { label: 'General To-Do', icon: '📝', color: 'bg-slate-50 text-slate-700 dark:bg-slate-800 dark:text-slate-300 border-slate-200' }
  }
}

export default function CrmTaskDetailPage() {
  const { taskId } = useParams<{ taskId: string }>()
  const navigate = useNavigate()
  const companyId = useAppSelector(selectSelectedCompanyId)
  const epoch = useAppSelector(selectWorkspaceEpoch)
  const { capture, isCurrent } = useWorkspaceEpochScope(companyId, epoch)
  const { can } = useIam()

  const [task, setTask] = useState<CrmTask | null>(null)
  const [columns, setColumns] = useState<CrmTaskColumn[]>([])
  const [members, setMembers] = useState<Array<{ userId: string; name: string }>>([])
  const [accounts, setAccounts] = useState<CrmAccount[]>([])
  const [leads, setLeads] = useState<Lead[]>([])
  const [comments, setComments] = useState<CrmComment[]>([])
  const [events, setEvents] = useState<CrmTaskEvent[]>([])

  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [activeTab, setActiveTab] = useState<'comments' | 'history'>('comments')
  const [newComment, setNewComment] = useState('')
  const [postingComment, setPostingComment] = useState(false)
  const [editingTitle, setEditingTitle] = useState(false)
  const [editTitleVal, setEditTitleVal] = useState('')
  const [editingDetails, setEditingDetails] = useState(false)
  const [editDetailsVal, setEditDetailsVal] = useState('')
  const [showDeleteModal, setShowDeleteModal] = useState(false)

  const canEdit = can(PERMISSIONS.CRM_TASK_MODIFY)

  const loadData = useCallback(async () => {
    if (!companyId || !taskId) return
    const scope = capture()
    setLoading(true)
    setError('')
    try {
      const [fetchedTask, cols, history, comms] = await Promise.all([
        crmWorkApi.task(companyId, taskId),
        crmWorkApi.taskColumns(companyId).catch(() => []),
        crmWorkApi.taskHistory(companyId, taskId).catch(() => []),
        crmWorkApi.taskComments(companyId, taskId).catch(() => []),
      ])

      if (isCurrent(scope)) {
        setTask(fetchedTask)
        setColumns(cols)
        setEvents(history)
        setComments(comms)
        setEditTitleVal(fetchedTask.title)
        setEditDetailsVal(fetchedTask.details || '')
      }

      // Load supporting directories in background
      if (can(PERMISSIONS.USER_READ)) {
        companyApi.users(companyId).then((users) => {
          if (isCurrent(scope)) {
            setMembers(
              users
                .filter((u) => u.status === 'ACTIVE')
                .map((u) => ({ userId: u.userId, name: u.name || u.email })),
            )
          }
        }).catch(() => {})
      }

      if (can(PERMISSIONS.CRM_ACCOUNT_READ)) {
        crmWorkApi.accounts(companyId).then((accs) => {
          if (isCurrent(scope)) setAccounts(accs.items)
        }).catch(() => {})
      }

      if (can(PERMISSIONS.CRM_LEAD_READ)) {
        crmLeadsApi.list(companyId).then((leadPage) => {
          if (isCurrent(scope)) setLeads(leadPage.items)
        }).catch(() => {})
      }
    } catch (err) {
      if (isCurrent(scope)) setError(apiErrorMessage(err, 'Failed to load task details'))
    } finally {
      if (isCurrent(scope)) setLoading(false)
    }
  }, [companyId, taskId, capture, isCurrent, can])

  useEffect(() => {
    void loadData()
  }, [loadData, epoch])

  const handleUpdate = async (patch: Partial<{
    title: string
    details: string | null
    priority: CrmTask['priority']
    dueAt: string | null
    assigneeUserId: string
    contactId: string | null
    accountId: string | null
    taskType: string
  }>) => {
    if (!companyId || !task || busy) return
    setBusy(true)
    try {
      const updated = await crmWorkApi.updateTask(companyId, task, {
        title: patch.title !== undefined ? patch.title : task.title,
        details: patch.details !== undefined ? patch.details : task.details,
        priority: patch.priority !== undefined ? patch.priority : task.priority,
        dueAt: patch.dueAt !== undefined ? patch.dueAt : task.dueAt,
        assigneeUserId: patch.assigneeUserId !== undefined ? patch.assigneeUserId : task.assigneeUserId,
        contactId: patch.contactId !== undefined ? patch.contactId : task.contactId,
        accountId: patch.accountId !== undefined ? patch.accountId : task.accountId,
        taskType: patch.taskType !== undefined ? patch.taskType : task.taskType,
      })
      setTask(updated)
      setEditTitleVal(updated.title)
      setEditDetailsVal(updated.details || '')
      setEditingTitle(false)
      setEditingDetails(false)
      // refresh history
      const history = await crmWorkApi.taskHistory(companyId, task.id).catch(() => [])
      setEvents(history)
    } catch (err) {
      setError(apiErrorMessage(err, 'Failed to update task'))
    } finally {
      setBusy(false)
    }
  }

  const handleMoveStep = async (columnKey: string) => {
    if (!companyId || !task || busy) return
    setBusy(true)
    try {
      const updated = await crmWorkApi.taskColumn(companyId, task, columnKey)
      setTask(updated)
      const history = await crmWorkApi.taskHistory(companyId, task.id).catch(() => [])
      setEvents(history)
    } catch (err) {
      setError(apiErrorMessage(err, 'Failed to move task'))
    } finally {
      setBusy(false)
    }
  }

  const handleDelete = async () => {
    if (!companyId || !task || busy) return
    setBusy(true)
    try {
      await crmWorkApi.deleteTask(companyId, task.id)
      navigate('/crm/tasks')
    } catch (err) {
      setError(apiErrorMessage(err, 'Failed to delete task'))
      setBusy(false)
    }
  }

  const handleAddComment = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!companyId || !task || !newComment.trim() || postingComment) return
    setPostingComment(true)
    try {
      const added = await crmWorkApi.addTaskComment(companyId, task.id, newComment.trim())
      setComments((prev) => [...prev, added])
      setNewComment('')
    } catch (err) {
      setError(apiErrorMessage(err, 'Failed to post comment'))
    } finally {
      setPostingComment(false)
    }
  }

  const handleDeleteComment = async (commentId: string) => {
    if (!companyId || !task) return
    try {
      await crmWorkApi.deleteTaskComment(companyId, task.id, commentId)
      setComments((prev) => prev.filter((c) => c.id !== commentId))
    } catch (err) {
      setError(apiErrorMessage(err, 'Failed to delete comment'))
    }
  }

  if (loading) {
    return (
      <div className="flex h-96 items-center justify-center">
        <FiRefreshCw className="h-8 w-8 animate-spin text-indigo-500" />
      </div>
    )
  }

  if (error && !task) {
    return (
      <div className="p-8 text-center">
        <p className="text-sm font-semibold text-rose-500">{error}</p>
        <button
          onClick={() => navigate('/crm/tasks')}
          className="secondary-button mt-4 gap-2"
        >
          <FiArrowLeft /> Back to Tasks
        </button>
      </div>
    )
  }

  if (!task) return null

  const typeInfo = taskTypeConfig(task.taskType)
  const isOverdue =
    task.dueAt &&
    new Date(task.dueAt).getTime() < Date.now() &&
    ['OPEN', 'IN_PROGRESS'].includes(task.status)

  const linkedContact = leads.find((l) => l.contactId === task.contactId)
  const linkedAccount = accounts.find((a) => a.id === task.accountId)

  return (
    <div className="mx-auto flex h-[calc(100dvh-72px-2*var(--app-page-gutter-y,1.5rem))] min-h-[500px] max-w-7xl flex-col gap-4 px-4 py-4 sm:px-6 lg:px-8">
      {/* Top Header / Breadcrumb Bar */}
      <header className="flex flex-wrap items-center justify-between gap-4 border-b border-slate-200 pb-3 dark:border-slate-800">
        <div className="flex items-center gap-3">
          <button
            type="button"
            onClick={() => navigate('/crm/tasks')}
            className="secondary-button gap-1.5 px-3 py-1.5 text-xs text-slate-600 dark:text-slate-300"
          >
            <FiArrowLeft className="h-3.5 w-3.5" />
            <span>Task Board</span>
          </button>
          <span className="text-slate-300 dark:text-slate-700">/</span>
          <span className="inline-flex items-center gap-1.5 rounded-full border px-2.5 py-0.5 text-xs font-semibold shadow-sm border-slate-200 bg-slate-50 text-slate-700 dark:border-slate-700 dark:bg-slate-800 dark:text-slate-200">
            <span>{typeInfo.icon}</span>
            <span>{typeInfo.label}</span>
          </span>
          <span className="text-xs text-slate-400">TASK-{task.id.slice(-6)}</span>
        </div>

        <div className="flex items-center gap-2">
          {canEdit && (
            <button
              type="button"
              onClick={() => setShowDeleteModal(true)}
              className="secondary-button gap-1 px-3 py-1.5 text-xs text-rose-600 hover:border-rose-300 hover:bg-rose-50 dark:text-rose-400"
            >
              <FiTrash2 />
              <span>Delete</span>
            </button>
          )}
        </div>
      </header>

      {error && (
        <div className="rounded-lg bg-rose-50 p-3 text-xs text-rose-700 dark:bg-rose-950/40 dark:text-rose-300">
          {error}
        </div>
      )}

      {/* Jira-style 2-Column Grid */}
      <div className="grid min-h-0 flex-1 grid-cols-1 gap-6 overflow-y-auto lg:grid-cols-[minmax(0,1fr)_340px]">
        {/* Main Left Column (65%) */}
        <div className="flex flex-col gap-6 pr-1">
          {/* Title Area */}
          <div>
            {editingTitle ? (
              <div className="flex items-center gap-2">
                <input
                  type="text"
                  value={editTitleVal}
                  onChange={(e) => setEditTitleVal(e.target.value)}
                  className="form-input flex-1 text-lg font-bold"
                  autoFocus
                />
                <button
                  type="button"
                  disabled={busy || !editTitleVal.trim()}
                  onClick={() => handleUpdate({ title: editTitleVal.trim() })}
                  className="primary-button px-3 py-2 text-xs"
                >
                  <FiCheck /> Save
                </button>
                <button
                  type="button"
                  onClick={() => {
                    setEditTitleVal(task.title)
                    setEditingTitle(false)
                  }}
                  className="secondary-button px-3 py-2 text-xs"
                >
                  <FiX />
                </button>
              </div>
            ) : (
              <div className="group flex items-start gap-2">
                <h1 className="text-2xl font-bold tracking-tight text-slate-900 dark:text-slate-100">
                  {task.title}
                </h1>
                {canEdit && (
                  <button
                    type="button"
                    onClick={() => setEditingTitle(true)}
                    className="opacity-0 transition-opacity group-hover:opacity-100 text-slate-400 hover:text-slate-600 dark:hover:text-slate-200"
                    title="Edit task title"
                  >
                    <FiEdit2 className="h-4 w-4" />
                  </button>
                )}
              </div>
            )}
          </div>

          {/* Description ("What needs to be done") */}
          <div className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm dark:border-slate-800 dark:bg-slate-900">
            <div className="mb-3 flex items-center justify-between">
              <h2 className="text-xs font-bold uppercase tracking-wider text-slate-500">
                Description / Action Items
              </h2>
              {canEdit && !editingDetails && (
                <button
                  type="button"
                  onClick={() => setEditingDetails(true)}
                  className="secondary-button gap-1 px-2 py-1 text-xs"
                >
                  <FiEdit2 className="h-3 w-3" /> Edit
                </button>
              )}
            </div>

            {editingDetails ? (
              <div>
                <textarea
                  rows={4}
                  value={editDetailsVal}
                  onChange={(e) => setEditDetailsVal(e.target.value)}
                  placeholder="Detail what needs to be accomplished in this task..."
                  className="form-input w-full text-sm leading-relaxed"
                />
                <div className="mt-2 flex gap-2">
                  <button
                    type="button"
                    disabled={busy}
                    onClick={() => handleUpdate({ details: editDetailsVal.trim() || null })}
                    className="primary-button px-3 py-1.5 text-xs"
                  >
                    <FiCheck /> Save Description
                  </button>
                  <button
                    type="button"
                    onClick={() => {
                      setEditDetailsVal(task.details || '')
                      setEditingDetails(false)
                    }}
                    className="secondary-button px-3 py-1.5 text-xs"
                  >
                    Cancel
                  </button>
                </div>
              </div>
            ) : (
              <div className="prose prose-sm dark:prose-invert max-w-none text-sm text-slate-700 dark:text-slate-300">
                {task.details ? (
                  <p className="whitespace-pre-wrap leading-relaxed">{task.details}</p>
                ) : (
                  <p className="italic text-slate-400">No action description provided. Click edit to add details.</p>
                )}
              </div>
            )}
          </div>

          {/* Associated Entity Section (Lead or Company) */}
          <div className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm dark:border-slate-800 dark:bg-slate-900">
            <h2 className="mb-3 text-xs font-bold uppercase tracking-wider text-slate-500">
              Linked CRM Entity
            </h2>
            <div className="grid gap-3 sm:grid-cols-2">
              {/* Linked Lead / Contact */}
              <div className="rounded-lg border border-slate-100 bg-slate-50/70 p-3 dark:border-slate-800 dark:bg-slate-950/40">
                <div className="flex items-center justify-between">
                  <span className="text-xs font-semibold text-slate-500">🎯 Related Lead / Contact</span>
                  {task.contactId && (
                    <Link
                      to={`/contacts?contactId=${task.contactId}`}
                      className="text-xs font-medium text-indigo-600 hover:underline dark:text-indigo-400"
                    >
                      View Profile →
                    </Link>
                  )}
                </div>
                <p className="mt-1 text-sm font-bold text-slate-900 dark:text-slate-100">
                  {task.contactName || linkedContact?.displayName || (task.contactId ? `Contact #${task.contactId}` : 'None linked')}
                </p>
                {linkedContact?.organization && (
                  <p className="text-xs text-slate-500">Org: {linkedContact.organization}</p>
                )}
              </div>

              {/* Linked Company / Account */}
              <div className="rounded-lg border border-slate-100 bg-slate-50/70 p-3 dark:border-slate-800 dark:bg-slate-950/40">
                <div className="flex items-center justify-between">
                  <span className="text-xs font-semibold text-slate-500">🏢 Related Company</span>
                  {task.accountId && (
                    <Link
                      to={`/crm/companies/${task.accountId}`}
                      className="text-xs font-medium text-indigo-600 hover:underline dark:text-indigo-400"
                    >
                      View Company →
                    </Link>
                  )}
                </div>
                <p className="mt-1 text-sm font-bold text-slate-900 dark:text-slate-100">
                  {task.accountName || linkedAccount?.name || (task.accountId ? `Company #${task.accountId}` : 'None linked')}
                </p>
                {linkedAccount?.website && (
                  <p className="text-xs text-slate-500 truncate">{linkedAccount.website}</p>
                )}
              </div>
            </div>
          </div>

          {/* Activity / Comments Tabs (Jira style) */}
          <div className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm dark:border-slate-800 dark:bg-slate-900">
            <div className="flex items-center gap-6 border-b border-slate-100 pb-2 dark:border-slate-800">
              <button
                type="button"
                onClick={() => setActiveTab('comments')}
                className={`flex items-center gap-2 pb-2 text-sm font-bold transition-colors ${
                  activeTab === 'comments'
                    ? 'border-b-2 border-indigo-600 text-indigo-600 dark:text-indigo-400'
                    : 'text-slate-500 hover:text-slate-800 dark:hover:text-slate-200'
                }`}
              >
                <FiMessageSquare className="h-4 w-4" />
                <span>Comments ({comments.length})</span>
              </button>
              <button
                type="button"
                onClick={() => setActiveTab('history')}
                className={`flex items-center gap-2 pb-2 text-sm font-bold transition-colors ${
                  activeTab === 'history'
                    ? 'border-b-2 border-indigo-600 text-indigo-600 dark:text-indigo-400'
                    : 'text-slate-500 hover:text-slate-800 dark:hover:text-slate-200'
                }`}
              >
                <FiClock className="h-4 w-4" />
                <span>Activity History ({events.length})</span>
              </button>
            </div>

            {/* Tab 1: Comments Stream */}
            {activeTab === 'comments' && (
              <div className="mt-4 flex flex-col gap-4">
                {/* Comment Input */}
                <form onSubmit={handleAddComment} className="flex flex-col gap-2">
                  <textarea
                    rows={3}
                    value={newComment}
                    onChange={(e) => setNewComment(e.target.value)}
                    placeholder="Add an update or note on this task..."
                    className="form-input w-full text-sm leading-relaxed"
                  />
                  <div className="flex justify-end">
                    <button
                      type="submit"
                      disabled={postingComment || !newComment.trim()}
                      className="primary-button gap-1.5 px-4 py-1.5 text-xs font-semibold"
                    >
                      <FiSend className="h-3 w-3" />
                      {postingComment ? 'Posting...' : 'Post Comment'}
                    </button>
                  </div>
                </form>

                {/* Comments List */}
                <div className="mt-2 divide-y divide-slate-100 dark:divide-slate-800">
                  {comments.length === 0 ? (
                    <p className="py-6 text-center text-xs text-slate-400">
                      No comments on this task yet. Start the conversation above!
                    </p>
                  ) : (
                    comments.map((c) => (
                      <div key={c.id} className="py-3">
                        <div className="flex items-center justify-between text-xs">
                          <div className="flex items-center gap-2">
                            <div className="flex h-6 w-6 items-center justify-center rounded-full bg-indigo-100 text-[10px] font-bold text-indigo-700 dark:bg-indigo-900 dark:text-indigo-300">
                              {c.authorName ? c.authorName.charAt(0).toUpperCase() : 'U'}
                            </div>
                            <span className="font-semibold text-slate-800 dark:text-slate-200">
                              {c.authorName || c.authorEmail || 'Teammate'}
                            </span>
                            <span className="text-[11px] text-slate-400">
                              {new Date(c.createdAt).toLocaleString()}
                            </span>
                          </div>
                          {canEdit && (
                            <button
                              type="button"
                              onClick={() => handleDeleteComment(c.id)}
                              className="text-slate-400 hover:text-rose-600 transition-colors"
                              title="Delete comment"
                            >
                              <FiTrash2 className="h-3.5 w-3.5" />
                            </button>
                          )}
                        </div>
                        <p className="mt-1.5 pl-8 text-sm text-slate-700 dark:text-slate-300 whitespace-pre-wrap">
                          {c.body}
                        </p>
                      </div>
                    ))
                  )}
                </div>
              </div>
            )}

            {/* Tab 2: Activity History */}
            {activeTab === 'history' && (
              <div className="mt-4 divide-y divide-slate-100 dark:divide-slate-800">
                {events.length === 0 ? (
                  <p className="py-6 text-center text-xs text-slate-400">
                    No transition events recorded yet.
                  </p>
                ) : (
                  events.map((evt) => (
                    <div key={evt.id} className="py-3 text-xs">
                      <div className="flex items-center justify-between text-slate-500">
                        <span className="font-semibold text-slate-700 dark:text-slate-300">
                          {evt.actorName || evt.actorUsername || 'System'}
                        </span>
                        <span className="text-[11px]">
                          {new Date(evt.occurredAt).toLocaleString()}
                        </span>
                      </div>
                      <p className="mt-1 text-slate-600 dark:text-slate-400">
                        {evt.eventType === 'CREATED' && 'Created this task.'}
                        {evt.eventType === 'STATUS_CHANGED' && (
                          <span>
                            Changed status from <b className="text-slate-800 dark:text-slate-200">{evt.previousStatus}</b> to{' '}
                            <b className="text-indigo-600 dark:text-indigo-400">{evt.newStatus}</b>
                          </span>
                        )}
                        {evt.eventType === 'STEP_CHANGED' && (
                          <span>
                            Moved step to <b className="text-indigo-600 dark:text-indigo-400">{evt.newColumn || evt.newStatus}</b>
                          </span>
                        )}
                        {evt.eventType === 'UPDATED' && (
                          <span>Updated fields: {evt.changedFields || 'details'}</span>
                        )}
                      </p>
                    </div>
                  ))
                )}
              </div>
            )}
          </div>
        </div>

        {/* Sidebar Column (35%, Jira style Details Panel) */}
        <div className="flex flex-col gap-5">
          {/* Status & Priority Card */}
          <div className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm dark:border-slate-800 dark:bg-slate-900">
            <h3 className="mb-4 text-xs font-bold uppercase tracking-wider text-slate-500">
              Workflow & Status
            </h3>

            <div className="space-y-4">
              {/* Step / Status Selector */}
              <div>
                <label className="text-xs font-semibold text-slate-600 dark:text-slate-400">
                  Current Step
                </label>
                <select
                  disabled={!canEdit || busy}
                  value={task.columnKey || task.status}
                  onChange={(e) => handleMoveStep(e.target.value)}
                  className="form-input mt-1.5 w-full text-xs font-semibold"
                >
                  {columns.map((col) => (
                    <option key={col.key} value={col.key}>
                      {col.name} ({col.statusGroup})
                    </option>
                  ))}
                </select>
              </div>

              {/* Priority Selector */}
              <div>
                <label className="text-xs font-semibold text-slate-600 dark:text-slate-400">
                  Priority
                </label>
                <select
                  disabled={!canEdit || busy}
                  value={task.priority}
                  onChange={(e) => handleUpdate({ priority: e.target.value as CrmTask['priority'] })}
                  className="form-input mt-1.5 w-full text-xs"
                >
                  <option value="LOW">Low Priority</option>
                  <option value="NORMAL">Normal Priority</option>
                  <option value="HIGH">High Priority (Urgent)</option>
                </select>
              </div>

              {/* Task Type Selector */}
              <div>
                <label className="text-xs font-semibold text-slate-600 dark:text-slate-400">
                  Task Type
                </label>
                <select
                  disabled={!canEdit || busy}
                  value={task.taskType || 'TODO'}
                  onChange={(e) => handleUpdate({ taskType: e.target.value })}
                  className="form-input mt-1.5 w-full text-xs"
                >
                  <option value="TODO">📝 General To-Do</option>
                  <option value="CALL">📞 Phone Call</option>
                  <option value="MEETING">👥 Meeting</option>
                  <option value="EMAIL">✉️ Send Email</option>
                  <option value="FOLLOW_UP">📋 Follow-up</option>
                  <option value="LEAD">🎯 Lead Task</option>
                  <option value="COMPANY">🏢 Company Task</option>
                </select>
              </div>
            </div>
          </div>

          {/* Assignment & People Card */}
          <div className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm dark:border-slate-800 dark:bg-slate-900">
            <h3 className="mb-4 text-xs font-bold uppercase tracking-wider text-slate-500">
              People & Deadlines
            </h3>

            <div className="space-y-4">
              {/* Assignee */}
              <div>
                <label className="text-xs font-semibold text-slate-600 dark:text-slate-400">
                  Assignee
                </label>
                <select
                  disabled={!canEdit || busy}
                  value={task.assigneeUserId}
                  onChange={(e) => handleUpdate({ assigneeUserId: e.target.value })}
                  className="form-input mt-1.5 w-full text-xs"
                >
                  {members.map((m) => (
                    <option key={m.userId} value={m.userId}>
                      {m.name}
                    </option>
                  ))}
                  {!members.some((m) => m.userId === task.assigneeUserId) && (
                    <option value={task.assigneeUserId}>
                      {task.assigneeName || `User #${task.assigneeUserId}`}
                    </option>
                  )}
                </select>
              </div>

              {/* Due Date */}
              <div>
                <label className="text-xs font-semibold text-slate-600 dark:text-slate-400">
                  Due Date
                </label>
                <input
                  type="date"
                  disabled={!canEdit || busy}
                  value={task.dueAt ? task.dueAt.slice(0, 10) : ''}
                  onChange={(e) =>
                    handleUpdate({
                      dueAt: e.target.value ? new Date(e.target.value).toISOString() : null,
                    })
                  }
                  className="form-input mt-1.5 w-full text-xs"
                />
                {isOverdue && (
                  <p className="mt-1 text-[11px] font-semibold text-rose-600">
                    ⚠️ Task is past its due date!
                  </p>
                )}
              </div>

              {/* Link to Lead */}
              <div>
                <label className="text-xs font-semibold text-slate-600 dark:text-slate-400">
                  Link to Lead
                </label>
                <select
                  disabled={!canEdit || busy}
                  value={task.contactId || ''}
                  onChange={(e) => handleUpdate({ contactId: e.target.value || null })}
                  className="form-input mt-1.5 w-full text-xs"
                >
                  <option value="">None (Unlinked)</option>
                  {leads.map((l) => (
                    <option key={l.contactId} value={l.contactId}>
                      {l.displayName || 'Lead'} {l.organization ? `(${l.organization})` : ''}
                    </option>
                  ))}
                </select>
              </div>

              {/* Link to Company */}
              <div>
                <label className="text-xs font-semibold text-slate-600 dark:text-slate-400">
                  Link to Company
                </label>
                <select
                  disabled={!canEdit || busy}
                  value={task.accountId || ''}
                  onChange={(e) => handleUpdate({ accountId: e.target.value || null })}
                  className="form-input mt-1.5 w-full text-xs"
                >
                  <option value="">None (Unlinked)</option>
                  {accounts.map((a) => (
                    <option key={a.id} value={a.id}>
                      {a.name}
                    </option>
                  ))}
                </select>
              </div>
            </div>
          </div>

          {/* Metadata Card */}
          <div className="rounded-xl border border-slate-200 bg-slate-50/60 p-4 text-[11px] text-slate-500 dark:border-slate-800 dark:bg-slate-900/50">
            <p>Created: {new Date(task.createdAt || Date.now()).toLocaleString()}</p>
            {task.updatedAt && (
              <p className="mt-1">Last updated: {new Date(task.updatedAt).toLocaleString()}</p>
            )}
          </div>
        </div>
      </div>

      {/* Delete Confirmation Modal */}
      {showDeleteModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/60 p-4 backdrop-blur-sm">
          <div className="panel w-full max-w-md p-6">
            <h3 className="text-lg font-bold text-slate-900 dark:text-slate-100">Delete Task</h3>
            <p className="mt-2 text-sm text-slate-500">
              Are you sure you want to delete &ldquo;{task.title}&rdquo;? This action cannot be undone.
            </p>
            <div className="mt-6 flex justify-end gap-3">
              <button
                type="button"
                onClick={() => setShowDeleteModal(false)}
                className="secondary-button text-xs"
              >
                Cancel
              </button>
              <button
                type="button"
                disabled={busy}
                onClick={handleDelete}
                className="primary-button bg-rose-600 text-xs hover:bg-rose-700"
              >
                {busy ? 'Deleting...' : 'Yes, Delete Task'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
