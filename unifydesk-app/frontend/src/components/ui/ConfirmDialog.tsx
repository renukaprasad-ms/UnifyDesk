import { useEffect, useRef, type ReactNode } from 'react'
import { FiAlertTriangle, FiLogOut, FiTrash2, FiX } from 'react-icons/fi'
import Spinner from './Spinner'

export type ConfirmDialogTone = 'default' | 'warning' | 'danger'

type ConfirmDialogProps = {
  open: boolean
  title: string
  description: string
  confirmLabel: string
  cancelLabel?: string
  tone?: ConfirmDialogTone
  working?: boolean
  onConfirm: () => void
  onCancel: () => void
  kind?: 'delete' | 'logout' | 'generic'
  children?: ReactNode
  confirmDisabled?: boolean
}

const toneStyles = {
  default: {
    icon: 'confirm-dialog-icon-default',
    button: 'confirm-dialog-button-default',
  },
  warning: {
    icon: 'confirm-dialog-icon-warning',
    button: 'confirm-dialog-button-warning',
  },
  danger: {
    icon: 'confirm-dialog-icon-danger',
    button: 'confirm-dialog-button-danger',
  },
}

export default function ConfirmDialog({
  open,
  title,
  description,
  confirmLabel,
  cancelLabel = 'No, cancel',
  tone = 'default',
  working = false,
  onConfirm,
  onCancel,
  kind = 'generic',
  children,
  confirmDisabled = false,
}: ConfirmDialogProps) {
  const cancelRef = useRef<HTMLButtonElement | null>(null)

  useEffect(() => {
    if (!open) return
    const previousOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    window.setTimeout(() => cancelRef.current?.focus(), 0)
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape' && !working) onCancel()
    }
    window.addEventListener('keydown', onKeyDown)
    return () => {
      document.body.style.overflow = previousOverflow
      window.removeEventListener('keydown', onKeyDown)
    }
  }, [open, onCancel, working])

  if (!open) return null

  const visual = toneStyles[tone]
  const Icon = kind === 'delete' ? FiTrash2 : kind === 'logout' ? FiLogOut : FiAlertTriangle

  return (
    <div
      className="fixed inset-0 z-[120] flex items-center justify-center overflow-y-auto bg-slate-950/45 p-4"
      role="presentation"
      onMouseDown={(event) => {
        if (event.target === event.currentTarget && !working) onCancel()
      }}
    >
      <section
        className="confirm-dialog-panel my-auto w-full max-w-md rounded-2xl p-5 shadow-2xl sm:p-6"
        role="alertdialog"
        aria-modal="true"
        aria-labelledby="confirm-dialog-title"
        aria-describedby="confirm-dialog-description"
      >
        <div className="flex items-start gap-4">
          <span
            className={`flex size-11 shrink-0 items-center justify-center rounded-xl ${visual.icon}`}
          >
            <Icon aria-hidden="true" size={20} />
          </span>
          <div className="min-w-0 flex-1">
            <h2 id="confirm-dialog-title" className="text-lg font-bold text-slate-950">
              {title}
            </h2>
            <p id="confirm-dialog-description" className="mt-2 text-sm leading-6 text-slate-600">
              {description}
            </p>
          </div>
          <button
            type="button"
            className="flex size-9 shrink-0 items-center justify-center rounded-lg text-slate-400 transition hover:bg-slate-100 hover:text-slate-700 disabled:opacity-50"
            aria-label="Close confirmation"
            disabled={working}
            onClick={onCancel}
          >
            <FiX aria-hidden="true" />
          </button>
        </div>

        {children && <div className="mt-5">{children}</div>}

        <div className="mt-6 flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
          <button
            ref={cancelRef}
            type="button"
            className="secondary-button justify-center"
            disabled={working}
            onClick={onCancel}
          >
            {cancelLabel}
          </button>
          <button
            type="button"
            className={`inline-flex items-center justify-center gap-2 rounded-xl px-4 py-2.5 text-sm font-semibold transition disabled:cursor-not-allowed disabled:opacity-55 ${visual.button}`}
            disabled={working || confirmDisabled}
            aria-busy={working}
            onClick={onConfirm}
          >
            {working && <Spinner size="sm" label={confirmLabel} />}
            {working ? 'Working…' : confirmLabel}
          </button>
        </div>
      </section>
    </div>
  )
}
