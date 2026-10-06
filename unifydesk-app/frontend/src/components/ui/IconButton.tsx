import type { ButtonHTMLAttributes, ReactNode } from 'react'

type IconButtonProps = Omit<ButtonHTMLAttributes<HTMLButtonElement>, 'children'> & {
  label: string
  icon: ReactNode
  tone?: 'default' | 'danger'
}

export default function IconButton({
  label,
  icon,
  tone = 'default',
  className = '',
  ...props
}: IconButtonProps) {
  return (
    <button
      {...props}
      type={props.type ?? 'button'}
      aria-label={label}
      title={label}
      className={`inline-flex size-9 items-center justify-center rounded-lg border transition disabled:cursor-not-allowed disabled:opacity-50 ${
        tone === 'danger'
          ? 'border-rose-200 text-rose-600 hover:bg-rose-50'
          : 'border-slate-200 text-slate-500 hover:bg-slate-50 hover:text-slate-800'
      } ${className}`}
    >
      {icon}
    </button>
  )
}
