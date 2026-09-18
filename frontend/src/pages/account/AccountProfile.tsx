import { useEffect, useRef, useState, type FormEvent } from 'react'
import {
  FiAtSign,
  FiCheckCircle,
  FiEdit2,
  FiMail,
  FiShield,
  FiUploadCloud,
  FiUser,
  FiX,
} from 'react-icons/fi'
import { authApi } from '../../features/auth/authApi'
import { mediaApi } from '../../features/media/mediaApi'
import { userApi } from '../../features/user/userApi'
import type { User } from '../../features/user/types'
import { apiErrorMessage } from '../../lib/api/apiClient'
import { setAuthUser } from '../../store/auth/authSlice'
import { useAppDispatch } from '../../store/hooks'

const initialsFor = (name: string) =>
  name
    .split(' ')
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase())
    .join('')
    .slice(0, 2) || 'U'

export default function AccountProfile() {
  const dispatch = useAppDispatch()
  const [user, setUser] = useState<User | null>(null)
  const [name, setName] = useState('')
  const [username, setUsername] = useState('')
  const [profilePictureUrl, setProfilePictureUrl] = useState('')
  const [pictureFile, setPictureFile] = useState<File | null>(null)
  const [picturePreviewUrl, setPicturePreviewUrl] = useState('')
  const [uploadProgress, setUploadProgress] = useState(0)
  const [editing, setEditing] = useState(false)
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const previewUrlRef = useRef('')

  const syncForm = (value: User) => {
    setUser(value)
    setName(value.name)
    setUsername(value.username)
  }

  useEffect(() => {
    let active = true
    userApi
      .me()
      .then((value) => {
        if (!active) return
        syncForm(value)
        if (!value.profilePictureMediaId) {
          setProfilePictureUrl('')
          return
        }
        return mediaApi.access(value.profilePictureMediaId).then((access) => {
          if (active) setProfilePictureUrl(access.url)
        })
      })
      .catch((loadError) => {
        if (active) setError(apiErrorMessage(loadError, 'Unable to load your profile'))
      })
      .finally(() => {
        if (active) setLoading(false)
      })

    return () => {
      active = false
    }
  }, [])

  useEffect(
    () => () => {
      if (previewUrlRef.current) URL.revokeObjectURL(previewUrlRef.current)
    },
    [],
  )

  const clearPreview = () => {
    if (previewUrlRef.current) {
      URL.revokeObjectURL(previewUrlRef.current)
      previewUrlRef.current = ''
    }
    setPicturePreviewUrl('')
    setPictureFile(null)
    setUploadProgress(0)
  }

  const beginEdit = () => {
    if (!user) return
    syncForm(user)
    clearPreview()
    setError('')
    setNotice('')
    setEditing(true)
  }

  const cancelEdit = () => {
    if (user) syncForm(user)
    clearPreview()
    setError('')
    setEditing(false)
  }

  const onPictureChange = (file: File | null) => {
    clearPreview()
    if (!file) return
    const preview = URL.createObjectURL(file)
    previewUrlRef.current = preview
    setPicturePreviewUrl(preview)
    setPictureFile(file)
  }

  const save = async (event: FormEvent) => {
    event.preventDefault()
    if (!user) return
    setSaving(true)
    setError('')
    setNotice('')

    try {
      let updated = await userApi.updateProfile({
        name: name.trim(),
        username: username.trim(),
      })

      if (pictureFile) {
        const upload = await mediaApi.initiateProfilePictureUpload(pictureFile)
        await mediaApi.uploadFileToStorage(upload, pictureFile, setUploadProgress)
        await mediaApi.completeUpload(upload.mediaId)
        await mediaApi.attachCurrentUserProfilePicture(upload.mediaId)
        updated = await userApi.me()
      }

      syncForm(updated)

      if (updated.profilePictureMediaId) {
        const access = await mediaApi.access(updated.profilePictureMediaId).catch(() => null)
        setProfilePictureUrl(access?.url ?? picturePreviewUrl)
      }

      const refreshedAuth = await authApi.refresh()
      dispatch(setAuthUser(refreshedAuth))
      clearPreview()
      setEditing(false)
      setNotice('Profile updated')
    } catch (saveError) {
      setError(apiErrorMessage(saveError, 'Unable to update your profile'))
    } finally {
      setSaving(false)
    }
  }

  const visiblePicture = picturePreviewUrl || profilePictureUrl

  return (
    <section className="px-4 py-8 sm:px-6 lg:px-8">
      <div className="mx-auto max-w-4xl">
        <header className="flex flex-wrap items-end justify-between gap-4">
          <div>
            <p className="eyebrow">Account</p>
            <h1 className="mt-2 text-3xl font-bold tracking-tight text-slate-950">Profile</h1>
            <p className="mt-2 max-w-2xl text-sm leading-6 text-slate-500">
              Manage your personal identity used across all workspaces.
            </p>
          </div>
          {!editing && (
            <button
              type="button"
              className="primary-button inline-flex items-center gap-2"
              onClick={beginEdit}
              disabled={loading || !user}
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
              <div className="flex size-20 shrink-0 items-center justify-center overflow-hidden rounded-full bg-indigo-50 text-xl font-bold text-indigo-600">
                {visiblePicture ? (
                  <img src={visiblePicture} alt="" className="h-full w-full object-cover" />
                ) : (
                  initialsFor(name || user?.name || 'User')
                )}
              </div>
              <div className="min-w-0 flex-1">
                <h2 className="text-lg font-bold text-slate-950">{user?.name ?? 'Your profile'}</h2>
                <p className="mt-1 text-sm text-slate-500">@{user?.username}</p>
                <div className="mt-3 flex flex-wrap gap-2">
                  <span
                    className={`rounded-full px-2.5 py-1 text-xs font-semibold ${
                      user?.verified
                        ? 'bg-emerald-50 text-emerald-700'
                        : 'bg-amber-50 text-amber-700'
                    }`}
                  >
                    {user?.verified ? 'Email verified' : 'Email not verified'}
                  </span>
                  <span className="rounded-full bg-slate-100 px-2.5 py-1 text-xs font-semibold text-slate-600">
                    2FA {user?.twoFactorEnabled ? 'enabled' : 'disabled'}
                  </span>
                </div>
              </div>
              {editing && (
                <label className="secondary-button inline-flex cursor-pointer items-center gap-2">
                  <FiUploadCloud aria-hidden="true" />
                  {pictureFile ? 'Change photo' : 'Upload photo'}
                  <input
                    className="sr-only"
                    type="file"
                    accept="image/jpeg,image/png,image/webp"
                    onChange={(event) => onPictureChange(event.target.files?.[0] ?? null)}
                  />
                </label>
              )}
            </div>

            {pictureFile && uploadProgress > 0 && uploadProgress < 100 && (
              <div className="px-6 pt-4">
                <div className="upload-progress">
                  <span style={{ width: `${uploadProgress}%` }} />
                </div>
              </div>
            )}

            <div className="grid gap-5 p-6 md:grid-cols-2">
              <label className="block text-xs font-semibold text-slate-700">
                <span className="mb-2 flex items-center gap-2">
                  <FiUser aria-hidden="true" />
                  Full name
                </span>
                <input
                  className="form-input"
                  value={name}
                  maxLength={120}
                  disabled={!editing}
                  onChange={(event) => setName(event.target.value)}
                  required
                />
              </label>

              <label className="block text-xs font-semibold text-slate-700">
                <span className="mb-2 flex items-center gap-2">
                  <FiAtSign aria-hidden="true" />
                  Username
                </span>
                <input
                  className="form-input"
                  value={username}
                  minLength={3}
                  maxLength={50}
                  disabled={!editing}
                  onChange={(event) => setUsername(event.target.value)}
                  required
                />
              </label>

              <label className="block text-xs font-semibold text-slate-700 md:col-span-2">
                <span className="mb-2 flex items-center gap-2">
                  <FiMail aria-hidden="true" />
                  Email
                </span>
                <input className="form-input" value={user?.email ?? ''} disabled readOnly />
                <span className="mt-2 block text-[11px] font-normal text-slate-400">
                  Email changes require a separate verification flow and are not editable here.
                </span>
              </label>
            </div>
          </div>

          {!editing && (
            <div className="panel grid gap-4 p-5 sm:grid-cols-3">
              <div>
                <p className="text-xs font-semibold text-slate-500">User ID</p>
                <p className="mt-1 break-all text-sm font-medium text-slate-900">{user?.id}</p>
              </div>
              <div>
                <p className="text-xs font-semibold text-slate-500">Email</p>
                <p className="mt-1 inline-flex items-center gap-1.5 text-sm font-medium text-slate-900">
                  <FiCheckCircle
                    className={user?.verified ? 'text-emerald-500' : 'text-amber-500'}
                    aria-hidden="true"
                  />
                  {user?.verified ? 'Verified' : 'Pending verification'}
                </p>
              </div>
              <div>
                <p className="text-xs font-semibold text-slate-500">Security</p>
                <p className="mt-1 inline-flex items-center gap-1.5 text-sm font-medium text-slate-900">
                  <FiShield aria-hidden="true" />
                  2FA {user?.twoFactorEnabled ? 'On' : 'Off'}
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
                disabled={saving || !name.trim() || username.trim().length < 3}
              >
                {saving ? 'Saving…' : 'Save profile'}
              </button>
            </div>
          )}
        </form>
      </div>
    </section>
  )
}
