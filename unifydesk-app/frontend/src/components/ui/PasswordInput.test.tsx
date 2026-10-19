import { describe, it, expect, vi } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import PasswordInput from './PasswordInput'

describe('PasswordInput', () => {
  it('renders input with type password initially', () => {
    render(<PasswordInput placeholder="Enter password" value="mysecret" onChange={vi.fn()} />)
    const input = screen.getByPlaceholderText('Enter password') as HTMLInputElement
    expect(input.type).toBe('password')
  })

  it('toggles visibility when eye button is clicked', () => {
    render(<PasswordInput placeholder="Enter password" value="mysecret" onChange={vi.fn()} />)
    const input = screen.getByPlaceholderText('Enter password') as HTMLInputElement
    const toggleBtn = screen.getByRole('button', { name: /show password/i })

    expect(input.type).toBe('password')

    fireEvent.click(toggleBtn)
    expect(input.type).toBe('text')
    expect(screen.getByRole('button', { name: /hide password/i })).toBeDefined()

    fireEvent.click(screen.getByRole('button', { name: /hide password/i }))
    expect(input.type).toBe('password')
  })
})
