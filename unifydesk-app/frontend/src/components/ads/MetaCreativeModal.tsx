import { useSnackbarState } from '../feedback/useSnackbarState'
import { useEffect, useMemo, useState, type FormEvent } from 'react'
import { FiPlus, FiTrash2, FiX } from 'react-icons/fi'
import { adCenterApi } from '../../features/ads/adCenterApi'
import type {
  AdAccount,
  AdAccountResource,
  CreateMetaCreativeInput,
  MetaCarouselCardInput,
  MetaCreativeFormat,
  MetaExistingPost,
} from '../../features/ads/types'

type Props = {
  account: AdAccount
  resources: AdAccountResource[]
  open: boolean
  saving: boolean
  onClose: () => void
  onCreate: (input: CreateMetaCreativeInput) => Promise<void>
}

const emptyCard = (): MetaCarouselCardInput => ({
  headline: '',
  description: '',
  destinationUrl: '',
  imageHash: '',
  imageUrl: '',
  callToActionType: 'LEARN_MORE',
})

export default function MetaCreativeModal({
  account,
  resources,
  open,
  saving,
  onClose,
  onCreate,
}: Props) {
  const pages = useMemo(
    () => resources.filter((item) => item.resourceType === 'FACEBOOK_PAGE' && item.accessible),
    [resources],
  )
  const instagramAccounts = useMemo(
    () => resources.filter((item) => item.resourceType === 'INSTAGRAM_ACCOUNT' && item.accessible),
    [resources],
  )

  const [name, setName] = useState('')
  const [format, setFormat] = useState<MetaCreativeFormat>('SINGLE_IMAGE')
  const [pageId, setPageId] = useState('')
  const [instagramActorId, setInstagramActorId] = useState('')
  const [primaryText, setPrimaryText] = useState('')
  const [headline, setHeadline] = useState('')
  const [description, setDescription] = useState('')
  const [cta, setCta] = useState('LEARN_MORE')
  const [destinationUrl, setDestinationUrl] = useState('')
  const [imageHash, setImageHash] = useState('')
  const [imageUrl, setImageUrl] = useState('')
  const [videoId, setVideoId] = useState('')
  const [videoUrl, setVideoUrl] = useState('')
  const [urlTags, setUrlTags] = useState('')
  const [advantagePlusCreative, setAdvantagePlusCreative] = useState(false)
  const [generativeText, setGenerativeText] = useState(false)
  const [imageExpansion, setImageExpansion] = useState(false)
  const [useExistingPost, setUseExistingPost] = useState(false)
  const [existingPostId, setExistingPostId] = useState('')
  const [existingPosts, setExistingPosts] = useState<MetaExistingPost[]>([])
  const [postsLoading, setPostsLoading] = useState(false)
  const [postsError, setPostsError] = useSnackbarState('', 'error')
  const [cards, setCards] = useState<MetaCarouselCardInput[]>([emptyCard(), emptyCard()])

  useEffect(() => {
    if (!open || !useExistingPost || !pageId) {
      setExistingPosts([])
      setPostsError('')
      return
    }
    let cancelled = false
    setPostsLoading(true)
    setPostsError('')
    void adCenterApi
      .metaExistingPosts(account.companyId, account.id, pageId)
      .then((posts) => {
        if (!cancelled) setExistingPosts(posts)
      })
      .catch(() => {
        if (!cancelled)
          setPostsError('Unable to load eligible posts. You can enter an object story ID manually.')
      })
      .finally(() => {
        if (!cancelled) setPostsLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [open, useExistingPost, pageId, account.companyId, account.id])

  if (!open) return null

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    await onCreate({
      name: name.trim(),
      format,
      pageId,
      instagramActorId: instagramActorId || null,
      primaryText: primaryText || null,
      headline: headline || null,
      description: description || null,
      callToActionType: cta || null,
      destinationUrl: destinationUrl || null,
      imageHash: format === 'SINGLE_IMAGE' ? imageHash || null : null,
      imageUrl: format === 'SINGLE_IMAGE' ? imageUrl || null : null,
      videoId: format === 'VIDEO' ? videoId || null : null,
      videoUrl: format === 'VIDEO' ? videoUrl || null : null,
      urlTags: urlTags || null,
      advantagePlusCreative,
      generativeText,
      imageExpansion: !useExistingPost && format === 'SINGLE_IMAGE' ? imageExpansion : false,
      existingPostId: useExistingPost ? existingPostId.trim() || null : null,
      carouselCards: format === 'CAROUSEL' ? cards : [],
    })
  }

  const updateCard = (index: number, patch: Partial<MetaCarouselCardInput>) => {
    setCards((current) =>
      current.map((card, cardIndex) => (cardIndex === index ? { ...card, ...patch } : card)),
    )
  }

  return (
    <div className="mobile-modal-shell fixed inset-0 z-50 flex items-center justify-center bg-slate-950/40 p-4">
      <div className="mobile-sheet max-h-[92vh] w-full max-w-3xl overflow-y-auto rounded-2xl bg-white shadow-2xl">
        <div className="sticky top-0 z-10 flex items-start justify-between border-b border-slate-100 bg-white px-6 py-5">
          <div>
            <p className="eyebrow">Meta Ads</p>
            <h2 className="mt-1 text-xl font-bold text-slate-950">Create creative</h2>
            <p className="mt-1 text-sm text-slate-500">{account.name}</p>
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
          <section className="rounded-xl border border-emerald-200 bg-emerald-50 p-4">
            <label className="flex items-start gap-3">
              <input
                type="checkbox"
                className="mt-1"
                checked={useExistingPost}
                onChange={(event) => setUseExistingPost(event.target.checked)}
              />
              <span className="flex-1">
                <span className="block text-sm font-bold text-emerald-950">
                  Use existing Meta post
                </span>
                <span className="mt-1 block text-xs leading-5 text-emerald-700">
                  Reuse an eligible Facebook or Instagram post by object story ID. Meta verifies
                  ownership and ad eligibility when the creative is created.
                </span>
                {useExistingPost && (
                  <>
                    <select
                      className="form-input mt-3"
                      value={existingPostId}
                      onChange={(event) => setExistingPostId(event.target.value)}
                      disabled={postsLoading}
                    >
                      <option value="">
                        {postsLoading
                          ? 'Loading eligible posts…'
                          : 'Choose a synced post or enter ID below'}
                      </option>
                      {existingPosts.map((post) => (
                        <option key={post.id} value={post.id}>
                          {(post.message || 'Post').slice(0, 80)} · {post.id}
                        </option>
                      ))}
                    </select>
                    <input
                      className="form-input mt-2"
                      value={existingPostId}
                      onChange={(event) => setExistingPostId(event.target.value)}
                      placeholder="Object story ID, for example PAGEID_POSTID"
                      required
                    />
                    {postsError && (
                      <span className="mt-2 block text-[11px] text-amber-700">{postsError}</span>
                    )}
                  </>
                )}
              </span>
            </label>
          </section>

          <section className="grid gap-4 sm:grid-cols-2">
            <label className="block text-xs font-semibold text-slate-700 sm:col-span-2">
              Creative name
              <input
                className="form-input mt-2"
                value={name}
                maxLength={255}
                required
                onChange={(e) => setName(e.target.value)}
              />
            </label>

            <label className="block text-xs font-semibold text-slate-700">
              Format
              <select
                className="form-input mt-2"
                value={format}
                disabled={useExistingPost}
                onChange={(e) => setFormat(e.target.value as MetaCreativeFormat)}
              >
                <option value="SINGLE_IMAGE">Single image</option>
                <option value="VIDEO">Video</option>
                <option value="CAROUSEL">Carousel</option>
              </select>
            </label>

            <label className="block text-xs font-semibold text-slate-700">
              Facebook Page
              <select
                className="form-input mt-2"
                value={pageId}
                required
                onChange={(e) => setPageId(e.target.value)}
              >
                <option value="">Choose page</option>
                {pages.map((page) => (
                  <option key={page.id} value={page.externalResourceId}>
                    {page.name || page.externalResourceId}
                  </option>
                ))}
              </select>
            </label>

            <label className="block text-xs font-semibold text-slate-700">
              Instagram identity
              <select
                className="form-input mt-2"
                value={instagramActorId}
                onChange={(e) => setInstagramActorId(e.target.value)}
              >
                <option value="">No Instagram identity</option>
                {instagramAccounts.map((item) => (
                  <option key={item.id} value={item.externalResourceId}>
                    {item.name || item.externalResourceId}
                  </option>
                ))}
              </select>
            </label>

            <label className="block text-xs font-semibold text-slate-700">
              CTA
              <select
                className="form-input mt-2"
                value={cta}
                onChange={(e) => setCta(e.target.value)}
              >
                <option value="LEARN_MORE">Learn more</option>
                <option value="SHOP_NOW">Shop now</option>
                <option value="SIGN_UP">Sign up</option>
                <option value="CONTACT_US">Contact us</option>
                <option value="GET_OFFER">Get offer</option>
                <option value="APPLY_NOW">Apply now</option>
                <option value="BOOK_TRAVEL">Book now</option>
                <option value="DOWNLOAD">Download</option>
              </select>
            </label>
          </section>

          <section className="border-t border-slate-100 pt-6">
            <h3 className="text-sm font-bold text-slate-950">Copy</h3>
            <div className="mt-4 grid gap-4">
              <label className="block text-xs font-semibold text-slate-700">
                Primary text
                <textarea
                  className="form-input mt-2"
                  value={primaryText}
                  onChange={(e) => setPrimaryText(e.target.value)}
                />
              </label>
              <div className="grid gap-4 sm:grid-cols-2">
                <label className="block text-xs font-semibold text-slate-700">
                  Headline
                  <input
                    className="form-input mt-2"
                    value={headline}
                    onChange={(e) => setHeadline(e.target.value)}
                  />
                </label>
                <label className="block text-xs font-semibold text-slate-700">
                  Description
                  <input
                    className="form-input mt-2"
                    value={description}
                    onChange={(e) => setDescription(e.target.value)}
                  />
                </label>
              </div>
              <label className="block text-xs font-semibold text-slate-700">
                Destination URL
                <input
                  className="form-input mt-2"
                  type="url"
                  value={destinationUrl}
                  required={!useExistingPost}
                  disabled={useExistingPost}
                  onChange={(e) => setDestinationUrl(e.target.value)}
                  placeholder="https://example.com"
                />
              </label>
              <label className="block text-xs font-semibold text-slate-700">
                URL tags
                <input
                  className="form-input mt-2"
                  value={urlTags}
                  onChange={(e) => setUrlTags(e.target.value)}
                  placeholder="utm_source=meta&utm_campaign=..."
                />
              </label>
            </div>
          </section>

          <section className="rounded-xl border border-indigo-200 bg-indigo-50 p-4">
            <label className="flex items-start gap-3">
              <input
                type="checkbox"
                className="mt-1"
                checked={advantagePlusCreative}
                onChange={(event) => setAdvantagePlusCreative(event.target.checked)}
              />
              <span>
                <span className="block text-sm font-bold text-indigo-950">Advantage+ creative</span>
                <span className="mt-1 block text-xs leading-5 text-indigo-700">
                  Opt in to Meta standard creative enhancements for eligible delivery. Generative
                  text, image expansion and background generation are separate controls.
                </span>
              </span>
            </label>
          </section>

          <section className="rounded-xl border border-violet-200 bg-violet-50 p-4">
            <label className="flex items-start gap-3">
              <input
                type="checkbox"
                className="mt-1"
                checked={generativeText}
                onChange={(event) => setGenerativeText(event.target.checked)}
              />
              <span>
                <span className="block text-sm font-bold text-violet-950">AI text generation</span>
                <span className="mt-1 block text-xs leading-5 text-violet-700">
                  Allow Meta to generate text variations from your original primary text. Review
                  provider previews/read-back before activating ads that use generated copy.
                </span>
              </span>
            </label>
          </section>

          <section className="rounded-xl border border-cyan-200 bg-cyan-50 p-4">
            <label className="flex items-start gap-3">
              <input
                type="checkbox"
                className="mt-1"
                checked={imageExpansion}
                disabled={format !== 'SINGLE_IMAGE'}
                onChange={(event) => setImageExpansion(event.target.checked)}
              />
              <span>
                <span className="block text-sm font-bold text-cyan-950">Image expansion</span>
                <span className="mt-1 block text-xs leading-5 text-cyan-700">
                  Allow Meta to extend image boundaries for eligible placements. Available only for
                  image creatives; review the provider preview before activation.
                </span>
              </span>
            </label>
          </section>

          {format === 'SINGLE_IMAGE' && (
            <section className="border-t border-slate-100 pt-6">
              <h3 className="text-sm font-bold text-slate-950">Image</h3>
              <p className="mt-1 text-xs text-slate-500">
                Use an existing Meta image hash or a publicly reachable image URL.
              </p>
              <div className="mt-4 grid gap-4 sm:grid-cols-2">
                <label className="block text-xs font-semibold text-slate-700">
                  Meta image hash
                  <input
                    className="form-input mt-2"
                    value={imageHash}
                    onChange={(e) => setImageHash(e.target.value)}
                  />
                </label>
                <label className="block text-xs font-semibold text-slate-700">
                  Image URL
                  <input
                    className="form-input mt-2"
                    type="url"
                    value={imageUrl}
                    onChange={(e) => setImageUrl(e.target.value)}
                  />
                </label>
              </div>
            </section>
          )}

          {format === 'VIDEO' && (
            <section className="border-t border-slate-100 pt-6">
              <h3 className="text-sm font-bold text-slate-950">Video</h3>
              <div className="mt-4 grid gap-4 sm:grid-cols-2">
                <label className="block text-xs font-semibold text-slate-700">
                  Existing Meta video ID
                  <input
                    className="form-input mt-2"
                    value={videoId}
                    onChange={(e) => setVideoId(e.target.value)}
                  />
                </label>
                <label className="block text-xs font-semibold text-slate-700">
                  Hosted video URL
                  <input
                    className="form-input mt-2"
                    type="url"
                    value={videoUrl}
                    onChange={(e) => setVideoUrl(e.target.value)}
                    placeholder="https://cdn.example.com/video.mp4"
                  />
                </label>
              </div>
              <p className="mt-2 text-xs text-slate-400">
                Provide either an existing Meta video ID or a public video URL. Hosted videos are
                uploaded to the selected ad account automatically.
              </p>
            </section>
          )}

          {format === 'CAROUSEL' && (
            <section className="border-t border-slate-100 pt-6">
              <div className="flex items-center justify-between gap-3">
                <div>
                  <h3 className="text-sm font-bold text-slate-950">Carousel cards</h3>
                  <p className="mt-1 text-xs text-slate-500">At least two cards are required.</p>
                </div>
                <button
                  type="button"
                  className="secondary-button inline-flex items-center gap-2"
                  onClick={() => setCards((current) => [...current, emptyCard()])}
                >
                  <FiPlus aria-hidden="true" />
                  Add card
                </button>
              </div>

              <div className="mt-4 space-y-4">
                {cards.map((card, index) => (
                  <div key={index} className="rounded-xl border border-slate-200 p-4">
                    <div className="flex items-center justify-between gap-3">
                      <p className="text-sm font-bold text-slate-900">Card {index + 1}</p>
                      {cards.length > 2 && (
                        <button
                          type="button"
                          className="rounded-lg p-2 text-slate-400 hover:bg-slate-100"
                          onClick={() =>
                            setCards((current) => current.filter((_, i) => i !== index))
                          }
                        >
                          <FiTrash2 aria-hidden="true" />
                        </button>
                      )}
                    </div>
                    <div className="mt-3 grid gap-3 sm:grid-cols-2">
                      <input
                        className="form-input"
                        placeholder="Headline"
                        value={card.headline ?? ''}
                        onChange={(e) => updateCard(index, { headline: e.target.value })}
                      />
                      <input
                        className="form-input"
                        placeholder="Description"
                        value={card.description ?? ''}
                        onChange={(e) => updateCard(index, { description: e.target.value })}
                      />
                      <input
                        className="form-input"
                        placeholder="Image hash"
                        value={card.imageHash ?? ''}
                        onChange={(e) => updateCard(index, { imageHash: e.target.value })}
                      />
                      <input
                        className="form-input"
                        type="url"
                        placeholder="Image URL"
                        value={card.imageUrl ?? ''}
                        onChange={(e) => updateCard(index, { imageUrl: e.target.value })}
                      />
                      <input
                        className="form-input sm:col-span-2"
                        type="url"
                        placeholder="Destination URL (optional override)"
                        value={card.destinationUrl ?? ''}
                        onChange={(e) => updateCard(index, { destinationUrl: e.target.value })}
                      />
                    </div>
                  </div>
                ))}
              </div>
            </section>
          )}

          <div className="rounded-xl border border-slate-200 bg-slate-50 px-4 py-3 text-xs leading-5 text-slate-600">
            Creative creation only builds the reusable Meta creative. It does not create or start an
            ad.
          </div>

          <div className="flex justify-end gap-2 border-t border-slate-100 pt-5">
            <button type="button" className="secondary-button" onClick={onClose} disabled={saving}>
              Cancel
            </button>
            <button
              type="submit"
              className="primary-button"
              disabled={
                saving ||
                !name.trim() ||
                !pageId ||
                (useExistingPost ? !existingPostId.trim() : !destinationUrl.trim()) ||
                (!useExistingPost &&
                  format === 'SINGLE_IMAGE' &&
                  !imageHash.trim() &&
                  !imageUrl.trim()) ||
                (!useExistingPost && format === 'VIDEO' && !videoId.trim() && !videoUrl.trim())
              }
            >
              {saving ? 'Creating…' : 'Create creative'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}
