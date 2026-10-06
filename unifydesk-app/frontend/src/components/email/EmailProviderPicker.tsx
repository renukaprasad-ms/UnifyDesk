import { useState } from 'react'
import { FaMicrosoft } from 'react-icons/fa'
import { FiMail, FiPlus, FiServer, FiX } from 'react-icons/fi'
import { SiGmail } from 'react-icons/si'
import { gmailApi } from '../../features/channel/gmailApi'
import { microsoftApi } from '../../features/channel/microsoftApi'
import type { ImapSmtpConnection } from '../../features/channel/types'
import { apiErrorMessage } from '../../lib/api/apiClient'
import ImapSmtpSetupForm from './ImapSmtpSetupForm'

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
  onConnected?: (connection: ImapSmtpConnection) => void
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
  onConnected,
}: EmailProviderPickerProps) {
  const [open, setOpen] = useState(false)
  const [connecting, setConnecting] = useState<'GMAIL' | 'MICROSOFT' | null>(null)
  const [customSetup, setCustomSetup] = useState(false)
  const [error, setError] = useState('')

  const close = () => {
    setOpen(false)
    setCustomSetup(false)
    onClose?.()
  }

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
          setCustomSetup(false)
          onOpen?.()
          setOpen(true)
        }}
      >
        {buttonIcon === 'plus' ? <FiPlus aria-hidden="true" /> : <FiMail aria-hidden="true" />}
        {label}
      </button>

      {open && (
        <div
          className="mobile-modal-shell fixed inset-0 z-[90] flex items-center justify-center overflow-y-auto bg-slate-950/40 p-4"
          role="presentation"
          onMouseDown={(event) => {
            if (event.currentTarget === event.target && !connecting) close()
          }}
        >
          <div
            className="mobile-sheet my-auto w-full max-w-lg rounded-2xl bg-white p-5 shadow-2xl sm:p-6"
            role="dialog"
            aria-modal="true"
            aria-labelledby="email-provider-picker-title"
          >
            {customSetup ? (
              <ImapSmtpSetupForm
                companyId={companyId}
                onBack={() => setCustomSetup(false)}
                onConnected={(connection) => {
                  close()
                  onConnected?.(connection)
                }}
                onError={onError}
              />
            ) : (
              <>
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
                    onClick={close}
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
                    <span className="email-provider-mark email-provider-logo-gmail">
                      <SiGmail aria-hidden="true" />
                    </span>
                    <span className="min-w-0 flex-1 text-left">
                      <span className="block text-sm font-bold text-slate-900">Google Gmail</span>
                      <span className="mt-0.5 block text-xs text-slate-500">
                        {connecting === 'GMAIL'
                          ? 'Opening Google authorization...'
                          : 'Connect securely with Google OAuth'}
                      </span>
                    </span>
                  </button>

                  <button
                    type="button"
                    className="email-provider-option"
                    disabled={Boolean(connecting)}
                    onClick={() => void connectMicrosoft()}
                  >
                    <span className="email-provider-mark email-provider-logo-outlook">
                      <FaMicrosoft aria-hidden="true" />
                    </span>
                    <span className="min-w-0 flex-1 text-left">
                      <span className="block text-sm font-bold text-slate-900">
                        Microsoft 365 / Outlook
                      </span>
                      <span className="mt-0.5 block text-xs text-slate-500">
                        {connecting === 'MICROSOFT'
                          ? 'Opening Microsoft authorization...'
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
                      setCustomSetup(true)
                    }}
                  >
                    <span className="email-provider-mark email-provider-logo-imap">
                      <FiServer aria-hidden="true" />
                    </span>
                    <span className="min-w-0 flex-1 text-left">
                      <span className="block text-sm font-bold text-slate-900">Other email</span>
                      <span className="mt-0.5 block text-xs text-slate-500">
                        IMAP + SMTP for custom domains and other mail providers
                      </span>
                    </span>
                  </button>
                </div>

                {error && (
                  <div className="mt-4 rounded-xl border border-rose-200 bg-rose-50 px-4 py-3 text-xs leading-5 text-rose-700">
                    {error}
                  </div>
                )}
              </>
            )}
          </div>
        </div>
      )}
    </>
  )
}
