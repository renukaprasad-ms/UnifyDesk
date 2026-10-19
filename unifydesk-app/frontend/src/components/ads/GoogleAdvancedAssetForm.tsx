import PhoneInput from '../ui/PhoneInput'
import { useState, type FormEvent } from 'react'
import { FiPlus, FiTrash2 } from 'react-icons/fi'
import { adCenterApi } from '../../features/ads/adCenterApi'
import type {
  CreateGoogleAdvancedReusableAssetInput,
  GooglePriceOfferingInput,
  GoogleReusableAsset,
} from '../../features/ads/types'
import { apiErrorMessage } from '../../lib/api/apiClient'

type Props = {
  companyId: string
  accountId: string
  onCreated: (asset: GoogleReusableAsset) => void
}

type AdvancedType = CreateGoogleAdvancedReusableAssetInput['type']

const emptyOffering = (): GooglePriceOfferingInput => ({
  header: '',
  description: '',
  amount: 0,
  currencyCode: 'INR',
  unit: null,
  finalUrl: '',
  finalMobileUrl: null,
})

const LEAD_FIELDS = [
  'FULL_NAME',
  'EMAIL',
  'PHONE_NUMBER',
  'WORK_EMAIL',
  'COMPANY_NAME',
  'WORK_PHONE',
  'JOB_TITLE',
  'POSTAL_CODE',
  'CITY',
  'REGION',
  'COUNTRY',
] as const

const CTA_TYPES = [
  'LEARN_MORE',
  'GET_QUOTE',
  'APPLY_NOW',
  'SIGN_UP',
  'CONTACT_US',
  'SUBSCRIBE',
  'DOWNLOAD',
  'BOOK_NOW',
  'GET_OFFER',
  'REGISTER',
  'GET_INFO',
  'REQUEST_DEMO',
  'JOIN_NOW',
  'GET_STARTED',
] as const

const PRICE_TYPES = [
  'BRANDS',
  'EVENTS',
  'LOCATIONS',
  'NEIGHBORHOODS',
  'PRODUCT_CATEGORIES',
  'PRODUCT_TIERS',
  'SERVICES',
  'SERVICE_CATEGORIES',
  'SERVICE_TIERS',
] as const

const PRICE_UNITS = [
  '',
  'PER_HOUR',
  'PER_DAY',
  'PER_WEEK',
  'PER_MONTH',
  'PER_YEAR',
  'PER_NIGHT',
] as const

const pretty = (value: string) =>
  value
    .replaceAll('_', ' ')
    .toLowerCase()
    .replace(/\b\w/g, (character) => character.toUpperCase())

export default function GoogleAdvancedAssetForm({ companyId, accountId, onCreated }: Props) {
  const [type, setType] = useState<AdvancedType>('CALL')
  const [name, setName] = useState('')
  const [working, setWorking] = useState(false)
  const [error, setError] = useState('')

  const [callCountry, setCallCountry] = useState('IN')
  const [phone, setPhone] = useState('')

  const [priceType, setPriceType] = useState('SERVICES')
  const [priceQualifier, setPriceQualifier] = useState('')
  const [priceLanguage, setPriceLanguage] = useState('en')
  const [priceOfferings, setPriceOfferings] = useState<GooglePriceOfferingInput[]>([
    emptyOffering(),
    emptyOffering(),
    emptyOffering(),
  ])

  const [promotionTarget, setPromotionTarget] = useState('')
  const [promotionDiscountKind, setPromotionDiscountKind] = useState<'PERCENT' | 'AMOUNT'>(
    'PERCENT',
  )
  const [promotionValue, setPromotionValue] = useState('')
  const [promotionCurrency, setPromotionCurrency] = useState('INR')
  const [promotionModifier, setPromotionModifier] = useState('')
  const [promotionCode, setPromotionCode] = useState('')
  const [promotionOccasion, setPromotionOccasion] = useState('')
  const [promotionLanguage, setPromotionLanguage] = useState('en')
  const [promotionUrl, setPromotionUrl] = useState('')

  const [leadBusinessName, setLeadBusinessName] = useState('')
  const [leadCta, setLeadCta] = useState('GET_QUOTE')
  const [leadCtaDescription, setLeadCtaDescription] = useState('')
  const [leadHeadline, setLeadHeadline] = useState('')
  const [leadDescription, setLeadDescription] = useState('')
  const [leadPrivacyUrl, setLeadPrivacyUrl] = useState('')
  const [leadFinalUrl, setLeadFinalUrl] = useState('')
  const [leadFields, setLeadFields] = useState<string[]>(['FULL_NAME', 'EMAIL', 'PHONE_NUMBER'])
  const [leadPostCta, setLeadPostCta] = useState('VISIT_SITE')
  const [leadPostHeadline, setLeadPostHeadline] = useState('')
  const [leadPostDescription, setLeadPostDescription] = useState('')

  const updateOffering = (index: number, patch: Partial<GooglePriceOfferingInput>) => {
    setPriceOfferings((current) =>
      current.map((item, itemIndex) => (itemIndex === index ? { ...item, ...patch } : item)),
    )
  }

  const toggleLeadField = (field: string) => {
    setLeadFields((current) =>
      current.includes(field) ? current.filter((item) => item !== field) : [...current, field],
    )
  }

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    setWorking(true)
    setError('')
    try {
      const numericPromotion = Number(promotionValue)
      const input: CreateGoogleAdvancedReusableAssetInput = {
        type,
        name: name.trim() || null,
        ...(type === 'CALL'
          ? {
              callCountryCode: callCountry.trim(),
              callPhoneNumber: phone.trim(),
            }
          : {}),
        ...(type === 'PRICE'
          ? {
              priceType,
              priceQualifier: priceQualifier || null,
              languageCode: priceLanguage.trim(),
              priceOfferings: priceOfferings.map((item) => ({
                ...item,
                header: item.header.trim(),
                description: item.description.trim(),
                currencyCode: item.currencyCode.trim().toUpperCase(),
                unit: item.unit || null,
                finalUrl: item.finalUrl.trim(),
                finalMobileUrl: item.finalMobileUrl?.trim() || null,
                amount: Number(item.amount),
              })),
            }
          : {}),
        ...(type === 'PROMOTION'
          ? {
              promotionTarget: promotionTarget.trim(),
              promotionPercentOff:
                promotionDiscountKind === 'PERCENT' && Number.isFinite(numericPromotion)
                  ? numericPromotion
                  : null,
              promotionMoneyAmountOff:
                promotionDiscountKind === 'AMOUNT' && Number.isFinite(numericPromotion)
                  ? numericPromotion
                  : null,
              promotionCurrencyCode:
                promotionDiscountKind === 'AMOUNT' ? promotionCurrency.trim().toUpperCase() : null,
              promotionDiscountModifier: promotionModifier || null,
              promotionCode: promotionCode.trim() || null,
              promotionOccasion: promotionOccasion.trim() || null,
              promotionLanguageCode: promotionLanguage.trim() || null,
              finalUrl: promotionUrl.trim(),
            }
          : {}),
        ...(type === 'LEAD_FORM'
          ? {
              leadBusinessName: leadBusinessName.trim(),
              leadCallToActionType: leadCta,
              leadCallToActionDescription: leadCtaDescription.trim(),
              leadHeadline: leadHeadline.trim(),
              leadDescription: leadDescription.trim(),
              leadPrivacyPolicyUrl: leadPrivacyUrl.trim(),
              leadFields,
              leadPostSubmitCallToActionType: leadPostCta || null,
              leadPostSubmitHeadline: leadPostHeadline.trim() || null,
              leadPostSubmitDescription: leadPostDescription.trim() || null,
              finalUrl: leadFinalUrl.trim(),
            }
          : {}),
      }

      const created = await adCenterApi.createGoogleAdvancedReusableAsset(
        companyId,
        accountId,
        input,
      )
      onCreated(created)
      setName('')
      if (type === 'CALL') setPhone('')
      if (type === 'PROMOTION') {
        setPromotionTarget('')
        setPromotionValue('')
        setPromotionCode('')
        setPromotionUrl('')
      }
      if (type === 'LEAD_FORM') {
        setLeadHeadline('')
        setLeadDescription('')
      }
    } catch (createError) {
      setError(apiErrorMessage(createError, 'Unable to create Google extension asset'))
    } finally {
      setWorking(false)
    }
  }

  return (
    <form className="mt-4 rounded-xl border border-slate-200 bg-white p-4" onSubmit={submit}>
      <div>
        <h4 className="text-sm font-bold text-slate-900">Advanced extension</h4>
        <p className="mt-1 text-[11px] leading-5 text-slate-500">
          Call, price and promotion assets can be reused across account, campaign and ad-group
          levels. Lead forms are campaign-only.
        </p>
      </div>

      {error && (
        <div className="mt-3 rounded-lg border border-red-100 bg-red-50 px-3 py-2 text-xs font-medium text-red-700">
          {error}
        </div>
      )}

      <div className="mt-4 grid gap-3 sm:grid-cols-2">
        <label className="text-xs font-semibold text-slate-700">
          Type
          <select
            className="form-input mt-2"
            value={type}
            onChange={(event) => setType(event.target.value as AdvancedType)}
          >
            <option value="CALL">Call</option>
            <option value="PRICE">Price</option>
            <option value="PROMOTION">Promotion</option>
            <option value="LEAD_FORM">Lead form</option>
          </select>
        </label>
        <label className="text-xs font-semibold text-slate-700">
          Internal name
          <input
            className="form-input mt-2"
            value={name}
            maxLength={128}
            onChange={(event) => setName(event.target.value)}
            placeholder="Optional management label"
          />
        </label>

        {type === 'CALL' && (
          <>
            <label className="text-xs font-semibold text-slate-700">
              Country code
              <input
                className="form-input mt-2"
                value={callCountry}
                maxLength={2}
                required
                onChange={(event) => setCallCountry(event.target.value.toUpperCase())}
                placeholder="IN"
              />
            </label>
            <label className="text-xs font-semibold text-slate-700">
              Phone number
              <PhoneInput
                className="form-input mt-2"
                value={phone}
                required
                onChange={(value) => setPhone(value)}
                placeholder="+91 98765 43210"
              />
            </label>
          </>
        )}

        {type === 'PRICE' && (
          <>
            <label className="text-xs font-semibold text-slate-700">
              Price type
              <select
                className="form-input mt-2"
                value={priceType}
                onChange={(event) => setPriceType(event.target.value)}
              >
                {PRICE_TYPES.map((value) => (
                  <option key={value} value={value}>
                    {pretty(value)}
                  </option>
                ))}
              </select>
            </label>
            <label className="text-xs font-semibold text-slate-700">
              Qualifier
              <select
                className="form-input mt-2"
                value={priceQualifier}
                onChange={(event) => setPriceQualifier(event.target.value)}
              >
                <option value="">None</option>
                <option value="FROM">From</option>
                <option value="UP_TO">Up to</option>
                <option value="AVERAGE">Average</option>
              </select>
            </label>
            <label className="text-xs font-semibold text-slate-700">
              Language
              <input
                className="form-input mt-2"
                value={priceLanguage}
                required
                onChange={(event) => setPriceLanguage(event.target.value)}
                placeholder="en"
              />
            </label>
            <div className="sm:col-span-2">
              <div className="flex items-center justify-between gap-3">
                <p className="text-xs font-semibold text-slate-700">Price offerings</p>
                <button
                  type="button"
                  className="secondary-button"
                  disabled={priceOfferings.length >= 8}
                  onClick={() => setPriceOfferings((current) => [...current, emptyOffering()])}
                >
                  Add offering
                </button>
              </div>
              <div className="mt-2 grid gap-3">
                {priceOfferings.map((offering, index) => (
                  <div
                    key={index}
                    className="grid gap-2 rounded-xl border border-slate-200 bg-slate-50 p-3 sm:grid-cols-2 lg:grid-cols-4"
                  >
                    <input
                      className="form-input"
                      value={offering.header}
                      maxLength={25}
                      required
                      placeholder="Header"
                      onChange={(event) => updateOffering(index, { header: event.target.value })}
                    />
                    <input
                      className="form-input"
                      value={offering.description}
                      maxLength={25}
                      required
                      placeholder="Description"
                      onChange={(event) =>
                        updateOffering(index, { description: event.target.value })
                      }
                    />
                    <input
                      className="form-input"
                      type="number"
                      min="0.000001"
                      step="0.01"
                      value={offering.amount || ''}
                      required
                      placeholder="Price"
                      onChange={(event) =>
                        updateOffering(index, {
                          amount: Number(event.target.value),
                        })
                      }
                    />
                    <div className="flex gap-2">
                      <input
                        className="form-input min-w-0"
                        value={offering.currencyCode}
                        maxLength={3}
                        required
                        placeholder="INR"
                        onChange={(event) =>
                          updateOffering(index, {
                            currencyCode: event.target.value.toUpperCase(),
                          })
                        }
                      />
                      <button
                        type="button"
                        className="rounded-lg p-2 text-slate-400 hover:bg-red-50 hover:text-red-600"
                        disabled={priceOfferings.length <= 3}
                        onClick={() =>
                          setPriceOfferings((current) =>
                            current.filter((_, itemIndex) => itemIndex !== index),
                          )
                        }
                        aria-label="Remove price offering"
                      >
                        <FiTrash2 aria-hidden="true" />
                      </button>
                    </div>
                    <select
                      className="form-input"
                      value={offering.unit ?? ''}
                      onChange={(event) =>
                        updateOffering(index, {
                          unit: event.target.value || null,
                        })
                      }
                    >
                      {PRICE_UNITS.map((value) => (
                        <option key={value || 'none'} value={value}>
                          {value ? pretty(value) : 'No unit'}
                        </option>
                      ))}
                    </select>
                    <input
                      className="form-input sm:col-span-2 lg:col-span-3"
                      value={offering.finalUrl}
                      required
                      placeholder="https://example.com/service"
                      onChange={(event) => updateOffering(index, { finalUrl: event.target.value })}
                    />
                  </div>
                ))}
              </div>
            </div>
          </>
        )}

        {type === 'PROMOTION' && (
          <>
            <label className="text-xs font-semibold text-slate-700">
              Promotion target
              <input
                className="form-input mt-2"
                value={promotionTarget}
                required
                onChange={(event) => setPromotionTarget(event.target.value)}
                placeholder="Annual plan"
              />
            </label>
            <label className="text-xs font-semibold text-slate-700">
              Discount
              <div className="mt-2 flex gap-2">
                <select
                  className="form-input w-32"
                  value={promotionDiscountKind}
                  onChange={(event) =>
                    setPromotionDiscountKind(event.target.value as 'PERCENT' | 'AMOUNT')
                  }
                >
                  <option value="PERCENT">Percent</option>
                  <option value="AMOUNT">Amount</option>
                </select>
                <input
                  className="form-input min-w-0 flex-1"
                  type="number"
                  min="0.000001"
                  step="0.01"
                  value={promotionValue}
                  required
                  onChange={(event) => setPromotionValue(event.target.value)}
                />
              </div>
            </label>
            {promotionDiscountKind === 'AMOUNT' && (
              <label className="text-xs font-semibold text-slate-700">
                Currency
                <input
                  className="form-input mt-2"
                  value={promotionCurrency}
                  maxLength={3}
                  required
                  onChange={(event) => setPromotionCurrency(event.target.value.toUpperCase())}
                />
              </label>
            )}
            <label className="text-xs font-semibold text-slate-700">
              Modifier
              <select
                className="form-input mt-2"
                value={promotionModifier}
                onChange={(event) => setPromotionModifier(event.target.value)}
              >
                <option value="">None</option>
                <option value="UP_TO">Up to</option>
              </select>
            </label>
            <label className="text-xs font-semibold text-slate-700">
              Promotion code
              <input
                className="form-input mt-2"
                value={promotionCode}
                onChange={(event) => setPromotionCode(event.target.value)}
                placeholder="SAVE20"
              />
            </label>
            <label className="text-xs font-semibold text-slate-700">
              Occasion
              <input
                className="form-input mt-2"
                value={promotionOccasion}
                onChange={(event) => setPromotionOccasion(event.target.value.toUpperCase())}
                placeholder="DIWALI"
              />
            </label>
            <label className="text-xs font-semibold text-slate-700">
              Language
              <input
                className="form-input mt-2"
                value={promotionLanguage}
                onChange={(event) => setPromotionLanguage(event.target.value)}
                placeholder="en"
              />
            </label>
            <label className="text-xs font-semibold text-slate-700 sm:col-span-2">
              Final URL
              <input
                className="form-input mt-2"
                value={promotionUrl}
                required
                onChange={(event) => setPromotionUrl(event.target.value)}
                placeholder="https://example.com/offer"
              />
            </label>
          </>
        )}

        {type === 'LEAD_FORM' && (
          <>
            <label className="text-xs font-semibold text-slate-700">
              Business name
              <input
                className="form-input mt-2"
                value={leadBusinessName}
                required
                onChange={(event) => setLeadBusinessName(event.target.value)}
              />
            </label>
            <label className="text-xs font-semibold text-slate-700">
              CTA
              <select
                className="form-input mt-2"
                value={leadCta}
                onChange={(event) => setLeadCta(event.target.value)}
              >
                {CTA_TYPES.map((value) => (
                  <option key={value} value={value}>
                    {pretty(value)}
                  </option>
                ))}
              </select>
            </label>
            <label className="text-xs font-semibold text-slate-700 sm:col-span-2">
              CTA description
              <input
                className="form-input mt-2"
                value={leadCtaDescription}
                maxLength={200}
                required
                onChange={(event) => setLeadCtaDescription(event.target.value)}
              />
            </label>
            <label className="text-xs font-semibold text-slate-700">
              Headline
              <input
                className="form-input mt-2"
                value={leadHeadline}
                maxLength={30}
                required
                onChange={(event) => setLeadHeadline(event.target.value)}
              />
            </label>
            <label className="text-xs font-semibold text-slate-700">
              Description
              <input
                className="form-input mt-2"
                value={leadDescription}
                maxLength={200}
                required
                onChange={(event) => setLeadDescription(event.target.value)}
              />
            </label>
            <label className="text-xs font-semibold text-slate-700">
              Privacy policy URL
              <input
                className="form-input mt-2"
                value={leadPrivacyUrl}
                required
                onChange={(event) => setLeadPrivacyUrl(event.target.value)}
              />
            </label>
            <label className="text-xs font-semibold text-slate-700">
              Post-submit URL
              <input
                className="form-input mt-2"
                value={leadFinalUrl}
                required
                onChange={(event) => setLeadFinalUrl(event.target.value)}
              />
            </label>
            <div className="sm:col-span-2">
              <p className="text-xs font-semibold text-slate-700">Form fields</p>
              <div className="mt-2 flex flex-wrap gap-2">
                {LEAD_FIELDS.map((field) => (
                  <label
                    key={field}
                    className="inline-flex items-center gap-2 rounded-full border border-slate-200 bg-white px-3 py-1.5 text-[11px] font-semibold text-slate-600"
                  >
                    <input
                      type="checkbox"
                      checked={leadFields.includes(field)}
                      onChange={() => toggleLeadField(field)}
                    />
                    {pretty(field)}
                  </label>
                ))}
              </div>
            </div>
            <label className="text-xs font-semibold text-slate-700">
              Post-submit CTA
              <select
                className="form-input mt-2"
                value={leadPostCta}
                onChange={(event) => setLeadPostCta(event.target.value)}
              >
                <option value="">None</option>
                <option value="VISIT_SITE">Visit site</option>
                <option value="DOWNLOAD">Download</option>
                <option value="LEARN_MORE">Learn more</option>
                <option value="SHOP_NOW">Shop now</option>
              </select>
            </label>
            <label className="text-xs font-semibold text-slate-700">
              Post-submit headline
              <input
                className="form-input mt-2"
                value={leadPostHeadline}
                maxLength={30}
                onChange={(event) => setLeadPostHeadline(event.target.value)}
              />
            </label>
            <label className="text-xs font-semibold text-slate-700 sm:col-span-2">
              Post-submit description
              <input
                className="form-input mt-2"
                value={leadPostDescription}
                maxLength={200}
                onChange={(event) => setLeadPostDescription(event.target.value)}
              />
            </label>
          </>
        )}
      </div>

      <div className="mt-4 flex justify-end">
        <button
          type="submit"
          className="primary-button inline-flex items-center gap-2"
          disabled={working}
        >
          <FiPlus aria-hidden="true" />
          {working ? 'Creating…' : `Create ${pretty(type)}`}
        </button>
      </div>
    </form>
  )
}
