import { useEffect, useMemo, useState, type FormEvent } from 'react'
import { FiX } from 'react-icons/fi'
import type {
  GoogleMerchantDataSource,
  GoogleMerchantProduct,
  UpsertGoogleMerchantProductInput,
} from '../../features/ads/types'

type Props = {
  open: boolean
  saving: boolean
  product: GoogleMerchantProduct | null
  dataSources: GoogleMerchantDataSource[]
  defaultCurrency?: string | null
  onClose: () => void
  onSave: (input: UpsertGoogleMerchantProductInput) => Promise<void>
}

const priceFromMicros = (value?: string | null) => {
  if (!value) return ''
  const numeric = Number(value)
  return Number.isFinite(numeric) ? String(numeric / 1_000_000) : ''
}

export default function GoogleMerchantProductModal({
  open,
  saving,
  product,
  dataSources,
  defaultCurrency,
  onClose,
  onSave,
}: Props) {
  const apiSources = useMemo(() => dataSources.filter((source) => source.apiManaged), [dataSources])
  const [dataSourceName, setDataSourceName] = useState('')
  const [offerId, setOfferId] = useState('')
  const [language, setLanguage] = useState('en')
  const [feedLabel, setFeedLabel] = useState('IN')
  const [title, setTitle] = useState('')
  const [description, setDescription] = useState('')
  const [link, setLink] = useState('')
  const [imageLink, setImageLink] = useState('')
  const [availability, setAvailability] =
    useState<UpsertGoogleMerchantProductInput['availability']>('IN_STOCK')
  const [condition, setCondition] = useState<UpsertGoogleMerchantProductInput['condition']>('NEW')
  const [brand, setBrand] = useState('')
  const [price, setPrice] = useState('')
  const [currency, setCurrency] = useState(defaultCurrency || 'INR')
  const [gtins, setGtins] = useState('')
  const [category, setCategory] = useState('')

  useEffect(() => {
    if (!open) return
    const source = product?.dataSource ?? apiSources[0]?.name ?? ''
    const sourceRecord = apiSources.find((item) => item.name === source)
    setDataSourceName(source)
    setOfferId(product?.offerId ?? '')
    setLanguage(product?.contentLanguage ?? sourceRecord?.contentLanguage ?? 'en')
    setFeedLabel(product?.feedLabel ?? sourceRecord?.feedLabel ?? 'IN')
    setTitle(product?.title ?? '')
    setDescription(product?.description ?? '')
    setLink(product?.link ?? '')
    setImageLink(product?.imageLink ?? '')
    setAvailability(
      (product?.availability as UpsertGoogleMerchantProductInput['availability']) ?? 'IN_STOCK',
    )
    setCondition((product?.condition as UpsertGoogleMerchantProductInput['condition']) ?? 'NEW')
    setBrand(product?.brand ?? '')
    setPrice(priceFromMicros(product?.priceAmountMicros))
    setCurrency(product?.priceCurrency ?? defaultCurrency ?? 'INR')
    setGtins(product?.gtins?.join('\n') ?? '')
    setCategory(product?.googleProductCategory ?? '')
  }, [open, product, dataSources, defaultCurrency])

  if (!open) return null

  const chooseSource = (value: string) => {
    const source = apiSources.find((item) => item.name === value)
    setDataSourceName(value)
    if (!product && source?.contentLanguage) setLanguage(source.contentLanguage)
    if (!product && source?.feedLabel) setFeedLabel(source.feedLabel)
  }

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    await onSave({
      dataSourceName,
      offerId: offerId.trim(),
      contentLanguage: language.trim().toLowerCase(),
      feedLabel: feedLabel.trim().toUpperCase(),
      title: title.trim(),
      description: description.trim() || null,
      link: link.trim(),
      imageLink: imageLink.trim(),
      availability,
      condition,
      brand: brand.trim() || null,
      price: price ? Number(price) : null,
      currency: price ? currency.trim().toUpperCase() : null,
      gtins: gtins
        .split(/[\s,]+/)
        .map((value) => value.trim())
        .filter(Boolean),
      googleProductCategory: category.trim() || null,
    })
  }

  return (
    <div className="mobile-modal-shell fixed inset-0 z-50 flex items-center justify-center bg-slate-950/40 p-4">
      <form
        className="mobile-sheet max-h-[92vh] w-full max-w-3xl overflow-y-auto rounded-2xl bg-white p-6 shadow-2xl"
        onSubmit={submit}
      >
        <div className="flex items-start justify-between">
          <div>
            <p className="eyebrow">Merchant catalog</p>
            <h2 className="mt-1 text-xl font-bold text-slate-950">
              {product ? 'Update product' : 'Add product'}
            </h2>
          </div>
          <button type="button" className="rounded-lg p-2 text-slate-400" onClick={onClose}>
            <FiX aria-hidden="true" />
          </button>
        </div>

        <div className="mt-5 grid gap-4 sm:grid-cols-2">
          <label className="text-xs font-semibold text-slate-700 sm:col-span-2">
            API data source
            <select
              className="form-input mt-2"
              value={dataSourceName}
              required
              disabled={Boolean(product)}
              onChange={(event) => chooseSource(event.target.value)}
            >
              <option value="">Choose data source</option>
              {apiSources.map((source) => (
                <option key={source.name} value={source.name}>
                  {source.displayName || source.dataSourceId}
                </option>
              ))}
            </select>
          </label>
          <label className="text-xs font-semibold text-slate-700">
            Offer ID
            <input
              className="form-input mt-2"
              value={offerId}
              required
              disabled={Boolean(product)}
              onChange={(event) => setOfferId(event.target.value)}
            />
          </label>
          <label className="text-xs font-semibold text-slate-700">
            Title
            <input
              className="form-input mt-2"
              value={title}
              required
              onChange={(event) => setTitle(event.target.value)}
            />
          </label>
          <label className="text-xs font-semibold text-slate-700">
            Content language
            <input
              className="form-input mt-2"
              value={language}
              required
              disabled={Boolean(product)}
              onChange={(event) => setLanguage(event.target.value)}
            />
          </label>
          <label className="text-xs font-semibold text-slate-700">
            Feed label
            <input
              className="form-input mt-2"
              value={feedLabel}
              required
              disabled={Boolean(product)}
              onChange={(event) => setFeedLabel(event.target.value)}
            />
          </label>
          <label className="text-xs font-semibold text-slate-700 sm:col-span-2">
            Description
            <textarea
              className="form-input mt-2 min-h-24"
              value={description}
              onChange={(event) => setDescription(event.target.value)}
            />
          </label>
          <label className="text-xs font-semibold text-slate-700 sm:col-span-2">
            Landing page
            <input
              type="url"
              className="form-input mt-2"
              value={link}
              required
              onChange={(event) => setLink(event.target.value)}
            />
          </label>
          <label className="text-xs font-semibold text-slate-700 sm:col-span-2">
            Image URL
            <input
              type="url"
              className="form-input mt-2"
              value={imageLink}
              required
              onChange={(event) => setImageLink(event.target.value)}
            />
          </label>
          <label className="text-xs font-semibold text-slate-700">
            Availability
            <select
              className="form-input mt-2"
              value={availability}
              onChange={(event) =>
                setAvailability(
                  event.target.value as UpsertGoogleMerchantProductInput['availability'],
                )
              }
            >
              <option value="IN_STOCK">In stock</option>
              <option value="OUT_OF_STOCK">Out of stock</option>
              <option value="PREORDER">Preorder</option>
              <option value="BACKORDER">Backorder</option>
            </select>
          </label>
          <label className="text-xs font-semibold text-slate-700">
            Condition
            <select
              className="form-input mt-2"
              value={condition}
              onChange={(event) =>
                setCondition(event.target.value as UpsertGoogleMerchantProductInput['condition'])
              }
            >
              <option value="NEW">New</option>
              <option value="REFURBISHED">Refurbished</option>
              <option value="USED">Used</option>
            </select>
          </label>
          <label className="text-xs font-semibold text-slate-700">
            Brand
            <input
              className="form-input mt-2"
              value={brand}
              onChange={(event) => setBrand(event.target.value)}
            />
          </label>
          <label className="text-xs font-semibold text-slate-700">
            Google product category
            <input
              className="form-input mt-2"
              value={category}
              onChange={(event) => setCategory(event.target.value)}
            />
          </label>
          <label className="text-xs font-semibold text-slate-700">
            Price
            <input
              type="number"
              min="0.01"
              step="0.01"
              className="form-input mt-2"
              value={price}
              onChange={(event) => setPrice(event.target.value)}
            />
          </label>
          <label className="text-xs font-semibold text-slate-700">
            Currency
            <input
              className="form-input mt-2"
              maxLength={3}
              value={currency}
              disabled={!price}
              onChange={(event) => setCurrency(event.target.value)}
            />
          </label>
          <label className="text-xs font-semibold text-slate-700 sm:col-span-2">
            GTINs
            <textarea
              className="form-input mt-2 min-h-20"
              value={gtins}
              placeholder="One per line"
              onChange={(event) => setGtins(event.target.value)}
            />
          </label>
        </div>

        <div className="mt-6 flex justify-end gap-2 border-t border-slate-100 pt-5">
          <button type="button" className="secondary-button" onClick={onClose}>
            Cancel
          </button>
          <button type="submit" className="primary-button" disabled={saving || !dataSourceName}>
            {saving ? 'Saving…' : product ? 'Update product' : 'Add product'}
          </button>
        </div>
      </form>
    </div>
  )
}
