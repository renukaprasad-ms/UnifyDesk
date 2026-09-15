import { Link } from 'react-router'
import { useState } from 'react'
import { useDispatch, useSelector } from 'react-redux'
import { useLocation, useNavigate } from 'react-router'
import { authApi } from '../../features/auth/authApi'
import { apiErrorMessage } from '../../lib/api/apiClient'
import { setAuthUser } from '../../store/user/userSlice'
import type { AppDispatch, RootState } from '../../store/store'

const VerifyOtp = () => {
  const dispatch = useDispatch<AppDispatch>()
  const navigate = useNavigate()
  const location = useLocation()
  const user = useSelector((state: RootState) => state.user.user)
  const routeState = location.state as
    | { email?: string; profilePictureWarning?: string }
    | null
  const email = routeState?.email ?? user?.email
  const [otp, setOtp] = useState('')
  const [error, setError] = useState('')
  const [submitting, setSubmitting] = useState(false)

  const onSubmit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    setError('')
    setSubmitting(true)
    try {
      const verifiedUser = await authApi.verifyEmail(otp)
      dispatch(setAuthUser(verifiedUser))
      navigate('/', { replace: true })
    } catch (submitError) {
      setError(apiErrorMessage(submitError, 'Unable to verify OTP'))
    } finally {
      setSubmitting(false)
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
        <button type="submit" className="primary-button flex w-full" disabled={submitting}>
          {submitting ? 'Verifying...' : 'Verify OTP'}
        </button>
      </form>

      <p className="mt-5 text-center text-sm text-slate-500">
        Need to start over?{' '}
        <Link className="font-semibold text-indigo-500" to="/login">
          Back to login
        </Link>
      </p>
    </div>
  )
}

export default VerifyOtp
