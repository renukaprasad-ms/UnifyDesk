import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, expect, it, vi } from 'vitest'
import WorkspaceDialog from './WorkspaceDialog'
import FilterPopover from './FilterPopover'
afterEach(cleanup)
it('focuses the form, traps Tab, closes with Escape and restores focus', () => {
  const previous = document.createElement('button')
  document.body.append(previous)
  previous.focus()
  const close = vi.fn()
  const { unmount } = render(
    <WorkspaceDialog title="New task" onClose={close}>
      <label>
        Title
        <input />
      </label>
      <button type="button">Save</button>
    </WorkspaceDialog>,
  )
  expect(screen.getByRole('textbox', { name: 'Title' })).toHaveFocus()
  screen.getByRole('button', { name: 'Save' }).focus()
  fireEvent.keyDown(document, { key: 'Tab' })
  expect(screen.getByRole('button', { name: 'Close New task' })).toHaveFocus()
  fireEvent.keyDown(document, { key: 'Escape' })
  expect(close).toHaveBeenCalledTimes(1)
  unmount()
  expect(previous).toHaveFocus()
  previous.remove()
})
it('keeps a saving dialog open and displays errors inside it', () => {
  const close = vi.fn()
  render(
    <WorkspaceDialog title="New task" onClose={close} busy error="Unable to save">
      <input aria-label="Title" />
    </WorkspaceDialog>,
  )
  fireEvent.keyDown(document, { key: 'Escape' })
  expect(close).not.toHaveBeenCalled()
  expect(screen.getByRole('button', { name: 'Close New task' })).toBeDisabled()
  expect(screen.getByRole('dialog')).toContainElement(screen.getByRole('alert'))
})
it('opens filters, clears applied values, and dismisses on outside click', () => {
  const reset = vi.fn()
  render(
    <FilterPopover activeCount={1} onReset={reset}>
      <label>
        Status
        <select>
          <option>Open</option>
        </select>
      </label>
    </FilterPopover>,
  )
  expect(screen.queryByRole('combobox')).toBeNull()
  fireEvent.click(screen.getByRole('button', { name: 'Filters, 1 active' }))
  expect(screen.getByRole('combobox')).toHaveFocus()
  fireEvent.click(screen.getByRole('button', { name: 'Clear filters' }))
  expect(reset).toHaveBeenCalledTimes(1)
  fireEvent.pointerDown(document.body)
  expect(screen.queryByRole('region', { name: 'Filter options' })).toBeNull()
})
