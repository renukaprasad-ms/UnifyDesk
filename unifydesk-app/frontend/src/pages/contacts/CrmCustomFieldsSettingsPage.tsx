/**
 * CRM Custom Fields Settings Page
 * Route: /crm/settings/custom-fields
 *
 * Administrators and Managers can define and configure custom typed fields
 * for Leads, Deals, and Companies. Members have read-only visibility.
 */
import WorkspaceDialog from '../../components/ui/WorkspaceDialog'
import { useCallback, useEffect, useState } from 'react'
import {
  FiPlus,
  FiTrash2,
  FiLayers,
  FiUserCheck,
  FiBriefcase,
  FiGlobe,
  FiSliders,
  FiHelpCircle,
  FiCheckCircle,
  FiType,
  FiHash,
  FiCalendar,
  FiToggleLeft,
  FiList,
  FiLink,
} from 'react-icons/fi'
import {
  crmCustomFieldsApi,
  type CrmEntityType,
  type CrmFieldDef,
  type CrmFieldType,
  type CreateFieldInput,
} from '../../features/contacts/crmCustomFieldsApi'
import { PERMISSIONS } from '../../features/iam/permissions'
import { useIam } from '../../features/iam/useIam'
import { apiErrorMessage } from '../../lib/api/apiClient'
import { useAppSelector } from '../../store/hooks'
import { selectSelectedCompanyId } from '../../store/workspace/workspaceSlice'

interface EntityTabConfig {
  type: CrmEntityType
  label: string
  singular: string
  icon: typeof FiUserCheck
  description: string
}

const ENTITY_TABS: EntityTabConfig[] = [
  {
    type: 'LEAD',
    label: 'Leads',
    singular: 'Lead',
    icon: FiUserCheck,
    description: 'Custom attributes for qualifying and scoring incoming leads.',
  },
  {
    type: 'DEAL',
    label: 'Deals & Pipeline',
    singular: 'Deal',
    icon: FiBriefcase,
    description: 'Custom attributes for tracking revenue, pipeline stages, and deal terms.',
  },
  {
    type: 'ACCOUNT',
    label: 'Companies & Accounts',
    singular: 'Company',
    icon: FiGlobe,
    description: 'Custom organization attributes like industry, tier, or contract renewal.',
  },
]

interface FieldTypeConfig {
  value: CrmFieldType
  label: string
  description: string
  icon: typeof FiType
  badgeColor: string
}

const FIELD_TYPES: FieldTypeConfig[] = [
  {
    value: 'TEXT',
    label: 'Single-line Text',
    description: 'Short strings like PO numbers, notes, or IDs',
    icon: FiType,
    badgeColor: 'bg-blue-50 text-blue-700 dark:bg-blue-950/40 dark:text-blue-300 border-blue-200/60 dark:border-blue-800/60',
  },
  {
    value: 'NUMBER',
    label: 'Numeric',
    description: 'Integer or decimal values (headcount, score, units)',
    icon: FiHash,
    badgeColor: 'bg-emerald-50 text-emerald-700 dark:bg-emerald-950/40 dark:text-emerald-300 border-emerald-200/60 dark:border-emerald-800/60',
  },
  {
    value: 'DATE',
    label: 'Date',
    description: 'Calendar dates (contract start, renewal, audit)',
    icon: FiCalendar,
    badgeColor: 'bg-amber-50 text-amber-700 dark:bg-amber-950/40 dark:text-amber-300 border-amber-200/60 dark:border-amber-800/60',
  },
  {
    value: 'BOOLEAN',
    label: 'Checkbox (Yes / No)',
    description: 'Binary flags (NDA signed, VIP customer, SLA active)',
    icon: FiToggleLeft,
    badgeColor: 'bg-purple-50 text-purple-700 dark:bg-purple-950/40 dark:text-purple-300 border-purple-200/60 dark:border-purple-800/60',
  },
  {
    value: 'DROPDOWN',
    label: 'Single-select Dropdown',
    description: 'Choose one option from a curated list of values',
    icon: FiList,
    badgeColor: 'bg-indigo-50 text-indigo-700 dark:bg-indigo-950/40 dark:text-indigo-300 border-indigo-200/60 dark:border-indigo-800/60',
  },
  {
    value: 'MULTI_SELECT',
    label: 'Multi-select Tags',
    description: 'Select one or more tags from predefined choices',
    icon: FiLayers,
    badgeColor: 'bg-teal-50 text-teal-700 dark:bg-teal-950/40 dark:text-teal-300 border-teal-200/60 dark:border-teal-800/60',
  },
  {
    value: 'URL',
    label: 'Web Link (URL)',
    description: 'Clickable links (LinkedIn profile, portfolio, dossier)',
    icon: FiLink,
    badgeColor: 'bg-sky-50 text-sky-700 dark:bg-sky-950/40 dark:text-sky-300 border-sky-200/60 dark:border-sky-800/60',
  },
]

const NEEDS_OPTIONS: CrmFieldType[] = ['DROPDOWN', 'MULTI_SELECT']

const slugify = (label: string) =>
  label
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '_')
    .replace(/^_+|_+$/g, '')
    .slice(0, 63) || ''

const emptyForm = (): CreateFieldInput => ({
  entityType: 'LEAD',
  fieldKey: '',
  label: '',
  fieldType: 'TEXT',
  required: false,
})

export default function CrmCustomFieldsSettingsPage() {
  const companyId = useAppSelector(selectSelectedCompanyId)
  const { can } = useIam()
  const canModify = can(PERMISSIONS.CRM_FIELD_MODIFY)

  const [activeTab, setActiveTab] = useState<CrmEntityType>('LEAD')
  const [fields, setFields] = useState<CrmFieldDef[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [showExplainer, setShowExplainer] = useState(true)

  // Dialog state
  const [creating, setCreating] = useState(false)
  const [form, setForm] = useState<CreateFieldInput>(emptyForm())
  const [optionInput, setOptionInput] = useState('')
  const [options, setOptions] = useState<string[]>([])
  const [busy, setBusy] = useState(false)
  const [formError, setFormError] = useState('')
  const [deletingId, setDeletingId] = useState<string | null>(null)

  const currentTabConfig = ENTITY_TABS.find((t) => t.type === activeTab) || ENTITY_TABS[0]

  const load = useCallback(async () => {
    if (!companyId) return
    setLoading(true)
    setError('')
    try {
      const data = await crmCustomFieldsApi.list(companyId, activeTab)
      setFields(data)
    } catch (e) {
      setError(apiErrorMessage(e, 'Failed to load custom fields'))
    } finally {
      setLoading(false)
    }
  }, [companyId, activeTab])

  useEffect(() => {
    void load()
  }, [load])

  function openCreate() {
    setForm({ ...emptyForm(), entityType: activeTab })
    setOptions([])
    setOptionInput('')
    setFormError('')
    setCreating(true)
  }

  function onLabelChange(label: string) {
    setForm((prev) => ({
      ...prev,
      label,
      fieldKey: prev.fieldKey || slugify(label),
    }))
  }

  function addOption() {
    const opt = optionInput.trim()
    if (!opt || options.includes(opt)) return
    setOptions((prev) => [...prev, opt])
    setOptionInput('')
  }

  function removeOption(opt: string) {
    setOptions((prev) => prev.filter((o) => o !== opt))
  }

  async function handleCreate(e: React.FormEvent) {
    e.preventDefault()
    if (!companyId) return
    setFormError('')

    if (!form.label.trim()) {
      setFormError('Field label is required')
      return
    }
    if (!form.fieldKey.match(/^[a-z][a-z0-9_]{0,62}$/)) {
      setFormError('Field key must start with a lowercase letter and contain only a–z, 0–9, and underscores')
      return
    }
    if (NEEDS_OPTIONS.includes(form.fieldType) && options.length < 1) {
      setFormError('Please add at least one choice option for dropdown/multi-select fields')
      return
    }

    setBusy(true)
    try {
      const payload: CreateFieldInput = {
        ...form,
        ...(NEEDS_OPTIONS.includes(form.fieldType) ? { options } : {}),
      }
      await crmCustomFieldsApi.create(companyId, payload)
      setCreating(false)
      void load()
    } catch (e) {
      setFormError(apiErrorMessage(e, 'Failed to create custom field'))
    } finally {
      setBusy(false)
    }
  }

  async function handleDelete(fieldId: string) {
    if (!companyId) return
    if (!window.confirm('Are you sure you want to delete this custom field? Existing values stored on records will no longer be visible.')) {
      return
    }
    setDeletingId(fieldId)
    try {
      await crmCustomFieldsApi.delete(companyId, fieldId)
      void load()
    } catch (e) {
      setError(apiErrorMessage(e, 'Failed to delete field'))
    } finally {
      setDeletingId(null)
    }
  }

  return (
    <div className="mx-auto max-w-6xl space-y-6 pb-12">
      {/* Page Header */}
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between border-b border-slate-200/80 dark:border-slate-800 pb-5">
        <div>
          <div className="flex items-center gap-2">
            <span className="inline-flex items-center gap-1.5 rounded-md bg-blue-50 dark:bg-blue-950/50 px-2.5 py-0.5 text-xs font-semibold text-blue-700 dark:text-blue-300 border border-blue-200/50 dark:border-blue-800/50">
              <FiSliders className="h-3.5 w-3.5" />
              CRM SCHEMA SETTINGS
            </span>
            <span className="text-xs text-slate-400">· Workspace Model</span>
          </div>
          <h1 className="mt-2 text-2xl font-bold tracking-tight text-slate-900 dark:text-white">
            Custom Fields & Schema
          </h1>
          <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
            Extend your CRM data model with tailored properties for your business workflows.
          </p>
        </div>

        {canModify && (
          <button
            onClick={openCreate}
            className="primary-button inline-flex items-center gap-2 self-start sm:self-auto shadow-sm"
          >
            <FiPlus className="h-4 w-4" />
            <span>Create Field</span>
          </button>
        )}
      </div>

      {/* Explainer Banner */}
      {showExplainer && (
        <div className="relative overflow-hidden rounded-xl border border-blue-100 bg-gradient-to-r from-blue-50/70 via-indigo-50/40 to-white p-5 dark:border-blue-900/40 dark:from-blue-950/20 dark:via-indigo-950/10 dark:to-slate-900/40">
          <button
            onClick={() => setShowExplainer(false)}
            className="absolute right-4 top-4 text-xs font-medium text-slate-400 hover:text-slate-600 dark:hover:text-slate-200"
            aria-label="Dismiss guide"
          >
            Dismiss
          </button>
          <div className="flex items-start gap-4">
            <div className="hidden sm:flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-blue-600 text-white shadow-md shadow-blue-500/20">
              <FiHelpCircle className="h-5 w-5" />
            </div>
            <div className="space-y-3">
              <h2 className="text-sm font-semibold text-slate-900 dark:text-slate-100">
                How Custom Fields Work in UnifyDesk
              </h2>
              <div className="grid gap-3 sm:grid-cols-3 text-xs text-slate-600 dark:text-slate-300">
                <div className="flex items-start gap-2 rounded-lg bg-white/70 dark:bg-slate-800/60 p-3 border border-slate-200/50 dark:border-slate-700/50">
                  <FiSliders className="h-4 w-4 mt-0.5 text-blue-600 dark:text-blue-400 shrink-0" />
                  <div>
                    <span className="font-semibold text-slate-900 dark:text-slate-100 block">1. Define Schema</span>
                    Create typed attributes (Text, Numbers, Dates, Dropdowns) for your specific industry.
                  </div>
                </div>
                <div className="flex items-start gap-2 rounded-lg bg-white/70 dark:bg-slate-800/60 p-3 border border-slate-200/50 dark:border-slate-700/50">
                  <FiCheckCircle className="h-4 w-4 mt-0.5 text-emerald-600 dark:text-emerald-400 shrink-0" />
                  <div>
                    <span className="font-semibold text-slate-900 dark:text-slate-100 block">2. Auto-Populate</span>
                    Fields automatically appear in detail sidebars, creation forms, and lead qualification steps.
                  </div>
                </div>
                <div className="flex items-start gap-2 rounded-lg bg-white/70 dark:bg-slate-800/60 p-3 border border-slate-200/50 dark:border-slate-700/50">
                  <FiLayers className="h-4 w-4 mt-0.5 text-indigo-600 dark:text-indigo-400 shrink-0" />
                  <div>
                    <span className="font-semibold text-slate-900 dark:text-slate-100 block">3. Fast & Indexed</span>
                    Stored with PostgreSQL JSONB + GIN index acceleration for responsive queries and analytics.
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Entity Selection Tabs */}
      <div className="flex flex-wrap items-center gap-2 border-b border-slate-200/80 dark:border-slate-800 pb-2">
        {ENTITY_TABS.map((tab) => {
          const Icon = tab.icon
          const isActive = activeTab === tab.type
          return (
            <button
              key={tab.type}
              onClick={() => setActiveTab(tab.type)}
              className={`flex items-center gap-2.5 rounded-xl px-4 py-2.5 text-sm font-semibold transition-all duration-150 ${
                isActive
                  ? 'bg-blue-600 text-white shadow-sm shadow-blue-500/25 ring-1 ring-blue-600'
                  : 'text-slate-600 hover:bg-slate-100 hover:text-slate-900 dark:text-slate-400 dark:hover:bg-slate-800/60 dark:hover:text-slate-100'
              }`}
            >
              <Icon className={`h-4 w-4 ${isActive ? 'text-white' : 'text-slate-400'}`} />
              <span>{tab.label}</span>
              {activeTab === tab.type && !loading && (
                <span className="ml-1 rounded-full bg-white/20 px-2 py-0.5 text-xs text-white">
                  {fields.length}
                </span>
              )}
            </button>
          )
        })}
      </div>

      {/* Tab Context Subtitle */}
      <div className="flex items-center justify-between text-xs text-slate-500 dark:text-slate-400">
        <p>{currentTabConfig.description}</p>
        <span>Max 50 fields per entity</span>
      </div>

      {/* Error state */}
      {error && (
        <div className="rounded-xl border border-rose-200 bg-rose-50 p-4 text-sm text-rose-700 dark:border-rose-900/50 dark:bg-rose-950/30 dark:text-rose-300">
          {error}
        </div>
      )}

      {/* Content Section */}
      <div className="panel overflow-hidden border border-slate-200/80 dark:border-slate-800 shadow-xs">
        {loading ? (
          <div className="flex flex-col items-center justify-center p-12 text-slate-400 space-y-3">
            <div className="h-6 w-6 animate-spin rounded-full border-2 border-blue-600 border-t-transparent" />
            <p className="text-sm">Loading custom field schema…</p>
          </div>
        ) : fields.length === 0 ? (
          /* Empty State */
          <div className="flex flex-col items-center justify-center py-16 px-4 text-center">
            <div className="flex h-16 w-16 items-center justify-center rounded-2xl bg-blue-50 text-blue-600 dark:bg-blue-950/40 dark:text-blue-400 ring-1 ring-blue-500/20 mb-4">
              <FiLayers className="h-8 w-8" />
            </div>
            <h2 className="text-lg font-bold text-slate-900 dark:text-white">
              No custom fields configured for {currentTabConfig.label}
            </h2>
            <p className="mt-2 max-w-md text-sm text-slate-500 dark:text-slate-400">
              Standard properties (like name, email, and stage) are built-in. Define custom fields to track
              attributes like Budget Range, Contract Expiry, Industry, or VIP Tier.
            </p>
            {canModify ? (
              <button
                onClick={openCreate}
                className="primary-button mt-6 inline-flex items-center gap-2"
              >
                <FiPlus className="h-4 w-4" />
                <span>Add first {currentTabConfig.singular} field</span>
              </button>
            ) : (
              <p className="mt-4 text-xs text-slate-400">
                You have read-only access. Contact a workspace admin to define custom fields.
              </p>
            )}
          </div>
        ) : (
          /* Data Table */
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead className="bg-slate-50 dark:bg-slate-800/60 text-xs font-semibold uppercase tracking-wider text-slate-500 dark:text-slate-400 border-b border-slate-200/80 dark:border-slate-800">
                <tr>
                  <th scope="col" className="py-3.5 px-4 font-semibold">Field Name & Key</th>
                  <th scope="col" className="py-3.5 px-4 font-semibold">Data Type</th>
                  <th scope="col" className="py-3.5 px-4 font-semibold">Requirement</th>
                  <th scope="col" className="py-3.5 px-4 font-semibold">Allowed Options</th>
                  {canModify && (
                    <th scope="col" className="py-3.5 px-4 text-right font-semibold">Actions</th>
                  )}
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-200/80 dark:divide-slate-800">
                {fields.map((field) => {
                  const typeConfig = FIELD_TYPES.find((ft) => ft.value === field.fieldType) || FIELD_TYPES[0]
                  const Icon = typeConfig.icon
                  return (
                    <tr
                      key={field.id}
                      className="hover:bg-slate-50/70 dark:hover:bg-slate-800/40 transition-colors"
                    >
                      {/* Name & Key */}
                      <td className="py-4 px-4">
                        <div className="flex flex-col">
                          <span className="font-semibold text-slate-900 dark:text-slate-100">
                            {field.label}
                          </span>
                          <span className="mt-0.5 inline-flex items-center text-xs text-slate-400 font-mono">
                            <code>{field.fieldKey}</code>
                          </span>
                        </div>
                      </td>

                      {/* Data Type */}
                      <td className="py-4 px-4">
                        <span
                          className={`inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-xs font-medium border ${typeConfig.badgeColor}`}
                        >
                          <Icon className="h-3.5 w-3.5" />
                          {typeConfig.label}
                        </span>
                      </td>

                      {/* Requirement */}
                      <td className="py-4 px-4">
                        {field.required ? (
                          <span className="inline-flex items-center rounded-full bg-amber-50 dark:bg-amber-950/40 px-2.5 py-0.5 text-xs font-medium text-amber-700 dark:text-amber-300 border border-amber-200/60 dark:border-amber-800/60">
                            Mandatory
                          </span>
                        ) : (
                          <span className="inline-flex items-center rounded-full bg-slate-100 dark:bg-slate-800 px-2.5 py-0.5 text-xs font-medium text-slate-600 dark:text-slate-400">
                            Optional
                          </span>
                        )}
                      </td>

                      {/* Options */}
                      <td className="py-4 px-4">
                        {field.options && field.options.length > 0 ? (
                          <div className="flex flex-wrap gap-1.5 max-w-md">
                            {field.options.map((opt) => (
                              <span
                                key={opt}
                                className="rounded-md bg-slate-100 dark:bg-slate-800 px-2 py-0.5 text-xs font-medium text-slate-700 dark:text-slate-300 border border-slate-200/60 dark:border-slate-700/60"
                              >
                                {opt}
                              </span>
                            ))}
                          </div>
                        ) : (
                          <span className="text-xs text-slate-400">—</span>
                        )}
                      </td>

                      {/* Action */}
                      {canModify && (
                        <td className="py-4 px-4 text-right">
                          <button
                            onClick={() => handleDelete(field.id)}
                            disabled={deletingId === field.id}
                            title="Delete custom field"
                            className="inline-flex items-center justify-center h-8 w-8 rounded-lg text-slate-400 hover:text-rose-600 hover:bg-rose-50 dark:hover:bg-rose-950/30 transition-colors"
                          >
                            <FiTrash2 className="h-4 w-4" />
                          </button>
                        </td>
                      )}
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* Create Dialog */}
      {creating && (
        <WorkspaceDialog
          title={`Create ${currentTabConfig.singular} Custom Field`}
          onClose={() => setCreating(false)}
          busy={busy}
          error={formError}
        >
          <form onSubmit={(e) => { void handleCreate(e) }} className="space-y-4 pt-1">
            {/* Entity Target Pill */}
            <div className="flex items-center justify-between rounded-lg bg-slate-50 dark:bg-slate-800/50 p-3 border border-slate-200/60 dark:border-slate-700/60 text-xs">
              <span className="text-slate-500">Target Entity:</span>
              <span className="font-semibold text-blue-600 dark:text-blue-400 flex items-center gap-1.5">
                <currentTabConfig.icon className="h-3.5 w-3.5" />
                {currentTabConfig.label}
              </span>
            </div>

            {/* Field Label */}
            <div>
              <label htmlFor="cf-label" className="block text-xs font-semibold uppercase tracking-wider text-slate-600 dark:text-slate-300">
                Field Label <span className="text-rose-500">*</span>
              </label>
              <input
                id="cf-label"
                className="form-input mt-1.5 block w-full"
                value={form.label}
                maxLength={120}
                onChange={(e) => onLabelChange(e.target.value)}
                placeholder="e.g. Budget Range, Contract Expiry, Lead Score"
                required
              />
              <p className="mt-1 text-xs text-slate-400">
                User-facing name displayed across forms and tables.
              </p>
            </div>

            {/* Field Key */}
            <div>
              <label htmlFor="cf-key" className="block text-xs font-semibold uppercase tracking-wider text-slate-600 dark:text-slate-300">
                API Key <span className="text-rose-500">*</span>
              </label>
              <input
                id="cf-key"
                className="form-input mt-1.5 block w-full font-mono text-sm"
                value={form.fieldKey}
                maxLength={64}
                onChange={(e) => setForm((prev) => ({ ...prev, fieldKey: e.target.value }))}
                pattern="^[a-z][a-z0-9_]{0,62}$"
                title="Must start with a lowercase letter, containing only a-z, 0-9, and underscores"
                placeholder="e.g. budget_range"
                required
              />
              <p className="mt-1 text-xs text-slate-400">
                Immutable programmatic identifier for reporting and integrations. Auto-derived from label.
              </p>
            </div>

            {/* Field Type */}
            <div>
              <label htmlFor="cf-type" className="block text-xs font-semibold uppercase tracking-wider text-slate-600 dark:text-slate-300">
                Data Type <span className="text-rose-500">*</span>
              </label>
              <select
                id="cf-type"
                className="form-input mt-1.5 block w-full"
                value={form.fieldType}
                onChange={(e) => {
                  setForm((prev) => ({ ...prev, fieldType: e.target.value as CrmFieldType }))
                  setOptions([])
                }}
              >
                {FIELD_TYPES.map((ft) => (
                  <option key={ft.value} value={ft.value}>
                    {ft.label} — {ft.description}
                  </option>
                ))}
              </select>
            </div>

            {/* Options Builder for Dropdown and Multi-Select */}
            {NEEDS_OPTIONS.includes(form.fieldType) && (
              <div className="rounded-xl border border-slate-200 dark:border-slate-800 bg-slate-50/50 dark:bg-slate-900/50 p-4 space-y-3">
                <div className="flex items-center justify-between">
                  <label className="block text-xs font-semibold uppercase tracking-wider text-slate-600 dark:text-slate-300">
                    Defined Options <span className="text-rose-500">*</span>
                  </label>
                  <span className="text-xs text-slate-400">{options.length} added</span>
                </div>

                {options.length > 0 && (
                  <div className="flex flex-wrap gap-2">
                    {options.map((opt) => (
                      <span
                        key={opt}
                        className="inline-flex items-center gap-1.5 rounded-lg bg-white dark:bg-slate-800 px-2.5 py-1 text-xs font-medium text-slate-800 dark:text-slate-200 shadow-xs border border-slate-200 dark:border-slate-700"
                      >
                        {opt}
                        <button
                          type="button"
                          onClick={() => removeOption(opt)}
                          className="text-slate-400 hover:text-rose-600 font-bold ml-0.5"
                          aria-label={`Remove ${opt}`}
                        >
                          ×
                        </button>
                      </span>
                    ))}
                  </div>
                )}

                <div className="flex gap-2">
                  <input
                    className="form-input text-sm flex-1"
                    value={optionInput}
                    onChange={(e) => setOptionInput(e.target.value)}
                    onKeyDown={(e) => {
                      if (e.key === 'Enter') {
                        e.preventDefault()
                        addOption()
                      }
                    }}
                    placeholder="Type option and press Enter"
                    maxLength={100}
                  />
                  <button
                    type="button"
                    onClick={addOption}
                    className="secondary-button"
                  >
                    Add
                  </button>
                </div>
              </div>
            )}

            {/* Required Toggle */}
            <div className="flex items-center gap-3 pt-2">
              <input
                id="cf-required"
                type="checkbox"
                className="h-4 w-4 rounded border-slate-300 text-blue-600 focus:ring-blue-500 dark:border-slate-700"
                checked={form.required}
                onChange={(e) => setForm((prev) => ({ ...prev, required: e.target.checked }))}
              />
              <label htmlFor="cf-required" className="text-sm font-medium text-slate-700 dark:text-slate-300">
                Make this field mandatory when creating or updating records
              </label>
            </div>

            {/* Dialog Footer Actions */}
            <div className="flex justify-end gap-3 pt-4 border-t border-slate-200/80 dark:border-slate-800">
              <button
                type="button"
                className="secondary-button"
                onClick={() => setCreating(false)}
                disabled={busy}
              >
                Cancel
              </button>
              <button
                type="submit"
                className="primary-button"
                disabled={busy}
              >
                {busy ? 'Saving Field…' : 'Save Custom Field'}
              </button>
            </div>
          </form>
        </WorkspaceDialog>
      )}
    </div>
  )
}
