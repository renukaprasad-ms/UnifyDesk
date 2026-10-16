import React, { useId } from 'react'
import { FiMapPin } from 'react-icons/fi'
import { getCitiesForCountry } from '../../data/countriesAndCities'

export interface CitySelectProps {
  country?: string | null
  value: string
  onChange: (value: string) => void
  disabled?: boolean
  className?: string
  placeholder?: string
  required?: boolean
  id?: string
  name?: string
  showIcon?: boolean
}

export const CitySelect: React.FC<CitySelectProps> = ({
  country,
  value,
  onChange,
  disabled = false,
  className = '',
  placeholder = 'Select or enter city',
  required = false,
  id,
  name,
  showIcon = false,
}) => {
  const datalistId = useId()
  const cities = getCitiesForCountry(country)

  const inputElement = (
    <>
      <input
        id={id}
        name={name}
        type="text"
        list={datalistId}
        disabled={disabled}
        required={required}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        placeholder={placeholder}
        className={`form-input bg-white dark:bg-slate-900 ${showIcon ? 'pl-9' : ''} ${className}`}
        autoComplete="address-level2"
      />
      <datalist id={datalistId}>
        {cities.map((city) => (
          <option key={city} value={city} />
        ))}
      </datalist>
    </>
  )

  if (showIcon) {
    return (
      <div className="relative">
        <FiMapPin
          className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-slate-400"
          aria-hidden="true"
        />
        {inputElement}
      </div>
    )
  }

  return inputElement
}

export default CitySelect
