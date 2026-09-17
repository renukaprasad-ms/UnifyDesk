import { useEffect, useState } from 'react'
import { FiKey, FiShield, FiTrash2 } from 'react-icons/fi'
import { useNavigate } from 'react-router'
import { authApi } from '../../features/auth/authApi'
import { userApi } from '../../features/user/userApi'
import type { User } from '../../features/user/types'
import { apiErrorMessage } from '../../lib/api/apiClient'
import { selectAuthUser, sessionCleared } from '../../store/auth/authSlice'
import { useAppDispatch, useAppSelector } from '../../store/hooks'

export default function AccountSettings() {
  const dispatch = useAppDispatch()
  const navigate = useNavigate()
  const authUser = useAppSelector(selectAuthUser)
  const [user, setUser] = useState<User | null>(null)
  const [currentPassword, setCurrentPassword] = useState('')
  const [password, setPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
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

  const resetPassword = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (!userId) return
    if (password !== confirmPassword) {
      setError('New passwords do not match')
      return
    }
    if (currentPassword === password) {
      setError('New password must be different from current password')
      return
    }

    setSaving(true)
    setError('')
    setNotice('')
    try {
      const updated = await userApi.resetPassword(userId, { currentPassword, password })
      setUser(updated)
      setCurrentPassword('')
      setPassword('')
      setConfirmPassword('')
      setNotice('Password changed successfully')
    } catch (resetError) {
      setError(apiErrorMessage(resetError, 'Unable to reset password'))
    } finally {
      setSaving(false)
    }
  }

  const toggleTwoFactor = async () => {
    if (!user || !userId) return
    setSaving(true)
    setError('')
    setNotice('')
    try {
      const updated = await userApi.toggleTwoFactor(userId, { enabled: !user.twoFactorEnabled })
      setUser(updated)
      setNotice('Two-factor authentication updated')
    } catch (toggleError) {
      setError(apiErrorMessage(toggleError, 'Unable to update two-factor authentication'))
    } finally {
      setSaving(false)
    }
  }

  const deleteAccount = async () => {
    if (!userId || !window.confirm('Delete your account permanently?')) return
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
    <section className="px-4 py-6 sm:px-6 lg:px-8">
      <div className="mx-auto max-w-3xl">
        <div className="panel p-6">
          <p className="eyebrow">Account</p>
          <h1 className="mt-2 text-2xl font-bold text-slate-950">Settings</h1>
        </div>

        {(error || notice) && (
          <div
            className={`mt-4 rounded-lg border px-4 py-3 text-sm ${
              error
                ? 'border-red-200 bg-red-50 text-red-700'
                : 'border-emerald-200 bg-emerald-50 text-emerald-700'
            }`}
          >
            {error || notice}
          </div>
        )}

        <div className="mt-5 grid gap-4">
          <form className="panel p-5" onSubmit={(event) => void resetPassword(event)}>
            <div className="flex items-start gap-3">
              <span className="security-icon"><FiKey aria-hidden="true" /></span>
              <div className="min-w-0 flex-1">
                <h2 className="text-base font-bold text-slate-950">Change password</h2>
                <p className="mt-1 text-sm text-slate-500">Confirm your current password before choosing a new one.</p>
                <div className="mt-4 grid gap-3">
                  <input className="form-input" type="password" autoComplete="current-password" value={currentPassword} placeholder="Current password" onChange={(event) => setCurrentPassword(event.target.value)} required />
                  <input className="form-input" type="password" autoComplete="new-password" minLength={8} value={password} placeholder="New password" onChange={(event) => setPassword(event.target.value)} required />
                  <input className="form-input" type="password" autoComplete="new-password" minLength={8} value={confirmPassword} placeholder="Confirm new password" onChange={(event) => setConfirmPassword(event.target.value)} required />
                  <div><button className="primary-button inline-flex" disabled={saving || loading}>{saving ? 'Saving…' : 'Change password'}</button></div>
                </div>
              </div>
            </div>
          </form>

          <div className="panel p-5">
            <div className="flex items-center justify-between gap-4">
              <div className="flex items-start gap-3">
                <span className="security-icon"><FiShield aria-hidden="true" /></span>
                <div>
                  <h2 className="text-base font-bold text-slate-950">Two-factor authentication</h2>
                  <p className="mt-1 text-sm text-slate-500">{user?.twoFactorEnabled ? 'Enabled' : 'Disabled'}</p>
                </div>
              </div>
              <button type="button" className={`switch ${user?.twoFactorEnabled ? 'is-on' : ''}`} onClick={() => void toggleTwoFactor()} disabled={saving || loading} aria-label="Toggle two-factor authentication"><span /></button>
            </div>
          </div>

          <div className="panel border-red-200 p-5">
            <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
              <div className="flex items-start gap-3">
                <span className="security-icon bg-red-50 text-red-600"><FiTrash2 aria-hidden="true" /></span>
                <div>
                  <h2 className="text-base font-bold text-slate-950">Delete account</h2>
                  <p className="mt-1 text-sm text-slate-500">This permanently removes your user account.</p>
                </div>
              </div>
              <button type="button" className="secondary-button inline-flex bg-red-50 text-red-600" onClick={() => void deleteAccount()} disabled={saving || loading}>Delete</button>
            </div>
          </div>
        </div>
      </div>
    </section>
  )
}
