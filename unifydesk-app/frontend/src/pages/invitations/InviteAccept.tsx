import { useEffect, useState } from 'react'
import { FiCheckCircle, FiMail, FiShield } from 'react-icons/fi'
import { Link, useNavigate, useParams } from 'react-router'
import { companyApi } from '../../features/company/companyApi'
import {
  clearPendingInvitationPath,
  rememberInvitationPath,
} from '../../features/company/invitationContinuation'
import { workspaceApi } from '../../features/workspace/workspaceApi'
import { apiErrorMessage } from '../../lib/api/apiClient'
import { selectAuthUser, selectIsAuthenticated } from '../../store/auth/authSlice'
import { useAppDispatch, useAppSelector } from '../../store/hooks'
import { selectCompany, setCompanies } from '../../store/workspace/workspaceSlice'

export default function InviteAccept() {
  const { token } = useParams()
  const navigate = useNavigate()
  const dispatch = useAppDispatch()
  const isAuthenticated = useAppSelector(selectIsAuthenticated)
  const user = useAppSelector(selectAuthUser)
  const [accepting, setAccepting] = useState(false)
  const [error, setError] = useState('')

  const redirectTo = token ? `/invite/${token}` : '/'

  useEffect(() => {
    if (token) rememberInvitationPath(redirectTo)
  }, [redirectTo, token])

  const accept = async () => {
    if (!token) return
    setAccepting(true)
    setError('')
    try {
      const accepted = await companyApi.acceptInvitation(token)
      clearPendingInvitationPath()

      try {
        const companies = await workspaceApi.list()
        dispatch(setCompanies(companies))
        dispatch(selectCompany(accepted.companyId))
        navigate('/workspace', { replace: true })
      } catch {
        navigate('/select-workspace', { replace: true })
      }
    } catch (acceptError) {
      setError(apiErrorMessage(acceptError, 'Unable to accept invitation'))
    } finally {
      setAccepting(false)
    }
  }

  if (!token) {
    return (
      <div className="panel p-6 shadow-sm">
        <h1 className="text-xl font-bold text-slate-950">Invalid invitation</h1>
        <p className="mt-2 text-sm text-slate-500">The invitation link is incomplete.</p>
      </div>
    )
  }

  return (
    <div className="panel p-6 shadow-sm">
      <span className="security-icon">
        <FiMail aria-hidden="true" />
      </span>
      <p className="eyebrow mt-5">Company invitation</p>
      <h1 className="mt-2 text-2xl font-bold tracking-tight text-slate-950">Join a workspace</h1>

      {!isAuthenticated ? (
        <>
          <p className="mt-2 text-sm leading-6 text-slate-500">
            Sign in with the email address that received this invitation. If you do not have a
            UnifyDesk account yet, create one first.
          </p>
          <div className="mt-6 grid gap-3">
            <Link className="primary-button flex" to="/login" state={{ redirectTo }}>
              Sign in to accept
            </Link>
            <Link className="secondary-button flex" to="/register" state={{ redirectTo }}>
              Create account
            </Link>
          </div>
        </>
      ) : !user?.verified ? (
        <>
          <div className="mt-4 flex gap-3 rounded-xl bg-amber-50 p-4 text-sm text-amber-800">
            <FiShield className="mt-0.5 shrink-0" aria-hidden="true" />
            <p>Verify {user?.email ?? 'your email'} before accepting this invitation.</p>
          </div>
          <Link
            className="primary-button mt-5 flex"
            to="/verifyotp"
            state={{ email: user?.email, redirectTo }}
          >
            Verify email
          </Link>
        </>
      ) : (
        <>
          <div className="mt-4 flex gap-3 rounded-xl bg-emerald-50 p-4 text-sm text-emerald-800">
            <FiCheckCircle className="mt-0.5 shrink-0" aria-hidden="true" />
            <p>
              You are signed in as <strong>{user.email}</strong>. The invitation can only be
              accepted if it was sent to this email.
            </p>
          </div>
          {error && <p className="mt-4 rounded-lg bg-rose-50 p-3 text-sm text-rose-600">{error}</p>}
          <button
            type="button"
            className="primary-button mt-5 flex w-full"
            onClick={() => void accept()}
            disabled={accepting}
          >
            {accepting ? 'Accepting…' : 'Accept invitation'}
          </button>
        </>
      )}
    </div>
  )
}
