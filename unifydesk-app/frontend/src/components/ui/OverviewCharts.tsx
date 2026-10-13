import { useId, useState } from 'react'
import { FiActivity, FiArrowUpRight } from 'react-icons/fi'

const colors = ['#6366f1', '#06b6d4', '#10b981', '#f59e0b', '#a855f7', '#f43f5e']
export type ChartDatum = { label: string; value: number }

export function BreakdownChart({
  title,
  description,
  items,
  donut = false,
}: {
  title: string
  description: string
  items: ChartDatum[]
  donut?: boolean
}) {
  const total = items.reduce((sum, item) => sum + item.value, 0)
  const maximum = Math.max(1, ...items.map((item) => item.value))
  return (
    <section className="panel min-w-0 p-5 sm:p-6">
      <h2 className="text-base font-bold text-slate-950">{title}</h2>
      <p className="mt-1 text-xs leading-5 text-slate-500">{description}</p>
      <div
        className={
          donut ? 'mt-6 flex flex-wrap items-center justify-center gap-7' : 'mt-7 space-y-5'
        }
      >
        {donut && (
          <div className="relative size-40 shrink-0">
            <svg
              viewBox="0 0 160 160"
              className="size-full -rotate-90"
              role="img"
              aria-label={`${title}: ${items.map((item) => `${item.label} ${item.value}`).join(', ')}`}
            >
              <circle
                cx="80"
                cy="80"
                r="62"
                fill="none"
                stroke="currentColor"
                strokeWidth="16"
                className="text-slate-100"
              />
              {items.map((item, index) => {
                const length = total ? (item.value / total) * 389.56 : 0
                const start = total
                  ? (items.slice(0, index).reduce((sum, previous) => sum + previous.value, 0) /
                      total) *
                    389.56
                  : 0
                return (
                  <circle
                    key={item.label}
                    cx="80"
                    cy="80"
                    r="62"
                    fill="none"
                    stroke={colors[index % colors.length]}
                    strokeWidth="16"
                    strokeDasharray={`${length} ${389.56 - length}`}
                    strokeDashoffset={-start}
                  >
                    <title>
                      {item.label}: {item.value.toLocaleString()}
                    </title>
                  </circle>
                )
              })}
            </svg>
            <div className="absolute inset-0 flex flex-col items-center justify-center">
              <span className="text-3xl font-bold tracking-tight text-slate-950">
                {total.toLocaleString()}
              </span>
              <span className="mt-1 text-xs text-slate-500">Total leads</span>
            </div>
          </div>
        )}
        <ul className={donut ? 'min-w-40 flex-1 space-y-4' : 'space-y-5'}>
          {items.map((item, index) => (
            <li key={item.label}>
              <div className="flex items-center justify-between gap-4 text-xs">
                <span className="flex items-center gap-2 text-slate-600">
                  <span
                    className="size-2 rounded-full"
                    style={{ background: colors[index % colors.length] }}
                  />
                  {item.label}
                </span>
                <span className="font-bold tabular-nums text-slate-950">
                  {item.value.toLocaleString()}
                  {donut && (
                    <span className="ml-2 font-normal text-slate-400">
                      {total ? Math.round((item.value / total) * 100) : 0}%
                    </span>
                  )}
                </span>
              </div>
              {!donut && (
                <div className="mt-2 h-2 overflow-hidden rounded-full bg-slate-100">
                  <div
                    className="h-full rounded-full transition-all duration-300"
                    style={{
                      width: `${(item.value / maximum) * 100}%`,
                      background: colors[index % colors.length],
                    }}
                  />
                </div>
              )}
            </li>
          ))}
        </ul>
      </div>
      {total === 0 && (
        <p className="mt-5 border-t border-slate-100 pt-4 text-xs text-slate-500">
          No activity yet. Your chart will update as data arrives.
        </p>
      )}
    </section>
  )
}

export function GrowthChart({
  points,
  loading,
  unavailable = false,
}: {
  points: { date: string; added: number }[]
  loading: boolean
  unavailable?: boolean
}) {
  const [days, setDays] = useState(30)
  const [selected, setSelected] = useState<number | null>(null)
  const id = useId().replaceAll(':', '')
  const data = points.slice(-days)
  const max = Math.max(1, ...data.map((point) => point.added))
  const total = data.reduce((sum, point) => sum + point.added, 0)
  const coords = data.map((point, index) => ({
    x: 42 + (index / Math.max(1, data.length - 1)) * 610,
    y: 185 - (point.added / max) * 145,
  }))
  const path = coords.map((point, index) => `${index ? 'L' : 'M'}${point.x},${point.y}`).join(' ')
  const active = selected === null ? null : data[selected]
  return (
    <section className="panel min-w-0 p-5 sm:p-6">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h2 className="flex items-center gap-2 text-base font-bold text-slate-950">
            <FiActivity className="text-indigo-500" />
            Audience growth
          </h2>
          <p className="mt-1 text-xs text-slate-500">New contacts added to your workspace</p>
        </div>
        <div className="flex rounded-lg bg-slate-100 p-1" aria-label="Chart period">
          {[7, 30].map((period) => (
            <button
              key={period}
              type="button"
              aria-pressed={days === period}
              onClick={() => {
                setDays(period)
                setSelected(null)
              }}
              className={`rounded-md px-3 py-1.5 text-xs font-semibold transition ${days === period ? 'bg-white text-indigo-600 shadow-sm' : 'text-slate-500'}`}
            >
              {period} days
            </button>
          ))}
        </div>
      </div>
      <div className="mt-5 flex flex-wrap items-end gap-3">
        <span className="text-3xl font-bold tracking-tight text-slate-950">
          {loading || unavailable ? '—' : total.toLocaleString()}
        </span>
        <span className="mb-1 flex items-center gap-1 rounded-full bg-indigo-50 px-2.5 py-1 text-[11px] font-semibold text-indigo-600">
          <FiArrowUpRight />
          New contacts · {days} days
        </span>
      </div>
      <div className="mt-2 min-h-5 text-xs text-slate-500" aria-live="polite">
        {loading
          ? 'Loading growth…'
          : unavailable
            ? 'Growth data is unavailable. Try refreshing.'
            : active
              ? `${active.date} · ${active.added.toLocaleString()} new contacts`
              : total
                ? 'Hover or focus a point to explore daily growth.'
                : 'No new contacts yet. Import your audience to get started.'}
      </div>
      <svg
        viewBox="0 0 680 215"
        className="mt-3 w-full"
        role="group"
        aria-label="Daily new contacts chart"
      >
        <defs>
          <linearGradient id={id} x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor="#6366f1" stopOpacity=".25" />
            <stop offset="100%" stopColor="#6366f1" stopOpacity="0" />
          </linearGradient>
        </defs>
        {[0, 1, 2, 3].map((index) => (
          <g key={index}>
            <line
              x1="42"
              x2="652"
              y1={40 + (index * 145) / 3}
              y2={40 + (index * 145) / 3}
              stroke="currentColor"
              className="text-slate-200"
              strokeDasharray="3 5"
            />
            <text
              x="30"
              y={44 + (index * 145) / 3}
              textAnchor="end"
              fill="currentColor"
              className="text-slate-400"
              fontSize="10"
            >
              {(max * (1 - index / 3)).toLocaleString(undefined, { maximumFractionDigits: 1 })}
            </text>
          </g>
        ))}
        {!loading && !unavailable && (
          <>
            <path d={`${path} L652,185 L42,185 Z`} fill={`url(#${id})`} />
            <path
              d={path}
              fill="none"
              stroke="#6366f1"
              strokeWidth="3"
              strokeLinejoin="round"
              strokeLinecap="round"
            />
            {coords.map((point, index) => (
              <g key={data[index].date}>
                <circle cx={point.x} cy={point.y} r={selected === index ? 5 : 3} fill="#6366f1" />
                <circle
                  cx={point.x}
                  cy={point.y}
                  r="10"
                  fill="transparent"
                  tabIndex={0}
                  role="img"
                  aria-label={`${data[index].date}: ${data[index].added} new contacts`}
                  onMouseEnter={() => setSelected(index)}
                  onMouseLeave={() => setSelected(null)}
                  onFocus={() => setSelected(index)}
                  onBlur={() => setSelected(null)}
                  className="outline-indigo-500"
                />
              </g>
            ))}
          </>
        )}
        <text x="42" y="210" fill="currentColor" className="text-slate-400" fontSize="10">
          {data[0]?.date}
        </text>
        <text
          x="652"
          y="210"
          textAnchor="end"
          fill="currentColor"
          className="text-slate-400"
          fontSize="10"
        >
          Today
        </text>
      </svg>
    </section>
  )
}
