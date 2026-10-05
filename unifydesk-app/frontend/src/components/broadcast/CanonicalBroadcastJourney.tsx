import { useEffect, useMemo, useState } from 'react'
import { useSearchParams } from 'react-router'
import {
  broadcastApi,
  isBroadcastAudiencePreviewStale,
} from '../../features/broadcast/broadcastApi'
import type {
  AudienceSelector,
  BroadcastSender,
  ChannelReadiness,
  CreateAudienceBroadcastMessageInput,
  RecipientExclusionReason,
  RecipientResolutionPreview,
} from '../../features/broadcast/types'
import { contactsApi } from '../../features/contacts/contactsApi'
import { PERMISSIONS } from '../../features/iam/permissions'
import { useIam } from '../../features/iam/useIam'
import type { ContactSummary, ListSummary, SegmentDefinition } from '../../features/contacts/types'
import type { ChannelType } from '../../features/channel/types'
import { whatsappApi } from '../../features/whatsapp/whatsappApi'
import type {
  WhatsAppTemplate,
  WhatsAppTemplateParameters,
} from '../../features/whatsapp/types'
import { apiErrorMessage } from '../../lib/api/apiClient'
import { useAppSelector } from '../../store/hooks'
import {
  selectSelectedCompanyId,
  selectWorkspaceEpoch,
} from '../../store/workspace/workspaceSlice'
import { useWorkspaceEpochScope } from '../../features/workspace/useWorkspaceEpochScope'
import { templateVariableCounts } from '../whatsapp/whatsappTemplateUtils'
import { managedSmsApi } from '../../features/managedSms/managedSmsApi'
import {
  isManagedSmsSender,
  managedSmsTemplateReadyForHeader,
  managedSmsVariableErrors,
} from '../../features/managedSms/composePolicy'
import { managedSmsErrorMessage } from '../../features/managedSms/copy'
import ConfirmDialog from '../ui/ConfirmDialog'
import type {
  ManagedSmsHeader,
  ManagedSmsPreview,
  ManagedSmsTemplate,
} from '../../features/managedSms/types'

type Props = {
  channel: ChannelType
  onCreated: (broadcastId: string) => void
}

const exclusionLabels: Record<RecipientExclusionReason, string> = {
  DUPLICATE: 'Duplicate identity',
  MISSING_IDENTITY: 'Missing channel identity',
  INVALID_IDENTITY: 'Invalid channel identity',
  OPTED_OUT: 'Opted out',
  CONSENT_REQUIRED: 'Consent required',
  SUPPRESSED: 'Suppressed',
  CHANNEL_INELIGIBLE: 'Not eligible for this channel',
}

const channelLabel = (channel: ChannelType) => (channel === 'WHATSAPP' ? 'WhatsApp' : channel)

export const buildAudienceSelector = (
  sourceType: 'CONTACT_LIST' | 'SEGMENT' | 'CONTACTS',
  sourceId: string,
  contactIds: Iterable<string>,
): AudienceSelector | null => {
  if (sourceType === 'CONTACT_LIST') return sourceId ? { sourceType, sourceId } : null
  if (sourceType === 'SEGMENT') return sourceId ? { sourceType, sourceId } : null
  const ids = Array.from(contactIds)
  return ids.length > 0 ? { sourceType: 'CONTACTS', contactIds: ids } : null
}

const blankWhatsAppParameters = (template: WhatsAppTemplate): WhatsAppTemplateParameters => {
  const counts = templateVariableCounts(template)
  return {
    header: Array.from({ length: counts.header }, () => ''),
    body: Array.from({ length: counts.body }, () => ''),
    buttons: Array.from({ length: counts.buttons }, () => ''),
  }
}

const sourceLabel = (selector: AudienceSelector | null, lists: ListSummary[], segments: SegmentDefinition[]) => {
  if (!selector) return 'Choose an audience'
  if (selector.sourceType === 'CONTACT_LIST') {
    return lists.find((item) => item.id === selector.sourceId)?.name ?? 'Contact List'
  }
  if (selector.sourceType === 'SEGMENT') {
    return segments.find((item) => item.id === selector.sourceId)?.name ?? 'Segment'
  }
  return `${selector.contactIds.length} selected Contacts`
}

type CanonicalAudienceSource =
  | { sourceType: 'LIST' | 'SEGMENT'; sourceId: string }
  | { sourceType: 'CONTACTS'; contactIds: string[] }

export const buildCanonicalBroadcastPayload = ({
  source,
  channel,
  senderId,
  subject,
  body,
  whatsappTemplateId,
  whatsappTemplateParameters,
  managedSmsTemplateId,
  templateVariables,
}: {
  source: CanonicalAudienceSource
  channel: ChannelType
  senderId: string
  subject: string
  body: string
  whatsappTemplateId?: string | null
  whatsappTemplateParameters: WhatsAppTemplateParameters
  managedSmsTemplateId?: string | null
  templateVariables?: Record<string, string>
}): CreateAudienceBroadcastMessageInput => {
  const usesManagedSms = channel === 'SMS' && Boolean(managedSmsTemplateId)
  return {
    ...source,
    channelType: channel,
    senderId,
    subject: channel === 'EMAIL' ? subject.trim() || null : null,
    bodyText: channel === 'WHATSAPP' || usesManagedSms ? null : body.trim() || null,
    templateId: null,
    managedSmsTemplateId: usesManagedSms ? managedSmsTemplateId : null,
    templateVariables: usesManagedSms ? templateVariables : undefined,
    whatsappTemplateId: channel === 'WHATSAPP' ? whatsappTemplateId || null : null,
    whatsappTemplateParameters:
      channel === 'WHATSAPP' ? whatsappTemplateParameters : null,
  }
}

export const canonicalSmsContentReady = ({
  sender,
  body,
  managedSmsHeaderId,
  templates,
  managedSmsTemplateId,
  templateVariables,
  preview,
}: {
  sender?: Pick<BroadcastSender, 'provider' | 'managementMode'> | null
  body: string
  managedSmsHeaderId?: string | null
  templates: ManagedSmsTemplate[]
  managedSmsTemplateId: string
  templateVariables: Record<string, string>
  preview?: ManagedSmsPreview | null
}) => {
  if (!isManagedSmsSender(sender)) return Boolean(body.trim())
  if (!managedSmsHeaderId || !managedSmsTemplateId) return false
  const selected = templates.find((template) => template.id === managedSmsTemplateId)
  return Boolean(
    selected &&
      managedSmsTemplateReadyForHeader(selected, managedSmsHeaderId) &&
      Object.keys(managedSmsVariableErrors(selected, templateVariables)).length === 0 &&
      (preview?.validationErrors.length ?? 0) === 0,
  )
}

export default function CanonicalBroadcastJourney({ channel, onCreated }: Props) {
  const companyId = useAppSelector(selectSelectedCompanyId)
  const epoch = useAppSelector(selectWorkspaceEpoch)
  const { capture: captureScope, isCurrent: scopeIsCurrent } = useWorkspaceEpochScope(
    companyId,
    epoch,
  )
  const journeyScopeKey = `${companyId ?? 'no-workspace'}:${epoch}:${channel}`
  const { can } = useIam()
  const [searchParams, setSearchParams] = useSearchParams()
  const [lists, setLists] = useState<ListSummary[]>([])
  const [segments, setSegments] = useState<SegmentDefinition[]>([])
  const [contacts, setContacts] = useState<ContactSummary[]>([])
  const [sourceType, setSourceType] = useState<'CONTACT_LIST' | 'SEGMENT' | 'CONTACTS'>(
    searchParams.get('audienceType') === 'SEGMENT'
      ? 'SEGMENT'
      : searchParams.get('audienceType') === 'CONTACTS'
        ? 'CONTACTS'
        : 'CONTACT_LIST',
  )
  const [sourceId, setSourceId] = useState(searchParams.get('audienceId') ?? '')
  const [contactIds, setContactIds] = useState<Set<string>>(new Set())
  const [readiness, setReadiness] = useState<ChannelReadiness | null>(null)
  const [broadcastSenders, setBroadcastSenders] = useState<BroadcastSender[]>([])
  const [catalogScopeKey, setCatalogScopeKey] = useState<string | null>(null)
  const [senderId, setSenderId] = useState('')
  const [preview, setPreview] = useState<RecipientResolutionPreview | null>(null)
  const [previewOwnerKey, setPreviewOwnerKey] = useState<string | null>(null)
  const [previewing, setPreviewing] = useState(false)
  const [subject, setSubject] = useState('')
  const [body, setBody] = useState('')
  const [templateId, setTemplateId] = useState('')
  const [whatsappTemplates, setWhatsappTemplates] = useState<WhatsAppTemplate[]>([])
  const [whatsappParameters, setWhatsappParameters] = useState<WhatsAppTemplateParameters>({
    header: [],
    body: [],
    buttons: [],
  })
  const [managedSmsHeaders, setManagedSmsHeaders] = useState<ManagedSmsHeader[]>([])
  const [managedSmsTemplates, setManagedSmsTemplates] = useState<ManagedSmsTemplate[]>([])
  const [managedSmsTemplateId, setManagedSmsTemplateId] = useState('')
  const [managedSmsTemplateVariables, setManagedSmsTemplateVariables] = useState<
    Record<string, string>
  >({})
  const [managedSmsPreview, setManagedSmsPreview] = useState<ManagedSmsPreview | null>(null)
  const [managedSmsLoading, setManagedSmsLoading] = useState(false)
  const [scheduledAt, setScheduledAt] = useState('')
  const [busy, setBusy] = useState(false)
  const [launchConfirmOpen, setLaunchConfirmOpen] = useState(false)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  const selector = useMemo(
    () => buildAudienceSelector(sourceType, sourceId, contactIds),
    [contactIds, sourceId, sourceType],
  )

  const catalogIsCurrent = catalogScopeKey === journeyScopeKey
  const currentLists = catalogIsCurrent ? lists : []
  const currentSegments = catalogIsCurrent ? segments : []
  const currentContacts = catalogIsCurrent ? contacts : []
  const currentReadiness = catalogIsCurrent ? readiness : null
  const currentBroadcastSenders = catalogIsCurrent ? broadcastSenders : []
  const selectedReadiness =
    currentReadiness?.senders.find((item) => item.senderId === senderId) ?? null
  const readySenders = currentReadiness?.senders.filter((item) => item.state === 'READY') ?? []
  const selectedBroadcastSender =
    currentBroadcastSenders.find((item) => item.id === senderId) ?? null
  const selectedWhatsappTemplate = whatsappTemplates.find((item) => item.id === templateId) ?? null
  const whatsappParametersReady = [
    ...whatsappParameters.header,
    ...whatsappParameters.body,
    ...whatsappParameters.buttons,
  ].every((value) => value.trim().length > 0)
  const usesManagedSms = channel === 'SMS' && isManagedSmsSender(selectedBroadcastSender)
  const managedSmsHeader = usesManagedSms
    ? managedSmsHeaders.find((item) => item.header === selectedBroadcastSender?.fromAddress) ?? null
    : null
  const availableManagedSmsTemplates = useMemo(
    () =>
      managedSmsHeader
        ? managedSmsTemplates.filter((template) =>
            managedSmsTemplateReadyForHeader(template, managedSmsHeader.id),
          )
        : [],
    [managedSmsHeader, managedSmsTemplates],
  )
  const selectedManagedSmsTemplate =
    managedSmsTemplates.find((template) => template.id === managedSmsTemplateId) ?? null
  const managedSmsVariableValidation = useMemo(
    () => managedSmsVariableErrors(selectedManagedSmsTemplate, managedSmsTemplateVariables),
    [managedSmsTemplateVariables, selectedManagedSmsTemplate],
  )
  const previewRequestKey = `${journeyScopeKey}:${senderId}:${
    selector ? JSON.stringify(selector) : 'no-audience'
  }`
  const currentPreview = previewOwnerKey === previewRequestKey ? preview : null
  const contentReady =
    channel === 'WHATSAPP'
      ? Boolean(selectedWhatsappTemplate && whatsappParametersReady)
      : channel === 'EMAIL'
        ? Boolean(subject.trim() && body.trim())
        : canonicalSmsContentReady({
            sender: selectedBroadcastSender,
            body,
            managedSmsHeaderId: managedSmsHeader?.id,
            templates: managedSmsTemplates,
            managedSmsTemplateId,
            templateVariables: managedSmsTemplateVariables,
            preview: managedSmsPreview,
          })
  const canLaunch =
    catalogIsCurrent &&
    Boolean(selector) &&
    Boolean(currentPreview) &&
    currentPreview!.eligibleCount > 0 &&
    Boolean(selectedReadiness && selectedReadiness.state === 'READY') &&
    !busy &&
    contentReady &&
    can(PERMISSIONS.BROADCAST_CREATE) &&
    can(PERMISSIONS.BROADCAST_SEND)

  useEffect(() => {
    if (!companyId) return
    const token = captureScope()
    setLoading(true)
    setError('')
    Promise.all([
      contactsApi.lists(companyId),
      contactsApi.segments(companyId),
      contactsApi.list(companyId, { size: 50, sort: 'UPDATED_DESC' }),
      broadcastApi.channelReadiness(companyId, channel),
      broadcastApi.senders(companyId),
    ])
      .then(([nextLists, nextSegments, contactPage, nextReadiness, nextSenders]) => {
        if (!scopeIsCurrent(token)) return
        setLists(nextLists.filter((item) => item.status !== 'ARCHIVED'))
        setSegments(nextSegments.filter((item) => item.status !== 'ARCHIVED' && !item.archived))
        setContacts(contactPage.items)
        setReadiness(nextReadiness)
        setBroadcastSenders(nextSenders)
        setCatalogScopeKey(journeyScopeKey)
        const firstReady = nextReadiness.senders.find((item) => item.state === 'READY')
        setSenderId((current) =>
          current && nextReadiness.senders.some((item) => item.senderId === current)
            ? current
            : (firstReady?.senderId ?? ''),
        )
      })
      .catch((requestError) => {
        if (scopeIsCurrent(token)) {
          setError(apiErrorMessage(requestError, 'Unable to load Broadcast readiness.'))
        }
      })
      .finally(() => {
        if (scopeIsCurrent(token)) setLoading(false)
      })
  }, [captureScope, channel, companyId, epoch, journeyScopeKey, scopeIsCurrent])

  useEffect(() => {
    setPreview(null)
    setPreviewOwnerKey(null)
  }, [selector, senderId, channel])

  useEffect(() => {
    if (
      channel !== 'WHATSAPP' ||
      !companyId ||
      !senderId ||
      !selectedBroadcastSender?.channelConnectionId
    ) {
      setWhatsappTemplates([])
      setTemplateId('')
      setWhatsappParameters({ header: [], body: [], buttons: [] })
      return
    }

    const token = captureScope()
    setWhatsappTemplates([])
    void whatsappApi
      .templates(companyId, selectedBroadcastSender.channelConnectionId)
      .then((items) => {
        if (!scopeIsCurrent(token)) return
        setWhatsappTemplates(items.filter((item) => item.status === 'APPROVED'))
      })
      .catch((requestError) => {
        if (scopeIsCurrent(token)) {
          setError(apiErrorMessage(requestError, 'Unable to load approved WhatsApp templates.'))
        }
      })
  }, [
    channel,
    companyId,
    epoch,
    captureScope,
    scopeIsCurrent,
    selectedBroadcastSender?.channelConnectionId,
    senderId,
  ])

  useEffect(() => {
    setManagedSmsHeaders([])
    setManagedSmsTemplates([])
    setManagedSmsTemplateId('')
    setManagedSmsTemplateVariables({})
    setManagedSmsPreview(null)

    if (!companyId || !usesManagedSms) {
      setManagedSmsLoading(false)
      return
    }

    let active = true
    const token = captureScope()
    setManagedSmsLoading(true)
    Promise.all([managedSmsApi.headers(companyId), managedSmsApi.templates(companyId)])
      .then(([nextHeaders, nextTemplates]) => {
        if (!active || !scopeIsCurrent(token)) return
        setManagedSmsHeaders(nextHeaders)
        setManagedSmsTemplates(nextTemplates)
      })
      .catch((requestError) => {
        if (active && scopeIsCurrent(token)) {
          setError(
            managedSmsErrorMessage(
              requestError,
              'Unable to load Managed SMS Headers and templates.',
            ),
          )
        }
      })
      .finally(() => {
        if (active && scopeIsCurrent(token)) setManagedSmsLoading(false)
      })

    return () => {
      active = false
    }
  }, [captureScope, companyId, epoch, scopeIsCurrent, senderId, usesManagedSms])

  useEffect(() => {
    setManagedSmsPreview(null)
    if (
      !companyId ||
      !usesManagedSms ||
      !managedSmsHeader ||
      !managedSmsTemplateId
    ) {
      return
    }

    let active = true
    const token = captureScope()
    const timer = window.setTimeout(() => {
      if (!scopeIsCurrent(token)) return
      void managedSmsApi
        .previewTemplate(companyId, managedSmsTemplateId, {
          headerId: managedSmsHeader.id,
          variables: managedSmsTemplateVariables,
        })
        .then((nextPreview) => {
          if (active && scopeIsCurrent(token)) setManagedSmsPreview(nextPreview)
        })
        .catch((requestError) => {
          if (!active || !scopeIsCurrent(token)) return
          setManagedSmsPreview(null)
          setError(
            managedSmsErrorMessage(
              requestError,
              'Unable to preview this Managed SMS template.',
            ),
          )
        })
    }, 250)

    return () => {
      active = false
      window.clearTimeout(timer)
    }
  }, [
    companyId,
    epoch,
    managedSmsHeader,
    managedSmsTemplateId,
    managedSmsTemplateVariables,
    captureScope,
    scopeIsCurrent,
    usesManagedSms,
  ])

  useEffect(() => {
    if (!sourceId) return
    setSearchParams((current) => {
      const next = new URLSearchParams(current)
      next.set('audienceType', sourceType)
      next.set('audienceId', sourceId)
      return next
    }, { replace: true })
  }, [setSearchParams, sourceId, sourceType])

  const runPreview = async () => {
    if (
      !companyId ||
      !selector ||
      !senderId ||
      !catalogIsCurrent ||
      selectedReadiness?.state !== 'READY'
    ) {
      return
    }
    const token = captureScope()
    const requestKey = previewRequestKey
    setPreviewing(true)
    setError('')
    try {
      const result = await broadcastApi.previewAudience(companyId, {
        selector,
        channel,
        senderId,
      })
      if (scopeIsCurrent(token)) {
        setPreview(result)
        setPreviewOwnerKey(requestKey)
      }
    } catch (requestError) {
      if (scopeIsCurrent(token)) {
        setError(apiErrorMessage(requestError, 'Unable to preview recipient eligibility.'))
      }
    } finally {
      if (scopeIsCurrent(token)) setPreviewing(false)
    }
  }

  const launch = async () => {
    if (!companyId || !selector || !currentPreview || !senderId || !canLaunch) return
    const confirmedPreview = currentPreview

    const token = captureScope()
    setBusy(true)
    setError('')
    try {
      const source =
        selector.sourceType === 'CONTACT_LIST'
          ? { sourceType: 'LIST' as const, sourceId: selector.sourceId }
          : selector.sourceType === 'SEGMENT'
            ? { sourceType: 'SEGMENT' as const, sourceId: selector.sourceId }
            : { sourceType: 'CONTACTS' as const, contactIds: selector.contactIds }

      const draft = await broadcastApi.createAudienceMessage(
        companyId,
        buildCanonicalBroadcastPayload({
          source,
          channel,
          senderId,
          subject,
          body,
          whatsappTemplateId: templateId,
          whatsappTemplateParameters: whatsappParameters,
          managedSmsTemplateId: usesManagedSms ? managedSmsTemplateId : null,
          templateVariables: usesManagedSms ? managedSmsTemplateVariables : undefined,
        }),
      )
      if (!scopeIsCurrent(token)) return

      let launched
      if (scheduledAt) {
        const scheduledDate = new Date(scheduledAt)
        if (Number.isNaN(scheduledDate.getTime()) || scheduledDate.getTime() <= Date.now()) {
          throw new Error('Schedule time must be in the future')
        }
        launched = await broadcastApi.schedule(
          companyId,
          draft.id,
          scheduledDate.toISOString(),
          confirmedPreview.sourceDescriptor,
        )
      } else {
        launched = await broadcastApi.send(
          companyId,
          draft.id,
          confirmedPreview.sourceDescriptor,
        )
      }
      if (scopeIsCurrent(token)) onCreated(launched.id)
    } catch (requestError) {
      if (scopeIsCurrent(token)) {
        if (isBroadcastAudiencePreviewStale(requestError)) {
          setPreview(null)
          setPreviewOwnerKey(null)
          setError(
            'The audience changed since this preview. Preview eligible recipients again before sending or scheduling.',
          )
        } else {
          setError(apiErrorMessage(requestError, 'Unable to launch Broadcast.'))
        }
      }
    } finally {
      if (scopeIsCurrent(token)) setBusy(false)
      setLaunchConfirmOpen(false)
    }
  }

  if (!companyId) return null

  return (
    <div className="channel-message-scroll min-h-[calc(100dvh-214px)] p-4 sm:p-5">
      <div className="mx-auto grid max-w-5xl gap-4">
        <section className="panel p-4 sm:p-5">
          <div className="flex flex-wrap items-start justify-between gap-3">
            <div>
              <p className="eyebrow">AUDIENCE</p>
              <h1 className="mt-2 text-xl font-bold text-slate-950">
                Create {channelLabel(channel)} Broadcast
              </h1>
              <p className="mt-1 text-sm text-slate-500">
                Use canonical Contacts, Lists or Segments. Legacy Broadcast groups remain available only for historical sends.
              </p>
            </div>
            <span className="rounded-full bg-indigo-50 px-3 py-1 text-xs font-semibold text-indigo-700">
              {sourceLabel(selector, currentLists, currentSegments)}
            </span>
          </div>

          {error && (
            <div role="alert" className="mt-4 rounded-xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700">
              {error}
            </div>
          )}

          <div className="mt-5 grid gap-4 lg:grid-cols-2">
            <div>
              <p className="text-xs font-bold uppercase tracking-wide text-slate-500">1. Audience source</p>
              <div className="mt-2 grid grid-cols-3 gap-2">
                {(['CONTACT_LIST', 'SEGMENT', 'CONTACTS'] as const).map((type) => (
                  <button
                    key={type}
                    type="button"
                    className={`rounded-xl border px-3 py-2 text-xs font-semibold ${sourceType === type ? 'border-indigo-400 bg-indigo-50 text-indigo-700' : 'border-slate-200 bg-white text-slate-600'}`}
                    onClick={() => {
                      setSourceType(type)
                      setSourceId('')
                      setContactIds(new Set())
                    }}
                  >
                    {type === 'CONTACT_LIST' ? 'List' : type === 'SEGMENT' ? 'Segment' : 'Contacts'}
                  </button>
                ))}
              </div>

              {sourceType !== 'CONTACTS' ? (
                <select
                  className="form-input mt-3"
                  value={sourceId}
                  disabled={loading}
                  onChange={(event) => setSourceId(event.target.value)}
                  aria-label="Audience"
                >
                  <option value="">Choose {sourceType === 'CONTACT_LIST' ? 'a List' : 'a Segment'}</option>
                  {(sourceType === 'CONTACT_LIST' ? currentLists : currentSegments).map((item) => (
                    <option key={item.id} value={item.id}>
                      {item.name} ({(sourceType === 'CONTACT_LIST' ? (item as ListSummary).memberCount : (item as SegmentDefinition).count ?? 0).toLocaleString()})
                    </option>
                  ))}
                </select>
              ) : (
                <div className="mt-3 max-h-64 overflow-y-auto rounded-xl border border-slate-200">
                  {currentContacts.map((contact) => (
                    <label key={contact.id} className="flex cursor-pointer items-center gap-3 border-b border-slate-100 px-3 py-2 last:border-b-0">
                      <input
                        type="checkbox"
                        checked={contactIds.has(contact.id)}
                        onChange={() =>
                          setContactIds((current) => {
                            const next = new Set(current)
                            if (next.has(contact.id)) next.delete(contact.id)
                            else if (next.size < 200) next.add(contact.id)
                            return next
                          })
                        }
                      />
                      <span className="min-w-0">
                        <span className="block truncate text-sm font-semibold text-slate-800">{contact.displayName}</span>
                        <span className="block truncate text-xs text-slate-500">{contact.primaryEmail || contact.primaryPhone || 'No primary identity'}</span>
                      </span>
                    </label>
                  ))}
                  <p className="px-3 py-2 text-[11px] text-slate-500">
                    Showing a bounded sample. Select up to 200 Contacts directly; use a List or Segment for larger audiences.
                  </p>
                </div>
              )}
            </div>

            <div>
              <p className="text-xs font-bold uppercase tracking-wide text-slate-500">2. Channel readiness</p>
              <div className="mt-2 rounded-xl border border-slate-200 p-3">
                <p className="text-sm font-semibold text-slate-900">
                  {currentReadiness?.configuration?.replaceAll('_', ' ') ?? (loading ? 'Loading…' : 'Unavailable')}
                </p>
                <p className="mt-1 text-xs text-slate-500">
                  {currentReadiness?.nextAction || (readySenders.length ? 'Choose a ready sender.' : 'No ready sender is available.')}
                </p>
                <select
                  className="form-input mt-3"
                  value={senderId}
                  onChange={(event) => setSenderId(event.target.value)}
                  aria-label="Ready sender"
                >
                  <option value="">Choose sender</option>
                  {currentReadiness?.senders.map((sender) => (
                    <option key={sender.senderId} value={sender.senderId} disabled={sender.state !== 'READY'}>
                      {sender.label} — {sender.state.replaceAll('_', ' ')}
                    </option>
                  ))}
                </select>
                {selectedReadiness && (
                  <p className="mt-2 text-[11px] leading-5 text-slate-500">
                    Provider acceptance: {selectedReadiness.capabilities.providerAcceptance ? 'tracked' : 'not reported'} ·
                    Delivery: {selectedReadiness.capabilities.deliveryEvents ? 'tracked' : 'not reported'} ·
                    Read: {selectedReadiness.capabilities.readEvents ? 'tracked' : 'not reported'}
                  </p>
                )}
              </div>
            </div>
          </div>

          <button
            type="button"
            className="secondary-button mt-4"
            disabled={
              !catalogIsCurrent ||
              !selector ||
              !senderId ||
              selectedReadiness?.state !== 'READY' ||
              previewing
            }
            onClick={() => void runPreview()}
          >
            {previewing ? 'Checking eligibility…' : 'Preview eligible recipients'}
          </button>
        </section>

        {currentPreview && (
          <section className="panel p-4 sm:p-5" aria-label="Recipient eligibility preview">
            <div className="grid gap-3 sm:grid-cols-3">
              <div className="rounded-xl bg-slate-50 p-3"><p className="text-xs text-slate-500">Selected</p><strong className="text-xl">{currentPreview.selectedCount.toLocaleString()}</strong></div>
              <div className="rounded-xl bg-emerald-50 p-3"><p className="text-xs text-emerald-700">Eligible</p><strong className="text-xl text-emerald-800">{currentPreview.eligibleCount.toLocaleString()}</strong></div>
              <div className="rounded-xl bg-amber-50 p-3"><p className="text-xs text-amber-700">Excluded</p><strong className="text-xl text-amber-800">{currentPreview.excludedCount.toLocaleString()}</strong></div>
            </div>
            <div className="mt-4 grid gap-2 sm:grid-cols-2 lg:grid-cols-3">
              {Object.entries(currentPreview.exclusions).filter(([, count]) => Number(count) > 0).map(([reason, count]) => (
                <div key={reason} className="rounded-lg border border-slate-200 px-3 py-2 text-xs">
                  <span className="font-semibold text-slate-800">{exclusionLabels[reason as RecipientExclusionReason]}</span>
                  <span className="float-right text-slate-500">{Number(count).toLocaleString()}</span>
                </div>
              ))}
            </div>
            {currentPreview.selectedCount !== currentPreview.eligibleCount + currentPreview.excludedCount && (
              <p className="mt-3 text-xs font-semibold text-rose-700">Preview counts are inconsistent. Refresh before sending.</p>
            )}
            {currentPreview.eligibleCount === 0 && (
              <p className="mt-3 text-sm font-semibold text-amber-700">No eligible recipients. Resolve exclusions before sending.</p>
            )}
          </section>
        )}

        <section className="panel p-4 sm:p-5">
          <p className="text-xs font-bold uppercase tracking-wide text-slate-500">3. Message & launch</p>
          <div className="mt-3 grid gap-3">
            {channel === 'EMAIL' && (
              <label className="text-xs font-semibold text-slate-700">
                Subject
                <input className="form-input mt-1" value={subject} onChange={(event) => setSubject(event.target.value)} maxLength={998} />
              </label>
            )}
            {channel === 'WHATSAPP' ? (
              <>
                <label className="text-xs font-semibold text-slate-700">
                  Approved WhatsApp template
                  <select
                    className="form-input mt-1"
                    value={templateId}
                    onChange={(event) => {
                      const nextId = event.target.value
                      setTemplateId(nextId)
                      const template = whatsappTemplates.find((item) => item.id === nextId)
                      setWhatsappParameters(
                        template
                          ? blankWhatsAppParameters(template)
                          : { header: [], body: [], buttons: [] },
                      )
                    }}
                  >
                    <option value="">Choose approved template</option>
                    {whatsappTemplates.map((template) => (
                      <option key={template.id} value={template.id}>
                        {template.name} · {template.language}
                      </option>
                    ))}
                  </select>
                  <span className="mt-1 block font-normal text-slate-500">
                    Only templates currently synced as APPROVED are available. Free-form Inbox session rules do not apply to Broadcast.
                  </span>
                </label>
                {selectedWhatsappTemplate && (
                  <div className="grid gap-3 sm:grid-cols-2">
                    {(['header', 'body', 'buttons'] as const).flatMap((section) =>
                      whatsappParameters[section].map((value, index) => (
                        <label
                          key={`${section}-${index}`}
                          className="text-xs font-semibold text-slate-700"
                        >
                          {section === 'body'
                            ? 'Body'
                            : section === 'header'
                              ? 'Header'
                              : 'Button'} variable {index + 1}
                          <input
                            className="form-input mt-1"
                            value={value}
                            onChange={(event) =>
                              setWhatsappParameters((current) => ({
                                ...current,
                                [section]: current[section].map((item, itemIndex) =>
                                  itemIndex === index ? event.target.value : item,
                                ),
                              }))
                            }
                          />
                        </label>
                      )),
                    )}
                  </div>
                )}
              </>
            ) : channel === 'SMS' && usesManagedSms ? (
              <div className="grid gap-3">
                <div className="rounded-xl border border-indigo-100 bg-indigo-50/60 p-4">
                  <p className="text-xs font-bold text-slate-900">Approved template required</p>
                  <p className="mt-1 text-[11px] leading-5 text-slate-600">
                    Managed SMS sends the exact DLT-approved template for this Header. The message
                    body cannot be edited freely.
                  </p>
                </div>

                {!managedSmsHeader ? (
                  <div className="rounded-xl border border-amber-200 bg-amber-50 p-4 text-xs leading-5 text-amber-800">
                    This sender is not linked to a ready managed Header. Refresh Managed SMS setup
                    from Company Connections.
                  </div>
                ) : (
                  <>
                    <label className="text-xs font-semibold text-slate-700">
                      Managed SMS template
                      <select
                        className="form-input mt-1"
                        value={managedSmsTemplateId}
                        disabled={managedSmsLoading}
                        onChange={(event) => {
                          const nextId = event.target.value
                          setManagedSmsTemplateId(nextId)
                          setManagedSmsPreview(null)
                          const template = managedSmsTemplates.find((item) => item.id === nextId)
                          const nextVariables: Record<string, string> = {}
                          for (const variable of template?.activeVersion?.variables ?? []) {
                            const current = managedSmsTemplateVariables[variable.key]
                            if (current) nextVariables[variable.key] = current
                          }
                          setManagedSmsTemplateVariables(nextVariables)
                        }}
                      >
                        <option value="">
                          {managedSmsLoading ? 'Loading templates…' : 'Choose ready template'}
                        </option>
                        {availableManagedSmsTemplates.map((template) => (
                          <option key={template.id} value={template.id}>
                            {template.name} · {template.messageCategory.toLowerCase()}
                          </option>
                        ))}
                      </select>
                    </label>

                    {!managedSmsLoading && availableManagedSmsTemplates.length === 0 && (
                      <div className="rounded-xl border border-dashed border-slate-300 bg-slate-50 p-3 text-[11px] leading-5 text-slate-500">
                        No READY managed templates are approved for Header{' '}
                        <strong>{managedSmsHeader.header}</strong>. Finish template verification in
                        Company Connections before sending.
                      </div>
                    )}

                    {selectedManagedSmsTemplate?.activeVersion && (
                      <div className="rounded-xl border border-slate-200 bg-white p-4">
                        <p className="text-xs font-bold text-slate-900">Template variables</p>
                        <p className="mt-1 text-[11px] leading-5 text-slate-500">
                          Enter only the declared values. Fixed template text stays locked.
                        </p>
                        {selectedManagedSmsTemplate.activeVersion.variables.length === 0 ? (
                          <p className="mt-3 text-[11px] text-slate-400">
                            This approved template has no variables.
                          </p>
                        ) : (
                          <div className="mt-4 grid gap-3 sm:grid-cols-2">
                            {selectedManagedSmsTemplate.activeVersion.variables.map((variable) => (
                              <label
                                key={variable.key}
                                className="text-[11px] font-semibold text-slate-700"
                              >
                                {variable.key}
                                <input
                                  className="form-input mt-1.5"
                                  value={managedSmsTemplateVariables[variable.key] ?? ''}
                                  maxLength={variable.maxLength}
                                  onChange={(event) =>
                                    setManagedSmsTemplateVariables((current) => ({
                                      ...current,
                                      [variable.key]: event.target.value,
                                    }))
                                  }
                                  placeholder={`Up to ${variable.maxLength} characters`}
                                />
                                {managedSmsVariableValidation[variable.key] && (
                                  <span className="mt-1 block text-[10px] font-medium text-rose-600">
                                    {managedSmsVariableValidation[variable.key]}
                                  </span>
                                )}
                              </label>
                            ))}
                          </div>
                        )}
                      </div>
                    )}

                    {selectedManagedSmsTemplate && (
                      <div className="rounded-xl border border-slate-200 bg-slate-50 p-4">
                        <p className="text-[10px] font-bold uppercase tracking-wide text-slate-400">
                          Message preview
                        </p>
                        <p className="mt-2 whitespace-pre-wrap text-sm leading-6 text-slate-700">
                          {managedSmsPreview?.renderedBody ||
                            managedSmsPreview?.bodyText ||
                            selectedManagedSmsTemplate.activeVersion?.bodyText ||
                            'Enter the required variables to preview this message.'}
                        </p>
                        {(managedSmsPreview?.validationErrors.length ?? 0) > 0 && (
                          <div className="mt-3 rounded-lg bg-rose-50 px-3 py-2 text-[11px] text-rose-700">
                            {managedSmsPreview?.validationErrors
                              .map((item) => item.message)
                              .join(' · ')}
                          </div>
                        )}
                      </div>
                    )}
                  </>
                )}
              </div>
            ) : (
              <label className="text-xs font-semibold text-slate-700">
                Message
                <textarea className="form-input mt-1 min-h-36" value={body} onChange={(event) => setBody(event.target.value)} maxLength={500000} />
              </label>
            )}
            <label className="text-xs font-semibold text-slate-700">
              Schedule (optional)
              <input type="datetime-local" className="form-input mt-1" value={scheduledAt} onChange={(event) => setScheduledAt(event.target.value)} />
              <span className="mt-1 block font-normal text-slate-500">Timezone: {Intl.DateTimeFormat().resolvedOptions().timeZone}</span>
            </label>
          </div>

          <div className="mt-4 flex flex-wrap items-center justify-between gap-3">
            <p className="text-xs leading-5 text-slate-500">
              Accepted means the provider accepted the request. Delivered/Read are shown only when supported by provider events.
            </p>
            <button type="button" className="primary-button" disabled={!canLaunch} onClick={() => setLaunchConfirmOpen(true)}>
              {busy ? 'Starting…' : scheduledAt ? 'Schedule Broadcast' : 'Send Broadcast'}
            </button>
          </div>
        </section>
      </div>
      <ConfirmDialog
        open={launchConfirmOpen}
        title={scheduledAt ? 'Schedule this broadcast?' : 'Send this broadcast now?'}
        description={
          currentPreview
            ? `${scheduledAt ? 'Schedule' : 'Send'} to ${currentPreview.eligibleCount.toLocaleString()} eligible recipients? The previewed audience will be frozen for this broadcast, while consent and suppression are rechecked at send time.`
            : ''
        }
        confirmLabel={scheduledAt ? 'Yes, schedule broadcast' : 'Yes, send broadcast'}
        cancelLabel="No, review again"
        tone="warning"
        working={busy}
        onCancel={() => setLaunchConfirmOpen(false)}
        onConfirm={() => void launch()}
      />
    </div>
  )
}
