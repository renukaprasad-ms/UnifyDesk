import { useEffect, useMemo, useState } from 'react'
import { FiCode, FiSave, FiSend, FiX } from 'react-icons/fi'
import { emailTemplateApi } from '../../features/emailTemplate/emailTemplateApi'
import type {
  EmailBlock,
  EmailDocument,
  EmailTemplate,
  EmailTemplateCategory,
  EmailTemplateEditorMode,
  EmailTemplatePurpose,
  SaveEmailTemplateInput,
} from '../../features/emailTemplate/types'
import { mediaApi } from '../../features/media/mediaApi'
import { apiErrorMessage } from '../../lib/api/apiClient'
import { useSnackbar } from '../feedback/SnackbarProvider'
import EmailBlockEditor from './EmailBlockEditor'
import EmailTemplatePreview from './EmailTemplatePreview'

type Props = {
  companyId: string
  template?: EmailTemplate | null
  initialMode?: EmailTemplateEditorMode
  onClose: () => void
  onSaved: (template: EmailTemplate) => void
}

const categories: EmailTemplateCategory[] = [
  'PROMOTIONAL',
  'NEWSLETTER',
  'WELCOME',
  'PRODUCT_LAUNCH',
  'EVENT',
  'ANNOUNCEMENT',
  'SEASONAL',
  'MINIMAL',
]

const withIds = (blocks: EmailBlock[]): EmailBlock[] =>
  blocks.map((block) => ({ ...block, id: block.id || crypto.randomUUID() }) as EmailBlock)

const defaultDocument = (): EmailDocument => ({
  schemaVersion: 1,
  settings: {
    width: 600,
    backgroundColor: '#f1f5f9',
    contentBackgroundColor: '#ffffff',
    accentColor: '#4f46e5',
  },
  blocks: withIds([
    { id: '', type: 'HEADER', brandText: 'YOUR BRAND' },
    {
      id: '',
      type: 'HERO',
      heading: 'A message worth opening',
      text: 'Hi {{first_name}}, write a clear and useful introduction here.',
    },
    { id: '', type: 'TEXT', text: 'Add the main campaign content here.' },
    { id: '', type: 'BUTTON', text: 'Learn more', url: '{{action_url}}' },
    { id: '', type: 'FOOTER', text: 'Your company · Your address' },
  ]),
})

const sampleValue = (variable: string) => {
  const key = variable.toLowerCase()
  if (key === 'first_name') return 'Renuka'
  if (key === 'last_name') return 'Prasad'
  if (key === 'full_name' || key === 'name') return 'Renuka Prasad'
  if (key === 'email') return 'renuka@example.com'
  if (key === 'company_name' || key === 'company') return 'Example Company'
  if (key.includes('url') || key.includes('link')) return 'https://example.com'
  if (key.includes('date')) return '30 September 2026'
  if (key.includes('discount')) return '20'
  return 'Sample value'
}

const extractVariables = (...values: Array<string | null | undefined>) => {
  const variables = new Set<string>()
  const pattern = /\{\{\s*([A-Za-z0-9_.-]+)\s*}}/g
  values.forEach((value) => {
    if (!value) return
    for (const match of value.matchAll(pattern)) variables.add(match[1])
  })
  return Array.from(variables)
}

const parseDocument = (value?: string | null): EmailDocument => {
  if (!value) return defaultDocument()
  try {
    const parsed = JSON.parse(value) as Partial<EmailDocument>
    if (!Array.isArray(parsed.blocks) || parsed.blocks.length === 0) return defaultDocument()
    return {
      schemaVersion: 1,
      settings: {
        width: parsed.settings?.width ?? 600,
        backgroundColor: parsed.settings?.backgroundColor ?? '#f1f5f9',
        contentBackgroundColor: parsed.settings?.contentBackgroundColor ?? '#ffffff',
        accentColor: parsed.settings?.accentColor ?? '#4f46e5',
      },
      blocks: withIds(parsed.blocks as EmailBlock[]),
    }
  } catch {
    return defaultDocument()
  }
}

export default function EmailTemplateEditorModal({
  companyId,
  template,
  initialMode = 'BUILDER',
  onClose,
  onSaved,
}: Props) {
  const snackbar = useSnackbar()
  const [mode, setMode] = useState<EmailTemplateEditorMode>(
    template?.editorMode ?? initialMode,
  )
  const [name, setName] = useState(template?.name ?? '')
  const [category, setCategory] = useState<EmailTemplateCategory>(
    template?.category ?? 'PROMOTIONAL',
  )
  const [purpose, setPurpose] = useState<EmailTemplatePurpose>(
    template?.purpose ?? 'MARKETING',
  )
  const [subject, setSubject] = useState(template?.subject ?? '')
  const [preheader, setPreheader] = useState(template?.preheader ?? '')
  const [htmlSource, setHtmlSource] = useState(
    template?.htmlSource ??
      '<!doctype html><html><body><h1>Hello {{first_name}}</h1><p>Write your campaign here.</p></body></html>',
  )
  const [document, setDocument] = useState<EmailDocument>(() =>
    parseDocument(template?.documentJson),
  )
  const [previewHtml, setPreviewHtml] = useState(template?.compiledHtml ?? '')
  const [previewValues, setPreviewValues] = useState<Record<string, string>>({})
  const [busy, setBusy] = useState(false)
  const [uploadingBlockId, setUploadingBlockId] = useState<string | null>(null)

  const variables = useMemo(
    () =>
      extractVariables(
        subject,
        preheader,
        mode === 'BUILDER' ? JSON.stringify(document) : htmlSource,
      ),
    [document, htmlSource, mode, preheader, subject],
  )

  useEffect(() => {
    setPreviewValues((current) => {
      const next: Record<string, string> = {}
      variables.forEach((variable) => {
        next[variable] = current[variable] ?? sampleValue(variable)
      })
      return next
    })
  }, [variables])

  const input = useMemo<SaveEmailTemplateInput>(
    () => ({
      name: name.trim(),
      category,
      purpose,
      editorMode: mode,
      subject: subject.trim() || null,
      preheader: preheader.trim() || null,
      documentJson: mode === 'BUILDER' ? JSON.stringify(document) : null,
      htmlSource: mode === 'HTML' ? htmlSource : null,
      previewValues,
    }),
    [category, document, htmlSource, mode, name, preheader, previewValues, purpose, subject],
  )

  const preview = async () => {
    if (!name.trim()) {
      snackbar.error('Give the template a name first.')
      return
    }
    setBusy(true)
    try {
      const result = await emailTemplateApi.preview(companyId, input)
      setPreviewHtml(result.html)
    } catch (error) {
      snackbar.error(apiErrorMessage(error, 'Unable to compile preview'))
    } finally {
      setBusy(false)
    }
  }

  const save = async (publish: boolean) => {
    if (!name.trim()) {
      snackbar.error('Template name is required.')
      return
    }
    setBusy(true)
    try {
      let saved = template
        ? await emailTemplateApi.update(companyId, template.id, input)
        : await emailTemplateApi.create(companyId, input)
      if (publish) saved = await emailTemplateApi.publish(companyId, saved.id)
      snackbar.success(publish ? 'Email template published.' : 'Draft saved.')
      onSaved(saved)
    } catch (error) {
      snackbar.error(apiErrorMessage(error, 'Unable to save email template'))
    } finally {
      setBusy(false)
    }
  }

  const uploadImage = async (
    blockId: string,
    field: 'logoUrl' | 'imageUrl',
    file: File,
  ) => {
    setUploadingBlockId(blockId)
    try {
      const uploaded = await mediaApi.uploadTemplateMedia(file, companyId)
      const asset = await emailTemplateApi.publishAsset(companyId, uploaded.mediaId)
      setDocument((current) => ({
        ...current,
        blocks: current.blocks.map((block) =>
          block.id === blockId ? ({ ...block, [field]: asset.publicUrl } as EmailBlock) : block,
        ),
      }))
      snackbar.success('Image added to the template.')
    } catch (error) {
      snackbar.error(apiErrorMessage(error, 'Unable to upload image'))
    } finally {
      setUploadingBlockId(null)
    }
  }

  return (
    <div className="mobile-modal-shell fixed inset-0 z-50 flex items-center justify-center bg-slate-950/45 p-3">
      <div className="mobile-sheet max-h-[96vh] w-full max-w-7xl overflow-y-auto rounded-2xl bg-white shadow-2xl">
        <header className="sticky top-0 z-10 flex items-center justify-between border-b border-slate-200 bg-white px-5 py-4">
          <div>
            <p className="text-base font-bold text-slate-950">
              {template ? 'Edit email template' : 'Create email template'}
            </p>
            <p className="mt-1 text-xs text-slate-500">
              Save drafts freely. Publishing creates the immutable version Broadcast can use.
            </p>
          </div>
          <button type="button" className="admin-back-button" onClick={onClose} aria-label="Close">
            <FiX />
          </button>
        </header>

        <div className="grid gap-0 xl:grid-cols-[minmax(0,1fr)_520px]">
          <div className="p-5">
            <div className="grid gap-4 sm:grid-cols-2">
              <label className="text-xs font-semibold text-slate-700">
                Template name
                <input
                  className="form-input mt-2"
                  value={name}
                  onChange={(event) => setName(event.target.value)}
                  maxLength={160}
                />
              </label>
              <label className="text-xs font-semibold text-slate-700">
                Category
                <select
                  className="form-input mt-2"
                  value={category}
                  onChange={(event) => setCategory(event.target.value as EmailTemplateCategory)}
                >
                  {categories.map((item) => (
                    <option key={item} value={item}>
                      {item.toLowerCase().replaceAll('_', ' ')}
                    </option>
                  ))}
                </select>
              </label>
              <label className="text-xs font-semibold text-slate-700 sm:col-span-2">
                Delivery purpose
                <select
                  className="form-input mt-2"
                  value={purpose}
                  onChange={(event) => setPurpose(event.target.value as EmailTemplatePurpose)}
                >
                  <option value="MARKETING">Marketing — offers, newsletters, campaigns</option>
                  <option value="TRANSACTIONAL">Transactional — expected service/account messages</option>
                </select>
                <span className="mt-1 block text-[10px] font-medium leading-4 text-slate-400">
                  MSG91 Email can only be synced when this template is genuinely transactional.
                </span>
              </label>

              <label className="text-xs font-semibold text-slate-700 sm:col-span-2">
                Subject
                <input
                  className="form-input mt-2"
                  value={subject}
                  onChange={(event) => setSubject(event.target.value)}
                  placeholder="A special offer for {{first_name}}"
                  maxLength={998}
                />
              </label>
              <label className="text-xs font-semibold text-slate-700 sm:col-span-2">
                Preheader
                <input
                  className="form-input mt-2"
                  value={preheader}
                  onChange={(event) => setPreheader(event.target.value)}
                  placeholder="Short inbox preview text"
                  maxLength={255}
                />
              </label>
            </div>

            <div className="mt-5 flex rounded-xl bg-slate-100 p-1">
              <button
                type="button"
                className={`flex-1 rounded-lg px-3 py-2 text-xs font-bold ${
                  mode === 'BUILDER' ? 'bg-white text-indigo-700 shadow-sm' : 'text-slate-500'
                }`}
                onClick={() => setMode('BUILDER')}
              >
                Visual builder
              </button>
              <button
                type="button"
                className={`flex-1 rounded-lg px-3 py-2 text-xs font-bold ${
                  mode === 'HTML' ? 'bg-white text-indigo-700 shadow-sm' : 'text-slate-500'
                }`}
                onClick={() => setMode('HTML')}
              >
                <FiCode className="mr-1 inline" /> Paste HTML
              </button>
            </div>

            {mode === 'HTML' ? (
              <div className="mt-5">
                <p className="text-xs font-bold text-slate-800">HTML source</p>
                <p className="mt-1 text-[10px] leading-4 text-slate-500">
                  Scripts, forms, iframes, event handlers and unsafe URL schemes are rejected.
                </p>
                <textarea
                  className="form-input mt-3 min-h-[520px] font-mono text-[11px]"
                  value={htmlSource}
                  onChange={(event) => setHtmlSource(event.target.value)}
                  spellCheck={false}
                />
              </div>
            ) : (
              <div className="mt-5">
                <EmailBlockEditor
                  document={document}
                  uploadingBlockId={uploadingBlockId}
                  onChange={setDocument}
                  onUploadImage={(blockId, field, file) =>
                    void uploadImage(blockId, field, file)
                  }
                />
              </div>
            )}

            <div className="mt-6 flex flex-wrap justify-end gap-2 border-t border-slate-100 pt-5">
              <button
                type="button"
                className="secondary-button"
                disabled={busy}
                onClick={() => void preview()}
              >
                Preview
              </button>
              <button
                type="button"
                className="secondary-button inline-flex items-center gap-2"
                disabled={busy}
                onClick={() => void save(false)}
              >
                <FiSave /> Save draft
              </button>
              <button
                type="button"
                className="primary-button inline-flex items-center gap-2"
                disabled={busy}
                onClick={() => void save(true)}
              >
                <FiSend /> Publish
              </button>
            </div>
          </div>

          <aside className="border-l border-slate-200 bg-slate-50 p-5">
            <EmailTemplatePreview
              html={
                previewHtml ||
                '<div style="padding:32px;font-family:Arial">Click Preview to render this email.</div>'
              }
            />
            <div className="mt-4 rounded-xl border border-slate-200 bg-white p-4">
              <p className="text-[10px] font-bold uppercase tracking-[0.08em] text-slate-400">
                Preview variables
              </p>
              <div className="mt-3 grid gap-2">
                {variables.length === 0 && (
                  <p className="text-[10px] text-slate-400">No variables detected.</p>
                )}
                {variables.map((key) => {
                  const value = previewValues[key] ?? ''
                  return (
                  <label
                    key={key}
                    className="grid grid-cols-[120px_1fr] items-center gap-2 text-[10px] text-slate-500"
                  >
                    <span>{'{{'}{key}{'}}'}</span>
                    <input
                      className="form-input !py-1.5 text-[11px]"
                      value={value}
                      onChange={(event) =>
                        setPreviewValues((current) => ({
                          ...current,
                          [key]: event.target.value,
                        }))
                      }
                    />
                  </label>
                  )
                })}
              </div>
            </div>
          </aside>
        </div>
      </div>
    </div>
  )
}
