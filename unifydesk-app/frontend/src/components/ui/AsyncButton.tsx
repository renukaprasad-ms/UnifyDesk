import type { ButtonHTMLAttributes, ReactNode } from 'react'
import Spinner from './Spinner'

type AsyncButtonProps = ButtonHTMLAttributes<HTMLButtonElement> & {
  loading?: boolean
  loadingLabel?: string
  children: ReactNode
}

export default function AsyncButton({
  loading = false,
  loadingLabel = 'Working…',
  disabled,
  children,
  className = '',
  ...props
}: AsyncButtonProps) {
  return (
    <button
      {...props}
      disabled={disabled || loading}
      aria-busy={loading}
      className={`inline-flex items-center justify-center gap-2 disabled:cursor-not-allowed disabled:opacity-55 ${className}`}
    >
      {loading && <Spinner size="sm" label={loadingLabel} />}
      <span>{loading ? loadingLabel : children}</span>
    </button>
  )
}
