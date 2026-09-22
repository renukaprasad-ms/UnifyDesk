import { useEffect, useMemo, useState } from 'react'
import {
  FiArrowDown,
  FiArrowUp,
  FiImage,
  FiLink,
  FiMinus,
  FiMonitor,
  FiMove,
  FiPlus,
  FiSmartphone,
  FiTrash2,
  FiType,
} from 'react-icons/fi'
import { mediaApi } from '../../features/media/mediaApi'
import {
  createEmailBlock,
  emailMediaIds,
  generateEmailOutputs,
  type EmailDesignBlock,
  type EmailBlockType,
  type EmailTemplateDesign,
} from '../../features/template/emailDesign'
import TemplateMediaPickerModal, {
  type TemplateMediaSelection,
} from './TemplateMediaPickerModal'

type Props = {
  companyId: string
  subject: string
  design: EmailTemplateDesign
  onChange: (design: EmailTemplateDesign) => void
  onGeneratedChange: (bodyText: string, bodyHtml: string) => void
}

const variableOptions = [
  ['first_name', 'First name'],
  ['last_name', 'Last name'],
  ['full_name', 'Full name'],
  ['email', 'Email'],
  ['company_name', 'Company name'],
  ['current_date', 'Current date'],
  ['current_year', 'Current year'],
  ['discount', 'Discount'],
  ['expiry_date', 'Expiry date'],
  ['offer_url', 'Offer URL'],
] as const

const variableValue = (key: string) => {
  if (key === 'first_name') return 'Renuka'
  if (key === 'last_name') return 'Prasad'
  if (key === 'full_name') return 'Renuka Prasad'
  if (key === 'email') return 'renuka@example.com'
  if (key === 'company_name') return 'Example Company'
  if (key === 'current_date') return '22 September 2026'
  if (key === 'current_year') return '2026'
  if (key === 'discount') return '20'
  if (key === 'expiry_date') return '30 September'
  if (key === 'offer_url') return 'https://example.com/offers'
  if (key === 'unsubscribe_url') return 'https://example.com/unsubscribe'
  return 'Sample value'
}

const replaceVariables = (value: string, values: Record<string, string>) =>
  value.replace(/{{s*([A-Za-z0-9_.-]+)s*}}/g, (token, variable: string) =>
    values[variable]?.trim() ? values[variable] : token,
  )

const blockName = (type: EmailBlockType) => {
  if (type === 'HEADING') return 'Heading'
  if (type === 'TEXT') return 'Text'
  if (type === 'IMAGE') return 'Image'
  if (type === 'BUTTON') return 'Button'
  if (type === 'DIVIDER') return 'Divider'
  return 'Spacer'
}

export default function EmailTemplateVisualBuilder({
  companyId,
  subject,
  design,
  onChange,
  onGeneratedChange,
}: Props) {
  const [mediaUrls, setMediaUrls] = useState<Record<string, string>>({})
  const [mediaTarget, setMediaTarget] = useState<'HEADER' | string | null>(null)
  const [previewDevice, setPreviewDevice] = useState<'DESKTOP' | 'MOBILE'>('DESKTOP')
  const [previewValues, setPreviewValues] = useState<Record<string, string>>({})

  useEffect(() => {
    const ids = emailMediaIds(design).filter((id) => !mediaUrls[id])
    if (ids.length === 0) return

    let active = true
    void Promise.all(
      ids.map(async (mediaId) => {
        try {
          const access = await mediaApi.access(mediaId)
          return [mediaId, access.url] as const
        } catch {
          return null
        }
      }),
    ).then((results) => {
      if (!active) return
      setMediaUrls((current) => {
        const next = { ...current }
        results.forEach((result) => {
          if (result) next[result[0]] = result[1]
        })
        return next
      })
    })

    return () => {
      active = false
    }
  }, [design, mediaUrls])

  const storedOutputs = useMemo(() => generateEmailOutputs(design), [design])
  const previewOutputs = useMemo(
    () => generateEmailOutputs(design, mediaUrls),
    [design, mediaUrls],
  )

  useEffect(() => {
    onGeneratedChange(storedOutputs.bodyText, storedOutputs.bodyHtml)
  }, [onGeneratedChange, storedOutputs.bodyHtml, storedOutputs.bodyText])

  const variables = useMemo(() => {
    const found = new Set<string>()
    const matcher = /{{s*([A-Za-z0-9_.-]+)s*}}/g
    const values = [
      subject,
      storedOutputs.bodyText,
      storedOutputs.bodyHtml,
      design.header.heading ?? '',
      design.footer.companyName ?? '',
      design.footer.address ?? '',
      design.footer.privacyUrl ?? '',
    ]
    values.forEach((value) => {
      let match: RegExpExecArray | null
      while ((match = matcher.exec(value)) !== null) found.add(match[1])
      matcher.lastIndex = 0
    })
    return Array.from(found)
  }, [design, storedOutputs.bodyHtml, storedOutputs.bodyText, subject])

  useEffect(() => {
    setPreviewValues((current) => {
      const next: Record<string, string> = {}
      variables.forEach((variable) => {
        next[variable] = current[variable] ?? variableValue(variable)
      })
      return next
    })
  }, [variables])

  const previewHtml = useMemo(
    () => replaceVariables(previewOutputs.bodyHtml, previewValues),
    [previewOutputs.bodyHtml, previewValues],
  )
  const previewSubject = useMemo(
    () => replaceVariables(subject, previewValues),
    [previewValues, subject],
  )

  const patchDesign = (patch: Partial<EmailTemplateDesign>) => {
    onChange({ ...design, ...patch })
  }

  const patchHeader = (patch: Partial<EmailTemplateDesign['header']>) => {
    onChange({ ...design, header: { ...design.header, ...patch } })
  }

  const patchFooter = (patch: Partial<EmailTemplateDesign['footer']>) => {
    onChange({ ...design, footer: { ...design.footer, ...patch } })
  }

  const updateBlock = (id: string, patch: Partial<EmailDesignBlock>) => {
    onChange({
      ...design,
      blocks: design.blocks.map((block) =>
        block.id === id ? { ...block, ...patch } : block,
      ),
    })
  }

  const removeBlock = (id: string) => {
    onChange({ ...design, blocks: design.blocks.filter((block) => block.id !== id) })
  }

  const moveBlock = (index: number, direction: -1 | 1) => {
    const target = index + direction
    if (target < 0 || target >= design.blocks.length) return
    const blocks = [...design.blocks]
    ;[blocks[index], blocks[target]] = [blocks[target], blocks[index]]
    onChange({ ...design, blocks })
  }

  const addBlock = (type: EmailBlockType) => {
    onChange({ ...design, blocks: [...design.blocks, createEmailBlock(type)] })
  }

  const appendVariable = (
    block: EmailDesignBlock,
    variable: string,
    target: 'TEXT' | 'URL' = 'TEXT',
  ) => {
    if (!variable) return
    const token = `{{${variable}}}`
    if (block.type === 'BUTTON' && target === 'URL') {
      updateBlock(block.id, {
        buttonUrl: `${block.buttonUrl ?? ''}${block.buttonUrl ? ' ' : ''}${token}`,
      })
      return
    }
    if (block.type === 'BUTTON') {
      updateBlock(block.id, {
        buttonText: `${block.buttonText ?? ''}${block.buttonText ? ' ' : ''}${token}`,
      })
      return
    }
    updateBlock(block.id, {
      text: `${block.text ?? ''}${block.text ? ' ' : ''}${token}`,
    })
  }

  const chooseMedia = (selection: TemplateMediaSelection) => {
    setMediaUrls((current) => ({ ...current, [selection.mediaId]: selection.url }))
    if (mediaTarget === 'HEADER') {
      patchHeader({ logoMediaId: selection.mediaId })
    } else if (mediaTarget) {
      updateBlock(mediaTarget, { mediaId: selection.mediaId })
    }
    setMediaTarget(null)
  }

  return (
    <>
      <div className="mt-5 grid gap-5 xl:grid-cols-[minmax(0,1fr)_380px]">
        <div className="grid gap-4">
          <section className="rounded-2xl border border-slate-200 bg-white p-4">
            <div>
              <p className="text-xs font-bold text-slate-900">Brand & layout</p>
              <p className="mt-1 text-[10px] leading-4 text-slate-500">
                Set the reusable email canvas and brand colors. The generated HTML is handled automatically.
              </p>
            </div>

            <div className="mt-4 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
              <label className="text-[10px] font-semibold text-slate-600">
                Brand color
                <div className="mt-2 flex items-center gap-2">
                  <input
                    type="color"
                    value={design.brandColor}
                    onChange={(event) => patchDesign({ brandColor: event.target.value })}
                    className="size-9 rounded border border-slate-200 bg-white"
                  />
                  <input
                    className="form-input !py-2 text-[11px]"
                    value={design.brandColor}
                    onChange={(event) => patchDesign({ brandColor: event.target.value })}
                  />
                </div>
              </label>
              <label className="text-[10px] font-semibold text-slate-600">
                Email background
                <input
                  type="color"
                  className="mt-2 h-9 w-full rounded border border-slate-200 bg-white"
                  value={design.backgroundColor}
                  onChange={(event) => patchDesign({ backgroundColor: event.target.value })}
                />
              </label>
              <label className="text-[10px] font-semibold text-slate-600">
                Content background
                <input
                  type="color"
                  className="mt-2 h-9 w-full rounded border border-slate-200 bg-white"
                  value={design.contentBackgroundColor}
                  onChange={(event) =>
                    patchDesign({ contentBackgroundColor: event.target.value })
                  }
                />
              </label>
              <label className="text-[10px] font-semibold text-slate-600">
                Content width
                <select
                  className="form-input mt-2 !py-2 text-[11px]"
                  value={design.contentWidth}
                  onChange={(event) =>
                    patchDesign({ contentWidth: Number(event.target.value) })
                  }
                >
                  <option value={520}>Compact · 520px</option>
                  <option value={600}>Standard · 600px</option>
                  <option value={680}>Wide · 680px</option>
                </select>
              </label>
            </div>
          </section>

          <section className="rounded-2xl border border-slate-200 bg-white p-4">
            <div className="flex items-start justify-between gap-3">
              <div>
                <p className="text-xs font-bold text-slate-900">Header</p>
                <p className="mt-1 text-[10px] text-slate-500">
                  Add your company logo and optional campaign heading.
                </p>
              </div>
              {design.header.logoMediaId && (
                <button
                  type="button"
                  className="text-[10px] font-semibold text-rose-600"
                  onClick={() => patchHeader({ logoMediaId: null })}
                >
                  Remove logo
                </button>
              )}
            </div>

            <div className="mt-4 grid gap-4 sm:grid-cols-[180px_1fr]">
              <button
                type="button"
                className="flex min-h-28 items-center justify-center overflow-hidden rounded-xl border border-dashed border-slate-300 bg-slate-50 p-3 text-center"
                onClick={() => setMediaTarget('HEADER')}
              >
                {design.header.logoMediaId && mediaUrls[design.header.logoMediaId] ? (
                  <img
                    src={mediaUrls[design.header.logoMediaId]}
                    alt="Logo"
                    className="max-h-20 max-w-full object-contain"
                  />
                ) : (
                  <span>
                    <FiImage className="mx-auto text-xl text-slate-400" aria-hidden="true" />
                    <span className="mt-2 block text-[10px] font-bold text-slate-600">
                      Choose logo
                    </span>
                    <span className="mt-1 block text-[9px] text-slate-400">
                      Company media / upload
                    </span>
                  </span>
                )}
              </button>

              <div className="grid gap-3 sm:grid-cols-2">
                <label className="text-[10px] font-semibold text-slate-600 sm:col-span-2">
                  Header heading
                  <input
                    className="form-input mt-2"
                    value={design.header.heading ?? ''}
                    onChange={(event) => patchHeader({ heading: event.target.value })}
                    placeholder="September Sale"
                  />
                </label>
                <label className="text-[10px] font-semibold text-slate-600">
                  Logo alignment
                  <select
                    className="form-input mt-2 !py-2"
                    value={design.header.logoAlignment}
                    onChange={(event) =>
                      patchHeader({
                        logoAlignment: event.target.value as EmailTemplateDesign['header']['logoAlignment'],
                      })
                    }
                  >
                    <option value="LEFT">Left</option>
                    <option value="CENTER">Center</option>
                    <option value="RIGHT">Right</option>
                  </select>
                </label>
                <label className="text-[10px] font-semibold text-slate-600">
                  Logo width · {design.header.logoWidth}px
                  <input
                    type="range"
                    min={60}
                    max={280}
                    step={10}
                    className="mt-3 w-full"
                    value={design.header.logoWidth}
                    onChange={(event) =>
                      patchHeader({ logoWidth: Number(event.target.value) })
                    }
                  />
                </label>
              </div>
            </div>
          </section>

          <section className="rounded-2xl border border-slate-200 bg-white p-4">
            <div>
              <p className="text-xs font-bold text-slate-900">Content</p>
              <p className="mt-1 text-[10px] text-slate-500">
                Build the email with reusable blocks. Variables are replaced separately for every recipient.
              </p>
            </div>

            <div className="mt-3 flex flex-wrap gap-2">
              {[
                ['HEADING', FiType],
                ['TEXT', FiType],
                ['IMAGE', FiImage],
                ['BUTTON', FiLink],
                ['DIVIDER', FiMinus],
                ['SPACER', FiMove],
              ].map(([type, Icon]) => (
                <button
                  key={type as string}
                  type="button"
                  className="secondary-button inline-flex items-center gap-1.5 !px-2.5 !py-2 text-[10px]"
                  onClick={() => addBlock(type as EmailBlockType)}
                >
                  <Icon aria-hidden="true" />
                  + {blockName(type as EmailBlockType)}
                </button>
              ))}
            </div>

            <div className="mt-4 grid gap-3">
              {design.blocks.map((block, index) => (
                <div key={block.id} className="rounded-xl border border-slate-200 bg-slate-50/50 p-3">
                  <div className="flex items-center justify-between gap-2">
                    <span className="text-[10px] font-bold uppercase tracking-[0.06em] text-slate-500">
                      {index + 1}. {blockName(block.type)}
                    </span>
                    <div className="flex items-center gap-1">
                      <button
                        type="button"
                        className="admin-back-button !size-7"
                        onClick={() => moveBlock(index, -1)}
                        disabled={index === 0}
                        aria-label="Move block up"
                      >
                        <FiArrowUp aria-hidden="true" />
                      </button>
                      <button
                        type="button"
                        className="admin-back-button !size-7"
                        onClick={() => moveBlock(index, 1)}
                        disabled={index === design.blocks.length - 1}
                        aria-label="Move block down"
                      >
                        <FiArrowDown aria-hidden="true" />
                      </button>
                      <button
                        type="button"
                        className="admin-back-button !size-7 text-rose-500"
                        onClick={() => removeBlock(block.id)}
                        aria-label="Remove block"
                      >
                        <FiTrash2 aria-hidden="true" />
                      </button>
                    </div>
                  </div>

                  {(block.type === 'TEXT' || block.type === 'HEADING') && (
                    <div className="mt-3">
                      <textarea
                        className="form-input min-h-24 resize-y"
                        value={block.text ?? ''}
                        onChange={(event) => updateBlock(block.id, { text: event.target.value })}
                        placeholder={block.type === 'HEADING' ? 'Your heading' : 'Write your message'}
                      />
                      <div className="mt-2 flex flex-wrap items-center gap-2">
                        <select
                          className="form-input !w-auto !py-1.5 text-[10px]"
                          defaultValue=""
                          onChange={(event) => {
                            appendVariable(block, event.target.value)
                            event.currentTarget.value = ''
                          }}
                        >
                          <option value="">+ Insert variable</option>
                          {variableOptions.map(([value, label]) => (
                            <option key={value} value={value}>
                              {label}
                            </option>
                          ))}
                        </select>
                        <select
                          className="form-input !w-auto !py-1.5 text-[10px]"
                          value={block.alignment ?? 'LEFT'}
                          onChange={(event) =>
                            updateBlock(block.id, {
                              alignment: event.target.value as EmailDesignBlock['alignment'],
                            })
                          }
                        >
                          <option value="LEFT">Left</option>
                          <option value="CENTER">Center</option>
                          <option value="RIGHT">Right</option>
                        </select>
                        {block.type === 'HEADING' && (
                          <select
                            className="form-input !w-auto !py-1.5 text-[10px]"
                            value={block.level ?? 'H2'}
                            onChange={(event) =>
                              updateBlock(block.id, {
                                level: event.target.value as EmailDesignBlock['level'],
                              })
                            }
                          >
                            <option value="H1">Large</option>
                            <option value="H2">Medium</option>
                            <option value="H3">Small</option>
                          </select>
                        )}
                      </div>
                    </div>
                  )}

                  {block.type === 'IMAGE' && (
                    <div className="mt-3 grid gap-3 sm:grid-cols-[160px_1fr]">
                      <button
                        type="button"
                        className="flex min-h-28 items-center justify-center overflow-hidden rounded-xl border border-dashed border-slate-300 bg-white p-2"
                        onClick={() => setMediaTarget(block.id)}
                      >
                        {block.mediaId && mediaUrls[block.mediaId] ? (
                          <img
                            src={mediaUrls[block.mediaId]}
                            alt={block.altText || 'Template image'}
                            className="max-h-28 max-w-full object-contain"
                          />
                        ) : (
                          <span className="text-center">
                            <FiImage className="mx-auto text-xl text-slate-400" aria-hidden="true" />
                            <span className="mt-1 block text-[10px] font-semibold text-slate-600">
                              Choose image
                            </span>
                          </span>
                        )}
                      </button>
                      <div className="grid gap-2">
                        <input
                          className="form-input"
                          value={block.altText ?? ''}
                          onChange={(event) =>
                            updateBlock(block.id, { altText: event.target.value })
                          }
                          placeholder="Image alt text"
                        />
                        <label className="text-[10px] font-semibold text-slate-500">
                          Width · {block.imageWidth ?? 560}px
                          <input
                            type="range"
                            min={100}
                            max={560}
                            step={20}
                            className="mt-2 w-full"
                            value={block.imageWidth ?? 560}
                            onChange={(event) =>
                              updateBlock(block.id, {
                                imageWidth: Number(event.target.value),
                              })
                            }
                          />
                        </label>
                      </div>
                    </div>
                  )}

                  {block.type === 'BUTTON' && (
                    <div className="mt-3 grid gap-3 sm:grid-cols-2">
                      <label className="text-[10px] font-semibold text-slate-600">
                        Button text
                        <input
                          className="form-input mt-2"
                          value={block.buttonText ?? ''}
                          onChange={(event) =>
                            updateBlock(block.id, { buttonText: event.target.value })
                          }
                        />
                      </label>
                      <label className="text-[10px] font-semibold text-slate-600">
                        Link
                        <input
                          className="form-input mt-2"
                          value={block.buttonUrl ?? ''}
                          onChange={(event) =>
                            updateBlock(block.id, { buttonUrl: event.target.value })
                          }
                          placeholder="https://example.com"
                        />
                      </label>
                      <div className="flex flex-wrap gap-2 sm:col-span-2">
                        <select
                          className="form-input !w-auto !py-1.5 text-[10px]"
                          defaultValue=""
                          onChange={(event) => {
                            appendVariable(block, event.target.value, 'TEXT')
                            event.currentTarget.value = ''
                          }}
                        >
                          <option value="">+ Variable in text</option>
                          {variableOptions.map(([value, label]) => (
                            <option key={value} value={value}>
                              {label}
                            </option>
                          ))}
                        </select>
                        <select
                          className="form-input !w-auto !py-1.5 text-[10px]"
                          defaultValue=""
                          onChange={(event) => {
                            appendVariable(block, event.target.value, 'URL')
                            event.currentTarget.value = ''
                          }}
                        >
                          <option value="">+ Variable in URL</option>
                          {variableOptions.map(([value, label]) => (
                            <option key={value} value={value}>
                              {label}
                            </option>
                          ))}
                        </select>
                      </div>
                    </div>
                  )}

                  {block.type === 'SPACER' && (
                    <label className="mt-3 block text-[10px] font-semibold text-slate-600">
                      Height · {block.height ?? 24}px
                      <input
                        type="range"
                        min={8}
                        max={80}
                        step={4}
                        className="mt-2 w-full"
                        value={block.height ?? 24}
                        onChange={(event) =>
                          updateBlock(block.id, { height: Number(event.target.value) })
                        }
                      />
                    </label>
                  )}
                </div>
              ))}
            </div>
          </section>

          <section className="rounded-2xl border border-slate-200 bg-white p-4">
            <div>
              <p className="text-xs font-bold text-slate-900">Footer</p>
              <p className="mt-1 text-[10px] text-slate-500">
                Company details and marketing unsubscribe controls.
              </p>
            </div>

            <div className="mt-4 grid gap-3 sm:grid-cols-2">
              <label className="text-[10px] font-semibold text-slate-600">
                Company name
                <input
                  className="form-input mt-2"
                  value={design.footer.companyName ?? ''}
                  onChange={(event) => patchFooter({ companyName: event.target.value })}
                  placeholder="Example Company"
                />
              </label>
              <label className="text-[10px] font-semibold text-slate-600">
                Privacy URL
                <input
                  className="form-input mt-2"
                  value={design.footer.privacyUrl ?? ''}
                  onChange={(event) => patchFooter({ privacyUrl: event.target.value })}
                  placeholder="https://example.com/privacy"
                />
              </label>
              <label className="text-[10px] font-semibold text-slate-600 sm:col-span-2">
                Business address
                <input
                  className="form-input mt-2"
                  value={design.footer.address ?? ''}
                  onChange={(event) => patchFooter({ address: event.target.value })}
                  placeholder="Bengaluru, Karnataka, India"
                />
              </label>
              <label className="flex items-start gap-2 rounded-xl border border-slate-200 p-3 text-[10px] text-slate-600 sm:col-span-2">
                <input
                  type="checkbox"
                  className="mt-0.5"
                  checked={design.footer.includeUnsubscribe}
                  onChange={(event) =>
                    patchFooter({ includeUnsubscribe: event.target.checked })
                  }
                />
                <span>
                  <strong className="block text-slate-800">Include unsubscribe link</strong>
                  <span className="mt-1 block leading-4 text-slate-500">
                    Recommended for marketing templates. The final compliance URL will be resolved by the sending layer.
                  </span>
                </span>
              </label>
            </div>
          </section>

          <section className="rounded-2xl border border-slate-200 bg-white p-4">
            <p className="text-xs font-bold text-slate-900">Personalization preview</p>
            <p className="mt-1 text-[10px] leading-4 text-slate-500">
              These values are only for preview. Actual values come from each recipient and CSV/custom fields.
            </p>
            {variables.length === 0 ? (
              <p className="mt-3 text-[10px] text-slate-400">No variables detected.</p>
            ) : (
              <div className="mt-3 grid gap-3 sm:grid-cols-2">
                {variables.map((variable) => (
                  <label key={variable} className="text-[10px] font-semibold text-slate-600">
                    <span className="rounded-full bg-indigo-50 px-2 py-1 text-[9px] text-indigo-700">
                      {'{{'}{variable}{'}}'}
                    </span>
                    <input
                      className="form-input mt-2"
                      value={previewValues[variable] ?? ''}
                      onChange={(event) =>
                        setPreviewValues((current) => ({
                          ...current,
                          [variable]: event.target.value,
                        }))
                      }
                    />
                  </label>
                ))}
              </div>
            )}
          </section>
        </div>

        <aside className="xl:sticky xl:top-0 xl:self-start">
          <div className="rounded-2xl border border-slate-200 bg-slate-50/70 p-4">
            <div className="flex items-center justify-between gap-3">
              <div>
                <p className="text-xs font-bold uppercase tracking-[0.08em] text-slate-400">
                  Live preview
                </p>
                <p className="mt-1 text-[10px] text-slate-500">
                  {previewSubject || '(no subject)'}
                </p>
              </div>
              <div className="flex rounded-lg bg-white p-1 shadow-sm">
                <button
                  type="button"
                  className={`rounded-md p-1.5 ${
                    previewDevice === 'DESKTOP'
                      ? 'bg-indigo-50 text-indigo-600'
                      : 'text-slate-400'
                  }`}
                  onClick={() => setPreviewDevice('DESKTOP')}
                  aria-label="Desktop preview"
                >
                  <FiMonitor aria-hidden="true" />
                </button>
                <button
                  type="button"
                  className={`rounded-md p-1.5 ${
                    previewDevice === 'MOBILE'
                      ? 'bg-indigo-50 text-indigo-600'
                      : 'text-slate-400'
                  }`}
                  onClick={() => setPreviewDevice('MOBILE')}
                  aria-label="Mobile preview"
                >
                  <FiSmartphone aria-hidden="true" />
                </button>
              </div>
            </div>

            <div className="mt-4 overflow-auto rounded-xl bg-slate-200/60 p-3">
              <iframe
                title="Email template preview"
                sandbox=""
                referrerPolicy="no-referrer"
                srcDoc={previewHtml}
                className={`mx-auto h-[620px] rounded-lg border-0 bg-white shadow-sm transition-all ${
                  previewDevice === 'MOBILE' ? 'w-[320px] max-w-full' : 'w-full'
                }`}
              />
            </div>

            <div className="mt-3 rounded-xl bg-white p-3 text-[9px] leading-4 text-slate-500">
              Images use short-lived signed media URLs only for this preview. The template stores the media ID, not the signed URL.
            </div>
          </div>
        </aside>
      </div>

      {mediaTarget && (
        <TemplateMediaPickerModal
          companyId={companyId}
          title={mediaTarget === 'HEADER' ? 'Choose logo' : 'Choose email image'}
          onClose={() => setMediaTarget(null)}
          onSelect={chooseMedia}
        />
      )}
    </>
  )
}
