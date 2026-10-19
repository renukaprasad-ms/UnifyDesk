import { useSnackbarState } from '../../components/feedback/useSnackbarState'
import { useState, type FormEvent } from 'react'
import { FiArrowLeft, FiEye, FiEyeOff, FiKey } from 'react-icons/fi'
import { useNavigate } from 'react-router'
import { userApi } from '../../features/user/userApi'
import { apiErrorMessage } from '../../lib/api/apiClient'
import { selectAuthUser, sessionCleared } from '../../store/auth/authSlice'
import { useAppDispatch, useAppSelector } from '../../store/hooks'

type PasswordFieldProps = {
  label: string
  value: string
  onChange: (value: string) => void
  autoComplete: string
  placeholder: string
  minLength?: number
}

function PasswordField({
  label,
  value,
  onChange,
  autoComplete,
  placeholder,
  minLength,
}: PasswordFieldProps) {
  const [visible, setVisible] = useState(false)

  return (
    <label className="block">
      <span className="mb-2 block text-xs font-semibold text-slate-700">{label}</span>
      <span className="relative block">
        <input
          className="form-input pr-11"
          type={visible ? 'text' : 'password'}
          value={value}
          onChange={(event) => onChange(event.target.value)}
          autoComplete={autoComplete}
          placeholder={placeholder}
          minLength={minLength}
          required
        />
        <button
          type="button"
          className="absolute inset-y-0 right-0 flex w-11 items-center justify-center text-slate-400 hover:text-slate-700"
          onClick={() => setVisible((current) => !current)}
          aria-label={visible ? `Hide ${label.toLowerCase()}` : `Show ${label.toLowerCase()}`}
        >
          {visible ? <FiEyeOff aria-hidden="true" /> : <FiEye aria-hidden="true" />}
        </button>
      </span>
    </label>
  )
}

export default function ChangePassword() {
  const navigate = useNavigate()
  const dispatch = useAppDispatch()
  const user = useAppSelector(selectAuthUser)
  const [currentPassword, setCurrentPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [saving, setSaving] = useState(false)
  const [, setError] = useSnackbarState('', 'error')

  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (!user) return

    setError('')
    if (newPassword !== confirmPassword) {
      setError('New passwords do not match')
      return
    }

    if (currentPassword === newPassword) {
      setError('New password must be different from your current password')
      return
    }

    setSaving(true)
    try {
      await userApi.resetPassword(user.userId, {
        currentPassword,
        password: newPassword,
      })
      dispatch(sessionCleared())
      navigate('/login', { replace: true, state: { passwordChanged: true } })
    } catch (submitError) {
      setError(apiErrorMessage(submitError, 'Unable to change password'))
    } finally {
      setSaving(false)
    }
  }

  return (
    <section className="px-4 py-8 sm:px-6 lg:px-8">
      <div className="mx-auto max-w-2xl">
        <button
          type="button"
          className="inline-flex items-center gap-2 text-sm font-semibold text-slate-500 transition hover:text-slate-950"
          onClick={() => navigate('/account/settings')}
        >
          <FiArrowLeft aria-hidden="true" />
          Back to settings
        </button>

        <header className="mt-6">
          <p className="eyebrow">Account security</p>
          <h1 className="mt-2 text-3xl font-bold tracking-tight text-slate-950">Change password</h1>
          <p className="mt-2 text-sm leading-6 text-slate-500">
            Enter your current password, then choose a new password for your account.
          </p>
        </header>



        <form className="panel mt-6 p-6 sm:p-7" onSubmit={(event) => void submit(event)}>
          <div className="flex items-start gap-4 border-b border-slate-100 pb-5">
            <span className="security-icon shrink-0">
              <FiKey aria-hidden="true" />
            </span>
            <div>
              <h2 className="text-base font-bold text-slate-950">Set a new password</h2>
              <p className="mt-1 text-sm leading-6 text-slate-500">
                Use at least 12 characters and avoid reusing a password from another account.
              </p>
            </div>
          </div>

          <div className="mt-6 space-y-5">
            <PasswordField
              label="Current password"
              value={currentPassword}
              onChange={setCurrentPassword}
              autoComplete="current-password"
              placeholder="Enter your current password"
            />

            <PasswordField
              label="New password"
              value={newPassword}
              onChange={setNewPassword}
              autoComplete="new-password"
              placeholder="Enter a new password"
              minLength={12}
            />

            <PasswordField
              label="Confirm new password"
              value={confirmPassword}
              onChange={setConfirmPassword}
              autoComplete="new-password"
              placeholder="Re-enter the new password"
              minLength={12}
            />
          </div>

          <div className="mt-7 flex flex-col-reverse gap-3 border-t border-slate-100 pt-5 sm:flex-row sm:justify-end">
            <button
              type="button"
              className="secondary-button inline-flex justify-center"
              onClick={() => navigate('/account/settings')}
              disabled={saving}
            >
              Cancel
            </button>
            <button
              type="submit"
              className="primary-button inline-flex justify-center"
              disabled={saving}
            >
              {saving ? 'Changing password…' : 'Change password'}
            </button>
          </div>
        </form>
      </div>
    </section>
  )
}
