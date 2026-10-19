import { useSnackbarState } from '../feedback/useSnackbarState'
import { useEffect, useState, type FormEvent } from 'react'
import PhoneInput from '../ui/PhoneInput'
import {
  emptyContactDraft,
  emptyContactMarketingPreferences,
  validateContactDraft,
  type ContactFormDraft,
  type ContactMarketingPreferences,
} from '../../features/contacts/contactModel'

type Props = {
  open: boolean
  title: string
  initial?: ContactFormDraft
  saving: boolean
  onClose: () => void
  onSubmit: (draft: ContactFormDraft, marketing: ContactMarketingPreferences) => void
  showIdentityFields?: boolean
  showMarketingPermissions?: boolean
  initialMarketingPreferences?: ContactMarketingPreferences
}

export default function ContactFormDialog({
  open,
  title,
  initial,
  saving,
  onClose,
  onSubmit,
  showIdentityFields = true,
  showMarketingPermissions = false,
  initialMarketingPreferences = emptyContactMarketingPreferences,
}: Props) {
  const [draft, setDraft] = useState<ContactFormDraft>(initial ?? emptyContactDraft)
  const [marketing, setMarketing] = useState<ContactMarketingPreferences>(
    initialMarketingPreferences,
  )
  const [marketingConfirmed, setMarketingConfirmed] = useState(false)
  const [marketingError, setMarketingError] = useSnackbarState('', 'error')
  const [errors, setErrors] = useState<Partial<Record<keyof ContactFormDraft, string>>>({})

  useEffect(() => {
    if (open) {
      setDraft(initial ?? emptyContactDraft)
      setMarketing(initialMarketingPreferences)
      setMarketingConfirmed(false)
      setMarketingError('')
      setErrors({})
    }
  }, [initial, initialMarketingPreferences, open])

  useEffect(() => {
    if (!open || !showMarketingPermissions) return
    setMarketing((current) => ({
      EMAIL: draft.email.trim() ? current.EMAIL : 'UNKNOWN',
      SMS: draft.phone.trim() ? current.SMS : 'UNKNOWN',
      WHATSAPP: draft.phone.trim() ? current.WHATSAPP : 'UNKNOWN',
    }))
  }, [draft.email, draft.phone, open, showMarketingPermissions])

  if (!open) return null

  const submit = (event: FormEvent) => {
    event.preventDefault()
    const nextErrors = validateContactDraft(draft, showIdentityFields)
    setErrors(nextErrors)
    const firstError = Object.values(nextErrors).find(Boolean)
    if (firstError) showGlobalSnackbar(firstError, { variant: 'error' })

    const hasOptIn = Object.values(marketing).includes('OPTED_IN')
    if (showMarketingPermissions && hasOptIn && !marketingConfirmed) {
      setMarketingError(
        'Confirm that this person gave permission for the selected marketing channels.',
      )
      return
    }

    setMarketingError('')
    if (Object.keys(nextErrors).length === 0) onSubmit(draft, marketing)
  }

  const field = (key: keyof ContactFormDraft, label: string, type = 'text') => (
    <div className="block text-xs font-semibold text-slate-700">
      <label htmlFor={`contact-field-${key}`} className="mb-2 block">
        {label}
      </label>
      {key === 'phone' ? (
        <PhoneInput
          id={`contact-field-${key}`}
          value={draft.phone}
          onChange={(value) => setDraft((current) => ({ ...current, phone: value }))}
          aria-label={label}
        />
      ) : (
        <input
          id={`contact-field-${key}`}
          className="form-input"
          type={type}
          autoFocus={key === 'displayName'}
          value={draft[key]}
          onChange={(event) => setDraft((current) => ({ ...current, [key]: event.target.value }))}
          aria-invalid={Boolean(errors[key])}
          aria-describedby={errors[key] ? 'contact-' + key + '-error' : undefined}
        />
      )}
      {errors[key] && (
        <span id={'contact-' + key + '-error'} className="mt-1 block text-xs text-rose-600">
          {errors[key]}
        </span>
      )}
    </div>
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
          Add the basics now. If display name is blank, UnifyDesk will use the person's name, email
          or phone automatically.
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

        {showMarketingPermissions && (
          <section className="mt-5 rounded-2xl border border-slate-200 bg-slate-50 p-4">
            <div>
              <p className="text-sm font-bold text-slate-900">Marketing permissions</p>
              <p className="mt-1 text-xs leading-5 text-slate-500">
                Adding a contact does not subscribe them automatically. Only choose Subscribed when
                you have permission to send marketing on that channel.
              </p>
            </div>

            <div className="mt-4 grid gap-3 sm:grid-cols-3">
              {(
                [
                  ['EMAIL', 'Email', Boolean(draft.email.trim())],
                  ['SMS', 'SMS', Boolean(draft.phone.trim())],
                  ['WHATSAPP', 'WhatsApp', Boolean(draft.phone.trim())],
                ] as const
              ).map(([channel, label, available]) => (
                <label key={channel} className="text-xs font-semibold text-slate-700">
                  <span className="mb-2 block">{label}</span>
                  <select
                    className="form-input"
                    value={marketing[channel]}
                    disabled={!available}
                    onChange={(event) => {
                      const state = event.target
                        .value as ContactMarketingPreferences[typeof channel]
                      setMarketing((current) => ({ ...current, [channel]: state }))
                      setMarketingError('')
                    }}
                  >
                    <option value="UNKNOWN">Permission unknown</option>
                    <option value="OPTED_IN">Subscribed</option>
                    <option value="OPTED_OUT">Opted out</option>
                  </select>
                  {!available && (
                    <span className="mt-1 block text-[10px] font-normal text-slate-400">
                      {channel === 'EMAIL' ? 'Add an email first.' : 'Add a phone first.'}
                    </span>
                  )}
                </label>
              ))}
            </div>

            {Object.values(marketing).includes('OPTED_IN') && (
              <label className="mt-4 flex items-start gap-2 rounded-xl border border-amber-200 bg-amber-50 px-3 py-2.5 text-xs text-amber-900">
                <input
                  type="checkbox"
                  className="mt-0.5"
                  checked={marketingConfirmed}
                  onChange={(event) => {
                    setMarketingConfirmed(event.target.checked)
                    setMarketingError('')
                  }}
                />
                <span>
                  I confirm this person gave permission for the selected marketing channel(s).
                </span>
              </label>
            )}

            {marketingError && (
              <p className="mt-3 text-xs font-semibold text-rose-600">{marketingError}</p>
            )}
          </section>
        )}
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
import { showGlobalSnackbar } from '../feedback/SnackbarProvider'
