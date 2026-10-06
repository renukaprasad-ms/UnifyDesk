import { useEffect, useMemo, useRef, useState, type FormEvent } from 'react'
import { FiBarChart2, FiImage, FiMail, FiMessageSquare, FiUploadCloud, FiUsers, FiX } from 'react-icons/fi'
import { useNavigate } from 'react-router'
import AsyncButton from '../../components/ui/AsyncButton'
import ProgressBar from '../../components/ui/ProgressBar'
import { DEFAULT_BRAND_COLOR } from '../../features/companyTheme/companyTheme'
import { mediaApi } from '../../features/media/mediaApi'
import type { InitiateMediaUploadResponse } from '../../features/media/types'
import { workspaceApi } from '../../features/workspace/workspaceApi'
import useBeforeUnloadGuard from '../../hooks/useBeforeUnloadGuard'
import { apiErrorMessage } from '../../lib/api/apiClient'
import { useAppDispatch } from '../../store/hooks'
import { selectCompany, setCompanies } from '../../store/workspace/workspaceSlice'

const countries = [
  ['IN', 'India'],
  ['US', 'United States'],
  ['GB', 'United Kingdom'],
  ['AE', 'United Arab Emirates'],
  ['SG', 'Singapore'],
  ['AU', 'Australia'],
] as const

const timezones = [
  'Asia/Kolkata',
  'Asia/Dubai',
  'Asia/Singapore',
  'Europe/London',
  'America/New_York',
  'America/Los_Angeles',
  'Australia/Sydney',
]

const isHexColor = (value: string) => /^#[0-9A-Fa-f]{6}$/.test(value)

export default function CreateWorkspace() {
  const navigate = useNavigate()
  const dispatch = useAppDispatch()
  const [name, setName] = useState('')
  const [businessEmail, setBusinessEmail] = useState('')
  const [country, setCountry] = useState('IN')
  const [timezone, setTimezone] = useState('Asia/Kolkata')
  const [brandColor, setBrandColor] = useState(DEFAULT_BRAND_COLOR)
  const [logo, setLogo] = useState<File | null>(null)
  const [logoPreviewUrl, setLogoPreviewUrl] = useState('')
  const [logoUploadStatus, setLogoUploadStatus] = useState('')
  const [logoUploadProgress, setLogoUploadProgress] = useState(0)
  const [logoUploadResult, setLogoUploadResult] = useState<InitiateMediaUploadResponse | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const [logoUploading, setLogoUploading] = useState(false)
  const [error, setError] = useState('')
  const logoPreviewUrlRef = useRef('')
  const logoUploadRunIdRef = useRef(0)
  const logoUploadPromiseRef = useRef<Promise<InitiateMediaUploadResponse> | null>(null)

  useBeforeUnloadGuard(
    !submitting &&
      Boolean(
        name.trim() ||
          businessEmail.trim() ||
          logo ||
          brandColor.toUpperCase() !== DEFAULT_BRAND_COLOR,
      ),
  )

  const companyInitials =
    name
      .split(' ')
      .filter(Boolean)
      .slice(0, 2)
      .map((part) => part[0]?.toUpperCase())
      .join('')
      .slice(0, 2) || 'CO'

  const previewColor = useMemo(
    () => (isHexColor(brandColor) ? brandColor.toUpperCase() : DEFAULT_BRAND_COLOR),
    [brandColor],
  )

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
    setLogoUploading(true)
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
      setLogoUploading(false)
    }
    return upload
  }

  const onLogoChange = (file: File | null) => {
    const runId = logoUploadRunIdRef.current + 1
    logoUploadRunIdRef.current = runId
    logoUploadPromiseRef.current = null
    setLogoUploadResult(null)
    setLogoUploading(Boolean(file))
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
          setLogoUploading(false)
        }
        throw uploadError
      })
      logoUploadPromiseRef.current = uploadPromise
    }
  }

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    setError('')
    if (!isHexColor(brandColor)) {
      setError('Choose a valid 6-digit brand colour, for example #2563EB.')
      return
    }

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
        brandColor: brandColor.toUpperCase(),
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
    <div className="min-h-dvh bg-white px-4 py-8 sm:px-6 lg:px-8">
      <main className="mx-auto grid w-full max-w-6xl gap-8 lg:grid-cols-[minmax(320px,430px)_minmax(0,1fr)] lg:items-start">
        <section>
          <p className="eyebrow">Workspace setup</p>
          <h1 className="mt-2 text-[28px] font-bold leading-tight text-slate-950">
            Create your workspace
          </h1>
          <p className="mt-2 text-sm leading-6 text-slate-500">
            Set your company identity once. Your brand colour is shared with everyone in this
            workspace; Light/Dark appearance is a personal account preference.
          </p>

          <form className="mt-6 space-y-4" onSubmit={submit}>
            <div>
              <span className="mb-2 block text-[11px] font-medium text-slate-600">Company logo</span>
              <label className="profile-upload">
                <input
                  className="sr-only"
                  type="file"
                  accept="image/jpeg,image/png,image/webp"
                  onChange={(event) => onLogoChange(event.target.files?.[0] ?? null)}
                />
                <span
                  className="profile-upload-preview font-semibold"
                  style={{ backgroundColor: `${previewColor}14`, color: previewColor }}
                >
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
                <FiUploadCloud style={{ color: previewColor }} aria-hidden="true" size={20} />
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
                <ProgressBar
                  className="mt-2"
                  value={logoUploadProgress}
                  label="Uploading company logo"
                  showValue
                />
              )}
            </div>

            <label className="block">
              <span className="mb-2 block text-[11px] font-medium text-slate-600">Company name</span>
              <input
                className="form-input"
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
                className="form-input"
                type="email"
                placeholder="hello@acme.in"
                value={businessEmail}
                onChange={(event) => setBusinessEmail(event.target.value)}
              />
            </label>

            <div className="grid gap-4 sm:grid-cols-2">
              <label className="block">
                <span className="mb-2 block text-[11px] font-medium text-slate-600">Country</span>
                <select
                  className="form-input"
                  value={country}
                  onChange={(event) => setCountry(event.target.value)}
                >
                  {countries.map(([code, label]) => (
                    <option key={code} value={code}>
                      {label}
                    </option>
                  ))}
                </select>
              </label>
              <label className="block">
                <span className="mb-2 block text-[11px] font-medium text-slate-600">Timezone</span>
                <select
                  className="form-input"
                  value={timezone}
                  onChange={(event) => setTimezone(event.target.value)}
                >
                  {timezones.map((zone) => (
                    <option key={zone} value={zone}>
                      {zone}
                    </option>
                  ))}
                </select>
              </label>
            </div>

            <fieldset className="rounded-xl border border-slate-200 p-4">
              <legend className="px-1 text-xs font-bold text-slate-800">Workspace brand colour</legend>
              <p className="mt-1 text-xs leading-5 text-slate-500">
                Pick the accent used for primary buttons, active navigation and highlights.
              </p>
              <div className="mt-4 flex items-center gap-3">
                <input
                  type="color"
                  value={previewColor}
                  onChange={(event) => setBrandColor(event.target.value.toUpperCase())}
                  className="h-11 w-14 cursor-pointer rounded-lg border border-slate-200 bg-white p-1"
                  aria-label="Choose workspace brand colour"
                />
                <input
                  className="form-input font-mono uppercase"
                  value={brandColor}
                  maxLength={7}
                  onChange={(event) => setBrandColor(event.target.value)}
                  placeholder="#2563EB"
                  aria-label="Brand colour hex value"
                />
              </div>
            </fieldset>

            {error && <p className="text-xs font-semibold text-red-600">{error}</p>}
            <AsyncButton
              type="submit"
              className="h-11 w-full rounded-[8px] text-xs font-semibold text-white"
              style={{ backgroundColor: previewColor }}
              disabled={logoUploading || !isHexColor(brandColor)}
              loading={submitting}
              loadingLabel="Creating workspace…"
            >
              {logoUploading ? 'Uploading logo…' : 'Create workspace'}
            </AsyncButton>
          </form>
          <p className="mt-4 text-[11px] text-slate-500">You become the workspace OWNER.</p>
        </section>

        <aside className="lg:sticky lg:top-8">
          <div className="rounded-2xl border border-slate-200 bg-slate-50 p-4 sm:p-6">
            <div className="mb-4">
              <p className="text-xs font-bold uppercase tracking-wide text-slate-500">Live preview</p>
              <p className="mt-1 text-sm text-slate-600">
                This is how your accent will feel inside the UnifyDesk dashboard.
              </p>
            </div>
            <div className="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-xl shadow-slate-200/60">
              <div className="flex min-h-[440px]">
                <div className="w-[112px] border-r border-slate-100 bg-slate-50 p-3">
                  <div
                    className="flex size-9 items-center justify-center overflow-hidden rounded-lg text-xs font-bold"
                    style={{ backgroundColor: `${previewColor}18`, color: previewColor }}
                  >
                    {logoPreviewUrl ? (
                      <img src={logoPreviewUrl} alt="" className="h-full w-full object-cover" />
                    ) : (
                      companyInitials
                    )}
                  </div>
                  <div className="mt-6 space-y-2">
                    {[FiBarChart2, FiMessageSquare, FiUsers, FiMail].map((Icon, index) => (
                      <div
                        key={index}
                        className="flex h-9 items-center gap-2 rounded-lg px-2 text-[10px] font-semibold"
                        style={
                          index === 0
                            ? { backgroundColor: `${previewColor}12`, color: previewColor }
                            : { color: '#64748b' }
                        }
                      >
                        <Icon size={13} />
                        <span>{['Home', 'Inbox', 'Contacts', 'Broadcast'][index]}</span>
                      </div>
                    ))}
                  </div>
                </div>
                <div className="min-w-0 flex-1 p-4 sm:p-5">
                  <div className="flex items-start justify-between gap-3">
                    <div>
                      <p className="text-[10px] font-semibold uppercase tracking-wide text-slate-400">
                        Dashboard
                      </p>
                      <h2 className="mt-1 truncate text-base font-bold text-slate-900">
                        {name.trim() || 'Your company'}
                      </h2>
                    </div>
                    <button
                      type="button"
                      tabIndex={-1}
                      className="rounded-lg px-3 py-2 text-[10px] font-bold text-white"
                      style={{ backgroundColor: previewColor }}
                    >
                      New broadcast
                    </button>
                  </div>

                  <div className="mt-5 grid grid-cols-2 gap-3">
                    {[
                      ['Contacts', '1,248'],
                      ['Open chats', '32'],
                      ['Delivered', '98.4%'],
                      ['Campaigns', '12'],
                    ].map(([label, value], index) => (
                      <div key={label} className="rounded-xl border border-slate-100 p-3">
                        <p className="text-[10px] font-medium text-slate-400">{label}</p>
                        <p className="mt-2 text-lg font-bold text-slate-900">{value}</p>
                        <div className="mt-3 h-1.5 overflow-hidden rounded-full bg-slate-100">
                          <div
                            className="h-full rounded-full"
                            style={{ width: `${52 + index * 11}%`, backgroundColor: previewColor }}
                          />
                        </div>
                      </div>
                    ))}
                  </div>

                  <div className="mt-4 rounded-xl border border-slate-100 p-4">
                    <div className="flex items-center justify-between">
                      <p className="text-xs font-bold text-slate-800">Recent activity</p>
                      <span className="text-[10px] font-semibold" style={{ color: previewColor }}>
                        View all
                      </span>
                    </div>
                    <div className="mt-4 space-y-3">
                      {[68, 84, 55].map((width, index) => (
                        <div key={index} className="flex items-center gap-3">
                          <span
                            className="size-7 rounded-lg"
                            style={{ backgroundColor: `${previewColor}16` }}
                          />
                          <div className="flex-1">
                            <div className="h-2 rounded bg-slate-200" style={{ width: `${width}%` }} />
                            <div className="mt-1.5 h-1.5 w-20 rounded bg-slate-100" />
                          </div>
                        </div>
                      ))}
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </aside>
      </main>
    </div>
  )
}
