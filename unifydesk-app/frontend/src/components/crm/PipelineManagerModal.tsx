import { useState } from 'react'
import {
  FiEdit2,
  FiPlus,
  FiTrash2,
  FiArrowUp,
  FiArrowDown,
} from 'react-icons/fi'
import WorkspaceDialog from '../ui/WorkspaceDialog'
import {
  crmSalesApi,
  type Pipeline,
  dealStageColor,
} from '../../features/contacts/crmSalesApi'
import { apiErrorMessage } from '../../lib/api/apiClient'

const COLOR_OPTIONS = [
  { key: 'sky', label: 'Sky' },
  { key: 'indigo', label: 'Indigo' },
  { key: 'violet', label: 'Violet' },
  { key: 'amber', label: 'Amber' },
  { key: 'emerald', label: 'Emerald' },
  { key: 'rose', label: 'Rose' },
  { key: 'teal', label: 'Teal' },
]

export default function PipelineManagerModal({
  companyId,
  pipelines,
  activePipelineId,
  onClose,
  onPipelinesChanged,
  onSelectPipeline,
}: {
  companyId: string
  pipelines: Pipeline[]
  activePipelineId: string
  onClose: () => void
  onPipelinesChanged: () => void
  onSelectPipeline: (id: string) => void
}) {
  const [editingPipeline, setEditingPipeline] = useState<Pipeline | null>(null)
  const [isCreating, setIsCreating] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  // Form state
  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
  const [isDefault, setIsDefault] = useState(false)
  const [stages, setStages] = useState<
    Array<{
      id?: string
      key: string
      name: string
      category: 'OPEN' | 'WON' | 'LOST'
      probability: number
      color: string
    }>
  >([])

  function startCreate() {
    setIsCreating(true)
    setEditingPipeline(null)
    setName('')
    setDescription('')
    setIsDefault(pipelines.length === 0)
    setStages([
      { key: 'NEW', name: 'New Lead', category: 'OPEN', probability: 10, color: 'sky' },
      { key: 'QUALIFIED', name: 'Qualified', category: 'OPEN', probability: 25, color: 'indigo' },
      { key: 'PROPOSAL', name: 'Proposal', category: 'OPEN', probability: 50, color: 'violet' },
      { key: 'NEGOTIATION', name: 'Negotiation', category: 'OPEN', probability: 75, color: 'amber' },
      { key: 'WON', name: 'Won', category: 'WON', probability: 100, color: 'emerald' },
      { key: 'LOST', name: 'Lost', category: 'LOST', probability: 0, color: 'rose' },
    ])
    setError('')
  }

  function startEdit(p: Pipeline) {
    setIsCreating(false)
    setEditingPipeline(p)
    setName(p.name)
    setDescription(p.description || '')
    setIsDefault(p.isDefault)
    setStages(
      p.stages.map((s) => ({
        id: s.id,
        key: s.key,
        name: s.name,
        category: s.category,
        probability: s.probability,
        color: s.color,
      })),
    )
    setError('')
  }

  function cancelForm() {
    setIsCreating(false)
    setEditingPipeline(null)
    setError('')
  }

  function addStage() {
    const num = stages.length + 1
    setStages([
      ...stages,
      {
        key: `STAGE_${num}`,
        name: `Stage ${num}`,
        category: 'OPEN',
        probability: 30,
        color: 'indigo',
      },
    ])
  }

  function removeStage(index: number) {
    if (stages.length <= 1) {
      setError('A pipeline must have at least one stage.')
      return
    }
    setStages(stages.filter((_, idx) => idx !== index))
  }

  function moveStage(index: number, direction: 'up' | 'down') {
    const target = direction === 'up' ? index - 1 : index + 1
    if (target < 0 || target >= stages.length) return
    const updated = [...stages]
    const temp = updated[index]
    updated[index] = updated[target]
    updated[target] = temp
    setStages(updated)
  }

  async function handleSave() {
    if (!name.trim()) {
      setError('Please provide a pipeline name.')
      return
    }
    if (stages.length === 0) {
      setError('Please configure at least one stage.')
      return
    }

    setBusy(true)
    setError('')

    try {
      if (isCreating) {
        const created = await crmSalesApi.createPipeline(companyId, {
          name: name.trim(),
          description: description.trim() || null,
          isDefault,
          stages: stages.map((s, idx) => ({
            key: s.key.trim().toUpperCase() || `STAGE_${idx + 1}`,
            name: s.name.trim(),
            category: s.category,
            probability: s.probability,
            color: s.color,
          })),
        })
        onPipelinesChanged()
        onSelectPipeline(created.id)
        cancelForm()
      } else if (editingPipeline) {
        const updated = await crmSalesApi.updatePipeline(companyId, editingPipeline.id, {
          name: name.trim(),
          description: description.trim() || null,
          isDefault,
          stages: stages.map((s, idx) => ({
            id: s.id,
            key: s.key.trim().toUpperCase() || `STAGE_${idx + 1}`,
            name: s.name.trim(),
            category: s.category,
            probability: s.probability,
            color: s.color,
          })),
        })
        onPipelinesChanged()
        onSelectPipeline(updated.id)
        cancelForm()
      }
    } catch (err) {
      setError(apiErrorMessage(err, 'Failed to save pipeline'))
    } finally {
      setBusy(false)
    }
  }

  async function handleDelete(p: Pipeline) {
    if (p.isDefault) {
      setError('Cannot delete the default sales pipeline.')
      return
    }
    if (!confirm(`Are you sure you want to delete pipeline "${p.name}"?`)) {
      return
    }

    setBusy(true)
    setError('')
    try {
      await crmSalesApi.deletePipeline(companyId, p.id)
      onPipelinesChanged()
      if (activePipelineId === p.id) {
        const fallback = pipelines.find((item) => item.id !== p.id)
        if (fallback) onSelectPipeline(fallback.id)
      }
    } catch (err) {
      setError(apiErrorMessage(err, 'Failed to delete pipeline'))
    } finally {
      setBusy(false)
    }
  }

  return (
    <WorkspaceDialog
      title={isCreating ? 'Create Sales Pipeline' : editingPipeline ? `Edit: ${editingPipeline.name}` : 'Sales Pipelines & Stages'}
      onClose={onClose}
      busy={busy}
      error={error}
      wide={true}
    >
      {isCreating || editingPipeline ? (
        <div className="space-y-6">
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <div>
              <label className="block text-xs font-bold text-slate-700 dark:text-slate-300 uppercase tracking-wider mb-1.5">
                Pipeline Name *
              </label>
              <input
                type="text"
                className="w-full rounded-xl border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-900 px-3.5 py-2.5 text-xs text-slate-900 dark:text-white shadow-xs focus:border-indigo-500 focus:outline-none focus:ring-1 focus:ring-indigo-500"
                placeholder="e.g. Enterprise Sales, Renewal Pipeline"
                value={name}
                onChange={(e) => setName(e.target.value)}
              />
            </div>
            <div>
              <label className="block text-xs font-bold text-slate-700 dark:text-slate-300 uppercase tracking-wider mb-1.5">
                Description
              </label>
              <input
                type="text"
                className="w-full rounded-xl border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-900 px-3.5 py-2.5 text-xs text-slate-900 dark:text-white shadow-xs focus:border-indigo-500 focus:outline-none focus:ring-1 focus:ring-indigo-500"
                placeholder="Optional purpose of this pipeline"
                value={description}
                onChange={(e) => setDescription(e.target.value)}
              />
            </div>
          </div>

          <div className="flex items-center gap-2">
            <input
              type="checkbox"
              id="pipeline-is-default"
              checked={isDefault}
              onChange={(e) => setIsDefault(e.target.checked)}
              className="h-4 w-4 rounded border-slate-300 text-indigo-600 focus:ring-indigo-500"
            />
            <label htmlFor="pipeline-is-default" className="text-xs font-semibold text-slate-800 dark:text-slate-200 cursor-pointer">
              Set as workspace default pipeline
            </label>
          </div>

          {/* Stages List */}
          <div className="rounded-xl border border-slate-200 dark:border-slate-800 bg-slate-50/50 dark:bg-slate-900/30 p-4 space-y-4">
            <div className="flex items-center justify-between">
              <h4 className="text-xs font-bold text-slate-950 dark:text-white uppercase tracking-wider">
                Pipeline Stages ({stages.length})
              </h4>
              <button
                type="button"
                onClick={addStage}
                className="secondary-button text-xs py-1.5 px-3 flex items-center gap-1.5 font-semibold"
              >
                <FiPlus className="h-3.5 w-3.5" /> Add Stage
              </button>
            </div>

            <div className="space-y-2.5">
              {stages.map((stage, idx) => (
                <div
                  key={idx}
                  className="flex flex-wrap items-center gap-2 rounded-xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 p-2.5 shadow-xs"
                >
                  <div className="flex flex-col gap-0.5">
                    <button
                      type="button"
                      disabled={idx === 0}
                      onClick={() => moveStage(idx, 'up')}
                      className="text-slate-400 hover:text-slate-600 disabled:opacity-20 p-0.5"
                    >
                      <FiArrowUp className="h-3 w-3" />
                    </button>
                    <button
                      type="button"
                      disabled={idx === stages.length - 1}
                      onClick={() => moveStage(idx, 'down')}
                      className="text-slate-400 hover:text-slate-600 disabled:opacity-20 p-0.5"
                    >
                      <FiArrowDown className="h-3 w-3" />
                    </button>
                  </div>

                  <span className="w-5 text-center text-xs font-bold text-slate-400">
                    {idx + 1}
                  </span>

                  {/* Stage Name */}
                  <div className="flex-1 min-w-[140px]">
                    <input
                      type="text"
                      className="w-full rounded-lg border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-900 px-2.5 py-1.5 text-xs text-slate-900 dark:text-white focus:border-indigo-500 focus:outline-none"
                      placeholder="Stage name"
                      value={stage.name}
                      onChange={(e) => {
                        const val = e.target.value
                        const updated = [...stages]
                        updated[idx].name = val
                        if (!updated[idx].id) {
                          updated[idx].key = val.trim().toUpperCase().replace(/[^A-Z0-9]/g, '_')
                        }
                        setStages(updated)
                      }}
                    />
                  </div>

                  {/* Stage Category */}
                  <div className="w-24">
                    <select
                      className="w-full rounded-lg border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-900 px-2 py-1.5 text-xs text-slate-900 dark:text-white focus:border-indigo-500 focus:outline-none"
                      value={stage.category}
                      onChange={(e) => {
                        const val = e.target.value as 'OPEN' | 'WON' | 'LOST'
                        const updated = [...stages]
                        updated[idx].category = val
                        if (val === 'WON') {
                          updated[idx].probability = 100
                          updated[idx].color = 'emerald'
                        } else if (val === 'LOST') {
                          updated[idx].probability = 0
                          updated[idx].color = 'rose'
                        }
                        setStages(updated)
                      }}
                    >
                      <option value="OPEN">Open</option>
                      <option value="WON">Won (Closed)</option>
                      <option value="LOST">Lost (Closed)</option>
                    </select>
                  </div>

                  {/* Probability */}
                  <div className="w-20 flex items-center gap-1">
                    <input
                      type="number"
                      min={0}
                      max={100}
                      className="w-full rounded-lg border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-900 px-2 py-1.5 text-xs text-slate-900 dark:text-white focus:border-indigo-500 focus:outline-none text-right"
                      value={stage.probability}
                      onChange={(e) => {
                        const val = parseInt(e.target.value, 10) || 0
                        const updated = [...stages]
                        updated[idx].probability = Math.max(0, Math.min(100, val))
                        setStages(updated)
                      }}
                    />
                    <span className="text-xs text-slate-400">%</span>
                  </div>

                  {/* Color */}
                  <div className="w-24">
                    <select
                      className="w-full rounded-lg border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-900 px-2 py-1.5 text-xs text-slate-900 dark:text-white focus:border-indigo-500 focus:outline-none"
                      value={stage.color}
                      onChange={(e) => {
                        const updated = [...stages]
                        updated[idx].color = e.target.value
                        setStages(updated)
                      }}
                    >
                      {COLOR_OPTIONS.map((c) => (
                        <option key={c.key} value={c.key}>
                          {c.label}
                        </option>
                      ))}
                    </select>
                  </div>

                  {/* Stage preview badge */}
                  <span className={`rounded-full px-2.5 py-0.5 text-[11px] font-bold ${dealStageColor(stage.name, stage.color)}`}>
                    {stage.name || 'Preview'}
                  </span>

                  {/* Remove button */}
                  <button
                    type="button"
                    onClick={() => removeStage(idx)}
                    disabled={stages.length <= 1}
                    className="text-slate-400 hover:text-rose-600 disabled:opacity-20 p-1.5"
                    title="Remove stage"
                  >
                    <FiTrash2 className="h-3.5 w-3.5" />
                  </button>
                </div>
              ))}
            </div>
          </div>

          <div className="flex justify-end gap-2.5 pt-2">
            <button
              type="button"
              className="secondary-button text-xs py-2 px-4"
              onClick={cancelForm}
              disabled={busy}
            >
              Cancel
            </button>
            <button
              type="button"
              className="primary-button bg-indigo-600 hover:bg-indigo-700 text-white text-xs py-2 px-5 font-semibold"
              onClick={handleSave}
              disabled={busy}
            >
              {busy ? 'Saving...' : isCreating ? 'Create Pipeline' : 'Save Pipeline'}
            </button>
          </div>
        </div>
      ) : (
        <div className="space-y-5">
          <div className="flex items-center justify-between">
            <p className="text-xs text-slate-500">
              Customize multiple pipelines for different sales teams, client tiers, or products.
            </p>
            <button
              type="button"
              onClick={startCreate}
              className="primary-button bg-indigo-600 hover:bg-indigo-700 text-white text-xs py-2 px-4 flex items-center gap-1.5 font-semibold shadow-xs"
            >
              <FiPlus className="h-4 w-4" /> New Pipeline
            </button>
          </div>

          <div className="grid grid-cols-1 gap-3.5">
            {pipelines.map((p) => {
              const isActive = p.id === activePipelineId
              return (
                <div
                  key={p.id}
                  className={`rounded-2xl border p-4 transition-all ${
                    isActive
                      ? 'border-indigo-500/50 bg-indigo-50/20 dark:border-indigo-500/40 dark:bg-indigo-950/20 shadow-xs'
                      : 'border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900'
                  }`}
                >
                  <div className="flex flex-wrap items-center justify-between gap-2">
                    <div className="flex items-center gap-2">
                      <h4 className="text-sm font-bold text-slate-950 dark:text-white">
                        {p.name}
                      </h4>
                      {p.isDefault && (
                        <span className="rounded-full bg-emerald-100 dark:bg-emerald-950/60 px-2 py-0.5 text-[10px] font-bold text-emerald-700 dark:text-emerald-300">
                          Default
                        </span>
                      )}
                      {isActive && (
                        <span className="rounded-full bg-indigo-100 dark:bg-indigo-950/60 px-2 py-0.5 text-[10px] font-bold text-indigo-700 dark:text-indigo-300">
                          Active View
                        </span>
                      )}
                    </div>

                    <div className="flex items-center gap-2">
                      <button
                        type="button"
                        onClick={() => {
                          onSelectPipeline(p.id)
                          onClose()
                        }}
                        className={`text-xs py-1.5 px-3 rounded-lg font-semibold transition-colors ${
                          isActive
                            ? 'bg-indigo-600 text-white shadow-xs'
                            : 'secondary-button'
                        }`}
                      >
                        {isActive ? 'Current Pipeline' : 'Switch to Pipeline'}
                      </button>
                      <button
                        type="button"
                        onClick={() => startEdit(p)}
                        className="secondary-button p-2"
                        title="Edit Pipeline & Stages"
                      >
                        <FiEdit2 className="h-3.5 w-3.5" />
                      </button>
                      {!p.isDefault && (
                        <button
                          type="button"
                          onClick={() => handleDelete(p)}
                          className="secondary-button p-2 text-rose-600 hover:text-rose-700"
                          title="Delete Pipeline"
                        >
                          <FiTrash2 className="h-3.5 w-3.5" />
                        </button>
                      )}
                    </div>
                  </div>

                  {p.description && (
                    <p className="mt-1 text-xs text-slate-500">{p.description}</p>
                  )}

                  {/* Stage flow pills */}
                  <div className="mt-3.5 flex flex-wrap items-center gap-1.5">
                    {p.stages.map((stg, sIdx) => (
                      <div key={stg.id || sIdx} className="flex items-center gap-1.5">
                        <span
                          className={`rounded-full px-2.5 py-1 text-xs font-semibold ${dealStageColor(
                            stg.name,
                            stg.color,
                          )}`}
                        >
                          {stg.name} <span className="opacity-70 text-[10px]">({stg.probability}%)</span>
                        </span>
                        {sIdx < p.stages.length - 1 && (
                          <span className="text-slate-300 dark:text-slate-700 text-xs font-bold">
                            →
                          </span>
                        )}
                      </div>
                    ))}
                  </div>
                </div>
              )
            })}
          </div>

          <div className="flex justify-end pt-2">
            <button
              type="button"
              className="secondary-button text-xs py-2 px-5"
              onClick={onClose}
            >
              Close
            </button>
          </div>
        </div>
      )}
    </WorkspaceDialog>
  )
}
