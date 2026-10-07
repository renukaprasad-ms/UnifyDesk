import { useEffect, useMemo, useState } from 'react'
import type { AsyncJob } from '../../features/asyncJob/types'
import { realtimeClient } from '../../features/realtime/realtimeClient'
import ProgressBar from '../ui/ProgressBar'
import StatusBadge from '../ui/StatusBadge'

type DisplayJob = AsyncJob & {
  displayedUnits: number
  targetUnits: number
  receivedCompletedAt?: number
}

const SYNC_TYPES = new Set(['GMAIL_SYNC', 'MICROSOFT_SYNC', 'ZOHO_MAIL_SYNC', 'IMAP_SMTP_SYNC'])
const COMPLETED_RETENTION_MS = 25_000
const ANIMATION_TICK_MS = 50
const MAX_CATCH_UP_MS = 1_000
const ANIMATION_FRAMES = Math.max(1, Math.floor(MAX_CATCH_UP_MS / ANIMATION_TICK_MS))

const nextDisplayedUnits = (displayedUnits: number, targetUnits: number) => {
  const delta = Math.max(0, targetUnits - displayedUnits)
  if (delta === 0) return displayedUnits

  const step = delta <= 25 ? 1 : Math.max(1, Math.ceil(delta / ANIMATION_FRAMES))
  return Math.min(targetUnits, displayedUnits + step)
}

const labelFor = (job: AsyncJob) => {
  if (job.jobType === 'GMAIL_SYNC') return 'Gmail sync'
  if (job.jobType === 'MICROSOFT_SYNC') return 'Microsoft sync'
  if (job.jobType === 'ZOHO_MAIL_SYNC') return 'Zoho Mail sync'
  if (job.jobType === 'IMAP_SMTP_SYNC') return 'Email sync'
  return 'Sync'
}

export default function GlobalSyncProgress() {
  const [jobs, setJobs] = useState<Record<string, DisplayJob>>({})
  const [collapsed, setCollapsed] = useState(false)

  useEffect(
    () =>
      realtimeClient.subscribe<AsyncJob>('async.job.progress', (event) => {
        const incoming = event.payload
        if (!incoming || !SYNC_TYPES.has(incoming.jobType)) return

        setJobs((current) => {
          const existing = current[incoming.jobId]
          const reportedCompleted = Math.max(0, incoming.completedUnits ?? 0)
          const reportedTotal = Math.max(0, incoming.totalUnits ?? 0)
          const targetUnits =
            incoming.status === 'COMPLETED' && reportedTotal > 0
              ? Math.max(existing?.targetUnits ?? 0, reportedTotal)
              : Math.max(existing?.targetUnits ?? 0, reportedCompleted)
          const displayedUnits = Math.min(existing?.displayedUnits ?? 0, targetUnits)
          const terminal = incoming.status === 'COMPLETED' || incoming.status === 'FAILED'
          return {
            ...current,
            [incoming.jobId]: {
              ...incoming,
              displayedUnits,
              targetUnits,
              receivedCompletedAt: terminal
                ? existing?.receivedCompletedAt ?? Date.now()
                : undefined,
            },
          }
        })
      }),
    [],
  )

  useEffect(() => {
    const timer = window.setInterval(() => {
      const now = Date.now()
      setJobs((current) => {
        let changed = false
        const next: Record<string, DisplayJob> = {}

        for (const [jobId, job] of Object.entries(current)) {
          const displayedUnits = nextDisplayedUnits(job.displayedUnits, job.targetUnits)
          if (displayedUnits !== job.displayedUnits) changed = true

          const terminal = job.status === 'COMPLETED' || job.status === 'FAILED'
          const settled = displayedUnits >= job.targetUnits
          const expired =
            terminal &&
            settled &&
            job.receivedCompletedAt !== undefined &&
            now - job.receivedCompletedAt >= COMPLETED_RETENTION_MS

          if (expired) {
            changed = true
            continue
          }

          next[jobId] = { ...job, displayedUnits }
        }

        return changed ? next : current
      })
    }, ANIMATION_TICK_MS)
    return () => window.clearInterval(timer)
  }, [])

  const visibleJobs = useMemo(
    () =>
      Object.values(jobs).sort(
        (left, right) =>
          Date.parse(right.updatedAt || right.createdAt) - Date.parse(left.updatedAt || left.createdAt),
      ),
    [jobs],
  )

  if (visibleJobs.length === 0) return null

  return (
    <aside className="fixed bottom-4 right-4 z-[70] w-[min(24rem,calc(100vw-2rem))] overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-2xl">
      <button
        type="button"
        className="flex w-full items-center justify-between gap-3 border-b border-slate-100 px-4 py-3 text-left"
        onClick={() => setCollapsed((value) => !value)}
        aria-expanded={!collapsed}
      >
        <span>
          <span className="block text-sm font-bold text-slate-950">
            {visibleJobs.some((job) => job.status === 'QUEUED' || job.status === 'RUNNING')
              ? 'Sync in progress'
              : 'Sync finished'}
          </span>
          <span className="mt-0.5 block text-[11px] text-slate-500">
            {visibleJobs.length} {visibleJobs.length === 1 ? 'job' : 'jobs'}
          </span>
        </span>
        <span className="text-xs font-semibold text-slate-500">{collapsed ? 'Show' : 'Hide'}</span>
      </button>

      {!collapsed && (
        <div className="max-h-[22rem] space-y-3 overflow-y-auto p-3">
          {visibleJobs.map((job) => {
            const total = Math.max(0, job.totalUnits ?? 0)
            const displayed = Math.min(job.displayedUnits, total || job.displayedUnits)
            const progress =
              total > 0
                ? Math.min(100, Math.round((displayed / total) * 100))
                : job.status === 'COMPLETED'
                  ? 100
                  : Math.max(0, Math.min(100, job.progressPercent ?? 0))

            return (
              <div key={job.jobId} className="rounded-xl border border-slate-100 bg-slate-50 p-3">
                <div className="flex items-start justify-between gap-3">
                  <div className="min-w-0">
                    <p className="text-sm font-semibold text-slate-900">{labelFor(job)}</p>
                    <p className="mt-0.5 truncate text-[11px] text-slate-500">
                      {job.statusMessage || 'Processing mailbox'}
                    </p>
                  </div>
                  <StatusBadge status={job.status}>{job.status.replaceAll('_', ' ')}</StatusBadge>
                </div>

                {total > 0 && (
                  <div className="mt-2 flex items-center justify-between gap-3 text-[11px] text-slate-500">
                    <span className="tabular-nums">
                      {displayed.toLocaleString()} of {total.toLocaleString()} processed
                    </span>
                    <span className="font-bold tabular-nums text-slate-700">{progress}%</span>
                  </div>
                )}

                <ProgressBar className="mt-2" value={progress} label={labelFor(job)} />
                {job.errorMessage && (
                  <p className="mt-2 text-xs text-red-600">{job.errorMessage}</p>
                )}
              </div>
            )
          })}
        </div>
      )}
    </aside>
  )
}
