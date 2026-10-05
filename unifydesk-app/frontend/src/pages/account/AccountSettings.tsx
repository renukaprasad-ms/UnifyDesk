import { useEffect, useState } from 'react'
import { FiBell, FiChevronRight, FiKey, FiShield, FiTrash2 } from 'react-icons/fi'
import { useNavigate } from 'react-router'
import { authApi } from '../../features/auth/authApi'
import { userApi } from '../../features/user/userApi'
import type { User } from '../../features/user/types'
import { apiErrorMessage } from '../../lib/api/apiClient'
import { selectAuthUser, sessionCleared } from '../../store/auth/authSlice'
import { useAppDispatch, useAppSelector } from '../../store/hooks'
import ConfirmDialog from '../../components/ui/ConfirmDialog'
import { CardSkeleton } from '../../components/ui/Skeleton'

export default function AccountSettings() {
  const dispatch = useAppDispatch()
  const navigate = useNavigate()
  const authUser = useAppSelector(selectAuthUser)
  const [user, setUser] = useState<User | null>(null)
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [deleteConfirmOpen, setDeleteConfirmOpen] = useState(false)
  const userId = authUser?.userId ?? null

  useEffect(() => {
    let active = true
    userApi
      .me()
      .then((response) => {
        if (active) setUser(response)
      })
      .catch((loadError) => {
        if (active) setError(apiErrorMessage(loadError, 'Unable to load settings'))
      })
      .finally(() => {
        if (active) setLoading(false)
      })

    return () => {
      active = false
    }
  }, [])

  const toggleTwoFactor = async () => {
    if (!user || !userId) return
    setSaving(true)
    setError('')
    setNotice('')

    try {
      const updated = await userApi.toggleTwoFactor(userId, {
        enabled: !user.twoFactorEnabled,
      })
      setUser(updated)
      setNotice(
        updated.twoFactorEnabled
          ? 'Two-factor authentication enabled'
          : 'Two-factor authentication disabled',
      )
    } catch (toggleError) {
      setError(apiErrorMessage(toggleError, 'Unable to update two-factor authentication'))
    } finally {
      setSaving(false)
    }
  }

  const deleteAccount = async () => {
    if (!userId) return
    setSaving(true)
    setError('')
    setNotice('')

    try {
      await userApi.delete(userId)
      await authApi.logout().catch(() => undefined)
      dispatch(sessionCleared())
      navigate('/login', { replace: true })
    } catch (deleteError) {
      setError(apiErrorMessage(deleteError, 'Unable to delete account'))
    } finally {
      setSaving(false)
    }
  }

  return (
    <section className="px-4 py-8 sm:px-6 lg:px-8">
      <div className="mx-auto max-w-4xl">
        <header>
          <p className="eyebrow">Account</p>
          <h1 className="mt-2 text-3xl font-bold tracking-tight text-slate-950">Settings</h1>
          <p className="mt-2 max-w-2xl text-sm leading-6 text-slate-500">
            Manage your password, sign-in security, and account lifecycle.
          </p>
        </header>

        {(error || notice) && (
          <div
            className={`mt-6 rounded-xl border px-4 py-3 text-sm font-medium ${
              error
                ? 'border-red-200 bg-red-50 text-red-700'
                : 'border-emerald-200 bg-emerald-50 text-emerald-700'
            }`}
          >
            {error || notice}
          </div>
        )}

        {loading ? (
          <div className="mt-7 space-y-4">
            <CardSkeleton rows={2} />
            <CardSkeleton rows={2} />
            <CardSkeleton rows={2} />
          </div>
        ) : (
        <div className="mt-7 space-y-4">
          <button
            type="button"
            className="panel flex w-full items-center gap-4 p-5 text-left transition hover:border-indigo-200 hover:shadow-sm"
            onClick={() => navigate('/account/settings/password')}
          >
            <span className="security-icon shrink-0">
              <FiKey aria-hidden="true" />
            </span>
            <span className="min-w-0 flex-1">
              <span className="block text-base font-bold text-slate-950">Password</span>
              <span className="mt-1 block text-sm leading-6 text-slate-500">
                Change your password and keep your account credentials secure.
              </span>
            </span>
            <span className="hidden text-sm font-semibold text-indigo-600 sm:block">Change</span>
            <FiChevronRight className="shrink-0 text-slate-400" aria-hidden="true" />
          </button>

          <div className="panel p-5">
            <div className="flex items-center justify-between gap-4">
              <div className="flex min-w-0 items-start gap-4">
                <span className="security-icon shrink-0">
                  <FiShield aria-hidden="true" />
                </span>
                <div className="min-w-0">
                  <div className="flex flex-wrap items-center gap-2">
                    <h2 className="text-base font-bold text-slate-950">
                      Two-factor authentication
                    </h2>
                    {!loading && user && (
                      <span
                        className={`rounded-full px-2.5 py-1 text-xs font-semibold ${
                          user.twoFactorEnabled
                            ? 'bg-emerald-50 text-emerald-700'
                            : 'bg-slate-100 text-slate-600'
                        }`}
                      >
                        {user.twoFactorEnabled ? 'Enabled' : 'Disabled'}
                      </span>
                    )}
                  </div>
                  <p className="mt-1 text-sm leading-6 text-slate-500">
                    Require an additional verification step when signing in.
                  </p>
                </div>
              </div>

              <button
                type="button"
                className={`switch shrink-0 ${user?.twoFactorEnabled ? 'is-on' : ''}`}
                onClick={() => void toggleTwoFactor()}
                disabled={saving || loading || !user}
                aria-label={
                  user?.twoFactorEnabled
                    ? 'Disable two-factor authentication'
                    : 'Enable two-factor authentication'
                }
                aria-pressed={Boolean(user?.twoFactorEnabled)}
              >
                <span />
              </button>
            </div>
          </div>

          <button
            type="button"
            className="panel flex w-full items-center gap-4 p-5 text-left transition hover:border-indigo-200 hover:shadow-sm"
            onClick={() => navigate('/account/settings/notifications')}
          >
            <span className="security-icon shrink-0">
              <FiBell aria-hidden="true" />
            </span>
            <span className="min-w-0 flex-1">
              <span className="block text-base font-bold text-slate-950">Notifications</span>
              <span className="mt-1 block text-sm leading-6 text-slate-500">
                Manage email, browser notifications, quiet hours, and delivery preferences.
              </span>
            </span>
            <span className="hidden text-sm font-semibold text-indigo-600 sm:block">Manage</span>
            <FiChevronRight className="shrink-0 text-slate-400" aria-hidden="true" />
          </button>
        </div>
        )}

        <div className="mt-10">
          <p className="text-xs font-bold uppercase tracking-[0.16em] text-red-500">Danger zone</p>
          <div className="panel mt-3 border-red-200 p-5">
            <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
              <div className="flex items-start gap-4">
                <span className="security-icon shrink-0 bg-red-50 text-red-600">
                  <FiTrash2 aria-hidden="true" />
                </span>
                <div>
                  <h2 className="text-base font-bold text-slate-950">Delete account</h2>
                  <p className="mt-1 text-sm leading-6 text-slate-500">
                    Permanently remove your user account. This action cannot be undone.
                  </p>
                </div>
              </div>
              <button
                type="button"
                className="secondary-button inline-flex shrink-0 border-red-200 bg-red-50 text-red-600 hover:bg-red-100"
                onClick={() => setDeleteConfirmOpen(true)}
                disabled={saving || loading}
              >
                Delete account
              </button>
            </div>
          </div>
        </div>
        <ConfirmDialog
          open={deleteConfirmOpen}
          title="Delete your account permanently?"
          description="Your UnifyDesk user account will be removed and this action cannot be undone."
          confirmLabel="Yes, delete account"
          cancelLabel="No, keep account"
          tone="danger"
          kind="delete"
          working={saving}
          onCancel={() => setDeleteConfirmOpen(false)}
          onConfirm={() => void deleteAccount()}
        />
      </div>
    </section>
  )
}
