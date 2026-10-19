import { useSnackbarState } from '../feedback/useSnackbarState'
import { useEffect, useState } from 'react'
import { FiRefreshCw, FiSave } from 'react-icons/fi'
import { adCenterApi } from '../../features/ads/adCenterApi'
import type { GoogleAssetGroup, GooglePerformanceMaxListingGroup } from '../../features/ads/types'
import { apiErrorMessage } from '../../lib/api/apiClient'

type Props = {
  companyId: string
  campaignId: string
  assetGroup: GoogleAssetGroup
  canModify: boolean
}

const DIMENSIONS = [
  ['ALL_PRODUCTS', 'All products'],
  ['BRAND', 'Brand'],
  ['CONDITION', 'Condition'],
  ['PRODUCT_TYPE', 'Product type'],
  ['CATEGORY', 'Google product category'],
  ['CUSTOM_LABEL', 'Custom label'],
  ['CHANNEL', 'Channel'],
  ['ITEM_ID', 'Item ID'],
] as const

const splitValues = (value: string) =>
  value
    .split(/[\r\n,]+/)
    .map((item) => item.trim())
    .filter(Boolean)

const format = (value?: string | null) =>
  value
    ? value
        .replaceAll('_', ' ')
        .toLowerCase()
        .replace(/\b\w/g, (character) => character.toUpperCase())
    : 'Other products'

export default function GooglePerformanceMaxListingGroups({
  companyId,
  campaignId,
  assetGroup,
  canModify,
}: Props) {
  const [retail, setRetail] = useState(false)
  const [merchantId, setMerchantId] = useState('')
  const [feedLabel, setFeedLabel] = useState('')
  const [nodes, setNodes] = useState<GooglePerformanceMaxListingGroup[]>([])
  const [dimension, setDimension] = useState('ALL_PRODUCTS')
  const [includedValues, setIncludedValues] = useState('')
  const [excludedValues, setExcludedValues] = useState('')
  const [level, setLevel] = useState('LEVEL1')
  const [customIndex, setCustomIndex] = useState('INDEX0')
  const [includeOther, setIncludeOther] = useState(true)
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [, setError] = useSnackbarState('', 'error')
  const [, setNotice] = useSnackbarState('', 'success')

  const load = async () => {
    if (!assetGroup.id) return
    setLoading(true)
    setError('')
    try {
      const settings = await adCenterApi.googlePerformanceMaxRetailSettings(companyId, campaignId)
      setRetail(settings.retail)
      setMerchantId(settings.merchantId ?? '')
      setFeedLabel(settings.feedLabel ?? '')
      if (!settings.retail) {
        setNodes([])
        return
      }
      const current = await adCenterApi.googlePerformanceMaxListingGroups(
        companyId,
        campaignId,
        assetGroup.id,
        30,
      )
      setNodes(current)
      const children = current.filter((node) => node.parentResourceName)
      const explicit = children.filter((node) => node.dimensionType)
      const other = children.find((node) => !node.dimensionType)
      if (explicit.length === 0) {
        setDimension('ALL_PRODUCTS')
        setIncludedValues('')
        setExcludedValues('')
        setIncludeOther(true)
      } else {
        setDimension(explicit[0]?.dimensionType ?? 'ALL_PRODUCTS')
        setIncludedValues(
          explicit
            .filter((node) => node.type === 'UNIT_INCLUDED')
            .map((node) => node.dimensionValue)
            .filter((value): value is string => Boolean(value))
            .join('\n'),
        )
        setExcludedValues(
          explicit
            .filter((node) => node.type === 'UNIT_EXCLUDED')
            .map((node) => node.dimensionValue)
            .filter((value): value is string => Boolean(value))
            .join('\n'),
        )
        setLevel(explicit[0]?.dimensionLevel ?? 'LEVEL1')
        setCustomIndex(explicit[0]?.customAttributeIndex ?? 'INDEX0')
        setIncludeOther(other ? other.type === 'UNIT_INCLUDED' : true)
      }
    } catch (loadError) {
      setError(apiErrorMessage(loadError, 'Unable to load Performance Max product groups'))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    void load()
  }, [companyId, campaignId, assetGroup.id])

  const save = async () => {
    if (!assetGroup.id) return
    setSaving(true)
    setError('')
    setNotice('')
    try {
      const included = splitValues(includedValues)
      const excluded = splitValues(excludedValues)
      const duplicate = included.find((value) => excluded.includes(value))
      if (duplicate) {
        throw new Error(`Product group value cannot be both included and excluded: ${duplicate}`)
      }
      const values =
        dimension === 'ALL_PRODUCTS'
          ? []
          : [
              ...included.map((value) => ({
                value,
                level: dimension === 'PRODUCT_TYPE' || dimension === 'CATEGORY' ? level : null,
                customAttributeIndex: dimension === 'CUSTOM_LABEL' ? customIndex : null,
                included: true,
              })),
              ...excluded.map((value) => ({
                value,
                level: dimension === 'PRODUCT_TYPE' || dimension === 'CATEGORY' ? level : null,
                customAttributeIndex: dimension === 'CUSTOM_LABEL' ? customIndex : null,
                included: false,
              })),
            ]

      const updated = await adCenterApi.replaceGooglePerformanceMaxListingGroups(
        companyId,
        campaignId,
        assetGroup.id,
        {
          dimensionType: dimension,
          values,
          includeOtherProducts: dimension === 'ALL_PRODUCTS' ? true : includeOther,
        },
      )
      setNodes(updated)
      setNotice(
        dimension === 'ALL_PRODUCTS'
          ? 'Product groups reset to include all Merchant Center products.'
          : 'Performance Max product-group tree replaced in Google Ads.',
      )
      await load()
    } catch (saveError) {
      setError(
        saveError instanceof Error
          ? saveError.message
          : apiErrorMessage(saveError, 'Unable to replace product groups'),
      )
    } finally {
      setSaving(false)
    }
  }

  if (loading) {
    return (
      <div className="mt-4 rounded-xl border border-slate-200 p-4 text-xs text-slate-500">
        Checking retail Performance Max product groups…
      </div>
    )
  }

  if (!retail) return null

  return (
    <div className="mt-4 rounded-xl border border-emerald-200 bg-emerald-50/30 p-4">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <p className="text-xs font-bold text-slate-800">Retail product groups</p>
          <p className="mt-1 text-[11px] leading-5 text-slate-500">
            Merchant Center {merchantId}
            {feedLabel ? ` · feed label ${feedLabel}` : ''}. Replacing this editor replaces the
            complete product partition tree for this Asset Group.
          </p>
        </div>
        <button
          type="button"
          className="secondary-button inline-flex items-center gap-2 text-xs"
          disabled={saving}
          onClick={() => void load()}
        >
          <FiRefreshCw aria-hidden="true" />
          Refresh groups
        </button>
      </div>



      <div className="mt-4 overflow-x-auto">
        <table className="min-w-full text-left text-xs">
          <thead>
            <tr className="border-b border-slate-200 text-[10px] uppercase tracking-wide text-slate-400">
              <th className="px-2 py-2">Product group</th>
              <th className="px-2 py-2">Rule</th>
              <th className="px-2 py-2 text-right">Impr.</th>
              <th className="px-2 py-2 text-right">Clicks</th>
              <th className="px-2 py-2 text-right">Conv.</th>
              <th className="px-2 py-2 text-right">Spend</th>
            </tr>
          </thead>
          <tbody>
            {nodes.map((node) => (
              <tr key={node.resourceName ?? node.id} className="border-b border-slate-100">
                <td className="px-2 py-2 font-medium text-slate-700">
                  {node.parentResourceName
                    ? node.dimensionType
                      ? `${format(node.dimensionType)}: ${node.dimensionValue}`
                      : 'Other products'
                    : 'All products'}
                </td>
                <td className="px-2 py-2 text-slate-500">{format(node.type)}</td>
                <td className="px-2 py-2 text-right">{(node.impressions ?? 0).toLocaleString()}</td>
                <td className="px-2 py-2 text-right">{(node.clicks ?? 0).toLocaleString()}</td>
                <td className="px-2 py-2 text-right">{node.conversions ?? 0}</td>
                <td className="px-2 py-2 text-right">
                  {((node.costMicros ?? 0) / 1_000_000).toLocaleString(undefined, {
                    maximumFractionDigits: 2,
                  })}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {canModify && (
        <div className="mt-5 border-t border-emerald-100 pt-4">
          <p className="text-xs font-bold text-slate-800">Replace product-group tree</p>
          <p className="mt-1 text-[11px] leading-5 text-amber-700">
            This is an intentional full replacement. Existing provider-only product groups under
            this Asset Group are removed before the new valid tree is created.
          </p>

          <div className="mt-3 grid gap-3 sm:grid-cols-2">
            <label className="text-xs font-semibold text-slate-700">
              Group products by
              <select
                className="form-input mt-2"
                value={dimension}
                onChange={(event) => setDimension(event.target.value)}
              >
                {DIMENSIONS.map(([value, label]) => (
                  <option key={value} value={value}>
                    {label}
                  </option>
                ))}
              </select>
            </label>

            {(dimension === 'PRODUCT_TYPE' || dimension === 'CATEGORY') && (
              <label className="text-xs font-semibold text-slate-700">
                Hierarchy level
                <select
                  className="form-input mt-2"
                  value={level}
                  onChange={(event) => setLevel(event.target.value)}
                >
                  {['LEVEL1', 'LEVEL2', 'LEVEL3', 'LEVEL4', 'LEVEL5'].map((value) => (
                    <option key={value} value={value}>
                      {format(value)}
                    </option>
                  ))}
                </select>
              </label>
            )}

            {dimension === 'CUSTOM_LABEL' && (
              <label className="text-xs font-semibold text-slate-700">
                Custom label
                <select
                  className="form-input mt-2"
                  value={customIndex}
                  onChange={(event) => setCustomIndex(event.target.value)}
                >
                  {['INDEX0', 'INDEX1', 'INDEX2', 'INDEX3', 'INDEX4'].map((value) => (
                    <option key={value} value={value}>
                      {value.replace('INDEX', 'Custom label ')}
                    </option>
                  ))}
                </select>
              </label>
            )}
          </div>

          {dimension !== 'ALL_PRODUCTS' && (
            <>
              <div className="mt-3 grid gap-3 sm:grid-cols-2">
                <label className="text-xs font-semibold text-slate-700">
                  Include values
                  <textarea
                    className="form-input mt-2 min-h-28"
                    value={includedValues}
                    onChange={(event) => setIncludedValues(event.target.value)}
                    placeholder={
                      dimension === 'CONDITION'
                        ? 'NEW\nUSED'
                        : dimension === 'CHANNEL'
                          ? 'ONLINE\nLOCAL'
                          : 'One value per line'
                    }
                  />
                </label>
                <label className="text-xs font-semibold text-slate-700">
                  Exclude values
                  <textarea
                    className="form-input mt-2 min-h-28"
                    value={excludedValues}
                    onChange={(event) => setExcludedValues(event.target.value)}
                    placeholder="One value per line"
                  />
                </label>
              </div>

              <label className="mt-3 flex items-center gap-2 text-xs text-slate-700">
                <input
                  type="checkbox"
                  checked={includeOther}
                  onChange={(event) => setIncludeOther(event.target.checked)}
                />
                Include all other products not explicitly listed
              </label>
            </>
          )}

          <div className="mt-4 flex justify-end">
            <button
              type="button"
              className="primary-button inline-flex items-center gap-2"
              disabled={saving}
              onClick={() => void save()}
            >
              <FiSave aria-hidden="true" />
              {saving ? 'Replacing…' : 'Replace product groups'}
            </button>
          </div>
        </div>
      )}
    </div>
  )
}
