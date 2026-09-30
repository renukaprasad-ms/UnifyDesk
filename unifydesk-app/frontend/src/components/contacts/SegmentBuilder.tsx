import type { CustomFieldValue, SegmentFieldOption, SegmentGroup, SegmentNode, SegmentPredicate } from '../../features/contacts/types'
import {
  MAX_SEGMENT_DEPTH,
  addSegmentChild,
  countPredicates,
  fieldOptionFor,
  operatorNeedsValue,
  removeSegmentNode,
  replaceSegmentNode,
} from '../../features/contacts/segmentModel'

type Props = {
  value: SegmentGroup
  fields: SegmentFieldOption[]
  onChange: (value: SegmentGroup) => void
}

const defaultPredicate = (field: SegmentFieldOption): SegmentPredicate => ({
  type: 'predicate',
  field: field.descriptor,
  operator: field.operators[0],
  value: operatorNeedsValue(field.operators[0]) ? '' : undefined,
})

export default function SegmentBuilder({ value, fields, onChange }: Props) {
  const renderNode = (node: SegmentNode, path: number[], depth: number) => {
    if (node.type === 'predicate') {
      const field = fieldOptionFor(fields, node) ?? fields[0]
      const updateField = (index: number) => {
        const next = fields[index]
        if (!next) return
        onChange(replaceSegmentNode(value, path, defaultPredicate(next)))
      }
      const update = (patch: Partial<SegmentPredicate>) =>
        onChange(replaceSegmentNode(value, path, { ...node, ...patch }))
      return (
        <div key={path.join('.')} className="grid gap-2 rounded-xl border border-slate-200 bg-white p-3 md:grid-cols-[1.4fr_1fr_1.4fr_auto]">
          <label className="sr-only" htmlFor={'segment-field-' + path.join('-')}>Field</label>
          <select
            id={'segment-field-' + path.join('-')}
            className="form-input"
            value={Math.max(0, fields.findIndex((candidate) => candidate === field))}
            onChange={(event) => updateField(Number(event.target.value))}
          >
            {fields.map((candidate, index) => <option key={candidate.label + index} value={index}>{candidate.label}</option>)}
          </select>
          <select
            className="form-input"
            aria-label="Operator"
            value={node.operator}
            onChange={(event) => {
              const operator = event.target.value as SegmentPredicate['operator']
              update({ operator, value: operatorNeedsValue(operator) ? node.value ?? '' : undefined })
            }}
          >
            {field.operators.map((operator) => <option key={operator} value={operator}>{operator.replaceAll('_', ' ')}</option>)}
          </select>
          {operatorNeedsValue(node.operator) ? (
            field.options?.length ? (
              <select className="form-input" aria-label="Value" value={String(node.value ?? '')} onChange={(event) => update({ value: event.target.value })}>
                <option value="">Choose…</option>
                {field.options.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}
              </select>
            ) : (
              <input
                className="form-input"
                aria-label="Value"
                type={field.valueType === 'DATE' ? 'date' : field.valueType === 'NUMBER' ? 'number' : 'text'}
                value={typeof node.value === 'string' || typeof node.value === 'number' ? node.value : ''}
                onChange={(event) => update({ value: (field.valueType === 'NUMBER' ? Number(event.target.value) : event.target.value) as CustomFieldValue })}
              />
            )
          ) : <span className="self-center text-xs font-medium text-slate-500">No value required</span>}
          <button type="button" className="secondary-button" aria-label="Remove condition" onClick={() => onChange(removeSegmentNode(value, path))}>Remove</button>
        </div>
      )
    }

    return (
      <fieldset key={path.join('.') || 'root'} className="rounded-2xl border border-slate-200 bg-slate-50 p-3 sm:p-4">
        <legend className="px-1 text-xs font-bold text-slate-500">{path.length === 0 ? 'Match contacts where' : 'Nested group'}</legend>
        <div className="flex flex-wrap items-center gap-2">
          <select
            className="form-input w-auto"
            aria-label="Group operator"
            value={node.op}
            onChange={(event) => onChange(replaceSegmentNode(value, path, { ...node, op: event.target.value as 'AND' | 'OR' }) as SegmentGroup)}
          >
            <option value="AND">All conditions (AND)</option>
            <option value="OR">Any condition (OR)</option>
          </select>
          {path.length > 0 && <button type="button" className="secondary-button" onClick={() => onChange(removeSegmentNode(value, path))}>Remove group</button>}
        </div>
        <div className="mt-3 space-y-3">{node.children.map((child, index) => renderNode(child, [...path, index], depth + 1))}</div>
        <div className="mt-3 flex flex-wrap gap-2">
          <button
            type="button"
            className="secondary-button"
            disabled={countPredicates(value) >= 50}
            onClick={() => fields[0] && onChange(addSegmentChild(value, path, defaultPredicate(fields[0])))}
          >
            Add condition
          </button>
          <button
            type="button"
            className="secondary-button"
            disabled={depth >= MAX_SEGMENT_DEPTH - 1}
            onClick={() => fields[0] && onChange(addSegmentChild(value, path, { type: 'group', op: 'AND', children: [defaultPredicate(fields[0])] }))}
          >
            Add group
          </button>
        </div>
      </fieldset>
    )
  }

  return <div>{renderNode(value, [], 0)}</div>
}
