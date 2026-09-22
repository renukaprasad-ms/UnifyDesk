import {
  FiCheckCircle,
  FiChevronRight,
  FiMessageSquare,
  FiPlus,
  FiX,
} from 'react-icons/fi'
import type { MessageTemplate } from '../../features/template/types'

type Props = {
  template: MessageTemplate
  onClose: () => void
  onCreateAnother: () => void
  onUseInBroadcast: () => void
}

export default function TemplateCreationCompleteModal({
  template,
  onClose,
  onCreateAnother,
  onUseInBroadcast,
}: Props) {
  const whatsapp = template.channelType === 'WHATSAPP'
  const ready = template.status === 'ACTIVE'

  return (
    <div className="mobile-modal-shell fixed inset-0 z-50 flex items-center justify-center bg-slate-950/40 p-4">
      <div className="mobile-sheet w-full max-w-xl rounded-2xl bg-white p-5 shadow-xl">
        <div className="flex items-start justify-between gap-4">
          <div>
            <p className="text-base font-bold text-slate-950">Template setup</p>
            <p className="mt-1 text-xs text-slate-500">Step 4 of 4 · Finish</p>
          </div>
          <button type="button" className="admin-back-button" onClick={onClose} aria-label="Close">
            <FiX aria-hidden="true" />
          </button>
        </div>

        <div className="mt-5 h-1.5 overflow-hidden rounded-full bg-slate-100">
          <div className="h-full w-full rounded-full bg-indigo-500" />
        </div>

        <div
          className={`mt-6 rounded-2xl border p-5 ${
            whatsapp
              ? 'border-amber-200 bg-amber-50'
              : ready
                ? 'border-emerald-200 bg-emerald-50'
                : 'border-slate-200 bg-slate-50'
          }`}
        >
          <div
            className={`flex size-11 items-center justify-center rounded-xl ${
              whatsapp
                ? 'bg-white text-amber-600'
                : ready
                  ? 'bg-white text-emerald-600'
                  : 'bg-white text-slate-600'
            }`}
          >
            {whatsapp ? (
              <FiMessageSquare aria-hidden="true" />
            ) : (
              <FiCheckCircle aria-hidden="true" />
            )}
          </div>

          <h2 className="mt-4 text-base font-bold text-slate-950">{template.name}</h2>

          {whatsapp ? (
            <>
              <p className="mt-2 text-sm leading-6 text-amber-800">
                Your WhatsApp template is saved locally. It is not sendable yet because Meta
                approval is still required.
              </p>
              <div className="mt-4 rounded-xl bg-white/80 p-3 text-xs leading-5 text-slate-600">
                Next provider phase:
                <strong className="ml-1">
                  choose a Meta WhatsApp connection → submit template → wait for approval → activate.
                </strong>
              </div>
            </>
          ) : (
            <p className="mt-2 text-sm leading-6 text-emerald-800">
              {ready
                ? 'The template is active and can now be selected from the Broadcast composer.'
                : 'The template is saved as a draft. Activate it when you are ready to use it in Broadcast.'}
            </p>
          )}
        </div>

        <div className="mt-6 flex flex-wrap items-center justify-between gap-3">
          <button
            type="button"
            className="secondary-button inline-flex items-center gap-2"
            onClick={onCreateAnother}
          >
            <FiPlus aria-hidden="true" />
            Create another
          </button>

          <div className="flex flex-wrap gap-2">
            <button type="button" className="secondary-button" onClick={onClose}>
              Done
            </button>
            {!whatsapp && ready && (
              <button
                type="button"
                className="primary-button inline-flex items-center gap-2"
                onClick={onUseInBroadcast}
              >
                Use in Broadcast
                <FiChevronRight aria-hidden="true" />
              </button>
            )}
          </div>
        </div>
      </div>
    </div>
  )
}
