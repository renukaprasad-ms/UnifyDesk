import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router'
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
  const [creating, setCreating] = useState(false)
  const [busy, setBusy] = useState(false)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  const refresh = useCallback(async () => {
    if (!companyId) return
    const scope = capture()
    setLoading(true)
    try {
      const page = await crmLeadsApi.list(companyId, filter || undefined)
      if (!isCurrent(scope)) return
      setLeads(page.items)
      setNextCursor(page.nextCursor)
      setError('')
    } catch (err) {
      if (isCurrent(scope)) setError(apiErrorMessage(err, 'Unable to load leads'))
    } finally {
      if (isCurrent(scope)) setLoading(false)
    }
  }, [companyId, filter, capture, isCurrent])

  useEffect(() => {
    setLeads([])
    setCandidates([])
    setNextCursor(null)
    setCreating(false)
    void refresh()
  }, [refresh, epoch])

  const openCreate = async () => {
    if (!companyId) return
    const scope = capture()
    setCreating(true)
    setError('')
    try {
      const page = await contactsApi.list(companyId, { status: 'ACTIVE', size: 100 })
      if (isCurrent(scope)) setCandidates(page.items)
    } catch (err) {
      if (isCurrent(scope)) setError(apiErrorMessage(err, 'Unable to load contacts'))
    }
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
      if (isCurrent(scope)) setLeads((current) => current.map((item) => item.contactId === updated.contactId ? updated : item))
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
          <p className="mt-1 text-sm text-slate-500">Qualify existing contacts without duplicating their identities or consent.</p>
        </div>
        {can(PERMISSIONS.CRM_LEAD_MODIFY) && (
          <button type="button" className="primary-button" onClick={() => void openCreate()} disabled={busy}>+ Create lead</button>
        )}
      </header>
      {error && <p role="alert" className="rounded-lg bg-rose-50 p-3 text-sm text-rose-700">{error}</p>}
      {creating && (
        <div className="rounded-xl border border-slate-200 bg-white p-4">
          <label htmlFor="crm-lead-contact" className="block text-sm font-semibold">Choose an existing contact</label>
          <select id="crm-lead-contact" className="form-input mt-2 w-full" value={contactId} onChange={(e) => setContactId(e.target.value)}>
            <option value="">Select contact</option>
            {candidates.filter((contact) => !leads.some((lead) => lead.contactId === contact.id)).map((contact) => (
              <option key={contact.id} value={contact.id}>{contact.displayName} {contact.primaryEmail ?? ''}</option>
            ))}
          </select>
          <p className="mt-2 text-xs text-slate-500">Showing up to 100 contacts. Create or find additional contacts in Contacts.</p>
          <div className="mt-3 flex gap-2">
            <button className="primary-button" disabled={!contactId || busy} onClick={() => void create()}>Create lead</button>
            <button className="secondary-button" disabled={busy} onClick={() => setCreating(false)}>Cancel</button>
          </div>
        </div>
      )}
      <div className="flex items-center gap-2">
        <label htmlFor="crm-lead-stage" className="text-sm font-medium">Stage</label>
        <select id="crm-lead-stage" className="form-input" value={filter} onChange={(e) => setFilter(e.target.value as LeadStage | '')}>
          <option value="">All stages</option>
          {stages.map((stage) => <option key={stage} value={stage}>{label(stage)}</option>)}
        </select>
      </div>
      {loading ? <p role="status" className="text-sm text-slate-500">Loading leads…</p> : leads.length === 0 ? (
        <div className="rounded-xl border border-dashed border-slate-300 p-8 text-center text-sm text-slate-500">No leads found. Add an existing contact as a lead to start tracking its progress.</div>
      ) : (
        <div className="overflow-x-auto rounded-xl border border-slate-200 bg-white">
          <table className="w-full text-left text-sm">
            <thead className="bg-slate-50 text-slate-600"><tr><th className="p-4">Contact</th><th className="p-4">Organization</th><th className="p-4">Stage</th><th className="p-4">Updated</th></tr></thead>
            <tbody>
              {leads.map((lead) => (
                <tr key={lead.contactId} className="border-t border-slate-100">
                  <td className="p-4"><Link className="font-semibold text-indigo-600 hover:underline" to={'/contacts/' + lead.contactId}>{lead.displayName || 'Unnamed contact'}</Link></td>
                  <td className="p-4">{lead.organization || '—'}</td>
                  <td className="p-4">
                    {can(PERMISSIONS.CRM_LEAD_MODIFY) ? (
                      <select aria-label={'Stage for ' + (lead.displayName || lead.contactId)} className="form-input" disabled={busy} value={lead.stage} onChange={(e) => void move(lead, e.target.value as LeadStage)}>
                        {stages.map((stage) => <option key={stage} value={stage}>{label(stage)}</option>)}
                      </select>
                    ) : label(lead.stage)}
                  </td>
                  <td className="p-4 text-slate-500">{new Date(lead.updatedAt).toLocaleDateString()}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {nextCursor && <button className="secondary-button" disabled={busy} onClick={() => void loadMore()}>Load more</button>}
    </section>
  )
}
