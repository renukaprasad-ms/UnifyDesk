import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import ContactsNav from '../../components/contacts/ContactsNav'
import ContactFormDialog from '../../components/contacts/ContactFormDialog'
import BulkActionsBar from '../../components/contacts/BulkActionsBar'
import { createIdempotencyKey, toCreateContactInput } from '../../features/contacts/contactModel'
import { contactsApi } from '../../features/contacts/contactsApi'
import { useContacts } from '../../features/contacts/useContacts'
import type { ContactQuery, ContactSort } from '../../features/contacts/types'
import { PERMISSIONS } from '../../features/iam/permissions'
import { useIam } from '../../features/iam/useIam'
import { apiErrorMessage } from '../../lib/api/apiClient'
import { useAppSelector } from '../../store/hooks'
import { selectSelectedCompanyId } from '../../store/workspace/workspaceSlice'

export default function ContactsPage() {
  const companyId = useAppSelector(selectSelectedCompanyId)
  const { can } = useIam()
  const [params, setParams] = useSearchParams()
  const [search, setSearch] = useState(params.get('q') ?? '')
  const [selected, setSelected] = useState<Set<string>>(new Set())
  const [allMatching, setAllMatching] = useState(false)
  const [creating, setCreating] = useState(false)
  const [saving, setSaving] = useState(false)
  const [exporting, setExporting] = useState(false)
  const [notice, setNotice] = useState('')
  const [error, setError] = useState('')
  const query: ContactQuery = {
    q: params.get('q') || undefined,
    sort: (params.get('sort') as ContactSort | null) ?? 'UPDATED_DESC',
    size: 50,
  }
  const contacts = useContacts(companyId, query)

  useEffect(() => {
    const timer = window.setTimeout(() => {
      const next = new URLSearchParams(params)
      if (search.trim()) next.set('q', search.trim())
      else next.delete('q')
      next.delete('cursor')
      setParams(next, { replace: true })
    }, 300)
    return () => window.clearTimeout(timer)
  }, [search])

  const toggle = (id: string) =>
    setSelected((current) => {
      const next = new Set(current)
      if (next.has(id)) next.delete(id)
      else next.add(id)
      return next
    })

  return (
    <section className="px-4 py-6 sm:px-6 lg:px-8">
      <div className="mx-auto max-w-7xl">
        <ContactsNav />
        <header className="flex flex-wrap items-end justify-between gap-4">
          <div>
            <p className="eyebrow">Audience</p>
            <h1 className="mt-2 text-3xl font-bold tracking-tight text-slate-950">Contacts</h1>
            <p className="mt-2 text-sm text-slate-500">Search and manage first-party customer records without loading the full audience into your browser.</p>
          </div>
          <div className="flex flex-wrap gap-2">
            {can(PERMISSIONS.CONTACT_EXPORT) && (
              <button
                className="secondary-button"
                disabled={exporting || Boolean(query.q)}
                title={query.q ? 'The frozen export contract does not define arbitrary filter exports.' : undefined}
                onClick={() => {
                  if (!companyId) return
                  setExporting(true)
                  setError('')
                  contactsApi.createExport(companyId, {
                    sourceType: 'CONTACTS',
                    sourceId: null,
                    fields: ['displayName', 'primaryEmail', 'primaryPhone', 'organization'],
                    idempotencyKey: createIdempotencyKey(),
                  }).then((resource) => {
                    setNotice('Export queued with ID ' + resource.id + '.')
                  }).catch((actionError) => {
                    setError(apiErrorMessage(actionError, 'Unable to start export.'))
                  }).finally(() => setExporting(false))
                }}
              >
                {exporting ? 'Starting export…' : 'Export contacts'}
              </button>
            )}
            {can(PERMISSIONS.CONTACT_IMPORT) && <Link className="secondary-button" to="/contacts/imports">Import CSV</Link>}
            {can(PERMISSIONS.CONTACT_CREATE) && <button className="primary-button" onClick={() => setCreating(true)}>New contact</button>}
          </div>
        </header>

        {(notice || error || contacts.error) && (
          <div role={error || contacts.error ? 'alert' : 'status'} className={'mt-5 rounded-xl border px-4 py-3 text-sm font-medium ' + (error || contacts.error ? 'border-rose-200 bg-rose-50 text-rose-700' : 'border-emerald-200 bg-emerald-50 text-emerald-700')}>
            {error || contacts.error || notice}
          </div>
        )}

        <div className="panel mt-6 p-4">
          <div className="grid gap-3 md:grid-cols-[1fr_220px]">
            <label className="text-xs font-semibold text-slate-700">
              <span className="mb-2 block">Search</span>
              <input className="form-input" type="search" placeholder="Name, email, phone or organization" value={search} onChange={(event) => setSearch(event.target.value)} />
            </label>
            <label className="text-xs font-semibold text-slate-700">
              <span className="mb-2 block">Sort</span>
              <select className="form-input" value={query.sort} onChange={(event) => {
                const next = new URLSearchParams(params)
                next.set('sort', event.target.value)
                setParams(next)
              }}>
                <option value="UPDATED_DESC">Recently updated</option>
                <option value="CREATED_DESC">Recently created</option>
                <option value="NAME_ASC">Name A–Z</option>
              </select>
            </label>
          </div>
        </div>

        {(selected.size > 0 || allMatching) && companyId && (
          <>
            {!allMatching && contacts.hasMore && (
              <div className="mt-4 rounded-xl border border-indigo-100 bg-indigo-50 px-4 py-3 text-sm">
                <button className="font-semibold text-indigo-700 underline" onClick={() => setAllMatching(true)}>
                  Select all matching results without enumerating contact IDs
                </button>
              </div>
            )}
            <BulkActionsBar
              companyId={companyId}
              selectedIds={selected}
              allMatching={allMatching}
              query={query}
              allowDelete={can(PERMISSIONS.CONTACT_DELETE)}
              onDone={(message) => {
                setNotice(message)
                void contacts.refresh()
              }}
              onError={setError}
              onClear={() => {
                setSelected(new Set())
                setAllMatching(false)
              }}
            />
          </>
        )}

        <div className="panel mt-4 overflow-hidden">
          {contacts.loading ? <div className="p-10 text-center text-sm text-slate-500" role="status">Loading contacts…</div> : contacts.items.length === 0 ? (
            <div className="p-10 text-center"><h2 className="font-bold text-slate-900">No contacts found</h2><p className="mt-2 text-sm text-slate-500">Adjust search or create your first contact.</p></div>
          ) : (
            <div className="overflow-x-auto">
              <table className="mobile-card-table min-w-full text-sm">
                <thead className="bg-slate-50 text-left text-xs uppercase tracking-wide text-slate-500">
                  <tr><th className="p-3"><span className="sr-only">Select</span></th><th className="p-3">Contact</th><th className="p-3">Email</th><th className="p-3">Phone</th><th className="p-3">Organization</th><th className="p-3">Updated</th></tr>
                </thead>
                <tbody>
                  {contacts.items.map((contact) => (
                    <tr key={contact.id} className="border-t border-slate-100">
                      <td className="p-3" data-label="Select"><input aria-label={'Select ' + contact.displayName} type="checkbox" checked={selected.has(contact.id)} disabled={allMatching} onChange={() => toggle(contact.id)} /></td>
                      <td className="p-3" data-label="Contact"><Link className="font-semibold text-indigo-700 hover:underline" to={'/contacts/' + contact.id}>{contact.displayName}</Link><div className="mt-1 flex flex-wrap gap-1">{contact.tags?.slice(0, 3).map((tag) => <span key={tag.id} className="rounded-full bg-slate-100 px-2 py-0.5 text-[11px]">{tag.name}</span>)}</div></td>
                      <td className="p-3" data-label="Email">{contact.primaryEmail || '—'}</td>
                      <td className="p-3" data-label="Phone">{contact.primaryPhone || '—'}</td>
                      <td className="p-3" data-label="Organization">{contact.organization || '—'}</td>
                      <td className="p-3" data-label="Updated">{new Date(contact.updatedAt).toLocaleDateString()}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
        {contacts.hasMore && <div className="mt-4 text-center"><button className="secondary-button" disabled={contacts.loadingMore} onClick={() => void contacts.loadMore()}>{contacts.loadingMore ? 'Loading…' : 'Load more'}</button></div>}

        <ContactFormDialog
          open={creating}
          title="Create contact"
          saving={saving}
          onClose={() => setCreating(false)}
          onSubmit={(draft) => {
            if (!companyId) return
            setSaving(true)
            setError('')
            contactsApi.create(companyId, toCreateContactInput(draft))
              .then(() => { setCreating(false); setNotice('Contact created.'); return contacts.refresh() })
              .catch((actionError) => setError(apiErrorMessage(actionError, 'Unable to create contact.')))
              .finally(() => setSaving(false))
          }}
        />
      </div>
    </section>
  )
}
