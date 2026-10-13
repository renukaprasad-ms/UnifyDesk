import { PageSkeleton, CardSkeleton } from '../../components/ui/Skeleton'
import { useEffect, useMemo, useRef, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import ContactsNav from '../../components/contacts/ContactsNav'
import ExportJobButton from '../../components/contacts/ExportJobButton'
import ConfirmDialog from '../../components/ui/ConfirmDialog'
import SegmentBuilder from '../../components/contacts/SegmentBuilder'
import { contactsApi, isContactVersionConflict } from '../../features/contacts/contactsApi'
import { asyncJobApi } from '../../features/asyncJob/asyncJobApi'
import { DEFAULT_CONTACT_EXPORT_FIELDS } from '../../features/contacts/contactModel'
import {
  emptySegment,
  segmentFields,
  validateSegmentClient,
} from '../../features/contacts/segmentModel'
import type {
  CustomFieldDefinition,
  ListSummary,
  SegmentGroup,
  SegmentPreview,
  TagSummary,
} from '../../features/contacts/types'
import { PERMISSIONS } from '../../features/iam/permissions'
import { useIam } from '../../features/iam/useIam'
import { apiErrorMessage } from '../../lib/api/apiClient'
import useBeforeUnloadGuard from '../../hooks/useBeforeUnloadGuard'
import { useAppSelector } from '../../store/hooks'
import { selectSelectedCompanyId, selectWorkspaceEpoch } from '../../store/workspace/workspaceSlice'
import { useWorkspaceEpochScope } from '../../features/workspace/useWorkspaceEpochScope'

export default function SegmentEditorPage() {
  const { segmentId } = useParams()
  const creating = !segmentId || segmentId === 'new'
  const companyId = useAppSelector(selectSelectedCompanyId)
  const workspaceEpoch = useAppSelector(selectWorkspaceEpoch)
  const { capture: captureScope, isCurrent: scopeIsCurrent } = useWorkspaceEpochScope(
    companyId,
    workspaceEpoch,
  )
  const { can } = useIam()
  const navigate = useNavigate()

  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
  const [filter, setFilter] = useState<SegmentGroup>(emptySegment())
  const [customFields, setCustomFields] = useState<CustomFieldDefinition[]>([])
  const [tags, setTags] = useState<TagSummary[]>([])
  const [lists, setLists] = useState<ListSummary[]>([])
  const [preview, setPreview] = useState<SegmentPreview | null>(null)
  const [errors, setErrors] = useState<string[]>([])
  const [serverErrors, setServerErrors] = useState<string[]>([])
  const [saving, setSaving] = useState(false)
  const [initialLoading, setInitialLoading] = useState(!creating)
  const [segmentVersion, setSegmentVersion] = useState<number | null>(null)
  const [archiveConfirmOpen, setArchiveConfirmOpen] = useState(false)
  const [savedSnapshot, setSavedSnapshot] = useState('')
  const [counting, setCounting] = useState(false)
  const [countStatusMessage, setCountStatusMessage] = useState('')
  const countRunRef = useRef(0)
  const currentSnapshot = useMemo(
    () => JSON.stringify({ name, description, filter }),
    [description, filter, name],
  )
  const dirty = savedSnapshot
    ? currentSnapshot !== savedSnapshot
    : Boolean(name.trim() || description.trim())
  useBeforeUnloadGuard(dirty && !saving)

  const canModify = can(PERMISSIONS.CONTACT_MODIFY)

  useEffect(() => {
    if (!companyId) return
    const scope = captureScope()

    Promise.all([
      contactsApi.customFields(companyId),
      contactsApi.tags(companyId),
      contactsApi.lists(companyId),
    ])
      .then(([nextFields, nextTags, nextLists]) => {
        if (!scopeIsCurrent(scope)) return
        setCustomFields(nextFields.filter((field) => field.status === 'ACTIVE'))
        setTags(nextTags)
        setLists(nextLists.filter((list) => list.status !== 'ARCHIVED'))
      })
      .catch((loadError) => {
        if (scopeIsCurrent(scope)) {
          setServerErrors([apiErrorMessage(loadError, 'Unable to load Segment field metadata.')])
        }
      })

    if (!creating && segmentId) {
      setInitialLoading(true)
      contactsApi
        .getSegment(companyId, segmentId)
        .then((segment) => {
          if (!scopeIsCurrent(scope)) return
          const nextFilter = segment.normalizedFilter ?? segment.filter
          const nextDescription = segment.description ?? ''
          setName(segment.name)
          setDescription(nextDescription)
          setFilter(nextFilter)
          setSegmentVersion(segment.version)
          setSavedSnapshot(
            JSON.stringify({
              name: segment.name,
              description: nextDescription,
              filter: nextFilter,
            }),
          )
        })
        .catch((loadError) => {
          if (scopeIsCurrent(scope)) {
            setServerErrors([apiErrorMessage(loadError, 'Unable to load Segment.')])
          }
        })
        .finally(() => {
          if (scopeIsCurrent(scope)) setInitialLoading(false)
        })
    }
  }, [captureScope, companyId, creating, scopeIsCurrent, segmentId, workspaceEpoch])

  const fields = segmentFields(
    customFields,
    tags.map((tag) => ({ value: tag.id, label: tag.name })),
    lists.map((list) => ({ value: list.id, label: list.name })),
  )

  useEffect(
    () => () => {
      countRunRef.current += 1
    },
    [],
  )

  const waitForAudienceCount = async (
    targetSegmentId: string,
    scope: ReturnType<typeof captureScope>,
  ) => {
    if (!companyId) return

    const run = ++countRunRef.current
    setCounting(true)
    setCountStatusMessage('Calculating audience size…')

    try {
      const queued = await contactsApi.refreshSegmentCount(companyId, targetSegmentId)
      if (!scopeIsCurrent(scope) || run !== countRunRef.current) return

      const jobId = queued.jobId ?? queued.id
      if (!jobId) throw new Error('Audience count job was not created.')

      for (let attempt = 0; attempt < 60; attempt += 1) {
        const job = await asyncJobApi.get(jobId)
        if (!scopeIsCurrent(scope) || run !== countRunRef.current) return

        if (job.status === 'COMPLETED') {
          const refreshed = await contactsApi.getSegment(companyId, targetSegmentId)
          if (!scopeIsCurrent(scope) || run !== countRunRef.current) return

          setPreview((current) =>
            current
              ? {
                  ...current,
                  count: refreshed.count ?? null,
                  countState:
                    refreshed.countState ?? (refreshed.count == null ? 'PENDING' : 'CACHED'),
                  countComputedAt: refreshed.countComputedAt ?? null,
                }
              : current,
          )
          setCountStatusMessage(
            refreshed.count == null
              ? 'Audience size is still being calculated. Check again shortly.'
              : '',
          )
          return
        }

        if (job.status === 'FAILED') {
          throw new Error(job.errorMessage || 'Unable to calculate audience size.')
        }

        await new Promise((resolve) => window.setTimeout(resolve, 1000))
      }

      setCountStatusMessage(
        'Audience size is taking longer than expected. You can continue working and check again shortly.',
      )
    } catch (countError) {
      if (scopeIsCurrent(scope) && run === countRunRef.current) {
        setCountStatusMessage(apiErrorMessage(countError, 'Unable to calculate audience size.'))
      }
    } finally {
      if (scopeIsCurrent(scope) && run === countRunRef.current) setCounting(false)
    }
  }

  const validate = () => {
    const localErrors = validateSegmentClient(filter, fields)
    setErrors(localErrors)
    return localErrors.length === 0
  }

  const runPreview = async () => {
    if (!companyId || !validate()) return
    const scope = captureScope()

    setServerErrors([])
    try {
      const validation = await contactsApi.validateSegment(companyId, filter)
      if (!scopeIsCurrent(scope)) return
      if (!validation.valid) {
        setServerErrors(validation.errors?.map((item) => item.message) ?? ['Segment is invalid.'])
        return
      }

      const normalized = validation.normalizedFilter ?? filter
      const nextPreview = await contactsApi.previewSegment(companyId, normalized, 20)
      if (!scopeIsCurrent(scope)) return

      setPreview(nextPreview)
      if (nextPreview.countState === 'PENDING') {
        if (!creating && segmentId && !dirty) {
          void waitForAudienceCount(segmentId, scope)
        } else {
          setCountStatusMessage('Save this segment to calculate the full audience size.')
        }
      } else {
        setCountStatusMessage('')
      }
    } catch (previewError) {
      if (scopeIsCurrent(scope)) {
        setServerErrors([apiErrorMessage(previewError, 'Unable to preview Segment.')])
      }
    }
  }

  const save = async () => {
    if (!companyId || !canModify || !name.trim() || !validate()) return
    const scope = captureScope()

    setSaving(true)
    setServerErrors([])
    try {
      const validation = await contactsApi.validateSegment(companyId, filter)
      if (!scopeIsCurrent(scope)) return
      if (!validation.valid) {
        setServerErrors(validation.errors?.map((item) => item.message) ?? ['Segment is invalid.'])
        return
      }

      const normalized = validation.normalizedFilter ?? filter
      const payload = {
        name: name.trim(),
        description: description.trim() || null,
        filterVersion: 1 as const,
        filter: normalized,
      }
      const saved = creating
        ? await contactsApi.createSegment(companyId, payload)
        : segmentVersion === null
          ? null
          : await contactsApi.updateSegment(companyId, segmentId!, {
              ...payload,
              version: segmentVersion,
            })

      if (!scopeIsCurrent(scope)) return
      if (!saved) {
        setServerErrors(['Segment version is unavailable. Reload this Segment before saving.'])
        return
      }

      setName(payload.name)
      setDescription(payload.description ?? '')
      setFilter(payload.filter)
      setSegmentVersion(saved.version)
      setSavedSnapshot(
        JSON.stringify({
          name: payload.name,
          description: payload.description ?? '',
          filter: payload.filter,
        }),
      )

      navigate('/contacts/segments/' + saved.id, { replace: true })

      if (saved.countState === 'PENDING') {
        void waitForAudienceCount(saved.id, scope)
      } else {
        setCountStatusMessage('')
      }
    } catch (saveError) {
      if (!scopeIsCurrent(scope)) return
      if (!creating && segmentId && isContactVersionConflict(saveError)) {
        try {
          const latest = await contactsApi.getSegment(companyId, segmentId)
          if (!scopeIsCurrent(scope)) return
          const latestFilter = latest.normalizedFilter ?? latest.filter
          const latestDescription = latest.description ?? ''
          setName(latest.name)
          setDescription(latestDescription)
          setFilter(latestFilter)
          setSegmentVersion(latest.version)
          setSavedSnapshot(
            JSON.stringify({
              name: latest.name,
              description: latestDescription,
              filter: latestFilter,
            }),
          )
          setPreview(null)
          setServerErrors([
            'This Segment changed after you opened it. The latest version has been reloaded; review it before saving again.',
          ])
        } catch {
          if (scopeIsCurrent(scope)) {
            setServerErrors([
              'This Segment changed after you opened it. Reload the page before saving again.',
            ])
          }
        }
      } else {
        setServerErrors([apiErrorMessage(saveError, 'Unable to save Segment.')])
      }
    } finally {
      if (scopeIsCurrent(scope)) setSaving(false)
    }
  }

  const archive = async () => {
    if (!companyId || !segmentId || !canModify) return
    const scope = captureScope()
    setSaving(true)
    try {
      await contactsApi.archiveSegment(companyId, segmentId)
      if (scopeIsCurrent(scope)) {
        navigate('/contacts/segments', { replace: true })
      }
    } catch (archiveError) {
      if (scopeIsCurrent(scope)) {
        setServerErrors([apiErrorMessage(archiveError, 'Unable to archive Segment.')])
      }
    } finally {
      if (scopeIsCurrent(scope)) setSaving(false)
      setArchiveConfirmOpen(false)
    }
  }

  if (initialLoading)
    return (
      <section className="px-4 py-6 sm:px-6 lg:px-8">
        <PageSkeleton />
      </section>
    )
  return (
    <section className="px-4 py-6 sm:px-6 lg:px-8">
      <div className="w-full">
        <ContactsNav />

        <header className="flex flex-wrap items-end justify-between gap-4">
          <div>
            <p className="eyebrow">Dynamic audience</p>
            <div className="flex flex-wrap items-center gap-2">
              <h1 className="mt-2 text-3xl font-bold text-slate-950">
                {creating ? 'New Segment' : canModify ? 'Edit Segment' : 'Segment'}
              </h1>
              {dirty && (
                <span className="mt-2 rounded-full bg-amber-50 px-2.5 py-1 text-[11px] font-semibold text-amber-800">
                  Unsaved changes
                </span>
              )}
            </div>
            <p className="mt-2 text-sm text-slate-500">
              Create a dynamic group of contacts using their details, permissions, Lists and
              activity. UnifyDesk keeps the audience updated automatically.
            </p>
          </div>

          {!creating && segmentId && companyId && (
            <div className="flex flex-wrap gap-2">
              {can(PERMISSIONS.BROADCAST_CREATE) && (
                <Link
                  className="primary-button"
                  to={`/broadcast/email?audienceType=SEGMENT&audienceId=${segmentId}`}
                >
                  Create Broadcast
                </Link>
              )}
              {can(PERMISSIONS.CONTACT_EXPORT) && (
                <ExportJobButton
                  companyId={companyId}
                  sourceType="SEGMENT"
                  sourceId={segmentId}
                  fields={DEFAULT_CONTACT_EXPORT_FIELDS}
                  label="Export Segment"
                />
              )}
              {canModify && (
                <button
                  type="button"
                  className="secondary-button text-rose-700"
                  disabled={saving}
                  onClick={() => setArchiveConfirmOpen(true)}
                >
                  Archive Segment
                </button>
              )}
            </div>
          )}
        </header>

        <fieldset disabled={!canModify}>
          <div className="panel mt-6 grid gap-4 p-5 md:grid-cols-2">
            <label className="text-xs font-semibold text-slate-700">
              Name
              <input
                className="form-input mt-2"
                value={name}
                onChange={(event) => setName(event.target.value)}
              />
            </label>
            <label className="text-xs font-semibold text-slate-700">
              Description
              <input
                className="form-input mt-2"
                value={description}
                onChange={(event) => setDescription(event.target.value)}
              />
            </label>
          </div>

          <div className="mt-5">
            <SegmentBuilder
              value={filter}
              fields={fields}
              onChange={(next) => {
                countRunRef.current += 1
                setCounting(false)
                setCountStatusMessage('')
                setFilter(next)
                setPreview(null)
                setErrors([])
                setServerErrors([])
              }}
            />
          </div>
        </fieldset>

        {[...errors, ...serverErrors].length > 0 && (
          <div
            role="alert"
            className="mt-4 rounded-xl border border-rose-200 bg-rose-50 p-4 text-sm text-rose-700"
          >
            <ul className="list-disc pl-5">
              {[...errors, ...serverErrors].map((message, index) => (
                <li key={index}>{message}</li>
              ))}
            </ul>
          </div>
        )}

        <div className="mt-5 flex flex-wrap gap-2">
          <button
            type="button"
            className="secondary-button"
            disabled={counting}
            onClick={() => void runPreview()}
          >
            {counting ? 'Calculating…' : 'Preview audience'}
          </button>
          {canModify && (
            <button
              type="button"
              className="primary-button"
              disabled={saving || !name.trim()}
              onClick={() => void save()}
            >
              {saving ? 'Saving…' : 'Save Segment'}
            </button>
          )}
        </div>

        {counting && <CardSkeleton rows={3} />}
        {preview && (
          <section className="panel mt-5 p-5" aria-live="polite">
            <div className="flex flex-wrap items-center justify-between gap-3">
              <div>
                <h2 className="font-bold text-slate-950">Audience preview</h2>
                <p className="mt-1 text-xs text-slate-500">
                  Showing a sample of contacts that match these conditions.
                </p>
              </div>
              <span className="rounded-full bg-indigo-50 px-3 py-1 text-xs font-bold text-indigo-700">
                {preview.count == null
                  ? counting
                    ? 'Calculating audience size…'
                    : 'Audience size not calculated'
                  : `${preview.count.toLocaleString()} matching ${preview.count === 1 ? 'contact' : 'contacts'}`}
              </span>
            </div>
            {countStatusMessage && (
              <p className="mt-3 text-xs font-medium text-slate-500">{countStatusMessage}</p>
            )}
            <div className="mt-4 grid gap-2">
              {preview.sample.map((contact) => (
                <div key={contact.id} className="rounded-lg bg-slate-50 p-3 text-sm">
                  <strong>{contact.displayName}</strong>
                  <span className="ml-2 text-slate-500">
                    {contact.primaryEmail || contact.primaryPhone || ''}
                  </span>
                </div>
              ))}
            </div>
          </section>
        )}
      </div>
      <ConfirmDialog
        open={archiveConfirmOpen}
        title="Archive this segment?"
        description="Historical references will remain intact. The segment will stop appearing as an active audience."
        confirmLabel="Yes, archive segment"
        cancelLabel="No, keep segment"
        tone="warning"
        working={saving}
        onCancel={() => setArchiveConfirmOpen(false)}
        onConfirm={() => void archive()}
      />
    </section>
  )
}
