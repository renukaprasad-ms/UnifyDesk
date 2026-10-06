import { useState, type FormEvent } from 'react'
import type { ContactIdentity, IdentityType } from '../../features/contacts/types'
import ConfirmDialog from '../ui/ConfirmDialog'

type Props = {
  identities: ContactIdentity[]
  canModify: boolean
  busy: boolean
  onAdd: (type: IdentityType, value: string, namespace?: string | null) => Promise<void>
  onPrimary: (identityId: string) => Promise<void>
  onRemove: (identityId: string) => Promise<void>
}

export default function IdentityManager({
  identities,
  canModify,
  busy,
  onAdd,
  onPrimary,
  onRemove,
}: Props) {
  const [type, setType] = useState<IdentityType>('EMAIL')
  const [value, setValue] = useState('')
  const [namespace, setNamespace] = useState('')
  const [identityToRemove, setIdentityToRemove] = useState<ContactIdentity | null>(null)

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    if (!value.trim() || (type === 'EXTERNAL' && !namespace.trim())) return
    await onAdd(type, value.trim(), type === 'EXTERNAL' ? namespace.trim() : null)
    setValue('')
    setNamespace('')
  }

  return (
    <section className="panel p-5">
      <h2 className="text-lg font-bold text-slate-950">Identities</h2>
      <p className="mt-1 text-sm text-slate-500">
        Email, phone and external identities are separate from the Contact profile.
      </p>

      <div className="mt-4 space-y-2">
        {identities.length === 0 && (
          <p className="rounded-xl bg-slate-50 p-4 text-sm text-slate-500">
            No identities have been added.
          </p>
        )}
        {identities.map((identity) => (
          <div
            key={identity.id}
            className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-slate-200 p-3"
          >
            <div className="min-w-0">
              <p className="text-xs font-bold uppercase tracking-wide text-slate-500">
                {identity.type}
              </p>
              <p className="truncate text-sm font-medium text-slate-900">{identity.value}</p>
            </div>
            <div className="flex flex-wrap items-center gap-2">
              {identity.primary ? (
                <span className="rounded-full bg-indigo-50 px-2 py-1 text-xs font-bold text-indigo-700">
                  Primary
                </span>
              ) : canModify ? (
                <button
                  type="button"
                  className="secondary-button"
                  disabled={busy}
                  onClick={() => void onPrimary(identity.id)}
                >
                  Make primary
                </button>
              ) : null}
              {canModify && (
                <button
                  type="button"
                  className="secondary-button text-rose-700"
                  disabled={busy}
                  onClick={() => setIdentityToRemove(identity)}
                >
                  Remove
                </button>
              )}
            </div>
          </div>
        ))}
      </div>

      {canModify && (
        <form className="mt-4 grid gap-2 sm:grid-cols-[150px_minmax(0,1fr)_auto]" onSubmit={submit}>
          <label className="sr-only" htmlFor="identity-type">
            Identity type
          </label>
          <select
            id="identity-type"
            className="form-input"
            value={type}
            onChange={(event) => setType(event.target.value as IdentityType)}
          >
            <option value="EMAIL">Email</option>
            <option value="PHONE">Phone</option>
            <option value="EXTERNAL">External</option>
          </select>
          <label className="sr-only" htmlFor="identity-value">
            Identity value
          </label>
          <input
            id="identity-value"
            className="form-input"
            value={value}
            placeholder={
              type === 'EMAIL' ? 'name@example.com' : type === 'PHONE' ? '+91…' : 'External ID'
            }
            onChange={(event) => setValue(event.target.value)}
          />
          {type === 'EXTERNAL' && (
            <>
              <label className="sr-only" htmlFor="identity-namespace">
                External identity namespace
              </label>
              <input
                id="identity-namespace"
                className="form-input sm:col-start-2"
                value={namespace}
                placeholder="Namespace, e.g. crm"
                onChange={(event) => setNamespace(event.target.value)}
              />
            </>
          )}
          <button
            className="primary-button"
            disabled={busy || !value.trim() || (type === 'EXTERNAL' && !namespace.trim())}
          >
            Add identity
          </button>
        </form>
      )}
      <ConfirmDialog
        open={Boolean(identityToRemove)}
        title="Remove this identity?"
        description={identityToRemove ? `Remove ${identityToRemove.value} from this contact? The contact record itself will remain.` : ''}
        confirmLabel="Yes, remove identity"
        cancelLabel="No, keep identity"
        tone="warning"
        working={busy}
        onCancel={() => setIdentityToRemove(null)}
        onConfirm={() => {
          if (!identityToRemove) return
          void onRemove(identityToRemove.id).finally(() => setIdentityToRemove(null))
        }}
      />
    </section>
  )
}
