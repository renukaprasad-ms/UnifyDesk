import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'
import {
  FiAlertCircle,
  FiArrowRight,
  FiBarChart2,
  FiCheckCircle,
  FiClock,
  FiInbox,
  FiLink,
  FiMail,
  FiMessageCircle,
  FiRefreshCw,
  FiSend,
  FiSmartphone,
  FiUpload,
  FiUsers,
} from 'react-icons/fi'
import { Link } from 'react-router'
import { adCenterApi } from '../../features/ads/adCenterApi'
import type { AdAccount, AdCampaign, AdProviderConnection } from '../../features/ads/types'
import { broadcastApi } from '../../features/broadcast/broadcastApi'
import type {
  BroadcastMessageSummary,
  Page as BroadcastPage,
} from '../../features/broadcast/types'
import { channelApi } from '../../features/channel/channelApi'
import type { ChannelConnection, ChannelType } from '../../features/channel/types'
import { contactsApi } from '../../features/contacts/contactsApi'
import type {
  ContactDashboardSummary,
  ContactSummary,
  ListSummary,
  SegmentDefinition,
} from '../../features/contacts/types'
import { PERMISSIONS } from '../../features/iam/permissions'
import { useIam } from '../../features/iam/useIam'
import { inboxApi } from '../../features/inbox/inboxApi'
import type { InboxConversation, InboxPage } from '../../features/inbox/types'
import { managedEmailApi } from '../../features/managedEmail/managedEmailApi'
import type {
  ManagedEmailDeliverability,
  ManagedEmailOverview,
  ManagedEmailUsage,
} from '../../features/managedEmail/types'
import { managedSmsApi } from '../../features/managedSms/managedSmsApi'
import type {
  ManagedSmsDeliveryHealth,
  ManagedSmsStatus,
  ManagedSmsUsage,
} from '../../features/managedSms/types'
import { useWorkspaceEpochScope } from '../../features/workspace/useWorkspaceEpochScope'
import { selectAuthUser } from '../../store/auth/authSlice'
import { useAppSelector } from '../../store/hooks'
import {
  selectSelectedCompany,
  selectSelectedCompanyId,
  selectWorkspaceEpoch,
} from '../../store/workspace/workspaceSlice'
import { CardSkeleton, ListSkeleton } from '../../components/ui/Skeleton'

type ManagedEmailDashboard = {
  overview: ManagedEmailOverview | null
  usage: ManagedEmailUsage | null
  deliverability: ManagedEmailDeliverability | null
}

type ManagedSmsDashboard = {
  status: ManagedSmsStatus | null
  usage: ManagedSmsUsage | null
  health: ManagedSmsDeliveryHealth | null
}

type AdsDashboard = {
  connections: AdProviderConnection[]
  accounts: AdAccount[]
  campaigns: AdCampaign[]
}

type AttentionItem = {
  id: string
  title: string
  detail: string
  to: string
  tone: 'danger' | 'warning' | 'info'
}

type ActivityItem = {
  id: string
  title: string
  detail: string
  at: string
  to: string
  icon: ReactNode
}

const number = new Intl.NumberFormat()

const safe = async <T,>(work: () => Promise<T>): Promise<T | null> => {
  try {
    return await work()
  } catch {
    return null
  }
}

const channelLabel = (channel?: ChannelType | null) =>
  channel === 'WHATSAPP' ? 'WhatsApp' : channel === 'SMS' ? 'SMS' : 'Email'

const broadcastTitle = (message: BroadcastMessageSummary) =>
  message.subject?.trim() ||
  message.bodyPreview?.trim()?.slice(0, 70) ||
  `${channelLabel(message.channelType)} broadcast`

const statusLabel = (value: string) =>
  value
    .replaceAll('_', ' ')
    .toLowerCase()
    .replace(/(^|\s)\S/g, (letter) => letter.toUpperCase())

const formatDateTime = (value?: string | null) => {
  if (!value) return '—'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '—'
  return new Intl.DateTimeFormat(undefined, {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(date)
}

const formatRelativeDate = (value?: string | null) => {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ''
  const diffMs = Date.now() - date.getTime()
  const minutes = Math.max(0, Math.round(diffMs / 60000))
  if (minutes < 60) return minutes <= 1 ? 'Just now' : `${minutes}m ago`
  const hours = Math.round(minutes / 60)
  if (hours < 24) return `${hours}h ago`
  const days = Math.round(hours / 24)
  if (days < 7) return `${days}d ago`
  return new Intl.DateTimeFormat(undefined, { month: 'short', day: 'numeric' }).format(date)
}

const broadcastStatusClass = (status: BroadcastMessageSummary['status']) => {
  if (status === 'COMPLETED') return 'bg-emerald-50 text-emerald-700'
  if (status === 'FAILED' || status === 'PARTIALLY_FAILED') return 'bg-rose-50 text-rose-700'
  if (status === 'RUNNING' || status === 'QUEUED') return 'bg-indigo-50 text-indigo-700'
  if (status === 'SCHEDULED') return 'bg-sky-50 text-sky-700'
  return 'bg-slate-100 text-slate-600'
}

const attentionClass = {
  danger: 'border-rose-200 bg-rose-50 text-rose-800',
  warning: 'border-amber-200 bg-amber-50 text-amber-800',
  info: 'border-sky-200 bg-sky-50 text-sky-800',
} as const

const connectionStatus = (
  connections: ChannelConnection[],
  channel: ChannelType,
  managedReady = false,
) => {
  const channelConnections = connections.filter((item) => item.channelType === channel)
  const active = channelConnections.filter((item) => item.status === 'ACTIVE').length
  const hasProblem = channelConnections.some((item) => item.status === 'ERROR')
  return {
    active,
    ready: managedReady || active > 0,
    hasProblem,
  }
}

const buildGrowthSeries = (summary: ContactDashboardSummary | null) => {
  const byDate = new Map((summary?.growth ?? []).map((point) => [point.date, point.added]))
  return Array.from({ length: 30 }, (_, index) => {
    const date = new Date()
    date.setUTCHours(0, 0, 0, 0)
    date.setUTCDate(date.getUTCDate() - (29 - index))
    const key = date.toISOString().slice(0, 10)
    return { date: key, added: byDate.get(key) ?? 0 }
  })
}

function SectionHeading({
  eyebrow,
  title,
  description,
  action,
}: {
  eyebrow?: string
  title: string
  description?: string
  action?: ReactNode
}) {
  return (
    <div className="flex flex-wrap items-end justify-between gap-3">
      <div>
        {eyebrow && <p className="eyebrow">{eyebrow}</p>}
        <h2 className={eyebrow ? 'mt-2 text-lg font-bold text-slate-950' : 'text-lg font-bold text-slate-950'}>
          {title}
        </h2>
        {description && <p className="mt-1 text-sm text-slate-500">{description}</p>}
      </div>
      {action}
    </div>
  )
}

export default function Home() {
  const user = useAppSelector(selectAuthUser)
  const selectedCompany = useAppSelector(selectSelectedCompany)
  const companyId = useAppSelector(selectSelectedCompanyId)
  const workspaceEpoch = useAppSelector(selectWorkspaceEpoch)
  const { can } = useIam()
  const { capture: captureScope, isCurrent: scopeIsCurrent } = useWorkspaceEpochScope(
    companyId,
    workspaceEpoch,
  )

  const canReadContacts = can(PERMISSIONS.CONTACT_READ)
  const canImportContacts = can(PERMISSIONS.CONTACT_IMPORT)
  const canReadBroadcasts = can(PERMISSIONS.BROADCAST_READ)
  const canCreateBroadcasts = can(PERMISSIONS.BROADCAST_CREATE)
  const canReadEmail = can(PERMISSIONS.EMAIL_READ)
  const canReadWhatsApp = can(PERMISSIONS.WHATSAPP_READ)
  const canReadSms = can(PERMISSIONS.SMS_READ)
  const canReadAds = can(PERMISSIONS.ADS_READ)
  const canReadInbox = canReadEmail || canReadWhatsApp
  const canReadChannels = canReadEmail || canReadWhatsApp || canReadSms || canReadBroadcasts

  const [loading, setLoading] = useState(true)
  const [refreshing, setRefreshing] = useState(false)
  const [contactSummary, setContactSummary] = useState<ContactDashboardSummary | null>(null)
  const [recentContacts, setRecentContacts] = useState<ContactSummary[]>([])
  const [lists, setLists] = useState<ListSummary[]>([])
  const [segments, setSegments] = useState<SegmentDefinition[]>([])
  const [broadcasts, setBroadcasts] = useState<BroadcastMessageSummary[]>([])
  const [broadcastTotal, setBroadcastTotal] = useState(0)
  const [inboxPage, setInboxPage] = useState<InboxPage<InboxConversation> | null>(null)
  const [connections, setConnections] = useState<ChannelConnection[]>([])
  const [managedEmail, setManagedEmail] = useState<ManagedEmailDashboard>({
    overview: null,
    usage: null,
    deliverability: null,
  })
  const [managedSms, setManagedSms] = useState<ManagedSmsDashboard>({
    status: null,
    usage: null,
    health: null,
  })
  const [ads, setAds] = useState<AdsDashboard>({
    connections: [],
    accounts: [],
    campaigns: [],
  })

  const loadDashboard = useCallback(
    async (quiet = false) => {
      if (!companyId) return
      const scope = captureScope()
      if (!scopeIsCurrent(scope)) return

      if (quiet) setRefreshing(true)
      else setLoading(true)

      const contactBundlePromise = canReadContacts
        ? Promise.all([
            safe(() => contactsApi.dashboardSummary(companyId)),
            safe(() =>
              contactsApi.list(companyId, {
                status: 'ACTIVE',
                sort: 'CREATED_DESC',
                size: 5,
              }),
            ),
            safe(() => contactsApi.lists(companyId)),
            safe(() => contactsApi.segments(companyId)),
          ]).then(([summary, recent, nextLists, nextSegments]) => ({
            summary,
            recent: recent?.items ?? [],
            lists: nextLists ?? [],
            segments: nextSegments ?? [],
          }))
        : Promise.resolve(null)

      const broadcastBundlePromise = canReadBroadcasts
        ? Promise.all(
            (['EMAIL', 'SMS', 'WHATSAPP'] as ChannelType[]).map((channel) =>
              safe(() => broadcastApi.channelHistory(companyId, channel, 0, 5)),
            ),
          ).then((historyPages) => {
            const pages = historyPages.filter(
              (page): page is BroadcastPage<BroadcastMessageSummary> => page !== null,
            )
            return {
              total: pages.reduce((sum, page) => sum + page.totalElements, 0),
              recent: pages
                .flatMap((page) => page.content)
                .sort(
                  (left, right) =>
                    new Date(right.createdAt).getTime() - new Date(left.createdAt).getTime(),
                )
                .slice(0, 5),
            }
          })
        : Promise.resolve(null)

      const inboxPromise = canReadInbox
        ? safe(() => inboxApi.conversations(companyId, 0, 5, null, 'INBOX'))
        : Promise.resolve(null)

      const connectionsPromise = canReadChannels
        ? safe(() => channelApi.list(companyId))
        : Promise.resolve(null)

      const managedEmailPromise = canReadBroadcasts
        ? Promise.all([
            safe(() => managedEmailApi.overview(companyId)),
            safe(() => managedEmailApi.usage(companyId)),
            safe(() => managedEmailApi.deliverability(companyId)),
          ]).then(([overview, usage, deliverability]) => ({
            overview,
            usage,
            deliverability,
          }))
        : Promise.resolve(null)

      const managedSmsPromise = canReadBroadcasts
        ? Promise.all([
            safe(() => managedSmsApi.status(companyId)),
            safe(() => managedSmsApi.usage(companyId)),
            safe(() => managedSmsApi.deliveryHealth(companyId)),
          ]).then(([status, usage, health]) => ({ status, usage, health }))
        : Promise.resolve(null)

      const adsPromise = canReadAds
        ? Promise.all([
            safe(() => adCenterApi.connections(companyId)),
            safe(() => adCenterApi.accounts(companyId)),
            safe(() => adCenterApi.campaigns(companyId, undefined, 0, 50)),
          ]).then(([adConnections, adAccounts, campaignPage]) => ({
            connections: adConnections ?? [],
            accounts: adAccounts ?? [],
            campaigns: campaignPage?.content ?? [],
          }))
        : Promise.resolve(null)

      try {
        const [
          contactBundle,
          broadcastBundle,
          nextInboxPage,
          nextConnections,
          nextManagedEmail,
          nextManagedSms,
          nextAds,
        ] = await Promise.all([
          contactBundlePromise,
          broadcastBundlePromise,
          inboxPromise,
          connectionsPromise,
          managedEmailPromise,
          managedSmsPromise,
          adsPromise,
        ])

        if (!scopeIsCurrent(scope)) return

        setContactSummary(contactBundle?.summary ?? null)
        setRecentContacts(contactBundle?.recent ?? [])
        setLists(contactBundle?.lists ?? [])
        setSegments(contactBundle?.segments ?? [])
        setBroadcastTotal(broadcastBundle?.total ?? 0)
        setBroadcasts(broadcastBundle?.recent ?? [])
        setInboxPage(nextInboxPage)
        setConnections(nextConnections ?? [])
        setManagedEmail(
          nextManagedEmail ?? { overview: null, usage: null, deliverability: null },
        )
        setManagedSms(nextManagedSms ?? { status: null, usage: null, health: null })
        setAds(nextAds ?? { connections: [], accounts: [], campaigns: [] })
      } finally {
        if (scopeIsCurrent(scope)) {
          setLoading(false)
          setRefreshing(false)
        }
      }
    },
    [
      canReadAds,
      canReadBroadcasts,
      canReadChannels,
      canReadContacts,
      canReadInbox,
      captureScope,
      companyId,
      scopeIsCurrent,
      workspaceEpoch,
    ],
  )

  useEffect(() => {
    void loadDashboard()
  }, [loadDashboard])

  const emailReady =
    managedEmail.overview?.serviceStatus === 'ACTIVE' &&
    managedEmail.overview.sendingStatus === 'ENABLED'
  const smsReady = managedSms.status?.state === 'READY' && managedSms.status.sendingEnabled
  const emailStatus = connectionStatus(connections, 'EMAIL', emailReady)
  const smsStatus = connectionStatus(connections, 'SMS', smsReady)
  const whatsAppStatus = connectionStatus(connections, 'WHATSAPP')
  const activeAdConnections = ads.connections.filter((item) => item.status === 'ACTIVE')
  const managedAdAccounts = ads.accounts.filter((item) => item.managed && item.accessible)
  const activeAdCampaigns = ads.campaigns.filter((item) => item.status === 'ACTIVE')

  const attentionItems = useMemo<AttentionItem[]>(() => {
    const items: AttentionItem[] = []

    if (canReadChannels) {
      const errorConnection = connections.find((connection) => connection.status === 'ERROR')
      if (errorConnection) {
        items.push({
          id: `connection-${errorConnection.id}`,
          title: `${channelLabel(errorConnection.channelType)} connection needs attention`,
          detail: `${errorConnection.name} is reporting a connection error.`,
          to: `/company/connections?section=${errorConnection.channelType.toLowerCase()}`,
          tone: 'danger',
        })
      }
    }

    if (
      managedEmail.deliverability &&
      ['WARNING', 'THROTTLED', 'SUSPENDED'].includes(managedEmail.deliverability.status)
    ) {
      items.push({
        id: 'managed-email-deliverability',
        title: 'Managed Email deliverability needs attention',
        detail:
          managedEmail.deliverability.notice ||
          `Deliverability status is ${statusLabel(managedEmail.deliverability.status)}.`,
        to: '/company/connections?section=email',
        tone: managedEmail.deliverability.status === 'SUSPENDED' ? 'danger' : 'warning',
      })
    } else if (
      managedEmail.overview?.enabled &&
      managedEmail.overview.sendingStatus !== 'ENABLED'
    ) {
      items.push({
        id: 'managed-email-sending',
        title: 'Managed Email sending is not ready',
        detail: managedEmail.overview.globalNotice || 'Review your Managed Email setup.',
        to: '/company/connections?section=email',
        tone: 'warning',
      })
    }

    if (managedSms.status && managedSms.status.state !== 'READY') {
      items.push({
        id: 'managed-sms-status',
        title: 'Managed SMS setup needs attention',
        detail:
          managedSms.status.nextAction ||
          `Current setup status: ${statusLabel(managedSms.status.state)}.`,
        to: '/company/connections?section=sms',
        tone:
          managedSms.status.state === 'REJECTED' || managedSms.status.state === 'SUSPENDED'
            ? 'danger'
            : 'warning',
      })
    } else if (managedSms.health && managedSms.health.status !== 'HEALTHY') {
      items.push({
        id: 'managed-sms-health',
        title: 'Managed SMS delivery health needs attention',
        detail: managedSms.health.actionRequired || `Health: ${managedSms.health.status}.`,
        to: '/company/connections?section=sms',
        tone: managedSms.health.status === 'SUSPENDED' ? 'danger' : 'warning',
      })
    }

    const failedBroadcast = broadcasts.find(
      (message) =>
        message.status === 'FAILED' ||
        message.status === 'PARTIALLY_FAILED' ||
        message.stats.failed > 0,
    )
    if (failedBroadcast) {
      items.push({
        id: `broadcast-${failedBroadcast.id}`,
        title: 'A recent broadcast had delivery failures',
        detail: `${broadcastTitle(failedBroadcast)} · ${number.format(failedBroadcast.stats.failed)} failed.`,
        to: `/broadcast/${failedBroadcast.channelType === 'WHATSAPP' ? 'social-media' : failedBroadcast.channelType?.toLowerCase() || 'email'}?broadcastId=${failedBroadcast.id}`,
        tone: failedBroadcast.status === 'FAILED' ? 'danger' : 'warning',
      })
    }

    const adsError = ads.connections.find((connection) => connection.status === 'ERROR')
    if (adsError) {
      items.push({
        id: `ads-${adsError.id}`,
        title: `${adsError.provider === 'META_ADS' ? 'Meta Ads' : 'Google Ads'} connection needs attention`,
        detail: adsError.lastError || 'Reconnect the advertising provider.',
        to: '/company/connections?section=ads',
        tone: 'danger',
      })
    }

    if (items.length === 0) {
      if (canReadEmail && !emailStatus.ready) {
        items.push({
          id: 'email-not-ready',
          title: 'Email is not connected yet',
          detail: 'Connect an Inbox mailbox or set up Managed Email for broadcasts.',
          to: '/company/connections?section=email',
          tone: 'info',
        })
      }
      if (canReadWhatsApp && !whatsAppStatus.ready) {
        items.push({
          id: 'whatsapp-not-ready',
          title: 'WhatsApp is not connected yet',
          detail: 'Use Meta Embedded Signup to connect a WhatsApp Business account.',
          to: '/company/connections?section=whatsapp',
          tone: 'info',
        })
      }
    }

    return items.slice(0, 5)
  }, [
    ads.connections,
    broadcasts,
    canReadChannels,
    canReadEmail,
    canReadWhatsApp,
    connections,
    emailStatus.ready,
    managedEmail.deliverability,
    managedEmail.overview,
    managedSms.health,
    managedSms.status,
    whatsAppStatus.ready,
  ])

  const activities = useMemo<ActivityItem[]>(() => {
    const items: ActivityItem[] = []

    broadcasts.forEach((message) => {
      items.push({
        id: `broadcast-${message.id}`,
        title: broadcastTitle(message),
        detail: `${channelLabel(message.channelType)} broadcast · ${statusLabel(message.status)}`,
        at: message.updatedAt || message.createdAt,
        to: `/broadcast/${message.channelType === 'WHATSAPP' ? 'social-media' : message.channelType?.toLowerCase() || 'email'}?broadcastId=${message.id}`,
        icon: <FiSend aria-hidden="true" />,
      })
    })

    inboxPage?.items.forEach((conversation) => {
      items.push({
        id: `inbox-${conversation.id}`,
        title: conversation.subject || conversation.participants[0]?.displayName || conversation.participants[0]?.address || 'Inbox conversation',
        detail: conversation.lastMessagePreview || 'Conversation updated',
        at: conversation.lastMessageAt || new Date(0).toISOString(),
        to: `/inbox/${conversation.providerType === 'META_WHATSAPP' ? 'social-media' : 'email'}?conversationId=${conversation.id}`,
        icon: <FiInbox aria-hidden="true" />,
      })
    })

    recentContacts.forEach((contact) => {
      if (!contact.createdAt) return
      items.push({
        id: `contact-${contact.id}`,
        title: contact.displayName,
        detail: 'Contact added',
        at: contact.createdAt,
        to: `/contacts/${contact.id}`,
        icon: <FiUsers aria-hidden="true" />,
      })
    })

    return items
      .filter((item) => item.at !== new Date(0).toISOString())
      .sort((left, right) => new Date(right.at).getTime() - new Date(left.at).getTime())
      .slice(0, 8)
  }, [broadcasts, inboxPage?.items, recentContacts])

  const growthSeries = useMemo(() => buildGrowthSeries(contactSummary), [contactSummary])
  const maxGrowth = Math.max(1, ...growthSeries.map((point) => point.added))
  const recentUnread = inboxPage?.items.reduce((sum, conversation) => sum + conversation.unreadCount, 0) ?? 0

  const kpis = [
    canReadContacts
      ? {
          label: 'Contacts',
          value: contactSummary ? number.format(contactSummary.totalContacts) : '—',
          meta: contactSummary
            ? `+${number.format(contactSummary.newContacts30d)} in 30 days`
            : 'Contact audience',
          icon: <FiUsers />,
          to: '/contacts',
        }
      : null,
    canReadContacts
      ? {
          label: 'Email subscribers',
          value: contactSummary ? number.format(contactSummary.emailSubscribers) : '—',
          meta: contactSummary
            ? `${number.format(contactSummary.smsSubscribers)} SMS · ${number.format(contactSummary.whatsappSubscribers)} WhatsApp`
            : 'Marketing permissions',
          icon: <FiMail />,
          to: '/contacts',
        }
      : null,
    canReadBroadcasts
      ? {
          label: 'Broadcasts',
          value: number.format(broadcastTotal),
          meta: 'Across Email, SMS and WhatsApp',
          icon: <FiSend />,
          to: '/broadcast/email',
        }
      : null,
    canReadInbox
      ? {
          label: 'Inbox conversations',
          value: inboxPage ? number.format(inboxPage.totalElements) : '—',
          meta: `${number.format(recentUnread)} unread in recent conversations`,
          icon: <FiInbox />,
          to: canReadEmail ? '/inbox/email' : '/inbox/social-media',
        }
      : null,
    canReadAds
      ? {
          label: 'Active ad campaigns',
          value: number.format(activeAdCampaigns.length),
          meta: `${number.format(managedAdAccounts.length)} managed accounts`,
          icon: <FiBarChart2 />,
          to: '/ad-center',
        }
      : null,
  ].filter((item): item is NonNullable<typeof item> => item !== null)

  const channelCards = [
    canReadEmail || canReadBroadcasts
      ? {
          id: 'email',
          label: 'Email',
          icon: <FiMail />,
          ready: emailStatus.ready,
          detail: emailReady
            ? 'Managed Email ready'
            : emailStatus.active > 0
              ? `${emailStatus.active} active connection${emailStatus.active === 1 ? '' : 's'}`
              : 'Setup required',
          problem: emailStatus.hasProblem,
          to: '/company/connections?section=email',
        }
      : null,
    canReadSms || canReadBroadcasts
      ? {
          id: 'sms',
          label: 'SMS',
          icon: <FiSmartphone />,
          ready: smsStatus.ready,
          detail: smsReady
            ? 'Managed SMS ready'
            : smsStatus.active > 0
              ? `${smsStatus.active} active connection${smsStatus.active === 1 ? '' : 's'}`
              : 'Setup required',
          problem: smsStatus.hasProblem,
          to: '/company/connections?section=sms',
        }
      : null,
    canReadWhatsApp || canReadBroadcasts
      ? {
          id: 'whatsapp',
          label: 'WhatsApp',
          icon: <FiMessageCircle />,
          ready: whatsAppStatus.ready,
          detail:
            whatsAppStatus.active > 0
              ? `${whatsAppStatus.active} active account${whatsAppStatus.active === 1 ? '' : 's'}`
              : 'Not connected',
          problem: whatsAppStatus.hasProblem,
          to: '/company/connections?section=whatsapp',
        }
      : null,
    canReadAds
      ? {
          id: 'ads',
          label: 'Ads',
          icon: <FiBarChart2 />,
          ready: activeAdConnections.length > 0,
          detail:
            activeAdConnections.length > 0
              ? `${activeAdConnections.length} provider${activeAdConnections.length === 1 ? '' : 's'} connected`
              : 'Not connected',
          problem: ads.connections.some((item) => item.status === 'ERROR'),
          to: '/company/connections?section=ads',
        }
      : null,
  ].filter((item): item is NonNullable<typeof item> => item !== null)

  const emailUsagePercent =
    managedEmail.usage && managedEmail.usage.limit > 0
      ? Math.min(100, (managedEmail.usage.accepted / managedEmail.usage.limit) * 100)
      : 0
  const smsUsagePercent =
    managedSms.usage && managedSms.usage.limit && managedSms.usage.limit > 0
      ? Math.min(100, (managedSms.usage.acceptedSegments / managedSms.usage.limit) * 100)
      : 0

  if (!companyId || !selectedCompany) {
    return (
      <section className="p-4 sm:p-6 lg:p-8">
        <div className="panel p-8 text-center text-sm text-slate-500">
          Select a workspace to open the dashboard.
        </div>
      </section>
    )
  }

  return (
    <section className="px-4 py-6 sm:px-6 lg:px-8">
      <div className="w-full">
        <header className="flex flex-wrap items-start justify-between gap-4">
          <div>
            <p className="eyebrow">WORKSPACE DASHBOARD</p>
            <h1 className="mt-2 text-2xl font-bold tracking-tight text-slate-950 sm:text-3xl">
              {selectedCompany.name}
            </h1>
            <p className="mt-2 max-w-3xl text-sm leading-6 text-slate-500">
              Welcome{user ? `, ${user.username}` : ''}. See what needs attention, what changed
              recently, and how your communication channels are performing.
            </p>
          </div>
          <div className="flex flex-wrap gap-2">
            <button
              type="button"
              className="secondary-button gap-2"
              disabled={refreshing}
              onClick={() => void loadDashboard(true)}
            >
              <FiRefreshCw aria-hidden="true" />
              {refreshing ? 'Refreshing…' : 'Refresh'}
            </button>
            {canCreateBroadcasts && (
              <Link className="primary-button gap-2" to="/broadcast/email">
                <FiSend aria-hidden="true" />
                New broadcast
              </Link>
            )}
          </div>
        </header>

        {loading ? (
          <div className="mt-7 grid gap-4 sm:grid-cols-2 xl:grid-cols-5">
            {Array.from({ length: Math.max(4, kpis.length) }).map((_, index) => (
              <CardSkeleton key={index} rows={2} />
            ))}
          </div>
        ) : (
          <div className="mt-7 grid gap-3 sm:grid-cols-2 xl:grid-cols-5">
            {kpis.map((kpi) => (
              <Link
                key={kpi.label}
                to={kpi.to}
                className="panel group p-4 transition hover:border-indigo-200 hover:shadow-sm"
              >
                <div className="flex items-start justify-between gap-3">
                  <span className="flex size-9 items-center justify-center rounded-xl bg-indigo-50 text-indigo-600">
                    {kpi.icon}
                  </span>
                  <FiArrowRight className="text-slate-300 transition group-hover:text-indigo-500" aria-hidden="true" />
                </div>
                <p className="mt-4 text-2xl font-bold text-slate-950">{kpi.value}</p>
                <p className="mt-1 text-xs font-bold text-slate-700">{kpi.label}</p>
                <p className="mt-1 text-[11px] leading-4 text-slate-500">{kpi.meta}</p>
              </Link>
            ))}
          </div>
        )}

        <section className="mt-6">
          <SectionHeading
            eyebrow="ACTION CENTER"
            title="Needs attention"
            description="Issues and setup steps that may affect sending, replies or campaign delivery."
          />
          <div className="mt-3">
            {loading ? (
              <ListSkeleton rows={2} />
            ) : attentionItems.length === 0 ? (
              <div className="flex items-center gap-3 rounded-2xl border border-emerald-200 bg-emerald-50 px-4 py-4 text-emerald-800">
                <FiCheckCircle className="shrink-0 text-xl" aria-hidden="true" />
                <div>
                  <p className="text-sm font-bold">Everything looks healthy</p>
                  <p className="mt-0.5 text-xs text-emerald-700">
                    No channel, delivery or recent broadcast issues need your attention.
                  </p>
                </div>
              </div>
            ) : (
              <div className="grid gap-2 lg:grid-cols-2">
                {attentionItems.map((item) => (
                  <Link
                    key={item.id}
                    to={item.to}
                    className={`flex items-start gap-3 rounded-2xl border p-4 transition hover:shadow-sm ${attentionClass[item.tone]}`}
                  >
                    <FiAlertCircle className="mt-0.5 shrink-0 text-lg" aria-hidden="true" />
                    <div className="min-w-0">
                      <p className="text-sm font-bold">{item.title}</p>
                      <p className="mt-1 text-xs leading-5 opacity-80">{item.detail}</p>
                    </div>
                    <FiArrowRight className="ml-auto mt-1 shrink-0 opacity-60" aria-hidden="true" />
                  </Link>
                ))}
              </div>
            )}
          </div>
        </section>

        {channelCards.length > 0 && (
          <section className="mt-6">
            <SectionHeading
              title="Channel status"
              description="Connection and managed-service readiness across your workspace."
              action={
                <Link className="secondary-button gap-2" to="/company/connections">
                  <FiLink aria-hidden="true" />
                  Manage connections
                </Link>
              }
            />
            <div className="mt-3 grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
              {channelCards.map((channel) => (
                <Link
                  key={channel.id}
                  to={channel.to}
                  className="panel group flex items-center gap-4 p-4 transition hover:border-indigo-200"
                >
                  <span
                    className={`flex size-11 shrink-0 items-center justify-center rounded-xl text-lg ${
                      channel.problem
                        ? 'bg-rose-50 text-rose-600'
                        : channel.ready
                          ? 'bg-emerald-50 text-emerald-600'
                          : 'bg-slate-100 text-slate-500'
                    }`}
                  >
                    {channel.icon}
                  </span>
                  <div className="min-w-0 flex-1">
                    <div className="flex items-center gap-2">
                      <p className="text-sm font-bold text-slate-950">{channel.label}</p>
                      <span
                        className={`rounded-full px-2 py-0.5 text-[9px] font-bold ${
                          channel.problem
                            ? 'bg-rose-50 text-rose-700'
                            : channel.ready
                              ? 'bg-emerald-50 text-emerald-700'
                              : 'bg-slate-100 text-slate-600'
                        }`}
                      >
                        {channel.problem ? 'ATTENTION' : channel.ready ? 'READY' : 'SETUP'}
                      </span>
                    </div>
                    <p className="mt-1 truncate text-xs text-slate-500">{channel.detail}</p>
                  </div>
                  <FiArrowRight className="shrink-0 text-slate-300 group-hover:text-indigo-500" aria-hidden="true" />
                </Link>
              ))}
            </div>
          </section>
        )}

        {canReadBroadcasts && (
          <section className="panel mt-6 overflow-hidden">
            <div className="border-b border-slate-100 p-5">
              <SectionHeading
                title="Recent broadcasts"
                description="Latest Email, SMS and WhatsApp sends."
                action={
                  <Link className="secondary-button gap-2" to="/broadcast/email">
                    View all
                    <FiArrowRight aria-hidden="true" />
                  </Link>
                }
              />
            </div>
            {loading ? (
              <div className="p-4"><ListSkeleton rows={4} /></div>
            ) : broadcasts.length === 0 ? (
              <div className="px-5 py-10 text-center">
                <FiSend className="mx-auto text-3xl text-slate-300" aria-hidden="true" />
                <p className="mt-3 text-sm font-bold text-slate-800">No broadcasts yet</p>
                <p className="mt-1 text-xs text-slate-500">Create your first broadcast when your audience and sender are ready.</p>
              </div>
            ) : (
              <div className="overflow-x-auto">
                <table className="min-w-full text-sm">
                  <thead>
                    <tr className="bg-slate-50 text-left text-xs text-slate-500">
                      <th className="px-5 py-3 font-semibold">Broadcast</th>
                      <th className="px-4 py-3 font-semibold">Channel</th>
                      <th className="px-4 py-3 font-semibold">Status</th>
                      <th className="px-4 py-3 text-right font-semibold">Accepted</th>
                      <th className="px-4 py-3 text-right font-semibold">Failed</th>
                      <th className="px-5 py-3 text-right font-semibold">Updated</th>
                    </tr>
                  </thead>
                  <tbody>
                    {broadcasts.map((message) => (
                      <tr key={message.id} className="border-t border-slate-100">
                        <td className="px-5 py-4">
                          <Link
                            className="font-semibold text-slate-900 hover:text-indigo-600"
                            to={`/broadcast/${message.channelType === 'WHATSAPP' ? 'social-media' : message.channelType?.toLowerCase() || 'email'}?broadcastId=${message.id}`}
                          >
                            {broadcastTitle(message)}
                          </Link>
                          <p className="mt-1 max-w-md truncate text-xs text-slate-500">
                            {message.bodyPreview || 'No message preview'}
                          </p>
                        </td>
                        <td className="px-4 py-4 text-xs font-semibold text-slate-600">
                          {channelLabel(message.channelType)}
                        </td>
                        <td className="px-4 py-4">
                          <span className={`rounded-full px-2.5 py-1 text-[10px] font-bold ${broadcastStatusClass(message.status)}`}>
                            {statusLabel(message.status)}
                          </span>
                        </td>
                        <td className="px-4 py-4 text-right font-semibold text-slate-700">
                          {number.format(message.stats.accepted)}
                        </td>
                        <td className={`px-4 py-4 text-right font-semibold ${message.stats.failed > 0 ? 'text-rose-600' : 'text-slate-700'}`}>
                          {number.format(message.stats.failed)}
                        </td>
                        <td className="px-5 py-4 text-right text-xs text-slate-500">
                          {formatRelativeDate(message.updatedAt)}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </section>
        )}

        <div className="mt-6 grid gap-6 xl:grid-cols-2">
          {canReadInbox && (
            <section className="panel overflow-hidden">
              <div className="border-b border-slate-100 p-5">
                <SectionHeading
                  title="Inbox"
                  description={
                    inboxPage
                      ? `${number.format(inboxPage.totalElements)} conversations · ${number.format(recentUnread)} unread in the latest items`
                      : 'Latest customer conversations'
                  }
                  action={
                    <Link className="secondary-button gap-2" to={canReadEmail ? '/inbox/email' : '/inbox/social-media'}>
                      Open Inbox
                      <FiArrowRight aria-hidden="true" />
                    </Link>
                  }
                />
              </div>
              {loading ? (
                <div className="p-4"><ListSkeleton rows={5} /></div>
              ) : !inboxPage || inboxPage.items.length === 0 ? (
                <div className="px-5 py-10 text-center text-sm text-slate-500">
                  No Inbox conversations yet.
                </div>
              ) : (
                <div className="divide-y divide-slate-100">
                  {inboxPage.items.map((conversation) => (
                    <Link
                      key={conversation.id}
                      to={`/inbox/${conversation.providerType === 'META_WHATSAPP' ? 'social-media' : 'email'}?conversationId=${conversation.id}`}
                      className="flex items-center gap-3 px-5 py-3 transition hover:bg-slate-50"
                    >
                      <span className="flex size-9 shrink-0 items-center justify-center rounded-full bg-slate-100 text-slate-500">
                        <FiInbox aria-hidden="true" />
                      </span>
                      <div className="min-w-0 flex-1">
                        <div className="flex items-center gap-2">
                          <p className="truncate text-sm font-semibold text-slate-900">
                            {conversation.subject || conversation.participants[0]?.displayName || conversation.participants[0]?.address || 'Conversation'}
                          </p>
                          {conversation.unreadCount > 0 && (
                            <span className="rounded-full bg-indigo-600 px-2 py-0.5 text-[9px] font-bold text-white">
                              {conversation.unreadCount}
                            </span>
                          )}
                        </div>
                        <p className="mt-0.5 truncate text-xs text-slate-500">
                          {conversation.lastMessagePreview || 'No preview'}
                        </p>
                      </div>
                      <span className="shrink-0 text-[10px] text-slate-400">
                        {formatRelativeDate(conversation.lastMessageAt)}
                      </span>
                    </Link>
                  ))}
                </div>
              )}
            </section>
          )}

          {canReadContacts && (
            <section className="panel p-5">
              <SectionHeading
                title="Contacts & audience growth"
                description="New contacts over the last 30 days and current marketing permissions."
                action={
                  <Link className="secondary-button gap-2" to="/contacts">
                    View contacts
                    <FiArrowRight aria-hidden="true" />
                  </Link>
                }
              />
              {loading ? (
                <div className="mt-4"><CardSkeleton rows={5} /></div>
              ) : (
                <>
                  <div className="mt-5 flex h-36 items-end gap-1 rounded-xl border border-slate-100 bg-slate-50 px-3 pb-3 pt-5">
                    {growthSeries.map((point) => (
                      <div key={point.date} className="flex h-full min-w-0 flex-1 items-end">
                        <div
                          className="w-full rounded-t bg-indigo-400"
                          style={{
                            height: point.added === 0 ? '3%' : `${Math.max(8, (point.added / maxGrowth) * 100)}%`,
                          }}
                          title={`${point.date}: ${point.added} contacts`}
                        />
                      </div>
                    ))}
                  </div>
                  <div className="mt-2 flex justify-between text-[10px] text-slate-400">
                    <span>30 days ago</span>
                    <span>Today</span>
                  </div>

                  <div className="mt-5 grid gap-3 sm:grid-cols-3">
                    <div className="rounded-xl border border-slate-200 p-3">
                      <p className="text-[10px] font-bold uppercase tracking-wide text-slate-400">Email</p>
                      <p className="mt-1 text-lg font-bold text-slate-900">{number.format(contactSummary?.emailSubscribers ?? 0)}</p>
                      <p className="text-[11px] text-slate-500">subscribed</p>
                    </div>
                    <div className="rounded-xl border border-slate-200 p-3">
                      <p className="text-[10px] font-bold uppercase tracking-wide text-slate-400">SMS</p>
                      <p className="mt-1 text-lg font-bold text-slate-900">{number.format(contactSummary?.smsSubscribers ?? 0)}</p>
                      <p className="text-[11px] text-slate-500">subscribed</p>
                    </div>
                    <div className="rounded-xl border border-slate-200 p-3">
                      <p className="text-[10px] font-bold uppercase tracking-wide text-slate-400">WhatsApp</p>
                      <p className="mt-1 text-lg font-bold text-slate-900">{number.format(contactSummary?.whatsappSubscribers ?? 0)}</p>
                      <p className="text-[11px] text-slate-500">subscribed</p>
                    </div>
                  </div>

                  <div className="mt-4 flex flex-wrap gap-2 text-xs text-slate-500">
                    <span className="rounded-full bg-slate-100 px-2.5 py-1">{lists.length} lists</span>
                    <span className="rounded-full bg-slate-100 px-2.5 py-1">{segments.length} segments</span>
                    <span className="rounded-full bg-indigo-50 px-2.5 py-1 font-semibold text-indigo-700">
                      +{number.format(contactSummary?.newContacts30d ?? 0)} contacts / 30d
                    </span>
                  </div>
                </>
              )}
            </section>
          )}
        </div>

        {canReadBroadcasts && (
          <section className="mt-6">
            <SectionHeading
              title="Managed channel usage"
              description="Current-period usage and delivery health for UnifyDesk Managed Email and SMS."
            />
            <div className="mt-3 grid gap-4 xl:grid-cols-2">
              <div className="panel p-5">
                <div className="flex items-start justify-between gap-3">
                  <div className="flex items-center gap-3">
                    <span className="flex size-10 items-center justify-center rounded-xl bg-indigo-50 text-indigo-600">
                      <FiMail aria-hidden="true" />
                    </span>
                    <div>
                      <p className="text-sm font-bold text-slate-950">Managed Email</p>
                      <p className="mt-0.5 text-xs text-slate-500">
                        {managedEmail.overview
                          ? statusLabel(managedEmail.overview.serviceStatus)
                          : 'Not configured'}
                      </p>
                    </div>
                  </div>
                  <Link className="text-xs font-semibold text-indigo-600" to="/company/connections?section=email">
                    Manage
                  </Link>
                </div>
                {managedEmail.usage ? (
                  <>
                    <div className="mt-5 flex items-end justify-between gap-3">
                      <div>
                        <p className="text-2xl font-bold text-slate-950">
                          {number.format(managedEmail.usage.accepted)}
                        </p>
                        <p className="text-xs text-slate-500">accepted this period</p>
                      </div>
                      <p className="text-right text-xs text-slate-500">
                        {managedEmail.usage.limit < 0
                          ? 'Unlimited'
                          : `${number.format(Math.max(0, managedEmail.usage.remaining))} remaining`}
                      </p>
                    </div>
                    {managedEmail.usage.limit > 0 && (
                      <div className="mt-3 h-2 overflow-hidden rounded-full bg-slate-100">
                        <div className="h-full rounded-full bg-indigo-500" style={{ width: `${emailUsagePercent}%` }} />
                      </div>
                    )}
                    {managedEmail.deliverability && (
                      <div className="mt-4 grid grid-cols-3 gap-2 text-center">
                        <div className="rounded-lg bg-slate-50 p-2">
                          <p className="text-xs font-bold text-slate-800">{statusLabel(managedEmail.deliverability.status)}</p>
                          <p className="text-[10px] text-slate-400">health</p>
                        </div>
                        <div className="rounded-lg bg-slate-50 p-2">
                          <p className="text-xs font-bold text-slate-800">{(managedEmail.deliverability.hardBounceRate * 100).toFixed(2)}%</p>
                          <p className="text-[10px] text-slate-400">bounce</p>
                        </div>
                        <div className="rounded-lg bg-slate-50 p-2">
                          <p className="text-xs font-bold text-slate-800">{(managedEmail.deliverability.complaintRate * 100).toFixed(2)}%</p>
                          <p className="text-[10px] text-slate-400">complaints</p>
                        </div>
                      </div>
                    )}
                  </>
                ) : (
                  <p className="mt-5 text-sm text-slate-500">Set up Managed Email to see usage and deliverability here.</p>
                )}
              </div>

              <div className="panel p-5">
                <div className="flex items-start justify-between gap-3">
                  <div className="flex items-center gap-3">
                    <span className="flex size-10 items-center justify-center rounded-xl bg-violet-50 text-violet-600">
                      <FiSmartphone aria-hidden="true" />
                    </span>
                    <div>
                      <p className="text-sm font-bold text-slate-950">Managed SMS</p>
                      <p className="mt-0.5 text-xs text-slate-500">
                        {managedSms.status ? statusLabel(managedSms.status.state) : 'Not configured'}
                      </p>
                    </div>
                  </div>
                  <Link className="text-xs font-semibold text-indigo-600" to="/company/connections?section=sms">
                    Manage
                  </Link>
                </div>
                {managedSms.usage ? (
                  <>
                    <div className="mt-5 flex items-end justify-between gap-3">
                      <div>
                        <p className="text-2xl font-bold text-slate-950">
                          {number.format(managedSms.usage.acceptedSegments)}
                        </p>
                        <p className="text-xs text-slate-500">segments this period</p>
                      </div>
                      <p className="text-right text-xs text-slate-500">
                        {managedSms.usage.limit && managedSms.usage.limit > 0
                          ? `${number.format(Math.max(0, managedSms.usage.remaining ?? 0))} remaining`
                          : 'Usage tracked'}
                      </p>
                    </div>
                    {managedSms.usage.limit && managedSms.usage.limit > 0 ? (
                      <div className="mt-3 h-2 overflow-hidden rounded-full bg-slate-100">
                        <div className="h-full rounded-full bg-violet-500" style={{ width: `${smsUsagePercent}%` }} />
                      </div>
                    ) : null}
                    {managedSms.health && (
                      <div className="mt-4 grid grid-cols-3 gap-2 text-center">
                        <div className="rounded-lg bg-slate-50 p-2">
                          <p className="text-xs font-bold text-slate-800">{statusLabel(managedSms.health.status)}</p>
                          <p className="text-[10px] text-slate-400">health</p>
                        </div>
                        <div className="rounded-lg bg-slate-50 p-2">
                          <p className="text-xs font-bold text-slate-800">{number.format(managedSms.health.delivered)}</p>
                          <p className="text-[10px] text-slate-400">delivered</p>
                        </div>
                        <div className="rounded-lg bg-slate-50 p-2">
                          <p className="text-xs font-bold text-slate-800">{number.format(managedSms.health.failed + managedSms.health.rejected)}</p>
                          <p className="text-[10px] text-slate-400">failed</p>
                        </div>
                      </div>
                    )}
                  </>
                ) : (
                  <p className="mt-5 text-sm text-slate-500">Set up Managed SMS to see usage and delivery health here.</p>
                )}
              </div>
            </div>
          </section>
        )}

        <div className="mt-6 grid gap-6 xl:grid-cols-2">
          {canReadAds && (
            <section className="panel p-5">
              <SectionHeading
                title="Ads snapshot"
                description="Connection and campaign overview without leaving the workspace dashboard."
                action={
                  <Link className="secondary-button gap-2" to="/ad-center">
                    Open Ad Center
                    <FiArrowRight aria-hidden="true" />
                  </Link>
                }
              />
              <div className="mt-5 grid grid-cols-3 gap-3">
                <div className="rounded-xl bg-slate-50 p-4">
                  <p className="text-2xl font-bold text-slate-950">{activeAdConnections.length}</p>
                  <p className="mt-1 text-[11px] text-slate-500">connected providers</p>
                </div>
                <div className="rounded-xl bg-slate-50 p-4">
                  <p className="text-2xl font-bold text-slate-950">{managedAdAccounts.length}</p>
                  <p className="mt-1 text-[11px] text-slate-500">managed accounts</p>
                </div>
                <div className="rounded-xl bg-slate-50 p-4">
                  <p className="text-2xl font-bold text-slate-950">{activeAdCampaigns.length}</p>
                  <p className="mt-1 text-[11px] text-slate-500">active campaigns</p>
                </div>
              </div>
              {ads.connections.length > 0 ? (
                <div className="mt-4 divide-y divide-slate-100 rounded-xl border border-slate-200">
                  {ads.connections.slice(0, 3).map((connection) => (
                    <div key={connection.id} className="flex items-center justify-between gap-3 p-3">
                      <div>
                        <p className="text-xs font-bold text-slate-800">
                          {connection.provider === 'META_ADS' ? 'Meta Ads' : 'Google Ads'}
                        </p>
                        <p className="mt-0.5 text-[10px] text-slate-500">
                          {connection.displayName || 'Advertising provider'}
                        </p>
                      </div>
                      <span className={`rounded-full px-2 py-0.5 text-[9px] font-bold ${connection.status === 'ACTIVE' ? 'bg-emerald-50 text-emerald-700' : connection.status === 'ERROR' ? 'bg-rose-50 text-rose-700' : 'bg-slate-100 text-slate-600'}`}>
                        {statusLabel(connection.status)}
                      </span>
                    </div>
                  ))}
                </div>
              ) : (
                <p className="mt-4 text-sm text-slate-500">No advertising provider is connected yet.</p>
              )}
            </section>
          )}

          <section className="panel overflow-hidden">
            <div className="border-b border-slate-100 p-5">
              <SectionHeading
                title="Recent workspace activity"
                description="Recent contact, Inbox and broadcast changes in one place."
              />
            </div>
            {loading ? (
              <div className="p-4"><ListSkeleton rows={6} /></div>
            ) : activities.length === 0 ? (
              <div className="px-5 py-10 text-center text-sm text-slate-500">
                Activity will appear here as your team starts using the workspace.
              </div>
            ) : (
              <div className="divide-y divide-slate-100">
                {activities.map((item) => (
                  <Link
                    key={item.id}
                    to={item.to}
                    className="flex items-start gap-3 px-5 py-3 transition hover:bg-slate-50"
                  >
                    <span className="mt-0.5 flex size-8 shrink-0 items-center justify-center rounded-lg bg-slate-100 text-slate-500">
                      {item.icon}
                    </span>
                    <div className="min-w-0 flex-1">
                      <p className="truncate text-xs font-bold text-slate-800">{item.title}</p>
                      <p className="mt-0.5 truncate text-[11px] text-slate-500">{item.detail}</p>
                    </div>
                    <span className="shrink-0 text-[10px] text-slate-400">
                      {formatRelativeDate(item.at)}
                    </span>
                  </Link>
                ))}
              </div>
            )}
          </section>
        </div>

        <section className="mt-6">
          <SectionHeading title="Quick actions" description="Common workspace tasks, one click away." />
          <div className="mt-3 grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
            {canCreateBroadcasts && (
              <Link className="panel group flex items-center gap-3 p-4 hover:border-indigo-200" to="/broadcast/email">
                <span className="flex size-10 items-center justify-center rounded-xl bg-indigo-50 text-indigo-600"><FiSend /></span>
                <div>
                  <p className="text-sm font-bold text-slate-900">New broadcast</p>
                  <p className="text-xs text-slate-500">Email, SMS or WhatsApp</p>
                </div>
              </Link>
            )}
            {canImportContacts && (
              <Link className="panel group flex items-center gap-3 p-4 hover:border-indigo-200" to="/contacts/imports">
                <span className="flex size-10 items-center justify-center rounded-xl bg-emerald-50 text-emerald-600"><FiUpload /></span>
                <div>
                  <p className="text-sm font-bold text-slate-900">Import contacts</p>
                  <p className="text-xs text-slate-500">Add a CSV audience</p>
                </div>
              </Link>
            )}
            {canReadInbox && (
              <Link className="panel group flex items-center gap-3 p-4 hover:border-indigo-200" to={canReadEmail ? '/inbox/email' : '/inbox/social-media'}>
                <span className="flex size-10 items-center justify-center rounded-xl bg-sky-50 text-sky-600"><FiInbox /></span>
                <div>
                  <p className="text-sm font-bold text-slate-900">Open Inbox</p>
                  <p className="text-xs text-slate-500">Reply to conversations</p>
                </div>
              </Link>
            )}
            {canReadChannels && (
              <Link className="panel group flex items-center gap-3 p-4 hover:border-indigo-200" to="/company/connections">
                <span className="flex size-10 items-center justify-center rounded-xl bg-violet-50 text-violet-600"><FiLink /></span>
                <div>
                  <p className="text-sm font-bold text-slate-900">Connections</p>
                  <p className="text-xs text-slate-500">Manage channel setup</p>
                </div>
              </Link>
            )}
          </div>
        </section>

        <footer className="mt-6 flex flex-wrap items-center gap-2 text-[11px] text-slate-400">
          <FiClock aria-hidden="true" />
          Dashboard data is loaded from the selected workspace and refreshed on demand.
          {activities[0] && <span>Latest activity: {formatDateTime(activities[0].at)}</span>}
        </footer>
      </div>
    </section>
  )
}
