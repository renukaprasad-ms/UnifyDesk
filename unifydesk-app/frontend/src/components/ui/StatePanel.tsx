import type { ReactNode } from 'react'
import { FiAlertCircle, FiInbox, FiRefreshCw } from 'react-icons/fi'

export function EmptyState({
  title,
  description,
  action,
}: {
  title: string
  description?: string
  action?: ReactNode
}) {
  return (
    <div className="rounded-2xl border border-dashed border-slate-200 bg-white px-6 py-10 text-center">
      <span className="mx-auto flex size-11 items-center justify-center rounded-xl bg-slate-100 text-slate-500">
        <FiInbox aria-hidden="true" size={20} />
      </span>
      <h3 className="mt-4 text-sm font-bold text-slate-900">{title}</h3>
      {description && <p className="mx-auto mt-2 max-w-md text-xs leading-5 text-slate-500">{description}</p>}
      {action && <div className="mt-5 flex justify-center">{action}</div>}
    </div>
  )
}

export function ErrorState({
  title = 'Unable to load this content',
  description,
  retry,
}: {
  title?: string
  description?: string
  retry?: () => void
}) {
  return (
    <div className="rounded-2xl border border-rose-200 bg-rose-50/60 px-6 py-8 text-center" role="alert">
      <span className="mx-auto flex size-11 items-center justify-center rounded-xl bg-white text-rose-600">
        <FiAlertCircle aria-hidden="true" size={20} />
      </span>
      <h3 className="mt-4 text-sm font-bold text-rose-900">{title}</h3>
      {description && <p className="mx-auto mt-2 max-w-md text-xs leading-5 text-rose-700">{description}</p>}
      {retry && (
        <button type="button" className="secondary-button mt-5 inline-flex items-center gap-2" onClick={retry}>
          <FiRefreshCw aria-hidden="true" />
          Retry
        </button>
      )}
    </div>
  )
}
