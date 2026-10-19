import { useCallback, useEffect, useRef, useState } from 'react'
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
  FiCheckSquare,
  FiSquare,
  FiPaperclip,
  FiUploadCloud,
  FiDownload,
  FiPlus,
  FiFileText,
} from 'react-icons/fi'
import { companyApi } from '../../features/company/companyApi'
import {
  crmWorkApi,
  type CrmAccount,
  type CrmComment,
  type CrmSubtask,
  type CrmTask,
  type CrmTaskAttachment,
  type CrmTaskColumn,
  type CrmTaskEvent,
} from '../../features/contacts/crmWorkApi'
import { crmLeadsApi, type Lead } from '../../features/contacts/crmLeadsApi'
import { crmSalesApi, type Deal } from '../../features/contacts/crmSalesApi'
import { PERMISSIONS } from '../../features/iam/permissions'
import { useIam } from '../../features/iam/useIam'
import { useWorkspaceEpochScope } from '../../features/workspace/useWorkspaceEpochScope'
import { apiErrorMessage } from '../../lib/api/apiClient'
import { selectAuthUser } from '../../store/auth/authSlice'
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

function formatMinutes(mins?: number | null) {
  if (!mins || mins <= 0) return '0m'
  const h = Math.floor(mins / 60)
  const m = mins % 60
  if (h > 0 && m > 0) return `${h}h ${m}m`
  if (h > 0) return `${h}h`
  return `${m}m`
}

function formatFileSize(bytes?: number) {
  if (!bytes || bytes <= 0) return '0 B'
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
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
  const [deals, setDeals] = useState<Deal[]>([])
  const [comments, setComments] = useState<CrmComment[]>([])
  const [events, setEvents] = useState<CrmTaskEvent[]>([])
  const [subtasks, setSubtasks] = useState<CrmSubtask[]>([])
  const [attachments, setAttachments] = useState<CrmTaskAttachment[]>([])

  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [activeTab, setActiveTab] = useState<'comments' | 'history'>('comments')
  const [newComment, setNewComment] = useState('')
  const [postingComment, setPostingComment] = useState(false)

  // Title & Details Inline Editing
  const [editingTitle, setEditingTitle] = useState(false)
  const [editTitleVal, setEditTitleVal] = useState('')
  const [editingDetails, setEditingDetails] = useState(false)
  const [editDetailsVal, setEditDetailsVal] = useState('')

  // Subtask creation
  const [newSubtaskTitle, setNewSubtaskTitle] = useState('')
  const [newSubtaskAssignee, setNewSubtaskAssignee] = useState('')
  const [addingSubtask, setAddingSubtask] = useState(false)

  // Attachments
  const fileInputRef = useRef<HTMLInputElement>(null)
  const [uploadingAttachment, setUploadingAttachment] = useState(false)

  // Time logging modal
  const [showLogTimeModal, setShowLogTimeModal] = useState(false)
  const [logMinutesInput, setLogMinutesInput] = useState('30')
  const [loggingTime, setLoggingTime] = useState(false)

  // Estimate inline edit
  const [editingEstimate, setEditingEstimate] = useState(false)
  const [estimateInput, setEstimateInput] = useState('120')

  const [showDeleteModal, setShowDeleteModal] = useState(false)

  const currentUser = useAppSelector(selectAuthUser)
  const currentUserId = currentUser ? String(currentUser.userId) : null

  const isAssigneeOrReporter =
    currentUserId !== null &&
    Boolean(task && (task.assigneeUserId === currentUserId || task.reporterUserId === currentUserId))

  // Granular permissions with umbrella fallback and assignee empowerment
  const canEdit =
    can(PERMISSIONS.CRM_TASK_EDIT) ||
    can(PERMISSIONS.CRM_TASK_MODIFY) ||
    isAssigneeOrReporter
  const canStatusChange =
    can(PERMISSIONS.CRM_TASK_STATUS) ||
    can(PERMISSIONS.CRM_TASK_MODIFY) ||
    canEdit ||
    isAssigneeOrReporter
  const canDelete =
    can(PERMISSIONS.CRM_TASK_DELETE) ||
    can(PERMISSIONS.CRM_TASK_MODIFY)
  const canComment =
    can(PERMISSIONS.CRM_TASK_COMMENT) ||
    can(PERMISSIONS.CRM_TASK_MODIFY) ||
    canEdit ||
    isAssigneeOrReporter

  const [elapsedActiveMinutes, setElapsedActiveMinutes] = useState(0)

  useEffect(() => {
    if (task?.status !== 'IN_PROGRESS' || !task?.inProgressStartedAt) {
      setElapsedActiveMinutes(0)
      return
    }
    const updateElapsed = () => {
      const start = new Date(task.inProgressStartedAt!).getTime()
      const diffSecs = Math.max(0, Math.floor((Date.now() - start) / 1000))
      setElapsedActiveMinutes(Math.max(1, Math.round(diffSecs / 60)))
    }
    updateElapsed()
    const timer = window.setInterval(updateElapsed, 10000)
    return () => window.clearInterval(timer)
  }, [task?.status, task?.inProgressStartedAt])

  const loadData = useCallback(async () => {
    if (!companyId || !taskId) return
    const scope = capture()
    setLoading(true)
    setError('')
    try {
      const [fetchedTask, cols, history, comms, subs, atts] = await Promise.all([
        crmWorkApi.task(companyId, taskId),
        crmWorkApi.taskColumns(companyId).catch(() => []),
        crmWorkApi.taskHistory(companyId, taskId).catch(() => []),
        crmWorkApi.taskComments(companyId, taskId).catch(() => []),
        crmWorkApi.subtasks(companyId, taskId).catch(() => []),
        crmWorkApi.taskAttachments(companyId, taskId).catch(() => []),
      ])

      if (isCurrent(scope)) {
        setTask(fetchedTask)
        setColumns(cols)
        setEvents(history)
        setComments(comms)
        setSubtasks(subs)
        setAttachments(atts)
        setEditTitleVal(fetchedTask.title)
        setEditDetailsVal(fetchedTask.details || '')
        setEstimateInput(String(fetchedTask.estimatedMinutes || 60))
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

      if (can(PERMISSIONS.CRM_DEAL_READ)) {
        crmSalesApi.deals(companyId).then((dealsPage) => {
          if (isCurrent(scope)) setDeals(dealsPage.items)
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
    dealId: string | null
    estimatedMinutes: number | null
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
        dealId: patch.dealId !== undefined ? patch.dealId : task.dealId,
        estimatedMinutes: patch.estimatedMinutes !== undefined ? patch.estimatedMinutes : task.estimatedMinutes,
        taskType: patch.taskType !== undefined ? patch.taskType : task.taskType,
      })
      setTask(updated)
      setEditTitleVal(updated.title)
      setEditDetailsVal(updated.details || '')
      setEditingTitle(false)
      setEditingDetails(false)
      setEditingEstimate(false)
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

  // Subtask handlers
  const handleToggleSubtask = async (subtaskId: string) => {
    if (!companyId || !task) return
    // Optimistic toggle
    setSubtasks((prev) =>
      prev.map((s) => (s.id === subtaskId ? { ...s, completed: !s.completed } : s)),
    )
    try {
      const updated = await crmWorkApi.toggleSubtask(companyId, task.id, subtaskId)
      setSubtasks((prev) => prev.map((s) => (s.id === subtaskId ? updated : s)))
    } catch (err) {
      setError(apiErrorMessage(err, 'Failed to toggle subtask'))
      // rollback
      const subs = await crmWorkApi.subtasks(companyId, task.id).catch(() => [])
      setSubtasks(subs)
    }
  }

  const handleAddSubtask = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!companyId || !task || !newSubtaskTitle.trim() || addingSubtask) return
    setAddingSubtask(true)
    try {
      const created = await crmWorkApi.createSubtask(
        companyId,
        task.id,
        newSubtaskTitle.trim(),
        newSubtaskAssignee || null,
      )
      setSubtasks((prev) => [...prev, created])
      setNewSubtaskTitle('')
      setNewSubtaskAssignee('')
    } catch (err) {
      setError(apiErrorMessage(err, 'Failed to add subtask'))
    } finally {
      setAddingSubtask(false)
    }
  }

  const handleDeleteSubtask = async (subtaskId: string) => {
    if (!companyId || !task) return
    try {
      await crmWorkApi.deleteSubtask(companyId, task.id, subtaskId)
      setSubtasks((prev) => prev.filter((s) => s.id !== subtaskId))
    } catch (err) {
      setError(apiErrorMessage(err, 'Failed to delete subtask'))
    }
  }

  // Attachment handlers
  const handleFileSelect = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (!file || !companyId || !task) return
    setUploadingAttachment(true)
    try {
      const attached = await crmWorkApi.attachTaskFile(companyId, task.id, file)
      setAttachments((prev) => [attached, ...prev])
    } catch (err) {
      setError(apiErrorMessage(err, 'Failed to attach file'))
    } finally {
      setUploadingAttachment(false)
      if (fileInputRef.current) fileInputRef.current.value = ''
    }
  }

  const handleDeleteAttachment = async (attachmentId: string) => {
    if (!companyId || !task) return
    try {
      await crmWorkApi.deleteTaskAttachment(companyId, task.id, attachmentId)
      setAttachments((prev) => prev.filter((a) => a.id !== attachmentId))
    } catch (err) {
      setError(apiErrorMessage(err, 'Failed to delete attachment'))
    }
  }

  // Time logging handlers
  const handleLogWork = async () => {
    const mins = parseInt(logMinutesInput, 10)
    if (isNaN(mins) || mins <= 0 || !companyId || !task || loggingTime) return
    setLoggingTime(true)
    try {
      const updated = await crmWorkApi.logTime(companyId, task.id, mins)
      setTask(updated)
      setShowLogTimeModal(false)
      setLogMinutesInput('30')
      const history = await crmWorkApi.taskHistory(companyId, task.id).catch(() => [])
      setEvents(history)
    } catch (err) {
      setError(apiErrorMessage(err, 'Failed to log time'))
    } finally {
      setLoggingTime(false)
    }
  }

  // Comment handlers
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
  const linkedDeal = deals.find((d) => d.id === task.dealId)

  const completedSubtasks = subtasks.filter((s) => s.completed).length
  const totalSubtasks = subtasks.length
  const subtaskProgress = totalSubtasks > 0 ? Math.round((completedSubtasks / totalSubtasks) * 100) : 0

  const estimateMinutes = task.estimatedMinutes || 0
  const loggedMinutes = task.loggedMinutes || 0
  const timeProgress =
    estimateMinutes > 0 ? Math.min(100, Math.round((loggedMinutes / estimateMinutes) * 100)) : 0

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
          <span className="font-mono text-xs font-bold text-indigo-600 dark:text-indigo-400">
            TSK-{task.id.slice(-6)}
          </span>
        </div>

        <div className="flex items-center gap-2">
          {canEdit && (
            <button
              type="button"
              onClick={() => setShowLogTimeModal(true)}
              className="secondary-button gap-1.5 px-3 py-1.5 text-xs text-indigo-600 hover:bg-indigo-50 dark:text-indigo-400 dark:hover:bg-indigo-950/40"
            >
              <FiClock className="h-3.5 w-3.5" />
              <span>Log Work</span>
            </button>
          )}
          {canDelete && (
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

          {/* Jira-grade Subtasks / Checklist Card */}
          <div className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm dark:border-slate-800 dark:bg-slate-900">
            <div className="mb-4 flex flex-wrap items-center justify-between gap-2">
              <div className="flex items-center gap-2">
                <FiCheckSquare className="h-4 w-4 text-indigo-500" />
                <h2 className="text-xs font-bold uppercase tracking-wider text-slate-700 dark:text-slate-300">
                  Subtasks & Checklist
                </h2>
                <span className="rounded-full bg-slate-100 px-2 py-0.5 text-xs font-semibold text-slate-600 dark:bg-slate-800 dark:text-slate-300">
                  {completedSubtasks}/{totalSubtasks}
                </span>
              </div>
              {totalSubtasks > 0 && (
                <span className="text-xs font-bold text-slate-500">
                  {subtaskProgress}% done
                </span>
              )}
            </div>

            {/* Progress bar */}
            {totalSubtasks > 0 && (
              <div className="mb-4 h-2 w-full overflow-hidden rounded-full bg-slate-100 dark:bg-slate-800">
                <div
                  className="h-full rounded-full bg-emerald-500 transition-all duration-300"
                  style={{ width: `${subtaskProgress}%` }}
                />
              </div>
            )}

            {/* Subtask list */}
            <div className="divide-y divide-slate-100 dark:divide-slate-800">
              {subtasks.length === 0 ? (
                <p className="py-4 text-center text-xs text-slate-400">
                  No subtasks yet. Break this task down into manageable checklist items below.
                </p>
              ) : (
                subtasks.map((st) => (
                  <div
                    key={st.id}
                    className="group flex items-center justify-between py-2.5 transition-colors hover:bg-slate-50/50 dark:hover:bg-slate-800/30"
                  >
                    <div className="flex items-center gap-3 min-w-0 flex-1">
                      <button
                        type="button"
                        disabled={!canEdit}
                        onClick={() => handleToggleSubtask(st.id)}
                        className="text-slate-400 hover:text-indigo-600 dark:hover:text-indigo-400 transition-colors"
                        title={st.completed ? 'Mark uncompleted' : 'Mark completed'}
                      >
                        {st.completed ? (
                          <FiCheckSquare className="h-4 w-4 text-emerald-600 dark:text-emerald-400" />
                        ) : (
                          <FiSquare className="h-4 w-4" />
                        )}
                      </button>
                      <span
                        className={`text-sm break-words ${
                          st.completed
                            ? 'line-through text-slate-400 dark:text-slate-500'
                            : 'text-slate-800 dark:text-slate-200'
                        }`}
                      >
                        {st.title}
                      </span>
                    </div>

                    <div className="flex items-center gap-2 pl-2">
                      {st.assigneeName && (
                        <span className="rounded bg-slate-100 px-2 py-0.5 text-[11px] font-medium text-slate-600 dark:bg-slate-800 dark:text-slate-300">
                          {st.assigneeName}
                        </span>
                      )}
                      {canEdit && (
                        <button
                          type="button"
                          onClick={() => handleDeleteSubtask(st.id)}
                          className="opacity-0 transition-opacity group-hover:opacity-100 text-slate-400 hover:text-rose-600"
                          title="Delete subtask"
                        >
                          <FiTrash2 className="h-3.5 w-3.5" />
                        </button>
                      )}
                    </div>
                  </div>
                ))
              )}
            </div>

            {/* Add Subtask input */}
            {canEdit && (
              <form onSubmit={handleAddSubtask} className="mt-4 flex flex-wrap items-center gap-2">
                <input
                  type="text"
                  placeholder="Add a subtask or checklist item..."
                  value={newSubtaskTitle}
                  onChange={(e) => setNewSubtaskTitle(e.target.value)}
                  className="form-input flex-1 text-xs"
                />
                {members.length > 0 && (
                  <select
                    value={newSubtaskAssignee}
                    onChange={(e) => setNewSubtaskAssignee(e.target.value)}
                    className="form-input text-xs w-36"
                  >
                    <option value="">No assignee</option>
                    {members.map((m) => (
                      <option key={m.userId} value={m.userId}>
                        {m.name}
                      </option>
                    ))}
                  </select>
                )}
                <button
                  type="submit"
                  disabled={addingSubtask || !newSubtaskTitle.trim()}
                  className="primary-button gap-1 px-3 py-1.5 text-xs"
                >
                  <FiPlus /> Add
                </button>
              </form>
            )}
          </div>

          {/* Jira-grade Attachments Section */}
          <div className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm dark:border-slate-800 dark:bg-slate-900">
            <div className="mb-4 flex items-center justify-between">
              <div className="flex items-center gap-2">
                <FiPaperclip className="h-4 w-4 text-indigo-500" />
                <h2 className="text-xs font-bold uppercase tracking-wider text-slate-700 dark:text-slate-300">
                  Attachments & Files
                </h2>
                <span className="rounded-full bg-slate-100 px-2 py-0.5 text-xs font-semibold text-slate-600 dark:bg-slate-800 dark:text-slate-300">
                  {attachments.length}
                </span>
              </div>
              {canEdit && (
                <div>
                  <input
                    ref={fileInputRef}
                    type="file"
                    className="hidden"
                    onChange={handleFileSelect}
                  />
                  <button
                    type="button"
                    disabled={uploadingAttachment}
                    onClick={() => fileInputRef.current?.click()}
                    className="secondary-button gap-1.5 px-3 py-1.5 text-xs"
                  >
                    <FiUploadCloud className="h-3.5 w-3.5" />
                    <span>{uploadingAttachment ? 'Uploading...' : 'Attach File'}</span>
                  </button>
                </div>
              )}
            </div>

            {attachments.length === 0 ? (
              <div
                onClick={() => canEdit && fileInputRef.current?.click()}
                className={`flex flex-col items-center justify-center rounded-lg border-2 border-dashed border-slate-200 p-6 text-center dark:border-slate-800 ${
                  canEdit ? 'cursor-pointer hover:border-indigo-400 hover:bg-slate-50/50 dark:hover:bg-slate-800/30' : ''
                }`}
              >
                <FiPaperclip className="h-6 w-6 text-slate-400" />
                <p className="mt-2 text-xs text-slate-500">
                  No attachments yet. Drop documents, images, or files here to link with this task.
                </p>
              </div>
            ) : (
              <div className="grid gap-3 sm:grid-cols-2">
                {attachments.map((att) => (
                  <div
                    key={att.id}
                    className="flex items-center justify-between rounded-lg border border-slate-200 bg-slate-50/60 p-3 transition-colors hover:border-indigo-300 dark:border-slate-800 dark:bg-slate-800/40"
                  >
                    <div className="flex items-center gap-3 min-w-0">
                      <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-indigo-100 text-indigo-600 dark:bg-indigo-900/60 dark:text-indigo-400">
                        <FiFileText className="h-5 w-5" />
                      </div>
                      <div className="min-w-0 flex-1">
                        <p className="truncate text-xs font-bold text-slate-800 dark:text-slate-200" title={att.fileName}>
                          {att.fileName}
                        </p>
                        <p className="text-[11px] text-slate-400">
                          {formatFileSize(att.fileSize)} • {att.uploadedByName || 'Teammate'}
                        </p>
                      </div>
                    </div>
                    <div className="flex items-center gap-1 pl-2">
                      <a
                        href={`/api/v1/companies/${companyId}/media/${att.mediaId}/download`}
                        target="_blank"
                        rel="noreferrer"
                        className="rounded p-1.5 text-slate-500 hover:bg-slate-200 hover:text-slate-800 dark:hover:bg-slate-700"
                        title="Download file"
                      >
                        <FiDownload className="h-3.5 w-3.5" />
                      </a>
                      {canEdit && (
                        <button
                          type="button"
                          onClick={() => handleDeleteAttachment(att.id)}
                          className="rounded p-1.5 text-slate-400 hover:bg-rose-50 hover:text-rose-600 dark:hover:bg-rose-950/40"
                          title="Delete attachment"
                        >
                          <FiTrash2 className="h-3.5 w-3.5" />
                        </button>
                      )}
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>

          {/* Associated Entities Section (Lead, Company, Deal) */}
          <div className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm dark:border-slate-800 dark:bg-slate-900">
            <h2 className="mb-3 text-xs font-bold uppercase tracking-wider text-slate-500">
              Linked CRM Entities
            </h2>
            <div className="grid gap-3 sm:grid-cols-3">
              {/* Linked Deal */}
              <div className="rounded-lg border border-slate-100 bg-slate-50/70 p-3 dark:border-slate-800 dark:bg-slate-950/40">
                <div className="flex items-center justify-between">
                  <span className="text-xs font-semibold text-slate-500">🏷️ Related Deal</span>
                  {task.dealId && (
                    <Link
                      to={`/crm/deals`}
                      className="text-xs font-medium text-indigo-600 hover:underline dark:text-indigo-400"
                    >
                      View Deals →
                    </Link>
                  )}
                </div>
                <p className="mt-1 text-sm font-bold text-slate-900 dark:text-slate-100">
                  {task.dealTitle || linkedDeal?.title || (task.dealId ? `Deal #${task.dealId}` : 'None linked')}
                </p>
                {linkedDeal && (
                  <p className="text-xs text-slate-500">
                    {linkedDeal.stage} • ${linkedDeal.amount}
                  </p>
                )}
              </div>

              {/* Linked Lead / Contact */}
              <div className="rounded-lg border border-slate-100 bg-slate-50/70 p-3 dark:border-slate-800 dark:bg-slate-950/40">
                <div className="flex items-center justify-between">
                  <span className="text-xs font-semibold text-slate-500">👤 Related Lead</span>
                  {task.contactId && (
                    <Link
                      to={`/contacts?contactId=${task.contactId}`}
                      className="text-xs font-medium text-indigo-600 hover:underline dark:text-indigo-400"
                    >
                      Profile →
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
                      Company →
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
                {canComment && (
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
                )}

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
                  disabled={!canStatusChange || busy}
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

          {/* Time Tracking (Jira-style) */}
          <div className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm dark:border-slate-800 dark:bg-slate-900">
            <div className="mb-3 flex items-center justify-between">
              <h3 className="text-xs font-bold uppercase tracking-wider text-slate-500">
                Time Tracking & Estimates
              </h3>
              {canEdit && (
                <button
                  type="button"
                  onClick={() => setShowLogTimeModal(true)}
                  className="text-xs font-semibold text-indigo-600 hover:underline dark:text-indigo-400"
                >
                  + Log Time
                </button>
              )}
            </div>

            <div className="space-y-3">
              <div className="flex items-center justify-between text-xs">
                <span className="text-slate-500">Original Estimate:</span>
                {editingEstimate ? (
                  <div className="flex items-center gap-1">
                    <input
                      type="number"
                      min="0"
                      step="15"
                      value={estimateInput}
                      onChange={(e) => setEstimateInput(e.target.value)}
                      className="form-input w-20 py-0.5 text-xs text-right"
                    />
                    <span className="text-[10px] text-slate-400">min</span>
                    <button
                      type="button"
                      onClick={() => handleUpdate({ estimatedMinutes: parseInt(estimateInput, 10) || 0 })}
                      className="text-emerald-600 hover:text-emerald-700"
                    >
                      <FiCheck className="h-3 w-3" />
                    </button>
                    <button
                      type="button"
                      onClick={() => setEditingEstimate(false)}
                      className="text-slate-400"
                    >
                      <FiX className="h-3 w-3" />
                    </button>
                  </div>
                ) : (
                  <div className="flex items-center gap-1.5 font-bold text-slate-800 dark:text-slate-200">
                    <span>{formatMinutes(task.estimatedMinutes)}</span>
                    {canEdit && (
                      <button
                        type="button"
                        onClick={() => {
                          setEstimateInput(String(task.estimatedMinutes || 60))
                          setEditingEstimate(true)
                        }}
                        className="text-slate-400 hover:text-slate-600 dark:hover:text-slate-200"
                        title="Edit estimate"
                      >
                        <FiEdit2 className="h-3 w-3" />
                      </button>
                    )}
                  </div>
                )}
              </div>

              <div className="flex items-center justify-between text-xs">
                <span className="text-slate-500">Time Logged:</span>
                <span className="font-bold text-slate-800 dark:text-slate-200">
                  {formatMinutes(task.loggedMinutes)}
                </span>
              </div>

              {task.status === 'IN_PROGRESS' && (
                <div className="flex items-center justify-between rounded-lg bg-indigo-50/80 p-2 text-xs text-indigo-700 dark:bg-indigo-950/40 dark:text-indigo-300">
                  <div className="flex items-center gap-1.5 font-medium">
                    <span className="relative flex h-2 w-2">
                      <span className="absolute inline-flex h-full w-full animate-ping rounded-full bg-indigo-400 opacity-75"></span>
                      <span className="relative inline-flex h-2 w-2 rounded-full bg-indigo-600"></span>
                    </span>
                    <span>In-Progress Session:</span>
                  </div>
                  <span className="font-mono font-bold">
                    {formatMinutes(elapsedActiveMinutes)} active
                  </span>
                </div>
              )}

              {/* Progress bar */}
              <div className="h-2 w-full overflow-hidden rounded-full bg-slate-100 dark:bg-slate-800">
                <div
                  className={`h-full rounded-full transition-all duration-300 ${
                    loggedMinutes > estimateMinutes && estimateMinutes > 0
                      ? 'bg-amber-500'
                      : 'bg-indigo-600'
                  }`}
                  style={{ width: `${timeProgress}%` }}
                />
              </div>
              <div className="flex justify-between text-[10px] text-slate-400">
                <span>{timeProgress}% logged</span>
                {estimateMinutes > 0 && loggedMinutes > estimateMinutes && (
                  <span className="font-semibold text-amber-500">Over estimate</span>
                )}
              </div>
            </div>
          </div>

          {/* Assignment & People Card */}
          <div className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm dark:border-slate-800 dark:bg-slate-900">
            <h3 className="mb-4 text-xs font-bold uppercase tracking-wider text-slate-500">
              People & Deadlines
            </h3>

            <div className="space-y-4">
              {/* Reporter (Jira-style) */}
              <div>
                <label className="text-xs font-semibold text-slate-600 dark:text-slate-400">
                  Reporter
                </label>
                <div className="mt-1.5 flex items-center gap-2 rounded-lg border border-slate-200 bg-slate-50/50 px-3 py-2 text-xs dark:border-slate-800 dark:bg-slate-800/40">
                  <div className="flex h-5 w-5 items-center justify-center rounded-full bg-indigo-200 text-[10px] font-bold text-indigo-800 dark:bg-indigo-900 dark:text-indigo-200">
                    {task.reporterName ? task.reporterName.charAt(0).toUpperCase() : 'R'}
                  </div>
                  <span className="font-medium text-slate-800 dark:text-slate-200">
                    {task.reporterName || 'Creator'}
                  </span>
                </div>
              </div>

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

              {/* Link to Deal */}
              <div>
                <label className="text-xs font-semibold text-slate-600 dark:text-slate-400">
                  Link to Deal
                </label>
                <select
                  disabled={!canEdit || busy}
                  value={task.dealId || ''}
                  onChange={(e) => handleUpdate({ dealId: e.target.value || null })}
                  className="form-input mt-1.5 w-full text-xs"
                >
                  <option value="">None (Unlinked)</option>
                  {deals.map((d) => (
                    <option key={d.id} value={d.id}>
                      {d.title} (${d.amount})
                    </option>
                  ))}
                </select>
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

      {/* Log Work Modal */}
      {showLogTimeModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/60 p-4 backdrop-blur-sm">
          <div className="panel w-full max-w-sm p-6 shadow-xl">
            <h3 className="text-base font-bold text-slate-900 dark:text-slate-100">Log Work Time</h3>
            <p className="mt-1 text-xs text-slate-500">
              Record time spent working on this task.
            </p>

            <div className="mt-4">
              <label className="text-xs font-semibold text-slate-700 dark:text-slate-300">
                Duration (minutes)
              </label>
              <input
                type="number"
                min="1"
                step="5"
                value={logMinutesInput}
                onChange={(e) => setLogMinutesInput(e.target.value)}
                className="form-input mt-1 w-full text-sm font-bold"
                autoFocus
              />
              <div className="mt-2 flex gap-1.5">
                {['15', '30', '45', '60', '120'].map((mins) => (
                  <button
                    key={mins}
                    type="button"
                    onClick={() => setLogMinutesInput(mins)}
                    className={`rounded border px-2 py-1 text-xs font-semibold transition-colors ${
                      logMinutesInput === mins
                        ? 'border-indigo-600 bg-indigo-50 text-indigo-700 dark:bg-indigo-950/60 dark:text-indigo-300'
                        : 'border-slate-200 text-slate-600 hover:bg-slate-50 dark:border-slate-700 dark:text-slate-400'
                    }`}
                  >
                    +{mins}m
                  </button>
                ))}
              </div>
            </div>

            <div className="mt-6 flex justify-end gap-2">
              <button
                type="button"
                onClick={() => setShowLogTimeModal(false)}
                className="secondary-button text-xs"
              >
                Cancel
              </button>
              <button
                type="button"
                disabled={loggingTime || !parseInt(logMinutesInput, 10)}
                onClick={handleLogWork}
                className="primary-button text-xs"
              >
                {loggingTime ? 'Logging...' : 'Save Work Log'}
              </button>
            </div>
          </div>
        </div>
      )}

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
