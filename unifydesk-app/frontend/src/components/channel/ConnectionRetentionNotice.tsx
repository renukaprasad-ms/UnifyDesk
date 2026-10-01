import { useEffect, useState } from 'react'\nimport { FiClock, FiLink, FiTrash2 } from 'react-icons/fi'
import type { ChannelConnection } from '../../features/channel/types'

const formatPurgeDate = (value: string) =>
  new Intl.DateTimeFormat(undefined, {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(new Date(value))

export default function ConnectionRetentionNotice({
  connection,
  working,
  onReconnect,
  onDeleteNow,
}: {
  connection: ChannelConnection
  working: boolean
  onReconnect?: () => void
  onDeleteNow: () => void
}) {
  if (connection.status !== 'DISCONNECTED') return null

  const hasDeadline = Boolean(connection.purgeAfter)
  const [deletionQueued, setDeletionQueued] = useState(false)

  useEffect(() => {
    setDeletionQueued(
      connection.purgeAfter
        ? new Date(connection.purgeAfter).getTime() <= Date.now()
        : false,
    )
  }, [connection.purgeAfter])

  return (
    <div className="mt-3 rounded-xl border border-amber-200 bg-amber-50/70 p-3">
      <div className="flex items-start gap-2.5">
        <FiClock className="mt-0.5 shrink-0 text-amber-600" aria-hidden="true" />
        <div className="min-w-0 flex-1">
          <p className="text-xs font-bold text-amber-900">
            {deletionQueued
              ? 'Permanent deletion is queued'
              : hasDeadline
                ? `Imported data is kept until ${formatPurgeDate(connection.purgeAfter as string)}`
                : 'Imported data is still retained'}
          </p>
          <p className="mt-1 text-[11px] leading-5 text-amber-800/80">
            {deletionQueued
              ? 'The channel is disconnected and its stored imported data will be removed by the cleanup worker.'
              : hasDeadline
                ? 'The channel is hidden from the inbox. Reconnect the same account before this deadline to cancel deletion and keep its imported history.'
                : 'This connection was disconnected before automatic retention was enabled. Its imported data remains hidden from the inbox until you reconnect or delete it.'}
          </p>

          <div className="mt-3 flex flex-wrap gap-2">
            {onReconnect && !deletionQueued && (
              <button
                type="button"
                className="secondary-button inline-flex items-center gap-2 px-3 py-2 text-xs"
                disabled={working}
                onClick={onReconnect}
              >
                <FiLink aria-hidden="true" />
                Reconnect
              </button>
            )}
            {!deletionQueued && (
              <button
                type="button"
                className="secondary-button inline-flex items-center gap-2 px-3 py-2 text-xs text-rose-600"
                disabled={working}
                onClick={onDeleteNow}
              >
                <FiTrash2 aria-hidden="true" />
                Delete now
              </button>
            )}
          </div>
        </div>
      </div>
    </div>
  )
}
