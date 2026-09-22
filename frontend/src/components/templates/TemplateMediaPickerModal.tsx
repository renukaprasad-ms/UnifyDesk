import { useEffect, useMemo, useRef, useState } from 'react'
import { FiImage, FiPlus, FiUploadCloud, FiX } from 'react-icons/fi'
import { mediaApi } from '../../features/media/mediaApi'
import type { MediaResponse } from '../../features/media/types'
import { apiErrorMessage } from '../../lib/api/apiClient'
import { useSnackbar } from '../feedback/SnackbarProvider'

export type TemplateMediaSelection = {
  mediaId: string
  url: string
  fileName?: string
}

type Props = {
  companyId: string
  title?: string
  onClose: () => void
  onSelect: (selection: TemplateMediaSelection) => void
}

type MediaWithUrl = MediaResponse & { url?: string }

export default function TemplateMediaPickerModal({
  companyId,
  title = 'Choose image',
  onClose,
  onSelect,
}: Props) {
  const snackbar = useSnackbar()
  const fileRef = useRef<HTMLInputElement | null>(null)
  const [items, setItems] = useState<MediaWithUrl[]>([])
  const [loading, setLoading] = useState(true)
  const [uploading, setUploading] = useState(false)
  const [uploadProgress, setUploadProgress] = useState(0)
  const [search, setSearch] = useState('')

  const load = async () => {
    setLoading(true)
    try {
      const media = await mediaApi.listCompanyMedia(companyId)
      const images = media
        .filter(
          (item) =>
            item.status === 'ACTIVE' &&
            item.mediaType === 'IMAGE' &&
            (item.purpose === 'TEMPLATE_MEDIA' || item.purpose === 'COMPANY_LOGO'),
        )
        .slice(0, 60)

      const withUrls = await Promise.all(
        images.map(async (item) => {
          try {
            const access = await mediaApi.access(item.mediaId)
            return { ...item, url: access.url }
          } catch {
            return item
          }
        }),
      )
      setItems(withUrls)
    } catch (requestError) {
      snackbar.error(apiErrorMessage(requestError, 'Unable to load company media'))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    void load()
  }, [companyId])

  const visible = useMemo(() => {
    const query = search.trim().toLowerCase()
    if (!query) return items
    return items.filter((item) =>
      (item.originalFileName ?? '').toLowerCase().includes(query),
    )
  }, [items, search])

  const upload = async (file?: File) => {
    if (!file) return
    if (!file.type.startsWith('image/')) {
      snackbar.warning('Choose an image file for template media.')
      return
    }

    setUploading(true)
    setUploadProgress(0)
    try {
      const result = await mediaApi.uploadTemplateMedia(
        file,
        companyId,
        setUploadProgress,
      )
      snackbar.success('Image uploaded.')
      onSelect({
        mediaId: result.mediaId,
        url: result.access.url,
        fileName: file.name,
      })
    } catch (requestError) {
      snackbar.error(apiErrorMessage(requestError, 'Unable to upload image'))
    } finally {
      setUploading(false)
      setUploadProgress(0)
    }
  }

  return (
    <div className="mobile-modal-shell fixed inset-0 z-[70] flex items-center justify-center bg-slate-950/40 p-4">
      <div className="mobile-sheet max-h-[88vh] w-full max-w-3xl overflow-hidden rounded-2xl bg-white shadow-xl">
        <div className="flex items-start justify-between gap-4 border-b border-slate-100 p-5">
          <div>
            <p className="text-base font-bold text-slate-950">{title}</p>
            <p className="mt-1 text-xs leading-5 text-slate-500">
              Choose an existing company image or upload a new one. Files are stored through the company media storage provider.
            </p>
          </div>
          <button type="button" className="admin-back-button" onClick={onClose} aria-label="Close">
            <FiX aria-hidden="true" />
          </button>
        </div>

        <div className="flex flex-col gap-3 border-b border-slate-100 p-4 sm:flex-row sm:items-center sm:justify-between">
          <input
            className="form-input sm:max-w-sm"
            value={search}
            onChange={(event) => setSearch(event.target.value)}
            placeholder="Search images"
          />
          <div>
            <input
              ref={fileRef}
              type="file"
              accept="image/png,image/jpeg,image/webp"
              className="hidden"
              onChange={(event) => {
                const file = event.target.files?.[0]
                void upload(file)
                event.currentTarget.value = ''
              }}
            />
            <button
              type="button"
              className="primary-button inline-flex items-center gap-2"
              onClick={() => fileRef.current?.click()}
              disabled={uploading}
            >
              <FiUploadCloud aria-hidden="true" />
              {uploading ? `Uploading ${uploadProgress}%` : 'Upload image'}
            </button>
          </div>
        </div>

        <div className="max-h-[58vh] overflow-y-auto p-4">
          {loading ? (
            <div className="py-16 text-center text-sm text-slate-500">Loading media…</div>
          ) : visible.length === 0 ? (
            <div className="py-16 text-center">
              <FiImage className="mx-auto text-4xl text-slate-300" aria-hidden="true" />
              <p className="mt-4 text-sm font-bold text-slate-800">No images yet</p>
              <p className="mt-1 text-xs text-slate-500">
                Upload a logo, banner or product image to use it in this template.
              </p>
              <button
                type="button"
                className="secondary-button mt-5 inline-flex items-center gap-2"
                onClick={() => fileRef.current?.click()}
                disabled={uploading}
              >
                <FiPlus aria-hidden="true" />
                Upload first image
              </button>
            </div>
          ) : (
            <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 md:grid-cols-4">
              {visible.map((item) => (
                <button
                  key={item.mediaId}
                  type="button"
                  className="group overflow-hidden rounded-xl border border-slate-200 bg-white text-left transition hover:border-indigo-300 hover:shadow-sm"
                  disabled={!item.url}
                  onClick={() =>
                    item.url &&
                    onSelect({
                      mediaId: item.mediaId,
                      url: item.url,
                      fileName: item.originalFileName,
                    })
                  }
                >
                  <div className="aspect-square bg-slate-50">
                    {item.url ? (
                      <img
                        src={item.url}
                        alt={item.originalFileName ?? 'Template media'}
                        className="size-full object-cover"
                      />
                    ) : (
                      <div className="flex size-full items-center justify-center text-slate-300">
                        <FiImage aria-hidden="true" />
                      </div>
                    )}
                  </div>
                  <div className="p-2">
                    <p className="truncate text-[10px] font-semibold text-slate-700">
                      {item.originalFileName ?? 'Image'}
                    </p>
                    <p className="mt-0.5 text-[9px] uppercase text-slate-400">
                      {item.purpose === 'COMPANY_LOGO' ? 'Company logo' : 'Template media'}
                    </p>
                  </div>
                </button>
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  )
}
