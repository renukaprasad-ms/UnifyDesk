import { useSnackbarState } from '../feedback/useSnackbarState'
import { ListSkeleton } from '../ui/Skeleton'
import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import { FiArrowRight, FiClock, FiRefreshCw, FiUser, FiX, FiSliders, FiCheck, FiMessageCircle } from 'react-icons/fi'
import { crmLeadsApi, type Lead, type LeadHistoryEvent } from '../../features/contacts/crmLeadsApi'
import {
  crmLeadScoringApi,
  type LeadScoreResult,
} from '../../features/contacts/crmLeadScoringApi'
import {
  crmCustomFieldsApi,
  type CrmFieldDef,
  type CustomFieldValues,
} from '../../features/contacts/crmCustomFieldsApi'
import { CustomFieldsSection } from '../crm/CustomFieldRenderer'
import SendWhatsAppModal from '../crm/SendWhatsAppModal'
import { PERMISSIONS } from '../../features/iam/permissions'
import { useIam } from '../../features/iam/useIam'
import { apiErrorMessage } from '../../lib/api/apiClient'
import { leadDateTime, leadStageClass } from '../../features/contacts/leadPresentation'

const stageLabel = (value: string) => value.charAt(0) + value.slice(1).toLowerCase()

export default function LeadHistoryPanel({
  companyId,
  lead,
  onClose,
  onLeadUpdated,
}: {
  companyId: string
  lead: Lead
  onClose: () => void
  onLeadUpdated?: (updated: Lead) => void
}) {
  const { can } = useIam()
  const canModify = can(PERMISSIONS.CRM_LEAD_MODIFY)
  const canSendWhatsApp = can(PERMISSIONS.WHATSAPP_SEND)

  const [showWhatsApp, setShowWhatsApp] = useState(false)
  const [reload, setReload] = useState(0)
  const key = `${companyId}:${lead.contactId}:${lead.version}:${reload}`
  const [result, setResult] = useState<{
    key: string
    events: LeadHistoryEvent[]
    error: string
  } | null>(null)
  const loading = result?.key !== key
  useEffect(() => {
    if (result?.key === key && result.error) showGlobalSnackbar(result.error, { variant: 'error' })
  }, [result, key])
  const events = !loading ? result.events : []

  // Custom fields state
  const [defs, setDefs] = useState<CrmFieldDef[]>([])
  const [customFields, setCustomFields] = useState<CustomFieldValues>(lead.customFields || {})
  const [editingCustom, setEditingCustom] = useState(false)
  const [savingCustom, setSavingCustom] = useState(false)
  const [customError, setCustomError] = useSnackbarState('', 'error')

  // Lead score breakdown state
  const [scoreResult, setScoreResult] = useState<LeadScoreResult | null>(null)
  const [showScoreBreakdown, setShowScoreBreakdown] = useState(false)

  useEffect(() => {
    if (!companyId || !lead.contactId) return
    crmLeadScoringApi
      .getLeadScore(companyId, lead.contactId)
      .then(setScoreResult)
      .catch(() => {})
  }, [companyId, lead.contactId, lead.stage, reload])

  useEffect(() => {
    setCustomFields(lead.customFields || {})
  }, [lead.customFields])

  useEffect(() => {
    if (!companyId) return
    crmCustomFieldsApi
      .list(companyId, 'LEAD')
      .then(setDefs)
      .catch(() => {})
  }, [companyId])

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

  async function handleSaveCustom() {
    setSavingCustom(true)
    setCustomError('')
    try {
      const updated = await crmLeadsApi.updateCustomFields(
        companyId,
        lead.contactId,
        customFields,
      )
      setEditingCustom(false)
      if (onLeadUpdated) onLeadUpdated(updated)
    } catch (err) {
      setCustomError(apiErrorMessage(err, 'Failed to save custom attributes'))
    } finally {
      setSavingCustom(false)
    }
  }

  return (
    <section className="panel overflow-hidden" aria-label="Lead details and status history">
      <div className="flex items-start justify-between gap-4 border-b border-slate-100 dark:border-slate-800 p-5">
        <div>
          <p className="eyebrow">LEAD DETAILS</p>
          <h2 className="mt-2 text-lg font-bold text-slate-950 dark:text-white">
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

        {/* Lead Score & Temperature Badge Card */}
        <div className="mt-4 rounded-xl border border-slate-200/80 dark:border-slate-800 bg-slate-50/60 dark:bg-slate-900/40 p-3.5 space-y-2.5">
          <div className="flex items-center justify-between">
            <span className="text-xs font-semibold text-slate-500 dark:text-slate-400">
              Lead Score & Grade
            </span>
            <span
              className={`inline-flex items-center gap-1 rounded-full px-2.5 py-0.5 text-xs font-bold ${
                (scoreResult?.temperature || lead.temperature) === 'HOT'
                  ? 'bg-rose-50 text-rose-700 dark:bg-rose-950/60 dark:text-rose-300 border border-rose-200 dark:border-rose-800'
                  : (scoreResult?.temperature || lead.temperature) === 'WARM'
                  ? 'bg-amber-50 text-amber-700 dark:bg-amber-950/60 dark:text-amber-300 border border-amber-200 dark:border-amber-800'
                  : 'bg-blue-50 text-blue-700 dark:bg-blue-950/60 dark:text-blue-300 border border-blue-200 dark:border-blue-800'
              }`}
            >
              {(scoreResult?.temperature || lead.temperature) === 'HOT' && '🔥 Hot Lead'}
              {(scoreResult?.temperature || lead.temperature) === 'WARM' && '⚡ Warm Lead'}
              {(!scoreResult?.temperature && !lead.temperature) || (scoreResult?.temperature || lead.temperature) === 'COLD' ? '❄️ Cold Lead' : ''}
            </span>
          </div>

          <div className="flex items-baseline justify-between">
            <div className="text-2xl font-black text-slate-900 dark:text-slate-100">
              {scoreResult?.score ?? lead.score ?? 0}{' '}
              <span className="text-xs font-normal text-slate-400">points</span>
            </div>
            {scoreResult && scoreResult.breakdown.length > 0 && (
              <button
                type="button"
                onClick={() => setShowScoreBreakdown(!showScoreBreakdown)}
                className="text-[11px] font-semibold text-indigo-600 dark:text-indigo-400 hover:underline"
              >
                {showScoreBreakdown ? 'Hide Breakdown' : `${scoreResult.breakdown.length} Rules Matched`}
              </button>
            )}
          </div>

          {showScoreBreakdown && scoreResult && scoreResult.breakdown.length > 0 && (
            <div className="mt-2 space-y-1.5 border-t border-slate-200/60 dark:border-slate-800 pt-2 text-[11px]">
              {scoreResult.breakdown.map((item, idx) => (
                <div key={idx} className="flex items-center justify-between text-slate-600 dark:text-slate-300">
                  <span className="truncate pr-2">{item.reason || item.ruleName}</span>
                  <span className="shrink-0 font-bold text-emerald-600 dark:text-emerald-400">
                    +{item.points}
                  </span>
                </div>
              ))}
            </div>
          )}
        </div>

        <dl className="mt-4 grid grid-cols-2 gap-4 text-xs">
          <div>
            <dt className="text-slate-500">Created</dt>
            <dd className="mt-1 font-medium text-slate-900 dark:text-slate-100">{leadDateTime(lead.createdAt)}</dd>
          </div>
          <div>
            <dt className="text-slate-500">Last updated</dt>
            <dd className="mt-1 font-medium text-slate-900 dark:text-slate-100">{leadDateTime(lead.updatedAt)}</dd>
          </div>
        </dl>
        <Link
          to={`/contacts/${lead.contactId}`}
          className="mt-5 inline-flex items-center gap-2 text-xs font-semibold text-indigo-600 dark:text-indigo-400"
        >
          Open contact profile <FiArrowRight />
        </Link>
        {canSendWhatsApp && (
          <button
            type="button"
            onClick={() => setShowWhatsApp(true)}
            className="mt-4 flex w-full items-center justify-center gap-2 rounded-xl bg-emerald-600 hover:bg-emerald-700 active:bg-emerald-800 text-white py-2.5 px-3 text-xs font-semibold shadow-sm transition-colors"
          >
            <FiMessageCircle className="h-4 w-4" />
            Send WhatsApp Message
          </button>
        )}
      </div>

      {/* Custom Fields Section */}
      {defs.length > 0 && (
        <div className="border-t border-slate-100 dark:border-slate-800 p-5">
          <div className="flex items-center justify-between">
            <h3 className="flex items-center gap-2 text-sm font-bold text-slate-950 dark:text-white">
              <FiSliders className="text-blue-500" />
              Custom Attributes
            </h3>
            {canModify && !editingCustom && (
              <button
                type="button"
                className="secondary-button text-xs py-1 px-2.5"
                onClick={() => setEditingCustom(true)}
              >
                Edit
              </button>
            )}
          </div>

          {customError && (
            <p role="alert" className="mt-2 rounded-lg bg-rose-50 dark:bg-rose-950/40 p-2 text-xs text-rose-700 dark:text-rose-300">
              {customError}
            </p>
          )}

          {editingCustom ? (
            <div className="mt-3 space-y-3">
              <CustomFieldsSection
                defs={defs}
                values={customFields}
                onChange={(key, val) =>
                  setCustomFields((prev: CustomFieldValues): CustomFieldValues => ({
                    ...prev,
                    [key]: val,
                  }))
                }
                disabled={savingCustom}
              />
              <div className="flex gap-2 pt-2">
                <button
                  type="button"
                  className="primary-button text-xs py-1.5 px-3 inline-flex items-center gap-1.5"
                  disabled={savingCustom}
                  onClick={() => { void handleSaveCustom() }}
                >
                  <FiCheck className="h-3.5 w-3.5" />
                  {savingCustom ? 'Saving…' : 'Save Attributes'}
                </button>
                <button
                  type="button"
                  className="secondary-button text-xs py-1.5 px-3"
                  disabled={savingCustom}
                  onClick={() => {
                    setCustomFields(lead.customFields || {})
                    setEditingCustom(false)
                    setCustomError('')
                  }}
                >
                  Cancel
                </button>
              </div>
            </div>
          ) : (
            <dl className="mt-3 grid grid-cols-2 gap-2.5 text-xs">
              {defs.map((def) => {
                const val = customFields[def.fieldKey]
                const displayVal =
                  val != null && val !== ''
                    ? Array.isArray(val)
                      ? val.join(', ')
                      : typeof val === 'boolean'
                      ? val ? 'Yes' : 'No'
                      : String(val)
                    : null
                return (
                  <div
                    key={def.id}
                    className="rounded-lg bg-slate-50 dark:bg-slate-800/50 p-2.5 border border-slate-100 dark:border-slate-800"
                  >
                    <dt className="text-slate-500 dark:text-slate-400 font-medium">{def.label}</dt>
                    <dd className="mt-1 font-semibold text-slate-900 dark:text-slate-100 break-words">
                      {displayVal ? (
                        displayVal
                      ) : (
                        <span className="font-normal text-slate-400 italic">Not set</span>
                      )}
                    </dd>
                  </div>
                )
              })}
            </dl>
          )}
        </div>
      )}

      {/* Status History Section */}
      <div className="border-t border-slate-100 dark:border-slate-800 p-5">
        <div className="flex items-center justify-between gap-2">
          <h3 className="flex items-center gap-2 text-sm font-bold text-slate-950 dark:text-white">
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
              <li key={event.id} className="relative border-l-2 border-indigo-100 dark:border-indigo-900/50 pl-5">
                <span className="absolute -left-[7px] top-1 size-3 rounded-full border-2 border-white dark:border-slate-900 bg-indigo-500" />
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
                <p className="mt-3 flex items-center gap-2 text-xs font-medium text-slate-800 dark:text-slate-200">
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

      {showWhatsApp && (
        <SendWhatsAppModal
          companyId={companyId}
          contactId={lead.contactId}
          recipientName={lead.displayName}
          onClose={() => setShowWhatsApp(false)}
          onSuccess={() => {
            setReload((prev) => prev + 1)
          }}
        />
      )}
    </section>
  )
}
import { showGlobalSnackbar } from '../feedback/SnackbarProvider'
