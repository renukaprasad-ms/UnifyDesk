import { useSnackbarState } from '../feedback/useSnackbarState'
import { useEffect, useState } from 'react'
import {
  FiX,
  FiZap,
  FiPlus,
  FiTrash2,
  FiEdit2,
  FiRefreshCw,
  FiSliders,
  FiToggleLeft,
  FiToggleRight,
  FiThermometer,
} from 'react-icons/fi'
import {
  crmLeadScoringApi,
  type ScoreRule,
  type ScoreSettings,
  type ScoreRuleType,
  type SaveRuleInput,
} from '../../features/contacts/crmLeadScoringApi'
import {
  crmCustomFieldsApi,
  type CrmFieldDef,
} from '../../features/contacts/crmCustomFieldsApi'
import { apiErrorMessage } from '../../lib/api/apiClient'
import WorkspaceDialog from '../ui/WorkspaceDialog'

interface LeadScoringModalProps {
  companyId: string
  onClose: () => void
  onRulesChanged?: () => void
}

export default function LeadScoringModal({
  companyId,
  onClose,
  onRulesChanged,
}: LeadScoringModalProps) {
  const [activeTab, setActiveTab] = useState<'rules' | 'thresholds'>('rules')
  const [rules, setRules] = useState<ScoreRule[]>([])
  const [settings, setSettings] = useState<ScoreSettings | null>(null)
  const [customDefs, setCustomDefs] = useState<CrmFieldDef[]>([])
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useSnackbarState('', 'error')
  const [, setFeedback] = useSnackbarState('', 'success')

  // Add / Edit Rule dialog
  const [editingRule, setEditingRule] = useState<ScoreRule | null>(null)
  const [showAddRule, setShowAddRule] = useState(false)

  const [ruleName, setRuleName] = useState('')
  const [ruleDesc, setRuleDesc] = useState('')
  const [ruleType, setRuleType] = useState<ScoreRuleType>('HAS_PHONE')
  const [rulePoints, setRulePoints] = useState<number>(15)
  const [stageValue, setStageValue] = useState<string>('QUALIFIED')
  const [cfKey, setCfKey] = useState<string>('')
  const [cfOp, setCfOp] = useState<string>('EXISTS')
  const [cfValue, setCfValue] = useState<string>('')

  // Thresholds state
  const [hotThreshold, setHotThreshold] = useState<number>(70)
  const [warmThreshold, setWarmThreshold] = useState<number>(30)

  useEffect(() => {
    loadAll()
  }, [companyId])

  async function loadAll() {
    if (!companyId) return
    setLoading(true)
    setError('')
    try {
      const [r, s, c] = await Promise.all([
        crmLeadScoringApi.getRules(companyId),
        crmLeadScoringApi.getSettings(companyId),
        crmCustomFieldsApi.list(companyId, 'LEAD').catch(() => [] as CrmFieldDef[]),
      ])
      setRules(r || [])
      setSettings(s)
      setHotThreshold(s.hotThreshold)
      setWarmThreshold(s.warmThreshold)
      setCustomDefs(c || [])
      if (c && c.length > 0) setCfKey(c[0].fieldKey)
    } catch (err) {
      setError(apiErrorMessage(err, 'Failed to load lead scoring configuration'))
    } finally {
      setLoading(false)
    }
  }

  function openNewRule() {
    setEditingRule(null)
    setRuleName('')
    setRuleDesc('')
    setRuleType('HAS_PHONE')
    setRulePoints(15)
    setStageValue('QUALIFIED')
    if (customDefs.length > 0) setCfKey(customDefs[0].fieldKey)
    setCfOp('EXISTS')
    setCfValue('')
    setShowAddRule(true)
  }

  function openEditRule(rule: ScoreRule) {
    setEditingRule(rule)
    setRuleName(rule.ruleName)
    setRuleDesc(rule.description || '')
    setRuleType(rule.ruleType)
    setRulePoints(rule.points)
    if (rule.ruleType === 'STAGE_MATCH') {
      setStageValue(rule.conditionConfig?.stage || 'QUALIFIED')
    } else if (rule.ruleType === 'CUSTOM_FIELD_MATCH') {
      setCfKey(rule.conditionConfig?.fieldKey || '')
      setCfOp(rule.conditionConfig?.operator || 'EXISTS')
      setCfValue(rule.conditionConfig?.value || '')
    }
    setShowAddRule(true)
  }

  async function handleSaveRule(e: React.FormEvent) {
    e.preventDefault()
    if (!companyId || busy) return
    setBusy(true)
    setError('')

    const conditionConfig: Record<string, any> = {}
    if (ruleType === 'STAGE_MATCH') {
      conditionConfig.stage = stageValue
    } else if (ruleType === 'CUSTOM_FIELD_MATCH') {
      conditionConfig.fieldKey = cfKey
      conditionConfig.operator = cfOp
      conditionConfig.value = cfValue
    }

    const payload: SaveRuleInput = {
      ruleName: ruleName.trim(),
      description: ruleDesc.trim() || null,
      ruleType,
      conditionConfig,
      points: Number(rulePoints),
      isEnabled: editingRule ? editingRule.isEnabled : true,
    }

    try {
      if (editingRule) {
        await crmLeadScoringApi.updateRule(companyId, editingRule.id, payload)
      } else {
        await crmLeadScoringApi.createRule(companyId, payload)
      }
      setShowAddRule(false)
      await loadAll()
      if (onRulesChanged) onRulesChanged()
    } catch (err) {
      setError(apiErrorMessage(err, 'Failed to save scoring rule'))
    } finally {
      setBusy(false)
    }
  }

  async function handleToggleRule(rule: ScoreRule) {
    if (!companyId || busy) return
    setBusy(true)
    try {
      await crmLeadScoringApi.toggleRule(companyId, rule.id, !rule.isEnabled)
      await loadAll()
      if (onRulesChanged) onRulesChanged()
    } catch (err) {
      setError(apiErrorMessage(err, 'Failed to toggle rule'))
    } finally {
      setBusy(false)
    }
  }

  async function handleDeleteRule(ruleId: string) {
    if (!companyId || busy) return
    setBusy(true)
    try {
      await crmLeadScoringApi.deleteRule(companyId, ruleId)
      await loadAll()
      if (onRulesChanged) onRulesChanged()
    } catch (err) {
      setError(apiErrorMessage(err, 'Failed to delete rule'))
    } finally {
      setBusy(false)
    }
  }

  async function handleSaveSettings(e: React.FormEvent) {
    e.preventDefault()
    if (!companyId || busy) return
    setBusy(true)
    setError('')
    setFeedback('')
    try {
      const updated = await crmLeadScoringApi.updateSettings(companyId, {
        hotThreshold: Number(hotThreshold),
        warmThreshold: Number(warmThreshold),
      })
      setSettings(updated)
      setFeedback('Thresholds updated and all lead scores recalculated successfully!')
      if (onRulesChanged) onRulesChanged()
    } catch (err) {
      setError(apiErrorMessage(err, 'Failed to save threshold settings'))
    } finally {
      setBusy(false)
    }
  }

  async function handleRecalculateAll() {
    if (!companyId || busy) return
    setBusy(true)
    setError('')
    setFeedback('')
    try {
      const count = await crmLeadScoringApi.recalculate(companyId)
      setFeedback(`Successfully recalculated lead scores for ${count} leads!`)
      if (onRulesChanged) onRulesChanged()
    } catch (err) {
      setError(apiErrorMessage(err, 'Failed to recalculate lead scores'))
    } finally {
      setBusy(false)
    }
  }

  const getRuleTypeBadge = (type: ScoreRuleType) => {
    switch (type) {
      case 'HAS_PHONE':
        return '📞 Phone Identity'
      case 'HAS_EMAIL':
        return '✉️ Email Identity'
      case 'HAS_ORGANIZATION':
        return '🏢 Organization / Company'
      case 'STAGE_MATCH':
        return '🎯 Lead Stage Match'
      case 'FORM_SUBMISSION':
        return '📝 Web Lead Form'
      case 'HAS_DEAL':
        return '💼 Sales Deal Opportunity'
      case 'COMMUNICATION_ACTIVITY':
        return '💬 Communication / Notes'
      case 'CUSTOM_FIELD_MATCH':
        return '🏷️ Custom Field Condition'
      default:
        return type
    }
  }

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/60 p-4 backdrop-blur-xs"
      onClick={(e) => {
        if (e.target === e.currentTarget) onClose()
      }}
    >
      <div className="relative flex max-h-[90vh] w-full max-w-4xl flex-col overflow-hidden rounded-2xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 shadow-2xl">
        {/* Header */}
        <header className="flex items-center justify-between border-b border-slate-100 dark:border-slate-800 p-5">
          <div className="flex items-center gap-3">
            <div className="flex size-10 items-center justify-center rounded-xl bg-amber-500/10 text-amber-500 dark:bg-amber-500/20">
              <FiZap className="size-5" />
            </div>
            <div>
              <h2 className="text-lg font-bold text-slate-950 dark:text-slate-100">
                Rule-Based Lead Scoring & Temperatures
              </h2>
              <p className="text-xs text-slate-500 dark:text-slate-400">
                Automatically grade leads (🔥 Hot / ⚡ Warm / ❄️ Cold) based on engagement and profile attributes
              </p>
            </div>
          </div>
          <button
            onClick={onClose}
            className="secondary-button p-2 text-slate-400 hover:text-slate-600 dark:hover:text-slate-200"
            aria-label="Close modal"
          >
            <FiX className="size-4" />
          </button>
        </header>

        {/* Tab Switcher & Sub-actions */}
        <div className="flex flex-wrap items-center justify-between border-b border-slate-100 dark:border-slate-800 bg-slate-50/50 dark:bg-slate-900/50 px-5 py-2.5">
          <div className="flex gap-1 rounded-xl bg-slate-100 dark:bg-slate-800 p-1">
            <button
              onClick={() => setActiveTab('rules')}
              className={`flex items-center gap-2 rounded-lg px-3 py-1.5 text-xs font-semibold transition ${
                activeTab === 'rules'
                  ? 'bg-white dark:bg-slate-900 text-indigo-600 dark:text-indigo-400 shadow-xs'
                  : 'text-slate-600 dark:text-slate-400'
              }`}
            >
              <FiSliders className="size-3.5" />
              <span>Scoring Rules ({rules.length})</span>
            </button>
            <button
              onClick={() => setActiveTab('thresholds')}
              className={`flex items-center gap-2 rounded-lg px-3 py-1.5 text-xs font-semibold transition ${
                activeTab === 'thresholds'
                  ? 'bg-white dark:bg-slate-900 text-indigo-600 dark:text-indigo-400 shadow-xs'
                  : 'text-slate-600 dark:text-slate-400'
              }`}
            >
              <FiThermometer className="size-3.5" />
              <span>Temperature Thresholds</span>
            </button>
          </div>

          <div className="flex items-center gap-2">
            <button
              onClick={handleRecalculateAll}
              disabled={busy}
              className="secondary-button gap-1.5 text-xs py-1.5 px-3"
              title="Recalculate scores for all leads"
            >
              <FiRefreshCw className={`size-3.5 ${busy ? 'animate-spin' : ''}`} />
              <span>Recalculate All</span>
            </button>
            {activeTab === 'rules' && (
              <button
                onClick={openNewRule}
                disabled={busy}
                className="primary-button gap-1.5 text-xs py-1.5 px-3"
              >
                <FiPlus className="size-3.5" />
                <span>Add Rule</span>
              </button>
            )}
          </div>
        </div>

        {/* Feedback / Error banner */}



        {/* Main Content Area */}
        <div className="min-h-0 flex-1 overflow-y-auto p-5">
          {loading ? (
            <div className="p-12 text-center text-xs text-slate-400">Loading scoring rules...</div>
          ) : activeTab === 'rules' ? (
            /* Rules List */
            <div className="space-y-3">
              {rules.length === 0 ? (
                <div className="rounded-xl border border-dashed border-slate-200 dark:border-slate-800 p-8 text-center text-xs text-slate-400">
                  No scoring rules defined yet. Click "Add Rule" to configure lead scoring criteria.
                </div>
              ) : (
                rules.map((rule) => (
                  <div
                    key={rule.id}
                    className={`flex items-center justify-between rounded-xl border p-4 transition ${
                      rule.isEnabled
                        ? 'border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900'
                        : 'border-slate-100 dark:border-slate-800/50 bg-slate-50/50 dark:bg-slate-900/30 opacity-60'
                    }`}
                  >
                    <div className="space-y-1">
                      <div className="flex items-center gap-2">
                        <span className="text-sm font-bold text-slate-900 dark:text-slate-100">
                          {rule.ruleName}
                        </span>
                        <span className="rounded-md bg-slate-100 dark:bg-slate-800 px-2 py-0.5 text-[10px] font-semibold text-slate-600 dark:text-slate-300">
                          {getRuleTypeBadge(rule.ruleType)}
                        </span>
                        {rule.points >= 0 ? (
                          <span className="rounded-md bg-emerald-50 dark:bg-emerald-950/60 px-2 py-0.5 text-xs font-bold text-emerald-600 dark:text-emerald-400 border border-emerald-200 dark:border-emerald-800">
                            +{rule.points} pts
                          </span>
                        ) : (
                          <span className="rounded-md bg-rose-50 dark:bg-rose-950/60 px-2 py-0.5 text-xs font-bold text-rose-600 dark:text-rose-400 border border-rose-200 dark:border-rose-800">
                            {rule.points} pts
                          </span>
                        )}
                      </div>
                      <p className="text-xs text-slate-500 dark:text-slate-400">
                        {rule.description || 'Applies points when lead meets this condition'}
                      </p>
                      {rule.ruleType === 'STAGE_MATCH' && rule.conditionConfig?.stage && (
                        <p className="text-[11px] text-indigo-600 dark:text-indigo-400 font-medium">
                          Target Stage: {rule.conditionConfig.stage}
                        </p>
                      )}
                      {rule.ruleType === 'CUSTOM_FIELD_MATCH' && rule.conditionConfig?.fieldKey && (
                        <p className="text-[11px] text-indigo-600 dark:text-indigo-400 font-medium">
                          Attribute: {rule.conditionConfig.fieldKey} ({rule.conditionConfig.operator || 'EXISTS'})
                          {rule.conditionConfig.value ? ` = "${rule.conditionConfig.value}"` : ''}
                        </p>
                      )}
                    </div>

                    <div className="flex items-center gap-2">
                      <button
                        onClick={() => handleToggleRule(rule)}
                        disabled={busy}
                        className="p-1 text-slate-500 hover:text-slate-800 dark:hover:text-slate-200"
                        title={rule.isEnabled ? 'Disable rule' : 'Enable rule'}
                      >
                        {rule.isEnabled ? (
                          <FiToggleRight className="size-6 text-emerald-600" />
                        ) : (
                          <FiToggleLeft className="size-6 text-slate-400" />
                        )}
                      </button>
                      <button
                        onClick={() => openEditRule(rule)}
                        disabled={busy}
                        className="secondary-button p-2 text-xs"
                        title="Edit rule"
                      >
                        <FiEdit2 className="size-3.5" />
                      </button>
                      <button
                        onClick={() => handleDeleteRule(rule.id)}
                        disabled={busy}
                        className="secondary-button p-2 text-xs text-rose-600 hover:bg-rose-50"
                        title="Delete rule"
                      >
                        <FiTrash2 className="size-3.5" />
                      </button>
                    </div>
                  </div>
                ))
              )}
            </div>
          ) : (
            /* Thresholds Settings */
            <form onSubmit={handleSaveSettings} className="max-w-xl space-y-6">
              <div>
                <h3 className="text-sm font-bold text-slate-900 dark:text-slate-100">
                  Lead Temperature Bands
                </h3>
                <p className="text-xs text-slate-500 dark:text-slate-400">
                  Define the score thresholds that categorize leads into Hot, Warm, and Cold
                </p>
              </div>

              {/* Visual Temperature Preview Spectrum */}
              <div className="rounded-xl border border-slate-200 dark:border-slate-800 bg-slate-50/50 dark:bg-slate-900/50 p-4 space-y-3">
                <div className="flex items-center justify-between text-xs font-semibold">
                  <span className="flex items-center gap-1.5 text-blue-600 dark:text-blue-400">
                    ❄️ Cold (0 – {warmThreshold - 1} pts)
                  </span>
                  <span className="flex items-center gap-1.5 text-amber-600 dark:text-amber-400">
                    ⚡ Warm ({warmThreshold} – {hotThreshold - 1} pts)
                  </span>
                  <span className="flex items-center gap-1.5 text-rose-600 dark:text-rose-400">
                    🔥 Hot ({hotThreshold}+ pts)
                  </span>
                </div>
                <div className="h-3 w-full overflow-hidden rounded-full bg-slate-200 dark:bg-slate-700 flex">
                  <div
                    style={{ width: `${Math.min(100, Math.max(10, warmThreshold))}%` }}
                    className="bg-blue-400 dark:bg-blue-500"
                  />
                  <div
                    style={{
                      width: `${Math.min(100, Math.max(10, hotThreshold - warmThreshold))}%`,
                    }}
                    className="bg-amber-400 dark:bg-amber-500"
                  />
                  <div className="flex-1 bg-rose-500 dark:bg-rose-600" />
                </div>
              </div>

              <div className="grid gap-4 sm:grid-cols-2">
                <label className="text-xs font-semibold text-slate-700 dark:text-slate-300">
                  <span className="flex items-center gap-1">
                    <span>🔥 Hot Threshold (points)</span>
                  </span>
                  <input
                    type="number"
                    required
                    min={warmThreshold + 1}
                    className="form-input mt-1.5 w-full text-xs"
                    value={hotThreshold}
                    onChange={(e) => setHotThreshold(Number(e.target.value))}
                  />
                  <span className="text-[11px] font-normal text-slate-400">
                    Leads with scores $\ge$ this number are classified as Hot
                  </span>
                </label>

                <label className="text-xs font-semibold text-slate-700 dark:text-slate-300">
                  <span className="flex items-center gap-1">
                    <span>⚡ Warm Threshold (points)</span>
                  </span>
                  <input
                    type="number"
                    required
                    min={0}
                    max={hotThreshold - 1}
                    className="form-input mt-1.5 w-full text-xs"
                    value={warmThreshold}
                    onChange={(e) => setWarmThreshold(Number(e.target.value))}
                  />
                  <span className="text-[11px] font-normal text-slate-400">
                    Leads between Warm and Hot are classified as Warm
                  </span>
                </label>
              </div>

              <div className="flex items-center justify-between pt-2">
                <button type="submit" disabled={busy} className="primary-button text-xs py-2 px-4">
                  {busy ? 'Saving...' : 'Save Temperature Thresholds'}
                </button>
                {settings?.updatedAt && (
                  <span className="text-[11px] text-slate-400">
                    Last saved: {new Date(settings.updatedAt).toLocaleDateString()}
                  </span>
                )}
              </div>
            </form>
          )}
        </div>

        {/* Add / Edit Rule Modal Dialog */}
        {showAddRule && (
          <WorkspaceDialog
            title={editingRule ? 'Edit Scoring Rule' : 'New Scoring Rule'}
            wide
            busy={busy}
            error={error}
            onClose={() => setShowAddRule(false)}
          >
            <form onSubmit={handleSaveRule} className="space-y-4">
              <div className="grid gap-4 sm:grid-cols-2">
                <label className="text-xs font-semibold text-slate-600 sm:col-span-2">
                  Rule Name
                  <input
                    required
                    maxLength={100}
                    className="form-input mt-1.5 w-full"
                    placeholder="e.g. Has phone number, High Budget"
                    value={ruleName}
                    onChange={(e) => setRuleName(e.target.value)}
                  />
                </label>

                <label className="text-xs font-semibold text-slate-600">
                  Rule Condition Type
                  <select
                    className="form-input mt-1.5 w-full text-xs"
                    value={ruleType}
                    onChange={(e) => setRuleType(e.target.value as ScoreRuleType)}
                  >
                    <option value="HAS_PHONE">📞 Contact has active phone identity</option>
                    <option value="HAS_EMAIL">✉️ Contact has active email identity</option>
                    <option value="HAS_ORGANIZATION">🏢 Contact has organization/company specified</option>
                    <option value="STAGE_MATCH">🎯 Lead is at specific stage</option>
                    <option value="FORM_SUBMISSION">📝 Contact submitted a web lead form</option>
                    <option value="HAS_DEAL">💼 Contact is linked to a sales deal</option>
                    <option value="COMMUNICATION_ACTIVITY">💬 Has communication notes/interactions</option>
                    <option value="CUSTOM_FIELD_MATCH">🏷️ Custom field attribute condition</option>
                  </select>
                </label>

                <label className="text-xs font-semibold text-slate-600">
                  Points (+ or -)
                  <input
                    type="number"
                    required
                    className="form-input mt-1.5 w-full text-xs"
                    placeholder="e.g. 20 or -50"
                    value={rulePoints}
                    onChange={(e) => setRulePoints(Number(e.target.value))}
                  />
                </label>

                {ruleType === 'STAGE_MATCH' && (
                  <label className="text-xs font-semibold text-slate-600 sm:col-span-2">
                    Target Stage
                    <select
                      className="form-input mt-1.5 w-full text-xs"
                      value={stageValue}
                      onChange={(e) => setStageValue(e.target.value)}
                    >
                      <option value="NEW">New</option>
                      <option value="CONTACTED">Contacted</option>
                      <option value="QUALIFIED">Qualified</option>
                      <option value="UNQUALIFIED">Unqualified</option>
                      <option value="CONVERTED">Converted</option>
                    </select>
                  </label>
                )}

                {ruleType === 'CUSTOM_FIELD_MATCH' && (
                  <div className="sm:col-span-2 grid gap-3 sm:grid-cols-3">
                    <label className="text-xs font-semibold text-slate-600">
                      Custom Field
                      <select
                        className="form-input mt-1.5 w-full text-xs"
                        value={cfKey}
                        onChange={(e) => setCfKey(e.target.value)}
                      >
                        {customDefs.map((def) => (
                          <option key={def.fieldKey} value={def.fieldKey}>
                            {def.label} ({def.fieldType})
                          </option>
                        ))}
                      </select>
                    </label>

                    <label className="text-xs font-semibold text-slate-600">
                      Condition
                      <select
                        className="form-input mt-1.5 w-full text-xs"
                        value={cfOp}
                        onChange={(e) => setCfOp(e.target.value)}
                      >
                        <option value="EXISTS">Is Not Empty / Present</option>
                        <option value="EQUALS">Equals Value</option>
                        <option value="GTE">Greater Than or Equal (&gt;=)</option>
                      </select>
                    </label>

                    {cfOp !== 'EXISTS' && (
                      <label className="text-xs font-semibold text-slate-600">
                        Value
                        <input
                          type="text"
                          required
                          className="form-input mt-1.5 w-full text-xs"
                          placeholder="e.g. 50000 or Enterprise"
                          value={cfValue}
                          onChange={(e) => setCfValue(e.target.value)}
                        />
                      </label>
                    )}
                  </div>
                )}

                <label className="text-xs font-semibold text-slate-600 sm:col-span-2">
                  Description (optional)
                  <textarea
                    rows={2}
                    className="form-input mt-1.5 w-full text-xs"
                    placeholder="Short description of this scoring rule"
                    value={ruleDesc}
                    onChange={(e) => setRuleDesc(e.target.value)}
                  />
                </label>
              </div>

              <div className="mt-4 flex gap-2">
                <button type="submit" disabled={busy} className="primary-button text-xs py-2 px-4">
                  {busy ? 'Saving...' : editingRule ? 'Update Rule' : 'Create Rule'}
                </button>
                <button
                  type="button"
                  disabled={busy}
                  onClick={() => setShowAddRule(false)}
                  className="secondary-button text-xs py-2 px-4"
                >
                  Cancel
                </button>
              </div>
            </form>
          </WorkspaceDialog>
        )}
      </div>
    </div>
  )
}
