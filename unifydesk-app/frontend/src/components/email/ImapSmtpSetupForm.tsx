import { useSnackbarState } from '../feedback/useSnackbarState'
import { useEffect, useState, type FormEvent } from 'react'
import { FiArrowLeft, FiEye, FiEyeOff, FiLock, FiServer } from 'react-icons/fi'
import { imapSmtpApi } from '../../features/channel/imapSmtpApi'
import type { ImapSmtpConnection, MailSecurityMode } from '../../features/channel/types'
import { apiErrorMessage } from '../../lib/api/apiClient'

type ImapSmtpSetupFormProps = {
  companyId: string
  onBack: () => void
  onConnected: (connection: ImapSmtpConnection) => void
  onError?: (message: string) => void
}

export default function ImapSmtpSetupForm({
  companyId,
  onBack,
  onConnected,
  onError,
}: ImapSmtpSetupFormProps) {
  const [email, setEmail] = useState('')
  const [displayName, setDisplayName] = useState('')
  const [username, setUsername] = useState('')
  const [usernameEdited, setUsernameEdited] = useState(false)
  const [password, setPassword] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [imapHost, setImapHost] = useState('')
  const [imapPort, setImapPort] = useState(993)
  const [imapSecurity, setImapSecurity] = useState<MailSecurityMode>('SSL_TLS')
  const [smtpHost, setSmtpHost] = useState('')
  const [smtpPort, setSmtpPort] = useState(465)
  const [smtpSecurity, setSmtpSecurity] = useState<MailSecurityMode>('SSL_TLS')
  const [sentFolder, setSentFolder] = useState('')
  const [connecting, setConnecting] = useState(false)
  const [, setError] = useSnackbarState('', 'error')

  useEffect(() => {
    if (!usernameEdited) setUsername(email)
  }, [email, usernameEdited])

  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (connecting) return

    setConnecting(true)
    setError('')
    try {
      const connection = await imapSmtpApi.connect(companyId, {
        email: email.trim(),
        displayName: displayName.trim() || undefined,
        username: username.trim(),
        password,
        imapHost: imapHost.trim(),
        imapPort,
        imapSecurity,
        smtpHost: smtpHost.trim(),
        smtpPort,
        smtpSecurity,
        sentFolder: sentFolder.trim() || undefined,
      })
      onConnected(connection)
    } catch (requestError) {
      const message = apiErrorMessage(
        requestError,
        'Unable to connect this mailbox. Check the server settings and app password.',
      )
      setError(message)
      onError?.(message)
    } finally {
      setConnecting(false)
    }
  }

  const updateImapSecurity = (value: MailSecurityMode) => {
    setImapSecurity(value)
    setImapPort(value === 'SSL_TLS' ? 993 : 143)
  }

  const updateSmtpSecurity = (value: MailSecurityMode) => {
    setSmtpSecurity(value)
    setSmtpPort(value === 'SSL_TLS' ? 465 : 587)
  }

  return (
    <form onSubmit={(event) => void submit(event)}>
      <button
        type="button"
        className="inline-flex items-center gap-2 text-xs font-semibold text-slate-500 hover:text-slate-800"
        onClick={onBack}
        disabled={connecting}
      >
        <FiArrowLeft aria-hidden="true" />
        Email providers
      </button>

      <div className="mt-4 flex items-start gap-3">
        <span className="email-provider-mark">
          <FiServer aria-hidden="true" />
        </span>
        <div>
          <h2 className="text-lg font-bold text-slate-950">Other email</h2>
          <p className="mt-1 text-sm leading-6 text-slate-500">
            Connect a custom mailbox using IMAP for receiving and SMTP for sending.
          </p>
        </div>
      </div>

      <div className="mt-5 grid gap-4 sm:grid-cols-2">
        <label className="text-xs font-semibold text-slate-600 sm:col-span-2">
          Email address
          <input
            type="email"
            className="form-input mt-1.5"
            value={email}
            autoComplete="email"
            required
            placeholder="you@company.com"
            onChange={(event) => setEmail(event.target.value)}
          />
        </label>

        <label className="text-xs font-semibold text-slate-600 sm:col-span-2">
          Display name
          <input
            type="text"
            className="form-input mt-1.5"
            value={displayName}
            placeholder="Your name or company"
            onChange={(event) => setDisplayName(event.target.value)}
          />
        </label>

        <label className="text-xs font-semibold text-slate-600 sm:col-span-2">
          Username
          <input
            type="text"
            className="form-input mt-1.5"
            value={username}
            autoComplete="username"
            required
            onChange={(event) => {
              setUsernameEdited(true)
              setUsername(event.target.value)
            }}
          />
        </label>

        <label className="text-xs font-semibold text-slate-600 sm:col-span-2">
          Password / app password
          <div className="relative mt-1.5">
            <input
              type={showPassword ? 'text' : 'password'}
              className="form-input pr-11"
              value={password}
              autoComplete="new-password"
              required
              onChange={(event) => setPassword(event.target.value)}
            />
            <button
              type="button"
              className="absolute inset-y-0 right-0 inline-flex w-10 items-center justify-center text-slate-400 hover:text-slate-700"
              aria-label={showPassword ? 'Hide password' : 'Show password'}
              onClick={() => setShowPassword((current) => !current)}
            >
              {showPassword ? <FiEyeOff aria-hidden="true" /> : <FiEye aria-hidden="true" />}
            </button>
          </div>
          <span className="mt-1.5 flex items-center gap-1 text-[10px] font-normal text-slate-400">
            <FiLock aria-hidden="true" />
            Stored encrypted. Use an app password when your provider requires one.
          </span>
        </label>
      </div>

      <div className="mt-6 rounded-xl border border-slate-200 p-4">
        <p className="text-xs font-bold text-slate-800">Incoming mail · IMAP</p>
        <div className="mt-3 grid gap-3 sm:grid-cols-[1fr_100px]">
          <label className="text-xs font-semibold text-slate-600">
            IMAP host
            <input
              type="text"
              className="form-input mt-1.5"
              value={imapHost}
              required
              placeholder="imap.example.com"
              onChange={(event) => setImapHost(event.target.value)}
            />
          </label>
          <label className="text-xs font-semibold text-slate-600">
            Port
            <input
              type="number"
              className="form-input mt-1.5"
              min={1}
              max={65535}
              value={imapPort}
              required
              onChange={(event) => setImapPort(Number(event.target.value))}
            />
          </label>
        </div>
        <div className="mt-3 grid grid-cols-2 gap-2">
          {(['SSL_TLS', 'STARTTLS'] as MailSecurityMode[]).map((mode) => (
            <button
              key={mode}
              type="button"
              className={
                'rounded-lg border px-3 py-2 text-xs font-semibold ' +
                (imapSecurity === mode
                  ? 'border-indigo-300 bg-indigo-50 text-indigo-700'
                  : 'border-slate-200 text-slate-500')
              }
              onClick={() => updateImapSecurity(mode)}
            >
              {mode === 'SSL_TLS' ? 'SSL / TLS' : 'STARTTLS'}
            </button>
          ))}
        </div>
      </div>

      <div className="mt-4 rounded-xl border border-slate-200 p-4">
        <p className="text-xs font-bold text-slate-800">Outgoing mail · SMTP</p>
        <div className="mt-3 grid gap-3 sm:grid-cols-[1fr_100px]">
          <label className="text-xs font-semibold text-slate-600">
            SMTP host
            <input
              type="text"
              className="form-input mt-1.5"
              value={smtpHost}
              required
              placeholder="smtp.example.com"
              onChange={(event) => setSmtpHost(event.target.value)}
            />
          </label>
          <label className="text-xs font-semibold text-slate-600">
            Port
            <input
              type="number"
              className="form-input mt-1.5"
              min={1}
              max={65535}
              value={smtpPort}
              required
              onChange={(event) => setSmtpPort(Number(event.target.value))}
            />
          </label>
        </div>
        <div className="mt-3 grid grid-cols-2 gap-2">
          {(['SSL_TLS', 'STARTTLS'] as MailSecurityMode[]).map((mode) => (
            <button
              key={mode}
              type="button"
              className={
                'rounded-lg border px-3 py-2 text-xs font-semibold ' +
                (smtpSecurity === mode
                  ? 'border-indigo-300 bg-indigo-50 text-indigo-700'
                  : 'border-slate-200 text-slate-500')
              }
              onClick={() => updateSmtpSecurity(mode)}
            >
              {mode === 'SSL_TLS' ? 'SSL / TLS' : 'STARTTLS'}
            </button>
          ))}
        </div>
      </div>

      <label className="mt-4 block text-xs font-semibold text-slate-600">
        Sent folder <span className="font-normal text-slate-400">(optional)</span>
        <input
          type="text"
          className="form-input mt-1.5"
          value={sentFolder}
          placeholder="Auto-detect"
          onChange={(event) => setSentFolder(event.target.value)}
        />
        <span className="mt-1.5 block text-[10px] font-normal leading-4 text-slate-400">
          Leave blank and we’ll try common folders such as Sent, Sent Items, and Sent Mail.
        </span>
      </label>



      <div className="mt-6 flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
        <button
          type="button"
          className="secondary-button justify-center"
          disabled={connecting}
          onClick={onBack}
        >
          Cancel
        </button>
        <button type="submit" className="primary-button justify-center gap-2" disabled={connecting}>
          <FiServer aria-hidden="true" />
          {connecting ? 'Testing servers…' : 'Test & connect'}
        </button>
      </div>
    </form>
  )
}
