import { FiArrowDown, FiArrowUp, FiImage, FiPlus, FiTrash2 } from 'react-icons/fi'
import type { EmailBlock, EmailDocument } from '../../features/emailTemplate/types'

type Props = {
  document: EmailDocument
  uploadingBlockId?: string | null
  onChange: (document: EmailDocument) => void
  onUploadImage: (blockId: string, field: 'logoUrl' | 'imageUrl', file: File) => void
}

const blockTypes: EmailBlock['type'][] = [
  'HEADER',
  'HERO',
  'HEADING',
  'TEXT',
  'IMAGE',
  'BUTTON',
  'DIVIDER',
  'SPACER',
  'FOOTER',
]

const newBlock = (type: EmailBlock['type']): EmailBlock => {
  const id = crypto.randomUUID()
  switch (type) {
    case 'HEADER':
      return { id, type, brandText: 'YOUR BRAND' }
    case 'HERO':
      return { id, type, heading: 'Campaign headline', text: 'Introduce your message here.' }
    case 'HEADING':
      return { id, type, text: 'Section heading' }
    case 'TEXT':
      return { id, type, text: 'Write your content here.' }
    case 'IMAGE':
      return { id, type }
    case 'BUTTON':
      return { id, type, text: 'Learn more', url: 'https://example.com' }
    case 'DIVIDER':
      return { id, type }
    case 'SPACER':
      return { id, type }
    case 'FOOTER':
      return { id, type, text: 'Your company · Your address' }
    default:
      throw new Error('Unsupported email block type')
  }
}

export default function EmailBlockEditor({
  document,
  uploadingBlockId,
  onChange,
  onUploadImage,
}: Props) {
  const patch = (id: string, values: Record<string, unknown>) => {
    onChange({
      ...document,
      blocks: document.blocks.map((block) =>
        block.id === id ? ({ ...block, ...values } as EmailBlock) : block,
      ),
    })
  }

  const move = (index: number, direction: -1 | 1) => {
    const target = index + direction
    if (target < 0 || target >= document.blocks.length) return
    const blocks = [...document.blocks]
    ;[blocks[index], blocks[target]] = [blocks[target], blocks[index]]
    onChange({ ...document, blocks })
  }

  const remove = (id: string) => {
    if (document.blocks.length <= 1) return
    onChange({ ...document, blocks: document.blocks.filter((block) => block.id !== id) })
  }

  const add = (type: EmailBlock['type']) => {
    onChange({ ...document, blocks: [...document.blocks, newBlock(type)] })
  }

  return (
    <div className="grid gap-4">
      <section className="rounded-2xl border border-slate-200 p-4">
        <p className="text-xs font-bold text-slate-900">Email style</p>
        <div className="mt-3 grid grid-cols-3 gap-3">
          {(
            [
              ['accentColor', 'Accent'],
              ['backgroundColor', 'Page'],
              ['contentBackgroundColor', 'Content'],
            ] as const
          ).map(([key, label]) => (
            <label key={key} className="text-[10px] font-semibold text-slate-500">
              {label}
              <input
                type="color"
                className="mt-2 h-10 w-full rounded-lg border border-slate-200"
                value={document.settings[key]}
                onChange={(event) =>
                  onChange({
                    ...document,
                    settings: { ...document.settings, [key]: event.target.value },
                  })
                }
              />
            </label>
          ))}
        </div>
      </section>

      {document.blocks.map((block, index) => (
        <section key={block.id} className="rounded-2xl border border-slate-200 bg-white p-4">
          <div className="flex items-center justify-between gap-3">
            <div>
              <p className="text-xs font-bold text-slate-900">
                {block.type.toLowerCase().replaceAll('_', ' ')}
              </p>
              <p className="mt-1 text-[10px] text-slate-400">Block {index + 1}</p>
            </div>
            <div className="flex gap-1">
              <button
                type="button"
                className="admin-back-button"
                disabled={index === 0}
                onClick={() => move(index, -1)}
                aria-label="Move block up"
              >
                <FiArrowUp />
              </button>
              <button
                type="button"
                className="admin-back-button"
                disabled={index === document.blocks.length - 1}
                onClick={() => move(index, 1)}
                aria-label="Move block down"
              >
                <FiArrowDown />
              </button>
              <button
                type="button"
                className="admin-back-button text-rose-500"
                disabled={document.blocks.length <= 1}
                onClick={() => remove(block.id)}
                aria-label="Remove block"
              >
                <FiTrash2 />
              </button>
            </div>
          </div>

          {block.type === 'HEADER' && (
            <div className="mt-3 grid gap-3">
              <input
                className="form-input"
                value={block.brandText ?? ''}
                onChange={(event) => patch(block.id, { brandText: event.target.value })}
                placeholder="YOUR BRAND"
              />
              <label className="secondary-button w-fit cursor-pointer">
                <FiImage className="mr-1 inline" />
                {uploadingBlockId === block.id ? 'Uploading…' : 'Upload logo'}
                <input
                  type="file"
                  accept="image/*"
                  className="hidden"
                  disabled={uploadingBlockId !== null}
                  onChange={(event) => {
                    const file = event.target.files?.[0]
                    if (file) onUploadImage(block.id, 'logoUrl', file)
                  }}
                />
              </label>
            </div>
          )}

          {block.type === 'HERO' && (
            <div className="mt-3 grid gap-3">
              <input
                className="form-input"
                value={block.heading ?? ''}
                onChange={(event) => patch(block.id, { heading: event.target.value })}
                placeholder="Campaign headline"
              />
              <textarea
                className="form-input min-h-24"
                value={block.text ?? ''}
                onChange={(event) => patch(block.id, { text: event.target.value })}
                placeholder="Hero introduction"
              />
              <label className="secondary-button w-fit cursor-pointer">
                <FiImage className="mr-1 inline" />
                {uploadingBlockId === block.id ? 'Uploading…' : 'Upload hero image'}
                <input
                  type="file"
                  accept="image/*"
                  className="hidden"
                  disabled={uploadingBlockId !== null}
                  onChange={(event) => {
                    const file = event.target.files?.[0]
                    if (file) onUploadImage(block.id, 'imageUrl', file)
                  }}
                />
              </label>
            </div>
          )}

          {(block.type === 'HEADING' || block.type === 'TEXT' || block.type === 'FOOTER') && (
            <textarea
              className="form-input mt-3 min-h-24"
              value={block.text}
              onChange={(event) => patch(block.id, { text: event.target.value })}
            />
          )}

          {block.type === 'IMAGE' && (
            <label className="secondary-button mt-3 w-fit cursor-pointer">
              <FiImage className="mr-1 inline" />
              {uploadingBlockId === block.id ? 'Uploading…' : 'Upload image'}
              <input
                type="file"
                accept="image/*"
                className="hidden"
                disabled={uploadingBlockId !== null}
                onChange={(event) => {
                  const file = event.target.files?.[0]
                  if (file) onUploadImage(block.id, 'imageUrl', file)
                }}
              />
            </label>
          )}

          {block.type === 'BUTTON' && (
            <div className="mt-3 grid gap-3 sm:grid-cols-2">
              <input
                className="form-input"
                value={block.text}
                onChange={(event) => patch(block.id, { text: event.target.value })}
                placeholder="Button text"
              />
              <input
                className="form-input"
                value={block.url}
                onChange={(event) => patch(block.id, { url: event.target.value })}
                placeholder="https://… or {{offer_url}}"
              />
            </div>
          )}

          {(block.type === 'DIVIDER' || block.type === 'SPACER') && (
            <p className="mt-3 text-[10px] leading-4 text-slate-500">
              This layout block has no editable text.
            </p>
          )}
        </section>
      ))}

      <section className="rounded-2xl border border-dashed border-slate-300 p-4">
        <p className="text-xs font-bold text-slate-800">Add block</p>
        <div className="mt-3 flex flex-wrap gap-2">
          {blockTypes.map((type) => (
            <button
              key={type}
              type="button"
              className="secondary-button inline-flex items-center gap-1"
              onClick={() => add(type)}
            >
              <FiPlus /> {type.toLowerCase().replaceAll('_', ' ')}
            </button>
          ))}
        </div>
      </section>
    </div>
  )
}
