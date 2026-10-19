import { ListSkeleton } from '../ui/Skeleton'
import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import { FiArrowRight, FiClock, FiRefreshCw, FiUser, FiX } from 'react-icons/fi'
import { crmWorkApi, type CrmTask, type CrmTaskEvent } from '../../features/contacts/crmWorkApi'
import { leadDateTime } from '../../features/contacts/leadPresentation'
import { taskLabel, taskStatusClass } from '../../features/contacts/taskPresentation'
import { apiErrorMessage } from '../../lib/api/apiClient'

export default function TaskHistoryPanel({
  companyId,
  task,
  assigneeName,
  stepName,
  onClose,
}: {
  companyId: string
  task: CrmTask
  stepName?: string
  assigneeName: string
  onClose: () => void
}) {
  const [reload, setReload] = useState(0)
  const key = `${companyId}:${task.id}:${task.version}:${reload}`
  const [result, setResult] = useState<{
    key: string
    events: CrmTaskEvent[]
    error: string
  } | null>(null)
  const loading = result?.key !== key
  useEffect(() => {
    if (result?.key === key && result.error) showGlobalSnackbar(result.error, { variant: 'error' })
  }, [result, key])
  const events = !loading ? result.events : []
  useEffect(() => {
    let active = true
    void crmWorkApi
      .taskHistory(companyId, task.id)
      .then((events) => {
        if (active) setResult({ key, events, error: '' })
      })
      .catch((error) => {
        if (active)
          setResult({
            key,
            events: [],
            error: apiErrorMessage(error, 'Unable to load task history'),
          })
      })
    return () => {
      active = false
    }
  }, [companyId, task.id, key])
  return (
    <section className="panel overflow-hidden" aria-label="Task details and activity history">
      <div className="flex items-start justify-between gap-4 border-b border-slate-100 p-5">
        <div className="min-w-0">
          <p className="eyebrow">TASK DETAILS</p>
          <h2 className="mt-2 break-words text-lg font-bold text-slate-950">{task.title}</h2>
        </div>
        <button
          type="button"
          onClick={onClose}
          className="secondary-button p-2"
          aria-label="Close task details"
        >
          <FiX />
        </button>
      </div>
      <div className="p-5">
        <span
          className={`rounded-full px-3 py-1 text-xs font-semibold ${taskStatusClass(task.status)}`}
        >
          {stepName || taskLabel(task.status)}
        </span>
        <p className="mt-4 whitespace-pre-wrap break-words text-sm leading-6 text-slate-600">
          {task.details || 'No additional details.'}
        </p>
        <dl className="mt-5 grid grid-cols-2 gap-4 text-xs">
          <div>
            <dt className="text-slate-500">Assignee</dt>
            <dd className="mt-1 font-medium text-slate-900">{assigneeName}</dd>
          </div>
          <div>
            <dt className="text-slate-500">Priority</dt>
            <dd className="mt-1 font-medium text-slate-900">{taskLabel(task.priority)}</dd>
          </div>
          <div>
            <dt className="text-slate-500">Due</dt>
            <dd className="mt-1 font-medium text-slate-900">
              {task.dueAt ? leadDateTime(task.dueAt) : 'No due date'}
            </dd>
          </div>
          <div>
            <dt className="text-slate-500">Last updated</dt>
            <dd className="mt-1 font-medium text-slate-900">
              {task.updatedAt ? leadDateTime(task.updatedAt) : 'Unavailable'}
            </dd>
          </div>
          {task.createdAt && (
            <div>
              <dt className="text-slate-500">Created</dt>
              <dd className="mt-1 font-medium text-slate-900">{leadDateTime(task.createdAt)}</dd>
            </div>
          )}
        </dl>
        <div className="mt-5 flex gap-4 text-xs font-semibold text-indigo-600">
          {task.contactId && <Link to={`/contacts/${task.contactId}`}>Open contact</Link>}
          {task.accountId && <Link to={`/crm/companies/${task.accountId}`}>Open company</Link>}
        </div>
      </div>
      <div className="border-t border-slate-100 p-5">
        <div className="flex items-center justify-between gap-2">
          <h3 className="flex items-center gap-2 text-sm font-bold text-slate-950">
            <FiClock className="text-indigo-500" />
            Activity history
          </h3>
          <button
            type="button"
            className="secondary-button p-2"
            aria-label="Refresh task history"
            disabled={loading}
            onClick={() => setReload((value) => value + 1)}
          >
            <FiRefreshCw />
          </button>
        </div>
        <p className="mt-2 text-xs text-slate-500">
          Latest 100 changes · times in {Intl.DateTimeFormat().resolvedOptions().timeZone}
        </p>
        {loading ? (
          <ListSkeleton rows={3} />
        ) : result?.error ? (
          <p role="alert" className="mt-4 rounded-lg bg-rose-50 p-3 text-xs text-rose-700">
            {result.error}
          </p>
        ) : !events.length ? (
          <p className="py-6 text-sm text-slate-500">
            No recorded activity yet. Older changes were not tracked; future edits and status
            changes will appear here.
          </p>
        ) : (
          <ol className="mt-5 space-y-5">
            {events.map((event) => (
              <li key={event.id} className="relative border-l-2 border-indigo-100 pl-5">
                <span className="absolute -left-[7px] top-1 size-3 rounded-full border-2 border-white bg-indigo-500" />
                {event.eventType === 'STATUS_CHANGED' || event.eventType === 'STEP_CHANGED' ? (
                  <div className="flex flex-wrap items-center gap-2 text-xs">
                    <span className="text-slate-500">
                      {event.previousColumn || taskLabel(event.previousStatus || 'OPEN')}
                    </span>
                    <FiArrowRight className="text-slate-400" />
                    <span
                      className={`rounded-full px-2 py-1 font-semibold ${taskStatusClass(event.newStatus)}`}
                    >
                      {event.newColumn || taskLabel(event.newStatus)}
                    </span>
                  </div>
                ) : (
                  <div>
                    <p className="text-xs font-semibold text-slate-900">
                      {event.eventType === 'CREATED' ? 'Task created' : 'Task updated'}
                    </p>
                    {event.changedFields && (
                      <p className="mt-1 text-xs text-slate-500">Changed: {event.changedFields}</p>
                    )}
                  </div>
                )}
                <p className="mt-3 flex flex-wrap items-center gap-2 text-xs font-medium text-slate-800">
                  <FiUser className="text-slate-400" />
                  {event.actorName ||
                    event.actorUsername ||
                    (event.actorUserId ? `User ${event.actorUserId}` : 'Unknown actor')}
                  {event.actorName && event.actorUsername && (
                    <span className="font-normal text-slate-500">@{event.actorUsername}</span>
                  )}
                </p>
                <time dateTime={event.occurredAt} className="mt-1 block text-[11px] text-slate-500">
                  {leadDateTime(event.occurredAt)}
                </time>
              </li>
            ))}
          </ol>
        )}
      </div>
    </section>
  )
}
import { showGlobalSnackbar } from '../feedback/SnackbarProvider'
