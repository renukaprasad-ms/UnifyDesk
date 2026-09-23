import { useEffect, useState, type FormEvent } from 'react'
import { FiArrowLeft, FiPause, FiPlay, FiPlus, FiRefreshCw, FiSave } from 'react-icons/fi'
import { Link } from 'react-router'
import GoogleKeywordModal from './GoogleKeywordModal'
import GoogleResponsiveSearchAdModal from './GoogleResponsiveSearchAdModal'
import { adCenterApi } from '../../features/ads/adCenterApi'
import type {
  Ad,
  AdAccount,
  AdCampaign,
  AdKeyword,
  AdSet,
  CreateGoogleKeywordInput,
  CreateGoogleResponsiveSearchAdInput,
} from '../../features/ads/types'
import { PERMISSIONS } from '../../features/iam/permissions'
import { useIam } from '../../features/iam/useIam'
import { apiErrorMessage } from '../../lib/api/apiClient'

type Props = {
  companyId: string
  initialAdGroup: AdSet
  campaign: AdCampaign | null
  account: AdAccount | null
  initialAds: Ad[]
}

const toMajor = (value: number | null | undefined, currency?: string | null) => {
  if (value == null) return ''
  if (!currency) return String(value)
  try {
    const digits =
      new Intl.NumberFormat(undefined, { style: 'currency', currency })
        .resolvedOptions().maximumFractionDigits ?? 2
    return String(value / 10 ** digits)
  } catch {
    return String(value)
  }
}

const toMinor = (value: string, currency?: string | null) => {
  if (!value.trim()) return undefined
  const numeric = Number(value)
  if (!Number.isFinite(numeric) || numeric <= 0) return undefined
  if (!currency) return Math.round(numeric)
  try {
    const digits =
      new Intl.NumberFormat(undefined, { style: 'currency', currency })
        .resolvedOptions().maximumFractionDigits ?? 2
    return Math.round(numeric * 10 ** digits)
  } catch {
    return Math.round(numeric)
  }
}

const formatMicros = (value?: number | null, currency?: string | null) => {
  if (value == null) return '—'
  const major = value / 1_000_000
  if (!currency) return String(major)
  try {
    return new Intl.NumberFormat(undefined, { style: 'currency', currency }).format(major)
  } catch {
    return `${major} ${currency}`
  }
}

export default function GoogleAdGroupDetails({
  companyId,
  initialAdGroup,
  campaign,
  account,
  initialAds,
}: Props) {
  const { can } = useIam()
  const [adGroup, setAdGroup] = useState(initialAdGroup)
  const [ads, setAds] = useState(initialAds)
  const [keywords, setKeywords] = useState<AdKeyword[]>([])
  const [name, setName] = useState(initialAdGroup.name)
  const [cpcBid, setCpcBid] = useState(toMajor(initialAdGroup.bidAmountMinor, account?.currency))
  const [saving, setSaving] = useState(false)
  const [syncing, setSyncing] = useState(false)
  const [syncingKeywords, setSyncingKeywords] = useState(false)
  const [creatingKeyword, setCreatingKeyword] = useState(false)
  const [creatingAd, setCreatingAd] = useState(false)
  const [workingId, setWorkingId] = useState<string | null>(null)
  const [keywordOpen, setKeywordOpen] = useState(false)
  const [adOpen, setAdOpen] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')

  const loadKeywords = async () => {
    setKeywords(await adCenterApi.keywords(companyId, adGroup.id))
  }

  useEffect(() => {
    void loadKeywords().catch(() => undefined)
  }, [companyId, adGroup.id])

  const save = async (event: FormEvent) => {
    event.preventDefault()
    setSaving(true)
    setError('')
    setNotice('')
    try {
      const updated = await adCenterApi.updateGoogleAdGroup(companyId, adGroup.id, {
        name: name.trim(),
        cpcBidMinor: toMinor(cpcBid, account?.currency),
      })
      setAdGroup(updated)
      setName(updated.name)
      setCpcBid(toMajor(updated.bidAmountMinor, account?.currency))
      setNotice('Ad group updated in Google Ads.')
    } catch (saveError) {
      setError(apiErrorMessage(saveError, 'Unable to update Google Ads ad group'))
    } finally {
      setSaving(false)
    }
  }

  const setGroupStatus = async (status: 'ACTIVE' | 'PAUSED') => {
    setWorkingId('group')
    setError('')
    try {
      const updated = await adCenterApi.setGoogleAdGroupStatus(companyId, adGroup.id, status)
      setAdGroup(updated)
      setNotice(`Ad group is now ${status.toLowerCase()}.`)
    } catch (statusError) {
      setError(apiErrorMessage(statusError, 'Unable to update ad group status'))
    } finally {
      setWorkingId(null)
    }
  }

  const syncKeywords = async () => {
    setSyncingKeywords(true)
    setError('')
    try {
      const result = await adCenterApi.syncGoogleKeywords(companyId, adGroup.id)
      await loadKeywords()
      setNotice(`Synchronized ${result.synchronizedCount} keyword(s) from Google Ads.`)
    } catch (syncError) {
      setError(apiErrorMessage(syncError, 'Unable to synchronize Google Ads keywords'))
    } finally {
      setSyncingKeywords(false)
    }
  }

  const createKeyword = async (input: CreateGoogleKeywordInput) => {
    setCreatingKeyword(true)
    setError('')
    try {
      const created = await adCenterApi.createGoogleKeyword(companyId, adGroup.id, input)
      setKeywords((current) => [created, ...current.filter((item) => item.id !== created.id)])
      setKeywordOpen(false)
      setNotice(`${created.keywordText} was added.`)
    } catch (createError) {
      setError(apiErrorMessage(createError, 'Unable to create Google Ads keyword'))
    } finally {
      setCreatingKeyword(false)
    }
  }

  const setKeywordStatus = async (keyword: AdKeyword, status: 'ACTIVE' | 'PAUSED') => {
    setWorkingId(keyword.id)
    setError('')
    try {
      const updated = await adCenterApi.updateGoogleKeyword(companyId, keyword.id, { status })
      setKeywords((current) => current.map((item) => item.id === updated.id ? updated : item))
      setNotice(`${updated.keywordText} is now ${status.toLowerCase()}.`)
    } catch (statusError) {
      setError(apiErrorMessage(statusError, 'Unable to update Google Ads keyword'))
    } finally {
      setWorkingId(null)
    }
  }

  const syncAds = async () => {
    setSyncing(true)
    setError('')
    try {
      const result = await adCenterApi.syncGoogleAds(companyId, adGroup.id)
      setAds(await adCenterApi.ads(companyId, adGroup.id))
      setNotice(`Synchronized ${result.synchronizedCount} ad(s) from Google Ads.`)
    } catch (syncError) {
      setError(apiErrorMessage(syncError, 'Unable to synchronize Google Ads ads'))
    } finally {
      setSyncing(false)
    }
  }

  const createAd = async (input: CreateGoogleResponsiveSearchAdInput) => {
    setCreatingAd(true)
    setError('')
    try {
      const created = await adCenterApi.createGoogleResponsiveSearchAd(companyId, adGroup.id, input)
      setAds((current) => [created, ...current.filter((item) => item.id !== created.id)])
      setAdOpen(false)
      setNotice(`${created.name} was created in paused state.`)
    } catch (createError) {
      setError(apiErrorMessage(createError, 'Unable to create responsive search ad'))
    } finally {
      setCreatingAd(false)
    }
  }

  const setAdStatus = async (ad: Ad, status: 'ACTIVE' | 'PAUSED') => {
    setWorkingId(ad.id)
    setError('')
    try {
      const updated = await adCenterApi.setGoogleAdStatus(companyId, ad.id, status)
      setAds((current) => current.map((item) => item.id === updated.id ? updated : item))
      setNotice(`${updated.name} is now ${status.toLowerCase()}.`)
    } catch (statusError) {
      setError(apiErrorMessage(statusError, 'Unable to update Google Ads ad'))
    } finally {
      setWorkingId(null)
    }
  }

  return (
    <section className="px-4 py-6 sm:px-6 lg:px-8">
      <div className="mx-auto max-w-5xl">
        <Link to={campaign ? `/ad-center/campaigns/${campaign.id}` : '/ad-center/campaigns'} className="inline-flex items-center gap-2 text-sm font-semibold text-slate-500 hover:text-slate-900">
          <FiArrowLeft /> Back to campaign
        </Link>

        <header className="mt-5 flex flex-wrap items-end justify-between gap-4">
          <div>
            <p className="eyebrow">Google Ads · Ad group</p>
            <h1 className="mt-2 text-2xl font-bold tracking-tight text-slate-950 sm:text-3xl">{adGroup.name}</h1>
            <p className="mt-2 text-sm text-slate-500">{campaign?.name ?? 'Campaign'} · {adGroup.externalAdSetId}</p>
          </div>
          {can(PERMISSIONS.ADS_MODIFY) && (
            adGroup.status === 'ACTIVE' ? (
              <button className="secondary-button inline-flex items-center gap-2" disabled={workingId !== null} onClick={() => void setGroupStatus('PAUSED')}><FiPause />Pause</button>
            ) : (
              <button className="primary-button inline-flex items-center gap-2" disabled={workingId !== null || adGroup.status === 'REMOVED'} onClick={() => void setGroupStatus('ACTIVE')}><FiPlay />Start ad group</button>
            )
          )}
        </header>

        {(error || notice) && <div className={`mt-6 rounded-xl border px-4 py-3 text-sm font-medium ${error ? 'border-red-200 bg-red-50 text-red-700' : 'border-emerald-200 bg-emerald-50 text-emerald-700'}`}>{error || notice}</div>}

        <form className="panel mt-6 p-6" onSubmit={save}>
          <h2 className="text-base font-bold text-slate-950">Ad group settings</h2>
          <div className="mt-5 grid gap-4 sm:grid-cols-2">
            <label className="text-xs font-semibold text-slate-700">Name<input className="form-input mt-2" value={name} maxLength={255} onChange={(e) => setName(e.target.value)} /></label>
            <label className="text-xs font-semibold text-slate-700">Default CPC bid {account?.currency ? `(${account.currency})` : ''}<input className="form-input mt-2" type="number" min="0.01" step="0.01" value={cpcBid} onChange={(e) => setCpcBid(e.target.value)} /></label>
          </div>
          {can(PERMISSIONS.ADS_MODIFY) && <div className="mt-5 flex justify-end"><button type="submit" className="primary-button inline-flex items-center gap-2" disabled={saving || !name.trim()}><FiSave />{saving ? 'Saving…' : 'Save to Google'}</button></div>}
        </form>

        <div className="panel mt-5 overflow-hidden">
          <div className="flex flex-wrap items-center justify-between gap-3 border-b border-slate-100 px-5 py-4">
            <div><h2 className="font-bold text-slate-950">Keywords</h2><p className="mt-1 text-xs text-slate-500">Broad, phrase, exact, and negative keywords for this ad group.</p></div>
            <div className="flex gap-2">
              {can(PERMISSIONS.ADS_MODIFY) && <button className="secondary-button inline-flex items-center gap-2" disabled={syncingKeywords} onClick={() => void syncKeywords()}><FiRefreshCw />{syncingKeywords ? 'Syncing…' : 'Sync keywords'}</button>}
              {can(PERMISSIONS.ADS_CREATE) && <button className="primary-button inline-flex items-center gap-2" onClick={() => setKeywordOpen(true)}><FiPlus />Add keyword</button>}
            </div>
          </div>
          {keywords.length === 0 ? <div className="p-8 text-center text-sm text-slate-500">No keywords stored yet.</div> : (
            <div className="divide-y divide-slate-100">
              {keywords.map((keyword) => <div key={keyword.id} className="flex flex-wrap items-center justify-between gap-4 px-5 py-4">
                <div><p className="font-semibold text-slate-950">{keyword.negative ? '− ' : ''}{keyword.keywordText}</p><p className="mt-1 text-xs text-slate-500">{keyword.matchType ?? 'Keyword'} · CPC {formatMicros(keyword.bidAmountMicros, account?.currency)}{keyword.qualityScore != null ? ` · Quality ${keyword.qualityScore}/10` : ''}</p></div>
                <div className="flex items-center gap-2"><span className="rounded-full bg-slate-100 px-2.5 py-1 text-xs font-semibold text-slate-600">{keyword.providerStatus ?? keyword.status}</span>{can(PERMISSIONS.ADS_MODIFY) && !keyword.negative && <button className="secondary-button" disabled={workingId !== null} onClick={() => void setKeywordStatus(keyword, keyword.status === 'ACTIVE' ? 'PAUSED' : 'ACTIVE')}>{keyword.status === 'ACTIVE' ? 'Pause' : 'Enable'}</button>}</div>
              </div>)}
            </div>
          )}
        </div>

        <div className="panel mt-5 overflow-hidden">
          <div className="flex flex-wrap items-center justify-between gap-3 border-b border-slate-100 px-5 py-4">
            <div><h2 className="font-bold text-slate-950">Responsive search ads</h2><p className="mt-1 text-xs text-slate-500">Headlines, descriptions, final URLs, and delivery status.</p></div>
            <div className="flex gap-2">
              {can(PERMISSIONS.ADS_MODIFY) && <button className="secondary-button inline-flex items-center gap-2" disabled={syncing} onClick={() => void syncAds()}><FiRefreshCw />{syncing ? 'Syncing…' : 'Sync ads'}</button>}
              {can(PERMISSIONS.ADS_CREATE) && <button className="primary-button inline-flex items-center gap-2" onClick={() => setAdOpen(true)}><FiPlus />Create ad</button>}
            </div>
          </div>
          {ads.length === 0 ? <div className="p-8 text-center text-sm text-slate-500">No ads stored yet.</div> : (
            <div className="divide-y divide-slate-100">
              {ads.map((ad) => <div key={ad.id} className="flex flex-wrap items-center justify-between gap-4 px-5 py-4">
                <div><Link to={`/ad-center/ads/${ad.id}`} className="font-semibold text-slate-950 hover:text-indigo-600">{ad.name}</Link><p className="mt-1 text-xs text-slate-500">Google ad {ad.externalAdId}</p></div>
                <div className="flex items-center gap-2"><span className="rounded-full bg-slate-100 px-2.5 py-1 text-xs font-semibold text-slate-600">{ad.providerStatus ?? ad.status}</span>{can(PERMISSIONS.ADS_MODIFY) && <button className="secondary-button" disabled={workingId !== null} onClick={() => void setAdStatus(ad, ad.status === 'ACTIVE' ? 'PAUSED' : 'ACTIVE')}>{ad.status === 'ACTIVE' ? 'Pause' : 'Enable'}</button>}</div>
              </div>)}
            </div>
          )}
        </div>
      </div>

      {account && <GoogleKeywordModal account={account} open={keywordOpen} saving={creatingKeyword} onClose={() => setKeywordOpen(false)} onCreate={createKeyword} />}
      <GoogleResponsiveSearchAdModal open={adOpen} saving={creatingAd} onClose={() => setAdOpen(false)} onCreate={createAd} />
    </section>
  )
}
