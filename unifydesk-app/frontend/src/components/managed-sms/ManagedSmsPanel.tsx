import { useCallback, useEffect, useLayoutEffect, useRef, useState } from 'react'
import { FiActivity, FiMessageSquare, FiRefreshCw, FiShield } from 'react-icons/fi'
import { managedSmsApi } from '../../features/managedSms/managedSmsApi'
import { managedSmsErrorMessage, managedSmsStateDescription } from '../../features/managedSms/copy'
import { isManagedSmsTenantCurrent } from '../../features/managedSms/tenantScope'
import type {
  ManagedSmsDeliveryHealth,
  ManagedSmsStatus,
  ManagedSmsUsage,
} from '../../features/managedSms/types'
import { useSnackbar } from '../feedback/SnackbarProvider'
import ManagedSmsStatusBadge from './ManagedSmsStatusBadge'
import { CardSkeleton } from '../ui/Skeleton'

export default function ManagedSmsPanel({
  companyId,
  canModify,
  refreshToken = 0,
  onOpenSetup,
}: {
  companyId: string
  canModify: boolean
  refreshToken?: number
  onOpenSetup: () => void
}) {
  const snackbar = useSnackbar()
  const [status, setStatus] = useState<ManagedSmsStatus | null>(null)
  const [usage, setUsage] = useState<ManagedSmsUsage | null>(null)
  const [health, setHealth] = useState<ManagedSmsDeliveryHealth | null>(null)
  const [loading, setLoading] = useState(true)
  const currentCompanyId = useRef(companyId)
  const requestGeneration = useRef(0)

  useLayoutEffect(() => {
    if (currentCompanyId.current === companyId) return
    currentCompanyId.current = companyId
    requestGeneration.current += 1
    setLoading(true)
    setStatus(null)
    setUsage(null)
    setHealth(null)
  }, [companyId])

  const load = useCallback(async () => {
    const requestCompanyId = companyId
    if (!isManagedSmsTenantCurrent(requestCompanyId, currentCompanyId.current)) return

    const generation = ++requestGeneration.current
    const isCurrentRequest = () =>
      isManagedSmsTenantCurrent(requestCompanyId, currentCompanyId.current) &&
      generation === requestGeneration.current

    setLoading(true)
    setStatus(null)
    setUsage(null)
    setHealth(null)
    try {
      const nextStatus = await managedSmsApi.status(requestCompanyId)
      if (!isCurrentRequest()) return
      setStatus(nextStatus)
      const [nextUsage, nextHealth] = await Promise.allSettled([
        managedSmsApi.usage(requestCompanyId),
        managedSmsApi.deliveryHealth(requestCompanyId),
      ])
      if (!isCurrentRequest()) return
      setUsage(nextUsage.status === 'fulfilled' ? nextUsage.value : null)
      setHealth(nextHealth.status === 'fulfilled' ? nextHealth.value : null)
    } catch (error) {
      if (!isCurrentRequest()) return
      setStatus(null)
      setUsage(null)
      setHealth(null)
      snackbar.error(managedSmsErrorMessage(error, 'Unable to load Managed SMS status.'))
    } finally {
      if (isCurrentRequest()) {
        setLoading(false)
      }
    }
  }, [companyId, snackbar])

  useEffect(() => {
    void load()
  }, [load, refreshToken])

  return (
    <div className="panel mt-6 overflow-hidden">
      <div className="flex flex-col gap-3 border-b border-slate-100 px-5 py-4 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <div className="flex flex-wrap items-center gap-2">
            <h2 className="text-sm font-bold text-slate-950">UnifyDesk Managed SMS</h2>
            <span className="rounded-full bg-indigo-600 px-2 py-0.5 text-[9px] font-bold text-white">
              RECOMMENDED
            </span>
            {status && <ManagedSmsStatusBadge state={status.state} />}
          </div>
          <p className="mt-1 max-w-2xl text-xs leading-5 text-slate-500">
            Use your own DLT-approved business identity, Headers and content templates while
            UnifyDesk operates the sending infrastructure.
          </p>
        </div>
        <div className="flex gap-2">
          <button
            type="button"
            className="secondary-button inline-flex items-center gap-2"
            onClick={() => void load()}
            disabled={loading}
          >
            <FiRefreshCw aria-hidden="true" />
            Refresh
          </button>
          {canModify && (
            <button type="button" className="primary-button" onClick={onOpenSetup}>
              {status?.state === 'READY' ? 'Manage setup' : 'Continue setup'}
            </button>
          )}
        </div>
      </div>

      {loading && !status ? (
        <div className="p-5"><CardSkeleton rows={5} /></div>
      ) : !status ? (
        <div className="px-5 py-8 text-center">
          <p className="text-sm font-semibold text-slate-800">Managed SMS is not configured yet.</p>
          <p className="mt-1 text-xs text-slate-500">
            Start with your DLT Principal Entity details. You never need to enter provider credentials.
          </p>
        </div>
      ) : (
        <div className="grid gap-3 p-5 md:grid-cols-3">
          <div className="rounded-xl border border-slate-200 p-4">
            <FiShield className="text-indigo-500" aria-hidden="true" />
            <p className="mt-3 text-xs font-bold text-slate-900">Compliance setup</p>
            <p className="mt-1 text-[11px] leading-5 text-slate-500">
              {status.nextAction || managedSmsStateDescription(status.state)}
            </p>
            <p className="mt-3 text-[11px] text-slate-500">
              {status.readyHeaderCount} ready Header{status.readyHeaderCount === 1 ? '' : 's'} ·{' '}
              {status.readyTemplateCount} ready template{status.readyTemplateCount === 1 ? '' : 's'}
            </p>
          </div>

          <div className="rounded-xl border border-slate-200 p-4">
            <FiMessageSquare className="text-indigo-500" aria-hidden="true" />
            <p className="mt-3 text-xs font-bold text-slate-900">Usage</p>
            <p className="mt-1 text-lg font-bold text-slate-950">
              {(usage?.acceptedSegments ?? status.usage.acceptedSegments).toLocaleString()}
            </p>
            <p className="text-[11px] text-slate-500">
              accepted SMS segments
              {(usage?.limit ?? status.usage.limit) !== null &&
              (usage?.limit ?? status.usage.limit) !== undefined
                ? ` of ${(usage?.limit ?? status.usage.limit ?? 0).toLocaleString()}`
                : ''}
            </p>
          </div>

          <div className="rounded-xl border border-slate-200 p-4">
            <FiActivity className="text-indigo-500" aria-hidden="true" />
            <p className="mt-3 text-xs font-bold text-slate-900">Delivery health</p>
            <p className="mt-1 text-sm font-bold text-slate-950">
              {health?.status === 'HEALTHY'
                ? 'Healthy'
                : health?.status === 'SUSPENDED'
                  ? 'Sending paused'
                  : health
                    ? 'Needs attention'
                    : status.sendingEnabled
                      ? 'Ready to send'
                      : 'Not sending yet'}
            </p>
            <p className="mt-1 text-[11px] leading-5 text-slate-500">
              {health?.actionRequired ||
                (health
                  ? `${health.delivered.toLocaleString()} delivered · ${health.failed.toLocaleString()} failed · ${health.unknown.toLocaleString()} unknown`
                  : 'Aggregate delivery status will appear after your first managed send.')}
            </p>
          </div>
        </div>
      )}
    </div>
  )
}
