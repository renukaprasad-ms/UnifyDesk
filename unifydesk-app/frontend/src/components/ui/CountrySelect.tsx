import React from 'react'
import { FiGlobe } from 'react-icons/fi'
import { COUNTRIES, findCountry } from '../../data/countriesAndCities'

export interface CountrySelectProps {
  value: string
  onChange: (value: string) => void
  valueType?: 'code' | 'name'
  disabled?: boolean
  className?: string
  placeholder?: string
  required?: boolean
  id?: string
  name?: string
  showIcon?: boolean
}

export const CountrySelect: React.FC<CountrySelectProps> = ({
  value,
  onChange,
  valueType = 'code',
  disabled = false,
  className = '',
  placeholder = 'Select country',
  required = false,
  id,
  name,
  showIcon = false,
}) => {
  // Determine matched country
  const matched = findCountry(value)
  const selectedValue = matched ? (valueType === 'code' ? matched.code : matched.name) : value

  const handleChange = (e: React.ChangeEvent<HTMLSelectElement>) => {
    const selectedCode = e.target.value
    const found = COUNTRIES.find((c) => c.code === selectedCode)
    if (found) {
      onChange(valueType === 'code' ? found.code : found.name)
    } else {
      onChange(selectedCode)
    }
  }

  const selectElement = (
    <select
      id={id}
      name={name}
      disabled={disabled}
      required={required}
      value={matched ? matched.code : selectedValue}
      onChange={handleChange}
      className={`form-input bg-white dark:bg-slate-900 cursor-pointer ${
        showIcon ? 'pl-9' : ''
      } ${className}`}
    >
      <option value="" disabled>
        {placeholder}
      </option>
      {COUNTRIES.map((country) => (
        <option key={country.code} value={country.code}>
          {country.flag} {country.name} ({country.code})
        </option>
      ))}
    </select>
  )

  if (showIcon) {
    return (
      <div className="relative">
        <FiGlobe
          className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-slate-400"
          aria-hidden="true"
        />
        {selectElement}
      </div>
    )
  }

  return selectElement
}

export default CountrySelect
