import { useSnackbarState } from '../feedback/useSnackbarState'
import { useEffect, useMemo, useState, type FormEvent } from 'react'
import { FiCopy, FiImage, FiRefreshCw, FiStar } from 'react-icons/fi'
import { adCenterApi } from '../../features/ads/adCenterApi'
import type {
  AdFeatureCapabilityInfo,
  GoogleAssetGenerationImageInput,
  GoogleAssetGenerationTextInput,
  GoogleGeneratedImageAsset,
  GoogleGeneratedTextAsset,
} from '../../features/ads/types'
import { apiErrorMessage } from '../../lib/api/apiClient'

type ChannelType = GoogleAssetGenerationTextInput['advertisingChannelType']

type Props = {
  companyId: string
  accountId: string
  channelType: ChannelType
  canCreate: boolean
}

const lines = (value: string) =>
  value
    .split(/\r?\n|,/)
    .map((item) => item.trim())
    .filter(Boolean)

const textOptions: Array<{
  value: GoogleAssetGenerationTextInput['assetFieldTypes'][number]
  label: string
}> = [
  { value: 'HEADLINE', label: 'Headlines' },
  { value: 'LONG_HEADLINE', label: 'Long headlines' },
  { value: 'DESCRIPTION', label: 'Descriptions' },
]

const imageOptions: Array<{
  value: NonNullable<GoogleAssetGenerationImageInput['assetFieldTypes']>[number]
  label: string
}> = [
  { value: 'MARKETING_IMAGE', label: 'Landscape' },
  { value: 'SQUARE_MARKETING_IMAGE', label: 'Square' },
  { value: 'PORTRAIT_MARKETING_IMAGE', label: 'Portrait' },
  { value: 'TALL_PORTRAIT_MARKETING_IMAGE', label: 'Tall portrait' },
]

const pretty = (value: string) =>
  value
    .replaceAll('_', ' ')
    .toLowerCase()
    .replace(/\b\w/g, (character) => character.toUpperCase())

export default function GoogleAiAssetStudio({
  companyId,
  accountId,
  channelType,
  canCreate,
}: Props) {
  const [capability, setCapability] = useState<AdFeatureCapabilityInfo | null>(null)
  const [finalUrl, setFinalUrl] = useState('')
  const [prompt, setPrompt] = useState('')
  const [keywords, setKeywords] = useState('')
  const [textFieldTypes, setTextFieldTypes] = useState<
    GoogleAssetGenerationTextInput['assetFieldTypes']
  >(['HEADLINE', 'DESCRIPTION'])
  const [imageFieldTypes, setImageFieldTypes] = useState<
    NonNullable<GoogleAssetGenerationImageInput['assetFieldTypes']>
  >(['MARKETING_IMAGE', 'SQUARE_MARKETING_IMAGE'])
  const [imageMode, setImageMode] =
    useState<GoogleAssetGenerationImageInput['generationMode']>('FINAL_URL')
  const [textSuggestions, setTextSuggestions] = useState<GoogleGeneratedTextAsset[]>([])
  const [imageSuggestions, setImageSuggestions] = useState<GoogleGeneratedImageAsset[]>([])
  const [loadingCapability, setLoadingCapability] = useState(true)
  const [generatingText, setGeneratingText] = useState(false)
  const [generatingImages, setGeneratingImages] = useState(false)
  const [, setError] = useSnackbarState('', 'error')
  const [, setNotice] = useSnackbarState('', 'success')

  const available = capability?.supported === true
  const eligibilityPending =
    capability?.availability === 'ACCOUNT_ELIGIBILITY_REQUIRED' &&
    capability.accountEligible == null

  const capabilityMessage = useMemo(() => {
    if (!capability) return ''
    if (!available) {
      return capability.reason ?? 'Google AI asset generation is unavailable for this account.'
    }
    if (eligibilityPending) {
      return (
        capability.reason ??
        'Google controls closed-beta eligibility and confirms it when generation is requested.'
      )
    }
    return 'Google AI asset generation is available for this account.'
  }, [capability, available, eligibilityPending])

  const loadCapability = async () => {
    setLoadingCapability(true)
    setError('')
    try {
      const capabilities = await adCenterApi.accountCapabilities(companyId, accountId)
      setCapability(capabilities.find((item) => item.feature === 'GOOGLE_ASSET_GENERATION') ?? null)
    } catch (loadError) {
      setError(apiErrorMessage(loadError, 'Unable to load Google AI capability'))
    } finally {
      setLoadingCapability(false)
    }
  }

  useEffect(() => {
    void loadCapability()
  }, [companyId, accountId])

  const toggleTextType = (value: GoogleAssetGenerationTextInput['assetFieldTypes'][number]) => {
    setTextFieldTypes((current) =>
      current.includes(value) ? current.filter((item) => item !== value) : [...current, value],
    )
  }

  const toggleImageType = (
    value: NonNullable<GoogleAssetGenerationImageInput['assetFieldTypes']>[number],
  ) => {
    setImageFieldTypes((current) =>
      current.includes(value) ? current.filter((item) => item !== value) : [...current, value],
    )
  }

  const generateText = async (event: FormEvent) => {
    event.preventDefault()
    setGeneratingText(true)
    setError('')
    setNotice('')
    try {
      const result = await adCenterApi.generateGoogleTextAssets(companyId, accountId, {
        advertisingChannelType: channelType,
        assetFieldTypes: textFieldTypes,
        finalUrl: finalUrl.trim(),
        freeformPrompt: prompt.trim() || null,
        keywords: lines(keywords),
      })
      setTextSuggestions(result.suggestions)
      setNotice(
        result.suggestions.length > 0
          ? 'Google generated draft text suggestions. Review and edit before using them.'
          : 'Google returned no text suggestions for this request.',
      )
    } catch (generationError) {
      setError(
        apiErrorMessage(
          generationError,
          'Google AI text generation is not available for this account or request',
        ),
      )
    } finally {
      setGeneratingText(false)
    }
  }

  const generateImages = async () => {
    setGeneratingImages(true)
    setError('')
    setNotice('')
    try {
      const result = await adCenterApi.generateGoogleImageAssets(companyId, accountId, {
        advertisingChannelType: channelType,
        assetFieldTypes: imageFieldTypes,
        generationMode: imageMode,
        finalUrl: imageMode === 'FINAL_URL' ? finalUrl.trim() : null,
        freeformPrompt: imageMode === 'FREEFORM' ? prompt.trim() : null,
      })
      setImageSuggestions(result.suggestions)
      setNotice(
        result.suggestions.length > 0
          ? 'Google generated temporary image suggestions. Review them before creating any ad asset.'
          : 'Google returned no image suggestions for this request.',
      )
    } catch (generationError) {
      setError(
        apiErrorMessage(
          generationError,
          'Google AI image generation is not available for this account or request',
        ),
      )
    } finally {
      setGeneratingImages(false)
    }
  }

  return (
    <section className="panel mt-5 overflow-hidden">
      <div className="flex flex-wrap items-start justify-between gap-3 border-b border-slate-100 px-5 py-4">
        <div>
          <div className="flex items-center gap-2">
            <FiStar className="text-indigo-600" aria-hidden="true" />
            <h2 className="text-base font-bold text-slate-950">Google AI asset studio</h2>
          </div>
          <p className="mt-1 max-w-3xl text-xs leading-5 text-slate-500">
            Generate copy and image suggestions with Google&apos;s AssetGenerationService.
            Suggestions are drafts only and are never published automatically.
          </p>
        </div>
        <button
          type="button"
          className="secondary-button inline-flex items-center gap-2"
          disabled={loadingCapability}
          onClick={() => void loadCapability()}
        >
          <FiRefreshCw className={loadingCapability ? 'animate-spin' : ''} aria-hidden="true" />
          Check eligibility
        </button>
      </div>

      <div className="px-5 pt-4">
        <div
          className={
            'rounded-xl border px-4 py-3 text-xs leading-5 ' +
            (!available
              ? 'border-amber-200 bg-amber-50 text-amber-800'
              : eligibilityPending
                ? 'border-indigo-200 bg-indigo-50 text-indigo-800'
                : 'border-emerald-200 bg-emerald-50 text-emerald-800')
          }
        >
          {loadingCapability
            ? 'Checking Google AI generation capability…'
            : capabilityMessage || 'Capability information is unavailable.'}
        </div>
      </div>



      <div className="grid gap-5 p-5 xl:grid-cols-2">
        <form className="rounded-xl border border-slate-200 p-4" onSubmit={generateText}>
          <h3 className="text-sm font-bold text-slate-900">Generate text</h3>
          <p className="mt-1 text-xs leading-5 text-slate-500">
            Google uses the landing page, optional prompt, and keywords as generation context.
          </p>

          <label className="mt-4 block text-xs font-semibold text-slate-700">
            Landing page URL
            <input
              className="form-input mt-2"
              value={finalUrl}
              required
              disabled={!canCreate || generatingText || generatingImages}
              onChange={(event) => setFinalUrl(event.target.value)}
              placeholder="https://example.com/product"
            />
          </label>

          <label className="mt-3 block text-xs font-semibold text-slate-700">
            Generation prompt
            <textarea
              className="form-input mt-2 min-h-24"
              value={prompt}
              maxLength={1500}
              disabled={!canCreate || generatingText || generatingImages}
              onChange={(event) => setPrompt(event.target.value)}
              placeholder="Focus on fast customer support and simple onboarding."
            />
          </label>

          <label className="mt-3 block text-xs font-semibold text-slate-700">
            Keywords
            <textarea
              className="form-input mt-2 min-h-20"
              value={keywords}
              disabled={!canCreate || generatingText}
              onChange={(event) => setKeywords(event.target.value)}
              placeholder={'customer messaging\nshared inbox\nWhatsApp support'}
            />
          </label>

          <div className="mt-3">
            <p className="text-xs font-semibold text-slate-700">Text types</p>
            <div className="mt-2 flex flex-wrap gap-2">
              {textOptions.map((option) => (
                <label
                  key={option.value}
                  className="inline-flex items-center gap-2 rounded-full border border-slate-200 px-3 py-1.5 text-xs text-slate-600"
                >
                  <input
                    type="checkbox"
                    checked={textFieldTypes.includes(option.value)}
                    disabled={!canCreate || generatingText}
                    onChange={() => toggleTextType(option.value)}
                  />
                  {option.label}
                </label>
              ))}
            </div>
          </div>

          {canCreate && (
            <button
              type="submit"
              className="primary-button mt-4 inline-flex items-center gap-2"
              disabled={
                !available || generatingText || textFieldTypes.length === 0 || !finalUrl.trim()
              }
            >
              <FiStar aria-hidden="true" />
              {generatingText ? 'Generating…' : 'Generate text drafts'}
            </button>
          )}

          {textSuggestions.length > 0 && (
            <div className="mt-5 space-y-2">
              {textSuggestions.map((suggestion, index) => (
                <div
                  key={`${suggestion.assetFieldType}-${index}`}
                  className="rounded-xl border border-slate-200 bg-slate-50 p-3"
                >
                  <div className="mb-2 flex items-center justify-between gap-2">
                    <span className="rounded-full bg-white px-2 py-0.5 text-[10px] font-bold text-slate-500">
                      {pretty(suggestion.assetFieldType)}
                    </span>
                    <button
                      type="button"
                      className="rounded-lg p-1.5 text-slate-400 hover:bg-white hover:text-slate-700"
                      title="Copy suggestion"
                      onClick={() => void navigator.clipboard.writeText(suggestion.text)}
                    >
                      <FiCopy aria-hidden="true" />
                    </button>
                  </div>
                  <textarea
                    className="form-input min-h-20 bg-white"
                    value={suggestion.text}
                    onChange={(event) =>
                      setTextSuggestions((current) =>
                        current.map((item, itemIndex) =>
                          itemIndex === index ? { ...item, text: event.target.value } : item,
                        ),
                      )
                    }
                  />
                </div>
              ))}
            </div>
          )}
        </form>

        <div className="rounded-xl border border-slate-200 p-4">
          <div className="flex items-center gap-2">
            <FiImage className="text-indigo-600" aria-hidden="true" />
            <h3 className="text-sm font-bold text-slate-900">Generate images</h3>
          </div>
          <p className="mt-1 text-xs leading-5 text-slate-500">
            Generate temporary visual suggestions from the landing page or your freeform prompt.
          </p>

          <div className="mt-4 flex gap-2">
            {(['FINAL_URL', 'FREEFORM'] as const).map((mode) => (
              <button
                key={mode}
                type="button"
                className={
                  'rounded-full px-3 py-1.5 text-xs font-semibold ' +
                  (imageMode === mode ? 'bg-slate-900 text-white' : 'bg-slate-100 text-slate-600')
                }
                disabled={!canCreate || generatingImages}
                onClick={() => setImageMode(mode)}
              >
                {mode === 'FINAL_URL' ? 'From landing page' : 'From prompt'}
              </button>
            ))}
          </div>

          <div className="mt-4">
            <p className="text-xs font-semibold text-slate-700">Image formats</p>
            <div className="mt-2 flex flex-wrap gap-2">
              {imageOptions.map((option) => (
                <label
                  key={option.value}
                  className="inline-flex items-center gap-2 rounded-full border border-slate-200 px-3 py-1.5 text-xs text-slate-600"
                >
                  <input
                    type="checkbox"
                    checked={imageFieldTypes.includes(option.value)}
                    disabled={!canCreate || generatingImages}
                    onChange={() => toggleImageType(option.value)}
                  />
                  {option.label}
                </label>
              ))}
            </div>
          </div>

          {canCreate && (
            <button
              type="button"
              className="primary-button mt-4 inline-flex items-center gap-2"
              disabled={
                !available ||
                generatingImages ||
                (imageMode === 'FINAL_URL' && !finalUrl.trim()) ||
                (imageMode === 'FREEFORM' && !prompt.trim())
              }
              onClick={() => void generateImages()}
            >
              <FiStar aria-hidden="true" />
              {generatingImages ? 'Generating…' : 'Generate image drafts'}
            </button>
          )}

          {imageSuggestions.length > 0 && (
            <div className="mt-5 grid gap-3 sm:grid-cols-2">
              {imageSuggestions.map((suggestion, index) => (
                <div
                  key={`${suggestion.imageTemporaryUrl}-${index}`}
                  className="overflow-hidden rounded-xl border border-slate-200 bg-slate-50"
                >
                  <img
                    src={suggestion.imageTemporaryUrl}
                    alt=""
                    className="aspect-square w-full bg-white object-contain"
                  />
                  <div className="p-3">
                    <p className="text-[10px] font-bold uppercase tracking-wide text-slate-500">
                      {pretty(suggestion.assetFieldType ?? 'Generated image')}
                    </p>
                    <p className="mt-1 text-[11px] leading-5 text-slate-500">
                      Temporary provider URL. Review it now; UnifyDesk has not published or saved it
                      as an ad asset.
                    </p>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>
    </section>
  )
}
