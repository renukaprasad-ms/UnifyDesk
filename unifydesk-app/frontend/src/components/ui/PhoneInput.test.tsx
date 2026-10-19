import { useState } from 'react'
import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import PhoneInput from './PhoneInput'
import { phoneValidationMessage } from '../../utils/phone'

function Example() {
  const [value, setValue] = useState('')
  return (
    <form>
      <PhoneInput value={value} onChange={setValue} aria-label="Phone number" />
      <output>{value}</output>
    </form>
  )
}

describe('PhoneInput', () => {
  it('keeps the country code separate and validates India at exactly ten digits', () => {
    render(<Example />)
    const input = screen.getByRole('textbox', { name: 'Phone number' })
    fireEvent.change(input, { target: { value: '987654321' } })
    expect(input).toBeInvalid()
    fireEvent.change(input, { target: { value: '9876543210' } })
    expect(input).toHaveValue('9876543210')
    expect(input).toBeValid()
    expect(screen.getByRole('status')).toHaveTextContent('+919876543210')
    fireEvent.change(input, { target: { value: '98765432101' } })
    expect(input).toBeInvalid()
  })

  it('supports international paste and country-specific lengths', () => {
    render(<Example />)
    fireEvent.change(screen.getByRole('textbox'), { target: { value: '+4915123456789' } })
    expect(screen.getByLabelText('Phone country code')).toHaveValue('DE')
    expect(screen.getByRole('textbox')).toHaveValue('15123456789')
    expect(screen.getByRole('textbox')).toBeValid()
    expect(phoneValidationMessage('+12025550123')).toBe('')
    expect(phoneValidationMessage('+91987654321')).not.toBe('')
    expect(phoneValidationMessage('+4915')).not.toBe('')
  })
})
