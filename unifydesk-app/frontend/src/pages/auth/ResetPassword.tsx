import { useSnackbarState } from '../../components/feedback/useSnackbarState'
import { useState } from 'react'
import { FiEye, FiEyeOff, FiKey } from 'react-icons/fi'
import { Link, useNavigate } from 'react-router'
import { authApi } from '../../features/auth/authApi'
import { apiErrorMessage } from '../../lib/api/apiClient'

export default function ResetPassword() {
  const navigate = useNavigate()
  const [password, setPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [showConfirmPassword, setShowConfirmPassword] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [, setError] = useSnackbarState('', 'error')

  const submit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    setError('')

    if (password !== confirmPassword) {
      setError('Passwords do not match')
      return
    }

    setSubmitting(true)
    try {
      await authApi.completePasswordReset(password)
      navigate('/login', { replace: true, state: { passwordReset: true } })
    } catch (submitError) {
      setError(apiErrorMessage(submitError, 'Unable to reset password'))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="panel p-6 shadow-sm">
      <div className="flex size-11 items-center justify-center rounded-xl bg-indigo-50 text-indigo-500">
        <FiKey aria-hidden="true" />
      </div>
      <p className="eyebrow mt-5">Password recovery</p>
      <h1 className="mt-2 text-2xl font-bold tracking-tight text-slate-950">
        Create a new password
      </h1>
      <p className="mt-2 text-sm leading-6 text-slate-500">
        Your reset code was verified. Choose a new password for your account.
      </p>

      <form className="mt-6 space-y-4" onSubmit={submit}>
        <label className="block">
          <span className="mb-1.5 block text-xs font-semibold text-slate-700">New password</span>
          <span className="relative block">
            <input
              className="form-input pr-11"
              type={showPassword ? 'text' : 'password'}
              autoComplete="new-password"
              minLength={12}
              maxLength={255}
              placeholder="Enter a new password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              required
            />
            <button
              type="button"
              className="absolute inset-y-0 right-0 flex w-11 items-center justify-center text-slate-400 hover:text-slate-700"
              onClick={() => setShowPassword((current) => !current)}
              aria-label={showPassword ? 'Hide password' : 'Show password'}
            >
              {showPassword ? <FiEyeOff aria-hidden="true" /> : <FiEye aria-hidden="true" />}
            </button>
          </span>
        </label>

        <label className="block">
          <span className="mb-1.5 block text-xs font-semibold text-slate-700">
            Confirm password
          </span>
          <span className="relative block">
            <input
              className="form-input pr-11"
              type={showConfirmPassword ? 'text' : 'password'}
              autoComplete="new-password"
              minLength={12}
              maxLength={255}
              placeholder="Confirm your new password"
              value={confirmPassword}
              onChange={(event) => setConfirmPassword(event.target.value)}
              required
            />
            <button
              type="button"
              className="absolute inset-y-0 right-0 flex w-11 items-center justify-center text-slate-400 hover:text-slate-700"
              onClick={() => setShowConfirmPassword((current) => !current)}
              aria-label={
                showConfirmPassword ? 'Hide confirmation password' : 'Show confirmation password'
              }
            >
              {showConfirmPassword ? <FiEyeOff aria-hidden="true" /> : <FiEye aria-hidden="true" />}
            </button>
          </span>
        </label>



        <button type="submit" className="primary-button flex w-full" disabled={submitting}>
          {submitting ? 'Resetting password...' : 'Reset password'}
        </button>
      </form>

      <p className="mt-5 text-center text-sm text-slate-500">
        Reset session expired?{' '}
        <Link className="font-semibold text-indigo-500" to="/forgot-password">
          Start again
        </Link>
      </p>
    </div>
  )
}
