import { useSnackbarState } from '../../components/feedback/useSnackbarState'
import { ListSkeleton, PageSkeleton } from '../../components/ui/Skeleton'
import { useEffect, useMemo, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import ContactsNav from '../../components/contacts/ContactsNav'
import ExportJobButton from '../../components/contacts/ExportJobButton'
import ConfirmDialog from '../../components/ui/ConfirmDialog'
import { contactsApi } from '../../features/contacts/contactsApi'
import { DEFAULT_CONTACT_EXPORT_FIELDS } from '../../features/contacts/contactModel'
import type { ContactSummary, ListSummary } from '../../features/contacts/types'
import { PERMISSIONS } from '../../features/iam/permissions'
import { useIam } from '../../features/iam/useIam'
import { apiErrorMessage } from '../../lib/api/apiClient'
import { useWorkspaceEpochScope } from '../../features/workspace/useWorkspaceEpochScope'
import type { WorkspaceEpochToken } from '../../features/workspace/workspaceEpoch'
import { useAppSelector } from '../../store/hooks'
import { selectSelectedCompanyId, selectWorkspaceEpoch } from '../../store/workspace/workspaceSlice'

export default function ListDetailPage() {
  const { listId = '' } = useParams()
  const companyId = useAppSelector(selectSelectedCompanyId)
  const workspaceEpoch = useAppSelector(selectWorkspaceEpoch)
  const { capture: captureScope, isCurrent: scopeIsCurrent } = useWorkspaceEpochScope(
    companyId,
    workspaceEpoch,
  )
  const { can } = useIam()
  const navigate = useNavigate()
  const [list, setList] = useState<ListSummary | null>(null)
  const [members, setMembers] = useState<ContactSummary[]>([])
  const [cursor, setCursor] = useState<string | null>(null)
  const [hasMore, setHasMore] = useState(false)
  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
  const [memberSearch, setMemberSearch] = useState('')
  const [candidateContacts, setCandidateContacts] = useState<ContactSummary[]>([])
  const [candidateLoading, setCandidateLoading] = useState(false)
  const [selectedCandidateIds, setSelectedCandidateIds] = useState<Set<string>>(new Set())
  const [busy, setBusy] = useState(false)
  const [error, setError] = useSnackbarState('', 'error')
  const [, setNotice] = useSnackbarState('', 'success')
  const [archiveConfirmOpen, setArchiveConfirmOpen] = useState(false)
  const [memberToRemove, setMemberToRemove] = useState<ContactSummary | null>(null)

  const loadedMemberIds = useMemo(() => new Set(members.map((member) => member.id)), [members])

  const load = async (reset = true, scope: WorkspaceEpochToken | null = captureScope()) => {
    if (!companyId || !scopeIsCurrent(scope)) return false
    setError('')
    try {
      const [detail, page] = await Promise.all([
        contactsApi.getList(companyId, listId),
        contactsApi.listMembers(companyId, listId, reset ? null : cursor),
      ])
      if (!scopeIsCurrent(scope)) return false
      setList(detail)
      setName(detail.name)
      setDescription(detail.description ?? '')
      setMembers((current) =>
        reset
          ? page.items
          : [
              ...current,
              ...page.items.filter((next) => !current.some((item) => item.id === next.id)),
            ],
      )
      setCursor(page.nextCursor)
      setHasMore(page.hasMore)
      return true
    } catch (loadError) {
      if (scopeIsCurrent(scope)) setError(apiErrorMessage(loadError, 'Unable to load list.'))
      return false
    }
  }

  useEffect(() => {
    void load(true, captureScope())
  }, [captureScope, companyId, listId, workspaceEpoch])

  useEffect(() => {
    if (!companyId || memberSearch.trim().length < 2) {
      setCandidateContacts([])
      return
    }
    const scope = captureScope()
    const timer = window.setTimeout(() => {
      setCandidateLoading(true)
      contactsApi
        .list(companyId, { q: memberSearch.trim(), status: 'ACTIVE', size: 20 })
        .then((page) => {
          if (!scopeIsCurrent(scope)) return
          setCandidateContacts(page.items.filter((item) => !loadedMemberIds.has(item.id)))
        })
        .catch((searchError) => {
          if (scopeIsCurrent(scope))
            setError(apiErrorMessage(searchError, 'Unable to search contacts.'))
        })
        .finally(() => {
          if (scopeIsCurrent(scope)) setCandidateLoading(false)
        })
    }, 250)
    return () => window.clearTimeout(timer)
  }, [captureScope, companyId, loadedMemberIds, memberSearch, scopeIsCurrent, workspaceEpoch])

  const action = async (work: () => Promise<void>, success: string) => {
    const scope = captureScope()
    if (!scopeIsCurrent(scope)) return false
    setBusy(true)
    setError('')
    setNotice('')
    try {
      await work()
      if (!scopeIsCurrent(scope)) return false
      setNotice(success)
      await load(true, scope)
      return scopeIsCurrent(scope)
    } catch (actionError) {
      if (scopeIsCurrent(scope)) setError(apiErrorMessage(actionError, 'Unable to update list.'))
      return false
    } finally {
      if (scopeIsCurrent(scope)) setBusy(false)
    }
  }

  const addSelectedMembers = async () => {
    if (!companyId || selectedCandidateIds.size === 0) return
    const changed = await action(
      () =>
        contactsApi
          .addListMembers(companyId, listId, Array.from(selectedCandidateIds))
          .then(() => undefined),
      selectedCandidateIds.size === 1
        ? 'Contact added to list.'
        : `${selectedCandidateIds.size} contacts added to list.`,
    )
    if (changed) {
      setSelectedCandidateIds(new Set())
      setMemberSearch('')
      setCandidateContacts([])
    }
  }

  const archiveList = async () => {
    if (!companyId || !listId) return
    const scope = captureScope()
    setBusy(true)
    setError('')
    try {
      await contactsApi.archiveList(companyId, listId)
      if (scopeIsCurrent(scope)) navigate('/contacts/lists', { replace: true })
    } catch (archiveError) {
      if (scopeIsCurrent(scope)) setError(apiErrorMessage(archiveError, 'Unable to archive list.'))
    } finally {
      if (scopeIsCurrent(scope)) setBusy(false)
      setArchiveConfirmOpen(false)
    }
  }

  if (!list && !error)
    return (
      <section className="px-4 py-6 sm:px-6 lg:px-8">
        <PageSkeleton />
      </section>
    )
  return (
    <section className="px-4 py-6 sm:px-6 lg:px-8">
      <div className="w-full">
        <ContactsNav />
        <div className="flex flex-wrap items-end justify-between gap-4">
          <div>
            <p className="eyebrow">List</p>
            <h1 className="mt-2 text-3xl font-bold text-slate-950">{list?.name || 'List'}</h1>
            <p className="mt-2 text-sm text-slate-500">
              {list?.memberCount.toLocaleString() || 0}{' '}
              {list?.memberCount === 1 ? 'contact' : 'contacts'} in this list.
            </p>
          </div>
          {list && companyId && (
            <div className="flex flex-wrap gap-2">
              {can(PERMISSIONS.BROADCAST_CREATE) && (
                <Link
                  className="primary-button"
                  to={`/broadcast/email?audienceType=CONTACT_LIST&audienceId=${listId}`}
                >
                  Create broadcast
                </Link>
              )}
              {can(PERMISSIONS.CONTACT_EXPORT) && (
                <ExportJobButton
                  companyId={companyId}
                  sourceType="LIST"
                  sourceId={listId}
                  fields={DEFAULT_CONTACT_EXPORT_FIELDS}
                  label="Export list"
                />
              )}
              {can(PERMISSIONS.CONTACT_MODIFY) && (
                <button
                  className="secondary-button text-rose-700"
                  disabled={busy}
                  onClick={() => setArchiveConfirmOpen(true)}
                >
                  Archive list
                </button>
              )}
            </div>
          )}
        </div>



        {can(PERMISSIONS.CONTACT_MODIFY) && list && (
          <section className="panel mt-6 p-5">
            <h2 className="font-bold text-slate-950">List details</h2>
            <div className="mt-4 grid gap-3 md:grid-cols-[1fr_1.5fr_auto]">
              <label className="text-xs font-semibold text-slate-700">
                Name
                <input
                  className="form-input mt-1"
                  value={name}
                  onChange={(event) => setName(event.target.value)}
                />
              </label>
              <label className="text-xs font-semibold text-slate-700">
                Description
                <input
                  className="form-input mt-1"
                  value={description}
                  onChange={(event) => setDescription(event.target.value)}
                />
              </label>
              <button
                className="primary-button self-end"
                disabled={busy || !name.trim()}
                onClick={() => {
                  if (!companyId) return
                  void action(
                    () =>
                      contactsApi
                        .updateList(companyId, listId, {
                          name: name.trim(),
                          description: description.trim() || null,
                        })
                        .then(() => undefined),
                    'List updated.',
                  )
                }}
              >
                Save
              </button>
            </div>
          </section>
        )}

        {can(PERMISSIONS.CONTACT_MODIFY) && (
          <section className="panel mt-4 p-5">
            <h2 className="font-bold text-slate-950">Add contacts</h2>
            <p className="mt-1 text-sm text-slate-500">
              Search by name, email, phone or company. Select the people you want to add.
            </p>
            <div className="mt-4">
              <input
                className="form-input"
                type="search"
                placeholder="Search contacts…"
                value={memberSearch}
                onChange={(event) => setMemberSearch(event.target.value)}
              />
            </div>
            {memberSearch.trim().length > 0 && memberSearch.trim().length < 2 && (
              <p className="mt-2 text-xs text-slate-500">Type at least 2 characters to search.</p>
            )}
            {candidateLoading ? (
              <ListSkeleton rows={3} />
            ) : candidateContacts.length > 0 ? (
              <div className="mt-3 max-h-72 overflow-auto rounded-xl border border-slate-200">
                {candidateContacts.map((candidate) => {
                  const checked = selectedCandidateIds.has(candidate.id)
                  return (
                    <label
                      key={candidate.id}
                      className="flex cursor-pointer items-center gap-3 border-b border-slate-100 p-3 last:border-b-0 hover:bg-slate-50"
                    >
                      <input
                        type="checkbox"
                        checked={checked}
                        onChange={() =>
                          setSelectedCandidateIds((current) => {
                            const next = new Set(current)
                            if (next.has(candidate.id)) next.delete(candidate.id)
                            else next.add(candidate.id)
                            return next
                          })
                        }
                      />
                      <div className="min-w-0">
                        <p className="truncate font-semibold text-slate-900">
                          {candidate.displayName}
                        </p>
                        <p className="truncate text-xs text-slate-500">
                          {candidate.primaryEmail ||
                            candidate.primaryPhone ||
                            candidate.organization ||
                            'No contact method'}
                        </p>
                      </div>
                    </label>
                  )
                })}
              </div>
            ) : memberSearch.trim().length >= 2 ? (
              <p className="mt-3 text-sm text-slate-500">No additional contacts found.</p>
            ) : null}
            <button
              className="primary-button mt-3"
              disabled={busy || selectedCandidateIds.size === 0}
              onClick={() => void addSelectedMembers()}
            >
              Add selected {selectedCandidateIds.size > 0 ? `(${selectedCandidateIds.size})` : ''}
            </button>
          </section>
        )}

        <div className="panel mt-4 overflow-hidden">
          {members.length === 0 ? (
            <div className="p-8 text-center">
              <h2 className="font-bold text-slate-900">No contacts in this list yet</h2>
              <p className="mt-2 text-sm text-slate-500">
                Use the search above to add your first contacts.
              </p>
            </div>
          ) : (
            <table className="mobile-card-table min-w-full text-sm">
              <thead>
                <tr className="bg-slate-50 text-left">
                  <th className="p-3">Contact</th>
                  <th className="p-3">Email</th>
                  <th className="p-3">Organization</th>
                  {can(PERMISSIONS.CONTACT_MODIFY) && <th className="p-3">Action</th>}
                </tr>
              </thead>
              <tbody>
                {members.map((member) => (
                  <tr key={member.id} className="border-t border-slate-100">
                    <td data-label="Contact" className="p-3">
                      <Link className="font-semibold text-indigo-700" to={'/contacts/' + member.id}>
                        {member.displayName}
                      </Link>
                    </td>
                    <td data-label="Email" className="p-3">
                      {member.primaryEmail || '—'}
                    </td>
                    <td data-label="Organization" className="p-3">
                      {member.organization || '—'}
                    </td>
                    {can(PERMISSIONS.CONTACT_MODIFY) && (
                      <td data-label="Action" className="mobile-action-cell p-3">
                        <button
                          className="secondary-button"
                          disabled={busy}
                          onClick={() => setMemberToRemove(member)}
                        >
                          Remove
                        </button>
                      </td>
                    )}
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
        {hasMore && (
          <button
            className="secondary-button mt-4"
            disabled={busy}
            onClick={() => void load(false, captureScope())}
          >
            Load more
          </button>
        )}
      </div>

      <ConfirmDialog
        open={archiveConfirmOpen}
        title="Archive this list?"
        description="Historical references stay intact, but the list will no longer be available for normal use."
        confirmLabel="Yes, archive list"
        cancelLabel="Cancel"
        tone="warning"
        working={busy}
        onCancel={() => setArchiveConfirmOpen(false)}
        onConfirm={() => void archiveList()}
      />
      <ConfirmDialog
        open={Boolean(memberToRemove)}
        title="Remove this contact from the list?"
        description={
          memberToRemove
            ? `${memberToRemove.displayName} will be removed from this list only. The contact record will remain.`
            : ''
        }
        confirmLabel="Yes, remove"
        cancelLabel="Cancel"
        tone="warning"
        working={busy}
        onCancel={() => setMemberToRemove(null)}
        onConfirm={() => {
          if (!memberToRemove || !companyId) return
          void action(
            () => contactsApi.removeListMember(companyId, listId, memberToRemove.id),
            'Contact removed from list.',
          ).finally(() => setMemberToRemove(null))
        }}
      />
    </section>
  )
}
