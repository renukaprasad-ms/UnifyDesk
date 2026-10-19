import { parsePhoneNumberFromString, type CountryCode } from 'libphonenumber-js/max'

export function phoneValidationMessage(value: string, country: CountryCode = 'IN'): string {
  if (!value.trim()) return ''
  if (!/^\+?[\d ()-]+$/.test(value.trim()))
    return 'Enter a valid phone number for the selected country code.'
  const parsed = parsePhoneNumberFromString(value, country)
  const digits = value.replace(/\D/g, '')
  const nationalLength = value.trim().startsWith('+91') ? digits.length - 2 : digits.length
  if ((parsed?.country ?? country) === 'IN' && nationalLength !== 10) {
    return 'Enter exactly 10 digits for an Indian phone number.'
  }
  return parsed?.isValid() ? '' : 'Enter a valid phone number for the selected country code.'
}
