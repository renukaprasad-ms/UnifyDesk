import { createContext, useContext } from 'react'

export type SnackbarVariant = 'success' | 'error' | 'warning' | 'info'

export type SnackbarOptions = {
  variant?: SnackbarVariant
  title?: string
  duration?: number
}

export type SnackbarApi = {
  show: (message: string, options?: SnackbarOptions) => number
  success: (message: string, title?: string) => number
  error: (message: string, title?: string) => number
  warning: (message: string, title?: string) => number
  info: (message: string, title?: string) => number
  dismiss: (id: number) => void
  clear: () => void
}

export const SnackbarContext = createContext<SnackbarApi | null>(null)

export type ExternalSnackbarRequest = {
  message: string
  options?: SnackbarOptions
}

export const externalSnackbarListeners = new Set<(request: ExternalSnackbarRequest) => void>()
export const pendingExternalSnackbars: ExternalSnackbarRequest[] = []

export const showGlobalSnackbar = (message: string, options?: SnackbarOptions) => {
  const normalized = message.trim()
  if (!normalized) return

  const request = { message: normalized, options }
  if (externalSnackbarListeners.size === 0) {
    if (pendingExternalSnackbars.some((item) => item.message === normalized)) return
    pendingExternalSnackbars.push(request)
    if (pendingExternalSnackbars.length > 3) {
      pendingExternalSnackbars.splice(0, pendingExternalSnackbars.length - 3)
    }
    return
  }

  externalSnackbarListeners.forEach((listener) => listener(request))
}

export const useSnackbar = () => {
  const context = useContext(SnackbarContext)
  if (!context) {
    throw new Error('useSnackbar must be used inside SnackbarProvider')
  }
  return context
}
