import { useState } from 'react'
import WorkspaceDialog from './WorkspaceDialog'
export default function AssigneeDialog({
  title,
  currentId,
  currentName,
  people,
  allowUnassigned = false,
  busy,
  error,
  onSave,
  onClose,
}: {
  title: string
  currentId: string | null
  currentName: string
  people: { id: string; name: string }[]
  allowUnassigned?: boolean
  busy: boolean
  error: string
  onSave: (id: string | null) => void
  onClose: () => void
}) {
  const [value, setValue] = useState(currentId || '')
  return (
    <WorkspaceDialog title={title} busy={busy} error={error} onClose={onClose}>
      <form
        className="space-y-4"
        onSubmit={(e) => {
          e.preventDefault()
          onSave(value || null)
        }}
      >
        <label className="block text-sm font-medium">
          Assign to
          <select
            className="form-input mt-2 w-full"
            value={value}
            disabled={busy}
            required={!allowUnassigned}
            onChange={(e) => setValue(e.target.value)}
          >
            {allowUnassigned && <option value="">Unassigned</option>}
            {currentId && !people.some((p) => p.id === currentId) && (
              <option value={currentId}>{currentName}</option>
            )}
            {people.map((p) => (
              <option key={p.id} value={p.id}>
                {p.name}
              </option>
            ))}
          </select>
        </label>
        <div className="flex justify-end gap-2">
          <button type="button" className="secondary-button" disabled={busy} onClick={onClose}>
            Cancel
          </button>
          <button
            type="submit"
            className="primary-button"
            disabled={busy || (!allowUnassigned && !value)}
          >
            {busy ? 'Saving...' : 'Save assignment'}
          </button>
        </div>
      </form>
    </WorkspaceDialog>
  )
}
