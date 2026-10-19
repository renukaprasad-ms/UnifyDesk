import { useSnackbarState } from '../feedback/useSnackbarState'
import { useEffect, useMemo, useState, type FormEvent } from 'react'
import { FiCheck, FiLink, FiPlus, FiRefreshCw, FiX } from 'react-icons/fi'
import { adCenterApi } from '../../features/ads/adCenterApi'
import type {
  CreateGoogleReusableAssetInput,
  GoogleAssetLinkLevel,
  GoogleReusableAsset,
  GoogleReusableAssetType,
  GoogleUrlCustomParameter,
} from '../../features/ads/types'
import { apiErrorMessage } from '../../lib/api/apiClient'
import GoogleAdvancedAssetForm from './GoogleAdvancedAssetForm'
import { CardSkeleton } from '../ui/Skeleton'

type Props = {
  companyId: string
  accountId: string
  campaignId?: string | null
  adGroupId?: string | null
  canCreate: boolean
  canModify: boolean
}

const pretty = (value: string) =>
  value
    .replaceAll('_', ' ')
    .toLowerCase()
    .replace(/\b\w/g, (character) => character.toUpperCase())

const lines = (value: string) =>
  value
    .split(/\r?\n/)
    .map((item) => item.trim())
    .filter(Boolean)

const parseParameters = (value: string): GoogleUrlCustomParameter[] => {
  if (!value.trim()) return []
  return lines(value).map((line) => {
    const separator = line.indexOf('=')
    if (separator <= 0) {
      throw new Error('Custom parameters must use key=value, one per line.')
    }
    return {
      key: line.slice(0, separator).trim(),
      value: line.slice(separator + 1).trim(),
    }
  })
}

const formatParameters = (values: GoogleUrlCustomParameter[]) =>
  values.map((item) => `${item.key}=${item.value}`).join('\n')

const fileToBase64 = (file: File) =>
  new Promise<string>((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(String(reader.result ?? ''))
    reader.onerror = () => reject(new Error('Unable to read image file'))
    reader.readAsDataURL(file)
  })

const defaultLinkRole = (asset: GoogleReusableAsset, level: GoogleAssetLinkLevel) => {
  if (asset.type === 'TEXT') return 'BUSINESS_NAME'
  if (asset.type === 'IMAGE') return level === 'CUSTOMER' ? 'BUSINESS_LOGO' : 'AD_IMAGE'
  return asset.type
}

const roleAllowed = (role: string, level: GoogleAssetLinkLevel) => {
  if (role === 'LEAD_FORM') return level === 'CAMPAIGN'
  if (role === 'BUSINESS_NAME') return level !== 'AD_GROUP'
  if (role === 'BUSINESS_LOGO') return level === 'CAMPAIGN'
  if (role === 'AD_IMAGE') return level !== 'CUSTOMER'
  return true
}

export default function GoogleReusableAssetsPanel(props: Props) {
  const levels = useMemo(() => {
    const values: Array<{ level: GoogleAssetLinkLevel; label: string; targetId?: string }> = [
      { level: 'CUSTOMER', label: 'Account defaults' },
    ]
    if (props.campaignId) {
      values.push({
        level: 'CAMPAIGN',
        label: 'Campaign',
        targetId: props.campaignId,
      })
    }
    if (props.adGroupId) {
      values.push({
        level: 'AD_GROUP',
        label: 'Ad group',
        targetId: props.adGroupId,
      })
    }
    return values
  }, [props.campaignId, props.adGroupId])

  const [level, setLevel] = useState<GoogleAssetLinkLevel>(
    props.adGroupId ? 'AD_GROUP' : props.campaignId ? 'CAMPAIGN' : 'CUSTOMER',
  )
  const [assets, setAssets] = useState<GoogleReusableAsset[]>([])
  const [linkedIds, setLinkedIds] = useState<Set<string>>(new Set())
  const [loading, setLoading] = useState(true)
  const [workingId, setWorkingId] = useState<string | null>(null)
  const [creating, setCreating] = useState(false)
  const [savingUrls, setSavingUrls] = useState(false)
  const [, setError] = useSnackbarState('', 'error')
  const [, setNotice] = useSnackbarState('', 'success')

  const [assetType, setAssetType] = useState<GoogleReusableAssetType>('SITELINK')
  const [assetName, setAssetName] = useState('')
  const [linkText, setLinkText] = useState('')
  const [description1, setDescription1] = useState('')
  const [description2, setDescription2] = useState('')
  const [calloutText, setCalloutText] = useState('')
  const [snippetHeader, setSnippetHeader] = useState('')
  const [snippetValues, setSnippetValues] = useState('')
  const [finalUrl, setFinalUrl] = useState('')
  const [assetTrackingTemplate, setAssetTrackingTemplate] = useState('')
  const [assetFinalUrlSuffix, setAssetFinalUrlSuffix] = useState('')
  const [assetCustomParameters, setAssetCustomParameters] = useState('')
  const [identityText, setIdentityText] = useState('')
  const [imageBase64, setImageBase64] = useState('')
  const [linkRoles, setLinkRoles] = useState<Record<string, string>>({})

  const [trackingTemplate, setTrackingTemplate] = useState('')
  const [finalUrlSuffix, setFinalUrlSuffix] = useState('')
  const [customParameters, setCustomParameters] = useState('')

  const current = levels.find((item) => item.level === level) ?? levels[0]
  const targetId = current?.targetId ?? null

  const load = async () => {
    if (!current) return
    setLoading(true)
    setError('')
    try {
      const [library, links, urls] = await Promise.all([
        adCenterApi.googleReusableAssets(props.companyId, props.accountId),
        adCenterApi.googleReusableAssetLinks(
          props.companyId,
          props.accountId,
          current.level,
          current.targetId ?? null,
        ),
        adCenterApi.googleUrlOptions(
          props.companyId,
          props.accountId,
          current.level,
          current.targetId ?? null,
        ),
      ])
      setAssets(library)
      setLinkedIds(new Set(links.map((item) => item.assetId)))
      setTrackingTemplate(urls.trackingUrlTemplate ?? '')
      setFinalUrlSuffix(urls.finalUrlSuffix ?? '')
      setCustomParameters(formatParameters(urls.customParameters))
    } catch (loadError) {
      setError(apiErrorMessage(loadError, 'Unable to load Google assets'))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    void load()
  }, [props.companyId, props.accountId, level, targetId])

  const createAsset = async (event: FormEvent) => {
    event.preventDefault()
    setCreating(true)
    setError('')
    setNotice('')
    try {
      const input: CreateGoogleReusableAssetInput = {
        type: assetType,
        name: assetName.trim() || null,
        ...(assetType === 'SITELINK'
          ? {
              linkText: linkText.trim(),
              description1: description1.trim() || null,
              description2: description2.trim() || null,
              finalUrl: finalUrl.trim(),
              trackingUrlTemplate: assetTrackingTemplate.trim() || null,
              finalUrlSuffix: assetFinalUrlSuffix.trim() || null,
              customParameters: parseParameters(assetCustomParameters),
            }
          : {}),
        ...(assetType === 'CALLOUT' ? { calloutText: calloutText.trim() } : {}),
        ...(assetType === 'STRUCTURED_SNIPPET'
          ? {
              structuredSnippetHeader: snippetHeader.trim(),
              structuredSnippetValues: lines(snippetValues),
            }
          : {}),
        ...(assetType === 'BUSINESS_NAME' ? { text: identityText.trim() } : {}),
        ...(assetType === 'AD_IMAGE' || assetType === 'BUSINESS_LOGO' ? { imageBase64 } : {}),
      }
      const created = await adCenterApi.createGoogleReusableAsset(
        props.companyId,
        props.accountId,
        input,
      )
      setAssets((currentAssets) => [
        created,
        ...currentAssets.filter((item) => item.id !== created.id),
      ])
      setAssetName('')
      setLinkText('')
      setDescription1('')
      setDescription2('')
      setCalloutText('')
      setSnippetHeader('')
      setSnippetValues('')
      setFinalUrl('')
      setAssetTrackingTemplate('')
      setAssetFinalUrlSuffix('')
      setAssetCustomParameters('')
      setIdentityText('')
      setImageBase64('')
      setNotice(`${pretty(assetType)} created in the Google asset library.`)
    } catch (createError) {
      setError(apiErrorMessage(createError, 'Unable to create Google asset'))
    } finally {
      setCreating(false)
    }
  }

  const toggleLink = async (asset: GoogleReusableAsset) => {
    if (!current) return
    const fieldType = linkRoles[asset.id] ?? defaultLinkRole(asset, current.level)
    if (!roleAllowed(fieldType, current.level)) {
      setError(`${pretty(fieldType)} is not available at ${current.label.toLowerCase()} scope.`)
      return
    }
    setWorkingId(asset.id)
    setError('')
    setNotice('')
    try {
      if (linkedIds.has(asset.id)) {
        await adCenterApi.detachGoogleReusableAsset(
          props.companyId,
          props.accountId,
          asset.id,
          current.level,
          current.targetId ?? null,
          fieldType,
        )
        setLinkedIds((ids) => {
          const next = new Set(ids)
          next.delete(asset.id)
          return next
        })
        setNotice(`${assetLabel(asset)} detached from ${current.label.toLowerCase()}.`)
      } else {
        await adCenterApi.attachGoogleReusableAsset(
          props.companyId,
          props.accountId,
          asset.id,
          current.level,
          current.targetId ?? null,
          fieldType,
        )
        setLinkedIds((ids) => new Set([...ids, asset.id]))
        setNotice(`${assetLabel(asset)} attached to ${current.label.toLowerCase()}.`)
      }
    } catch (linkError) {
      setError(apiErrorMessage(linkError, 'Unable to update Google asset association'))
    } finally {
      setWorkingId(null)
    }
  }

  const saveUrlOptions = async (event: FormEvent) => {
    event.preventDefault()
    if (!current) return
    setSavingUrls(true)
    setError('')
    setNotice('')
    try {
      const updated = await adCenterApi.updateGoogleUrlOptions(
        props.companyId,
        props.accountId,
        current.level,
        current.targetId ?? null,
        {
          trackingUrlTemplate: trackingTemplate,
          finalUrlSuffix,
          ...(current.level === 'CUSTOMER'
            ? {}
            : { customParameters: parseParameters(customParameters) }),
        },
      )
      setTrackingTemplate(updated.trackingUrlTemplate ?? '')
      setFinalUrlSuffix(updated.finalUrlSuffix ?? '')
      setCustomParameters(formatParameters(updated.customParameters))
      setNotice(`${current.label} URL options saved to Google Ads.`)
    } catch (saveError) {
      setError(apiErrorMessage(saveError, 'Unable to update Google URL options'))
    } finally {
      setSavingUrls(false)
    }
  }

  return (
    <section className="panel mt-5 overflow-hidden">
      <div className="flex flex-wrap items-start justify-between gap-3 border-b border-slate-100 px-5 py-4">
        <div>
          <h2 className="text-base font-bold text-slate-950">Google assets & URL options</h2>
          <p className="mt-1 max-w-3xl text-xs leading-5 text-slate-500">
            Create reusable Google assets once, then attach them at account, campaign, or ad-group
            level. Lower-level URL tracking settings override higher-level defaults.
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

      <div className="flex flex-wrap gap-2 border-b border-slate-100 px-5 py-3">
        {levels.map((item) => (
          <button
            key={item.level}
            type="button"
            className={
              'rounded-full px-3 py-1.5 text-xs font-semibold ' +
              (level === item.level
                ? 'bg-slate-900 text-white'
                : 'bg-slate-100 text-slate-600 hover:bg-slate-200')
            }
            onClick={() => setLevel(item.level)}
          >
            {item.label}
          </button>
        ))}
      </div>



      <div className="grid gap-5 p-5 xl:grid-cols-[1.2fr_0.8fr]">
        <div>
          <div className="flex items-center justify-between gap-3">
            <div>
              <h3 className="text-sm font-bold text-slate-900">Reusable asset library</h3>
              <p className="mt-1 text-xs text-slate-500">
                Advertiser-created sitelinks, callouts, structured snippets, calls, prices,
                promotions, lead forms, reusable ad images, and business identity assets.
              </p>
            </div>
            <span className="rounded-full bg-slate-100 px-2.5 py-1 text-[11px] font-semibold text-slate-500">
              {linkedIds.size} attached here
            </span>
          </div>

          {loading ? (
            <div className="mt-4 grid gap-3 md:grid-cols-2"><CardSkeleton rows={3} /><CardSkeleton rows={3} /></div>
          ) : assets.length === 0 ? (
            <div className="mt-4 rounded-xl bg-slate-50 p-6 text-center text-sm text-slate-500">
              No reusable advertiser assets yet. Create the first one below.
            </div>
          ) : (
            <div className="mt-4 grid gap-2">
              {assets.map((asset) => {
                const attached = linkedIds.has(asset.id)
                return (
                  <div
                    key={asset.id}
                    className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-slate-200 px-3 py-3"
                  >
                    <div className="min-w-0">
                      <div className="flex flex-wrap items-center gap-2">
                        <p className="truncate text-sm font-semibold text-slate-900">
                          {assetLabel(asset)}
                        </p>
                        <span className="rounded-full bg-slate-100 px-2 py-0.5 text-[10px] font-bold text-slate-500">
                          {pretty(asset.type)}
                        </span>
                        {attached && (
                          <span className="inline-flex items-center gap-1 rounded-full bg-emerald-50 px-2 py-0.5 text-[10px] font-bold text-emerald-700">
                            <FiCheck aria-hidden="true" />
                            Attached
                          </span>
                        )}
                      </div>
                      <p className="mt-1 truncate text-[11px] text-slate-400">
                        {assetSecondary(asset)}
                      </p>
                    </div>
                    {(asset.type === 'TEXT' || asset.type === 'IMAGE') && current && (
                      <select
                        className="form-input min-w-36 py-2 text-xs"
                        value={linkRoles[asset.id] ?? defaultLinkRole(asset, current.level)}
                        onChange={(event) =>
                          setLinkRoles((roles) => ({
                            ...roles,
                            [asset.id]: event.target.value,
                          }))
                        }
                      >
                        {asset.type === 'TEXT' && current.level !== 'AD_GROUP' && (
                          <option value="BUSINESS_NAME">Business name</option>
                        )}
                        {asset.type === 'IMAGE' && current.level !== 'CUSTOMER' && (
                          <option value="AD_IMAGE">Ad image</option>
                        )}
                        {asset.type === 'IMAGE' && current.level === 'CAMPAIGN' && (
                          <option value="BUSINESS_LOGO">Business logo</option>
                        )}
                      </select>
                    )}
                    {props.canModify &&
                      roleAllowed(
                        linkRoles[asset.id] ?? defaultLinkRole(asset, current?.level ?? 'CUSTOMER'),
                        current?.level ?? 'CUSTOMER',
                      ) && (
                        <button
                          type="button"
                          className="secondary-button inline-flex items-center gap-2"
                          disabled={workingId !== null}
                          onClick={() => void toggleLink(asset)}
                        >
                          {attached ? <FiX aria-hidden="true" /> : <FiLink aria-hidden="true" />}
                          {workingId === asset.id ? 'Updating…' : attached ? 'Detach' : 'Attach'}
                        </button>
                      )}
                    {!roleAllowed(
                      linkRoles[asset.id] ?? defaultLinkRole(asset, current?.level ?? 'CUSTOMER'),
                      current?.level ?? 'CUSTOMER',
                    ) && (
                      <span className="rounded-full bg-amber-50 px-2.5 py-1 text-[10px] font-bold text-amber-700">
                        Not eligible at this scope
                      </span>
                    )}
                  </div>
                )
              })}
            </div>
          )}

          {props.canCreate && (
            <form
              className="mt-5 rounded-xl border border-slate-200 bg-slate-50 p-4"
              onSubmit={createAsset}
            >
              <div className="flex items-center gap-2">
                <FiPlus className="text-indigo-600" aria-hidden="true" />
                <h4 className="text-sm font-bold text-slate-900">Create reusable asset</h4>
              </div>

              <div className="mt-4 grid gap-3 sm:grid-cols-2">
                <label className="text-xs font-semibold text-slate-700">
                  Asset type
                  <select
                    className="form-input mt-2"
                    value={assetType}
                    onChange={(event) =>
                      setAssetType(event.target.value as GoogleReusableAssetType)
                    }
                  >
                    <option value="SITELINK">Sitelink</option>
                    <option value="CALLOUT">Callout</option>
                    <option value="STRUCTURED_SNIPPET">Structured snippet</option>
                    <option value="BUSINESS_NAME">Business name</option>
                    <option value="AD_IMAGE">Ad image</option>
                    <option value="BUSINESS_LOGO">Business logo</option>
                  </select>
                </label>
                <label className="text-xs font-semibold text-slate-700">
                  Internal name
                  <input
                    className="form-input mt-2"
                    value={assetName}
                    maxLength={128}
                    onChange={(event) => setAssetName(event.target.value)}
                    placeholder="Optional management label"
                  />
                </label>

                {assetType === 'SITELINK' && (
                  <>
                    <label className="text-xs font-semibold text-slate-700">
                      Link text
                      <input
                        className="form-input mt-2"
                        value={linkText}
                        maxLength={25}
                        required
                        onChange={(event) => setLinkText(event.target.value)}
                      />
                    </label>
                    <label className="text-xs font-semibold text-slate-700">
                      Final URL
                      <input
                        className="form-input mt-2"
                        value={finalUrl}
                        required
                        onChange={(event) => setFinalUrl(event.target.value)}
                        placeholder="https://example.com/pricing"
                      />
                    </label>
                    <label className="text-xs font-semibold text-slate-700">
                      Description 1
                      <input
                        className="form-input mt-2"
                        value={description1}
                        maxLength={35}
                        onChange={(event) => setDescription1(event.target.value)}
                      />
                    </label>
                    <label className="text-xs font-semibold text-slate-700">
                      Description 2
                      <input
                        className="form-input mt-2"
                        value={description2}
                        maxLength={35}
                        onChange={(event) => setDescription2(event.target.value)}
                      />
                    </label>
                    <label className="text-xs font-semibold text-slate-700 sm:col-span-2">
                      Asset tracking template
                      <input
                        className="form-input mt-2"
                        value={assetTrackingTemplate}
                        onChange={(event) => setAssetTrackingTemplate(event.target.value)}
                        placeholder="{lpurl}?source={_source}"
                      />
                    </label>
                    <label className="text-xs font-semibold text-slate-700">
                      Asset final URL suffix
                      <input
                        className="form-input mt-2"
                        value={assetFinalUrlSuffix}
                        onChange={(event) => setAssetFinalUrlSuffix(event.target.value)}
                        placeholder="utm_source=google"
                      />
                    </label>
                    <label className="text-xs font-semibold text-slate-700">
                      Asset custom parameters
                      <textarea
                        className="form-input mt-2 min-h-20"
                        value={assetCustomParameters}
                        onChange={(event) => setAssetCustomParameters(event.target.value)}
                        placeholder={'source=search\npromo=spring'}
                      />
                    </label>
                  </>
                )}

                {assetType === 'BUSINESS_NAME' && (
                  <label className="text-xs font-semibold text-slate-700 sm:col-span-2">
                    Business name
                    <input
                      className="form-input mt-2"
                      value={identityText}
                      maxLength={25}
                      required
                      onChange={(event) => setIdentityText(event.target.value)}
                      placeholder="Your advertised business name"
                    />
                  </label>
                )}

                {(assetType === 'AD_IMAGE' || assetType === 'BUSINESS_LOGO') && (
                  <label className="text-xs font-semibold text-slate-700 sm:col-span-2">
                    {assetType === 'BUSINESS_LOGO' ? 'Business logo' : 'Ad image'}
                    <input
                      className="form-input mt-2"
                      type="file"
                      accept="image/png,image/jpeg,image/gif"
                      required
                      onChange={(event) => {
                        const file = event.target.files?.[0]
                        if (!file) {
                          setImageBase64('')
                          return
                        }
                        void fileToBase64(file)
                          .then(setImageBase64)
                          .catch((fileError) =>
                            setError(
                              fileError instanceof Error
                                ? fileError.message
                                : 'Unable to read image file',
                            ),
                          )
                      }}
                    />
                    <span className="mt-1 block text-[11px] font-normal text-slate-500">
                      Images are validated before Google mutation. Business logos require a square
                      logo.
                    </span>
                  </label>
                )}

                {assetType === 'CALLOUT' && (
                  <label className="text-xs font-semibold text-slate-700 sm:col-span-2">
                    Callout text
                    <input
                      className="form-input mt-2"
                      value={calloutText}
                      maxLength={25}
                      required
                      onChange={(event) => setCalloutText(event.target.value)}
                      placeholder="24/7 support"
                    />
                  </label>
                )}

                {assetType === 'STRUCTURED_SNIPPET' && (
                  <>
                    <label className="text-xs font-semibold text-slate-700">
                      Header
                      <input
                        className="form-input mt-2"
                        value={snippetHeader}
                        required
                        onChange={(event) => setSnippetHeader(event.target.value)}
                        placeholder="Services"
                      />
                    </label>
                    <label className="text-xs font-semibold text-slate-700">
                      Values
                      <textarea
                        className="form-input mt-2 min-h-24"
                        value={snippetValues}
                        required
                        onChange={(event) => setSnippetValues(event.target.value)}
                        placeholder={'Email\nWhatsApp\nLive chat'}
                      />
                      <span className="mt-1 block text-[10px] font-normal text-slate-400">
                        3–10 values, one per line.
                      </span>
                    </label>
                  </>
                )}
              </div>

              <div className="mt-4 flex justify-end">
                <button
                  type="submit"
                  className="primary-button inline-flex items-center gap-2"
                  disabled={creating}
                >
                  <FiPlus aria-hidden="true" />
                  {creating ? 'Creating…' : 'Create asset'}
                </button>
              </div>
            </form>
          )}

          {props.canCreate && (
            <GoogleAdvancedAssetForm
              companyId={props.companyId}
              accountId={props.accountId}
              onCreated={(created) => {
                setAssets((currentAssets) => [
                  created,
                  ...currentAssets.filter((item) => item.id !== created.id),
                ])
                setNotice(`${pretty(created.type)} created in the Google asset library.`)
              }}
            />
          )}
        </div>

        <form className="rounded-xl border border-slate-200 p-4" onSubmit={saveUrlOptions}>
          <h3 className="text-sm font-bold text-slate-900">{current?.label} URL options</h3>
          <p className="mt-1 text-xs leading-5 text-slate-500">
            Tracking template and final URL suffix inherit downward unless overridden at a lower
            level.
          </p>

          <label className="mt-4 block text-xs font-semibold text-slate-700">
            Tracking template
            <input
              className="form-input mt-2"
              value={trackingTemplate}
              disabled={!props.canModify}
              onChange={(event) => setTrackingTemplate(event.target.value)}
              placeholder="{lpurl}?utm_source=google"
            />
          </label>
          <label className="mt-3 block text-xs font-semibold text-slate-700">
            Final URL suffix
            <input
              className="form-input mt-2"
              value={finalUrlSuffix}
              disabled={!props.canModify}
              onChange={(event) => setFinalUrlSuffix(event.target.value)}
              placeholder="utm_campaign={campaignid}"
            />
          </label>

          {current?.level !== 'CUSTOMER' && (
            <label className="mt-3 block text-xs font-semibold text-slate-700">
              Custom parameters
              <textarea
                className="form-input mt-2 min-h-24"
                value={customParameters}
                disabled={!props.canModify}
                onChange={(event) => setCustomParameters(event.target.value)}
                placeholder={'channel=search\nsegment=brand'}
              />
              <span className="mt-1 block text-[10px] font-normal text-slate-400">
                Up to 8 key=value pairs. Use them as {'{_key}'} in tracking URLs.
              </span>
            </label>
          )}

          {props.canModify && (
            <div className="mt-4 flex justify-end">
              <button type="submit" className="primary-button" disabled={savingUrls}>
                {savingUrls ? 'Saving…' : 'Save URL options'}
              </button>
            </div>
          )}
        </form>
      </div>
    </section>
  )
}

function assetLabel(asset: GoogleReusableAsset) {
  if (asset.type === 'SITELINK') return asset.linkText || asset.name || 'Sitelink'
  if (asset.type === 'CALLOUT') return asset.calloutText || asset.name || 'Callout'
  if (asset.type === 'STRUCTURED_SNIPPET') {
    return asset.structuredSnippetHeader || asset.name || 'Structured snippet'
  }
  return asset.name || pretty(asset.type)
}

function assetSecondary(asset: GoogleReusableAsset) {
  if (asset.type === 'SITELINK') return asset.finalUrls[0] || 'No final URL'
  if (asset.type === 'STRUCTURED_SNIPPET') {
    return asset.structuredSnippetValues.join(' · ') || 'No values'
  }
  return asset.name || 'Reusable Google asset'
}
