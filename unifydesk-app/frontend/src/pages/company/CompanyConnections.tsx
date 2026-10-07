import { useEffect, useMemo, useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import {
  FiBarChart2,
  FiCheckCircle,
  FiExternalLink,
  FiLink,
  FiMail,
  FiMessageCircle,
  FiRefreshCw,
  FiSmartphone,
  FiSlash,
} from 'react-icons/fi'
import ConnectionRetentionNotice from '../../components/channel/ConnectionRetentionNotice'
import DeleteChannelDataModal from '../../components/channel/DeleteChannelDataModal'
import DisconnectChannelModal from '../../components/channel/DisconnectChannelModal'
import WhatsAppDisconnectModal from '../../components/whatsapp/WhatsAppDisconnectModal'
import EmailProviderPicker from '../../components/email/EmailProviderPicker'
import AmazonSesConnectModal from '../../components/broadcast/AmazonSesConnectModal'
import AzureEmailConnectModal from '../../components/broadcast/AzureEmailConnectModal'
import BroadcastEmailProviderPickerModal, {
  type BroadcastEmailProviderChoice,
} from '../../components/broadcast/BroadcastEmailProviderPickerModal'
import SendGridConnectModal from '../../components/broadcast/SendGridConnectModal'
import BroadcastTestEmailModal from '../../components/broadcast/BroadcastTestEmailModal'
import ManagedEmailModal from '../../components/managed-email/ManagedEmailModal'
import ManagedEmailPanel from '../../components/managed-email/ManagedEmailPanel'
import ManagedSmsPanel from '../../components/managed-sms/ManagedSmsPanel'
import ManagedSmsSetupModal from '../../components/managed-sms/ManagedSmsSetupModal'
import {
  broadcastSenderProviderLabel,
  managedConnectionDisplayName,
} from '../../features/managedEmail/privacy'
import Msg91ConnectModal from '../../components/broadcast/Msg91ConnectModal'
import Msg91SmsSetupGuideModal from '../../components/broadcast/Msg91SmsSetupGuideModal'
import Msg91EmailConnectModal from '../../components/broadcast/Msg91EmailConnectModal'
import { broadcastApi } from '../../features/broadcast/broadcastApi'
import { adCenterApi } from '../../features/ads/adCenterApi'
import type {
  AdAccount,
  AdProvider,
  AdProviderConnection,
  AdProviderInfo,
} from '../../features/ads/types'
import type { BroadcastSender, BroadcastSetupSession } from '../../features/broadcast/types'
import { channelApi } from '../../features/channel/channelApi'
import { gmailApi } from '../../features/channel/gmailApi'
import { microsoftApi } from '../../features/channel/microsoftApi'
import { metaWhatsAppApi } from '../../features/channel/metaWhatsAppApi'
import { launchMetaWhatsAppSignup, loadMetaSdk } from '../../features/channel/metaWhatsAppSdk'
import type {
  ChannelConnection,
  DisconnectDataPolicy,
  MetaWhatsAppConnection,
  SenderIdentity,
} from '../../features/channel/types'
import { PERMISSIONS } from '../../features/iam/permissions'
import { useIam } from '../../features/iam/useIam'
import { apiErrorMessage } from '../../lib/api/apiClient'
import { useSnackbar } from '../../components/feedback/SnackbarProvider'
import { useAppSelector } from '../../store/hooks'
import { selectSelectedCompany } from '../../store/workspace/workspaceSlice'
import { ListSkeleton } from '../../components/ui/Skeleton'

const providerLabel = (
  providerType: ChannelConnection['providerType'],
  broadcastSender?: BroadcastSender | null,
) => {
  if (broadcastSender) return broadcastSenderProviderLabel(broadcastSender)
  if (providerType === 'MICROSOFT') return 'MICROSOFT 365'
  if (providerType === 'IMAP_SMTP') return 'IMAP + SMTP'
  if (providerType === 'SMTP') return 'SMTP'
  if (providerType === 'AMAZON_SES') return 'AMAZON SES'
  if (providerType === 'SENDGRID') return 'SENDGRID'
  if (providerType === 'AZURE_EMAIL') return 'AZURE EMAIL'
  if (providerType === 'MSG91_EMAIL') return 'MSG91 EMAIL'
  if (providerType === 'MSG91_SMS') return 'MSG91 SMS'
  return providerType
}

const statusClass: Record<ChannelConnection['status'], string> = {
  ACTIVE: 'bg-emerald-50 text-emerald-700',
  PENDING: 'bg-amber-50 text-amber-700',
  ERROR: 'bg-rose-50 text-rose-700',
  DISCONNECTED: 'bg-slate-100 text-slate-600',
}

const whatsAppStatusLabel: Record<MetaWhatsAppConnection['status'], string> = {
  CONNECTING: 'Connecting',
  VALIDATING: 'Validating',
  ACTIVE: 'Connected',
  DEGRADED: 'Temporarily degraded',
  ACTION_REQUIRED: 'Action required',
  REAUTH_REQUIRED: 'Authorization expired',
  DISCONNECTED: 'Disconnected',
  ERROR: 'Error',
}

const whatsAppStatusClass: Record<MetaWhatsAppConnection['status'], string> = {
  CONNECTING: 'bg-amber-50 text-amber-700',
  VALIDATING: 'bg-amber-50 text-amber-700',
  ACTIVE: 'bg-emerald-50 text-emerald-700',
  DEGRADED: 'bg-amber-50 text-amber-700',
  ACTION_REQUIRED: 'bg-rose-50 text-rose-700',
  REAUTH_REQUIRED: 'bg-rose-50 text-rose-700',
  DISCONNECTED: 'bg-slate-100 text-slate-600',
  ERROR: 'bg-rose-50 text-rose-700',
}

const whatsAppGenericStatusLabel: Record<ChannelConnection['status'], string> = {
  ACTIVE: 'Connected',
  PENDING: 'Connecting',
  ERROR: 'Error',
  DISCONNECTED: 'Disconnected',
}

type ConnectionSection = 'EMAIL' | 'SMS' | 'WHATSAPP' | 'ADS'

const sectionMeta: Array<{
  id: ConnectionSection
  label: string
  description: string
}> = [
  { id: 'EMAIL', label: 'Email', description: 'Inbox and broadcast sending' },
  { id: 'SMS', label: 'SMS', description: 'Managed DLT and provider sending' },
  { id: 'WHATSAPP', label: 'WhatsApp', description: 'Meta Business Platform' },
  { id: 'ADS', label: 'Ads', description: 'Google Ads and Meta Ads' },
]

export default function CompanyConnections() {
  const selectedCompany = useAppSelector(selectSelectedCompany)
  const [searchParams, setSearchParams] = useSearchParams()
  const { can } = useIam()
  const snackbar = useSnackbar()
  const [connections, setConnections] = useState<ChannelConnection[]>([])
  const [senders, setSenders] = useState<Record<string, SenderIdentity[]>>({})
  const [broadcastSenders, setBroadcastSenders] = useState<BroadcastSender[]>([])
  const [metaConnection, setMetaConnection] = useState<MetaWhatsAppConnection | null>(null)
  const [metaHealthLoading, setMetaHealthLoading] = useState(false)
  const [loading, setLoading] = useState(true)
  const [connecting, setConnecting] = useState(false)
  const [broadcastEmailPickerOpen, setBroadcastEmailPickerOpen] = useState(false)
  const [managedEmailOpen, setManagedEmailOpen] = useState(false)
  const [managedEmailDomainId, setManagedEmailDomainId] = useState<string | null>(null)
  const [managedEmailRefreshToken, setManagedEmailRefreshToken] = useState(0)
  const [managedSmsOpen, setManagedSmsOpen] = useState(false)
  const [managedSmsRefreshToken, setManagedSmsRefreshToken] = useState(0)
  const [sesConnectOpen, setSesConnectOpen] = useState(false)
  const [sendGridConnectOpen, setSendGridConnectOpen] = useState(false)
  const [azureEmailConnectOpen, setAzureEmailConnectOpen] = useState(false)
  const [msg91ConnectOpen, setMsg91ConnectOpen] = useState(false)
  const [msg91SmsSetupOpen, setMsg91SmsSetupOpen] = useState(false)
  const [msg91EmailConnectOpen, setMsg91EmailConnectOpen] = useState(false)
  const [activeEmailSetup, setActiveEmailSetup] = useState<BroadcastSetupSession | null>(null)
  const [activeSmsSetup, setActiveSmsSetup] = useState<BroadcastSetupSession | null>(null)
  const [testEmailSender, setTestEmailSender] = useState<BroadcastSender | null>(null)
  const [workingConnectionId, setWorkingConnectionId] = useState<string | null>(null)
  const [disconnectTarget, setDisconnectTarget] = useState<ChannelConnection | null>(null)
  const [deleteTarget, setDeleteTarget] = useState<ChannelConnection | null>(null)
  const [adProviders, setAdProviders] = useState<AdProviderInfo[]>([])
  const [adConnections, setAdConnections] = useState<AdProviderConnection[]>([])
  const [adAccounts, setAdAccounts] = useState<AdAccount[]>([])
  const [adsLoading, setAdsLoading] = useState(false)
  const [connectingAdProvider, setConnectingAdProvider] = useState<AdProvider | null>(null)

  const requestedSection = searchParams.get('section')?.toUpperCase()
  const activeSection: ConnectionSection =
    requestedSection === 'SMS' ||
    requestedSection === 'WHATSAPP' ||
    requestedSection === 'ADS'
      ? requestedSection
      : 'EMAIL'

  const selectSection = (section: ConnectionSection) => {
    setSearchParams((current) => {
      const next = new URLSearchParams(current)
      next.set('section', section.toLowerCase())
      return next
    })
  }

  const whatsAppConnections = useMemo(
    () => connections.filter((connection) => connection.channelType === 'WHATSAPP'),
    [connections],
  )
  const emailConnections = useMemo(
    () => connections.filter((connection) => connection.channelType === 'EMAIL'),
    [connections],
  )
  const smsConnections = useMemo(
    () => connections.filter((connection) => connection.channelType === 'SMS'),
    [connections],
  )

  const loadAds = async () => {
    if (!selectedCompany || !can(PERMISSIONS.ADS_READ)) {
      setAdProviders([])
      setAdConnections([])
      setAdAccounts([])
      return
    }

    setAdsLoading(true)
    try {
      const [providers, providerConnections, accounts] = await Promise.all([
        adCenterApi.providers(selectedCompany.companyId),
        adCenterApi.connections(selectedCompany.companyId),
        adCenterApi.accounts(selectedCompany.companyId),
      ])
      setAdProviders(providers)
      setAdConnections(providerConnections)
      setAdAccounts(accounts)
    } catch {
      setAdProviders([])
      setAdConnections([])
      setAdAccounts([])
    } finally {
      setAdsLoading(false)
    }
  }

  const connectAdProvider = async (provider: AdProviderInfo) => {
    if (
      !selectedCompany ||
      !provider.configured ||
      !can(PERMISSIONS.ADS_CONNECT) ||
      connectingAdProvider
    ) {
      return
    }

    setConnectingAdProvider(provider.provider)
    try {
      const authorization =
        provider.provider === 'META_ADS'
          ? await adCenterApi.authorizeMeta(selectedCompany.companyId)
          : await adCenterApi.authorizeGoogle(selectedCompany.companyId)
      window.location.assign(authorization.authorizationUrl)
    } catch (connectError) {
      snackbar.error(
        apiErrorMessage(connectError, `Unable to start ${provider.displayName} connection`),
      )
      setConnectingAdProvider(null)
    }
  }

  const loadConnections = async () => {
    if (!selectedCompany) return
    setLoading(true)
    try {
      const visible = await channelApi.list(selectedCompany.companyId)
      setConnections(visible)

      const readableConnections = visible.filter(
        (connection) =>
          (connection.channelType === 'WHATSAPP' && can(PERMISSIONS.WHATSAPP_READ)) ||
          (connection.channelType === 'EMAIL' &&
            (can(PERMISSIONS.EMAIL_READ) || can(PERMISSIONS.BROADCAST_READ))) ||
          (connection.channelType === 'SMS' && can(PERMISSIONS.BROADCAST_READ)),
      )
      const senderEntries = await Promise.all(
        readableConnections.map(async (connection) => {
          try {
            return [
              connection.id,
              await channelApi.senders(selectedCompany.companyId, connection.id),
            ] as const
          } catch {
            return [connection.id, [] as SenderIdentity[]] as const
          }
        }),
      )
      setSenders(Object.fromEntries(senderEntries))

      if (can(PERMISSIONS.BROADCAST_READ)) {
        try {
          setBroadcastSenders(await broadcastApi.senders(selectedCompany.companyId))
        } catch {
          setBroadcastSenders([])
        }
      } else {
        setBroadcastSenders([])
      }
    } catch (loadError) {
      snackbar.error(apiErrorMessage(loadError, 'Unable to load channel connections'))
    } finally {
      setLoading(false)
    }
  }

  const loadBroadcastSetup = async () => {
    if (!selectedCompany || !can(PERMISSIONS.BROADCAST_READ)) {
      setActiveEmailSetup(null)
      setActiveSmsSetup(null)
      return
    }
    try {
      const [emailSetup, smsSetup] = await Promise.all([
        broadcastApi.activeSetupSession(selectedCompany.companyId, 'EMAIL'),
        broadcastApi.activeSetupSession(selectedCompany.companyId, 'SMS'),
      ])
      setActiveEmailSetup(emailSetup)
      setActiveSmsSetup(smsSetup)
    } catch {
      setActiveEmailSetup(null)
      setActiveSmsSetup(null)
    }
  }

  const loadMetaConnection = async () => {
    if (!selectedCompany || !can(PERMISSIONS.WHATSAPP_READ)) {
      setMetaConnection(null)
      return
    }

    setMetaHealthLoading(true)
    try {
      setMetaConnection(await metaWhatsAppApi.connection(selectedCompany.companyId))
    } catch {
      setMetaConnection(null)
    } finally {
      setMetaHealthLoading(false)
    }
  }

  useEffect(() => {
    void loadConnections()
    void loadBroadcastSetup()
    void loadMetaConnection()
    void loadAds()
  }, [selectedCompany?.companyId])

  useEffect(() => {
    const url = new URL(window.location.href)
    const connectProvider = url.searchParams.get('connect')
    if (connectProvider === 'broadcast-email' && can(PERMISSIONS.BROADCAST_MODIFY)) {
      setBroadcastEmailPickerOpen(true)
      url.searchParams.set('section', 'email')
      url.searchParams.delete('connect')
      window.history.replaceState({}, '', `${url.pathname}${url.search}${url.hash}`)
    } else if (connectProvider === 'amazon-ses' && can(PERMISSIONS.BROADCAST_MODIFY)) {
      setSesConnectOpen(true)
      url.searchParams.set('section', 'email')
      url.searchParams.delete('connect')
      window.history.replaceState({}, '', `${url.pathname}${url.search}${url.hash}`)
    } else if (connectProvider === 'sendgrid' && can(PERMISSIONS.BROADCAST_MODIFY)) {
      setSendGridConnectOpen(true)
      url.searchParams.set('section', 'email')
      url.searchParams.delete('connect')
      window.history.replaceState({}, '', `${url.pathname}${url.search}${url.hash}`)
    } else if (connectProvider === 'azure-email' && can(PERMISSIONS.BROADCAST_MODIFY)) {
      setAzureEmailConnectOpen(true)
      url.searchParams.set('section', 'email')
      url.searchParams.delete('connect')
      window.history.replaceState({}, '', `${url.pathname}${url.search}${url.hash}`)
    } else if (
      (connectProvider === 'msg91' ||
        connectProvider === 'msg91-sms' ||
        connectProvider === 'broadcast-sms') &&
      can(PERMISSIONS.BROADCAST_MODIFY)
    ) {
      setMsg91SmsSetupOpen(true)
      url.searchParams.set('section', 'sms')
      url.searchParams.delete('connect')
      window.history.replaceState({}, '', `${url.pathname}${url.search}${url.hash}`)
    } else if (connectProvider === 'msg91-email' && can(PERMISSIONS.BROADCAST_MODIFY)) {
      setMsg91EmailConnectOpen(true)
      url.searchParams.set('section', 'email')
      url.searchParams.delete('connect')
      window.history.replaceState({}, '', `${url.pathname}${url.search}${url.hash}`)
    }

    const gmailResult = url.searchParams.get('gmail')
    const microsoftResult = url.searchParams.get('microsoft')

    if (gmailResult) {
      if (gmailResult === 'connected') {
        snackbar.success('Gmail connected successfully')
      } else if (gmailResult === 'cancelled') {
        snackbar.info('Gmail connection was cancelled')
      } else {
        snackbar.error('Unable to connect Gmail. Please try again.')
      }
      url.searchParams.delete('gmail')
    }

    if (microsoftResult) {
      if (microsoftResult === 'connected') {
        snackbar.success('Microsoft 365 connected successfully')
      } else if (microsoftResult === 'cancelled') {
        snackbar.info('Microsoft 365 connection was cancelled')
      } else {
        snackbar.error('Unable to connect Microsoft 365. Please try again.')
      }
      url.searchParams.delete('microsoft')
    }

    if (gmailResult || microsoftResult) {
      window.history.replaceState({}, '', `${url.pathname}${url.search}${url.hash}`)
      void loadConnections()
    }
  }, [])

  const refreshAll = async () => {
    await Promise.all([
      loadConnections(),
      loadBroadcastSetup(),
      loadMetaConnection(),
      loadAds(),
    ])
  }

  const connectWhatsApp = async (reauthorize = false) => {
    if (!selectedCompany || connecting || !can(PERMISSIONS.WHATSAPP_CONNECT)) return

    setConnecting(true)
    try {
      const session = reauthorize
        ? await metaWhatsAppApi.reauthorizeSession(selectedCompany.companyId)
        : await metaWhatsAppApi.signupSession(selectedCompany.companyId)

      await loadMetaSdk(session.appId, session.graphApiVersion)
      const result = await launchMetaWhatsAppSignup(session)
      const connection = await metaWhatsAppApi.complete(
        selectedCompany.companyId,
        session.state,
        result,
      )

      setMetaConnection(connection)
      if (connection.status === 'ACTIVE') {
        snackbar.success(
          connection.phone?.displayPhoneNumber
            ? `WhatsApp ${connection.phone.displayPhoneNumber} is connected.`
            : 'WhatsApp is connected.',
        )
      } else if (connection.status === 'ACTION_REQUIRED') {
        snackbar.warning('WhatsApp is connected, but Meta requires an additional action.')
      } else if (connection.status === 'REAUTH_REQUIRED') {
        snackbar.warning('WhatsApp authorization still needs to be renewed.')
      } else {
        snackbar.info('WhatsApp setup is continuing. Refresh status to check progress.')
      }

      await loadConnections()
      await loadMetaConnection()
    } catch (connectError) {
      snackbar.error(apiErrorMessage(connectError, 'Unable to connect WhatsApp'))
    } finally {
      setConnecting(false)
    }
  }

  const disconnect = async (connection: ChannelConnection, dataPolicy: DisconnectDataPolicy) => {
    if (!selectedCompany) return

    setWorkingConnectionId(connection.id)
    try {
      if (connection.providerType === 'GMAIL') {
        await gmailApi.disconnect(selectedCompany.companyId, connection.id, dataPolicy)
      } else if (connection.providerType === 'MICROSOFT') {
        await microsoftApi.disconnect(selectedCompany.companyId, connection.id, dataPolicy)
      } else if (connection.providerType === 'META_WHATSAPP') {
        await metaWhatsAppApi.disconnect(selectedCompany.companyId, dataPolicy)
      } else {
        await channelApi.disconnect(selectedCompany.companyId, connection.id, dataPolicy)
      }

      snackbar.success(
        dataPolicy === 'DELETE_NOW'
          ? 'Channel disconnected. Permanent deletion of imported data is queued.'
          : 'Channel disconnected. Imported data will be kept for 7 days unless you reconnect or delete it sooner.',
      )
      setDisconnectTarget(null)
      await loadConnections()
      if (connection.providerType === 'META_WHATSAPP') await loadMetaConnection()
    } catch (disconnectError) {
      snackbar.error(apiErrorMessage(disconnectError, 'Unable to disconnect channel'))
    } finally {
      setWorkingConnectionId(null)
    }
  }

  const deleteDisconnectedDataNow = async (connection: ChannelConnection) => {
    if (!selectedCompany || connection.status !== 'DISCONNECTED') return
    setWorkingConnectionId(connection.id)
    try {
      if (connection.providerType === 'META_WHATSAPP') {
        await metaWhatsAppApi.disconnect(selectedCompany.companyId, 'DELETE_NOW')
      } else {
        await channelApi.disconnect(selectedCompany.companyId, connection.id, 'DELETE_NOW')
      }
      snackbar.success('Permanent deletion of the disconnected channel data is queued.')
      setDeleteTarget(null)
      await loadConnections()
    } catch (deleteError) {
      snackbar.error(apiErrorMessage(deleteError, 'Unable to queue channel data deletion'))
    } finally {
      setWorkingConnectionId(null)
    }
  }

  const broadcastSenderFor = (connectionId: string) =>
    broadcastSenders.find((sender) => sender.channelConnectionId === connectionId) ?? null

  const reconnectBroadcast = (connection: ChannelConnection) => {
    if (workingConnectionId || connection.status !== 'DISCONNECTED') return

    const broadcastSender = broadcastSenderFor(connection.id)
    if (broadcastSender?.managementMode === 'MESSAGING_MANAGED') {
      setManagedEmailDomainId(null)
      setManagedEmailOpen(true)
      return
    }

    if (connection.providerType === 'AMAZON_SES') {
      setSesConnectOpen(true)
      return
    }
    if (connection.providerType === 'SENDGRID') {
      setSendGridConnectOpen(true)
      return
    }
    if (connection.providerType === 'AZURE_EMAIL') {
      setAzureEmailConnectOpen(true)
      return
    }
    if (connection.providerType === 'MSG91_EMAIL') {
      setMsg91EmailConnectOpen(true)
      return
    }
    if (connection.providerType === 'MSG91_SMS') {
      setMsg91ConnectOpen(true)
    }
  }

  const customerEmailConnections = emailConnections.filter(
    (connection) => broadcastSenderFor(connection.id)?.managementMode !== 'MESSAGING_MANAGED',
  )
  const customerSmsConnections = smsConnections.filter(
    (connection) => broadcastSenderFor(connection.id)?.managementMode !== 'MESSAGING_MANAGED',
  )

  const reconnectEmail = async (connection: ChannelConnection) => {
    if (!selectedCompany || workingConnectionId) return
    setWorkingConnectionId(connection.id)
    try {
      if (connection.providerType === 'GMAIL') {
        const authorization = await gmailApi.authorize(selectedCompany.companyId)
        window.location.assign(authorization.authorizationUrl)
        return
      }
      if (connection.providerType === 'MICROSOFT') {
        const authorization = await microsoftApi.authorize(selectedCompany.companyId)
        window.location.assign(authorization.authorizationUrl)
        return
      }
      snackbar.info('Use Connect email above and choose Other email to reconnect this mailbox.')
    } catch (reconnectError) {
      snackbar.error(apiErrorMessage(reconnectError, 'Unable to reconnect email account'))
    } finally {
      setWorkingConnectionId(null)
    }
  }

  if (!selectedCompany) {
    return (
      <section className="px-4 py-6 sm:px-6 lg:px-8">
        <div className="panel mx-auto max-w-5xl p-8 text-center text-sm text-slate-500">
          Select a workspace to manage channel connections.
        </div>
      </section>
    )
  }

  return (
    <section className="px-4 py-6 sm:px-6 lg:px-8">
      <div className="w-full">
        <header className="flex flex-wrap items-end justify-between gap-4">
          <div>
            <p className="eyebrow">Company</p>
            <h1 className="mt-2 text-2xl font-bold tracking-tight text-slate-950 sm:text-3xl">
              Connections
            </h1>
            <p className="mt-2 max-w-3xl text-sm leading-6 text-slate-500">
              Set up every channel from one place. UnifyDesk-managed Email and SMS are the
              recommended paths; you can still connect your own providers and advertising accounts.
            </p>
          </div>
          <button
            type="button"
            className="secondary-button inline-flex items-center gap-2"
            onClick={() => void refreshAll()}
            disabled={loading || adsLoading}
          >
            <FiRefreshCw aria-hidden="true" />
            Refresh
          </button>
        </header>

        <div className="mt-7 grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
          {sectionMeta.map((section) => {
            const count =
              section.id === 'EMAIL'
                ? emailConnections.filter((item) => item.status === 'ACTIVE').length
                : section.id === 'SMS'
                  ? smsConnections.filter((item) => item.status === 'ACTIVE').length
                  : section.id === 'WHATSAPP'
                    ? whatsAppConnections.filter((item) => item.status === 'ACTIVE').length
                    : adConnections.filter((item) => item.status === 'ACTIVE').length
            const icon =
              section.id === 'EMAIL' ? (
                <FiMail aria-hidden="true" />
              ) : section.id === 'SMS' ? (
                <FiSmartphone aria-hidden="true" />
              ) : section.id === 'WHATSAPP' ? (
                <FiMessageCircle aria-hidden="true" />
              ) : (
                <FiBarChart2 aria-hidden="true" />
              )

            return (
              <button
                key={section.id}
                type="button"
                aria-pressed={activeSection === section.id}
                onClick={() => selectSection(section.id)}
                className={`rounded-2xl border p-4 text-left transition ${
                  activeSection === section.id
                    ? 'border-indigo-300 bg-indigo-50/70 shadow-sm'
                    : 'border-slate-200 bg-white hover:border-slate-300 hover:bg-slate-50'
                }`}
              >
                <div className="flex items-start justify-between gap-3">
                  <span
                    className={`flex size-10 items-center justify-center rounded-xl text-lg ${
                      activeSection === section.id
                        ? 'bg-indigo-100 text-indigo-700'
                        : 'bg-slate-100 text-slate-600'
                    }`}
                  >
                    {icon}
                  </span>
                  <span className="rounded-full bg-slate-100 px-2.5 py-1 text-[10px] font-bold text-slate-600">
                    {count} connected
                  </span>
                </div>
                <p className="mt-3 text-sm font-bold text-slate-950">{section.label}</p>
                <p className="mt-1 text-xs leading-5 text-slate-500">{section.description}</p>
              </button>
            )
          })}
        </div>

        {activeSection === 'EMAIL' && (
          <>
            <div className="mt-7 flex flex-wrap items-end justify-between gap-4">
              <div>
                <p className="eyebrow">EMAIL</p>
                <h2 className="mt-2 text-xl font-bold text-slate-950">Email connections</h2>
                <p className="mt-1 text-sm text-slate-500">
                  Start with UnifyDesk Managed Email for broadcasts, then connect Inbox mailboxes or
                  your own broadcast provider when needed.
                </p>
              </div>
            </div>

            {can(PERMISSIONS.BROADCAST_READ) && (
              <ManagedEmailPanel
                companyId={selectedCompany.companyId}
                canModify={can(PERMISSIONS.BROADCAST_MODIFY)}
                refreshToken={managedEmailRefreshToken}
                onOpenSetup={(domainId) => {
                  setManagedEmailDomainId(domainId ?? null)
                  setManagedEmailOpen(true)
                }}
              />
            )}

            <div className="panel mt-6 p-5">
              <div className="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
                <div>
                  <h3 className="text-sm font-bold text-slate-950">Connect your own email accounts</h3>
                  <p className="mt-1 text-xs leading-5 text-slate-500">
                    Gmail, Microsoft 365 and IMAP + SMTP power Inbox. SES, SendGrid, Azure Email and
                    MSG91 Email are available for customer-managed broadcast sending.
                  </p>
                </div>
                <div className="flex flex-wrap gap-2">
                  {can(PERMISSIONS.EMAIL_CONNECT) && (
                    <EmailProviderPicker
                      companyId={selectedCompany.companyId}
                      label="Connect Inbox email"
                      className="secondary-button gap-2"
                      onError={(message) => snackbar.error(message)}
                      onConnected={(connection) => {
                        snackbar.success(`Connected ${connection.email}`)
                        void loadConnections()
                      }}
                    />
                  )}
                  {can(PERMISSIONS.BROADCAST_MODIFY) && (
                    <button
                      type="button"
                      className="secondary-button gap-2"
                      onClick={() => setBroadcastEmailPickerOpen(true)}
                    >
                      <FiLink aria-hidden="true" />
                      {activeEmailSetup ? 'Continue provider setup' : 'Connect broadcast provider'}
                    </button>
                  )}
                </div>
              </div>
            </div>

        <div className="panel mt-6 overflow-hidden">
          <div className="border-b border-slate-100 px-5 py-4">
            <h2 className="text-sm font-bold text-slate-950">Your email accounts and providers</h2>
            <p className="mt-1 text-xs text-slate-500">
              Customer-connected Inbox and broadcast providers. Managed Email is shown separately
              above.
            </p>
          </div>

          {loading ? (
            <div className="p-4"><ListSkeleton rows={3} /></div>
          ) : customerEmailConnections.length === 0 ? (
            <div className="px-5 py-12 text-center">
              <FiMail className="mx-auto text-slate-300" size={28} />
              <p className="mt-3 text-sm font-semibold text-slate-800">No customer-managed email accounts</p>
              <p className="mt-1 text-xs text-slate-500">
                Use the options above to connect an Inbox mailbox or your own broadcast provider.
              </p>
            </div>
          ) : (
            <div className="divide-y divide-slate-100">
              {customerEmailConnections.map((connection) => (
                <div
                  key={connection.id}
                  className="flex flex-col gap-4 px-5 py-4 lg:flex-row lg:items-center lg:justify-between"
                >
                  <div className="min-w-0">
                    <div className="flex flex-wrap items-center gap-2">
                      <p className="text-sm font-bold text-slate-950">
                        {managedConnectionDisplayName(
                          broadcastSenderFor(connection.id),
                          connection.name,
                        )}
                      </p>
                      <span
                        className={`rounded-full px-2.5 py-1 text-[10px] font-bold ${
                          statusClass[connection.status]
                        }`}
                      >
                        {connection.status}
                      </span>
                      <span className="rounded-full bg-sky-50 px-2.5 py-1 text-[10px] font-bold text-sky-700">
                        {providerLabel(connection.providerType, broadcastSenderFor(connection.id))}
                      </span>
                      {connection.capabilities.map((capability) => (
                        <span
                          key={capability}
                          className={
                            capability === 'BROADCAST'
                              ? 'rounded-full bg-violet-50 px-2.5 py-1 text-[10px] font-bold text-violet-700'
                              : 'rounded-full bg-emerald-50 px-2.5 py-1 text-[10px] font-bold text-emerald-700'
                          }
                        >
                          {capability === 'BROADCAST' ? 'Broadcast' : 'Inbox'}
                        </span>
                      ))}
                    </div>
                    <div className="mt-2 flex flex-wrap gap-2">
                      {(senders[connection.id] ?? []).map((sender) => (
                        <span
                          key={sender.id}
                          className="inline-flex items-center gap-1.5 rounded-full bg-slate-100 px-2.5 py-1 text-xs font-semibold text-slate-700"
                        >
                          <FiCheckCircle className="text-emerald-500" aria-hidden="true" />
                          {sender.value}
                          {sender.defaultIdentity ? ' · Default' : ''}
                        </span>
                      ))}
                    </div>
                  </div>

                  <div className="flex w-full shrink-0 flex-wrap gap-2 lg:w-auto lg:max-w-md">
                    {connection.status === 'ACTIVE' &&
                      connection.capabilities.includes('BROADCAST') &&
                      can(PERMISSIONS.BROADCAST_MODIFY) &&
                      broadcastSenderFor(connection.id)?.sendingEnabled && (
                        <button
                          type="button"
                          className="secondary-button inline-flex w-full items-center gap-2 sm:w-auto"
                          onClick={() => setTestEmailSender(broadcastSenderFor(connection.id))}
                        >
                          <FiMail aria-hidden="true" />
                          Test email
                        </button>
                      )}

                    {connection.status !== 'DISCONNECTED' &&
                      broadcastSenderFor(connection.id)?.managementMode !== 'MESSAGING_MANAGED' &&
                      (connection.capabilities.includes('BROADCAST')
                        ? can(PERMISSIONS.BROADCAST_MODIFY)
                        : can(PERMISSIONS.EMAIL_DISCONNECT)) && (
                        <button
                          type="button"
                          className="secondary-button inline-flex w-full items-center gap-2 text-rose-600 sm:w-auto"
                          disabled={workingConnectionId === connection.id}
                          onClick={() => setDisconnectTarget(connection)}
                        >
                          <FiSlash aria-hidden="true" />
                          Disconnect
                        </button>
                      )}

                    <ConnectionRetentionNotice
                      connection={connection}
                      working={workingConnectionId === connection.id}
                      onReconnect={
                        connection.providerType === 'GMAIL' ||
                        connection.providerType === 'MICROSOFT'
                          ? () => void reconnectEmail(connection)
                          : connection.capabilities.includes('BROADCAST') &&
                              broadcastSenderFor(connection.id)?.managementMode !==
                                'MESSAGING_MANAGED' &&
                              can(PERMISSIONS.BROADCAST_MODIFY)
                            ? () => reconnectBroadcast(connection)
                            : undefined
                      }
                      onDeleteNow={() => setDeleteTarget(connection)}
                    />
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>

          </>
        )}

        {activeSection === 'SMS' && (
          <>
            <div className="mt-7">
              <p className="eyebrow">SMS</p>
              <h2 className="mt-2 text-xl font-bold text-slate-950">SMS connections</h2>
              <p className="mt-1 text-sm text-slate-500">
                Use UnifyDesk Managed SMS first, or bring your own MSG91 account when you need
                direct provider ownership.
              </p>
            </div>

            {can(PERMISSIONS.BROADCAST_READ) && (
              <ManagedSmsPanel
                companyId={selectedCompany.companyId}
                canModify={can(PERMISSIONS.BROADCAST_MODIFY)}
                refreshToken={managedSmsRefreshToken}
                onOpenSetup={() => setManagedSmsOpen(true)}
              />
            )}

            {can(PERMISSIONS.BROADCAST_MODIFY) && (
              <div className="panel mt-6 p-5">
                <div className="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
                  <div>
                    <h3 className="text-sm font-bold text-slate-950">Use your own MSG91 account</h3>
                    <p className="mt-1 text-xs leading-5 text-slate-500">
                      Keep provider credentials and account ownership under your control.
                    </p>
                  </div>
                  <button
                    type="button"
                    className="secondary-button gap-2"
                    onClick={() => setMsg91SmsSetupOpen(true)}
                  >
                    <FiLink aria-hidden="true" />
                    {activeSmsSetup ? 'Continue MSG91 setup' : 'Connect MSG91'}
                  </button>
                </div>
              </div>
            )}

        <div className="panel mt-6 overflow-hidden">
          <div className="border-b border-slate-100 px-5 py-4">
            <h2 className="text-sm font-bold text-slate-950">Your SMS providers</h2>
            <p className="mt-1 text-xs text-slate-500">
              Customer-managed SMS connections. UnifyDesk Managed SMS is shown separately above.
            </p>
          </div>

          {loading ? (
            <div className="p-4"><ListSkeleton rows={3} /></div>
          ) : customerSmsConnections.length === 0 ? (
            <div className="px-5 py-12 text-center">
              <FiSmartphone className="mx-auto text-slate-300" size={28} />
              <p className="mt-3 text-sm font-semibold text-slate-800">No customer-managed SMS provider</p>
              <p className="mt-1 text-xs text-slate-500">
                Managed SMS is available above. Connect your own MSG91 account only when needed.
              </p>
            </div>
          ) : (
            <div className="divide-y divide-slate-100">
              {customerSmsConnections.map((connection) => (
                <div
                  key={connection.id}
                  className="flex flex-col gap-4 px-5 py-4 lg:flex-row lg:items-center lg:justify-between"
                >
                  <div className="min-w-0">
                    <div className="flex flex-wrap items-center gap-2">
                      <p className="text-sm font-bold text-slate-950">
                        {broadcastSenderFor(connection.id)?.managementMode === 'MESSAGING_MANAGED'
                          ? 'UnifyDesk Managed SMS'
                          : connection.name}
                      </p>
                      <span
                        className={`rounded-full px-2.5 py-1 text-[10px] font-bold ${statusClass[connection.status]}`}
                      >
                        {connection.status}
                      </span>
                      <span className="rounded-full bg-violet-50 px-2.5 py-1 text-[10px] font-bold text-violet-700">
                        {broadcastSenderFor(connection.id)?.managementMode === 'MESSAGING_MANAGED'
                          ? 'UnifyDesk Managed SMS'
                          : providerLabel(connection.providerType)}
                      </span>
                      <span className="rounded-full bg-violet-50 px-2.5 py-1 text-[10px] font-bold text-violet-700">
                        Broadcast
                      </span>
                    </div>
                    <p className="mt-1 text-xs text-slate-500">
                      {broadcastSenderFor(connection.id)?.managementMode === 'MESSAGING_MANAGED'
                        ? 'Approved Header managed through UnifyDesk'
                        : `Flow ${connection.externalAccountId ?? 'Configured'}`}
                    </p>
                    <div className="mt-2 flex flex-wrap gap-2">
                      {(senders[connection.id] ?? []).map((sender) => (
                        <span
                          key={sender.id}
                          className="inline-flex items-center gap-1.5 rounded-full bg-slate-100 px-2.5 py-1 text-xs font-semibold text-slate-700"
                        >
                          <FiCheckCircle className="text-emerald-500" aria-hidden="true" />
                          {sender.value}
                        </span>
                      ))}
                    </div>
                  </div>
                  <div className="w-full shrink-0 lg:w-auto lg:max-w-md">
                    {connection.status !== 'DISCONNECTED' &&
                      broadcastSenderFor(connection.id)?.managementMode !== 'MESSAGING_MANAGED' &&
                      can(PERMISSIONS.BROADCAST_MODIFY) && (
                        <button
                          type="button"
                          className="secondary-button inline-flex shrink-0 items-center gap-2 text-rose-600"
                          disabled={workingConnectionId === connection.id}
                          onClick={() => setDisconnectTarget(connection)}
                        >
                          <FiSlash aria-hidden="true" />
                          Disconnect
                        </button>
                      )}

                    <ConnectionRetentionNotice
                      connection={connection}
                      working={workingConnectionId === connection.id}
                      onReconnect={
                        connection.providerType === 'MSG91_SMS' &&
                        broadcastSenderFor(connection.id)?.managementMode !== 'MESSAGING_MANAGED' &&
                        can(PERMISSIONS.BROADCAST_MODIFY)
                          ? () => reconnectBroadcast(connection)
                          : undefined
                      }
                      onDeleteNow={() => setDeleteTarget(connection)}
                    />
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>

          </>
        )}

        {activeSection === 'WHATSAPP' && (
          <>
            <div className="mt-7 flex flex-col gap-4 lg:flex-row lg:items-end lg:justify-between">
              <div>
                <p className="eyebrow">WHATSAPP</p>
                <h2 className="mt-2 text-xl font-bold text-slate-950">WhatsApp Business</h2>
                <p className="mt-1 text-sm text-slate-500">
                  Connect Meta WhatsApp Business Platform using Embedded Signup.
                </p>
              </div>
              {can(PERMISSIONS.WHATSAPP_CONNECT) && (
                <button
                  type="button"
                  className="primary-button gap-2"
                  onClick={() => void connectWhatsApp(false)}
                  disabled={connecting}
                >
                  <FiLink aria-hidden="true" />
                  {connecting
                    ? 'Connecting…'
                    : whatsAppConnections.some((item) => item.status === 'ACTIVE')
                      ? 'Connect another WhatsApp'
                      : 'Connect WhatsApp'}
                </button>
              )}
            </div>

        <div className="panel mt-6 overflow-hidden">
          <div className="border-b border-slate-100 px-5 py-4">
            <h2 className="text-sm font-bold text-slate-950">WhatsApp Business accounts</h2>
            <p className="mt-1 text-xs text-slate-500">
              Each connected WABA is stored as a channel connection and its phone number as a sender
              identity.
            </p>
          </div>

          {loading ? (
            <div className="p-4"><ListSkeleton rows={3} /></div>
          ) : whatsAppConnections.length === 0 ? (
            <div className="px-5 py-12 text-center">
              <FiMessageCircle className="mx-auto text-slate-300" size={28} />
              <p className="mt-3 text-sm font-semibold text-slate-800">
                No WhatsApp account connected
              </p>
              <p className="mt-1 text-xs text-slate-500">
                Use Connect WhatsApp to launch Meta Embedded Signup.
              </p>
            </div>
          ) : (
            <div className="divide-y divide-slate-100">
              {whatsAppConnections.map((connection) => (
                <div
                  key={connection.id}
                  className="flex flex-col gap-4 px-5 py-4 lg:flex-row lg:items-center lg:justify-between"
                >
                  <div className="min-w-0">
                    <div className="flex flex-wrap items-center gap-2">
                      <p className="text-sm font-bold text-slate-950">{connection.name}</p>
                      <span
                        className={`rounded-full px-2.5 py-1 text-[10px] font-bold ${
                          metaConnection?.connectionId === connection.id
                            ? whatsAppStatusClass[metaConnection.status]
                            : statusClass[connection.status]
                        }`}
                      >
                        {metaConnection?.connectionId === connection.id
                          ? whatsAppStatusLabel[metaConnection.status]
                          : whatsAppGenericStatusLabel[connection.status]}
                      </span>
                    </div>
                    <p className="mt-1 text-xs text-slate-500">
                      {metaConnection?.connectionId === connection.id
                        ? [
                            metaConnection.phone?.displayPhoneNumber,
                            metaConnection.phone?.verifiedName,
                            metaConnection.phone?.qualityRating
                              ? `Quality ${metaConnection.phone.qualityRating}`
                              : null,
                          ]
                            .filter(Boolean)
                            .join(' · ') || 'WhatsApp Business connection'
                        : `WABA ${connection.externalAccountId ?? 'Pending'}`}
                    </p>
                    {metaConnection?.connectionId === connection.id && (
                      <div className="mt-2 flex flex-wrap items-center gap-2 text-[11px]">
                        {metaConnection.authState && (
                          <span className="text-slate-500">
                            Authorization: {metaConnection.authState.replaceAll('_', ' ')}
                          </span>
                        )}
                        {metaConnection.lastValidatedAt && (
                          <span className="text-slate-400">
                            Checked {new Date(metaConnection.lastValidatedAt).toLocaleString()}
                          </span>
                        )}
                        {metaConnection.status === 'DEGRADED' && (
                          <span className="font-semibold text-amber-700">Temporarily degraded</span>
                        )}
                        {metaConnection.status === 'ACTION_REQUIRED' && (
                          <span className="font-semibold text-rose-700">Action required</span>
                        )}
                        {metaConnection.status === 'REAUTH_REQUIRED' && (
                          <span className="font-semibold text-rose-700">Authorization expired</span>
                        )}
                      </div>
                    )}
                    <div className="mt-2 flex flex-wrap gap-2">
                      {(senders[connection.id] ?? []).map((sender) => (
                        <span
                          key={sender.id}
                          className="inline-flex items-center gap-1.5 rounded-full bg-slate-100 px-2.5 py-1 text-xs font-semibold text-slate-700"
                        >
                          <FiCheckCircle className="text-emerald-500" aria-hidden="true" />
                          {sender.value}
                          {sender.defaultIdentity ? ' · Default' : ''}
                        </span>
                      ))}
                    </div>
                  </div>

                  <div className="flex w-full shrink-0 flex-wrap gap-2 lg:w-auto lg:max-w-md">
                    {metaConnection?.connectionId === connection.id &&
                      can(PERMISSIONS.WHATSAPP_MODIFY) && (
                        <button
                          type="button"
                          className="secondary-button inline-flex items-center gap-2"
                          disabled={metaHealthLoading || workingConnectionId === connection.id}
                          onClick={async () => {
                            if (!selectedCompany) return
                            setMetaHealthLoading(true)
                            try {
                              setMetaConnection(
                                await metaWhatsAppApi.reconcile(selectedCompany.companyId),
                              )
                            } catch (requestError) {
                              snackbar.error(
                                apiErrorMessage(requestError, 'Unable to refresh WhatsApp status'),
                              )
                            } finally {
                              setMetaHealthLoading(false)
                            }
                          }}
                        >
                          <FiRefreshCw aria-hidden="true" />
                          {metaHealthLoading ? 'Refreshing…' : 'Refresh status'}
                        </button>
                      )}
                    {metaConnection?.connectionId === connection.id &&
                      metaConnection.nextAction === 'REAUTHORIZE' &&
                      can(PERMISSIONS.WHATSAPP_CONNECT) && (
                        <button
                          type="button"
                          className="primary-button inline-flex items-center gap-2"
                          disabled={connecting}
                          onClick={() => void connectWhatsApp(true)}
                        >
                          <FiLink aria-hidden="true" />
                          Reauthorize
                        </button>
                      )}
                    {metaConnection?.connectionId === connection.id &&
                      metaConnection.nextAction === 'RETRY' &&
                      can(PERMISSIONS.WHATSAPP_CONNECT) && (
                        <button
                          type="button"
                          className="secondary-button inline-flex items-center gap-2"
                          disabled={connecting}
                          onClick={() => void connectWhatsApp(false)}
                        >
                          <FiRefreshCw aria-hidden="true" />
                          Retry
                        </button>
                      )}
                    {metaConnection?.connectionId === connection.id &&
                      metaConnection.nextAction === 'OPEN_META' && (
                        <a
                          className="secondary-button inline-flex items-center gap-2"
                          href="https://business.facebook.com/settings/whatsapp-business-accounts"
                          target="_blank"
                          rel="noreferrer"
                        >
                          <FiLink aria-hidden="true" />
                          Open Meta instructions
                        </a>
                      )}
                    {connection.status !== 'DISCONNECTED' &&
                      can(PERMISSIONS.WHATSAPP_DISCONNECT) && (
                        <button
                          type="button"
                          className="secondary-button inline-flex shrink-0 items-center gap-2 text-rose-600"
                          disabled={workingConnectionId === connection.id}
                          onClick={() => setDisconnectTarget(connection)}
                        >
                          <FiSlash aria-hidden="true" />
                          Disconnect
                        </button>
                      )}

                    <ConnectionRetentionNotice
                      connection={connection}
                      working={workingConnectionId === connection.id}
                      onReconnect={
                        connection.providerType === 'META_WHATSAPP'
                          ? () => void connectWhatsApp(true)
                          : undefined
                      }
                      onDeleteNow={() => setDeleteTarget(connection)}
                    />
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
          </>
        )}

        {activeSection === 'ADS' && (
          <>
            <div className="mt-7 flex flex-col gap-4 lg:flex-row lg:items-end lg:justify-between">
              <div>
                <p className="eyebrow">ADS</p>
                <h2 className="mt-2 text-xl font-bold text-slate-950">Advertising connections</h2>
                <p className="mt-1 text-sm text-slate-500">
                  Connect Google Ads and Meta Ads, then choose which advertiser accounts UnifyDesk
                  can manage.
                </p>
              </div>
              <Link className="secondary-button gap-2" to="/ad-center">
                <FiExternalLink aria-hidden="true" />
                Open Ad Center
              </Link>
            </div>

            {!can(PERMISSIONS.ADS_READ) ? (
              <div className="panel mt-4 p-8 text-center text-sm text-slate-500">
                You do not have permission to view advertising connections.
              </div>
            ) : adsLoading ? (
              <div className="panel mt-4 p-4">
                <ListSkeleton rows={4} />
              </div>
            ) : (
              <div className="mt-4 grid gap-4 xl:grid-cols-2">
                {adProviders.map((provider) => {
                  const providerConnection = adConnections.find(
                    (connection) =>
                      connection.provider === provider.provider &&
                      connection.status === 'ACTIVE',
                  )
                  const accounts = adAccounts.filter(
                    (account) => account.provider === provider.provider && account.accessible,
                  )
                  const managedAccounts = accounts.filter((account) => account.managed)
                  const isMeta = provider.provider === 'META_ADS'

                  return (
                    <article key={provider.provider} className="panel p-5">
                      <div className="flex items-start justify-between gap-4">
                        <div className="flex items-start gap-3">
                          <span className="flex size-10 shrink-0 items-center justify-center rounded-xl bg-slate-100 text-slate-700">
                            <FiBarChart2 aria-hidden="true" />
                          </span>
                          <div>
                            <p className="text-sm font-bold text-slate-950">{provider.displayName}</p>
                            <p className="mt-1 text-xs text-slate-500">
                              {isMeta
                                ? 'Meta campaigns, audiences, pixels and creative assets.'
                                : 'Google campaigns, keywords, conversions and reporting.'}
                            </p>
                          </div>
                        </div>
                        <span
                          className={`rounded-full px-2.5 py-1 text-[10px] font-bold ${
                            providerConnection
                              ? 'bg-emerald-50 text-emerald-700'
                              : provider.configured
                                ? 'bg-slate-100 text-slate-600'
                                : 'bg-amber-50 text-amber-700'
                          }`}
                        >
                          {providerConnection
                            ? 'CONNECTED'
                            : provider.configured
                              ? 'READY TO CONNECT'
                              : 'NOT CONFIGURED'}
                        </span>
                      </div>

                      <div className="mt-5 grid grid-cols-2 gap-3 rounded-xl bg-slate-50 p-4">
                        <div>
                          <p className="text-[10px] font-bold uppercase tracking-wide text-slate-400">
                            Accessible
                          </p>
                          <p className="mt-1 text-lg font-bold text-slate-900">{accounts.length}</p>
                        </div>
                        <div>
                          <p className="text-[10px] font-bold uppercase tracking-wide text-slate-400">
                            Managed
                          </p>
                          <p className="mt-1 text-lg font-bold text-slate-900">
                            {managedAccounts.length}
                          </p>
                        </div>
                      </div>

                      {providerConnection?.displayName && (
                        <p className="mt-3 text-xs text-slate-500">
                          Connected as {providerConnection.displayName}
                        </p>
                      )}

                      <div className="mt-4 flex flex-wrap gap-2">
                        {can(PERMISSIONS.ADS_CONNECT) && (
                          <button
                            type="button"
                            className={providerConnection ? 'secondary-button gap-2' : 'primary-button gap-2'}
                            disabled={!provider.configured || connectingAdProvider !== null}
                            onClick={() => void connectAdProvider(provider)}
                          >
                            <FiExternalLink aria-hidden="true" />
                            {connectingAdProvider === provider.provider
                              ? 'Opening…'
                              : providerConnection
                                ? 'Reconnect'
                                : 'Connect'}
                          </button>
                        )}
                        <Link className="secondary-button gap-2" to="/ad-center">
                          Manage accounts
                        </Link>
                      </div>
                    </article>
                  )
                })}

                {adProviders.length === 0 && (
                  <div className="panel p-8 text-center text-sm text-slate-500 xl:col-span-2">
                    Advertising providers are not available for this workspace yet.
                  </div>
                )}
              </div>
            )}
          </>
        )}
      </div>

      {broadcastEmailPickerOpen && selectedCompany && (
        <BroadcastEmailProviderPickerModal
          companyId={selectedCompany.companyId}
          onClose={() => {
            setBroadcastEmailPickerOpen(false)
            void loadBroadcastSetup()
          }}
          onManagedEmail={() => {
            setBroadcastEmailPickerOpen(false)
            setManagedEmailDomainId(null)
            setManagedEmailOpen(true)
          }}
          onSelect={(provider: BroadcastEmailProviderChoice) => {
            setBroadcastEmailPickerOpen(false)
            if (provider === 'AMAZON_SES') {
              setSesConnectOpen(true)
            } else if (provider === 'SENDGRID') {
              setSendGridConnectOpen(true)
            } else if (provider === 'AZURE_EMAIL') {
              setAzureEmailConnectOpen(true)
            } else {
              setMsg91EmailConnectOpen(true)
            }
          }}
        />
      )}

      {managedEmailOpen && selectedCompany && (
        <ManagedEmailModal
          companyId={selectedCompany.companyId}
          initialDomainId={managedEmailDomainId}
          canModify={can(PERMISSIONS.BROADCAST_MODIFY)}
          onClose={() => {
            setManagedEmailOpen(false)
            setManagedEmailDomainId(null)
          }}
          onChanged={() => {
            setManagedEmailRefreshToken((current) => current + 1)
            void loadConnections()
          }}
        />
      )}

      {managedSmsOpen && selectedCompany && (
        <ManagedSmsSetupModal
          companyId={selectedCompany.companyId}
          canModify={can(PERMISSIONS.BROADCAST_MODIFY)}
          onClose={() => setManagedSmsOpen(false)}
          onChanged={() => {
            setManagedSmsRefreshToken((current) => current + 1)
            void loadConnections()
          }}
        />
      )}

      {msg91EmailConnectOpen && selectedCompany && (
        <Msg91EmailConnectModal
          companyId={selectedCompany.companyId}
          onClose={() => setMsg91EmailConnectOpen(false)}
          onConnected={(sender) => {
            setMsg91EmailConnectOpen(false)
            snackbar.success(
              `Connected MSG91 Email sender ${sender.fromAddress}. Publish and sync a transactional template before sending a test.`,
            )
            setActiveEmailSetup(null)
            void loadConnections()
          }}
        />
      )}

      {azureEmailConnectOpen && selectedCompany && (
        <AzureEmailConnectModal
          companyId={selectedCompany.companyId}
          onClose={() => setAzureEmailConnectOpen(false)}
          onConnected={(sender) => {
            setAzureEmailConnectOpen(false)
            snackbar.success(`Connected Azure Email sender ${sender.fromAddress}`)
            setActiveEmailSetup(null)
            setTestEmailSender(sender)
            void loadConnections()
          }}
        />
      )}

      {sesConnectOpen && selectedCompany && (
        <AmazonSesConnectModal
          companyId={selectedCompany.companyId}
          onClose={() => setSesConnectOpen(false)}
          onConnected={(sender) => {
            setSesConnectOpen(false)
            snackbar.success(`Connected Amazon SES sender ${sender.fromAddress}`)
            setActiveEmailSetup(null)
            setTestEmailSender(sender)
            void loadConnections()
          }}
        />
      )}

      {sendGridConnectOpen && selectedCompany && (
        <SendGridConnectModal
          companyId={selectedCompany.companyId}
          onClose={() => setSendGridConnectOpen(false)}
          onConnected={(sender) => {
            setSendGridConnectOpen(false)
            snackbar.success(`Connected SendGrid sender ${sender.fromAddress}`)
            setActiveEmailSetup(null)
            setTestEmailSender(sender)
            void loadConnections()
          }}
        />
      )}

      {msg91SmsSetupOpen && selectedCompany && (
        <Msg91SmsSetupGuideModal
          companyId={selectedCompany.companyId}
          onClose={() => {
            setMsg91SmsSetupOpen(false)
            void loadBroadcastSetup()
          }}
          onContinue={() => {
            setMsg91SmsSetupOpen(false)
            setMsg91ConnectOpen(true)
          }}
        />
      )}

      {msg91ConnectOpen && selectedCompany && (
        <Msg91ConnectModal
          companyId={selectedCompany.companyId}
          onClose={() => setMsg91ConnectOpen(false)}
          onConnected={(sender) => {
            setMsg91ConnectOpen(false)
            snackbar.success(`Connected MSG91 SMS sender ${sender.fromAddress}`)
            setActiveSmsSetup(null)
            void loadConnections()
          }}
        />
      )}

      {testEmailSender && selectedCompany && (
        <BroadcastTestEmailModal
          companyId={selectedCompany.companyId}
          sender={testEmailSender}
          onClose={() => setTestEmailSender(null)}
          onDone={(recipient) => {
            setTestEmailSender(null)
            snackbar.success(`Test email accepted for ${recipient}`)
          }}
        />
      )}

      <DisconnectChannelModal
        connection={disconnectTarget?.providerType === 'META_WHATSAPP' ? null : disconnectTarget}
        working={disconnectTarget !== null && workingConnectionId === disconnectTarget.id}
        onClose={() => {
          if (!workingConnectionId) setDisconnectTarget(null)
        }}
        onConfirm={(policy) => {
          if (disconnectTarget) void disconnect(disconnectTarget, policy)
        }}
      />

      <WhatsAppDisconnectModal
        connection={disconnectTarget?.providerType === 'META_WHATSAPP' ? disconnectTarget : null}
        working={disconnectTarget !== null && workingConnectionId === disconnectTarget.id}
        onClose={() => {
          if (!workingConnectionId) setDisconnectTarget(null)
        }}
        onConfirm={(policy) => {
          if (disconnectTarget) void disconnect(disconnectTarget, policy)
        }}
      />

      <DeleteChannelDataModal
        connection={deleteTarget}
        working={deleteTarget !== null && workingConnectionId === deleteTarget.id}
        onClose={() => {
          if (!workingConnectionId) setDeleteTarget(null)
        }}
        onConfirm={() => {
          if (deleteTarget) void deleteDisconnectedDataNow(deleteTarget)
        }}
      />
    </section>
  )
}
