import { useSnackbarState } from '../../components/feedback/useSnackbarState'
import { useState } from 'react'
import { FiArrowLeft, FiMail } from 'react-icons/fi'
import { Link, useNavigate } from 'react-router'
import { authApi } from '../../features/auth/authApi'
import { apiErrorMessage } from '../../lib/api/apiClient'
import OtpBoxInput from '../../components/ui/OtpBoxInput'

export default function ForgotPassword() {
  const navigate = useNavigate()
  const [identifier, setIdentifier] = useState('')
  const [challengeId, setChallengeId] = useState<string | null>(null)
  const [otp, setOtp] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useSnackbarState('', 'error')

  const requestReset = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    setError('')
    setSubmitting(true)
    try {
      const challenge = await authApi.requestPasswordReset(identifier)
      setChallengeId(challenge.challengeId)
      setOtp('')
    } catch (requestError) {
      setError(apiErrorMessage(requestError, 'Unable to start password reset'))
    } finally {
      setSubmitting(false)
    }
  }

  const verifyOtp = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (!challengeId || otp.length !== 6) return
    setError('')
    setSubmitting(true)
    try {
      await authApi.verifyPasswordResetOtp(challengeId, otp)
      navigate('/reset-password', { replace: true })
    } catch (verifyError) {
      setError(apiErrorMessage(verifyError, 'Unable to verify reset code'))
    } finally {
      setSubmitting(false)
    }
  }

  if (challengeId) {
    return (
      <div className="panel p-6 shadow-sm">
        <p className="eyebrow">Password recovery</p>
        <h1 className="mt-2 text-2xl font-bold tracking-tight text-slate-950">Check your email</h1>
        <p className="mt-2 text-sm leading-6 text-slate-500">
          If an account matches <span className="font-semibold text-slate-700">{identifier}</span>,
          we sent a 6-digit reset code to its registered email address.
        </p>

        <form className="mt-6 space-y-4" onSubmit={verifyOtp}>
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



          <button
            type="submit"
            className="primary-button flex w-full"
            disabled={submitting || otp.length !== 6}
          >
            {submitting ? 'Verifying...' : 'Verify code'}
          </button>

          <button
            type="button"
            className="secondary-button flex w-full"
            disabled={submitting}
            onClick={() => {
              setChallengeId(null)
              setOtp('')
              setError('')
            }}
          >
            Use another email or username
          </button>
        </form>
      </div>
    )
  }

  return (
    <div className="panel p-6 shadow-sm">
      <Link
        to="/login"
        className="inline-flex items-center gap-2 text-sm font-semibold text-slate-500 transition hover:text-slate-900"
      >
        <FiArrowLeft aria-hidden="true" />
        Back to login
      </Link>

      <div className="mt-5 flex size-11 items-center justify-center rounded-xl bg-indigo-50 text-indigo-500">
        <FiMail aria-hidden="true" />
      </div>
      <p className="eyebrow mt-5">Password recovery</p>
      <h1 className="mt-2 text-2xl font-bold tracking-tight text-slate-950">
        Forgot your password?
      </h1>
      <p className="mt-2 text-sm leading-6 text-slate-500">
        Enter your email address or username. We’ll send a one-time code to the email registered on
        the account.
      </p>

      <form className="mt-6 space-y-4" onSubmit={requestReset}>
        <label className="block">
          <span className="mb-1.5 block text-xs font-semibold text-slate-700">
            Email or username
          </span>
          <input
            className="form-input"
            type="text"
            autoComplete="username"
            placeholder="you@example.com or username"
            value={identifier}
            onChange={(event) => setIdentifier(event.target.value)}
            required
          />
        </label>



        <button type="submit" className="primary-button flex w-full" disabled={submitting}>
          {submitting ? 'Sending code...' : 'Send reset code'}
        </button>
      </form>

      <p className="mt-5 text-center text-sm text-slate-500">
        Remembered it?{' '}
        <Link className="font-semibold text-indigo-500" to="/login">
          Sign in
        </Link>
      </p>
    </div>
  )
}
