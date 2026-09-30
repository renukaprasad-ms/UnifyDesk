import { useEffect, useRef, useState } from 'react'
import { useParams } from 'react-router'
import ContactsNav from '../../components/contacts/ContactsNav'
import {
  buildImportCommitInput,
  createIdempotencyKey,
} from '../../features/contacts/contactModel'
import { contactsApi } from '../../features/contacts/contactsApi'
import type {
  ConsentImportMapping,
  ContactChannel,
  ContactImport,
  CustomFieldDefinition,
  ImportFieldMapping,
  ImportFieldTarget,
  ImportPreview,
  ListSummary,
} from '../../features/contacts/types'
import { apiErrorMessage } from '../../lib/api/apiClient'
import { useAppSelector } from '../../store/hooks'
import { selectSelectedCompanyId } from '../../store/workspace/workspaceSlice'

const channels: ContactChannel[] = ['EMAIL', 'SMS', 'WHATSAPP']

const targetKey = (target: ImportFieldTarget) => {
  if (target.type === 'IGNORE') return 'IGNORE'
  if (target.type === 'BUILTIN') return 'BUILTIN:' + target.field
  if (target.type === 'IDENTITY') return 'IDENTITY:' + target.identityType
  if (target.type === 'TAG_NAMES') return 'TAG_NAMES'
  return 'CUSTOM_FIELD:' + target.fieldId
}

const parseTarget = (value: string): ImportFieldTarget => {
  if (value === 'IGNORE') return { type: 'IGNORE' }
  if (value === 'TAG_NAMES') return { type: 'TAG_NAMES' }
  if (value.startsWith('IDENTITY:')) {
    return {
      type: 'IDENTITY',
      identityType: value.slice('IDENTITY:'.length) as 'EMAIL' | 'PHONE',
    }
  }
  if (value.startsWith('CUSTOM_FIELD:')) {
    return { type: 'CUSTOM_FIELD', fieldId: value.slice('CUSTOM_FIELD:'.length) }
  }
  return {
    type: 'BUILTIN',
    field: value.slice('BUILTIN:'.length) as
      | 'displayName'
      | 'firstName'
      | 'lastName'
      | 'organization'
      | 'notes',
  }
}

export default function ImportDetailPage() {
  const { importId = '' } = useParams()
  const companyId = useAppSelector(selectSelectedCompanyId)
  const [resource, setResource] = useState<ContactImport | null>(null)
  const [preview, setPreview] = useState<ImportPreview | null>(null)
  const [mappings, setMappings] = useState<ImportFieldMapping[]>([])
  const [consentMappings, setConsentMappings] = useState<ConsentImportMapping[]>([])
  const [customFields, setCustomFields] = useState<CustomFieldDefinition[]>([])
  const [strategy, setStrategy] =
    useState<'SKIP' | 'MERGE_EXACT' | 'FAIL'>('SKIP')
  const [targetListId, setTargetListId] = useState('')
  const [lists, setLists] = useState<ListSummary[]>([])
  const [error, setError] = useState('')
  const [committing, setCommitting] = useState(false)
  const [downloadingErrors, setDownloadingErrors] = useState(false)
  const [pollingPaused, setPollingPaused] = useState(false)
  const pollAttempts = useRef(0)

  useEffect(() => {
    if (!companyId) return

    Promise.all([
      contactsApi.getImport(companyId, importId),
      contactsApi.lists(companyId),
      contactsApi.customFields(companyId),
    ])
      .then(async ([nextResource, nextLists, nextFields]) => {
        setResource(nextResource)
        setLists(nextLists.filter((list) => !list.archived))
        setCustomFields(nextFields.filter((field) => field.status === 'ACTIVE'))

        if (['UPLOADED', 'DRAFT_UPLOAD', 'PREVIEWED'].includes(nextResource.status)) {
          const nextPreview = await contactsApi.previewImport(companyId, importId)
          setPreview(nextPreview)
          setMappings(
            nextPreview.headers.map((column) => ({
              column,
              target: { type: 'IGNORE' },
            })),
          )
        }
      })
      .catch((loadError) =>
        setError(apiErrorMessage(loadError, 'Unable to load import.')),
      )
  }, [companyId, importId])

  useEffect(() => {
    if (
      !companyId ||
      !resource ||
      !['QUEUED', 'RUNNING'].includes(resource.status)
    ) {
      return
    }
    pollAttempts.current = 0
    setPollingPaused(false)
    const timer = window.setInterval(() => {
      if (pollAttempts.current >= 120) {
        window.clearInterval(timer)
        setPollingPaused(true)
        return
      }
      pollAttempts.current += 1
      contactsApi
        .getImport(companyId, importId)
        .then(setResource)
        .catch(() => undefined)
    }, 3000)
    return () => window.clearInterval(timer)
  }, [companyId, importId, resource?.status])

  const setConsentEnabled = (channel: ContactChannel, enabled: boolean) => {
    setConsentMappings((current) => {
      if (!enabled) return current.filter((mapping) => mapping.channel !== channel)
      if (current.some((mapping) => mapping.channel === channel)) return current
      return [
        ...current,
        {
          channel,
          stateColumn: '',
          source: 'IMPORT',
          evidenceColumn: null,
          occurredAtColumn: null,
        },
      ]
    })
  }

  const updateConsent = (
    channel: ContactChannel,
    patch: Partial<ConsentImportMapping>,
  ) =>
    setConsentMappings((current) =>
      current.map((mapping) =>
        mapping.channel === channel ? { ...mapping, ...patch } : mapping,
      ),
    )

  const commit = async () => {
    if (!companyId || !preview) return
    const mappedFieldCount = mappings.filter(
      (mapping) => mapping.target.type !== 'IGNORE',
    ).length
    if (mappedFieldCount === 0) {
      setError('Map at least one Contact field or identity before starting the import.')
      return
    }

    const invalidConsent = consentMappings.some((mapping) => !mapping.stateColumn)
    if (invalidConsent) {
      setError('Every enabled consent mapping needs a state column.')
      return
    }

    setCommitting(true)
    setError('')
    try {
      await contactsApi.commitImport(
        companyId,
        importId,
        buildImportCommitInput({
          mappings,
          duplicateStrategy: strategy,
          consentMappings,
          targetListId: targetListId || null,
          idempotencyKey: createIdempotencyKey(),
        }),
      )
      setResource(await contactsApi.getImport(companyId, importId))
    } catch (commitError) {
      setError(apiErrorMessage(commitError, 'Unable to commit import.'))
    } finally {
      setCommitting(false)
    }
  }

  const downloadErrors = async () => {
    if (!companyId) return
    setDownloadingErrors(true)
    setError('')
    try {
      const result = await contactsApi.importErrors(companyId, importId)
      if (!result.ready || !result.downloadUrl) {
        setError('The import error report is not ready yet.')
        return
      }
      window.open(result.downloadUrl, '_blank', 'noopener,noreferrer')
    } catch (downloadError) {
      setError(apiErrorMessage(downloadError, 'Unable to download import errors.'))
    } finally {
      setDownloadingErrors(false)
    }
  }

  const terminal =
    resource &&
    ['COMPLETED', 'FAILED', 'VALIDATION_FAILED', 'EXPIRED'].includes(resource.status)
  const processing =
    resource && ['QUEUED', 'RUNNING'].includes(resource.status)

  return (
    <section className="px-4 py-6 sm:px-6 lg:px-8">
      <div className="mx-auto max-w-6xl">
        <ContactsNav />
        <h1 className="text-3xl font-bold text-slate-950">CSV import</h1>
        <p className="mt-2 text-sm text-slate-500">
          Preview, map fields and consent evidence, review, then process asynchronously.
        </p>

        {error && (
          <div
            role="alert"
            className="mt-4 rounded-xl border border-rose-200 bg-rose-50 p-3 text-sm text-rose-700"
          >
            {error}
          </div>
        )}

        {(processing || terminal) && resource ? (
          <section className="panel mt-6 p-6" aria-live="polite">
            <div className="flex flex-wrap items-center justify-between gap-3">
              <div>
                <h2 className="font-bold text-slate-950">
                  Processing status: {resource.status}
                </h2>
                <p className="mt-2 text-sm text-slate-500">
                  Processed {resource.job?.processedCount ?? resource.totalRows ?? 0} · Success{' '}
                  {resource.successRows ?? resource.job?.successCount ?? 0} · Errors{' '}
                  {resource.errorRows ?? resource.job?.errorCount ?? 0}
                </p>
              </div>
              {(resource.errorRows ?? resource.job?.errorCount ?? 0) > 0 && (
                <button
                  className="secondary-button"
                  disabled={downloadingErrors}
                  onClick={() => void downloadErrors()}
                >
                  {downloadingErrors ? 'Authorizing…' : 'Download error report'}
                </button>
              )}
            </div>
            <div
              className="upload-progress mt-4"
              role="progressbar"
              aria-label="Import progress"
              aria-valuemin={0}
              aria-valuemax={100}
              aria-valuenow={resource.job?.progressPercent ?? (terminal ? 100 : 0)}
            >
              <span
                style={{
                  width:
                    String(resource.job?.progressPercent ?? (terminal ? 100 : 0)) +
                    '%',
                }}
              />
            </div>
            <p className="mt-2 text-xs text-slate-500">
              Error downloads are requested from the backend only when you click the button;
              signed URLs are not retained in React state.
            </p>
            {pollingPaused && processing && (
              <p className="mt-2 text-xs font-medium text-amber-700">
                Automatic status refresh paused after the bounded polling window. Reopen this
                import to resume status polling.
              </p>
            )}
          </section>
        ) : preview ? (
          <>
            <section className="panel mt-6 p-5">
              <h2 className="font-bold text-slate-950">1. Preview</h2>
              <p className="mt-1 text-xs text-slate-500">
                This is a bounded server-generated sample, not the full CSV.
              </p>
              <div className="mt-3 overflow-x-auto">
                <table className="min-w-full text-xs">
                  <thead>
                    <tr>
                      {preview.headers.map((header) => (
                        <th key={header} className="p-2 text-left">
                          {header}
                        </th>
                      ))}
                    </tr>
                  </thead>
                  <tbody>
                    {preview.sampleRows.slice(0, 10).map((row, rowIndex) => (
                      <tr key={rowIndex} className="border-t border-slate-100">
                        {row.map((cell, cellIndex) => (
                          <td
                            key={cellIndex}
                            className="max-w-52 truncate p-2"
                            title={cell}
                          >
                            {cell}
                          </td>
                        ))}
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </section>

            <section className="panel mt-4 p-5">
              <h2 className="font-bold text-slate-950">2. Field mapping</h2>
              <div className="mt-3 grid gap-3 sm:grid-cols-2">
                {mappings.map((mapping, index) => (
                  <label
                    key={mapping.column}
                    className="text-xs font-semibold text-slate-700"
                  >
                    {mapping.column}
                    <select
                      className="form-input mt-1"
                      value={targetKey(mapping.target)}
                      onChange={(event) =>
                        setMappings((current) =>
                          current.map((item, itemIndex) =>
                            itemIndex === index
                              ? { ...item, target: parseTarget(event.target.value) }
                              : item,
                          ),
                        )
                      }
                    >
                      <option value="IGNORE">Ignore</option>
                      <option value="BUILTIN:displayName">Display name</option>
                      <option value="BUILTIN:firstName">First name</option>
                      <option value="BUILTIN:lastName">Last name</option>
                      <option value="BUILTIN:organization">Organization</option>
                      <option value="BUILTIN:notes">Notes</option>
                      <option value="IDENTITY:EMAIL">Email identity</option>
                      <option value="IDENTITY:PHONE">Phone identity</option>
                      <option value="TAG_NAMES">Tag names</option>
                      {customFields.map((field) => (
                        <option key={field.id} value={'CUSTOM_FIELD:' + field.id}>
                          Custom field · {field.label}
                        </option>
                      ))}
                    </select>
                  </label>
                ))}
              </div>
            </section>

            <section className="panel mt-4 grid gap-4 p-5 md:grid-cols-2">
              <label className="text-xs font-semibold text-slate-700">
                3. Duplicate behavior
                <select
                  className="form-input mt-1"
                  value={strategy}
                  onChange={(event) =>
                    setStrategy(event.target.value as typeof strategy)
                  }
                >
                  <option value="SKIP">Skip existing exact identity</option>
                  <option value="MERGE_EXACT">Merge exact match</option>
                  <option value="FAIL">Fail duplicate row</option>
                </select>
              </label>
              <label className="text-xs font-semibold text-slate-700">
                4. Target List (optional)
                <select
                  className="form-input mt-1"
                  value={targetListId}
                  onChange={(event) => setTargetListId(event.target.value)}
                >
                  <option value="">No target List</option>
                  {lists.map((list) => (
                    <option key={list.id} value={list.id}>
                      {list.name}
                    </option>
                  ))}
                </select>
              </label>
            </section>

            <section className="panel mt-4 p-5">
              <h2 className="font-bold text-slate-950">5. Consent evidence mapping</h2>
              <div className="mt-3 rounded-xl border border-amber-200 bg-amber-50 p-4 text-sm text-amber-800">
                <strong>Importing a Contact never grants marketing consent.</strong> Enable a
                channel only when the CSV contains explicit preference/evidence data that you are
                authorized to record.
              </div>
              <div className="mt-4 space-y-4">
                {channels.map((channel) => {
                  const mapping = consentMappings.find(
                    (item) => item.channel === channel,
                  )
                  return (
                    <fieldset
                      key={channel}
                      className="rounded-xl border border-slate-200 p-4"
                    >
                      <label className="flex items-center gap-2 text-sm font-semibold">
                        <input
                          type="checkbox"
                          checked={Boolean(mapping)}
                          onChange={(event) =>
                            setConsentEnabled(channel, event.target.checked)
                          }
                        />
                        Map {channel === 'WHATSAPP' ? 'WhatsApp' : channel} marketing
                        preference evidence
                      </label>
                      {mapping && (
                        <div className="mt-3 grid gap-3 md:grid-cols-3">
                          <label className="text-xs font-semibold text-slate-700">
                            Preference state column
                            <select
                              className="form-input mt-1"
                              value={mapping.stateColumn}
                              onChange={(event) =>
                                updateConsent(channel, {
                                  stateColumn: event.target.value,
                                })
                              }
                            >
                              <option value="">Choose state column…</option>
                              {preview.headers.map((header) => (
                                <option key={header} value={header}>
                                  {header}
                                </option>
                              ))}
                            </select>
                          </label>
                          <label className="text-xs font-semibold text-slate-700">
                            Evidence/source reference column
                            <select
                              className="form-input mt-1"
                              value={mapping.evidenceColumn ?? ''}
                              onChange={(event) =>
                                updateConsent(channel, {
                                  evidenceColumn: event.target.value || null,
                                })
                              }
                            >
                              <option value="">Not mapped</option>
                              {preview.headers.map((header) => (
                                <option key={header} value={header}>
                                  {header}
                                </option>
                              ))}
                            </select>
                          </label>
                          <label className="text-xs font-semibold text-slate-700">
                            Occurred-at column
                            <select
                              className="form-input mt-1"
                              value={mapping.occurredAtColumn ?? ''}
                              onChange={(event) =>
                                updateConsent(channel, {
                                  occurredAtColumn: event.target.value || null,
                                })
                              }
                            >
                              <option value="">Not mapped</option>
                              {preview.headers.map((header) => (
                                <option key={header} value={header}>
                                  {header}
                                </option>
                              ))}
                            </select>
                          </label>
                        </div>
                      )}
                    </fieldset>
                  )
                })}
              </div>
            </section>

            <section className="panel mt-4 p-5">
              <h2 className="font-bold text-slate-950">6. Review & start</h2>
              <dl className="mt-3 grid gap-3 text-sm sm:grid-cols-3">
                <div>
                  <dt className="text-slate-500">Mapped fields</dt>
                  <dd className="font-bold">
                    {mappings.filter((mapping) => mapping.target.type !== 'IGNORE').length}
                  </dd>
                </div>
                <div>
                  <dt className="text-slate-500">Consent channels</dt>
                  <dd className="font-bold">{consentMappings.length}</dd>
                </div>
                <div>
                  <dt className="text-slate-500">Target List</dt>
                  <dd className="font-bold">
                    {lists.find((list) => list.id === targetListId)?.name || 'None'}
                  </dd>
                </div>
              </dl>
              <p className="mt-3 text-sm text-slate-500">
                After commit, processing is asynchronous and this import can be reopened by ID.
              </p>
              <button
                className="primary-button mt-4"
                disabled={committing}
                onClick={() => void commit()}
              >
                {committing ? 'Starting…' : 'Start import'}
              </button>
            </section>
          </>
        ) : (
          <div className="panel mt-6 p-6 text-sm text-slate-500" role="status">
            Preparing bounded preview…
          </div>
        )}
      </div>
    </section>
  )
}
