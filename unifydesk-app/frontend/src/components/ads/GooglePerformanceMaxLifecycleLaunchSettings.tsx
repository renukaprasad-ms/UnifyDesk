import { useSnackbarState } from '../feedback/useSnackbarState'
import { useEffect, useMemo, useState } from 'react'
import { adCenterApi } from '../../features/ads/adCenterApi'
import type {
  GoogleLifecycleGoal,
  GoogleLifecycleGoalType,
  GoogleUserListCustomerType,
} from '../../features/ads/types'
import { apiErrorMessage } from '../../lib/api/apiClient'

type Props = {
  companyId: string
  accountId: string
  goalType: GoogleLifecycleGoalType | ''
  setGoalType: (value: GoogleLifecycleGoalType | '') => void
  targetOption: 'TARGET_ALL' | 'TARGET_SPECIFIC'
  setTargetOption: (value: 'TARGET_ALL' | 'TARGET_SPECIFIC') => void
  additionalValue: string
  setAdditionalValue: (value: string) => void
  highLifetimeValue: string
  setHighLifetimeValue: (value: string) => void
  valueMultiplier: string
  setValueMultiplier: (value: string) => void
  enableBidAdjustmentsForLoyaltyMembers: boolean
  setEnableBidAdjustmentsForLoyaltyMembers: (value: boolean) => void
  showTargetedLoyaltyMemberBenefitsInPla: boolean
  setShowTargetedLoyaltyMemberBenefitsInPla: (value: boolean) => void
  disabled?: boolean
}

const OPTIONS: Array<{ value: GoogleLifecycleGoalType; label: string }> = [
  { value: 'NEW_CUSTOMER_ACQUISITION', label: 'New customer acquisition' },
  { value: 'CUSTOMER_RETENTION', label: 'Customer retention' },
  { value: 'LOYALTY_RETENTION', label: 'Loyalty retention' },
]

export default function GooglePerformanceMaxLifecycleLaunchSettings({
  companyId,
  accountId,
  goalType,
  setGoalType,
  targetOption,
  setTargetOption,
  additionalValue,
  setAdditionalValue,
  highLifetimeValue,
  setHighLifetimeValue,
  valueMultiplier,
  setValueMultiplier,
  enableBidAdjustmentsForLoyaltyMembers,
  setEnableBidAdjustmentsForLoyaltyMembers,
  showTargetedLoyaltyMemberBenefitsInPla,
  setShowTargetedLoyaltyMemberBenefitsInPla,
  disabled = false,
}: Props) {
  const [goals, setGoals] = useState<GoogleLifecycleGoal[]>([])
  const [customerTypes, setCustomerTypes] = useState<GoogleUserListCustomerType[]>([])
  const [loading, setLoading] = useState(false)
  const [, setError] = useSnackbarState('', 'error')

  useEffect(() => {
    if (!accountId) {
      setGoals([])
      setCustomerTypes([])
      return
    }
    setLoading(true)
    setError('')
    void Promise.all([
      adCenterApi.googleLifecycleGoals(companyId, accountId),
      adCenterApi.googleUserListCustomerTypes(companyId, accountId),
    ])
      .then(([goalData, customerTypeData]) => {
        setGoals(goalData)
        setCustomerTypes(customerTypeData)
      })
      .catch((loadError) =>
        setError(apiErrorMessage(loadError, 'Unable to load Google lifecycle-goal readiness')),
      )
      .finally(() => setLoading(false))
  }, [companyId, accountId])

  const configured = useMemo(() => new Set(goals.map((goal) => goal.goalType)), [goals])
  const hasClassifiedLists = customerTypes.length > 0
  const hasLoyaltyTier = customerTypes.some((item) =>
    item.customerTypeCategory?.startsWith('LOYALTY_TIER_'),
  )

  return (
    <div className="rounded-xl border border-violet-100 bg-violet-50/40 p-4 sm:col-span-2">
      <p className="text-xs font-semibold text-slate-700">Customer lifecycle optimization</p>
      <p className="mt-1 text-[11px] leading-5 text-slate-500">
        Optional. Account lifecycle goals and customer-list classifications must already be
        configured. This launch only applies campaign-specific settings.
      </p>



      {loading ? (
        <p className="mt-3 text-xs text-slate-500">Checking Google lifecycle setup…</p>
      ) : (
        <>
          <label className="mt-3 block text-xs font-semibold text-slate-700">
            Lifecycle goal
            <select
              className="form-input mt-2"
              value={goalType}
              disabled={disabled || !accountId}
              onChange={(event) => setGoalType(event.target.value as GoogleLifecycleGoalType | '')}
            >
              <option value="">No lifecycle goal</option>
              {OPTIONS.map((option) => {
                const ready =
                  configured.has(option.value) &&
                  hasClassifiedLists &&
                  (option.value !== 'LOYALTY_RETENTION' || hasLoyaltyTier)
                return (
                  <option key={option.value} value={option.value} disabled={!ready}>
                    {option.label}
                    {ready
                      ? ''
                      : !configured.has(option.value)
                        ? ' · account goal missing'
                        : !hasClassifiedLists
                          ? ' · customer lists not classified'
                          : ' · loyalty tier list missing'}
                  </option>
                )
              })}
            </select>
          </label>

          {goalType && (
            <div className="mt-4 space-y-3">
              {goalType !== 'LOYALTY_RETENTION' ? (
                <>
                  <label className="block text-xs font-semibold text-slate-700">
                    Optimization mode
                    <select
                      className="form-input mt-2"
                      value={targetOption}
                      disabled={disabled}
                      onChange={(event) =>
                        setTargetOption(event.target.value as 'TARGET_ALL' | 'TARGET_SPECIFIC')
                      }
                    >
                      <option value="TARGET_ALL">
                        Target all · bid/value higher for lifecycle customers
                      </option>
                      <option value="TARGET_SPECIFIC">Target lifecycle customers only</option>
                    </select>
                  </label>
                  {goalType === 'CUSTOMER_RETENTION' && targetOption === 'TARGET_SPECIFIC' && (
                    <p className="text-[11px] leading-5 text-amber-700">
                      Google only allows retention TARGET_SPECIFIC for allowlisted accounts.
                    </p>
                  )}

                  {targetOption === 'TARGET_ALL' && (
                    <div className="grid gap-3 sm:grid-cols-2">
                      <label className="block text-xs font-semibold text-slate-700">
                        Additional value override
                        <input
                          className="form-input mt-2"
                          type="number"
                          min="0"
                          value={additionalValue}
                          disabled={disabled}
                          onChange={(event) => setAdditionalValue(event.target.value)}
                        />
                      </label>
                      <label className="block text-xs font-semibold text-slate-700">
                        High lifetime value override
                        <input
                          className="form-input mt-2"
                          type="number"
                          min="0"
                          value={highLifetimeValue}
                          disabled={disabled}
                          onChange={(event) => setHighLifetimeValue(event.target.value)}
                        />
                      </label>
                    </div>
                  )}
                </>
              ) : (
                <>
                  <label className="block text-xs font-semibold text-slate-700">
                    Loyalty value multiplier override
                    <input
                      className="form-input mt-2 max-w-xs"
                      type="number"
                      min="0.01"
                      step="0.1"
                      value={valueMultiplier}
                      disabled={disabled}
                      onChange={(event) => setValueMultiplier(event.target.value)}
                    />
                  </label>
                  <label className="flex items-center gap-2 text-xs text-slate-700">
                    <input
                      type="checkbox"
                      checked={enableBidAdjustmentsForLoyaltyMembers}
                      disabled={disabled}
                      onChange={(event) =>
                        setEnableBidAdjustmentsForLoyaltyMembers(event.target.checked)
                      }
                    />
                    Enable bid adjustments for loyalty members
                  </label>
                  <label className="flex items-center gap-2 text-xs text-slate-700">
                    <input
                      type="checkbox"
                      checked={showTargetedLoyaltyMemberBenefitsInPla}
                      disabled={disabled}
                      onChange={(event) =>
                        setShowTargetedLoyaltyMemberBenefitsInPla(event.target.checked)
                      }
                    />
                    Show eligible loyalty-member PLA benefits
                  </label>
                </>
              )}
            </div>
          )}
        </>
      )}
    </div>
  )
}
