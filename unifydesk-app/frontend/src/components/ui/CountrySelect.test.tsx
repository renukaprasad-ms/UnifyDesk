import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import CountrySelect from './CountrySelect'
import CitySelect from './CitySelect'
import StateSelect from './StateSelect'
import { COUNTRIES } from '../../data/countriesAndCities'

afterEach(() => vi.unstubAllGlobals())
beforeEach(() => {
  vi.stubGlobal(
    'fetch',
    vi.fn().mockResolvedValue({
      ok: true,
      json: async () => [
        { code: 'KA', name: 'Karnataka', cities: ['Bengaluru'] },
        { code: 'MH', name: 'Maharashtra', cities: ['Mumbai'] },
      ],
    }),
  )
})

describe('CountrySelect and CitySelect', () => {
  it('renders country dropdown and selects country', () => {
    const handleChange = vi.fn()
    render(<CountrySelect value="IN" onChange={handleChange} valueType="code" />)
    const select = screen.getByRole('combobox') as HTMLSelectElement
    expect(select.value).toBe('IN')

    fireEvent.change(select, { target: { value: 'US' } })
    expect(handleChange).toHaveBeenCalledWith('US')
  })

  it('renders city select and updates value', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: true,
        json: async () => [
          { code: 'KA', name: 'Karnataka', cities: ['Bengaluru'] },
          { code: 'MH', name: 'Maharashtra', cities: ['Mumbai'] },
        ],
      }),
    )
    const handleChange = vi.fn()
    render(<CitySelect country="IN" value="Bengaluru" onChange={handleChange} />)
    await screen.findByRole('option', { name: 'Mumbai' })
    const input = screen.getByRole('combobox')
    expect(input).toHaveValue('Bengaluru')

    fireEvent.change(input, { target: { value: 'Mumbai' } })
    expect(handleChange).toHaveBeenCalledWith('Mumbai')
  })

  it('includes worldwide countries and allows a manual country', () => {
    expect(COUNTRIES.length).toBeGreaterThanOrEqual(250)
    const onChange = vi.fn()
    render(<CountrySelect value="" onChange={onChange} />)
    fireEvent.change(screen.getByRole('combobox'), { target: { value: '__other__' } })
    fireEvent.change(screen.getByLabelText('Enter country manually'), {
      target: { value: 'Custom location' },
    })
    expect(onChange).toHaveBeenLastCalledWith('Custom location')
  })

  it('filters cities by state and offers manual entry', async () => {
    const onChange = vi.fn()
    render(<CitySelect country="IN" state="Karnataka" value="" onChange={onChange} />)
    await screen.findByRole('option', { name: 'Bengaluru' })
    expect(screen.queryByRole('option', { name: 'Mumbai' })).not.toBeInTheDocument()
    fireEvent.change(screen.getByRole('combobox'), { target: { value: '__other__' } })
    fireEvent.change(screen.getByPlaceholderText('Type manually'), { target: { value: 'My town' } })
    expect(onChange).toHaveBeenLastCalledWith('My town')
  })

  it('lists states for the selected country', async () => {
    render(<StateSelect country="IN" value="" onChange={vi.fn()} />)
    expect(await screen.findByRole('option', { name: 'Karnataka' })).toBeInTheDocument()
  })

  it('allows manual entry when location loading fails', async () => {
    vi.mocked(fetch).mockRejectedValueOnce(new Error('Offline'))
    render(<StateSelect country="US" value="" onChange={vi.fn()} />)
    expect(await screen.findByText(/Locations could not load/)).toBeInTheDocument()
    fireEvent.change(screen.getByRole('combobox'), { target: { value: '__other__' } })
    expect(screen.getByPlaceholderText('Type manually')).toBeInTheDocument()
  })
})
