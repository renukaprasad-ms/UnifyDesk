import { useSnackbarState } from '../../components/feedback/useSnackbarState'
import { useEffect, useMemo, useRef, useState } from 'react'
import { FiEdit2, FiPlus, FiRefreshCw, FiSearch, FiShield, FiTrash2, FiX } from 'react-icons/fi'
import { iamApi } from '../../features/iam/iamApi'
import { PERMISSIONS, type PermissionKey } from '../../features/iam/permissions'
import type { CompanyRole, PermissionDefinition } from '../../features/iam/types'
import { useIam } from '../../features/iam/useIam'
import { apiErrorMessage } from '../../lib/api/apiClient'
import ConfirmDialog from '../../components/ui/ConfirmDialog'
import { TableSkeleton } from '../../components/ui/Skeleton'
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

const permissionModule = (permission: PermissionDefinition) =>
  permission.key.split('.')[0] || permission.resource.split('.')[0] || permission.resource

type ModuleSelectAllProps = {
  checked: boolean
  indeterminate: boolean
  disabled: boolean
  selectedCount: number
  totalCount: number
  onChange: () => void
}

function ModuleSelectAll({
  checked,
  indeterminate,
  disabled,
  selectedCount,
  totalCount,
  onChange,
}: ModuleSelectAllProps) {
  const inputRef = useRef<HTMLInputElement | null>(null)

  useEffect(() => {
    if (inputRef.current) inputRef.current.indeterminate = indeterminate
  }, [indeterminate])

  return (
    <label className="flex cursor-pointer items-center gap-2 text-xs font-semibold text-indigo-700">
      <input
        ref={inputRef}
        type="checkbox"
        checked={checked}
        disabled={disabled}
        onChange={onChange}
      />
      <span>Select all</span>
      <span className="font-normal text-slate-400">
        {selectedCount}/{totalCount}
      </span>
    </label>
  )
}

export default function CompanyRoles() {
  const selectedCompany = useAppSelector(selectSelectedCompany)
  const { owner, can } = useIam()
  const [roles, setRoles] = useState<CompanyRole[]>([])
  const [catalog, setCatalog] = useState<PermissionDefinition[]>([])
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [draft, setDraft] = useState<RoleDraft | null>(null)
  const [permissionSearch, setPermissionSearch] = useState('')
  const [, setError] = useSnackbarState('', 'error')
  const [roleToDelete, setRoleToDelete] = useState<CompanyRole | null>(null)

  const groupedPermissions = useMemo(() => {
    const query = permissionSearch.trim().toLowerCase()
    const groups = new Map<string, PermissionDefinition[]>()

    catalog.forEach((permission) => {
      const module = permissionModule(permission)
      const searchable = [
        module,
        resourceLabel(module),
        permission.key,
        permission.resource,
        permission.action,
        actionLabel(permission.action),
        permission.description,
      ]
        .filter(Boolean)
        .join(' ')
        .toLowerCase()

      if (query && !searchable.includes(query)) return

      const current = groups.get(module) ?? []
      current.push(permission)
      groups.set(module, current)
    })

    return [...groups.entries()]
      .map(
        ([module, permissions]) =>
          [
            module,
            permissions.sort(
              (first, second) =>
                first.resource.localeCompare(second.resource) ||
                first.action.localeCompare(second.action),
            ),
          ] as const,
      )
      .sort(([first], [second]) => first.localeCompare(second))
  }, [catalog, permissionSearch])

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

  const beginCreate = () => {
    setPermissionSearch('')
    setDraft(emptyDraft)
  }

  const beginEdit = (role: CompanyRole) => {
    setPermissionSearch('')
    setDraft({
      id: role.id,
      name: role.name,
      description: role.description ?? '',
      permissions: [...role.permissions],
    })
  }

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

  const toggleModulePermissions = (module: string) => {
    if (!draft) return

    const moduleKeys = catalog
      .filter((permission) => permission.assignable && permissionModule(permission) === module)
      .map((permission) => permission.key as PermissionKey)

    const selected = new Set(draft.permissions)
    const allSelected = moduleKeys.length > 0 && moduleKeys.every((key) => selected.has(key))

    moduleKeys.forEach((key) => {
      if (allSelected) selected.delete(key)
      else selected.add(key)
    })

    setDraft({ ...draft, permissions: [...selected] })
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
    setSaving(true)
    setError('')
    try {
      await iamApi.deleteRole(selectedCompany.companyId, role.id)
      await load()
    } catch (deleteError) {
      setError(apiErrorMessage(deleteError, 'Unable to delete role'))
    } finally {
      setSaving(false)
      setRoleToDelete(null)
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



        <div className="panel mt-5 overflow-hidden">
          <div className="border-b border-slate-100 px-5 py-4">
            <h2 className="text-sm font-bold text-slate-950">Company roles</h2>
            <p className="mt-1 text-xs text-slate-500">
              Assign these roles from Company → Users or while sending an invitation.
            </p>
          </div>

          {loading ? (
            <div className="p-4"><TableSkeleton rows={5} columns={4} /></div>
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
                          onClick={() => setRoleToDelete(role)}
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
        <div className="mobile-modal-shell fixed inset-0 z-50 flex items-center justify-center bg-slate-950/40 p-4">
          <div className="mobile-sheet max-h-[92vh] w-full max-w-3xl overflow-hidden rounded-2xl bg-white shadow-2xl">
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
                  <div className="flex flex-wrap items-end justify-between gap-3">
                    <div>
                      <p className="text-xs font-bold uppercase tracking-wide text-slate-500">
                        Permissions
                      </p>
                      <p className="mt-1 text-xs text-slate-400">
                        Search permissions or grant all assignable access for a module.
                      </p>
                    </div>
                    <label className="relative block w-full sm:w-80">
                      <FiSearch
                        className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-slate-400"
                        aria-hidden="true"
                      />
                      <input
                        type="search"
                        className="form-input pl-9"
                        placeholder="Search permissions..."
                        value={permissionSearch}
                        onChange={(event) => setPermissionSearch(event.target.value)}
                      />
                    </label>
                  </div>

                  <div className="mt-3 space-y-4">
                    {groupedPermissions.length === 0 ? (
                      <div className="rounded-xl border border-dashed border-slate-200 px-4 py-10 text-center">
                        <p className="text-sm font-semibold text-slate-700">
                          No permissions match “{permissionSearch}”
                        </p>
                        <button
                          type="button"
                          className="mt-2 text-xs font-semibold text-indigo-600"
                          onClick={() => setPermissionSearch('')}
                        >
                          Clear search
                        </button>
                      </div>
                    ) : (
                      groupedPermissions.map(([module, permissions]) => {
                        const moduleAssignableKeys = catalog
                          .filter(
                            (permission) =>
                              permission.assignable && permissionModule(permission) === module,
                          )
                          .map((permission) => permission.key as PermissionKey)
                        const selectedCount = moduleAssignableKeys.filter((key) =>
                          draft.permissions.includes(key),
                        ).length
                        const allSelected =
                          moduleAssignableKeys.length > 0 &&
                          selectedCount === moduleAssignableKeys.length
                        const partiallySelected = selectedCount > 0 && !allSelected

                        return (
                          <fieldset
                            key={module}
                            className="rounded-xl border border-slate-200 bg-white p-4"
                          >
                            <div className="mb-3 flex flex-wrap items-center justify-between gap-3 border-b border-slate-100 pb-3">
                              <legend className="text-sm font-bold text-slate-900">
                                {resourceLabel(module)}
                              </legend>
                              <ModuleSelectAll
                                checked={allSelected}
                                indeterminate={partiallySelected}
                                disabled={moduleAssignableKeys.length === 0}
                                selectedCount={selectedCount}
                                totalCount={moduleAssignableKeys.length}
                                onChange={() => toggleModulePermissions(module)}
                              />
                            </div>

                            <div className="grid gap-2 md:grid-cols-2">
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
                                      {permission.resource !== module && (
                                        <span className="mt-1 block text-[10px] font-medium text-slate-400">
                                          {resourceLabel(permission.resource)}
                                        </span>
                                      )}
                                    </span>
                                  </label>
                                )
                              })}
                            </div>
                          </fieldset>
                        )
                      })
                    )}
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
        <ConfirmDialog
          open={Boolean(roleToDelete)}
          title="Delete this role?"
          description={roleToDelete ? `Existing assignments using “${roleToDelete.name}” will lose this role.` : ''}
          confirmLabel="Yes, delete role"
          cancelLabel="No, cancel"
          tone="danger"
          kind="delete"
          working={saving}
          onCancel={() => setRoleToDelete(null)}
          onConfirm={() => roleToDelete && void deleteRole(roleToDelete)}
        />
    </section>
  )
}
