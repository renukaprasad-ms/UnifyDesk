import { useEffect, useMemo, useState } from 'react'
import {
  FiClock,
  FiKey,
  FiMail,
  FiRefreshCw,
  FiSend,
  FiShield,
  FiUserPlus,
  FiUsers,
  FiX,
} from 'react-icons/fi'
import { companyApi } from '../../features/company/companyApi'
import type { CompanyInvitation, CompanyUser } from '../../features/company/types'
import { iamApi } from '../../features/iam/iamApi'
import { PERMISSIONS } from '../../features/iam/permissions'
import type { CompanyRole, MemberRoleAssignment } from '../../features/iam/types'
import { useIam } from '../../features/iam/useIam'
import { apiErrorMessage } from '../../lib/api/apiClient'
import ConfirmDialog from '../../components/ui/ConfirmDialog'
import { TableSkeleton } from '../../components/ui/Skeleton'
import { useAppSelector } from '../../store/hooks'
import { selectSelectedCompany } from '../../store/workspace/workspaceSlice'

const roleClass: Record<CompanyUser['role'], string> = {
  OWNER: 'bg-indigo-50 text-indigo-700',
  MANAGER: 'bg-sky-50 text-sky-700',
  MEMBER: 'bg-slate-100 text-slate-700',
}

const invitationClass: Record<CompanyInvitation['status'], string> = {
  PENDING: 'bg-amber-50 text-amber-700',
  ACCEPTED: 'bg-emerald-50 text-emerald-700',
  REVOKED: 'bg-slate-100 text-slate-600',
  EXPIRED: 'bg-rose-50 text-rose-700',
}

export default function CompanyUsers() {
  const selectedCompany = useAppSelector(selectSelectedCompany)
  const { can, canAll } = useIam()
  const [users, setUsers] = useState<CompanyUser[]>([])
  const [invitations, setInvitations] = useState<CompanyInvitation[]>([])
  const [roles, setRoles] = useState<CompanyRole[]>([])
  const [loading, setLoading] = useState(true)
  const [showInvite, setShowInvite] = useState(false)
  const [inviteEmail, setInviteEmail] = useState('')
  const [selectedRoleIds, setSelectedRoleIds] = useState<string[]>([])
  const [submittingInvite, setSubmittingInvite] = useState(false)
  const [workingInvitationId, setWorkingInvitationId] = useState<string | null>(null)
  const [managingUser, setManagingUser] = useState<CompanyUser | null>(null)
  const [memberAssignments, setMemberAssignments] = useState<MemberRoleAssignment[]>([])
  const [roleActionId, setRoleActionId] = useState<string | null>(null)
  const [error, setError] = useState('')
  const [invitationToRevoke, setInvitationToRevoke] = useState<CompanyInvitation | null>(null)
  const [memberActionUserId, setMemberActionUserId] = useState<string | null>(null)
  const [ownershipPassword, setOwnershipPassword] = useState('')
  const [memberConfirmation, setMemberConfirmation] = useState<{
    kind: 'REMOVE' | 'TRANSFER'
    user: CompanyUser
  } | null>(null)

  const sortedUsers = useMemo(
    () =>
      [...users].sort((first, second) => {
        const roleOrder = { OWNER: 0, MANAGER: 1, MEMBER: 2 }
        return (
          roleOrder[first.role] - roleOrder[second.role] || first.name.localeCompare(second.name)
        )
      }),
    [users],
  )

  const activeInvitations = useMemo(
    () => invitations.filter((invitation) => ['PENDING', 'EXPIRED'].includes(invitation.status)),
    [invitations],
  )

  const loadUsers = async () => {
    if (!selectedCompany) return
    setError('')
    setLoading(true)
    try {
      const [loadedUsers, loadedInvitations, loadedRoles] = await Promise.all([
        companyApi.users(selectedCompany.companyId),
        companyApi.invitations(selectedCompany.companyId),
        can(PERMISSIONS.ROLE_READ)
          ? iamApi.roles(selectedCompany.companyId)
          : Promise.resolve([] as CompanyRole[]),
      ])
      setUsers(loadedUsers)
      setInvitations(loadedInvitations)
      setRoles(loadedRoles.filter((role) => !role.systemRole))
    } catch (loadError) {
      setError(apiErrorMessage(loadError, 'Unable to load company users'))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    void loadUsers()
  }, [selectedCompany?.companyId])

  const sendInvite = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (!selectedCompany) return
    setSubmittingInvite(true)
    setError('')
    try {
      await companyApi.invite(selectedCompany.companyId, {
        email: inviteEmail.trim(),
        roleIds: can(PERMISSIONS.ROLE_ASSIGN) ? selectedRoleIds : [],
      })
      setInviteEmail('')
      setSelectedRoleIds([])
      setShowInvite(false)
      await loadUsers()
    } catch (inviteError) {
      setError(apiErrorMessage(inviteError, 'Unable to send invitation'))
    } finally {
      setSubmittingInvite(false)
    }
  }

  const resend = async (invitationId: string) => {
    if (!selectedCompany) return
    setWorkingInvitationId(invitationId)
    setError('')
    try {
      await companyApi.resendInvitation(selectedCompany.companyId, invitationId)
      await loadUsers()
    } catch (inviteError) {
      setError(apiErrorMessage(inviteError, 'Unable to resend invitation'))
    } finally {
      setWorkingInvitationId(null)
    }
  }

  const revoke = async (invitation: CompanyInvitation) => {
    if (!selectedCompany) return
    setWorkingInvitationId(invitation.invitationId)
    setError('')
    try {
      await companyApi.revokeInvitation(selectedCompany.companyId, invitation.invitationId)
      await loadUsers()
    } catch (inviteError) {
      setError(apiErrorMessage(inviteError, 'Unable to revoke invitation'))
    } finally {
      setWorkingInvitationId(null)
      setInvitationToRevoke(null)
    }
  }

  const openRoleManager = async (user: CompanyUser) => {
    if (!selectedCompany || user.role === 'OWNER') return
    setManagingUser(user)
    setMemberAssignments([])
    setError('')
    if (user.status !== 'ACTIVE' || !can(PERMISSIONS.ROLE_READ)) return
    try {
      setMemberAssignments(await iamApi.memberRoles(selectedCompany.companyId, user.userId))
    } catch (roleError) {
      setError(apiErrorMessage(roleError, 'Unable to load member roles'))
    }
  }

  const changeBaseRole = async (role: 'MANAGER' | 'MEMBER') => {
    if (!selectedCompany || !managingUser) return
    setMemberActionUserId(managingUser.userId)
    setError('')
    try {
      const updated = await companyApi.updateMemberRole(
        selectedCompany.companyId,
        managingUser.userId,
        role,
      )
      setManagingUser(updated)
      await loadUsers()
    } catch (memberError) {
      setError(apiErrorMessage(memberError, 'Unable to update member access'))
    } finally {
      setMemberActionUserId(null)
    }
  }

  const changeMemberStatus = async (status: 'ACTIVE' | 'SUSPENDED') => {
    if (!selectedCompany || !managingUser) return
    setMemberActionUserId(managingUser.userId)
    setError('')
    try {
      const updated = await companyApi.updateMemberStatus(
        selectedCompany.companyId,
        managingUser.userId,
        status,
      )
      setManagingUser(updated)
      if (status === 'SUSPENDED') setMemberAssignments([])
      await loadUsers()
    } catch (memberError) {
      setError(apiErrorMessage(memberError, 'Unable to update member status'))
    } finally {
      setMemberActionUserId(null)
    }
  }

  const confirmMemberAction = async () => {
    if (!selectedCompany || !memberConfirmation) return
    const { kind, user } = memberConfirmation
    setMemberActionUserId(user.userId)
    setError('')
    try {
      if (kind === 'REMOVE') {
        await companyApi.removeMember(selectedCompany.companyId, user.userId)
      } else {
        await companyApi.transferOwnership(
          selectedCompany.companyId,
          user.userId,
          ownershipPassword,
        )
        setOwnershipPassword('')
        window.location.assign('/workspace')
        return
      }
      setManagingUser(null)
      setMemberAssignments([])
      setMemberConfirmation(null)
      await loadUsers()
    } catch (memberError) {
      setError(
        apiErrorMessage(
          memberError,
          kind === 'REMOVE' ? 'Unable to remove member' : 'Unable to transfer ownership',
        ),
      )
    } finally {
      if (kind === 'TRANSFER') setOwnershipPassword('')
      setMemberActionUserId(null)
    }
  }

  const activeRoleIds = new Set(
    memberAssignments
      .filter((assignment) => !assignment.revokedAt)
      .map((assignment) => assignment.roleId),
  )

  const toggleMemberRole = async (role: CompanyRole) => {
    if (!selectedCompany || !managingUser) return
    setRoleActionId(role.id)
    setError('')
    try {
      if (activeRoleIds.has(role.id)) {
        await iamApi.revokeRole(selectedCompany.companyId, managingUser.userId, role.id)
      } else {
        await iamApi.assignRole(selectedCompany.companyId, managingUser.userId, role.id)
      }
      setMemberAssignments(await iamApi.memberRoles(selectedCompany.companyId, managingUser.userId))
      await loadUsers()
    } catch (roleError) {
      setError(apiErrorMessage(roleError, 'Unable to update member role'))
    } finally {
      setRoleActionId(null)
    }
  }

  const toggleRole = (roleId: string) => {
    setSelectedRoleIds((current) =>
      current.includes(roleId) ? current.filter((id) => id !== roleId) : [...current, roleId],
    )
  }

  return (
    <section className="px-4 py-6 sm:px-6 lg:px-8">
      <div className="mx-auto max-w-6xl">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div>
            <p className="eyebrow">Company</p>
            <h1 className="mt-2 text-2xl font-bold text-slate-950">Users & access</h1>
            <p className="mt-1 text-sm text-slate-500">
              Invite teammates and control their access with company IAM roles.
            </p>
          </div>
          <div className="flex items-center gap-2">
            <button type="button" className="secondary-button inline-flex" onClick={loadUsers}>
              <FiRefreshCw aria-hidden="true" />
              <span className="ml-2">Refresh</span>
            </button>
            {can(PERMISSIONS.USER_INVITE) && (
              <button
                type="button"
                className="primary-button inline-flex items-center gap-2"
                onClick={() => setShowInvite(true)}
              >
                <FiUserPlus aria-hidden="true" />
                Invite user
              </button>
            )}
          </div>
        </div>

        {error && (
          <div className="mt-4 rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
            {error}
          </div>
        )}

        <div className="mt-5 grid gap-4 sm:grid-cols-3">
          <div className="panel p-5">
            <p className="text-xs font-semibold text-slate-500">Active users</p>
            <p className="mt-2 text-3xl font-bold text-slate-950">
              {users.filter((user) => user.status === 'ACTIVE').length}
            </p>
          </div>
          <div className="panel p-5">
            <p className="text-xs font-semibold text-slate-500">Pending invitations</p>
            <p className="mt-2 text-3xl font-bold text-slate-950">{activeInvitations.length}</p>
          </div>
          <div className="panel p-5">
            <p className="text-xs font-semibold text-slate-500">Custom IAM roles</p>
            <p className="mt-2 text-3xl font-bold text-slate-950">{roles.length}</p>
          </div>
        </div>

        <div className="panel mt-5 overflow-hidden">
          <div className="border-b border-slate-100 px-5 py-4">
            <h2 className="text-sm font-bold text-slate-950">Members</h2>
            <p className="mt-1 text-xs text-slate-500">
              A user account is created only when the invited person registers.
            </p>
          </div>
          <div className="overflow-x-auto">
            <table className="mobile-card-table min-w-full divide-y divide-slate-200 text-left text-sm">
              <thead className="bg-slate-50 text-xs font-semibold uppercase tracking-wide text-slate-500">
                <tr>
                  <th className="px-4 py-3">User</th>
                  <th className="px-4 py-3">Access</th>
                  <th className="px-4 py-3">Security</th>
                  <th className="px-4 py-3">Joined</th>
                  {(can(PERMISSIONS.USER_MODIFY) ||
                    can(PERMISSIONS.USER_REMOVE) ||
                    can(PERMISSIONS.ROLE_ASSIGN) ||
                    can(PERMISSIONS.COMPANY_TRANSFER_OWNERSHIP)) && (
                    <th className="px-4 py-3">Action</th>
                  )}
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100 bg-white">
                {loading ? (
                  <tr>
                    <td
                      className="p-4"
                      colSpan={
                        (can(PERMISSIONS.USER_MODIFY) ||
                          can(PERMISSIONS.USER_REMOVE) ||
                          can(PERMISSIONS.ROLE_ASSIGN) ||
                          can(PERMISSIONS.COMPANY_TRANSFER_OWNERSHIP))
                          ? 5
                          : 4
                      }
                    >
                      <TableSkeleton
                        rows={6}
                        columns={
                          can(PERMISSIONS.USER_MODIFY) ||
                          can(PERMISSIONS.USER_REMOVE) ||
                          can(PERMISSIONS.ROLE_ASSIGN) ||
                          can(PERMISSIONS.COMPANY_TRANSFER_OWNERSHIP)
                            ? 5
                            : 4
                        }
                      />
                    </td>
                  </tr>
                ) : sortedUsers.length === 0 ? (
                  <tr>
                    <td
                      className="px-4 py-8 text-center text-slate-500"
                      colSpan={
                        (can(PERMISSIONS.USER_MODIFY) ||
                          can(PERMISSIONS.USER_REMOVE) ||
                          can(PERMISSIONS.ROLE_ASSIGN) ||
                          can(PERMISSIONS.COMPANY_TRANSFER_OWNERSHIP))
                          ? 5
                          : 4
                      }
                    >
                      No users found
                    </td>
                  </tr>
                ) : (
                  sortedUsers.map((user) => (
                    <tr key={user.memberId} className="hover:bg-slate-50/60">
                      <td className="px-4 py-3" data-label="User">
                        <div className="flex items-center gap-3">
                          <span className="flex size-9 items-center justify-center rounded-lg bg-indigo-50 text-xs font-bold text-indigo-500">
                            {user.name.slice(0, 2).toUpperCase()}
                          </span>
                          <span className="min-w-0">
                            <span className="block font-semibold text-slate-950">{user.name}</span>
                            <span className="block truncate text-xs text-slate-500">
                              @{user.username} · {user.email}
                            </span>
                          </span>
                        </div>
                      </td>
                      <td className="px-4 py-3" data-label="Access">
                        <div className="flex max-w-sm flex-wrap gap-1.5">
                          <span
                            className={`rounded-full px-2.5 py-1 text-xs font-semibold ${roleClass[user.role]}`}
                          >
                            {user.role}
                          </span>
                          {user.status === 'SUSPENDED' && (
                            <span className="rounded-full bg-amber-50 px-2.5 py-1 text-xs font-semibold text-amber-700">
                              SUSPENDED
                            </span>
                          )}
                          {user.customRoles?.map((role) => (
                            <span
                              key={role}
                              className="rounded-full bg-violet-50 px-2.5 py-1 text-xs font-semibold text-violet-700"
                            >
                              {role}
                            </span>
                          ))}
                        </div>
                      </td>
                      <td className="px-4 py-3" data-label="Security">
                        <div className="flex flex-wrap gap-2">
                          <span
                            className={`inline-flex items-center gap-1 rounded-full px-2.5 py-1 text-xs font-semibold ${
                              user.verified
                                ? 'bg-emerald-50 text-emerald-700'
                                : 'bg-amber-50 text-amber-700'
                            }`}
                          >
                            <FiUsers aria-hidden="true" />
                            {user.verified ? 'Verified' : 'Pending'}
                          </span>
                          <span
                            className={`inline-flex items-center gap-1 rounded-full px-2.5 py-1 text-xs font-semibold ${
                              user.twoFactorEnabled
                                ? 'bg-emerald-50 text-emerald-700'
                                : 'bg-slate-100 text-slate-600'
                            }`}
                          >
                            <FiShield aria-hidden="true" />
                            2FA {user.twoFactorEnabled ? 'On' : 'Off'}
                          </span>
                        </div>
                      </td>
                      <td className="px-4 py-3 text-slate-500" data-label="Joined">
                        {new Date(user.joinedAt).toLocaleDateString()}
                      </td>
                      {(can(PERMISSIONS.USER_MODIFY) ||
                        can(PERMISSIONS.USER_REMOVE) ||
                        can(PERMISSIONS.ROLE_ASSIGN) ||
                        can(PERMISSIONS.COMPANY_TRANSFER_OWNERSHIP)) && (
                        <td className="mobile-action-cell px-4 py-3" data-label="Action">
                          {user.role === 'OWNER' ? (
                            <span className="text-xs font-medium text-slate-400">Full access</span>
                          ) : (
                            <button
                              type="button"
                              className="secondary-button inline-flex items-center gap-2"
                              onClick={() => void openRoleManager(user)}
                            >
                              <FiKey aria-hidden="true" />
                              Manage access
                            </button>
                          )}
                        </td>
                      )}
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        </div>

        <div className="panel mt-5 overflow-hidden">
          <div className="border-b border-slate-100 px-5 py-4">
            <h2 className="text-sm font-bold text-slate-950">Invitations</h2>
            <p className="mt-1 text-xs text-slate-500">
              Pending invitations reserve a workspace seat until they expire or are revoked.
            </p>
          </div>

          {loading ? (
            <div className="p-4"><TableSkeleton rows={4} columns={3} /></div>
          ) : activeInvitations.length === 0 ? (
            <div className="px-5 py-10 text-center">
              <FiMail className="mx-auto text-slate-400" size={24} />
              <p className="mt-3 text-sm font-semibold text-slate-800">No pending invitations</p>
              <p className="mt-1 text-xs text-slate-500">Invite a teammate when you are ready.</p>
            </div>
          ) : (
            <div className="divide-y divide-slate-100">
              {activeInvitations.map((invitation) => (
                <div
                  key={invitation.invitationId}
                  className="flex flex-col gap-4 px-5 py-4 lg:flex-row lg:items-center lg:justify-between"
                >
                  <div className="min-w-0">
                    <div className="flex flex-wrap items-center gap-2">
                      <p className="truncate text-sm font-semibold text-slate-950">
                        {invitation.email}
                      </p>
                      <span
                        className={`rounded-full px-2.5 py-1 text-[11px] font-semibold ${invitationClass[invitation.status]}`}
                      >
                        {invitation.status}
                      </span>
                    </div>
                    <div className="mt-2 flex flex-wrap gap-1.5">
                      {invitation.roles.length === 0 ? (
                        <span className="text-xs text-slate-500">Default member access</span>
                      ) : (
                        invitation.roles.map((role) => (
                          <span
                            key={role.roleId}
                            className="rounded-full bg-violet-50 px-2.5 py-1 text-xs font-semibold text-violet-700"
                          >
                            {role.name}
                          </span>
                        ))
                      )}
                    </div>
                    <p className="mt-2 inline-flex items-center gap-1.5 text-xs text-slate-500">
                      <FiClock aria-hidden="true" />
                      {invitation.status === 'EXPIRED' ? 'Expired' : 'Expires'}{' '}
                      {new Date(invitation.expiresAt).toLocaleString()}
                    </p>
                  </div>

                  {can(PERMISSIONS.USER_INVITE) && (
                    <div className="flex shrink-0 items-center gap-2">
                      <button
                        type="button"
                        className="secondary-button inline-flex items-center gap-2"
                        disabled={workingInvitationId === invitation.invitationId}
                        onClick={() => void resend(invitation.invitationId)}
                      >
                        <FiSend aria-hidden="true" />
                        Resend
                      </button>
                      <button
                        type="button"
                        className="rounded-lg px-3 py-2 text-xs font-semibold text-rose-600 hover:bg-rose-50 disabled:opacity-50"
                        disabled={workingInvitationId === invitation.invitationId}
                        onClick={() => setInvitationToRevoke(invitation)}
                      >
                        Revoke
                      </button>
                    </div>
                  )}
                </div>
              ))}
            </div>
          )}
        </div>
      </div>

      {managingUser && (
        <div className="mobile-modal-shell fixed inset-0 z-50 flex items-center justify-center bg-slate-950/40 p-4">
          <div className="mobile-sheet w-full max-w-lg rounded-2xl bg-white shadow-2xl">
            <div className="flex items-start justify-between border-b border-slate-100 px-5 py-4">
              <div>
                <p className="eyebrow">Member access</p>
                <h2 className="mt-1 text-lg font-bold text-slate-950">
                  Manage roles for {managingUser.name}
                </h2>
                <p className="mt-1 text-sm text-slate-500">{managingUser.email}</p>
              </div>
              <button
                type="button"
                className="rounded-lg p-2 text-slate-400 hover:bg-slate-100"
                onClick={() => {
                  setManagingUser(null)
                  setMemberAssignments([])
                }}
                disabled={Boolean(roleActionId)}
              >
                <FiX aria-hidden="true" />
              </button>
            </div>

            <div className="max-h-[65vh] overflow-y-auto p-5">
              <div className="rounded-xl border border-slate-200 p-4">
                <p className="text-xs font-bold uppercase tracking-wide text-slate-500">Base access</p>
                <div className="mt-3 grid gap-3 sm:grid-cols-2">
                  <label className="text-xs font-semibold text-slate-700">
                    Company role
                    <select
                      className="form-input mt-1.5"
                      value={managingUser.role}
                      disabled={
                        !can(PERMISSIONS.USER_MODIFY) ||
                        managingUser.status !== 'ACTIVE' ||
                        memberActionUserId === managingUser.userId
                      }
                      onChange={(event) =>
                        void changeBaseRole(event.target.value as 'MANAGER' | 'MEMBER')
                      }
                    >
                      <option value="MEMBER">Member</option>
                      <option value="MANAGER">Manager</option>
                    </select>
                  </label>
                  <div>
                    <p className="text-xs font-semibold text-slate-700">Membership</p>
                    <div className="mt-1.5 flex gap-2">
                      {can(PERMISSIONS.USER_MODIFY) && (
                        <button
                          type="button"
                          className="secondary-button flex-1 justify-center"
                          disabled={memberActionUserId === managingUser.userId}
                          onClick={() =>
                            void changeMemberStatus(
                              managingUser.status === 'SUSPENDED' ? 'ACTIVE' : 'SUSPENDED',
                            )
                          }
                        >
                          {managingUser.status === 'SUSPENDED' ? 'Reactivate' : 'Suspend'}
                        </button>
                      )}
                      {can(PERMISSIONS.USER_REMOVE) && (
                        <button
                          type="button"
                          className="rounded-lg border border-rose-200 px-3 py-2 text-xs font-semibold text-rose-600 hover:bg-rose-50"
                          disabled={memberActionUserId === managingUser.userId}
                          onClick={() =>
                            setMemberConfirmation({ kind: 'REMOVE', user: managingUser })
                          }
                        >
                          Remove
                        </button>
                      )}
                    </div>
                  </div>
                </div>
                {can(PERMISSIONS.COMPANY_TRANSFER_OWNERSHIP) &&
                  managingUser.status === 'ACTIVE' && (
                    <div className="mt-3 rounded-lg border border-amber-200 bg-amber-50 p-3">
                      <p className="text-xs font-semibold text-amber-900">
                        Transfer ownership
                      </p>
                      <p className="mt-1 text-[11px] leading-5 text-amber-800">
                        Re-enter your current password before transferring this company.
                      </p>
                      <input
                        type="password"
                        className="form-input mt-2"
                        autoComplete="current-password"
                        placeholder="Current password"
                        value={ownershipPassword}
                        onChange={(event) => setOwnershipPassword(event.target.value)}
                      />
                      <button
                        type="button"
                        className="mt-2 w-full rounded-lg border border-amber-300 bg-white px-3 py-2 text-xs font-semibold text-amber-800 hover:bg-amber-100 disabled:opacity-50"
                        disabled={!ownershipPassword || memberActionUserId === managingUser.userId}
                        onClick={() =>
                          setMemberConfirmation({ kind: 'TRANSFER', user: managingUser })
                        }
                      >
                        Transfer company ownership to {managingUser.name}
                      </button>
                    </div>
                  )}
              </div>

              <div className="mt-5">
                <p className="mb-2 text-xs font-bold uppercase tracking-wide text-slate-500">
                  Custom IAM roles
                </p>
              {managingUser.status !== 'ACTIVE' ? (
                <div className="rounded-xl border border-dashed border-slate-200 p-6 text-center">
                  <p className="text-sm font-semibold text-slate-800">Member is suspended</p>
                  <p className="mt-1 text-xs text-slate-500">
                    Reactivate this member before changing custom roles.
                  </p>
                </div>
              ) : roles.length === 0 ? (
                <div className="rounded-xl border border-dashed border-slate-200 p-6 text-center">
                  <p className="text-sm font-semibold text-slate-800">No custom roles</p>
                  <p className="mt-1 text-xs text-slate-500">
                    Create roles from Company → Roles & permissions first.
                  </p>
                </div>
              ) : (
                <div className="space-y-2">
                  {roles.map((role) => {
                    const assigned = activeRoleIds.has(role.id)
                    return (
                      <label
                        key={role.id}
                        className="flex cursor-pointer items-start gap-3 rounded-xl border border-slate-200 p-3 hover:border-indigo-200 hover:bg-indigo-50/30"
                      >
                        <input
                          type="checkbox"
                          className="mt-1"
                          checked={assigned}
                          disabled={roleActionId === role.id}
                          onChange={() => void toggleMemberRole(role)}
                        />
                        <span className="min-w-0 flex-1">
                          <span className="block text-sm font-semibold text-slate-900">
                            {role.name}
                          </span>
                          <span className="mt-0.5 block text-xs text-slate-500">
                            {role.description || `${role.permissions.length} permissions`}
                          </span>
                        </span>
                      </label>
                    )
                  })}
                </div>
              )}
              </div>

              {error && (
                <p className="mt-4 rounded-lg bg-rose-50 p-3 text-sm text-rose-600">{error}</p>
              )}
            </div>

            <div className="flex justify-end border-t border-slate-100 px-5 py-4">
              <button
                type="button"
                className="secondary-button"
                onClick={() => {
                  setManagingUser(null)
                  setMemberAssignments([])
                }}
                disabled={Boolean(roleActionId)}
              >
                Done
              </button>
            </div>
          </div>
        </div>
      )}

      {showInvite && (
        <div className="mobile-modal-shell fixed inset-0 z-50 flex items-center justify-center bg-slate-950/40 p-4">
          <div className="mobile-sheet w-full max-w-lg rounded-2xl bg-white shadow-2xl">
            <div className="flex items-start justify-between border-b border-slate-100 px-5 py-4">
              <div>
                <h2 className="text-lg font-bold text-slate-950">Invite user</h2>
                <p className="mt-1 text-sm text-slate-500">
                  If the email has no account, they will register before joining.
                </p>
              </div>
              <button
                type="button"
                className="rounded-lg p-2 text-slate-400 hover:bg-slate-100"
                onClick={() => setShowInvite(false)}
              >
                <FiX aria-hidden="true" />
              </button>
            </div>

            <form onSubmit={sendInvite} className="p-5">
              <label className="block text-xs font-semibold text-slate-700">
                Email
                <input
                  type="email"
                  className="form-input mt-1.5"
                  placeholder="teammate@example.com"
                  value={inviteEmail}
                  onChange={(event) => setInviteEmail(event.target.value)}
                  autoFocus
                  required
                />
              </label>

              {canAll([PERMISSIONS.ROLE_READ, PERMISSIONS.ROLE_ASSIGN]) && roles.length > 0 && (
                <fieldset className="mt-5">
                  <legend className="text-xs font-semibold text-slate-700">
                    IAM roles <span className="font-normal text-slate-400">(optional)</span>
                  </legend>
                  <div className="mt-2 max-h-64 space-y-2 overflow-y-auto">
                    {roles.map((role) => (
                      <label
                        key={role.id}
                        className="flex cursor-pointer gap-3 rounded-xl border border-slate-200 p-3 hover:border-indigo-200 hover:bg-indigo-50/30"
                      >
                        <input
                          type="checkbox"
                          className="mt-1"
                          checked={selectedRoleIds.includes(role.id)}
                          onChange={() => toggleRole(role.id)}
                        />
                        <span>
                          <span className="block text-sm font-semibold text-slate-900">
                            {role.name}
                          </span>
                          <span className="mt-0.5 block text-xs text-slate-500">
                            {role.description || `${role.permissions.length} permissions`}
                          </span>
                        </span>
                      </label>
                    ))}
                  </div>
                </fieldset>
              )}

              {error && (
                <p className="mt-5 rounded-lg bg-rose-50 p-3 text-sm text-rose-600">{error}</p>
              )}

              <div className="mt-6 flex justify-end gap-2">
                <button
                  type="button"
                  className="secondary-button"
                  onClick={() => setShowInvite(false)}
                  disabled={submittingInvite}
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="primary-button inline-flex items-center gap-2"
                  disabled={submittingInvite}
                >
                  <FiSend aria-hidden="true" />
                  {submittingInvite ? 'Sending…' : 'Send invitation'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
        <ConfirmDialog
          open={Boolean(memberConfirmation)}
          title={
            memberConfirmation?.kind === 'TRANSFER'
              ? 'Transfer company ownership?'
              : 'Remove this member?'
          }
          description={
            memberConfirmation?.kind === 'TRANSFER'
              ? `${memberConfirmation.user.name} will become OWNER and you will become MANAGER.`
              : `${memberConfirmation?.user.name ?? 'This member'} will lose workspace access and must be invited again to return.`
          }
          confirmLabel={
            memberConfirmation?.kind === 'TRANSFER' ? 'Yes, transfer ownership' : 'Yes, remove member'
          }
          cancelLabel="No, cancel"
          tone={memberConfirmation?.kind === 'TRANSFER' ? 'warning' : 'danger'}
          working={Boolean(
            memberConfirmation && memberActionUserId === memberConfirmation.user.userId,
          )}
          onCancel={() => {
            if (memberConfirmation?.kind === 'TRANSFER') setOwnershipPassword('')
            setMemberConfirmation(null)
          }}
          onConfirm={() => void confirmMemberAction()}
        />
        <ConfirmDialog
          open={Boolean(invitationToRevoke)}
          title="Revoke this invitation?"
          description={invitationToRevoke ? `${invitationToRevoke.email} will no longer be able to use this invitation.` : ''}
          confirmLabel="Yes, revoke"
          cancelLabel="No, keep invitation"
          tone="warning"
          working={Boolean(invitationToRevoke && workingInvitationId === invitationToRevoke.invitationId)}
          onCancel={() => setInvitationToRevoke(null)}
          onConfirm={() => invitationToRevoke && void revoke(invitationToRevoke)}
        />
    </section>
  )
}
