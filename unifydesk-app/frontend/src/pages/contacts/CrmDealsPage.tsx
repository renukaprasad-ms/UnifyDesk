import AssigneeDialog from '../../components/ui/AssigneeDialog'
import BoardViewToggle from '../../components/ui/BoardViewToggle'
import { BoardSkeleton, TableSkeleton, ListSkeleton } from '../../components/ui/Skeleton'
import WorkspaceDialog from '../../components/ui/WorkspaceDialog'
import FilterPopover from '../../components/ui/FilterPopover'
import { useCallback, useEffect, useRef, useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import {
  FiArrowRight,
  FiBriefcase,
  FiClock,
  FiPlus,
  FiRefreshCw,
  FiUser,
  FiX,
} from 'react-icons/fi'
import {
  crmSalesApi,
  dealMoney,
  dealStages,
  dealStageColor,
  salesLabel,
  type Deal,
  type DealEvent,
  type DealFields,
  type DealStage,
} from '../../features/contacts/crmSalesApi'
import { crmWorkApi } from '../../features/contacts/crmWorkApi'
import { contactsApi } from '../../features/contacts/contactsApi'
import { companyApi } from '../../features/company/companyApi'
import { PERMISSIONS } from '../../features/iam/permissions'
import { useIam } from '../../features/iam/useIam'
import { useWorkspaceEpochScope } from '../../features/workspace/useWorkspaceEpochScope'
import { leadDateTime } from '../../features/contacts/leadPresentation'
import { apiErrorMessage } from '../../lib/api/apiClient'
import { useAppSelector } from '../../store/hooks'
import { selectSelectedCompanyId, selectWorkspaceEpoch } from '../../store/workspace/workspaceSlice'

const empty: DealFields = {
  title: '',
  description: '',
  amount: '0',
  currency: 'INR',
  expectedCloseDate: null,
  ownerUserId: null,
  contactId: null,
  accountId: null,
}

function DealDetails({
  companyId,
  deal,
  onClose,
  onEdit,
  canEdit,
}: {
  companyId: string
  deal: Deal
  onClose: () => void
  onEdit: () => void
  canEdit: boolean
}) {
  const [result, setResult] = useState<{
    version: number
    events: DealEvent[]
    error: string
  } | null>(null)
  useEffect(() => {
    let active = true
    void crmSalesApi
      .history(companyId, deal.id)
      .then((events) => {
        if (active) setResult({ version: deal.version, events, error: '' })
      })
      .catch((error) => {
        if (active)
          setResult({
            version: deal.version,
            events: [],
            error: apiErrorMessage(error, 'Unable to load history'),
          })
      })
    return () => {
      active = false
    }
  }, [companyId, deal.id, deal.version])
  return (
    <aside className="panel p-5">
      <div className="flex justify-between gap-3">
        <div>
          <p className="eyebrow">OPPORTUNITY</p>
          <h2 className="mt-2 break-words text-lg font-bold text-slate-950">{deal.title}</h2>
        </div>
        <button className="secondary-button p-2" onClick={onClose} aria-label="Close deal details">
          <FiX />
        </button>
      </div>
      <p className="mt-4 text-2xl font-bold text-slate-950">
        {dealMoney(deal.amount, deal.currency)}
      </p>
      <span
        className={`mt-3 inline-block rounded-full px-3 py-1 text-xs font-bold ${dealStageColor(deal.stage)}`}
      >
        {salesLabel(deal.stage)}
      </span>
      <p className="mt-4 whitespace-pre-wrap break-words text-sm leading-6 text-slate-500">
        {deal.description || 'No description added.'}
      </p>
      <dl className="mt-5 grid grid-cols-2 gap-4 text-xs">
        <div>
          <dt className="text-slate-500">Owner</dt>
          <dd className="mt-1 text-slate-900">{deal.ownerName || 'Unassigned'}</dd>
        </div>
        <div>
          <dt className="text-slate-500">Expected close</dt>
          <dd className="mt-1 text-slate-900">{deal.expectedCloseDate || 'Not scheduled'}</dd>
        </div>
        <div>
          <dt className="text-slate-500">Created</dt>
          <dd className="mt-1 text-slate-900">{leadDateTime(deal.createdAt)}</dd>
        </div>
        <div>
          <dt className="text-slate-500">Updated</dt>
          <dd className="mt-1 text-slate-900">{leadDateTime(deal.updatedAt)}</dd>
        </div>
      </dl>
      <div className="mt-4 flex flex-wrap gap-3 text-xs font-semibold text-indigo-600">
        {deal.contactId && (
          <Link to={`/contacts/${deal.contactId}`}>{deal.contactName || 'Contact'}</Link>
        )}
        {deal.accountId && (
          <Link to={`/crm/companies/${deal.accountId}`}>{deal.accountName || 'Company'}</Link>
        )}
      </div>
      {canEdit && (
        <button className="secondary-button mt-5" onClick={onEdit}>
          Edit opportunity
        </button>
      )}
      <h3 className="mt-6 flex items-center gap-2 border-t border-slate-100 pt-5 text-sm font-bold text-slate-950">
        <FiClock />
        Activity history
      </h3>
      <p className="mt-2 text-xs text-slate-500">
        Latest 100 changes · {Intl.DateTimeFormat().resolvedOptions().timeZone}
      </p>
      {result?.version !== deal.version ? (
        <ListSkeleton rows={3} />
      ) : result.error ? (
        <p role="alert" className="mt-4 text-xs text-rose-600">
          {result.error}
        </p>
      ) : (
        <ol className="mt-5 space-y-5">
          {result.events.map((event) => (
            <li key={event.id} className="border-l-2 border-indigo-100 pl-4">
              <p className="text-xs font-semibold text-slate-900">
                {event.eventType === 'STAGE_CHANGED'
                  ? `${salesLabel(event.previousStage || 'NEW')} → ${salesLabel(event.newStage)}`
                  : event.eventType === 'CREATED'
                    ? 'Deal created'
                    : `Updated: ${event.changedFields}`}
              </p>
              <p className="mt-2 text-xs text-slate-500">
                {event.actorName || event.actorUsername || 'Unknown actor'}
              </p>
              <time dateTime={event.occurredAt} className="mt-1 block text-[11px] text-slate-400">
                {leadDateTime(event.occurredAt)}
              </time>
            </li>
          ))}
        </ol>
      )}
    </aside>
  )
}

export default function CrmDealsPage() {
  const companyId = useAppSelector(selectSelectedCompanyId)
  const epoch = useAppSelector(selectWorkspaceEpoch)
  const { capture, isCurrent } = useWorkspaceEpochScope(companyId, epoch)
  const { can } = useIam()
  const [params, setParams] = useSearchParams()
  const [deals, setDeals] = useState<Deal[]>([])
  const [next, setNext] = useState<string | null>(null)
  const [view, setView] = useState<'board' | 'list'>('board')
  const [stage, setStage] = useState<DealStage | ''>('')
  const [query, setQuery] = useState('')
  const [appliedQuery, setAppliedQuery] = useState('')
  const [selected, setSelected] = useState<Deal | null>(null)
  const [editing, setEditing] = useState<Deal | null>(null)
  const [assigning, setAssigning] = useState<Deal | null>(null)
  const [creating, setCreating] = useState(false)
  const [fields, setFields] = useState<DealFields>(empty)
  const [owners, setOwners] = useState<{ id: string; name: string }[]>([])
  const [contacts, setContacts] = useState<{ id: string; name: string }[]>([])
  const [accounts, setAccounts] = useState<{ id: string; name: string }[]>([])
  const [contactQuery, setContactQuery] = useState('')
  const [busy, setBusy] = useState(false)
  const [draggedId, setDraggedId] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const pendingMoves = useRef(new Set<string>())
  const [pending, setPending] = useState<Set<string>>(new Set())
  const scopeKey = companyId + ':' + epoch
  const moving = deals.some((d) => pending.has(scopeKey + ':' + d.id))
  const request = useRef(0)
  const canEdit = can(PERMISSIONS.CRM_DEAL_MODIFY)
  const load = useCallback(
    async (before?: string) => {
      if (!companyId) return
      const scope = capture(),
        current = ++request.current
      setLoading(true)
      try {
        const page = await crmSalesApi.deals(companyId, {
          stage: stage || undefined,
          q: appliedQuery || undefined,
          before,
        })
        if (!isCurrent(scope) || current !== request.current) return
        setDeals((old) => (before ? [...old, ...page.items] : page.items))
        setNext(page.nextCursor)
        setError('')
      } catch (e) {
        if (isCurrent(scope) && current === request.current)
          setError(apiErrorMessage(e, 'Unable to load deals'))
      } finally {
        if (isCurrent(scope) && current === request.current) setLoading(false)
      }
    },
    [companyId, stage, appliedQuery, capture, isCurrent],
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
    const id = params.get('dealId')
    if (!id || !companyId) return
    let active = true
    const scope = capture()
    void crmSalesApi
      .deal(companyId, id)
      .then((deal) => {
        if (active && isCurrent(scope)) setSelected(deal)
      })
      .catch((e) => {
        if (active && isCurrent(scope)) setError(apiErrorMessage(e, 'Unable to open deal'))
      })
    return () => {
      active = false
    }
  }, [companyId, params, capture, isCurrent])
  useEffect(() => {
    if (!companyId || (!creating && !editing && !assigning)) return
    let active = true
    const scope = capture()
    if (can(PERMISSIONS.USER_READ))
      void companyApi
        .users(companyId)
        .then((rows) => {
          if (active && isCurrent(scope))
            setOwners(
              rows
                .filter((u) => u.status === 'ACTIVE')
                .map((u) => ({ id: u.userId, name: u.name || u.email })),
            )
        })
        .catch(() => {})
    void crmWorkApi
      .accounts(companyId)
      .then((page) => {
        if (active && isCurrent(scope)) setAccounts(page.items)
      })
      .catch(() => {})
    const timer = setTimeout(() => {
      void contactsApi
        .list(companyId, { q: contactQuery || undefined, status: 'ACTIVE', size: 30 })
        .then((page) => {
          if (active && isCurrent(scope))
            setContacts(
              page.items.map((c) => ({
                id: c.id,
                name: c.displayName || c.primaryEmail || 'Unnamed contact',
              })),
            )
        })
        .catch(() => {})
    }, 250)
    return () => {
      active = false
      clearTimeout(timer)
    }
  }, [companyId, creating, editing, assigning, contactQuery, can, capture, isCurrent])
  const open = (deal: Deal) => {
    setSelected(deal)
    setParams({ dealId: deal.id })
  }
  const run = async (work: () => Promise<Deal>) => {
    if (busy) return
    const scope = capture()
    setBusy(true)
    try {
      const deal = await work()
      if (!isCurrent(scope)) return
      setSelected(deal)
      setDeals((old) => old.map((d) => (d.id === deal.id ? deal : d)))
      setParams({ dealId: deal.id })
      setCreating(false)
      setEditing(null)
      await load()
    } catch (e) {
      if (isCurrent(scope)) {
        await load()
        if (isCurrent(scope)) setError(apiErrorMessage(e, 'Unable to save. Refresh and try again.'))
      }
    } finally {
      if (isCurrent(scope)) setBusy(false)
    }
  }
  const moveDeal = async (deal: Deal, targetStage: DealStage) => {
    const operation = scopeKey + ':' + deal.id
    if (
      !companyId ||
      busy ||
      loading ||
      pendingMoves.current.has(operation) ||
      deal.stage === targetStage
    )
      return
    const scope = capture()
    const optimistic = { ...deal, stage: targetStage }
    pendingMoves.current.add(operation)
    setPending(new Set(pendingMoves.current))
    setError('')
    setDeals((old) => old.map((d) => (d.id === deal.id ? optimistic : d)))
    setSelected((old) => (old?.id === deal.id ? optimistic : old))
    try {
      const saved = await crmSalesApi.moveDeal(companyId, deal, targetStage)
      if (!isCurrent(scope)) return
      setDeals((old) =>
        stage && saved.stage !== stage
          ? old.filter((d) => d.id !== deal.id)
          : old.map((d) => (d.id === deal.id ? saved : d)),
      )
      setSelected((old) => (old?.id === deal.id ? saved : old))
    } catch (err) {
      if (!isCurrent(scope)) return
      setDeals((old) => old.map((d) => (d.id === deal.id ? deal : d)))
      setSelected((old) => (old?.id === deal.id ? deal : old))
      setError(
        apiErrorMessage(err, 'Deal could not be moved. Its previous stage has been restored.'),
      )
    } finally {
      pendingMoves.current.delete(operation)
      if (isCurrent(scope)) setPending(new Set(pendingMoves.current))
    }
  }
  const editDeal = (deal: Deal) => {
    setEditing(deal)
    setCreating(false)
    setFields({
      title: deal.title,
      description: deal.description,
      amount: deal.amount,
      currency: deal.currency,
      expectedCloseDate: deal.expectedCloseDate,
      ownerUserId: deal.ownerUserId,
      contactId: deal.contactId,
      accountId: deal.accountId,
    })
  }
  const assignDeal = async (ownerUserId: string | null) => {
    if (!companyId || !assigning) return
    const deal = assigning,
      scope = capture()
    await run(async () => {
      const saved = await crmSalesApi.editDeal(companyId, deal, {
        title: deal.title,
        description: deal.description,
        amount: deal.amount,
        currency: deal.currency,
        expectedCloseDate: deal.expectedCloseDate,
        ownerUserId,
        contactId: deal.contactId,
        accountId: deal.accountId,
      })
      if (isCurrent(scope)) setAssigning(null)
      return saved
    })
  }
  const card = (deal: Deal) => (
    <article
      key={deal.id}
      aria-busy={pending.has(scopeKey + ':' + deal.id)}
      className={`panel p-4 ${draggedId === deal.id ? 'opacity-50' : ''}`}
      draggable={canEdit && !busy && !pending.has(scopeKey + ':' + deal.id)}
      onDragStart={(event) => {
        event.dataTransfer.setData('text/plain', deal.id)
        event.dataTransfer.effectAllowed = 'move'
        setDraggedId(deal.id)
      }}
      onDragEnd={() => setDraggedId(null)}
    >
      <button
        className="w-full text-left text-sm font-bold text-slate-950 hover:text-indigo-600"
        onClick={() => open(deal)}
      >
        {deal.title}
      </button>
      <p className="mt-3 text-base font-bold text-slate-900">
        {dealMoney(deal.amount, deal.currency)}
      </p>
      <p className="mt-2 text-xs text-slate-500">
        {deal.accountName || deal.contactName || 'No linked customer'}
      </p>
      <div className="mt-4 flex items-center justify-between gap-2 text-[11px] text-slate-500">
        <span className="flex items-center gap-1">
          <FiUser />
          {deal.ownerName || 'Unassigned'}
        </span>
        <span>{deal.expectedCloseDate || 'No close date'}</span>
      </div>
      {pending.has(scopeKey + ':' + deal.id) && (
        <p role="status" className="mt-2 text-xs text-indigo-500">
          Saving...
        </p>
      )}
      {canEdit && (
        <select
          aria-label={`Stage for ${deal.title}`}
          className="form-input mt-4 w-full text-xs"
          disabled={busy || pending.has(scopeKey + ':' + deal.id)}
          value={deal.stage}
          onChange={(e) => void moveDeal(deal, e.target.value as DealStage)}
        >
          {dealStages.map((s) => (
            <option key={s} value={s}>
              {salesLabel(s)}
            </option>
          ))}
        </select>
      )}
      <div className="mt-3 flex flex-wrap gap-2 border-t border-slate-100 pt-3">
        {canEdit && (
          <>
            <button
              type="button"
              className="secondary-button gap-1 px-2 py-1.5 text-xs"
              aria-label={'Assign ' + deal.title}
              disabled={busy || moving}
              onClick={() => setAssigning(deal)}
            >
              <FiUser />
              Assign
            </button>
            <button
              type="button"
              className="secondary-button px-2 py-1.5 text-xs"
              aria-label={'Edit ' + deal.title}
              disabled={busy || moving}
              onClick={() => editDeal(deal)}
            >
              Edit
            </button>
          </>
        )}
        <button
          type="button"
          className="secondary-button gap-1 px-2 py-1.5 text-xs"
          aria-label={'View history for ' + deal.title}
          onClick={() => open(deal)}
        >
          <FiClock />
          View history
        </button>
      </div>
    </article>
  )
  return (
    <section className="flex h-[calc(100dvh-72px-2*var(--app-page-gutter-y,1.5rem))] min-h-[480px] flex-col gap-4 px-4 py-6 sm:px-6 lg:px-8">
      <header className="flex flex-wrap items-center justify-between gap-4">
        <div>
          <p className="eyebrow">SALES WORKSPACE</p>
          <h1 className="mt-2 text-3xl font-bold text-slate-950">Deals</h1>
          <p className="mt-2 text-sm text-slate-500">
            Move opportunities forward, from first interest to a closed deal.
          </p>
        </div>
        <div className="flex gap-2">
          <button
            className="secondary-button gap-2"
            disabled={loading || busy || moving}
            onClick={() => void load()}
          >
            <FiRefreshCw />
            Refresh
          </button>
          {canEdit && (
            <button
              className="primary-button gap-2"
              disabled={busy || moving}
              onClick={() => {
                setFields(empty)
                setEditing(null)
                setCreating(true)
              }}
            >
              <FiPlus />
              New deal
            </button>
          )}
        </div>
      </header>
      {error && !creating && !editing && !assigning && (
        <p role="alert" className="rounded-xl bg-rose-50 p-4 text-sm text-rose-700">
          {error}
        </p>
      )}
      {(creating || editing) && (
        <WorkspaceDialog
          title={editing ? 'Edit opportunity' : 'New opportunity'}
          wide
          busy={busy}
          error={error}
          onClose={() => {
            setCreating(false)
            setEditing(null)
          }}
        >
          <form
            className="space-y-4"
            onSubmit={(e) => {
              e.preventDefault()
              if (companyId)
                void run(() =>
                  editing
                    ? crmSalesApi.editDeal(companyId, editing, fields)
                    : crmSalesApi.createDeal(companyId, fields),
                )
            }}
          >
            <div className="mt-4 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
              <label className="text-xs font-semibold text-slate-600">
                Deal name
                <input
                  required
                  maxLength={240}
                  className="form-input mt-2 w-full"
                  value={fields.title}
                  onChange={(e) => setFields({ ...fields, title: e.target.value })}
                />
              </label>
              <label className="text-xs font-semibold text-slate-600">
                Value
                <input
                  required
                  inputMode="decimal"
                  pattern="[0-9]+(\.[0-9]{1,3})?"
                  className="form-input mt-2 w-full"
                  value={fields.amount}
                  onChange={(e) => setFields({ ...fields, amount: e.target.value })}
                />
              </label>
              <label className="text-xs font-semibold text-slate-600">
                Currency
                <input
                  required
                  maxLength={3}
                  minLength={3}
                  pattern="[A-Z]{3}"
                  placeholder="INR"
                  className="form-input mt-2 w-full"
                  value={fields.currency}
                  onChange={(e) => setFields({ ...fields, currency: e.target.value.toUpperCase() })}
                />
              </label>
              <label className="text-xs font-semibold text-slate-600">
                Expected close
                <input
                  type="date"
                  className="form-input mt-2 w-full"
                  value={fields.expectedCloseDate || ''}
                  onChange={(e) =>
                    setFields({ ...fields, expectedCloseDate: e.target.value || null })
                  }
                />
              </label>
              {can(PERMISSIONS.USER_READ) && (
                <label className="text-xs font-semibold text-slate-600">
                  Owner
                  <select
                    className="form-input mt-2 w-full"
                    value={fields.ownerUserId || ''}
                    onChange={(e) => setFields({ ...fields, ownerUserId: e.target.value || null })}
                  >
                    <option value="">{editing ? 'Unassigned' : 'Myself'}</option>
                    {fields.ownerUserId && !owners.some((o) => o.id === fields.ownerUserId) && (
                      <option value={fields.ownerUserId}>
                        {editing?.ownerName || 'Current owner'}
                      </option>
                    )}
                    {owners.map((o) => (
                      <option key={o.id} value={o.id}>
                        {o.name}
                      </option>
                    ))}
                  </select>
                </label>
              )}
              <label className="text-xs font-semibold text-slate-600">
                Customer company
                <select
                  className="form-input mt-2 w-full"
                  value={fields.accountId || ''}
                  onChange={(e) => setFields({ ...fields, accountId: e.target.value || null })}
                >
                  <option value="">No company</option>
                  {fields.accountId && !accounts.some((a) => a.id === fields.accountId) && (
                    <option value={fields.accountId}>
                      {editing?.accountName || 'Current company'}
                    </option>
                  )}
                  {accounts.map((a) => (
                    <option key={a.id} value={a.id}>
                      {a.name}
                    </option>
                  ))}
                </select>
              </label>
              <label className="text-xs font-semibold text-slate-600">
                Find a contact
                <input
                  className="form-input mt-2 w-full"
                  placeholder="Search name, email or phone"
                  value={contactQuery}
                  onChange={(e) => setContactQuery(e.target.value)}
                />
              </label>
              <label className="text-xs font-semibold text-slate-600">
                Linked contact
                <select
                  className="form-input mt-2 w-full"
                  value={fields.contactId || ''}
                  onChange={(e) => setFields({ ...fields, contactId: e.target.value || null })}
                >
                  <option value="">No contact</option>
                  {fields.contactId && !contacts.some((c) => c.id === fields.contactId) && (
                    <option value={fields.contactId}>
                      {editing?.contactName || 'Current contact'}
                    </option>
                  )}
                  {contacts.map((c) => (
                    <option key={c.id} value={c.id}>
                      {c.name}
                    </option>
                  ))}
                </select>
              </label>
              <label className="text-xs font-semibold text-slate-600 sm:col-span-2 lg:col-span-3">
                Description
                <textarea
                  className="form-input mt-2 w-full"
                  rows={2}
                  maxLength={4000}
                  value={fields.description || ''}
                  onChange={(e) => setFields({ ...fields, description: e.target.value })}
                />
              </label>
            </div>
            <div className="mt-4 flex gap-2">
              <button className="primary-button" disabled={busy} type="submit">
                {busy ? 'Saving…' : 'Save deal'}
              </button>
              <button
                className="secondary-button"
                type="button"
                disabled={busy}
                onClick={() => {
                  setCreating(false)
                  setEditing(null)
                }}
              >
                Cancel
              </button>
            </div>
          </form>
        </WorkspaceDialog>
      )}
      <div className="flex flex-wrap items-center gap-3">
        <BoardViewToggle value={view} onChange={setView} />
        <span className="text-xs text-slate-500">{deals.length} loaded</span>
        <FilterPopover
          className="ml-auto"
          disabled={moving}
          activeCount={Number(!!stage) + Number(!!appliedQuery)}
          onReset={() => {
            setStage('')
            setQuery('')
            setAppliedQuery('')
          }}
        >
          {' '}
          <form
            className="flex gap-2"
            onSubmit={(e) => {
              e.preventDefault()
              setAppliedQuery(query.trim())
            }}
          >
            <input
              aria-label="Search deals"
              placeholder="Search opportunities"
              className="form-input"
              maxLength={100}
              value={query}
              onChange={(e) => setQuery(e.target.value)}
            />
            <button className="secondary-button">Search</button>
          </form>
          <select
            aria-label="Filter stage"
            className="form-input"
            value={stage}
            disabled={moving}
            onChange={(e) => setStage(e.target.value as DealStage | '')}
          >
            <option value="">All stages</option>
            {dealStages.map((s) => (
              <option key={s} value={s}>
                {salesLabel(s)}
              </option>
            ))}
          </select>
        </FilterPopover>
      </div>
      <div
        className={`min-h-0 flex-1 overflow-auto grid gap-5 ${selected ? 'grid-rows-[minmax(0,1fr)_minmax(0,1fr)] xl:grid-cols-[minmax(0,1fr)_370px] xl:grid-rows-[minmax(0,1fr)]' : ''}`}
      >
        <div className="flex h-full min-h-0 min-w-0 flex-col">
          {loading ? (
            view === 'board' ? (
              <BoardSkeleton columns={6} />
            ) : (
              <TableSkeleton columns={6} />
            )
          ) : !deals.length && view === 'list' ? (
            <div className="panel p-12 text-center">
              <FiBriefcase className="mx-auto text-3xl text-indigo-400" />
              <h2 className="mt-4 font-bold text-slate-950">Your next opportunity starts here</h2>
              <p className="mt-2 text-sm text-slate-500">
                Create a deal, or adjust your filters, to begin tracking your pipeline.
              </p>
            </div>
          ) : view === 'board' ? (
            <div className="min-h-0 flex-1 overflow-x-auto pb-2">
              <div className="flex h-full min-h-0 gap-4">
                {dealStages.map((s) => (
                  <section
                    key={s}
                    className={`flex h-full min-h-0 min-w-72 flex-1 flex-col overflow-hidden rounded-xl border p-3 ${draggedId ? 'border-indigo-200 bg-indigo-50' : 'border-slate-200 bg-slate-50'}`}
                    aria-label={`${salesLabel(s)} stage`}
                    onDragOver={(event) => {
                      if (canEdit && !busy && draggedId) event.preventDefault()
                    }}
                    onDrop={(event) => {
                      event.preventDefault()
                      const id = event.dataTransfer.getData('text/plain')
                      const deal = deals.find((item) => item.id === id)
                      setDraggedId(null)
                      if (canEdit && !busy && companyId && deal && deal.stage !== s)
                        void moveDeal(deal, s)
                    }}
                  >
                    <h2 className="mb-4 flex items-center justify-between">
                      <span
                        className={`rounded-full px-3 py-1 text-xs font-bold ${dealStageColor(s)}`}
                      >
                        {salesLabel(s)}
                      </span>
                      <span className="text-xs text-slate-500">
                        {deals.filter((d) => d.stage === s).length}
                      </span>
                      {canEdit && s === 'NEW' && (
                        <button
                          type="button"
                          className="secondary-button p-1.5"
                          aria-label={'Create deal in ' + salesLabel(s)}
                          disabled={busy || moving}
                          onClick={() => {
                            setFields(empty)
                            setCreating(true)
                            setEditing(null)
                          }}
                        >
                          <FiPlus />
                        </button>
                      )}
                    </h2>
                    <div className="min-h-0 flex-1 space-y-3 overflow-y-auto overscroll-contain pr-1">
                      {deals.filter((d) => d.stage === s).map(card)}
                      {!deals.some((d) => d.stage === s) && (
                        <p className="rounded-lg border border-dashed border-slate-200 p-5 text-center text-xs text-slate-400">
                          No loaded deals
                        </p>
                      )}
                    </div>
                  </section>
                ))}
              </div>
            </div>
          ) : (
            <div className="panel overflow-x-auto">
              <table className="w-full text-left text-sm">
                <thead className="bg-slate-50 text-xs text-slate-500">
                  <tr>
                    {['Opportunity', 'Value', 'Stage', 'Owner', 'Expected close', ''].map((h) => (
                      <th key={h} className="p-4">
                        {h}
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {deals.map((d) => (
                    <tr key={d.id} className="border-t border-slate-100">
                      <td className="p-4 font-semibold text-slate-900">
                        {d.title}
                        <p className="mt-1 text-xs font-normal text-slate-500">
                          {d.accountName || d.contactName}
                        </p>
                      </td>
                      <td className="whitespace-nowrap p-4">{dealMoney(d.amount, d.currency)}</td>
                      <td className="p-4">
                        <span
                          className={`rounded-full px-2 py-1 text-xs ${dealStageColor(d.stage)}`}
                        >
                          {salesLabel(d.stage)}
                        </span>
                      </td>
                      <td className="p-4">{d.ownerName || 'Unassigned'}</td>
                      <td className="p-4">{d.expectedCloseDate || '—'}</td>
                      <td className="p-4">
                        <button
                          aria-label={`Open ${d.title}`}
                          className="secondary-button"
                          onClick={() => open(d)}
                        >
                          <FiArrowRight />
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
          {next && (
            <button
              className="secondary-button mt-4"
              disabled={loading || moving}
              onClick={() => void load(next)}
            >
              Load more opportunities
            </button>
          )}
        </div>
        {selected && companyId && (
          <div className="min-h-0 overflow-y-auto">
            <DealDetails
              key={selected.id}
              companyId={companyId}
              deal={selected}
              canEdit={canEdit && !moving}
              onClose={() => {
                setSelected(null)
                setParams({})
              }}
              onEdit={() => {
                setFields({
                  title: selected.title,
                  description: selected.description,
                  amount: selected.amount,
                  currency: selected.currency,
                  expectedCloseDate: selected.expectedCloseDate,
                  ownerUserId: selected.ownerUserId,
                  contactId: selected.contactId,
                  accountId: selected.accountId,
                })
                setEditing(selected)
                setCreating(false)
              }}
            />
          </div>
        )}
      </div>
      {assigning && (
        <AssigneeDialog
          title="Assign deal"
          currentId={assigning.ownerUserId}
          currentName={assigning.ownerName || 'Current owner'}
          people={owners}
          allowUnassigned
          busy={busy}
          error={error}
          onSave={(id) => void assignDeal(id)}
          onClose={() => setAssigning(null)}
        />
      )}
    </section>
  )
}
