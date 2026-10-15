import { useEffect, useId, useRef, useState, type ReactNode } from 'react'
import { FiFilter, FiX } from 'react-icons/fi'
export default function FilterPopover({
  children,
  activeCount = 0,
  onReset,
  wide = false,
  disabled = false,
  className = '',
}: {
  children: ReactNode
  activeCount?: number
  onReset: () => void
  wide?: boolean
  disabled?: boolean
  className?: string
}) {
  const [open, setOpen] = useState(false)
  const root = useRef<HTMLDivElement>(null),
    trigger = useRef<HTMLButtonElement>(null),
    panel = useRef<HTMLDivElement>(null)
  const id = useId()
  useEffect(() => {
    if (!open) return
    ;(
      panel.current?.querySelector<HTMLElement>('input,select') ||
      panel.current?.querySelector<HTMLElement>('button')
    )?.focus()
    const outside = (event: PointerEvent) => {
      if (!root.current?.contains(event.target as Node)) setOpen(false)
    }
    const escape = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        event.stopPropagation()
        setOpen(false)
        trigger.current?.focus()
      }
    }
    document.addEventListener('pointerdown', outside)
    document.addEventListener('keydown', escape)
    return () => {
      document.removeEventListener('pointerdown', outside)
      document.removeEventListener('keydown', escape)
    }
  }, [open])
  return (
    <div
      ref={root}
      className={`relative shrink-0 ${className}`}
      onBlur={(e) => {
        if (e.relatedTarget && !e.currentTarget.contains(e.relatedTarget as Node)) setOpen(false)
      }}
    >
      <button
        ref={trigger}
        type="button"
        className="secondary-button gap-2"
        aria-label={activeCount ? `Filters, ${activeCount} active` : 'Filters'}
        aria-expanded={open}
        aria-controls={id}
        disabled={disabled}
        onClick={() => setOpen((v) => !v)}
      >
        <FiFilter />
        Filters
        {activeCount > 0 && (
          <span className="rounded-full bg-indigo-600 px-1.5 text-[10px] text-white">
            {activeCount}
          </span>
        )}
      </button>
      {open && (
        <div
          ref={panel}
          id={id}
          role="region"
          aria-label="Filter options"
          className={`panel absolute right-0 top-full z-40 mt-2 max-h-[70dvh] w-[min(22rem,calc(100vw-3rem))] overflow-y-auto rounded-xl p-4 shadow-xl ${wide ? 'sm:w-[min(40rem,calc(100vw-3rem))]' : ''}`}
          onSubmit={() => {
            setOpen(false)
            trigger.current?.focus()
          }}
        >
          <div className="mb-4 flex items-center justify-between">
            <h2 className="text-sm font-bold">Filters</h2>
            <button
              type="button"
              aria-label="Close filters"
              className="secondary-button p-1.5"
              onClick={() => {
                setOpen(false)
                trigger.current?.focus()
              }}
            >
              <FiX />
            </button>
          </div>
          <div className="space-y-4 [&_select]:w-full [&_input]:w-full [&_label]:block [&_select]:ml-0 [&_input]:ml-0">
            {children}
          </div>
          <div className="mt-4 flex items-center justify-between border-t border-slate-200 pt-3">
            <button
              type="button"
              className="text-xs font-semibold text-indigo-600"
              disabled={disabled || !activeCount}
              onClick={onReset}
            >
              Clear filters
            </button>
            <button
              type="button"
              className="secondary-button"
              onClick={() => {
                setOpen(false)
                trigger.current?.focus()
              }}
            >
              Done
            </button>
          </div>
        </div>
      )}
    </div>
  )
}
