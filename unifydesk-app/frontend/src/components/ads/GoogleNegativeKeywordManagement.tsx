import { useEffect, useState, type FormEvent } from 'react'
import { FiLink, FiPlus, FiRefreshCw, FiTrash2, FiX } from 'react-icons/fi'
import { adCenterApi } from '../../features/ads/adCenterApi'
import type {
  AdAccount,
  AdCampaign,
  GoogleNegativeKeyword,
  GoogleNegativeKeywordInput,
  GoogleNegativeKeywordList,
} from '../../features/ads/types'
import { apiErrorMessage } from '../../lib/api/apiClient'
import { ListSkeleton } from '../ui/Skeleton'

type Props = {
  companyId: string
  campaign: AdCampaign
  account: AdAccount
  canCreate: boolean
  canModify: boolean
}

const MATCH_TYPES: GoogleNegativeKeywordInput['matchType'][] = ['BROAD', 'PHRASE', 'EXACT']

const label = (value: string) =>
  value.toLowerCase().replace(/\b\w/g, (character) => character.toUpperCase())

const lines = (value: string) => [
  ...new Set(
    value
      .split(/\r?\n/)
      .map((item) => item.trim())
      .filter(Boolean),
  ),
]

export default function GoogleNegativeKeywordManagement({
  companyId,
  campaign,
  account,
  canCreate,
  canModify,
}: Props) {
  const [campaignKeywords, setCampaignKeywords] = useState<GoogleNegativeKeyword[]>([])
  const [lists, setLists] = useState<GoogleNegativeKeywordList[]>([])
  const [selectedList, setSelectedList] = useState<GoogleNegativeKeywordList | null>(null)
  const [campaignText, setCampaignText] = useState('')
  const [campaignMatchType, setCampaignMatchType] =
    useState<GoogleNegativeKeywordInput['matchType']>('PHRASE')
  const [listName, setListName] = useState('')
  const [memberText, setMemberText] = useState('')
  const [memberMatchType, setMemberMatchType] =
    useState<GoogleNegativeKeywordInput['matchType']>('PHRASE')
  const [loading, setLoading] = useState(true)
  const [working, setWorking] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')

  const load = async () => {
    setLoading(true)
    setError('')
    try {
      const [keywords, sharedLists] = await Promise.all([
        adCenterApi.googleCampaignNegativeKeywords(companyId, campaign.id),
        adCenterApi.googleNegativeKeywordLists(companyId, account.id, campaign.id),
      ])
      setCampaignKeywords(keywords)
      setLists(sharedLists)
      if (selectedList) {
        const refreshed = await adCenterApi.googleNegativeKeywordList(
          companyId,
          account.id,
          selectedList.id,
          campaign.id,
        )
        setSelectedList(refreshed)
      }
    } catch (loadError) {
      setError(apiErrorMessage(loadError, 'Unable to load Google negative keywords'))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    void load()
  }, [companyId, campaign.id, account.id])

  const run = async (action: () => Promise<void>) => {
    setWorking(true)
    setError('')
    setNotice('')
    try {
      await action()
    } catch (actionError) {
      setError(apiErrorMessage(actionError, 'Unable to update Google negative keywords'))
    } finally {
      setWorking(false)
    }
  }

  const addCampaignKeyword = async (event: FormEvent) => {
    event.preventDefault()
    const values = lines(campaignText)
    if (values.length === 0) return
    await run(async () => {
      const result = await adCenterApi.addGoogleCampaignNegativeKeywords(
        companyId,
        campaign.id,
        values.map((text) => ({ text, matchType: campaignMatchType })),
      )
      setCampaignText('')
      const refreshed = await adCenterApi.googleCampaignNegativeKeywords(companyId, campaign.id)
      setCampaignKeywords(refreshed)
      setNotice(
        result.createdCount > 0
          ? `${result.createdCount} campaign negative keyword${result.createdCount === 1 ? '' : 's'} added.`
          : 'All supplied campaign negative keywords already exist.',
      )
    })
  }

  const removeCampaignKeyword = async (keyword: GoogleNegativeKeyword) => {
    await run(async () => {
      await adCenterApi.removeGoogleCampaignNegativeKeyword(
        companyId,
        campaign.id,
        keyword.criterionId,
      )
      setCampaignKeywords((current) =>
        current.filter((item) => item.criterionId !== keyword.criterionId),
      )
      setNotice('Campaign negative keyword removed.')
    })
  }

  const createList = async (event: FormEvent) => {
    event.preventDefault()
    const name = listName.trim()
    if (!name) return
    await run(async () => {
      const created = await adCenterApi.createGoogleNegativeKeywordList(companyId, account.id, {
        name,
      })
      setListName('')
      const refreshed = await adCenterApi.googleNegativeKeywordLists(
        companyId,
        account.id,
        campaign.id,
      )
      setLists(refreshed)
      setSelectedList(
        await adCenterApi.googleNegativeKeywordList(companyId, account.id, created.id, campaign.id),
      )
      setNotice('Reusable negative keyword list created.')
    })
  }

  const openList = async (list: GoogleNegativeKeywordList) => {
    await run(async () => {
      const detail = await adCenterApi.googleNegativeKeywordList(
        companyId,
        account.id,
        list.id,
        campaign.id,
      )
      setSelectedList(detail)
    })
  }

  const toggleAttachment = async (list: GoogleNegativeKeywordList) => {
    await run(async () => {
      const updated = list.attachedToCampaign
        ? await adCenterApi.detachGoogleNegativeKeywordList(companyId, campaign.id, list.id)
        : await adCenterApi.attachGoogleNegativeKeywordList(companyId, campaign.id, list.id)
      setLists((current) => current.map((item) => (item.id === updated.id ? updated : item)))
      if (selectedList?.id === updated.id) {
        setSelectedList({ ...selectedList, attachedToCampaign: updated.attachedToCampaign })
      }
      setNotice(
        updated.attachedToCampaign
          ? 'Negative keyword list attached to this campaign.'
          : 'Negative keyword list detached from this campaign.',
      )
    })
  }

  const addMember = async (event: FormEvent) => {
    event.preventDefault()
    if (!selectedList) return
    const values = lines(memberText)
    if (values.length === 0) return
    await run(async () => {
      const result = await adCenterApi.addGoogleNegativeKeywordListMembers(
        companyId,
        account.id,
        selectedList.id,
        values.map((text) => ({ text, matchType: memberMatchType })),
      )
      setMemberText('')
      const detail = await adCenterApi.googleNegativeKeywordList(
        companyId,
        account.id,
        selectedList.id,
        campaign.id,
      )
      setSelectedList(detail)
      setLists((current) =>
        current.map((item) =>
          item.id === detail.id ? { ...item, memberCount: detail.memberCount } : item,
        ),
      )
      setNotice(
        result.createdCount > 0
          ? `${result.createdCount} shared negative keyword${result.createdCount === 1 ? '' : 's'} added.`
          : 'All supplied shared negative keywords already exist.',
      )
    })
  }

  const removeMember = async (keyword: GoogleNegativeKeyword) => {
    if (!selectedList) return
    await run(async () => {
      await adCenterApi.removeGoogleNegativeKeywordListMember(
        companyId,
        account.id,
        selectedList.id,
        keyword.criterionId,
      )
      const detail = await adCenterApi.googleNegativeKeywordList(
        companyId,
        account.id,
        selectedList.id,
        campaign.id,
      )
      setSelectedList(detail)
      setLists((current) =>
        current.map((item) =>
          item.id === detail.id ? { ...item, memberCount: detail.memberCount } : item,
        ),
      )
      setNotice('Shared negative keyword removed.')
    })
  }

  return (
    <section className="panel mt-5 overflow-hidden">
      <div className="flex flex-wrap items-start justify-between gap-3 border-b border-slate-100 px-5 py-4">
        <div>
          <h2 className="text-base font-bold text-slate-950">Negative keywords</h2>
          <p className="mt-1 text-xs leading-5 text-slate-500">
            Block unwanted Search queries at campaign level or reuse a shared Google Ads negative
            keyword list.
          </p>
        </div>
        <button
          type="button"
          className="secondary-button inline-flex items-center gap-2"
          disabled={loading || working}
          onClick={() => void load()}
        >
          <FiRefreshCw aria-hidden="true" />
          Refresh
        </button>
      </div>

      {(error || notice) && (
        <div
          className={
            'mx-5 mt-4 rounded-xl border px-4 py-3 text-sm font-medium ' +
            (error
              ? 'border-red-200 bg-red-50 text-red-700'
              : 'border-emerald-200 bg-emerald-50 text-emerald-700')
          }
        >
          {error || notice}
        </div>
      )}

      <div className="grid gap-6 p-5 lg:grid-cols-2">
        <div>
          <h3 className="text-sm font-bold text-slate-900">Campaign negatives</h3>
          <p className="mt-1 text-xs text-slate-500">
            These apply to every ad group in this Search campaign.
          </p>

          {canModify && (
            <form
              className="mt-4 grid gap-2 sm:grid-cols-[1fr_130px_auto]"
              onSubmit={addCampaignKeyword}
            >
              <textarea
                className="form-input min-h-24"
                value={campaignText}
                placeholder={'free\njobs\nsupport'}
                onChange={(event) => setCampaignText(event.target.value)}
              />
              <select
                className="form-input"
                value={campaignMatchType}
                onChange={(event) =>
                  setCampaignMatchType(
                    event.target.value as GoogleNegativeKeywordInput['matchType'],
                  )
                }
              >
                {MATCH_TYPES.map((type) => (
                  <option key={type} value={type}>
                    {label(type)}
                  </option>
                ))}
              </select>
              <button
                className="primary-button inline-flex items-center justify-center gap-2"
                type="submit"
                disabled={working || !campaignText.trim()}
              >
                <FiPlus aria-hidden="true" />
                Add
              </button>
            </form>
          )}

          <div className="mt-4 grid gap-2">
            {loading ? (
              <ListSkeleton rows={3} />
            ) : campaignKeywords.length === 0 ? (
              <p className="rounded-xl bg-slate-50 px-4 py-3 text-xs text-slate-500">
                No campaign-level negative keywords.
              </p>
            ) : (
              campaignKeywords.map((keyword) => (
                <div
                  key={keyword.criterionId}
                  className="flex items-center justify-between gap-3 rounded-xl border border-slate-200 px-3 py-2.5"
                >
                  <div className="min-w-0">
                    <p className="truncate text-sm font-semibold text-slate-800">{keyword.text}</p>
                    <p className="mt-0.5 text-[11px] text-slate-400">{label(keyword.matchType)}</p>
                  </div>
                  {canModify && (
                    <button
                      type="button"
                      className="rounded-lg p-2 text-slate-400 hover:bg-red-50 hover:text-red-600"
                      disabled={working}
                      aria-label="Remove campaign negative keyword"
                      onClick={() => void removeCampaignKeyword(keyword)}
                    >
                      <FiTrash2 aria-hidden="true" />
                    </button>
                  )}
                </div>
              ))
            )}
          </div>
        </div>

        <div>
          <h3 className="text-sm font-bold text-slate-900">Reusable lists</h3>
          <p className="mt-1 text-xs text-slate-500">
            Google shared negative lists can be attached to multiple campaigns.
          </p>

          {canCreate && (
            <form className="mt-4 flex gap-2" onSubmit={createList}>
              <input
                className="form-input"
                value={listName}
                maxLength={255}
                placeholder="Brand safety negatives"
                onChange={(event) => setListName(event.target.value)}
              />
              <button
                className="primary-button inline-flex items-center gap-2"
                type="submit"
                disabled={working || !listName.trim()}
              >
                <FiPlus aria-hidden="true" />
                Create
              </button>
            </form>
          )}

          <div className="mt-4 grid gap-2">
            {loading ? (
              <ListSkeleton rows={3} />
            ) : lists.length === 0 ? (
              <p className="rounded-xl bg-slate-50 px-4 py-3 text-xs text-slate-500">
                No reusable negative keyword lists on this account.
              </p>
            ) : (
              lists.map((list) => (
                <div key={list.id} className="rounded-xl border border-slate-200 p-3">
                  <div className="flex items-start justify-between gap-3">
                    <button
                      type="button"
                      className="min-w-0 text-left"
                      onClick={() => void openList(list)}
                    >
                      <span className="block truncate text-sm font-semibold text-slate-800">
                        {list.name}
                      </span>
                      <span className="mt-1 block text-[11px] text-slate-400">
                        {list.memberCount} keyword{list.memberCount === 1 ? '' : 's'} ·{' '}
                        {list.referenceCount} campaign reference
                        {list.referenceCount === 1 ? '' : 's'}
                      </span>
                    </button>
                    {canModify && (
                      <button
                        type="button"
                        className="secondary-button inline-flex items-center gap-2"
                        disabled={working}
                        onClick={() => void toggleAttachment(list)}
                      >
                        {list.attachedToCampaign ? (
                          <FiX aria-hidden="true" />
                        ) : (
                          <FiLink aria-hidden="true" />
                        )}
                        {list.attachedToCampaign ? 'Detach' : 'Attach'}
                      </button>
                    )}
                  </div>
                </div>
              ))
            )}
          </div>
        </div>
      </div>

      {selectedList && (
        <div className="border-t border-slate-100 p-5">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <div>
              <h3 className="text-sm font-bold text-slate-900">{selectedList.name}</h3>
              <p className="mt-1 text-xs text-slate-500">
                Shared list members ·{' '}
                {selectedList.attachedToCampaign ? 'attached to this campaign' : 'not attached'}
              </p>
            </div>
            <button
              type="button"
              className="secondary-button"
              onClick={() => setSelectedList(null)}
            >
              Close
            </button>
          </div>

          {canModify && (
            <form className="mt-4 grid gap-2 sm:grid-cols-[1fr_130px_auto]" onSubmit={addMember}>
              <textarea
                className="form-input min-h-24"
                value={memberText}
                placeholder={'free\njobs\nsupport'}
                onChange={(event) => setMemberText(event.target.value)}
              />
              <select
                className="form-input"
                value={memberMatchType}
                onChange={(event) =>
                  setMemberMatchType(event.target.value as GoogleNegativeKeywordInput['matchType'])
                }
              >
                {MATCH_TYPES.map((type) => (
                  <option key={type} value={type}>
                    {label(type)}
                  </option>
                ))}
              </select>
              <button
                type="submit"
                className="primary-button"
                disabled={working || !memberText.trim()}
              >
                Add member
              </button>
            </form>
          )}

          <div className="mt-4 grid gap-2 sm:grid-cols-2">
            {selectedList.members.length === 0 ? (
              <p className="rounded-xl bg-slate-50 px-4 py-3 text-xs text-slate-500 sm:col-span-2">
                This list has no negative keywords yet.
              </p>
            ) : (
              selectedList.members.map((keyword) => (
                <div
                  key={keyword.criterionId}
                  className="flex items-center justify-between gap-3 rounded-xl border border-slate-200 px-3 py-2.5"
                >
                  <div className="min-w-0">
                    <p className="truncate text-sm font-semibold text-slate-800">{keyword.text}</p>
                    <p className="mt-0.5 text-[11px] text-slate-400">{label(keyword.matchType)}</p>
                  </div>
                  {canModify && (
                    <button
                      type="button"
                      className="rounded-lg p-2 text-slate-400 hover:bg-red-50 hover:text-red-600"
                      disabled={working}
                      aria-label="Remove shared negative keyword"
                      onClick={() => void removeMember(keyword)}
                    >
                      <FiTrash2 aria-hidden="true" />
                    </button>
                  )}
                </div>
              ))
            )}
          </div>
        </div>
      )}
    </section>
  )
}
