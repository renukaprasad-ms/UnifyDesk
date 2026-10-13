import { TableSkeleton, ListSkeleton } from '../../components/ui/Skeleton'
import WorkspaceDialog from '../../components/ui/WorkspaceDialog'
import FilterPopover from '../../components/ui/FilterPopover'
import { useCallback, useEffect, useRef, useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import { FiClock, FiRefreshCw, FiUsers } from 'react-icons/fi'
import LeadHistoryPanel from '../../components/contacts/LeadHistoryPanel'
import { leadDateTime, leadStageClass } from '../../features/contacts/leadPresentation'
import { crmLeadsApi, type Lead, type LeadStage } from '../../features/contacts/crmLeadsApi'
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
  const [candidates, setCandidates] = useState<ContactSummary[]>([])
  const [contactId, setContactId] = useState('')
  const [contactQuery, setContactQuery] = useState('')
  const [searching, setSearching] = useState(false)
  const [creating, setCreating] = useState(false)
  const [busy, setBusy] = useState(false)
  const requestNumber = useRef(0)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [detail, setDetail] = useState<{ scope: string; contactId: string } | null>(null)
  const scopeKey = `${companyId}:${epoch}`
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
      const page = await crmLeadsApi.list(companyId, filter || undefined)
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
  }, [companyId, filter, capture, isCurrent])

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
    setError('')
  }

  const create = async () => {
    if (!companyId || !contactId || busy) return
    const scope = capture()
    setBusy(true)
    try {
      await crmLeadsApi.enroll(companyId, contactId)
      if (!isCurrent(scope)) return
      setCreating(false)
      setContactId('')
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
      const page = await crmLeadsApi.list(companyId, filter || undefined, nextCursor)
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
                'Showing up to 30 matching contacts. Refine the search for more results.'
              </p>
            )}
            <div className="mt-3 flex gap-2">
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
      <div className="flex items-center gap-2">
        <span className="flex items-center gap-2 text-xs text-slate-500">
          <FiUsers />
          {leads.length} loaded leads
        </span>
        <FilterPopover
          className="ml-auto"
          activeCount={Number(!!filter)}
          onReset={() => setFilter('')}
        >
          {' '}
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
        </FilterPopover>
      </div>
      <div
        className={`grid items-start gap-5 ${selectedLead ? 'xl:grid-cols-[minmax(0,1fr)_380px]' : ''}`}
      >
        {loading ? (
          <TableSkeleton rows={5} columns={5} />
        ) : leads.length === 0 ? (
          <div className="rounded-xl border border-dashed border-slate-300 p-8 text-center text-sm text-slate-500">
            No leads found. Add an existing contact as a lead to start tracking its progress.
          </div>
        ) : (
          <div className="overflow-x-auto rounded-xl border border-slate-200 bg-white">
            <table className="w-full text-left text-sm">
              <thead className="bg-slate-50 text-xs uppercase tracking-wide text-slate-500">
                <tr>
                  <th className="p-4">Lead</th>
                  <th className="p-4">Organization</th>
                  <th className="p-4">Status</th>
                  <th className="p-4">Last updated</th>
                  <th className="p-4">Activity</th>
                </tr>
              </thead>
              <tbody>
                {leads.map((lead) => (
                  <tr
                    key={lead.contactId}
                    className={`border-t border-slate-100 transition hover:bg-slate-50 ${selectedLead?.contactId === lead.contactId ? 'bg-indigo-50' : ''}`}
                  >
                    <td className="p-4">
                      <div className="flex items-center gap-3">
                        <span className="flex size-9 shrink-0 items-center justify-center rounded-full bg-indigo-50 text-xs font-bold text-indigo-600">
                          {(lead.displayName || '?').slice(0, 2).toUpperCase()}
                        </span>
                        <div>
                          <Link
                            className="font-semibold text-slate-900 hover:text-indigo-600"
                            to={'/contacts/' + lead.contactId}
                          >
                            {lead.displayName || 'Unnamed contact'}
                          </Link>
                          <p className="mt-1 text-[11px] text-slate-500">
                            Added {new Date(lead.createdAt).toLocaleDateString()}
                          </p>
                        </div>
                      </div>
                    </td>
                    <td className="p-4">{lead.organization || '—'}</td>
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
