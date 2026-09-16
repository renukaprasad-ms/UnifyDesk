import { useEffect, useMemo, useState } from 'react'
import { FiEdit2, FiKey, FiPlus, FiRefreshCw, FiSave, FiTrash2, FiX } from 'react-icons/fi'
import { apiErrorMessage } from '../../lib/api/apiClient'
import { userApi } from '../../features/user/userApi'
import type { User } from '../../features/user/types'

type UserForm = {
  email: string
  name: string
  username: string
  password: string
  verified: boolean
  twoFactorEnabled: boolean
}

const emptyForm: UserForm = {
  email: '',
  name: '',
  username: '',
  password: '',
  verified: false,
  twoFactorEnabled: false,
}

const toForm = (user: User): UserForm => ({
  email: user.email,
  name: user.name,
  username: user.username,
  password: '',
  verified: user.verified,
  twoFactorEnabled: user.twoFactorEnabled,
})

export default function CompanyUsers() {
  const [users, setUsers] = useState<User[]>([])
  const [selectedUser, setSelectedUser] = useState<User | null>(null)
  const [form, setForm] = useState<UserForm>(emptyForm)
  const [resetPassword, setResetPassword] = useState('')
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')

  const isEditing = Boolean(selectedUser)
  const sortedUsers = useMemo(
    () => [...users].sort((first, second) => first.name.localeCompare(second.name)),
    [users],
  )

  const loadUsers = async () => {
    setError('')
    setLoading(true)
    try {
      setUsers(await userApi.list())
    } catch (loadError) {
      setError(apiErrorMessage(loadError, 'Unable to load users'))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    void loadUsers()
  }, [])

  const updateUserInList = (user: User) => {
    setUsers((current) => current.map((item) => (item.id === user.id ? user : item)))
    setSelectedUser(user)
    setForm(toForm(user))
  }

  const clearForm = () => {
    setSelectedUser(null)
    setForm(emptyForm)
    setResetPassword('')
  }

  const handleSubmit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    setSaving(true)
    setError('')
    setNotice('')

    try {
      if (selectedUser) {
        const updated = await userApi.update(selectedUser.id, {
          email: form.email,
          name: form.name,
          username: form.username,
          verified: form.verified,
        })
        updateUserInList(updated)
        setNotice('User updated')
      } else {
        const created = await userApi.create({
          email: form.email,
          name: form.name,
          username: form.username,
          password: form.password,
          twoFactorEnabled: form.twoFactorEnabled,
        })
        setUsers((current) => [...current, created])
        clearForm()
        setNotice('User created')
      }
    } catch (saveError) {
      setError(apiErrorMessage(saveError, 'Unable to save user'))
    } finally {
      setSaving(false)
    }
  }

  const handleToggleTwoFactor = async (user: User) => {
    setError('')
    setNotice('')
    try {
      const updated = await userApi.toggleTwoFactor(user.id, { enabled: !user.twoFactorEnabled })
      updateUserInList(updated)
      setNotice('2FA updated')
    } catch (toggleError) {
      setError(apiErrorMessage(toggleError, 'Unable to update 2FA'))
    }
  }

  const handleResetPassword = async () => {
    if (!selectedUser || !resetPassword) return
    setSaving(true)
    setError('')
    setNotice('')
    try {
      const updated = await userApi.resetPassword(selectedUser.id, { password: resetPassword })
      updateUserInList(updated)
      setResetPassword('')
      setNotice('Password reset')
    } catch (passwordError) {
      setError(apiErrorMessage(passwordError, 'Unable to reset password'))
    } finally {
      setSaving(false)
    }
  }

  const handleDelete = async (user: User) => {
    if (!window.confirm(`Delete ${user.name}?`)) return
    setError('')
    setNotice('')
    try {
      await userApi.delete(user.id)
      setUsers((current) => current.filter((item) => item.id !== user.id))
      if (selectedUser?.id === user.id) {
        clearForm()
      }
      setNotice('User deleted')
    } catch (deleteError) {
      setError(apiErrorMessage(deleteError, 'Unable to delete user'))
    }
  }

  return (
    <section className="px-4 py-6 sm:px-6 lg:px-8">
      <div className="mx-auto grid max-w-7xl gap-6 xl:grid-cols-[1fr_390px]">
        <div>
          <div className="flex flex-wrap items-center justify-between gap-3">
            <div>
              <p className="eyebrow">Company</p>
              <h1 className="mt-2 text-2xl font-bold text-slate-950">Users</h1>
            </div>
            <button type="button" className="secondary-button inline-flex" onClick={loadUsers}>
              <FiRefreshCw aria-hidden="true" />
              <span className="ml-2">Refresh</span>
            </button>
          </div>

          {(error || notice) && (
            <div
              className={`mt-4 rounded-lg border px-4 py-3 text-sm ${
                error
                  ? 'border-red-200 bg-red-50 text-red-700'
                  : 'border-emerald-200 bg-emerald-50 text-emerald-700'
              }`}
            >
              {error || notice}
            </div>
          )}

          <div className="panel mt-5 overflow-hidden">
            <div className="overflow-x-auto">
              <table className="min-w-full divide-y divide-slate-200 text-left text-sm">
                <thead className="bg-slate-50 text-xs font-semibold uppercase tracking-wide text-slate-500">
                  <tr>
                    <th className="px-4 py-3">User</th>
                    <th className="px-4 py-3">Status</th>
                    <th className="px-4 py-3">2FA</th>
                    <th className="px-4 py-3 text-right">Actions</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100 bg-white">
                  {loading ? (
                    <tr>
                      <td className="px-4 py-8 text-center text-slate-500" colSpan={4}>
                        Loading users
                      </td>
                    </tr>
                  ) : sortedUsers.length === 0 ? (
                    <tr>
                      <td className="px-4 py-8 text-center text-slate-500" colSpan={4}>
                        No users yet
                      </td>
                    </tr>
                  ) : (
                    sortedUsers.map((user) => (
                      <tr key={user.id}>
                        <td className="px-4 py-3">
                          <p className="font-semibold text-slate-950">{user.name}</p>
                          <p className="text-xs text-slate-500">
                            @{user.username} · {user.email}
                          </p>
                        </td>
                        <td className="px-4 py-3">
                          <span
                            className={`rounded-full px-2.5 py-1 text-xs font-semibold ${
                              user.verified
                                ? 'bg-emerald-50 text-emerald-700'
                                : 'bg-amber-50 text-amber-700'
                            }`}
                          >
                            {user.verified ? 'Verified' : 'Pending'}
                          </span>
                        </td>
                        <td className="px-4 py-3">
                          <button
                            type="button"
                            className={`switch ${user.twoFactorEnabled ? 'is-on' : ''}`}
                            onClick={() => void handleToggleTwoFactor(user)}
                            aria-label="Toggle two-factor authentication"
                          >
                            <span />
                          </button>
                        </td>
                        <td className="px-4 py-3">
                          <div className="flex justify-end gap-2">
                            <button
                              type="button"
                              className="secondary-button inline-flex h-9 w-9 p-0"
                              onClick={() => {
                                setSelectedUser(user)
                                setForm(toForm(user))
                                setResetPassword('')
                              }}
                              aria-label="Edit user"
                              title="Edit"
                            >
                              <FiEdit2 aria-hidden="true" />
                            </button>
                            <button
                              type="button"
                              className="secondary-button inline-flex h-9 w-9 p-0 text-red-600"
                              onClick={() => void handleDelete(user)}
                              aria-label="Delete user"
                              title="Delete"
                            >
                              <FiTrash2 aria-hidden="true" />
                            </button>
                          </div>
                        </td>
                      </tr>
                    ))
                  )}
                </tbody>
              </table>
            </div>
          </div>
        </div>

        <aside className="panel p-5">
          <div className="flex items-center justify-between gap-3">
            <h2 className="text-lg font-bold text-slate-950">{isEditing ? 'Edit user' : 'Add user'}</h2>
            {isEditing && (
              <button type="button" className="secondary-button inline-flex h-9 w-9 p-0" onClick={clearForm}>
                <FiX aria-hidden="true" />
              </button>
            )}
          </div>

          <form className="mt-5 grid gap-4" onSubmit={(event) => void handleSubmit(event)}>
            <label className="grid gap-1.5 text-xs font-semibold text-slate-600">
              Name
              <input
                className="form-input"
                value={form.name}
                onChange={(event) => setForm({ ...form, name: event.target.value })}
                required
              />
            </label>
            <label className="grid gap-1.5 text-xs font-semibold text-slate-600">
              Username
              <input
                className="form-input"
                value={form.username}
                onChange={(event) => setForm({ ...form, username: event.target.value })}
                required
              />
            </label>
            <label className="grid gap-1.5 text-xs font-semibold text-slate-600">
              Email
              <input
                className="form-input"
                type="email"
                value={form.email}
                onChange={(event) => setForm({ ...form, email: event.target.value })}
                required
              />
            </label>
            {!isEditing && (
              <label className="grid gap-1.5 text-xs font-semibold text-slate-600">
                Password
                <input
                  className="form-input"
                  type="password"
                  value={form.password}
                  minLength={8}
                  onChange={(event) => setForm({ ...form, password: event.target.value })}
                  required
                />
              </label>
            )}
            <div className="grid gap-3 rounded-lg border border-slate-200 p-3">
              <label className="flex items-center justify-between gap-3 text-sm font-semibold text-slate-700">
                Verified
                <input
                  type="checkbox"
                  checked={form.verified}
                  onChange={(event) => setForm({ ...form, verified: event.target.checked })}
                />
              </label>
              <label className="flex items-center justify-between gap-3 text-sm font-semibold text-slate-700">
                2FA enabled
                <input
                  type="checkbox"
                  checked={form.twoFactorEnabled}
                  disabled={isEditing}
                  onChange={(event) => setForm({ ...form, twoFactorEnabled: event.target.checked })}
                />
              </label>
            </div>
            <button type="submit" className="primary-button inline-flex" disabled={saving}>
              {isEditing ? <FiSave aria-hidden="true" /> : <FiPlus aria-hidden="true" />}
              <span className="ml-2">{isEditing ? 'Save changes' : 'Create user'}</span>
            </button>
          </form>

          {selectedUser && (
            <div className="mt-6 border-t border-slate-200 pt-5">
              <h3 className="text-sm font-bold text-slate-950">Reset password</h3>
              <div className="mt-3 flex gap-2">
                <input
                  className="form-input"
                  type="password"
                  value={resetPassword}
                  minLength={8}
                  placeholder="New password"
                  onChange={(event) => setResetPassword(event.target.value)}
                />
                <button
                  type="button"
                  className="secondary-button inline-flex h-11 w-11 flex-none p-0"
                  onClick={() => void handleResetPassword()}
                  disabled={saving || resetPassword.length < 8}
                  aria-label="Reset password"
                  title="Reset password"
                >
                  <FiKey aria-hidden="true" />
                </button>
              </div>
            </div>
          )}
        </aside>
      </div>
    </section>
  )
}
