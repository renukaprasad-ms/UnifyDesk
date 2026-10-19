import { useSnackbarState } from '../../components/feedback/useSnackbarState'
import { useEffect, useMemo, useState, type FormEvent } from 'react'
import { FiArrowLeft, FiPause, FiPlay, FiSave } from 'react-icons/fi'
import { Link, useParams } from 'react-router'
import { adCenterApi } from '../../features/ads/adCenterApi'
import type { Ad, AdCreative, AdSet, UpdateMetaAdInput } from '../../features/ads/types'
import { PERMISSIONS } from '../../features/iam/permissions'
import { useIam } from '../../features/iam/useIam'
import { apiErrorMessage } from '../../lib/api/apiClient'
import { useAppSelector } from '../../store/hooks'
import { selectSelectedCompany } from '../../store/workspace/workspaceSlice'
import { PageSkeleton } from '../../components/ui/Skeleton'

export default function AdDetails() {
  const { adId = '' } = useParams()
  const selectedCompany = useAppSelector(selectSelectedCompany)
  const { can } = useIam()

  const [ad, setAd] = useState<Ad | null>(null)
  const [adSet, setAdSet] = useState<AdSet | null>(null)
  const [creatives, setCreatives] = useState<AdCreative[]>([])
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [statusWorking, setStatusWorking] = useState(false)
  const [name, setName] = useState('')
  const [creativeId, setCreativeId] = useState('')
  const [error, setError] = useSnackbarState('', 'error')
  const [, setNotice] = useSnackbarState('', 'success')

  const selectedCreative = useMemo(
    () => creatives.find((creative) => creative.id === creativeId) ?? null,
    [creatives, creativeId],
  )

  const load = async () => {
    if (!selectedCompany || !adId) return
    setLoading(true)
    setError('')
    try {
      const adData = await adCenterApi.ad(selectedCompany.companyId, adId)
      const [adSetData, creativeData] = await Promise.all([
        adCenterApi.adSet(selectedCompany.companyId, adData.adSetId),
        adCenterApi.creatives(selectedCompany.companyId, adData.adAccountId),
      ])
      setAd(adData)
      setAdSet(adSetData)
      setCreatives(creativeData)
      setName(adData.name)
      setCreativeId(
        adData.creativeId ??
          creativeData.find((creative) => creative.externalCreativeId === adData.externalCreativeId)
            ?.id ??
          '',
      )
    } catch (loadError) {
      setError(apiErrorMessage(loadError, 'Unable to load ad'))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    void load()
  }, [selectedCompany?.companyId, adId])

  const save = async (event: FormEvent) => {
    event.preventDefault()
    if (!selectedCompany || !ad) return
    setSaving(true)
    setError('')
    setNotice('')

    const input: UpdateMetaAdInput = {
      name: name.trim(),
      creativeId: creativeId || undefined,
    }

    try {
      const updated = await adCenterApi.updateMetaAd(selectedCompany.companyId, ad.id, input)
      setAd(updated)
      setName(updated.name)
      setCreativeId(
        updated.creativeId ??
          creatives.find((creative) => creative.externalCreativeId === updated.externalCreativeId)
            ?.id ??
          '',
      )
      setNotice('Ad updated in Meta.')
    } catch (saveError) {
      setError(apiErrorMessage(saveError, 'Unable to update Meta ad'))
    } finally {
      setSaving(false)
    }
  }

  const setStatus = async (status: 'ACTIVE' | 'PAUSED') => {
    if (!selectedCompany || !ad) return
    setStatusWorking(true)
    setError('')
    setNotice('')
    try {
      const updated = await adCenterApi.setMetaAdStatus(selectedCompany.companyId, ad.id, status)
      setAd(updated)
      setNotice(`Ad is now ${status.toLowerCase()}.`)
    } catch (statusError) {
      setError(apiErrorMessage(statusError, 'Unable to update ad status'))
    } finally {
      setStatusWorking(false)
    }
  }

  if (!selectedCompany) return null

  return (
    <section className="px-4 py-6 sm:px-6 lg:px-8">
      <div className="mx-auto max-w-5xl">
        <Link
          to={adSet ? `/ad-center/ad-sets/${adSet.id}` : '/ad-center/campaigns'}
          className="inline-flex items-center gap-2 text-sm font-semibold text-slate-500 hover:text-slate-900"
        >
          <FiArrowLeft aria-hidden="true" />
          Back to ad set
        </Link>

        {loading ? (
          <div className="mt-6"><PageSkeleton /></div>
        ) : ad ? (
          <>
            <header className="mt-5 flex flex-wrap items-end justify-between gap-4">
              <div>
                <p className="eyebrow">Meta ad</p>
                <h1 className="mt-2 text-2xl font-bold tracking-tight text-slate-950 sm:text-3xl">
                  {ad.name}
                </h1>
                <p className="mt-2 text-sm text-slate-500">
                  {adSet?.name ?? 'Ad set'} · {ad.externalAdId}
                </p>
              </div>

              {can(PERMISSIONS.ADS_MODIFY) &&
                (ad.providerStatus === 'ACTIVE' ? (
                  <button
                    type="button"
                    className="secondary-button inline-flex items-center gap-2"
                    disabled={statusWorking}
                    onClick={() => void setStatus('PAUSED')}
                  >
                    <FiPause aria-hidden="true" />
                    {statusWorking ? 'Updating…' : 'Pause'}
                  </button>
                ) : (
                  <button
                    type="button"
                    className="primary-button inline-flex items-center gap-2"
                    disabled={
                      statusWorking || ad.providerStatus === 'ARCHIVED' || ad.status === 'DISABLED'
                    }
                    onClick={() => void setStatus('ACTIVE')}
                  >
                    <FiPlay aria-hidden="true" />
                    {statusWorking ? 'Updating…' : 'Start ad'}
                  </button>
                ))}
            </header>



            <div className="mt-6 grid gap-4 sm:grid-cols-3">
              <div className="panel p-5">
                <p className="text-xs font-semibold text-slate-400">Configured status</p>
                <p className="mt-2 text-lg font-bold text-slate-950">
                  {ad.providerStatus ?? ad.status}
                </p>
              </div>
              <div className="panel p-5">
                <p className="text-xs font-semibold text-slate-400">Effective status</p>
                <p className="mt-2 text-lg font-bold text-slate-950">{ad.effectiveStatus ?? '—'}</p>
              </div>
              <div className="panel p-5">
                <p className="text-xs font-semibold text-slate-400">Creative</p>
                <p className="mt-2 truncate text-lg font-bold text-slate-950">
                  {selectedCreative?.name ?? ad.externalCreativeId ?? '—'}
                </p>
              </div>
            </div>

            <form className="panel mt-5 p-6" onSubmit={save}>
              <div>
                <h2 className="text-base font-bold text-slate-950">Ad settings</h2>
                <p className="mt-1 text-xs text-slate-500">
                  Swap the reusable creative without rebuilding the campaign or ad set.
                </p>
              </div>

              <div className="mt-5 grid gap-5 sm:grid-cols-2">
                <label className="block text-xs font-semibold text-slate-700">
                  Ad name
                  <input
                    className="form-input mt-2"
                    value={name}
                    maxLength={255}
                    disabled={!can(PERMISSIONS.ADS_MODIFY)}
                    onChange={(event) => setName(event.target.value)}
                  />
                </label>

                <label className="block text-xs font-semibold text-slate-700">
                  Creative
                  <select
                    className="form-input mt-2"
                    value={creativeId}
                    disabled={!can(PERMISSIONS.ADS_MODIFY)}
                    onChange={(event) => setCreativeId(event.target.value)}
                  >
                    <option value="">Choose creative</option>
                    {creatives
                      .filter((creative) => creative.accessible)
                      .map((creative) => (
                        <option key={creative.id} value={creative.id}>
                          {creative.name} · {creative.creativeFormat ?? 'Creative'}
                        </option>
                      ))}
                  </select>
                </label>
              </div>

              {selectedCreative && (
                <div className="mt-5 rounded-xl border border-slate-200 bg-slate-50 p-4">
                  <p className="text-xs font-semibold uppercase tracking-wide text-slate-400">
                    Selected creative
                  </p>
                  <p className="mt-2 font-semibold text-slate-900">{selectedCreative.name}</p>
                  {selectedCreative.primaryText && (
                    <p className="mt-2 text-sm leading-6 text-slate-600">
                      {selectedCreative.primaryText}
                    </p>
                  )}
                  {selectedCreative.destinationUrl && (
                    <p className="mt-2 break-all text-xs text-slate-400">
                      {selectedCreative.destinationUrl}
                    </p>
                  )}
                </div>
              )}

              {can(PERMISSIONS.ADS_MODIFY) && (
                <div className="mt-6 flex justify-end border-t border-slate-100 pt-5">
                  <button
                    type="submit"
                    className="primary-button inline-flex items-center gap-2"
                    disabled={saving || !name.trim() || !creativeId}
                  >
                    <FiSave aria-hidden="true" />
                    {saving ? 'Saving…' : 'Save to Meta'}
                  </button>
                </div>
              )}
            </form>
          </>
        ) : (
          error && (
            <div className="mt-6 rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
              {error}
            </div>
          )
        )}
      </div>
    </section>
  )
}
