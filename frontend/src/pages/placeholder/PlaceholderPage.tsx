import { useLocation } from 'react-router'

const titles: Record<string, string> = {
  '/': 'Dashboard',
  '/inbox/social-media': 'Social Media Inbox',
  '/inbox/sms': 'SMS Inbox',
  '/inbox/email': 'Email Inbox',
  '/broadcast/sms': 'SMS Broadcast',
  '/broadcast/social-media': 'SM Broadcast',
  '/broadcast/email': 'Email Broadcast',
  '/templates': 'Templates',
  '/company/details': 'Company Details',
  '/company/plan': 'Plan Details',
  '/company/users': 'Company Users',
  '/account/profile': 'User Profile',
  '/account/settings': 'Settings',
}

export default function PlaceholderPage() {
  const location = useLocation()
  const title = titles[location.pathname] ?? 'Page'

  return (
    <section className="px-6 py-8">
      <div className="panel max-w-3xl p-6">
        <p className="eyebrow">Workspace</p>
        <h1 className="mt-2 text-2xl font-bold text-slate-950">{title}</h1>
      </div>
    </section>
  )
}
