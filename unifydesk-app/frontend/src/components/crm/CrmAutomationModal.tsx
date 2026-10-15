import { useEffect, useState } from 'react'
import {
  FiX,
  FiZap,
  FiPlus,
  FiTrash2,
  FiEdit2,
  FiRefreshCw,
  FiToggleLeft,
  FiToggleRight,
  FiList,
  FiCheckCircle,
  FiAlertCircle,
  FiClock,
  FiArrowRight,
  FiPlay,
} from 'react-icons/fi'
import {
  crmAutomationApi,
  type AutomationRule,
  type AutomationLog,
  type TriggerType,
  type ActionType,
  type SaveRuleInput,
} from '../../features/contacts/crmAutomationApi'
import { apiErrorMessage } from '../../lib/api/apiClient'
import WorkspaceDialog from '../ui/WorkspaceDialog'

interface CrmAutomationModalProps {
  companyId: string
  onClose: () => void
}

export default function CrmAutomationModal({
  companyId,
  onClose,
}: CrmAutomationModalProps) {
  const [activeTab, setActiveTab] = useState<'rules' | 'logs'>('rules')
  const [rules, setRules] = useState<AutomationRule[]>([])
  const [logs, setLogs] = useState<AutomationLog[]>([])
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [feedback, setFeedback] = useState('')

  // Add / Edit Modal state
  const [editingRule, setEditingRule] = useState<AutomationRule | null>(null)
  const [showAddModal, setShowAddModal] = useState(false)

  // Form states
  const [ruleName, setRuleName] = useState('')
  const [ruleDesc, setRuleDesc] = useState('')
  const [triggerType, setTriggerType] = useState<TriggerType>('LEAD_ENROLLED')
  const [triggerStage, setTriggerStage] = useState('QUALIFIED')
  const [actionType, setActionType] = useState<ActionType>('CREATE_TASK')
  const [taskTitle, setTaskTitle] = useState('Follow up with {contactName}')
  const [taskDetails, setTaskDetails] = useState('Review requirements and schedule an introduction call')
  const [taskPriority, setTaskPriority] = useState('NORMAL')
  const [dueDaysOffset, setDueDaysOffset] = useState<number>(1)
  const [assigneeType, setAssigneeType] = useState<'ROUND_ROBIN' | 'LEAD_OWNER' | 'SPECIFIC_USER'>('ROUND_ROBIN')
  const [targetLeadStage, setTargetLeadStage] = useState('CONTACTED')

  useEffect(() => {
    loadAll()
  }, [companyId])

  async function loadAll() {
    if (!companyId) return
    setLoading(true)
    setError('')
    try {
      const [r, l] = await Promise.all([
        crmAutomationApi.listRules(companyId),
        crmAutomationApi.listLogs(companyId, undefined, 50),
      ])
      setRules(r || [])
      setLogs(l || [])
    } catch (err) {
      setError(apiErrorMessage(err, 'Failed to load CRM automations'))
    } finally {
      setLoading(false)
    }
  }

  function openNewRule() {
    setEditingRule(null)
    setRuleName('')
    setRuleDesc('')
    setTriggerType('LEAD_ENROLLED')
    setTriggerStage('QUALIFIED')
    setActionType('CREATE_TASK')
    setTaskTitle('Follow up with {contactName}')
    setTaskDetails('Review requirements and schedule an introduction call')
    setTaskPriority('NORMAL')
    setDueDaysOffset(1)
    setAssigneeType('ROUND_ROBIN')
    setTargetLeadStage('CONTACTED')
    setShowAddModal(true)
  }

  function openEditRule(r: AutomationRule) {
    setEditingRule(r)
    setRuleName(r.name)
    setRuleDesc(r.description || '')
    setTriggerType(r.triggerType)
    setTriggerStage(r.triggerConfig?.toStage || 'QUALIFIED')
    setActionType(r.actionType)
    setTaskTitle(r.actionConfig?.taskTitle || 'Follow up with {contactName}')
    setTaskDetails(r.actionConfig?.taskDetails || '')
    setTaskPriority(r.actionConfig?.priority || 'NORMAL')
    setDueDaysOffset(r.actionConfig?.dueDaysOffset ?? 1)
    setAssigneeType(r.actionConfig?.assigneeType || 'ROUND_ROBIN')
    setTargetLeadStage(r.actionConfig?.targetStage || 'CONTACTED')
    setShowAddModal(true)
  }

  async function handleToggleRule(r: AutomationRule) {
    if (!companyId || busy) return
    setBusy(true)
    try {
      const updated = await crmAutomationApi.toggleRule(companyId, r.id, !r.isEnabled)
      setRules((prev) => prev.map((item) => (item.id === r.id ? updated : item)))
    } catch (err) {
      setError(apiErrorMessage(err, 'Failed to toggle automation rule'))
    } finally {
      setBusy(false)
    }
  }

  async function handleDeleteRule(ruleId: string) {
    if (!companyId || busy) return
    if (!window.confirm('Are you sure you want to delete this automation rule?')) return
    setBusy(true)
    try {
      await crmAutomationApi.deleteRule(companyId, ruleId)
      setRules((prev) => prev.filter((item) => item.id !== ruleId))
    } catch (err) {
      setError(apiErrorMessage(err, 'Failed to delete automation rule'))
    } finally {
      setBusy(false)
    }
  }

  async function handleSaveRule(e: React.FormEvent) {
    e.preventDefault()
    if (!companyId || busy) return
    setBusy(true)
    setError('')

    const triggerConfig: Record<string, any> = {}
    if (triggerType === 'LEAD_STAGE_CHANGED' || triggerType === 'DEAL_STAGE_CHANGED') {
      triggerConfig.toStage = triggerStage
    }

    const actionConfig: Record<string, any> = {}
    if (actionType === 'CREATE_TASK') {
      actionConfig.taskTitle = taskTitle.trim()
      actionConfig.taskDetails = taskDetails.trim()
      actionConfig.priority = taskPriority
      actionConfig.dueDaysOffset = Number(dueDaysOffset)
      actionConfig.assigneeType = assigneeType
    } else if (actionType === 'ASSIGN_OWNER') {
      actionConfig.assigneeType = assigneeType
    } else if (actionType === 'UPDATE_LEAD_STAGE') {
      actionConfig.targetStage = targetLeadStage
    }

    const payload: SaveRuleInput = {
      name: ruleName.trim(),
      description: ruleDesc.trim() || null,
      isEnabled: editingRule ? editingRule.isEnabled : true,
      triggerType,
      triggerConfig,
      actionType,
      actionConfig,
    }

    try {
      if (editingRule) {
        await crmAutomationApi.updateRule(companyId, editingRule.id, payload)
      } else {
        await crmAutomationApi.createRule(companyId, payload)
      }
      setShowAddModal(false)
      await loadAll()
      setFeedback('Automation rule saved successfully')
      setTimeout(() => setFeedback(''), 3000)
    } catch (err) {
      setError(apiErrorMessage(err, 'Failed to save automation rule'))
    } finally {
      setBusy(false)
    }
  }

  function getTriggerLabel(type: TriggerType, config: Record<string, any>) {
    switch (type) {
      case 'LEAD_ENROLLED':
        return '🚀 When new lead is enrolled'
      case 'LEAD_STAGE_CHANGED':
        return `🎯 When lead moves to ${config.toStage || 'any stage'}`
      case 'FORM_SUBMITTED':
        return '📝 When web lead form is submitted'
      case 'DEAL_CREATED':
        return '💼 When new sales deal is created'
      case 'DEAL_STAGE_CHANGED':
        return `🏆 When deal moves to ${config.toStage || 'any stage'}`
    }
  }

  function getActionLabel(type: ActionType, config: Record<string, any>) {
    switch (type) {
      case 'CREATE_TASK':
        return `📋 Create task: "${config.taskTitle || 'Task'}" (${config.assigneeType === 'ROUND_ROBIN' ? 'Round-Robin' : config.assigneeType || 'Owner'})`
      case 'ASSIGN_OWNER':
        return `👤 Auto-assign owner (${config.assigneeType === 'ROUND_ROBIN' ? 'Round-Robin' : 'User'})`
      case 'UPDATE_LEAD_STAGE':
        return `🎯 Move lead stage to ${config.targetStage || 'stage'}`
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm p-4 overflow-y-auto">
      <div className="relative w-full max-w-4xl rounded-2xl bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 shadow-2xl overflow-hidden flex flex-col max-h-[90vh]">
        {/* Header */}
        <div className="flex items-center justify-between border-b border-slate-200 dark:border-slate-800 px-6 py-4 bg-slate-50/50 dark:bg-slate-900/50">
          <div className="flex items-center gap-2.5">
            <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-violet-600/10 dark:bg-violet-400/10 text-violet-600 dark:text-violet-400">
              <FiZap className="h-5 w-5" />
            </div>
            <div>
              <h2 className="text-lg font-bold text-slate-900 dark:text-white flex items-center gap-2">
                CRM Workflow Automations
                <span className="rounded-full bg-violet-100 dark:bg-violet-900/40 text-violet-700 dark:text-violet-300 text-xs px-2.5 py-0.5 font-medium">
                  Triggers & Actions
                </span>
              </h2>
              <p className="text-xs text-slate-500 dark:text-slate-400">
                Trigger automatic follow-ups, round-robin assignments, and stage transitions
              </p>
            </div>
          </div>
          <button
            onClick={onClose}
            className="rounded-lg p-2 text-slate-400 hover:text-slate-600 dark:hover:text-slate-200 hover:bg-slate-100 dark:hover:bg-slate-800 transition"
          >
            <FiX className="h-5 w-5" />
          </button>
        </div>

        {/* Tab Navigation & Toolbar */}
        <div className="flex items-center justify-between border-b border-slate-200 dark:border-slate-800 px-6 py-2.5 bg-white dark:bg-slate-900">
          <div className="flex items-center gap-2">
            <button
              onClick={() => setActiveTab('rules')}
              className={`flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium rounded-lg transition ${
                activeTab === 'rules'
                  ? 'bg-violet-50 dark:bg-violet-950/50 text-violet-600 dark:text-violet-400 border border-violet-200 dark:border-violet-800'
                  : 'text-slate-600 dark:text-slate-400 hover:bg-slate-100 dark:hover:bg-slate-800'
              }`}
            >
              <FiZap className="h-3.5 w-3.5" />
              Automation Rules ({rules.length})
            </button>
            <button
              onClick={() => setActiveTab('logs')}
              className={`flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium rounded-lg transition ${
                activeTab === 'logs'
                  ? 'bg-violet-50 dark:bg-violet-950/50 text-violet-600 dark:text-violet-400 border border-violet-200 dark:border-violet-800'
                  : 'text-slate-600 dark:text-slate-400 hover:bg-slate-100 dark:hover:bg-slate-800'
              }`}
            >
              <FiList className="h-3.5 w-3.5" />
              Execution History ({logs.length})
            </button>
          </div>

          <div className="flex items-center gap-2">
            {activeTab === 'rules' && (
              <button
                onClick={openNewRule}
                className="flex items-center gap-1.5 rounded-lg bg-violet-600 text-white px-3 py-1.5 text-xs font-medium hover:bg-violet-500 shadow-sm transition"
              >
                <FiPlus className="h-3.5 w-3.5" />
                New Rule
              </button>
            )}
            <button
              onClick={loadAll}
              disabled={loading || busy}
              className="p-1.5 rounded-lg text-slate-500 hover:text-slate-700 dark:hover:text-slate-200 hover:bg-slate-100 dark:hover:bg-slate-800 transition"
              title="Refresh"
            >
              <FiRefreshCw className={`h-3.5 w-3.5 ${loading ? 'animate-spin' : ''}`} />
            </button>
          </div>
        </div>

        {/* Feedback & Error banners */}
        {feedback && (
          <div className="bg-emerald-50 dark:bg-emerald-950/50 text-emerald-700 dark:text-emerald-300 px-6 py-2 text-xs flex items-center gap-2 border-b border-emerald-200 dark:border-emerald-800">
            <FiCheckCircle className="h-4 w-4" />
            <span>{feedback}</span>
          </div>
        )}
        {error && (
          <div className="bg-rose-50 dark:bg-rose-950/50 text-rose-700 dark:text-rose-300 px-6 py-2 text-xs flex items-center gap-2 border-b border-rose-200 dark:border-rose-800">
            <FiAlertCircle className="h-4 w-4" />
            <span>{error}</span>
          </div>
        )}

        {/* Content Body */}
        <div className="flex-1 overflow-y-auto p-6 space-y-4">
          {loading && rules.length === 0 ? (
            <div className="py-16 text-center text-sm text-slate-400">Loading automations...</div>
          ) : activeTab === 'rules' ? (
            rules.length === 0 ? (
              <div className="rounded-xl border border-dashed border-slate-300 dark:border-slate-700 p-12 text-center">
                <FiZap className="mx-auto h-8 w-8 text-slate-400 mb-2" />
                <h4 className="text-sm font-semibold text-slate-700 dark:text-slate-300">
                  No Automation Rules Created Yet
                </h4>
                <p className="text-xs text-slate-400 mt-1 max-w-sm mx-auto">
                  Automate task assignments, follow-ups, and pipeline transitions when leads arrive or deals close.
                </p>
                <button
                  onClick={openNewRule}
                  className="mt-4 inline-flex items-center gap-1.5 rounded-lg bg-violet-600 text-white px-3.5 py-1.5 text-xs font-medium hover:bg-violet-500 shadow-sm"
                >
                  <FiPlus className="h-3.5 w-3.5" />
                  Create First Automation Rule
                </button>
              </div>
            ) : (
              <div className="space-y-3">
                {rules.map((r) => (
                  <div
                    key={r.id}
                    className={`rounded-xl border transition p-4 flex flex-col sm:flex-row sm:items-center justify-between gap-4 ${
                      r.isEnabled
                        ? 'border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900/60 shadow-sm hover:border-violet-300 dark:hover:border-violet-800'
                        : 'border-slate-200 dark:border-slate-800 bg-slate-50/60 dark:bg-slate-900/30 opacity-60'
                    }`}
                  >
                    <div className="space-y-2 flex-1 min-w-0">
                      <div className="flex items-center gap-2 flex-wrap">
                        <span className="font-semibold text-sm text-slate-900 dark:text-white">
                          {r.name}
                        </span>
                        {r.isEnabled ? (
                          <span className="rounded-full bg-emerald-50 dark:bg-emerald-950/60 text-emerald-700 dark:text-emerald-400 text-[10px] font-medium px-2 py-0.5 border border-emerald-200 dark:border-emerald-800">
                            Active
                          </span>
                        ) : (
                          <span className="rounded-full bg-slate-100 dark:bg-slate-800 text-slate-500 text-[10px] font-medium px-2 py-0.5">
                            Paused
                          </span>
                        )}
                        <span className="text-[11px] text-slate-400 flex items-center gap-1 ml-auto sm:ml-0">
                          <FiPlay className="h-3 w-3" />
                          {r.executionCount} runs
                        </span>
                      </div>

                      {r.description && (
                        <p className="text-xs text-slate-500 dark:text-slate-400 truncate">
                          {r.description}
                        </p>
                      )}

                      {/* Trigger → Action Pathway visualization */}
                      <div className="flex items-center gap-2 text-xs flex-wrap pt-1">
                        <span className="inline-flex items-center gap-1 rounded-lg bg-blue-50 dark:bg-blue-950/50 text-blue-700 dark:text-blue-300 px-2.5 py-1 border border-blue-200 dark:border-blue-800">
                          {getTriggerLabel(r.triggerType, r.triggerConfig)}
                        </span>
                        <FiArrowRight className="h-3.5 w-3.5 text-slate-400 shrink-0" />
                        <span className="inline-flex items-center gap-1 rounded-lg bg-purple-50 dark:bg-purple-950/50 text-purple-700 dark:text-purple-300 px-2.5 py-1 border border-purple-200 dark:border-purple-800">
                          {getActionLabel(r.actionType, r.actionConfig)}
                        </span>
                      </div>
                    </div>

                    {/* Actions and Toggle */}
                    <div className="flex items-center gap-2 shrink-0 self-end sm:self-center border-t sm:border-t-0 pt-2 sm:pt-0 border-slate-100 dark:border-slate-800">
                      <button
                        onClick={() => handleToggleRule(r)}
                        disabled={busy}
                        className={`text-2xl transition ${
                          r.isEnabled
                            ? 'text-violet-600 hover:text-violet-500'
                            : 'text-slate-300 dark:text-slate-600 hover:text-slate-400'
                        }`}
                        title={r.isEnabled ? 'Deactivate Rule' : 'Activate Rule'}
                      >
                        {r.isEnabled ? <FiToggleRight /> : <FiToggleLeft />}
                      </button>

                      <button
                        onClick={() => openEditRule(r)}
                        className="rounded-lg p-2 text-slate-400 hover:text-slate-600 dark:hover:text-slate-200 hover:bg-slate-100 dark:hover:bg-slate-800 transition"
                        title="Edit Rule"
                      >
                        <FiEdit2 className="h-4 w-4" />
                      </button>

                      <button
                        onClick={() => handleDeleteRule(r.id)}
                        className="rounded-lg p-2 text-slate-400 hover:text-rose-600 hover:bg-rose-50 dark:hover:bg-rose-950/50 transition"
                        title="Delete Rule"
                      >
                        <FiTrash2 className="h-4 w-4" />
                      </button>
                    </div>
                  </div>
                ))}
              </div>
            )
          ) : (
            /* Execution History & Audit Logs */
            logs.length === 0 ? (
              <div className="rounded-xl border border-dashed border-slate-300 dark:border-slate-700 p-12 text-center text-xs text-slate-400">
                <FiClock className="mx-auto h-8 w-8 text-slate-300 mb-2" />
                No execution logs recorded yet. Once triggers fire, audit history will appear here.
              </div>
            ) : (
              <div className="overflow-x-auto rounded-xl border border-slate-200 dark:border-slate-800">
                <table className="w-full text-left text-xs">
                  <thead className="bg-slate-50 dark:bg-slate-800/60 text-slate-500 border-b border-slate-200 dark:border-slate-800">
                    <tr>
                      <th className="py-2.5 px-3 font-semibold">Time</th>
                      <th className="py-2.5 px-3 font-semibold">Rule</th>
                      <th className="py-2.5 px-3 font-semibold">Trigger</th>
                      <th className="py-2.5 px-3 font-semibold">Status</th>
                      <th className="py-2.5 px-3 font-semibold">Execution Details</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-100 dark:divide-slate-800">
                    {logs.map((log) => (
                      <tr key={log.id} className="hover:bg-slate-50/50 dark:hover:bg-slate-800/30">
                        <td className="py-2.5 px-3 text-slate-400 whitespace-nowrap">
                          {new Date(log.executedAt).toLocaleTimeString([], {
                            hour: '2-digit',
                            minute: '2-digit',
                            second: '2-digit',
                          })}
                        </td>
                        <td className="py-2.5 px-3 font-medium text-slate-800 dark:text-slate-200 whitespace-nowrap">
                          {log.ruleName}
                        </td>
                        <td className="py-2.5 px-3 text-slate-600 dark:text-slate-400 whitespace-nowrap">
                          {log.triggerType}
                        </td>
                        <td className="py-2.5 px-3 whitespace-nowrap">
                          {log.status === 'SUCCESS' ? (
                            <span className="inline-flex items-center gap-1 rounded-full bg-emerald-50 dark:bg-emerald-950/60 text-emerald-700 dark:text-emerald-400 px-2 py-0.5 text-[10px] font-medium border border-emerald-200 dark:border-emerald-800">
                              <FiCheckCircle className="h-3 w-3" />
                              Success
                            </span>
                          ) : log.status === 'SKIPPED' ? (
                            <span className="inline-flex items-center gap-1 rounded-full bg-amber-50 dark:bg-amber-950/60 text-amber-700 dark:text-amber-400 px-2 py-0.5 text-[10px] font-medium border border-amber-200 dark:border-amber-800">
                              Skipped
                            </span>
                          ) : (
                            <span className="inline-flex items-center gap-1 rounded-full bg-rose-50 dark:bg-rose-950/60 text-rose-700 dark:text-rose-400 px-2 py-0.5 text-[10px] font-medium border border-rose-200 dark:border-rose-800">
                              <FiAlertCircle className="h-3 w-3" />
                              Failed
                            </span>
                          )}
                        </td>
                        <td className="py-2.5 px-3 text-slate-500 dark:text-slate-400">
                          {log.details}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )
          )}
        </div>

        {/* Add / Edit Automation Rule Modal */}
        {showAddModal && (
          <WorkspaceDialog
            title={editingRule ? 'Edit Automation Rule' : 'New Automation Rule'}
            wide
            busy={busy}
            error={error}
            onClose={() => setShowAddModal(false)}
          >
            <form onSubmit={handleSaveRule} className="space-y-4">
              <label className="block text-xs font-semibold text-slate-700 dark:text-slate-300">
                Rule Name
                <input
                  required
                  maxLength={120}
                  className="form-input mt-1.5 w-full text-xs"
                  placeholder="e.g. Onboard Won Client, Assign Inbound Lead"
                  value={ruleName}
                  onChange={(e) => setRuleName(e.target.value)}
                />
              </label>

              <label className="block text-xs font-semibold text-slate-700 dark:text-slate-300">
                Description (Optional)
                <input
                  maxLength={250}
                  className="form-input mt-1.5 w-full text-xs"
                  placeholder="Explain why this rule runs..."
                  value={ruleDesc}
                  onChange={(e) => setRuleDesc(e.target.value)}
                />
              </label>

              {/* Trigger Configuration */}
              <div className="rounded-xl border border-slate-200 dark:border-slate-800 bg-slate-50/50 dark:bg-slate-900/50 p-4 space-y-3">
                <h4 className="text-xs font-bold uppercase tracking-wider text-slate-500">
                  Step 1: When this event happens (Trigger)
                </h4>

                <div className="grid gap-3 sm:grid-cols-2">
                  <label className="text-xs font-medium text-slate-700 dark:text-slate-300">
                    Trigger Event
                    <select
                      className="form-input mt-1 w-full text-xs"
                      value={triggerType}
                      onChange={(e) => setTriggerType(e.target.value as TriggerType)}
                    >
                      <option value="LEAD_ENROLLED">🚀 New Lead Enrolled (Manual or Inbound)</option>
                      <option value="FORM_SUBMITTED">📝 Web Lead Form Submitted</option>
                      <option value="LEAD_STAGE_CHANGED">🎯 Lead Stage Changed</option>
                      <option value="DEAL_CREATED">💼 New Sales Deal Created</option>
                      <option value="DEAL_STAGE_CHANGED">🏆 Deal Stage Changed (Won / Lost / Custom)</option>
                    </select>
                  </label>

                  {(triggerType === 'LEAD_STAGE_CHANGED' || triggerType === 'DEAL_STAGE_CHANGED') && (
                    <label className="text-xs font-medium text-slate-700 dark:text-slate-300">
                      When Moved to Stage
                      <select
                        className="form-input mt-1 w-full text-xs"
                        value={triggerStage}
                        onChange={(e) => setTriggerStage(e.target.value)}
                      >
                        {triggerType === 'LEAD_STAGE_CHANGED' ? (
                          <>
                            <option value="ALL">Any Stage</option>
                            <option value="CONTACTED">Contacted</option>
                            <option value="QUALIFIED">Qualified</option>
                            <option value="UNQUALIFIED">Unqualified</option>
                            <option value="CONVERTED">Converted</option>
                          </>
                        ) : (
                          <>
                            <option value="ALL">Any Stage</option>
                            <option value="WON">WON (Closed-Won)</option>
                            <option value="LOST">LOST (Closed-Lost)</option>
                            <option value="PROPOSAL">Proposal</option>
                            <option value="NEGOTIATION">Negotiation</option>
                            <option value="QUALIFIED">Qualified</option>
                          </>
                        )}
                      </select>
                    </label>
                  )}
                </div>
              </div>

              {/* Action Configuration */}
              <div className="rounded-xl border border-slate-200 dark:border-slate-800 bg-slate-50/50 dark:bg-slate-900/50 p-4 space-y-3">
                <h4 className="text-xs font-bold uppercase tracking-wider text-slate-500">
                  Step 2: Take this action
                </h4>

                <label className="block text-xs font-medium text-slate-700 dark:text-slate-300">
                  Action Type
                  <select
                    className="form-input mt-1 w-full text-xs"
                    value={actionType}
                    onChange={(e) => setActionType(e.target.value as ActionType)}
                  >
                    <option value="CREATE_TASK">📋 Automatically create CRM Task</option>
                    <option value="ASSIGN_OWNER">👤 Auto-assign Contact Owner (Round-Robin)</option>
                    <option value="UPDATE_LEAD_STAGE">🎯 Automatically advance Lead Stage</option>
                  </select>
                </label>

                {actionType === 'CREATE_TASK' && (
                  <div className="space-y-3 pt-2">
                    <label className="block text-xs font-medium text-slate-700 dark:text-slate-300">
                      Task Title
                      <input
                        required
                        className="form-input mt-1 w-full text-xs"
                        placeholder="e.g. Onboard client {contactName}"
                        value={taskTitle}
                        onChange={(e) => setTaskTitle(e.target.value)}
                      />
                      <span className="text-[11px] text-slate-400">
                        Supports tokens: <code>{'{contactName}'}</code>, <code>{'{dealName}'}</code>, <code>{'{stage}'}</code>
                      </span>
                    </label>

                    <label className="block text-xs font-medium text-slate-700 dark:text-slate-300">
                      Task Details / Instructions
                      <textarea
                        rows={2}
                        className="form-input mt-1 w-full text-xs"
                        placeholder="Instructions for the assigned agent..."
                        value={taskDetails}
                        onChange={(e) => setTaskDetails(e.target.value)}
                      />
                    </label>

                    <div className="grid gap-3 sm:grid-cols-3">
                      <label className="text-xs font-medium text-slate-700 dark:text-slate-300">
                        Priority
                        <select
                          className="form-input mt-1 w-full text-xs"
                          value={taskPriority}
                          onChange={(e) => setTaskPriority(e.target.value)}
                        >
                          <option value="LOW">Low</option>
                          <option value="NORMAL">Normal</option>
                          <option value="HIGH">High (Urgent)</option>
                        </select>
                      </label>

                      <label className="text-xs font-medium text-slate-700 dark:text-slate-300">
                        Due After (Days)
                        <input
                          type="number"
                          min={0}
                          max={90}
                          required
                          className="form-input mt-1 w-full text-xs"
                          value={dueDaysOffset}
                          onChange={(e) => setDueDaysOffset(Number(e.target.value))}
                        />
                      </label>

                      <label className="text-xs font-medium text-slate-700 dark:text-slate-300">
                        Assignee
                        <select
                          className="form-input mt-1 w-full text-xs"
                          value={assigneeType}
                          onChange={(e) => setAssigneeType(e.target.value as any)}
                        >
                          <option value="ROUND_ROBIN">🔄 Round-Robin Team</option>
                          <option value="LEAD_OWNER">👤 Existing Lead Owner</option>
                        </select>
                      </label>
                    </div>
                  </div>
                )}

                {actionType === 'ASSIGN_OWNER' && (
                  <label className="block text-xs font-medium text-slate-700 dark:text-slate-300 pt-2">
                    Assignment Method
                    <select
                      className="form-input mt-1 w-full text-xs"
                      value={assigneeType}
                      onChange={(e) => setAssigneeType(e.target.value as any)}
                    >
                      <option value="ROUND_ROBIN">🔄 Round-Robin (Rotates evenly among active team members)</option>
                    </select>
                  </label>
                )}

                {actionType === 'UPDATE_LEAD_STAGE' && (
                  <label className="block text-xs font-medium text-slate-700 dark:text-slate-300 pt-2">
                    Advance Lead to Stage
                    <select
                      className="form-input mt-1 w-full text-xs"
                      value={targetLeadStage}
                      onChange={(e) => setTargetLeadStage(e.target.value)}
                    >
                      <option value="CONTACTED">Contacted</option>
                      <option value="QUALIFIED">Qualified</option>
                      <option value="UNQUALIFIED">Unqualified</option>
                      <option value="CONVERTED">Converted</option>
                    </select>
                  </label>
                )}
              </div>

              <div className="flex items-center justify-end gap-2 pt-2">
                <button
                  type="button"
                  onClick={() => setShowAddModal(false)}
                  className="rounded-lg border border-slate-200 dark:border-slate-700 px-4 py-2 text-xs font-medium text-slate-700 dark:text-slate-300 hover:bg-slate-50 dark:hover:bg-slate-800"
                >
                  Cancel
                </button>
                <button type="submit" disabled={busy} className="primary-button text-xs py-2 px-4">
                  {busy ? 'Saving...' : editingRule ? 'Update Rule' : 'Save Automation Rule'}
                </button>
              </div>
            </form>
          </WorkspaceDialog>
        )}
      </div>
    </div>
  )
}
