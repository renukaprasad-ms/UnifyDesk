import { FiActivity, FiBriefcase, FiCreditCard, FiShield, FiUsers } from 'react-icons/fi'
import { Link } from 'react-router'

const cards = [
  {
    title: 'Companies',
    description:
      'Open an assigned company, inspect its status, or switch into its normal workspace experience.',
    to: '/platform/companies',
    icon: <FiBriefcase />,
  },
  {
    title: 'Users',
    description:
      'Search platform users and support account-level administration without leaving the unified shell.',
    to: '/platform/users',
    icon: <FiUsers />,
  },
  {
    title: 'Plans & pricing',
    description:
      'Build plan versions, publish subscription prices, configure limits and channel rate cards.',
    to: '/platform/plans',
    icon: <FiCreditCard />,
  },
  {
    title: 'Billing operations',
    description:
      'Assign company subscriptions and maintain negotiated customer-specific pricing overrides.',
    to: '/platform/billing',
    icon: <FiActivity />,
  },
  {
    title: 'Administrators',
    description: 'Manage platform admins, permissions and company assignments.',
    to: '/platform/admins',
    icon: <FiShield />,
  },
]

const PlatformDashboard = () => (
  <section className="p-4 sm:p-6 lg:p-8">
    <div className="mx-auto max-w-7xl">
      <p className="eyebrow">PLATFORM ADMINISTRATION</p>
      <h1 className="mt-2 text-2xl font-bold text-slate-950">Admin workspace</h1>
      <p className="mt-2 max-w-2xl text-sm leading-6 text-slate-500">
        Administration is an additional layer on top of the normal Messaging workspace. Use the
        company switcher in the icon rail to jump into a customer workspace, or stay here for global
        platform operations.
      </p>
      <div className="mt-7 grid gap-4 md:grid-cols-2 xl:grid-cols-3">
        {cards.map((card) => (
          <Link
            key={card.to}
            to={card.to}
            className="panel group p-5 transition hover:-translate-y-0.5 hover:border-indigo-200 hover:shadow-sm"
          >
            <span className="security-icon">{card.icon}</span>
            <h2 className="mt-4 text-sm font-bold text-slate-950 group-hover:text-indigo-600">
              {card.title}
            </h2>
            <p className="mt-2 text-xs leading-5 text-slate-500">{card.description}</p>
          </Link>
        ))}
      </div>
    </div>
  </section>
)
export default PlatformDashboard
