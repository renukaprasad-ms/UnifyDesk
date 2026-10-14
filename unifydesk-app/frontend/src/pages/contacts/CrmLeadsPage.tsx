import { TableSkeleton, ListSkeleton } from '../../components/ui/Skeleton'
import WorkspaceDialog from '../../components/ui/WorkspaceDialog'
import FilterPopover from '../../components/ui/FilterPopover'
import { useCallback, useEffect, useRef, useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import { FiCalendar, FiClock, FiGlobe, FiRefreshCw, FiUsers, FiZap } from 'react-icons/fi'
import LeadHistoryPanel from '../../components/contacts/LeadHistoryPanel'
import LeadFormsModal from '../../components/crm/LeadFormsModal'
import LeadScoringModal from '../../components/crm/LeadScoringModal'
import CrmAutomationModal from '../../components/crm/CrmAutomationModal'
import BookingManagerModal from '../../components/crm/BookingManagerModal'
import { leadDateTime, leadStageClass } from '../../features/contacts/leadPresentation'
import {
  crmLeadsApi,
  type Lead,
  type LeadStage,
  type LeadTemperature,
} from '../../features/contacts/crmLeadsApi'
import {
  crmCustomFieldsApi,
  type CrmFieldDef,
  type CustomFieldValues,
} from '../../features/contacts/crmCustomFieldsApi'
import { CustomFieldsSection } from '../../components/crm/CustomFieldRenderer'
import { contactsApi } from '../../features/contacts/contactsApi'
import type { ContactSummary } from '../../features/contacts/types'
import { PERMISSIONS } from '../../features/iam/permissions'
import { useIam } from '../../features/iam/useIam'
import { useWorkspaceEpochScope } from '../../features/workspace/useWorkspaceEpochScope'
import { apiErrorMessage } from '../../lib/api/apiClient'
import { useAppSelector } from '../../store/hooks'
import { selectSelectedCompanyId, selectWorkspaceEpoch } from '../../store/workspace/workspaceSlice'

const stages: LeadStage[] = ['NEW', 'CONTACTED', 'QUALIFIED', 'UNQUALIFIED', 'CONVERTED']
const label = (value: string) => value.charAt(0) + value.slice(1).toLowerCase().replaceAll('_', ' ')

export default function CrmLeadsPage() {
  const companyId = useAppSelector(selectSelectedCompanyId)
  const epoch = useAppSelector(selectWorkspaceEpoch)
  const { capture, isCurrent } = useWorkspaceEpochScope(companyId, epoch)
  const { can } = useIam()
  const [leads, setLeads] = useState<Lead[]>([])
  const [nextCursor, setNextCursor] = useState<string | null>(null)
  const [filter, setFilter] = useState<LeadStage | ''>('')
  const [tempFilter, setTempFilter] = useState<LeadTemperature | ''>('')
  const [candidates, setCandidates] = useState<ContactSummary[]>([])
  const [contactId, setContactId] = useState('')
  const [contactQuery, setContactQuery] = useState('')
  const [searching, setSearching] = useState(false)
  const [creating, setCreating] = useState(false)
  const [showFormsModal, setShowFormsModal] = useState(false)
  const [showScoringModal, setShowScoringModal] = useState(false)
  const [showAutomationModal, setShowAutomationModal] = useState(false)
  const [showBookingModal, setShowBookingModal] = useState(false)
  const [customDefs, setCustomDefs] = useState<CrmFieldDef[]>([])
  const [createCustomFields, setCreateCustomFields] = useState<CustomFieldValues>({})
  const [busy, setBusy] = useState(false)
  const requestNumber = useRef(0)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [detail, setDetail] = useState<{ scope: string; contactId: string } | null>(null)
  const scopeKey = `${companyId}:${epoch}`

  useEffect(() => {
    if (!companyId) return
    crmCustomFieldsApi.list(companyId, 'LEAD').then(setCustomDefs).catch(() => {})
  }, [companyId])
  const [params, setParams] = useSearchParams()
  const [linkedLead, setLinkedLead] = useState<Lead | null>(null)
  const selectedLead =
    detail?.scope === scopeKey
      ? leads.find((lead) => lead.contactId === detail.contactId) ||
        (linkedLead?.contactId === detail.contactId ? linkedLead : null)
      : null
  useEffect(() => {
    const id = params.get('contactId')
    if (!companyId || !id) return
    let active = true
    const scope = capture()
    const found = leads.find((lead) => lead.contactId === id)
    void (found ? Promise.resolve(found) : crmLeadsApi.get(companyId, id))
      .then((lead) => {
        if (active && isCurrent(scope)) {
          setLinkedLead(lead)
          setDetail({ scope: scopeKey, contactId: id })
        }
      })
      .catch((error) => {
        if (active && isCurrent(scope)) setError(apiErrorMessage(error, 'Unable to open lead'))
      })
    return () => {
      active = false
    }
  }, [companyId, params, leads, scopeKey, capture, isCurrent])

  const refresh = useCallback(async () => {
    if (!companyId) return
    const scope = capture()
    const request = ++requestNumber.current
    setLoading(true)
    try {
      const page = await crmLeadsApi.list(companyId, filter || undefined, tempFilter || undefined)
      if (!isCurrent(scope) || request !== requestNumber.current) return
      setLeads(page.items)
      setNextCursor(page.nextCursor)
      setError('')
    } catch (err) {
      if (isCurrent(scope) && request === requestNumber.current)
        setError(apiErrorMessage(err, 'Unable to load leads'))
    } finally {
      if (isCurrent(scope) && request === requestNumber.current) setLoading(false)
    }
  }, [companyId, filter, tempFilter, capture, isCurrent])

  useEffect(() => {
    setLeads([])
    setCandidates([])
    setContactQuery('')
    setContactId('')
    setNextCursor(null)
    setCreating(false)
    void refresh()
  }, [refresh, epoch])

  // The canonical Contacts endpoint owns search and identity normalization. Debounced
  // searches are scoped to this workspace and do not retrieve the full contacts database.
  useEffect(() => {
    if (!creating || !companyId) return
    let cancelled = false
    const scope = capture()
    const handle = setTimeout(() => {
      setSearching(true)
      void contactsApi
        .list(companyId, {
          q: contactQuery.trim() || undefined,
          status: 'ACTIVE',
          size: 30,
        })
        .then((page) => {
          if (!cancelled && isCurrent(scope)) {
            setCandidates(page.items)
            setError('')
          }
        })
        .catch((err) => {
          if (!cancelled && isCurrent(scope)) {
            setError(apiErrorMessage(err, 'Unable to search contacts'))
          }
        })
        .finally(() => {
          if (!cancelled && isCurrent(scope)) setSearching(false)
        })
    }, 300)
    return () => {
      cancelled = true
      clearTimeout(handle)
    }
  }, [companyId, contactQuery, creating, epoch, capture, isCurrent])

  const openCreate = () => {
    setCreating(true)
    setContactId('')
    setContactQuery('')
    setCandidates([])
    setCreateCustomFields({})
    setError('')
  }

  const create = async () => {
    if (!companyId || !contactId || busy) return
    const scope = capture()
    setBusy(true)
    try {
      await crmLeadsApi.enroll(companyId, contactId, createCustomFields)
      if (!isCurrent(scope)) return
      setCreating(false)
      setContactId('')
      setCreateCustomFields({})
      await refresh()
    } catch (err) {
      if (isCurrent(scope)) setError(apiErrorMessage(err, 'Unable to create lead'))
    } finally {
      if (isCurrent(scope)) setBusy(false)
    }
  }

  const move = async (lead: Lead, stage: LeadStage) => {
    if (!companyId || busy || stage === lead.stage) return
    const scope = capture()
    setBusy(true)
    try {
      const updated = await crmLeadsApi.move(companyId, lead, stage)
      if (isCurrent(scope))
        setLeads((current) =>
          current.map((item) => (item.contactId === updated.contactId ? updated : item)),
        )
    } catch (err) {
      if (isCurrent(scope)) {
        setError(apiErrorMessage(err, 'Unable to update lead. Refresh and try again.'))
        void refresh()
      }
    } finally {
      if (isCurrent(scope)) setBusy(false)
    }
  }

  const loadMore = async () => {
    if (!companyId || !nextCursor || busy) return
    const scope = capture()
    setBusy(true)
    try {
      const page = await crmLeadsApi.list(
        companyId,
        filter || undefined,
        tempFilter || undefined,
        nextCursor,
      )
      if (isCurrent(scope)) {
        setLeads((current) => [...current, ...page.items])
        setNextCursor(page.nextCursor)
      }
    } catch (err) {
      if (isCurrent(scope)) setError(apiErrorMessage(err, 'Unable to load more leads'))
    } finally {
      if (isCurrent(scope)) setBusy(false)
    }
  }

  return (
    <section className="w-full space-y-5 px-4 py-6 sm:px-6">
      <header className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <p className="eyebrow">CRM</p>
          <h1 className="mt-2 text-3xl font-bold text-slate-950">Leads</h1>
          <p className="mt-1 text-sm text-slate-500">
            Qualify existing contacts without duplicating their identities or consent.
          </p>
        </div>
        <div className="flex gap-2">
          <button
            type="button"
            className="secondary-button gap-2 text-xs"
            onClick={() => setShowScoringModal(true)}
            title="Configure Rule-Based Lead Scoring & Grade Thresholds"
          >
            <FiZap className="text-amber-500" />
            Lead Scoring
          </button>
          <button
            type="button"
            className="secondary-button gap-2 text-xs"
            onClick={() => setShowFormsModal(true)}
            title="Manage Web-to-Lead Capture Forms"
          >
            <FiGlobe />
            Web Forms
          </button>
          <button
            type="button"
            className="secondary-button gap-2 text-xs"
            onClick={() => setShowAutomationModal(true)}
            title="Configure CRM Workflow Automation Rules & Triggers"
          >
            <FiZap className="text-violet-500" />
            Automations
          </button>
          <button
            type="button"
            className="secondary-button gap-2 text-xs"
            onClick={() => setShowBookingModal(true)}
            title="Manage Shareable Meeting Scheduling Links & Calendar Bookings"
          >
            <FiCalendar className="text-violet-600" />
            Bookings
          </button>
          <button
            type="button"
            className="secondary-button gap-2"
            disabled={loading || busy}
            onClick={() => void refresh()}
          >
            <FiRefreshCw />
            Refresh
          </button>
          {can(PERMISSIONS.CRM_LEAD_MODIFY) && (
            <button type="button" className="primary-button" onClick={openCreate} disabled={busy}>
              + Create lead
            </button>
          )}
        </div>
      </header>
      {error && !creating && (
        <p role="alert" className="rounded-lg bg-rose-50 p-3 text-sm text-rose-700">
          {error}
        </p>
      )}
      {creating && (
        <WorkspaceDialog
          title="Create lead"
          busy={busy}
          error={error}
          onClose={() => setCreating(false)}
        >
          <div className="space-y-3">
            <label htmlFor="crm-lead-search" className="block text-sm font-semibold">
              Find a contact
            </label>
            <input
              id="crm-lead-search"
              className="form-input mt-2 w-full"
              placeholder="Search by name, email or phone"
              value={contactQuery}
              onChange={(event) => {
                setContactQuery(event.target.value)
                setContactId('')
                setCandidates([])
              }}
            />
            <label htmlFor="crm-lead-contact" className="mt-3 block text-sm font-semibold">
              Choose a contact
            </label>
            <select
              id="crm-lead-contact"
              className="form-input mt-2 w-full"
              value={contactId}
              disabled={searching}
              onChange={(event) => setContactId(event.target.value)}
            >
              <option value="">Select contact</option>
              {candidates
                .filter((contact) => !leads.some((lead) => lead.contactId === contact.id))
                .map((contact) => (
                  <option key={contact.id} value={contact.id}>
                    {contact.displayName || 'Unnamed contact'} {contact.primaryEmail ?? ''}
                  </option>
                ))}
            </select>
            {searching ? (
              <ListSkeleton rows={2} />
            ) : (
              <p className="mt-2 text-xs text-slate-500">
                Showing up to 30 matching contacts. Refine the search for more results.
              </p>
            )}
            {customDefs.length > 0 && (
              <CustomFieldsSection
                defs={customDefs}
                values={createCustomFields}
                onChange={(key, val) =>
                  setCreateCustomFields((prev: CustomFieldValues): CustomFieldValues => ({
                    ...prev,
                    [key]: val,
                  }))
                }
                disabled={busy}
              />
            )}
            <div className="mt-4 flex gap-2">
              <button
                className="primary-button"
                disabled={!contactId || busy || searching}
                onClick={() => void create()}
              >
                Create lead
              </button>
              <button
                className="secondary-button"
                disabled={busy}
                onClick={() => setCreating(false)}
              >
                Cancel
              </button>
            </div>
          </div>
        </WorkspaceDialog>
      )}
      {showFormsModal && companyId && (
        <LeadFormsModal
          companyId={companyId}
          onClose={() => {
            setShowFormsModal(false)
            void refresh()
          }}
        />
      )}
      {showScoringModal && companyId && (
        <LeadScoringModal
          companyId={companyId}
          onClose={() => setShowScoringModal(false)}
          onRulesChanged={() => void refresh()}
        />
      )}
      {showAutomationModal && companyId && (
        <CrmAutomationModal
          companyId={companyId}
          onClose={() => setShowAutomationModal(false)}
        />
      )}
      {showBookingModal && companyId && (
        <BookingManagerModal
          companyId={companyId}
          onClose={() => setShowBookingModal(false)}
        />
      )}
      <div className="flex items-center gap-2">
        <span className="flex items-center gap-2 text-xs text-slate-500">
          <FiUsers />
          {leads.length} loaded leads
        </span>
        <FilterPopover
          className="ml-auto"
          activeCount={Number(!!filter) + Number(!!tempFilter)}
          onReset={() => {
            setFilter('')
            setTempFilter('')
          }}
        >
          <label htmlFor="crm-lead-stage" className="text-sm font-medium">
            Stage
          </label>
          <select
            id="crm-lead-stage"
            className="form-input"
            value={filter}
            onChange={(e) => setFilter(e.target.value as LeadStage | '')}
          >
            <option value="">All stages</option>
            {stages.map((stage) => (
              <option key={stage} value={stage}>
                {label(stage)}
              </option>
            ))}
          </select>

          <label htmlFor="crm-lead-temp" className="mt-3 block text-sm font-medium">
            Grade & Temperature
          </label>
          <select
            id="crm-lead-temp"
            className="form-input"
            value={tempFilter}
            onChange={(e) => setTempFilter(e.target.value as LeadTemperature | '')}
          >
            <option value="">All grades</option>
            <option value="HOT">🔥 Hot Leads</option>
            <option value="WARM">⚡ Warm Leads</option>
            <option value="COLD">❄️ Cold Leads</option>
          </select>
        </FilterPopover>
      </div>
      <div
        className={`grid items-start gap-5 ${selectedLead ? 'xl:grid-cols-[minmax(0,1fr)_380px]' : ''}`}
      >
        {loading ? (
          <TableSkeleton rows={5} columns={6} />
        ) : leads.length === 0 ? (
          <div className="rounded-xl border border-dashed border-slate-300 p-8 text-center text-sm text-slate-500">
            No leads found. Add an existing contact as a lead to start tracking its progress.
          </div>
        ) : (
          <div className="overflow-x-auto rounded-xl border border-slate-200 bg-white dark:border-slate-800 dark:bg-slate-900">
            <table className="w-full text-left text-sm">
              <thead className="bg-slate-50 text-xs uppercase tracking-wide text-slate-500 dark:bg-slate-800/60 dark:text-slate-400">
                <tr>
                  <th className="p-4">Lead</th>
                  <th className="p-4">Organization</th>
                  <th className="p-4">Score & Grade</th>
                  <th className="p-4">Status</th>
                  <th className="p-4">Last updated</th>
                  <th className="p-4">Activity</th>
                </tr>
              </thead>
              <tbody>
                {leads.map((lead) => (
                  <tr
                    key={lead.contactId}
                    className={`border-t border-slate-100 dark:border-slate-800 transition hover:bg-slate-50 dark:hover:bg-slate-800/50 ${selectedLead?.contactId === lead.contactId ? 'bg-indigo-50 dark:bg-indigo-950/40' : ''}`}
                  >
                    <td className="p-4">
                      <div className="flex items-center gap-3">
                        <span className="flex size-9 shrink-0 items-center justify-center rounded-full bg-indigo-50 text-xs font-bold text-indigo-600 dark:bg-indigo-950/50 dark:text-indigo-400">
                          {(lead.displayName || '?').slice(0, 2).toUpperCase()}
                        </span>
                        <div>
                          <Link
                            className="font-semibold text-slate-900 dark:text-slate-100 hover:text-indigo-600 dark:hover:text-indigo-400"
                            to={'/contacts/' + lead.contactId}
                          >
                            {lead.displayName || 'Unnamed contact'}
                          </Link>
                          {lead.customFields && Object.keys(lead.customFields).length > 0 && (
                            <div className="mt-1 flex flex-wrap gap-1">
                              {Object.entries(lead.customFields).map(([k, v]) => {
                                const def = customDefs.find((d) => d.fieldKey === k)
                                const fieldLabel = def?.label || k
                                return (
                                  <span
                                    key={k}
                                    className="inline-flex items-center rounded bg-blue-50 dark:bg-blue-950/40 px-1.5 py-0.5 text-[10px] font-medium text-blue-700 dark:text-blue-300 border border-blue-200/50 dark:border-blue-800/50"
                                  >
                                    {fieldLabel}: {Array.isArray(v) ? v.join(', ') : String(v)}
                                  </span>
                                )
                              })}
                            </div>
                          )}
                          <p className="mt-1 text-[11px] text-slate-500">
                            Added {new Date(lead.createdAt).toLocaleDateString()}
                          </p>
                        </div>
                      </div>
                    </td>
                    <td className="p-4">{lead.organization || '—'}</td>
                    <td className="p-4">
                      <span
                        className={`inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-xs font-bold ${
                          lead.temperature === 'HOT'
                            ? 'bg-rose-50 text-rose-700 dark:bg-rose-950/60 dark:text-rose-300 border border-rose-200 dark:border-rose-800'
                            : lead.temperature === 'WARM'
                            ? 'bg-amber-50 text-amber-700 dark:bg-amber-950/60 dark:text-amber-300 border border-amber-200 dark:border-amber-800'
                            : 'bg-blue-50 text-blue-700 dark:bg-blue-950/60 dark:text-blue-300 border border-blue-200 dark:border-blue-800'
                        }`}
                      >
                        {lead.temperature === 'HOT' && '🔥 Hot'}
                        {lead.temperature === 'WARM' && '⚡ Warm'}
                        {(!lead.temperature || lead.temperature === 'COLD') && '❄️ Cold'}
                        <span className="opacity-75">· {lead.score ?? 0} pts</span>
                      </span>
                    </td>
                    <td className="p-4">
                      {can(PERMISSIONS.CRM_LEAD_MODIFY) ? (
                        <select
                          aria-label={'Stage for ' + (lead.displayName || lead.contactId)}
                          className={`form-input min-w-32 text-xs font-semibold ${leadStageClass(lead.stage)}`}
                          disabled={busy}
                          value={lead.stage}
                          onChange={(e) => void move(lead, e.target.value as LeadStage)}
                        >
                          {stages.map((stage) => (
                            <option key={stage} value={stage}>
                              {label(stage)}
                            </option>
                          ))}
                        </select>
                      ) : (
                        <span
                          className={`rounded-full px-3 py-1 text-xs font-semibold ${leadStageClass(lead.stage)}`}
                        >
                          {label(lead.stage)}
                        </span>
                      )}
                    </td>
                    <td className="p-4 text-xs text-slate-500">
                      <time dateTime={lead.updatedAt}>{leadDateTime(lead.updatedAt)}</time>
                    </td>
                    <td className="p-4">
                      <button
                        type="button"
                        aria-expanded={selectedLead?.contactId === lead.contactId}
                        aria-label={`View status history for ${lead.displayName || 'lead'}`}
                        className="secondary-button gap-2 whitespace-nowrap text-xs"
                        onClick={() => {
                          setDetail({ scope: scopeKey, contactId: lead.contactId })
                          setParams({ contactId: lead.contactId })
                        }}
                      >
                        <FiClock />
                        View history
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        {selectedLead && companyId && (
          <LeadHistoryPanel
            key={`${scopeKey}:${selectedLead.contactId}`}
            companyId={companyId}
            lead={selectedLead}
            onLeadUpdated={(updated) => {
              setLeads((curr) =>
                curr.map((item) => (item.contactId === updated.contactId ? updated : item)),
              )
              setLinkedLead(updated)
            }}
            onClose={() => {
              setDetail(null)
              setParams({})
            }}
          />
        )}
      </div>
      {nextCursor && (
        <button className="secondary-button" disabled={busy} onClick={() => void loadMore()}>
          Load more
        </button>
      )}
    </section>
  )
}
