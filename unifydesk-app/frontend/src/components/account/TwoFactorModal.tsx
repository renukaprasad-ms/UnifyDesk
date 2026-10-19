import { useSnackbarState } from '../feedback/useSnackbarState'
import React, { useEffect, useState } from 'react'
import { FiCheckCircle, FiLock, FiMail, FiShield, FiX } from 'react-icons/fi'
import { authApi } from '../../features/auth/authApi'
import { apiErrorMessage } from '../../lib/api/apiClient'
import OtpBoxInput from '../ui/OtpBoxInput'
import PasswordInput from '../ui/PasswordInput'

export interface TwoFactorModalProps {
  open: boolean
  targetEnabled: boolean
  onClose: () => void
  onSuccess: (targetEnabled: boolean) => void
}

export const TwoFactorModal: React.FC<TwoFactorModalProps> = ({
  open,
  targetEnabled,
  onClose,
  onSuccess,
}) => {
  const [step, setStep] = useState<'PASSWORD' | 'OTP'>('PASSWORD')
  const [password, setPassword] = useState('')
  const [challengeToken, setChallengeToken] = useState('')
  const [otp, setOtp] = useState('')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useSnackbarState('', 'error')
  const [resendCooldown, setResendCooldown] = useState(0)

  useEffect(() => {
    if (open) {
      setStep('PASSWORD')
      setPassword('')
      setChallengeToken('')
      setOtp('')
      setError('')
      setLoading(false)
      setResendCooldown(0)
    }
  }, [open, targetEnabled])

  useEffect(() => {
    if (resendCooldown <= 0) return
    const timer = window.setTimeout(() => setResendCooldown((c) => Math.max(0, c - 1)), 1000)
    return () => window.clearTimeout(timer)
  }, [resendCooldown])

  useEffect(() => {
    if (!open) return
    const prev = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape' && !loading) {
        onClose()
      }
    }
    window.addEventListener('keydown', handleKeyDown)
    return () => {
      document.body.style.overflow = prev
      window.removeEventListener('keydown', handleKeyDown)
    }
  }, [open, loading, onClose])

  if (!open) return null

  const handleRequestCode = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!password.trim() || loading) return
    setError('')
    setLoading(true)
    try {
      const challenge = await authApi.beginTwoFactorSettingsChange(password, targetEnabled)
      setChallengeToken(challenge.challengeToken)
      setStep('OTP')
      setResendCooldown(60)
    } catch (err) {
      setError(apiErrorMessage(err, 'Failed to verify password or send code'))
    } finally {
      setLoading(false)
    }
  }

  const handleVerifyOtp = async (e?: React.FormEvent) => {
    if (e) e.preventDefault()
    if (!challengeToken || otp.length !== 6 || loading) return
    setError('')
    setLoading(true)
    try {
      await authApi.completeTwoFactorSettingsChange(challengeToken, otp)
      onSuccess(targetEnabled)
    } catch (err) {
      setError(apiErrorMessage(err, 'Invalid or expired verification code'))
    } finally {
      setLoading(false)
    }
  }

  const handleResend = async () => {
    if (resendCooldown > 0 || loading || !password) return
    setError('')
    setLoading(true)
    try {
      const challenge = await authApi.beginTwoFactorSettingsChange(password, targetEnabled)
      setChallengeToken(challenge.challengeToken)
      setOtp('')
      setResendCooldown(60)
    } catch (err) {
      setError(apiErrorMessage(err, 'Unable to resend verification code'))
    } finally {
      setLoading(false)
    }
  }

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center p-4"
      role="dialog"
      aria-modal="true"
      aria-labelledby="two-factor-modal-title"
    >
      {/* Backdrop */}
      <div
        className="fixed inset-0 bg-slate-950/60 backdrop-blur-xs transition-opacity"
        onClick={() => !loading && onClose()}
      />

      {/* Modal Dialog */}
      <div className="relative z-10 w-full max-w-md rounded-2xl bg-white dark:bg-slate-900 p-6 sm:p-7 shadow-2xl border border-slate-200/80 dark:border-slate-800 transition-all">
        {/* Header */}
        <div className="flex items-start justify-between gap-4">
          <div className="flex items-center gap-3">
            <div className="flex size-11 items-center justify-center rounded-xl bg-indigo-50 dark:bg-indigo-950/40 text-indigo-600 dark:text-indigo-400">
              <FiShield className="size-5" aria-hidden="true" />
            </div>
            <div>
              <h2
                id="two-factor-modal-title"
                className="text-lg font-bold text-slate-950 dark:text-white"
              >
                {targetEnabled ? 'Enable' : 'Disable'} 2FA
              </h2>
              <p className="text-xs text-slate-500 dark:text-slate-400">
                Two-Factor Authentication Setup
              </p>
            </div>
          </div>
          <button
            type="button"
            className="rounded-lg p-1.5 text-slate-400 hover:text-slate-600 hover:bg-slate-100 dark:hover:bg-slate-800 dark:hover:text-slate-200 transition"
            onClick={onClose}
            disabled={loading}
            aria-label="Close dialog"
          >
            <FiX className="size-5" aria-hidden="true" />
          </button>
        </div>

        {/* Step Indicator */}
        <div className="mt-5 flex items-center gap-2 border-b border-slate-100 dark:border-slate-800 pb-4 text-xs font-semibold">
          <span
            className={`flex items-center gap-1.5 ${
              step === 'PASSWORD'
                ? 'text-indigo-600 dark:text-indigo-400'
                : 'text-emerald-600 dark:text-emerald-400'
            }`}
          >
            {step === 'OTP' ? (
              <FiCheckCircle className="size-4" />
            ) : (
              <span className="flex size-5 items-center justify-center rounded-full bg-indigo-100 dark:bg-indigo-900/50 text-[11px]">
                1
              </span>
            )}
            Confirm Password
          </span>
          <span className="text-slate-300 dark:text-slate-700">/</span>
          <span
            className={`flex items-center gap-1.5 ${
              step === 'OTP'
                ? 'text-indigo-600 dark:text-indigo-400'
                : 'text-slate-400 dark:text-slate-500'
            }`}
          >
            <span
              className={`flex size-5 items-center justify-center rounded-full text-[11px] ${
                step === 'OTP'
                  ? 'bg-indigo-100 dark:bg-indigo-900/50'
                  : 'bg-slate-100 dark:bg-slate-800'
              }`}
            >
              2
            </span>
            Verify Code
          </span>
        </div>

        {/* Form Body */}
        {step === 'PASSWORD' ? (
          <form className="mt-5 space-y-4" onSubmit={handleRequestCode}>
            <p className="text-sm leading-6 text-slate-600 dark:text-slate-300">
              For your security, please confirm your current password to receive a one-time
              verification code via email.
            </p>

            <label className="block">
              <span className="mb-1.5 flex items-center gap-1.5 text-xs font-semibold text-slate-700 dark:text-slate-300">
                <FiLock className="size-3.5 text-slate-400" />
                Current password
              </span>
              <PasswordInput
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder="Enter current password"
                autoComplete="current-password"
                required
                autoFocus
                disabled={loading}
              />
            </label>



            <div className="flex items-center justify-end gap-3 pt-3">
              <button
                type="button"
                className="secondary-button"
                onClick={onClose}
                disabled={loading}
              >
                Cancel
              </button>
              <button
                type="submit"
                className="primary-button"
                disabled={loading || !password.trim()}
              >
                {loading ? 'Sending code...' : 'Send verification code'}
              </button>
            </div>
          </form>
        ) : (
          <form className="mt-5 space-y-4" onSubmit={handleVerifyOtp}>
            <div className="flex items-center gap-2 rounded-xl bg-indigo-50/60 dark:bg-indigo-950/30 p-3 text-xs text-indigo-700 dark:text-indigo-300">
              <FiMail className="size-4 shrink-0" />
              <span>We sent a 6-digit verification code to your registered email address.</span>
            </div>

            <div>
              <span className="mb-2 block text-center text-xs font-semibold text-slate-700 dark:text-slate-300">
                Enter 6-digit code
              </span>
              <OtpBoxInput
                length={6}
                value={otp}
                onChange={(val) => {
                  setOtp(val)
                  if (error) setError('')
                }}
                disabled={loading}
                autoFocus
                error={Boolean(error)}
                onComplete={() => {
                  // auto submit on 6th digit if desired
                }}
              />
            </div>



            <div className="flex items-center justify-center pt-1 text-xs">
              <button
                type="button"
                onClick={handleResend}
                disabled={loading || resendCooldown > 0}
                className="font-medium text-indigo-600 hover:text-indigo-700 dark:text-indigo-400 disabled:opacity-50"
              >
                {resendCooldown > 0 ? `Resend code in ${resendCooldown}s` : 'Resend code'}
              </button>
            </div>

            <div className="flex items-center justify-end gap-3 pt-3">
              <button
                type="button"
                className="secondary-button"
                onClick={() => setStep('PASSWORD')}
                disabled={loading}
              >
                Back
              </button>
              <button
                type="submit"
                className="primary-button"
                disabled={loading || otp.length !== 6}
              >
                {loading
                  ? 'Verifying...'
                  : targetEnabled
                    ? 'Verify & Enable 2FA'
                    : 'Verify & Disable 2FA'}
              </button>
            </div>
          </form>
        )}
      </div>
    </div>
  )
}

export default TwoFactorModal
