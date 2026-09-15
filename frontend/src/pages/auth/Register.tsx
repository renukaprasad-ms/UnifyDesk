import { Link } from 'react-router'
import { useEffect, useRef, useState } from 'react'
import { useDispatch } from 'react-redux'
import { useNavigate } from 'react-router'
import { FiImage, FiUploadCloud, FiX } from 'react-icons/fi'
import { authApi } from '../../features/auth/authApi'
import { mediaApi } from '../../features/media/mediaApi'
import { apiErrorMessage } from '../../lib/api/apiClient'
import { setAuthUser } from '../../store/user/userSlice'
import type { AppDispatch } from '../../store/store'
import type { InitiateMediaUploadResponse } from '../../features/media/types'

const Register = () => {
  const dispatch = useDispatch<AppDispatch>()
  const navigate = useNavigate()
  const [name, setName] = useState('')
  const [email, setEmail] = useState('')
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [profilePicture, setProfilePicture] = useState<File | null>(null)
  const [profilePreviewUrl, setProfilePreviewUrl] = useState('')
  const [profileUploadStatus, setProfileUploadStatus] = useState('')
  const [profileUploadProgress, setProfileUploadProgress] = useState(0)
  const [profileUploadResult, setProfileUploadResult] =
    useState<InitiateMediaUploadResponse | null>(null)
  const [error, setError] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const profilePreviewUrlRef = useRef('')
  const profileUploadRunIdRef = useRef(0)
  const profileUploadPromiseRef = useRef<Promise<InitiateMediaUploadResponse> | null>(null)

  useEffect(() => {
    return () => {
      if (profilePreviewUrlRef.current) {
        URL.revokeObjectURL(profilePreviewUrlRef.current)
      }
    }
  }, [])

  const uploadProfilePictureOnChange = async (
    file: File,
    runId: number,
  ): Promise<InitiateMediaUploadResponse> => {
    setProfileUploadStatus('Preparing upload...')
    setProfileUploadProgress(1)
    const upload = await mediaApi.initiateProfilePictureUpload(file)
    if (profileUploadRunIdRef.current !== runId) {
      return upload
    }
    setProfileUploadStatus('Uploading profile picture...')
    await mediaApi.uploadFileToStorage(upload, file, (progress) => {
      if (profileUploadRunIdRef.current === runId) {
        setProfileUploadProgress(progress)
      }
    })
    if (profileUploadRunIdRef.current === runId) {
      setProfileUploadProgress(100)
      setProfileUploadStatus('Uploaded. It will be saved when you register.')
      setProfileUploadResult(upload)
    }
    return upload
  }

  const onProfilePictureChange = (file: File | null) => {
    const runId = profileUploadRunIdRef.current + 1
    profileUploadRunIdRef.current = runId
    profileUploadPromiseRef.current = null
    setProfileUploadResult(null)
    setProfileUploadProgress(0)
    if (profilePreviewUrlRef.current) {
      URL.revokeObjectURL(profilePreviewUrlRef.current)
      profilePreviewUrlRef.current = ''
    }
    if (file) {
      const previewUrl = URL.createObjectURL(file)
      profilePreviewUrlRef.current = previewUrl
      setProfilePreviewUrl(previewUrl)
    } else {
      setProfilePreviewUrl('')
    }
    setProfilePicture(file)
    setProfileUploadStatus(file ? 'Preparing upload...' : '')
    if (file) {
      const uploadPromise = uploadProfilePictureOnChange(file, runId).catch((uploadError) => {
        if (profileUploadRunIdRef.current === runId) {
          setProfileUploadProgress(0)
          setProfileUploadStatus(apiErrorMessage(uploadError, 'Profile picture upload failed'))
        }
        throw uploadError
      })
      profileUploadPromiseRef.current = uploadPromise
    }
  }

  const onSubmit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    setError('')
    if (password !== confirmPassword) {
      setError('Passwords do not match')
      return
    }
    setSubmitting(true)
    try {
      let profileUpload = profileUploadResult
      if (profilePicture && !profileUpload) {
        try {
          profileUpload = await profileUploadPromiseRef.current
        } catch (uploadError) {
          setError(apiErrorMessage(uploadError, 'Profile picture upload failed'))
          return
        }
      }
      const user = await authApi.register({
        email,
        name,
        username,
        password,
        profilePictureMediaId: profileUpload?.mediaId,
        profilePictureUploadToken: profileUpload?.uploadToken ?? undefined,
      })
      dispatch(setAuthUser(user))
      navigate('/verifyotp', {
        replace: true,
        state: { email: user.email },
      })
    } catch (submitError) {
      setError(apiErrorMessage(submitError, 'Unable to register'))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="panel p-6 shadow-sm">
      <p className="eyebrow">Get started</p>
      <h1 className="mt-2 text-2xl font-bold tracking-tight text-slate-950">Register</h1>
      <p className="mt-2 text-sm leading-6 text-slate-500">
        Create your account and verify it with an OTP.
      </p>

      <form className="mt-6 space-y-4" onSubmit={onSubmit}>
        <label className="block">
          <span className="mb-1.5 block text-xs font-semibold text-slate-700">Name</span>
          <input
            className="form-input"
            type="text"
            placeholder="Your name"
            value={name}
            onChange={(event) => setName(event.target.value)}
            autoComplete="name"
            required
          />
        </label>
        <label className="block">
          <span className="mb-1.5 block text-xs font-semibold text-slate-700">Email</span>
          <input
            className="form-input"
            type="email"
            placeholder="you@example.com"
            value={email}
            onChange={(event) => setEmail(event.target.value)}
            autoComplete="email"
            required
          />
        </label>
        <label className="block">
          <span className="mb-1.5 block text-xs font-semibold text-slate-700">Username</span>
          <input
            className="form-input"
            type="text"
            placeholder="yourname"
            value={username}
            onChange={(event) => setUsername(event.target.value)}
            autoComplete="username"
            minLength={3}
            maxLength={50}
            required
          />
        </label>
        <label className="block">
          <span className="mb-1.5 block text-xs font-semibold text-slate-700">Password</span>
          <input
            className="form-input"
            type="password"
            placeholder="Create password"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            autoComplete="new-password"
            minLength={8}
            required
          />
        </label>
        <label className="block">
          <span className="mb-1.5 block text-xs font-semibold text-slate-700">
            Confirm password
          </span>
          <input
            className="form-input"
            type="password"
            placeholder="Confirm password"
            value={confirmPassword}
            onChange={(event) => setConfirmPassword(event.target.value)}
            autoComplete="new-password"
            minLength={8}
            required
          />
        </label>
        <div className="block">
          <span className="mb-1.5 block text-xs font-semibold text-slate-700">
            Profile picture
          </span>
          <label className="profile-upload">
            <input
              className="sr-only"
              type="file"
              accept="image/jpeg,image/png,image/webp"
              onChange={(event) => onProfilePictureChange(event.target.files?.[0] ?? null)}
            />
            <span className="profile-upload-preview">
              {profilePreviewUrl ? (
                <img src={profilePreviewUrl} alt="" />
              ) : (
                <FiImage aria-hidden="true" size={22} />
              )}
            </span>
            <span className="min-w-0 flex-1">
              <span className="block truncate text-sm font-semibold text-slate-800">
                {profilePicture?.name ?? 'Choose an image'}
              </span>
              <span className="mt-1 block text-xs text-slate-500">
                JPG, PNG, or WEBP. Optional.
              </span>
            </span>
            <FiUploadCloud className="text-indigo-500" aria-hidden="true" size={20} />
          </label>
          {profilePicture && (
            <div className="mt-2 flex items-center justify-between gap-3 text-xs text-slate-500">
              <span>{profileUploadStatus}</span>
              <button
                type="button"
                className="inline-flex items-center gap-1 font-semibold text-rose-600"
                onClick={() => onProfilePictureChange(null)}
              >
                <FiX aria-hidden="true" />
                Remove
              </button>
            </div>
          )}
          {profilePicture && profileUploadProgress > 0 && profileUploadProgress < 100 && (
            <div className="upload-progress mt-2" aria-label="Profile picture upload progress">
              <span style={{ width: `${profileUploadProgress}%` }} />
            </div>
          )}
        </div>
        {error && <p className="rounded-lg bg-rose-50 p-3 text-sm text-rose-600">{error}</p>}
        <button type="submit" className="primary-button flex w-full" disabled={submitting}>
          {submitting ? 'Creating account...' : 'Register'}
        </button>
      </form>

      <p className="mt-5 text-center text-sm text-slate-500">
        Already registered?{' '}
        <Link className="font-semibold text-indigo-500" to="/login">
          Login
        </Link>
      </p>
    </div>
  )
}

export default Register
