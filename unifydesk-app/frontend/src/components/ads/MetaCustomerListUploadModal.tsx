import { useSnackbarState } from '../feedback/useSnackbarState'
import { useMemo, useState, type FormEvent } from 'react'
import { FiTrash2, FiUploadCloud, FiX } from 'react-icons/fi'
import type {
  AdAudience,
  MetaCustomerListMembersInput,
  MetaCustomerListSchema,
} from '../../features/ads/types'

type Props = {
  audience: AdAudience | null
  open: boolean
  saving: boolean
  onClose: () => void
  onSubmit: (input: MetaCustomerListMembersInput) => Promise<void>
}

const SCHEMA_OPTIONS: Array<{ value: MetaCustomerListSchema; label: string }> = [
  { value: 'EMAIL', label: 'Email address' },
  { value: 'PHONE', label: 'Phone number' },
  { value: 'EXTERN_ID', label: 'External customer ID' },
  { value: 'FN', label: 'First name' },
  { value: 'LN', label: 'Last name' },
  { value: 'FI', label: 'First initial' },
  { value: 'CT', label: 'City' },
  { value: 'ST', label: 'State / region' },
  { value: 'ZIP', label: 'Postal / ZIP code' },
  { value: 'COUNTRY', label: 'Country code' },
  { value: 'GEN', label: 'Gender initial' },
  { value: 'DOBY', label: 'Birth year' },
  { value: 'DOBM', label: 'Birth month' },
  { value: 'DOBD', label: 'Birth day' },
]

const hashHex = async (value: string) => {
  const bytes = new TextEncoder().encode(value)
  const digest = await crypto.subtle.digest('SHA-256', bytes)
  return Array.from(new Uint8Array(digest))
    .map((byte) => byte.toString(16).padStart(2, '0'))
    .join('')
}

const normalize = (schema: MetaCustomerListSchema, raw: string) => {
  const value = raw.trim().toLowerCase()
  if (schema === 'EXTERN_ID') return raw.trim()
  if (schema === 'EMAIL') return value.replace(/^\.+|\.+$/g, '')
  if (schema === 'PHONE' || schema === 'DOBY') return value.replace(/[^0-9]/g, '')
  if (schema === 'DOBM' || schema === 'DOBD') {
    const digits = value.replace(/[^0-9]/g, '')
    return digits.length === 1 ? `0${digits}` : digits
  }
  if (['FN', 'LN', 'FI', 'CT', 'ST'].includes(schema)) {
    return value.replace(/[^a-z]/g, '')
  }
  if (schema === 'ZIP') return value.split('-', 1)[0]
  if (schema === 'COUNTRY') return value.replace(/[^a-z]/g, '').slice(0, 2)
  if (schema === 'GEN') return value.slice(0, 1)
  return value
}

export default function MetaCustomerListUploadModal({
  audience,
  open,
  saving,
  onClose,
  onSubmit,
}: Props) {
  const [schema, setSchema] = useState<MetaCustomerListSchema>('EMAIL')
  const [values, setValues] = useState('')
  const [operation, setOperation] = useState<MetaCustomerListMembersInput['operation']>('ADD')
  const [termsAccepted, setTermsAccepted] = useState(false)
  const [hashing, setHashing] = useState(false)
  const [, setError] = useSnackbarState('', 'error')

  const rows = useMemo(
    () =>
      values
        .split(/\r?\n/)
        .map((value) => value.trim())
        .filter(Boolean),
    [values],
  )

  if (!open || !audience) return null

  const close = () => {
    setValues('')
    setTermsAccepted(false)
    setError('')
    onClose()
  }

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    if (!rows.length || !termsAccepted) return
    setHashing(true)
    setError('')
    try {
      const processed: string[][] = []
      for (const raw of rows) {
        const normalized = normalize(schema, raw)
        if (!normalized) continue
        processed.push([schema === 'EXTERN_ID' ? normalized : await hashHex(normalized)])
      }
      if (!processed.length) {
        setError('No valid customer identifiers remain after normalization.')
        return
      }
      await onSubmit({
        schema: [schema],
        rows: processed,
        operation,
        preHashed: true,
        customAudienceTermsAccepted: true,
      })
      setValues('')
      setTermsAccepted(false)
    } catch (submitError) {
      setError(
        submitError instanceof Error
          ? submitError.message
          : 'Unable to prepare customer-list identifiers',
      )
    } finally {
      setHashing(false)
    }
  }

  return (
    <div className="mobile-modal-shell fixed inset-0 z-50 flex items-center justify-center bg-slate-950/40 p-4">
      <div className="mobile-sheet max-h-[92vh] w-full max-w-2xl overflow-y-auto rounded-2xl bg-white shadow-2xl">
        <div className="flex items-start justify-between border-b border-slate-100 px-6 py-5">
          <div>
            <p className="eyebrow">Meta Customer List</p>
            <h2 className="mt-1 text-xl font-bold text-slate-950">Manage audience members</h2>
            <p className="mt-1 text-sm text-slate-500">{audience.name}</p>
          </div>
          <button
            type="button"
            className="rounded-lg p-2 text-slate-400 hover:bg-slate-100"
            onClick={close}
          >
            <FiX size={18} aria-hidden="true" />
          </button>
        </div>

        <form className="space-y-5 p-6" onSubmit={submit}>


          <div className="grid gap-4 sm:grid-cols-2">
            <label className="text-xs font-semibold text-slate-700">
              Identifier type
              <select
                className="form-input mt-2"
                value={schema}
                onChange={(event) => setSchema(event.target.value as MetaCustomerListSchema)}
              >
                {SCHEMA_OPTIONS.map((option) => (
                  <option key={option.value} value={option.value}>
                    {option.label}
                  </option>
                ))}
              </select>
            </label>

            <label className="text-xs font-semibold text-slate-700">
              Operation
              <select
                className="form-input mt-2"
                value={operation}
                onChange={(event) =>
                  setOperation(event.target.value as MetaCustomerListMembersInput['operation'])
                }
              >
                <option value="ADD">Add / match members</option>
                <option value="REMOVE">Remove members</option>
              </select>
            </label>
          </div>

          <label className="block text-xs font-semibold text-slate-700">
            Customer identifiers
            <textarea
              className="form-input mt-2 min-h-56 font-mono text-xs"
              value={values}
              placeholder={
                schema === 'EMAIL'
                  ? 'customer@example.com'
                  : schema === 'PHONE'
                    ? '+919876543210'
                    : 'one identifier per line'
              }
              onChange={(event) => setValues(event.target.value)}
            />
            <span className="mt-1 block font-normal leading-5 text-slate-400">
              {rows.length} row(s). Matching identifiers are normalized and SHA-256 hashed in your
              browser before they are sent to UnifyDesk. External customer IDs are sent as
              advertiser-defined IDs because Meta's multi-key schema treats them differently.
            </span>
          </label>

          <div className="rounded-xl border border-slate-200 bg-slate-50 p-4 text-xs leading-5 text-slate-600">
            <label className="flex items-start gap-2">
              <input
                className="mt-1"
                type="checkbox"
                checked={termsAccepted}
                onChange={(event) => setTermsAccepted(event.target.checked)}
              />
              <span>
                I confirm this business is authorized to use this customer data for advertising and
                has satisfied Meta's Custom Audience terms and applicable consent/privacy
                requirements.
              </span>
            </label>
          </div>

          <div className="rounded-xl border border-indigo-200 bg-indigo-50 px-4 py-3 text-xs leading-5 text-indigo-800">
            UnifyDesk does not save these uploaded rows or their hashes. Activity history stores
            only the operation, schema names, and aggregate row counts.
          </div>

          <div className="flex justify-end gap-2 border-t border-slate-100 pt-5">
            <button
              type="button"
              className="secondary-button"
              disabled={saving || hashing}
              onClick={close}
            >
              Cancel
            </button>
            <button
              type="submit"
              className={
                operation === 'REMOVE'
                  ? 'secondary-button inline-flex items-center gap-2'
                  : 'primary-button inline-flex items-center gap-2'
              }
              disabled={saving || hashing || !rows.length || !termsAccepted}
            >
              {operation === 'REMOVE' ? (
                <FiTrash2 aria-hidden="true" />
              ) : (
                <FiUploadCloud aria-hidden="true" />
              )}
              {saving || hashing
                ? 'Preparing…'
                : operation === 'REMOVE'
                  ? 'Remove members'
                  : 'Upload members'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}
