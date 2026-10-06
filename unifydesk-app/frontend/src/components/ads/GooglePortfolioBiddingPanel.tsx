import { useEffect, useMemo, useState } from 'react'
import { FiLink, FiPlus, FiRefreshCw, FiSave } from 'react-icons/fi'
import { adCenterApi } from '../../features/ads/adCenterApi'
import type {
  GoogleCampaignPortfolioAssignment,
  GooglePortfolioBiddingStrategy,
  GoogleSharedBudget,
} from '../../features/ads/types'
import { apiErrorMessage } from '../../lib/api/apiClient'
import { CardSkeleton } from '../ui/Skeleton'

type Props = {
  companyId: string
  accountId: string
  campaignId: string
  currency?: string | null
  canCreate: boolean
  canModify: boolean
}

type PortfolioStrategyType =
  'TARGET_CPA' | 'TARGET_ROAS' | 'MAXIMIZE_CONVERSIONS' | 'MAXIMIZE_CONVERSION_VALUE'

const STRATEGY_TYPES: Array<{ value: PortfolioStrategyType; label: string }> = [
  { value: 'TARGET_CPA', label: 'Target CPA' },
  { value: 'TARGET_ROAS', label: 'Target ROAS' },
  { value: 'MAXIMIZE_CONVERSIONS', label: 'Maximize conversions' },
  { value: 'MAXIMIZE_CONVERSION_VALUE', label: 'Maximize conversion value' },
]

const resourceId = (resourceName?: string | null) => {
  if (!resourceName) return ''
  const parts = resourceName.split('/')
  return parts.at(-1) ?? ''
}

const currencyDigits = (currency?: string | null) => {
  if (!currency) return 2
  try {
    return (
      new Intl.NumberFormat(undefined, { style: 'currency', currency }).resolvedOptions()
        .maximumFractionDigits ?? 2
    )
  } catch {
    return 2
  }
}

const majorToMinor = (value: string, currency?: string | null) => {
  const amount = Number(value)
  if (!Number.isFinite(amount) || amount <= 0) return null
  return Math.round(amount * 10 ** currencyDigits(currency))
}

const formatMicros = (value: number | null | undefined, currency?: string | null) => {
  if (value == null) return '—'
  const major = value / 1_000_000
  if (!currency) return major.toLocaleString()
  try {
    return new Intl.NumberFormat(undefined, {
      style: 'currency',
      currency,
      maximumFractionDigits: currencyDigits(currency),
    }).format(major)
  } catch {
    return `${major.toLocaleString()} ${currency}`
  }
}

const formatNumber = (value: number | null | undefined, digits = 0) =>
  value == null
    ? '—'
    : new Intl.NumberFormat(undefined, { maximumFractionDigits: digits }).format(value)

const pretty = (value?: string | null) =>
  value
    ? value
        .replaceAll('_', ' ')
        .toLowerCase()
        .replace(/\b\w/g, (character) => character.toUpperCase())
    : '—'

export default function GooglePortfolioBiddingPanel({
  companyId,
  accountId,
  campaignId,
  currency,
  canCreate,
  canModify,
}: Props) {
  const [strategies, setStrategies] = useState<GooglePortfolioBiddingStrategy[]>([])
  const [budgets, setBudgets] = useState<GoogleSharedBudget[]>([])
  const [assignment, setAssignment] = useState<GoogleCampaignPortfolioAssignment | null>(null)

  const [strategyName, setStrategyName] = useState('')
  const [strategyType, setStrategyType] = useState<PortfolioStrategyType>('MAXIMIZE_CONVERSIONS')
  const [targetCpa, setTargetCpa] = useState('')
  const [targetRoas, setTargetRoas] = useState('')
  const [budgetName, setBudgetName] = useState('')
  const [budgetAmount, setBudgetAmount] = useState('')

  const [selectedStrategyId, setSelectedStrategyId] = useState('')
  const [selectedBudgetId, setSelectedBudgetId] = useState('')
  const [loading, setLoading] = useState(true)
  const [working, setWorking] = useState('')
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')

  const selectedStrategy = useMemo(
    () => strategies.find((strategy) => strategy.id === selectedStrategyId) ?? null,
    [strategies, selectedStrategyId],
  )
  const selectedBudget = useMemo(
    () => budgets.find((budget) => budget.id === selectedBudgetId) ?? null,
    [budgets, selectedBudgetId],
  )

  const hydrateAssignment = (value: GoogleCampaignPortfolioAssignment) => {
    setAssignment(value)
    setSelectedStrategyId(resourceId(value.biddingStrategyResourceName))
    setSelectedBudgetId(resourceId(value.campaignBudgetResourceName))
  }

  const load = async () => {
    setLoading(true)
    setError('')
    try {
      const [strategyData, budgetData, assignmentData] = await Promise.all([
        adCenterApi.googlePortfolioBiddingStrategies(companyId, accountId),
        adCenterApi.googleSharedBudgets(companyId, accountId),
        adCenterApi.googleCampaignPortfolioAssignment(companyId, campaignId),
      ])
      setStrategies(strategyData)
      setBudgets(budgetData)
      hydrateAssignment(assignmentData)
    } catch (loadError) {
      setError(apiErrorMessage(loadError, 'Unable to load Google portfolio bidding'))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    void load()
  }, [companyId, accountId, campaignId])

  const createStrategy = async () => {
    if (!strategyName.trim()) {
      setError('Enter a portfolio strategy name.')
      return
    }

    const targetCpaMinor = targetCpa.trim() ? majorToMinor(targetCpa, currency) : null
    const targetRoasValue = targetRoas.trim() ? Number(targetRoas) : null

    if (
      (strategyType === 'TARGET_CPA' ||
        (strategyType === 'MAXIMIZE_CONVERSIONS' && targetCpa.trim())) &&
      targetCpaMinor == null
    ) {
      setError('Enter a positive target CPA.')
      return
    }
    if (
      (strategyType === 'TARGET_ROAS' ||
        (strategyType === 'MAXIMIZE_CONVERSION_VALUE' && targetRoas.trim())) &&
      (targetRoasValue == null || !Number.isFinite(targetRoasValue) || targetRoasValue <= 0)
    ) {
      setError('Enter a positive target ROAS.')
      return
    }

    setWorking('strategy')
    setError('')
    setNotice('')
    try {
      const created = await adCenterApi.createGooglePortfolioBiddingStrategy(companyId, accountId, {
        name: strategyName.trim(),
        type: strategyType,
        targetCpaMinor:
          strategyType === 'TARGET_CPA' || strategyType === 'MAXIMIZE_CONVERSIONS'
            ? targetCpaMinor
            : null,
        targetRoas:
          strategyType === 'TARGET_ROAS' || strategyType === 'MAXIMIZE_CONVERSION_VALUE'
            ? targetRoasValue
            : null,
      })
      setStrategies((current) => [
        created,
        ...current.filter((strategy) => strategy.id !== created.id),
      ])
      setSelectedStrategyId(created.id)
      setStrategyName('')
      setTargetCpa('')
      setTargetRoas('')
      setNotice('Portfolio bidding strategy created in Google Ads.')
    } catch (createError) {
      setError(apiErrorMessage(createError, 'Unable to create portfolio bidding strategy'))
    } finally {
      setWorking('')
    }
  }

  const createBudget = async () => {
    if (!budgetName.trim()) {
      setError('Enter a shared budget name.')
      return
    }
    const amountMinor = majorToMinor(budgetAmount, currency)
    if (amountMinor == null) {
      setError('Enter a positive daily shared budget.')
      return
    }

    setWorking('budget')
    setError('')
    setNotice('')
    try {
      const created = await adCenterApi.createGoogleSharedBudget(companyId, accountId, {
        name: budgetName.trim(),
        amountMinor,
      })
      setBudgets((current) => [created, ...current.filter((budget) => budget.id !== created.id)])
      setSelectedBudgetId(created.id)
      setBudgetName('')
      setBudgetAmount('')
      setNotice('Shared campaign budget created in Google Ads.')
    } catch (createError) {
      setError(apiErrorMessage(createError, 'Unable to create shared budget'))
    } finally {
      setWorking('')
    }
  }

  const saveAssignment = async () => {
    if (!selectedStrategyId && !selectedBudgetId) {
      setError('Choose a portfolio strategy, a shared budget, or both.')
      return
    }

    setWorking('assignment')
    setError('')
    setNotice('')
    try {
      const updated = await adCenterApi.updateGoogleCampaignPortfolioAssignment(
        companyId,
        campaignId,
        selectedStrategyId || null,
        selectedBudgetId || null,
      )
      hydrateAssignment(updated)
      setNotice('Campaign portfolio assignment updated in Google Ads.')
    } catch (saveError) {
      setError(apiErrorMessage(saveError, 'Unable to update campaign portfolio assignment'))
    } finally {
      setWorking('')
    }
  }

  const alignPair = async () => {
    if (!selectedStrategyId || !selectedBudgetId) {
      setError('Choose both a portfolio strategy and a shared budget before alignment.')
      return
    }

    setWorking('align')
    setError('')
    setNotice('')
    try {
      await adCenterApi.alignGooglePortfolioBudget(
        companyId,
        accountId,
        selectedStrategyId,
        selectedBudgetId,
      )
      await load()
      setNotice('Google portfolio strategy and shared budget are aligned.')
    } catch (alignError) {
      setError(apiErrorMessage(alignError, 'Unable to align portfolio strategy and shared budget'))
    } finally {
      setWorking('')
    }
  }

  return (
    <section className="panel mt-5 overflow-hidden">
      <div className="flex flex-wrap items-start justify-between gap-3 border-b border-slate-100 px-5 py-4">
        <div>
          <h2 className="font-bold text-slate-950">Portfolio bidding & shared budgets</h2>
          <p className="mt-1 max-w-3xl text-xs leading-5 text-slate-500">
            Reuse one Google bidding strategy or budget across compatible Search, Display, and
            Shopping campaigns. Google requires aligned pairs to be attached to the same campaign
            set before they can be linked.
          </p>
        </div>
        <button
          type="button"
          className="secondary-button inline-flex items-center gap-2"
          disabled={loading || Boolean(working)}
          onClick={() => void load()}
        >
          <FiRefreshCw aria-hidden="true" />
          Refresh
        </button>
      </div>

      <div className="space-y-6 p-5">
        {(error || notice) && (
          <div
            className={`rounded-xl border px-4 py-3 text-sm font-medium ${
              error
                ? 'border-red-200 bg-red-50 text-red-700'
                : 'border-emerald-200 bg-emerald-50 text-emerald-700'
            }`}
          >
            {error || notice}
          </div>
        )}

        {loading ? (
          <div className="grid gap-4 lg:grid-cols-2"><CardSkeleton rows={5} /><CardSkeleton rows={5} /></div>
        ) : (
          <>
            <div className="grid gap-5 lg:grid-cols-2">
              <div className="rounded-2xl border border-slate-200 p-4">
                <h3 className="text-sm font-bold text-slate-950">Create portfolio strategy</h3>
                <p className="mt-1 text-xs leading-5 text-slate-500">
                  Create an account-level strategy that can be assigned to multiple compatible
                  campaigns.
                </p>
                <div className="mt-4 grid gap-3 sm:grid-cols-2">
                  <label className="text-xs font-semibold text-slate-700 sm:col-span-2">
                    Name
                    <input
                      className="form-input mt-1"
                      value={strategyName}
                      disabled={!canCreate || Boolean(working)}
                      maxLength={255}
                      onChange={(event) => setStrategyName(event.target.value)}
                      placeholder="Lead generation portfolio"
                    />
                  </label>
                  <label className="text-xs font-semibold text-slate-700 sm:col-span-2">
                    Strategy
                    <select
                      className="form-input mt-1"
                      value={strategyType}
                      disabled={!canCreate || Boolean(working)}
                      onChange={(event) =>
                        setStrategyType(event.target.value as PortfolioStrategyType)
                      }
                    >
                      {STRATEGY_TYPES.map((option) => (
                        <option key={option.value} value={option.value}>
                          {option.label}
                        </option>
                      ))}
                    </select>
                  </label>
                  {(strategyType === 'TARGET_CPA' || strategyType === 'MAXIMIZE_CONVERSIONS') && (
                    <label className="text-xs font-semibold text-slate-700">
                      Target CPA {strategyType === 'MAXIMIZE_CONVERSIONS' ? '(optional)' : ''}
                      <input
                        className="form-input mt-1"
                        type="number"
                        min="0.01"
                        step="0.01"
                        value={targetCpa}
                        disabled={!canCreate || Boolean(working)}
                        onChange={(event) => setTargetCpa(event.target.value)}
                        placeholder={currency ?? 'Amount'}
                      />
                    </label>
                  )}
                  {(strategyType === 'TARGET_ROAS' ||
                    strategyType === 'MAXIMIZE_CONVERSION_VALUE') && (
                    <label className="text-xs font-semibold text-slate-700">
                      Target ROAS {strategyType === 'MAXIMIZE_CONVERSION_VALUE' ? '(optional)' : ''}
                      <input
                        className="form-input mt-1"
                        type="number"
                        min="0.01"
                        step="0.01"
                        value={targetRoas}
                        disabled={!canCreate || Boolean(working)}
                        onChange={(event) => setTargetRoas(event.target.value)}
                        placeholder="e.g. 4.0"
                      />
                    </label>
                  )}
                </div>
                <button
                  type="button"
                  className="primary-button mt-4 inline-flex items-center gap-2"
                  disabled={!canCreate || Boolean(working)}
                  onClick={() => void createStrategy()}
                >
                  <FiPlus aria-hidden="true" />
                  {working === 'strategy' ? 'Creating…' : 'Create strategy'}
                </button>
              </div>

              <div className="rounded-2xl border border-slate-200 p-4">
                <h3 className="text-sm font-bold text-slate-950">Create shared budget</h3>
                <p className="mt-1 text-xs leading-5 text-slate-500">
                  Share one daily Google campaign budget across several compatible campaigns.
                </p>
                <div className="mt-4 grid gap-3 sm:grid-cols-2">
                  <label className="text-xs font-semibold text-slate-700 sm:col-span-2">
                    Name
                    <input
                      className="form-input mt-1"
                      value={budgetName}
                      disabled={!canCreate || Boolean(working)}
                      maxLength={255}
                      onChange={(event) => setBudgetName(event.target.value)}
                      placeholder="Shared acquisition budget"
                    />
                  </label>
                  <label className="text-xs font-semibold text-slate-700">
                    Daily amount
                    <input
                      className="form-input mt-1"
                      type="number"
                      min="0.01"
                      step="0.01"
                      value={budgetAmount}
                      disabled={!canCreate || Boolean(working)}
                      onChange={(event) => setBudgetAmount(event.target.value)}
                      placeholder={currency ?? 'Amount'}
                    />
                  </label>
                </div>
                <button
                  type="button"
                  className="primary-button mt-4 inline-flex items-center gap-2"
                  disabled={!canCreate || Boolean(working)}
                  onClick={() => void createBudget()}
                >
                  <FiPlus aria-hidden="true" />
                  {working === 'budget' ? 'Creating…' : 'Create shared budget'}
                </button>
              </div>
            </div>

            <div className="rounded-2xl border border-slate-200 p-4">
              <div className="flex flex-wrap items-start justify-between gap-3">
                <div>
                  <h3 className="text-sm font-bold text-slate-950">Campaign assignment</h3>
                  <p className="mt-1 text-xs leading-5 text-slate-500">
                    Select the account-level strategy and/or shared budget for this campaign.
                  </p>
                </div>
                <span
                  className={`rounded-full px-2.5 py-1 text-[11px] font-semibold ${
                    assignment?.portfolioCompatible
                      ? 'bg-emerald-50 text-emerald-700'
                      : 'bg-amber-50 text-amber-700'
                  }`}
                >
                  {assignment?.portfolioCompatible ? 'Compatible' : 'Provider restricted'}
                </span>
              </div>

              <div className="mt-4 grid gap-4 lg:grid-cols-2">
                <label className="text-xs font-semibold text-slate-700">
                  Portfolio bidding strategy
                  <select
                    className="form-input mt-1"
                    value={selectedStrategyId}
                    disabled={!canModify || Boolean(working)}
                    onChange={(event) => setSelectedStrategyId(event.target.value)}
                  >
                    <option value="">No new strategy selection</option>
                    {strategies.map((strategy) => (
                      <option key={strategy.id} value={strategy.id}>
                        {strategy.name} · {pretty(strategy.type)}
                      </option>
                    ))}
                  </select>
                </label>

                <label className="text-xs font-semibold text-slate-700">
                  Shared budget
                  <select
                    className="form-input mt-1"
                    value={selectedBudgetId}
                    disabled={!canModify || Boolean(working)}
                    onChange={(event) => setSelectedBudgetId(event.target.value)}
                  >
                    <option value="">No new budget selection</option>
                    {budgets.map((budget) => (
                      <option key={budget.id} value={budget.id}>
                        {budget.name} · {formatMicros(budget.amountMicros, currency)}
                      </option>
                    ))}
                  </select>
                </label>
              </div>

              {(selectedStrategy?.alignedCampaignBudgetId ||
                selectedBudget?.alignedBiddingStrategyId) && (
                <div className="mt-3 rounded-xl border border-indigo-100 bg-indigo-50 px-3 py-2 text-xs leading-5 text-indigo-800">
                  Google reports an existing alignment for this selection. Keep the aligned strategy
                  and budget together when assigning them to campaigns.
                </div>
              )}

              <div className="mt-4 flex flex-wrap gap-2">
                <button
                  type="button"
                  className="primary-button inline-flex items-center gap-2"
                  disabled={
                    !canModify || Boolean(working) || (!selectedStrategyId && !selectedBudgetId)
                  }
                  onClick={() => void saveAssignment()}
                >
                  <FiSave aria-hidden="true" />
                  {working === 'assignment' ? 'Saving…' : 'Save assignment'}
                </button>
                <button
                  type="button"
                  className="secondary-button inline-flex items-center gap-2"
                  disabled={
                    !canModify || Boolean(working) || !selectedStrategyId || !selectedBudgetId
                  }
                  onClick={() => void alignPair()}
                >
                  <FiLink aria-hidden="true" />
                  {working === 'align' ? 'Aligning…' : 'Align selected pair'}
                </button>
              </div>
            </div>

            <div className="grid gap-5 xl:grid-cols-2">
              <div className="overflow-hidden rounded-2xl border border-slate-200">
                <div className="border-b border-slate-100 px-4 py-3">
                  <h3 className="text-sm font-bold text-slate-950">
                    Portfolio strategies ({strategies.length})
                  </h3>
                </div>
                {strategies.length === 0 ? (
                  <p className="px-4 py-6 text-sm text-slate-500">
                    No portfolio bidding strategies found.
                  </p>
                ) : (
                  <div className="overflow-x-auto">
                    <table className="min-w-full text-left text-xs">
                      <thead className="bg-slate-50 text-slate-500">
                        <tr>
                          <th className="px-4 py-2 font-semibold">Strategy</th>
                          <th className="px-4 py-2 font-semibold">Campaigns</th>
                          <th className="px-4 py-2 font-semibold">30d performance</th>
                        </tr>
                      </thead>
                      <tbody className="divide-y divide-slate-100">
                        {strategies.map((strategy) => (
                          <tr key={strategy.id}>
                            <td className="px-4 py-3 align-top">
                              <p className="font-semibold text-slate-900">{strategy.name}</p>
                              <p className="mt-1 text-slate-500">{pretty(strategy.type)}</p>
                              {strategy.targetCpaMicros != null && (
                                <p className="mt-1 text-slate-500">
                                  CPA {formatMicros(strategy.targetCpaMicros, currency)}
                                </p>
                              )}
                              {strategy.targetRoas != null && (
                                <p className="mt-1 text-slate-500">
                                  ROAS {formatNumber(strategy.targetRoas, 2)}
                                </p>
                              )}
                            </td>
                            <td className="px-4 py-3 align-top text-slate-700">
                              {formatNumber(strategy.nonRemovedCampaignCount)}
                            </td>
                            <td className="px-4 py-3 align-top text-slate-600">
                              <p>{formatNumber(strategy.clicks)} clicks</p>
                              <p>{formatNumber(strategy.conversions, 2)} conversions</p>
                              <p>{formatMicros(strategy.costMicros, currency)} spend</p>
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                )}
              </div>

              <div className="overflow-hidden rounded-2xl border border-slate-200">
                <div className="border-b border-slate-100 px-4 py-3">
                  <h3 className="text-sm font-bold text-slate-950">
                    Shared budgets ({budgets.length})
                  </h3>
                </div>
                {budgets.length === 0 ? (
                  <p className="px-4 py-6 text-sm text-slate-500">No shared budgets found.</p>
                ) : (
                  <div className="overflow-x-auto">
                    <table className="min-w-full text-left text-xs">
                      <thead className="bg-slate-50 text-slate-500">
                        <tr>
                          <th className="px-4 py-2 font-semibold">Budget</th>
                          <th className="px-4 py-2 font-semibold">Campaigns</th>
                          <th className="px-4 py-2 font-semibold">30d performance</th>
                        </tr>
                      </thead>
                      <tbody className="divide-y divide-slate-100">
                        {budgets.map((budget) => (
                          <tr key={budget.id}>
                            <td className="px-4 py-3 align-top">
                              <p className="font-semibold text-slate-900">{budget.name}</p>
                              <p className="mt-1 text-slate-500">
                                {formatMicros(budget.amountMicros, currency)} / day
                              </p>
                            </td>
                            <td className="px-4 py-3 align-top text-slate-700">
                              {formatNumber(budget.referenceCount)}
                            </td>
                            <td className="px-4 py-3 align-top text-slate-600">
                              <p>{formatNumber(budget.clicks)} clicks</p>
                              <p>{formatNumber(budget.conversions, 2)} conversions</p>
                              <p>{formatMicros(budget.costMicros, currency)} spend</p>
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                )}
              </div>
            </div>
          </>
        )}
      </div>
    </section>
  )
}
