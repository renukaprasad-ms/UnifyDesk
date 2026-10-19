import { useSnackbarState } from '../feedback/useSnackbarState'
import { useEffect, useMemo, useState } from 'react'
import { FiRefreshCw, FiSave, FiTrash2 } from 'react-icons/fi'
import { adCenterApi } from '../../features/ads/adCenterApi'
import type {
  AdAudience,
  GoogleLifecycleGoal,
  GoogleLifecycleGoalType,
  GooglePerformanceMaxLifecycle,
  GoogleUserListCustomerType,
} from '../../features/ads/types'
import { apiErrorMessage } from '../../lib/api/apiClient'
import { CardSkeleton } from '../ui/Skeleton'

type Props = {
  companyId: string
  accountId: string
  campaignId: string
  canModify: boolean
}

const GOAL_TYPES: Array<{ value: GoogleLifecycleGoalType; label: string; help: string }> = [
  {
    value: 'NEW_CUSTOMER_ACQUISITION',
    label: 'New customer acquisition',
    help: 'Bid higher for new customers or restrict the campaign to new customers only.',
  },
  {
    value: 'CUSTOMER_RETENTION',
    label: 'Customer retention',
    help: 'Re-engage existing, lapsed, or high-value customers.',
  },
  {
    value: 'LOYALTY_RETENTION',
    label: 'Loyalty retention',
    help: 'Adjust value and eligible PLA benefits for loyalty-program members.',
  },
]

const CUSTOMER_TYPES = [
  'ALL_CUSTOMERS',
  'PURCHASERS',
  'CONVERTED_LEADS',
  'QUALIFIED_LEADS',
  'CART_ABANDONERS',
  'DISENGAGED_CUSTOMERS',
  'HIGH_VALUE_CUSTOMERS',
  'PAID_SUBSCRIBERS',
  'LOYALTY_TIER_1_MEMBERS',
  'LOYALTY_TIER_2_MEMBERS',
  'LOYALTY_TIER_3_MEMBERS',
  'LOYALTY_TIER_4_MEMBERS',
  'LOYALTY_TIER_5_MEMBERS',
  'LOYALTY_TIER_6_MEMBERS',
  'LOYALTY_TIER_7_MEMBERS',
] as const

const pretty = (value: string) =>
  value
    .replaceAll('_', ' ')
    .toLowerCase()
    .replace(/\b\w/g, (character) => character.toUpperCase())

const optionalNumber = (value: string) => {
  if (!value.trim()) return null
  const parsed = Number(value)
  return Number.isFinite(parsed) ? parsed : null
}

export default function GooglePerformanceMaxLifecyclePanel({
  companyId,
  accountId,
  campaignId,
  canModify,
}: Props) {
  const [goals, setGoals] = useState<GoogleLifecycleGoal[]>([])
  const [configs, setConfigs] = useState<GooglePerformanceMaxLifecycle[]>([])
  const [customerTypes, setCustomerTypes] = useState<GoogleUserListCustomerType[]>([])
  const [audiences, setAudiences] = useState<AdAudience[]>([])
  const [selectedGoalType, setSelectedGoalType] = useState<GoogleLifecycleGoalType>(
    'NEW_CUSTOMER_ACQUISITION',
  )

  const [accountValue, setAccountValue] = useState('')
  const [accountHighValue, setAccountHighValue] = useState('')
  const [accountMultiplier, setAccountMultiplier] = useState('')

  const [selectedAudienceId, setSelectedAudienceId] = useState('')
  const [selectedCustomerTypes, setSelectedCustomerTypes] = useState<string[]>([])

  const [targetOption, setTargetOption] = useState<'TARGET_ALL' | 'TARGET_SPECIFIC'>('TARGET_ALL')
  const [campaignValue, setCampaignValue] = useState('')
  const [campaignHighValue, setCampaignHighValue] = useState('')
  const [campaignMultiplier, setCampaignMultiplier] = useState('')
  const [loyaltyBidAdjustments, setLoyaltyBidAdjustments] = useState(false)
  const [loyaltyPlaBenefits, setLoyaltyPlaBenefits] = useState(false)

  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState('')
  const [, setError] = useSnackbarState('', 'error')
  const [, setNotice] = useSnackbarState('', 'success')

  const googleAudiences = useMemo(
    () => audiences.filter((audience) => audience.accessible),
    [audiences],
  )

  const currentGoal = useMemo(
    () => goals.find((goal) => goal.goalType === selectedGoalType) ?? null,
    [goals, selectedGoalType],
  )

  const currentConfig = useMemo(
    () => configs.find((config) => config.goalType === selectedGoalType) ?? null,
    [configs, selectedGoalType],
  )

  const load = async () => {
    setLoading(true)
    setError('')
    try {
      const [goalData, configData, typeData, audienceData] = await Promise.all([
        adCenterApi.googleLifecycleGoals(companyId, accountId),
        adCenterApi.googlePerformanceMaxLifecycle(companyId, campaignId),
        adCenterApi.googleUserListCustomerTypes(companyId, accountId),
        adCenterApi.audiences(companyId, accountId),
      ])
      setGoals(goalData)
      setConfigs(configData)
      setCustomerTypes(typeData)
      setAudiences(audienceData)
    } catch (loadError) {
      setError(apiErrorMessage(loadError, 'Unable to load Performance Max lifecycle goals'))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    void load()
  }, [companyId, accountId, campaignId])

  useEffect(() => {
    setAccountValue(currentGoal?.additionalValue?.toString() ?? '')
    setAccountHighValue(currentGoal?.additionalHighLifetimeValue?.toString() ?? '')
    setAccountMultiplier(currentGoal?.valueMultiplier?.toString() ?? '')

    setTargetOption(currentConfig?.targetOption ?? 'TARGET_ALL')
    setCampaignValue(currentConfig?.additionalValue?.toString() ?? '')
    setCampaignHighValue(currentConfig?.additionalHighLifetimeValue?.toString() ?? '')
    setCampaignMultiplier(currentConfig?.valueMultiplier?.toString() ?? '')
    setLoyaltyBidAdjustments(currentConfig?.enableBidAdjustmentsForLoyaltyMembers ?? false)
    setLoyaltyPlaBenefits(currentConfig?.showTargetedLoyaltyMemberBenefitsInPla ?? false)
  }, [currentGoal, currentConfig, selectedGoalType])

  useEffect(() => {
    if (!selectedAudienceId) {
      setSelectedCustomerTypes([])
      return
    }
    const audience = audiences.find((item) => item.id === selectedAudienceId)
    if (!audience) return
    setSelectedCustomerTypes(
      customerTypes
        .filter((item) => item.externalUserListId === audience.externalAudienceId)
        .map((item) => item.customerTypeCategory),
    )
  }, [selectedAudienceId, audiences, customerTypes])

  const saveAccountGoal = async () => {
    setSaving('goal')
    setError('')
    setNotice('')
    try {
      const saved = await adCenterApi.upsertGoogleLifecycleGoal(companyId, accountId, {
        goalType: selectedGoalType,
        additionalValue:
          selectedGoalType === 'LOYALTY_RETENTION' ? null : optionalNumber(accountValue),
        additionalHighLifetimeValue:
          selectedGoalType === 'LOYALTY_RETENTION' ? null : optionalNumber(accountHighValue),
        valueMultiplier:
          selectedGoalType === 'LOYALTY_RETENTION' ? optionalNumber(accountMultiplier) : null,
      })
      setGoals((current) => [...current.filter((goal) => goal.goalType !== saved.goalType), saved])
      setNotice('Google account lifecycle goal saved.')
    } catch (saveError) {
      setError(apiErrorMessage(saveError, 'Unable to save Google lifecycle goal'))
    } finally {
      setSaving('')
    }
  }

  const saveCustomerTypes = async () => {
    if (!selectedAudienceId) return
    setSaving('audience')
    setError('')
    setNotice('')
    try {
      await adCenterApi.updateGoogleUserListCustomerTypes(companyId, accountId, {
        audienceId: selectedAudienceId,
        customerTypeCategories: selectedCustomerTypes,
      })
      const refreshed = await adCenterApi.googleUserListCustomerTypes(companyId, accountId)
      setCustomerTypes(refreshed)
      setNotice('Google customer-list classification updated.')
    } catch (saveError) {
      setError(apiErrorMessage(saveError, 'Unable to classify Google customer list'))
    } finally {
      setSaving('')
    }
  }

  const saveCampaignConfig = async () => {
    setSaving('campaign')
    setError('')
    setNotice('')
    try {
      const saved = await adCenterApi.updateGooglePerformanceMaxLifecycle(companyId, campaignId, {
        goalType: selectedGoalType,
        targetOption: selectedGoalType === 'LOYALTY_RETENTION' ? null : targetOption,
        additionalValue:
          selectedGoalType === 'LOYALTY_RETENTION' ? null : optionalNumber(campaignValue),
        additionalHighLifetimeValue:
          selectedGoalType === 'LOYALTY_RETENTION' ? null : optionalNumber(campaignHighValue),
        valueMultiplier:
          selectedGoalType === 'LOYALTY_RETENTION' ? optionalNumber(campaignMultiplier) : null,
        enableBidAdjustmentsForLoyaltyMembers:
          selectedGoalType === 'LOYALTY_RETENTION' ? loyaltyBidAdjustments : null,
        showTargetedLoyaltyMemberBenefitsInPla:
          selectedGoalType === 'LOYALTY_RETENTION' ? loyaltyPlaBenefits : null,
      })
      setConfigs((current) => [
        ...current.filter((config) => config.goalType !== saved.goalType),
        saved,
      ])
      setNotice('Performance Max lifecycle optimization updated.')
    } catch (saveError) {
      setError(apiErrorMessage(saveError, 'Unable to update campaign lifecycle goal'))
    } finally {
      setSaving('')
    }
  }

  const removeCampaignConfig = async () => {
    setSaving('remove')
    setError('')
    setNotice('')
    try {
      const updated = await adCenterApi.removeGooglePerformanceMaxLifecycle(
        companyId,
        campaignId,
        selectedGoalType,
      )
      setConfigs(updated)
      setNotice('Campaign-specific lifecycle optimization removed.')
    } catch (removeError) {
      setError(apiErrorMessage(removeError, 'Unable to remove campaign lifecycle goal'))
    } finally {
      setSaving('')
    }
  }

  const toggleCustomerType = (category: string, checked: boolean) => {
    setSelectedCustomerTypes((current) =>
      checked
        ? [...new Set([...current, category])]
        : current.filter((value) => value !== category),
    )
  }

  return (
    <section className="panel mt-5 overflow-hidden">
      <div className="flex flex-wrap items-start justify-between gap-3 border-b border-slate-100 px-5 py-4">
        <div>
          <h2 className="font-bold text-slate-950">Performance Max lifecycle goals</h2>
          <p className="mt-1 max-w-3xl text-xs leading-5 text-slate-500">
            Configure Google account lifecycle value, classify first-party customer lists, and then
            apply campaign-specific acquisition or retention optimization.
          </p>
        </div>
        <button
          type="button"
          className="secondary-button inline-flex items-center gap-2"
          disabled={loading || Boolean(saving)}
          onClick={() => void load()}
        >
          <FiRefreshCw aria-hidden="true" />
          Refresh
        </button>
      </div>

      <div className="p-5">


        {loading ? (
          <CardSkeleton rows={5} />
        ) : (
          <>
            <div className="flex flex-wrap gap-2">
              {GOAL_TYPES.map((goal) => (
                <button
                  key={goal.value}
                  type="button"
                  className={`rounded-xl border px-3 py-2 text-xs font-semibold ${
                    selectedGoalType === goal.value
                      ? 'border-indigo-300 bg-indigo-50 text-indigo-700'
                      : 'border-slate-200 bg-white text-slate-600'
                  }`}
                  onClick={() => setSelectedGoalType(goal.value)}
                >
                  {goal.label}
                </button>
              ))}
            </div>

            <p className="mt-3 text-xs leading-5 text-slate-500">
              {GOAL_TYPES.find((goal) => goal.value === selectedGoalType)?.help}
            </p>

            <div className="mt-5 rounded-2xl border border-slate-200 p-4">
              <div className="flex flex-wrap items-center justify-between gap-3">
                <div>
                  <p className="text-sm font-bold text-slate-900">1. Account lifecycle goal</p>
                  <p className="mt-1 text-xs text-slate-500">
                    Google permits one account goal per lifecycle type. This baseline can affect
                    campaigns that adopt the goal.
                  </p>
                </div>
                <span
                  className={`rounded-full px-2.5 py-1 text-[10px] font-semibold ${
                    currentGoal ? 'bg-emerald-100 text-emerald-700' : 'bg-amber-100 text-amber-700'
                  }`}
                >
                  {currentGoal ? 'Configured' : 'Not configured'}
                </span>
              </div>

              <div className="mt-4 grid gap-3 sm:grid-cols-3">
                {selectedGoalType !== 'LOYALTY_RETENTION' ? (
                  <>
                    <label className="block text-xs font-semibold text-slate-700">
                      Additional value
                      <input
                        className="form-input mt-2"
                        type="number"
                        min="0"
                        value={accountValue}
                        disabled={!canModify}
                        onChange={(event) => setAccountValue(event.target.value)}
                        placeholder="50"
                      />
                    </label>
                    <label className="block text-xs font-semibold text-slate-700">
                      High lifetime value
                      <input
                        className="form-input mt-2"
                        type="number"
                        min="0"
                        value={accountHighValue}
                        disabled={!canModify}
                        onChange={(event) => setAccountHighValue(event.target.value)}
                        placeholder="100"
                      />
                    </label>
                  </>
                ) : (
                  <label className="block text-xs font-semibold text-slate-700">
                    Loyalty value multiplier
                    <input
                      className="form-input mt-2"
                      type="number"
                      min="0.01"
                      step="0.1"
                      value={accountMultiplier}
                      disabled={!canModify}
                      onChange={(event) => setAccountMultiplier(event.target.value)}
                      placeholder="1.5"
                    />
                  </label>
                )}
              </div>

              {canModify && (
                <div className="mt-4 flex justify-end">
                  <button
                    type="button"
                    className="primary-button inline-flex items-center gap-2"
                    disabled={saving === 'goal'}
                    onClick={() => void saveAccountGoal()}
                  >
                    <FiSave aria-hidden="true" />
                    {saving === 'goal' ? 'Saving…' : 'Save account goal'}
                  </button>
                </div>
              )}
            </div>

            <div className="mt-5 rounded-2xl border border-slate-200 p-4">
              <p className="text-sm font-bold text-slate-900">2. Classify customer lists</p>
              <p className="mt-1 text-xs leading-5 text-slate-500">
                Google requires UserListCustomerType classifications for lifecycle goals. Assign
                only categories that truthfully describe the people in the selected list.
              </p>

              <label className="mt-4 block text-xs font-semibold text-slate-700">
                Google audience list
                <select
                  className="form-input mt-2"
                  value={selectedAudienceId}
                  disabled={!canModify}
                  onChange={(event) => setSelectedAudienceId(event.target.value)}
                >
                  <option value="">Choose an audience</option>
                  {googleAudiences.map((audience) => (
                    <option key={audience.id} value={audience.id}>
                      {audience.name}
                    </option>
                  ))}
                </select>
              </label>

              {selectedAudienceId && (
                <div className="mt-4 grid gap-2 sm:grid-cols-2 lg:grid-cols-3">
                  {CUSTOMER_TYPES.map((category) => (
                    <label
                      key={category}
                      className="flex items-center gap-2 rounded-xl border border-slate-200 px-3 py-2 text-xs text-slate-700"
                    >
                      <input
                        type="checkbox"
                        checked={selectedCustomerTypes.includes(category)}
                        disabled={!canModify}
                        onChange={(event) => toggleCustomerType(category, event.target.checked)}
                      />
                      {pretty(category)}
                    </label>
                  ))}
                </div>
              )}

              {canModify && selectedAudienceId && (
                <div className="mt-4 flex justify-end">
                  <button
                    type="button"
                    className="primary-button inline-flex items-center gap-2"
                    disabled={saving === 'audience'}
                    onClick={() => void saveCustomerTypes()}
                  >
                    <FiSave aria-hidden="true" />
                    {saving === 'audience' ? 'Saving…' : 'Save list classification'}
                  </button>
                </div>
              )}
            </div>

            <div className="mt-5 rounded-2xl border border-slate-200 p-4">
              <div className="flex flex-wrap items-center justify-between gap-3">
                <div>
                  <p className="text-sm font-bold text-slate-900">3. Campaign optimization</p>
                  <p className="mt-1 text-xs leading-5 text-slate-500">
                    Campaign-level settings override or specialize the account lifecycle goal.
                  </p>
                </div>
                <span
                  className={`rounded-full px-2.5 py-1 text-[10px] font-semibold ${
                    currentConfig
                      ? 'bg-emerald-100 text-emerald-700'
                      : 'bg-slate-100 text-slate-600'
                  }`}
                >
                  {currentConfig ? 'Enabled on campaign' : 'Not enabled'}
                </span>
              </div>

              {!currentGoal ? (
                <p className="mt-4 rounded-xl border border-amber-200 bg-amber-50 px-3 py-2 text-xs text-amber-800">
                  Configure the account lifecycle goal above before enabling it on this campaign.
                </p>
              ) : (
                <>
                  {selectedGoalType !== 'LOYALTY_RETENTION' ? (
                    <>
                      <label className="mt-4 block text-xs font-semibold text-slate-700">
                        Optimization mode
                        <select
                          className="form-input mt-2"
                          value={targetOption}
                          disabled={!canModify}
                          onChange={(event) =>
                            setTargetOption(event.target.value as 'TARGET_ALL' | 'TARGET_SPECIFIC')
                          }
                        >
                          <option value="TARGET_ALL">
                            Target all · bid/value higher for lifecycle customers
                          </option>
                          <option value="TARGET_SPECIFIC">
                            Target specific lifecycle customers only
                          </option>
                        </select>
                      </label>
                      {selectedGoalType === 'CUSTOMER_RETENTION' &&
                        targetOption === 'TARGET_SPECIFIC' && (
                          <p className="mt-2 text-[11px] leading-5 text-amber-700">
                            Google only allows retention TARGET_SPECIFIC for allowlisted accounts.
                          </p>
                        )}

                      {targetOption === 'TARGET_ALL' && (
                        <div className="mt-4 grid gap-3 sm:grid-cols-2">
                          <label className="block text-xs font-semibold text-slate-700">
                            Campaign additional value override
                            <input
                              className="form-input mt-2"
                              type="number"
                              min="0"
                              value={campaignValue}
                              disabled={!canModify}
                              onChange={(event) => setCampaignValue(event.target.value)}
                            />
                          </label>
                          <label className="block text-xs font-semibold text-slate-700">
                            Campaign high lifetime value override
                            <input
                              className="form-input mt-2"
                              type="number"
                              min="0"
                              value={campaignHighValue}
                              disabled={!canModify}
                              onChange={(event) => setCampaignHighValue(event.target.value)}
                            />
                          </label>
                        </div>
                      )}
                    </>
                  ) : (
                    <div className="mt-4 space-y-3">
                      <label className="block text-xs font-semibold text-slate-700">
                        Campaign loyalty value multiplier
                        <input
                          className="form-input mt-2 max-w-xs"
                          type="number"
                          min="0.01"
                          step="0.1"
                          value={campaignMultiplier}
                          disabled={!canModify}
                          onChange={(event) => setCampaignMultiplier(event.target.value)}
                        />
                      </label>
                      <label className="flex items-center gap-2 text-xs text-slate-700">
                        <input
                          type="checkbox"
                          checked={loyaltyBidAdjustments}
                          disabled={!canModify}
                          onChange={(event) => setLoyaltyBidAdjustments(event.target.checked)}
                        />
                        Enable bid adjustments for loyalty members
                      </label>
                      <label className="flex items-center gap-2 text-xs text-slate-700">
                        <input
                          type="checkbox"
                          checked={loyaltyPlaBenefits}
                          disabled={!canModify}
                          onChange={(event) => setLoyaltyPlaBenefits(event.target.checked)}
                        />
                        Show targeted loyalty-member benefits in eligible PLA formats
                      </label>
                    </div>
                  )}

                  {canModify && (
                    <div className="mt-5 flex flex-wrap justify-end gap-2">
                      {currentConfig && (
                        <button
                          type="button"
                          className="secondary-button inline-flex items-center gap-2"
                          disabled={saving === 'remove'}
                          onClick={() => void removeCampaignConfig()}
                        >
                          <FiTrash2 aria-hidden="true" />
                          {saving === 'remove' ? 'Removing…' : 'Remove from campaign'}
                        </button>
                      )}
                      <button
                        type="button"
                        className="primary-button inline-flex items-center gap-2"
                        disabled={saving === 'campaign'}
                        onClick={() => void saveCampaignConfig()}
                      >
                        <FiSave aria-hidden="true" />
                        {saving === 'campaign'
                          ? 'Saving…'
                          : currentConfig
                            ? 'Save campaign goal'
                            : 'Enable on campaign'}
                      </button>
                    </div>
                  )}
                </>
              )}
            </div>
          </>
        )}
      </div>
    </section>
  )
}
