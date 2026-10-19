import { useSnackbarState } from '../../components/feedback/useSnackbarState'
import { Link, useLocation, useNavigate } from 'react-router'
import { useEffect, useState } from 'react'
import { FiMail } from 'react-icons/fi'
import { authApi } from '../../features/auth/authApi'
import { resolvePostAuthRedirect } from '../../features/company/invitationContinuation'
import { apiErrorMessage } from '../../lib/api/apiClient'
import { selectAuthUser, setAuthUser } from '../../store/auth/authSlice'
import { useAppDispatch, useAppSelector } from '../../store/hooks'
import OtpBoxInput from '../../components/ui/OtpBoxInput'

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
  const profilePictureWarning = routeState?.profilePictureWarning
  useEffect(() => {
    if (profilePictureWarning) showGlobalSnackbar(`${profilePictureWarning}. You can update it later.`, { variant: 'warning' })
  }, [profilePictureWarning])
  const [otp, setOtp] = useState('')
  const [error, setError] = useSnackbarState('', 'error')
  const [, setNotice] = useSnackbarState('', 'success')
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
      showGlobalSnackbar('Email verified successfully.', { variant: 'success' })
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
      <div className="flex items-center gap-3 rounded-xl bg-indigo-50/70 dark:bg-indigo-950/30 p-3.5 text-xs text-indigo-700 dark:text-indigo-300">
        <FiMail className="size-5 shrink-0" aria-hidden="true" />
        <span>
          A 6-digit verification code has been sent to your inbox. Check your spam folder if it
          doesn't appear shortly.
        </span>
      </div>

      <form className="mt-6 space-y-5" onSubmit={onSubmit}>
        <div>
          <span className="mb-2 block text-center text-xs font-semibold text-slate-700 dark:text-slate-300">
            Enter 6-digit verification code
          </span>
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



        <button
          type="submit"
          className="primary-button flex w-full justify-center"
          disabled={submitting || otp.length !== 6}
        >
          {submitting ? 'Verifying...' : 'Verify OTP'}
        </button>
        <button
          type="button"
          className="secondary-button flex w-full justify-center"
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
import { showGlobalSnackbar } from '../../components/feedback/SnackbarProvider'
