import { Outlet } from 'react-router'
import { FiBarChart2, FiLayers, FiMessageCircle, FiShield, FiZap } from 'react-icons/fi'

const capabilities = [
  {
    icon: FiMessageCircle,
    title: 'Unified Inbox',
    description: 'WhatsApp, Email and SMS conversations in one place.',
  },
  {
    icon: FiZap,
    title: 'Smart Broadcasts',
    description: 'Reach thousands of customers with targeted campaigns.',
  },
  {
    icon: FiBarChart2,
    title: 'Advertising Insights',
    description: 'Meta, Google, Hotstar and more ad channels.',
  },
  {
    icon: FiLayers,
    title: 'Automations',
    description: 'Create workflows that reduce repetitive work.',
  },
]

const AuthLayout = () => {
  return (
    <main className="h-dvh overflow-hidden bg-white lg:grid lg:grid-cols-[48%_52%]">
      <section className="relative hidden h-dvh overflow-hidden border-r border-slate-100 bg-[linear-gradient(105deg,#F0F1FF_0%,#FFFFFF_100%)] px-10 py-8 lg:flex lg:flex-col xl:px-12">
        <div className="absolute -left-24 top-52 h-72 w-72 rounded-full bg-indigo-200/20 blur-3xl" />
        <div className="absolute -right-24 bottom-20 h-80 w-80 rounded-full bg-violet-200/30 blur-3xl" />

        <div className="relative z-10 flex shrink-0 items-center gap-3">
          <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-indigo-500 text-lg font-bold text-white shadow-lg shadow-indigo-200">
            •••
          </div>
          <span className="text-2xl font-bold tracking-tight text-slate-900">Messaging</span>
        </div>

        <div className="relative z-0 flex min-h-0 flex-1 flex-col justify-top py-8">
          <h1 className="text-3xl font-bold leading-tight tracking-tight text-slate-950 xl:text-4xl 2xl:text-4xl">
            Connect every conversation.
            <br />
            Grow from one place.
          </h1>
          <p className="mt-3 max-w-lg text-sm leading-6 text-slate-500 xl:text-base">
            The all-in-one platform for messaging, broadcasts, automation and advertising.
          </p>

          <div className="mt-4 flex flex-wrap gap-2 text-xs font-semibold xl:mt-5">
            <span className="rounded-full bg-emerald-50 px-3 py-1.5 text-emerald-600">
              WhatsApp
            </span>
            <span className="rounded-full bg-indigo-50 px-3 py-1.5 text-indigo-600">Email</span>
            <span className="rounded-full bg-amber-50 px-3 py-1.5 text-amber-600">SMS</span>
            <span className="rounded-full bg-blue-50 px-3 py-1.5 text-blue-600">Ads</span>
          </div>

          <div className="mt-5 grid gap-3 xl:mt-6 xl:gap-4">
            {capabilities.map(({ icon: Icon, title, description }) => (
              <div key={title} className="flex items-center gap-3">
                <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-xl bg-white text-indigo-500 shadow-sm ring-1 ring-slate-100 xl:h-10 xl:w-10">
                  <Icon size={17} />
                </div>
                <div>
                  <h2 className="text-xs font-semibold text-slate-900 xl:text-sm">{title}</h2>
                  <p className="mt-0.5 text-[11px] leading-4 text-slate-500 xl:text-xs">
                    {description}
                  </p>
                </div>
              </div>
            ))}
          </div>

          <div className="relative z-10 ml-auto mt-5 w-60 rounded-2xl border border-slate-100 bg-white px-4 py-6 shadow-xl shadow-slate-200/50 xl:absolute xl:left-[45%] xl:top-[70%] xl:mt-0">
            <p className="text-xs font-semibold text-slate-900">Dashboard</p>
            <div className="mt-3 grid grid-cols-2 gap-4">
              <div>
                <strong className="block text-lg text-slate-950">24.8K</strong>
                <span className="text-[10px] text-slate-400">Conversations</span>
              </div>
              <div>
                <strong className="block text-lg text-slate-950">112K</strong>
                <span className="text-[10px] text-slate-400">Messages</span>
              </div>
            </div>
            <div className="mt-3 h-1.5 overflow-hidden rounded-full bg-indigo-50">
              <div className="h-full w-[70%] rounded-full bg-indigo-500" />
            </div>
            <p className="mt-3 text-[10px] font-medium text-indigo-500">
              Messaging + marketing, together
            </p>
          </div>
        </div>

        <div className="relative z-10 flex shrink-0 items-center gap-3 text-slate-500">
          <FiShield className="text-indigo-500" size={20} />
          <div>
            <p className="text-xs font-semibold text-slate-800">Secure by design</p>
            <p className="mt-0.5 text-[10px]">Modern authentication protects your workspace.</p>
          </div>
        </div>
      </section>

      <section className="flex h-dvh items-center justify-center overflow-y-auto bg-white px-6 py-6 sm:px-10 lg:py-5">
        <div className="w-full max-w-[390px]">
          <div className="mb-7 flex items-center gap-3 lg:hidden">
            <div className="flex h-9 w-9 items-center justify-center rounded-xl bg-indigo-500 text-sm font-bold text-white">
              •••
            </div>
            <span className="text-xl font-bold text-slate-900">Messaging</span>
          </div>
          <Outlet />
        </div>
      </section>
    </main>
  )
}

export default AuthLayout
