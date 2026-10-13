import { useState } from 'react'
import { FiPlus, FiClock, FiUser, FiMove } from 'react-icons/fi'
import type { CrmTask, CrmTaskColumn } from '../../features/contacts/crmWorkApi'
import { taskLabel, taskStatusClass } from '../../features/contacts/taskPresentation'

export default function TaskBoard({
  tasks,
  columns,
  busy,
  pendingIds = new Set<string>(),
  canMove,
  onMove,
  onOpen,
  onEdit,
  onAssign,
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
  onEdit: (task: CrmTask) => void
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
            className={`flex h-full min-h-0 w-72 min-w-72 flex-1 flex-col overflow-hidden rounded-xl border bg-slate-50 p-3 transition-colors ${over === column.key ? 'border-indigo-400 ring-2 ring-indigo-100' : 'border-slate-200'}`}
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
                <h2 className="truncate text-sm font-bold" title={column.name}>
                  {column.name}
                </h2>
                <span className="rounded-md bg-slate-200 px-2 text-xs text-slate-600">
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
                    className={`rounded-lg border border-slate-200 bg-white p-4 shadow-sm transition-shadow hover:shadow-md ${canMove ? 'cursor-grab active:cursor-grabbing' : ''} ${dragged === task.id ? 'opacity-40' : ''}`}
                  >
                    <div className="mb-3 flex items-center justify-between">
                      <span
                        className={`rounded px-2 py-1 text-[10px] font-bold ${task.priority === 'HIGH' ? 'bg-rose-50 text-rose-600' : task.priority === 'LOW' ? 'bg-slate-100 text-slate-500' : 'bg-indigo-50 text-indigo-600'}`}
                      >
                        {taskLabel(task.priority)} priority
                      </span>
                      {pendingIds.has(task.id) ? (
                        <span role="status" className="text-[10px] text-indigo-500">
                          Saving...
                        </span>
                      ) : (
                        <FiMove className="text-slate-400" />
                      )}
                    </div>
                    <button
                      type="button"
                      onClick={() => onOpen(task)}
                      className="w-full break-words text-left text-sm font-semibold hover:text-indigo-600"
                    >
                      {task.title}
                    </button>
                    {task.details && (
                      <p className="mt-2 line-clamp-2 break-words text-xs leading-5 text-slate-500">
                        {task.details}
                      </p>
                    )}
                    {task.dueAt && (
                      <p
                        className={`mt-3 flex items-center gap-1.5 text-xs ${overdue ? 'text-rose-600' : 'text-slate-500'}`}
                      >
                        <FiClock />
                        {overdue ? 'Overdue - ' : ''}
                        {new Date(task.dueAt).toLocaleDateString()}
                      </p>
                    )}
                    <div className="mt-3 flex items-center gap-2 border-t border-slate-100 pt-3 text-xs text-slate-500">
                      <FiUser />
                      <span className="truncate">{assigneeName(task)}</span>
                    </div>
                    {canMove && (
                      <div className="mt-3 flex gap-2">
                        <select
                          aria-label={'Move ' + task.title + ' to step'}
                          className={`form-input min-w-0 flex-1 text-xs ${taskStatusClass(task.status)}`}
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
                        <button
                          className="secondary-button text-xs"
                          type="button"
                          disabled={busy || pendingIds.size > 0}
                          onClick={() => onEdit(task)}
                        >
                          Edit
                        </button>
                      </div>
                    )}
                    <div className="mt-3 flex flex-wrap gap-2 border-t border-slate-100 pt-3">
                      {canMove && onAssign && (
                        <button
                          type="button"
                          className="secondary-button gap-1 px-2 py-1.5 text-xs"
                          aria-label={'Assign ' + task.title}
                          disabled={busy || pendingIds.size > 0}
                          onClick={() => onAssign(task)}
                        >
                          <FiUser />
                          Assign
                        </button>
                      )}
                      <button
                        type="button"
                        className="secondary-button gap-1 px-2 py-1.5 text-xs"
                        aria-label={'View history for ' + task.title}
                        onClick={() => onOpen(task)}
                      >
                        <FiClock />
                        View history
                      </button>
                    </div>
                  </article>
                )
              })}
              {!items.length && (
                <p className="rounded-lg border border-dashed border-slate-300 px-3 py-8 text-center text-xs text-slate-400">
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
