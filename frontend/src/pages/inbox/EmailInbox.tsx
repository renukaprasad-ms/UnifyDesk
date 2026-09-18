import { useCallback, useEffect, useMemo, useState } from 'react'
import { FiInbox, FiMail, FiRefreshCw } from 'react-icons/fi'
import { channelApi } from '../../features/channel/channelApi'
import { gmailApi } from '../../features/channel/gmailApi'
import type { ChannelConnection } from '../../features/channel/types'
import { inboxApi } from '../../features/inbox/inboxApi'
import type { InboxConversation, InboxMessage } from '../../features/inbox/types'
import { apiErrorMessage } from '../../lib/api/apiClient'
import { useAppSelector } from '../../store/hooks'
import { selectSelectedCompanyId } from '../../store/workspace/workspaceSlice'

const formatDate = (value?: string | null) => {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ''

  const today = new Date()
  if (date.toDateString() === today.toDateString()) {
    return date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
  }

  return date.toLocaleDateString([], { month: 'short', day: 'numeric' })
}

const displayParticipant = (conversation: InboxConversation, ownAddress?: string) => {
  const participant = conversation.participants.find(
    (item) => item.address.toLowerCase() !== ownAddress?.toLowerCase(),
  )
  return participant?.displayName || participant?.address || ownAddress || 'Email'
}

const messageTitle = (message: InboxMessage) =>
  message.subject || message.snippet || (message.direction === 'OUTBOUND' ? 'Sent email' : 'Email')

export default function EmailInbox() {
  const companyId = useAppSelector(selectSelectedCompanyId)
  const [connections, setConnections] = useState<ChannelConnection[]>([])
  const [conversations, setConversations] = useState<InboxConversation[]>([])
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const [messages, setMessages] = useState<InboxMessage[]>([])
  const [loading, setLoading] = useState(true)
  const [messagesLoading, setMessagesLoading] = useState(false)
  const [syncing, setSyncing] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)

  const selectedConversation = useMemo(
    () => conversations.find((conversation) => conversation.id === selectedId) ?? null,
    [conversations, selectedId],
  )

  const ownAddress = useMemo(() => {
    if (!selectedConversation) return undefined
    return connections.find((connection) => connection.id === selectedConversation.connectionId)
      ?.externalAccountId
  }, [connections, selectedConversation])

  const loadConversations = useCallback(async () => {
    if (!companyId) return
    setError(null)

    try {
      const [inbox, emailConnections] = await Promise.all([
        inboxApi.conversations(companyId),
        channelApi.list(companyId, 'EMAIL'),
      ])
      setConversations(inbox.items)
      setConnections(emailConnections)

      setSelectedId((current) => {
        if (current && inbox.items.some((conversation) => conversation.id === current)) {
          return current
        }
        return inbox.items[0]?.id ?? null
      })
    } catch (requestError) {
      setError(apiErrorMessage(requestError, 'Unable to load email inbox'))
    } finally {
      setLoading(false)
    }
  }, [companyId])

  useEffect(() => {
    setLoading(true)
    void loadConversations()
  }, [loadConversations])

  useEffect(() => {
    if (!companyId || !selectedId) {
      setMessages([])
      return
    }

    let active = true
    setMessagesLoading(true)
    inboxApi
      .messages(companyId, selectedId)
      .then((items) => {
        if (active) setMessages(items)
      })
      .catch((requestError) => {
        if (active) setError(apiErrorMessage(requestError, 'Unable to load conversation'))
      })
      .finally(() => {
        if (active) setMessagesLoading(false)
      })

    return () => {
      active = false
    }
  }, [companyId, selectedId])

  const syncGmail = async () => {
    if (!companyId || syncing) return

    const gmailConnections = connections.filter(
      (connection) => connection.providerType === 'GMAIL' && connection.status === 'ACTIVE',
    )

    if (gmailConnections.length === 0) {
      setNotice('Connect a Gmail account before syncing your inbox.')
      return
    }

    setSyncing(true)
    setError(null)
    setNotice(null)

    try {
      await Promise.all(
        gmailConnections.map((connection) => gmailApi.sync(companyId, connection.id)),
      )
      setNotice('Gmail sync queued. Refresh in a few seconds to see new messages.')
    } catch (requestError) {
      setError(apiErrorMessage(requestError, 'Unable to queue Gmail sync'))
    } finally {
      setSyncing(false)
    }
  }

  if (!companyId) {
    return (
      <section className="px-6 py-8">
        <div className="panel p-8 text-center text-sm text-slate-500">
          Select a workspace to open the email inbox.
        </div>
      </section>
    )
  }

  return (
    <section className="px-4 py-5 sm:px-6 sm:py-7">
      <div className="mx-auto max-w-[1500px]">
        <div className="mb-5 flex flex-wrap items-center justify-between gap-3">
          <div>
            <p className="eyebrow">INBOX</p>
            <h1 className="mt-1 text-2xl font-bold text-slate-950">Email</h1>
            <p className="mt-1 text-sm text-slate-500">
              Gmail conversations synchronized into your ReachFly workspace.
            </p>
          </div>
          <div className="flex items-center gap-2">
            <button
              type="button"
              className="secondary-button gap-2"
              onClick={() => void loadConversations()}
              disabled={loading}
            >
              <FiRefreshCw aria-hidden="true" />
              Refresh
            </button>
            <button
              type="button"
              className="primary-button gap-2"
              onClick={() => void syncGmail()}
              disabled={syncing}
            >
              <FiMail aria-hidden="true" />
              {syncing ? 'Queuing...' : 'Sync Gmail'}
            </button>
          </div>
        </div>

        {error && (
          <div className="mb-4 rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
            {error}
          </div>
        )}
        {notice && (
          <div className="mb-4 rounded-xl border border-indigo-100 bg-indigo-50 px-4 py-3 text-sm text-indigo-700">
            {notice}
          </div>
        )}

        <div className="panel grid min-h-[620px] overflow-hidden lg:grid-cols-[360px_minmax(0,1fr)]">
          <aside className="border-b border-slate-200 lg:border-b-0 lg:border-r">
            <div className="border-b border-slate-100 px-4 py-4">
              <div className="flex items-center justify-between gap-3">
                <div>
                  <h2 className="text-sm font-bold text-slate-900">Conversations</h2>
                  <p className="mt-1 text-xs text-slate-500">
                    {conversations.length} loaded
                  </p>
                </div>
                <FiInbox className="text-slate-400" aria-hidden="true" />
              </div>
            </div>

            <div className="max-h-[555px] overflow-y-auto">
              {loading ? (
                <div className="px-5 py-10 text-center text-sm text-slate-500">
                  Loading conversations...
                </div>
              ) : conversations.length === 0 ? (
                <div className="px-6 py-12 text-center">
                  <FiMail className="mx-auto text-3xl text-slate-300" aria-hidden="true" />
                  <p className="mt-4 text-sm font-semibold text-slate-800">No emails synced yet</p>
                  <p className="mt-2 text-xs leading-5 text-slate-500">
                    Click Sync Gmail to import recent conversations from your connected account.
                  </p>
                </div>
              ) : (
                conversations.map((conversation) => {
                  const connection = connections.find(
                    (item) => item.id === conversation.connectionId,
                  )
                  const counterpart = displayParticipant(
                    conversation,
                    connection?.externalAccountId ?? undefined,
                  )
                  const active = selectedId === conversation.id

                  return (
                    <button
                      key={conversation.id}
                      type="button"
                      onClick={() => setSelectedId(conversation.id)}
                      className={`w-full border-b border-slate-100 px-4 py-4 text-left transition ${
                        active ? 'bg-indigo-50' : 'bg-white hover:bg-slate-50'
                      }`}
                    >
                      <div className="flex items-start justify-between gap-3">
                        <p className="truncate text-sm font-semibold text-slate-900">
                          {counterpart}
                        </p>
                        <span className="shrink-0 text-[11px] text-slate-400">
                          {formatDate(conversation.lastMessageAt)}
                        </span>
                      </div>
                      <p className="mt-1 truncate text-xs font-medium text-slate-700">
                        {conversation.subject || '(no subject)'}
                      </p>
                      <div className="mt-1 flex items-center gap-2">
                        <p className="min-w-0 flex-1 truncate text-xs text-slate-500">
                          {conversation.lastMessagePreview || 'No preview available'}
                        </p>
                        {conversation.unreadCount > 0 && (
                          <span className="inline-flex min-w-5 items-center justify-center rounded-full bg-indigo-500 px-1.5 py-0.5 text-[10px] font-bold text-white">
                            {conversation.unreadCount}
                          </span>
                        )}
                      </div>
                      {connection?.externalAccountId && (
                        <p className="mt-2 truncate text-[10px] font-medium uppercase tracking-wide text-slate-400">
                          {connection.externalAccountId}
                        </p>
                      )}
                    </button>
                  )
                })
              )}
            </div>
          </aside>

          <main className="min-w-0 bg-slate-50/40">
            {!selectedConversation ? (
              <div className="flex min-h-[560px] items-center justify-center px-6 text-center">
                <div>
                  <FiMail className="mx-auto text-4xl text-slate-300" aria-hidden="true" />
                  <p className="mt-4 text-sm font-semibold text-slate-800">
                    Select a conversation
                  </p>
                  <p className="mt-2 text-xs text-slate-500">
                    Email messages will appear here.
                  </p>
                </div>
              </div>
            ) : (
              <>
                <header className="border-b border-slate-200 bg-white px-5 py-4">
                  <p className="text-sm font-bold text-slate-950">
                    {selectedConversation.subject || '(no subject)'}
                  </p>
                  <p className="mt-1 truncate text-xs text-slate-500">
                    {displayParticipant(selectedConversation, ownAddress)}
                    {ownAddress ? ` · ${ownAddress}` : ''}
                  </p>
                </header>

                <div className="max-h-[555px] space-y-4 overflow-y-auto p-4 sm:p-6">
                  {messagesLoading ? (
                    <p className="py-10 text-center text-sm text-slate-500">
                      Loading messages...
                    </p>
                  ) : messages.length === 0 ? (
                    <p className="py-10 text-center text-sm text-slate-500">
                      No messages found in this conversation.
                    </p>
                  ) : (
                    messages.map((message) => (
                      <article
                        key={message.id}
                        className={`max-w-3xl rounded-xl border p-4 ${
                          message.direction === 'OUTBOUND'
                            ? 'ml-auto border-indigo-100 bg-indigo-50'
                            : 'border-slate-200 bg-white'
                        }`}
                      >
                        <div className="flex flex-wrap items-center justify-between gap-2">
                          <div className="flex items-center gap-2">
                            <span className="text-xs font-bold text-slate-800">
                              {message.direction === 'OUTBOUND' ? 'You' : 'Received'}
                            </span>
                            {!message.read && (
                              <span className="rounded-full bg-indigo-100 px-2 py-0.5 text-[10px] font-bold text-indigo-600">
                                UNREAD
                              </span>
                            )}
                          </div>
                          <time className="text-[11px] text-slate-400">
                            {message.sentAt ? new Date(message.sentAt).toLocaleString() : ''}
                          </time>
                        </div>

                        <p className="mt-3 text-xs font-semibold text-slate-700">
                          {messageTitle(message)}
                        </p>
                        <pre className="mt-3 whitespace-pre-wrap break-words font-sans text-sm leading-6 text-slate-700">
                          {message.bodyText || message.snippet || 'No message body available.'}
                        </pre>

                        {message.attachments.length > 0 && (
                          <div className="mt-4 flex flex-wrap gap-2">
                            {message.attachments.map((attachment) => (
                              <span
                                key={attachment.id}
                                className="rounded-lg border border-slate-200 bg-white px-3 py-2 text-xs text-slate-600"
                              >
                                {attachment.filename || 'Attachment'}
                              </span>
                            ))}
                          </div>
                        )}
                      </article>
                    ))
                  )}
                </div>
              </>
            )}
          </main>
        </div>
      </div>
    </section>
  )
}
