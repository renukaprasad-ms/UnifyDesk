import { FiClock, FiTrash2, FiX } from 'react-icons/fi'
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
  if (!connection) return null

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
              Provider access, credentials and live synchronization stop immediately in both
              options. Choose what Messaging should do with the imported data already stored.
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
            onClick={() => onConfirm('KEEP_7_DAYS')}
            className="rounded-2xl border-2 border-indigo-200 bg-indigo-50/60 p-4 text-left transition hover:border-indigo-300 hover:bg-indigo-50 disabled:opacity-60"
          >
            <div className="flex items-start gap-3">
              <span className="flex size-10 shrink-0 items-center justify-center rounded-xl bg-white text-indigo-600 shadow-sm">
                <FiClock aria-hidden="true" />
              </span>
              <span className="min-w-0">
                <span className="flex flex-wrap items-center gap-2">
                  <span className="text-sm font-bold text-slate-950">
                    Keep imported data for 7 days
                  </span>
                  <span className="rounded-full bg-indigo-100 px-2 py-0.5 text-[10px] font-bold uppercase tracking-wide text-indigo-700">
                    Recommended
                  </span>
                </span>
                <span className="mt-1.5 block text-xs leading-5 text-slate-600">
                  The channel disappears from the active inbox immediately, but its imported
                  messages remain recoverable for 7 days. Reconnect the same account before the
                  deadline to keep that history.
                </span>
              </span>
            </div>
          </button>

          <button
            type="button"
            disabled={working}
            onClick={() => onConfirm('DELETE_NOW')}
            className="rounded-2xl border border-rose-200 bg-white p-4 text-left transition hover:border-rose-300 hover:bg-rose-50/50 disabled:opacity-60"
          >
            <div className="flex items-start gap-3">
              <span className="flex size-10 shrink-0 items-center justify-center rounded-xl bg-rose-50 text-rose-600">
                <FiTrash2 aria-hidden="true" />
              </span>
              <span className="min-w-0">
                <span className="text-sm font-bold text-rose-700">Delete imported data now</span>
                <span className="mt-1.5 block text-xs leading-5 text-slate-600">
                  Permanently queue deletion of imported messages, attachment metadata and sync
                  state. This cannot be undone and reconnecting later will require a fresh sync.
                </span>
              </span>
            </div>
          </button>
        </div>

        <p className="mt-4 text-[11px] leading-5 text-slate-400">
          Messaging removes provider credentials immediately regardless of the data option you
          choose.
        </p>

        <div className="mt-6 flex justify-end">
          <button type="button" className="secondary-button" disabled={working} onClick={onClose}>
            Cancel
          </button>
        </div>
      </div>
    </div>
  )
}
