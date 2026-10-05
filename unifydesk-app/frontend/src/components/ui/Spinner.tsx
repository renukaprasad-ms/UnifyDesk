type SpinnerProps = {
  size?: 'sm' | 'md' | 'lg'
  label?: string
  className?: string
}

const sizes = {
  sm: 'size-3.5 border-2',
  md: 'size-4 border-2',
  lg: 'size-6 border-[3px]',
}

export default function Spinner({ size = 'md', label = 'Loading', className = '' }: SpinnerProps) {
  return (
    <span
      className={`inline-flex shrink-0 animate-spin rounded-full border-current border-r-transparent ${sizes[size]} ${className}`}
      role="status"
      aria-label={label}
    />
  )
}
