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
import { channelApi } from '../../features/channel/channelApi'
import { gmailApi } from '../../features/channel/gmailApi'
import { metaWhatsAppApi } from '../../features/channel/metaWhatsAppApi'
import { launchMetaWhatsAppSignup, loadMetaSdk } from '../../features/channel/metaWhatsAppSdk'
import type {
  ChannelConnection,
  MetaWhatsAppSignupConfig,
  SenderIdentity,
} from '../../features/channel/types'
import { PERMISSIONS } from '../../features/iam/permissions'
import { useIam } from '../../features/iam/useIam'
import { apiErrorMessage } from '../../lib/api/apiClient'
import { useAppSelector } from '../../store/hooks'
import { selectSelectedCompany } from '../../store/workspace/workspaceSlice'

const statusClass: Record<ChannelConnection['status'], string> = {
  ACTIVE: 'bg-emerald-50 text-emerald-700',
  PENDING: 'bg-amber-50 text-amber-700',
  ERROR: 'bg-rose-50 text-rose-700',
  DISCONNECTED: 'bg-slate-100 text-slate-600',
}

export default function CompanyConnections() {
  const selectedCompany = useAppSelector(selectSelectedCompany)
  const { can } = useIam()
  const [connections, setConnections] = useState<ChannelConnection[]>([])
  const [senders, setSenders] = useState<Record<string, SenderIdentity[]>>({})
  const [signupConfig, setSignupConfig] = useState<MetaWhatsAppSignupConfig | null>(null)
  const [metaReady, setMetaReady] = useState(false)
  const [loading, setLoading] = useState(true)
  const [connecting, setConnecting] = useState(false)
  const [connectingGmail, setConnectingGmail] = useState(false)
  const [workingConnectionId, setWorkingConnectionId] = useState<string | null>(null)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')

  const whatsAppConnections = useMemo(
    () => connections.filter((connection) => connection.channelType === 'WHATSAPP'),
    [connections],
  )
  const emailConnections = useMemo(
    () => connections.filter((connection) => connection.channelType === 'EMAIL'),
    [connections],
  )
  const gmailConnections = useMemo(
    () => emailConnections.filter((connection) => connection.providerType === 'GMAIL'),
    [emailConnections],
  )
  const smsConnections = useMemo(
    () => connections.filter((connection) => connection.channelType === 'SMS'),
    [connections],
  )

  const loadConnections = async () => {
    if (!selectedCompany) return
    setLoading(true)
    setError('')
    try {
      const visible = await channelApi.list(selectedCompany.companyId)
      setConnections(visible)

      const readableConnections = visible.filter(
        (connection) =>
          (connection.channelType === 'WHATSAPP' && can(PERMISSIONS.WHATSAPP_READ)) ||
          (connection.channelType === 'EMAIL' && can(PERMISSIONS.EMAIL_READ)),
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
    } catch (loadError) {
      setError(apiErrorMessage(loadError, 'Unable to load channel connections'))
    } finally {
      setLoading(false)
    }
  }

  const prepareMeta = async () => {
    if (!selectedCompany || !can(PERMISSIONS.WHATSAPP_CONNECT)) return
    try {
      const config = await metaWhatsAppApi.signupConfig(selectedCompany.companyId)
      await loadMetaSdk(config.appId, config.graphApiVersion)
      setSignupConfig(config)
      setMetaReady(true)
    } catch {
      setSignupConfig(null)
      setMetaReady(false)
    }
  }

  useEffect(() => {
    void loadConnections()
    void prepareMeta()
  }, [selectedCompany?.companyId])

  useEffect(() => {
    const url = new URL(window.location.href)
    const gmailResult = url.searchParams.get('gmail')
    if (!gmailResult) return

    if (gmailResult === 'connected') {
      setNotice('Gmail connected successfully')
    } else if (gmailResult === 'cancelled') {
      setNotice('Gmail connection was cancelled')
    } else {
      setError('Unable to connect Gmail. Please try again.')
    }

    url.searchParams.delete('gmail')
    window.history.replaceState({}, '', `${url.pathname}${url.search}${url.hash}`)
  }, [])

  const connectWhatsApp = () => {
    if (!selectedCompany || !signupConfig || !metaReady || connecting) return

    setConnecting(true)
    setError('')
    setNotice('')

    launchMetaWhatsAppSignup(signupConfig)
      .then((result) =>
        metaWhatsAppApi.complete(selectedCompany.companyId, signupConfig.state, result),
      )
      .then(async (connection) => {
        setNotice(
          `Connected ${connection.phoneNumber.displayPhoneNumber} to ${connection.wabaName}`,
        )
        await loadConnections()
        await prepareMeta()
      })
      .catch((connectError) => {
        setError(apiErrorMessage(connectError, 'Unable to connect WhatsApp'))
        void prepareMeta()
      })
      .finally(() => setConnecting(false))
  }

  const connectGmail = async () => {
    if (!selectedCompany || connectingGmail) return
    setConnectingGmail(true)
    setError('')
    setNotice('')

    try {
      const authorization = await gmailApi.authorize(selectedCompany.companyId)
      window.location.assign(authorization.authorizationUrl)
    } catch (connectError) {
      setError(apiErrorMessage(connectError, 'Unable to start Gmail connection'))
      setConnectingGmail(false)
    }
  }

  const disconnect = async (connection: ChannelConnection) => {
    if (!selectedCompany) return
    if (!window.confirm(`Disconnect ${connection.name} from this workspace?`)) return

    setWorkingConnectionId(connection.id)
    setError('')
    setNotice('')
    try {
      if (connection.providerType === 'GMAIL') {
        await gmailApi.disconnect(selectedCompany.companyId, connection.id)
        setNotice('Gmail disconnected')
      } else {
        await channelApi.disconnect(selectedCompany.companyId, connection.id)
        setNotice('WhatsApp connection disconnected')
      }
      await loadConnections()
      if (connection.providerType === 'META_WHATSAPP') await prepareMeta()
    } catch (disconnectError) {
      setError(apiErrorMessage(disconnectError, 'Unable to disconnect channel'))
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
            <h1 className="mt-2 text-2xl font-bold tracking-tight text-slate-950 sm:text-3xl">Connections</h1>
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

        {(error || notice) && (
          <div
            className={`mt-6 rounded-xl border px-4 py-3 text-sm font-medium ${
              error
                ? 'border-rose-200 bg-rose-50 text-rose-700'
                : 'border-emerald-200 bg-emerald-50 text-emerald-700'
            }`}
          >
            {error || notice}
          </div>
        )}

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
              Connect Gmail securely with Google OAuth. Microsoft and SMTP will follow.
            </p>
            {can(PERMISSIONS.EMAIL_CONNECT) && (
              <button
                type="button"
                className="primary-button mt-4 inline-flex w-full items-center justify-center gap-2"
                onClick={() => void connectGmail()}
                disabled={connectingGmail}
              >
                <FiLink aria-hidden="true" />
                {connectingGmail
                  ? 'Opening Google…'
                  : gmailConnections.some((item) => item.status === 'ACTIVE')
                    ? 'Connect another Gmail'
                    : 'Connect Gmail'}
              </button>
            )}
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
              MSG91 sender setup will be added after WhatsApp and Email.
            </p>
            <button type="button" className="secondary-button mt-4 w-full" disabled>
              SMS setup later
            </button>
          </div>
        </div>

        <div className="panel mt-6 overflow-hidden">
          <div className="border-b border-slate-100 px-5 py-4">
            <h2 className="text-sm font-bold text-slate-950">Gmail accounts</h2>
            <p className="mt-1 text-xs text-slate-500">
              Gmail OAuth credentials are encrypted on the backend. The connected address becomes an
              email sender identity for this workspace.
            </p>
          </div>

          {loading ? (
            <p className="px-5 py-10 text-center text-sm text-slate-500">Loading connections…</p>
          ) : gmailConnections.length === 0 ? (
            <div className="px-5 py-12 text-center">
              <FiMail className="mx-auto text-slate-300" size={28} />
              <p className="mt-3 text-sm font-semibold text-slate-800">
                No Gmail account connected
              </p>
              <p className="mt-1 text-xs text-slate-500">
                Use Connect Gmail to authorize a mailbox through Google.
              </p>
            </div>
          ) : (
            <div className="divide-y divide-slate-100">
              {gmailConnections.map((connection) => (
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
                        GMAIL
                      </span>
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

                  {connection.status !== 'DISCONNECTED' && can(PERMISSIONS.EMAIL_DISCONNECT) && (
                    <button
                      type="button"
                      className="secondary-button inline-flex w-full shrink-0 items-center gap-2 text-rose-600 sm:w-auto"
                      disabled={workingConnectionId === connection.id}
                      onClick={() => void disconnect(connection)}
                    >
                      <FiSlash aria-hidden="true" />
                      Disconnect
                    </button>
                  )}
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

                  {connection.status !== 'DISCONNECTED' && can(PERMISSIONS.WHATSAPP_DISCONNECT) && (
                    <button
                      type="button"
                      className="secondary-button inline-flex shrink-0 items-center gap-2 text-rose-600"
                      disabled={workingConnectionId === connection.id}
                      onClick={() => void disconnect(connection)}
                    >
                      <FiSlash aria-hidden="true" />
                      Disconnect
                    </button>
                  )}
                </div>
              ))}
            </div>
          )}
        </div>
      </div>
    </section>
  )
}
