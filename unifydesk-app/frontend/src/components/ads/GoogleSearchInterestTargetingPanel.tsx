import { useSnackbarState } from '../feedback/useSnackbarState'
import { useEffect, useState } from 'react'
import { FiSave } from 'react-icons/fi'
import GoogleSearchInterestSelector from './GoogleSearchInterestSelector'
import { adCenterApi } from '../../features/ads/adCenterApi'
import type { GoogleSearchInterestTargeting } from '../../features/ads/types'
import { apiErrorMessage } from '../../lib/api/apiClient'
import { CardSkeleton } from '../ui/Skeleton'

type Props = {
  companyId: string
  accountId: string
  adGroupId: string
  canModify: boolean
}

export default function GoogleSearchInterestTargetingPanel({
  companyId,
  accountId,
  adGroupId,
  canModify,
}: Props) {
  const [snapshot, setSnapshot] = useState<GoogleSearchInterestTargeting | null>(null)
  const [included, setIncluded] = useState<string[]>([])
  const [excluded, setExcluded] = useState<string[]>([])
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [, setError] = useSnackbarState('', 'error')
  const [, setNotice] = useSnackbarState('', 'success')

  const applySnapshot = (value: GoogleSearchInterestTargeting) => {
    setSnapshot(value)
    setIncluded(value.includedResourceNames)
    setExcluded(value.excludedResourceNames)
  }

  useEffect(() => {
    setLoading(true)
    setError('')
    void adCenterApi
      .googleSearchInterestTargeting(companyId, adGroupId)
      .then(applySnapshot)
      .catch((loadError) =>
        setError(apiErrorMessage(loadError, 'Unable to load Google Search interest targeting')),
      )
      .finally(() => setLoading(false))
  }, [companyId, adGroupId])

  const save = async () => {
    setSaving(true)
    setError('')
    setNotice('')
    try {
      const updated = await adCenterApi.updateGoogleSearchInterestTargeting(companyId, adGroupId, {
        includedResourceNames: included,
        excludedResourceNames: excluded,
      })
      applySnapshot(updated)
      setNotice('Affinity and In-market targeting updated in Google Ads.')
    } catch (saveError) {
      setError(apiErrorMessage(saveError, 'Unable to update Google Search interest targeting'))
    } finally {
      setSaving(false)
    }
  }

  return (
    <section className="panel mt-5 p-5">
      <div>
        <h2 className="font-bold text-slate-950">Search interest segments</h2>
        <p className="mt-1 max-w-3xl text-xs leading-5 text-slate-500">
          Manage Google Affinity and In-market criteria on this Search ad group. These criteria
          share the Observation/Targeting mode configured in the Search audience panel above.
        </p>
      </div>



      {loading ? (
        <div className="mt-4"><CardSkeleton rows={4} /></div>
      ) : (
        <>
          {snapshot?.preservedUnsupportedResourceNames.length ? (
            <p className="mt-4 rounded-xl border border-amber-200 bg-amber-50 px-3 py-2 text-xs leading-5 text-amber-800">
              This ad group contains other Google USER_INTEREST criteria that are outside the
              Affinity/In-market scope managed here. UnifyDesk preserves them:{' '}
              {snapshot.preservedUnsupportedResourceNames.join(', ')}.
            </p>
          ) : null}

          <div className="mt-4">
            <GoogleSearchInterestSelector
              companyId={companyId}
              accountId={accountId}
              includedResourceNames={included}
              excludedResourceNames={excluded}
              setIncludedResourceNames={setIncluded}
              setExcludedResourceNames={setExcluded}
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
                {saving ? 'Saving…' : 'Save interest targeting'}
              </button>
            </div>
          )}
        </>
      )}
    </section>
  )
}
