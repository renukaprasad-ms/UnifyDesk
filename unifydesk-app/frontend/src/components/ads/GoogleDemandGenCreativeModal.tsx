import { useSnackbarState } from '../feedback/useSnackbarState'
import { useMemo, useState, type ChangeEvent, type FormEvent } from 'react'
import { FiPlus, FiTrash2, FiUpload, FiX } from 'react-icons/fi'
import type {
  CreateGoogleDemandGenCarouselAdInput,
  CreateGoogleDemandGenMultiAssetAdInput,
  GoogleDemandGenCarouselCardInput,
} from '../../features/ads/types'

type Format = 'VIDEO' | 'MULTI_ASSET' | 'CAROUSEL'

type Props = {
  open: boolean
  saving: boolean
  onClose: () => void
  onCreateMultiAsset: (input: CreateGoogleDemandGenMultiAssetAdInput) => Promise<void>
  onCreateCarousel: (input: CreateGoogleDemandGenCarouselAdInput) => Promise<void>
  onChooseVideo: () => void
}

type CardState = GoogleDemandGenCarouselCardInput & {
  id: string
}

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

const newCard = (): CardState => ({
  id: crypto.randomUUID(),
  headline: '',
  callToActionText: '',
  marketingImageBase64: '',
  squareMarketingImageBase64: '',
  portraitMarketingImageBase64: '',
})

export default function GoogleDemandGenCreativeModal({
  open,
  saving,
  onClose,
  onCreateMultiAsset,
  onCreateCarousel,
  onChooseVideo,
}: Props) {
  const [format, setFormat] = useState<Format>('MULTI_ASSET')
  const [name, setName] = useState('')
  const [finalUrl, setFinalUrl] = useState('')
  const [businessName, setBusinessName] = useState('')
  const [headlines, setHeadlines] = useState([''])
  const [descriptions, setDescriptions] = useState([''])
  const [headline, setHeadline] = useState('')
  const [description, setDescription] = useState('')
  const [callToAction, setCallToAction] = useState('')
  const [landscapeImage, setLandscapeImage] = useState('')
  const [squareImage, setSquareImage] = useState('')
  const [portraitImage, setPortraitImage] = useState('')
  const [tallPortraitImage, setTallPortraitImage] = useState('')
  const [logoImage, setLogoImage] = useState('')
  const [cards, setCards] = useState<CardState[]>([newCard(), newCard()])
  const [, setImageError] = useSnackbarState('', 'error')

  const canSubmit = useMemo(() => {
    if (!name.trim() || !finalUrl.trim() || !businessName.trim() || !logoImage) {
      return false
    }
    if (format === 'MULTI_ASSET') {
      return (
        headlines.every((value) => value.trim()) &&
        descriptions.every((value) => value.trim()) &&
        Boolean(landscapeImage || squareImage)
      )
    }
    if (format === 'CAROUSEL') {
      return (
        headline.trim().length > 0 &&
        description.trim().length > 0 &&
        cards.length >= 2 &&
        cards.every(
          (card) =>
            card.headline.trim() &&
            Boolean(card.marketingImageBase64 || card.squareMarketingImageBase64),
        )
      )
    }
    return false
  }, [
    format,
    name,
    finalUrl,
    businessName,
    logoImage,
    headlines,
    descriptions,
    landscapeImage,
    squareImage,
    headline,
    description,
    cards,
  ])

  if (!open) return null

  const readImage =
    (setter: (value: string) => void) => async (event: ChangeEvent<HTMLInputElement>) => {
      const file = event.target.files?.[0]
      if (!file) return
      setImageError('')
      if (file.size > 5 * 1024 * 1024) {
        setImageError('Each Google image must be 5 MB or smaller.')
        event.target.value = ''
        return
      }
      if (!['image/jpeg', 'image/png', 'image/gif'].includes(file.type)) {
        setImageError('Use PNG, JPG, or GIF images.')
        event.target.value = ''
        return
      }
      try {
        setter(await fileToDataUrl(file))
      } catch (error) {
        setImageError(error instanceof Error ? error.message : 'Unable to read image file')
      }
    }

  const readCardImage =
    (
      id: string,
      key: 'marketingImageBase64' | 'squareMarketingImageBase64' | 'portraitMarketingImageBase64',
    ) =>
    async (event: ChangeEvent<HTMLInputElement>) => {
      const file = event.target.files?.[0]
      if (!file) return
      if (file.size > 5 * 1024 * 1024) {
        setImageError('Each Google image must be 5 MB or smaller.')
        event.target.value = ''
        return
      }
      if (!['image/jpeg', 'image/png', 'image/gif'].includes(file.type)) {
        setImageError('Use PNG, JPG, or GIF images.')
        event.target.value = ''
        return
      }
      try {
        const value = await fileToDataUrl(file)
        setCards((current) =>
          current.map((card) => (card.id === id ? { ...card, [key]: value } : card)),
        )
        setImageError('')
      } catch (error) {
        setImageError(error instanceof Error ? error.message : 'Unable to read image file')
      }
    }

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    if (!canSubmit) return

    if (format === 'MULTI_ASSET') {
      await onCreateMultiAsset({
        name: name.trim(),
        finalUrl: finalUrl.trim(),
        businessName: businessName.trim(),
        headlines: headlines.map((value) => value.trim()),
        descriptions: descriptions.map((value) => value.trim()),
        callToActionText: callToAction.trim() || null,
        marketingImageBase64: landscapeImage || null,
        squareMarketingImageBase64: squareImage || null,
        portraitMarketingImageBase64: portraitImage || null,
        tallPortraitMarketingImageBase64: tallPortraitImage || null,
        logoImageBase64: logoImage,
      })
      return
    }

    await onCreateCarousel({
      name: name.trim(),
      finalUrl: finalUrl.trim(),
      businessName: businessName.trim(),
      headline: headline.trim(),
      description: description.trim(),
      callToActionText: callToAction.trim() || null,
      logoImageBase64: logoImage,
      cards: cards.map((card) => ({
        headline: card.headline.trim(),
        callToActionText: card.callToActionText?.trim() || null,
        marketingImageBase64: card.marketingImageBase64 || null,
        squareMarketingImageBase64: card.squareMarketingImageBase64 || null,
        portraitMarketingImageBase64: card.portraitMarketingImageBase64 || null,
      })),
    })
  }

  const updateCard = (id: string, patch: Partial<GoogleDemandGenCarouselCardInput>) =>
    setCards((current) => current.map((card) => (card.id === id ? { ...card, ...patch } : card)))

  return (
    <div className="mobile-modal-shell fixed inset-0 z-50 flex items-center justify-center bg-slate-950/40 p-4">
      <div className="mobile-sheet max-h-[92vh] w-full max-w-4xl overflow-y-auto rounded-2xl bg-white shadow-2xl">
        <div className="sticky top-0 z-10 flex items-start justify-between border-b border-slate-100 bg-white px-6 py-5">
          <div>
            <p className="eyebrow">Google Ads · Demand Gen</p>
            <h2 className="mt-1 text-xl font-bold text-slate-950">Create Demand Gen creative</h2>
            <p className="mt-1 text-sm text-slate-500">
              Multi-Asset and Carousel ads are created paused for review.
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

        <div className="grid grid-cols-3 gap-2 border-b border-slate-100 px-6 py-4">
          <button
            type="button"
            className="rounded-lg px-3 py-2 text-xs font-semibold text-slate-500 hover:bg-slate-100"
            onClick={onChooseVideo}
          >
            Video
          </button>
          {(['MULTI_ASSET', 'CAROUSEL'] as const).map((value) => (
            <button
              key={value}
              type="button"
              className={`rounded-lg px-3 py-2 text-xs font-semibold ${
                format === value
                  ? 'bg-indigo-50 text-indigo-700'
                  : 'text-slate-500 hover:bg-slate-100'
              }`}
              onClick={() => setFormat(value)}
            >
              {value === 'MULTI_ASSET' ? 'Multi-Asset' : 'Carousel'}
            </button>
          ))}
        </div>

        <form className="space-y-6 p-6" onSubmit={submit}>
          <div className="grid gap-4 sm:grid-cols-2">
            <label className="text-xs font-semibold text-slate-700">
              Ad name
              <input
                className="form-input mt-2"
                value={name}
                maxLength={255}
                required
                onChange={(event) => setName(event.target.value)}
              />
            </label>
            <label className="text-xs font-semibold text-slate-700">
              Business name
              <input
                className="form-input mt-2"
                value={businessName}
                maxLength={25}
                required
                onChange={(event) => setBusinessName(event.target.value)}
              />
            </label>
            <label className="text-xs font-semibold text-slate-700 sm:col-span-2">
              Final URL
              <input
                className="form-input mt-2"
                type="url"
                value={finalUrl}
                required
                onChange={(event) => setFinalUrl(event.target.value)}
              />
            </label>
            <label className="text-xs font-semibold text-slate-700 sm:col-span-2">
              Call to action
              <input
                className="form-input mt-2"
                value={callToAction}
                placeholder="Optional Google CTA text"
                onChange={(event) => setCallToAction(event.target.value)}
              />
            </label>
          </div>

          {format === 'MULTI_ASSET' ? (
            <>
              <div>
                <div className="flex items-center justify-between gap-3">
                  <h3 className="text-sm font-bold text-slate-950">Headlines</h3>
                  {headlines.length < 5 && (
                    <button
                      type="button"
                      className="secondary-button text-xs"
                      onClick={() => setHeadlines((current) => [...current, ''])}
                    >
                      <FiPlus aria-hidden="true" /> Add
                    </button>
                  )}
                </div>
                <div className="mt-3 grid gap-3 sm:grid-cols-2">
                  {headlines.map((value, index) => (
                    <label key={index} className="text-xs font-semibold text-slate-700">
                      Headline {index + 1}
                      <input
                        className="form-input mt-2"
                        maxLength={30}
                        value={value}
                        required
                        onChange={(event) =>
                          setHeadlines((current) =>
                            current.map((item, itemIndex) =>
                              itemIndex === index ? event.target.value : item,
                            ),
                          )
                        }
                      />
                    </label>
                  ))}
                </div>
              </div>

              <div>
                <div className="flex items-center justify-between gap-3">
                  <h3 className="text-sm font-bold text-slate-950">Descriptions</h3>
                  {descriptions.length < 5 && (
                    <button
                      type="button"
                      className="secondary-button text-xs"
                      onClick={() => setDescriptions((current) => [...current, ''])}
                    >
                      <FiPlus aria-hidden="true" /> Add
                    </button>
                  )}
                </div>
                <div className="mt-3 grid gap-3 sm:grid-cols-2">
                  {descriptions.map((value, index) => (
                    <label key={index} className="text-xs font-semibold text-slate-700">
                      Description {index + 1}
                      <textarea
                        className="form-input mt-2 min-h-20"
                        maxLength={90}
                        value={value}
                        required
                        onChange={(event) =>
                          setDescriptions((current) =>
                            current.map((item, itemIndex) =>
                              itemIndex === index ? event.target.value : item,
                            ),
                          )
                        }
                      />
                    </label>
                  ))}
                </div>
              </div>

              <div>
                <h3 className="text-sm font-bold text-slate-950">Images</h3>
                <p className="mt-1 text-xs text-slate-500">
                  Supply at least a landscape or square image. Portrait and tall portrait are
                  optional.
                </p>
                <div className="mt-3 grid gap-3 md:grid-cols-3">
                  <ImageInput
                    label="Landscape"
                    hint="1.91:1 · 600×314+"
                    onChange={readImage(setLandscapeImage)}
                  />
                  <ImageInput
                    label="Square"
                    hint="1:1 · 300×300+"
                    onChange={readImage(setSquareImage)}
                  />
                  <ImageInput
                    label="Portrait"
                    hint="4:5 · 480×600+"
                    onChange={readImage(setPortraitImage)}
                  />
                  <ImageInput
                    label="Tall portrait"
                    hint="9:16 · 600×1067+"
                    onChange={readImage(setTallPortraitImage)}
                  />
                  <ImageInput
                    label="Square logo"
                    hint="1:1 · 128×128+"
                    required
                    onChange={readImage(setLogoImage)}
                  />
                </div>
              </div>
            </>
          ) : (
            <>
              <div className="grid gap-4 sm:grid-cols-2">
                <label className="text-xs font-semibold text-slate-700">
                  Carousel headline
                  <input
                    className="form-input mt-2"
                    maxLength={40}
                    value={headline}
                    required
                    onChange={(event) => setHeadline(event.target.value)}
                  />
                </label>
                <label className="text-xs font-semibold text-slate-700">
                  Carousel description
                  <input
                    className="form-input mt-2"
                    maxLength={90}
                    value={description}
                    required
                    onChange={(event) => setDescription(event.target.value)}
                  />
                </label>
                <div className="sm:col-span-2">
                  <ImageInput
                    label="Carousel logo"
                    hint="1:1 · 128×128+"
                    required
                    onChange={readImage(setLogoImage)}
                  />
                </div>
              </div>

              <div>
                <div className="flex items-center justify-between gap-3">
                  <div>
                    <h3 className="text-sm font-bold text-slate-950">Carousel cards</h3>
                    <p className="mt-1 text-xs text-slate-500">Google requires 2–10 cards.</p>
                  </div>
                  {cards.length < 10 && (
                    <button
                      type="button"
                      className="secondary-button inline-flex items-center gap-2 text-xs"
                      onClick={() => setCards((current) => [...current, newCard()])}
                    >
                      <FiPlus aria-hidden="true" />
                      Add card
                    </button>
                  )}
                </div>

                <div className="mt-4 space-y-4">
                  {cards.map((card, index) => (
                    <div key={card.id} className="rounded-xl border border-slate-200 p-4">
                      <div className="flex items-center justify-between gap-3">
                        <p className="text-xs font-bold text-slate-800">Card {index + 1}</p>
                        {cards.length > 2 && (
                          <button
                            type="button"
                            className="rounded-lg p-2 text-slate-400 hover:bg-red-50 hover:text-red-600"
                            onClick={() =>
                              setCards((current) => current.filter((item) => item.id !== card.id))
                            }
                          >
                            <FiTrash2 aria-hidden="true" />
                          </button>
                        )}
                      </div>
                      <div className="mt-3 grid gap-3 sm:grid-cols-2">
                        <label className="text-xs font-semibold text-slate-700">
                          Headline
                          <input
                            className="form-input mt-2"
                            maxLength={40}
                            value={card.headline}
                            required
                            onChange={(event) =>
                              updateCard(card.id, { headline: event.target.value })
                            }
                          />
                        </label>
                        <label className="text-xs font-semibold text-slate-700">
                          Card CTA
                          <input
                            className="form-input mt-2"
                            value={card.callToActionText ?? ''}
                            onChange={(event) =>
                              updateCard(card.id, {
                                callToActionText: event.target.value,
                              })
                            }
                          />
                        </label>
                      </div>
                      <div className="mt-3 grid gap-3 md:grid-cols-3">
                        <ImageInput
                          label="Landscape"
                          hint="1.91:1"
                          onChange={readCardImage(card.id, 'marketingImageBase64')}
                        />
                        <ImageInput
                          label="Square"
                          hint="1:1"
                          onChange={readCardImage(card.id, 'squareMarketingImageBase64')}
                        />
                        <ImageInput
                          label="Portrait"
                          hint="4:5"
                          onChange={readCardImage(card.id, 'portraitMarketingImageBase64')}
                        />
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            </>
          )}



          <div className="flex justify-end gap-2 border-t border-slate-100 pt-5">
            <button type="button" className="secondary-button" disabled={saving} onClick={onClose}>
              Cancel
            </button>
            <button type="submit" className="primary-button" disabled={saving || !canSubmit}>
              {saving
                ? 'Creating…'
                : `Create paused ${format === 'MULTI_ASSET' ? 'Multi-Asset' : 'Carousel'} ad`}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}

function ImageInput({
  label,
  hint,
  required = false,
  onChange,
}: {
  label: string
  hint: string
  required?: boolean
  onChange: (event: ChangeEvent<HTMLInputElement>) => void
}) {
  return (
    <label className="rounded-xl border border-dashed border-slate-300 p-3 text-xs font-semibold text-slate-700">
      <span className="flex items-center gap-2">
        <FiUpload aria-hidden="true" />
        {label}
      </span>
      <span className="mt-1 block font-normal text-slate-400">{hint}</span>
      <input
        className="mt-2 block w-full text-xs"
        type="file"
        accept="image/png,image/jpeg,image/gif"
        required={required}
        onChange={onChange}
      />
    </label>
  )
}
