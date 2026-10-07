import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { baseSegmentFields, emptySegment } from '../../features/contacts/segmentModel'
import SegmentBuilder from './SegmentBuilder'

describe('SegmentBuilder', () => {
  it('only exposes operators valid for the selected field type', () => {
    render(<SegmentBuilder value={emptySegment()} fields={baseSegmentFields} onChange={vi.fn()} />)

    const operator = screen.getByLabelText('Operator')
    expect(operator.querySelector('option[value="CONTAINS"]')).not.toBeNull()
    expect(operator.querySelector('option[value="IS_SUPPRESSED"]')).toBeNull()
  })

  it('renders a saved channel preference predicate returned with fieldId null', () => {
    render(
      <SegmentBuilder
        value={{
          type: 'group',
          op: 'AND',
          children: [
            {
              type: 'predicate',
              field: {
                kind: 'CHANNEL_PREFERENCE',
                key: 'emailMarketing',
                fieldId: null,
              },
              operator: 'IS_OPTED_IN',
              value: null,
            },
          ],
        }}
        fields={baseSegmentFields}
        onChange={vi.fn()}
      />,
    )

    expect(screen.getByLabelText('Field')).toHaveValue('6')
    expect(screen.getByLabelText('Operator')).toHaveValue('IS_OPTED_IN')
    expect(screen.getByText('No value required')).toBeInTheDocument()
  })

  it('emits AST changes instead of SQL fragments', () => {
    const onChange = vi.fn()
    render(<SegmentBuilder value={emptySegment()} fields={baseSegmentFields} onChange={onChange} />)

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
