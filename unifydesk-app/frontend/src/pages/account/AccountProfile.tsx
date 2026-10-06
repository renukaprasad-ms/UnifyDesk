import { useEffect, useRef, useState, type FormEvent } from 'react'
import {
  FiAtSign,
  FiCheckCircle,
  FiEdit2,
  FiMail,
  FiShield,
  FiTrash2,
  FiUploadCloud,
  FiUser,
  FiX,
} from 'react-icons/fi'
import { useNavigate } from 'react-router'
import { authApi } from '../../features/auth/authApi'
import { mediaApi } from '../../features/media/mediaApi'
import { userApi } from '../../features/user/userApi'
import type { User } from '../../features/user/types'
import { apiErrorMessage } from '../../lib/api/apiClient'
import { sessionCleared, setAuthUser } from '../../store/auth/authSlice'
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
  const navigate = useNavigate()
  const [user, setUser] = useState<User | null>(null)
  const [name, setName] = useState('')
  const [username, setUsername] = useState('')
  const [profilePictureUrl, setProfilePictureUrl] = useState('')
  const [pictureFile, setPictureFile] = useState<File | null>(null)
  const [picturePreviewUrl, setPicturePreviewUrl] = useState('')
  const [removePicture, setRemovePicture] = useState(false)
  const [uploadProgress, setUploadProgress] = useState(0)
  const [emailChangeOpen, setEmailChangeOpen] = useState(false)
  const [newEmail, setNewEmail] = useState('')
  const [emailCurrentPassword, setEmailCurrentPassword] = useState('')
  const [emailChallengeId, setEmailChallengeId] = useState('')
  const [emailOtp, setEmailOtp] = useState('')
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
    setRemovePicture(false)
    setError('')
    setNotice('')
    setEditing(true)
  }

  const cancelEdit = () => {
    if (user) syncForm(user)
    clearPreview()
    setRemovePicture(false)
    setError('')
    setEditing(false)
  }

  const onPictureChange = (file: File | null) => {
    clearPreview()
    setRemovePicture(false)
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
      } else if (removePicture && user.profilePictureMediaId) {
        await mediaApi.removeCurrentUserProfilePicture()
        updated = await userApi.me()
      }

      syncForm(updated)

      if (updated.profilePictureMediaId) {
        const access = await mediaApi.access(updated.profilePictureMediaId).catch(() => null)
        setProfilePictureUrl(access?.url ?? picturePreviewUrl)
      } else {
        setProfilePictureUrl('')
      }

      const refreshedAuth = await authApi.refresh()
      dispatch(setAuthUser(refreshedAuth))
      clearPreview()
      setRemovePicture(false)
      setEditing(false)
      setNotice('Profile updated')
    } catch (saveError) {
      setError(apiErrorMessage(saveError, 'Unable to update your profile'))
    } finally {
      setSaving(false)
    }
  }

  const openEmailChange = () => {
    if (!user) return
    setError('')
    setNotice('')
    setNewEmail('')
    setEmailCurrentPassword('')
    setEmailChallengeId('')
    setEmailOtp('')
    setEmailChangeOpen(true)
  }

  const closeEmailChange = () => {
    if (saving) return
    setEmailChangeOpen(false)
    setNewEmail('')
    setEmailCurrentPassword('')
    setEmailChallengeId('')
    setEmailOtp('')
  }

  const requestEmailChangeCode = async () => {
    if (!newEmail.trim() || !emailCurrentPassword) return
    setSaving(true)
    setError('')
    try {
      const challenge = await authApi.beginEmailChange(newEmail.trim(), emailCurrentPassword)
      setEmailChallengeId(challenge.challengeId)
      setEmailCurrentPassword('')
    } catch (changeError) {
      setError(apiErrorMessage(changeError, 'Unable to start email change'))
    } finally {
      setSaving(false)
    }
  }

  const completeEmailChange = async () => {
    if (!emailChallengeId || emailOtp.length !== 6) return
    setSaving(true)
    setError('')
    try {
      await authApi.completeEmailChange(emailChallengeId, emailOtp)
      dispatch(sessionCleared())
      navigate('/login', { replace: true, state: { emailChanged: true } })
    } catch (changeError) {
      setError(apiErrorMessage(changeError, 'Unable to change email'))
    } finally {
      setSaving(false)
    }
  }

  const visiblePicture = picturePreviewUrl || (!removePicture ? profilePictureUrl : '')

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
                <div className="flex flex-wrap items-center gap-2">
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
                  {(user?.profilePictureMediaId || pictureFile) && (
                    <button
                      type="button"
                      className="inline-flex items-center gap-1 rounded-lg px-3 py-2 text-xs font-semibold text-rose-600 hover:bg-rose-50"
                      onClick={() => {
                        clearPreview()
                        setRemovePicture(true)
                      }}
                    >
                      <FiTrash2 aria-hidden="true" />
                      Remove
                    </button>
                  )}
                </div>
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

              <div className="block text-xs font-semibold text-slate-700 md:col-span-2">
                <span className="mb-2 flex items-center gap-2">
                  <FiMail aria-hidden="true" />
                  Email
                </span>
                <div className="flex flex-col gap-2 sm:flex-row">
                  <input
                    className="form-input flex-1"
                    value={user?.email ?? ''}
                    disabled
                    readOnly
                  />
                  <button
                    type="button"
                    className="secondary-button shrink-0"
                    onClick={openEmailChange}
                    disabled={!user?.verified}
                  >
                    Change email
                  </button>
                </div>
                <span className="mt-2 block text-[11px] font-normal text-slate-400">
                  Changing email requires your current password and a verification code sent to the new address.
                </span>
              </div>
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
        {emailChangeOpen && (
          <div className="mobile-modal-shell fixed inset-0 z-50 flex items-center justify-center bg-slate-950/40 p-4">
            <div className="mobile-sheet w-full max-w-md rounded-2xl bg-white shadow-2xl">
              <div className="flex items-start justify-between border-b border-slate-100 px-5 py-4">
                <div>
                  <p className="eyebrow">Account security</p>
                  <h2 className="mt-1 text-lg font-bold text-slate-950">Change email address</h2>
                </div>
                <button
                  type="button"
                  className="rounded-lg p-2 text-slate-400 hover:bg-slate-100"
                  onClick={closeEmailChange}
                  disabled={saving}
                  aria-label="Close email change"
                >
                  <FiX aria-hidden="true" />
                </button>
              </div>

              <div className="p-5">
                {!emailChallengeId ? (
                  <div className="space-y-4">
                    <p className="text-sm leading-6 text-slate-500">
                      Re-enter your current password. We will send a one-time code to the new email address before changing your account.
                    </p>
                    <label className="block text-xs font-semibold text-slate-700">
                      New email
                      <input
                        type="email"
                        className="form-input mt-1.5"
                        autoComplete="email"
                        value={newEmail}
                        onChange={(event) => setNewEmail(event.target.value)}
                        required
                      />
                    </label>
                    <label className="block text-xs font-semibold text-slate-700">
                      Current password
                      <input
                        type="password"
                        className="form-input mt-1.5"
                        autoComplete="current-password"
                        value={emailCurrentPassword}
                        onChange={(event) => setEmailCurrentPassword(event.target.value)}
                        required
                      />
                    </label>
                    <button
                      type="button"
                      className="primary-button w-full justify-center"
                      disabled={saving || !newEmail.trim() || !emailCurrentPassword}
                      onClick={() => void requestEmailChangeCode()}
                    >
                      {saving ? 'Sending…' : 'Send verification code'}
                    </button>
                  </div>
                ) : (
                  <div className="space-y-4">
                    <p className="text-sm leading-6 text-slate-500">
                      Enter the 6-digit code sent to <strong>{newEmail}</strong>. Completing this change signs out all sessions.
                    </p>
                    <input
                      type="text"
                      inputMode="numeric"
                      autoComplete="one-time-code"
                      className="form-input text-center tracking-[0.35em]"
                      maxLength={6}
                      value={emailOtp}
                      onChange={(event) =>
                        setEmailOtp(event.target.value.replace(/\D/g, '').slice(0, 6))
                      }
                    />
                    <button
                      type="button"
                      className="primary-button w-full justify-center"
                      disabled={saving || emailOtp.length !== 6}
                      onClick={() => void completeEmailChange()}
                    >
                      {saving ? 'Verifying…' : 'Verify and change email'}
                    </button>
                  </div>
                )}
              </div>
            </div>
          </div>
        )}
      </div>
    </section>
  )
}
