import { useState } from 'react'
import { FiArrowLeft, FiArrowRight, FiTrash2 } from 'react-icons/fi'
import type { CrmTask, CrmTaskColumn } from '../../features/contacts/crmWorkApi'
const groups: { key: CrmTask['status']; label: string }[] = [
  { key: 'OPEN', label: 'To do' },
  { key: 'IN_PROGRESS', label: 'In progress' },
  { key: 'DONE', label: 'Done' },
  { key: 'CANCELLED', label: 'Cancelled' },
]
export default function TaskSteps({
  columns,
  busy,
  onCreate,
  onRename,
  onReorder,
  onRemove,
}: {
  columns: CrmTaskColumn[]
  busy: boolean
  onCreate: (name: string, group: CrmTask['status']) => Promise<boolean>
  onRename: (column: CrmTaskColumn, name: string) => void
  onReorder: (columns: CrmTaskColumn[]) => void
  onRemove: (column: CrmTaskColumn) => void
}) {
  const [name, setName] = useState('')
  const [group, setGroup] = useState<CrmTask['status']>('IN_PROGRESS')
  const [names, setNames] = useState<Record<string, string>>({})
  const move = (index: number, delta: number) => {
    const next = [...columns]
    ;[next[index], next[index + delta]] = [next[index + delta], next[index]]
    onReorder(next)
  }
  return (
    <section className="panel space-y-4 p-5" aria-label="Manage task steps">
      <div>
        <h2 className="font-bold">Workspace steps</h2>
        <p className="mt-1 text-xs text-slate-500">
          Give steps names that fit your team. Status groups keep reminders and reports consistent.
          Remove custom steps after moving their tasks elsewhere.
        </p>
      </div>
      <div className="space-y-2">
        {columns.map((c, index) => (
          <div
            key={c.key}
            className="flex flex-wrap items-center gap-2 rounded-lg border border-slate-200 p-2"
          >
            <input
              aria-label={'Name for ' + c.name}
              className="form-input min-w-0 flex-1"
              value={names[c.key] ?? c.name}
              maxLength={80}
              disabled={busy}
              onChange={(e) => setNames({ ...names, [c.key]: e.target.value })}
            />
            <span className="px-2 text-xs text-slate-500">
              {groups.find((g) => g.key === c.statusGroup)?.label}
            </span>
            <button
              type="button"
              className="secondary-button"
              disabled={
                busy ||
                !(names[c.key] ?? c.name).trim() ||
                (names[c.key] ?? c.name).trim() === c.name
              }
              onClick={() => onRename(c, (names[c.key] ?? c.name).trim())}
            >
              Save name
            </button>
            <button
              type="button"
              className="secondary-button p-2"
              aria-label={'Move ' + c.name + ' left'}
              disabled={busy || index === 0}
              onClick={() => move(index, -1)}
            >
              <FiArrowLeft />
            </button>
            <button
              type="button"
              className="secondary-button p-2"
              aria-label={'Move ' + c.name + ' right'}
              disabled={busy || index === columns.length - 1}
              onClick={() => move(index, 1)}
            >
              <FiArrowRight />
            </button>
            {!c.systemColumn && (
              <button
                type="button"
                className="secondary-button p-2 text-rose-600"
                aria-label={'Remove ' + c.name}
                disabled={busy}
                onClick={() => onRemove(c)}
              >
                <FiTrash2 />
              </button>
            )}
          </div>
        ))}
      </div>
      <form
        className="flex flex-wrap items-end gap-3 border-t border-slate-100 pt-4"
        onSubmit={(e) => {
          e.preventDefault()
          void onCreate(name.trim(), group).then((ok) => {
            if (ok) setName('')
          })
        }}
      >
        <label className="text-xs">
          Step name
          <input
            className="form-input mt-1 block"
            required
            maxLength={80}
            placeholder="Review"
            value={name}
            disabled={busy}
            onChange={(e) => setName(e.target.value)}
          />
        </label>
        <label className="text-xs">
          Status group
          <select
            className="form-input mt-1 block"
            value={group}
            disabled={busy}
            onChange={(e) => setGroup(e.target.value as CrmTask['status'])}
          >
            {groups.map((g) => (
              <option key={g.key} value={g.key}>
                {g.label}
              </option>
            ))}
          </select>
        </label>
        <button
          type="submit"
          className="primary-button"
          disabled={busy || !name.trim() || columns.length >= 20}
        >
          Add step
        </button>
        <span className="text-xs text-slate-400">{columns.length}/20 steps</span>
      </form>
    </section>
  )
}
