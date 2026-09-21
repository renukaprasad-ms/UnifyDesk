import { FiRefreshCw, FiSend } from 'react-icons/fi'
import { useLocation } from 'react-router'

const channelLabel = (pathname: string) => {
  if (pathname.endsWith('/email')) return 'Email'
  if (pathname.endsWith('/social-media')) return 'WhatsApp'
  return 'SMS'
}

export default function BroadcastWorkspace() {
  const location = useLocation()
  const label = channelLabel(location.pathname)

  return (
    <section className="channel-workspace-page">
      <div className="channel-workspace-toolbar">
        <div className="min-w-0 lg:hidden">
          <p className="eyebrow">BROADCAST</p>
          <p className="mt-1 text-xs font-semibold text-slate-700">{label}</p>
        </div>

        <div className="ml-auto flex items-center gap-2">
          <button type="button" className="secondary-button gap-2" disabled>
            <FiRefreshCw aria-hidden="true" />
            Refresh
          </button>
          <button type="button" className="primary-button gap-2" disabled>
            <FiSend aria-hidden="true" />
            New broadcast
          </button>
        </div>
      </div>

      <div className="channel-workspace-body">
        <main className="channel-message-surface">
          <div className="flex min-h-[calc(100dvh-210px)] items-center justify-center px-6 text-center">
            <div className="max-w-sm">
              <FiSend className="mx-auto text-4xl text-slate-300" aria-hidden="true" />
              <p className="mt-4 text-sm font-semibold text-slate-800">
                Select a broadcast
              </p>
              <p className="mt-2 text-xs leading-5 text-slate-500">
                Your {label.toLowerCase()} broadcast details and delivery activity will appear here.
                The broadcast APIs are not wired yet, so the layout is ready without showing fake
                campaign data.
              </p>
            </div>
          </div>
        </main>
      </div>
    </section>
  )
}
