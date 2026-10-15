import { useState } from 'react'
import { FiPlus, FiClock, FiUser, FiMove, FiCheckSquare } from 'react-icons/fi'
import type { CrmTask, CrmTaskColumn } from '../../features/contacts/crmWorkApi'
import { taskLabel, taskStatusClass } from '../../features/contacts/taskPresentation'
import { taskTypeConfig } from '../../pages/contacts/CrmTaskDetailPage'

function formatMins(mins?: number | null) {
  if (!mins || mins <= 0) return '0m'
  const h = Math.floor(mins / 60)
  const m = mins % 60
  if (h > 0 && m > 0) return `${h}h ${m}m`
  if (h > 0) return `${h}h`
  return `${m}m`
}

export default function TaskBoard({
  tasks,
  columns,
  busy,
  pendingIds = new Set<string>(),
  canMove,
  onMove,
  onOpen,
  onEdit: _onEdit,
  onAssign: _onAssign,
  onNew,
  assigneeName,
}: {
  tasks: CrmTask[]
  columns: CrmTaskColumn[]
  pendingIds?: Set<string>
  busy: boolean
  canMove: boolean
  onMove: (task: CrmTask, key: string) => void
  onOpen: (task: CrmTask) => void
  onEdit?: (task: CrmTask) => void
  onAssign?: (task: CrmTask) => void
  onNew: (key: string) => void
  assigneeName: (task: CrmTask) => string
}) {
  const [dragged, setDragged] = useState<string | null>(null)
  const [over, setOver] = useState<string | null>(null)
  return (
    <div
      className="flex h-full min-h-0 items-stretch gap-4 overflow-x-auto pb-2"
      aria-label="Task board"
    >
      {columns.map((column) => {
        const items = tasks.filter((t) => (t.columnKey || t.status) === column.key)
        return (
          <section
            key={column.key}
            aria-label={column.name + ' column'}
            className={`flex h-full min-h-0 w-72 min-w-72 flex-1 flex-col overflow-hidden rounded-xl border bg-slate-50 p-3 transition-colors dark:bg-slate-900/60 ${over === column.key ? 'border-indigo-400 ring-2 ring-indigo-100 dark:border-indigo-500 dark:ring-indigo-900/30' : 'border-slate-200 dark:border-slate-800'}`}
            onDragOver={(e) => {
              if (canMove && !busy && dragged) {
                e.preventDefault()
                e.dataTransfer.dropEffect = 'move'
                setOver(column.key)
              }
            }}
            onDragLeave={(e) => {
              if (!e.currentTarget.contains(e.relatedTarget as Node)) setOver(null)
            }}
            onDrop={(e) => {
              e.preventDefault()
              const id = e.dataTransfer.getData('text/plain')
              const task = tasks.find((t) => t.id === id)
              if (
                canMove &&
                !busy &&
                task &&
                !pendingIds.has(task.id) &&
                (task.columnKey || task.status) !== column.key
              )
                onMove(task, column.key)
              setDragged(null)
              setOver(null)
            }}
          >
            <header className="mb-4 flex items-center justify-between gap-2">
              <div className="flex min-w-0 items-center gap-2">
                <span
                  className={`size-2 shrink-0 rounded-full ${column.statusGroup === 'DONE' ? 'bg-emerald-500' : column.statusGroup === 'IN_PROGRESS' ? 'bg-indigo-500' : column.statusGroup === 'CANCELLED' ? 'bg-rose-400' : 'bg-slate-400'}`}
                />
                <h2 className="truncate text-sm font-bold text-slate-900 dark:text-slate-100" title={column.name}>
                  {column.name}
                </h2>
                <span className="rounded-md bg-slate-200 px-2 text-xs font-semibold text-slate-600 dark:bg-slate-800 dark:text-slate-300">
                  {items.length}
                </span>
              </div>
              {canMove && (
                <button
                  type="button"
                  className="secondary-button p-1.5"
                  aria-label={'Create task in ' + column.name}
                  disabled={busy || pendingIds.size > 0}
                  onClick={() => onNew(column.key)}
                >
                  <FiPlus />
                </button>
              )}
            </header>
            <div className="min-h-0 flex-1 space-y-3 overflow-y-auto overscroll-contain pr-1">
              {items.map((task) => {
                const overdue =
                  task.dueAt &&
                  new Date(task.dueAt).getTime() < Date.now() &&
                  ['OPEN', 'IN_PROGRESS'].includes(task.status)
                const typeInfo = taskTypeConfig(task.taskType)
                return (
                  <article
                    key={task.id}
                    draggable={canMove && !busy && !pendingIds.has(task.id)}
                    aria-label={task.title}
                    aria-busy={pendingIds.has(task.id)}
                    onDragStart={(e) => {
                      e.dataTransfer.setData('text/plain', task.id)
                      e.dataTransfer.effectAllowed = 'move'
                      setDragged(task.id)
                    }}
                    onDragEnd={() => {
                      setDragged(null)
                      setOver(null)
                    }}
                    onClick={() => onOpen(task)}
                    className={`group cursor-pointer rounded-xl border border-slate-200/90 bg-white p-3.5 shadow-xs transition-all hover:border-indigo-300 hover:shadow-md dark:border-slate-800 dark:bg-slate-900 ${canMove ? 'cursor-grab active:cursor-grabbing' : ''} ${dragged === task.id ? 'opacity-40' : ''}`}
                  >
                    {/* Header tags: Key + Priority + Task Type */}
                    <div className="mb-2.5 flex items-center justify-between gap-1.5">
                      <div className="flex flex-wrap items-center gap-1.5">
                        <span className="font-mono text-[10px] font-bold text-indigo-600 dark:text-indigo-400">
                          TSK-{task.id.slice(-6)}
                        </span>
                        <span
                          className={`rounded px-1.5 py-0.5 text-[10px] font-semibold border ${typeInfo.color}`}
                        >
                          <span className="mr-1">{typeInfo.icon}</span>
                          {typeInfo.label}
                        </span>
                        <span
                          className={`rounded px-1.5 py-0.5 text-[10px] font-bold ${task.priority === 'HIGH' ? 'bg-rose-50 text-rose-600 dark:bg-rose-950/50 dark:text-rose-400' : task.priority === 'LOW' ? 'bg-slate-100 text-slate-500 dark:bg-slate-800 dark:text-slate-400' : 'bg-indigo-50 text-indigo-600 dark:bg-indigo-950/50 dark:text-indigo-400'}`}
                        >
                          {taskLabel(task.priority)}
                        </span>
                      </div>
                      {pendingIds.has(task.id) ? (
                        <span role="status" className="text-[10px] text-indigo-500 font-medium">
                          Saving...
                        </span>
                      ) : (
                        <FiMove className="text-slate-300 group-hover:text-slate-400 transition-colors" />
                      )}
                    </div>

                    {/* Title */}
                    <h3 className="break-words text-sm font-semibold text-slate-900 group-hover:text-indigo-600 dark:text-slate-100 dark:group-hover:text-indigo-400 transition-colors">
                      {task.title}
                    </h3>

                    {/* Details / description excerpt */}
                    {task.details && (
                      <p className="mt-1.5 line-clamp-2 break-words text-xs leading-relaxed text-slate-500 dark:text-slate-400">
                        {task.details}
                      </p>
                    )}

                    {/* Jira Badges: Deal, Lead, Company, Subtasks & Work Log */}
                    <div className="mt-2.5 flex flex-wrap gap-1.5">
                      {task.dealTitle && (
                        <span
                          className="inline-flex items-center gap-1 rounded bg-emerald-50 px-2 py-0.5 text-[11px] font-medium text-emerald-700 dark:bg-emerald-950/40 dark:text-emerald-300"
                          title={`Linked Deal: ${task.dealTitle}`}
                        >
                          <span className="text-[10px]">🏷️</span>
                          <span className="truncate max-w-[130px]">{task.dealTitle}</span>
                        </span>
                      )}
                      {task.contactName && (
                        <span
                          className="inline-flex items-center gap-1 rounded bg-sky-50 px-2 py-0.5 text-[11px] font-medium text-sky-700 dark:bg-sky-950/40 dark:text-sky-300"
                          title={`Linked Lead: ${task.contactName}`}
                        >
                          <span className="text-[10px]">👤</span>
                          <span className="truncate max-w-[130px]">{task.contactName}</span>
                        </span>
                      )}
                      {task.accountName && (
                        <span
                          className="inline-flex items-center gap-1 rounded bg-violet-50 px-2 py-0.5 text-[11px] font-medium text-violet-700 dark:bg-violet-950/40 dark:text-violet-300"
                          title={`Linked Company: ${task.accountName}`}
                        >
                          <span className="text-[10px]">🏢</span>
                          <span className="truncate max-w-[130px]">{task.accountName}</span>
                        </span>
                      )}
                      {(task.subtaskCount || 0) > 0 && (
                        <span
                          className="inline-flex items-center gap-1 rounded bg-slate-100 px-2 py-0.5 text-[11px] font-medium text-slate-700 dark:bg-slate-800 dark:text-slate-300"
                          title="Checklist Subtasks completed"
                        >
                          <FiCheckSquare className="h-3 w-3 text-indigo-500" />
                          <span>
                            {task.subtasksCompletedCount || 0}/{task.subtaskCount}
                          </span>
                        </span>
                      )}
                      {((task.estimatedMinutes || 0) > 0 || (task.loggedMinutes || 0) > 0) && (
                        <span
                          className="inline-flex items-center gap-1 rounded bg-amber-50 px-2 py-0.5 text-[11px] font-medium text-amber-700 dark:bg-amber-950/40 dark:text-amber-300"
                          title="Time tracking"
                        >
                          <FiClock className="h-3 w-3" />
                          <span>
                            {formatMins(task.loggedMinutes)} / {formatMins(task.estimatedMinutes)}
                          </span>
                        </span>
                      )}
                    </div>

                    {/* Due Date & Assignee row */}
                    <div className="mt-3 flex items-center justify-between border-t border-slate-100 pt-2.5 text-xs text-slate-500 dark:border-slate-800 dark:text-slate-400">
                      <div className="flex items-center gap-1.5 min-w-0">
                        <FiUser className="shrink-0 text-slate-400" />
                        <span className="truncate font-medium text-slate-700 dark:text-slate-300">
                          {assigneeName(task)}
                        </span>
                      </div>
                      {task.dueAt && (
                        <p
                          className={`flex items-center gap-1 shrink-0 font-medium ${overdue ? 'text-rose-600 dark:text-rose-400' : 'text-slate-500 dark:text-slate-400'}`}
                        >
                          <FiClock />
                          {new Date(task.dueAt).toLocaleDateString()}
                        </p>
                      )}
                    </div>

                    {/* Quick Move Status Selector */}
                    {canMove && (
                      <div
                        className="mt-2.5"
                        onClick={(e) => e.stopPropagation()}
                      >
                        <select
                          aria-label={'Move ' + task.title + ' to step'}
                          className={`form-input w-full text-xs py-1.5 rounded-lg border-slate-200 dark:border-slate-700 bg-slate-50/50 dark:bg-slate-800/50 ${taskStatusClass(task.status)}`}
                          value={task.columnKey || task.status}
                          disabled={busy || pendingIds.has(task.id)}
                          onChange={(e) => onMove(task, e.target.value)}
                        >
                          {columns.map((c) => (
                            <option key={c.key} value={c.key}>
                              {c.name}
                            </option>
                          ))}
                        </select>
                      </div>
                    )}
                  </article>
                )
              })}
              {!items.length && (
                <p className="rounded-lg border border-dashed border-slate-300 px-3 py-8 text-center text-xs text-slate-400 dark:border-slate-800">
                  {canMove ? 'Drop a task here or create one' : 'No tasks in this step'}
                </p>
              )}
            </div>
          </section>
        )
      })}
    </div>
  )
}
