import { useState } from 'react'
import type {
  CustomFieldDefinition,
  CustomFieldValue,
  TagSummary,
} from '../../features/contacts/types'

type Props = {
  tags: TagSummary[]
  assignedTags: TagSummary[]
  customFields: CustomFieldDefinition[]
  values: Record<string, CustomFieldValue>
  canModify: boolean
  busy: boolean
  onAddTag: (tagId: string) => Promise<void>
  onRemoveTag: (tagId: string) => Promise<void>
  onSaveFields: (values: Record<string, CustomFieldValue>) => Promise<void>
}

const renderValue = (value: CustomFieldValue) =>
  Array.isArray(value) ? value.join(', ') : value === null ? '' : String(value)

export default function ContactAttributesPanel({
  tags,
  assignedTags,
  customFields,
  values,
  canModify,
  busy,
  onAddTag,
  onRemoveTag,
  onSaveFields,
}: Props) {
  const [tagId, setTagId] = useState('')
  const [draft, setDraft] = useState<Record<string, CustomFieldValue>>(values)
  const unassigned = tags.filter((tag) => !assignedTags.some((assigned) => assigned.id === tag.id))

  const updateField = (field: CustomFieldDefinition, raw: string | boolean) => {
    let value: CustomFieldValue = typeof raw === 'boolean' ? raw : raw
    if (field.type === 'NUMBER') value = raw === '' ? null : Number(raw)
    if (field.type === 'MULTI_SELECT' && typeof raw === 'string') {
      value = raw.split(',').map((item) => item.trim()).filter(Boolean)
    }
    setDraft((current) => ({ ...current, [field.id]: value }))
  }

  return (
    <section className="panel p-5">
      <h2 className="text-lg font-bold text-slate-950">Tags & custom fields</h2>

      <div className="mt-4">
        <h3 className="text-xs font-bold uppercase tracking-wide text-slate-500">Tags</h3>
        <div className="mt-2 flex flex-wrap gap-2">
          {assignedTags.length === 0 && <span className="text-sm text-slate-500">No tags</span>}
          {assignedTags.map((tag) => (
            <span
              key={tag.id}
              className="inline-flex items-center gap-2 rounded-full bg-slate-100 px-3 py-1 text-xs font-semibold"
            >
              {tag.name}
              {canModify && (
                <button
                  type="button"
                  aria-label={'Remove tag ' + tag.name}
                  disabled={busy}
                  onClick={() => void onRemoveTag(tag.id)}
                >
                  ×
                </button>
              )}
            </span>
          ))}
        </div>
        {canModify && unassigned.length > 0 && (
          <div className="mt-3 flex gap-2">
            <label className="sr-only" htmlFor="contact-tag">Add tag</label>
            <select
              id="contact-tag"
              className="form-input"
              value={tagId}
              onChange={(event) => setTagId(event.target.value)}
            >
              <option value="">Choose tag…</option>
              {unassigned.map((tag) => <option key={tag.id} value={tag.id}>{tag.name}</option>)}
            </select>
            <button
              type="button"
              className="secondary-button"
              disabled={!tagId || busy}
              onClick={async () => {
                await onAddTag(tagId)
                setTagId('')
              }}
            >
              Add
            </button>
          </div>
        )}
      </div>

      <div className="mt-6 border-t border-slate-100 pt-5">
        <h3 className="text-xs font-bold uppercase tracking-wide text-slate-500">Custom fields</h3>
        {customFields.length === 0 ? (
          <p className="mt-3 text-sm text-slate-500">No custom fields are configured.</p>
        ) : (
          <div className="mt-3 grid gap-4 sm:grid-cols-2">
            {customFields.map((field) => (
              <label key={field.id} className="text-xs font-semibold text-slate-700">
                <span className="mb-2 block">{field.name}</span>
                {field.type === 'BOOLEAN' ? (
                  <select
                    className="form-input"
                    disabled={!canModify}
                    value={draft[field.id] === true ? 'true' : draft[field.id] === false ? 'false' : ''}
                    onChange={(event) =>
                      updateField(field, event.target.value === '' ? '' : event.target.value === 'true')
                    }
                  >
                    <option value="">Unknown</option>
                    <option value="true">Yes</option>
                    <option value="false">No</option>
                  </select>
                ) : field.type === 'SELECT' && field.options?.length ? (
                  <select
                    className="form-input"
                    disabled={!canModify}
                    value={renderValue(draft[field.id] ?? null)}
                    onChange={(event) => updateField(field, event.target.value)}
                  >
                    <option value="">Choose…</option>
                    {field.options.map((option) => <option key={option} value={option}>{option}</option>)}
                  </select>
                ) : (
                  <input
                    className="form-input"
                    disabled={!canModify}
                    type={field.type === 'NUMBER' ? 'number' : field.type === 'DATE' ? 'date' : 'text'}
                    value={renderValue(draft[field.id] ?? null)}
                    onChange={(event) => updateField(field, event.target.value)}
                  />
                )}
              </label>
            ))}
          </div>
        )}
        {canModify && customFields.length > 0 && (
          <button
            type="button"
            className="primary-button mt-4"
            disabled={busy}
            onClick={() => void onSaveFields(draft)}
          >
            Save custom fields
          </button>
        )}
      </div>
    </section>
  )
}
