import { useEffect, useMemo, useState } from 'react'
import { adCenterApi } from '../../features/ads/adCenterApi'
import type {
  CreativeVariantType,
  ReachlyoCreativePack,
  ReachlyoCreativeVariant,
} from '../../features/ads/types'
import { mediaApi } from '../../features/media/mediaApi'
import { apiErrorMessage } from '../../lib/api/apiClient'

type Props = {
  companyId: string
  campaignId: string
  channelId: string
  pack: ReachlyoCreativePack
  selectedPositions: string[]
  disabled?: boolean
  onSaved: (pack: ReachlyoCreativePack) => void
  onError: (message: string) => void
}

type Draft = {
  enabled: boolean
  primaryText: string
  headline: string
  description: string
  mediaId: string
  mediaFile: File | null
}

const VARIANTS: Array<{
  type: Extract<CreativeVariantType, 'META_FEED' | 'META_STORY' | 'META_REEL'>
  label: string
  placements: string[]
  hint: string
}> = [
  {
    type: 'META_FEED',
    label: 'Feed',
    placements: [
      'feed',
      'stream',
      'marketplace',
      'video_feeds',
      'search',
      'explore',
      'explore_home',
      'profile_feed',
    ],
    hint: 'Use square or landscape media for feed-style placements.',
  },
  {
    type: 'META_STORY',
    label: 'Stories',
    placements: ['story'],
    hint: 'Use a vertical image or video for Story placements.',
  },
  {
    type: 'META_REEL',
    label: 'Reels',
    placements: ['reels'],
    hint: 'Use a vertical video or image for Reels placements.',
  },
]

const parseFirst = (value?: string | null) => {
  if (!value) return ''
  try {
    const parsed = JSON.parse(value)
    return Array.isArray(parsed) && typeof parsed[0] === 'string' ? parsed[0] : ''
  } catch {
    return ''
  }
}

const parseMediaId = (value?: string | null) => {
  if (!value) return ''
  try {
    const parsed = JSON.parse(value) as { mediaId?: string | number }
    return parsed.mediaId == null ? '' : String(parsed.mediaId)
  } catch {
    return ''
  }
}

export default function MetaCreativeVariantsEditor({
  companyId,
  campaignId,
  channelId,
  pack,
  selectedPositions,
  disabled,
  onSaved,
  onError,
}: Props) {
  const existingByType = useMemo(
    () =>
      new Map(
        pack.variants
          .filter((variant) => variant.provider === 'META_ADS')
          .map((variant) => [variant.variantType, variant]),
      ),
    [pack.variants],
  )

  const [drafts, setDrafts] = useState<Record<string, Draft>>({})
  const [savingType, setSavingType] = useState<string | null>(null)
  const [uploadProgress, setUploadProgress] = useState<Record<string, number>>({})

  useEffect(() => {
    const next: Record<string, Draft> = {}
    for (const definition of VARIANTS) {
      const existing = existingByType.get(definition.type)
      next[definition.type] = {
        enabled: Boolean(existing),
        primaryText: existing?.primaryText ?? pack.primaryMessage ?? '',
        headline: parseFirst(existing?.headlinesJson) || parseFirst(pack.headlinesJson),
        description: parseFirst(existing?.descriptionsJson) || parseFirst(pack.descriptionsJson),
        mediaId: parseMediaId(existing?.assetSpecJson),
        mediaFile: null,
      }
    }
    setDrafts(next)
  }, [pack.id, pack.updatedAt, pack.variants])

  const save = async (type: (typeof VARIANTS)[number]['type']) => {
    const draft = drafts[type]
    if (!draft) return
    setSavingType(type)
    onError('')
    try {
      const existing = existingByType.get(type)
      let variant: ReachlyoCreativeVariant | null = null
      let mediaId = draft.mediaId

      if (!draft.enabled && existing) {
        await adCenterApi.deleteReachlyoCreativeVariant(companyId, campaignId, pack.id, existing.id)
      } else if (draft.enabled) {
        if (draft.mediaFile) {
          setUploadProgress((current) => ({ ...current, [type]: 1 }))
          const uploaded = await mediaApi.uploadAdCreative(draft.mediaFile, companyId, (progress) =>
            setUploadProgress((current) => ({ ...current, [type]: progress })),
          )
          mediaId = uploaded.mediaId
        }

        const input = {
          campaignChannelId: channelId,
          name: `${pack.name} · ${type.replace('META_', '').toLowerCase()}`,
          placement: type.replace('META_', '').toLowerCase(),
          primaryText: draft.primaryText.trim() || null,
          headlines: draft.headline.trim() ? [draft.headline.trim()] : [],
          descriptions: draft.description.trim() ? [draft.description.trim()] : [],
          callToAction: pack.callToAction ?? null,
          destinationUrl: pack.destinationUrl ?? null,
          assetSpecJson: mediaId ? JSON.stringify({ mediaId }) : null,
        }
        variant = existing
          ? await adCenterApi.updateReachlyoCreativeVariant(
              companyId,
              campaignId,
              pack.id,
              existing.id,
              input,
            )
          : await adCenterApi.createReachlyoCreativeVariant(companyId, campaignId, pack.id, {
              ...input,
              variantType: type,
            })
      }

      const variants = !draft.enabled
        ? pack.variants.filter((item) => item.id !== existing?.id)
        : [
            ...pack.variants.filter((item) => item.id !== existing?.id),
            ...(variant ? [variant] : []),
          ]
      setDrafts((current) => ({
        ...current,
        [type]: {
          ...current[type],
          mediaId,
          mediaFile: null,
        },
      }))
      setUploadProgress((current) => ({ ...current, [type]: 0 }))
      onSaved({ ...pack, variants })
    } catch (error) {
      onError(apiErrorMessage(error, 'Unable to save Meta creative variant'))
    } finally {
      setSavingType(null)
    }
  }

  if (selectedPositions.length === 0) {
    return (
      <div className="rounded-xl border border-slate-200 bg-slate-50 px-4 py-3 text-xs leading-5 text-slate-600">
        Advantage+ placements are selected. Placement-specific variants are optional; Meta can reuse
        the canonical creative.
      </div>
    )
  }

  return (
    <div className="rounded-2xl border border-slate-200 p-4">
      <div>
        <h4 className="text-sm font-bold text-slate-900">Placement creative variants</h4>
        <p className="mt-1 text-xs leading-5 text-slate-500">
          Customize copy and media for Feed, Stories, and Reels. Image and video uploads are stored
          as AD_CREATIVE media and validated by the backend.
        </p>
      </div>

      <div className="mt-4 grid gap-3">
        {VARIANTS.map((definition) => {
          const draft = drafts[definition.type]
          if (!draft) return null
          const recommended = definition.placements.some((placement) =>
            selectedPositions.includes(placement),
          )
          const progress = uploadProgress[definition.type] ?? 0
          return (
            <div key={definition.type} className="rounded-xl border border-slate-200 p-4">
              <div className="flex flex-wrap items-start justify-between gap-3">
                <label className="flex items-start gap-3">
                  <input
                    className="mt-1"
                    type="checkbox"
                    checked={draft.enabled}
                    disabled={disabled}
                    onChange={(event) =>
                      setDrafts((current) => ({
                        ...current,
                        [definition.type]: {
                          ...current[definition.type],
                          enabled: event.target.checked,
                        },
                      }))
                    }
                  />
                  <span>
                    <span className="block text-sm font-semibold text-slate-800">
                      {definition.label}
                      {recommended ? ' · Recommended' : ''}
                    </span>
                    <span className="mt-1 block text-xs text-slate-500">{definition.hint}</span>
                  </span>
                </label>
                <button
                  type="button"
                  className="secondary-button"
                  disabled={disabled || savingType === definition.type}
                  onClick={() => void save(definition.type)}
                >
                  {savingType === definition.type ? 'Saving…' : 'Save variant'}
                </button>
              </div>

              {draft.enabled && (
                <div className="mt-4 grid gap-3 sm:grid-cols-2">
                  <label className="block text-xs font-semibold text-slate-700 sm:col-span-2">
                    Placement media
                    <span className="mt-1 block font-normal leading-5 text-slate-500">
                      {draft.mediaFile
                        ? draft.mediaFile.name
                        : draft.mediaId
                          ? 'Custom placement media is configured.'
                          : 'No custom media yet; the canonical campaign image is used as fallback.'}
                    </span>
                    <input
                      className="form-input mt-2"
                      type="file"
                      accept="image/*,video/*"
                      disabled={disabled || savingType === definition.type}
                      onChange={(event) =>
                        setDrafts((current) => ({
                          ...current,
                          [definition.type]: {
                            ...current[definition.type],
                            mediaFile: event.target.files?.[0] ?? null,
                          },
                        }))
                      }
                    />
                    {progress > 0 && (
                      <span className="mt-2 block font-normal text-slate-500">
                        Upload {progress}%
                      </span>
                    )}
                  </label>
                  <label className="block text-xs font-semibold text-slate-700 sm:col-span-2">
                    Primary text
                    <textarea
                      className="form-input mt-2 min-h-24"
                      value={draft.primaryText}
                      disabled={disabled}
                      onChange={(event) =>
                        setDrafts((current) => ({
                          ...current,
                          [definition.type]: {
                            ...current[definition.type],
                            primaryText: event.target.value,
                          },
                        }))
                      }
                    />
                  </label>
                  <label className="block text-xs font-semibold text-slate-700">
                    Headline
                    <input
                      className="form-input mt-2"
                      value={draft.headline}
                      disabled={disabled}
                      onChange={(event) =>
                        setDrafts((current) => ({
                          ...current,
                          [definition.type]: {
                            ...current[definition.type],
                            headline: event.target.value,
                          },
                        }))
                      }
                    />
                  </label>
                  <label className="block text-xs font-semibold text-slate-700">
                    Description
                    <input
                      className="form-input mt-2"
                      value={draft.description}
                      disabled={disabled}
                      onChange={(event) =>
                        setDrafts((current) => ({
                          ...current,
                          [definition.type]: {
                            ...current[definition.type],
                            description: event.target.value,
                          },
                        }))
                      }
                    />
                  </label>
                </div>
              )}
            </div>
          )
        })}
      </div>
    </div>
  )
}
