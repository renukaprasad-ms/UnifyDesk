import { Link, useLocation, useNavigate } from 'react-router'
import { useEffect, useState } from 'react'
import { authApi } from '../../features/auth/authApi'
import { resolvePostAuthRedirect } from '../../features/company/invitationContinuation'
import { apiErrorMessage } from '../../lib/api/apiClient'
import { selectAuthUser, setAuthUser } from '../../store/auth/authSlice'
import { useAppDispatch, useAppSelector } from '../../store/hooks'

const RESEND_COOLDOWN_SECONDS = 60

const VerifyOtp = () => {
  const dispatch = useAppDispatch()
  const navigate = useNavigate()
  const location = useLocation()
  const user = useAppSelector(selectAuthUser)
  const routeState = location.state as {
    email?: string
    profilePictureWarning?: string
    redirectTo?: string
  } | null
  const email = routeState?.email ?? user?.email
  const redirectTo = resolvePostAuthRedirect(routeState?.redirectTo)
  const [otp, setOtp] = useState('')
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [resending, setResending] = useState(false)
  const [resendCooldown, setResendCooldown] = useState(RESEND_COOLDOWN_SECONDS)

  useEffect(() => {
    if (resendCooldown <= 0) return
    const timer = window.setTimeout(
      () => setResendCooldown((current) => Math.max(0, current - 1)),
      1000,
    )
    return () => window.clearTimeout(timer)
  }, [resendCooldown])

  const onSubmit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    setError('')
    setNotice('')
    setSubmitting(true)
    try {
      const verifiedUser = await authApi.verifyEmail(otp)
      dispatch(setAuthUser(verifiedUser))
      navigate(redirectTo, { replace: true })
    } catch (submitError) {
      setError(apiErrorMessage(submitError, 'Unable to verify OTP'))
    } finally {
      setSubmitting(false)
    }
  }

  const resend = async () => {
    if (resending || resendCooldown > 0) return
    setError('')
    setNotice('')
    setResending(true)
    try {
      await authApi.resendVerificationEmail()
      setOtp('')
      setResendCooldown(RESEND_COOLDOWN_SECONDS)
      setNotice('A new verification code was sent.')
    } catch (resendError) {
      setError(apiErrorMessage(resendError, 'Unable to resend verification code'))
    } finally {
      setResending(false)
    }
  }

  return (
    <div className="panel p-6 shadow-sm">
      <p className="eyebrow">Verification</p>
      <h1 className="mt-2 text-2xl font-bold tracking-tight text-slate-950">Verify OTP</h1>
      <p className="mt-2 text-sm leading-6 text-slate-500">
        Enter the one-time password sent to {email ?? 'your email'}.
      </p>
      {routeState?.profilePictureWarning && (
        <p className="mt-4 rounded-lg bg-amber-50 p-3 text-sm text-amber-700">
          {routeState.profilePictureWarning}. You can update it later.
        </p>
      )}
      <form className="mt-6 space-y-4" onSubmit={onSubmit}>
        <label className="block">
          <span className="mb-1.5 block text-xs font-semibold text-slate-700">OTP</span>
          <input
            className="form-input text-center tracking-[0.35em]"
            inputMode="numeric"
            maxLength={6}
            pattern="\d{6}"
            value={otp}
            onChange={(event) => setOtp(event.target.value.replace(/\D/g, '').slice(0, 6))}
            autoComplete="one-time-code"
            required
          />
        </label>
        {error && <p className="rounded-lg bg-rose-50 p-3 text-sm text-rose-600">{error}</p>}
        {notice && (
          <p className="rounded-lg bg-emerald-50 p-3 text-sm text-emerald-700">{notice}</p>
        )}
        <button
          type="submit"
          className="primary-button flex w-full"
          disabled={submitting || otp.length !== 6}
        >
          {submitting ? 'Verifying...' : 'Verify OTP'}
        </button>
        <button
          type="button"
          className="secondary-button flex w-full"
          disabled={resending || resendCooldown > 0}
          onClick={() => void resend()}
        >
          {resending
            ? 'Sending...'
            : resendCooldown > 0
              ? `Resend code in ${resendCooldown}s`
              : 'Resend verification code'}
        </button>
      </form>
      <p className="mt-5 text-center text-sm text-slate-500">
        Need to start over?{' '}
        <Link
          className="font-semibold text-indigo-500"
          to="/login"
          state={redirectTo !== '/' ? { redirectTo } : undefined}
        >
          Back to login
        </Link>
      </p>
    </div>
  )
}

export default VerifyOtp
