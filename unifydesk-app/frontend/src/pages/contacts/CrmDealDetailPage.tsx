import { useSnackbarState } from '../../components/feedback/useSnackbarState'
import { useCallback, useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import {
  FiArrowLeft,
  FiBriefcase,
  FiCheck,
  FiClock,
  FiDownload,
  FiEdit2,
  FiFileText,
  FiMessageSquare,
  FiPaperclip,
  FiRefreshCw,
  FiSend,
  FiTrash2,
  FiUploadCloud,
  FiX,
} from 'react-icons/fi'
import { companyApi } from '../../features/company/companyApi'
import {
  crmSalesApi,
  type Deal,
  type DealEvent,
  type Pipeline,
  type PipelineStage,
} from '../../features/contacts/crmSalesApi'
import { crmWorkApi, type CrmAccount } from '../../features/contacts/crmWorkApi'
import { crmLeadsApi, type Lead } from '../../features/contacts/crmLeadsApi'
import { PERMISSIONS } from '../../features/iam/permissions'
import { useIam } from '../../features/iam/useIam'
import { useWorkspaceEpochScope } from '../../features/workspace/useWorkspaceEpochScope'
import { apiErrorMessage } from '../../lib/api/apiClient'
import { useAppSelector } from '../../store/hooks'
import { selectSelectedCompanyId, selectWorkspaceEpoch } from '../../store/workspace/workspaceSlice'

export type DealAttachment = {
  id: string
  mediaId: string
  filename: string
  contentType: string | null
  sizeBytes: number
  uploaderName: string | null
  downloadUrl?: string
  createdAt: string
}

export type DealComment = {
  id: string
  entityType: string
  entityId: string
  body: string
  authorUserId: string | null
  authorName: string | null
  authorEmail: string | null
  createdAt: string
  updatedAt: string
}

export default function CrmDealDetailPage() {
  const { dealId } = useParams<{ dealId: string }>()
  const navigate = useNavigate()
  const companyId = useAppSelector(selectSelectedCompanyId)
  const epoch = useAppSelector(selectWorkspaceEpoch)
  const { capture, isCurrent } = useWorkspaceEpochScope(companyId, epoch)
  const { can } = useIam()

  const [deal, setDeal] = useState<Deal | null>(null)
  const [pipelines, setPipelines] = useState<Pipeline[]>([])
  const [members, setMembers] = useState<Array<{ userId: string; name: string }>>([])
  const [accounts, setAccounts] = useState<CrmAccount[]>([])
  const [leads, setLeads] = useState<Lead[]>([])
  const [comments, setComments] = useState<DealComment[]>([])
  const [events, setEvents] = useState<DealEvent[]>([])
  const [attachments, setAttachments] = useState<DealAttachment[]>([])

  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useSnackbarState('', 'error')
  const [activeTab, setActiveTab] = useState<'comments' | 'history' | 'attachments'>('comments')
  const [newComment, setNewComment] = useState('')
  const [postingComment, setPostingComment] = useState(false)
  const [uploadingFile, setUploadingFile] = useState(false)
  const [editingTitle, setEditingTitle] = useState(false)
  const [editTitleVal, setEditTitleVal] = useState('')
  const [editingDesc, setEditingDesc] = useState(false)
  const [editDescVal, setEditDescVal] = useState('')
  const [showDeleteModal, setShowDeleteModal] = useState(false)

  const canEdit = can(PERMISSIONS.CRM_DEAL_MODIFY)

  const loadData = useCallback(async () => {
    if (!companyId || !dealId) return
    const scope = capture()
    setLoading(true)
    setError('')
    try {
      const [fetchedDeal, pipes, history, comms] = await Promise.all([
        crmSalesApi.deal(companyId, dealId),
        crmSalesApi.pipelines(companyId).catch(() => []),
        crmSalesApi.history(companyId, dealId).catch(() => []),
        crmSalesApi.dealComments(companyId, dealId).catch(() => []),
      ])

      if (isCurrent(scope)) {
        setDeal(fetchedDeal)
        setPipelines(pipes)
        setEvents(history)
        setComments(comms)
        setEditTitleVal(fetchedDeal.title)
        setEditDescVal(fetchedDeal.description || '')
      }

      // Fetch attachments
      try {
        const attRes = await fetch(`/api/v1/companies/${companyId}/crm/deals/${dealId}/attachments`)
        if (attRes.ok) {
          const json = await attRes.json()
          if (json?.data && isCurrent(scope)) {
            setAttachments(json.data)
          }
        }
      } catch {
        /* Attachments gracefully optional */
      }

      // Supporting directories
      if (can(PERMISSIONS.USER_READ)) {
        companyApi.users(companyId).then((users) => {
          if (isCurrent(scope)) {
            setMembers(
              users
                .filter((u) => u.status === 'ACTIVE')
                .map((u) => ({ userId: u.userId, name: u.name || u.email })),
            )
          }
        }).catch(() => {})
      }

      if (can(PERMISSIONS.CRM_ACCOUNT_READ)) {
        crmWorkApi.accounts(companyId).then((accs) => {
          if (isCurrent(scope)) setAccounts(accs.items)
        }).catch(() => {})
      }

      if (can(PERMISSIONS.CRM_LEAD_READ)) {
        crmLeadsApi.list(companyId).then((leadPage) => {
          if (isCurrent(scope)) setLeads(leadPage.items)
        }).catch(() => {})
      }
    } catch (err) {
      if (isCurrent(scope)) setError(apiErrorMessage(err, 'Failed to load deal details'))
    } finally {
      if (isCurrent(scope)) setLoading(false)
    }
  }, [companyId, dealId, capture, isCurrent, can])

  useEffect(() => {
    void loadData()
  }, [loadData, epoch])

  const handleUpdate = async (fieldsPatch: Partial<Deal>) => {
    if (!companyId || !deal || busy) return
    setBusy(true)
    try {
      const mergedFields = {
        title: fieldsPatch.title !== undefined ? fieldsPatch.title : deal.title,
        description: fieldsPatch.description !== undefined ? fieldsPatch.description : deal.description,
        amount: fieldsPatch.amount !== undefined ? fieldsPatch.amount : deal.amount,
        currency: fieldsPatch.currency !== undefined ? fieldsPatch.currency : deal.currency,
        expectedCloseDate: fieldsPatch.expectedCloseDate !== undefined ? fieldsPatch.expectedCloseDate : deal.expectedCloseDate,
        ownerUserId: fieldsPatch.ownerUserId !== undefined ? fieldsPatch.ownerUserId : deal.ownerUserId,
        contactId: fieldsPatch.contactId !== undefined ? fieldsPatch.contactId : deal.contactId,
        accountId: fieldsPatch.accountId !== undefined ? fieldsPatch.accountId : deal.accountId,
        pipelineId: fieldsPatch.pipelineId !== undefined ? fieldsPatch.pipelineId : deal.pipelineId,
        stage: fieldsPatch.stage !== undefined ? fieldsPatch.stage : deal.stage,
        attributionChannel: fieldsPatch.attributionChannel !== undefined ? fieldsPatch.attributionChannel : deal.attributionChannel,
        broadcastMessageId: fieldsPatch.broadcastMessageId !== undefined ? fieldsPatch.broadcastMessageId : deal.broadcastMessageId,
        adCampaignId: fieldsPatch.adCampaignId !== undefined ? fieldsPatch.adCampaignId : deal.adCampaignId,
        leadFormId: fieldsPatch.leadFormId !== undefined ? fieldsPatch.leadFormId : deal.leadFormId,
        utmSource: fieldsPatch.utmSource !== undefined ? fieldsPatch.utmSource : deal.utmSource,
        utmMedium: fieldsPatch.utmMedium !== undefined ? fieldsPatch.utmMedium : deal.utmMedium,
        utmCampaign: fieldsPatch.utmCampaign !== undefined ? fieldsPatch.utmCampaign : deal.utmCampaign,
        customFields: fieldsPatch.customFields !== undefined ? fieldsPatch.customFields : deal.customFields,
      }

      const updated = await crmSalesApi.editDeal(companyId, deal, mergedFields)
      setDeal(updated)
      setEditTitleVal(updated.title)
      setEditDescVal(updated.description || '')
      setEditingTitle(false)
      setEditingDesc(false)
      const hist = await crmSalesApi.history(companyId, deal.id).catch(() => [])
      setEvents(hist)
    } catch (err) {
      setError(apiErrorMessage(err, 'Failed to update deal'))
    } finally {
      setBusy(false)
    }
  }

  const handleMoveStage = async (newStage: string) => {
    if (!companyId || !deal || busy || deal.stage === newStage) return
    setBusy(true)
    try {
      const updated = await crmSalesApi.moveDeal(companyId, deal, newStage)
      setDeal(updated)
      const hist = await crmSalesApi.history(companyId, deal.id).catch(() => [])
      setEvents(hist)
    } catch (err) {
      setError(apiErrorMessage(err, 'Failed to move deal stage'))
    } finally {
      setBusy(false)
    }
  }

  const handleDelete = async () => {
    if (!companyId || !deal || busy) return
    setBusy(true)
    try {
      await crmSalesApi.deleteDeal(companyId, deal.id)
      navigate('/crm/deals')
    } catch (err) {
      setError(apiErrorMessage(err, 'Failed to delete deal'))
      setBusy(false)
    }
  }

  const handleAddComment = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!companyId || !deal || !newComment.trim() || postingComment) return
    setPostingComment(true)
    try {
      const added = await crmSalesApi.addDealComment(companyId, deal.id, newComment.trim())
      setComments((prev) => [...prev, added])
      setNewComment('')
    } catch (err) {
      setError(apiErrorMessage(err, 'Failed to post comment'))
    } finally {
      setPostingComment(false)
    }
  }

  const handleDeleteComment = async (commentId: string) => {
    if (!companyId || !deal) return
    try {
      await crmSalesApi.deleteDealComment(companyId, deal.id, commentId)
      setComments((prev) => prev.filter((c) => c.id !== commentId))
    } catch (err) {
      setError(apiErrorMessage(err, 'Failed to delete comment'))
    }
  }

  const handleFileUpload = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (!file || !companyId || !deal || uploadingFile) return
    setUploadingFile(true)
    try {
      const formData = new FormData()
      formData.append('file', file)
      const uploadRes = await fetch(`/api/v1/companies/${companyId}/media`, {
        method: 'POST',
        body: formData,
      })
      if (!uploadRes.ok) throw new Error('Upload failed')
      const uploadJson = await uploadRes.json()
      const mediaId = uploadJson.data?.id || uploadJson.data?.mediaId

      const attachRes = await fetch(`/api/v1/companies/${companyId}/crm/deals/${deal.id}/attachments`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          mediaId,
          filename: file.name,
          contentType: file.type || 'application/octet-stream',
          sizeBytes: file.size,
        }),
      })
      if (!attachRes.ok) throw new Error('Attachment link failed')
      const attachJson = await attachRes.json()
      if (attachJson.data) {
        setAttachments((prev) => [attachJson.data, ...prev])
      }
    } catch (err) {
      setError(apiErrorMessage(err, 'Failed to upload document attachment'))
    } finally {
      setUploadingFile(false)
      e.target.value = ''
    }
  }

  const handleDeleteAttachment = async (attachmentId: string) => {
    if (!companyId || !deal) return
    try {
      const res = await fetch(`/api/v1/companies/${companyId}/crm/deals/${deal.id}/attachments/${attachmentId}`, {
        method: 'DELETE',
      })
      if (res.ok) {
        setAttachments((prev) => prev.filter((a) => a.id !== attachmentId))
      }
    } catch (err) {
      setError(apiErrorMessage(err, 'Failed to delete attachment'))
    }
  }

  if (loading) {
    return (
      <div className="flex h-96 items-center justify-center">
        <FiRefreshCw className="h-8 w-8 animate-spin text-indigo-500" />
      </div>
    )
  }

  if (error && !deal) {
    return (
      <div className="p-8 text-center">
        <p className="text-sm font-semibold text-rose-500">{error}</p>
        <button
          onClick={() => navigate('/crm/deals')}
          className="secondary-button mt-4 gap-2"
        >
          <FiArrowLeft /> Back to Deals
        </button>
      </div>
    )
  }

  if (!deal) return null

  // Active pipeline stages
  const activePipeline = pipelines.find((p) => p.id === deal.pipelineId) || pipelines[0]
  const stages: PipelineStage[] = activePipeline?.stages || [
    { id: '1', key: 'NEW', name: 'New Lead', category: 'OPEN', probability: 10, displayOrder: 1, color: '#6366f1' },
    { id: '2', key: 'QUALIFIED', name: 'Qualified', category: 'OPEN', probability: 30, displayOrder: 2, color: '#3b82f6' },
    { id: '3', key: 'PROPOSAL', name: 'Proposal Sent', category: 'OPEN', probability: 60, displayOrder: 3, color: '#8b5cf6' },
    { id: '4', key: 'NEGOTIATION', name: 'Negotiation', category: 'OPEN', probability: 80, displayOrder: 4, color: '#f59e0b' },
    { id: '5', key: 'WON', name: 'Closed Won', category: 'WON', probability: 100, displayOrder: 5, color: '#10b981' },
    { id: '6', key: 'LOST', name: 'Closed Lost', category: 'LOST', probability: 0, displayOrder: 6, color: '#ef4444' },
  ]

  const currentStageIndex = stages.findIndex((s) => s.key.toUpperCase() === deal.stage.toUpperCase())
  const currentStage = stages[currentStageIndex] || stages[0]

  const linkedContact = leads.find((l) => l.contactId === deal.contactId)
  const linkedAccount = accounts.find((a) => a.id === deal.accountId)

  return (
    <div className="mx-auto flex h-[calc(100dvh-72px-2*var(--app-page-gutter-y,1.5rem))] min-h-[500px] max-w-7xl flex-col gap-4 px-4 py-4 sm:px-6 lg:px-8">
      {/* Top Header / Breadcrumb Bar */}
      <header className="flex flex-wrap items-center justify-between gap-4 border-b border-slate-200 pb-3 dark:border-slate-800">
        <div className="flex items-center gap-3">
          <button
            type="button"
            onClick={() => navigate('/crm/deals')}
            className="secondary-button gap-1.5 px-3 py-1.5 text-xs text-slate-600 dark:text-slate-300"
          >
            <FiArrowLeft className="h-3.5 w-3.5" />
            <span>Opportunities</span>
          </button>
          <span className="text-slate-300 dark:text-slate-700">/</span>
          <span className="inline-flex items-center gap-1.5 rounded-full border border-purple-200 bg-purple-50 px-2.5 py-0.5 text-xs font-semibold text-purple-700 shadow-sm dark:border-purple-800 dark:bg-purple-950/60 dark:text-purple-300">
            <FiBriefcase className="h-3.5 w-3.5" />
            <span>{activePipeline?.name || 'Sales Pipeline'}</span>
          </span>
          <span className="text-xs text-slate-400">DEAL-{deal.id.slice(-6)}</span>
        </div>

        <div className="flex items-center gap-2">
          {canEdit && (
            <button
              type="button"
              onClick={() => setShowDeleteModal(true)}
              className="secondary-button gap-1 px-3 py-1.5 text-xs text-rose-600 hover:border-rose-300 hover:bg-rose-50 dark:text-rose-400"
            >
              <FiTrash2 />
              <span>Delete</span>
            </button>
          )}
        </div>
      </header>



      {/* Interactive Jira / HubSpot Pipeline Chevron Stepper */}
      <div className="rounded-xl border border-slate-200 bg-white p-3 shadow-sm dark:border-slate-800 dark:bg-slate-900">
        <div className="flex flex-wrap items-center gap-1.5 sm:gap-2">
          {stages.map((stg, idx) => {
            const isCurrent = stg.key.toUpperCase() === deal.stage.toUpperCase()
            const isPassed = currentStageIndex > idx && currentStage.category !== 'LOST'
            const isWon = stg.category === 'WON' && isCurrent
            const isLost = stg.category === 'LOST' && isCurrent

            return (
              <button
                key={stg.id || stg.key}
                type="button"
                disabled={!canEdit || busy}
                onClick={() => handleMoveStage(stg.key)}
                className={`group relative flex flex-1 min-w-[120px] items-center justify-between rounded-lg px-3 py-2 text-xs font-semibold transition-all ${
                  isWon
                    ? 'bg-emerald-600 text-white shadow-sm'
                    : isLost
                    ? 'bg-rose-600 text-white shadow-sm'
                    : isCurrent
                    ? 'bg-indigo-600 text-white shadow-md ring-2 ring-indigo-400/50'
                    : isPassed
                    ? 'bg-indigo-50 text-indigo-700 hover:bg-indigo-100 dark:bg-indigo-950/40 dark:text-indigo-300'
                    : 'bg-slate-50 text-slate-600 hover:bg-slate-100 dark:bg-slate-800 dark:text-slate-400'
                }`}
              >
                <div className="flex items-center gap-1.5 truncate">
                  {(isPassed || isWon) && <FiCheck className="h-3.5 w-3.5 shrink-0" />}
                  <span className="truncate">{stg.name}</span>
                </div>
                <span
                  className={`text-[10px] font-normal ${
                    isCurrent ? 'text-white/80' : 'text-slate-400'
                  }`}
                >
                  {stg.probability}%
                </span>
              </button>
            )
          })}
        </div>
      </div>

      {/* Jira-style 2-Column Content Grid */}
      <div className="grid min-h-0 flex-1 grid-cols-1 gap-6 overflow-y-auto lg:grid-cols-[minmax(0,1fr)_340px]">
        {/* Main Left Column (65%) */}
        <div className="flex flex-col gap-6 pr-1">
          {/* Title Area & Primary Value */}
          <div className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm dark:border-slate-800 dark:bg-slate-900">
            <div className="flex flex-wrap items-start justify-between gap-4">
              <div className="flex-1 min-w-[240px]">
                {editingTitle ? (
                  <div className="flex items-center gap-2">
                    <input
                      type="text"
                      value={editTitleVal}
                      onChange={(e) => setEditTitleVal(e.target.value)}
                      className="form-input flex-1 text-xl font-bold"
                      autoFocus
                    />
                    <button
                      type="button"
                      disabled={busy || !editTitleVal.trim()}
                      onClick={() => handleUpdate({ title: editTitleVal.trim() })}
                      className="primary-button px-3 py-2 text-xs"
                    >
                      <FiCheck /> Save
                    </button>
                    <button
                      type="button"
                      onClick={() => {
                        setEditTitleVal(deal.title)
                        setEditingTitle(false)
                      }}
                      className="secondary-button px-3 py-2 text-xs"
                    >
                      <FiX />
                    </button>
                  </div>
                ) : (
                  <div className="group flex items-center gap-2">
                    <h1 className="text-2xl font-bold tracking-tight text-slate-900 dark:text-slate-100">
                      {deal.title}
                    </h1>
                    {canEdit && (
                      <button
                        type="button"
                        onClick={() => setEditingTitle(true)}
                        className="opacity-0 transition-opacity group-hover:opacity-100 text-slate-400 hover:text-slate-600 dark:hover:text-slate-200"
                        title="Edit deal name"
                      >
                        <FiEdit2 className="h-4 w-4" />
                      </button>
                    )}
                  </div>
                )}
                <p className="mt-1 text-xs text-slate-500">
                  Expected Close: {deal.expectedCloseDate || 'Not specified'}
                </p>
              </div>

              {/* Deal Value Pill */}
              <div className="rounded-xl bg-gradient-to-br from-indigo-50 to-blue-50 p-4 text-right dark:from-indigo-950/40 dark:to-blue-950/40 border border-indigo-100 dark:border-indigo-900/40">
                <p className="text-[10px] font-bold uppercase tracking-wider text-indigo-600 dark:text-indigo-400">
                  Deal Value
                </p>
                <p className="text-2xl font-black text-slate-950 dark:text-slate-50">
                  {new Intl.NumberFormat(undefined, {
                    style: 'currency',
                    currency: deal.currency || 'INR',
                    maximumFractionDigits: 0,
                  }).format(parseFloat(deal.amount) || 0)}
                </p>
                <p className="text-[11px] text-slate-500">
                  Weighted: {new Intl.NumberFormat(undefined, {
                    style: 'currency',
                    currency: deal.currency || 'INR',
                    maximumFractionDigits: 0,
                  }).format(((parseFloat(deal.amount) || 0) * (currentStage.probability || 0)) / 100)}
                </p>
              </div>
            </div>
          </div>

          {/* Description Section */}
          <div className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm dark:border-slate-800 dark:bg-slate-900">
            <div className="mb-3 flex items-center justify-between">
              <h2 className="text-xs font-bold uppercase tracking-wider text-slate-500">
                Opportunity Summary & Requirements
              </h2>
              {canEdit && !editingDesc && (
                <button
                  type="button"
                  onClick={() => setEditingDesc(true)}
                  className="secondary-button gap-1 px-2 py-1 text-xs"
                >
                  <FiEdit2 className="h-3 w-3" /> Edit
                </button>
              )}
            </div>

            {editingDesc ? (
              <div>
                <textarea
                  rows={4}
                  value={editDescVal}
                  onChange={(e) => setEditDescVal(e.target.value)}
                  placeholder="Outline client requirements, decision makers, and scope..."
                  className="form-input w-full text-sm leading-relaxed"
                />
                <div className="mt-2 flex gap-2">
                  <button
                    type="button"
                    disabled={busy}
                    onClick={() => handleUpdate({ description: editDescVal.trim() || null })}
                    className="primary-button px-3 py-1.5 text-xs"
                  >
                    <FiCheck /> Save
                  </button>
                  <button
                    type="button"
                    onClick={() => {
                      setEditDescVal(deal.description || '')
                      setEditingDesc(false)
                    }}
                    className="secondary-button px-3 py-1.5 text-xs"
                  >
                    Cancel
                  </button>
                </div>
              </div>
            ) : (
              <div className="text-sm text-slate-700 dark:text-slate-300">
                {deal.description ? (
                  <p className="whitespace-pre-wrap leading-relaxed">{deal.description}</p>
                ) : (
                  <p className="italic text-slate-400">No opportunity summary added yet.</p>
                )}
              </div>
            )}
          </div>

          {/* Customer & Linked Association Card */}
          <div className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm dark:border-slate-800 dark:bg-slate-900">
            <h2 className="mb-3 text-xs font-bold uppercase tracking-wider text-slate-500">
              Customer & Associated Contact
            </h2>
            <div className="grid gap-3 sm:grid-cols-2">
              {/* Linked Company */}
              <div className="rounded-lg border border-slate-100 bg-slate-50/70 p-3.5 dark:border-slate-800 dark:bg-slate-950/40">
                <div className="flex items-center justify-between">
                  <span className="text-xs font-semibold text-slate-500">🏢 Company Account</span>
                  {deal.accountId && (
                    <Link
                      to={`/crm/companies/${deal.accountId}`}
                      className="text-xs font-medium text-indigo-600 hover:underline dark:text-indigo-400"
                    >
                      View Account →
                    </Link>
                  )}
                </div>
                <p className="mt-1 text-base font-bold text-slate-900 dark:text-slate-100">
                  {deal.accountName || linkedAccount?.name || 'No company linked'}
                </p>
                {linkedAccount?.website && (
                  <p className="text-xs text-slate-500 truncate">{linkedAccount.website}</p>
                )}
              </div>

              {/* Linked Contact */}
              <div className="rounded-lg border border-slate-100 bg-slate-50/70 p-3.5 dark:border-slate-800 dark:bg-slate-950/40">
                <div className="flex items-center justify-between">
                  <span className="text-xs font-semibold text-slate-500">👤 Primary Contact</span>
                  {deal.contactId && (
                    <Link
                      to={`/contacts?contactId=${deal.contactId}`}
                      className="text-xs font-medium text-indigo-600 hover:underline dark:text-indigo-400"
                    >
                      View Contact →
                    </Link>
                  )}
                </div>
                <p className="mt-1 text-base font-bold text-slate-900 dark:text-slate-100">
                  {deal.contactName || linkedContact?.displayName || 'No contact linked'}
                </p>
                {linkedContact?.organization && (
                  <p className="text-xs text-slate-500">Role: {linkedContact.organization}</p>
                )}
              </div>
            </div>

            {/* Attribution Banner */}
            {deal.attributionChannel && deal.attributionChannel !== 'DIRECT_SALES' && (
              <div className="mt-4 flex items-center gap-2 rounded-lg bg-purple-50 p-3 text-xs text-purple-700 dark:bg-purple-950/40 dark:text-purple-300 border border-purple-200 dark:border-purple-800">
                <span>🎯 Inbound Source:</span>
                <span className="font-semibold">
                  {deal.attributionChannel === 'BROADCAST' && `Broadcast (${deal.broadcastSubject || 'Campaign'})`}
                  {deal.attributionChannel === 'META_ADS' && `Meta Ads (${deal.adCampaignName || 'Ad'})`}
                  {deal.attributionChannel === 'GOOGLE_ADS' && `Google Ads (${deal.adCampaignName || 'Ad'})`}
                  {deal.attributionChannel === 'WEB_FORM' && `Web Lead Form (${deal.leadFormTitle || 'Form'})`}
                </span>
              </div>
            )}
          </div>

          {/* Jira-Style Collaboration Tabs: Comments, History, Attachments */}
          <div className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm dark:border-slate-800 dark:bg-slate-900">
            <div className="flex items-center gap-6 border-b border-slate-100 pb-2 dark:border-slate-800">
              <button
                type="button"
                onClick={() => setActiveTab('comments')}
                className={`flex items-center gap-2 pb-2 text-sm font-bold transition-colors ${
                  activeTab === 'comments'
                    ? 'border-b-2 border-indigo-600 text-indigo-600 dark:text-indigo-400'
                    : 'text-slate-500 hover:text-slate-800 dark:hover:text-slate-200'
                }`}
              >
                <FiMessageSquare className="h-4 w-4" />
                <span>Comments ({comments.length})</span>
              </button>

              <button
                type="button"
                onClick={() => setActiveTab('attachments')}
                className={`flex items-center gap-2 pb-2 text-sm font-bold transition-colors ${
                  activeTab === 'attachments'
                    ? 'border-b-2 border-indigo-600 text-indigo-600 dark:text-indigo-400'
                    : 'text-slate-500 hover:text-slate-800 dark:hover:text-slate-200'
                }`}
              >
                <FiPaperclip className="h-4 w-4" />
                <span>Documents ({attachments.length})</span>
              </button>

              <button
                type="button"
                onClick={() => setActiveTab('history')}
                className={`flex items-center gap-2 pb-2 text-sm font-bold transition-colors ${
                  activeTab === 'history'
                    ? 'border-b-2 border-indigo-600 text-indigo-600 dark:text-indigo-400'
                    : 'text-slate-500 hover:text-slate-800 dark:hover:text-slate-200'
                }`}
              >
                <FiClock className="h-4 w-4" />
                <span>History ({events.length})</span>
              </button>
            </div>

            {/* Tab 1: Comments */}
            {activeTab === 'comments' && (
              <div className="mt-4 flex flex-col gap-4">
                <form onSubmit={handleAddComment} className="flex flex-col gap-2">
                  <textarea
                    rows={3}
                    value={newComment}
                    onChange={(e) => setNewComment(e.target.value)}
                    placeholder="Log conversation notes, internal deal comments, or next steps..."
                    className="form-input w-full text-sm leading-relaxed"
                  />
                  <div className="flex justify-end">
                    <button
                      type="submit"
                      disabled={postingComment || !newComment.trim()}
                      className="primary-button gap-1.5 px-4 py-1.5 text-xs font-semibold"
                    >
                      <FiSend className="h-3 w-3" />
                      {postingComment ? 'Posting...' : 'Post Comment'}
                    </button>
                  </div>
                </form>

                <div className="mt-2 divide-y divide-slate-100 dark:divide-slate-800">
                  {comments.length === 0 ? (
                    <p className="py-6 text-center text-xs text-slate-400">
                      No comments on this opportunity yet. Collaborate with your sales team above!
                    </p>
                  ) : (
                    comments.map((c) => (
                      <div key={c.id} className="py-3">
                        <div className="flex items-center justify-between text-xs">
                          <div className="flex items-center gap-2">
                            <div className="flex h-6 w-6 items-center justify-center rounded-full bg-indigo-100 text-[10px] font-bold text-indigo-700 dark:bg-indigo-900 dark:text-indigo-300">
                              {c.authorName ? c.authorName.charAt(0).toUpperCase() : 'U'}
                            </div>
                            <span className="font-semibold text-slate-800 dark:text-slate-200">
                              {c.authorName || c.authorEmail || 'Teammate'}
                            </span>
                            <span className="text-[11px] text-slate-400">
                              {new Date(c.createdAt).toLocaleString()}
                            </span>
                          </div>
                          {canEdit && (
                            <button
                              type="button"
                              onClick={() => handleDeleteComment(c.id)}
                              className="text-slate-400 hover:text-rose-600 transition-colors"
                              title="Delete comment"
                            >
                              <FiTrash2 className="h-3.5 w-3.5" />
                            </button>
                          )}
                        </div>
                        <p className="mt-1.5 pl-8 text-sm text-slate-700 dark:text-slate-300 whitespace-pre-wrap">
                          {c.body}
                        </p>
                      </div>
                    ))
                  )}
                </div>
              </div>
            )}

            {/* Tab 2: Document Attachments */}
            {activeTab === 'attachments' && (
              <div className="mt-4 flex flex-col gap-4">
                {canEdit && (
                  <div className="flex items-center justify-between rounded-lg border border-dashed border-slate-300 p-4 dark:border-slate-700 bg-slate-50/50 dark:bg-slate-900/50">
                    <div>
                      <p className="text-xs font-semibold text-slate-700 dark:text-slate-300">
                        Attach Proposal, Contract, or Quotation
                      </p>
                      <p className="text-[11px] text-slate-400">
                        PDF, DOCX, XLSX, Images up to 25MB
                      </p>
                    </div>
                    <label className="primary-button cursor-pointer gap-1.5 px-3 py-1.5 text-xs">
                      <FiUploadCloud className="h-3.5 w-3.5" />
                      <span>{uploadingFile ? 'Uploading...' : 'Upload File'}</span>
                      <input
                        type="file"
                        onChange={handleFileUpload}
                        disabled={uploadingFile}
                        className="hidden"
                      />
                    </label>
                  </div>
                )}

                <div className="divide-y divide-slate-100 dark:divide-slate-800">
                  {attachments.length === 0 ? (
                    <p className="py-6 text-center text-xs text-slate-400">
                      No files or proposals attached to this deal yet.
                    </p>
                  ) : (
                    attachments.map((att) => (
                      <div key={att.id} className="flex items-center justify-between py-3 text-xs">
                        <div className="flex items-center gap-3">
                          <FiFileText className="h-5 w-5 text-indigo-500" />
                          <div>
                            <p className="font-semibold text-slate-800 dark:text-slate-200">
                              {att.filename}
                            </p>
                            <p className="text-[11px] text-slate-400">
                              {(att.sizeBytes / 1024).toFixed(1)} KB • {new Date(att.createdAt).toLocaleDateString()}
                            </p>
                          </div>
                        </div>
                        <div className="flex items-center gap-2">
                          {att.downloadUrl && (
                            <a
                              href={att.downloadUrl}
                              target="_blank"
                              rel="noreferrer"
                              className="secondary-button gap-1 px-2.5 py-1 text-xs"
                            >
                              <FiDownload className="h-3 w-3" />
                              <span>Download</span>
                            </a>
                          )}
                          {canEdit && (
                            <button
                              type="button"
                              onClick={() => handleDeleteAttachment(att.id)}
                              className="text-slate-400 hover:text-rose-600 transition-colors"
                              title="Delete attachment"
                            >
                              <FiTrash2 className="h-3.5 w-3.5" />
                            </button>
                          )}
                        </div>
                      </div>
                    ))
                  )}
                </div>
              </div>
            )}

            {/* Tab 3: Stage History */}
            {activeTab === 'history' && (
              <div className="mt-4 divide-y divide-slate-100 dark:divide-slate-800">
                {events.length === 0 ? (
                  <p className="py-6 text-center text-xs text-slate-400">
                    No transition history logged yet.
                  </p>
                ) : (
                  events.map((evt) => (
                    <div key={evt.id} className="py-3 text-xs">
                      <div className="flex items-center justify-between text-slate-500">
                        <span className="font-semibold text-slate-700 dark:text-slate-300">
                          {evt.actorName || evt.actorUsername || 'System'}
                        </span>
                        <span className="text-[11px]">
                          {new Date(evt.occurredAt).toLocaleString()}
                        </span>
                      </div>
                      <p className="mt-1 text-slate-600 dark:text-slate-400">
                        {evt.eventType === 'CREATED' && 'Created this deal.'}
                        {evt.eventType === 'STAGE_CHANGED' && (
                          <span>
                            Moved stage from <b className="text-slate-700 dark:text-slate-300">{evt.previousStage}</b> to{' '}
                            <b className="text-indigo-600 dark:text-indigo-400">{evt.newStage}</b>
                          </span>
                        )}
                        {evt.eventType === 'UPDATED' && (
                          <span>Updated fields: {evt.changedFields || 'deal details'}</span>
                        )}
                      </p>
                    </div>
                  ))
                )}
              </div>
            )}
          </div>
        </div>

        {/* Right Sidebar (35% Jira style Details Panel) */}
        <div className="flex flex-col gap-5">
          {/* Opportunity Details Card */}
          <div className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm dark:border-slate-800 dark:bg-slate-900">
            <h3 className="mb-4 text-xs font-bold uppercase tracking-wider text-slate-500">
              Deal Information
            </h3>

            <div className="space-y-4">
              {/* Value & Currency */}
              <div className="grid grid-cols-2 gap-2">
                <div>
                  <label className="text-xs font-semibold text-slate-600 dark:text-slate-400">
                    Amount
                  </label>
                  <input
                    type="number"
                    disabled={!canEdit || busy}
                    value={deal.amount}
                    onChange={(e) => handleUpdate({ amount: e.target.value })}
                    className="form-input mt-1.5 w-full text-xs font-bold"
                  />
                </div>
                <div>
                  <label className="text-xs font-semibold text-slate-600 dark:text-slate-400">
                    Currency
                  </label>
                  <input
                    type="text"
                    maxLength={3}
                    disabled={!canEdit || busy}
                    value={deal.currency}
                    onChange={(e) => handleUpdate({ currency: e.target.value.toUpperCase() })}
                    className="form-input mt-1.5 w-full text-xs font-bold"
                  />
                </div>
              </div>

              {/* Pipeline Selector */}
              <div>
                <label className="text-xs font-semibold text-slate-600 dark:text-slate-400">
                  Sales Pipeline
                </label>
                <select
                  disabled={!canEdit || busy}
                  value={deal.pipelineId || activePipeline?.id}
                  onChange={(e) => handleUpdate({ pipelineId: e.target.value })}
                  className="form-input mt-1.5 w-full text-xs font-semibold"
                >
                  {pipelines.map((p) => (
                    <option key={p.id} value={p.id}>
                      {p.name} {p.isDefault ? '(Default)' : ''}
                    </option>
                  ))}
                </select>
              </div>

              {/* Current Stage */}
              <div>
                <label className="text-xs font-semibold text-slate-600 dark:text-slate-400">
                  Stage
                </label>
                <select
                  disabled={!canEdit || busy}
                  value={deal.stage}
                  onChange={(e) => handleMoveStage(e.target.value)}
                  className="form-input mt-1.5 w-full text-xs font-semibold"
                >
                  {stages.map((stg) => (
                    <option key={stg.id || stg.key} value={stg.key}>
                      {stg.name} ({stg.probability}%)
                    </option>
                  ))}
                </select>
              </div>

              {/* Expected Close Date */}
              <div>
                <label className="text-xs font-semibold text-slate-600 dark:text-slate-400">
                  Expected Close Date
                </label>
                <input
                  type="date"
                  disabled={!canEdit || busy}
                  value={deal.expectedCloseDate || ''}
                  onChange={(e) => handleUpdate({ expectedCloseDate: e.target.value || null })}
                  className="form-input mt-1.5 w-full text-xs"
                />
              </div>

              {/* Deal Owner */}
              <div>
                <label className="text-xs font-semibold text-slate-600 dark:text-slate-400">
                  Deal Owner
                </label>
                <select
                  disabled={!canEdit || busy}
                  value={deal.ownerUserId || ''}
                  onChange={(e) => handleUpdate({ ownerUserId: e.target.value || null })}
                  className="form-input mt-1.5 w-full text-xs"
                >
                  <option value="">Unassigned</option>
                  {members.map((m) => (
                    <option key={m.userId} value={m.userId}>
                      {m.name}
                    </option>
                  ))}
                </select>
              </div>

              {/* Linked Company */}
              <div>
                <label className="text-xs font-semibold text-slate-600 dark:text-slate-400">
                  Customer Company
                </label>
                <select
                  disabled={!canEdit || busy}
                  value={deal.accountId || ''}
                  onChange={(e) => handleUpdate({ accountId: e.target.value || null })}
                  className="form-input mt-1.5 w-full text-xs"
                >
                  <option value="">No company</option>
                  {accounts.map((a) => (
                    <option key={a.id} value={a.id}>
                      {a.name}
                    </option>
                  ))}
                </select>
              </div>

              {/* Linked Contact */}
              <div>
                <label className="text-xs font-semibold text-slate-600 dark:text-slate-400">
                  Primary Contact
                </label>
                <select
                  disabled={!canEdit || busy}
                  value={deal.contactId || ''}
                  onChange={(e) => handleUpdate({ contactId: e.target.value || null })}
                  className="form-input mt-1.5 w-full text-xs"
                >
                  <option value="">No contact</option>
                  {leads.map((l) => (
                    <option key={l.contactId} value={l.contactId}>
                      {l.displayName || 'Lead'} {l.organization ? `(${l.organization})` : ''}
                    </option>
                  ))}
                </select>
              </div>
            </div>
          </div>

          {/* Deal Metadata */}
          <div className="rounded-xl border border-slate-200 bg-slate-50/60 p-4 text-[11px] text-slate-500 dark:border-slate-800 dark:bg-slate-900/50">
            <p>Created: {new Date(deal.createdAt || Date.now()).toLocaleString()}</p>
            {deal.updatedAt && (
              <p className="mt-1">Last updated: {new Date(deal.updatedAt).toLocaleString()}</p>
            )}
            <p className="mt-1">Version: {deal.version}</p>
          </div>
        </div>
      </div>

      {/* Delete Confirmation Modal */}
      {showDeleteModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/60 p-4 backdrop-blur-sm">
          <div className="panel w-full max-w-md p-6">
            <h3 className="text-lg font-bold text-slate-900 dark:text-slate-100">Delete Opportunity</h3>
            <p className="mt-2 text-sm text-slate-500">
              Are you sure you want to delete &ldquo;{deal.title}&rdquo;? This will remove all associated comments and records.
            </p>
            <div className="mt-6 flex justify-end gap-3">
              <button
                type="button"
                onClick={() => setShowDeleteModal(false)}
                className="secondary-button text-xs"
              >
                Cancel
              </button>
              <button
                type="button"
                disabled={busy}
                onClick={handleDelete}
                className="primary-button bg-rose-600 text-xs hover:bg-rose-700"
              >
                {busy ? 'Deleting...' : 'Yes, Delete Deal'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
