import { useSnackbarState } from '../feedback/useSnackbarState'
import { useEffect, useMemo, useRef, useState } from 'react'
import { Link, useSearchParams } from 'react-router'
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
import { emailTemplateApi } from '../../features/emailTemplate/emailTemplateApi'
import type {
  EmailTemplateProviderBinding,
  EmailTemplateSummary,
} from '../../features/emailTemplate/types'
import { PERMISSIONS } from '../../features/iam/permissions'
import { useIam } from '../../features/iam/useIam'
import type { ContactSummary, ListSummary, SegmentDefinition } from '../../features/contacts/types'
import type { ChannelType } from '../../features/channel/types'
import { whatsappApi } from '../../features/whatsapp/whatsappApi'
import type { WhatsAppTemplate, WhatsAppTemplateParameters } from '../../features/whatsapp/types'
import { apiErrorMessage } from '../../lib/api/apiClient'
import { useAppSelector } from '../../store/hooks'
import { selectSelectedCompanyId, selectWorkspaceEpoch } from '../../store/workspace/workspaceSlice'
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
  DUPLICATE: 'Duplicate contact method',
  MISSING_IDENTITY: 'Missing email or phone',
  INVALID_IDENTITY: 'Invalid email or phone',
  OPTED_OUT: 'Opted out',
  CONSENT_REQUIRED: 'Permission not recorded',
  SUPPRESSED: 'Delivery blocked',
  CHANNEL_INELIGIBLE: 'Channel unavailable',
}

const channelLabel = (channel: ChannelType) => (channel === 'WHATSAPP' ? 'WhatsApp' : channel)

const automaticEmailVariables = new Set([
  'destination',
  'email',
  'phone',
  'mobile',
  'current_date',
  'current_year',
])

const recipientNameVariables = new Set([
  'name',
  'display_name',
  'full_name',
  'first_name',
  'last_name',
])

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

const sourceLabel = (
  selector: AudienceSelector | null,
  lists: ListSummary[],
  segments: SegmentDefinition[],
) => {
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
  emailTemplateId,
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
  emailTemplateId?: string | null
  managedSmsTemplateId?: string | null
  templateVariables?: Record<string, string>
}): CreateAudienceBroadcastMessageInput => {
  const usesManagedSms = channel === 'SMS' && Boolean(managedSmsTemplateId)
  const usesEmailTemplate = channel === 'EMAIL' && Boolean(emailTemplateId)
  return {
    ...source,
    channelType: channel,
    senderId,
    subject: channel === 'EMAIL' && !usesEmailTemplate ? subject.trim() || null : null,
    bodyText:
      channel === 'WHATSAPP' || usesManagedSms || usesEmailTemplate ? null : body.trim() || null,
    templateId: usesEmailTemplate ? emailTemplateId : null,
    managedSmsTemplateId: usesManagedSms ? managedSmsTemplateId : null,
    templateVariables: usesManagedSms || usesEmailTemplate ? templateVariables : undefined,
    whatsappTemplateId: channel === 'WHATSAPP' ? whatsappTemplateId || null : null,
    whatsappTemplateParameters: channel === 'WHATSAPP' ? whatsappTemplateParameters : null,
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
  const [contactSearch, setContactSearch] = useState('')
  const [contactSearchLoading, setContactSearchLoading] = useState(false)
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
  const [emailTemplates, setEmailTemplates] = useState<EmailTemplateSummary[]>([])
  const [emailTemplateId, setEmailTemplateId] = useState('')
  const [emailTemplateVariables, setEmailTemplateVariables] = useState<Record<string, string>>({})
  const [emailProviderBindings, setEmailProviderBindings] = useState<
    EmailTemplateProviderBinding[]
  >([])
  const [emailTemplatesLoading, setEmailTemplatesLoading] = useState(false)
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
  const savedDraft = useRef<{ key: string; id: string } | null>(null)
  const [launchConfirmOpen, setLaunchConfirmOpen] = useState(false)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useSnackbarState('', 'error')

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
  const usesMsg91Email = channel === 'EMAIL' && selectedBroadcastSender?.provider === 'MSG91_EMAIL'
  const emailBindingByVersion = useMemo(
    () =>
      new Map(
        emailProviderBindings.map((binding) => [
          `${binding.templateId}:${binding.templateVersion}`,
          binding,
        ]),
      ),
    [emailProviderBindings],
  )
  const availableEmailTemplates = useMemo(
    () =>
      emailTemplates.filter((template) => {
        if (!usesMsg91Email) return true
        if (template.purpose !== 'TRANSACTIONAL' || template.publishedVersion == null) return false
        return (
          emailBindingByVersion.get(`${template.id}:${template.publishedVersion}`)?.sendable ===
          true
        )
      }),
    [emailBindingByVersion, emailTemplates, usesMsg91Email],
  )
  const selectedEmailTemplate = emailTemplates.find((item) => item.id === emailTemplateId) ?? null
  const whatsappParametersReady = [
    ...whatsappParameters.header,
    ...whatsappParameters.body,
    ...whatsappParameters.buttons,
  ].every((value) => value.trim().length > 0)
  const usesManagedSms = channel === 'SMS' && isManagedSmsSender(selectedBroadcastSender)
  const managedSmsHeader = usesManagedSms
    ? (managedSmsHeaders.find((item) => item.header === selectedBroadcastSender?.fromAddress) ??
      null)
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
        ? emailTemplateId
          ? Boolean(
              selectedEmailTemplate &&
              availableEmailTemplates.some((template) => template.id === emailTemplateId),
            )
          : !usesMsg91Email && Boolean(subject.trim() && body.trim())
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
    if (!companyId || sourceType !== 'CONTACTS') return
    const token = captureScope()
    const timer = window.setTimeout(
      () => {
        setContactSearchLoading(true)
        contactsApi
          .list(companyId, {
            q: contactSearch.trim().length >= 2 ? contactSearch.trim() : undefined,
            status: 'ACTIVE',
            size: 30,
            sort: 'UPDATED_DESC',
          })
          .then((page) => {
            if (scopeIsCurrent(token)) setContacts(page.items)
          })
          .catch((requestError) => {
            if (scopeIsCurrent(token)) {
              setError(apiErrorMessage(requestError, 'Unable to search Contacts.'))
            }
          })
          .finally(() => {
            if (scopeIsCurrent(token)) setContactSearchLoading(false)
          })
      },
      contactSearch.trim().length >= 2 ? 250 : 0,
    )

    return () => window.clearTimeout(timer)
  }, [captureScope, companyId, contactSearch, epoch, scopeIsCurrent, sourceType])

  useEffect(() => {
    setEmailTemplates([])
    setEmailProviderBindings([])
    setEmailTemplateId('')
    setEmailTemplateVariables({})
    if (!companyId || channel !== 'EMAIL') {
      setEmailTemplatesLoading(false)
      return
    }

    let active = true
    const token = captureScope()
    setEmailTemplatesLoading(true)

    void (async () => {
      try {
        const templates = await emailTemplateApi.published(companyId)
        if (!active || !scopeIsCurrent(token)) return
        setEmailTemplates(templates)

        if (
          selectedBroadcastSender?.provider === 'MSG91_EMAIL' &&
          selectedBroadcastSender.connectionId
        ) {
          const bindings = await emailTemplateApi.providerBindings(companyId, {
            connectionId: selectedBroadcastSender.connectionId,
          })
          if (!active || !scopeIsCurrent(token)) return

          const refreshable = bindings.filter((binding) =>
            ['SYNCING', 'SYNCED', 'PENDING_REVIEW'].includes(binding.status),
          )
          if (refreshable.length === 0) {
            setEmailProviderBindings(bindings)
            return
          }

          const refreshed = await Promise.allSettled(
            refreshable.map((binding) =>
              emailTemplateApi.refreshProviderBinding(companyId, binding.id),
            ),
          )
          if (!active || !scopeIsCurrent(token)) return
          const updates = new Map(
            refreshed
              .filter(
                (result): result is PromiseFulfilledResult<EmailTemplateProviderBinding> =>
                  result.status === 'fulfilled',
              )
              .map((result) => [result.value.id, result.value]),
          )
          setEmailProviderBindings(bindings.map((binding) => updates.get(binding.id) ?? binding))
        }
      } catch (requestError) {
        if (active && scopeIsCurrent(token)) {
          setError(apiErrorMessage(requestError, 'Unable to load published Email templates.'))
        }
      } finally {
        if (active && scopeIsCurrent(token)) setEmailTemplatesLoading(false)
      }
    })()

    return () => {
      active = false
    }
  }, [
    captureScope,
    channel,
    companyId,
    epoch,
    scopeIsCurrent,
    selectedBroadcastSender?.connectionId,
    selectedBroadcastSender?.provider,
  ])

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
    if (!companyId || !usesManagedSms || !managedSmsHeader || !managedSmsTemplateId) {
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
            managedSmsErrorMessage(requestError, 'Unable to preview this Managed SMS template.'),
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
    setSearchParams(
      (current) => {
        const next = new URLSearchParams(current)
        next.set('audienceType', sourceType)
        next.set('audienceId', sourceId)
        return next
      },
      { replace: true },
    )
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

      const payload = buildCanonicalBroadcastPayload({
        source,
        channel,
        senderId,
        subject,
        body,
        whatsappTemplateId: templateId,
        whatsappTemplateParameters: whatsappParameters,
        emailTemplateId: channel === 'EMAIL' ? emailTemplateId : null,
        managedSmsTemplateId: usesManagedSms ? managedSmsTemplateId : null,
        templateVariables:
          channel === 'EMAIL' && emailTemplateId
            ? emailTemplateVariables
            : usesManagedSms
              ? managedSmsTemplateVariables
              : undefined,
      })
      const draftKey = JSON.stringify({ companyId, epoch, journeyScopeKey, payload })
      const draft =
        savedDraft.current?.key === draftKey
          ? { id: savedDraft.current.id }
          : await broadcastApi.createAudienceMessage(companyId, payload)
      if (!scopeIsCurrent(token)) return
      savedDraft.current = { key: draftKey, id: draft.id }

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
        launched = await broadcastApi.send(companyId, draft.id, confirmedPreview.sourceDescriptor)
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
      <div className="grid w-full gap-4">
        <section className="panel p-4 sm:p-5">
          <div className="flex flex-wrap items-start justify-between gap-3">
            <div>
              <p className="eyebrow">AUDIENCE</p>
              <h1 className="mt-2 text-xl font-bold text-slate-950">
                Create {channelLabel(channel)} Broadcast
              </h1>
              <p className="mt-1 text-sm text-slate-500">
                Choose who should receive the message, confirm sending eligibility, then review and
                launch.
              </p>
            </div>
            <span className="rounded-full bg-indigo-50 px-3 py-1 text-xs font-semibold text-indigo-700">
              {sourceLabel(selector, currentLists, currentSegments)}
            </span>
          </div>



          <div className="mt-5 grid gap-4 lg:grid-cols-2">
            <div>
              <p className="text-xs font-bold uppercase tracking-wide text-slate-500">
                1. Audience source
              </p>
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
                  <option value="">
                    Choose {sourceType === 'CONTACT_LIST' ? 'a List' : 'a Segment'}
                  </option>
                  {(sourceType === 'CONTACT_LIST' ? currentLists : currentSegments).map((item) => (
                    <option key={item.id} value={item.id}>
                      {item.name} (
                      {(sourceType === 'CONTACT_LIST'
                        ? (item as ListSummary).memberCount
                        : ((item as SegmentDefinition).count ?? 0)
                      ).toLocaleString()}
                      )
                    </option>
                  ))}
                </select>
              ) : (
                <div className="mt-3">
                  <label className="block text-xs font-semibold text-slate-700">
                    Search contacts
                    <input
                      className="form-input mt-1"
                      type="search"
                      value={contactSearch}
                      onChange={(event) => setContactSearch(event.target.value)}
                      placeholder="Name, email, phone or company"
                    />
                  </label>
                  <div className="mt-2 max-h-64 overflow-y-auto rounded-xl border border-slate-200">
                    {contactSearchLoading ? (
                      <p className="px-3 py-5 text-center text-xs text-slate-500">
                        Searching contacts…
                      </p>
                    ) : currentContacts.length === 0 ? (
                      <p className="px-3 py-5 text-center text-xs text-slate-500">
                        No contacts found.
                      </p>
                    ) : (
                      currentContacts.map((contact) => (
                        <label
                          key={contact.id}
                          className="flex cursor-pointer items-center gap-3 border-b border-slate-100 px-3 py-2 last:border-b-0"
                        >
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
                            <span className="block truncate text-sm font-semibold text-slate-800">
                              {contact.displayName}
                            </span>
                            <span className="block truncate text-xs text-slate-500">
                              {contact.primaryEmail ||
                                contact.primaryPhone ||
                                'No primary identity'}
                            </span>
                          </span>
                        </label>
                      ))
                    )}
                  </div>
                  <p className="mt-2 text-[11px] text-slate-500">
                    {contactIds.size > 0 ? `${contactIds.size} selected. ` : ''}
                    Select up to 200 contacts directly. For larger audiences, use a List or Segment.
                  </p>
                </div>
              )}
            </div>

            <div>
              <p className="text-xs font-bold uppercase tracking-wide text-slate-500">2. Sender</p>
              <div className="mt-2 rounded-xl border border-slate-200 p-3">
                <p className="text-sm font-semibold text-slate-900">
                  {readySenders.length > 0
                    ? 'Ready to send'
                    : loading
                      ? 'Checking sender…'
                      : 'Sender setup needed'}
                </p>
                <p className="mt-1 text-xs text-slate-500">
                  {currentReadiness?.nextAction ||
                    (readySenders.length
                      ? 'Choose a ready sender.'
                      : 'No ready sender is available.')}
                </p>
                <select
                  className="form-input mt-3"
                  value={senderId}
                  onChange={(event) => setSenderId(event.target.value)}
                  aria-label="Ready sender"
                >
                  <option value="">Choose sender</option>
                  {currentReadiness?.senders.map((sender) => (
                    <option
                      key={sender.senderId}
                      value={sender.senderId}
                      disabled={sender.state !== 'READY'}
                    >
                      {sender.label} — {sender.state.replaceAll('_', ' ')}
                    </option>
                  ))}
                </select>
                {selectedReadiness && (
                  <p className="mt-2 text-[11px] leading-5 text-slate-500">
                    Tracking:{' '}
                    {selectedReadiness.capabilities.providerAcceptance ? 'accepted' : 'basic'}
                    {selectedReadiness.capabilities.deliveryEvents ? ' · delivered' : ''}
                    {selectedReadiness.capabilities.readEvents ? ' · read' : ''}
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
            {previewing ? 'Checking recipients…' : 'Check recipients'}
          </button>
        </section>

        {currentPreview && (
          <section className="panel p-4 sm:p-5" aria-label="Recipient eligibility preview">
            <div className="grid gap-3 sm:grid-cols-3">
              <div className="rounded-xl bg-slate-50 p-3">
                <p className="text-xs text-slate-500">Selected</p>
                <strong className="text-xl">{currentPreview.selectedCount.toLocaleString()}</strong>
              </div>
              <div className="rounded-xl bg-emerald-50 p-3">
                <p className="text-xs text-emerald-700">Eligible</p>
                <strong className="text-xl text-emerald-800">
                  {currentPreview.eligibleCount.toLocaleString()}
                </strong>
              </div>
              <div className="rounded-xl bg-amber-50 p-3">
                <p className="text-xs text-amber-700">Excluded</p>
                <strong className="text-xl text-amber-800">
                  {currentPreview.excludedCount.toLocaleString()}
                </strong>
              </div>
            </div>
            <div className="mt-4 grid gap-2 sm:grid-cols-2 lg:grid-cols-3">
              {Object.entries(currentPreview.exclusions)
                .filter(([, count]) => Number(count) > 0)
                .map(([reason, count]) => (
                  <div
                    key={reason}
                    className="rounded-lg border border-slate-200 px-3 py-2 text-xs"
                  >
                    <span className="font-semibold text-slate-800">
                      {exclusionLabels[reason as RecipientExclusionReason]}
                    </span>
                    <span className="float-right text-slate-500">
                      {Number(count).toLocaleString()}
                    </span>
                  </div>
                ))}
            </div>
            {currentPreview.selectedCount !==
              currentPreview.eligibleCount + currentPreview.excludedCount && (
              <p className="mt-3 text-xs font-semibold text-rose-700">
                Preview counts are inconsistent. Refresh before sending.
              </p>
            )}
            {currentPreview.eligibleCount === 0 && (
              <p className="mt-3 text-sm font-semibold text-amber-700">
                No eligible recipients. Resolve exclusions before sending.
              </p>
            )}
          </section>
        )}

        <section className="panel p-4 sm:p-5">
          <p className="text-xs font-bold uppercase tracking-wide text-slate-500">
            3. Message & send
          </p>
          <div className="mt-3 grid gap-3">
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
                    Choose an approved WhatsApp template for this broadcast.
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
                          {section === 'body' ? 'Body' : section === 'header' ? 'Header' : 'Button'}{' '}
                          variable {index + 1}
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
            ) : channel === 'EMAIL' ? (
              <div className="grid gap-3">
                <label className="text-xs font-semibold text-slate-700">
                  Start with
                  <select
                    className="form-input mt-1"
                    value={emailTemplateId}
                    disabled={emailTemplatesLoading}
                    onChange={(event) => {
                      const nextId = event.target.value
                      setEmailTemplateId(nextId)
                      setEmailTemplateVariables({})
                      if (!nextId) return
                      const template = emailTemplates.find((item) => item.id === nextId)
                      if (template?.subject) setSubject(template.subject)
                    }}
                  >
                    <option value="">
                      {emailTemplatesLoading
                        ? 'Loading templates…'
                        : usesMsg91Email
                          ? 'Choose approved transactional template'
                          : 'Write from scratch'}
                    </option>
                    {availableEmailTemplates.map((template) => (
                      <option key={template.id} value={template.id}>
                        {template.name} · {template.category.toLowerCase().replaceAll('_', ' ')}
                      </option>
                    ))}
                  </select>
                </label>

                {availableEmailTemplates.length === 0 && !emailTemplatesLoading && (
                  <div className="rounded-xl border border-dashed border-slate-300 bg-slate-50 p-3 text-xs text-slate-500">
                    {usesMsg91Email
                      ? 'No approved transactional templates are ready for this MSG91 Email sender.'
                      : 'No published email templates yet.'}
                    <Link
                      to={
                        usesMsg91Email
                          ? '/templates?create=transactional'
                          : '/templates?create=marketing'
                      }
                      className="mt-2 inline-flex font-semibold text-indigo-600"
                    >
                      Create an email template
                    </Link>
                  </div>
                )}

                {emailTemplateId && selectedEmailTemplate ? (
                  <div className="rounded-xl border border-indigo-100 bg-indigo-50/50 p-4">
                    <p className="text-sm font-bold text-slate-900">{selectedEmailTemplate.name}</p>
                    <p className="mt-1 text-xs text-slate-600">
                      Published template content is locked for this broadcast.
                      {selectedEmailTemplate.subject
                        ? ` Subject: ${selectedEmailTemplate.subject}`
                        : ''}
                    </p>
                    {selectedEmailTemplate.variables.length > 0 && (
                      <div className="mt-4 grid gap-3 sm:grid-cols-2">
                        {selectedEmailTemplate.variables.map((variable) => {
                          if (automaticEmailVariables.has(variable)) {
                            return (
                              <div
                                key={variable}
                                className="rounded-lg border border-emerald-100 bg-emerald-50/60 px-3 py-2"
                              >
                                <p className="text-xs font-semibold text-emerald-800">
                                  {'{{'}
                                  {variable}
                                  {'}}'}
                                </p>
                                <p className="mt-0.5 text-[10px] text-emerald-600">
                                  Filled automatically for each recipient
                                </p>
                              </div>
                            )
                          }
                          return (
                            <label key={variable} className="text-xs font-semibold text-slate-700">
                              {'{{'}
                              {variable}
                              {'}}'}
                              <input
                                className="form-input mt-1"
                                value={emailTemplateVariables[variable] ?? ''}
                                maxLength={10000}
                                onChange={(event) =>
                                  setEmailTemplateVariables((current) => ({
                                    ...current,
                                    [variable]: event.target.value,
                                  }))
                                }
                                placeholder={
                                  recipientNameVariables.has(variable)
                                    ? 'Optional fallback if the contact name is missing'
                                    : 'Fallback value'
                                }
                              />
                            </label>
                          )
                        })}
                      </div>
                    )}
                  </div>
                ) : usesMsg91Email ? (
                  <div className="rounded-xl border border-amber-200 bg-amber-50 p-4 text-xs leading-5 text-amber-800">
                    MSG91 Email sends published transactional templates only. Choose a ready
                    template above before continuing.
                  </div>
                ) : (
                  <>
                    <label className="text-xs font-semibold text-slate-700">
                      Subject
                      <input
                        className="form-input mt-1"
                        value={subject}
                        onChange={(event) => setSubject(event.target.value)}
                        maxLength={998}
                      />
                    </label>
                    <label className="text-xs font-semibold text-slate-700">
                      Message
                      <textarea
                        className="form-input mt-1 min-h-36"
                        value={body}
                        onChange={(event) => setBody(event.target.value)}
                        maxLength={500000}
                      />
                    </label>
                  </>
                )}
              </div>
            ) : (
              <label className="text-xs font-semibold text-slate-700">
                Message
                <textarea
                  className="form-input mt-1 min-h-36"
                  value={body}
                  onChange={(event) => setBody(event.target.value)}
                  maxLength={500000}
                />
              </label>
            )}
            <label className="text-xs font-semibold text-slate-700">
              Schedule (optional)
              <input
                type="datetime-local"
                className="form-input mt-1"
                value={scheduledAt}
                onChange={(event) => setScheduledAt(event.target.value)}
              />
              <span className="mt-1 block font-normal text-slate-500">
                Timezone: {Intl.DateTimeFormat().resolvedOptions().timeZone}
              </span>
            </label>
          </div>

          <div className="mt-4 flex flex-wrap items-center justify-between gap-3">
            <p className="text-xs leading-5 text-slate-500">
              Final delivery and read tracking appears when the selected provider supports those
              events.
            </p>
            <button
              type="button"
              className="primary-button"
              disabled={!canLaunch}
              onClick={() => setLaunchConfirmOpen(true)}
            >
              {busy
                ? 'Starting…'
                : error && currentPreview && savedDraft.current
                  ? 'Retry Broadcast'
                  : scheduledAt
                    ? 'Schedule Broadcast'
                    : 'Send Broadcast'}
            </button>
          </div>
        </section>
      </div>
      <ConfirmDialog
        open={launchConfirmOpen}
        title={scheduledAt ? 'Schedule this broadcast?' : 'Send this broadcast now?'}
        description={
          currentPreview
            ? `${scheduledAt ? 'Schedule' : 'Send'} to ${currentPreview.eligibleCount.toLocaleString()} eligible recipients? UnifyDesk will use the audience you just reviewed and recheck permission and delivery restrictions before sending.`
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
