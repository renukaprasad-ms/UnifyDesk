import { ListSkeleton } from '../../components/ui/Skeleton'
import WorkspaceDialog from '../../components/ui/WorkspaceDialog'
import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router'
import { crmWorkApi, type CrmAccount } from '../../features/contacts/crmWorkApi'
import {
  crmCustomFieldsApi,
  type CrmFieldDef,
  type CustomFieldValues,
} from '../../features/contacts/crmCustomFieldsApi'
import { CustomFieldsSection } from '../../components/crm/CustomFieldRenderer'
import { PERMISSIONS } from '../../features/iam/permissions'
import { useIam } from '../../features/iam/useIam'
import { useWorkspaceEpochScope } from '../../features/workspace/useWorkspaceEpochScope'
import { apiErrorMessage } from '../../lib/api/apiClient'
import { useAppSelector } from '../../store/hooks'
import { selectSelectedCompanyId, selectWorkspaceEpoch } from '../../store/workspace/workspaceSlice'

export default function CrmCompaniesPage() {
  const companyId = useAppSelector(selectSelectedCompanyId)
  const epoch = useAppSelector(selectWorkspaceEpoch)
  const { capture, isCurrent } = useWorkspaceEpochScope(companyId, epoch)
  const { can } = useIam()
  const [items, setItems] = useState<CrmAccount[]>([])
  const [next, setNext] = useState<string | null>(null)
  const [showCreate, setShowCreate] = useState(false)
  const [name, setName] = useState('')
  const [customDefs, setCustomDefs] = useState<CrmFieldDef[]>([])
  const [customFields, setCustomFields] = useState<CustomFieldValues>({})
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    if (!companyId) return
    crmCustomFieldsApi.list(companyId, 'ACCOUNT').then(setCustomDefs).catch(() => {})
  }, [companyId])

  const load = useCallback(async () => {
    if (!companyId) return
    const token = capture()
    setLoading(true)
    try {
      const page = await crmWorkApi.accounts(companyId)
      if (isCurrent(token)) {
        setItems(page.items)
        setNext(page.nextCursor)
        setError('')
      }
    } catch (err) {
      if (isCurrent(token)) setError(apiErrorMessage(err, 'Unable to load customer companies'))
    } finally {
      if (isCurrent(token)) setLoading(false)
    }
  }, [companyId, capture, isCurrent])

  useEffect(() => {
    setItems([])
    setNext(null)
    void load()
  }, [load, epoch])

  const add = async () => {
    if (!companyId || !name.trim() || busy) return
    const token = capture()
    setBusy(true)
    try {
      await crmWorkApi.addAccount(companyId, name.trim(), { customFields })
      if (isCurrent(token)) {
        setName('')
        setCustomFields({})
        setShowCreate(false)
        await load()
      }
    } catch (err) {
      if (isCurrent(token)) setError(apiErrorMessage(err, 'Unable to create company'))
    } finally {
      if (isCurrent(token)) setBusy(false)
    }
  }

  const more = async () => {
    if (!companyId || !next || busy) return
    const token = capture()
    setBusy(true)
    try {
      const page = await crmWorkApi.accounts(companyId, next)
      if (isCurrent(token)) {
        setItems((old) => [...old, ...page.items])
        setNext(page.nextCursor)
      }
    } catch (err) {
      if (isCurrent(token)) setError(apiErrorMessage(err, 'Unable to load more companies'))
    } finally {
      if (isCurrent(token)) setBusy(false)
    }
  }

  return (
    <section className="w-full space-y-5 px-4 py-6 sm:px-6">
      <header className="flex flex-wrap items-center justify-between gap-4">
        <div>
          <p className="eyebrow">CRM</p>
          <h1 className="mt-2 text-3xl font-bold">Companies</h1>
          <p className="mt-2 text-sm text-slate-500">
            Customer organizations, separate from your UnifyDesk workspace.
          </p>
        </div>
        {can(PERMISSIONS.CRM_ACCOUNT_MODIFY) && (
          <button type="button" className="primary-button" onClick={() => setShowCreate(true)}>
            New company
          </button>
        )}
      </header>
      {error && !showCreate && (
        <p role="alert" className="rounded-lg bg-rose-50 p-3 text-sm text-rose-700">
          {error}
        </p>
      )}
      {showCreate && can(PERMISSIONS.CRM_ACCOUNT_MODIFY) && (
        <WorkspaceDialog
          title="New customer company"
          busy={busy}
          error={error}
          onClose={() => setShowCreate(false)}
        >
          <form
            className="flex flex-col gap-3"
            onSubmit={(event) => {
              event.preventDefault()
              void add()
            }}
          >
            <label htmlFor="crm-company-name" className="text-sm font-medium">
              Customer company name
            </label>
            <input
              id="crm-company-name"
              className="form-input w-full"
              placeholder="Customer company name"
              maxLength={240}
              value={name}
              onChange={(event) => setName(event.target.value)}
            />
            {customDefs.length > 0 && (
              <CustomFieldsSection
                defs={customDefs}
                values={customFields}
                onChange={(key, val) =>
                  setCustomFields((prev) => ({
                    ...prev,
                    [key]: val,
                  }))
                }
                disabled={busy}
              />
            )}
            <button className="primary-button mt-2" type="submit" disabled={busy || !name.trim()}>
              Add company
            </button>
          </form>
        </WorkspaceDialog>
      )}
      {loading ? (
        <ListSkeleton rows={3} />
      ) : items.length === 0 ? (
        <div className="rounded-xl border border-dashed p-8 text-center text-sm text-slate-500">
          No customer companies yet.
        </div>
      ) : (
        <div className="overflow-hidden rounded-xl border bg-white">
          {items.map((item) => (
            <div key={item.id} className="border-b border-slate-100 px-4 py-3">
              <div className="flex items-center justify-between gap-3">
                <Link
                  to={'/crm/companies/' + item.id}
                  className="font-semibold text-indigo-600 hover:underline"
                >
                  {item.name}
                </Link>
                {item.website && <span className="text-xs text-slate-500">{item.website}</span>}
              </div>
              {item.customFields && Object.keys(item.customFields).length > 0 && (
                <div className="mt-1 flex flex-wrap gap-1">
                  {Object.entries(item.customFields)
                    .filter(([, v]) => v != null && v !== '')
                    .slice(0, 3)
                    .map(([k, v]) => (
                      <span
                        key={k}
                        className="inline-flex items-center rounded bg-slate-100 px-1.5 py-0.5 text-[10px] font-normal text-slate-600"
                      >
                        {k}: {Array.isArray(v) ? v.join(', ') : String(v)}
                      </span>
                    ))}
                </div>
              )}
            </div>
          ))}
        </div>
      )}
      {next && (
        <button
          type="button"
          className="secondary-button"
          disabled={busy}
          onClick={() => void more()}
        >
          Load more
        </button>
      )}
    </section>
  )
}
