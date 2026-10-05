import { useEffect, useState } from 'react'
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
import { useAppSelector } from '../../store/hooks'
import {
  selectSelectedCompanyId,
  selectWorkspaceEpoch,
} from '../../store/workspace/workspaceSlice'
import { useWorkspaceEpochScope } from '../../features/workspace/useWorkspaceEpochScope'
import type { WorkspaceEpochToken } from '../../features/workspace/workspaceEpoch'

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
  const [memberIds, setMemberIds] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [archiveConfirmOpen, setArchiveConfirmOpen] = useState(false)

  const load = async (
    reset = true,
    scope: WorkspaceEpochToken | null = captureScope(),
  ) => {
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
      if (scopeIsCurrent(scope)) {
        setError(apiErrorMessage(loadError, 'Unable to load List.'))
      }
      return false
    }
  }

  useEffect(() => {
    void load(true, captureScope())
  }, [captureScope, companyId, listId, workspaceEpoch])

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
      if (scopeIsCurrent(scope)) {
        setError(apiErrorMessage(actionError, 'Unable to update List.'))
      }
      return false
    } finally {
      if (scopeIsCurrent(scope)) setBusy(false)
    }
  }

  const addMembers = async () => {
    const ids = Array.from(
      new Set(
        memberIds
          .split(/[\s,]+/)
          .map((item) => item.trim())
          .filter(Boolean),
      ),
    )
    if (!companyId || ids.length === 0) return
    if (ids.length > 500) {
      setError(
        'Small List membership changes are limited to 500 Contact IDs. Use bulk actions for larger selections.',
      )
      return
    }
    const changed = await action(
      () => contactsApi.addListMembers(companyId, listId, ids).then(() => undefined),
      'List members added.',
    )
    if (changed) setMemberIds('')
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
      if (scopeIsCurrent(scope)) {
        setError(apiErrorMessage(archiveError, 'Unable to archive List.'))
      }
    } finally {
      if (scopeIsCurrent(scope)) setBusy(false)
      setArchiveConfirmOpen(false)
    }
  }

  return (
    <section className="px-4 py-6 sm:px-6 lg:px-8">
      <div className="mx-auto max-w-6xl">
        <ContactsNav />
        <div className="flex flex-wrap items-end justify-between gap-4">
          <div>
            <p className="eyebrow">Static audience</p>
            <h1 className="mt-2 text-3xl font-bold text-slate-950">{list?.name || 'List'}</h1>
            <p className="mt-2 text-sm text-slate-500">
              {list?.memberCount.toLocaleString() || 0} members. Membership remains server-side and
              is loaded by cursor.
            </p>
          </div>
          {list && companyId && (
            <div className="flex flex-wrap gap-2">
              {can(PERMISSIONS.BROADCAST_CREATE) && (
                <Link
                  className="primary-button"
                  to={`/broadcast/email?audienceType=CONTACT_LIST&audienceId=${listId}`}
                >
                  Create Broadcast
                </Link>
              )}
              {can(PERMISSIONS.CONTACT_EXPORT) && (
                <ExportJobButton
                  companyId={companyId}
                  sourceType="LIST"
                  sourceId={listId}
                  fields={DEFAULT_CONTACT_EXPORT_FIELDS}
                  label="Export List"
                />
              )}
              {can(PERMISSIONS.CONTACT_MODIFY) && (
                <button
                  className="secondary-button text-rose-700"
                  disabled={busy}
                  onClick={() => setArchiveConfirmOpen(true)}
                >
                  Archive List
                </button>
              )}
            </div>
          )}
        </div>

        {(error || notice) && (
          <div
            role={error ? 'alert' : 'status'}
            className={
              'mt-5 rounded-xl border px-4 py-3 text-sm ' +
              (error
                ? 'border-rose-200 bg-rose-50 text-rose-700'
                : 'border-emerald-200 bg-emerald-50 text-emerald-700')
            }
          >
            {error || notice}
          </div>
        )}

        {can(PERMISSIONS.CONTACT_MODIFY) && list && (
          <section className="panel mt-6 p-5">
            <h2 className="font-bold text-slate-950">List settings</h2>
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
            <h2 className="font-bold text-slate-950">Add a small set of members</h2>
            <p className="mt-1 text-xs text-slate-500">
              Enter up to 500 Contact IDs. Use Contacts bulk selection for larger audiences.
            </p>
            <div className="mt-3 flex flex-col gap-2 sm:flex-row">
              <label className="sr-only" htmlFor="list-member-ids">
                Contact IDs
              </label>
              <input
                id="list-member-ids"
                className="form-input"
                value={memberIds}
                placeholder="123, 456, 789"
                onChange={(event) => setMemberIds(event.target.value)}
              />
              <button
                className="primary-button"
                disabled={busy || !memberIds.trim()}
                onClick={() => void addMembers()}
              >
                Add members
              </button>
            </div>
          </section>
        )}

        <div className="panel mt-4 overflow-hidden">
          {members.length === 0 ? (
            <div className="p-8 text-center text-sm text-slate-500">
              This List has no loaded members.
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
                          onClick={() => {
                            if (!companyId || !window.confirm('Remove this Contact from the List?'))
                              return
                            void action(
                              () => contactsApi.removeListMember(companyId, listId, member.id),
                              'List member removed.',
                            )
                          }}
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
          description="Historical references will remain intact, but the list will no longer be available for normal active use."
          confirmLabel="Yes, archive list"
          cancelLabel="No, keep list"
          tone="warning"
          working={busy}
          onCancel={() => setArchiveConfirmOpen(false)}
          onConfirm={() => void archiveList()}
        />
    </section>
  )
}
