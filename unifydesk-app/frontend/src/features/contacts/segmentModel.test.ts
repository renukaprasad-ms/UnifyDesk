import { describe, expect, it } from 'vitest'
import {
  baseSegmentFields,
  emptySegment,
  serializeSegment,
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
          children: [{
            type: 'predicate',
            field: { kind: 'BUILTIN_CONTACT', key: 'organization' },
            operator: 'CONTAINS',
            value: 'Acme',
          }],
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

  it('allows no-value preference operators', () => {
    const filter: SegmentGroup = {
      type: 'group',
      op: 'AND',
      children: [{
        type: 'predicate',
        field: { kind: 'CHANNEL_PREFERENCE', key: 'smsMarketing' },
        operator: 'IS_OPTED_OUT',
      }],
    }
    expect(validateSegmentClient(filter, baseSegmentFields)).toEqual([])
  })
})
