import { useSelector } from 'react-redux'
import { Link } from 'react-router'
import type { RootState } from '../../store/store'

export default function Home() {
  const user = useSelector((state: RootState) => state.user.user)

  return (
    <section className="p-6 sm:p-8">
      <div className="panel max-w-3xl p-6">
        <p className="eyebrow">Workspace</p>
        <h1 className="mt-2 text-2xl font-bold tracking-tight text-slate-950">
          Welcome{user ? `, ${user.username}` : ''}
        </h1>
        <p className="mt-2 text-sm leading-6 text-slate-500">
          Your authentication flow is connected. Media-backed profile pictures can be added after
          registration.
        </p>
        {user && !user.verified && (
          <div className="mt-5 rounded-lg border border-amber-200 bg-amber-50 p-4 text-sm text-amber-800">
            Please verify your email to unlock the full workspace.{' '}
            <Link className="font-semibold text-amber-900 underline" to="/verifyotp">
              Verify now
            </Link>
          </div>
        )}
      </div>
    </section>
  )
}
