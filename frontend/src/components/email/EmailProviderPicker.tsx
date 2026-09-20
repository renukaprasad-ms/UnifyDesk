import { useState } from 'react'
import { FiMail, FiPlus, FiX } from 'react-icons/fi'
import { gmailApi } from '../../features/channel/gmailApi'
import { microsoftApi } from '../../features/channel/microsoftApi'
import { apiErrorMessage } from '../../lib/api/apiClient'

type EmailProviderPickerProps = {
  companyId: string
  returnTo?: string
  label?: string
  className?: string
  disabled?: boolean
  onError?: (message: string) => void
  onOpen?: () => void
  onClose?: () => void
  buttonIcon?: 'mail' | 'plus'
}

export default function EmailProviderPicker({
  companyId,
  returnTo,
  label = 'Connect email',
  className = 'primary-button w-full',
  disabled = false,
  onError,
  onOpen,
  onClose,
  buttonIcon = 'mail',
}: EmailProviderPickerProps) {
  const [open, setOpen] = useState(false)
  const [connecting, setConnecting] = useState<'GMAIL' | 'MICROSOFT' | null>(null)
  const [smtpNotice, setSmtpNotice] = useState(false)
  const [error, setError] = useState('')

  const fail = (requestError: unknown, fallback: string) => {
    const message = apiErrorMessage(requestError, fallback)
    setError(message)
    onError?.(message)
    setConnecting(null)
  }

  const connectGmail = async () => {
    if (connecting) return
    setConnecting('GMAIL')
    setError('')
    setSmtpNotice(false)
    try {
      const authorization = await gmailApi.authorize(companyId, returnTo)
      window.location.assign(authorization.authorizationUrl)
    } catch (requestError) {
      fail(requestError, 'Unable to start Gmail connection')
    }
  }

  const connectMicrosoft = async () => {
    if (connecting) return
    setConnecting('MICROSOFT')
    setError('')
    setSmtpNotice(false)
    try {
      const authorization = await microsoftApi.authorize(companyId, returnTo)
      window.location.assign(authorization.authorizationUrl)
    } catch (requestError) {
      fail(requestError, 'Unable to start Microsoft 365 connection')
    }
  }

  return (
    <>
      <button
        type="button"
        className={className}
        disabled={disabled || Boolean(connecting)}
        onClick={() => {
          setError('')
          setSmtpNotice(false)
          onOpen?.()
          setOpen(true)
        }}
      >
        {buttonIcon === 'plus' ? <FiPlus aria-hidden="true" /> : <FiMail aria-hidden="true" />}
        {label}
      </button>

      {open && (
        <div
          className="fixed inset-0 z-[90] flex items-center justify-center bg-slate-950/40 p-4"
          role="presentation"
          onMouseDown={(event) => {
            if (event.currentTarget === event.target && !connecting) {
              setOpen(false)
              onClose?.()
            }
          }}
        >
          <div
            className="w-full max-w-lg rounded-2xl bg-white p-5 shadow-2xl sm:p-6"
            role="dialog"
            aria-modal="true"
            aria-labelledby="email-provider-picker-title"
          >
            <div className="flex items-start justify-between gap-4">
              <div>
                <p className="eyebrow">EMAIL PROVIDER</p>
                <h2
                  id="email-provider-picker-title"
                  className="mt-2 text-lg font-bold text-slate-950"
                >
                  Connect an email account
                </h2>
                <p className="mt-2 text-sm leading-6 text-slate-500">
                  Choose the provider for this mailbox.
                </p>
              </div>
              <button
                type="button"
                className="inline-flex size-9 items-center justify-center rounded-lg border border-slate-200 text-slate-500 hover:bg-slate-50"
                aria-label="Close provider picker"
                disabled={Boolean(connecting)}
                onClick={() => {
                  setOpen(false)
                  onClose?.()
                }}
              >
                <FiX aria-hidden="true" />
              </button>
            </div>

            <div className="mt-5 grid gap-3">
              <button
                type="button"
                className="email-provider-option"
                disabled={Boolean(connecting)}
                onClick={() => void connectGmail()}
              >
                <span className="email-provider-mark">G</span>
                <span className="min-w-0 flex-1 text-left">
                  <span className="block text-sm font-bold text-slate-900">Google Gmail</span>
                  <span className="mt-0.5 block text-xs text-slate-500">
                    {connecting === 'GMAIL' ? 'Opening Google authorization…' : 'Connect securely with Google OAuth'}
                  </span>
                </span>
              </button>

              <button
                type="button"
                className="email-provider-option"
                disabled={Boolean(connecting)}
                onClick={() => void connectMicrosoft()}
              >
                <span className="email-provider-mark">M</span>
                <span className="min-w-0 flex-1 text-left">
                  <span className="block text-sm font-bold text-slate-900">
                    Microsoft 365 / Outlook
                  </span>
                  <span className="mt-0.5 block text-xs text-slate-500">
                    {connecting === 'MICROSOFT'
                      ? 'Opening Microsoft authorization…'
                      : 'Connect Outlook, Hotmail, or Microsoft 365 with OAuth'}
                  </span>
                </span>
              </button>

              <button
                type="button"
                className="email-provider-option"
                disabled={Boolean(connecting)}
                onClick={() => {
                  setError('')
                  setSmtpNotice(true)
                }}
              >
                <span className="email-provider-mark">S</span>
                <span className="min-w-0 flex-1 text-left">
                  <span className="block text-sm font-bold text-slate-900">SMTP</span>
                  <span className="mt-0.5 block text-xs text-slate-500">
                    Manual server credentials for custom mail providers
                  </span>
                </span>
              </button>
            </div>

            {smtpNotice && (
              <div className="mt-4 rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-xs leading-5 text-amber-800">
                SMTP is defined as an email provider in the backend, but its credential/setup API is
                not implemented yet. SMTP uses manual server credentials rather than OAuth, so this
                option will be wired when that backend flow is added.
              </div>
            )}

            {error && (
              <div className="mt-4 rounded-xl border border-rose-200 bg-rose-50 px-4 py-3 text-xs leading-5 text-rose-700">
                {error}
              </div>
            )}
          </div>
        </div>
      )}
    </>
  )
}
