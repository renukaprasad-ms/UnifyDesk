import { useEffect, useId, useRef, useState, type InputHTMLAttributes } from 'react'
import {
  getCountries,
  getCountryCallingCode,
  parsePhoneNumberFromString,
  type CountryCode,
} from 'libphonenumber-js/max'
import { COUNTRIES, findCountry } from '../../data/countriesAndCities'
import { phoneValidationMessage } from '../../utils/phone'

type Props = Omit<
  InputHTMLAttributes<HTMLInputElement>,
  'value' | 'onChange' | 'type' | 'country'
> & {
  value: string
  onChange: (value: string) => void
  country?: string | null
}
const supported = new Set<string>(getCountries())
const countries = COUNTRIES.filter((country) => supported.has(country.code))

export default function PhoneInput({
  value,
  onChange,
  country,
  className = '',
  id,
  ...props
}: Props) {
  const generatedId = useId()
  const inputId = id ?? generatedId
  const requestedCountry = findCountry(country)?.code
  const initialCountry = (
    requestedCountry && supported.has(requestedCountry) ? requestedCountry : 'IN'
  ) as CountryCode
  const [selectedCountry, setSelectedCountry] = useState<CountryCode>(initialCountry)
  const input = useRef<HTMLInputElement>(null)
  const parsed = value.startsWith('+') ? parsePhoneNumberFromString(value) : undefined
  const activeCountry = parsed?.country ?? selectedCountry
  const dialCode = getCountryCallingCode(activeCountry)
  const national = (
    value.startsWith(`+${dialCode}`)
      ? value.slice(dialCode.length + 1)
      : (parsed?.nationalNumber.toString() ?? value)
  ).replace(/\D/g, '')
  const error = phoneValidationMessage(value, activeCountry)
  useEffect(() => {
    input.current?.setCustomValidity(error)
  }, [error])
  const [touched, setTouched] = useState(false)
  return (
    <div className="space-y-1">
      <div className="flex min-w-0 gap-2">
        <select
          aria-label="Phone country code"
          className="form-input w-32 max-w-[45%] shrink-0"
          disabled={props.disabled}
          value={activeCountry}
          onChange={(event) => {
            const next = event.target.value as CountryCode
            setSelectedCountry(next)
            onChange(national ? `+${getCountryCallingCode(next)}${national}` : '')
          }}
        >
          {countries.map((item) => (
            <option key={item.code} value={item.code}>
              {item.name} (+{getCountryCallingCode(item.code as CountryCode)})
            </option>
          ))}
        </select>
        <input
          {...props}
          id={inputId}
          ref={input}
          type="tel"
          inputMode="numeric"
          autoComplete="tel-national"
          aria-label={props['aria-label'] ?? 'Phone number'}
          className={`form-input min-w-0 flex-1 ${className}`}
          value={national}
          placeholder={
            props.placeholder ?? (activeCountry === 'IN' ? '10-digit number' : 'Phone number')
          }
          aria-invalid={!!error}
          aria-describedby={error && touched ? `${inputId}-error` : props['aria-describedby']}
          onBlur={(event) => {
            setTouched(true)
            props.onBlur?.(event)
          }}
          onInvalid={() => setTouched(true)}
          onChange={(event) => {
            const raw = event.target.value
            const pasted = raw.startsWith('+') ? parsePhoneNumberFromString(raw) : undefined
            if (pasted?.country) setSelectedCountry(pasted.country)
            const digits = raw.replace(/\D/g, '')
            onChange(pasted?.number ?? (digits ? `+${dialCode}${digits}` : ''))
          }}
        />
      </div>
      {error && touched && (
        <p id={`${inputId}-error`} className="text-xs text-rose-600">
          {error}
        </p>
      )}
    </div>
  )
}
