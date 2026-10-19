import { useEffect, useId, useRef, type ReactNode } from 'react'
import { createPortal } from 'react-dom'
import { FiX } from 'react-icons/fi'
import { showGlobalSnackbar } from '../feedback/SnackbarProvider'

export default function WorkspaceDialog({
  title,
  children,
  onClose,
  busy = false,
  error = '',
  wide = false,
}: {
  title: string
  children: ReactNode
  onClose: () => void
  busy?: boolean
  error?: string
  wide?: boolean
}) {
  const titleId = useId()
  useEffect(() => {
    if (error) showGlobalSnackbar(error, { variant: 'error' })
  }, [error])
  const panel = useRef<HTMLDivElement>(null)
  const close = useRef(onClose)
  const saving = useRef(busy)
  useEffect(() => {
    close.current = onClose
    saving.current = busy
  }, [onClose, busy])
  useEffect(() => {
    const previous = document.activeElement as HTMLElement | null
    const overflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    const controls = () =>
      Array.from(
        panel.current?.querySelectorAll<HTMLElement>(
          'button:not(:disabled),input:not(:disabled),select:not(:disabled),textarea:not(:disabled),a[href],[tabindex="0"]',
        ) || [],
      ).filter((el) => el.getAttribute('type') !== 'hidden')
    const first = panel.current?.querySelector<HTMLElement>(
      'input:not(:disabled),select:not(:disabled),textarea:not(:disabled)',
    )
    ;(first || controls()[0] || panel.current)?.focus()
    const key = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        event.stopPropagation()
        if (!saving.current) close.current()
      }
      if (event.key === 'Tab') {
        const elements = controls(),
          first = elements[0],
          last = elements.at(-1)
        if (!first) {
          event.preventDefault()
          panel.current?.focus()
          return
        }
        if (
          event.shiftKey &&
          (document.activeElement === first || !panel.current?.contains(document.activeElement))
        ) {
          event.preventDefault()
          last?.focus()
        } else if (
          !event.shiftKey &&
          (document.activeElement === last || !panel.current?.contains(document.activeElement))
        ) {
          event.preventDefault()
          first.focus()
        }
      }
    }
    document.addEventListener('keydown', key)
    return () => {
      document.removeEventListener('keydown', key)
      document.body.style.overflow = overflow
      if (previous?.isConnected) previous.focus()
    }
  }, [])
  return createPortal(
    <div
      className="fixed inset-0 z-[120] flex items-center justify-center bg-slate-950/60 p-3 backdrop-blur-sm sm:p-6"
      onMouseDown={(e) => {
        if (e.target === e.currentTarget && !busy) onClose()
      }}
    >
      <div
        ref={panel}
        tabIndex={-1}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        aria-busy={busy}
        className={`panel flex max-h-[90dvh] w-full flex-col overflow-hidden rounded-2xl shadow-2xl ${wide ? 'max-w-4xl' : 'max-w-2xl'}`}
      >
        <header className="flex shrink-0 items-center justify-between gap-4 border-b border-slate-200 px-5 py-4">
          <h2 id={titleId} className="text-lg font-bold text-slate-950">
            {title}
          </h2>
          <button
            type="button"
            className="secondary-button p-2"
            aria-label={'Close ' + title}
            disabled={busy}
            onClick={onClose}
          >
            <FiX />
          </button>
        </header>
        <div className="overflow-y-auto p-5">
          {children}
        </div>
      </div>
    </div>,
    document.body,
  )
}
