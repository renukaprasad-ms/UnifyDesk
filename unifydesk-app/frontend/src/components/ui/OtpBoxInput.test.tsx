import { describe, it, expect, vi } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import OtpBoxInput from './OtpBoxInput'

describe('OtpBoxInput', () => {
  it('renders correct number of input boxes', () => {
    render(<OtpBoxInput length={6} value="" onChange={vi.fn()} />)
    const inputs = screen.getAllByRole('textbox')
    expect(inputs).toHaveLength(6)
  })

  it('renders digits from value', () => {
    render(<OtpBoxInput length={6} value="123" onChange={vi.fn()} />)
    const inputs = screen.getAllByRole('textbox') as HTMLInputElement[]
    expect(inputs[0].value).toBe('1')
    expect(inputs[1].value).toBe('2')
    expect(inputs[2].value).toBe('3')
    expect(inputs[3].value).toBe('')
  })

  it('handles typing and triggers onChange', () => {
    const handleChange = vi.fn()
    render(<OtpBoxInput length={6} value="" onChange={handleChange} />)
    const inputs = screen.getAllByRole('textbox')
    fireEvent.change(inputs[0], { target: { value: '5' } })
    expect(handleChange).toHaveBeenCalledWith('5')
  })

  it('handles paste of full OTP code', () => {
    const handleChange = vi.fn()
    const handleComplete = vi.fn()
    render(
      <OtpBoxInput
        length={6}
        value=""
        onChange={handleChange}
        onComplete={handleComplete}
      />,
    )
    const inputs = screen.getAllByRole('textbox')
    fireEvent.paste(inputs[0], {
      clipboardData: {
        getData: () => '654321',
      },
    })
    expect(handleChange).toHaveBeenCalledWith('654321')
    expect(handleComplete).toHaveBeenCalledWith('654321')
  })
})
