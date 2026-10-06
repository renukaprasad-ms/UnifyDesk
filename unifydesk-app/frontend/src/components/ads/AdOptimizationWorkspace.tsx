import { useEffect, useMemo, useState, type FormEvent } from 'react'
import { FiActivity, FiRefreshCw, FiSliders, FiUsers } from 'react-icons/fi'
import { adCenterApi } from '../../features/ads/adCenterApi'
import type {
  AdAccount,
  AdCampaign,
  AdExperimentOverview,
  AdOptimizationPolicy,
  AdOptimizationPolicyMode,
  AdOptimizationPolicyType,
  FirstPartyAudienceBuildResult,
  GoogleCampaignSimulation,
  ReachlyoBudgetRecommendation,
  ReachlyoCampaignSummary,
  ReachlyoFunnelAttribution,
} from '../../features/ads/types'
import { apiErrorMessage } from '../../lib/api/apiClient'
import ConfirmDialog from '../ui/ConfirmDialog'

type Props = {
  companyId: string
  selectedAccount: AdAccount
  accounts: AdAccount[]
  from: string
  to: string
  canCreate: boolean
  canModify: boolean
}

const pretty = (value?: string | null) =>
  value
    ? value
        .replaceAll('_', ' ')
        .toLowerCase()
        .replace(/\b\w/g, (character) => character.toUpperCase())
    : '—'

const number = (value?: number | null, digits = 2) =>
  value == null
    ? '—'
    : new Intl.NumberFormat(undefined, { maximumFractionDigits: digits }).format(value)

const percent = (value?: number | null) => (value == null ? '—' : `${number(value, 1)}%`)

export default function AdOptimizationWorkspace({
  companyId,
  selectedAccount,
  accounts,
  from,
  to,
  canCreate,
  canModify,
}: Props) {
  const [experiments, setExperiments] = useState<AdExperimentOverview>({
    experiments: [],
    failures: [],
  })
  const [policies, setPolicies] = useState<AdOptimizationPolicy[]>([])
  const [providerCampaigns, setProviderCampaigns] = useState<AdCampaign[]>([])
  const [reachlyoCampaigns, setReachlyoCampaigns] = useState<ReachlyoCampaignSummary[]>([])
  const [selectedProviderCampaignId, setSelectedProviderCampaignId] = useState('')
  const [selectedReachlyoCampaignId, setSelectedReachlyoCampaignId] = useState('')
  const [simulations, setSimulations] = useState<GoogleCampaignSimulation[]>([])
  const [budgetRecommendation, setBudgetRecommendation] =
    useState<ReachlyoBudgetRecommendation | null>(null)
  const [funnel, setFunnel] = useState<ReachlyoFunnelAttribution | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [policyToApprove, setPolicyToApprove] = useState<AdOptimizationPolicy | null>(null)

  const [policyName, setPolicyName] = useState('')
  const [policyType, setPolicyType] = useState<AdOptimizationPolicyType>('AUTOMATION_RULE')
  const [policyMode, setPolicyMode] = useState<AdOptimizationPolicyMode>('RECOMMENDATION_ONLY')
  const [metric, setMetric] = useState('CPA')
  const [operator, setOperator] = useState('GT')
  const [threshold, setThreshold] = useState('0')
  const [minimumConversions, setMinimumConversions] = useState('10')
  const [cooldownHours, setCooldownHours] = useState('24')
  const [action, setAction] = useState('NOTIFY_ONLY')
  const [segmentType, setSegmentType] = useState('CLICKED_NOT_CONVERTED')
  const [policyCampaignId, setPolicyCampaignId] = useState('')

  const [audienceName, setAudienceName] = useState('')
  const [audienceRows, setAudienceRows] = useState('')
  const [targetAccountIds, setTargetAccountIds] = useState<string[]>([selectedAccount.id])
  const [googleTerms, setGoogleTerms] = useState(false)
  const [metaTerms, setMetaTerms] = useState(false)
  const [adUserDataConsent, setAdUserDataConsent] = useState(false)
  const [adPersonalizationConsent, setAdPersonalizationConsent] = useState(false)
  const [audienceResult, setAudienceResult] = useState<FirstPartyAudienceBuildResult | null>(null)

  const managedAccounts = useMemo(
    () => accounts.filter((account) => account.managed && account.accessible),
    [accounts],
  )

  const load = async () => {
    setLoading(true)
    setError('')
    try {
      const [experimentData, policyData, providerPage, reachlyoPage] = await Promise.all([
        adCenterApi.optimizationExperiments(companyId),
        adCenterApi.optimizationPolicies(companyId),
        adCenterApi.campaigns(companyId, selectedAccount.id, 0, 100),
        adCenterApi.reachlyoCampaigns(companyId, 0, 100),
      ])
      setExperiments(experimentData)
      setPolicies(policyData)
      setProviderCampaigns(providerPage.content)
      setReachlyoCampaigns(reachlyoPage.content)
      setSelectedProviderCampaignId((current) =>
        current && providerPage.content.some((campaign) => campaign.id === current)
          ? current
          : (providerPage.content[0]?.id ?? ''),
      )
      setSelectedReachlyoCampaignId((current) =>
        current && reachlyoPage.content.some((campaign) => campaign.id === current)
          ? current
          : (reachlyoPage.content[0]?.id ?? ''),
      )
    } catch (loadError) {
      setError(apiErrorMessage(loadError, 'Unable to load Ad Center optimization data'))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    void load()
  }, [companyId, selectedAccount.id])

  useEffect(() => {
    setTargetAccountIds((current) =>
      current.includes(selectedAccount.id) ? current : [selectedAccount.id],
    )
  }, [selectedAccount.id])

  useEffect(() => {
    const loadSimulation = async () => {
      if (selectedAccount.provider !== 'GOOGLE_ADS' || !selectedProviderCampaignId) {
        setSimulations([])
        return
      }
      try {
        setSimulations(
          await adCenterApi.googleCampaignSimulations(companyId, selectedProviderCampaignId),
        )
      } catch {
        setSimulations([])
      }
    }
    void loadSimulation()
  }, [companyId, selectedAccount.provider, selectedProviderCampaignId])

  useEffect(() => {
    const loadUnifiedEvidence = async () => {
      if (!selectedReachlyoCampaignId) {
        setBudgetRecommendation(null)
        setFunnel(null)
        return
      }
      try {
        const [budget, funnelData] = await Promise.all([
          adCenterApi.reachlyoBudgetRecommendations(
            companyId,
            selectedReachlyoCampaignId,
            from,
            to,
          ),
          adCenterApi.reachlyoFunnelAttribution(companyId, selectedReachlyoCampaignId, from, to),
        ])
        setBudgetRecommendation(budget)
        setFunnel(funnelData)
      } catch {
        setBudgetRecommendation(null)
        setFunnel(null)
      }
    }
    void loadUnifiedEvidence()
  }, [companyId, selectedReachlyoCampaignId, from, to])

  const createPolicy = async (event: FormEvent) => {
    event.preventDefault()
    if (!policyName.trim()) return
    setError('')
    setNotice('')
    try {
      const config =
        policyType === 'AUTOMATION_RULE'
          ? {
              metric,
              operator,
              threshold: Number(threshold),
              minimumConversions: Number(minimumConversions),
              cooldownHours: Number(cooldownHours),
              lookbackDays: 7,
              maxDailyBudgetMovementPercent: 20,
              action,
            }
          : {
              segmentType,
              lookbackDays: 30,
              minimumCandidateCount: 1,
            }
      await adCenterApi.createOptimizationPolicy(companyId, {
        name: policyName.trim(),
        policyType,
        mode: policyMode,
        provider: selectedAccount.provider,
        adAccountId: selectedAccount.id,
        campaignId: policyCampaignId || null,
        configJson: JSON.stringify(config),
        enabled: true,
      })
      setPolicyName('')
      setNotice('Optimization policy created.')
      setPolicies(await adCenterApi.optimizationPolicies(companyId))
    } catch (policyError) {
      setError(apiErrorMessage(policyError, 'Unable to create optimization policy'))
    }
  }

  const evaluatePolicy = async (policy: AdOptimizationPolicy) => {
    setError('')
    try {
      const result = await adCenterApi.evaluateOptimizationPolicy(companyId, policy.id)
      setNotice(result.message)
      setPolicies(await adCenterApi.optimizationPolicies(companyId))
    } catch (evaluationError) {
      setError(apiErrorMessage(evaluationError, 'Unable to evaluate optimization policy'))
    }
  }

  const approvePolicy = async (policy: AdOptimizationPolicy) => {
    setError('')
    try {
      const result = await adCenterApi.approveOptimizationPolicy(companyId, policy.id)
      setNotice(result.message)
      setPolicies(await adCenterApi.optimizationPolicies(companyId))
      setPolicyToApprove(null)
    } catch (approvalError) {
      setError(apiErrorMessage(approvalError, 'Unable to approve optimization action'))
    }
  }

  const togglePolicy = async (policy: AdOptimizationPolicy) => {
    try {
      const updated = await adCenterApi.setOptimizationPolicyEnabled(
        companyId,
        policy.id,
        !policy.enabled,
      )
      setPolicies((current) => current.map((item) => (item.id === updated.id ? updated : item)))
    } catch (toggleError) {
      setError(apiErrorMessage(toggleError, 'Unable to update optimization policy'))
    }
  }

  const buildAudience = async (event: FormEvent) => {
    event.preventDefault()
    const members = audienceRows
      .split('\n')
      .map((line) => line.trim())
      .filter(Boolean)
      .map((line) => {
        const [email = '', phone = '', externalId = ''] = line
          .split(',')
          .map((value) => value.trim())
        return {
          email: email || null,
          phone: phone || null,
          externalId: externalId || null,
        }
      })
    if (!audienceName.trim() || members.length === 0 || targetAccountIds.length === 0) {
      setError(
        'Audience name, at least one member, and at least one provider account are required.',
      )
      return
    }
    try {
      const targets = targetAccountIds
        .map((id) => managedAccounts.find((account) => account.id === id))
        .filter((account): account is AdAccount => Boolean(account))
        .map((account) => ({ provider: account.provider, adAccountId: account.id }))
      const result = await adCenterApi.buildFirstPartyAudience(companyId, {
        name: audienceName.trim(),
        membershipDays: 30,
        targets,
        members,
        googleCustomerMatchTermsAccepted: googleTerms,
        adUserDataConsent,
        adPersonalizationConsent,
        metaCustomAudienceTermsAccepted: metaTerms,
      })
      setAudienceResult(result)
      setNotice(
        'First-party audience submission completed. Raw identifiers were not retained by the optimization workspace.',
      )
      setAudienceRows('')
    } catch (audienceError) {
      setError(apiErrorMessage(audienceError, 'Unable to build first-party audience'))
    }
  }

  return (
    <section className="panel mt-6 overflow-hidden">
      <div className="flex flex-wrap items-start justify-between gap-3 border-b border-slate-100 px-5 py-4">
        <div>
          <div className="flex items-center gap-2">
            <FiSliders className="text-indigo-500" aria-hidden="true" />
            <h2 className="text-base font-bold text-slate-950">Optimization lab</h2>
          </div>
          <p className="mt-1 max-w-3xl text-xs leading-5 text-slate-500">
            Experiments, simulations, guarded automation, budget evidence, first-party audiences,
            funnel attribution, and lifecycle retargeting controls.
          </p>
        </div>
        <button
          type="button"
          className="secondary-button inline-flex items-center gap-2"
          disabled={loading}
          onClick={() => void load()}
        >
          <FiRefreshCw className={loading ? 'animate-spin' : ''} aria-hidden="true" />
          Refresh
        </button>
      </div>

      {error && (
        <div className="m-5 rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
          {error}
        </div>
      )}
      {notice && (
        <div className="m-5 rounded-xl border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-700">
          {notice}
        </div>
      )}

      <div className="grid gap-6 p-5 xl:grid-cols-2">
        <div className="rounded-2xl border border-slate-100 p-4">
          <h3 className="text-sm font-bold text-slate-950">Cross-provider experiments</h3>
          <p className="mt-1 text-xs text-slate-500">
            Google experiments are API-managed. Meta Ad Studies are discovery-only until a stable
            public customer-facing creation contract is available.
          </p>
          <div className="mt-4 space-y-3">
            {experiments.experiments.length === 0 ? (
              <p className="text-xs text-slate-400">No provider experiments discovered.</p>
            ) : (
              experiments.experiments.slice(0, 12).map((experiment) => (
                <div
                  key={`${experiment.provider}-${experiment.adAccountId}-${experiment.providerExperimentId}`}
                  className="rounded-xl bg-slate-50 p-3"
                >
                  <div className="flex flex-wrap items-center justify-between gap-2">
                    <span className="text-xs font-bold text-slate-900">
                      {experiment.name || 'Experiment'}
                    </span>
                    <span className="rounded-full bg-white px-2 py-1 text-[10px] font-semibold text-slate-500">
                      {pretty(experiment.provider)} · {pretty(experiment.managementMode)}
                    </span>
                  </div>
                  <p className="mt-1 text-[11px] text-slate-500">
                    {pretty(experiment.type)} · {pretty(experiment.status)} · clicks{' '}
                    {number(experiment.clicks, 0)} · conversions {number(experiment.conversions)}
                  </p>
                </div>
              ))
            )}
            {experiments.failures.map((failure) => (
              <p
                key={`${failure.provider}-${failure.adAccountId}`}
                className="text-[11px] text-amber-700"
              >
                {pretty(failure.provider)}: {failure.message}
              </p>
            ))}
          </div>
        </div>

        <div className="rounded-2xl border border-slate-100 p-4">
          <h3 className="text-sm font-bold text-slate-950">Google bid & budget simulations</h3>
          {selectedAccount.provider !== 'GOOGLE_ADS' ? (
            <p className="mt-2 text-xs text-slate-500">
              Select a managed Google Ads account to inspect provider simulation curves.
            </p>
          ) : (
            <>
              <select
                className="form-input mt-3"
                value={selectedProviderCampaignId}
                onChange={(event) => setSelectedProviderCampaignId(event.target.value)}
              >
                <option value="">Choose campaign</option>
                {providerCampaigns.map((campaign) => (
                  <option key={campaign.id} value={campaign.id}>
                    {campaign.name}
                  </option>
                ))}
              </select>
              <p className="mt-2 text-[11px] leading-5 text-slate-400">
                Google simulations are historical counterfactuals based on prior traffic. They are
                planning evidence, not guaranteed forecasts.
              </p>
              <div className="mt-3 space-y-2">
                {simulations.length === 0 ? (
                  <p className="text-xs text-slate-400">
                    No current campaign simulation returned by Google.
                  </p>
                ) : (
                  simulations.map((simulation) => (
                    <div
                      key={simulation.resourceName ?? simulation.type}
                      className="rounded-xl bg-slate-50 p-3"
                    >
                      <p className="text-xs font-bold text-slate-900">{pretty(simulation.type)}</p>
                      <p className="mt-1 text-[11px] text-slate-500">
                        {simulation.points.length} provider points · {simulation.startDate || '—'} →{' '}
                        {simulation.endDate || '—'}
                      </p>
                    </div>
                  ))
                )}
              </div>
            </>
          )}
        </div>

        <div className="rounded-2xl border border-slate-100 p-4 xl:col-span-2">
          <h3 className="text-sm font-bold text-slate-950">Unified budget & funnel evidence</h3>
          <select
            className="form-input mt-3 max-w-xl"
            value={selectedReachlyoCampaignId}
            onChange={(event) => setSelectedReachlyoCampaignId(event.target.value)}
          >
            <option value="">Choose UnifyDesk campaign</option>
            {reachlyoCampaigns.map((campaign) => (
              <option key={campaign.id} value={campaign.id}>
                {campaign.name}
              </option>
            ))}
          </select>

          {budgetRecommendation && (
            <div className="mt-4">
              <p className="text-xs leading-5 text-slate-500">
                Confidence: <strong>{pretty(budgetRecommendation.confidence)}</strong>.{' '}
                {budgetRecommendation.methodology}
              </p>
              <div className="mt-3 grid gap-3 md:grid-cols-2 lg:grid-cols-3">
                {budgetRecommendation.channels.map((channel) => (
                  <div key={channel.channelId} className="rounded-xl bg-slate-50 p-3">
                    <p className="text-xs font-bold text-slate-900">
                      {pretty(channel.channelType || channel.provider)}
                    </p>
                    <p className="mt-1 text-lg font-bold text-slate-950">
                      {percent(channel.recommendedPercent)}
                    </p>
                    <p className="text-[11px] text-slate-500">
                      Historical spend {percent(channel.historicalSpendSharePercent)} · conversions{' '}
                      {number(channel.conversions)}
                    </p>
                  </div>
                ))}
              </div>
            </div>
          )}

          {funnel && (
            <div className="mt-4 rounded-xl border border-slate-100 p-4">
              <p className="text-xs font-bold text-slate-900">
                Normalized provider-attributed funnel
              </p>
              <div className="mt-3 grid grid-cols-2 gap-3 sm:grid-cols-4 lg:grid-cols-6">
                {[
                  ['Impressions', number(funnel.totals.impressions, 0)],
                  ['Clicks', number(funnel.totals.clicks, 0)],
                  ['Leads', number(funnel.totals.leads)],
                  ['Conversions', number(funnel.totals.conversions)],
                  ['Purchases', number(funnel.totals.purchases)],
                  ['Click → conversion', percent(funnel.totals.clickToConversionRate)],
                ].map(([label, value]) => (
                  <div key={label}>
                    <p className="text-[10px] uppercase text-slate-400">{label}</p>
                    <p className="mt-1 text-sm font-bold text-slate-900">{value}</p>
                  </div>
                ))}
              </div>
              <p className="mt-3 text-[11px] leading-5 text-slate-400">{funnel.limitation}</p>
            </div>
          )}
        </div>

        {canModify && (
          <form className="rounded-2xl border border-slate-100 p-4" onSubmit={createPolicy}>
            <h3 className="text-sm font-bold text-slate-950">Automation & lifecycle policy</h3>
            <div className="mt-3 grid gap-3 sm:grid-cols-2">
              <input
                className="form-input"
                value={policyName}
                onChange={(event) => setPolicyName(event.target.value)}
                placeholder="Policy name"
                required
              />
              <select
                className="form-input"
                value={policyType}
                onChange={(event) => setPolicyType(event.target.value as AdOptimizationPolicyType)}
              >
                <option value="AUTOMATION_RULE">Automation rule</option>
                <option value="LIFECYCLE_RETARGETING">Lifecycle / retargeting</option>
              </select>
              <select
                className="form-input"
                value={policyMode}
                onChange={(event) => setPolicyMode(event.target.value as AdOptimizationPolicyMode)}
              >
                <option value="RECOMMENDATION_ONLY">Recommendation only</option>
                <option value="APPROVAL_REQUIRED">Approval required</option>
                <option value="AUTOMATIC">Automatic with guardrails</option>
              </select>
              <select
                className="form-input"
                value={policyCampaignId}
                onChange={(event) => setPolicyCampaignId(event.target.value)}
              >
                <option value="">Account scope</option>
                {providerCampaigns.map((campaign) => (
                  <option key={campaign.id} value={campaign.id}>
                    {campaign.name}
                  </option>
                ))}
              </select>
            </div>

            {policyType === 'AUTOMATION_RULE' ? (
              <div className="mt-3 grid gap-3 sm:grid-cols-2">
                <select
                  className="form-input"
                  value={metric}
                  onChange={(event) => setMetric(event.target.value)}
                >
                  <option value="SPEND">Spend</option>
                  <option value="CPA">CPA</option>
                  <option value="ROAS">ROAS</option>
                  <option value="CONVERSIONS">Conversions</option>
                  <option value="CTR">CTR</option>
                </select>
                <select
                  className="form-input"
                  value={operator}
                  onChange={(event) => setOperator(event.target.value)}
                >
                  <option value="GT">&gt;</option>
                  <option value="GTE">≥</option>
                  <option value="LT">&lt;</option>
                  <option value="LTE">≤</option>
                </select>
                <input
                  className="form-input"
                  type="number"
                  step="0.01"
                  value={threshold}
                  onChange={(event) => setThreshold(event.target.value)}
                  placeholder="Threshold"
                />
                <input
                  className="form-input"
                  type="number"
                  min="0"
                  value={minimumConversions}
                  onChange={(event) => setMinimumConversions(event.target.value)}
                  placeholder="Minimum conversions"
                />
                <input
                  className="form-input"
                  type="number"
                  min="0"
                  max="720"
                  value={cooldownHours}
                  onChange={(event) => setCooldownHours(event.target.value)}
                  placeholder="Cooldown hours"
                />
                <select
                  className="form-input"
                  value={action}
                  onChange={(event) => setAction(event.target.value)}
                >
                  <option value="NOTIFY_ONLY">Notify only</option>
                  <option value="PAUSE_CAMPAIGN">Pause campaign</option>
                  <option value="ENABLE_CAMPAIGN">Enable campaign</option>
                  <option value="RECOMMEND_BUDGET_CHANGE">Recommend budget change</option>
                  <option value="RECOMMEND_NEGATIVE_KEYWORD">Recommend negative keyword</option>
                  <option value="RECOMMEND_CREATIVE_REFRESH">Recommend creative refresh</option>
                </select>
              </div>
            ) : (
              <select
                className="form-input mt-3"
                value={segmentType}
                onChange={(event) => setSegmentType(event.target.value)}
              >
                <option value="CLICKED_NOT_CONVERTED">Clicked ad, not converted</option>
                <option value="LEAD_NOT_PURCHASED">Lead, not purchased</option>
                <option value="INACTIVE_CUSTOMER">Inactive customer</option>
                <option value="HIGH_VALUE_CUSTOMER">High-value customer</option>
                <option value="MESSAGING_RESPONDER">Messaging responder</option>
              </select>
            )}

            <p className="mt-3 text-[11px] leading-5 text-slate-400">
              Automatic mode only executes reversible campaign pause/enable actions. Budget and
              creative actions remain advisory or approval-based.
            </p>
            <button className="primary-button mt-3" type="submit">
              Create policy
            </button>
          </form>
        )}

        <div className="rounded-2xl border border-slate-100 p-4">
          <h3 className="text-sm font-bold text-slate-950">Policy status</h3>
          <div className="mt-3 space-y-3">
            {policies.length === 0 ? (
              <p className="text-xs text-slate-400">No optimization policies yet.</p>
            ) : (
              policies.slice(0, 12).map((policy) => (
                <div key={policy.id} className="rounded-xl bg-slate-50 p-3">
                  <div className="flex flex-wrap items-center justify-between gap-2">
                    <div>
                      <p className="text-xs font-bold text-slate-900">{policy.name}</p>
                      <p className="mt-1 text-[11px] text-slate-500">
                        {pretty(policy.policyType)} · {pretty(policy.mode)} ·{' '}
                        {pretty(policy.status)}
                      </p>
                    </div>
                    {canModify && (
                      <div className="flex gap-2">
                        <button
                          type="button"
                          className="secondary-button"
                          onClick={() => void evaluatePolicy(policy)}
                        >
                          Evaluate
                        </button>
                        {policy.status === 'PENDING_APPROVAL' && (
                          <button
                            type="button"
                            className="primary-button"
                            onClick={() => setPolicyToApprove(policy)}
                          >
                            Approve
                          </button>
                        )}
                        <button
                          type="button"
                          className="secondary-button"
                          onClick={() => void togglePolicy(policy)}
                        >
                          {policy.enabled ? 'Disable' : 'Enable'}
                        </button>
                      </div>
                    )}
                  </div>
                </div>
              ))
            )}
          </div>
        </div>

        {canCreate && (
          <form
            className="rounded-2xl border border-slate-100 p-4 xl:col-span-2"
            onSubmit={buildAudience}
          >
            <div className="flex items-center gap-2">
              <FiUsers className="text-indigo-500" aria-hidden="true" />
              <h3 className="text-sm font-bold text-slate-950">First-party audience builder</h3>
            </div>
            <p className="mt-1 text-xs leading-5 text-slate-500">
              Submit consented identifiers directly to Google Customer Match and/or Meta Custom
              Audiences. Raw member rows are not persisted by this workspace.
            </p>
            <div className="mt-3 grid gap-3 lg:grid-cols-2">
              <input
                className="form-input"
                value={audienceName}
                onChange={(event) => setAudienceName(event.target.value)}
                placeholder="Audience name"
                required
              />
              <div className="rounded-xl border border-slate-200 p-3">
                <p className="text-xs font-semibold text-slate-700">Provider targets</p>
                <div className="mt-2 flex flex-wrap gap-3">
                  {managedAccounts.map((account) => (
                    <label
                      key={account.id}
                      className="inline-flex items-center gap-2 text-xs text-slate-600"
                    >
                      <input
                        type="checkbox"
                        checked={targetAccountIds.includes(account.id)}
                        onChange={(event) =>
                          setTargetAccountIds((current) =>
                            event.target.checked
                              ? [...new Set([...current, account.id])]
                              : current.filter((id) => id !== account.id),
                          )
                        }
                      />
                      {account.name} ({pretty(account.provider)})
                    </label>
                  ))}
                </div>
              </div>
              <textarea
                className="form-input min-h-32 lg:col-span-2"
                value={audienceRows}
                onChange={(event) => setAudienceRows(event.target.value)}
                placeholder={
                  'One member per line: email,phone,externalId\nuser@example.com,+919876543210,crm-123'
                }
                required
              />
            </div>
            <div className="mt-3 grid gap-2 sm:grid-cols-2">
              <label className="inline-flex items-start gap-2 text-xs text-slate-600">
                <input
                  type="checkbox"
                  checked={googleTerms}
                  onChange={(event) => setGoogleTerms(event.target.checked)}
                />
                Google Customer Match terms accepted
              </label>
              <label className="inline-flex items-start gap-2 text-xs text-slate-600">
                <input
                  type="checkbox"
                  checked={metaTerms}
                  onChange={(event) => setMetaTerms(event.target.checked)}
                />
                Meta Custom Audience terms / first-party authorization confirmed
              </label>
              <label className="inline-flex items-start gap-2 text-xs text-slate-600">
                <input
                  type="checkbox"
                  checked={adUserDataConsent}
                  onChange={(event) => setAdUserDataConsent(event.target.checked)}
                />
                Google ad user data consent granted for these members
              </label>
              <label className="inline-flex items-start gap-2 text-xs text-slate-600">
                <input
                  type="checkbox"
                  checked={adPersonalizationConsent}
                  onChange={(event) => setAdPersonalizationConsent(event.target.checked)}
                />
                Google ad personalization consent granted for these members
              </label>
            </div>
            <button className="primary-button mt-3" type="submit">
              Build provider audiences
            </button>
            {audienceResult && (
              <div className="mt-4 grid gap-3 sm:grid-cols-2">
                {audienceResult.targets.map((target) => (
                  <div
                    key={`${target.provider}-${target.adAccountId}`}
                    className="rounded-xl bg-slate-50 p-3"
                  >
                    <p className="text-xs font-bold text-slate-900">{pretty(target.provider)}</p>
                    <p className="mt-1 text-[11px] text-slate-500">
                      {target.success
                        ? `Accepted ${target.providerAccepted}; rejected ${target.providerRejected}`
                        : target.error || 'Provider audience submission failed'}
                    </p>
                  </div>
                ))}
              </div>
            )}
          </form>
        )}
      </div>

      <div className="border-t border-slate-100 bg-slate-50 px-5 py-3 text-[11px] leading-5 text-slate-500">
        <FiActivity className="mr-1 inline" aria-hidden="true" />
        Optimization evidence never guarantees future results. Provider eligibility, attribution,
        privacy terms, and platform delivery remain authoritative.
      </div>
      <ConfirmDialog
        open={Boolean(policyToApprove)}
        title="Approve this optimization action?"
        description="This allows UnifyDesk to apply the reviewed optimization action. Review the policy result before continuing."
        confirmLabel="Yes, approve"
        cancelLabel="No, cancel"
        tone="warning"
        working={false}
        onCancel={() => setPolicyToApprove(null)}
        onConfirm={() => policyToApprove && void approvePolicy(policyToApprove)}
      />
    </section>
  )
}
