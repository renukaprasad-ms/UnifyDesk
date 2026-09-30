import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { baseSegmentFields, emptySegment } from '../../features/contacts/segmentModel'
import SegmentBuilder from './SegmentBuilder'

describe('SegmentBuilder', () => {
  it('only exposes operators valid for the selected field type', () => {
    render(
      <SegmentBuilder
        value={emptySegment()}
        fields={baseSegmentFields}
        onChange={vi.fn()}
      />,
    )

    const operator = screen.getByLabelText('Operator')
    expect(operator.querySelector('option[value="CONTAINS"]')).not.toBeNull()
    expect(operator.querySelector('option[value="IS_SUPPRESSED"]')).toBeNull()
  })

  it('emits AST changes instead of SQL fragments', () => {
    const onChange = vi.fn()
    render(
      <SegmentBuilder
        value={emptySegment()}
        fields={baseSegmentFields}
        onChange={onChange}
      />,
    )

    fireEvent.change(screen.getByLabelText('Value'), {
      target: { value: 'Acme' },
    })

    const next = onChange.mock.calls[0][0]
    expect(next).toEqual(
      expect.objectContaining({
        type: 'group',
        op: 'AND',
      }),
    )
    expect(JSON.stringify(next).toLowerCase()).not.toContain('select ')
  })
})
