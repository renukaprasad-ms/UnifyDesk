import { useSnackbarState } from '../feedback/useSnackbarState'
import {
  useEffect,
  useMemo,
  useState,
  type Dispatch,
  type FormEvent,
  type SetStateAction,
} from 'react'
import { FiPlus, FiTrash2, FiX } from 'react-icons/fi'
import { adCenterApi } from '../../features/ads/adCenterApi'
import type {
  AdAccount,
  AdCampaign,
  CreateGoogleAppAdInput,
  GoogleAppCreativeAsset,
} from '../../features/ads/types'
import { apiErrorMessage } from '../../lib/api/apiClient'

type Props = {
  companyId: string
  account: AdAccount
  campaign: AdCampaign
  open: boolean
  saving: boolean
  onClose: () => void
  onCreate: (input: CreateGoogleAppAdInput) => Promise<void>
}

export default function GoogleAppAdModal({
  companyId,
  account,
  campaign,
  open,
  saving,
  onClose,
  onCreate,
}: Props) {
  const [name, setName] = useState('')
  const [headlines, setHeadlines] = useState([''])
  const [descriptions, setDescriptions] = useState([''])
  const [assets, setAssets] = useState<GoogleAppCreativeAsset[]>([])
  const [selectedImages, setSelectedImages] = useState<string[]>([])
  const [selectedVideos, setSelectedVideos] = useState<string[]>([])
  const [, setError] = useSnackbarState('', 'error')

  const images = useMemo(() => assets.filter((asset) => asset.type === 'IMAGE'), [assets])
  const videos = useMemo(() => assets.filter((asset) => asset.type === 'YOUTUBE_VIDEO'), [assets])

  useEffect(() => {
    if (!open) return
    setError('')
    void adCenterApi
      .googleAppCreativeAssets(companyId, account.id)
      .then(setAssets)
      .catch((loadError) =>
        setError(
          apiErrorMessage(
            loadError,
            'Unable to load reusable image and YouTube assets from Google Ads.',
          ),
        ),
      )
  }, [open, companyId, account.id])

  if (!open) return null

  const updateValue = (setter: Dispatch<SetStateAction<string[]>>, index: number, value: string) =>
    setter((current) => current.map((item, itemIndex) => (itemIndex === index ? value : item)))

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    await onCreate({
      name: name.trim(),
      headlines: headlines.map((value) => value.trim()).filter(Boolean),
      descriptions: descriptions.map((value) => value.trim()).filter(Boolean),
      imageAssetResourceNames: selectedImages,
      videoAssetResourceNames: selectedVideos,
    })
  }

  const subtype =
    campaign.buyingType === 'APP_CAMPAIGN_FOR_ENGAGEMENT'
      ? 'Engagement'
      : campaign.buyingType === 'APP_CAMPAIGN_FOR_PRE_REGISTRATION'
        ? 'Pre-registration'
        : 'App'

  return (
    <div className="mobile-modal-shell fixed inset-0 z-50 flex items-center justify-center bg-slate-950/40 p-4">
      <div className="mobile-sheet max-h-[94vh] w-full max-w-4xl overflow-y-auto rounded-2xl bg-white shadow-2xl">
        <div className="sticky top-0 z-10 flex items-start justify-between border-b border-slate-100 bg-white px-6 py-5">
          <div>
            <p className="eyebrow">Google Ads · {subtype}</p>
            <h2 className="mt-1 text-xl font-bold text-slate-950">Create App ad</h2>
            <p className="mt-1 text-sm text-slate-500">
              Text can be entered directly; images and videos reuse assets already stored in Google
              Ads.
            </p>
          </div>
          <button
            type="button"
            className="rounded-lg p-2 text-slate-400 hover:bg-slate-100"
            onClick={onClose}
          >
            <FiX size={18} aria-hidden="true" />
          </button>
        </div>

        <form className="space-y-6 p-6" onSubmit={submit}>


          <label className="block text-xs font-semibold text-slate-700">
            Ad name
            <input
              className="form-input mt-2"
              value={name}
              maxLength={255}
              required
              onChange={(event) => setName(event.target.value)}
            />
          </label>

          <TextList
            title="Headlines"
            max={5}
            maxLength={30}
            values={headlines}
            setValues={setHeadlines}
            onChange={(index, value) => updateValue(setHeadlines, index, value)}
          />

          <TextList
            title="Descriptions"
            max={5}
            maxLength={90}
            values={descriptions}
            setValues={setDescriptions}
            onChange={(index, value) => updateValue(setDescriptions, index, value)}
          />

          <div>
            <h3 className="text-sm font-bold text-slate-950">Image assets</h3>
            <p className="mt-1 text-xs text-slate-500">
              Optional. Create image assets in the Google asset library first if none are available.
            </p>
            <div className="mt-3 grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
              {images.length === 0 ? (
                <p className="text-xs text-slate-500">No reusable Google image assets found.</p>
              ) : (
                images.map((asset) => (
                  <label
                    key={asset.resourceName}
                    className="overflow-hidden rounded-xl border border-slate-200 bg-slate-50"
                  >
                    {asset.imageUrl && (
                      <img
                        src={asset.imageUrl}
                        alt=""
                        className="aspect-video w-full bg-white object-contain"
                      />
                    )}
                    <span className="flex items-start gap-2 p-3">
                      <input
                        type="checkbox"
                        className="mt-1"
                        checked={selectedImages.includes(asset.resourceName)}
                        onChange={(event) =>
                          setSelectedImages((current) =>
                            event.target.checked
                              ? [...current, asset.resourceName]
                              : current.filter((item) => item !== asset.resourceName),
                          )
                        }
                      />
                      <span className="min-w-0">
                        <span className="block truncate text-xs font-semibold text-slate-800">
                          {asset.name ?? 'Google image asset'}
                        </span>
                        <span className="mt-1 block break-all text-[10px] text-slate-400">
                          {asset.resourceName}
                        </span>
                      </span>
                    </span>
                  </label>
                ))
              )}
            </div>
          </div>

          <div>
            <h3 className="text-sm font-bold text-slate-950">YouTube video assets</h3>
            <div className="mt-3 grid gap-2 sm:grid-cols-2">
              {videos.length === 0 ? (
                <p className="text-xs text-slate-500">No advertiser YouTube video assets found.</p>
              ) : (
                videos.map((asset) => (
                  <label
                    key={asset.resourceName}
                    className="flex items-start gap-3 rounded-xl border border-slate-200 p-3"
                  >
                    <input
                      type="checkbox"
                      className="mt-1"
                      checked={selectedVideos.includes(asset.resourceName)}
                      onChange={(event) =>
                        setSelectedVideos((current) =>
                          event.target.checked
                            ? [...current, asset.resourceName]
                            : current.filter((item) => item !== asset.resourceName),
                        )
                      }
                    />
                    <span>
                      <span className="block text-xs font-semibold text-slate-800">
                        {asset.name ?? 'YouTube video'}
                      </span>
                      <span className="mt-1 block text-[11px] text-slate-500">
                        Video ID {asset.youtubeVideoId ?? '—'}
                      </span>
                    </span>
                  </label>
                ))
              )}
            </div>
          </div>

          <div className="rounded-xl border border-indigo-200 bg-indigo-50 px-4 py-3 text-xs leading-5 text-indigo-800">
            The ad is created paused. Google can generate additional assets from App Store content
            depending on account/campaign eligibility; UnifyDesk only sends the assets you selected
            here.
          </div>

          <div className="flex justify-end gap-2 border-t border-slate-100 pt-5">
            <button type="button" className="secondary-button" disabled={saving} onClick={onClose}>
              Cancel
            </button>
            <button
              type="submit"
              className="primary-button"
              disabled={
                saving ||
                !name.trim() ||
                headlines.every((value) => !value.trim()) ||
                descriptions.every((value) => !value.trim())
              }
            >
              {saving ? 'Creating…' : 'Create paused App ad'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}

function TextList({
  title,
  max,
  maxLength,
  values,
  setValues,
  onChange,
}: {
  title: string
  max: number
  maxLength: number
  values: string[]
  setValues: Dispatch<SetStateAction<string[]>>
  onChange: (index: number, value: string) => void
}) {
  return (
    <div>
      <div className="flex items-center justify-between gap-3">
        <h3 className="text-sm font-bold text-slate-950">{title}</h3>
        {values.length < max && (
          <button
            type="button"
            className="secondary-button inline-flex items-center gap-2 text-xs"
            onClick={() => setValues((current) => [...current, ''])}
          >
            <FiPlus aria-hidden="true" /> Add
          </button>
        )}
      </div>
      <div className="mt-3 grid gap-3 sm:grid-cols-2">
        {values.map((value, index) => (
          <label key={index} className="text-xs font-semibold text-slate-700">
            {title.slice(0, -1)} {index + 1}
            <span className="flex gap-2">
              <input
                className="form-input mt-2"
                value={value}
                maxLength={maxLength}
                required={index === 0}
                onChange={(event) => onChange(index, event.target.value)}
              />
              {values.length > 1 && (
                <button
                  type="button"
                  className="mt-2 rounded-lg px-2 text-slate-400 hover:bg-red-50 hover:text-red-600"
                  onClick={() =>
                    setValues((current) => current.filter((_, itemIndex) => itemIndex !== index))
                  }
                >
                  <FiTrash2 aria-hidden="true" />
                </button>
              )}
            </span>
          </label>
        ))}
      </div>
    </div>
  )
}
