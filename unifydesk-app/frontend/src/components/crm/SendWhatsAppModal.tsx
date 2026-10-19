import { useSnackbarState } from '../feedback/useSnackbarState'
import PhoneInput from '../ui/PhoneInput'
import { phoneValidationMessage } from '../../utils/phone'
import { useEffect, useMemo, useState } from 'react'
import {
  FiCheck,
  FiCheckCircle,
  FiInfo,
  FiMessageCircle,
  FiPhone,
  FiSend,
  FiZap,
} from 'react-icons/fi'
import WorkspaceDialog from '../ui/WorkspaceDialog'
import {
  crmWhatsAppApi,
  type WhatsAppContext,
  type WhatsAppTemplate,
  type SendWhatsAppResult,
} from '../../features/contacts/crmWhatsAppApi'
import { apiErrorMessage } from '../../lib/api/apiClient'

export default function SendWhatsAppModal({
  companyId,
  contactId,
  dealId,
  recipientName,
  defaultPhone,
  onClose,
  onSuccess,
}: {
  companyId: string
  contactId?: string | null
  dealId?: string | null
  recipientName?: string | null
  defaultPhone?: string | null
  onClose: () => void
  onSuccess?: (result: SendWhatsAppResult) => void
}) {
  const [loading, setLoading] = useState(true)
  const [context, setContext] = useState<WhatsAppContext | null>(null)
  const [templates, setTemplates] = useState<WhatsAppTemplate[]>([])
  const [error, setError] = useSnackbarState('', 'error')

  // Form State
  const [selectedPhone, setSelectedPhone] = useState('')
  const [customPhone, setCustomPhone] = useState('')
  const [isCustomPhone, setIsCustomPhone] = useState(false)
  const [mode, setMode] = useState<'TEMPLATE' | 'TEXT'>('TEMPLATE')
  const [selectedTemplateId, setSelectedTemplateId] = useState('')
  const [paramValues, setParamValues] = useState<Record<string, string>>({})
  const [freeText, setFreeText] = useState('')
  const [sending, setSending] = useState(false)
  const [sentResult, setSentResult] = useState<SendWhatsAppResult | null>(null)

  // Load Context and Templates
  useEffect(() => {
    let active = true
    setLoading(true)
    setError('')

    Promise.all([
      crmWhatsAppApi.context(companyId, contactId),
      crmWhatsAppApi.templates(companyId),
    ])
      .then(([ctx, tmpls]) => {
        if (!active) return
        setContext(ctx)
        setTemplates(tmpls)

        // Select default phone
        const phones = [...(ctx.phoneNumbers || [])]
        if (defaultPhone && !phones.includes(defaultPhone)) {
          phones.unshift(defaultPhone)
        }
        if (phones.length > 0) {
          setSelectedPhone(phones[0])
          setIsCustomPhone(false)
        } else if (defaultPhone) {
          setSelectedPhone(defaultPhone)
          setIsCustomPhone(false)
        } else {
          setIsCustomPhone(true)
        }

        // Default template
        if (tmpls.length > 0) {
          setSelectedTemplateId(tmpls[0].id)
        }

        // Set mode based on 24h window
        if (!ctx.freeFormAllowed) {
          setMode('TEMPLATE')
        }
      })
      .catch((err) => {
        if (!active) return
        setError(apiErrorMessage(err, 'Failed to load WhatsApp channel context'))
      })
      .finally(() => {
        if (active) setLoading(false)
      })

    return () => {
      active = false
    }
  }, [companyId, contactId, defaultPhone])

  // Selected template details
  const activeTemplate = useMemo(() => {
    return templates.find((t) => t.id === selectedTemplateId) || null
  }, [templates, selectedTemplateId])

  // Parse template placeholders (e.g. {{1}}, {{2}})
  const bodyComponent = useMemo(
    () => activeTemplate?.components?.find((c) => c.type === 'BODY'),
    [activeTemplate],
  )
  const headerComponent = useMemo(
    () => activeTemplate?.components?.find((c) => c.type === 'HEADER'),
    [activeTemplate],
  )
  const footerComponent = useMemo(
    () => activeTemplate?.components?.find((c) => c.type === 'FOOTER'),
    [activeTemplate],
  )
  const buttonsComponent = useMemo(
    () => activeTemplate?.components?.find((c) => c.type === 'BUTTONS'),
    [activeTemplate],
  )

  const bodyPlaceholders = useMemo(() => {
    if (!bodyComponent?.text) return []
    const matches = bodyComponent.text.match(/\{\{(\d+)\}\}/g) || []
    const unique = Array.from(new Set(matches))
    return unique.sort()
  }, [bodyComponent])

  const headerPlaceholders = useMemo(() => {
    if (!headerComponent?.text) return []
    const matches = headerComponent.text.match(/\{\{(\d+)\}\}/g) || []
    const unique = Array.from(new Set(matches))
    return unique.sort()
  }, [headerComponent])

  // Initialize placeholder values with sensible defaults when template changes
  useEffect(() => {
    if (!activeTemplate) return
    const initial: Record<string, string> = {}
    bodyPlaceholders.forEach((ph, idx) => {
      // First placeholder often is the recipient's name
      if (idx === 0 && recipientName) {
        initial[`body_${ph}`] = recipientName
      } else {
        initial[`body_${ph}`] = ''
      }
    })
    headerPlaceholders.forEach((ph) => {
      initial[`header_${ph}`] = ''
    })
    setParamValues(initial)
  }, [activeTemplate, bodyPlaceholders, headerPlaceholders, recipientName])

  // Formatted preview text
  const previewBody = useMemo(() => {
    if (!bodyComponent?.text) return ''
    let text = bodyComponent.text
    bodyPlaceholders.forEach((ph) => {
      const val = paramValues[`body_${ph}`]
      text = text.replace(ph, val ? `*${val}*` : ph)
    })
    return text
  }, [bodyComponent, bodyPlaceholders, paramValues])

  const previewHeader = useMemo(() => {
    if (!headerComponent?.text) return null
    let text = headerComponent.text
    headerPlaceholders.forEach((ph) => {
      const val = paramValues[`header_${ph}`]
      text = text.replace(ph, val ? val : ph)
    })
    return text
  }, [headerComponent, headerPlaceholders, paramValues])

  // Active phone number
  const targetPhone = isCustomPhone ? customPhone : selectedPhone

  async function handleSend() {
    const phoneError = phoneValidationMessage(targetPhone || '')
    if (phoneError) { setError(phoneError); return }
    if (!targetPhone) {
      setError('Please select or specify a recipient phone number with country code.')
      return
    }

    if (mode === 'TEXT' && !freeText.trim()) {
      setError('Please enter a message text to send.')
      return
    }

    if (mode === 'TEMPLATE' && !selectedTemplateId) {
      setError('Please select an approved WhatsApp template.')
      return
    }

    setSending(true)
    setError('')

    try {
      const bodyParams: string[] = []
      bodyPlaceholders.forEach((ph) => {
        bodyParams.push(paramValues[`body_${ph}`] || '')
      })

      const headerParams: string[] = []
      headerPlaceholders.forEach((ph) => {
        headerParams.push(paramValues[`header_${ph}`] || '')
      })

      const res = await crmWhatsAppApi.send(companyId, {
        contactId: contactId ? contactId : undefined,
        dealId: dealId ? dealId : undefined,
        phone: targetPhone,
        type: mode,
        templateId: mode === 'TEMPLATE' ? selectedTemplateId : undefined,
        parameters:
          mode === 'TEMPLATE' && (bodyParams.length > 0 || headerParams.length > 0)
            ? {
                body: bodyParams,
                header: headerParams,
              }
            : undefined,
        text: mode === 'TEXT' ? freeText.trim() : undefined,
      })

      setSentResult(res)
      if (onSuccess) {
        onSuccess(res)
      }
    } catch (err) {
      setError(apiErrorMessage(err, 'Failed to send WhatsApp message'))
    } finally {
      setSending(false)
    }
  }

  return (
    <WorkspaceDialog
      title="Send WhatsApp Message"
      onClose={onClose}
      busy={sending}
      error={error}
      wide={true}
    >
      {loading ? (
        <div className="flex flex-col items-center justify-center py-12 text-slate-500">
          <div className="h-8 w-8 animate-spin rounded-full border-2 border-emerald-500 border-t-transparent" />
          <p className="mt-3 text-xs font-medium">Connecting to WhatsApp channel...</p>
        </div>
      ) : !context?.connected ? (
        <div className="rounded-xl border border-amber-200 bg-amber-50 dark:border-amber-900/60 dark:bg-amber-950/30 p-5 text-center">
          <div className="mx-auto flex h-12 w-12 items-center justify-center rounded-full bg-amber-100 text-amber-600 dark:bg-amber-900/60 dark:text-amber-400">
            <FiMessageCircle className="h-6 w-6" />
          </div>
          <h3 className="mt-3 text-sm font-bold text-amber-900 dark:text-amber-200">
            No Active WhatsApp Channel Found
          </h3>
          <p className="mt-1 text-xs text-amber-700 dark:text-amber-300">
            You must connect a Meta WhatsApp Business channel under Channels before sending messages
            from the CRM.
          </p>
          <div className="mt-4">
            <button type="button" onClick={onClose} className="secondary-button text-xs py-1.5 px-4">
              Close
            </button>
          </div>
        </div>
      ) : sentResult ? (
        <div className="rounded-xl border border-emerald-200 bg-emerald-50 dark:border-emerald-900/60 dark:bg-emerald-950/30 p-6 text-center">
          <div className="mx-auto flex h-12 w-12 items-center justify-center rounded-full bg-emerald-100 text-emerald-600 dark:bg-emerald-900/60 dark:text-emerald-400">
            <FiCheckCircle className="h-7 w-7" />
          </div>
          <h3 className="mt-3 text-base font-bold text-emerald-900 dark:text-emerald-100">
            Message Dispatched Successfully!
          </h3>
          <p className="mt-1 text-xs text-emerald-700 dark:text-emerald-300">
            Sent to <span className="font-semibold">+{sentResult.recipientPhone}</span>. The
            conversation has been linked and recorded on the CRM timeline.
          </p>
          <div className="mt-5 flex justify-center gap-3">
            <button
              type="button"
              onClick={onClose}
              className="primary-button bg-emerald-600 hover:bg-emerald-700 text-white text-xs py-2 px-5"
            >
              Done
            </button>
          </div>
        </div>
      ) : (
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
          {/* Left Form Column */}
          <div className="lg:col-span-7 space-y-5">
            {/* Connection & 24h Window Badge */}
            <div className="flex flex-wrap items-center justify-between gap-2 rounded-xl bg-slate-50 dark:bg-slate-900/60 p-3 border border-slate-200 dark:border-slate-800">
              <div className="flex items-center gap-2">
                <span className="flex h-2.5 w-2.5 rounded-full bg-emerald-500 ring-4 ring-emerald-500/20" />
                <span className="text-xs font-semibold text-slate-800 dark:text-slate-200">
                  {context.connectionName || 'WhatsApp Business'}
                </span>
              </div>
              {context.freeFormAllowed ? (
                <span className="inline-flex items-center gap-1.5 rounded-full bg-emerald-100 dark:bg-emerald-950/60 px-2.5 py-0.5 text-[11px] font-semibold text-emerald-700 dark:text-emerald-300">
                  <FiZap className="h-3 w-3" /> 24h Customer Window Active
                </span>
              ) : (
                <span className="inline-flex items-center gap-1.5 rounded-full bg-slate-200 dark:bg-slate-800 px-2.5 py-0.5 text-[11px] font-medium text-slate-600 dark:text-slate-400">
                  <FiInfo className="h-3 w-3" /> Template Required (Outside 24h)
                </span>
              )}
            </div>

            {/* Recipient Phone Selection */}
            <div>
              <label className="block text-xs font-bold text-slate-700 dark:text-slate-300 uppercase tracking-wider mb-1.5">
                Recipient Phone Number
              </label>
              {context.phoneNumbers && context.phoneNumbers.length > 0 && !isCustomPhone ? (
                <div className="space-y-2">
                  <select
                    className="w-full rounded-xl border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-900 px-3.5 py-2.5 text-xs text-slate-900 dark:text-white shadow-xs focus:border-emerald-500 focus:outline-none focus:ring-1 focus:ring-emerald-500"
                    value={selectedPhone}
                    onChange={(e) => setSelectedPhone(e.target.value)}
                  >
                    {context.phoneNumbers.map((p) => (
                      <option key={p} value={p}>
                        {recipientName ? `${recipientName} (${p})` : p}
                      </option>
                    ))}
                  </select>
                  <button
                    type="button"
                    onClick={() => {
                      setIsCustomPhone(true)
                      setCustomPhone(selectedPhone)
                    }}
                    className="text-[11px] font-semibold text-emerald-600 dark:text-emerald-400 hover:underline inline-flex items-center gap-1"
                  >
                    <FiPhone className="h-3 w-3" /> Enter different phone number
                  </button>
                </div>
              ) : (
                <div className="space-y-1.5">
                  <div className="relative">
                    <PhoneInput
                      placeholder="e.g. +919876543210 or 14155552671"
                      value={customPhone}
                      onChange={(value) => setCustomPhone(value)}
                      className="w-full rounded-xl border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-900 px-3.5 py-2.5 text-xs text-slate-900 dark:text-white shadow-xs focus:border-emerald-500 focus:outline-none focus:ring-1 focus:ring-emerald-500"
                    />
                  </div>
                  {context.phoneNumbers && context.phoneNumbers.length > 0 && (
                    <button
                      type="button"
                      onClick={() => setIsCustomPhone(false)}
                      className="text-[11px] font-semibold text-emerald-600 dark:text-emerald-400 hover:underline"
                    >
                      Use existing contact phone ({selectedPhone})
                    </button>
                  )}
                </div>
              )}
            </div>

            {/* Mode Selector (Template vs Free-form if inside 24h) */}
            {context.freeFormAllowed && (
              <div className="flex rounded-xl bg-slate-100 dark:bg-slate-800/80 p-1">
                <button
                  type="button"
                  onClick={() => setMode('TEMPLATE')}
                  className={`flex-1 rounded-lg py-1.5 text-xs font-bold transition-colors ${
                    mode === 'TEMPLATE'
                      ? 'bg-white dark:bg-slate-900 text-slate-950 dark:text-white shadow-xs'
                      : 'text-slate-600 dark:text-slate-400 hover:text-slate-900'
                  }`}
                >
                  WhatsApp Template
                </button>
                <button
                  type="button"
                  onClick={() => setMode('TEXT')}
                  className={`flex-1 rounded-lg py-1.5 text-xs font-bold transition-colors ${
                    mode === 'TEXT'
                      ? 'bg-white dark:bg-slate-900 text-slate-950 dark:text-white shadow-xs'
                      : 'text-slate-600 dark:text-slate-400 hover:text-slate-900'
                  }`}
                >
                  Free-form Text (24h Window)
                </button>
              </div>
            )}

            {/* Template Selection */}
            {mode === 'TEMPLATE' ? (
              <div className="space-y-4">
                <div>
                  <label className="block text-xs font-bold text-slate-700 dark:text-slate-300 uppercase tracking-wider mb-1.5">
                    Select Approved Template
                  </label>
                  {templates.length === 0 ? (
                    <p className="rounded-xl border border-slate-200 dark:border-slate-800 bg-slate-50 dark:bg-slate-900/50 p-3 text-xs text-slate-500">
                      No approved templates available on this WhatsApp connection. Please sync or
                      create templates in Meta Business Manager.
                    </p>
                  ) : (
                    <select
                      className="w-full rounded-xl border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-900 px-3.5 py-2.5 text-xs text-slate-900 dark:text-white shadow-xs focus:border-emerald-500 focus:outline-none focus:ring-1 focus:ring-emerald-500"
                      value={selectedTemplateId}
                      onChange={(e) => setSelectedTemplateId(e.target.value)}
                    >
                      {templates.map((tmpl) => (
                        <option key={tmpl.id} value={tmpl.id}>
                          {tmpl.name} ({tmpl.language}) · {tmpl.category}
                        </option>
                      ))}
                    </select>
                  )}
                </div>

                {/* Placeholders Input */}
                {(headerPlaceholders.length > 0 || bodyPlaceholders.length > 0) && (
                  <div className="rounded-xl border border-slate-200 dark:border-slate-800 bg-slate-50/50 dark:bg-slate-900/30 p-4 space-y-3">
                    <p className="text-xs font-bold text-slate-800 dark:text-slate-200 flex items-center gap-1.5">
                      <FiZap className="text-emerald-600" /> Template Variables
                    </p>

                    {headerPlaceholders.map((ph) => (
                      <div key={ph}>
                        <label className="block text-[11px] font-semibold text-slate-600 dark:text-slate-400 mb-1">
                          Header variable {ph}:
                        </label>
                        <input
                          type="text"
                          className="w-full rounded-lg border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-900 px-3 py-1.5 text-xs text-slate-900 dark:text-white focus:border-emerald-500 focus:outline-none"
                          placeholder={`Value for ${ph}`}
                          value={paramValues[`header_${ph}`] || ''}
                          onChange={(e) =>
                            setParamValues((prev) => ({
                              ...prev,
                              [`header_${ph}`]: e.target.value,
                            }))
                          }
                        />
                      </div>
                    ))}

                    {bodyPlaceholders.map((ph, idx) => (
                      <div key={ph}>
                        <label className="block text-[11px] font-semibold text-slate-600 dark:text-slate-400 mb-1">
                          Body variable {ph} {idx === 0 ? '(e.g. Contact Name)' : ''}:
                        </label>
                        <input
                          type="text"
                          className="w-full rounded-lg border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-900 px-3 py-1.5 text-xs text-slate-900 dark:text-white focus:border-emerald-500 focus:outline-none"
                          placeholder={`Value for ${ph}`}
                          value={paramValues[`body_${ph}`] || ''}
                          onChange={(e) =>
                            setParamValues((prev) => ({
                              ...prev,
                              [`body_${ph}`]: e.target.value,
                            }))
                          }
                        />
                      </div>
                    ))}
                  </div>
                )}
              </div>
            ) : (
              <div>
                <label className="block text-xs font-bold text-slate-700 dark:text-slate-300 uppercase tracking-wider mb-1.5">
                  Message Text
                </label>
                <textarea
                  rows={4}
                  className="w-full rounded-xl border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-900 p-3 text-xs text-slate-900 dark:text-white shadow-xs focus:border-emerald-500 focus:outline-none focus:ring-1 focus:ring-emerald-500"
                  placeholder="Type your WhatsApp message..."
                  value={freeText}
                  onChange={(e) => setFreeText(e.target.value)}
                />
                <p className="mt-1 text-right text-[11px] text-slate-400">
                  {freeText.length}/4096 characters
                </p>
              </div>
            )}
          </div>

          {/* Right Live Preview Column */}
          <div className="lg:col-span-5 flex flex-col">
            <p className="text-xs font-bold text-slate-700 dark:text-slate-300 uppercase tracking-wider mb-1.5">
              Live Preview
            </p>
            <div className="flex-1 rounded-2xl bg-[#EFEAE2] dark:bg-[#0c1317] border border-slate-200 dark:border-slate-800 p-4 flex flex-col justify-between shadow-inner min-h-[280px]">
              {/* WhatsApp Simulated Chat Bubble */}
              <div className="max-w-[90%] self-end rounded-2xl rounded-tr-xs bg-[#D9FDD3] dark:bg-[#005c4b] p-3 text-xs text-slate-900 dark:text-emerald-50 shadow-sm space-y-1.5">
                {mode === 'TEMPLATE' ? (
                  <>
                    {previewHeader && (
                      <p className="font-bold text-xs border-b border-black/10 dark:border-white/10 pb-1">
                        {previewHeader}
                      </p>
                    )}
                    <p className="whitespace-pre-wrap leading-relaxed text-xs">
                      {previewBody || (
                        <span className="italic text-slate-500">No template selected</span>
                      )}
                    </p>
                    {footerComponent?.text && (
                      <p className="text-[10px] text-slate-500 dark:text-emerald-200/70 pt-0.5">
                        {footerComponent.text}
                      </p>
                    )}
                  </>
                ) : (
                  <p className="whitespace-pre-wrap leading-relaxed text-xs">
                    {freeText || <span className="italic text-slate-500">Your message text...</span>}
                  </p>
                )}
                <div className="flex items-center justify-end gap-1 text-[10px] text-slate-500 dark:text-emerald-200/70">
                  <span>Just now</span>
                  <FiCheck className="h-3 w-3" />
                </div>
              </div>

              {/* Template Buttons preview if any */}
              {mode === 'TEMPLATE' && buttonsComponent?.buttons && (
                <div className="mt-2 space-y-1 self-end max-w-[90%] w-full">
                  {buttonsComponent.buttons.map((btn, idx) => (
                    <div
                      key={idx}
                      className="rounded-lg bg-white/90 dark:bg-slate-800/90 py-1.5 px-3 text-center text-xs font-semibold text-emerald-700 dark:text-emerald-400 shadow-xs"
                    >
                      {btn.text}
                    </div>
                  ))}
                </div>
              )}

              <p className="mt-4 text-center text-[10px] text-slate-500 dark:text-slate-400">
                End-to-end encrypted · Logged to CRM Timeline
              </p>
            </div>
          </div>
        </div>
      )}

      {/* Footer Controls */}
      {!sentResult && context?.connected && (
        <div className="mt-6 flex items-center justify-between border-t border-slate-200 dark:border-slate-800 pt-4">
          <p className="text-[11px] text-slate-500">
            Sending as <span className="font-semibold">{context.connectionName}</span>
          </p>
          <div className="flex gap-2">
            <button
              type="button"
              className="secondary-button text-xs py-2 px-4"
              onClick={onClose}
              disabled={sending}
            >
              Cancel
            </button>
            <button
              type="button"
              onClick={handleSend}
              disabled={sending || (mode === 'TEMPLATE' && templates.length === 0)}
              className="primary-button bg-emerald-600 hover:bg-emerald-700 active:bg-emerald-800 text-white text-xs py-2 px-5 font-semibold flex items-center gap-2 shadow-sm transition-all"
            >
              {sending ? (
                <>
                  <div className="h-3.5 w-3.5 animate-spin rounded-full border-2 border-white border-t-transparent" />
                  Sending...
                </>
              ) : (
                <>
                  <FiSend className="h-3.5 w-3.5" />
                  Send WhatsApp
                </>
              )}
            </button>
          </div>
        </div>
      )}
    </WorkspaceDialog>
  )
}
