import { useEffect, useState } from 'react'
import { FiWifiOff } from 'react-icons/fi'
import { showGlobalSnackbar } from '../feedback/SnackbarProvider'

export default function ConnectivityBanner() {
  const [online, setOnline] = useState(() => navigator.onLine)

  useEffect(() => {
    const onOnline = () => {
      setOnline(true)
      showGlobalSnackbar('Connection restored.', { variant: 'success' })
    }
    const onOffline = () => {
      setOnline(false)
      showGlobalSnackbar('You are offline. Network actions may be unavailable until your connection returns.', { variant: 'warning' })
    }
    window.addEventListener('online', onOnline)
    window.addEventListener('offline', onOffline)
    return () => {
      window.removeEventListener('online', onOnline)
      window.removeEventListener('offline', onOffline)
    }
  }, [])

  if (online) return null

  return (
    <div
      className="fixed inset-x-4 bottom-4 z-[140] mx-auto flex max-w-xl items-center gap-3 rounded-2xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-900 shadow-xl shadow-slate-900/10"
      role="status"
      aria-live="polite"
    >
      <span className="flex size-9 shrink-0 items-center justify-center rounded-xl bg-white text-amber-700">
        <FiWifiOff aria-hidden="true" />
      </span>
      <div className="min-w-0">
        <p className="font-bold">You’re offline</p>
        <p className="mt-0.5 text-xs leading-5 text-amber-800">
          Existing content stays visible, but network actions may be unavailable until your connection returns.
        </p>
      </div>
    </div>
  )
}
