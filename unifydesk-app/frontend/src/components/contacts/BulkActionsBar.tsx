import { useEffect, useState } from 'react'
import {
  buildSelectionDescriptor,
  createIdempotencyKey,
} from '../../features/contacts/contactModel'
import { contactsApi } from '../../features/contacts/contactsApi'
import type {
  ContactQuery,
  CustomFieldDefinition,
  ListSummary,
  TagSummary,
} from '../../features/contacts/types'
import { apiErrorMessage } from '../../lib/api/apiClient'
import ConfirmDialog from '../ui/ConfirmDialog'

type Props = {
  companyId: string
  selectedIds: ReadonlySet<string>
  allMatching: boolean
  query: ContactQuery
  allowModify: boolean
  allowDelete: boolean
  onDone: (message: string) => void
  onError: (message: string) => void
  onClear: () => void
}

type Action =
  'ADD_TAG' | 'REMOVE_TAG' | 'ADD_TO_LIST' | 'REMOVE_FROM_LIST' | 'UPDATE_FIELD' | 'DELETE'

export default function BulkActionsBar({
  companyId,
  selectedIds,
  allMatching,
  query,
  allowModify,
  allowDelete,
  onDone,
  onError,
  onClear,
}: Props) {
  const [action, setAction] = useState<Action>(allowModify ? 'ADD_TAG' : 'DELETE')
  const [tags, setTags] = useState<TagSummary[]>([])
  const [lists, setLists] = useState<ListSummary[]>([])
  const [fields, setFields] = useState<CustomFieldDefinition[]>([])
  const [targetId, setTargetId] = useState('')
  const [fieldValue, setFieldValue] = useState('')
  const [busy, setBusy] = useState(false)
  const [deleteConfirmOpen, setDeleteConfirmOpen] = useState(false)

  useEffect(() => {
    if (!allowModify && allowDelete) setAction('DELETE')
  }, [allowModify, allowDelete])

  useEffect(() => {
    if (!allowModify) return
    Promise.all([
      contactsApi.tags(companyId),
      contactsApi.lists(companyId),
      contactsApi.customFields(companyId),
    ])
      .then(([nextTags, nextLists, nextFields]) => {
        setTags(nextTags)
        setLists(nextLists.filter((item) => item.status !== 'ARCHIVED'))
        setFields(nextFields.filter((item) => item.status === 'ACTIVE'))
      })
      .catch(() => undefined)
  }, [companyId, allowModify])

  const choices =
    action === 'ADD_TAG' || action === 'REMOVE_TAG'
      ? tags.map((item) => ({ id: item.id, label: item.name }))
      : action === 'ADD_TO_LIST' || action === 'REMOVE_FROM_LIST'
        ? lists.map((item) => ({ id: item.id, label: item.name }))
        : action === 'UPDATE_FIELD'
          ? fields.map((item) => ({ id: item.id, label: item.label }))
          : []

  const selectedField =
    action === 'UPDATE_FIELD' ? fields.find((field) => field.id === targetId) : undefined

  const typedFieldValue = () => {
    if (!selectedField) return fieldValue
    if (selectedField.type === 'NUMBER') {
      return fieldValue.trim() === '' ? null : Number(fieldValue)
    }
    if (selectedField.type === 'BOOLEAN') {
      return fieldValue === '' ? null : fieldValue === 'true'
    }
    if (selectedField.type === 'MULTI_SELECT') {
      return fieldValue
        .split(',')
        .map((item) => item.trim())
        .filter(Boolean)
    }
    return fieldValue
  }

  const run = async () => {
    if (action !== 'DELETE' && !targetId) return
    if (action === 'DELETE' && !deleteConfirmOpen) {
      setDeleteConfirmOpen(true)
      return
    }
    setBusy(true)
    onError('')
    try {
      const selection = buildSelectionDescriptor(selectedIds, allMatching, query)
      const operation =
        action === 'ADD_TAG'
          ? { type: 'ADD_TAG' as const, tagId: targetId }
          : action === 'REMOVE_TAG'
            ? { type: 'REMOVE_TAG' as const, tagId: targetId }
            : action === 'ADD_TO_LIST'
              ? { type: 'ADD_TO_LIST' as const, listId: targetId }
              : action === 'REMOVE_FROM_LIST'
                ? { type: 'REMOVE_FROM_LIST' as const, listId: targetId }
                : action === 'UPDATE_FIELD'
                  ? { type: 'UPDATE_FIELD' as const, fieldId: targetId, value: typedFieldValue() }
                  : { type: 'DELETE' as const }
      await contactsApi.bulk(companyId, selection, operation, createIdempotencyKey())
      onClear()
      onDone('Bulk operation queued.')
    } catch (error) {
      onError(apiErrorMessage(error, 'Unable to start bulk operation.'))
    } finally {
      setBusy(false)
      setDeleteConfirmOpen(false)
    }
  }

  return (
    <div className="mt-4 rounded-xl border border-indigo-200 bg-indigo-50 p-4">
      <div className="flex flex-wrap items-center justify-between gap-3 text-sm">
        <strong>
          {allMatching
            ? 'All matching contacts selected'
            : selectedIds.size + ' loaded contacts selected'}
        </strong>
        <button className="secondary-button" onClick={onClear}>
          Clear selection
        </button>
      </div>
      <div className="mt-3 grid gap-2 sm:grid-cols-[180px_minmax(180px,1fr)_minmax(160px,1fr)_auto]">
        <label className="sr-only" htmlFor="bulk-action">
          Bulk action
        </label>
        <select
          id="bulk-action"
          className="form-input"
          value={action}
          onChange={(event) => {
            setAction(event.target.value as Action)
            setTargetId('')
            setFieldValue('')
          }}
        >
          {allowModify && <option value="ADD_TAG">Add tag</option>}
          {allowModify && <option value="REMOVE_TAG">Remove tag</option>}
          {allowModify && <option value="ADD_TO_LIST">Add to list</option>}
          {allowModify && <option value="REMOVE_FROM_LIST">Remove from list</option>}
          {allowModify && <option value="UPDATE_FIELD">Update custom field</option>}
          {allowDelete && <option value="DELETE">Delete contacts</option>}
        </select>
        {action === 'DELETE' ? (
          <div className="self-center text-xs font-semibold text-rose-700">Destructive action</div>
        ) : (
          <select
            className="form-input"
            aria-label="Bulk action target"
            value={targetId}
            onChange={(event) => {
              setTargetId(event.target.value)
              setFieldValue('')
            }}
          >
            <option value="">Choose target…</option>
            {choices.map((choice) => (
              <option key={choice.id} value={choice.id}>
                {choice.label}
              </option>
            ))}
          </select>
        )}
        {action === 'UPDATE_FIELD' ? (
          selectedField?.type === 'BOOLEAN' ? (
            <select
              className="form-input"
              aria-label="Custom field value"
              value={fieldValue}
              onChange={(event) => setFieldValue(event.target.value)}
            >
              <option value="">Unknown</option>
              <option value="true">Yes</option>
              <option value="false">No</option>
            </select>
          ) : selectedField?.type === 'SINGLE_SELECT' && selectedField.options?.length ? (
            <select
              className="form-input"
              aria-label="Custom field value"
              value={fieldValue}
              onChange={(event) => setFieldValue(event.target.value)}
            >
              <option value="">Choose…</option>
              {selectedField.options.map((option) => (
                <option key={option} value={option}>
                  {option}
                </option>
              ))}
            </select>
          ) : (
            <input
              className="form-input"
              aria-label="Custom field value"
              type={selectedField?.type === 'NUMBER' ? 'number' : 'text'}
              value={fieldValue}
              onChange={(event) => setFieldValue(event.target.value)}
              placeholder={
                selectedField?.type === 'MULTI_SELECT' ? 'Comma-separated values' : 'New value'
              }
            />
          )
        ) : (
          <div />
        )}
        <button
          className="primary-button"
          disabled={busy || (action !== 'DELETE' && !targetId)}
          onClick={() => void run()}
        >
          {busy ? 'Starting…' : 'Apply'}
        </button>
      </div>
      <ConfirmDialog
        open={deleteConfirmOpen}
        title="Delete the selected contacts?"
        description="This queues permanent cleanup for the selected contacts and cannot be undone."
        confirmLabel="Yes, delete contacts"
        cancelLabel="No, keep contacts"
        tone="danger"
        kind="delete"
        working={busy}
        onCancel={() => setDeleteConfirmOpen(false)}
        onConfirm={() => void run()}
      />
    </div>
  )
}
