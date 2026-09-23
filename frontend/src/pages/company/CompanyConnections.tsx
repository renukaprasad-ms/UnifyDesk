import { useEffect, useMemo, useState } from 'react'
import {
  FiCheckCircle,
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
import EmailProviderPicker from '../../components/email/EmailProviderPicker'
import AmazonSesConnectModal from '../../components/broadcast/AmazonSesConnectModal'
import AzureEmailConnectModal from '../../components/broadcast/AzureEmailConnectModal'
import BroadcastEmailProviderPickerModal, {
  type BroadcastEmailProviderChoice,
} from '../../components/broadcast/BroadcastEmailProviderPickerModal'
import SendGridConnectModal from '../../components/broadcast/SendGridConnectModal'
import BroadcastTestEmailModal from '../../components/broadcast/BroadcastTestEmailModal'
import Msg91ConnectModal from '../../components/broadcast/Msg91ConnectModal'
import Msg91SmsSetupGuideModal from '../../components/broadcast/Msg91SmsSetupGuideModal'
import Msg91EmailConnectModal from '../../components/broadcast/Msg91EmailConnectModal'
import { broadcastApi } from '../../features/broadcast/broadcastApi'
import type { BroadcastSender, BroadcastSetupSession } from '../../features/broadcast/types'
import { channelApi } from '../../features/channel/channelApi'
import { gmailApi } from '../../features/channel/gmailApi'
import { microsoftApi } from '../../features/channel/microsoftApi'
import { metaWhatsAppApi } from '../../features/channel/metaWhatsAppApi'
import { launchMetaWhatsAppSignup, loadMetaSdk } from '../../features/channel/metaWhatsAppSdk'
import type {
  ChannelConnection,
  DisconnectDataPolicy,
  MetaWhatsAppSignupConfig,
  SenderIdentity,
} from '../../features/channel/types'
import { PERMISSIONS } from '../../features/iam/permissions'
import { useIam } from '../../features/iam/useIam'
import { apiErrorMessage } from '../../lib/api/apiClient'
import { useSnackbar } from '../../components/feedback/SnackbarProvider'
import { useAppSelector } from '../../store/hooks'
import { selectSelectedCompany } from '../../store/workspace/workspaceSlice'

const providerLabel = (providerType: ChannelConnection['providerType']) => {
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

export default function CompanyConnections() {
  const selectedCompany = useAppSelector(selectSelectedCompany)
  const { can } = useIam()
  const snackbar = useSnackbar()
  const [connections, setConnections] = useState<ChannelConnection[]>([])
  const [senders, setSenders] = useState<Record<string, SenderIdentity[]>>({})
  const [broadcastSenders, setBroadcastSenders] = useState<BroadcastSender[]>([])
  const [signupConfig, setSignupConfig] = useState<MetaWhatsAppSignupConfig | null>(null)
  const [metaReady, setMetaReady] = useState(false)
  const [loading, setLoading] = useState(true)
  const [connecting, setConnecting] = useState(false)
  const [broadcastEmailPickerOpen, setBroadcastEmailPickerOpen] = useState(false)
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

  const prepareMeta = async () => {
    if (!selectedCompany || !can(PERMISSIONS.WHATSAPP_CONNECT)) return

    console.info('[MetaWhatsApp] CONFIG_LOAD_START', {
      companyId: selectedCompany.companyId,
    })

    try {
      const config = await metaWhatsAppApi.signupConfig(selectedCompany.companyId)

      console.info('[MetaWhatsApp] CONFIG_LOADED', {
        companyId: selectedCompany.companyId,
        appId: config.appId,
        configurationId: config.configurationId,
        graphApiVersion: config.graphApiVersion,
        hasState: Boolean(config.state),
      })

      await loadMetaSdk(config.appId, config.graphApiVersion)

      console.info('[MetaWhatsApp] CONFIG_AND_SDK_READY', {
        companyId: selectedCompany.companyId,
      })

      setSignupConfig(config)
      setMetaReady(true)
    } catch (prepareError) {
      console.error('[MetaWhatsApp] CONFIG_OR_SDK_FAILED', {
        companyId: selectedCompany.companyId,
        message:
          prepareError instanceof Error
            ? prepareError.message
            : apiErrorMessage(prepareError, 'Unable to prepare Meta WhatsApp'),
      })
      setSignupConfig(null)
      setMetaReady(false)
    }
  }

  useEffect(() => {
    void loadConnections()
    void loadBroadcastSetup()
    void prepareMeta()
  }, [selectedCompany?.companyId])

  useEffect(() => {
    const url = new URL(window.location.href)
    const connectProvider = url.searchParams.get('connect')
    if (connectProvider === 'broadcast-email' && can(PERMISSIONS.BROADCAST_MODIFY)) {
      setBroadcastEmailPickerOpen(true)
      url.searchParams.delete('connect')
      window.history.replaceState({}, '', `${url.pathname}${url.search}${url.hash}`)
    } else if (connectProvider === 'amazon-ses' && can(PERMISSIONS.BROADCAST_MODIFY)) {
      setSesConnectOpen(true)
      url.searchParams.delete('connect')
      window.history.replaceState({}, '', `${url.pathname}${url.search}${url.hash}`)
    } else if (connectProvider === 'sendgrid' && can(PERMISSIONS.BROADCAST_MODIFY)) {
      setSendGridConnectOpen(true)
      url.searchParams.delete('connect')
      window.history.replaceState({}, '', `${url.pathname}${url.search}${url.hash}`)
    } else if (connectProvider === 'azure-email' && can(PERMISSIONS.BROADCAST_MODIFY)) {
      setAzureEmailConnectOpen(true)
      url.searchParams.delete('connect')
      window.history.replaceState({}, '', `${url.pathname}${url.search}${url.hash}`)
    } else if (
      (connectProvider === 'msg91' ||
        connectProvider === 'msg91-sms' ||
        connectProvider === 'broadcast-sms') &&
      can(PERMISSIONS.BROADCAST_MODIFY)
    ) {
      setMsg91SmsSetupOpen(true)
      url.searchParams.delete('connect')
      window.history.replaceState({}, '', `${url.pathname}${url.search}${url.hash}`)
    } else if (connectProvider === 'msg91-email' && can(PERMISSIONS.BROADCAST_MODIFY)) {
      setMsg91EmailConnectOpen(true)
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

  const connectWhatsApp = () => {
    if (!selectedCompany || !signupConfig || !metaReady || connecting) {
      console.warn('[MetaWhatsApp] CONNECT_IGNORED', {
        hasCompany: Boolean(selectedCompany),
        hasSignupConfig: Boolean(signupConfig),
        metaReady,
        connecting,
      })
      return
    }

    console.info('[MetaWhatsApp] CONNECT_CLICKED', {
      companyId: selectedCompany.companyId,
      appId: signupConfig.appId,
      configurationId: signupConfig.configurationId,
      graphApiVersion: signupConfig.graphApiVersion,
    })

    setConnecting(true)

    launchMetaWhatsAppSignup(signupConfig, {
      onPopupNotObserved: () => {
        console.warn('[MetaWhatsApp] NO_META_RESPONSE_AFTER_HINT_DELAY', {
          companyId: selectedCompany.companyId,
        })
        snackbar.info(
          'Meta has not responded yet. Check the browser console for [MetaWhatsApp] logs and verify the WhatsApp Embedded Signup configuration.',
        )
      },
    })
      .then((result) => {
        console.info('[MetaWhatsApp] EMBEDDED_SIGNUP_RESULT_RECEIVED', {
          companyId: selectedCompany.companyId,
          hasCode: Boolean(result.code),
          wabaId: result.wabaId,
          phoneNumberId: result.phoneNumberId,
        })

        console.info('[MetaWhatsApp] COMPLETE_API_CALLING', {
          companyId: selectedCompany.companyId,
        })

        return metaWhatsAppApi.complete(selectedCompany.companyId, signupConfig.state, result)
      })
      .then(async (connection) => {
        console.info('[MetaWhatsApp] COMPLETE_API_SUCCEEDED', {
          companyId: selectedCompany.companyId,
          connectionId: connection.connectionId,
          wabaName: connection.wabaName,
        })

        snackbar.success(
          `Connected ${connection.phoneNumber.displayPhoneNumber} to ${connection.wabaName}`,
        )
        await loadConnections()
        await prepareMeta()
      })
      .catch((connectError) => {
        const message =
          connectError instanceof Error
            ? connectError.message
            : apiErrorMessage(connectError, 'Unable to connect WhatsApp')

        console.error('[MetaWhatsApp] CONNECT_FAILED', {
          companyId: selectedCompany.companyId,
          message,
        })

        snackbar.error(message)
        void prepareMeta()
      })
      .finally(() => {
        console.info('[MetaWhatsApp] CONNECT_FINISHED', {
          companyId: selectedCompany.companyId,
        })
        setConnecting(false)
      })
  }

  const disconnect = async (
    connection: ChannelConnection,
    dataPolicy: DisconnectDataPolicy,
  ) => {
    if (!selectedCompany) return

    setWorkingConnectionId(connection.id)
    try {
      if (connection.providerType === 'GMAIL') {
        await gmailApi.disconnect(selectedCompany.companyId, connection.id, dataPolicy)
      } else if (connection.providerType === 'MICROSOFT') {
        await microsoftApi.disconnect(selectedCompany.companyId, connection.id, dataPolicy)
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
      if (connection.providerType === 'META_WHATSAPP') await prepareMeta()
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
      await channelApi.disconnect(selectedCompany.companyId, connection.id, 'DELETE_NOW')
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
      <div className="mx-auto max-w-6xl">
        <header className="flex flex-wrap items-end justify-between gap-4">
          <div>
            <p className="eyebrow">Company</p>
            <h1 className="mt-2 text-2xl font-bold tracking-tight text-slate-950 sm:text-3xl">
              Connections
            </h1>
            <p className="mt-2 max-w-2xl text-sm leading-6 text-slate-500">
              Connect the communication providers this workspace will use for inboxes, sending,
              templates, and broadcasts.
            </p>
          </div>
          <button
            type="button"
            className="secondary-button inline-flex items-center gap-2"
            onClick={() => void loadConnections()}
            disabled={loading}
          >
            <FiRefreshCw aria-hidden="true" />
            Refresh
          </button>
        </header>

        <div className="mt-7 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          <div className="panel p-5">
            <div className="flex items-center justify-between">
              <span className="flex size-10 items-center justify-center rounded-xl bg-emerald-50 text-emerald-600">
                <FiMessageCircle aria-hidden="true" size={19} />
              </span>
              <span className="text-xs font-semibold text-slate-400">
                {whatsAppConnections.filter((item) => item.status === 'ACTIVE').length} connected
              </span>
            </div>
            <h2 className="mt-4 text-base font-bold text-slate-950">WhatsApp</h2>
            <p className="mt-1 text-xs leading-5 text-slate-500">
              Meta WhatsApp Business Platform using Embedded Signup.
            </p>
            {can(PERMISSIONS.WHATSAPP_CONNECT) && (
              <button
                type="button"
                className="primary-button mt-4 inline-flex w-full items-center justify-center gap-2"
                onClick={connectWhatsApp}
                disabled={!metaReady || connecting}
              >
                <FiLink aria-hidden="true" />
                {connecting
                  ? 'Connecting…'
                  : whatsAppConnections.some((item) => item.status === 'ACTIVE')
                    ? 'Connect another'
                    : 'Connect WhatsApp'}
              </button>
            )}
            {can(PERMISSIONS.WHATSAPP_CONNECT) && !metaReady && (
              <p className="mt-2 text-[11px] leading-4 text-slate-400">
                Meta configuration must be available before the signup popup can open.
              </p>
            )}
          </div>

          <div className="panel p-5">
            <div className="flex items-center justify-between">
              <span className="flex size-10 items-center justify-center rounded-xl bg-sky-50 text-sky-600">
                <FiMail aria-hidden="true" size={19} />
              </span>
              <span className="text-xs font-semibold text-slate-400">
                {emailConnections.filter((item) => item.status === 'ACTIVE').length} connected
              </span>
            </div>
            <h2 className="mt-4 text-base font-bold text-slate-950">Email</h2>
            <p className="mt-1 text-xs leading-5 text-slate-500">
              Inbox accounts use Gmail, Microsoft 365 or IMAP + SMTP. Broadcast senders use
              Amazon SES, SendGrid, Azure Email or MSG91 Email. MSG91 Email templates are synced
              separately from the Templates module.
            </p>
            <div className="mt-4 grid gap-2">
              {can(PERMISSIONS.EMAIL_CONNECT) && (
                <EmailProviderPicker
                  companyId={selectedCompany.companyId}
                  label="Connect inbox email"
                  className="primary-button inline-flex w-full items-center justify-center gap-2"
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
                  className="secondary-button inline-flex w-full items-center justify-center gap-2"
                  onClick={() => setBroadcastEmailPickerOpen(true)}
                >
                  <FiLink aria-hidden="true" />
                  {activeEmailSetup
                    ? `Continue ${
                        activeEmailSetup.provider === 'AMAZON_SES'
                          ? 'Amazon SES'
                          : activeEmailSetup.provider === 'SENDGRID'
                            ? 'SendGrid'
                            : activeEmailSetup.provider === 'AZURE_EMAIL'
                              ? 'Azure Email'
                              : 'MSG91 Email'
                      } setup`
                    : 'Connect broadcast email'}
                </button>
              )}
            </div>
          </div>

          <div className="panel p-5">
            <div className="flex items-center justify-between">
              <span className="flex size-10 items-center justify-center rounded-xl bg-violet-50 text-violet-600">
                <FiSmartphone aria-hidden="true" size={19} />
              </span>
              <span className="text-xs font-semibold text-slate-400">
                {smsConnections.filter((item) => item.status === 'ACTIVE').length} connected
              </span>
            </div>
            <h2 className="mt-4 text-base font-bold text-slate-950">SMS</h2>
            <p className="mt-1 text-xs leading-5 text-slate-500">
              Connect MSG91 SMS using an approved Flow, Sender ID/Header and template.
            </p>
            {can(PERMISSIONS.BROADCAST_MODIFY) && (
              <button
                type="button"
                className="secondary-button mt-4 inline-flex w-full items-center justify-center gap-2"
                onClick={() => setMsg91SmsSetupOpen(true)}
              >
                <FiLink aria-hidden="true" />
                {activeSmsSetup ? 'Continue MSG91 SMS setup' : 'Connect MSG91 SMS'}
              </button>
            )}
          </div>
        </div>

        <div className="panel mt-6 overflow-hidden">
          <div className="border-b border-slate-100 px-5 py-4">
            <h2 className="text-sm font-bold text-slate-950">Email connections</h2>
            <p className="mt-1 text-xs text-slate-500">
              Inbox and Broadcast providers share the company connection model. Capability badges
              show where each connection can be used.
            </p>
          </div>

          {loading ? (
            <p className="px-5 py-10 text-center text-sm text-slate-500">Loading connections…</p>
          ) : emailConnections.length === 0 ? (
            <div className="px-5 py-12 text-center">
              <FiMail className="mx-auto text-slate-300" size={28} />
              <p className="mt-3 text-sm font-semibold text-slate-800">
                No email connection yet
              </p>
              <p className="mt-1 text-xs text-slate-500">
                Connect an Inbox mailbox or a Broadcast email provider above.
              </p>
            </div>
          ) : (
            <div className="divide-y divide-slate-100">
              {emailConnections.map((connection) => (
                <div
                  key={connection.id}
                  className="flex flex-col gap-4 px-5 py-4 lg:flex-row lg:items-center lg:justify-between"
                >
                  <div className="min-w-0">
                    <div className="flex flex-wrap items-center gap-2">
                      <p className="text-sm font-bold text-slate-950">{connection.name}</p>
                      <span
                        className={`rounded-full px-2.5 py-1 text-[10px] font-bold ${
                          statusClass[connection.status]
                        }`}
                      >
                        {connection.status}
                      </span>
                      <span className="rounded-full bg-sky-50 px-2.5 py-1 text-[10px] font-bold text-sky-700">
                        {providerLabel(connection.providerType)}
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
                      broadcastSenderFor(connection.id) && (
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

        <div className="panel mt-6 overflow-hidden">
          <div className="border-b border-slate-100 px-5 py-4">
            <h2 className="text-sm font-bold text-slate-950">SMS connections</h2>
            <p className="mt-1 text-xs text-slate-500">
              MSG91 SMS Broadcast connections use an approved Flow and SMS Header.
            </p>
          </div>

          {loading ? (
            <p className="px-5 py-10 text-center text-sm text-slate-500">Loading connections…</p>
          ) : smsConnections.length === 0 ? (
            <div className="px-5 py-12 text-center">
              <FiSmartphone className="mx-auto text-slate-300" size={28} />
              <p className="mt-3 text-sm font-semibold text-slate-800">No SMS provider connected</p>
              <p className="mt-1 text-xs text-slate-500">
                Connect MSG91 SMS above to enable SMS Broadcast.
              </p>
            </div>
          ) : (
            <div className="divide-y divide-slate-100">
              {smsConnections.map((connection) => (
                <div
                  key={connection.id}
                  className="flex flex-col gap-4 px-5 py-4 lg:flex-row lg:items-center lg:justify-between"
                >
                  <div className="min-w-0">
                    <div className="flex flex-wrap items-center gap-2">
                      <p className="text-sm font-bold text-slate-950">{connection.name}</p>
                      <span className={`rounded-full px-2.5 py-1 text-[10px] font-bold ${statusClass[connection.status]}`}>
                        {connection.status}
                      </span>
                      <span className="rounded-full bg-violet-50 px-2.5 py-1 text-[10px] font-bold text-violet-700">
                        {providerLabel(connection.providerType)}
                      </span>
                      <span className="rounded-full bg-violet-50 px-2.5 py-1 text-[10px] font-bold text-violet-700">
                        Broadcast
                      </span>
                    </div>
                    <p className="mt-1 text-xs text-slate-500">
                      Flow {connection.externalAccountId ?? 'Configured'}
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
                    {connection.status !== 'DISCONNECTED' && can(PERMISSIONS.BROADCAST_MODIFY) && (
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

        <div className="panel mt-6 overflow-hidden">
          <div className="border-b border-slate-100 px-5 py-4">
            <h2 className="text-sm font-bold text-slate-950">WhatsApp Business accounts</h2>
            <p className="mt-1 text-xs text-slate-500">
              Each connected WABA is stored as a channel connection and its phone number as a sender
              identity.
            </p>
          </div>

          {loading ? (
            <p className="px-5 py-10 text-center text-sm text-slate-500">Loading connections…</p>
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
                          statusClass[connection.status]
                        }`}
                      >
                        {connection.status}
                      </span>
                    </div>
                    <p className="mt-1 text-xs text-slate-500">
                      WABA {connection.externalAccountId ?? 'Pending'}
                    </p>
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

                  <div className="w-full shrink-0 lg:w-auto lg:max-w-md">
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
                          ? () => connectWhatsApp()
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
      </div>

      {broadcastEmailPickerOpen && selectedCompany && (
        <BroadcastEmailProviderPickerModal
          companyId={selectedCompany.companyId}
          onClose={() => {
            setBroadcastEmailPickerOpen(false)
            void loadBroadcastSetup()
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
        connection={disconnectTarget}
        working={
          disconnectTarget !== null && workingConnectionId === disconnectTarget.id
        }
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
