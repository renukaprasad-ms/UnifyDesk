import { useEffect, useRef, useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router'
import { FiImage, FiUploadCloud, FiX } from 'react-icons/fi'
import { workspaceApi } from '../../features/workspace/workspaceApi'
import { useAppDispatch } from '../../store/hooks'
import { selectCompany, setCompanies } from '../../store/workspace/workspaceSlice'
import { mediaApi } from '../../features/media/mediaApi'
import type { InitiateMediaUploadResponse } from '../../features/media/types'
import { apiErrorMessage } from '../../lib/api/apiClient'

export default function CreateWorkspace() {
  const navigate = useNavigate()
  const dispatch = useAppDispatch()
  const [name, setName] = useState('')
  const [businessEmail, setBusinessEmail] = useState('')
  const [country, setCountry] = useState('India')
  const [timezone, setTimezone] = useState('Asia/Kolkata')
  const [logo, setLogo] = useState<File | null>(null)
  const [logoPreviewUrl, setLogoPreviewUrl] = useState('')
  const [logoUploadStatus, setLogoUploadStatus] = useState('')
  const [logoUploadProgress, setLogoUploadProgress] = useState(0)
  const [logoUploadResult, setLogoUploadResult] = useState<InitiateMediaUploadResponse | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState('')
  const logoPreviewUrlRef = useRef('')
  const logoUploadRunIdRef = useRef(0)
  const logoUploadPromiseRef = useRef<Promise<InitiateMediaUploadResponse> | null>(null)

  const companyInitials =
    name
      .split(' ')
      .filter(Boolean)
      .slice(0, 2)
      .map((part) => part[0]?.toUpperCase())
      .join('')
      .slice(0, 2) || 'CO'

  useEffect(
    () => () => {
      if (logoPreviewUrlRef.current) URL.revokeObjectURL(logoPreviewUrlRef.current)
    },
    [],
  )

  const uploadLogoOnChange = async (
    file: File,
    runId: number,
  ): Promise<InitiateMediaUploadResponse> => {
    setLogoUploadStatus('Preparing upload...')
    setLogoUploadProgress(1)
    const upload = await mediaApi.initiateCompanyLogoUpload(file)
    if (logoUploadRunIdRef.current !== runId) return upload
    setLogoUploadStatus('Uploading logo...')
    await mediaApi.uploadFileToStorage(upload, file, (progress) => {
      if (logoUploadRunIdRef.current === runId) setLogoUploadProgress(progress)
    })
    if (logoUploadRunIdRef.current === runId) {
      setLogoUploadProgress(100)
      setLogoUploadStatus('Uploaded. It will be saved when you create.')
      setLogoUploadResult(upload)
    }
    return upload
  }

  const onLogoChange = (file: File | null) => {
    const runId = logoUploadRunIdRef.current + 1
    logoUploadRunIdRef.current = runId
    logoUploadPromiseRef.current = null
    setLogoUploadResult(null)
    setLogoUploadProgress(0)
    if (logoPreviewUrlRef.current) {
      URL.revokeObjectURL(logoPreviewUrlRef.current)
      logoPreviewUrlRef.current = ''
    }
    if (file) {
      const previewUrl = URL.createObjectURL(file)
      logoPreviewUrlRef.current = previewUrl
      setLogoPreviewUrl(previewUrl)
    } else {
      setLogoPreviewUrl('')
    }
    setLogo(file)
    setLogoUploadStatus(file ? 'Preparing upload...' : '')
    if (file) {
      const uploadPromise = uploadLogoOnChange(file, runId).catch((uploadError) => {
        if (logoUploadRunIdRef.current === runId) {
          setLogoUploadProgress(0)
          setLogoUploadStatus(apiErrorMessage(uploadError, 'Logo upload failed'))
        }
        throw uploadError
      })
      logoUploadPromiseRef.current = uploadPromise
    }
  }

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    setError('')
    setSubmitting(true)
    try {
      let logoUpload = logoUploadResult
      if (logo && !logoUpload) {
        try {
          logoUpload = await logoUploadPromiseRef.current
        } catch (uploadError) {
          setError(apiErrorMessage(uploadError, 'Logo upload failed'))
          return
        }
      }
      if (logoUpload) await mediaApi.completeUpload(logoUpload.mediaId)
      const company = await workspaceApi.create({
        name,
        businessEmail,
        country,
        timezone,
        logoMediaId: logoUpload?.mediaId,
      })
      const companies = await workspaceApi.list()
      dispatch(setCompanies(companies))
      dispatch(selectCompany(company.companyId))
      navigate(`/select-plan/${company.companyId}`, { replace: true })
    } catch (submitError) {
      setError(apiErrorMessage(submitError, 'Unable to create workspace'))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="min-h-dvh bg-white px-4 py-10 sm:px-6 sm:py-20">
      <main className="mx-auto w-full max-w-[358px]">
        <h1 className="text-[26px] font-bold leading-tight text-slate-950">
          Create your workspace
        </h1>
        <p className="mt-2 text-xs text-slate-500">
          Company and workspace are the same concept in Phase 1
        </p>
        <form className="mt-5 space-y-4" onSubmit={submit}>
          <div className="block">
            <span className="mb-2 block text-[11px] font-medium text-slate-600">Company logo</span>
            <label className="profile-upload">
              <input
                className="sr-only"
                type="file"
                accept="image/jpeg,image/png,image/webp"
                onChange={(event) => onLogoChange(event.target.files?.[0] ?? null)}
              />
              <span className="profile-upload-preview font-semibold">
                {logoPreviewUrl ? (
                  <img src={logoPreviewUrl} alt="" />
                ) : name.trim() ? (
                  companyInitials
                ) : (
                  <FiImage aria-hidden="true" size={22} />
                )}
              </span>
              <span className="min-w-0 flex-1">
                <span className="block truncate text-sm font-semibold text-slate-800">
                  {logo?.name ?? 'Choose a logo'}
                </span>
                <span className="mt-1 block text-xs text-slate-500">
                  JPG, PNG, or WEBP. Optional.
                </span>
              </span>
              <FiUploadCloud className="text-indigo-500" aria-hidden="true" size={20} />
            </label>
            {logo && (
              <div className="mt-2 flex items-center justify-between gap-3 text-xs text-slate-500">
                <span>{logoUploadStatus}</span>
                <button
                  type="button"
                  className="inline-flex items-center gap-1 font-semibold text-rose-600"
                  onClick={() => onLogoChange(null)}
                >
                  <FiX aria-hidden="true" />
                  Remove
                </button>
              </div>
            )}
            {logo && logoUploadProgress > 0 && logoUploadProgress < 100 && (
              <div className="upload-progress mt-2" aria-label="Company logo upload progress">
                <span style={{ width: `${logoUploadProgress}%` }} />
              </div>
            )}
          </div>
          <label className="block">
            <span className="mb-2 block text-[11px] font-medium text-slate-600">Company name</span>
            <input
              className="h-10 w-full rounded-[8px] border border-slate-200 px-3 text-sm text-slate-950 placeholder:text-slate-400"
              placeholder="Acme Pvt Ltd"
              value={name}
              onChange={(event) => setName(event.target.value)}
              maxLength={160}
              required
            />
          </label>
          <label className="block">
            <span className="mb-2 block text-[11px] font-medium text-slate-600">
              Business email
            </span>
            <input
              className="h-10 w-full rounded-[8px] border border-slate-200 px-3 text-sm text-slate-950 placeholder:text-slate-400"
              type="email"
              placeholder="hello@acme.in"
              value={businessEmail}
              onChange={(event) => setBusinessEmail(event.target.value)}
            />
          </label>
          <label className="block">
            <span className="mb-2 block text-[11px] font-medium text-slate-600">Country</span>
            <input
              className="h-10 w-full rounded-[8px] border border-slate-200 px-3 text-sm text-slate-950 placeholder:text-slate-400"
              value={country}
              onChange={(event) => setCountry(event.target.value)}
            />
          </label>
          <label className="block">
            <span className="mb-2 block text-[11px] font-medium text-slate-600">Timezone</span>
            <input
              className="h-10 w-full rounded-[8px] border border-slate-200 px-3 text-sm text-slate-950 placeholder:text-slate-400"
              value={timezone}
              onChange={(event) => setTimezone(event.target.value)}
            />
          </label>
          {error && <p className="text-xs font-semibold text-red-600">{error}</p>}
          <button
            type="submit"
            className="mt-5 flex h-10 w-full items-center justify-center rounded-[8px] bg-indigo-500 text-xs font-semibold text-white"
            disabled={submitting}
          >
            {submitting ? 'Creating...' : 'Create workspace'}
          </button>
        </form>
        <p className="mt-5 text-[11px] text-slate-500">You become the workspace OWNER</p>
      </main>
    </div>
  )
}
