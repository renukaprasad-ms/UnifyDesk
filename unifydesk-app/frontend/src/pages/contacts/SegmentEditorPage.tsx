import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import ContactsNav from '../../components/contacts/ContactsNav'
import ExportJobButton from '../../components/contacts/ExportJobButton'
import SegmentBuilder from '../../components/contacts/SegmentBuilder'
import { contactsApi, isContactVersionConflict } from '../../features/contacts/contactsApi'
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
import { useAppSelector } from '../../store/hooks'
import {
  selectSelectedCompanyId,
  selectWorkspaceEpoch,
} from '../../store/workspace/workspaceSlice'
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
  const [segmentVersion, setSegmentVersion] = useState<number | null>(null)
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
      contactsApi
        .getSegment(companyId, segmentId)
        .then((segment) => {
          if (!scopeIsCurrent(scope)) return
          setName(segment.name)
          setDescription(segment.description ?? '')
          setFilter(segment.normalizedFilter ?? segment.filter)
          setSegmentVersion(segment.version)
        })
        .catch((loadError) => {
          if (scopeIsCurrent(scope)) {
            setServerErrors([apiErrorMessage(loadError, 'Unable to load Segment.')])
          }
        })
    }
  }, [captureScope, companyId, creating, scopeIsCurrent, segmentId, workspaceEpoch])

  const fields = segmentFields(
    customFields,
    tags.map((tag) => ({ value: tag.id, label: tag.name })),
    lists.map((list) => ({ value: list.id, label: list.name })),
  )

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
      setFilter(normalized)
      const nextPreview = await contactsApi.previewSegment(companyId, normalized, 20)
      if (scopeIsCurrent(scope)) setPreview(nextPreview)
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

      setSegmentVersion(saved.version)
      navigate('/contacts/segments/' + saved.id, { replace: true })
    } catch (saveError) {
      if (!scopeIsCurrent(scope)) return
      if (!creating && segmentId && isContactVersionConflict(saveError)) {
        try {
          const latest = await contactsApi.getSegment(companyId, segmentId)
          if (!scopeIsCurrent(scope)) return
          setName(latest.name)
          setDescription(latest.description ?? '')
          setFilter(latest.normalizedFilter ?? latest.filter)
          setSegmentVersion(latest.version)
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
    if (!window.confirm('Archive this Segment? Historical references remain intact.')) {
      return
    }

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
    }
  }

  return (
    <section className="px-4 py-6 sm:px-6 lg:px-8">
      <div className="mx-auto max-w-6xl">
        <ContactsNav />

        <header className="flex flex-wrap items-end justify-between gap-4">
          <div>
            <p className="eyebrow">Dynamic audience</p>
            <h1 className="mt-2 text-3xl font-bold text-slate-950">
              {creating ? 'New Segment' : canModify ? 'Edit Segment' : 'Segment'}
            </h1>
            <p className="mt-2 text-sm text-slate-500">
              The browser builds the frozen AST only. Validation, counts and matching are
              server-side.
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
                  onClick={() => void archive()}
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
          <button type="button" className="secondary-button" onClick={() => void runPreview()}>
            Validate & preview
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

        {preview && (
          <section className="panel mt-5 p-5" aria-live="polite">
            <div className="flex flex-wrap items-center justify-between gap-3">
              <h2 className="font-bold text-slate-950">Preview</h2>
              <span className="rounded-full bg-indigo-50 px-3 py-1 text-xs font-bold text-indigo-700">
                {preview.countState}:{' '}
                {preview.count == null ? 'pending' : preview.count.toLocaleString()}
              </span>
            </div>
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
    </section>
  )
}
