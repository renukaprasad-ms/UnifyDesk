import { useEffect, useState } from 'react'
import { showGlobalSnackbar } from '../feedback/SnackbarProvider'

export type LocationSelectProps = {
  value: string
  onChange: (value: string) => void
  options: string[]
  disabled?: boolean
  required?: boolean
  id?: string
  name?: string
  className?: string
  placeholder?: string
  showIcon?: boolean
  loading?: boolean
  error?: boolean
}

export default function LocationSelect({
  value,
  onChange,
  options,
  disabled,
  required,
  id,
  name,
  className = '',
  placeholder = 'Select location',
  loading,
  error,
}: LocationSelectProps) {
  const [manual, setManual] = useState(false)
  useEffect(() => {
    if (error) showGlobalSnackbar('Locations could not load. Select Other to enter manually.', { variant: 'warning' })
  }, [error])
  const custom = manual || (!!value && !loading && !options.includes(value))
  return (
    <div className="space-y-2">
      <select
        id={custom && id ? `${id}-select` : id}
        name={custom ? undefined : name}
        aria-label={custom ? `${placeholder} options` : undefined}
        className={`form-input ${className}`}
        disabled={disabled}
        required={required && !custom}
        value={custom ? '__other__' : value}
        onChange={(event) => {
          const other = event.target.value === '__other__'
          setManual(other)
          onChange(other ? '' : event.target.value)
        }}
      >
        <option value="">{loading ? 'Loading locations…' : placeholder}</option>
        {options.map((option) => (
          <option key={option} value={option}>
            {option}
          </option>
        ))}
        <option value="__other__">Other — enter manually</option>
      </select>
      {custom && (
        <input
          id={id}
          name={name}
          aria-label={`Enter ${placeholder.replace(/^Select (or enter )?/i, '').toLowerCase()} manually`}
          className={`form-input ${className}`}
          value={value}
          disabled={disabled}
          required={required}
          placeholder="Type manually"
          maxLength={120}
          onChange={(event) => onChange(event.target.value)}
        />
      )}
      {error && (
        <p className="text-xs text-amber-700">
          Locations could not load. Select Other to enter manually.
        </p>
      )}
    </div>
  )
}
