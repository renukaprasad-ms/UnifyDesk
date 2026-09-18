import { useEffect, useMemo, useState } from 'react'
import { FiEdit2, FiPlus, FiRefreshCw, FiShield, FiTrash2, FiX } from 'react-icons/fi'
import { iamApi } from '../../features/iam/iamApi'
import { PERMISSIONS, type PermissionKey } from '../../features/iam/permissions'
import type { CompanyRole, PermissionDefinition } from '../../features/iam/types'
import { useIam } from '../../features/iam/useIam'
import { apiErrorMessage } from '../../lib/api/apiClient'
import { useAppSelector } from '../../store/hooks'
import { selectSelectedCompany } from '../../store/workspace/workspaceSlice'

type RoleDraft = {
  id?: string
  name: string
  description: string
  permissions: PermissionKey[]
}

const emptyDraft: RoleDraft = {
  name: '',
  description: '',
  permissions: [],
}

const resourceLabel = (resource: string) =>
  resource
    .split(/[._-]/)
    .filter(Boolean)
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(' ')

const actionLabel = (action: string) =>
  action
    .split(/[._-]/)
    .filter(Boolean)
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(' ')

export default function CompanyRoles() {
  const selectedCompany = useAppSelector(selectSelectedCompany)
  const { owner, can } = useIam()
  const [roles, setRoles] = useState<CompanyRole[]>([])
  const [catalog, setCatalog] = useState<PermissionDefinition[]>([])
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [draft, setDraft] = useState<RoleDraft | null>(null)
  const [error, setError] = useState('')

  const groupedPermissions = useMemo(() => {
    const groups = new Map<string, PermissionDefinition[]>()
    catalog.forEach((permission) => {
      const current = groups.get(permission.resource) ?? []
      current.push(permission)
      groups.set(permission.resource, current)
    })
    return [...groups.entries()].sort(([first], [second]) => first.localeCompare(second))
  }, [catalog])

  const load = async () => {
    if (!selectedCompany) return
    setLoading(true)
    setError('')
    try {
      const [loadedRoles, loadedCatalog] = await Promise.all([
        iamApi.roles(selectedCompany.companyId),
        iamApi.permissionCatalog(selectedCompany.companyId),
      ])
      setRoles(loadedRoles)
      setCatalog(loadedCatalog)
    } catch (loadError) {
      setError(apiErrorMessage(loadError, 'Unable to load company roles'))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    void load()
  }, [selectedCompany?.companyId])

  const beginCreate = () => setDraft(emptyDraft)

  const beginEdit = (role: CompanyRole) =>
    setDraft({
      id: role.id,
      name: role.name,
      description: role.description ?? '',
      permissions: [...role.permissions],
    })

  const togglePermission = (key: PermissionKey) => {
    setDraft((current) => {
      if (!current) return current
      return {
        ...current,
        permissions: current.permissions.includes(key)
          ? current.permissions.filter((permission) => permission !== key)
          : [...current.permissions, key],
      }
    })
  }

  const saveRole = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (!selectedCompany || !draft) return
    setSaving(true)
    setError('')
    try {
      const payload = {
        name: draft.name.trim(),
        description: draft.description.trim() || null,
        permissions: draft.permissions,
      }
      if (draft.id) {
        await iamApi.updateRole(selectedCompany.companyId, draft.id, payload)
      } else {
        await iamApi.createRole(selectedCompany.companyId, payload)
      }
      setDraft(null)
      await load()
    } catch (saveError) {
      setError(apiErrorMessage(saveError, 'Unable to save role'))
    } finally {
      setSaving(false)
    }
  }

  const deleteRole = async (role: CompanyRole) => {
    if (!selectedCompany) return
    if (
      !window.confirm(`Delete the role "${role.name}"? Existing assignments will lose this role.`)
    ) {
      return
    }
    setSaving(true)
    setError('')
    try {
      await iamApi.deleteRole(selectedCompany.companyId, role.id)
      await load()
    } catch (deleteError) {
      setError(apiErrorMessage(deleteError, 'Unable to delete role'))
    } finally {
      setSaving(false)
    }
  }

  return (
    <section className="px-4 py-6 sm:px-6 lg:px-8">
      <div className="mx-auto max-w-6xl">
        <div className="flex flex-wrap items-end justify-between gap-4">
          <div>
            <p className="eyebrow">Company IAM</p>
            <h1 className="mt-2 text-2xl font-bold text-slate-950">Roles & permissions</h1>
            <p className="mt-1 max-w-2xl text-sm text-slate-500">
              Create reusable roles and decide exactly what teammates can access inside this
              company.
            </p>
          </div>
          <div className="flex items-center gap-2">
            <button
              type="button"
              className="secondary-button inline-flex"
              onClick={() => void load()}
            >
              <FiRefreshCw aria-hidden="true" />
              <span className="ml-2">Refresh</span>
            </button>
            {can(PERMISSIONS.ROLE_CREATE) && (
              <button
                type="button"
                className="primary-button inline-flex items-center gap-2"
                onClick={beginCreate}
              >
                <FiPlus aria-hidden="true" />
                Create role
              </button>
            )}
          </div>
        </div>

        {owner && (
          <div className="mt-5 flex gap-3 rounded-xl border border-indigo-100 bg-indigo-50/60 p-4">
            <FiShield className="mt-0.5 shrink-0 text-indigo-600" aria-hidden="true" />
            <div>
              <p className="text-sm font-semibold text-indigo-950">Owner access</p>
              <p className="mt-1 text-xs leading-5 text-indigo-700">
                The company owner always has full access. Custom roles are for other company members
                and cannot reduce the owner's permissions.
              </p>
            </div>
          </div>
        )}

        {error && (
          <div className="mt-5 rounded-xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700">
            {error}
          </div>
        )}

        <div className="panel mt-5 overflow-hidden">
          <div className="border-b border-slate-100 px-5 py-4">
            <h2 className="text-sm font-bold text-slate-950">Company roles</h2>
            <p className="mt-1 text-xs text-slate-500">
              Assign these roles from Company → Users or while sending an invitation.
            </p>
          </div>

          {loading ? (
            <p className="px-5 py-10 text-center text-sm text-slate-500">Loading roles…</p>
          ) : roles.length === 0 ? (
            <div className="px-5 py-12 text-center">
              <span className="mx-auto flex size-10 items-center justify-center rounded-xl bg-slate-100 text-slate-500">
                <FiShield aria-hidden="true" />
              </span>
              <p className="mt-3 text-sm font-semibold text-slate-800">No custom roles yet</p>
              <p className="mt-1 text-sm text-slate-500">
                Create a role such as Email Agent, Broadcast Operator or Billing Manager.
              </p>
            </div>
          ) : (
            <div className="divide-y divide-slate-100">
              {roles.map((role) => (
                <div
                  key={role.id}
                  className="flex flex-col gap-4 px-5 py-4 lg:flex-row lg:items-center lg:justify-between"
                >
                  <div className="min-w-0">
                    <div className="flex flex-wrap items-center gap-2">
                      <p className="text-sm font-bold text-slate-950">{role.name}</p>
                      {role.systemRole && (
                        <span className="rounded-full bg-slate-100 px-2.5 py-1 text-[10px] font-bold text-slate-600">
                          SYSTEM
                        </span>
                      )}
                    </div>
                    <p className="mt-1 text-xs text-slate-500">
                      {role.description || 'No description'}
                    </p>
                    <div className="mt-2 flex flex-wrap gap-1.5">
                      {role.permissions.slice(0, 8).map((permission) => (
                        <span
                          key={permission}
                          className="rounded-full bg-indigo-50 px-2 py-1 text-[10px] font-semibold text-indigo-700"
                        >
                          {permission}
                        </span>
                      ))}
                      {role.permissions.length > 8 && (
                        <span className="rounded-full bg-slate-100 px-2 py-1 text-[10px] font-semibold text-slate-600">
                          +{role.permissions.length - 8} more
                        </span>
                      )}
                    </div>
                  </div>

                  {!role.systemRole && (
                    <div className="flex shrink-0 items-center gap-2">
                      {can(PERMISSIONS.ROLE_MODIFY) && (
                        <button
                          type="button"
                          className="secondary-button inline-flex items-center gap-2"
                          onClick={() => beginEdit(role)}
                        >
                          <FiEdit2 aria-hidden="true" />
                          Edit
                        </button>
                      )}
                      {can(PERMISSIONS.ROLE_DELETE) && (
                        <button
                          type="button"
                          className="rounded-lg px-3 py-2 text-xs font-semibold text-rose-600 hover:bg-rose-50 disabled:opacity-50"
                          disabled={saving}
                          onClick={() => void deleteRole(role)}
                        >
                          <FiTrash2 aria-hidden="true" className="mr-1 inline" />
                          Delete
                        </button>
                      )}
                    </div>
                  )}
                </div>
              ))}
            </div>
          )}
        </div>
      </div>

      {draft && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/40 p-4">
          <div className="max-h-[92vh] w-full max-w-3xl overflow-hidden rounded-2xl bg-white shadow-2xl">
            <div className="flex items-start justify-between border-b border-slate-100 px-5 py-4">
              <div>
                <h2 className="text-lg font-bold text-slate-950">
                  {draft.id ? 'Edit role' : 'Create role'}
                </h2>
                <p className="mt-1 text-sm text-slate-500">
                  Select the permissions members with this role should receive.
                </p>
              </div>
              <button
                type="button"
                className="rounded-lg p-2 text-slate-400 hover:bg-slate-100"
                onClick={() => setDraft(null)}
                disabled={saving}
              >
                <FiX aria-hidden="true" />
              </button>
            </div>

            <form onSubmit={saveRole} className="flex max-h-[calc(92vh-82px)] flex-col">
              <div className="overflow-y-auto p-5">
                <div className="grid gap-4 md:grid-cols-2">
                  <label className="text-xs font-semibold text-slate-700">
                    Role name
                    <input
                      className="form-input mt-1.5"
                      value={draft.name}
                      maxLength={120}
                      placeholder="Email Agent"
                      onChange={(event) =>
                        setDraft((current) =>
                          current ? { ...current, name: event.target.value } : current,
                        )
                      }
                      required
                    />
                  </label>
                  <label className="text-xs font-semibold text-slate-700">
                    Description
                    <input
                      className="form-input mt-1.5"
                      value={draft.description}
                      maxLength={500}
                      placeholder="Can read and send company email"
                      onChange={(event) =>
                        setDraft((current) =>
                          current ? { ...current, description: event.target.value } : current,
                        )
                      }
                    />
                  </label>
                </div>

                <div className="mt-6">
                  <p className="text-xs font-bold uppercase tracking-wide text-slate-500">
                    Permissions
                  </p>
                  <div className="mt-3 space-y-4">
                    {groupedPermissions.map(([resource, permissions]) => (
                      <fieldset
                        key={resource}
                        className="rounded-xl border border-slate-200 bg-white p-4"
                      >
                        <legend className="px-1 text-sm font-bold text-slate-900">
                          {resourceLabel(resource)}
                        </legend>
                        <div className="mt-2 grid gap-2 md:grid-cols-2">
                          {permissions.map((permission) => {
                            const permissionKey = permission.key as PermissionKey
                            const disabled = !permission.assignable
                            return (
                              <label
                                key={permission.key}
                                className={`flex gap-3 rounded-lg p-2.5 ${
                                  disabled
                                    ? 'cursor-not-allowed bg-slate-50 opacity-60'
                                    : 'cursor-pointer hover:bg-indigo-50/50'
                                }`}
                              >
                                <input
                                  type="checkbox"
                                  className="mt-1"
                                  disabled={disabled}
                                  checked={draft.permissions.includes(permissionKey)}
                                  onChange={() => togglePermission(permissionKey)}
                                />
                                <span>
                                  <span className="block text-xs font-semibold text-slate-800">
                                    {actionLabel(permission.action)}
                                  </span>
                                  <span className="mt-0.5 block text-[11px] leading-4 text-slate-500">
                                    {permission.description || permission.key}
                                    {!permission.assignable && ' · Owner only'}
                                  </span>
                                </span>
                              </label>
                            )
                          })}
                        </div>
                      </fieldset>
                    ))}
                  </div>
                </div>
              </div>

              <div className="flex justify-end gap-2 border-t border-slate-100 bg-white px-5 py-4">
                <button
                  type="button"
                  className="secondary-button"
                  onClick={() => setDraft(null)}
                  disabled={saving}
                >
                  Cancel
                </button>
                <button type="submit" className="primary-button" disabled={saving}>
                  {saving ? 'Saving…' : draft.id ? 'Save changes' : 'Create role'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </section>
  )
}
