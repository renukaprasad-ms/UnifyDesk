import { FiGrid, FiList } from 'react-icons/fi'
export default function BoardViewToggle({
  value,
  onChange,
}: {
  value: 'board' | 'list'
  onChange: (value: 'board' | 'list') => void
}) {
  return (
    <div
      className="inline-flex shrink-0 items-center gap-1 rounded-lg border border-slate-200 bg-slate-50 p-1"
      role="group"
      aria-label="View layout"
    >
      {(['board', 'list'] as const).map((view) => (
        <button
          key={view}
          type="button"
          aria-pressed={value === view}
          onClick={() => onChange(view)}
          className={`inline-flex items-center gap-2 rounded-md px-3 py-2 text-xs font-semibold transition-colors ${value === view ? 'bg-indigo-600 text-white shadow-sm' : 'text-slate-500 hover:bg-slate-100'}`}
        >
          {view === 'board' ? <FiGrid aria-hidden="true" /> : <FiList aria-hidden="true" />}
          {view === 'board' ? 'Board' : 'List'}
        </button>
      ))}
    </div>
  )
}
