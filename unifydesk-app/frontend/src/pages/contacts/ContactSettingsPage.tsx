import { useEffect, useState } from 'react'
import ContactsNav from '../../components/contacts/ContactsNav'
import ConfirmDialog from '../../components/ui/ConfirmDialog'
import { contactsApi } from '../../features/contacts/contactsApi'
import type { CustomFieldDefinition, CustomFieldType, TagSummary } from '../../features/contacts/types'
import { PERMISSIONS } from '../../features/iam/permissions'
import { useIam } from '../../features/iam/useIam'
import { apiErrorMessage } from '../../lib/api/apiClient'
import { useAppSelector } from '../../store/hooks'
import { selectSelectedCompanyId } from '../../store/workspace/workspaceSlice'

const FIELD_TYPES: Array<{ value: CustomFieldType; label: string }> = [
  { value: 'TEXT', label: 'Text' },
  { value: 'NUMBER', label: 'Number' },
  { value: 'BOOLEAN', label: 'Yes / No' },
  { value: 'DATE', label: 'Date' },
  { value: 'DATETIME', label: 'Date & time' },
  { value: 'SINGLE_SELECT', label: 'Single choice' },
  { value: 'MULTI_SELECT', label: 'Multiple choice' },
]

export default function ContactSettingsPage() {
  const companyId = useAppSelector(selectSelectedCompanyId)
  const { can } = useIam()
  const [tags, setTags] = useState<TagSummary[]>([])
  const [fields, setFields] = useState<CustomFieldDefinition[]>([])
  const [tagName, setTagName] = useState('')
  const [tagColor, setTagColor] = useState('#3b82f6')
  const [fieldLabel, setFieldLabel] = useState('')
  const [fieldKey, setFieldKey] = useState('')
  const [fieldType, setFieldType] = useState<CustomFieldType>('TEXT')
  const [fieldOptions, setFieldOptions] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [archiveTarget, setArchiveTarget] = useState<{ kind: 'tag' | 'field'; id: string; label: string } | null>(null)

  const load = async () => {
    if (!companyId) return
    try {
      const [nextTags, nextFields] = await Promise.all([
        contactsApi.tags(companyId),
        contactsApi.customFields(companyId),
      ])
      setTags(nextTags)
      setFields(nextFields.filter((item) => item.status === 'ACTIVE'))
    } catch (loadError) {
      setError(apiErrorMessage(loadError, 'Unable to load contact settings.'))
    }
  }

  useEffect(() => {
    void load()
  }, [companyId])

  const run = async (work: () => Promise<unknown>, success: string) => {
    setBusy(true)
    setError('')
    setNotice('')
    try {
      await work()
      setNotice(success)
      await load()
    } catch (actionError) {
      setError(apiErrorMessage(actionError, 'Unable to save contact settings.'))
    } finally {
      setBusy(false)
    }
  }

  const createTag = async () => {
    if (!companyId || !tagName.trim()) return
    await run(
      () => contactsApi.createTag(companyId, { name: tagName.trim(), color: tagColor.trim() || null }),
      'Tag created.',
    )
    setTagName('')
    setTagColor('#3b82f6')
  }

  const createField = async () => {
    if (!companyId || !fieldLabel.trim()) return
    const key = (fieldKey.trim() || fieldLabel.trim())
      .toLowerCase()
      .replace(/[^a-z0-9]+/g, '_')
      .replace(/^_+|_+$/g, '')
      .slice(0, 64)
    const options =
      fieldType === 'SINGLE_SELECT' || fieldType === 'MULTI_SELECT'
        ? fieldOptions.split(',').map((item) => item.trim()).filter(Boolean)
        : undefined
    if (!key) {
      setError('Enter a field name that can produce a valid key.')
      return
    }
    await run(
      () => contactsApi.createCustomField(companyId, {
        fieldKey: key,
        label: fieldLabel.trim(),
        type: fieldType,
        options,
        position: fields.length,
      }),
      'Contact field created.',
    )
    setFieldLabel('')
    setFieldKey('')
    setFieldOptions('')
    setFieldType('TEXT')
  }

  const archive = async () => {
    if (!companyId || !archiveTarget) return
    const target = archiveTarget
    await run(
      () =>
        target.kind === 'tag'
          ? contactsApi.archiveTag(companyId, target.id)
          : contactsApi.archiveCustomField(companyId, target.id),
      target.kind === 'tag' ? 'Tag archived.' : 'Contact field archived.',
    )
    setArchiveTarget(null)
  }

  return (
    <section className="px-4 py-6 sm:px-6 lg:px-8">
      <div className="w-full">
        <ContactsNav />
        <header>
          <p className="eyebrow">Contacts</p>
          <h1 className="mt-2 text-3xl font-bold text-slate-950">Contact settings</h1>
          <p className="mt-2 text-sm text-slate-500">
            Manage reusable tags and custom fields. These options are available across contacts,
            imports, bulk actions and segments.
          </p>
        </header>

        {(error || notice) && (
          <div className={'mt-5 rounded-xl border px-4 py-3 text-sm ' + (error
            ? 'border-rose-200 bg-rose-50 text-rose-700'
            : 'border-emerald-200 bg-emerald-50 text-emerald-700')}>
            {error || notice}
          </div>
        )}

        <div className="mt-6 grid gap-5 xl:grid-cols-2">
          <section className="panel p-5">
            <div>
              <h2 className="text-lg font-bold text-slate-950">Tags</h2>
              <p className="mt-1 text-sm text-slate-500">
                Use tags for lightweight labels such as VIP, Lead or Event attendee.
              </p>
            </div>
            {can(PERMISSIONS.CONTACT_MODIFY) && (
              <div className="mt-4 grid gap-2 sm:grid-cols-[1fr_150px_auto]">
                <input
                  className="form-input"
                  placeholder="Tag name"
                  value={tagName}
                  onChange={(event) => setTagName(event.target.value)}
                />
                <label className="flex h-[42px] items-center gap-2 rounded-lg border border-slate-200 bg-white px-2 text-xs font-semibold text-slate-600">
                  <input
                    type="color"
                    value={tagColor}
                    onChange={(event) => setTagColor(event.target.value)}
                    aria-label="Tag color"
                    className="h-7 w-9 cursor-pointer rounded border-0 bg-transparent p-0"
                  />
                  <span className="font-mono text-[11px] uppercase">{tagColor}</span>
                </label>
                <button className="primary-button" disabled={busy || !tagName.trim()} onClick={() => void createTag()}>
                  Create tag
                </button>
              </div>
            )}
            <div className="mt-4 divide-y divide-slate-100 rounded-xl border border-slate-200">
              {tags.length === 0 ? (
                <p className="p-4 text-sm text-slate-500">No tags yet.</p>
              ) : tags.map((tag) => (
                <div key={tag.id} className="flex items-center justify-between gap-3 p-4">
                  <div className="flex items-center gap-3">
                    <span className="h-3 w-3 rounded-full border border-slate-200" style={{ backgroundColor: tag.color || '#e2e8f0' }} />
                    <span className="font-semibold text-slate-800">{tag.name}</span>
                  </div>
                  {can(PERMISSIONS.CONTACT_MODIFY) && (
                    <button className="secondary-button text-rose-700" disabled={busy} onClick={() => setArchiveTarget({ kind: 'tag', id: tag.id, label: tag.name })}>
                      Archive
                    </button>
                  )}
                </div>
              ))}
            </div>
          </section>

          <section className="panel p-5">
            <div>
              <h2 className="text-lg font-bold text-slate-950">Contact fields</h2>
              <p className="mt-1 text-sm text-slate-500">
                Store business-specific details without changing the standard contact profile.
              </p>
            </div>
            {can(PERMISSIONS.CONTACT_MODIFY) && (
              <div className="mt-4 grid gap-2 sm:grid-cols-2">
                <input className="form-input" placeholder="Field label" value={fieldLabel} onChange={(event) => setFieldLabel(event.target.value)} />
                <input className="form-input" placeholder="Key (optional)" value={fieldKey} onChange={(event) => setFieldKey(event.target.value)} />
                <select className="form-input" value={fieldType} onChange={(event) => setFieldType(event.target.value as CustomFieldType)}>
                  {FIELD_TYPES.map((type) => <option key={type.value} value={type.value}>{type.label}</option>)}
                </select>
                {(fieldType === 'SINGLE_SELECT' || fieldType === 'MULTI_SELECT') ? (
                  <input className="form-input" placeholder="Options, comma separated" value={fieldOptions} onChange={(event) => setFieldOptions(event.target.value)} />
                ) : <div />}
                <button className="primary-button sm:col-span-2 sm:justify-self-start" disabled={busy || !fieldLabel.trim()} onClick={() => void createField()}>
                  Create field
                </button>
              </div>
            )}
            <div className="mt-4 divide-y divide-slate-100 rounded-xl border border-slate-200">
              {fields.length === 0 ? (
                <p className="p-4 text-sm text-slate-500">No custom fields yet.</p>
              ) : fields.map((field) => (
                <div key={field.id} className="flex items-center justify-between gap-3 p-4">
                  <div>
                    <p className="font-semibold text-slate-800">{field.label}</p>
                    <p className="mt-1 text-xs text-slate-500">{FIELD_TYPES.find((type) => type.value === field.type)?.label || field.type}</p>
                  </div>
                  {can(PERMISSIONS.CONTACT_MODIFY) && (
                    <button className="secondary-button text-rose-700" disabled={busy} onClick={() => setArchiveTarget({ kind: 'field', id: field.id, label: field.label })}>
                      Archive
                    </button>
                  )}
                </div>
              ))}
            </div>
          </section>
        </div>
      </div>

      <ConfirmDialog
        open={Boolean(archiveTarget)}
        title={archiveTarget?.kind === 'tag' ? 'Archive this tag?' : 'Archive this contact field?'}
        description={archiveTarget ? `${archiveTarget.label} will stop appearing in new contact workflows. Existing historical data is preserved.` : ''}
        confirmLabel="Yes, archive"
        cancelLabel="Cancel"
        tone="warning"
        working={busy}
        onCancel={() => setArchiveTarget(null)}
        onConfirm={() => void archive()}
      />
    </section>
  )
}
