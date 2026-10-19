import { useSnackbarState } from '../../components/feedback/useSnackbarState'
import { useEffect, useMemo, useState } from 'react'
import { FiBox, FiPlus, FiRefreshCw, FiShoppingBag, FiTrash2 } from 'react-icons/fi'
import AdFeedbackAlert from '../../components/ads/AdFeedbackAlert'
import GoogleMerchantDataSourceModal from '../../components/ads/GoogleMerchantDataSourceModal'
import GoogleMerchantProductModal from '../../components/ads/GoogleMerchantProductModal'
import ConfirmDialog from '../../components/ui/ConfirmDialog'
import { adCenterApi } from '../../features/ads/adCenterApi'
import type {
  AdAccount,
  CreateGoogleMerchantDataSourceInput,
  GoogleMerchantAccount,
  GoogleMerchantDataSource,
  GoogleMerchantProduct,
  UpsertGoogleMerchantProductInput,
} from '../../features/ads/types'
import { PERMISSIONS } from '../../features/iam/permissions'
import { useIam } from '../../features/iam/useIam'
import { apiErrorMessage } from '../../lib/api/apiClient'
import { useAppSelector } from '../../store/hooks'
import { selectSelectedCompany } from '../../store/workspace/workspaceSlice'

const priceFromMicros = (value?: string | null) => {
  if (!value) return ''
  const numeric = Number(value)
  return Number.isFinite(numeric) ? String(numeric / 1_000_000) : ''
}

export default function AdMerchantCenter() {
  const selectedCompany = useAppSelector(selectSelectedCompany)
  const { can } = useIam()
  const [accounts, setAccounts] = useState<AdAccount[]>([])
  const [selectedAccountId, setSelectedAccountId] = useState('')
  const [merchantAccounts, setMerchantAccounts] = useState<GoogleMerchantAccount[]>([])
  const [merchantId, setMerchantId] = useState('')
  const [dataSources, setDataSources] = useState<GoogleMerchantDataSource[]>([])
  const [products, setProducts] = useState<GoogleMerchantProduct[]>([])
  const [nextPageToken, setNextPageToken] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)
  const [loadingMerchant, setLoadingMerchant] = useState(false)
  const [loadingMore, setLoadingMore] = useState(false)
  const [sourceOpen, setSourceOpen] = useState(false)
  const [savingSource, setSavingSource] = useState(false)
  const [productOpen, setProductOpen] = useState(false)
  const [savingProduct, setSavingProduct] = useState(false)
  const [editingProduct, setEditingProduct] = useState<GoogleMerchantProduct | null>(null)
  const [error, setError] = useSnackbarState('', 'error')
  const [notice, setNotice] = useSnackbarState('', 'success')
  const [sourceToDelete, setSourceToDelete] = useState<GoogleMerchantDataSource | null>(null)
  const [productToDelete, setProductToDelete] = useState<GoogleMerchantProduct | null>(null)
  const [deleting, setDeleting] = useState(false)

  const googleAccounts = useMemo(
    () =>
      accounts.filter(
        (account) => account.provider === 'GOOGLE_ADS' && account.managed && account.accessible,
      ),
    [accounts],
  )

  const selectedAccount =
    googleAccounts.find((account) => account.id === selectedAccountId) ??
    googleAccounts.find((account) => account.primaryAccount) ??
    googleAccounts[0] ??
    null

  const apiSources = dataSources.filter((source) => source.apiManaged)

  const loadMerchant = async (accountId: string, preferredMerchantId?: string) => {
    if (!selectedCompany) return
    setLoadingMerchant(true)
    setError('')
    try {
      const merchants = await adCenterApi.googleMerchantAccounts(
        selectedCompany.companyId,
        accountId,
      )
      setMerchantAccounts(merchants)
      const selected =
        merchants.find((merchant) => merchant.accountId === preferredMerchantId) ??
        merchants[0] ??
        null
      setMerchantId(selected?.accountId ?? '')

      if (!selected) {
        setDataSources([])
        setProducts([])
        setNextPageToken(null)
        return
      }

      const [sources, productPage] = await Promise.all([
        adCenterApi.googleMerchantDataSources(
          selectedCompany.companyId,
          accountId,
          selected.accountId,
        ),
        adCenterApi.googleMerchantProducts(
          selectedCompany.companyId,
          accountId,
          selected.accountId,
          null,
          100,
        ),
      ])
      setDataSources(sources)
      setProducts(productPage.products)
      setNextPageToken(productPage.nextPageToken ?? null)
    } catch (loadError) {
      setMerchantAccounts([])
      setDataSources([])
      setProducts([])
      setNextPageToken(null)
      setError(
        apiErrorMessage(
          loadError,
          'Unable to load Merchant Center. Reconnect Google Ads if this connection was created before Merchant API support.',
        ),
      )
    } finally {
      setLoadingMerchant(false)
    }
  }

  const load = async () => {
    if (!selectedCompany) return
    setLoading(true)
    setError('')
    try {
      const accountData = await adCenterApi.accounts(selectedCompany.companyId)
      setAccounts(accountData)
      const eligible = accountData.filter(
        (account) => account.provider === 'GOOGLE_ADS' && account.managed && account.accessible,
      )
      const preferred = eligible.find((account) => account.primaryAccount) ?? eligible[0]
      if (preferred) {
        setSelectedAccountId(preferred.id)
        await loadMerchant(preferred.id)
      }
    } catch (loadError) {
      setError(apiErrorMessage(loadError, 'Unable to load Google Ads accounts'))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    void load()
  }, [selectedCompany?.companyId])

  const chooseAccount = async (accountId: string) => {
    setSelectedAccountId(accountId)
    setMerchantId('')
    setNotice('')
    await loadMerchant(accountId)
  }

  const chooseMerchant = async (nextMerchantId: string) => {
    if (!selectedCompany || !selectedAccount) return
    setMerchantId(nextMerchantId)
    setLoadingMerchant(true)
    setError('')
    try {
      const [sources, productPage] = await Promise.all([
        adCenterApi.googleMerchantDataSources(
          selectedCompany.companyId,
          selectedAccount.id,
          nextMerchantId,
        ),
        adCenterApi.googleMerchantProducts(
          selectedCompany.companyId,
          selectedAccount.id,
          nextMerchantId,
          null,
          100,
        ),
      ])
      setDataSources(sources)
      setProducts(productPage.products)
      setNextPageToken(productPage.nextPageToken ?? null)
    } catch (loadError) {
      setError(apiErrorMessage(loadError, 'Unable to load Merchant account'))
    } finally {
      setLoadingMerchant(false)
    }
  }

  const refresh = async () => {
    if (!selectedAccount || !merchantId) return
    await chooseMerchant(merchantId)
    setNotice('Merchant Center refreshed.')
  }

  const createSource = async (input: CreateGoogleMerchantDataSourceInput) => {
    if (!selectedCompany || !selectedAccount || !merchantId) return
    setSavingSource(true)
    setError('')
    try {
      const created = await adCenterApi.createGoogleMerchantDataSource(
        selectedCompany.companyId,
        selectedAccount.id,
        merchantId,
        input,
      )
      setDataSources((current) => [created, ...current])
      setSourceOpen(false)
      setNotice((created.displayName || 'API data source') + ' was created.')
    } catch (createError) {
      setError(apiErrorMessage(createError, 'Unable to create Merchant API data source'))
    } finally {
      setSavingSource(false)
    }
  }

  const deleteSource = async (source: GoogleMerchantDataSource) => {
    if (!selectedCompany || !selectedAccount || !merchantId) return
    setDeleting(true)
    setError('')
    try {
      await adCenterApi.deleteGoogleMerchantDataSource(
        selectedCompany.companyId,
        selectedAccount.id,
        merchantId,
        source.dataSourceId,
      )
      setNotice('Merchant data source deleted.')
      await refresh()
    } catch (deleteError) {
      setError(apiErrorMessage(deleteError, 'Unable to delete Merchant data source'))
    } finally {
      setDeleting(false)
      setSourceToDelete(null)
    }
  }

  const saveProduct = async (input: UpsertGoogleMerchantProductInput) => {
    if (!selectedCompany || !selectedAccount || !merchantId) return
    setSavingProduct(true)
    setError('')
    try {
      await adCenterApi.upsertGoogleMerchantProduct(
        selectedCompany.companyId,
        selectedAccount.id,
        merchantId,
        input,
      )
      setProductOpen(false)
      setNotice(
        editingProduct
          ? 'Product input updated. Google may take a few minutes to reprocess it.'
          : 'Product submitted. Google may take a few minutes to process it.',
      )
      await refresh()
    } catch (saveError) {
      setError(apiErrorMessage(saveError, 'Unable to save Merchant product'))
    } finally {
      setSavingProduct(false)
    }
  }

  const deleteProduct = async (product: GoogleMerchantProduct) => {
    if (!selectedCompany || !selectedAccount || !merchantId || !product.dataSource) {
      return
    }
    setDeleting(true)
    setError('')
    try {
      await adCenterApi.deleteGoogleMerchantProduct(
        selectedCompany.companyId,
        selectedAccount.id,
        merchantId,
        {
          dataSourceName: product.dataSource,
          contentLanguage: product.contentLanguage,
          feedLabel: product.feedLabel,
          offerId: product.offerId,
        },
      )
      setProducts((current) =>
        current.filter(
          (item) =>
            !(
              item.offerId === product.offerId &&
              item.feedLabel === product.feedLabel &&
              item.contentLanguage === product.contentLanguage
            ),
        ),
      )
      setNotice('Product input deleted. Merchant processing can take a few minutes.')
    } catch (deleteError) {
      setError(apiErrorMessage(deleteError, 'Unable to delete Merchant product'))
    } finally {
      setDeleting(false)
      setProductToDelete(null)
    }
  }

  const loadMore = async () => {
    if (!selectedCompany || !selectedAccount || !merchantId || !nextPageToken) {
      return
    }
    setLoadingMore(true)
    try {
      const page = await adCenterApi.googleMerchantProducts(
        selectedCompany.companyId,
        selectedAccount.id,
        merchantId,
        nextPageToken,
        100,
      )
      setProducts((current) => [...current, ...page.products])
      setNextPageToken(page.nextPageToken ?? null)
    } catch (loadError) {
      setError(apiErrorMessage(loadError, 'Unable to load more Merchant products'))
    } finally {
      setLoadingMore(false)
    }
  }

  if (!selectedCompany) return null

  return (
    <section className="px-4 py-6 sm:px-6 lg:px-8">
      <div className="mx-auto max-w-7xl">
        <header className="flex flex-wrap items-end justify-between gap-4">
          <div>
            <p className="eyebrow">Ad Center · Google</p>
            <h1 className="mt-2 text-2xl font-bold tracking-tight text-slate-950 sm:text-3xl">
              Merchant Center
            </h1>
            <p className="mt-2 max-w-3xl text-sm leading-6 text-slate-500">
              Manage Merchant API product data sources and catalog items used by Google Shopping
              campaigns.
            </p>
          </div>
          <div className="flex flex-wrap gap-2">
            {can(PERMISSIONS.ADS_MODIFY) && (
              <button
                type="button"
                className="secondary-button inline-flex items-center gap-2"
                disabled={!merchantId || loadingMerchant}
                onClick={() => void refresh()}
              >
                <FiRefreshCw aria-hidden="true" />
                Refresh
              </button>
            )}
            {can(PERMISSIONS.ADS_CREATE) && (
              <button
                type="button"
                className="primary-button inline-flex items-center gap-2"
                disabled={!merchantId || apiSources.length === 0}
                onClick={() => {
                  setEditingProduct(null)
                  setProductOpen(true)
                }}
              >
                <FiPlus aria-hidden="true" />
                Add product
              </button>
            )}
          </div>
        </header>

        <AdFeedbackAlert error={error} notice={notice} />

        <div className="mt-6 grid gap-4 lg:grid-cols-2">
          <label className="text-xs font-semibold text-slate-700">
            Google Ads account
            <select
              className="form-input mt-2"
              value={selectedAccount?.id ?? ''}
              disabled={loading || googleAccounts.length === 0}
              onChange={(event) => void chooseAccount(event.target.value)}
            >
              {googleAccounts.length === 0 ? (
                <option value="">No managed Google Ads account</option>
              ) : (
                googleAccounts.map((account) => (
                  <option key={account.id} value={account.id}>
                    {account.name}
                  </option>
                ))
              )}
            </select>
          </label>

          <label className="text-xs font-semibold text-slate-700">
            Merchant Center account
            <select
              className="form-input mt-2"
              value={merchantId}
              disabled={!selectedAccount || loadingMerchant || merchantAccounts.length === 0}
              onChange={(event) => void chooseMerchant(event.target.value)}
            >
              {merchantAccounts.length === 0 ? (
                <option value="">No accessible Merchant account</option>
              ) : (
                merchantAccounts.map((merchant) => (
                  <option key={merchant.accountId} value={merchant.accountId}>
                    {(merchant.accountName || 'Merchant Center') + ' · ' + merchant.accountId}
                  </option>
                ))
              )}
            </select>
          </label>
        </div>

        {!loading && googleAccounts.length === 0 && (
          <div className="panel mt-6 p-10 text-center text-sm text-slate-500">
            Connect and manage a Google Ads account first.
          </div>
        )}

        {selectedAccount && merchantAccounts.length === 0 && !loadingMerchant && !error && (
          <div className="panel mt-6 p-10 text-center">
            <FiShoppingBag className="mx-auto text-3xl text-slate-300" aria-hidden="true" />
            <h2 className="mt-3 font-bold text-slate-950">No Merchant Center account found</h2>
            <p className="mt-2 text-sm text-slate-500">
              Ensure the connected Google user has Merchant Center access. Existing Google
              connections must reconnect once to grant the Merchant API scope.
            </p>
          </div>
        )}

        {merchantId && (
          <>
            <div className="panel mt-6 overflow-hidden">
              <div className="flex flex-wrap items-center justify-between gap-3 border-b border-slate-100 px-5 py-4">
                <div>
                  <h2 className="font-bold text-slate-950">Product data sources</h2>
                  <p className="mt-1 text-xs text-slate-500">
                    Reachlyo can write products only to API-managed sources.
                  </p>
                </div>
                {can(PERMISSIONS.ADS_CREATE) && (
                  <button
                    type="button"
                    className="secondary-button inline-flex items-center gap-2"
                    onClick={() => setSourceOpen(true)}
                  >
                    <FiPlus aria-hidden="true" />
                    Create API source
                  </button>
                )}
              </div>

              {dataSources.length === 0 ? (
                <div className="p-8 text-center text-sm text-slate-500">
                  No product data sources found.
                </div>
              ) : (
                <div className="divide-y divide-slate-100">
                  {dataSources.map((source) => (
                    <div
                      key={source.name}
                      className="flex flex-wrap items-center justify-between gap-4 px-5 py-4"
                    >
                      <div>
                        <p className="font-semibold text-slate-950">
                          {source.displayName || source.dataSourceId}
                        </p>
                        <p className="mt-1 text-xs text-slate-500">
                          {(source.input || 'Unknown input') +
                            ' · ' +
                            (source.contentLanguage || 'Any language') +
                            ' · ' +
                            (source.feedLabel || 'Any feed label') +
                            ' · ' +
                            (source.countries.join(', ') || 'No countries returned')}
                        </p>
                      </div>
                      <div className="flex items-center gap-2">
                        <span
                          className={
                            'rounded-full px-2.5 py-1 text-xs font-semibold ' +
                            (source.apiManaged
                              ? 'bg-emerald-50 text-emerald-700'
                              : 'bg-slate-100 text-slate-500')
                          }
                        >
                          {source.apiManaged ? 'API managed' : 'Read only'}
                        </span>
                        {source.apiManaged && can(PERMISSIONS.ADS_MODIFY) && (
                          <button
                            type="button"
                            className="rounded-lg p-2 text-slate-400 hover:bg-red-50 hover:text-red-600"
                            onClick={() => setSourceToDelete(source)}
                          >
                            <FiTrash2 aria-hidden="true" />
                          </button>
                        )}
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>

            <div className="panel mt-6 overflow-hidden">
              <div className="border-b border-slate-100 px-5 py-4">
                <h2 className="font-bold text-slate-950">Products</h2>
                <p className="mt-1 text-xs text-slate-500">
                  Processed Merchant Center products. Google can take several minutes to show a
                  newly submitted change.
                </p>
              </div>

              {products.length === 0 ? (
                <div className="p-10 text-center">
                  <FiBox className="mx-auto text-3xl text-slate-300" aria-hidden="true" />
                  <h3 className="mt-3 font-bold text-slate-950">No products returned</h3>
                  <p className="mt-2 text-sm text-slate-500">
                    Create an API source, then add the first catalog product.
                  </p>
                </div>
              ) : (
                <div className="divide-y divide-slate-100">
                  {products.map((product) => (
                    <div
                      key={
                        product.name ||
                        product.contentLanguage + '~' + product.feedLabel + '~' + product.offerId
                      }
                      className="flex flex-wrap items-center justify-between gap-4 px-5 py-4"
                    >
                      <div className="flex min-w-0 items-center gap-3">
                        {product.imageLink ? (
                          <img
                            src={product.imageLink}
                            alt=""
                            className="h-14 w-14 rounded-lg bg-slate-100 object-contain"
                          />
                        ) : (
                          <div className="flex h-14 w-14 items-center justify-center rounded-lg bg-slate-100">
                            <FiBox className="text-slate-400" aria-hidden="true" />
                          </div>
                        )}
                        <div className="min-w-0">
                          <p className="truncate font-semibold text-slate-950">
                            {product.title || product.offerId}
                          </p>
                          <p className="mt-1 text-xs text-slate-500">
                            {product.offerId +
                              ' · ' +
                              (product.availability || 'Unknown availability')}
                            {product.priceAmountMicros && product.priceCurrency
                              ? ' · ' +
                                priceFromMicros(product.priceAmountMicros) +
                                ' ' +
                                product.priceCurrency
                              : ''}
                          </p>
                          {product.issues.length > 0 && (
                            <p className="mt-1 text-xs font-medium text-amber-600">
                              {product.issues.length} Merchant issue(s)
                            </p>
                          )}
                        </div>
                      </div>

                      {can(PERMISSIONS.ADS_MODIFY) &&
                        product.dataSource &&
                        apiSources.some((source) => source.name === product.dataSource) && (
                          <div className="flex gap-2">
                            <button
                              type="button"
                              className="secondary-button text-xs"
                              onClick={() => {
                                setEditingProduct(product)
                                setProductOpen(true)
                              }}
                            >
                              Edit
                            </button>
                            <button
                              type="button"
                              className="secondary-button text-xs"
                              onClick={() => setProductToDelete(product)}
                            >
                              Delete
                            </button>
                          </div>
                        )}
                    </div>
                  ))}
                </div>
              )}

              {nextPageToken && (
                <div className="border-t border-slate-100 p-4 text-center">
                  <button
                    type="button"
                    className="secondary-button"
                    disabled={loadingMore}
                    onClick={() => void loadMore()}
                  >
                    {loadingMore ? 'Loading…' : 'Load more products'}
                  </button>
                </div>
              )}
            </div>
          </>
        )}
      </div>

      <GoogleMerchantDataSourceModal
        open={sourceOpen}
        saving={savingSource}
        onClose={() => setSourceOpen(false)}
        onCreate={createSource}
      />

      <GoogleMerchantProductModal
        open={productOpen}
        saving={savingProduct}
        product={editingProduct}
        dataSources={dataSources}
        defaultCurrency={selectedAccount?.currency}
        onClose={() => setProductOpen(false)}
        onSave={saveProduct}
      />
      <ConfirmDialog
        open={Boolean(sourceToDelete)}
        title="Delete this Merchant data source?"
        description={sourceToDelete ? `Deleting ${sourceToDelete.displayName || sourceToDelete.dataSourceId} can also remove products owned by a primary source from Merchant Center.` : ''}
        confirmLabel="Yes, delete source"
        cancelLabel="No, keep source"
        tone="danger"
        kind="delete"
        working={deleting}
        onCancel={() => setSourceToDelete(null)}
        onConfirm={() => sourceToDelete && void deleteSource(sourceToDelete)}
      />
      <ConfirmDialog
        open={Boolean(productToDelete)}
        title="Delete this Merchant product?"
        description={productToDelete ? `Delete ${productToDelete.title || productToDelete.offerId} from this API data source?` : ''}
        confirmLabel="Yes, delete product"
        cancelLabel="No, keep product"
        tone="danger"
        kind="delete"
        working={deleting}
        onCancel={() => setProductToDelete(null)}
        onConfirm={() => productToDelete && void deleteProduct(productToDelete)}
      />
    </section>
  )
}
