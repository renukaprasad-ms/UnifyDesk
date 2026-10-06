import {
  useCallback,
  useEffect,
  useLayoutEffect,
  useMemo,
  useRef,
  useState,
  type FormEvent,
  type ReactNode,
} from 'react'
import { FiCheckCircle, FiRefreshCw, FiSend, FiX } from 'react-icons/fi'
import { managedSmsApi } from '../../features/managedSms/managedSmsApi'
import {
  managedSmsErrorMessage,
  managedSmsStateDescription,
} from '../../features/managedSms/copy'
import { isManagedSmsTenantCurrent } from '../../features/managedSms/tenantScope'
import type {
  ManagedSmsDltPlatform,
  ManagedSmsDltProfile,
  ManagedSmsHeader,
  ManagedSmsMessageCategory,
  ManagedSmsStatus,
  ManagedSmsTemplate,
  ManagedSmsTemplateVariable,
} from '../../features/managedSms/types'
import { useSnackbar } from '../feedback/SnackbarProvider'
import ManagedSmsStatusBadge from './ManagedSmsStatusBadge'
import { CardSkeleton } from '../ui/Skeleton'

const categories: ManagedSmsMessageCategory[] = [
  'SERVICE',
  'TRANSACTIONAL',
  'PROMOTIONAL',
  'GOVERNMENT',
]

const dltPlatforms: ManagedSmsDltPlatform[] = ['AIRTEL', 'JIO', 'VIL', 'BSNL', 'OTHER']

const splitVariables = (value: string): ManagedSmsTemplateVariable[] =>
  value
    .split(',')
    .map((item) => item.trim())
    .filter(Boolean)
    .map((key) => ({ key, type: 'ALPHANUMERIC', maxLength: 100 }))

export default function ManagedSmsSetupModal({
  companyId,
  canModify,
  onClose,
  onChanged,
}: {
  companyId: string
  canModify: boolean
  onClose: () => void
  onChanged: () => void
}) {
  const snackbar = useSnackbar()
  const [status, setStatus] = useState<ManagedSmsStatus | null>(null)
  const [profile, setProfile] = useState<ManagedSmsDltProfile | null>(null)
  const [headers, setHeaders] = useState<ManagedSmsHeader[]>([])
  const [templates, setTemplates] = useState<ManagedSmsTemplate[]>([])
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const currentCompanyId = useRef(companyId)
  const tenantEpoch = useRef(0)
  const requestGeneration = useRef(0)

  useLayoutEffect(() => {
    if (currentCompanyId.current === companyId) return
    currentCompanyId.current = companyId
    tenantEpoch.current += 1
    requestGeneration.current += 1
    setBusy(false)
    setLoading(true)
    setStatus(null)
    setProfile(null)
    setHeaders([])
    setTemplates([])
  }, [companyId])

  const [legalName, setLegalName] = useState('')
  const [dltPlatform, setDltPlatform] = useState<ManagedSmsDltPlatform>('OTHER')
  const [peId, setPeId] = useState('')
  const [tmChainReference, setTmChainReference] = useState('')

  const [header, setHeader] = useState('')
  const [headerCategory, setHeaderCategory] = useState<ManagedSmsMessageCategory>('SERVICE')

  const [templateName, setTemplateName] = useState('')
  const [templateHeaderId, setTemplateHeaderId] = useState('')
  const [templateCategory, setTemplateCategory] =
    useState<ManagedSmsMessageCategory>('SERVICE')
  const [dltTemplateId, setDltTemplateId] = useState('')
  const [templateBody, setTemplateBody] = useState('')
  const [templateVariables, setTemplateVariables] = useState('')

  const readyHeaders = useMemo(
    () => headers.filter((item) => item.state === 'ACTIVE' || item.state === 'READY'),
    [headers],
  )

  const load = useCallback(async () => {
    const requestCompanyId = companyId
    if (!isManagedSmsTenantCurrent(requestCompanyId, currentCompanyId.current)) return

    const generation = ++requestGeneration.current
    const isCurrentRequest = () =>
      isManagedSmsTenantCurrent(requestCompanyId, currentCompanyId.current) &&
      generation === requestGeneration.current

    setLoading(true)
    setStatus(null)
    setProfile(null)
    setHeaders([])
    setTemplates([])
    try {
      const [nextStatus, profileResult, headerResult, templateResult] =
        await Promise.allSettled([
          managedSmsApi.status(requestCompanyId),
          managedSmsApi.dltProfile(requestCompanyId),
          managedSmsApi.headers(requestCompanyId),
          managedSmsApi.templates(requestCompanyId),
        ])

      if (!isCurrentRequest()) return
      setStatus(nextStatus.status === 'fulfilled' ? nextStatus.value : null)
      setProfile(profileResult.status === 'fulfilled' ? profileResult.value : null)
      setHeaders(headerResult.status === 'fulfilled' ? headerResult.value : [])
      setTemplates(templateResult.status === 'fulfilled' ? templateResult.value : [])
    } catch (error) {
      if (!isCurrentRequest()) return
      snackbar.error(managedSmsErrorMessage(error, 'Unable to load Managed SMS setup.'))
    } finally {
      if (isCurrentRequest()) {
        setLoading(false)
      }
    }
  }, [companyId, snackbar])

  useEffect(() => {
    void load()
  }, [load])

  useEffect(() => {
    if (!templateHeaderId && readyHeaders[0]) {
      setTemplateHeaderId(readyHeaders[0].id)
      setTemplateCategory(readyHeaders[0].messageCategory)
    }
  }, [readyHeaders, templateHeaderId])

  const withBusy = async (
    action: (isCurrentTenant: () => boolean) => Promise<void>,
  ) => {
    const mutationCompanyId = companyId
    const mutationTenantEpoch = tenantEpoch.current
    const isCurrentTenant = () =>
      isManagedSmsTenantCurrent(mutationCompanyId, currentCompanyId.current) &&
      mutationTenantEpoch === tenantEpoch.current

    if (!isCurrentTenant()) return

    setBusy(true)
    try {
      await action(isCurrentTenant)
      if (!isCurrentTenant()) return
      onChanged()
      await load()
    } catch (error) {
      if (!isCurrentTenant()) return
      snackbar.error(managedSmsErrorMessage(error))
    } finally {
      if (isCurrentTenant()) {
        setBusy(false)
      }
    }
  }

  const createProfile = (event: FormEvent) => {
    event.preventDefault()
    if (!legalName.trim() || !peId.trim()) return
    void withBusy(async (isCurrentTenant) => {
      const created = await managedSmsApi.createDltProfile(companyId, {
        legalName: legalName.trim(),
        dltPlatform,
        peId: peId.trim(),
        tmChainReference: tmChainReference.trim() || null,
        evidence: {},
      })
      if (!isCurrentTenant()) return
      await managedSmsApi.submitDltProfile(companyId, created.id)
      if (!isCurrentTenant()) return
      snackbar.success('DLT profile submitted for verification.')
    })
  }

  const createHeader = (event: FormEvent) => {
    event.preventDefault()
    if (!header.trim()) return
    void withBusy(async (isCurrentTenant) => {
      const created = await managedSmsApi.createHeader(companyId, {
        header: header.trim().toUpperCase(),
        messageCategory: headerCategory,
      })
      if (!isCurrentTenant()) return
      await managedSmsApi.submitHeader(companyId, created.id)
      if (!isCurrentTenant()) return
      setHeader('')
      snackbar.success('Header submitted for verification.')
    })
  }

  const createTemplate = (event: FormEvent) => {
    event.preventDefault()
    if (
      !templateName.trim() ||
      !templateHeaderId ||
      !dltTemplateId.trim() ||
      !templateBody.trim()
    ) {
      return
    }
    void withBusy(async (isCurrentTenant) => {
      const created = await managedSmsApi.createTemplate(companyId, {
        name: templateName.trim(),
        headerIds: [templateHeaderId],
        messageCategory: templateCategory,
        dltTemplateId: dltTemplateId.trim(),
        bodyText: templateBody,
        variables: splitVariables(templateVariables),
        ctaPolicy: { urlVariables: [], callbackVariables: [] },
        consentPolicy: null,
        optOutPolicy: null,
      })
      if (!isCurrentTenant()) return
      if (!created.activeVersion) {
        throw new Error('Template version was not returned')
      }
      await managedSmsApi.submitTemplateVersion(
        companyId,
        created.id,
        created.activeVersion.id,
      )
      if (!isCurrentTenant()) return
      setTemplateName('')
      setDltTemplateId('')
      setTemplateBody('')
      setTemplateVariables('')
      snackbar.success('Template submitted for verification.')
    })
  }

  const createSender = (headerId: string) => {
    void withBusy(async (isCurrentTenant) => {
      await managedSmsApi.createSender(companyId, { headerId })
      if (!isCurrentTenant()) return
      snackbar.success('Managed SMS sender is ready for Broadcast.')
    })
  }

  return (
    <div className="mobile-modal-shell fixed inset-0 z-50 flex items-center justify-center bg-slate-950/35 p-4">
      <div className="mobile-sheet flex max-h-[92dvh] w-full max-w-5xl flex-col overflow-hidden rounded-2xl bg-white shadow-xl">
        <div className="flex items-start justify-between gap-4 border-b border-slate-100 p-5">
          <div>
            <div className="flex flex-wrap items-center gap-2">
              <p className="text-base font-bold text-slate-950">UnifyDesk Managed SMS</p>
              {status && <ManagedSmsStatusBadge state={status.state} />}
            </div>
            <p className="mt-1 max-w-2xl text-xs leading-5 text-slate-500">
              Add your DLT-approved business identity, Headers and exact content templates. UnifyDesk
              handles the sending infrastructure; you never enter provider credentials here.
            </p>
          </div>
          <button type="button" className="admin-back-button" onClick={onClose} aria-label="Close">
            <FiX aria-hidden="true" />
          </button>
        </div>

        <div className="overflow-y-auto p-5">
          {loading && !status ? (
            <div className="py-4"><CardSkeleton rows={6} /></div>
          ) : (
            <div className="grid gap-5">
              {status && (
                <div className="rounded-xl border border-indigo-100 bg-indigo-50/60 p-4">
                  <p className="text-sm font-bold text-slate-900">
                    {status.nextAction || managedSmsStateDescription(status.state)}
                  </p>
                  <p className="mt-1 text-[11px] leading-5 text-slate-600">
                    Your business remains the DLT Principal Entity. Only submit identifiers that are
                    already approved for your company.
                  </p>
                </div>
              )}

              <section className="rounded-2xl border border-slate-200 p-5">
                <div className="flex flex-wrap items-center justify-between gap-3">
                  <div>
                    <p className="text-sm font-bold text-slate-950">1. Business & DLT registration</p>
                    <p className="mt-1 text-xs leading-5 text-slate-500">
                      PE ID is the Principal Entity identifier assigned to your business by your DLT
                      platform. Do not enter your DLT password.
                    </p>
                  </div>
                  {profile?.id && (
                    <button
                      type="button"
                      className="secondary-button inline-flex items-center gap-2"
                      disabled={busy}
                      onClick={() =>
                        void withBusy(async () => {
                          await managedSmsApi.refreshDltProfile(companyId, profile.id)
                        })
                      }
                    >
                      <FiRefreshCw aria-hidden="true" />
                      Refresh verification
                    </button>
                  )}
                </div>

                {profile?.id ? (
                  <div className="mt-4 grid gap-3 sm:grid-cols-3">
                    <Info label="Business" value={profile.legalName || 'Submitted'} />
                    <Info label="PE ID" value={profile.peIdMasked || 'Submitted'} />
                    <Info label="PE–TM chain" value={profile.chainState || 'Pending verification'} />
                  </div>
                ) : (
                  <form className="mt-4 grid gap-3 sm:grid-cols-2" onSubmit={createProfile}>
                    <Field label="Registered legal business name">
                      <input
                        className="form-input mt-1.5"
                        value={legalName}
                        onChange={(event) => setLegalName(event.target.value)}
                        maxLength={200}
                        required
                      />
                    </Field>
                    <Field label="DLT platform">
                      <select
                        className="form-input mt-1.5"
                        value={dltPlatform}
                        onChange={(event) =>
                          setDltPlatform(event.target.value as ManagedSmsDltPlatform)
                        }
                      >
                        {dltPlatforms.map((item) => (
                          <option key={item} value={item}>
                            {item}
                          </option>
                        ))}
                      </select>
                    </Field>
                    <Field label="PE / Entity ID">
                      <input
                        className="form-input mt-1.5"
                        value={peId}
                        onChange={(event) => setPeId(event.target.value)}
                        maxLength={100}
                        required
                      />
                    </Field>
                    <Field label="PE–Telemarketer chain reference (optional)">
                      <input
                        className="form-input mt-1.5"
                        value={tmChainReference}
                        onChange={(event) => setTmChainReference(event.target.value)}
                        maxLength={200}
                      />
                    </Field>
                    {canModify && (
                      <button
                        type="submit"
                        className="primary-button sm:col-span-2 sm:justify-self-end"
                        disabled={busy || !legalName.trim() || !peId.trim()}
                      >
                        Submit DLT profile
                      </button>
                    )}
                  </form>
                )}
              </section>

              <section className="rounded-2xl border border-slate-200 p-5">
                <div>
                  <p className="text-sm font-bold text-slate-950">2. Approved Headers</p>
                  <p className="mt-1 text-xs leading-5 text-slate-500">
                    A Header is the sender ID approved on DLT for your business. Add the exact value
                    and matching message category.
                  </p>
                </div>

                {headers.length > 0 && (
                  <div className="mt-4 grid gap-2">
                    {headers.map((item) => (
                      <div
                        key={item.id}
                        className="flex flex-col gap-3 rounded-xl bg-slate-50 px-4 py-3 sm:flex-row sm:items-center sm:justify-between"
                      >
                        <div>
                          <p className="text-sm font-bold text-slate-900">{item.header}</p>
                          <p className="mt-0.5 text-[11px] text-slate-500">
                            {item.messageCategory.toLowerCase()} · {item.state.toLowerCase().replaceAll('_', ' ')}
                            {item.actionRequired ? ` · ${item.actionRequired}` : ''}
                          </p>
                        </div>
                        <div className="flex flex-wrap gap-2">
                          {canModify && (item.state === 'ACTIVE' || item.state === 'READY') && (
                            <button
                              type="button"
                              className="secondary-button"
                              disabled={busy}
                              onClick={() => createSender(item.id)}
                            >
                              <FiCheckCircle aria-hidden="true" />
                              Use in Broadcast
                            </button>
                          )}
                          {canModify && item.state !== 'ACTIVE' && item.state !== 'READY' && (
                            <button
                              type="button"
                              className="secondary-button"
                              disabled={busy}
                              onClick={() =>
                                void withBusy(async () => {
                                  await managedSmsApi.refreshHeader(companyId, item.id)
                                })
                              }
                            >
                              <FiRefreshCw aria-hidden="true" />
                              Refresh
                            </button>
                          )}
                        </div>
                      </div>
                    ))}
                  </div>
                )}

                {canModify && (
                  <form className="mt-4 grid gap-3 sm:grid-cols-[1fr_1fr_auto]" onSubmit={createHeader}>
                    <Field label="Approved Header">
                      <input
                        className="form-input mt-1.5 uppercase"
                        value={header}
                        onChange={(event) => setHeader(event.target.value)}
                        maxLength={20}
                        placeholder="ACMEIN"
                        required
                      />
                    </Field>
                    <Field label="Message category">
                      <select
                        className="form-input mt-1.5"
                        value={headerCategory}
                        onChange={(event) =>
                          setHeaderCategory(event.target.value as ManagedSmsMessageCategory)
                        }
                      >
                        {categories.map((item) => (
                          <option key={item} value={item}>
                            {item}
                          </option>
                        ))}
                      </select>
                    </Field>
                    <button
                      type="submit"
                      className="primary-button self-end"
                      disabled={busy || !header.trim()}
                    >
                      Add Header
                    </button>
                  </form>
                )}
              </section>

              <section className="rounded-2xl border border-slate-200 p-5">
                <div>
                  <p className="text-sm font-bold text-slate-950">3. Approved content templates</p>
                  <p className="mt-1 text-xs leading-5 text-slate-500">
                    Copy the exact DLT-approved content. Managed broadcasts are locked to approved
                    templates; arbitrary SMS text cannot be sent. If your approved template contains
                    a URL or callback CTA, keep it exactly within the approved DLT template policy.
                  </p>
                </div>

                {templates.length > 0 && (
                  <div className="mt-4 grid gap-2">
                    {templates.map((item) => (
                      <div key={item.id} className="rounded-xl bg-slate-50 px-4 py-3">
                        <div className="flex flex-wrap items-center justify-between gap-2">
                          <p className="text-sm font-bold text-slate-900">{item.name}</p>
                          <span className="text-[10px] font-bold uppercase text-slate-500">
                            {item.state.replaceAll('_', ' ')}
                          </span>
                        </div>
                        <p className="mt-1 whitespace-pre-wrap text-[11px] leading-5 text-slate-600">
                          {item.activeVersion?.bodyText || 'Template details pending'}
                        </p>
                        {item.activeVersion && item.state !== 'READY' && canModify && (
                          <button
                            type="button"
                            className="secondary-button mt-3 inline-flex items-center gap-2"
                            disabled={busy}
                            onClick={() =>
                              void withBusy(async () => {
                                await managedSmsApi.refreshTemplateVersion(
                                  companyId,
                                  item.id,
                                  item.activeVersion!.id,
                                )
                              })
                            }
                          >
                            <FiRefreshCw aria-hidden="true" />
                            Refresh verification
                          </button>
                        )}
                      </div>
                    ))}
                  </div>
                )}

                {canModify && (
                  <form className="mt-4 grid gap-3 sm:grid-cols-2" onSubmit={createTemplate}>
                    <Field label="Template name">
                      <input
                        className="form-input mt-1.5"
                        value={templateName}
                        onChange={(event) => setTemplateName(event.target.value)}
                        maxLength={160}
                        required
                      />
                    </Field>
                    <Field label="Approved Header">
                      <select
                        className="form-input mt-1.5"
                        value={templateHeaderId}
                        onChange={(event) => {
                          const nextId = event.target.value
                          setTemplateHeaderId(nextId)
                          const nextHeader = readyHeaders.find((item) => item.id === nextId)
                          if (nextHeader) setTemplateCategory(nextHeader.messageCategory)
                        }}
                        required
                      >
                        <option value="">Choose ready Header</option>
                        {readyHeaders.map((item) => (
                          <option key={item.id} value={item.id}>
                            {item.header}
                          </option>
                        ))}
                      </select>
                    </Field>
                    <Field label="DLT Template ID">
                      <input
                        className="form-input mt-1.5"
                        value={dltTemplateId}
                        onChange={(event) => setDltTemplateId(event.target.value)}
                        maxLength={100}
                        required
                      />
                    </Field>
                    <Field label="Message category">
                      <select
                        className="form-input mt-1.5"
                        value={templateCategory}
                        onChange={(event) =>
                          setTemplateCategory(event.target.value as ManagedSmsMessageCategory)
                        }
                      >
                        {categories.map((item) => (
                          <option key={item} value={item}>
                            {item}
                          </option>
                        ))}
                      </select>
                    </Field>
                    <Field label="Declared variables (comma separated)">
                      <input
                        className="form-input mt-1.5"
                        value={templateVariables}
                        onChange={(event) => setTemplateVariables(event.target.value)}
                        placeholder="order_id, tracking_id"
                      />
                    </Field>
                    <div />
                    <div className="sm:col-span-2">
                      <Field label="Exact DLT-approved message body">
                        <textarea
                          className="form-input mt-1.5 min-h-36"
                          value={templateBody}
                          onChange={(event) => setTemplateBody(event.target.value)}
                          maxLength={5000}
                          required
                        />
                      </Field>
                    </div>
                    <button
                      type="submit"
                      className="primary-button sm:col-span-2 sm:justify-self-end"
                      disabled={
                        busy ||
                        !templateName.trim() ||
                        !templateHeaderId ||
                        !dltTemplateId.trim() ||
                        !templateBody.trim()
                      }
                    >
                      <FiSend aria-hidden="true" />
                      Submit template
                    </button>
                  </form>
                )}
              </section>
            </div>
          )}
        </div>
      </div>
    </div>
  )
}

function Field({ label, children }: { label: string; children: ReactNode }) {
  return (
    <label className="block text-xs font-semibold text-slate-700">
      {label}
      {children}
    </label>
  )
}

function Info({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-xl bg-slate-50 px-3 py-3">
      <p className="text-[10px] font-bold uppercase tracking-wide text-slate-400">{label}</p>
      <p className="mt-1 text-xs font-semibold text-slate-800">{value}</p>
    </div>
  )
}
