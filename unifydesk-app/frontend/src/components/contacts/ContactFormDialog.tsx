import { useEffect, useState, type FormEvent } from 'react'
import {
  emptyContactDraft,
  validateContactDraft,
  type ContactFormDraft,
} from '../../features/contacts/contactModel'

type Props = {
  open: boolean
  title: string
  initial?: ContactFormDraft
  saving: boolean
  onClose: () => void
  onSubmit: (draft: ContactFormDraft) => void
  showIdentityFields?: boolean
}

export default function ContactFormDialog({
  open,
  title,
  initial,
  saving,
  onClose,
  onSubmit,
  showIdentityFields = true,
}: Props) {
  const [draft, setDraft] = useState<ContactFormDraft>(initial ?? emptyContactDraft)
  const [errors, setErrors] = useState<Partial<Record<keyof ContactFormDraft, string>>>({})

  useEffect(() => {
    if (open) {
      setDraft(initial ?? emptyContactDraft)
      setErrors({})
    }
  }, [open, initial])

  if (!open) return null

  const submit = (event: FormEvent) => {
    event.preventDefault()
    const nextErrors = validateContactDraft(draft, showIdentityFields)
    setErrors(nextErrors)
    if (Object.keys(nextErrors).length === 0) onSubmit(draft)
  }

  const field = (key: keyof ContactFormDraft, label: string, type = 'text') => (
    <label className="block text-xs font-semibold text-slate-700">
      <span className="mb-2 block">{label}</span>
      <input
        className="form-input"
        type={type}
        autoFocus={key === 'displayName'}
        value={draft[key]}
        onChange={(event) => setDraft((current) => ({ ...current, [key]: event.target.value }))}
        aria-invalid={Boolean(errors[key])}
        aria-describedby={errors[key] ? 'contact-' + key + '-error' : undefined}
      />
      {errors[key] && (
        <span id={'contact-' + key + '-error'} className="mt-1 block text-xs text-rose-600">
          {errors[key]}
        </span>
      )}
    </label>
  )

  return (
    <div
      className="mobile-modal-shell fixed inset-0 z-50 flex items-center justify-center bg-slate-950/35 p-4"
      role="presentation"
    >
      <form
        role="dialog"
        aria-modal="true"
        aria-labelledby="contact-dialog-title"
        className="mobile-sheet w-full max-w-2xl rounded-2xl bg-white p-5 shadow-2xl"
        onSubmit={submit}
        onKeyDown={(event) => {
          if (event.key === 'Escape') {
            event.preventDefault()
            onClose()
          }
        }}
      >
        <div className="flex items-center justify-between gap-4">
          <h2 id="contact-dialog-title" className="text-xl font-bold text-slate-950">
            {title}
          </h2>
          <button type="button" className="secondary-button" onClick={onClose}>
            Close
          </button>
        </div>
        <p className="mt-3 text-sm text-slate-500">
          Add the basics now. If display name is blank, UnifyDesk will use the person's name,
          email or phone automatically.
        </p>
        <div className="mt-5 grid gap-4 sm:grid-cols-2">
          {field('displayName', 'Display name (optional)')}
          {field('organization', 'Organization')}
          {field('firstName', 'First name')}
          {field('lastName', 'Last name')}
          {showIdentityFields && field('email', 'Primary email', 'email')}
          {showIdentityFields && field('phone', 'Primary phone', 'tel')}
          <label className="block text-xs font-semibold text-slate-700 sm:col-span-2">
            <span className="mb-2 block">Notes</span>
            <textarea
              className="form-input min-h-24"
              value={draft.notes}
              onChange={(event) =>
                setDraft((current) => ({ ...current, notes: event.target.value }))
              }
            />
          </label>
        </div>
        <div className="mt-6 flex justify-end gap-2">
          <button type="button" className="secondary-button" onClick={onClose} disabled={saving}>
            Cancel
          </button>
          <button type="submit" className="primary-button" disabled={saving}>
            {saving ? 'Saving…' : 'Save contact'}
          </button>
        </div>
      </form>
    </div>
  )
}
