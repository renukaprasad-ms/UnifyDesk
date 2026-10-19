import { Link, NavLink, Outlet } from 'react-router'
import {
  FiArrowLeft,
  FiBookOpen,
  FiCheckCircle,
  FiPhone,
  FiRefreshCw,
  FiShield,
  FiTruck,
} from 'react-icons/fi'

const LEGAL_TABS = [
  { path: '/terms', label: 'Terms & Conditions', icon: FiBookOpen },
  { path: '/privacy', label: 'Privacy Policy', icon: FiShield },
  { path: '/cancellation-and-refund', label: 'Cancellation & Refund', icon: FiRefreshCw },
  { path: '/shipping-and-exchange', label: 'Shipping & Delivery', icon: FiTruck },
  { path: '/contact', label: 'Contact Us', icon: FiPhone },
]

export default function LegalLayout() {
  return (
    <div className="min-h-screen bg-slate-950 text-slate-100 flex flex-col selection:bg-indigo-500 selection:text-white">
      {/* Top Header */}
      <header className="sticky top-0 z-40 bg-slate-900/80 backdrop-blur-md border-b border-slate-800">
        <div className="max-w-6xl mx-auto px-4 sm:px-6 h-16 flex items-center justify-between">
          <div className="flex items-center gap-6">
            <Link to="/login" className="flex items-center gap-3 group">
              <img src="/favicon.svg" alt="UnifyDesk" className="h-8 w-8" />
              <span className="text-xl font-bold tracking-tight text-white group-hover:text-indigo-400 transition-colors">
                UnifyDesk
              </span>
            </Link>
            <span className="hidden sm:inline-block h-4 w-px bg-slate-700" />
            <span className="hidden sm:inline-flex items-center gap-1.5 text-xs text-slate-400 font-medium bg-slate-800/60 px-2.5 py-1 rounded-full border border-slate-700/60">
              <FiShield className="text-indigo-400" /> Legal &amp; Policies
            </span>
          </div>

          <div className="flex items-center gap-3">
            <Link
              to="/login"
              className="inline-flex items-center gap-1.5 text-xs font-semibold text-slate-300 hover:text-white bg-slate-800/80 hover:bg-slate-800 px-3.5 py-2 rounded-xl border border-slate-700/80 transition-all"
            >
              <FiArrowLeft />
              <span>Back to App</span>
            </Link>
          </div>
        </div>
      </header>

      {/* Hero Header */}
      <div className="relative border-b border-slate-800/80 bg-gradient-to-b from-slate-900 via-slate-900/60 to-slate-950 pt-10 pb-8 px-4 sm:px-6 overflow-hidden">
        <div className="absolute top-0 left-1/2 -translate-x-1/2 w-96 h-48 bg-indigo-500/10 blur-3xl pointer-events-none rounded-full" />
        <div className="max-w-4xl mx-auto text-center relative z-10">
          <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-semibold bg-indigo-500/15 text-indigo-400 border border-indigo-500/25 mb-3">
            <FiCheckCircle className="text-xs" /> Verified Merchant Policies
          </span>
          <h1 className="text-3xl sm:text-4xl font-black text-white tracking-tight">
            Trust, Security &amp; Compliance
          </h1>
          <p className="mt-3 text-sm sm:text-base text-slate-400 max-w-2xl mx-auto leading-relaxed">
            UnifyDesk is committed to complete transparency, robust data protection, fair refunds, and transparent merchant service standards.
          </p>

          {/* Navigation Pills */}
          <div className="mt-8 flex flex-wrap items-center justify-center gap-2 max-w-3xl mx-auto">
            {LEGAL_TABS.map((tab) => {
              const Icon = tab.icon
              return (
                <NavLink
                  key={tab.path}
                  to={tab.path}
                  className={({ isActive }) =>
                    `inline-flex items-center gap-2 px-4 py-2 rounded-xl text-xs sm:text-sm font-semibold transition-all ${
                      isActive
                        ? 'bg-indigo-600 text-white shadow-lg shadow-indigo-600/30'
                        : 'bg-slate-900/80 text-slate-400 hover:text-slate-100 hover:bg-slate-800 border border-slate-800'
                    }`
                  }
                >
                  <Icon className="text-sm shrink-0" />
                  <span>{tab.label}</span>
                </NavLink>
              )
            })}
          </div>
        </div>
      </div>

      {/* Main Content Area */}
      <main className="flex-1 max-w-4xl w-full mx-auto px-4 sm:px-6 py-10">
        <div className="bg-slate-900/50 rounded-2xl border border-slate-800/80 p-6 sm:p-10 shadow-xl backdrop-blur-sm">
          <Outlet />
        </div>
      </main>

      {/* Footer */}
      <footer className="border-t border-slate-800/80 bg-slate-900/60 py-8 px-4 sm:px-6 mt-12 text-xs text-slate-400">
        <div className="max-w-6xl mx-auto flex flex-col sm:flex-row items-center justify-between gap-4">
          <div className="flex items-center gap-2">
            <img src="/favicon.svg" alt="UnifyDesk" className="h-5 w-5 opacity-80" />
            <span>&copy; {new Date().getFullYear()} UnifyDesk Technologies. All rights reserved.</span>
          </div>

          <div className="flex flex-wrap items-center justify-center gap-5">
            <Link to="/terms" className="hover:text-slate-200 transition-colors">
              Terms &amp; Conditions
            </Link>
            <Link to="/privacy" className="hover:text-slate-200 transition-colors">
              Privacy Policy
            </Link>
            <Link to="/cancellation-and-refund" className="hover:text-slate-200 transition-colors">
              Cancellation &amp; Refund
            </Link>
            <Link to="/shipping-and-exchange" className="hover:text-slate-200 transition-colors">
              Shipping &amp; Delivery
            </Link>
            <Link to="/contact" className="hover:text-slate-200 transition-colors">
              Contact Us
            </Link>
          </div>
        </div>
      </footer>
    </div>
  )
}
