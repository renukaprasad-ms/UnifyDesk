import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from 'react'
import {
  FiAlertCircle,
  FiCheckCircle,
  FiInfo,
  FiX,
} from 'react-icons/fi'

export type SnackbarVariant = 'success' | 'error' | 'warning' | 'info'

export type SnackbarOptions = {
  variant?: SnackbarVariant
  title?: string
  duration?: number
}

type SnackbarItem = {
  id: number
  message: string
  variant: SnackbarVariant
  title?: string
  duration: number
}

type SnackbarApi = {
  show: (message: string, options?: SnackbarOptions) => number
  success: (message: string, title?: string) => number
  error: (message: string, title?: string) => number
  warning: (message: string, title?: string) => number
  info: (message: string, title?: string) => number
  dismiss: (id: number) => void
  clear: () => void
}

const SnackbarContext = createContext<SnackbarApi | null>(null)

type ExternalSnackbarRequest = {
  message: string
  options?: SnackbarOptions
}

const externalSnackbarListeners = new Set<
  (request: ExternalSnackbarRequest) => void
>()

export const showGlobalSnackbar = (
  message: string,
  options?: SnackbarOptions,
) => {
  const normalized = message.trim()
  if (!normalized) return
  externalSnackbarListeners.forEach((listener) =>
    listener({ message: normalized, options }),
  )
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
        <p className={`text-xs font-bold ${visual.title}`}>
          {item.title ?? visual.defaultTitle}
        </p>
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
  const nextId = useRef(1)
  const timers = useRef(new Map<number, number>())

  const dismiss = useCallback((id: number) => {
    const timer = timers.current.get(id)
    if (timer !== undefined) {
      window.clearTimeout(timer)
      timers.current.delete(id)
    }
    setItems((current) => current.filter((item) => item.id !== id))
  }, [])

  const show = useCallback(
    (message: string, options?: SnackbarOptions) => {
      const normalized = message.trim()
      if (!normalized) return -1

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

      setItems((current) => [...current.slice(-3), item])

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
    setItems([])
  }, [])

  useEffect(() => {
    const listener = ({ message, options }: ExternalSnackbarRequest) => {
      show(message, options)
    }
    externalSnackbarListeners.add(listener)
    return () => {
      externalSnackbarListeners.delete(listener)
    }
  }, [show])

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
      <div
        className="pointer-events-none fixed inset-x-3 bottom-3 z-[100] flex flex-col items-end gap-2 sm:inset-x-auto sm:bottom-5 sm:right-5"
        aria-label="Notifications"
      >
        {items.map((item) => (
          <SnackbarCard key={item.id} item={item} onDismiss={dismiss} />
        ))}
      </div>
    </SnackbarContext.Provider>
  )
}

export const useSnackbar = () => {
  const context = useContext(SnackbarContext)
  if (!context) {
    throw new Error('useSnackbar must be used inside SnackbarProvider')
  }
  return context
}
