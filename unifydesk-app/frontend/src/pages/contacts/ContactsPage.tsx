import { useSnackbarState } from '../../components/feedback/useSnackbarState'
import FilterPopover from '../../components/ui/FilterPopover'
import { useEffect, useMemo, useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import ContactsNav from '../../components/contacts/ContactsNav'
import ContactFormDialog from '../../components/contacts/ContactFormDialog'
import BulkActionsBar from '../../components/contacts/BulkActionsBar'
import ExportJobButton from '../../components/contacts/ExportJobButton'
import {
  DEFAULT_CONTACT_EXPORT_FIELDS,
  emptyContactDraft,
  parseContactSearchStatus,
  toCreateContactInput,
} from '../../features/contacts/contactModel'
import { contactsApi } from '../../features/contacts/contactsApi'
import { useContacts } from '../../features/contacts/useContacts'
import type {
  ContactQuery,
  ContactSort,
  ListSummary,
  TagSummary,
} from '../../features/contacts/types'
import { PERMISSIONS } from '../../features/iam/permissions'
import { useIam } from '../../features/iam/useIam'
import { apiErrorMessage } from '../../lib/api/apiClient'
import { TableSkeleton } from '../../components/ui/Skeleton'
import { useAppSelector } from '../../store/hooks'
import { selectSelectedCompanyId, selectWorkspaceEpoch } from '../../store/workspace/workspaceSlice'
import { useWorkspaceEpochScope } from '../../features/workspace/useWorkspaceEpochScope'

export default function ContactsPage() {
  const companyId = useAppSelector(selectSelectedCompanyId)
  const workspaceEpoch = useAppSelector(selectWorkspaceEpoch)
  const { capture: captureScope, isCurrent: scopeIsCurrent } = useWorkspaceEpochScope(
    companyId,
    workspaceEpoch,
  )
  const { can } = useIam()
  const [params, setParams] = useSearchParams()
  const [search, setSearch] = useState(params.get('q') ?? '')
  const [selected, setSelected] = useState<Set<string>>(new Set())
  const [allMatching, setAllMatching] = useState(false)
  const createFromInbox = params.get('new') === '1'
  const createEmail = params.get('email') ?? ''
  const createPhone = params.get('phone') ?? ''
  const initialContactDraft = useMemo(
    () =>
      createFromInbox
        ? { ...emptyContactDraft, email: createEmail, phone: createPhone }
        : undefined,
    [createEmail, createFromInbox, createPhone],
  )
  const [creating, setCreating] = useState(createFromInbox)
  const [saving, setSaving] = useState(false)
  const [tags, setTags] = useState<TagSummary[]>([])
  const [lists, setLists] = useState<ListSummary[]>([])
  const [notice, setNotice] = useSnackbarState('', 'success')
  const [error, setError] = useSnackbarState('', 'error')
  const rawStatus = params.get('status')
  const status = parseContactSearchStatus(rawStatus)
  const query: ContactQuery = {
    q: params.get('q') || undefined,
    status,
    tagId: params.get('tagId') || undefined,
    listId: params.get('listId') || undefined,
    createdAfter: params.get('createdAfter') || undefined,
    createdBefore: params.get('createdBefore') || undefined,
    sort: (params.get('sort') as ContactSort | null) ?? 'UPDATED_DESC',
    size: 50,
  }
  const contacts = useContacts(companyId, workspaceEpoch, query)
  const canBulk = can(PERMISSIONS.CONTACT_MODIFY) || can(PERMISSIONS.CONTACT_DELETE)
  const selectionQueryKey = JSON.stringify(query)

  useEffect(() => {
    setSelected(new Set())
    setAllMatching(false)
  }, [selectionQueryKey, workspaceEpoch])

  useEffect(() => {
    if (!rawStatus || status) return
    setParams(
      (current) => {
        const next = new URLSearchParams(current)
        next.delete('status')
        next.delete('cursor')
        return next
      },
      { replace: true },
    )
  }, [rawStatus, setParams, status])

  useEffect(() => {
    setTags([])
    setLists([])
    if (!companyId) return
    const scope = captureScope()
    Promise.all([contactsApi.tags(companyId), contactsApi.lists(companyId)])
      .then(([nextTags, nextLists]) => {
        if (!scopeIsCurrent(scope)) return
        setTags(nextTags)
        setLists(nextLists.filter((item) => item.status !== 'ARCHIVED'))
      })
      .catch(() => undefined)
  }, [captureScope, companyId, scopeIsCurrent, workspaceEpoch])

  useEffect(() => {
    const timer = window.setTimeout(() => {
      setParams(
        (current) => {
          const next = new URLSearchParams(current)
          if (search.trim()) next.set('q', search.trim())
          else next.delete('q')
          next.delete('cursor')
          return next
        },
        { replace: true },
      )
    }, 300)
    return () => window.clearTimeout(timer)
  }, [search, setParams])

  const toggle = (id: string) =>
    setSelected((current) => {
      const next = new Set(current)
      if (next.has(id)) next.delete(id)
      else next.add(id)
      return next
    })

  return (
    <section className="px-4 py-6 sm:px-6 lg:px-8">
      <div className="w-full">
        <ContactsNav />
        <header className="flex flex-wrap items-end justify-between gap-4">
          <div>
            <p className="eyebrow">Audience</p>
            <h1 className="mt-2 text-3xl font-bold tracking-tight text-slate-950">Contacts</h1>
            <p className="mt-2 text-sm text-slate-500">
              Manage people, groups and communication permissions in one place.
            </p>
          </div>
          <div className="flex flex-wrap gap-2">
            {can(PERMISSIONS.CONTACT_EXPORT) && companyId && (
              <ExportJobButton
                companyId={companyId}
                sourceType="CONTACTS"
                fields={DEFAULT_CONTACT_EXPORT_FIELDS}
                label="Export contacts"
                disabled={Boolean(
                  query.q ||
                  query.status ||
                  query.tagId ||
                  query.listId ||
                  query.createdAfter ||
                  query.createdBefore,
                )}
                disabledReason="Clear the filters to export all contacts. Exporting a filtered result is not supported yet."
              />
            )}
            {can(PERMISSIONS.CONTACT_IMPORT) && (
              <Link className="secondary-button" to="/contacts/imports">
                Import CSV
              </Link>
            )}
            {can(PERMISSIONS.CONTACT_CREATE) && (
              <button className="primary-button" onClick={() => setCreating(true)}>
                + Add contact
              </button>
            )}
          </div>
        </header>

        {(notice || error || contacts.error) && (
          <div
            role={error || contacts.error ? 'alert' : 'status'}
            className={
              'mt-5 rounded-xl border px-4 py-3 text-sm font-medium ' +
              (error || contacts.error
                ? 'border-rose-200 bg-rose-50 text-rose-700'
                : 'border-emerald-200 bg-emerald-50 text-emerald-700')
            }
          >
            {error || contacts.error || notice}
          </div>
        )}

        <div className="mt-6 flex flex-wrap items-end gap-3">
          {' '}
          <label className="min-w-0 flex-1 text-xs font-semibold text-slate-700 sm:max-w-sm">
            <span className="mb-2 block">Search</span>
            <input
              className="form-input"
              type="search"
              placeholder="Name, email, phone or organization"
              value={search}
              onChange={(event) => setSearch(event.target.value)}
            />
          </label>
          <FilterPopover
            className="ml-auto"
            wide
            activeCount={
              [
                query.tagId,
                query.listId,
                query.status,
                query.createdAfter,
                query.createdBefore,
                query.sort !== 'UPDATED_DESC',
              ].filter(Boolean).length
            }
            onReset={() => {
              const next = new URLSearchParams(params)
              for (const key of [
                'tagId',
                'listId',
                'status',
                'createdAfter',
                'createdBefore',
                'sort',
                'cursor',
              ])
                next.delete(key)
              setParams(next)
            }}
          >
            <div className="grid gap-3 sm:grid-cols-2">
              <label className="text-xs font-semibold text-slate-700">
                <span className="mb-2 block">Tag</span>
                <select
                  className="form-input"
                  value={query.tagId ?? ''}
                  onChange={(event) => {
                    const next = new URLSearchParams(params)
                    if (event.target.value) next.set('tagId', event.target.value)
                    else next.delete('tagId')
                    setParams(next)
                  }}
                >
                  <option value="">All tags</option>
                  {tags.map((tag) => (
                    <option key={tag.id} value={tag.id}>
                      {tag.name}
                    </option>
                  ))}
                </select>
              </label>
              <label className="text-xs font-semibold text-slate-700">
                <span className="mb-2 block">List</span>
                <select
                  className="form-input"
                  value={query.listId ?? ''}
                  onChange={(event) => {
                    const next = new URLSearchParams(params)
                    if (event.target.value) next.set('listId', event.target.value)
                    else next.delete('listId')
                    setParams(next)
                  }}
                >
                  <option value="">All Lists</option>
                  {lists.map((list) => (
                    <option key={list.id} value={list.id}>
                      {list.name}
                    </option>
                  ))}
                </select>
              </label>
              <label className="text-xs font-semibold text-slate-700">
                <span className="mb-2 block">Status</span>
                <select
                  className="form-input"
                  value={query.status ?? ''}
                  onChange={(event) => {
                    const next = new URLSearchParams(params)
                    if (event.target.value) next.set('status', event.target.value)
                    else next.delete('status')
                    setParams(next)
                  }}
                >
                  <option value="">All active contacts</option>
                  <option value="ACTIVE">Active</option>
                  <option value="MERGED">Merged</option>
                </select>
              </label>
              <label className="text-xs font-semibold text-slate-700">
                <span className="mb-2 block">Created after</span>
                <input
                  className="form-input"
                  type="date"
                  value={query.createdAfter ?? ''}
                  onChange={(event) => {
                    const next = new URLSearchParams(params)
                    if (event.target.value) next.set('createdAfter', event.target.value)
                    else next.delete('createdAfter')
                    setParams(next)
                  }}
                />
              </label>
              <label className="text-xs font-semibold text-slate-700">
                <span className="mb-2 block">Created before</span>
                <input
                  className="form-input"
                  type="date"
                  value={query.createdBefore ?? ''}
                  onChange={(event) => {
                    const next = new URLSearchParams(params)
                    if (event.target.value) next.set('createdBefore', event.target.value)
                    else next.delete('createdBefore')
                    setParams(next)
                  }}
                />
              </label>
              <label className="text-xs font-semibold text-slate-700">
                <span className="mb-2 block">Sort</span>
                <select
                  className="form-input"
                  value={query.sort}
                  onChange={(event) => {
                    const next = new URLSearchParams(params)
                    next.set('sort', event.target.value)
                    setParams(next)
                  }}
                >
                  <option value="UPDATED_DESC">Recently updated</option>
                  <option value="CREATED_DESC">Recently created</option>
                  <option value="NAME_ASC">Name A–Z</option>
                </select>
              </label>
            </div>
          </FilterPopover>
        </div>

        {canBulk && (selected.size > 0 || allMatching) && companyId && (
          <>
            {!allMatching && contacts.hasMore && (
              <div className="mt-4 rounded-xl border border-indigo-100 bg-indigo-50 px-4 py-3 text-sm">
                <button
                  className="font-semibold text-indigo-700 underline"
                  onClick={() => setAllMatching(true)}
                >
                  Select all contacts matching these filters
                </button>
              </div>
            )}
            <BulkActionsBar
              companyId={companyId}
              selectedIds={selected}
              allMatching={allMatching}
              query={query}
              allowModify={can(PERMISSIONS.CONTACT_MODIFY)}
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
          {contacts.loading ? (
            <div className="p-4 sm:p-5">
              <TableSkeleton rows={8} columns={7} />
            </div>
          ) : contacts.items.length === 0 ? (
            <div className="p-6 sm:p-10">
              {query.q ||
              query.status ||
              query.tagId ||
              query.listId ||
              query.createdAfter ||
              query.createdBefore ? (
                <div className="text-center">
                  <h2 className="font-bold text-slate-900">No contacts match these filters</h2>
                  <p className="mt-2 text-sm text-slate-500">
                    Try changing your search, tag, List or date filters.
                  </p>
                </div>
              ) : (
                <div className="mx-auto max-w-3xl text-center">
                  <p className="text-xs font-semibold uppercase tracking-wide text-indigo-600">
                    Start your audience
                  </p>
                  <h2 className="mt-2 text-2xl font-bold text-slate-950">
                    Add your first customers
                  </h2>
                  <p className="mx-auto mt-2 max-w-xl text-sm text-slate-500">
                    Add one person manually or import a CSV. Contacts power your Lists, Segments and
                    Email, SMS and WhatsApp audiences.
                  </p>
                  <div className="mt-6 flex flex-wrap justify-center gap-3">
                    {can(PERMISSIONS.CONTACT_CREATE) && (
                      <button className="primary-button" onClick={() => setCreating(true)}>
                        Add one contact
                      </button>
                    )}
                    {can(PERMISSIONS.CONTACT_IMPORT) && (
                      <Link className="secondary-button" to="/contacts/imports">
                        Import CSV
                      </Link>
                    )}
                  </div>
                  <p className="mt-5 text-xs text-slate-400">
                    Adding a contact never opts them into marketing automatically.
                  </p>
                </div>
              )}
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="mobile-card-table min-w-full text-sm">
                <thead className="bg-slate-50 text-left text-xs uppercase tracking-wide text-slate-500">
                  <tr>
                    {canBulk && (
                      <th className="p-3">
                        <span className="sr-only">Select</span>
                      </th>
                    )}
                    <th className="p-3">Contact</th>
                    <th className="p-3">Email</th>
                    <th className="p-3">Phone</th>
                    <th className="p-3">Organization</th>
                    <th className="p-3">Marketing</th>
                    <th className="p-3">Updated</th>
                  </tr>
                </thead>
                <tbody>
                  {contacts.items.map((contact) => (
                    <tr key={contact.id} className="border-t border-slate-100">
                      {canBulk && (
                        <td className="p-3" data-label="Select">
                          <input
                            aria-label={'Select ' + contact.displayName}
                            type="checkbox"
                            checked={selected.has(contact.id)}
                            disabled={allMatching}
                            onChange={() => toggle(contact.id)}
                          />
                        </td>
                      )}
                      <td className="p-3" data-label="Contact">
                        <Link
                          className="font-semibold text-indigo-700 hover:underline"
                          to={'/contacts/' + contact.id}
                        >
                          {contact.displayName}
                        </Link>
                        <div className="mt-1 flex flex-wrap gap-1">
                          {contact.tags?.slice(0, 3).map((tag) => (
                            <span
                              key={tag.id}
                              className="rounded-full bg-slate-100 px-2 py-0.5 text-[11px]"
                            >
                              {tag.name}
                            </span>
                          ))}
                        </div>
                      </td>
                      <td className="p-3" data-label="Email">
                        {contact.primaryEmail || '—'}
                      </td>
                      <td className="p-3" data-label="Phone">
                        {contact.primaryPhone || '—'}
                      </td>
                      <td className="p-3" data-label="Organization">
                        {contact.organization || '—'}
                      </td>
                      <td className="p-3" data-label="Marketing">
                        <div className="flex flex-wrap gap-1">
                          {(['EMAIL', 'SMS', 'WHATSAPP'] as const).map((channel) => (
                            <span
                              key={channel}
                              className="rounded-full bg-slate-100 px-2 py-0.5 text-[10px] font-semibold text-slate-600"
                            >
                              {channel === 'WHATSAPP' ? 'WA' : channel}:{' '}
                              {contact.preferenceSummary?.[channel] ?? 'UNKNOWN'}
                            </span>
                          ))}
                        </div>
                      </td>
                      <td className="p-3" data-label="Updated">
                        {new Date(contact.updatedAt).toLocaleDateString()}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
        {contacts.loadingMore && (
          <div className="mt-4">
            <TableSkeleton rows={2} columns={7} />
          </div>
        )}
        {contacts.hasMore && (
          <div className="mt-4 text-center">
            <button
              className="secondary-button"
              disabled={contacts.loadingMore}
              onClick={() => void contacts.loadMore()}
            >
              {contacts.loadingMore ? 'Loading…' : 'Load more'}
            </button>
          </div>
        )}

        <ContactFormDialog
          open={creating}
          title="Create contact"
          initial={initialContactDraft}
          saving={saving}
          showMarketingPermissions
          onClose={() => {
            setCreating(false)
            if (createFromInbox) {
              setParams(
                (current) => {
                  const next = new URLSearchParams(current)
                  next.delete('new')
                  next.delete('email')
                  next.delete('phone')
                  return next
                },
                { replace: true },
              )
            }
          }}
          onSubmit={(draft, marketing) => {
            if (!companyId) return
            const scope = captureScope()
            setSaving(true)
            setError('')
            contactsApi
              .create(companyId, toCreateContactInput(draft))
              .then(async (created) => {
                if (!scopeIsCurrent(scope)) return

                let preferenceWarning = ''
                try {
                  await contactsApi.applyMarketingPreferences(
                    companyId,
                    created.id,
                    marketing,
                    'Recorded while creating the contact in UnifyDesk.',
                  )
                } catch {
                  preferenceWarning =
                    ' Contact was created, but one or more marketing permissions could not be saved.'
                }

                if (!scopeIsCurrent(scope)) return
                setCreating(false)
                setNotice('Contact created.' + preferenceWarning)
                await contacts.refresh()
              })
              .catch((actionError) => {
                if (scopeIsCurrent(scope)) {
                  setError(apiErrorMessage(actionError, 'Unable to create contact.'))
                }
              })
              .finally(() => {
                if (scopeIsCurrent(scope)) setSaving(false)
              })
          }}
        />
      </div>
    </section>
  )
}
