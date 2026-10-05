import { useEffect, useState, type FormEvent } from 'react'
import { Link } from 'react-router'
import ContactsNav from '../../components/contacts/ContactsNav'
import { contactsApi } from '../../features/contacts/contactsApi'
import type { ListSummary } from '../../features/contacts/types'
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
import { ListSkeleton } from '../../components/ui/Skeleton'

export default function ListsPage() {
  const companyId = useAppSelector(selectSelectedCompanyId)
  const workspaceEpoch = useAppSelector(selectWorkspaceEpoch)
  const { capture: captureScope, isCurrent: scopeIsCurrent } = useWorkspaceEpochScope(
    companyId,
    workspaceEpoch,
  )
  const { can } = useIam()
  const [items, setItems] = useState<ListSummary[]>([])
  const [name, setName] = useState('')
  const [loading, setLoading] = useState(true)
  const [creating, setCreating] = useState(false)
  const [error, setError] = useState('')

  const load = async (scope: WorkspaceEpochToken | null = captureScope()) => {
    if (!companyId || !scopeIsCurrent(scope)) return
    setLoading(true)
    setError('')
    try {
      const nextItems = await contactsApi.lists(companyId)
      if (!scopeIsCurrent(scope)) return
      setItems(nextItems.filter((item) => item.status !== 'ARCHIVED'))
    } catch (loadError) {
      if (scopeIsCurrent(scope)) {
        setError(apiErrorMessage(loadError, 'Unable to load Lists.'))
      }
    } finally {
      if (scopeIsCurrent(scope)) setLoading(false)
    }
  }

  useEffect(() => {
    void load(captureScope())
  }, [captureScope, companyId, scopeIsCurrent, workspaceEpoch])

  const create = async (event: FormEvent) => {
    event.preventDefault()
    if (!companyId || !name.trim() || creating) return

    const scope = captureScope()
    setCreating(true)
    setError('')
    try {
      await contactsApi.createList(companyId, { name: name.trim() })
      if (!scopeIsCurrent(scope)) return
      setName('')
      await load(scope)
    } catch (createError) {
      if (scopeIsCurrent(scope)) {
        setError(apiErrorMessage(createError, 'Unable to create List.'))
      }
    } finally {
      if (scopeIsCurrent(scope)) setCreating(false)
    }
  }

  return (
    <section className="px-4 py-6 sm:px-6 lg:px-8">
      <div className="mx-auto max-w-6xl">
        <ContactsNav />
        <header className="flex flex-wrap items-end justify-between gap-4">
          <div>
            <p className="eyebrow">Audience</p>
            <h1 className="mt-2 text-3xl font-bold text-slate-950">Lists</h1>
            <p className="mt-2 text-sm text-slate-500">
              Static membership with server-side counts and cursor-paged members.
            </p>
          </div>

          {can(PERMISSIONS.CONTACT_MODIFY) && (
            <form className="flex w-full gap-2 sm:w-auto" onSubmit={create}>
              <label className="sr-only" htmlFor="list-name">
                List name
              </label>
              <input
                id="list-name"
                className="form-input"
                value={name}
                placeholder="New List name"
                onChange={(event) => setName(event.target.value)}
              />
              <button
                className="primary-button whitespace-nowrap"
                disabled={creating || !name.trim()}
              >
                {creating ? 'Creating…' : 'Create List'}
              </button>
            </form>
          )}
        </header>

        {error && (
          <div
            role="alert"
            className="mt-5 rounded-xl border border-rose-200 bg-rose-50 p-3 text-sm text-rose-700"
          >
            {error}
          </div>
        )}

        {loading ? (
          <div className="mt-6"><ListSkeleton rows={6} /></div>
        ) : items.length === 0 ? (
          <div className="panel mt-6 p-10 text-center">
            <h2 className="font-bold text-slate-950">No Lists yet</h2>
            <p className="mt-2 text-sm text-slate-500">
              Create a static audience or add Contacts to a List from bulk actions.
            </p>
          </div>
        ) : (
          <div className="mt-6 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {items.map((item) => (
              <Link
                key={item.id}
                to={'/contacts/lists/' + item.id}
                className="panel p-5 hover:border-indigo-200"
              >
                <h2 className="font-bold text-slate-950">{item.name}</h2>
                <p className="mt-2 text-sm text-slate-500">
                  {item.description || 'Static audience List'}
                </p>
                <p className="mt-4 text-2xl font-bold text-slate-950">
                  {item.memberCount.toLocaleString()}
                </p>
                <p className="text-xs text-slate-500">members</p>
              </Link>
            ))}
          </div>
        )}
      </div>
    </section>
  )
}
