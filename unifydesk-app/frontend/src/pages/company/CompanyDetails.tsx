import { useEffect, useRef, useState, type FormEvent } from 'react'
import {
  FiBriefcase,
  FiCheckCircle,
  FiClock,
  FiEdit2,
  FiMail,
  FiMapPin,
  FiTrash2,
  FiUploadCloud,
  FiX,
} from 'react-icons/fi'
import { useNavigate } from 'react-router'
import ConfirmDialog from '../../components/ui/ConfirmDialog'
import CountrySelect from '../../components/ui/CountrySelect'
import { companyApi } from '../../features/company/companyApi'
import type { Company } from '../../features/company/types'
import { brandContrastColor, DEFAULT_BRAND_COLOR } from '../../features/companyTheme/companyTheme'
import { PERMISSIONS } from '../../features/iam/permissions'
import { useIam } from '../../features/iam/useIam'
import { mediaApi } from '../../features/media/mediaApi'
import { workspaceApi } from '../../features/workspace/workspaceApi'
import { apiErrorMessage } from '../../lib/api/apiClient'
import { useAppDispatch, useAppSelector } from '../../store/hooks'
import {
  selectCompanies,
  selectSelectedCompany,
  setCompanies,
} from '../../store/workspace/workspaceSlice'

const isHexColor = (value: string) => /^#[0-9A-Fa-f]{6}$/.test(value)

const initialsFor = (name: string) =>
  name
    .split(' ')
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase())
    .join('')
    .slice(0, 2) || 'CO'

export default function CompanyDetails() {
  const navigate = useNavigate()
  const dispatch = useAppDispatch()
  const companies = useAppSelector(selectCompanies)
  const selectedCompany = useAppSelector(selectSelectedCompany)
  const { can } = useIam()
  const [company, setCompany] = useState<Company | null>(selectedCompany)
  const [name, setName] = useState(selectedCompany?.name ?? '')
  const [businessEmail, setBusinessEmail] = useState(selectedCompany?.businessEmail ?? '')
  const [country, setCountry] = useState(selectedCompany?.country ?? '')
  const [timezone, setTimezone] = useState(selectedCompany?.timezone ?? '')
  const [brandColor, setBrandColor] = useState(selectedCompany?.brandColor ?? DEFAULT_BRAND_COLOR)
  const [logoUrl, setLogoUrl] = useState('')
  const [logoFile, setLogoFile] = useState<File | null>(null)
  const [logoPreviewUrl, setLogoPreviewUrl] = useState('')
  const [removeLogo, setRemoveLogo] = useState(false)
  const [uploadProgress, setUploadProgress] = useState(0)
  const [editing, setEditing] = useState(false)
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [deleteConfirmOpen, setDeleteConfirmOpen] = useState(false)
  const [leaveConfirmOpen, setLeaveConfirmOpen] = useState(false)
  const previewUrlRef = useRef('')

  const syncForm = (value: Company) => {
    setCompany(value)
    setName(value.name)
    setBusinessEmail(value.businessEmail ?? '')
    setCountry(value.country ?? '')
    setTimezone(value.timezone ?? '')
    setBrandColor(value.brandColor ?? DEFAULT_BRAND_COLOR)
  }

  useEffect(() => {
    let active = true
    if (!selectedCompany) {
      setLoading(false)
      return
    }

    setLoading(true)
    companyApi
      .get(selectedCompany.companyId)
      .then((value) => {
        if (!active) return
        syncForm(value)
        if (!value.logoMediaId) {
          setLogoUrl('')
          return
        }
        return mediaApi.access(value.logoMediaId).then((access) => {
          if (active) setLogoUrl(access.url)
        })
      })
      .catch((loadError) => {
        if (active) setError(apiErrorMessage(loadError, 'Unable to load company profile'))
      })
      .finally(() => {
        if (active) setLoading(false)
      })

    return () => {
      active = false
    }
  }, [selectedCompany?.companyId])

  useEffect(
    () => () => {
      if (previewUrlRef.current) URL.revokeObjectURL(previewUrlRef.current)
    },
    [],
  )

  const resetLogoPreview = () => {
    if (previewUrlRef.current) {
      URL.revokeObjectURL(previewUrlRef.current)
      previewUrlRef.current = ''
    }
    setLogoPreviewUrl('')
    setLogoFile(null)
    setUploadProgress(0)
  }

  const beginEdit = () => {
    if (!company) return
    syncForm(company)
    setRemoveLogo(false)
    resetLogoPreview()
    setError('')
    setNotice('')
    setEditing(true)
  }

  const cancelEdit = () => {
    if (company) syncForm(company)
    setRemoveLogo(false)
    resetLogoPreview()
    setError('')
    setEditing(false)
  }

  const onLogoChange = (file: File | null) => {
    resetLogoPreview()
    setRemoveLogo(false)
    if (!file) return
    const preview = URL.createObjectURL(file)
    previewUrlRef.current = preview
    setLogoPreviewUrl(preview)
    setLogoFile(file)
  }

  const save = async (event: FormEvent) => {
    event.preventDefault()
    if (!company || !selectedCompany) return
    setError('')
    setNotice('')
    if (!isHexColor(brandColor)) {
      setError('Choose a valid 6-digit brand colour, for example #2563EB.')
      return
    }
    setSaving(true)

    try {
      let logoMediaId = removeLogo ? null : (company.logoMediaId ?? null)

      if (logoFile) {
        const upload = await mediaApi.initiateCompanyLogoUpload(logoFile, selectedCompany.companyId)
        await mediaApi.uploadFileToStorage(upload, logoFile, setUploadProgress)
        await mediaApi.completeUpload(upload.mediaId)
        logoMediaId = upload.mediaId
      }

      const updated = await companyApi.update(company.companyId, {
        name: name.trim(),
        businessEmail: businessEmail.trim() || null,
        country: country.trim() || null,
        timezone: timezone.trim() || null,
        logoMediaId,
        brandColor: brandColor.toUpperCase(),
      })

      syncForm(updated)
      dispatch(
        setCompanies(
          companies.map((item) => (item.companyId === updated.companyId ? updated : item)),
        ),
      )

      if (updated.logoMediaId) {
        const access = await mediaApi.access(updated.logoMediaId).catch(() => null)
        setLogoUrl(access?.url ?? logoPreviewUrl)
      } else {
        setLogoUrl('')
      }

      setRemoveLogo(false)
      resetLogoPreview()
      setEditing(false)
      setNotice('Company profile updated')
    } catch (saveError) {
      setError(apiErrorMessage(saveError, 'Unable to update company profile'))
    } finally {
      setSaving(false)
    }
  }

  const deleteWorkspace = async () => {
    if (!company) return
    setSaving(true)
    setError('')
    setNotice('')
    try {
      await companyApi.delete(company.companyId)
      const remaining = await workspaceApi.list().catch(() => [])
      dispatch(setCompanies(remaining))
      navigate(remaining.length > 0 ? '/select-workspace' : '/create-workspace', { replace: true })
    } catch (deleteError) {
      setError(apiErrorMessage(deleteError, 'Unable to schedule workspace deletion'))
    } finally {
      setSaving(false)
      setDeleteConfirmOpen(false)
    }
  }

  const leaveWorkspace = async () => {
    if (!company) return
    setSaving(true)
    setError('')
    setNotice('')
    try {
      await companyApi.leave(company.companyId)
      const remaining = await workspaceApi.list().catch(() => [])
      dispatch(setCompanies(remaining))
      navigate(
        remaining.some((item) => item.status === 'ACTIVE')
          ? '/select-workspace'
          : '/create-workspace',
        {
          replace: true,
        },
      )
    } catch (leaveError) {
      setError(apiErrorMessage(leaveError, 'Unable to leave workspace'))
    } finally {
      setSaving(false)
      setLeaveConfirmOpen(false)
    }
  }

  const visibleLogo = logoPreviewUrl || (!removeLogo ? logoUrl : '')
  const previewBrandColor = isHexColor(brandColor)
    ? brandColor.toUpperCase()
    : (company?.brandColor ?? DEFAULT_BRAND_COLOR)
  const previewBrandContrast = brandContrastColor(previewBrandColor)
  const canModifyCompany = can(PERMISSIONS.COMPANY_MODIFY)
  const canDeleteCompany = can(PERMISSIONS.COMPANY_DELETE)
  const canLeaveCompany = company?.role === 'MANAGER' || company?.role === 'MEMBER'

  if (!selectedCompany) {
    return (
      <section className="px-4 py-8 sm:px-6 lg:px-8">
        <div className="panel mx-auto max-w-4xl p-8 text-center text-sm text-slate-500">
          Select a workspace to view company details.
        </div>
      </section>
    )
  }

  return (
    <section className="px-4 py-8 sm:px-6 lg:px-8">
      <div className="mx-auto max-w-5xl">
        <header className="flex flex-wrap items-end justify-between gap-4">
          <div>
            <p className="eyebrow">Company</p>
            <h1 className="mt-2 text-3xl font-bold tracking-tight text-slate-950">
              Company profile
            </h1>
            <p className="mt-2 max-w-2xl text-sm leading-6 text-slate-500">
              Manage the business identity and workspace brand colour. Light/Dark appearance is
              controlled separately from each user's account.
            </p>
          </div>
          {!editing && canModifyCompany && (
            <button
              type="button"
              className="primary-button inline-flex items-center gap-2"
              onClick={beginEdit}
              disabled={loading || !company}
            >
              <FiEdit2 aria-hidden="true" />
              Edit profile
            </button>
          )}
        </header>

        {(error || notice) && (
          <div
            className={`mt-6 rounded-xl border px-4 py-3 text-sm font-medium ${
              error
                ? 'border-red-200 bg-red-50 text-red-700'
                : 'border-emerald-200 bg-emerald-50 text-emerald-700'
            }`}
          >
            {error || notice}
          </div>
        )}

        <form className="mt-7 space-y-5" onSubmit={save}>
          <div className="panel overflow-hidden">
            <div className="flex flex-col gap-5 border-b border-slate-100 p-6 sm:flex-row sm:items-center">
              <div
                className="flex size-20 shrink-0 items-center justify-center overflow-hidden rounded-2xl text-xl font-bold"
                style={{
                  backgroundColor: `${previewBrandColor}18`,
                  color: previewBrandColor,
                }}
              >
                {visibleLogo ? (
                  <img src={visibleLogo} alt="" className="h-full w-full object-cover" />
                ) : (
                  initialsFor(name || company?.name || 'Company')
                )}
              </div>
              <div className="min-w-0 flex-1">
                <h2 className="text-lg font-bold text-slate-950">{company?.name ?? 'Company'}</h2>
                <p className="mt-1 text-sm text-slate-500">
                  Workspace slug: <span className="font-medium">{company?.slug}</span>
                </p>
                <div className="mt-3 flex flex-wrap items-center gap-2">
                  <span className="rounded-full bg-emerald-50 px-2.5 py-1 text-xs font-semibold text-emerald-700">
                    {company?.status}
                  </span>
                  {company?.role && (
                    <span className="rounded-full bg-indigo-50 px-2.5 py-1 text-xs font-semibold text-indigo-700">
                      {company.role}
                    </span>
                  )}
                  <span className="inline-flex items-center gap-2 rounded-full bg-slate-100 px-2.5 py-1 text-xs font-semibold text-slate-700">
                    <span
                      className="size-3 rounded-full"
                      style={{ backgroundColor: previewBrandColor }}
                    />
                    {previewBrandColor}
                  </span>
                </div>
              </div>
              {editing && (
                <div className="flex flex-wrap items-center gap-2">
                  <label className="secondary-button inline-flex cursor-pointer items-center gap-2">
                    <FiUploadCloud aria-hidden="true" />
                    {logoFile ? 'Change logo' : 'Upload logo'}
                    <input
                      className="sr-only"
                      type="file"
                      accept="image/jpeg,image/png,image/webp"
                      onChange={(event) => onLogoChange(event.target.files?.[0] ?? null)}
                    />
                  </label>
                  {(company?.logoMediaId || logoFile) && (
                    <button
                      type="button"
                      className="rounded-lg px-3 py-2 text-xs font-semibold text-rose-600 hover:bg-rose-50"
                      onClick={() => {
                        resetLogoPreview()
                        setRemoveLogo(true)
                      }}
                    >
                      Remove
                    </button>
                  )}
                </div>
              )}
            </div>

            {logoFile && uploadProgress > 0 && uploadProgress < 100 && (
              <div className="px-6 pt-4">
                <div className="upload-progress">
                  <span style={{ width: `${uploadProgress}%` }} />
                </div>
              </div>
            )}

            <div className="grid gap-5 p-6 md:grid-cols-2">
              <label className="block text-xs font-semibold text-slate-700">
                <span className="mb-2 flex items-center gap-2">
                  <FiBriefcase aria-hidden="true" />
                  Company name
                </span>
                <input
                  className="form-input"
                  value={name}
                  maxLength={160}
                  disabled={!editing}
                  onChange={(event) => setName(event.target.value)}
                  required
                />
              </label>

              <label className="block text-xs font-semibold text-slate-700">
                <span className="mb-2 flex items-center gap-2">
                  <FiMail aria-hidden="true" />
                  Business email
                </span>
                <input
                  className="form-input"
                  type="email"
                  value={businessEmail}
                  maxLength={254}
                  disabled={!editing}
                  onChange={(event) => setBusinessEmail(event.target.value)}
                  placeholder="hello@company.com"
                />
              </label>

              <label className="block text-xs font-semibold text-slate-700">
                <span className="mb-2 flex items-center gap-2">
                  <FiMapPin aria-hidden="true" />
                  Country
                </span>
                <CountrySelect
                  value={country}
                  valueType="code"
                  disabled={!editing}
                  onChange={setCountry}
                />
              </label>

              <label className="block text-xs font-semibold text-slate-700">
                <span className="mb-2 flex items-center gap-2">
                  <FiClock aria-hidden="true" />
                  Timezone
                </span>
                <input
                  className="form-input"
                  value={timezone}
                  maxLength={80}
                  disabled={!editing}
                  onChange={(event) => setTimezone(event.target.value)}
                  placeholder="Asia/Kolkata"
                />
              </label>
            </div>

            {editing && (
              <div className="border-t border-slate-100 p-6">
                <div className="grid gap-5 lg:grid-cols-[minmax(260px,360px)_minmax(0,1fr)]">
                  <div>
                    <p className="text-sm font-bold text-slate-950">Workspace theme</p>
                    <p className="mt-1 text-xs leading-5 text-slate-500">
                      Choose the shared accent colour used for primary actions, active navigation,
                      highlights and charts across this workspace.
                    </p>
                    <div className="mt-4 flex items-center gap-3">
                      <input
                        type="color"
                        value={previewBrandColor}
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
                        aria-label="Workspace brand colour hex value"
                      />
                    </div>
                    {!isHexColor(brandColor) && (
                      <p className="mt-2 text-xs font-semibold text-rose-600">
                        Enter a valid 6-digit HEX colour.
                      </p>
                    )}
                  </div>

                  <div className="rounded-2xl border border-slate-200 bg-slate-50 p-4">
                    <div className="mb-3 flex items-center justify-between gap-3">
                      <div>
                        <p className="text-[10px] font-bold uppercase tracking-wide text-slate-400">
                          Live dashboard preview
                        </p>
                        <p className="mt-1 text-xs font-semibold text-slate-800">
                          {name.trim() || company?.name || 'Your company'}
                        </p>
                      </div>
                      <button
                        type="button"
                        tabIndex={-1}
                        className="rounded-lg px-3 py-2 text-[10px] font-bold"
                        style={{
                          backgroundColor: previewBrandColor,
                          color: previewBrandContrast,
                        }}
                      >
                        New broadcast
                      </button>
                    </div>
                    <div className="grid grid-cols-[92px_minmax(0,1fr)] overflow-hidden rounded-xl border border-slate-200 bg-white">
                      <div className="border-r border-slate-100 bg-slate-50 p-3">
                        {['Dashboard', 'Inbox', 'Contacts'].map((item, index) => (
                          <div
                            key={item}
                            className="mb-2 rounded-lg px-2 py-2 text-[9px] font-semibold"
                            style={
                              index === 0
                                ? {
                                    backgroundColor: `${previewBrandColor}14`,
                                    color: previewBrandColor,
                                  }
                                : { color: '#64748B' }
                            }
                          >
                            {item}
                          </div>
                        ))}
                      </div>
                      <div className="p-3">
                        <div className="grid grid-cols-2 gap-2">
                          {[
                            ['Contacts', '1,248'],
                            ['Open chats', '32'],
                            ['Delivered', '98.4%'],
                            ['Campaigns', '12'],
                          ].map(([label, value], index) => (
                            <div key={label} className="rounded-lg border border-slate-100 p-2.5">
                              <p className="text-[9px] text-slate-400">{label}</p>
                              <p className="mt-1 text-sm font-bold text-slate-900">{value}</p>
                              <div className="mt-2 h-1 rounded-full bg-slate-100">
                                <div
                                  className="h-full rounded-full"
                                  style={{
                                    width: `${50 + index * 12}%`,
                                    backgroundColor: previewBrandColor,
                                  }}
                                />
                              </div>
                            </div>
                          ))}
                        </div>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            )}
          </div>

          {!editing && (
            <div className="panel grid gap-4 p-5 sm:grid-cols-4">
              <div>
                <p className="text-xs font-semibold text-slate-500">Workspace ID</p>
                <p className="mt-1 break-all text-sm font-medium text-slate-900">
                  {company?.companyId}
                </p>
              </div>
              <div>
                <p className="text-xs font-semibold text-slate-500">Slug</p>
                <p className="mt-1 text-sm font-medium text-slate-900">{company?.slug}</p>
              </div>
              <div>
                <p className="text-xs font-semibold text-slate-500">Brand</p>
                <p className="mt-1 inline-flex items-center gap-2 text-sm font-medium text-slate-900">
                  <span
                    className="size-3 rounded-full"
                    style={{ backgroundColor: company?.brandColor ?? '#2563EB' }}
                  />
                  {company?.brandColor}
                </p>
              </div>
              <div>
                <p className="text-xs font-semibold text-slate-500">Status</p>
                <p className="mt-1 inline-flex items-center gap-1.5 text-sm font-medium text-slate-900">
                  <FiCheckCircle className="text-emerald-500" aria-hidden="true" />
                  {company?.status}
                </p>
              </div>
            </div>
          )}

          {editing && (
            <div className="flex justify-end gap-2">
              <button
                type="button"
                className="secondary-button"
                onClick={cancelEdit}
                disabled={saving}
              >
                <FiX className="mr-1 inline" aria-hidden="true" />
                Cancel
              </button>
              <button
                type="submit"
                className="primary-button"
                disabled={saving || !name.trim() || !isHexColor(brandColor)}
              >
                {saving ? 'Saving…' : 'Save company profile'}
              </button>
            </div>
          )}
        </form>

        {(canDeleteCompany || canLeaveCompany) && (
          <div className="mt-10">
            <p className="text-xs font-bold uppercase tracking-[0.16em] text-red-500">
              Danger zone
            </p>
            {canDeleteCompany ? (
              <div className="panel mt-3 border-red-200 p-5">
                <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
                  <div className="flex items-start gap-4">
                    <span className="security-icon shrink-0 bg-red-50 text-red-600">
                      <FiTrash2 aria-hidden="true" />
                    </span>
                    <div>
                      <h2 className="text-base font-bold text-slate-950">Delete workspace</h2>
                      <p className="mt-1 text-sm leading-6 text-slate-500">
                        Sending and integrations stop immediately. Workspace data then enters the
                        configured retention period before permanent purge.
                      </p>
                    </div>
                  </div>
                  <button
                    type="button"
                    className="secondary-button shrink-0 border-red-200 bg-red-50 text-red-600 hover:bg-red-100"
                    onClick={() => setDeleteConfirmOpen(true)}
                    disabled={saving}
                  >
                    Delete workspace
                  </button>
                </div>
              </div>
            ) : (
              <div className="panel mt-3 border-amber-200 p-5">
                <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
                  <div>
                    <h2 className="text-base font-bold text-slate-950">Leave workspace</h2>
                    <p className="mt-1 text-sm leading-6 text-slate-500">
                      Your membership and assigned roles will be revoked. An owner must invite you
                      again if you need access later.
                    </p>
                  </div>
                  <button
                    type="button"
                    className="secondary-button shrink-0 border-amber-200 text-amber-700"
                    onClick={() => setLeaveConfirmOpen(true)}
                    disabled={saving}
                  >
                    Leave workspace
                  </button>
                </div>
              </div>
            )}
          </div>
        )}

        <ConfirmDialog
          open={leaveConfirmOpen}
          title="Leave this workspace?"
          description="Your company membership and assigned roles will be revoked. You will need a new invitation to return."
          confirmLabel="Yes, leave workspace"
          cancelLabel="No, stay"
          tone="warning"
          working={saving}
          onCancel={() => setLeaveConfirmOpen(false)}
          onConfirm={() => void leaveWorkspace()}
        />
        <ConfirmDialog
          open={deleteConfirmOpen}
          title={`Delete ${company?.name ?? 'this workspace'}?`}
          description="Connections will be disconnected and future billing will stop immediately. The workspace then enters the deletion retention lifecycle."
          confirmLabel="Yes, delete workspace"
          cancelLabel="No, keep workspace"
          tone="danger"
          kind="delete"
          working={saving}
          onCancel={() => setDeleteConfirmOpen(false)}
          onConfirm={() => void deleteWorkspace()}
        />
      </div>
    </section>
  )
}
