import { useEffect, useMemo, useState, type FormEvent } from 'react'
import { FiCheckCircle, FiMail, FiSend, FiX } from 'react-icons/fi'
import { broadcastApi } from '../../features/broadcast/broadcastApi'
import type { BroadcastSender } from '../../features/broadcast/types'
import { emailTemplateApi } from '../../features/emailTemplate/emailTemplateApi'
import type {
  EmailTemplateProviderBinding,
  EmailTemplateSummary,
} from '../../features/emailTemplate/types'
import { apiErrorMessage } from '../../lib/api/apiClient'
import { useSnackbar } from '../feedback/SnackbarProvider'

type Props = {
  companyId: string
  sender: BroadcastSender
  onClose: () => void
  onDone: (recipient: string) => void
}

const sampleValue = (variable: string) => {
  const key = variable.toLowerCase()
  if (key === 'first_name') return 'Test'
  if (key === 'last_name') return 'Recipient'
  if (key === 'full_name' || key === 'name') return 'Test Recipient'
  if (key.includes('url') || key.includes('link')) return 'https://example.com'
  if (key.includes('date')) return '30 September 2026'
  if (key.includes('year')) return '2026'
  return 'Sample value'
}

export default function BroadcastTestEmailModal({
  companyId,
  sender,
  onClose,
  onDone,
}: Props) {
  const snackbar = useSnackbar()
  const isMsg91 = sender.provider === 'MSG91_EMAIL'
  const [recipient, setRecipient] = useState('')
  const [subject, setSubject] = useState('Messaging test email')
  const [bodyText, setBodyText] = useState(
    'This is a test email from Messaging. Your Broadcast provider connection is working.',
  )
  const [templates, setTemplates] = useState<EmailTemplateSummary[]>([])
  const [bindings, setBindings] = useState<EmailTemplateProviderBinding[]>([])
  const [templateId, setTemplateId] = useState('')
  const [variables, setVariables] = useState<Record<string, string>>({})
  const [busy, setBusy] = useState(false)
  const [acceptedRecipient, setAcceptedRecipient] = useState<string | null>(null)

  useEffect(() => {
    if (!isMsg91) return
    let active = true
    Promise.all([
      emailTemplateApi.published(companyId),
      emailTemplateApi.providerBindings(companyId, { connectionId: sender.connectionId }),
    ])
      .then(([published, providerBindings]) => {
        if (!active) return
        setTemplates(published.filter((template) => template.purpose === 'TRANSACTIONAL'))
        setBindings(providerBindings)
      })
      .catch((error) => {
        if (active) snackbar.error(apiErrorMessage(error, 'Unable to load MSG91 templates'))
      })
    return () => {
      active = false
    }
  }, [companyId, isMsg91, sender.connectionId, snackbar])

  const sendableTemplates = useMemo(() => {
    if (!isMsg91) return templates
    const sendable = new Set(
      bindings
        .filter((binding) => binding.sendable)
        .map((binding) => `${binding.templateId}:${binding.templateVersion}`),
    )
    return templates.filter(
      (template) =>
        template.publishedVersion !== null &&
        template.publishedVersion !== undefined &&
        sendable.has(`${template.id}:${template.publishedVersion}`),
    )
  }, [bindings, isMsg91, templates])

  const selectedTemplate = sendableTemplates.find((template) => template.id === templateId) ?? null

  useEffect(() => {
    if (!selectedTemplate) {
      setVariables({})
      return
    }
    setVariables((current) => {
      const next: Record<string, string> = {}
      selectedTemplate.variables.forEach((variable) => {
        if (
          variable === 'email' ||
          variable === 'destination' ||
          variable === 'current_date' ||
          variable === 'current_year'
        ) {
          return
        }
        next[variable] = current[variable] ?? sampleValue(variable)
      })
      return next
    })
  }, [selectedTemplate])

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    if (isMsg91 && !templateId) {
      snackbar.error('Choose a synced MSG91 Email template.')
      return
    }
    setBusy(true)
    try {
      const result = await broadcastApi.sendTestEmail(companyId, sender.id, {
        recipient: recipient.trim(),
        subject: isMsg91 ? undefined : subject.trim(),
        bodyText: isMsg91 ? undefined : bodyText.trim(),
        templateId: isMsg91 ? templateId : undefined,
        variables: isMsg91 ? variables : undefined,
      })
      setAcceptedRecipient(result.recipient)
    } catch (requestError) {
      snackbar.error(apiErrorMessage(requestError, 'Unable to send test email'))
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="mobile-modal-shell fixed inset-0 z-50 flex items-center justify-center bg-slate-950/40 p-4">
      <div className="mobile-sheet max-h-[92vh] w-full max-w-xl overflow-y-auto rounded-2xl bg-white p-5 shadow-xl">
        <div className="flex items-start justify-between gap-4">
          <div className="flex items-start gap-3">
            <span className="flex size-10 shrink-0 items-center justify-center rounded-xl bg-emerald-50 text-emerald-600">
              <FiMail aria-hidden="true" />
            </span>
            <div>
              <p className="text-base font-bold text-slate-950">
                {acceptedRecipient ? 'Test email accepted' : 'Send a test email'}
              </p>
              <p className="mt-1 text-xs leading-5 text-slate-500">
                {sender.displayName
                  ? `${sender.displayName} <${sender.fromAddress}>`
                  : sender.fromAddress}
              </p>
            </div>
          </div>
          <button type="button" className="admin-back-button" onClick={onClose} aria-label="Close">
            <FiX aria-hidden="true" />
          </button>
        </div>

        {acceptedRecipient ? (
          <div className="mt-6">
            <div className="rounded-2xl border border-emerald-200 bg-emerald-50 p-5">
              <FiCheckCircle className="text-emerald-600" size={24} aria-hidden="true" />
              <p className="mt-3 text-sm font-bold text-emerald-900">
                The provider accepted the test message
              </p>
              <p className="mt-1 text-xs leading-5 text-emerald-700">
                Sent to {acceptedRecipient}. Provider acceptance confirms submission; final inbox
                delivery still depends on the recipient provider and spam rules.
              </p>
            </div>
            <div className="mt-5 flex justify-end">
              <button
                type="button"
                className="primary-button"
                onClick={() => onDone(acceptedRecipient)}
              >
                Finish setup
              </button>
            </div>
          </div>
        ) : (
          <form className="mt-5" onSubmit={submit}>
            <label className="block text-xs font-semibold text-slate-700">
              Send test to
              <input
                type="email"
                className="form-input mt-2"
                value={recipient}
                onChange={(event) => setRecipient(event.target.value)}
                placeholder="you@example.com"
                required
              />
            </label>

            {isMsg91 ? (
              <>
                <label className="mt-4 block text-xs font-semibold text-slate-700">
                  Synced transactional template
                  <select
                    className="form-input mt-2"
                    value={templateId}
                    onChange={(event) => setTemplateId(event.target.value)}
                    required
                  >
                    <option value="">Choose a template</option>
                    {sendableTemplates.map((template) => (
                      <option key={template.id} value={template.id}>
                        {template.name} · v{template.publishedVersion}
                      </option>
                    ))}
                  </select>
                  {sendableTemplates.length === 0 && (
                    <span className="mt-1 block text-[10px] leading-4 text-amber-600">
                      No MSG91-ready templates. Publish a transactional template and sync it from
                      Templates first.
                    </span>
                  )}
                </label>

                {selectedTemplate && selectedTemplate.variables.length > 0 && (
                  <div className="mt-4 rounded-xl border border-slate-200 p-4">
                    <p className="text-xs font-bold text-slate-800">Test variable values</p>
                    <div className="mt-3 grid gap-3">
                      {selectedTemplate.variables
                        .filter(
                          (variable) =>
                            variable !== 'email' &&
                            variable !== 'destination' &&
                            variable !== 'current_date' &&
                            variable !== 'current_year',
                        )
                        .map((variable) => (
                          <label key={variable} className="text-xs font-semibold text-slate-600">
                            {'{{'}{variable}{'}}'}
                            <input
                              className="form-input mt-1.5"
                              value={variables[variable] ?? ''}
                              onChange={(event) =>
                                setVariables((current) => ({
                                  ...current,
                                  [variable]: event.target.value,
                                }))
                              }
                              required
                            />
                          </label>
                        ))}
                    </div>
                  </div>
                )}
              </>
            ) : (
              <>
                <label className="mt-4 block text-xs font-semibold text-slate-700">
                  Subject
                  <input
                    className="form-input mt-2"
                    value={subject}
                    onChange={(event) => setSubject(event.target.value)}
                    maxLength={998}
                  />
                </label>

                <label className="mt-4 block text-xs font-semibold text-slate-700">
                  Message
                  <textarea
                    className="form-input mt-2 min-h-28 resize-y"
                    value={bodyText}
                    onChange={(event) => setBodyText(event.target.value)}
                    maxLength={20000}
                  />
                </label>
              </>
            )}

            <div className="mt-4 rounded-xl bg-slate-50 p-3 text-[11px] leading-5 text-slate-500">
              {isMsg91
                ? 'MSG91 test sends use the same provider template binding and variables as production Broadcast.'
                : 'Messaging sends only when you click Send test.'}
            </div>

            <div className="mt-6 flex flex-wrap justify-end gap-2">
              <button type="button" className="secondary-button" onClick={onClose} disabled={busy}>
                Skip for now
              </button>
              <button
                type="submit"
                className="primary-button inline-flex items-center gap-2"
                disabled={busy || (isMsg91 && !templateId)}
              >
                <FiSend aria-hidden="true" />
                {busy ? 'Sending…' : 'Send test'}
              </button>
            </div>
          </form>
        )}
      </div>
    </div>
  )
}
