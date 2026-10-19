import { Link, useLocation, useNavigate } from 'react-router'
import { useState } from 'react'
import { FiUser } from 'react-icons/fi'
import { authApi } from '../../features/auth/authApi'
import { resolvePostAuthRedirect } from '../../features/company/invitationContinuation'
import { apiErrorMessage } from '../../lib/api/apiClient'
import { setAuthUser } from '../../store/auth/authSlice'
import { useAppDispatch } from '../../store/hooks'
import OtpBoxInput from '../../components/ui/OtpBoxInput'
import PasswordInput from '../../components/ui/PasswordInput'

const Login = () => {
  const dispatch = useAppDispatch()
  const navigate = useNavigate()
  const location = useLocation()
  const [identifier, setIdentifier] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [preAuthToken, setPreAuthToken] = useState<string | null>(null)
  const [otp, setOtp] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const routeState = location.state as {
    passwordReset?: boolean
    passwordChanged?: boolean
    securityUpdated?: boolean
    emailChanged?: boolean
    redirectTo?: string
  } | null
  const passwordReset = Boolean(routeState?.passwordReset)
  const passwordChanged = Boolean(routeState?.passwordChanged)
  const securityUpdated = Boolean(routeState?.securityUpdated)
  const emailChanged = Boolean(routeState?.emailChanged)
  const redirectTo = resolvePostAuthRedirect(routeState?.redirectTo)

  const onSubmit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    setError('')
    setSubmitting(true)
    try {
      const result = await authApi.login({ identifier, password })
      if (result.status === 'TWO_FACTOR_REQUIRED') {
        setPreAuthToken(result.preAuthToken)
        setPassword('')
        return
      }
      dispatch(setAuthUser(result.user))
      if (result.user.verified) {
        navigate(redirectTo, { replace: true })
      } else {
        navigate('/verifyotp', {
          replace: true,
          state: { email: result.user.email, redirectTo },
        })
      }
    } catch (submitError) {
      setError(apiErrorMessage(submitError, 'Unable to login'))
    } finally {
      setSubmitting(false)
    }
  }

  const onVerifyTwoFactor = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (!preAuthToken || otp.length !== 6) return
    setError('')
    setSubmitting(true)
    try {
      const user = await authApi.verifyTwoFactor(preAuthToken, otp)
      dispatch(setAuthUser(user))
      if (user.verified) {
        navigate(redirectTo, { replace: true })
      } else {
        navigate('/verifyotp', {
          replace: true,
          state: { email: user.email, redirectTo },
        })
      }
    } catch (submitError) {
      setError(apiErrorMessage(submitError, 'Unable to verify code'))
    } finally {
      setSubmitting(false)
    }
  }

  if (preAuthToken) {
    return (
      <div className="panel p-6 shadow-sm">
        <p className="eyebrow">Two-factor authentication</p>
        <h1 className="mt-2 text-2xl font-bold tracking-tight text-slate-950">Verify your login</h1>
        <p className="mt-2 text-sm leading-6 text-slate-500">
          Enter the 6-digit verification code sent to your email.
        </p>
        <form className="mt-6 space-y-4" onSubmit={onVerifyTwoFactor}>
          <div className="py-2">
            <OtpBoxInput
              length={6}
              value={otp}
              onChange={(val) => {
                setOtp(val)
                if (error) setError('')
              }}
              autoFocus
              disabled={submitting}
              error={Boolean(error)}
            />
          </div>
          {error && (
            <p className="rounded-lg bg-rose-50 p-3 text-center text-sm text-rose-600">{error}</p>
          )}
          <button
            type="submit"
            className="primary-button flex w-full justify-center"
            disabled={submitting || otp.length !== 6}
          >
            {submitting ? 'Verifying...' : 'Verify and login'}
          </button>
          <button
            type="button"
            className="secondary-button flex w-full justify-center"
            onClick={() => {
              setPreAuthToken(null)
              setOtp('')
              setError('')
            }}
            disabled={submitting}
          >
            Back to login
          </button>
        </form>
      </div>
    )
  }

  return (
    <div className="panel p-6 shadow-sm">
      <p className="eyebrow">Welcome back</p>
      <h1 className="mt-2 text-2xl font-bold tracking-tight text-slate-950">Login</h1>
      <p className="mt-2 text-sm leading-6 text-slate-500">
        Sign in to continue to your messaging workspace.
      </p>

      {(passwordReset || passwordChanged || securityUpdated || emailChanged) && (
        <p className="mt-5 rounded-lg border border-emerald-200 bg-emerald-50 p-3 text-sm font-medium text-emerald-700">
          {passwordReset
            ? 'Password reset successfully. Sign in with your new password.'
            : passwordChanged
              ? 'Password changed successfully. Sign in again with your new password.'
              : emailChanged
                ? 'Email changed successfully. Sign in again with your new email address.'
                : 'Security settings updated. Sign in again to continue.'}
        </p>
      )}

      <form className="mt-6 space-y-4" onSubmit={onSubmit}>
        <label className="block">
          <span className="mb-1.5 flex items-center gap-1.5 text-xs font-semibold text-slate-700">
            <FiUser className="size-3.5 text-slate-400" />
            Email or username
          </span>
          <div className="relative">
            <input
              className="form-input"
              type="text"
              placeholder="you@example.com or username"
              value={identifier}
              onChange={(event) => setIdentifier(event.target.value)}
              autoComplete="username"
              required
            />
          </div>
        </label>
        <label className="block">
          <span className="mb-1.5 flex items-center justify-between gap-3 text-xs font-semibold text-slate-700">
            <span className="flex items-center gap-1.5">
              <span>Password</span>
            </span>
            <Link
              className="font-semibold text-indigo-500 hover:text-indigo-600"
              to="/forgot-password"
            >
              Forgot password?
            </Link>
          </span>
          <PasswordInput
            placeholder="Enter password"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            autoComplete="current-password"
            required
            showLeftIcon
          />
        </label>
        {error && <p className="rounded-lg bg-rose-50 p-3 text-sm text-rose-600">{error}</p>}
        <button
          type="submit"
          className="primary-button flex w-full justify-center"
          disabled={submitting}
        >
          {submitting ? 'Logging in...' : 'Login'}
        </button>
      </form>
      <p className="mt-5 text-center text-sm text-slate-500">
        New here?{' '}
        <Link
          className="font-semibold text-indigo-500"
          to="/register"
          state={redirectTo ? { redirectTo } : undefined}
        >
          Create account
        </Link>
      </p>
    </div>
  )
}

export default Login
