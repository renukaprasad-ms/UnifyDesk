import { useEffect, useRef, useState } from 'react'
import { Link, useParams } from 'react-router'
import ContactsNav from '../../components/contacts/ContactsNav'
import {
  buildImportCommitInput,
  buildSuggestedConsentMappings,
  buildSuggestedImportMappings,
  createIdempotencyKey,
} from '../../features/contacts/contactModel'
import { contactsApi } from '../../features/contacts/contactsApi'
import type {
  ConsentImportDeclaration,
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

const consentEvidenceOptions = [
  { value: 'Website form', label: 'Website form' },
  { value: 'Existing customer list', label: 'Existing customer list' },
  { value: 'Previous marketing platform', label: 'Previous marketing platform' },
  { value: 'Offline consent', label: 'Offline / in-person consent' },
  { value: 'Other documented consent', label: 'Other documented consent' },
] as const

const channelLabel = (channel: ContactChannel) =>
  channel === 'WHATSAPP' ? 'WhatsApp' : channel

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
      'displayName' | 'firstName' | 'lastName' | 'organization' | 'notes',
  }
}

const strategyLabel = (strategy: 'SKIP' | 'MERGE_EXACT' | 'FAIL') => {
  if (strategy === 'MERGE_EXACT') return 'Update exact matches'
  if (strategy === 'FAIL') return 'Stop duplicate rows'
  return 'Keep existing contacts'
}

const statusPresentation = (resource: ContactImport) => {
  const imported = resource.successRows ?? resource.job?.successCount ?? 0
  const failed = resource.errorRows ?? resource.job?.errorCount ?? 0

  if (['QUEUED', 'RUNNING'].includes(resource.status)) {
    return {
      title: 'Importing contacts…',
      description:
        'Your contacts are being processed. You can leave this page and come back later.',
      tone: 'neutral',
    }
  }
  if (resource.status === 'COMPLETED' && failed === 0) {
    return {
      title: 'Import complete',
      description:
        imported > 0
          ? `${imported.toLocaleString()} contacts were imported successfully.`
          : 'The import completed successfully.',
      tone: 'success',
    }
  }
  if (resource.status === 'COMPLETED' && imported > 0 && failed > 0) {
    return {
      title: 'Import finished with some issues',
      description: `${imported.toLocaleString()} contacts were imported and ${failed.toLocaleString()} rows need attention.`,
      tone: 'warning',
    }
  }
  if (resource.status === 'COMPLETED' && failed > 0) {
    return {
      title: "We couldn't add any contacts",
      description:
        'Download the error report to see what needs to be corrected, then try the file again.',
      tone: 'error',
    }
  }
  if (resource.status === 'EXPIRED') {
    return {
      title: 'Import expired',
      description: 'This import is no longer available. Start a new import with the CSV file.',
      tone: 'warning',
    }
  }
  return {
    title: 'Import failed',
    description: 'The import could not be completed. Review the error details and try again.',
    tone: 'error',
  }
}

const resultToneClass = (tone: string) => {
  if (tone === 'success') return 'border-emerald-200 bg-emerald-50'
  if (tone === 'warning') return 'border-amber-200 bg-amber-50'
  if (tone === 'error') return 'border-rose-200 bg-rose-50'
  return 'border-slate-200 bg-white'
}

export default function ImportDetailPage() {
  const { importId = '' } = useParams()
  const companyId = useAppSelector(selectSelectedCompanyId)
  const [resource, setResource] = useState<ContactImport | null>(null)
  const [preview, setPreview] = useState<ImportPreview | null>(null)
  const [mappings, setMappings] = useState<ImportFieldMapping[]>([])
  const [consentMappings, setConsentMappings] = useState<ConsentImportMapping[]>([])
  const [consentMode, setConsentMode] = useState<'NONE' | 'SIMPLE' | 'CSV'>('NONE')
  const [simpleConsentChannels, setSimpleConsentChannels] = useState<ContactChannel[]>([])
  const [simpleConsentEvidence, setSimpleConsentEvidence] = useState('Website form')
  const [simpleConsentDate, setSimpleConsentDate] = useState('')
  const [simpleConsentConfirmed, setSimpleConsentConfirmed] = useState(false)
  const [customFields, setCustomFields] = useState<CustomFieldDefinition[]>([])
  const [strategy, setStrategy] = useState<'SKIP' | 'MERGE_EXACT' | 'FAIL'>('SKIP')
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
        setLists(nextLists.filter((list) => list.status !== 'ARCHIVED'))
        setCustomFields(nextFields.filter((field) => field.status === 'ACTIVE'))

        if (['UPLOADED', 'DRAFT_UPLOAD', 'PREVIEWED'].includes(nextResource.status)) {
          const nextPreview = await contactsApi.previewImport(companyId, importId)
          setPreview(nextPreview)
          setMappings(buildSuggestedImportMappings(nextPreview.headers))
          const suggestedConsent = buildSuggestedConsentMappings(nextPreview.headers)
          setConsentMappings(suggestedConsent)
          setConsentMode(suggestedConsent.length > 0 ? 'CSV' : 'NONE')
        }
      })
      .catch((loadError) => setError(apiErrorMessage(loadError, 'Unable to load import.')))
  }, [companyId, importId])

  useEffect(() => {
    if (!companyId || !resource || !['QUEUED', 'RUNNING'].includes(resource.status)) {
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

  const updateMapping = (index: number, target: ImportFieldTarget) => {
    const nextKey = targetKey(target)
    setMappings((current) =>
      current.map((item, itemIndex) => {
        if (itemIndex === index) return { ...item, target }
        if (nextKey !== 'IGNORE' && targetKey(item.target) === nextKey) {
          return { ...item, target: { type: 'IGNORE' } }
        }
        return item
      }),
    )
  }

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

  const updateConsent = (channel: ContactChannel, patch: Partial<ConsentImportMapping>) =>
    setConsentMappings((current) =>
      current.map((mapping) => (mapping.channel === channel ? { ...mapping, ...patch } : mapping)),
    )

  const toggleSimpleConsentChannel = (channel: ContactChannel) =>
    setSimpleConsentChannels((current) =>
      current.includes(channel)
        ? current.filter((item) => item !== channel)
        : [...current, channel],
    )

  const simpleConsentDeclarations = (): ConsentImportDeclaration[] =>
    simpleConsentChannels.map((channel) => ({
      channel,
      state: 'OPTED_IN',
      source: 'IMPORT',
      evidence: simpleConsentEvidence,
      occurredAt: simpleConsentDate
        ? new Date(simpleConsentDate + 'T00:00:00.000Z').toISOString()
        : null,
      confirmed: simpleConsentConfirmed,
    }))

  const commit = async () => {
    if (!companyId || !preview) return
    const mappedFieldCount = mappings.filter((mapping) => mapping.target.type !== 'IGNORE').length
    if (mappedFieldCount === 0) {
      setError('Choose at least one column to import.')
      return
    }

    if (consentMode === 'CSV') {
      const invalidConsent = consentMappings.some((mapping) => !mapping.stateColumn)
      if (invalidConsent) {
        setError('Choose a preference column for every enabled consent channel.')
        return
      }
    }

    if (consentMode === 'SIMPLE') {
      if (simpleConsentChannels.length === 0) {
        setError('Choose at least one marketing channel these contacts consented to.')
        return
      }
      if (!simpleConsentEvidence.trim()) {
        setError('Choose how marketing permission was collected.')
        return
      }
      if (!simpleConsentConfirmed) {
        setError('Confirm that these contacts gave permission for the selected channels.')
        return
      }
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
          consentMappings: consentMode === 'CSV' ? consentMappings : [],
          consentDeclarations: consentMode === 'SIMPLE' ? simpleConsentDeclarations() : [],
          targetListId: targetListId || null,
          idempotencyKey: createIdempotencyKey(),
        }),
      )
      setResource(await contactsApi.getImport(companyId, importId))
    } catch (commitError) {
      setError(apiErrorMessage(commitError, 'Unable to start the import.'))
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
        setError('The error report is still being prepared. Please try again shortly.')
        return
      }
      window.open(result.downloadUrl, '_blank', 'noopener,noreferrer')
    } catch (downloadError) {
      setError(apiErrorMessage(downloadError, 'Unable to download the error report.'))
    } finally {
      setDownloadingErrors(false)
    }
  }

  const terminal =
    resource && ['COMPLETED', 'FAILED', 'VALIDATION_FAILED', 'EXPIRED'].includes(resource.status)
  const processing = resource && ['QUEUED', 'RUNNING'].includes(resource.status)
  const mappedCount = mappings.filter((mapping) => mapping.target.type !== 'IGNORE').length
  const ignoredCount = Math.max(mappings.length - mappedCount, 0)

  return (
    <section className="px-4 py-6 sm:px-6 lg:px-8">
      <div className="mx-auto max-w-5xl">
        <ContactsNav />
        <p className="eyebrow">Contacts</p>
        <h1 className="mt-2 text-3xl font-bold text-slate-950">Import contacts</h1>
        <p className="mt-2 max-w-2xl text-sm text-slate-500">
          Check how your CSV columns match UnifyDesk, choose where the contacts should go, then
          start the import.
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
          (() => {
            const presentation = statusPresentation(resource)
            const processed = resource.job?.processedCount ?? resource.totalRows ?? 0
            const imported = resource.successRows ?? resource.job?.successCount ?? 0
            const failed = resource.errorRows ?? resource.job?.errorCount ?? 0
            return (
              <section
                className={'mt-6 rounded-2xl border p-6 ' + resultToneClass(presentation.tone)}
                aria-live="polite"
              >
                <div className="flex flex-wrap items-start justify-between gap-4">
                  <div>
                    <h2 className="text-xl font-bold text-slate-950">{presentation.title}</h2>
                    <p className="mt-2 max-w-2xl text-sm text-slate-600">
                      {presentation.description}
                    </p>
                  </div>
                  <div className="flex flex-wrap gap-2">
                    {failed > 0 && (
                      <button
                        className="secondary-button"
                        disabled={downloadingErrors}
                        onClick={() => void downloadErrors()}
                      >
                        {downloadingErrors ? 'Preparing…' : 'Download error report'}
                      </button>
                    )}
                    <Link className="secondary-button" to="/contacts/imports">
                      Import another file
                    </Link>
                  </div>
                </div>

                <dl className="mt-6 grid gap-3 sm:grid-cols-3">
                  <div className="rounded-xl bg-white/80 p-4">
                    <dt className="text-xs font-semibold uppercase tracking-wide text-slate-500">
                      Processed
                    </dt>
                    <dd className="mt-1 text-2xl font-bold text-slate-950">
                      {processed.toLocaleString()}
                    </dd>
                  </div>
                  <div className="rounded-xl bg-white/80 p-4">
                    <dt className="text-xs font-semibold uppercase tracking-wide text-slate-500">
                      Imported
                    </dt>
                    <dd className="mt-1 text-2xl font-bold text-slate-950">
                      {imported.toLocaleString()}
                    </dd>
                  </div>
                  <div className="rounded-xl bg-white/80 p-4">
                    <dt className="text-xs font-semibold uppercase tracking-wide text-slate-500">
                      Failed
                    </dt>
                    <dd className="mt-1 text-2xl font-bold text-slate-950">
                      {failed.toLocaleString()}
                    </dd>
                  </div>
                </dl>

                <div
                  className="upload-progress mt-5"
                  role="progressbar"
                  aria-label="Import progress"
                  aria-valuemin={0}
                  aria-valuemax={100}
                  aria-valuenow={resource.job?.progressPercent ?? (terminal ? 100 : 0)}
                >
                  <span
                    style={{
                      width: String(resource.job?.progressPercent ?? (terminal ? 100 : 0)) + '%',
                    }}
                  />
                </div>

                {pollingPaused && processing && (
                  <p className="mt-3 text-xs font-medium text-amber-700">
                    Automatic refresh has paused. Refresh this page to check the latest status.
                  </p>
                )}

                {terminal && imported > 0 && (
                  <Link
                    className="mt-5 inline-flex text-sm font-semibold text-indigo-700"
                    to="/contacts"
                  >
                    View imported contacts →
                  </Link>
                )}
              </section>
            )
          })()
        ) : preview ? (
          <>
            <section className="panel mt-6 p-5">
              <div className="flex flex-wrap items-start justify-between gap-3">
                <div>
                  <p className="text-xs font-semibold uppercase tracking-wide text-indigo-600">
                    Step 1 of 3
                  </p>
                  <h2 className="mt-1 text-lg font-bold text-slate-950">Match your columns</h2>
                  <p className="mt-1 text-sm text-slate-500">
                    We matched common column names automatically. Check the choices below before
                    importing.
                  </p>
                </div>
                <div className="rounded-xl bg-slate-50 px-4 py-3 text-sm">
                  <strong className="text-slate-950">{mappedCount}</strong> mapped
                  {ignoredCount > 0 && (
                    <span className="text-slate-500"> · {ignoredCount} not imported</span>
                  )}
                </div>
              </div>

              <div className="mt-5 divide-y divide-slate-100 rounded-xl border border-slate-200">
                {mappings.map((mapping, index) => (
                  <label
                    key={mapping.column}
                    className="grid gap-2 p-3 sm:grid-cols-[minmax(0,1fr)_minmax(220px,1fr)] sm:items-center"
                  >
                    <span>
                      <span className="block text-sm font-semibold text-slate-800">
                        {mapping.column}
                      </span>
                      <span className="text-xs text-slate-400">CSV column</span>
                    </span>
                    <select
                      className="form-input"
                      aria-label={'Import ' + mapping.column + ' as'}
                      value={targetKey(mapping.target)}
                      onChange={(event) => updateMapping(index, parseTarget(event.target.value))}
                    >
                      <option value="IGNORE">Don't import</option>
                      <option value="BUILTIN:displayName">Full name</option>
                      <option value="BUILTIN:firstName">First name</option>
                      <option value="BUILTIN:lastName">Last name</option>
                      <option value="BUILTIN:organization">Organization</option>
                      <option value="BUILTIN:notes">Notes</option>
                      <option value="IDENTITY:EMAIL">Email</option>
                      <option value="IDENTITY:PHONE">Phone</option>
                      <option value="TAG_NAMES">Tags</option>
                      {customFields.map((field) => (
                        <option key={field.id} value={'CUSTOM_FIELD:' + field.id}>
                          Custom field · {field.label}
                        </option>
                      ))}
                    </select>
                  </label>
                ))}
              </div>

              {ignoredCount > 0 && (
                <div className="mt-4 rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-900">
                  <strong>{ignoredCount} column{ignoredCount === 1 ? '' : 's'} won't be imported.</strong>{' '}
                  That is usually fine for IDs or source metadata. Review only the fields you need.
                </div>
              )}

              <details className="mt-4 rounded-xl border border-slate-200 bg-slate-50 p-4">
                <summary className="cursor-pointer text-sm font-semibold text-slate-800">
                  Preview sample rows
                </summary>
                <p className="mt-2 text-xs text-slate-500">
                  Showing a small sample so you can confirm the column mapping.
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
                        <tr key={rowIndex} className="border-t border-slate-200">
                          {row.map((cell, cellIndex) => (
                            <td key={cellIndex} className="max-w-52 truncate p-2" title={cell}>
                              {cell}
                            </td>
                          ))}
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </details>
            </section>

            <section className="panel mt-4 p-5">
              <p className="text-xs font-semibold uppercase tracking-wide text-indigo-600">
                Step 2 of 3
              </p>
              <h2 className="mt-1 text-lg font-bold text-slate-950">Import options</h2>
              <div className="mt-4 grid gap-4 md:grid-cols-2">
                <label className="text-sm font-semibold text-slate-700">
                  If a contact already exists
                  <select
                    className="form-input mt-2"
                    value={strategy}
                    onChange={(event) => setStrategy(event.target.value as typeof strategy)}
                  >
                    <option value="SKIP">Keep existing contact (recommended)</option>
                    <option value="MERGE_EXACT">Update exact email/phone match</option>
                    <option value="FAIL">Mark duplicate row as failed</option>
                  </select>
                  <span className="mt-1 block text-xs font-normal text-slate-500">
                    Exact matching uses normalized email or phone identities.
                  </span>
                </label>

                <label className="text-sm font-semibold text-slate-700">
                  Add imported contacts to a List
                  <select
                    className="form-input mt-2"
                    value={targetListId}
                    onChange={(event) => setTargetListId(event.target.value)}
                  >
                    <option value="">Don't add to a List</option>
                    {lists.map((list) => (
                      <option key={list.id} value={list.id}>
                        {list.name}
                      </option>
                    ))}
                  </select>
                  <span className="mt-1 block text-xs font-normal text-slate-500">
                    Lists make it easy to use these contacts later in Broadcast.
                  </span>
                </label>
              </div>
            </section>

            <section className="panel mt-4 p-5">
              <div className="flex flex-wrap items-start justify-between gap-3">
                <div>
                  <h2 className="text-lg font-bold text-slate-950">Marketing permissions</h2>
                  <p className="mt-1 max-w-3xl text-sm text-slate-500">
                    Importing a contact does not automatically allow marketing. Tell UnifyDesk how
                    permission should be handled for this file.
                  </p>
                </div>
                {consentMappings.length > 0 && (
                  <span className="rounded-full bg-emerald-100 px-2.5 py-1 text-xs font-semibold text-emerald-700">
                    Consent columns detected
                  </span>
                )}
              </div>

              <div className="mt-4 grid gap-3 lg:grid-cols-3">
                <label className="rounded-xl border border-slate-200 p-4">
                  <span className="flex items-start gap-3">
                    <input
                      type="radio"
                      name="consent-mode"
                      checked={consentMode === 'NONE'}
                      onChange={() => setConsentMode('NONE')}
                    />
                    <span>
                      <span className="block text-sm font-bold text-slate-900">
                        No marketing permission
                      </span>
                      <span className="mt-1 block text-xs leading-5 text-slate-500">
                        Import contacts with permission left Unknown. Safest when you are unsure.
                      </span>
                    </span>
                  </span>
                </label>

                <label className="rounded-xl border border-slate-200 p-4">
                  <span className="flex items-start gap-3">
                    <input
                      type="radio"
                      name="consent-mode"
                      checked={consentMode === 'SIMPLE'}
                      onChange={() => setConsentMode('SIMPLE')}
                    />
                    <span>
                      <span className="block text-sm font-bold text-slate-900">
                        Same permission for all contacts
                      </span>
                      <span className="mt-1 block text-xs leading-5 text-slate-500">
                        Use this when everyone in the file already opted in through the same source.
                      </span>
                    </span>
                  </span>
                </label>

                <label className="rounded-xl border border-slate-200 p-4">
                  <span className="flex items-start gap-3">
                    <input
                      type="radio"
                      name="consent-mode"
                      checked={consentMode === 'CSV'}
                      onChange={() => setConsentMode('CSV')}
                    />
                    <span>
                      <span className="block text-sm font-bold text-slate-900">
                        Read permission from CSV
                      </span>
                      <span className="mt-1 block text-xs leading-5 text-slate-500">
                        Best when each row has its own Email, SMS or WhatsApp consent value.
                      </span>
                    </span>
                  </span>
                </label>
              </div>

              {consentMode === 'SIMPLE' && (
                <div className="mt-4 rounded-xl border border-indigo-100 bg-indigo-50 p-4">
                  <p className="text-sm font-bold text-slate-950">
                    Which channels did these contacts agree to?
                  </p>
                  <div className="mt-3 flex flex-wrap gap-3">
                    {channels.map((channel) => (
                      <label
                        key={channel}
                        className="inline-flex items-center gap-2 rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm font-semibold"
                      >
                        <input
                          type="checkbox"
                          checked={simpleConsentChannels.includes(channel)}
                          onChange={() => toggleSimpleConsentChannel(channel)}
                        />
                        {channelLabel(channel)}
                      </label>
                    ))}
                  </div>

                  <div className="mt-4 grid gap-3 md:grid-cols-2">
                    <label className="text-xs font-semibold text-slate-700">
                      How was permission collected?
                      <select
                        className="form-input mt-1"
                        value={simpleConsentEvidence}
                        onChange={(event) => setSimpleConsentEvidence(event.target.value)}
                      >
                        {consentEvidenceOptions.map((option) => (
                          <option key={option.value} value={option.value}>
                            {option.label}
                          </option>
                        ))}
                      </select>
                    </label>

                    <label className="text-xs font-semibold text-slate-700">
                      Consent date
                      <input
                        className="form-input mt-1"
                        type="date"
                        max={new Date().toISOString().slice(0, 10)}
                        value={simpleConsentDate}
                        onChange={(event) => setSimpleConsentDate(event.target.value)}
                      />
                      <span className="mt-1 block font-normal text-slate-500">
                        Optional. Leave blank if the exact date is not available.
                      </span>
                    </label>
                  </div>

                  <label className="mt-4 flex items-start gap-2 rounded-lg border border-amber-200 bg-amber-50 p-3 text-sm text-amber-950">
                    <input
                      className="mt-0.5"
                      type="checkbox"
                      checked={simpleConsentConfirmed}
                      onChange={(event) => setSimpleConsentConfirmed(event.target.checked)}
                    />
                    <span>
                      I confirm these contacts gave my business permission to receive marketing on
                      the selected channels.
                    </span>
                  </label>

                  {strategy === 'SKIP' && (
                    <p className="mt-3 text-xs text-slate-500">
                      Existing contacts skipped as duplicates will keep their current permission
                      state. The declaration applies to contacts successfully created by this import.
                    </p>
                  )}
                </div>
              )}

              {consentMode === 'CSV' && (
                <div className="mt-4 space-y-4">
                  <p className="text-xs text-slate-500">
                    Map each channel to the CSV column that contains TRUE/FALSE, YES/NO,
                    OPTED_IN/OPTED_OUT or UNKNOWN.
                  </p>
                  {channels.map((channel) => {
                    const mapping = consentMappings.find((item) => item.channel === channel)
                    return (
                      <fieldset key={channel} className="rounded-xl border border-slate-200 p-4">
                        <label className="flex items-center gap-2 text-sm font-semibold">
                          <input
                            type="checkbox"
                            checked={Boolean(mapping)}
                            onChange={(event) => setConsentEnabled(channel, event.target.checked)}
                          />
                          Import {channelLabel(channel)} marketing preference
                        </label>
                        {mapping && (
                          <div className="mt-3 grid gap-3 md:grid-cols-3">
                            <label className="text-xs font-semibold text-slate-700">
                              Preference column
                              <select
                                className="form-input mt-1"
                                value={mapping.stateColumn}
                                onChange={(event) =>
                                  updateConsent(channel, { stateColumn: event.target.value })
                                }
                              >
                                <option value="">Choose column…</option>
                                {preview.headers.map((header) => (
                                  <option key={header} value={header}>
                                    {header}
                                  </option>
                                ))}
                              </select>
                            </label>
                            <label className="text-xs font-semibold text-slate-700">
                              Evidence column
                              <select
                                className="form-input mt-1"
                                value={mapping.evidenceColumn ?? ''}
                                onChange={(event) =>
                                  updateConsent(channel, {
                                    evidenceColumn: event.target.value || null,
                                  })
                                }
                              >
                                <option value="">Not included</option>
                                {preview.headers.map((header) => (
                                  <option key={header} value={header}>
                                    {header}
                                  </option>
                                ))}
                              </select>
                            </label>
                            <label className="text-xs font-semibold text-slate-700">
                              Consent date column
                              <select
                                className="form-input mt-1"
                                value={mapping.occurredAtColumn ?? ''}
                                onChange={(event) =>
                                  updateConsent(channel, {
                                    occurredAtColumn: event.target.value || null,
                                  })
                                }
                              >
                                <option value="">Not included</option>
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
              )}
            </section>

            <section className="panel mt-4 p-5">
              <p className="text-xs font-semibold uppercase tracking-wide text-indigo-600">
                Step 3 of 3
              </p>
              <h2 className="mt-1 text-lg font-bold text-slate-950">Ready to import</h2>
              <div className="mt-4 grid gap-3 text-sm sm:grid-cols-2 lg:grid-cols-4">
                <div className="rounded-xl bg-slate-50 p-4">
                  <p className="text-slate-500">Columns</p>
                  <p className="mt-1 font-bold text-slate-950">{mappedCount} will be imported</p>
                </div>
                <div className="rounded-xl bg-slate-50 p-4">
                  <p className="text-slate-500">Marketing permissions</p>
                  <p className="mt-1 font-bold text-slate-950">
                    {consentMode === 'SIMPLE'
                      ? simpleConsentChannels.length > 0
                        ? simpleConsentChannels.map(channelLabel).join(', ') + ' confirmed'
                        : 'Choose channels'
                      : consentMode === 'CSV'
                        ? consentMappings.length > 0
                          ? consentMappings.length +
                            ' channel' +
                            (consentMappings.length === 1 ? '' : 's') +
                            ' mapped'
                          : 'No consent columns mapped'
                        : 'Permission stays Unknown'}
                  </p>
                </div>
                <div className="rounded-xl bg-slate-50 p-4">
                  <p className="text-slate-500">Existing contacts</p>
                  <p className="mt-1 font-bold text-slate-950">{strategyLabel(strategy)}</p>
                </div>
                <div className="rounded-xl bg-slate-50 p-4">
                  <p className="text-slate-500">List</p>
                  <p className="mt-1 font-bold text-slate-950">
                    {lists.find((list) => list.id === targetListId)?.name || 'No List selected'}
                  </p>
                </div>
              </div>
              <p className="mt-4 text-sm text-slate-500">
                You can continue using UnifyDesk while the file imports in the background.
              </p>
              <button
                className="primary-button mt-4"
                disabled={committing || mappedCount === 0}
                onClick={() => void commit()}
              >
                {committing ? 'Starting import…' : 'Import contacts'}
              </button>
            </section>
          </>
        ) : (
          <div className="panel mt-6 p-6 text-sm text-slate-500" role="status">
            Preparing your CSV…
          </div>
        )}
      </div>
    </section>
  )
}
