import { ListSkeleton } from '../ui/Skeleton'
import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import { FiArrowRight, FiClock, FiRefreshCw, FiUser, FiX } from 'react-icons/fi'
import { crmLeadsApi, type Lead, type LeadHistoryEvent } from '../../features/contacts/crmLeadsApi'
import { apiErrorMessage } from '../../lib/api/apiClient'
import { leadDateTime, leadStageClass } from '../../features/contacts/leadPresentation'

const stageLabel = (value: string) => value.charAt(0) + value.slice(1).toLowerCase()
export default function LeadHistoryPanel({
  companyId,
  lead,
  onClose,
}: {
  companyId: string
  lead: Lead
  onClose: () => void
}) {
  const [reload, setReload] = useState(0)
  const key = `${companyId}:${lead.contactId}:${lead.version}:${reload}`
  const [result, setResult] = useState<{
    key: string
    events: LeadHistoryEvent[]
    error: string
  } | null>(null)
  const loading = result?.key !== key
  const events = !loading ? result.events : []
  useEffect(() => {
    let active = true
    void crmLeadsApi
      .history(companyId, lead.contactId)
      .then((events) => {
        if (active) setResult({ key, events, error: '' })
      })
      .catch((error) => {
        if (active)
          setResult({
            key,
            events: [],
            error: apiErrorMessage(error, 'Unable to load lead history'),
          })
      })
    return () => {
      active = false
    }
  }, [companyId, lead.contactId, key])

  return (
    <section className="panel overflow-hidden" aria-label="Lead details and status history">
      <div className="flex items-start justify-between gap-4 border-b border-slate-100 p-5">
        <div>
          <p className="eyebrow">LEAD DETAILS</p>
          <h2 className="mt-2 text-lg font-bold text-slate-950">
            {lead.displayName || 'Unnamed contact'}
          </h2>
          <p className="mt-1 text-xs text-slate-500">
            {lead.organization || 'No organization added'}
          </p>
        </div>
        <button
          type="button"
          onClick={onClose}
          className="secondary-button p-2"
          aria-label="Close lead details"
        >
          <FiX />
        </button>
      </div>
      <div className="p-5">
        <span
          className={`rounded-full px-3 py-1 text-xs font-semibold ${leadStageClass(lead.stage)}`}
        >
          {stageLabel(lead.stage)}
        </span>
        <dl className="mt-5 grid grid-cols-2 gap-4 text-xs">
          <div>
            <dt className="text-slate-500">Created</dt>
            <dd className="mt-1 font-medium text-slate-900">{leadDateTime(lead.createdAt)}</dd>
          </div>
          <div>
            <dt className="text-slate-500">Last updated</dt>
            <dd className="mt-1 font-medium text-slate-900">{leadDateTime(lead.updatedAt)}</dd>
          </div>
        </dl>
        <Link
          to={`/contacts/${lead.contactId}`}
          className="mt-5 inline-flex items-center gap-2 text-xs font-semibold text-indigo-600"
        >
          Open contact profile <FiArrowRight />
        </Link>
      </div>
      <div className="border-t border-slate-100 p-5">
        <div className="flex items-center justify-between gap-2">
          <h3 className="flex items-center gap-2 text-sm font-bold text-slate-950">
            <FiClock className="text-indigo-500" />
            Status history
          </h3>
          <button
            type="button"
            className="secondary-button p-2"
            aria-label="Refresh status history"
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
          <p className="py-6 text-sm text-slate-500">No recorded status changes.</p>
        ) : (
          <ol className="mt-5 space-y-5">
            {events.map((event) => (
              <li key={event.id} className="relative border-l-2 border-indigo-100 pl-5">
                <span className="absolute -left-[7px] top-1 size-3 rounded-full border-2 border-white bg-indigo-500" />
                <div className="flex flex-wrap items-center gap-2 text-xs">
                  <span className="text-slate-500">
                    {event.previousStage ? stageLabel(event.previousStage) : 'Lead created'}
                  </span>
                  <FiArrowRight className="text-slate-400" />
                  <span
                    className={`rounded-full px-2 py-1 font-semibold ${leadStageClass(event.newStage)}`}
                  >
                    {stageLabel(event.newStage)}
                  </span>
                </div>
                <p className="mt-3 flex items-center gap-2 text-xs font-medium text-slate-800">
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
