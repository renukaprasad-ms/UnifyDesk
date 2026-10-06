import { useEffect, useRef } from 'react'
import { FiMail } from 'react-icons/fi'
import type { ChannelConnection } from '../../features/channel/types'
import type { InboxConversation } from '../../features/inbox/types'
import { conversationParticipant } from './conversationListUtils'
import { Skeleton } from '../ui/Skeleton'

type ConversationListProps = {
  conversations: InboxConversation[]
  connections: ChannelConnection[]
  selectedId: string | null
  loading?: boolean
  loadingMore?: boolean
  totalElements?: number
  compact?: boolean
  onSelect: (conversation: InboxConversation) => void
  onLoadMore?: () => void
  hasMore?: boolean
}

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

export default function ConversationList({
  conversations,
  connections,
  selectedId,
  loading = false,
  loadingMore = false,
  totalElements = conversations.length,
  compact = false,
  onSelect,
  onLoadMore,
  hasMore = false,
}: ConversationListProps) {
  const loadMoreRef = useRef<HTMLDivElement | null>(null)
  const loadRequestedRef = useRef(false)

  useEffect(() => {
    if (!loadingMore) {
      loadRequestedRef.current = false
    }
  }, [loadingMore, conversations.length])

  useEffect(() => {
    if (!hasMore || !onLoadMore || loadingMore) return
    const sentinel = loadMoreRef.current
    if (!sentinel) return

    const observer = new IntersectionObserver(
      (entries) => {
        if (entries.some((entry) => entry.isIntersecting) && !loadRequestedRef.current) {
          loadRequestedRef.current = true
          onLoadMore()
        }
      },
      { rootMargin: '180px 0px' },
    )

    observer.observe(sentinel)
    return () => observer.disconnect()
  }, [hasMore, loadingMore, onLoadMore])

  if (loading) {
    return (
      <div className="divide-y divide-slate-100" role="status" aria-label="Loading conversations">
        {Array.from({ length: 7 }).map((_, index) => (
          <div key={index} className={compact ? 'px-3 py-3' : 'px-4 py-4'}>
            <div className="flex items-start justify-between gap-3">
              <Skeleton className="h-4 w-32" />
              <Skeleton className="h-3 w-12" />
            </div>
            <Skeleton className="mt-2 h-3 w-3/4" />
            <Skeleton className="mt-2 h-3 w-full" />
          </div>
        ))}
      </div>
    )
  }

  if (conversations.length === 0) {
    return (
      <div className="px-4 py-10 text-center">
        <FiMail className="mx-auto text-2xl text-slate-300" aria-hidden="true" />
        <p className="mt-3 text-xs font-semibold text-slate-700">No conversations yet</p>
        <p className="mt-1 text-[11px] leading-5 text-slate-500">Synced emails will appear here.</p>
      </div>
    )
  }

  return (
    <>
      {!compact && (
        <div className="border-b border-slate-100 px-4 py-3">
          <p className="text-[11px] text-slate-500">
            {conversations.length} of {totalElements} loaded
          </p>
        </div>
      )}

      {conversations.map((conversation) => {
        const connection = connections.find((item) => item.id === conversation.connectionId)
        const counterpart = conversationParticipant(
          conversation,
          connection?.externalAccountId ?? undefined,
        )
        const active = selectedId === conversation.id

        return (
          <button
            key={conversation.id}
            type="button"
            onClick={() => onSelect(conversation)}
            className={`conversation-list-item group w-full border-b text-left transition ${
              compact ? 'px-3 py-3' : 'px-4 py-4'
            } ${active ? 'is-active' : ''}`}
          >
            <div className="flex items-start justify-between gap-2">
              <p
                className={`min-w-0 flex-1 truncate font-semibold ${
                  compact ? 'text-[12px]' : 'text-sm'
                } text-slate-900`}
              >
                {counterpart}
              </p>
              <span className="shrink-0 text-[10px] text-slate-400">
                {formatDate(conversation.lastMessageAt)}
              </span>
            </div>

            <p className="mt-1 truncate text-[11px] font-medium text-slate-700">
              {conversation.subject || '(no subject)'}
            </p>

            <div className="mt-1 flex items-center gap-2">
              <p className="min-w-0 flex-1 truncate text-[10px] leading-4 text-slate-500">
                {conversation.lastMessagePreview || 'No preview available'}
              </p>
              {conversation.unreadCount > 0 && (
                <span className="inline-flex min-w-5 shrink-0 items-center justify-center rounded-full bg-indigo-500 px-1.5 py-0.5 text-[9px] font-bold text-white">
                  {conversation.unreadCount > 99 ? '99+' : conversation.unreadCount}
                </span>
              )}
            </div>
          </button>
        )
      })}

      {hasMore && onLoadMore && (
        <div
          ref={loadMoreRef}
          className="flex min-h-12 items-center justify-center px-3 py-2 text-[10px] text-slate-400"
          aria-live="polite"
        >
          {loadingMore ? 'Loading more conversations…' : 'Scroll for more'}
        </div>
      )}
    </>
  )
}
