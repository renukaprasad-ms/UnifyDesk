import { useEffect, useRef, useState } from 'react'
import { FiClock, FiTrash2, FiX } from 'react-icons/fi'
import type { ChannelConnection, DisconnectDataPolicy } from '../../features/channel/types'

export default function WhatsAppDisconnectModal({
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
  const closeRef = useRef<HTMLButtonElement | null>(null)

  useEffect(() => {
    if (!connection) return
    setPolicy('KEEP_7_DAYS')
    window.setTimeout(() => closeRef.current?.focus(), 0)
  }, [connection?.id])

  useEffect(() => {
    if (!connection) return
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape' && !working) onClose()
    }
    window.addEventListener('keydown', onKeyDown)
    return () => window.removeEventListener('keydown', onKeyDown)
  }, [connection, onClose, working])

  if (!connection) return null

  return (
    <div
      className="mobile-modal-shell fixed inset-0 z-[95] flex items-center justify-center overflow-y-auto bg-slate-950/45 p-4"
      role="presentation"
      onMouseDown={(event) => {
        if (event.target === event.currentTarget && !working) onClose()
      }}
    >
      <div
        className="mobile-sheet my-auto w-full max-w-xl rounded-2xl bg-white p-5 shadow-2xl sm:p-6"
        role="dialog"
        aria-modal="true"
        aria-labelledby="disconnect-whatsapp-title"
        aria-describedby="disconnect-whatsapp-description"
      >
        <div className="flex items-start justify-between gap-4">
          <div>
            <p className="eyebrow">DISCONNECT WHATSAPP</p>
            <h2 id="disconnect-whatsapp-title" className="mt-2 text-lg font-bold text-slate-950">
              Disconnect {connection.name} from UnifyDesk?
            </h2>
            <p id="disconnect-whatsapp-description" className="mt-2 text-sm leading-6 text-slate-600">
              UnifyDesk will stop using this WhatsApp connection and remove its local provider
              credentials. This does not delete your WhatsApp Business Account, phone number, or
              other Meta business assets.
            </p>
          </div>
          <button
            ref={closeRef}
            type="button"
            className="inline-flex size-9 shrink-0 items-center justify-center rounded-lg border border-slate-200 text-slate-500 hover:bg-slate-50"
            aria-label="Close WhatsApp disconnect dialog"
            disabled={working}
            onClick={onClose}
          >
            <FiX aria-hidden="true" />
          </button>
        </div>

        <div className="mt-5 grid gap-3">
          <button
            type="button"
            className={`rounded-2xl border-2 p-4 text-left transition ${
              policy === 'KEEP_7_DAYS'
                ? 'border-indigo-300 bg-indigo-50/70'
                : 'border-slate-200 hover:border-slate-300'
            }`}
            aria-pressed={policy === 'KEEP_7_DAYS'}
            disabled={working}
            onClick={() => setPolicy('KEEP_7_DAYS')}
          >
            <span className="flex gap-3">
              <FiClock className="mt-0.5 shrink-0 text-indigo-600" aria-hidden="true" />
              <span>
                <span className="block text-sm font-bold text-slate-900">Keep imported data for 7 days</span>
                <span className="mt-1 block text-xs leading-5 text-slate-600">
                  Reconnecting the same WhatsApp account during the retention period can keep local history.
                </span>
              </span>
            </span>
          </button>
          <button
            type="button"
            className={`rounded-2xl border-2 p-4 text-left transition ${
              policy === 'DELETE_NOW'
                ? 'border-rose-300 bg-rose-50/70'
                : 'border-slate-200 hover:border-rose-200'
            }`}
            aria-pressed={policy === 'DELETE_NOW'}
            disabled={working}
            onClick={() => setPolicy('DELETE_NOW')}
          >
            <span className="flex gap-3">
              <FiTrash2 className="mt-0.5 shrink-0 text-rose-600" aria-hidden="true" />
              <span>
                <span className="block text-sm font-bold text-rose-700">Delete imported UnifyDesk data now</span>
                <span className="mt-1 block text-xs leading-5 text-slate-600">
                  This removes local imported WhatsApp history according to the existing retention policy.
                  It still does not delete Meta-owned business assets.
                </span>
              </span>
            </span>
          </button>
        </div>

        <div className="mt-6 flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
          <button type="button" className="secondary-button justify-center" disabled={working} onClick={onClose}>
            Cancel
          </button>
          <button
            type="button"
            className={policy === 'DELETE_NOW' ? 'rounded-xl bg-rose-600 px-4 py-2.5 text-sm font-semibold text-white' : 'primary-button justify-center'}
            disabled={working}
            onClick={() => onConfirm(policy)}
          >
            {working ? 'Disconnecting…' : policy === 'DELETE_NOW' ? 'Disconnect & delete local data' : 'Disconnect'}
          </button>
        </div>
      </div>
    </div>
  )
}
