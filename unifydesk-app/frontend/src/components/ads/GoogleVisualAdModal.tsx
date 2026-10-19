import { useSnackbarState } from '../feedback/useSnackbarState'
import { useMemo, useState, type ChangeEvent, type FormEvent } from 'react'
import { FiUpload, FiX } from 'react-icons/fi'
import type {
  CreateGoogleDemandGenVideoAdInput,
  CreateGoogleResponsiveDisplayAdInput,
} from '../../features/ads/types'

type Props = {
  mode: 'DISPLAY' | 'DEMAND_GEN'
  open: boolean
  saving: boolean
  onClose: () => void
  onCreateDisplay: (input: CreateGoogleResponsiveDisplayAdInput) => Promise<void>
  onCreateDemandGen: (input: CreateGoogleDemandGenVideoAdInput) => Promise<void>
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

export default function GoogleVisualAdModal({
  mode,
  open,
  saving,
  onClose,
  onCreateDisplay,
  onCreateDemandGen,
}: Props) {
  const [name, setName] = useState('')
  const [finalUrl, setFinalUrl] = useState('')
  const [businessName, setBusinessName] = useState('')
  const [headlines, setHeadlines] = useState(['', '', ''])
  const [longHeadline, setLongHeadline] = useState('')
  const [descriptions, setDescriptions] = useState(['', ''])
  const [landscapeImage, setLandscapeImage] = useState('')
  const [squareImage, setSquareImage] = useState('')
  const [logoImage, setLogoImage] = useState('')
  const [youtubeVideoId, setYoutubeVideoId] = useState('')
  const [, setImageError] = useSnackbarState('', 'error')

  const canSubmit = useMemo(
    () =>
      Boolean(
        name.trim() &&
        finalUrl.trim() &&
        businessName.trim() &&
        headlines.every((value) => value.trim()) &&
        longHeadline.trim() &&
        descriptions.every((value) => value.trim()) &&
        (mode === 'DISPLAY' ? landscapeImage && squareImage : logoImage && youtubeVideoId.trim()),
      ),
    [
      mode,
      name,
      finalUrl,
      businessName,
      headlines,
      longHeadline,
      descriptions,
      landscapeImage,
      squareImage,
      logoImage,
      youtubeVideoId,
    ],
  )

  if (!open) return null

  const readImage =
    (setter: (value: string) => void) => async (event: ChangeEvent<HTMLInputElement>) => {
      const file = event.target.files?.[0]
      if (!file) return
      setImageError('')
      if (file.size > 5 * 1024 * 1024) {
        setImageError('Each image must be 5 MB or smaller.')
        event.target.value = ''
        return
      }
      if (!['image/jpeg', 'image/png', 'image/gif'].includes(file.type)) {
        setImageError('Use a PNG, JPG, or GIF image.')
        event.target.value = ''
        return
      }
      try {
        setter(await fileToDataUrl(file))
      } catch (error) {
        setImageError(error instanceof Error ? error.message : 'Unable to read image file')
      }
    }

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    if (!canSubmit) return

    if (mode === 'DISPLAY') {
      await onCreateDisplay({
        name: name.trim(),
        finalUrl: finalUrl.trim(),
        businessName: businessName.trim(),
        headlines: headlines.map((value) => value.trim()),
        longHeadline: longHeadline.trim(),
        descriptions: descriptions.map((value) => value.trim()),
        marketingImageBase64: landscapeImage,
        squareMarketingImageBase64: squareImage,
        squareLogoImageBase64: logoImage || null,
      })
      return
    }

    await onCreateDemandGen({
      name: name.trim(),
      finalUrl: finalUrl.trim(),
      businessName: businessName.trim(),
      headlines: headlines.map((value) => value.trim()),
      longHeadlines: [longHeadline.trim()],
      descriptions: descriptions.map((value) => value.trim()),
      youtubeVideoId: youtubeVideoId.trim(),
      logoImageBase64: logoImage,
    })
  }

  const setHeadline = (index: number, value: string) => {
    setHeadlines((current) =>
      current.map((item, itemIndex) => (itemIndex === index ? value : item)),
    )
  }

  const setDescription = (index: number, value: string) => {
    setDescriptions((current) =>
      current.map((item, itemIndex) => (itemIndex === index ? value : item)),
    )
  }

  return (
    <div className="mobile-modal-shell fixed inset-0 z-50 flex items-center justify-center bg-slate-950/40 p-4">
      <div className="mobile-sheet max-h-[92vh] w-full max-w-3xl overflow-y-auto rounded-2xl bg-white shadow-2xl">
        <div className="sticky top-0 z-10 flex items-start justify-between border-b border-slate-100 bg-white px-6 py-5">
          <div>
            <p className="eyebrow">Google Ads</p>
            <h2 className="mt-1 text-xl font-bold text-slate-950">
              {mode === 'DISPLAY' ? 'Create responsive display ad' : 'Create Demand Gen video ad'}
            </h2>
            <p className="mt-1 text-sm text-slate-500">New ads are created paused for review.</p>
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
          <div className="grid gap-4 sm:grid-cols-2">
            <label className="text-xs font-semibold text-slate-700">
              Ad name
              <input
                className="form-input mt-2"
                value={name}
                required
                maxLength={255}
                autoFocus
                onChange={(event) => setName(event.target.value)}
              />
            </label>
            <label className="text-xs font-semibold text-slate-700">
              Business name
              <input
                className="form-input mt-2"
                value={businessName}
                required
                maxLength={25}
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
                placeholder="https://example.com"
                onChange={(event) => setFinalUrl(event.target.value)}
              />
            </label>
          </div>

          <div>
            <h3 className="text-sm font-bold text-slate-950">Creative text</h3>
            <div className="mt-4 grid gap-4 sm:grid-cols-3">
              {headlines.map((headline, index) => (
                <label key={index} className="text-xs font-semibold text-slate-700">
                  Headline {index + 1}
                  <input
                    className="form-input mt-2"
                    value={headline}
                    maxLength={mode === 'DISPLAY' ? 30 : 40}
                    required
                    onChange={(event) => setHeadline(index, event.target.value)}
                  />
                </label>
              ))}
            </div>
            <label className="mt-4 block text-xs font-semibold text-slate-700">
              Long headline
              <input
                className="form-input mt-2"
                value={longHeadline}
                maxLength={90}
                required
                onChange={(event) => setLongHeadline(event.target.value)}
              />
            </label>
            <div className="mt-4 grid gap-4 sm:grid-cols-2">
              {descriptions.map((description, index) => (
                <label key={index} className="text-xs font-semibold text-slate-700">
                  Description {index + 1}
                  <textarea
                    className="form-input mt-2 min-h-24"
                    value={description}
                    maxLength={90}
                    required
                    onChange={(event) => setDescription(index, event.target.value)}
                  />
                </label>
              ))}
            </div>
          </div>

          {mode === 'DISPLAY' ? (
            <div>
              <h3 className="text-sm font-bold text-slate-950">Display images</h3>
              <div className="mt-4 grid gap-4 md:grid-cols-3">
                <ImageInput
                  title="Landscape marketing"
                  hint="1.91:1 · minimum 600×314"
                  required
                  onChange={readImage(setLandscapeImage)}
                />
                <ImageInput
                  title="Square marketing"
                  hint="1:1 · minimum 300×300"
                  required
                  onChange={readImage(setSquareImage)}
                />
                <ImageInput
                  title="Square logo"
                  hint="Optional · 1:1 · minimum 128×128"
                  onChange={readImage(setLogoImage)}
                />
              </div>
            </div>
          ) : (
            <div className="grid gap-4 sm:grid-cols-2">
              <label className="text-xs font-semibold text-slate-700">
                YouTube video ID
                <input
                  className="form-input mt-2"
                  value={youtubeVideoId}
                  required
                  placeholder="dQw4w9WgXcQ"
                  onChange={(event) => setYoutubeVideoId(event.target.value)}
                />
              </label>
              <ImageInput
                title="Logo image"
                hint="1:1 · minimum 128×128"
                required
                onChange={readImage(setLogoImage)}
              />
            </div>
          )}



          <div className="flex justify-end gap-2 border-t border-slate-100 pt-5">
            <button type="button" className="secondary-button" onClick={onClose} disabled={saving}>
              Cancel
            </button>
            <button type="submit" className="primary-button" disabled={saving || !canSubmit}>
              {saving ? 'Creating…' : 'Create paused ad'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}

function ImageInput({
  title,
  hint,
  required = false,
  onChange,
}: {
  title: string
  hint: string
  required?: boolean
  onChange: (event: ChangeEvent<HTMLInputElement>) => void
}) {
  return (
    <label className="rounded-xl border border-dashed border-slate-300 p-4 text-xs font-semibold text-slate-700">
      <span className="flex items-center gap-2">
        <FiUpload aria-hidden="true" />
        {title}
      </span>
      <span className="mt-1 block font-normal text-slate-400">{hint}</span>
      <input
        className="mt-3 block w-full text-xs"
        type="file"
        accept="image/png,image/jpeg,image/gif"
        required={required}
        onChange={onChange}
      />
    </label>
  )
}
