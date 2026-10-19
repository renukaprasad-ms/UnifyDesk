import { describe, it, expect, vi } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import CountrySelect from './CountrySelect'
import CitySelect from './CitySelect'

describe('CountrySelect and CitySelect', () => {
  it('renders country dropdown and selects country', () => {
    const handleChange = vi.fn()
    render(<CountrySelect value="IN" onChange={handleChange} valueType="code" />)
    const select = screen.getByRole('combobox') as HTMLSelectElement
    expect(select.value).toBe('IN')

    fireEvent.change(select, { target: { value: 'US' } })
    expect(handleChange).toHaveBeenCalledWith('US')
  })

  it('renders city select and updates value', () => {
    const handleChange = vi.fn()
    render(<CitySelect country="IN" value="Bengaluru" onChange={handleChange} />)
    const input = screen.getByPlaceholderText('Select or enter city') as HTMLInputElement
    expect(input.value).toBe('Bengaluru')

    fireEvent.change(input, { target: { value: 'Mumbai' } })
    expect(handleChange).toHaveBeenCalledWith('Mumbai')
  })
})
