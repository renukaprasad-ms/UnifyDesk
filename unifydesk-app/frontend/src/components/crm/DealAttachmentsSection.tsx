import { useSnackbarState } from '../feedback/useSnackbarState'
import { useEffect, useRef, useState } from 'react'
import { FiFileText, FiFile, FiImage, FiUploadCloud, FiDownload, FiTrash2, FiPaperclip } from 'react-icons/fi'
import {
  crmSalesApi,
  type DealAttachment,
} from '../../features/contacts/crmSalesApi'
import { mediaApi } from '../../features/media/mediaApi'
import { apiErrorMessage } from '../../lib/api/apiClient'

interface Props {
  companyId: string
  dealId: string
  canEdit: boolean
  onAttachmentChanged?: () => void
}

function formatBytes(bytes: number): string {
  if (!bytes || bytes <= 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return `${parseFloat((bytes / Math.pow(k, i)).toFixed(1))} ${sizes[i]}`
}

function getFileIcon(filename: string, contentType?: string | null) {
  const lower = (filename || '').toLowerCase()
  const mime = (contentType || '').toLowerCase()

  if (lower.endsWith('.pdf') || mime.includes('pdf')) {
    return <FiFileText className="size-5 text-rose-500 shrink-0" />
  }
  if (
    lower.endsWith('.png') ||
    lower.endsWith('.jpg') ||
    lower.endsWith('.jpeg') ||
    lower.endsWith('.webp') ||
    lower.endsWith('.svg') ||
    mime.startsWith('image/')
  ) {
    return <FiImage className="size-5 text-sky-500 shrink-0" />
  }
  if (
    lower.endsWith('.xlsx') ||
    lower.endsWith('.xls') ||
    lower.endsWith('.csv') ||
    mime.includes('spreadsheet') ||
    mime.includes('excel')
  ) {
    return <FiFileText className="size-5 text-emerald-500 shrink-0" />
  }
  if (
    lower.endsWith('.pptx') ||
    lower.endsWith('.ppt') ||
    mime.includes('presentation') ||
    mime.includes('powerpoint')
  ) {
    return <FiFileText className="size-5 text-amber-500 shrink-0" />
  }
  return <FiFile className="size-5 text-indigo-500 shrink-0" />
}

export default function DealAttachmentsSection({
  companyId,
  dealId,
  canEdit,
  onAttachmentChanged,
}: Props) {
  const [attachments, setAttachments] = useState<DealAttachment[]>([])
  const [loading, setLoading] = useState(true)
  const [uploading, setUploading] = useState(false)
  const [uploadProgress, setUploadProgress] = useState(0)
  const [uploadingName, setUploadingName] = useState('')
  const [, setError] = useSnackbarState('', 'error')
  const [isDragOver, setIsDragOver] = useState(false)
  const [downloadingId, setDownloadingId] = useState<string | null>(null)

  const fileInputRef = useRef<HTMLInputElement>(null)

  useEffect(() => {
    loadAttachments()
  }, [companyId, dealId])

  async function loadAttachments() {
    setLoading(true)
    setError('')
    try {
      const data = await crmSalesApi.listAttachments(companyId, dealId)
      setAttachments(data)
    } catch (err) {
      setError(apiErrorMessage(err, 'Failed to load deal documents'))
    } finally {
      setLoading(false)
    }
  }

  async function handleFileUpload(files: FileList | File[]) {
    if (!files || files.length === 0) return

    setUploading(true)
    setError('')

    try {
      for (let i = 0; i < files.length; i++) {
        const file = files[i]
        setUploadingName(file.name)
        setUploadProgress(0)

        // 1. Initiate upload
        const upload = await mediaApi.initiateUpload(file, 'MESSAGE_ATTACHMENT', companyId)

        // 2. Upload file bytes with progress tracking
        await mediaApi.uploadFileToStorage(upload, file, (progress) => {
          setUploadProgress(progress)
        })

        // 3. Mark complete in media service
        await mediaApi.completeUpload(upload.mediaId)

        // 4. Attach to CRM deal
        await crmSalesApi.attachDocument(companyId, dealId, {
          mediaId: upload.mediaId,
          filename: file.name,
          contentType: file.type || undefined,
          sizeBytes: file.size,
        })
      }

      await loadAttachments()
      onAttachmentChanged?.()
    } catch (err) {
      setError(apiErrorMessage(err, 'Upload failed. Please try again.'))
    } finally {
      setUploading(false)
      setUploadProgress(0)
      setUploadingName('')
      if (fileInputRef.current) fileInputRef.current.value = ''
    }
  }

  async function handleDownload(att: DealAttachment) {
    setDownloadingId(att.id)
    try {
      const access = await mediaApi.access(att.mediaId)
      if (access && access.url) {
        window.open(access.url, '_blank')
      }
    } catch (err) {
      setError(apiErrorMessage(err, 'Failed to generate download URL'))
    } finally {
      setDownloadingId(null)
    }
  }

  async function handleDelete(att: DealAttachment) {
    if (!window.confirm(`Delete document "${att.filename}"?`)) return

    try {
      await crmSalesApi.deleteAttachment(companyId, dealId, att.id)
      setAttachments((prev) => prev.filter((a) => a.id !== att.id))
      onAttachmentChanged?.()
    } catch (err) {
      setError(apiErrorMessage(err, 'Failed to delete attachment'))
    }
  }

  function onDragOver(e: React.DragEvent) {
    e.preventDefault()
    e.stopPropagation()
    setIsDragOver(true)
  }

  function onDragLeave(e: React.DragEvent) {
    e.preventDefault()
    e.stopPropagation()
    setIsDragOver(false)
  }

  function onDrop(e: React.DragEvent) {
    e.preventDefault()
    e.stopPropagation()
    setIsDragOver(false)
    if (e.dataTransfer.files && e.dataTransfer.files.length > 0) {
      void handleFileUpload(e.dataTransfer.files)
    }
  }

  return (
    <div className="mt-6 border-t border-slate-100 dark:border-slate-800 pt-5 space-y-3">
      <div className="flex items-center justify-between">
        <h3 className="flex items-center gap-2 text-sm font-bold text-slate-950 dark:text-slate-100">
          <FiPaperclip className="size-4 text-indigo-500" />
          <span>Documents & Proposals</span>
          <span className="rounded-full bg-slate-100 dark:bg-slate-800 px-2 py-0.5 text-[11px] font-semibold text-slate-600 dark:text-slate-300">
            {attachments.length}
          </span>
        </h3>

        {canEdit && (
          <button
            type="button"
            onClick={() => fileInputRef.current?.click()}
            disabled={uploading}
            className="inline-flex items-center gap-1 text-xs font-semibold text-indigo-600 dark:text-indigo-400 hover:text-indigo-700 disabled:opacity-50 transition"
          >
            <FiUploadCloud className="size-3.5" />
            <span>Upload file</span>
          </button>
        )}
      </div>

      <input
        ref={fileInputRef}
        type="file"
        multiple
        className="hidden"
        onChange={(e) => {
          if (e.target.files && e.target.files.length > 0) {
            void handleFileUpload(e.target.files)
          }
        }}
      />



      {/* Upload Dropzone */}
      {canEdit && (
        <div
          onDragOver={onDragOver}
          onDragLeave={onDragLeave}
          onDrop={onDrop}
          onClick={() => !uploading && fileInputRef.current?.click()}
          className={`flex flex-col items-center justify-center rounded-xl border-2 border-dashed p-4 text-center cursor-pointer transition ${
            isDragOver
              ? 'border-indigo-500 bg-indigo-50/50 dark:bg-indigo-950/30'
              : 'border-slate-200 dark:border-slate-700 hover:border-slate-300 dark:hover:border-slate-600 bg-slate-50/50 dark:bg-slate-850/40'
          }`}
        >
          {uploading ? (
            <div className="w-full space-y-2">
              <div className="flex items-center justify-between text-xs text-slate-600 dark:text-slate-400">
                <span className="truncate max-w-[200px] font-medium">{uploadingName}</span>
                <span className="font-semibold text-indigo-600">{uploadProgress}%</span>
              </div>
              <div className="h-1.5 w-full overflow-hidden rounded-full bg-slate-200 dark:bg-slate-700">
                <div
                  className="h-full bg-indigo-600 transition-all duration-200 ease-out"
                  style={{ width: `${uploadProgress}%` }}
                />
              </div>
              <p className="text-[11px] text-slate-400">Uploading to secure storage...</p>
            </div>
          ) : (
            <>
              <FiUploadCloud className="size-6 text-slate-400 mb-1" />
              <p className="text-xs text-slate-600 dark:text-slate-400">
                <span className="font-semibold text-indigo-600 dark:text-indigo-400">Click to upload</span> or drag and drop
              </p>
              <p className="text-[10px] text-slate-400 mt-0.5">
                Proposals, contracts, PDFs, spreadsheets, presentations up to 50MB
              </p>
            </>
          )}
        </div>
      )}

      {/* Attachments List */}
      {loading ? (
        <div className="flex items-center justify-center py-4">
          <div className="h-5 w-5 animate-spin rounded-full border-2 border-indigo-600 border-t-transparent" />
        </div>
      ) : attachments.length === 0 ? (
        <p className="text-center py-3 text-xs text-slate-400 dark:text-slate-500">
          No files attached to this opportunity yet.
        </p>
      ) : (
        <div className="space-y-2">
          {attachments.map((att) => (
            <div
              key={att.id}
              className="flex items-center justify-between gap-3 rounded-xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 p-2.5 hover:shadow-xs transition"
            >
              <div className="flex items-center gap-2.5 min-w-0 flex-1">
                {getFileIcon(att.filename, att.contentType)}
                <div className="min-w-0 flex-1">
                  <p
                    className="truncate text-xs font-semibold text-slate-900 dark:text-slate-100"
                    title={att.filename}
                  >
                    {att.filename}
                  </p>
                  <p className="text-[11px] text-slate-400">
                    {formatBytes(att.sizeBytes)} • {new Date(att.createdAt).toLocaleDateString()}
                    {att.uploaderName && ` • by ${att.uploaderName}`}
                  </p>
                </div>
              </div>

              <div className="flex items-center gap-1 shrink-0">
                <button
                  type="button"
                  onClick={() => handleDownload(att)}
                  disabled={downloadingId === att.id}
                  title="Download / View document"
                  className="rounded-lg p-1.5 text-slate-500 hover:text-indigo-600 dark:text-slate-400 dark:hover:text-indigo-400 hover:bg-slate-50 dark:hover:bg-slate-800 transition"
                >
                  {downloadingId === att.id ? (
                    <div className="h-4 w-4 animate-spin rounded-full border-2 border-indigo-600 border-t-transparent" />
                  ) : (
                    <FiDownload className="size-4" />
                  )}
                </button>

                {canEdit && (
                  <button
                    type="button"
                    onClick={() => handleDelete(att)}
                    title="Delete document"
                    className="rounded-lg p-1.5 text-slate-400 hover:text-rose-600 hover:bg-rose-50 dark:hover:bg-rose-950/40 transition"
                  >
                    <FiTrash2 className="size-4" />
                  </button>
                )}
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
