import type {
  CustomFieldDefinition,
  SegmentFieldOption,
  SegmentGroup,
  SegmentNode,
  SegmentOperator,
  SegmentPredicate,
} from './types'

export const MAX_SEGMENT_DEPTH = 4
export const MAX_SEGMENT_PREDICATES = 50

const textOperators: SegmentOperator[] = ['EQ', 'NE', 'CONTAINS', 'PRESENT', 'ABSENT']
const selectOperators: SegmentOperator[] = ['EQ', 'NE', 'IN', 'NOT_IN', 'PRESENT', 'ABSENT']
const numberOperators: SegmentOperator[] = [
  'EQ',
  'NE',
  'GT',
  'GTE',
  'LT',
  'LTE',
  'PRESENT',
  'ABSENT',
]
const dateOperators: SegmentOperator[] = ['BEFORE', 'AFTER', 'PRESENT', 'ABSENT']

export const baseSegmentFields: SegmentFieldOption[] = [
  {
    descriptor: { kind: 'BUILTIN_CONTACT', key: 'displayName' },
    label: 'Display name',
    valueType: 'TEXT',
    operators: textOperators,
  },
  {
    descriptor: { kind: 'BUILTIN_CONTACT', key: 'organization' },
    label: 'Organization',
    valueType: 'TEXT',
    operators: textOperators,
  },
  {
    descriptor: { kind: 'IDENTITY', key: 'email' },
    label: 'Email identity',
    valueType: 'TEXT',
    operators: textOperators,
  },
  {
    descriptor: { kind: 'IDENTITY', key: 'phone' },
    label: 'Phone identity',
    valueType: 'TEXT',
    operators: textOperators,
  },
  {
    descriptor: { kind: 'TAG', key: 'tagId' },
    label: 'Has tag',
    valueType: 'SINGLE_SELECT',
    operators: ['HAS_TAG'],
  },
  {
    descriptor: { kind: 'LIST', key: 'listId' },
    label: 'In list',
    valueType: 'SINGLE_SELECT',
    operators: ['IN_LIST'],
  },
  {
    descriptor: { kind: 'CHANNEL_PREFERENCE', key: 'emailMarketing' },
    label: 'Email marketing preference',
    valueType: 'NONE',
    operators: ['IS_OPTED_IN', 'IS_OPTED_OUT'],
  },
  {
    descriptor: { kind: 'CHANNEL_PREFERENCE', key: 'smsMarketing' },
    label: 'SMS marketing preference',
    valueType: 'NONE',
    operators: ['IS_OPTED_IN', 'IS_OPTED_OUT'],
  },
  {
    descriptor: { kind: 'CHANNEL_PREFERENCE', key: 'whatsappMarketing' },
    label: 'WhatsApp marketing preference',
    valueType: 'NONE',
    operators: ['IS_OPTED_IN', 'IS_OPTED_OUT'],
  },
  {
    descriptor: { kind: 'SUPPRESSION', key: 'active' },
    label: 'Suppressed',
    valueType: 'NONE',
    operators: ['IS_SUPPRESSED'],
  },
  {
    descriptor: { kind: 'CREATED_AT', key: 'createdAt' },
    label: 'Created at',
    valueType: 'DATE',
    operators: dateOperators,
  },
  {
    descriptor: { kind: 'LAST_ACTIVITY_AT', key: 'lastActivityAt' },
    label: 'Last activity',
    valueType: 'DATE',
    operators: dateOperators,
  },
]

const operatorsForCustomType = (type: CustomFieldDefinition['type']): SegmentOperator[] => {
  if (type === 'NUMBER') return numberOperators
  if (type === 'DATE' || type === 'DATETIME') return dateOperators
  if (type === 'SINGLE_SELECT' || type === 'MULTI_SELECT') return selectOperators
  if (type === 'BOOLEAN') return ['EQ', 'NE', 'PRESENT', 'ABSENT']
  return textOperators
}

export const segmentFields = (
  customFields: CustomFieldDefinition[],
  tagOptions: Array<{ value: string; label: string }> = [],
  listOptions: Array<{ value: string; label: string }> = [],
): SegmentFieldOption[] => [
  ...baseSegmentFields.map((field) => {
    if (field.descriptor.kind === 'TAG') return { ...field, options: tagOptions }
    if (field.descriptor.kind === 'LIST') return { ...field, options: listOptions }
    return field
  }),
  ...customFields
    .filter((field) => field.status === 'ACTIVE')
    .map<SegmentFieldOption>((field) => ({
      descriptor: { kind: 'CUSTOM_FIELD', key: 'customField', fieldId: field.id },
      label: field.label,
      valueType: field.type === 'DATETIME' ? 'DATE' : field.type,
      operators: operatorsForCustomType(field.type),
      options: field.options?.map((option) => ({ value: option, label: option })),
    })),
]

export const emptySegment = (): SegmentGroup => ({
  type: 'group',
  op: 'AND',
  children: [
    {
      type: 'predicate',
      field: { kind: 'BUILTIN_CONTACT', key: 'displayName' },
      operator: 'CONTAINS',
      value: '',
    },
  ],
})

export const fieldOptionFor = (fields: SegmentFieldOption[], predicate: SegmentPredicate) =>
  fields.find(
    (field) =>
      field.descriptor.kind === predicate.field.kind &&
      field.descriptor.key === predicate.field.key &&
      (field.descriptor.fieldId ?? null) === (predicate.field.fieldId ?? null),
  )

export const operatorNeedsValue = (operator: SegmentOperator) =>
  !['PRESENT', 'ABSENT', 'IS_OPTED_IN', 'IS_OPTED_OUT', 'IS_SUPPRESSED'].includes(operator)

export const countPredicates = (node: SegmentNode): number =>
  node.type === 'predicate'
    ? 1
    : node.children.reduce((total, child) => total + countPredicates(child), 0)

export const segmentDepth = (node: SegmentNode): number =>
  node.type === 'predicate' ? 1 : 1 + Math.max(0, ...node.children.map(segmentDepth))

export const replaceSegmentNode = (
  root: SegmentGroup,
  path: number[],
  replacement: SegmentNode,
): SegmentGroup => {
  if (path.length === 0) {
    if (replacement.type !== 'group') return root
    return replacement
  }
  const [head, ...tail] = path
  return {
    ...root,
    children: root.children.map((child, index) => {
      if (index !== head) return child
      if (tail.length === 0) return replacement
      if (child.type !== 'group') return child
      return replaceSegmentNode(child, tail, replacement)
    }),
  }
}

export const removeSegmentNode = (root: SegmentGroup, path: number[]): SegmentGroup => {
  if (path.length === 0) return root
  const [head, ...tail] = path
  if (tail.length === 0) {
    return { ...root, children: root.children.filter((_, index) => index !== head) }
  }
  return {
    ...root,
    children: root.children.map((child, index) => {
      if (index !== head || child.type !== 'group') return child
      return removeSegmentNode(child, tail)
    }),
  }
}

export const addSegmentChild = (
  root: SegmentGroup,
  path: number[],
  child: SegmentNode,
): SegmentGroup => {
  if (path.length === 0) return { ...root, children: [...root.children, child] }
  const [head, ...tail] = path
  return {
    ...root,
    children: root.children.map((node, index) => {
      if (index !== head || node.type !== 'group') return node
      return addSegmentChild(node, tail, child)
    }),
  }
}

export const validateSegmentClient = (root: SegmentGroup, fields: SegmentFieldOption[]) => {
  const errors: string[] = []
  if (segmentDepth(root) > MAX_SEGMENT_DEPTH) errors.push('Segment nesting cannot exceed 4 levels.')
  if (countPredicates(root) > MAX_SEGMENT_PREDICATES)
    errors.push('A segment can contain at most 50 predicates.')

  const walk = (node: SegmentNode) => {
    if (node.type === 'group') {
      if (node.children.length === 0)
        errors.push('Every AND/OR group needs at least one condition.')
      node.children.forEach(walk)
      return
    }
    const field = fieldOptionFor(fields, node)
    if (!field) {
      errors.push('A segment condition references an unavailable field.')
      return
    }
    if (!field.operators.includes(node.operator)) {
      errors.push(field.label + ' does not support operator ' + node.operator + '.')
    }
    if (operatorNeedsValue(node.operator)) {
      const value = node.value
      if (value === null || value === undefined || value === '') {
        errors.push(field.label + ' requires a value.')
      }
    }
  }

  walk(root)
  const bytes = new TextEncoder().encode(JSON.stringify(root)).byteLength
  if (bytes > 32 * 1024) errors.push('Segment filter exceeds the 32 KiB limit.')
  return errors
}

export const serializeSegment = (root: SegmentGroup) =>
  JSON.parse(JSON.stringify(root)) as SegmentGroup
