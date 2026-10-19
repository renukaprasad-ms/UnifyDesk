import { useSnackbarState } from '../feedback/useSnackbarState'
import { useEffect, useState } from 'react'
import { FiSave } from 'react-icons/fi'
import GoogleSearchDemographicSelector from './GoogleSearchDemographicSelector'
import { adCenterApi } from '../../features/ads/adCenterApi'
import type { GoogleSearchDemographicTargeting } from '../../features/ads/types'
import { apiErrorMessage } from '../../lib/api/apiClient'
import { CardSkeleton } from '../ui/Skeleton'

type Props = {
  companyId: string
  accountId: string
  campaignId: string
  canModify: boolean
}

export default function GoogleSearchDemographicTargetingPanel({
  companyId,
  accountId,
  campaignId,
  canModify,
}: Props) {
  const [parentalIncludes, setParentalIncludes] = useState<string[]>([])
  const [parentalExcludes, setParentalExcludes] = useState<string[]>([])
  const [incomeIncludes, setIncomeIncludes] = useState<string[]>([])
  const [incomeExcludes, setIncomeExcludes] = useState<string[]>([])
  const [detailedIds, setDetailedIds] = useState<string[]>([])
  const [excludedDetailedIds, setExcludedDetailedIds] = useState<string[]>([])
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [, setError] = useSnackbarState('', 'error')
  const [, setNotice] = useSnackbarState('', 'success')

  const apply = (value: GoogleSearchDemographicTargeting) => {
    setParentalIncludes(value.parentalStatusIncludes)
    setParentalExcludes(value.parentalStatusExcludes)
    setIncomeIncludes(value.incomeRangeIncludes)
    setIncomeExcludes(value.incomeRangeExcludes)
    setDetailedIds(value.detailedDemographicIds.map(String))
    setExcludedDetailedIds(value.excludedDetailedDemographicIds.map(String))
  }

  useEffect(() => {
    setLoading(true)
    setError('')
    void adCenterApi
      .googleSearchDemographics(companyId, campaignId)
      .then(apply)
      .catch((loadError) =>
        setError(apiErrorMessage(loadError, 'Unable to load Google Search demographics')),
      )
      .finally(() => setLoading(false))
  }, [companyId, campaignId])

  const save = async () => {
    setSaving(true)
    setError('')
    setNotice('')
    try {
      const updated = await adCenterApi.updateGoogleSearchDemographics(companyId, campaignId, {
        parentalStatusIncludes: parentalIncludes,
        parentalStatusExcludes: parentalExcludes,
        incomeRangeIncludes: incomeIncludes,
        incomeRangeExcludes: incomeExcludes,
        detailedDemographicIds: detailedIds,
        excludedDetailedDemographicIds: excludedDetailedIds,
      })
      apply(updated)
      setNotice('Search demographic targeting updated in Google Ads.')
    } catch (saveError) {
      setError(apiErrorMessage(saveError, 'Unable to update Google Search demographics'))
    } finally {
      setSaving(false)
    }
  }

  return (
    <section className="panel mt-5 p-5">
      <div>
        <h2 className="font-bold text-slate-950">Search demographics</h2>
        <p className="mt-1 max-w-3xl text-xs leading-5 text-slate-500">
          Manage Google Search parental status, household income, and detailed demographic criteria
          at campaign level.
        </p>
      </div>



      {loading ? (
        <div className="mt-4"><CardSkeleton rows={4} /></div>
      ) : (
        <>
          <div className="mt-4">
            <GoogleSearchDemographicSelector
              companyId={companyId}
              accountId={accountId}
              parentalStatusIncludes={parentalIncludes}
              parentalStatusExcludes={parentalExcludes}
              incomeRangeIncludes={incomeIncludes}
              incomeRangeExcludes={incomeExcludes}
              detailedDemographicIds={detailedIds}
              excludedDetailedDemographicIds={excludedDetailedIds}
              setParentalStatusIncludes={setParentalIncludes}
              setParentalStatusExcludes={setParentalExcludes}
              setIncomeRangeIncludes={setIncomeIncludes}
              setIncomeRangeExcludes={setIncomeExcludes}
              setDetailedDemographicIds={setDetailedIds}
              setExcludedDetailedDemographicIds={setExcludedDetailedIds}
              disabled={!canModify}
            />
          </div>

          {canModify && (
            <div className="mt-5 flex justify-end border-t border-slate-100 pt-5">
              <button
                type="button"
                className="primary-button inline-flex items-center gap-2"
                disabled={saving}
                onClick={() => void save()}
              >
                <FiSave aria-hidden="true" />
                {saving ? 'Saving…' : 'Save demographics'}
              </button>
            </div>
          )}
        </>
      )}
    </section>
  )
}
