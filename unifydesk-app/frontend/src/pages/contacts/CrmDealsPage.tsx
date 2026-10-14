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
  FiMessageCircle,
  FiSliders,
} from 'react-icons/fi'
import {
  crmSalesApi,
  dealMoney,
  dealStageColor,
  salesLabel,
  type Deal,
  type DealEvent,
  type DealFields,
  type DealStage,
  type Pipeline,
  type PipelineStage,
} from '../../features/contacts/crmSalesApi'
import {
  crmCustomFieldsApi,
  type CrmFieldDef,
} from '../../features/contacts/crmCustomFieldsApi'
import { CustomFieldsSection } from '../../components/crm/CustomFieldRenderer'
import SendWhatsAppModal from '../../components/crm/SendWhatsAppModal'
import PipelineManagerModal from '../../components/crm/PipelineManagerModal'
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
  pipelineId: null,
  stage: 'NEW',
  customFields: {},
}

function DealDetails({
  companyId,
  deal,
  onClose,
  onEdit,
  canEdit,
  customDefs,
}: {
  companyId: string
  deal: Deal
  onClose: () => void
  onEdit: () => void
  canEdit: boolean
  customDefs?: CrmFieldDef[]
}) {
  const { can } = useIam()
  const canSendWhatsApp = can(PERMISSIONS.WHATSAPP_SEND)
  const [showWhatsApp, setShowWhatsApp] = useState(false)
  const [historyReload, setHistoryReload] = useState(0)

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
  }, [companyId, deal.id, deal.version, historyReload])
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
      {customDefs && customDefs.length > 0 && (
        <div className="mt-5 border-t border-slate-100 pt-4">
          <p className="eyebrow mb-2">CUSTOM ATTRIBUTES</p>
          <dl className="grid grid-cols-2 gap-2 text-xs">
            {customDefs.map((def) => {
              const val = deal.customFields ? deal.customFields[def.fieldKey] : null
              const displayVal =
                val != null && val !== ''
                  ? Array.isArray(val)
                    ? val.join(', ')
                    : typeof val === 'boolean'
                    ? val ? 'Yes' : 'No'
                    : String(val)
                  : null
              return (
                <div
                  key={def.id}
                  className="rounded-lg bg-slate-50 p-2.5 border border-slate-100"
                >
                  <dt className="text-slate-500 font-medium">{def.label}</dt>
                  <dd className="mt-1 font-semibold text-slate-900 break-words">
                    {displayVal ? (
                      displayVal
                    ) : (
                      <span className="font-normal text-slate-400 italic">Not set</span>
                    )}
                  </dd>
                </div>
              )
            })}
          </dl>
        </div>
      )}
      <div className="mt-5 flex flex-wrap gap-2">
        {canEdit && (
          <button className="secondary-button" onClick={onEdit}>
            Edit opportunity
          </button>
        )}
        {canSendWhatsApp && (
          <button
            type="button"
            className="primary-button bg-emerald-600 hover:bg-emerald-700 active:bg-emerald-800 text-white flex items-center gap-1.5 text-xs py-2 px-3 shadow-xs"
            onClick={() => setShowWhatsApp(true)}
          >
            <FiMessageCircle className="h-4 w-4" />
            Send WhatsApp
          </button>
        )}
      </div>
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

      {showWhatsApp && (
        <SendWhatsAppModal
          companyId={companyId}
          contactId={deal.contactId}
          dealId={deal.id}
          recipientName={deal.contactName}
          onClose={() => setShowWhatsApp(false)}
          onSuccess={() => {
            setHistoryReload((v) => v + 1)
          }}
        />
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
  const [stage, setStage] = useState<string>('')
  const [query, setQuery] = useState('')
  const [appliedQuery, setAppliedQuery] = useState('')
  const [selected, setSelected] = useState<Deal | null>(null)
  const [editing, setEditing] = useState<Deal | null>(null)
  const [assigning, setAssigning] = useState<Deal | null>(null)
  const [creating, setCreating] = useState(false)
  const [fields, setFields] = useState<DealFields>(empty)
  const [customDefs, setCustomDefs] = useState<CrmFieldDef[]>([])
  const [owners, setOwners] = useState<{ id: string; name: string }[]>([])
  const [contacts, setContacts] = useState<{ id: string; name: string }[]>([])
  const [accounts, setAccounts] = useState<{ id: string; name: string }[]>([])
  const [contactQuery, setContactQuery] = useState('')
  const [busy, setBusy] = useState(false)

  // Pipelines state
  const [pipelines, setPipelines] = useState<Pipeline[]>([])
  const [activePipelineId, setActivePipelineId] = useState<string>('')
  const [showPipelineManager, setShowPipelineManager] = useState(false)

  const loadPipelines = useCallback(async () => {
    if (!companyId) return
    try {
      const list = await crmSalesApi.pipelines(companyId)
      setPipelines(list)
      if (list.length > 0) {
        setActivePipelineId((prev) => {
          if (prev && list.some((p) => p.id === prev)) return prev
          const def = list.find((p) => p.isDefault)
          return def ? def.id : list[0].id
        })
      }
    } catch (err) {
      console.error('Failed to load pipelines', err)
    }
  }, [companyId])

  useEffect(() => {
    void loadPipelines()
  }, [loadPipelines])

  const activePipeline = pipelines.find((p) => p.id === activePipelineId) || pipelines[0] || null
  const currentStages: PipelineStage[] = activePipeline?.stages || [
    { id: '1', key: 'NEW', name: 'New', category: 'OPEN', probability: 10, displayOrder: 1, color: 'sky' },
    { id: '2', key: 'QUALIFIED', name: 'Qualified', category: 'OPEN', probability: 25, displayOrder: 2, color: 'indigo' },
    { id: '3', key: 'PROPOSAL', name: 'Proposal', category: 'OPEN', probability: 50, displayOrder: 3, color: 'violet' },
    { id: '4', key: 'NEGOTIATION', name: 'Negotiation', category: 'OPEN', probability: 75, displayOrder: 4, color: 'amber' },
    { id: '5', key: 'WON', name: 'Won', category: 'WON', probability: 100, displayOrder: 5, color: 'emerald' },
    { id: '6', key: 'LOST', name: 'Lost', category: 'LOST', probability: 0, displayOrder: 6, color: 'rose' },
  ]

  useEffect(() => {
    if (!companyId) return
    crmCustomFieldsApi.list(companyId, 'DEAL').then(setCustomDefs).catch(() => {})
  }, [companyId])
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
          pipelineId: activePipelineId || undefined,
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
    [companyId, activePipelineId, stage, appliedQuery, capture, isCurrent],
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
      customFields: deal.customFields || {},
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
      {deal.customFields && Object.keys(deal.customFields).length > 0 && (
        <div className="mt-2 flex flex-wrap gap-1">
          {Object.entries(deal.customFields)
            .filter(([, v]) => v != null && v !== '')
            .slice(0, 3)
            .map(([k, v]) => (
              <span
                key={k}
                className="inline-flex items-center rounded bg-slate-100 px-1.5 py-0.5 text-[10px] font-medium text-slate-600"
              >
                {k}: {Array.isArray(v) ? v.join(', ') : String(v)}
              </span>
            ))}
        </div>
      )}
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
          onChange={(e) => void moveDeal(deal, e.target.value)}
        >
          {currentStages.map((s) => (
            <option key={s.key} value={s.key}>
              {s.name} ({s.probability}%)
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
              <label className="text-xs font-semibold text-slate-600">
                Sales Pipeline
                <select
                  className="form-input mt-2 w-full"
                  value={fields.pipelineId || activePipelineId}
                  onChange={(e) => {
                    const newPid = e.target.value
                    const p = pipelines.find((item) => item.id === newPid)
                    setFields({
                      ...fields,
                      pipelineId: newPid,
                      stage: p?.stages[0]?.key || 'NEW',
                    })
                  }}
                >
                  {pipelines.map((p) => (
                    <option key={p.id} value={p.id}>
                      {p.name} {p.isDefault ? '(Default)' : ''}
                    </option>
                  ))}
                </select>
              </label>
              <label className="text-xs font-semibold text-slate-600">
                Stage
                <select
                  className="form-input mt-2 w-full"
                  value={fields.stage || currentStages[0]?.key || 'NEW'}
                  onChange={(e) => setFields({ ...fields, stage: e.target.value })}
                >
                  {(pipelines.find((p) => p.id === (fields.pipelineId || activePipelineId))?.stages || currentStages).map((s) => (
                    <option key={s.key} value={s.key}>
                      {s.name} ({s.probability}%)
                    </option>
                  ))}
                </select>
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
            {customDefs.length > 0 && (
              <CustomFieldsSection
                defs={customDefs}
                values={fields.customFields || {}}
                onChange={(key, val) =>
                  setFields((prev) => ({
                    ...prev,
                    customFields: {
                      ...(prev.customFields || {}),
                      [key]: val,
                    },
                  }))
                }
                disabled={busy}
              />
            )}
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
        {pipelines.length > 0 && (
          <div className="flex items-center gap-1.5">
            <select
              aria-label="Active pipeline"
              className="form-input text-xs font-semibold py-1.5 px-3 rounded-xl border-indigo-200 dark:border-indigo-900 bg-indigo-50/50 dark:bg-indigo-950/40 text-indigo-900 dark:text-indigo-200"
              value={activePipelineId}
              onChange={(e) => {
                setActivePipelineId(e.target.value)
                setStage('')
              }}
            >
              {pipelines.map((p) => (
                <option key={p.id} value={p.id}>
                  {p.name} {p.isDefault ? '★' : ''} ({p.stages.length} stages)
                </option>
              ))}
            </select>
            {canEdit && (
              <button
                type="button"
                onClick={() => setShowPipelineManager(true)}
                className="secondary-button text-xs py-1.5 px-2.5 flex items-center gap-1 font-semibold"
                title="Manage Pipelines & Stages"
              >
                <FiSliders className="h-3.5 w-3.5" />
                <span className="hidden sm:inline">Pipelines</span>
              </button>
            )}
          </div>
        )}
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
            onChange={(e) => setStage(e.target.value)}
          >
            <option value="">All stages</option>
            {currentStages.map((s) => (
              <option key={s.key} value={s.key}>
                {s.name}
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
              <BoardSkeleton columns={currentStages.length || 6} />
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
                {currentStages.map((stg) => {
                  const stageDeals = deals.filter(
                    (d) =>
                      d.stage.toUpperCase() === stg.key.toUpperCase() ||
                      d.stage.toUpperCase() === stg.name.toUpperCase(),
                  )
                  const stageTotal = stageDeals.reduce(
                    (sum, d) => sum + (parseFloat(d.amount) || 0),
                    0,
                  )
                  return (
                    <section
                      key={stg.id || stg.key}
                      className={`flex h-full min-h-0 min-w-72 flex-1 flex-col overflow-hidden rounded-2xl border p-3.5 transition-all ${
                        draggedId
                          ? 'border-indigo-200 dark:border-indigo-800 bg-indigo-50/30 dark:bg-indigo-950/20'
                          : 'border-slate-200 dark:border-slate-800 bg-slate-50/60 dark:bg-slate-900/40'
                      }`}
                      aria-label={`${stg.name} stage`}
                      onDragOver={(event) => {
                        if (canEdit && !busy && draggedId) event.preventDefault()
                      }}
                      onDrop={(event) => {
                        event.preventDefault()
                        const id = event.dataTransfer.getData('text/plain')
                        const deal = deals.find((item) => item.id === id)
                        setDraggedId(null)
                        if (canEdit && !busy && companyId && deal && deal.stage !== stg.key)
                          void moveDeal(deal, stg.key)
                      }}
                    >
                      <div className="mb-3">
                        <div className="flex items-center justify-between">
                          <span
                            className={`rounded-full px-2.5 py-0.5 text-xs font-bold ${dealStageColor(
                              stg.name,
                              stg.color,
                            )}`}
                          >
                            {stg.name}
                          </span>
                          <span className="text-xs font-bold text-slate-500">
                            {stageDeals.length}
                          </span>
                        </div>
                        <div className="mt-1.5 flex items-center justify-between text-[11px] text-slate-400">
                          <span>Probability: {stg.probability}%</span>
                          <span className="font-semibold text-slate-700 dark:text-slate-300">
                            {stageDeals.length > 0
                              ? `${stageDeals[0].currency} ${stageTotal.toLocaleString()}`
                              : '—'}
                          </span>
                        </div>
                      </div>
                      <div className="min-h-0 flex-1 space-y-3 overflow-y-auto overscroll-contain pr-1">
                        {stageDeals.map(card)}
                        {stageDeals.length === 0 && (
                          <p className="rounded-xl border border-dashed border-slate-200 dark:border-slate-800 p-5 text-center text-xs text-slate-400">
                            No loaded deals
                          </p>
                        )}
                      </div>
                    </section>
                  )
                })}
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
                        {d.customFields && Object.keys(d.customFields).length > 0 && (
                          <div className="mt-1 flex flex-wrap gap-1">
                            {Object.entries(d.customFields)
                              .filter(([, v]) => v != null && v !== '')
                              .slice(0, 2)
                              .map(([k, v]) => (
                                <span
                                  key={k}
                                  className="inline-flex items-center rounded bg-slate-100 px-1.5 py-0.5 text-[10px] font-normal text-slate-500"
                                >
                                  {k}: {Array.isArray(v) ? v.join(', ') : String(v)}
                                </span>
                              ))}
                          </div>
                        )}
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
              customDefs={customDefs}
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
                  pipelineId: selected.pipelineId || activePipelineId,
                  stage: selected.stage,
                  customFields: selected.customFields || {},
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
      {showPipelineManager && companyId && (
        <PipelineManagerModal
          companyId={companyId}
          pipelines={pipelines}
          activePipelineId={activePipelineId}
          onClose={() => setShowPipelineManager(false)}
          onPipelinesChanged={() => {
            void loadPipelines()
            void load()
          }}
          onSelectPipeline={(id) => {
            setActivePipelineId(id)
            setStage('')
          }}
        />
      )}
    </section>
  )
}
