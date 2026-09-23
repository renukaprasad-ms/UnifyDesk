import { useEffect, useMemo, useState, type FormEvent } from 'react'
import { FiAlertTriangle, FiX } from 'react-icons/fi'

type Props = {
  open: boolean
  saving: boolean
  currency?: string | null
  currentSpendCapMinor?: number | null
  amountSpentMinor?: number | null
  onClose: () => void
  onSave: (spendCapMinor: number) => Promise<void>
}

const fractionDigits = (currency?: string | null) => {
  if (!currency) return 2
  try {
    return new Intl.NumberFormat(undefined, {
      style: 'currency',
      currency,
    }).resolvedOptions().maximumFractionDigits ?? 2
  } catch {
    return 2
  }
}

const minorToMajor = (value: number, currency?: string | null) =>
  value / 10 ** fractionDigits(currency)

const majorToMinor = (value: number, currency?: string | null) =>
  Math.round(value * 10 ** fractionDigits(currency))

export default function MetaSpendCapModal({
  open,
  saving,
  currency,
  currentSpendCapMinor,
  amountSpentMinor,
  onClose,
  onSave,
}: Props) {
  const currentMajor = useMemo(
    () =>
      currentSpendCapMinor && currentSpendCapMinor > 0
        ? minorToMajor(currentSpendCapMinor, currency)
        : 0,
    [currentSpendCapMinor, currency],
  )
  const [value, setValue] = useState(currentMajor > 0 ? String(currentMajor) : '')

  useEffect(() => {
    if (open) {
      setValue(currentMajor > 0 ? String(currentMajor) : '')
    }
  }, [open, currentMajor])

  if (!open) return null

  const parsed = Number(value)
  const valid = Number.isFinite(parsed) && parsed >= 0
  const nextMinor = valid ? majorToMinor(parsed, currency) : 0
  const nearOrBelowSpent =
    valid &&
    nextMinor > 0 &&
    amountSpentMinor != null &&
    nextMinor <= amountSpentMinor

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    if (!valid) return
    await onSave(nextMinor)
  }

  return (
    <div className="mobile-modal-shell fixed inset-0 z-50 flex items-center justify-center bg-slate-950/40 p-4">
      <div className="mobile-sheet w-full max-w-lg rounded-2xl bg-white shadow-2xl">
        <div className="flex items-start justify-between border-b border-slate-100 px-6 py-5">
          <div>
            <p className="eyebrow">Meta Ads</p>
            <h2 className="mt-1 text-xl font-bold text-slate-950">Account spending limit</h2>
            <p className="mt-1 text-sm leading-6 text-slate-500">
              Update the account-level spend cap Meta applies across campaigns.
            </p>
          </div>
          <button
            type="button"
            className="rounded-lg p-2 text-slate-400 hover:bg-slate-100"
            onClick={onClose}
          >
            <FiX size={18} aria-hidden="true" />
          </button>
        </div>

        <form className="space-y-5 p-6" onSubmit={submit}>
          <label className="block text-xs font-semibold text-slate-700">
            Spend cap {currency ? `(${currency})` : ''}
            <input
              className="form-input mt-2"
              type="number"
              min="0"
              step={fractionDigits(currency) === 0 ? '1' : '0.01'}
              value={value}
              placeholder="0 = no account spending limit"
              autoFocus
              onChange={(event) => setValue(event.target.value)}
            />
            <span className="mt-2 block font-normal leading-5 text-slate-400">
              Enter 0 to remove the account spending limit. Campaign and ad-set budgets remain unchanged.
            </span>
          </label>

          <div className="rounded-xl border border-amber-200 bg-amber-50 px-4 py-3">
            <div className="flex items-start gap-3">
              <FiAlertTriangle className="mt-0.5 shrink-0 text-amber-700" aria-hidden="true" />
              <div className="text-xs leading-5 text-amber-800">
                <p className="font-semibold">This can affect live delivery.</p>
                <p className="mt-1">
                  When Meta considers the account cap reached, campaign delivery can stop until the limit is changed or reset.
                </p>
              </div>
            </div>
          </div>

          {nearOrBelowSpent && (
            <div className="rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-xs leading-5 text-red-700">
              The new cap is at or below Meta's currently reported amount spent. This may immediately restrict delivery depending on Meta's spend-cap state.
            </div>
          )}

          <div className="flex justify-end gap-2 border-t border-slate-100 pt-5">
            <button
              type="button"
              className="secondary-button"
              onClick={onClose}
              disabled={saving}
            >
              Cancel
            </button>
            <button
              type="submit"
              className="primary-button"
              disabled={saving || !valid}
            >
              {saving ? 'Updating…' : nextMinor === 0 ? 'Remove limit' : 'Update limit'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}
