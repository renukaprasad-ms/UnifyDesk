import { FiTrash2, FiX } from 'react-icons/fi'
import type { ChannelConnection } from '../../features/channel/types'

export default function DeleteChannelDataModal({
  connection,
  working,
  onClose,
  onConfirm,
}: {
  connection: ChannelConnection | null
  working: boolean
  onClose: () => void
  onConfirm: () => void
}) {
  if (!connection) return null

  return (
    <div
      className="mobile-modal-shell fixed inset-0 z-[95] flex items-center justify-center overflow-y-auto bg-slate-950/45 p-4"
      role="presentation"
      onMouseDown={(event) => {
        if (event.currentTarget === event.target && !working) onClose()
      }}
    >
      <div
        className="mobile-sheet my-auto w-full max-w-lg rounded-2xl bg-white p-5 shadow-2xl sm:p-6"
        role="dialog"
        aria-modal="true"
        aria-labelledby="delete-channel-data-title"
      >
        <div className="flex items-start justify-between gap-4">
          <div>
            <span className="flex size-11 items-center justify-center rounded-xl bg-rose-50 text-rose-600">
              <FiTrash2 aria-hidden="true" />
            </span>
            <h2 id="delete-channel-data-title" className="mt-4 text-lg font-bold text-slate-950">
              Delete imported data now?
            </h2>
            <p className="mt-2 text-sm leading-6 text-slate-500">
              Messaging will permanently queue deletion of the imported data retained for{' '}
              <span className="font-semibold text-slate-700">{connection.name}</span>.
            </p>
          </div>
          <button
            type="button"
            className="inline-flex size-9 shrink-0 items-center justify-center rounded-lg border border-slate-200 text-slate-500 hover:bg-slate-50"
            aria-label="Close delete data dialog"
            disabled={working}
            onClick={onClose}
          >
            <FiX aria-hidden="true" />
          </button>
        </div>

        <div className="mt-5 rounded-xl border border-rose-200 bg-rose-50 p-4 text-xs leading-5 text-rose-800">
          Imported messages, attachment metadata and provider sync state will be removed. After
          cleanup, reconnecting this account starts with a fresh sync.
        </div>

        <div className="mt-6 flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
          <button
            type="button"
            className="secondary-button justify-center"
            disabled={working}
            onClick={onClose}
          >
            Keep data
          </button>
          <button
            type="button"
            className="inline-flex items-center justify-center rounded-xl bg-rose-600 px-4 py-2.5 text-sm font-semibold text-white transition hover:bg-rose-700 disabled:cursor-not-allowed disabled:opacity-50"
            disabled={working}
            onClick={onConfirm}
          >
            {working ? 'Queuing deletion…' : 'Delete data permanently'}
          </button>
        </div>
      </div>
    </div>
  )
}
