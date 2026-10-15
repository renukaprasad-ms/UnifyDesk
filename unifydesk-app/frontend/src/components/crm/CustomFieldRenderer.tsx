/**
 * CustomFieldRenderer
 *
 * Renders typed input controls for CRM custom field definitions.
 * Integrated into Lead, Deal, and Account creation/edit forms.
 */
import type { CrmFieldDef, CustomFieldValues } from '../../features/contacts/crmCustomFieldsApi'

type FieldValue = string | number | boolean | string[] | null | undefined

interface Props {
  def: CrmFieldDef
  value: FieldValue
  onChange: (key: string, value: FieldValue) => void
  disabled?: boolean
}

export default function CustomFieldRenderer({ def, value, onChange, disabled }: Props) {
  const id = 'cf-' + def.id

  const label = (
    <label
      htmlFor={id}
      className="block text-xs font-semibold uppercase tracking-wider text-slate-600 dark:text-slate-300 mb-1"
    >
      {def.label}
      {def.required && <span className="text-rose-500 ml-1" aria-hidden>*</span>}
    </label>
  )

  const baseProps = { id, disabled, 'aria-required': def.required }

  switch (def.fieldType) {
    case 'TEXT':
    case 'URL':
      return (
        <div className="space-y-1">
          {label}
          <input
            {...baseProps}
            type={def.fieldType === 'URL' ? 'url' : 'text'}
            className="form-input block w-full text-sm"
            value={typeof value === 'string' ? value : ''}
            maxLength={1000}
            onChange={(e) => onChange(def.fieldKey, e.target.value || null)}
            placeholder={def.fieldType === 'URL' ? 'https://example.com' : `Enter ${def.label.toLowerCase()}`}
          />
        </div>
      )

    case 'NUMBER':
      return (
        <div className="space-y-1">
          {label}
          <input
            {...baseProps}
            type="number"
            className="form-input block w-full text-sm"
            value={value == null ? '' : String(value)}
            step="any"
            onChange={(e) => onChange(def.fieldKey, e.target.value === '' ? null : Number(e.target.value))}
            placeholder="0"
          />
        </div>
      )

    case 'DATE':
      return (
        <div className="space-y-1">
          {label}
          <input
            {...baseProps}
            type="date"
            className="form-input block w-full text-sm"
            value={typeof value === 'string' ? value : ''}
            onChange={(e) => onChange(def.fieldKey, e.target.value || null)}
          />
        </div>
      )

    case 'BOOLEAN':
      return (
        <div className="flex items-center gap-2.5 pt-1">
          <input
            {...baseProps}
            type="checkbox"
            className="h-4 w-4 rounded border-slate-300 text-blue-600 focus:ring-blue-500 dark:border-slate-700"
            checked={Boolean(value)}
            onChange={(e) => onChange(def.fieldKey, e.target.checked)}
          />
          <label htmlFor={id} className="text-sm font-medium text-slate-700 dark:text-slate-300">
            {def.label}
            {def.required && <span className="text-rose-500 ml-1" aria-hidden>*</span>}
          </label>
        </div>
      )

    case 'DROPDOWN':
      return (
        <div className="space-y-1">
          {label}
          <select
            {...baseProps}
            className="form-input block w-full text-sm"
            value={typeof value === 'string' ? value : ''}
            onChange={(e) => onChange(def.fieldKey, e.target.value || null)}
          >
            <option value="">— Select an option —</option>
            {(def.options ?? []).map((opt) => (
              <option key={opt} value={opt}>{opt}</option>
            ))}
          </select>
        </div>
      )

    case 'MULTI_SELECT': {
      const selected: string[] = Array.isArray(value) ? (value as string[]) : []
      const toggle = (opt: string) => {
        const next = selected.includes(opt) ? selected.filter((s) => s !== opt) : [...selected, opt]
        onChange(def.fieldKey, next.length ? next : null)
      }
      return (
        <div className="space-y-1.5">
          {label}
          <div className="flex flex-wrap gap-1.5" role="group" aria-labelledby={id + '-label'}>
            <span id={id + '-label'} className="sr-only">{def.label}</span>
            {(def.options ?? []).map((opt) => {
              const isSelected = selected.includes(opt)
              return (
                <button
                  key={opt}
                  type="button"
                  disabled={disabled}
                  onClick={() => toggle(opt)}
                  className={`rounded-lg px-2.5 py-1 text-xs font-medium transition-colors border ${
                    isSelected
                      ? 'bg-blue-600 text-white border-blue-600 shadow-xs'
                      : 'bg-white dark:bg-slate-800 text-slate-700 dark:text-slate-300 border-slate-200 dark:border-slate-700 hover:bg-slate-50 dark:hover:bg-slate-700/60'
                  }`}
                  aria-pressed={isSelected}
                >
                  {opt}
                </button>
              )
            })}
          </div>
        </div>
      )
    }

    default:
      return null
  }
}

/**
 * CustomFieldsSection
 * Renders all custom field definitions for a given entity inside a form.
 */
export function CustomFieldsSection({
  defs,
  values,
  onChange,
  disabled,
}: {
  defs: CrmFieldDef[]
  values: CustomFieldValues
  onChange: (key: string, value: FieldValue) => void
  disabled?: boolean
}) {
  if (!defs || !defs.length) return null
  return (
    <div className="mt-4 pt-4 border-t border-slate-200 dark:border-slate-800 space-y-3">
      <div className="flex items-center gap-2">
        <span className="text-xs font-bold uppercase tracking-wider text-slate-500 dark:text-slate-400">
          Custom Attributes
        </span>
        <div className="h-px flex-1 bg-slate-200 dark:bg-slate-800" />
      </div>
      <div className="grid gap-3 sm:grid-cols-2">
        {defs.map((def) => (
          <CustomFieldRenderer
            key={def.id}
            def={def}
            value={values[def.fieldKey] as FieldValue}
            onChange={onChange}
            disabled={disabled}
          />
        ))}
      </div>
    </div>
  )
}
