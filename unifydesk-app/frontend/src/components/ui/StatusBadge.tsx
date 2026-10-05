import type { ReactNode } from 'react'

export type StatusTone = 'success' | 'info' | 'processing' | 'warning' | 'partial' | 'danger' | 'neutral' | 'accent'

const toneClasses: Record<StatusTone, string> = {
  success: 'bg-emerald-50 text-emerald-700 ring-emerald-600/15',
  info: 'bg-sky-50 text-sky-700 ring-sky-600/15',
  processing: 'bg-blue-50 text-blue-700 ring-blue-600/15',
  warning: 'bg-amber-50 text-amber-800 ring-amber-600/15',
  partial: 'bg-orange-50 text-orange-700 ring-orange-600/15',
  danger: 'bg-rose-50 text-rose-700 ring-rose-600/15',
  neutral: 'bg-slate-100 text-slate-600 ring-slate-500/15',
  accent: 'bg-violet-50 text-violet-700 ring-violet-600/15',
}

export function semanticStatusTone(status: string): StatusTone {
  const value = status.trim().toUpperCase().replaceAll(' ', '_')
  if (['ACTIVE', 'READY', 'HEALTHY', 'COMPLETED', 'DELIVERED', 'VERIFIED', 'APPROVED', 'SYNCED', 'SUCCESS', 'ACCEPTED', 'ACCEPTED_BY_PROVIDER'].includes(value)) return 'success'
  if (['RUNNING', 'PROCESSING', 'SENDING', 'SYNCING', 'PROVIDER_SYNCING', 'PREPARING', 'UPLOADING', 'RETRY', 'IN_PROGRESS'].includes(value)) return 'processing'
  if (['QUEUED', 'SCHEDULED', 'RECEIVED', 'PENDING', 'PENDING_DNS', 'PENDING_REVIEW', 'SENT'].includes(value)) return 'info'
  if (['PARTIALLY_FAILED'].includes(value)) return 'partial'
  if (['READ'].includes(value)) return 'accent'
  if (['WARNING', 'ACTION_REQUIRED', 'EXPIRED', 'THROTTLED', 'PAUSED', 'WAITING_EXTERNAL_ACTION', 'UNKNOWN'].includes(value)) return 'warning'
  if (['FAILED', 'ERROR', 'REJECTED', 'BOUNCED', 'COMPLAINT', 'SUSPENDED_POLICY'].includes(value)) return 'danger'
  if (['SUSPENDED'].includes(value)) return 'warning'
  if (['CANCELLED', 'SUPPRESSED', 'SKIPPED', 'DRAFT', 'DISABLED', 'DELETED', 'RETIRED'].includes(value)) return 'neutral'
  if (['OPENED'].includes(value)) return 'info'
  if (['CLICKED'].includes(value)) return 'accent'
  return 'neutral'
}

export default function StatusBadge({
  children,
  status,
  tone,
  className = '',
}: {
  children?: ReactNode
  status?: string
  tone?: StatusTone
  className?: string
}) {
  const label = String(children ?? status ?? '')
  const resolvedTone = tone ?? semanticStatusTone(status ?? label)
  return (
    <span className={`inline-flex items-center rounded-full px-2.5 py-1 text-[11px] font-semibold ring-1 ring-inset ${toneClasses[resolvedTone]} ${className}`}>
      {label}
    </span>
  )
}
