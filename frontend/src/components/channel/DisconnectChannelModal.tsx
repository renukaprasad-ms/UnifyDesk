import { useEffect, useState } from 'react'
import { FiCheck, FiClock, FiTrash2, FiX } from 'react-icons/fi'
import type {
  ChannelConnection,
  DisconnectDataPolicy,
} from '../../features/channel/types'

export default function DisconnectChannelModal({
  connection,
  working,
  onClose,
  onConfirm,
}: {
  connection: ChannelConnection | null
  working: boolean
  onClose: () => void
  onConfirm: (policy: DisconnectDataPolicy) => void
}) {
  const [policy, setPolicy] = useState<DisconnectDataPolicy>('KEEP_7_DAYS')

  useEffect(() => {
    if (connection) setPolicy('KEEP_7_DAYS')
  }, [connection?.id])

  if (!connection) return null

  const destructive = policy === 'DELETE_NOW'

  return (
    <div
      className="mobile-modal-shell fixed inset-0 z-[90] flex items-center justify-center overflow-y-auto bg-slate-950/45 p-4"
      role="presentation"
      onMouseDown={(event) => {
        if (event.currentTarget === event.target && !working) onClose()
      }}
    >
      <div
        className="mobile-sheet my-auto w-full max-w-xl rounded-2xl bg-white p-5 shadow-2xl sm:p-6"
        role="dialog"
        aria-modal="true"
        aria-labelledby="disconnect-channel-title"
      >
        <div className="flex items-start justify-between gap-4">
          <div>
            <p className="eyebrow">DISCONNECT CHANNEL</p>
            <h2 id="disconnect-channel-title" className="mt-2 text-lg font-bold text-slate-950">
              Disconnect {connection.name}?
            </h2>
            <p className="mt-2 text-sm leading-6 text-slate-500">
              Provider access, credentials and live synchronization stop immediately. Choose what
              Messaging should do with the imported data already stored.
            </p>
          </div>
          <button
            type="button"
            className="inline-flex size-9 shrink-0 items-center justify-center rounded-lg border border-slate-200 text-slate-500 hover:bg-slate-50"
            aria-label="Close disconnect dialog"
            disabled={working}
            onClick={onClose}
          >
            <FiX aria-hidden="true" />
          </button>
        </div>

        <div className="mt-5 grid gap-3">
          <button
            type="button"
            disabled={working}
            aria-pressed={policy === 'KEEP_7_DAYS'}
            onClick={() => setPolicy('KEEP_7_DAYS')}
            className={`rounded-2xl border-2 p-4 text-left transition disabled:opacity-60 ${
              policy === 'KEEP_7_DAYS'
                ? 'border-indigo-300 bg-indigo-50/70'
                : 'border-slate-200 bg-white hover:border-slate-300'
            }`}
          >
            <div className="flex items-start gap-3">
              <span className="flex size-10 shrink-0 items-center justify-center rounded-xl bg-white text-indigo-600 shadow-sm">
                <FiClock aria-hidden="true" />
              </span>
              <span className="min-w-0 flex-1">
                <span className="flex flex-wrap items-center gap-2">
                  <span className="text-sm font-bold text-slate-950">
                    Keep imported data for 7 days
                  </span>
                  <span className="rounded-full bg-indigo-100 px-2 py-0.5 text-[10px] font-bold uppercase tracking-wide text-indigo-700">
                    Recommended
                  </span>
                </span>
                <span className="mt-1.5 block text-xs leading-5 text-slate-600">
                  The channel disappears from the active inbox immediately, but imported messages
                  remain recoverable for 7 days. Reconnect the same account before the deadline to
                  keep that history.
                </span>
              </span>
              {policy === 'KEEP_7_DAYS' && (
                <span className="flex size-6 shrink-0 items-center justify-center rounded-full bg-indigo-600 text-white">
                  <FiCheck aria-hidden="true" size={13} />
                </span>
              )}
            </div>
          </button>

          <button
            type="button"
            disabled={working}
            aria-pressed={policy === 'DELETE_NOW'}
            onClick={() => setPolicy('DELETE_NOW')}
            className={`rounded-2xl border-2 p-4 text-left transition disabled:opacity-60 ${
              policy === 'DELETE_NOW'
                ? 'border-rose-300 bg-rose-50/70'
                : 'border-slate-200 bg-white hover:border-rose-200 hover:bg-rose-50/30'
            }`}
          >
            <div className="flex items-start gap-3">
              <span className="flex size-10 shrink-0 items-center justify-center rounded-xl bg-rose-50 text-rose-600">
                <FiTrash2 aria-hidden="true" />
              </span>
              <span className="min-w-0 flex-1">
                <span className="text-sm font-bold text-rose-700">Delete imported data now</span>
                <span className="mt-1.5 block text-xs leading-5 text-slate-600">
                  Queue permanent deletion of imported messages, attachment metadata and sync
                  state. Reconnecting later will require a fresh sync.
                </span>
              </span>
              {policy === 'DELETE_NOW' && (
                <span className="flex size-6 shrink-0 items-center justify-center rounded-full bg-rose-600 text-white">
                  <FiCheck aria-hidden="true" size={13} />
                </span>
              )}
            </div>
          </button>
        </div>

        <p className="mt-4 text-[11px] leading-5 text-slate-400">
          Messaging removes provider credentials immediately regardless of the data option you
          choose.
        </p>

        <div className="mt-6 flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
          <button type="button" className="secondary-button justify-center" disabled={working} onClick={onClose}>
            Cancel
          </button>
          <button
            type="button"
            className={destructive ? 'danger-button justify-center' : 'primary-button justify-center'}
            disabled={working}
            onClick={() => onConfirm(policy)}
          >
            {working
              ? 'Disconnecting…'
              : destructive
                ? 'Disconnect & delete data'
                : 'Disconnect'}
          </button>
        </div>
      </div>
    </div>
  )
}
