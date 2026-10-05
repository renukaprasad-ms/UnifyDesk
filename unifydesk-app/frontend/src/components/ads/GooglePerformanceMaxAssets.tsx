import { useEffect, useMemo, useState, type ChangeEvent } from 'react'
import { FiImage, FiLink2, FiPlus, FiRefreshCw, FiTrash2, FiVideo } from 'react-icons/fi'
import { adCenterApi } from '../../features/ads/adCenterApi'
import type {
  GoogleAsset,
  GoogleAssetGroup,
  GoogleAssetGroupAsset,
  GoogleAssetGroupPerformance,
  GoogleAssetGroupAssetInput,
  GoogleUrlCustomParameter,
} from '../../features/ads/types'
import { apiErrorMessage } from '../../lib/api/apiClient'
import GooglePerformanceMaxListingGroups from './GooglePerformanceMaxListingGroups'
import { ListSkeleton } from '../ui/Skeleton'

type FieldType = GoogleAssetGroupAssetInput['fieldType']
type SourceMode = 'NEW' | 'EXISTING'

type Props = {
  companyId: string
  campaignId: string
  accountId: string
  assetGroup: GoogleAssetGroup
  canModify: boolean
}

const FIELD_OPTIONS: Array<{
  value: FieldType
  label: string
  source: 'TEXT' | 'IMAGE' | 'YOUTUBE_VIDEO'
}> = [
  { value: 'HEADLINE', label: 'Headline', source: 'TEXT' },
  { value: 'LONG_HEADLINE', label: 'Long headline', source: 'TEXT' },
  { value: 'DESCRIPTION', label: 'Description', source: 'TEXT' },
  { value: 'BUSINESS_NAME', label: 'Business name', source: 'TEXT' },
  { value: 'MARKETING_IMAGE', label: 'Landscape marketing image', source: 'IMAGE' },
  { value: 'SQUARE_MARKETING_IMAGE', label: 'Square marketing image', source: 'IMAGE' },
  { value: 'PORTRAIT_MARKETING_IMAGE', label: 'Portrait marketing image', source: 'IMAGE' },
  { value: 'LOGO', label: 'Square logo', source: 'IMAGE' },
  { value: 'LANDSCAPE_LOGO', label: 'Landscape logo', source: 'IMAGE' },
  { value: 'YOUTUBE_VIDEO', label: 'YouTube video', source: 'YOUTUBE_VIDEO' },
]

const FIELD_HINTS: Partial<Record<FieldType, string>> = {
  HEADLINE: 'Up to 30 characters · 3–15 per Asset Group',
  LONG_HEADLINE: 'Up to 90 characters · 1–5 per Asset Group',
  DESCRIPTION: 'Up to 90 characters · 2–5 per Asset Group',
  BUSINESS_NAME: 'Up to 25 characters · one when brand guidelines are disabled',
  MARKETING_IMAGE: '1.91:1 · minimum 600×314 · up to 20',
  SQUARE_MARKETING_IMAGE: '1:1 · minimum 300×300 · up to 20',
  PORTRAIT_MARKETING_IMAGE: '4:5 · minimum 480×600 · optional · up to 20',
  LOGO: '1:1 · minimum 128×128',
  LANDSCAPE_LOGO: '4:1 · minimum 512×128 · optional',
  YOUTUBE_VIDEO: 'Optional · YouTube video ID',
}

const formatLabel = (value?: string | null) =>
  value
    ? value
        .replaceAll('_', ' ')
        .toLowerCase()
        .replace(/\b\w/g, (character) => character.toUpperCase())
    : 'Unknown'

const parseParameters = (value: string): GoogleUrlCustomParameter[] =>
  value
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter(Boolean)
    .map((line) => {
      const separator = line.indexOf('=')
      if (separator <= 0) {
        throw new Error('Asset Group custom parameters must use key=value, one per line.')
      }
      return {
        key: line.slice(0, separator).trim(),
        value: line.slice(separator + 1).trim(),
      }
    })

const formatParameters = (values: GoogleUrlCustomParameter[]) =>
  values.map((item) => `${item.key}=${item.value}`).join('\n')

const fileToDataUrl = (file: File) =>
  new Promise<string>((resolve, reject) => {
    const reader = new FileReader()
    reader.onerror = () => reject(new Error('Unable to read image file'))
    reader.onload = () =>
      typeof reader.result === 'string'
        ? resolve(reader.result)
        : reject(new Error('Unable to read image file'))
    reader.readAsDataURL(file)
  })

export default function GooglePerformanceMaxAssets({
  companyId,
  campaignId,
  accountId,
  assetGroup,
  canModify,
}: Props) {
  const [linkedAssets, setLinkedAssets] = useState<GoogleAssetGroupAsset[]>([])
  const [performance, setPerformance] = useState<GoogleAssetGroupPerformance | null>(null)
  const [libraryAssets, setLibraryAssets] = useState<GoogleAsset[]>([])
  const [fieldType, setFieldType] = useState<FieldType>('HEADLINE')
  const [sourceMode, setSourceMode] = useState<SourceMode>('NEW')
  const [existingAssetResourceName, setExistingAssetResourceName] = useState('')
  const [assetName, setAssetName] = useState('')
  const [textValue, setTextValue] = useState('')
  const [imageBase64, setImageBase64] = useState('')
  const [youtubeVideoId, setYoutubeVideoId] = useState('')
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [removing, setRemoving] = useState<string | null>(null)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [trackingTemplate, setTrackingTemplate] = useState('')
  const [finalUrlSuffix, setFinalUrlSuffix] = useState('')
  const [customParameters, setCustomParameters] = useState('')
  const [savingUrls, setSavingUrls] = useState(false)

  const field = FIELD_OPTIONS.find((option) => option.value === fieldType)!

  const compatibleLibraryAssets = useMemo(() => {
    return libraryAssets.filter((asset) => {
      if (field.source === 'TEXT') return asset.type === 'TEXT'
      if (field.source === 'IMAGE') return asset.type === 'IMAGE'
      return asset.type === 'YOUTUBE_VIDEO'
    })
  }, [libraryAssets, field.source])

  const groupedAssets = useMemo(() => {
    const result = new Map<string, GoogleAssetGroupAsset[]>()
    for (const asset of linkedAssets) {
      const key = asset.fieldType || 'OTHER'
      result.set(key, [...(result.get(key) ?? []), asset])
    }
    return [...result.entries()]
  }, [linkedAssets])

  const load = async () => {
    if (!assetGroup.id) return
    setLoading(true)
    setError('')
    try {
      const [linked, library, urlOptions, performanceRows] = await Promise.all([
        adCenterApi.googleAssetGroupAssets(companyId, campaignId, assetGroup.id),
        adCenterApi.googleAssets(companyId, accountId),
        adCenterApi.googleAssetGroupUrlOptions(companyId, campaignId, assetGroup.id),
        adCenterApi.googleAssetGroupPerformance(companyId, campaignId, 30),
      ])
      setLinkedAssets(linked)
      setLibraryAssets(library)
      setPerformance(performanceRows.find((row) => row.id === assetGroup.id) ?? null)
      setTrackingTemplate(urlOptions.trackingUrlTemplate ?? '')
      setFinalUrlSuffix(urlOptions.finalUrlSuffix ?? '')
      setCustomParameters(formatParameters(urlOptions.customParameters))
    } catch (loadError) {
      setError(apiErrorMessage(loadError, 'Unable to load Performance Max assets'))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    void load()
  }, [companyId, campaignId, accountId, assetGroup.id])

  useEffect(() => {
    setExistingAssetResourceName('')
    setTextValue('')
    setImageBase64('')
    setYoutubeVideoId('')
    setError('')
  }, [fieldType, sourceMode])

  const readImage = async (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0]
    if (!file) return

    if (file.size > 5 * 1024 * 1024) {
      setError('Google image assets must be 5 MB or smaller.')
      event.target.value = ''
      return
    }
    if (!['image/jpeg', 'image/png', 'image/gif'].includes(file.type)) {
      setError('Use a PNG, JPG, or GIF image.')
      event.target.value = ''
      return
    }

    try {
      setImageBase64(await fileToDataUrl(file))
      setError('')
    } catch (readError) {
      setError(readError instanceof Error ? readError.message : 'Unable to read image')
    }
  }

  const add = async () => {
    if (!assetGroup.id) return

    const input: GoogleAssetGroupAssetInput = {
      fieldType,
      ...(sourceMode === 'EXISTING'
        ? { existingAssetResourceName }
        : {
            name: assetName.trim() || null,
            ...(field.source === 'TEXT'
              ? { text: textValue.trim() }
              : field.source === 'IMAGE'
                ? { imageBase64 }
                : { youtubeVideoId: youtubeVideoId.trim() }),
          }),
    }

    setSaving(true)
    setError('')
    setNotice('')
    try {
      const updated = await adCenterApi.addGoogleAssetGroupAsset(
        companyId,
        campaignId,
        assetGroup.id,
        input,
      )
      setLinkedAssets(updated)
      setExistingAssetResourceName('')
      setAssetName('')
      setTextValue('')
      setImageBase64('')
      setYoutubeVideoId('')
      setNotice(
        sourceMode === 'EXISTING'
          ? 'Existing Google asset linked to the Asset Group.'
          : 'New Google asset created and linked to the Asset Group.',
      )
      setLibraryAssets(await adCenterApi.googleAssets(companyId, accountId))
    } catch (saveError) {
      setError(apiErrorMessage(saveError, 'Unable to add Performance Max asset'))
    } finally {
      setSaving(false)
    }
  }

  const remove = async (asset: GoogleAssetGroupAsset) => {
    if (!assetGroup.id || !asset.resourceName) return
    setRemoving(asset.resourceName)
    setError('')
    setNotice('')
    try {
      const updated = await adCenterApi.removeGoogleAssetGroupAsset(
        companyId,
        campaignId,
        assetGroup.id,
        asset.resourceName,
      )
      setLinkedAssets(updated)
      setNotice('Asset unlinked from the Performance Max Asset Group.')
    } catch (removeError) {
      setError(apiErrorMessage(removeError, 'Unable to remove Performance Max asset'))
    } finally {
      setRemoving(null)
    }
  }

  const saveUrlOptions = async () => {
    if (!assetGroup.id) return
    setSavingUrls(true)
    setError('')
    setNotice('')
    try {
      const updated = await adCenterApi.updateGoogleAssetGroupUrlOptions(
        companyId,
        campaignId,
        assetGroup.id,
        {
          trackingUrlTemplate: trackingTemplate,
          finalUrlSuffix,
          customParameters: parseParameters(customParameters),
        },
      )
      setTrackingTemplate(updated.trackingUrlTemplate ?? '')
      setFinalUrlSuffix(updated.finalUrlSuffix ?? '')
      setCustomParameters(formatParameters(updated.customParameters))
      setNotice('Asset Group URL options saved to Google Ads.')
    } catch (saveError) {
      setError(apiErrorMessage(saveError, 'Unable to update Asset Group URL options'))
    } finally {
      setSavingUrls(false)
    }
  }

  const canSubmit =
    sourceMode === 'EXISTING'
      ? Boolean(existingAssetResourceName)
      : field.source === 'TEXT'
        ? Boolean(textValue.trim())
        : field.source === 'IMAGE'
          ? Boolean(imageBase64)
          : Boolean(youtubeVideoId.trim())

  return (
    <div className="border-t border-slate-100 px-5 py-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <p className="text-sm font-bold text-slate-900">Creative assets</p>
          <p className="mt-1 text-xs text-slate-500">
            Manage required and optional assets without recreating the Performance Max campaign.
          </p>
        </div>
        <button
          type="button"
          className="secondary-button inline-flex items-center gap-2 text-xs"
          disabled={loading}
          onClick={() => void load()}
        >
          <FiRefreshCw aria-hidden="true" />
          Refresh assets
        </button>
      </div>

      {(error || notice) && (
        <div
          className={`mt-3 rounded-lg border px-3 py-2 text-xs font-medium ${
            error
              ? 'border-red-200 bg-red-50 text-red-700'
              : 'border-emerald-200 bg-emerald-50 text-emerald-700'
          }`}
        >
          {error || notice}
        </div>
      )}

      {performance && (
        <div className="mt-4 rounded-xl border border-slate-200 bg-white p-4">
          <div className="flex flex-wrap items-start justify-between gap-3">
            <div>
              <p className="text-xs font-bold text-slate-800">Asset Group diagnostics</p>
              <p className="mt-1 text-[11px] leading-5 text-slate-500">
                Google provider status, asset strength, coverage, and 30-day performance.
              </p>
            </div>
            <div className="flex flex-wrap gap-2">
              <span className="rounded-full bg-slate-100 px-2.5 py-1 text-[10px] font-semibold text-slate-600">
                {formatLabel(performance.primaryStatus)}
              </span>
              <span className="rounded-full bg-indigo-50 px-2.5 py-1 text-[10px] font-semibold text-indigo-700">
                Ad strength: {formatLabel(performance.adStrength)}
              </span>
            </div>
          </div>

          {performance.primaryStatusReasons.length > 0 && (
            <div className="mt-3 rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-xs text-amber-800">
              {performance.primaryStatusReasons.map(formatLabel).join(' · ')}
            </div>
          )}

          <div className="mt-4 grid gap-3 sm:grid-cols-2 lg:grid-cols-5">
            {[
              ['Impressions', performance.impressions ?? 0],
              ['Clicks', performance.clicks ?? 0],
              ['Conversions', performance.conversions ?? 0],
              ['Conversion value', performance.conversionsValue ?? 0],
              [
                'Spend',
                ((performance.costMicros ?? 0) / 1_000_000).toLocaleString(undefined, {
                  maximumFractionDigits: 2,
                }),
              ],
            ].map(([label, value]) => (
              <div key={String(label)} className="rounded-lg bg-slate-50 px-3 py-2">
                <p className="text-[10px] font-semibold uppercase tracking-wide text-slate-400">
                  {label}
                </p>
                <p className="mt-1 text-sm font-bold text-slate-900">
                  {typeof value === 'number' ? value.toLocaleString() : value}
                </p>
              </div>
            ))}
          </div>

          {performance.assetCoverage && Object.keys(performance.assetCoverage).length > 0 && (
            <details className="mt-4 rounded-lg border border-slate-200 bg-slate-50 px-3 py-2">
              <summary className="cursor-pointer text-xs font-semibold text-slate-700">
                Asset coverage details
              </summary>
              <pre className="mt-2 overflow-x-auto whitespace-pre-wrap text-[10px] leading-5 text-slate-500">
                {JSON.stringify(performance.assetCoverage, null, 2)}
              </pre>
            </details>
          )}
        </div>
      )}

      <div className="mt-4 rounded-xl border border-slate-200 bg-slate-50 p-4">
        <div>
          <p className="text-xs font-bold text-slate-800">Asset Group URL options</p>
          <p className="mt-1 text-[11px] leading-5 text-slate-500">
            Configure v25 Asset Group tracking template, final URL suffix, and custom parameters.
          </p>
        </div>
        <div className="mt-3 grid gap-3 lg:grid-cols-2">
          <label className="text-xs font-semibold text-slate-700 lg:col-span-2">
            Tracking template
            <input
              className="form-input mt-2"
              value={trackingTemplate}
              disabled={!canModify || loading || savingUrls}
              onChange={(event) => setTrackingTemplate(event.target.value)}
              placeholder="{lpurl}?source=google"
            />
          </label>
          <label className="text-xs font-semibold text-slate-700">
            Final URL suffix
            <input
              className="form-input mt-2"
              value={finalUrlSuffix}
              disabled={!canModify || loading || savingUrls}
              onChange={(event) => setFinalUrlSuffix(event.target.value)}
              placeholder="utm_source=google&utm_campaign={campaignid}"
            />
          </label>
          <label className="text-xs font-semibold text-slate-700">
            Custom parameters
            <textarea
              className="form-input mt-2 min-h-20"
              value={customParameters}
              disabled={!canModify || loading || savingUrls}
              onChange={(event) => setCustomParameters(event.target.value)}
              placeholder={'source=pmax\nmarket=in'}
            />
          </label>
        </div>
        {canModify && (
          <button
            type="button"
            className="secondary-button mt-3 inline-flex items-center gap-2 text-xs"
            disabled={loading || savingUrls}
            onClick={() => void saveUrlOptions()}
          >
            <FiLink2 aria-hidden="true" />
            {savingUrls ? 'Saving URL options…' : 'Save URL options'}
          </button>
        )}
      </div>

      {loading ? (
        <div className="mt-4"><ListSkeleton rows={4} /></div>
      ) : groupedAssets.length === 0 ? (
        <div className="mt-4 text-xs text-slate-400">
          Google returned no linked assets for this Asset Group.
        </div>
      ) : (
        <div className="mt-4 space-y-4">
          {groupedAssets.map(([type, assets]) => (
            <div key={type}>
              <div className="mb-2 flex items-center justify-between">
                <p className="text-xs font-bold uppercase tracking-wide text-slate-500">
                  {formatLabel(type)}
                </p>
                <span className="text-[11px] font-semibold text-slate-400">{assets.length}</span>
              </div>
              <div className="grid gap-2 md:grid-cols-2">
                {assets.map((asset) => (
                  <div
                    key={asset.resourceName}
                    className="flex min-w-0 items-center gap-3 rounded-xl border border-slate-200 bg-white p-3"
                  >
                    {asset.imageUrl ? (
                      <img
                        src={asset.imageUrl}
                        alt=""
                        className="h-12 w-16 rounded-lg bg-slate-100 object-contain"
                      />
                    ) : asset.youtubeVideoId ? (
                      <div className="flex h-12 w-16 items-center justify-center rounded-lg bg-slate-100">
                        <FiVideo className="text-slate-400" aria-hidden="true" />
                      </div>
                    ) : (
                      <div className="flex h-12 w-16 items-center justify-center rounded-lg bg-slate-100">
                        <FiImage className="text-slate-400" aria-hidden="true" />
                      </div>
                    )}

                    <div className="min-w-0 flex-1">
                      <p className="truncate text-xs font-semibold text-slate-800">
                        {asset.text ||
                          asset.assetName ||
                          asset.youtubeVideoId ||
                          asset.assetId ||
                          'Google asset'}
                      </p>
                      <div className="mt-1 flex flex-wrap gap-1">
                        <span className="rounded-full bg-slate-100 px-2 py-0.5 text-[10px] font-semibold text-slate-500">
                          {asset.status ?? 'UNKNOWN'}
                        </span>
                        {asset.primaryStatus && (
                          <span className="rounded-full bg-slate-100 px-2 py-0.5 text-[10px] font-semibold text-slate-600">
                            {formatLabel(asset.primaryStatus)}
                          </span>
                        )}
                        {asset.policyApprovalStatus && (
                          <span className="rounded-full bg-indigo-50 px-2 py-0.5 text-[10px] font-semibold text-indigo-700">
                            {formatLabel(asset.policyApprovalStatus)}
                          </span>
                        )}
                        {asset.policyReviewStatus && (
                          <span className="rounded-full bg-violet-50 px-2 py-0.5 text-[10px] font-semibold text-violet-700">
                            {formatLabel(asset.policyReviewStatus)}
                          </span>
                        )}
                        {asset.source && (
                          <span className="rounded-full bg-slate-50 px-2 py-0.5 text-[10px] font-semibold text-slate-500">
                            {formatLabel(asset.source)}
                          </span>
                        )}
                        {asset.imageWidth && asset.imageHeight && (
                          <span className="text-[10px] text-slate-400">
                            {asset.imageWidth}×{asset.imageHeight}
                          </span>
                        )}
                      </div>
                      {asset.primaryStatusReasons.length > 0 && (
                        <p className="mt-2 text-[10px] leading-4 text-amber-700">
                          {asset.primaryStatusReasons.map(formatLabel).join(' · ')}
                        </p>
                      )}
                    </div>

                    {canModify && (
                      <button
                        type="button"
                        className="rounded-lg p-2 text-slate-400 hover:bg-red-50 hover:text-red-600"
                        disabled={removing !== null}
                        title="Unlink asset"
                        onClick={() => void remove(asset)}
                      >
                        <FiTrash2 aria-hidden="true" />
                      </button>
                    )}
                  </div>
                ))}
              </div>
            </div>
          ))}
        </div>
      )}

      <GooglePerformanceMaxListingGroups
        companyId={companyId}
        campaignId={campaignId}
        assetGroup={assetGroup}
        canModify={canModify}
      />

      {canModify && (
        <div className="mt-5 rounded-xl border border-slate-200 bg-slate-50 p-4">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <div>
              <p className="text-xs font-bold text-slate-800">Add asset</p>
              <p className="mt-1 text-[11px] text-slate-500">
                Create a new Google Asset or reuse an existing account asset.
              </p>
            </div>
            <div className="flex rounded-lg bg-slate-200 p-1">
              {(['NEW', 'EXISTING'] as const).map((mode) => (
                <button
                  key={mode}
                  type="button"
                  className={`rounded-md px-3 py-1.5 text-[11px] font-semibold ${
                    sourceMode === mode ? 'bg-white text-slate-900 shadow-sm' : 'text-slate-500'
                  }`}
                  onClick={() => setSourceMode(mode)}
                >
                  {mode === 'NEW' ? 'New asset' : 'Reuse asset'}
                </button>
              ))}
            </div>
          </div>

          <div className="mt-4 grid gap-4 lg:grid-cols-2">
            <label className="text-xs font-semibold text-slate-700">
              Placement
              <select
                className="form-input mt-2"
                value={fieldType}
                onChange={(event) => setFieldType(event.target.value as FieldType)}
              >
                {FIELD_OPTIONS.map((option) => (
                  <option key={option.value} value={option.value}>
                    {option.label}
                  </option>
                ))}
              </select>
              <span className="mt-1 block font-normal text-slate-400">
                {FIELD_HINTS[fieldType]}
              </span>
            </label>

            {sourceMode === 'EXISTING' ? (
              <label className="text-xs font-semibold text-slate-700">
                Existing Google asset
                <select
                  className="form-input mt-2"
                  value={existingAssetResourceName}
                  onChange={(event) => setExistingAssetResourceName(event.target.value)}
                >
                  <option value="">Select an asset</option>
                  {compatibleLibraryAssets.map((asset) => (
                    <option key={asset.resourceName ?? asset.id} value={asset.resourceName ?? ''}>
                      {asset.name || asset.text || asset.youtubeVideoId || asset.id}
                    </option>
                  ))}
                </select>
                {compatibleLibraryAssets.length === 0 && (
                  <span className="mt-1 block font-normal text-amber-600">
                    No compatible account assets found. Create a new asset instead.
                  </span>
                )}
              </label>
            ) : (
              <label className="text-xs font-semibold text-slate-700">
                Asset name
                <input
                  className="form-input mt-2"
                  value={assetName}
                  maxLength={128}
                  placeholder="Optional internal Google asset name"
                  onChange={(event) => setAssetName(event.target.value)}
                />
              </label>
            )}

            {sourceMode === 'NEW' && field.source === 'TEXT' && (
              <label className="text-xs font-semibold text-slate-700 lg:col-span-2">
                Text
                <textarea
                  className="form-input mt-2 min-h-24"
                  value={textValue}
                  onChange={(event) => setTextValue(event.target.value)}
                />
              </label>
            )}

            {sourceMode === 'NEW' && field.source === 'IMAGE' && (
              <label className="rounded-xl border border-dashed border-slate-300 bg-white p-4 text-xs font-semibold text-slate-700 lg:col-span-2">
                Upload image
                <input
                  className="mt-3 block w-full text-xs"
                  type="file"
                  accept="image/png,image/jpeg,image/gif"
                  onChange={(event) => void readImage(event)}
                />
                {imageBase64 && (
                  <span className="mt-2 block font-normal text-emerald-600">
                    Image ready to upload.
                  </span>
                )}
              </label>
            )}

            {sourceMode === 'NEW' && field.source === 'YOUTUBE_VIDEO' && (
              <label className="text-xs font-semibold text-slate-700 lg:col-span-2">
                YouTube video ID
                <input
                  className="form-input mt-2"
                  value={youtubeVideoId}
                  placeholder="dQw4w9WgXcQ"
                  onChange={(event) => setYoutubeVideoId(event.target.value)}
                />
              </label>
            )}
          </div>

          <button
            type="button"
            className="primary-button mt-4 inline-flex items-center gap-2"
            disabled={saving || !canSubmit}
            onClick={() => void add()}
          >
            {sourceMode === 'EXISTING' ? (
              <FiLink2 aria-hidden="true" />
            ) : (
              <FiPlus aria-hidden="true" />
            )}
            {saving
              ? 'Adding…'
              : sourceMode === 'EXISTING'
                ? 'Link existing asset'
                : 'Create and link asset'}
          </button>
        </div>
      )}
    </div>
  )
}
