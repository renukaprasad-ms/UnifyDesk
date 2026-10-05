export default function ProgressBar({
  value,
  label,
  showValue = false,
  className = '',
}: {
  value: number
  label?: string
  showValue?: boolean
  className?: string
}) {
  const bounded = Math.min(100, Math.max(0, Math.round(value)))
  return (
    <div className={className}>
      {(label || showValue) && (
        <div className="mb-1.5 flex items-center justify-between gap-3 text-xs">
          <span className="font-medium text-slate-600">{label}</span>
          {showValue && <span className="font-semibold tabular-nums text-slate-700">{bounded}%</span>}
        </div>
      )}
      <div
        className="h-2 overflow-hidden rounded-full bg-slate-100"
        role="progressbar"
        aria-valuemin={0}
        aria-valuemax={100}
        aria-valuenow={bounded}
        aria-label={label ?? 'Progress'}
      >
        <div className="h-full rounded-full bg-blue-500 transition-[width]" style={{ width: `${bounded}%` }} />
      </div>
    </div>
  )
}
