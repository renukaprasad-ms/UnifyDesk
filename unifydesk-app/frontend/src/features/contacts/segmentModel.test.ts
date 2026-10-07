import { describe, expect, it } from 'vitest'
import {
  baseSegmentFields,
  emptySegment,
  fieldOptionFor,
  serializeSegment,
  segmentFields,
  validateSegmentClient,
} from './segmentModel'
import type { SegmentGroup } from './types'

describe('segmentModel', () => {
  it('serializes the frozen AST without semantic loss', () => {
    const filter: SegmentGroup = {
      type: 'group',
      op: 'OR',
      children: [
        {
          type: 'predicate',
          field: { kind: 'CHANNEL_PREFERENCE', key: 'emailMarketing' },
          operator: 'IS_OPTED_IN',
        },
        {
          type: 'group',
          op: 'AND',
          children: [
            {
              type: 'predicate',
              field: { kind: 'BUILTIN_CONTACT', key: 'organization' },
              operator: 'CONTAINS',
              value: 'Acme',
            },
          ],
        },
      ],
    }
    expect(serializeSegment(filter)).toEqual(filter)
  })

  it('rejects invalid field/operator combinations before the request', () => {
    const filter = emptySegment()
    filter.children[0] = {
      type: 'predicate',
      field: { kind: 'BUILTIN_CONTACT', key: 'displayName' },
      operator: 'IS_SUPPRESSED',
    }
    expect(validateSegmentClient(filter, baseSegmentFields)[0]).toContain('does not support')
  })

  it('uses canonical SINGLE_SELECT custom-field definitions', () => {
    const fields = segmentFields([
      {
        id: 'field-1',
        fieldKey: 'plan',
        label: 'Plan',
        type: 'SINGLE_SELECT',
        options: ['Starter', 'Growth'],
        status: 'ACTIVE',
        indexability: 'INDEXED',
      },
    ])
    const field = fields.find((candidate) => candidate.descriptor.fieldId === 'field-1')
    expect(field?.label).toBe('Plan')
    expect(field?.valueType).toBe('SINGLE_SELECT')
    expect(field?.operators).toContain('IN')
  })

  it('matches saved API fields when fieldId is null', () => {
    const predicate = {
      type: 'predicate' as const,
      field: { kind: 'CHANNEL_PREFERENCE' as const, key: 'emailMarketing', fieldId: null },
      operator: 'IS_OPTED_IN' as const,
      value: null,
    }

    const field = fieldOptionFor(baseSegmentFields, predicate)

    expect(field?.label).toBe('Email marketing preference')
    expect(field?.operators).toEqual(['IS_OPTED_IN', 'IS_OPTED_OUT'])
  })

  it('allows no-value preference operators', () => {
    const filter: SegmentGroup = {
      type: 'group',
      op: 'AND',
      children: [
        {
          type: 'predicate',
          field: { kind: 'CHANNEL_PREFERENCE', key: 'smsMarketing' },
          operator: 'IS_OPTED_OUT',
        },
      ],
    }
    expect(validateSegmentClient(filter, baseSegmentFields)).toEqual([])
  })
})
