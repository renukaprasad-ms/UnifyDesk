import { useMemo, useState, type FormEvent } from 'react'
import {
  FiCheck,
  FiMail,
  FiMessageSquare,
  FiPlus,
  FiSmartphone,
  FiTrash2,
  FiX,
} from 'react-icons/fi'
import type { ChannelType } from '../../features/channel/types'
import type {
  MessageTemplate,
  MessageTemplateButton,
  MessageTemplateCategory,
  SaveMessageTemplateInput,
} from '../../features/template/types'

type Props = {
  channelType: ChannelType
  template?: MessageTemplate | null
  initialCategory?: MessageTemplateCategory
  creationFlow?: boolean
  busy?: boolean
  onBack?: () => void
  onClose: () => void
  onSave: (input: SaveMessageTemplateInput, activateAfterSave: boolean) => void
}

const categoriesFor = (channelType: ChannelType): MessageTemplateCategory[] =>
  channelType === 'WHATSAPP'
    ? ['MARKETING', 'UTILITY', 'AUTHENTICATION']
    : ['MARKETING', 'TRANSACTIONAL', 'UTILITY']

const defaultBody = (channelType: ChannelType) => {
  if (channelType === 'EMAIL') {
    return 'Hi {{first_name}},\n\nWrite your email message here.'
  }
  if (channelType === 'WHATSAPP') {
    return 'Hi {{1}}, thanks for choosing {{2}}.'
  }
  return 'Hi {{first_name}}, write your SMS message here.'
}

const channelIcon = (channelType: ChannelType) =>
  channelType === 'EMAIL' ? (
    <FiMail aria-hidden="true" />
  ) : channelType === 'WHATSAPP' ? (
    <FiMessageSquare aria-hidden="true" />
  ) : (
    <FiSmartphone aria-hidden="true" />
  )

export default function MessageTemplateEditorModal({
  channelType,
  template,
  initialCategory,
  creationFlow = false,
  busy = false,
  onBack,
  onClose,
  onSave,
}: Props) {
  const [name, setName] = useState(template?.name ?? '')
  const [category, setCategory] = useState<MessageTemplateCategory>(
    template?.category ?? initialCategory ?? categoriesFor(channelType)[0],
  )
  const [language, setLanguage] = useState(
    template?.language ?? (channelType === 'WHATSAPP' ? 'en_US' : 'en'),
  )
  const [subject, setSubject] = useState(template?.subject ?? '')
  const [headerText, setHeaderText] = useState(template?.headerText ?? '')
  const [bodyText, setBodyText] = useState(template?.bodyText ?? defaultBody(channelType))
  const [bodyHtml, setBodyHtml] = useState(template?.bodyHtml ?? '')
  const [footerText, setFooterText] = useState(template?.footerText ?? '')
  const [buttons, setButtons] = useState<MessageTemplateButton[]>(template?.buttons ?? [])
  const [activateAfterSave, setActivateAfterSave] = useState(
    channelType !== 'WHATSAPP' && template?.status !== 'DRAFT' ? true : channelType !== 'WHATSAPP',
  )

  const variables = useMemo(() => {
    const matches = new Set<string>()
    const matcher = /\{\{\s*([A-Za-z0-9_.-]+)\s*}}/g
    ;[subject, headerText, bodyText, bodyHtml, footerText].forEach((value) => {
      let match: RegExpExecArray | null
      while ((match = matcher.exec(value)) !== null) matches.add(match[1])
      matcher.lastIndex = 0
    })
    return Array.from(matches)
  }, [bodyHtml, bodyText, footerText, headerText, subject])

  const submit = (event: FormEvent) => {
    event.preventDefault()
    onSave(
      {
        name: name.trim(),
        channelType,
        category,
        language: language.trim() || null,
        subject: channelType === 'EMAIL' ? subject.trim() || null : null,
        headerText: channelType === 'WHATSAPP' ? headerText.trim() || null : null,
        bodyText: bodyText.trim(),
        bodyHtml: channelType === 'EMAIL' ? bodyHtml.trim() || null : null,
        footerText: channelType === 'WHATSAPP' ? footerText.trim() || null : null,
        buttons: channelType === 'WHATSAPP' ? buttons : [],
      },
      activateAfterSave,
    )
  }

  const addButton = () => {
    if (buttons.length >= 3) return
    setButtons((current) => [...current, { type: 'QUICK_REPLY', text: '' }])
  }

  const updateButton = (index: number, patch: Partial<MessageTemplateButton>) => {
    setButtons((current) =>
      current.map((button, buttonIndex) =>
        buttonIndex === index ? { ...button, ...patch } : button,
      ),
    )
  }

  const removeButton = (index: number) => {
    setButtons((current) => current.filter((_, buttonIndex) => buttonIndex !== index))
  }

  return (
    <div className="mobile-modal-shell fixed inset-0 z-50 flex items-center justify-center bg-slate-950/40 p-4">
      <form
        className="mobile-sheet max-h-[94vh] w-full max-w-5xl overflow-y-auto rounded-2xl bg-white p-5 shadow-xl"
        onSubmit={submit}
      >
        <div className="flex items-start justify-between gap-4">
          <div className="flex items-start gap-3">
            {creationFlow && onBack && (
              <button
                type="button"
                className="admin-back-button"
                onClick={onBack}
                aria-label="Back"
              >
                ←
              </button>
            )}
            <span className="flex size-10 shrink-0 items-center justify-center rounded-xl bg-indigo-50 text-indigo-600">
              {channelIcon(channelType)}
            </span>
            <div>
              <p className="text-base font-bold text-slate-950">
                {template ? 'Edit template' : creationFlow ? 'Build template' : 'Create template'}
              </p>
              <p className="mt-1 text-xs text-slate-500">
                {creationFlow && !template ? 'Step 3 of 4 · Build content and preview' : ''}{creationFlow && !template ? ' · ' : ''}
                {channelType === 'EMAIL'
                  ? 'Reusable email subject and message content.'
                  : channelType === 'WHATSAPP'
                    ? 'Create the local WhatsApp template definition. Meta approval is handled separately.'
                    : 'Reusable SMS copy for Broadcast and provider flows.'}
              </p>
            </div>
          </div>
          <button type="button" className="admin-back-button" onClick={onClose} aria-label="Close">
            <FiX aria-hidden="true" />
          </button>
        </div>

        {creationFlow && !template && (
          <div className="mt-5 h-1.5 overflow-hidden rounded-full bg-slate-100">
            <div className="h-full w-3/4 rounded-full bg-indigo-500" />
          </div>
        )}

        <div className="mt-6 grid gap-6 lg:grid-cols-[minmax(0,1fr)_320px]">
          <div>
            <div className="grid gap-4 sm:grid-cols-2">
              <label className="block text-xs font-semibold text-slate-700">
                Template name
                <input
                  className="form-input mt-2"
                  value={name}
                  onChange={(event) => setName(event.target.value)}
                  placeholder="September offer"
                  maxLength={160}
                  required
                />
              </label>

              <label className="block text-xs font-semibold text-slate-700">
                Category
                <select
                  className="form-input mt-2"
                  value={category}
                  onChange={(event) =>
                    setCategory(event.target.value as MessageTemplateCategory)
                  }
                >
                  {categoriesFor(channelType).map((value) => (
                    <option key={value} value={value}>
                      {value.toLowerCase().replaceAll('_', ' ')}
                    </option>
                  ))}
                </select>
              </label>

              <label className="block text-xs font-semibold text-slate-700">
                Language
                <input
                  className="form-input mt-2"
                  value={language}
                  onChange={(event) => setLanguage(event.target.value)}
                  placeholder={channelType === 'WHATSAPP' ? 'en_US' : 'en'}
                  maxLength={32}
                />
              </label>

              {channelType === 'EMAIL' && (
                <label className="block text-xs font-semibold text-slate-700">
                  Subject
                  <input
                    className="form-input mt-2"
                    value={subject}
                    onChange={(event) => setSubject(event.target.value)}
                    placeholder="Hello {{first_name}}"
                    maxLength={998}
                  />
                </label>
              )}
            </div>

            {channelType === 'WHATSAPP' && (
              <label className="mt-4 block text-xs font-semibold text-slate-700">
                Header text
                <input
                  className="form-input mt-2"
                  value={headerText}
                  onChange={(event) => setHeaderText(event.target.value)}
                  placeholder="Optional header"
                  maxLength={60}
                />
              </label>
            )}

            <label className="mt-4 block text-xs font-semibold text-slate-700">
              {channelType === 'SMS' ? 'SMS message' : 'Message body'}
              <textarea
                className="form-input mt-2 min-h-44 resize-y"
                value={bodyText}
                onChange={(event) => setBodyText(event.target.value)}
                maxLength={channelType === 'EMAIL' ? 500000 : 4096}
                required
              />
              <span className="mt-1 block text-[10px] font-medium text-slate-400">
                Variables use double braces, for example {'{{first_name}}'}.
              </span>
            </label>

            {channelType === 'EMAIL' && (
              <label className="mt-4 block text-xs font-semibold text-slate-700">
                HTML version
                <textarea
                  className="form-input mt-2 min-h-36 resize-y font-mono text-[11px]"
                  value={bodyHtml}
                  onChange={(event) => setBodyHtml(event.target.value)}
                  placeholder="<p>Hi {{first_name}},</p>"
                  maxLength={1000000}
                />
                <span className="mt-1 block text-[10px] font-medium text-slate-400">
                  Optional for now. The Broadcast transport currently uses the text body; HTML is stored for the rich-email phase.
                </span>
              </label>
            )}

            {channelType === 'WHATSAPP' && (
              <>
                <label className="mt-4 block text-xs font-semibold text-slate-700">
                  Footer text
                  <input
                    className="form-input mt-2"
                    value={footerText}
                    onChange={(event) => setFooterText(event.target.value)}
                    placeholder="Optional footer"
                    maxLength={60}
                  />
                </label>

                <div className="mt-5">
                  <div className="flex items-center justify-between gap-3">
                    <div>
                      <p className="text-xs font-bold text-slate-800">Buttons</p>
                      <p className="mt-1 text-[10px] text-slate-500">Up to 3 buttons in this editor.</p>
                    </div>
                    <button
                      type="button"
                      className="secondary-button inline-flex items-center gap-2"
                      onClick={addButton}
                      disabled={buttons.length >= 3}
                    >
                      <FiPlus aria-hidden="true" />
                      Add button
                    </button>
                  </div>

                  <div className="mt-3 grid gap-3">
                    {buttons.map((button, index) => (
                      <div key={index} className="rounded-xl border border-slate-200 p-3">
                        <div className="grid gap-3 sm:grid-cols-[140px_1fr_auto]">
                          <select
                            className="form-input"
                            value={button.type}
                            onChange={(event) =>
                              updateButton(index, {
                                type: event.target.value as MessageTemplateButton['type'],
                                url: null,
                                phoneNumber: null,
                              })
                            }
                          >
                            <option value="QUICK_REPLY">Quick reply</option>
                            <option value="URL">URL</option>
                            <option value="PHONE_NUMBER">Phone</option>
                          </select>
                          <input
                            className="form-input"
                            value={button.text}
                            onChange={(event) => updateButton(index, { text: event.target.value })}
                            placeholder="Button text"
                            maxLength={25}
                            required
                          />
                          <button
                            type="button"
                            className="admin-back-button text-rose-500"
                            onClick={() => removeButton(index)}
                            aria-label="Remove button"
                          >
                            <FiTrash2 aria-hidden="true" />
                          </button>
                        </div>

                        {button.type === 'URL' && (
                          <input
                            className="form-input mt-3"
                            value={button.url ?? ''}
                            onChange={(event) => updateButton(index, { url: event.target.value })}
                            placeholder="https://example.com"
                            required
                          />
                        )}
                        {button.type === 'PHONE_NUMBER' && (
                          <input
                            className="form-input mt-3"
                            value={button.phoneNumber ?? ''}
                            onChange={(event) =>
                              updateButton(index, { phoneNumber: event.target.value })
                            }
                            placeholder="+919876543210"
                            required
                          />
                        )}
                      </div>
                    ))}
                  </div>
                </div>
              </>
            )}

            {channelType !== 'WHATSAPP' && (
              <label className="mt-5 flex items-start gap-3 rounded-xl border border-slate-200 p-3">
                <input
                  type="checkbox"
                  className="mt-0.5"
                  checked={activateAfterSave}
                  onChange={(event) => setActivateAfterSave(event.target.checked)}
                />
                <span>
                  <span className="block text-xs font-bold text-slate-800">
                    Available for Broadcast immediately
                  </span>
                  <span className="mt-1 block text-[10px] leading-4 text-slate-500">
                    Active Email and SMS templates appear in the Broadcast composer.
                  </span>
                </span>
              </label>
            )}

            {channelType === 'WHATSAPP' && (
              <div className="mt-5 rounded-xl border border-amber-200 bg-amber-50 p-3 text-xs leading-5 text-amber-800">
                WhatsApp templates stay as local drafts until we submit them to Meta and receive an approved status.
              </div>
            )}
          </div>

          <aside>
            <div className="sticky top-0 rounded-2xl border border-slate-200 bg-slate-50/70 p-4">
              <p className="text-xs font-bold uppercase tracking-[0.08em] text-slate-400">Preview</p>

              {channelType === 'EMAIL' ? (
                <div className="mt-3 rounded-xl border border-slate-200 bg-white p-4">
                  <p className="text-[10px] font-semibold uppercase text-slate-400">Subject</p>
                  <p className="mt-1 text-sm font-bold text-slate-900">
                    {subject || '(no subject)'}
                  </p>
                  <div className="mt-4 whitespace-pre-wrap text-xs leading-5 text-slate-600">
                    {bodyText || 'Your email content appears here.'}
                  </div>
                </div>
              ) : channelType === 'WHATSAPP' ? (
                <div className="mt-3 rounded-2xl bg-[#efeae2] p-4">
                  <div className="ml-auto max-w-[260px] rounded-xl bg-white p-3 shadow-sm">
                    {headerText && <p className="text-xs font-bold text-slate-900">{headerText}</p>}
                    <p className="mt-1 whitespace-pre-wrap text-xs leading-5 text-slate-700">
                      {bodyText || 'WhatsApp message'}
                    </p>
                    {footerText && <p className="mt-2 text-[10px] text-slate-400">{footerText}</p>}
                    {buttons.map((button, index) => (
                      <div
                        key={index}
                        className="mt-2 border-t border-slate-100 pt-2 text-center text-[11px] font-semibold text-sky-600"
                      >
                        {button.text || 'Button'}
                      </div>
                    ))}
                  </div>
                </div>
              ) : (
                <div className="mt-3 rounded-2xl border border-slate-200 bg-white p-4">
                  <div className="rounded-xl bg-slate-100 p-3 text-xs leading-5 text-slate-700">
                    {bodyText || 'SMS message'}
                  </div>
                  <p className="mt-2 text-[10px] text-slate-400">{bodyText.length} characters</p>
                </div>
              )}

              <div className="mt-4">
                <p className="text-[10px] font-bold uppercase tracking-[0.08em] text-slate-400">
                  Variables
                </p>
                <div className="mt-2 flex flex-wrap gap-1.5">
                  {variables.length === 0 ? (
                    <span className="text-[10px] text-slate-400">No variables detected</span>
                  ) : (
                    variables.map((variable) => (
                      <span
                        key={variable}
                        className="rounded-full bg-indigo-50 px-2 py-1 text-[10px] font-semibold text-indigo-700"
                      >
                        {'{{'}{variable}{'}}'}
                      </span>
                    ))
                  )}
                </div>
              </div>
            </div>
          </aside>
        </div>

        <div className="mt-6 flex flex-wrap justify-end gap-2 border-t border-slate-100 pt-5">
          <button type="button" className="secondary-button" onClick={onClose} disabled={busy}>
            Cancel
          </button>
          <button type="submit" className="primary-button inline-flex items-center gap-2" disabled={busy}>
            <FiCheck aria-hidden="true" />
            {busy ? 'Saving…' : template ? 'Save changes' : 'Save template'}
          </button>
        </div>
      </form>
    </div>
  )
}
