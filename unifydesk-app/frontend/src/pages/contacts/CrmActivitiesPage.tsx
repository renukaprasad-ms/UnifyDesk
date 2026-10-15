import { ListSkeleton } from '../../components/ui/Skeleton'
import WorkspaceDialog from '../../components/ui/WorkspaceDialog'
import FilterPopover from '../../components/ui/FilterPopover'
import { useCallback, useEffect, useRef, useState } from 'react'
import { Link } from 'react-router'
import { FiActivity, FiArrowRight, FiClock, FiEdit3, FiRefreshCw } from 'react-icons/fi'
import { crmSalesApi, salesLabel, type CrmActivity } from '../../features/contacts/crmSalesApi'
import { contactsApi } from '../../features/contacts/contactsApi'
import { PERMISSIONS } from '../../features/iam/permissions'
import { useIam } from '../../features/iam/useIam'
import { useWorkspaceEpochScope } from '../../features/workspace/useWorkspaceEpochScope'
import { leadDateTime } from '../../features/contacts/leadPresentation'
import { apiErrorMessage } from '../../lib/api/apiClient'
import { useAppSelector } from '../../store/hooks'
import { selectSelectedCompanyId, selectWorkspaceEpoch } from '../../store/workspace/workspaceSlice'

type Filters = { source?: string; person?: string; contactId?: string; from?: string; to?: string }
export default function CrmActivitiesPage() {
  const companyId = useAppSelector(selectSelectedCompanyId),
    epoch = useAppSelector(selectWorkspaceEpoch)
  const { capture, isCurrent } = useWorkspaceEpochScope(companyId, epoch)
  const { can } = useIam()
  const [items, setItems] = useState<CrmActivity[]>([])
  const [next, setNext] = useState<string | null>(null)
  const [source, setSource] = useState(''),
    [person, setPerson] = useState('')
  const [from, setFrom] = useState(''),
    [to, setTo] = useState(''),
    [contactId, setContactId] = useState('')
  const [filters, setFilters] = useState<Filters>(() => ({ to: new Date().toISOString() }))
  const [contacts, setContacts] = useState<{ id: string; name: string }[]>([])
  const [contactSearch, setContactSearch] = useState('')
  const [note, setNote] = useState(''),
    [noteContact, setNoteContact] = useState('')
  const [showNote, setShowNote] = useState(false),
    [saving, setSaving] = useState(false)
  const [loading, setLoading] = useState(true),
    [error, setError] = useState('')
  const request = useRef(0)
  const load = useCallback(
    async (cursor?: string) => {
      if (!companyId) return
      const scope = capture(),
        id = ++request.current
      setLoading(true)
      try {
        const page = await crmSalesApi.activities(companyId, { ...filters, cursor })
        if (!isCurrent(scope) || id !== request.current) return
        setItems((old) => (cursor ? [...old, ...page.items] : page.items))
        setNext(page.nextCursor)
        setError('')
      } catch (e) {
        if (isCurrent(scope) && id === request.current)
          setError(apiErrorMessage(e, 'Unable to load CRM activity'))
      } finally {
        if (isCurrent(scope) && id === request.current) setLoading(false)
      }
    },
    [companyId, filters, capture, isCurrent],
  )
  useEffect(() => {
    let active = true
    void Promise.resolve().then(() => {
      if (active) void load()
    })
    return () => {
      active = false
    }
  }, [load])
  useEffect(() => {
    if (!companyId) return
    let active = true
    const scope = capture()
    const timer = setTimeout(() => {
      void contactsApi
        .list(companyId, { q: contactSearch || undefined, status: 'ACTIVE', size: 30 })
        .then((page) => {
          if (active && isCurrent(scope))
            setContacts(
              page.items.map((c) => ({
                id: c.id,
                name: c.displayName || c.primaryEmail || 'Unnamed contact',
              })),
            )
        })
        .catch(() => {
          if (active && isCurrent(scope)) setContacts([])
        })
    }, 250)
    return () => {
      active = false
      clearTimeout(timer)
    }
  }, [companyId, contactSearch, capture, isCurrent])
  const save = async () => {
    if (!companyId || saving) return
    const scope = capture()
    setSaving(true)
    try {
      await crmSalesApi.note(companyId, note.trim(), noteContact || null)
      if (isCurrent(scope)) {
        setNote('')
        setShowNote(false)
        setFilters((old) => ({ ...old, to: new Date().toISOString() }))
      }
    } catch (e) {
      if (isCurrent(scope)) setError(apiErrorMessage(e, 'Unable to save note'))
    } finally {
      if (isCurrent(scope)) setSaving(false)
    }
  }
  const sources = [
    'NOTE',
    'CONTACT',
    ...(can(PERMISSIONS.CRM_LEAD_READ) ? ['LEAD'] : []),
    ...(can(PERMISSIONS.CRM_TASK_READ) ? ['TASK'] : []),
    ...(can(PERMISSIONS.CRM_DEAL_READ) && can(PERMISSIONS.CRM_ACCOUNT_READ) ? ['DEAL'] : []),
    ...(can(PERMISSIONS.EMAIL_READ) || can(PERMISSIONS.WHATSAPP_READ) ? ['INBOX'] : []),
    ...(can(PERMISSIONS.BROADCAST_READ) ? ['BROADCAST'] : []),
  ]
  return (
    <section className="space-y-5 px-4 py-6 sm:px-6">
      <header className="flex flex-wrap items-center justify-between gap-4">
        <div>
          <p className="eyebrow">CUSTOMER RELATIONSHIPS</p>
          <h1 className="mt-2 text-3xl font-bold text-slate-950">Activities</h1>
          <p className="mt-2 text-sm text-slate-500">
            A shared timeline of customer conversations, CRM changes and internal notes.
          </p>
        </div>
        <div className="flex gap-2">
          <FilterPopover
            wide
            activeCount={
              Number(!!filters.source) +
              Number(!!filters.person) +
              Number(!!filters.contactId) +
              Number(!!filters.from) +
              Number(!!to)
            }
            onReset={() => {
              setSource('')
              setPerson('')
              setFrom('')
              setTo('')
              setContactId('')
              setContactSearch('')
              setFilters({ to: new Date().toISOString() })
            }}
          >
            {' '}
            <form
              className="space-y-4"
              onSubmit={(e) => {
                e.preventDefault()
                const end = to ? new Date(to + 'T00:00:00') : new Date()
                if (to) end.setDate(end.getDate() + 1)
                setFilters({
                  source: source || undefined,
                  person: person.trim() || undefined,
                  contactId: contactId || undefined,
                  from: from ? new Date(from + 'T00:00:00').toISOString() : undefined,
                  to: end.toISOString(),
                })
              }}
            >
              <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-2">
                <label className="text-xs font-semibold text-slate-600">
                  Activity type
                  <select
                    className="form-input mt-2 w-full"
                    value={source}
                    onChange={(e) => setSource(e.target.value)}
                  >
                    <option value="">All permitted activity</option>
                    {sources.map((s) => (
                      <option key={s} value={s}>
                        {salesLabel(s)}
                      </option>
                    ))}
                  </select>
                </label>
                <label className="text-xs font-semibold text-slate-600">
                  Changed by
                  <input
                    className="form-input mt-2 w-full"
                    placeholder="Name or username"
                    maxLength={100}
                    value={person}
                    onChange={(e) => setPerson(e.target.value)}
                  />
                </label>
                <label className="text-xs font-semibold text-slate-600">
                  From
                  <input
                    type="date"
                    className="form-input mt-2 w-full"
                    value={from}
                    onChange={(e) => setFrom(e.target.value)}
                  />
                </label>
                <label className="text-xs font-semibold text-slate-600">
                  Through
                  <input
                    type="date"
                    className="form-input mt-2 w-full"
                    value={to}
                    onChange={(e) => setTo(e.target.value)}
                  />
                </label>
                <label className="text-xs font-semibold text-slate-600">
                  Find a contact
                  <input
                    className="form-input mt-2 w-full"
                    value={contactSearch}
                    placeholder="Search name, email or phone"
                    onChange={(e) => setContactSearch(e.target.value)}
                  />
                </label>
                <label className="text-xs font-semibold text-slate-600">
                  Related contact
                  <select
                    className="form-input mt-2 w-full"
                    value={contactId}
                    onChange={(e) => setContactId(e.target.value)}
                  >
                    <option value="">Everyone</option>
                    {contactId && !contacts.some((c) => c.id === contactId) && (
                      <option value={contactId}>Selected contact</option>
                    )}
                    {contacts.map((c) => (
                      <option key={c.id} value={c.id}>
                        {c.name}
                      </option>
                    ))}
                  </select>
                </label>
              </div>
              <div className="mt-4 flex flex-wrap items-center gap-3">
                <button className="secondary-button" disabled={loading}>
                  Apply filters
                </button>
                <p className="text-xs text-slate-500">
                  Defaults to the last 30 days. Date ranges can cover up to a year.
                </p>
              </div>
            </form>
          </FilterPopover>
          <button
            className="secondary-button gap-2"
            disabled={loading}
            onClick={() =>
              setFilters((old) => ({ ...old, to: to ? old.to : new Date().toISOString() }))
            }
          >
            <FiRefreshCw />
            Refresh
          </button>
          {can(PERMISSIONS.CRM_ACTIVITY_MODIFY) && (
            <button
              className="primary-button gap-2"
              onClick={() => setShowNote((old) => !old)}
              aria-expanded={showNote}
            >
              <FiEdit3 />
              Add note
            </button>
          )}
        </div>
      </header>
      {error && !showNote && (
        <p role="alert" className="rounded-xl bg-rose-50 p-4 text-sm text-rose-700">
          {error}
        </p>
      )}

      {showNote && can(PERMISSIONS.CRM_ACTIVITY_MODIFY) && (
        <WorkspaceDialog
          title="Add internal note"
          busy={saving}
          error={error}
          onClose={() => setShowNote(false)}
        >
          <form
            className="space-y-4"
            onSubmit={(e) => {
              e.preventDefault()
              void save()
            }}
          >
            <h2 className="text-sm font-bold text-slate-950">Internal note</h2>
            <p className="mt-1 text-xs text-slate-500">
              Visible to teammates with CRM activity access. This does not send a message to the
              customer.
            </p>
            <label className="mt-4 block text-xs font-semibold text-slate-600">
              Note
              <textarea
                className="form-input mt-2 w-full"
                rows={3}
                required
                maxLength={2000}
                value={note}
                onChange={(e) => setNote(e.target.value)}
              />
            </label>
            <label className="mt-3 block text-xs font-semibold text-slate-600">
              Link to contact
              <select
                className="form-input mt-2 w-full"
                value={noteContact}
                onChange={(e) => setNoteContact(e.target.value)}
              >
                <option value="">Workspace note</option>
                {noteContact && !contacts.some((c) => c.id === noteContact) && (
                  <option value={noteContact}>Selected contact</option>
                )}
                {contacts.map((c) => (
                  <option key={c.id} value={c.id}>
                    {c.name}
                  </option>
                ))}
              </select>
            </label>
            <div className="mt-4 flex gap-2">
              <button type="submit" className="primary-button" disabled={saving || !note.trim()}>
                {saving ? 'Saving…' : 'Save note'}
              </button>
              <button
                type="button"
                className="secondary-button"
                disabled={saving}
                onClick={() => setShowNote(false)}
              >
                Cancel
              </button>
            </div>
          </form>
        </WorkspaceDialog>
      )}
      <div className="flex items-center justify-between">
        <h2 className="flex items-center gap-2 text-base font-bold text-slate-950">
          <FiClock />
          Recent activity
        </h2>
        <span className="text-xs text-slate-500">
          Times in {Intl.DateTimeFormat().resolvedOptions().timeZone}
        </span>
      </div>
      {loading && !items.length ? (
        <ListSkeleton rows={3} />
      ) : !items.length ? (
        <div className="panel p-12 text-center">
          <FiActivity className="mx-auto text-3xl text-indigo-400" />
          <h2 className="mt-4 font-bold text-slate-950">A clear record starts here</h2>
          <p className="mt-2 text-sm text-slate-500">
            Changes and conversations will appear here. Try a wider date range or add an internal
            note.
          </p>
        </div>
      ) : (
        <ol className="panel divide-y divide-slate-100">
          {items.map((item) => (
            <li key={`${item.source}:${item.id}`} className="flex gap-4 p-5">
              <span className="flex size-10 shrink-0 items-center justify-center rounded-xl bg-indigo-50 text-indigo-500">
                <FiActivity />
              </span>
              <div className="min-w-0 flex-1">
                <div className="flex flex-wrap items-center gap-2">
                  <h3 className="break-words text-sm font-bold text-slate-950">{item.title}</h3>
                  <span className="rounded-full bg-slate-100 px-2 py-1 text-[10px] font-semibold text-slate-500">
                    {item.source === 'NOTE' ? 'Internal note' : salesLabel(item.source)}
                  </span>
                </div>
                <p className="mt-2 whitespace-pre-wrap break-words text-sm text-slate-600">
                  {item.detail}
                </p>
                <p className="mt-3 text-xs text-slate-500">
                  {item.actorName ||
                    item.actorUsername ||
                    (['INBOX', 'BROADCAST'].includes(item.source)
                      ? 'Provider activity'
                      : 'Actor unavailable')}{' '}
                  · <time dateTime={item.occurredAt}>{leadDateTime(item.occurredAt)}</time>
                </p>
                {item.href && (
                  <Link
                    className="mt-3 inline-flex items-center gap-2 text-xs font-semibold text-indigo-600"
                    to={item.href}
                  >
                    Open source <FiArrowRight />
                  </Link>
                )}
              </div>
            </li>
          ))}
        </ol>
      )}
      {loading && items.length > 0 && <ListSkeleton rows={2} />}
      {next && (
        <button className="secondary-button" disabled={loading} onClick={() => void load(next)}>
          {loading ? 'Loading…' : 'Load more activity'}
        </button>
      )}
    </section>
  )
}
