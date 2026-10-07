import { useEffect, useMemo, useRef, useState } from 'react'
import { FiSearch, FiUser } from 'react-icons/fi'
import { contactsApi } from '../../features/contacts/contactsApi'
import type { ContactSummary } from '../../features/contacts/types'
import { useWorkspaceEpochScope } from '../../features/workspace/useWorkspaceEpochScope'

const currentRecipientToken = (value: string) => {
  const parts = value.split(/[;,]/)
  return parts[parts.length - 1]?.trim() ?? ''
}

const replaceCurrentToken = (value: string, email: string) => {
  const parts = value.split(/[;,]/)
  parts[parts.length - 1] = ' ' + email
  return parts.map((part) => part.trim()).filter(Boolean).join(', ') + ', '
}

type Props = {
  companyId: string
  workspaceEpoch: number
  label: string
  value: string
  onChange: (value: string) => void
  excludedEmails?: string[]
  autoFocus?: boolean
  placeholder?: string
}

export default function ContactRecipientInput({
  companyId,
  workspaceEpoch,
  label,
  value,
  onChange,
  excludedEmails = [],
  autoFocus = false,
  placeholder,
}: Props) {
  const { capture, isCurrent } = useWorkspaceEpochScope(companyId, workspaceEpoch)
  const [results, setResults] = useState<ContactSummary[]>([])
  const [loading, setLoading] = useState(false)
  const [open, setOpen] = useState(false)
  const blurTimer = useRef<number | null>(null)

  const token = currentRecipientToken(value)
  const excluded = useMemo(
    () => new Set(excludedEmails.map((email) => email.trim().toLowerCase()).filter(Boolean)),
    [excludedEmails],
  )

  useEffect(() => {
    if (token.length < 2) {
      setResults([])
      setLoading(false)
      return
    }

    const scope = capture()
    const timer = window.setTimeout(() => {
      setLoading(true)
      contactsApi
        .list(companyId, {
          q: token,
          status: 'ACTIVE',
          sort: 'UPDATED_DESC',
          size: 8,
        })
        .then((page) => {
          if (!isCurrent(scope)) return
          setResults(
            page.items.filter(
              (contact) =>
                Boolean(contact.primaryEmail) &&
                !excluded.has(contact.primaryEmail!.trim().toLowerCase()),
            ),
          )
        })
        .catch(() => {
          if (isCurrent(scope)) setResults([])
        })
        .finally(() => {
          if (isCurrent(scope)) setLoading(false)
        })
    }, 220)

    return () => window.clearTimeout(timer)
  }, [capture, companyId, excluded, isCurrent, token, workspaceEpoch])

  const selectContact = (contact: ContactSummary) => {
    if (!contact.primaryEmail) return
    onChange(replaceCurrentToken(value, contact.primaryEmail))
    setOpen(false)
    setResults([])
  }

  return (
    <label className="relative block text-xs font-semibold text-slate-600">
      {label}
      <div className="relative mt-1.5">
        <input
          type="text"
          className="form-input pr-9"
          value={value}
          onChange={(event) => {
            onChange(event.target.value)
            setOpen(true)
          }}
          onFocus={() => {
            if (blurTimer.current) window.clearTimeout(blurTimer.current)
            setOpen(true)
          }}
          onBlur={() => {
            blurTimer.current = window.setTimeout(() => setOpen(false), 120)
          }}
          placeholder={placeholder}
          autoFocus={autoFocus}
          autoComplete="off"
        />
        <FiSearch
          aria-hidden="true"
          className="pointer-events-none absolute right-3 top-1/2 -translate-y-1/2 text-slate-400"
        />
      </div>

      {open && token.length >= 2 && (
        <div className="absolute left-0 right-0 z-30 mt-2 overflow-hidden rounded-xl border border-slate-200 bg-white shadow-xl">
          {loading ? (
            <p className="px-3 py-3 text-xs font-normal text-slate-500">Searching contacts…</p>
          ) : results.length === 0 ? (
            <p className="px-3 py-3 text-xs font-normal text-slate-500">
              No workspace contacts found. You can still type an email address manually.
            </p>
          ) : (
            <div className="max-h-56 overflow-y-auto py-1">
              {results.map((contact) => (
                <button
                  key={contact.id}
                  type="button"
                  className="flex w-full items-center gap-3 px-3 py-2.5 text-left transition hover:bg-slate-50"
                  onMouseDown={(event) => event.preventDefault()}
                  onClick={() => selectContact(contact)}
                >
                  <span className="flex size-8 shrink-0 items-center justify-center rounded-full bg-indigo-50 text-indigo-600">
                    <FiUser aria-hidden="true" />
                  </span>
                  <span className="min-w-0 flex-1">
                    <span className="block truncate text-xs font-semibold text-slate-900">
                      {contact.displayName}
                    </span>
                    <span className="block truncate text-[11px] font-normal text-slate-500">
                      {contact.primaryEmail}
                    </span>
                  </span>
                </button>
              ))}
            </div>
          )}
        </div>
      )}
    </label>
  )
}
