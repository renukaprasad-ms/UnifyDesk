import type { ReactNode } from 'react'

export default function FormField({
  label,
  htmlFor,
  hint,
  error,
  required = false,
  children,
  className = '',
}: {
  label: ReactNode
  htmlFor?: string
  hint?: ReactNode
  error?: ReactNode
  required?: boolean
  children: ReactNode
  className?: string
}) {
  const labelContent = (
    <>
      {label}
      {required && <span className="ml-1 text-rose-600" aria-hidden="true">*</span>}
    </>
  )

  return (
    <div className={className}>
      {htmlFor ? (
        <label htmlFor={htmlFor} className="mb-1.5 block text-xs font-semibold text-slate-700">
          {labelContent}
        </label>
      ) : (
        <div className="mb-1.5 block text-xs font-semibold text-slate-700">{labelContent}</div>
      )}
      {children}
      {error ? (
        <p className="mt-1.5 text-xs font-semibold text-rose-600" role="alert">{error}</p>
      ) : hint ? (
        <p className="mt-1.5 text-xs leading-5 text-slate-500">{hint}</p>
      ) : null}
    </div>
  )
}
