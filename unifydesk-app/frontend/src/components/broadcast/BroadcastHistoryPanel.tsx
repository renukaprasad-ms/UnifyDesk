import { useCallback, useEffect, useMemo, useState } from 'react'
import { FiClock, FiRefreshCw, FiSend } from 'react-icons/fi'
import { broadcastApi } from '../../features/broadcast/broadcastApi'
import type {
  BroadcastMessageStatus,
  BroadcastMessageSummary,
  BroadcastSender,
  Page,
} from '../../features/broadcast/types'
import type { ChannelType } from '../../features/channel/types'
import { apiErrorMessage } from '../../lib/api/apiClient'
import { useWorkspaceEpochScope } from '../../features/workspace/useWorkspaceEpochScope'
import { useAppSelector } from '../../store/hooks'
import {
  selectSelectedCompanyId,
  selectWorkspaceEpoch,
} from '../../store/workspace/workspaceSlice'
import { CardSkeleton } from '../ui/Skeleton'
import StatusBadge from '../ui/StatusBadge'

type Props = {
  channel: ChannelType
  onOpen: (broadcastId: string) => void
  onCreate: () => void
}

const emptyPage = (): Page<BroadcastMessageSummary> => ({
  content: [],
  number: 0,
  size: 30,
  totalElements: 0,
  totalPages: 0,
  first: true,
  last: true,
})

const statusLabel = (status: BroadcastMessageStatus) =>
  status
    .replaceAll('_', ' ')
    .toLowerCase()
    .replace(/(^|\s)\S/g, (letter) => letter.toUpperCase())

const channelLabel = (channel: ChannelType) =>
  channel === 'WHATSAPP' ? 'WhatsApp' : channel === 'EMAIL' ? 'Email' : 'SMS'

const audienceLabel = (message: BroadcastMessageSummary) => {
  if (message.groupId) return 'Previous recipient group'
  if (message.audienceSourceType === 'LIST') return 'Contact list'
  if (message.audienceSourceType === 'SEGMENT') return 'Segment'
  if (message.audienceSourceType === 'CONTACTS') return 'Selected contacts'
  return 'Audience'
}

const senderLabel = (sender?: BroadcastSender) =>
  sender?.displayName
    ? `${sender.displayName} <${sender.fromAddress}>`
    : sender?.fromAddress || 'Sender unavailable'

const messageTitle = (message: BroadcastMessageSummary, channel: ChannelType) =>
  message.subject?.trim() ||
  message.bodyPreview?.trim()?.slice(0, 72) ||
  `${channelLabel(channel)} broadcast`

const formattedDate = (value?: string | null) =>
  value
    ? new Intl.DateTimeFormat(undefined, {
        dateStyle: 'medium',
        timeStyle: 'short',
      }).format(new Date(value))
    : '—'

export default function BroadcastHistoryPanel({ channel, onOpen, onCreate }: Props) {
  const companyId = useAppSelector(selectSelectedCompanyId)
  const epoch = useAppSelector(selectWorkspaceEpoch)
  const { capture: captureScope, isCurrent: scopeIsCurrent } = useWorkspaceEpochScope(
    companyId,
    epoch,
  )
  const [page, setPage] = useState<Page<BroadcastMessageSummary>>(emptyPage)
  const [senders, setSenders] = useState<BroadcastSender[]>([])
  const [pageNumber, setPageNumber] = useState(0)
  const [loading, setLoading] = useState(true)
  const [refreshing, setRefreshing] = useState(false)
  const [error, setError] = useState('')

  const sendersById = useMemo(
    () => new Map(senders.map((sender) => [sender.id, sender])),
    [senders],
  )

  const load = useCallback(
    async (quiet = false) => {
      if (!companyId) return
      const scope = captureScope()
      if (!scopeIsCurrent(scope)) return
      if (quiet) setRefreshing(true)
      else setLoading(true)
      setError('')
      try {
        const [history, nextSenders] = await Promise.all([
          broadcastApi.channelHistory(companyId, channel, pageNumber, 30),
          broadcastApi.senders(companyId),
        ])
        if (!scopeIsCurrent(scope)) return
        setPage(history)
        setSenders(nextSenders)
      } catch (requestError) {
        if (scopeIsCurrent(scope)) {
          setError(apiErrorMessage(requestError, 'Unable to load broadcast history.'))
        }
      } finally {
        if (scopeIsCurrent(scope)) {
          setLoading(false)
          setRefreshing(false)
        }
      }
    },
    [captureScope, channel, companyId, pageNumber, scopeIsCurrent],
  )

  useEffect(() => {
    void load()
  }, [load, epoch])

  useEffect(() => {
    const active = page.content.some((message) =>
      ['QUEUED', 'RUNNING', 'PAUSED'].includes(message.status),
    )
    if (!active) return
    const timer = window.setInterval(() => void load(true), 8000)
    return () => window.clearInterval(timer)
  }, [load, page.content])

  return (
    <div className="channel-message-scroll min-h-[calc(100dvh-214px)] p-4 sm:p-5">
      <div className="w-full">
        <section className="panel p-4 sm:p-5">
          <div className="flex flex-wrap items-start justify-between gap-3">
            <div>
              <p className="eyebrow">BROADCASTS</p>
              <h1 className="mt-2 text-xl font-bold text-slate-950">
                {channelLabel(channel)} history
              </h1>
              <p className="mt-1 text-sm text-slate-500">
                Review drafts, scheduled sends, delivery progress and completed broadcasts.
              </p>
            </div>
            <div className="flex gap-2">
              <button
                type="button"
                className="secondary-button gap-2"
                disabled={refreshing}
                onClick={() => void load(true)}
              >
                <FiRefreshCw aria-hidden="true" />
                {refreshing ? 'Refreshing…' : 'Refresh'}
              </button>
              <button type="button" className="primary-button gap-2" onClick={onCreate}>
                <FiSend aria-hidden="true" />
                New broadcast
              </button>
            </div>
          </div>

          {error && (
            <div role="alert" className="mt-4 rounded-xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700">
              {error}
            </div>
          )}

          {loading && page.content.length === 0 ? (
            <div className="mt-5 grid gap-3">
              <CardSkeleton rows={4} />
              <CardSkeleton rows={4} />
              <CardSkeleton rows={4} />
            </div>
          ) : page.content.length === 0 ? (
            <div className="mt-5 rounded-2xl border border-dashed border-slate-200 px-5 py-12 text-center">
              <FiSend className="mx-auto text-3xl text-slate-300" aria-hidden="true" />
              <h2 className="mt-3 font-bold text-slate-900">No broadcasts yet</h2>
              <p className="mt-1 text-sm text-slate-500">
                Create your first {channelLabel(channel).toLowerCase()} broadcast to see its progress here.
              </p>
              <button type="button" className="primary-button mt-4" onClick={onCreate}>
                Create broadcast
              </button>
            </div>
          ) : (
            <div className="mt-5 grid gap-3">
              {page.content.map((message) => {
                const sender = message.senderId ? sendersById.get(message.senderId) : undefined
                return (
                  <button
                    key={message.id}
                    type="button"
                    className="w-full rounded-2xl border border-slate-200 bg-white p-4 text-left transition hover:border-indigo-200 hover:bg-indigo-50/20"
                    onClick={() => onOpen(message.id)}
                  >
                    <div className="flex flex-wrap items-start justify-between gap-3">
                      <div className="min-w-0 flex-1">
                        <div className="flex flex-wrap items-center gap-2">
                          <h2 className="min-w-0 truncate text-sm font-bold text-slate-950">
                            {messageTitle(message, channel)}
                          </h2>
                          <StatusBadge status={message.status}>{statusLabel(message.status)}</StatusBadge>
                        </div>
                        <p className="mt-1 line-clamp-2 text-xs leading-5 text-slate-500">
                          {message.bodyPreview || 'No message preview available.'}
                        </p>
                        <div className="mt-3 flex flex-wrap gap-x-4 gap-y-1 text-[11px] text-slate-500">
                          <span>{audienceLabel(message)}</span>
                          <span>{senderLabel(sender)}</span>
                          <span className="inline-flex items-center gap-1">
                            <FiClock aria-hidden="true" />
                            {formattedDate(message.scheduledAt || message.createdAt)}
                          </span>
                        </div>
                      </div>
                      {message.stats.total > 0 && (
                        <div className="grid shrink-0 grid-cols-2 gap-x-4 gap-y-1 text-right text-[11px]">
                          <span className="text-slate-400">Accepted</span>
                          <strong className="text-slate-800">{message.stats.accepted.toLocaleString()}</strong>
                          <span className="text-slate-400">Failed</span>
                          <strong className="text-slate-800">{message.stats.failed.toLocaleString()}</strong>
                        </div>
                      )}
                    </div>
                  </button>
                )
              })}
            </div>
          )}

          {page.totalPages > 1 && (
            <div className="mt-5 flex items-center justify-between gap-3 border-t border-slate-100 pt-4">
              <button
                type="button"
                className="secondary-button"
                disabled={loading || page.first}
                onClick={() => setPageNumber((current) => Math.max(0, current - 1))}
              >
                Previous
              </button>
              <span className="text-xs text-slate-500">
                Page {page.number + 1} of {page.totalPages}
              </span>
              <button
                type="button"
                className="secondary-button"
                disabled={loading || page.last}
                onClick={() => setPageNumber((current) => current + 1)}
              >
                Next
              </button>
            </div>
          )}
        </section>
      </div>
    </div>
  )
}
