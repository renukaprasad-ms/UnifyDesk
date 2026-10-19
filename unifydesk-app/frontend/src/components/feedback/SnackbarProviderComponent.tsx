import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import { createPortal } from 'react-dom'
import { FiAlertCircle, FiCheckCircle, FiInfo, FiX } from 'react-icons/fi'
import {
  SnackbarContext,
  externalSnackbarListeners,
  pendingExternalSnackbars,
  type ExternalSnackbarRequest,
  type SnackbarApi,
  type SnackbarVariant,
} from './SnackbarProvider'

type SnackbarItem = {
  id: number
  message: string
  variant: SnackbarVariant
  title?: string
  duration: number
}

const durationFor = (variant: SnackbarVariant) => {
  if (variant === 'error' || variant === 'warning') return 6500
  if (variant === 'success') return 4000
  return 5000
}

const visualFor = (variant: SnackbarVariant) => {
  if (variant === 'success') {
    return {
      shell: 'border-emerald-200 bg-white',
      icon: 'bg-emerald-50 text-emerald-600',
      title: 'text-emerald-800',
      Icon: FiCheckCircle,
      defaultTitle: 'Success',
    }
  }
  if (variant === 'error') {
    return {
      shell: 'border-rose-200 bg-white',
      icon: 'bg-rose-50 text-rose-600',
      title: 'text-rose-800',
      Icon: FiAlertCircle,
      defaultTitle: 'Something went wrong',
    }
  }
  if (variant === 'warning') {
    return {
      shell: 'border-amber-200 bg-white',
      icon: 'bg-amber-50 text-amber-600',
      title: 'text-amber-800',
      Icon: FiAlertCircle,
      defaultTitle: 'Action required',
    }
  }
  return {
    shell: 'border-sky-200 bg-white',
    icon: 'bg-sky-50 text-sky-600',
    title: 'text-sky-800',
    Icon: FiInfo,
    defaultTitle: 'Info',
  }
}

function SnackbarCard({
  item,
  onDismiss,
}: {
  item: SnackbarItem
  onDismiss: (id: number) => void
}) {
  const visual = visualFor(item.variant)

  return (
    <div
      role={item.variant === 'error' ? 'alert' : 'status'}
      aria-live={item.variant === 'error' ? 'assertive' : 'polite'}
      className={`pointer-events-auto flex w-full items-start gap-3 rounded-2xl border p-3.5 shadow-[0_14px_40px_rgba(15,23,42,0.16)] sm:min-w-[340px] sm:max-w-[440px] ${visual.shell}`}
    >
      <span
        className={`flex size-9 shrink-0 items-center justify-center rounded-xl ${visual.icon}`}
      >
        <visual.Icon aria-hidden="true" size={17} />
      </span>

      <div className="min-w-0 flex-1 pt-0.5">
        <p className={`text-xs font-bold ${visual.title}`}>{item.title ?? visual.defaultTitle}</p>
        <p className="mt-1 break-words text-xs leading-5 text-slate-600">{item.message}</p>
      </div>

      <button
        type="button"
        className="flex size-7 shrink-0 items-center justify-center rounded-lg text-slate-400 transition hover:bg-slate-100 hover:text-slate-700"
        aria-label="Dismiss notification"
        onClick={() => onDismiss(item.id)}
      >
        <FiX aria-hidden="true" />
      </button>
    </div>
  )
}

export function SnackbarProvider({ children }: { children: ReactNode }) {
  const [items, setItems] = useState<SnackbarItem[]>([])
  const activeItems = useRef<SnackbarItem[]>([])
  const nextId = useRef(1)
  const timers = useRef(new Map<number, number>())

  const dismiss = useCallback((id: number) => {
    const timer = timers.current.get(id)
    if (timer !== undefined) {
      window.clearTimeout(timer)
      timers.current.delete(id)
    }
    activeItems.current = activeItems.current.filter((item) => item.id !== id)
    setItems(activeItems.current)
  }, [])

  const show = useCallback(
    (message: string, options?: Parameters<SnackbarApi['show']>[1]) => {
      const normalized = message.trim()
      if (!normalized) return -1
      const existing = activeItems.current.find((item) => item.message === normalized)
      if (existing) return existing.id

      const variant = options?.variant ?? 'info'
      const id = nextId.current++
      const duration = options?.duration ?? durationFor(variant)
      const item: SnackbarItem = {
        id,
        message: normalized,
        variant,
        title: options?.title,
        duration,
      }

      const next = [...activeItems.current, item]
      const removed = next.slice(0, Math.max(0, next.length - 3))
      removed.forEach((removedItem) => {
        const timer = timers.current.get(removedItem.id)
        if (timer !== undefined) {
          window.clearTimeout(timer)
          timers.current.delete(removedItem.id)
        }
      })
      activeItems.current = next.slice(-3)
      setItems(activeItems.current)

      if (duration > 0) {
        const timer = window.setTimeout(() => dismiss(id), duration)
        timers.current.set(id, timer)
      }
      return id
    },
    [dismiss],
  )

  const clear = useCallback(() => {
    timers.current.forEach((timer) => window.clearTimeout(timer))
    timers.current.clear()
    activeItems.current = []
    setItems([])
  }, [])

  useEffect(() => {
    const listener = ({ message, options }: ExternalSnackbarRequest) => {
      show(message, options)
    }
    externalSnackbarListeners.add(listener)
    pendingExternalSnackbars.splice(0).forEach(listener)
    return () => {
      externalSnackbarListeners.delete(listener)
    }
  }, [show])

  useEffect(() => {
    activeItems.current.forEach((item) => {
      if (item.duration > 0 && !timers.current.has(item.id)) {
        timers.current.set(
          item.id,
          window.setTimeout(() => dismiss(item.id), item.duration),
        )
      }
    })
    return () => {
      timers.current.forEach((timer) => window.clearTimeout(timer))
      timers.current.clear()
    }
  }, [dismiss])

  const api = useMemo<SnackbarApi>(
    () => ({
      show,
      success: (message, title) => show(message, { variant: 'success', title }),
      error: (message, title) => show(message, { variant: 'error', title }),
      warning: (message, title) => show(message, { variant: 'warning', title }),
      info: (message, title) => show(message, { variant: 'info', title }),
      dismiss,
      clear,
    }),
    [clear, dismiss, show],
  )

  return (
    <SnackbarContext.Provider value={api}>
      {children}
      {createPortal(
        <div
          className="pointer-events-none fixed inset-x-3 top-3 z-[200] flex flex-col items-end gap-2 sm:inset-x-auto sm:right-5 sm:top-5"
          aria-label="Notifications"
        >
          {items.map((item) => (
            <SnackbarCard key={item.id} item={item} onDismiss={dismiss} />
          ))}
        </div>,
        document.body,
      )}
    </SnackbarContext.Provider>
  )
}
