import { useSnackbarState } from '../feedback/useSnackbarState'
import { useEffect, useMemo, useState, type ChangeEvent, type FormEvent } from 'react'
import { FiUpload, FiX } from 'react-icons/fi'
import type {
  AdAccount,
  CreateGooglePerformanceMaxCampaignInput,
  GoogleMerchantAccount,
} from '../../features/ads/types'
import { adCenterApi } from '../../features/ads/adCenterApi'

type Props = {
  companyId: string
  account: AdAccount
  open: boolean
  saving: boolean
  onClose: () => void
  onCreate: (input: CreateGooglePerformanceMaxCampaignInput) => Promise<void>
}

const toMinor = (value: string, currency?: string | null) => {
  const numeric = Number(value)
  if (!Number.isFinite(numeric) || numeric <= 0) return null
  if (!currency) return Math.round(numeric)
  try {
    const digits =
      new Intl.NumberFormat(undefined, { style: 'currency', currency }).resolvedOptions()
        .maximumFractionDigits ?? 2
    return Math.round(numeric * 10 ** digits)
  } catch {
    return Math.round(numeric)
  }
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

export default function GooglePerformanceMaxCampaignModal({
  companyId,
  account,
  open,
  saving,
  onClose,
  onCreate,
}: Props) {
  const [name, setName] = useState('')
  const [budget, setBudget] = useState('')
  const [finalUrl, setFinalUrl] = useState('')
  const [businessName, setBusinessName] = useState('')
  const [headlines, setHeadlines] = useState(['', '', ''])
  const [longHeadline, setLongHeadline] = useState('')
  const [descriptions, setDescriptions] = useState(['', ''])
  const [bidStrategy, setBidStrategy] =
    useState<CreateGooglePerformanceMaxCampaignInput['bidStrategy']>('MAXIMIZE_CONVERSIONS')
  const [containsEuPoliticalAdvertising, setContainsEuPoliticalAdvertising] = useState<
    'YES' | 'NO' | ''
  >('')
  const [startDate, setStartDate] = useState('')
  const [endDate, setEndDate] = useState('')
  const [landscapeImage, setLandscapeImage] = useState('')
  const [squareImage, setSquareImage] = useState('')
  const [logoImage, setLogoImage] = useState('')
  const [portraitImage, setPortraitImage] = useState('')
  const [landscapeLogoImage, setLandscapeLogoImage] = useState('')
  const [youtubeVideoIds, setYoutubeVideoIds] = useState('')
  const [brandGuidelinesEnabled, setBrandGuidelinesEnabled] = useState(true)
  const [brandMainColor, setBrandMainColor] = useState('')
  const [brandAccentColor, setBrandAccentColor] = useState('')
  const [brandFontFamily, setBrandFontFamily] = useState('')
  const [merchantAccounts, setMerchantAccounts] = useState<GoogleMerchantAccount[]>([])
  const [merchantId, setMerchantId] = useState('')
  const [feedLabel, setFeedLabel] = useState('')
  const [merchantLoading, setMerchantLoading] = useState(false)
  const [, setImageError] = useSnackbarState('', 'error')

  const canSubmit = useMemo(
    () =>
      Boolean(
        name.trim() &&
        budget &&
        finalUrl.trim() &&
        businessName.trim() &&
        headlines.every((value) => value.trim()) &&
        longHeadline.trim() &&
        descriptions.every((value) => value.trim()) &&
        landscapeImage &&
        squareImage &&
        logoImage &&
        containsEuPoliticalAdvertising,
      ),
    [
      name,
      budget,
      finalUrl,
      businessName,
      headlines,
      longHeadline,
      descriptions,
      landscapeImage,
      squareImage,
      logoImage,
      containsEuPoliticalAdvertising,
    ],
  )

  useEffect(() => {
    if (!open) return
    setMerchantLoading(true)
    void adCenterApi
      .googleMerchantAccounts(companyId, account.id)
      .then(setMerchantAccounts)
      .catch(() => setMerchantAccounts([]))
      .finally(() => setMerchantLoading(false))
  }, [open, companyId, account.id])

  if (!open) return null

  const readImage =
    (setter: (value: string) => void) => async (event: ChangeEvent<HTMLInputElement>) => {
      const file = event.target.files?.[0]
      if (!file) return
      setImageError('')
      if (file.size > 5 * 1024 * 1024) {
        setImageError('Each Performance Max image must be 5 MB or smaller.')
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
    const dailyBudgetMinor = toMinor(budget, account.currency)
    if (dailyBudgetMinor == null || !canSubmit) return

    await onCreate({
      name: name.trim(),
      dailyBudgetMinor,
      finalUrl: finalUrl.trim(),
      businessName: businessName.trim(),
      headlines: headlines.map((value) => value.trim()),
      longHeadline: longHeadline.trim(),
      descriptions: descriptions.map((value) => value.trim()),
      marketingImageBase64: landscapeImage,
      squareMarketingImageBase64: squareImage,
      logoImageBase64: logoImage,
      portraitMarketingImageBase64: portraitImage || null,
      landscapeLogoImageBase64: landscapeLogoImage || null,
      youtubeVideoIds: youtubeVideoIds
        .split(/[\n,]/)
        .map((value) => value.trim())
        .filter(Boolean),
      bidStrategy,
      containsEuPoliticalAdvertising: containsEuPoliticalAdvertising === 'YES',
      startDate: startDate || null,
      endDate: endDate || null,
      brandGuidelinesEnabled,
      brandMainColor: brandGuidelinesEnabled ? brandMainColor || null : null,
      brandAccentColor: brandGuidelinesEnabled ? brandAccentColor || null : null,
      brandFontFamily: brandGuidelinesEnabled ? brandFontFamily || null : null,
      merchantId: merchantId || null,
      feedLabel: merchantId && feedLabel.trim() ? feedLabel.trim().toUpperCase() : null,
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
              Create Performance Max campaign
            </h2>
            <p className="mt-1 text-sm text-slate-500">
              {account.name}. Campaign and first asset group are created paused.
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
          <div className="grid gap-4 sm:grid-cols-2">
            <label className="block text-xs font-semibold text-slate-700">
              Campaign name
              <input
                className="form-input mt-2"
                value={name}
                maxLength={255}
                required
                autoFocus
                onChange={(event) => setName(event.target.value)}
              />
            </label>

            <label className="block text-xs font-semibold text-slate-700">
              Daily budget {account.currency ? `(${account.currency})` : ''}
              <input
                className="form-input mt-2"
                type="number"
                min="0.01"
                step="0.01"
                value={budget}
                required
                onChange={(event) => setBudget(event.target.value)}
              />
            </label>

            <label className="block text-xs font-semibold text-slate-700 sm:col-span-2">
              Final URL
              <input
                className="form-input mt-2"
                type="url"
                placeholder="https://example.com"
                value={finalUrl}
                required
                onChange={(event) => setFinalUrl(event.target.value)}
              />
            </label>

            <label className="block text-xs font-semibold text-slate-700">
              Business name
              <input
                className="form-input mt-2"
                value={businessName}
                maxLength={25}
                required
                onChange={(event) => setBusinessName(event.target.value)}
              />
            </label>

            <label className="block text-xs font-semibold text-slate-700">
              Bid strategy
              <select
                className="form-input mt-2"
                value={bidStrategy}
                onChange={(event) =>
                  setBidStrategy(
                    event.target.value as CreateGooglePerformanceMaxCampaignInput['bidStrategy'],
                  )
                }
              >
                <option value="MAXIMIZE_CONVERSIONS">Maximize conversions</option>
                <option value="MAXIMIZE_CONVERSION_VALUE">Maximize conversion value</option>
              </select>
            </label>
          </div>

          <div className="rounded-2xl border border-emerald-100 bg-emerald-50/40 p-4">
            <h3 className="text-sm font-bold text-slate-950">Retail / Merchant Center</h3>
            <p className="mt-1 text-xs leading-5 text-slate-500">
              Optional. Select Merchant Center to create a retail Performance Max campaign.
              UnifyDesk creates the required all-products listing group atomically.
            </p>
            <div className="mt-4 grid gap-4 sm:grid-cols-2">
              <label className="block text-xs font-semibold text-slate-700">
                Merchant Center account
                <select
                  className="form-input mt-2"
                  value={merchantId}
                  disabled={merchantLoading}
                  onChange={(event) => {
                    setMerchantId(event.target.value)
                    if (!event.target.value) setFeedLabel('')
                  }}
                >
                  <option value="">
                    {merchantLoading ? 'Loading Merchant accounts…' : 'Standard Performance Max'}
                  </option>
                  {merchantAccounts.map((merchant) => (
                    <option key={merchant.accountId} value={merchant.accountId}>
                      {merchant.accountName || merchant.accountId}
                    </option>
                  ))}
                </select>
              </label>
              <label className="block text-xs font-semibold text-slate-700">
                Feed label
                <input
                  className="form-input mt-2"
                  value={feedLabel}
                  disabled={!merchantId}
                  maxLength={20}
                  placeholder="Optional, e.g. IN"
                  onChange={(event) => setFeedLabel(event.target.value.toUpperCase())}
                />
                <span className="mt-1 block font-normal text-slate-400">
                  Optional · uppercase letters, numbers, or dashes.
                </span>
              </label>
            </div>
          </div>

          <div className="rounded-2xl border border-indigo-100 bg-indigo-50/40 p-4">
            <div className="flex flex-wrap items-start justify-between gap-3">
              <div>
                <h3 className="text-sm font-bold text-slate-950">Brand guidelines</h3>
                <p className="mt-1 max-w-2xl text-xs leading-5 text-slate-500">
                  Recommended for new Performance Max campaigns. Google will use the business name
                  and logos at campaign level instead of asset-group level.
                </p>
              </div>
              <label className="inline-flex items-center gap-2 text-xs font-semibold text-slate-700">
                <input
                  type="checkbox"
                  checked={brandGuidelinesEnabled}
                  onChange={(event) => setBrandGuidelinesEnabled(event.target.checked)}
                />
                Enabled
              </label>
            </div>

            {brandGuidelinesEnabled && (
              <div className="mt-4 grid gap-4 sm:grid-cols-3">
                <label className="block text-xs font-semibold text-slate-700">
                  Main color
                  <input
                    className="form-input mt-2"
                    type="text"
                    placeholder="#3366FF"
                    value={brandMainColor}
                    onChange={(event) => setBrandMainColor(event.target.value)}
                  />
                </label>
                <label className="block text-xs font-semibold text-slate-700">
                  Accent color
                  <input
                    className="form-input mt-2"
                    type="text"
                    placeholder="#FFCC00"
                    value={brandAccentColor}
                    onChange={(event) => setBrandAccentColor(event.target.value)}
                  />
                </label>
                <label className="block text-xs font-semibold text-slate-700">
                  Brand font
                  <select
                    className="form-input mt-2"
                    value={brandFontFamily}
                    onChange={(event) => setBrandFontFamily(event.target.value)}
                  >
                    <option value="">Google default</option>
                    <option value="Open Sans">Open Sans</option>
                    <option value="Roboto">Roboto</option>
                    <option value="Montserrat">Montserrat</option>
                    <option value="Poppins">Poppins</option>
                    <option value="Lato">Lato</option>
                    <option value="Oswald">Oswald</option>
                    <option value="Playfair Display">Playfair Display</option>
                    <option value="Roboto Slab">Roboto Slab</option>
                  </select>
                </label>
                <p className="text-[11px] leading-5 text-slate-500 sm:col-span-3">
                  Main and accent colors are optional, but Google requires both when either is set.
                  Brand Guidelines cannot be disabled later after Google enables them.
                </p>
              </div>
            )}
          </div>

          <div>
            <h3 className="text-sm font-bold text-slate-950">Text assets</h3>
            <p className="mt-1 text-xs text-slate-500">
              Google requires at least three headlines, one long headline, and two descriptions.
            </p>

            <div className="mt-4 grid gap-4 sm:grid-cols-3">
              {headlines.map((headline, index) => (
                <label key={index} className="block text-xs font-semibold text-slate-700">
                  Headline {index + 1}
                  <input
                    className="form-input mt-2"
                    value={headline}
                    maxLength={30}
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
                <label key={index} className="block text-xs font-semibold text-slate-700">
                  Description {index + 1}
                  <textarea
                    className="form-input mt-2 min-h-24"
                    value={description}
                    maxLength={index === 0 ? 60 : 90}
                    required
                    onChange={(event) => setDescription(index, event.target.value)}
                  />
                  {index === 0 && (
                    <span className="mt-1 block font-normal text-slate-400">
                      Keep this description at 60 characters or fewer.
                    </span>
                  )}
                </label>
              ))}
            </div>
          </div>

          <div>
            <h3 className="text-sm font-bold text-slate-950">Image assets</h3>
            <p className="mt-1 text-xs text-slate-500">
              PNG, JPG or GIF. Maximum 5 MB each. Reachlyo validates dimensions before sending to
              Google.
            </p>

            <div className="mt-4 grid gap-4 md:grid-cols-3">
              <label className="rounded-xl border border-dashed border-slate-300 p-4 text-xs font-semibold text-slate-700">
                <span className="flex items-center gap-2">
                  <FiUpload aria-hidden="true" />
                  Landscape image
                </span>
                <span className="mt-1 block font-normal text-slate-400">
                  1.91:1, minimum 600×314
                </span>
                <input
                  className="mt-3 block w-full text-xs"
                  type="file"
                  accept="image/png,image/jpeg,image/gif"
                  required
                  onChange={readImage(setLandscapeImage)}
                />
              </label>

              <label className="rounded-xl border border-dashed border-slate-300 p-4 text-xs font-semibold text-slate-700">
                <span className="flex items-center gap-2">
                  <FiUpload aria-hidden="true" />
                  Square image
                </span>
                <span className="mt-1 block font-normal text-slate-400">1:1, minimum 300×300</span>
                <input
                  className="mt-3 block w-full text-xs"
                  type="file"
                  accept="image/png,image/jpeg,image/gif"
                  required
                  onChange={readImage(setSquareImage)}
                />
              </label>

              <label className="rounded-xl border border-dashed border-slate-300 p-4 text-xs font-semibold text-slate-700">
                <span className="flex items-center gap-2">
                  <FiUpload aria-hidden="true" />
                  Square logo
                </span>
                <span className="mt-1 block font-normal text-slate-400">1:1, minimum 128×128</span>
                <input
                  className="mt-3 block w-full text-xs"
                  type="file"
                  accept="image/png,image/jpeg,image/gif"
                  required
                  onChange={readImage(setLogoImage)}
                />
              </label>
            </div>

            <div className="mt-4 grid gap-4 md:grid-cols-2">
              <label className="rounded-xl border border-dashed border-slate-300 p-4 text-xs font-semibold text-slate-700">
                <span className="flex items-center gap-2">
                  <FiUpload aria-hidden="true" />
                  Portrait marketing image
                </span>
                <span className="mt-1 block font-normal text-slate-400">
                  Optional · 4:5 · minimum 480×600
                </span>
                <input
                  className="mt-3 block w-full text-xs"
                  type="file"
                  accept="image/png,image/jpeg,image/gif"
                  onChange={readImage(setPortraitImage)}
                />
              </label>

              <label className="rounded-xl border border-dashed border-slate-300 p-4 text-xs font-semibold text-slate-700">
                <span className="flex items-center gap-2">
                  <FiUpload aria-hidden="true" />
                  Landscape logo
                </span>
                <span className="mt-1 block font-normal text-slate-400">
                  Optional · 4:1 · minimum 512×128
                </span>
                <input
                  className="mt-3 block w-full text-xs"
                  type="file"
                  accept="image/png,image/jpeg,image/gif"
                  onChange={readImage(setLandscapeLogoImage)}
                />
              </label>
            </div>

            <label className="mt-4 block text-xs font-semibold text-slate-700">
              YouTube video IDs
              <textarea
                className="form-input mt-2 min-h-24"
                placeholder={'videoIdOne\nvideoIdTwo'}
                value={youtubeVideoIds}
                onChange={(event) => setYoutubeVideoIds(event.target.value)}
              />
              <span className="mt-1 block font-normal text-slate-400">
                Optional · one ID per line or comma-separated · up to 15 videos.
              </span>
            </label>


          </div>

          <div className="grid gap-4 sm:grid-cols-2">
            <label className="block text-xs font-semibold text-slate-700 sm:col-span-2">
              EU political advertising
              <select
                className="form-input mt-2"
                value={containsEuPoliticalAdvertising}
                required
                onChange={(event) =>
                  setContainsEuPoliticalAdvertising(event.target.value as 'YES' | 'NO' | '')
                }
              >
                <option value="">Select an answer</option>
                <option value="NO">
                  No, this campaign does not contain EU political advertising
                </option>
                <option value="YES">Yes, this campaign contains EU political advertising</option>
              </select>
              <span className="mt-1 block font-normal text-slate-400">
                Google requires an explicit declaration for every new campaign.
              </span>
            </label>

            <label className="block text-xs font-semibold text-slate-700">
              Start date
              <input
                className="form-input mt-2"
                type="date"
                value={startDate}
                onChange={(event) => setStartDate(event.target.value)}
              />
            </label>
            <label className="block text-xs font-semibold text-slate-700">
              End date
              <input
                className="form-input mt-2"
                type="date"
                value={endDate}
                onChange={(event) => setEndDate(event.target.value)}
              />
            </label>
          </div>

          <div className="rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-xs leading-5 text-amber-800">
            Reachlyo creates the budget, Performance Max campaign, assets, asset group, and required
            asset links atomically. Delivery remains paused until you review and start the campaign.
          </div>

          <div className="flex justify-end gap-2 border-t border-slate-100 pt-5">
            <button type="button" className="secondary-button" onClick={onClose} disabled={saving}>
              Cancel
            </button>
            <button type="submit" className="primary-button" disabled={saving || !canSubmit}>
              {saving ? 'Creating…' : 'Create paused Performance Max'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}
