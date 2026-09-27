import { FiBell } from 'react-icons/fi'
import NotificationPreferencesPanel from '../../features/notifications/NotificationPreferencesPanel'

export default function AccountNotificationSettings() {
  return (
    <section className="px-4 py-8 sm:px-6 lg:px-8">
      <div className="mx-auto max-w-4xl">
        <header>
          <p className="eyebrow">Account settings</p>
          <div className="mt-2 flex items-center gap-3">
            <FiBell className="text-indigo-500" aria-hidden="true" />
            <h1 className="text-3xl font-bold tracking-tight text-slate-950">Notifications</h1>
          </div>
          <p className="mt-2 max-w-2xl text-sm leading-6 text-slate-500">
            Manage account-wide email, browser notification, quiet-hour, and delivery preferences.
          </p>
        </header>
        <div className="mt-7">
          <NotificationPreferencesPanel companyId={null} />
        </div>
      </div>
    </section>
  )
}
