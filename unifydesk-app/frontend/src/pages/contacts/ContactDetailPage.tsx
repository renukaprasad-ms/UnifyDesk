import { useEffect, useState } from 'react'
import { Link, Navigate, useParams } from 'react-router'
import ContactAttributesPanel from '../../components/contacts/ContactAttributesPanel'
import ContactFormDialog from '../../components/contacts/ContactFormDialog'
import ContactsNav from '../../components/contacts/ContactsNav'
import IdentityManager from '../../components/contacts/IdentityManager'
import ManualSuppressionForm from '../../components/contacts/ManualSuppressionForm'
import ConfirmDialog from '../../components/ui/ConfirmDialog'
import { CardSkeleton } from '../../components/ui/Skeleton'
import {
  contactPrimaryIdentityValue,
  createIdempotencyKey,
  toPatchContactInput,
  type ContactFormDraft,
} from '../../features/contacts/contactModel'
import { contactsApi, isContactVersionConflict } from '../../features/contacts/contactsApi'
import type {
  CommunicationPreference,
  ContactActivity,
  ContactDetail,
  CustomFieldDefinition,
  CustomFieldValue,
  Suppression,
  TagSummary,
  ListSummary,
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
import type { WorkspaceEpochToken } from '../../features/workspace/workspaceEpoch'

const channels: CommunicationPreference['channel'][] = ['EMAIL', 'SMS', 'WHATSAPP']
const preferenceLabel = (state: CommunicationPreference['state'] | undefined) =>
  state === 'OPTED_IN' ? 'Subscribed' : state === 'OPTED_OUT' ? 'Opted out' : 'Permission unknown'

const asDraft = (contact: ContactDetail): ContactFormDraft => ({
  displayName: contact.displayName,
  firstName: contact.firstName ?? '',
  lastName: contact.lastName ?? '',
  organization: contact.organization ?? '',
  notes: contact.notes ?? '',
  email: contactPrimaryIdentityValue(contact, 'EMAIL') ?? '',
  phone: contactPrimaryIdentityValue(contact, 'PHONE') ?? '',
})

export default function ContactDetailPage() {
  const { contactId = '' } = useParams()
  const companyId = useAppSelector(selectSelectedCompanyId)
  const workspaceEpoch = useAppSelector(selectWorkspaceEpoch)
  const { capture: captureScope, isCurrent: scopeIsCurrent } = useWorkspaceEpochScope(
    companyId,
    workspaceEpoch,
  )
  const { can } = useIam()
  const [contact, setContact] = useState<ContactDetail | null>(null)
  const [preferences, setPreferences] = useState<CommunicationPreference[]>([])
  const [suppressions, setSuppressions] = useState<Suppression[]>([])
  const [activity, setActivity] = useState<ContactActivity[]>([])
  const [activityCursor, setActivityCursor] = useState<string | null>(null)
  const [activityHasMore, setActivityHasMore] = useState(false)
  const [activityLoadingMore, setActivityLoadingMore] = useState(false)
  const [tagOptions, setTagOptions] = useState<TagSummary[]>([])
  const [customFields, setCustomFields] = useState<CustomFieldDefinition[]>([])
  const [listOptions, setListOptions] = useState<ListSummary[]>([])
  const [listToAdd, setListToAdd] = useState('')
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [editing, setEditing] = useState(false)
  const [editingPreference, setEditingPreference] = useState<
    CommunicationPreference['channel'] | null
  >(null)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [pendingPreferenceState, setPendingPreferenceState] = useState<CommunicationPreference['state'] | null>(null)
  const [suppressionToLift, setSuppressionToLift] = useState<Suppression | null>(null)

  const load = async (scope: WorkspaceEpochToken | null = captureScope()) => {
    if (!companyId || !contactId || !scopeIsCurrent(scope)) return
    setLoading(true)
    setError('')
    try {
      const [detail, tags, fields, lists, activityPage] = await Promise.all([
        contactsApi.get(companyId, contactId),
        contactsApi.tags(companyId),
        contactsApi.customFields(companyId),
        contactsApi.lists(companyId),
        contactsApi
          .activity(companyId, contactId)
          .catch(() => ({ items: [], nextCursor: null, hasMore: false })),
      ])
      if (!scopeIsCurrent(scope)) return

      setContact(detail)
      setTagOptions(tags)
      setCustomFields(fields.filter((field) => field.status === 'ACTIVE'))
      setListOptions(lists.filter((item) => item.status !== 'ARCHIVED'))
      setActivity(activityPage.items)
      setActivityCursor(activityPage.nextCursor)
      setActivityHasMore(activityPage.hasMore)

      if (can(PERMISSIONS.CONTACT_CONSENT_READ)) {
        const [preferenceItems, suppressionItems] = await Promise.all([
          contactsApi.preferences(companyId, contactId),
          contactsApi.suppressions(companyId, contactId),
        ])
        if (!scopeIsCurrent(scope)) return
        setPreferences(preferenceItems)
        setSuppressions(suppressionItems)
      } else if (scopeIsCurrent(scope)) {
        setPreferences([])
        setSuppressions([])
      }
    } catch (loadError) {
      if (scopeIsCurrent(scope)) {
        setError(apiErrorMessage(loadError, 'Contact not found.'))
      }
    } finally {
      if (scopeIsCurrent(scope)) setLoading(false)
    }
  }

  useEffect(() => {
    void load(captureScope())
  }, [captureScope, companyId, contactId, workspaceEpoch])

  const mutate = async (
    action: () => Promise<void>,
    success: string,
    options?: { versioned?: boolean },
  ) => {
    const scope = captureScope()
    if (!scopeIsCurrent(scope)) return false
    setBusy(true)
    setError('')
    setNotice('')
    try {
      await action()
      if (!scopeIsCurrent(scope)) return false
      setNotice(success)
      await load(scope)
      return scopeIsCurrent(scope)
    } catch (actionError) {
      if (!scopeIsCurrent(scope)) return false
      if (options?.versioned && isContactVersionConflict(actionError)) {
        setEditing(false)
        await load(scope)
        if (scopeIsCurrent(scope)) {
          setError(
            'This Contact changed after you opened it. The latest version has been reloaded; review the changes and try again.',
          )
        }
        return false
      }

      setError(apiErrorMessage(actionError, 'Unable to update Contact.'))
      return false
    } finally {
      if (scopeIsCurrent(scope)) setBusy(false)
    }
  }

  const loadMoreActivity = async () => {
    if (!companyId || !activityHasMore || !activityCursor || activityLoadingMore) {
      return
    }

    const scope = captureScope()
    setActivityLoadingMore(true)
    try {
      const page = await contactsApi.activity(companyId, contactId, activityCursor)
      if (!scopeIsCurrent(scope)) return
      setActivity((current) => [
        ...current,
        ...page.items.filter((next) => !current.some((existing) => existing.id === next.id)),
      ])
      setActivityCursor(page.nextCursor)
      setActivityHasMore(page.hasMore)
    } catch (activityError) {
      if (scopeIsCurrent(scope)) {
        setError(apiErrorMessage(activityError, 'Unable to load more Contact activity.'))
      }
    } finally {
      if (scopeIsCurrent(scope)) setActivityLoadingMore(false)
    }
  }

  const changePreference = async (state: CommunicationPreference['state']) => {
    const channel = editingPreference
    if (!companyId || !channel) return
    const changed = await mutate(
      () =>
        contactsApi
          .updatePreference(companyId, contactId, channel, state, 'Changed by workspace user')
          .then(() => undefined),
      channel + ' marketing preference updated.',
    )
    if (changed) setEditingPreference(null)
    setPendingPreferenceState(null)
  }

  if (contact?.status === 'MERGED' && contact.mergedIntoContactId) {
    return <Navigate to={'/contacts/' + contact.mergedIntoContactId} replace />
  }

  if (loading) {
    return (
      <section className="px-4 py-6 sm:px-6 lg:px-8">
        <div className="w-full">
          <ContactsNav />
          <div className="mt-4"><CardSkeleton rows={7} /></div>
        </div>
      </section>
    )
  }

  if (error && !contact) {
    return (
      <section className="px-4 py-6 sm:px-6 lg:px-8">
        <div className="w-full">
          <ContactsNav />
          <div className="panel p-8 text-center">
            <h1 className="text-xl font-bold text-slate-950">Contact unavailable</h1>
            <p className="mt-2 text-sm text-slate-500">
              {error || 'This Contact could not be loaded.'}
            </p>
            <Link to="/contacts" className="primary-button mt-5 inline-flex">
              Back to Contacts
            </Link>
          </div>
        </div>
      </section>
    )
  }

  if (!contact || !companyId) return null

  const primaryEmail = contactPrimaryIdentityValue(contact, 'EMAIL')
  const primaryPhone = contactPrimaryIdentityValue(contact, 'PHONE')

  return (
    <section className="px-4 py-6 sm:px-6 lg:px-8">
      <div className="w-full">
        <ContactsNav />

        <header className="flex flex-wrap items-end justify-between gap-4">
          <div>
            <p className="eyebrow">Contact</p>
            <h1 className="mt-2 text-3xl font-bold text-slate-950">{contact.displayName}</h1>
            <p className="mt-2 text-sm text-slate-500">
              {contact.organization || 'No organization'}
            </p>
          </div>
          {can(PERMISSIONS.CONTACT_MODIFY) && (
            <button className="primary-button" onClick={() => setEditing(true)}>
              Edit contact
            </button>
          )}
        </header>

        {(error || notice) && (
          <div
            role={error ? 'alert' : 'status'}
            className={
              'mt-5 rounded-xl border px-4 py-3 text-sm font-medium ' +
              (error
                ? 'border-rose-200 bg-rose-50 text-rose-700'
                : 'border-emerald-200 bg-emerald-50 text-emerald-700')
            }
          >
            {error || notice}
          </div>
        )}

        <div className="mt-6 grid gap-5 lg:grid-cols-2">
          <section className="panel p-5">
            <h2 className="text-lg font-bold text-slate-950">Profile</h2>
            <dl className="mt-4 grid gap-4 text-sm sm:grid-cols-2">
              <div>
                <dt className="text-slate-500">Name</dt>
                <dd className="font-medium text-slate-900">{contact.displayName}</dd>
              </div>
              <div>
                <dt className="text-slate-500">Organization</dt>
                <dd className="font-medium text-slate-900">{contact.organization || '—'}</dd>
              </div>
              <div>
                <dt className="text-slate-500">Primary email</dt>
                <dd className="font-medium text-slate-900">{primaryEmail || '—'}</dd>
              </div>
              <div>
                <dt className="text-slate-500">Primary phone</dt>
                <dd className="font-medium text-slate-900">{primaryPhone || '—'}</dd>
              </div>
            </dl>
            {contact.notes && (
              <div className="mt-5 border-t border-slate-100 pt-4">
                <p className="text-xs font-bold uppercase tracking-wide text-slate-500">Notes</p>
                <p className="mt-2 whitespace-pre-wrap text-sm text-slate-700">{contact.notes}</p>
              </div>
            )}
          </section>

          <section className="panel p-5">
            <h2 className="text-lg font-bold text-slate-950">Lists</h2>
            {can(PERMISSIONS.CONTACT_MODIFY) && (
              <div className="mt-4 flex gap-2">
                <select className="form-input" value={listToAdd} onChange={(event) => setListToAdd(event.target.value)}>
                  <option value="">Add to a list…</option>
                  {listOptions
                    .filter((option) => !(contact.lists ?? []).some((assigned) => assigned.id === option.id))
                    .map((option) => <option key={option.id} value={option.id}>{option.name}</option>)}
                </select>
                <button
                  className="primary-button"
                  disabled={busy || !listToAdd}
                  onClick={() => {
                    if (!listToAdd) return
                    void mutate(
                      () => contactsApi.addListMembers(companyId, listToAdd, [contactId]).then(() => undefined),
                      'Contact added to list.',
                    ).then((changed) => changed && setListToAdd(''))
                  }}
                >
                  Add
                </button>
              </div>
            )}
            <div className="mt-4 space-y-2">
              {contact.lists?.length ? (
                contact.lists
                  .filter((list) => list.status !== 'ARCHIVED')
                  .map((list) => (
                    <Link
                      key={list.id}
                      to={'/contacts/lists/' + list.id}
                      className="block rounded-xl border border-slate-200 p-3 text-sm font-semibold hover:bg-slate-50"
                    >
                      {list.name}
                      <span className="ml-2 font-normal text-slate-500">
                        {list.memberCount.toLocaleString()} members
                      </span>
                    </Link>
                  ))
              ) : (
                <p className="text-sm text-slate-500">Not in any Lists.</p>
              )}
            </div>
          </section>

          <IdentityManager
            identities={contact.identities}
            canModify={can(PERMISSIONS.CONTACT_MODIFY)}
            busy={busy}
            onAdd={(type, value, namespace) =>
              mutate(
                () =>
                  contactsApi
                    .addIdentity(companyId, contactId, {
                      type,
                      value,
                      namespace,
                    })
                    .then(() => undefined),
                'Identity added.',
              ).then(() => undefined)
            }
            onPrimary={(identityId) =>
              mutate(
                () =>
                  contactsApi
                    .setPrimaryIdentity(companyId, contactId, identityId)
                    .then(() => undefined),
                'Primary identity updated.',
              ).then(() => undefined)
            }
            onRemove={(identityId) =>
              mutate(
                () => contactsApi.removeIdentity(companyId, contactId, identityId),
                'Identity removed.',
              ).then(() => undefined)
            }
          />

          <ContactAttributesPanel
            tags={tagOptions}
            assignedTags={contact.tags ?? []}
            customFields={customFields}
            values={contact.customFields ?? {}}
            canModify={can(PERMISSIONS.CONTACT_MODIFY)}
            busy={busy}
            onAddTag={(tagId) =>
              mutate(() => contactsApi.addTag(companyId, contactId, tagId), 'Tag added.').then(
                () => undefined,
              )
            }
            onRemoveTag={(tagId) =>
              mutate(() => contactsApi.removeTag(companyId, contactId, tagId), 'Tag removed.').then(
                () => undefined,
              )
            }
            onSaveFields={(customFieldValues: Record<string, CustomFieldValue>) =>
              mutate(
                () =>
                  contactsApi
                    .update(companyId, contactId, {
                      version: contact.version,
                      customFields: customFieldValues,
                    })
                    .then(() => undefined),
                'Custom fields updated.',
                { versioned: true },
              ).then(() => undefined)
            }
          />

          {can(PERMISSIONS.CONTACT_CONSENT_READ) && (
            <section className="panel p-5 lg:col-span-2">
              <h2 className="text-lg font-bold text-slate-950">Marketing preferences</h2>
              <p className="mt-1 text-sm text-slate-500">
                Choose whether this contact can receive marketing on each channel. Permission is managed separately for email, SMS and WhatsApp.
              </p>
              <div className="mt-4 grid gap-3 md:grid-cols-3">
                {channels.map((channel) => {
                  const preference = preferences.find((item) => item.channel === channel)
                  return (
                    <div key={channel} className="rounded-xl border border-slate-200 p-4">
                      <div className="flex items-center justify-between gap-3">
                        <strong>{channel === 'WHATSAPP' ? 'WhatsApp' : channel}</strong>
                        <span className="rounded-full bg-slate-100 px-2 py-1 text-xs font-bold">
                          {preferenceLabel(preference?.state)}
                        </span>
                      </div>
                      <p className="mt-2 text-xs text-slate-500">
                        Source: {preference?.source || 'Not recorded'}
                        {preference?.occurredAt
                          ? ' · ' + new Date(preference.occurredAt).toLocaleString()
                          : ''}
                      </p>
                      {preference?.reason && (
                        <p className="mt-1 text-xs text-slate-500">Reason: {preference.reason}</p>
                      )}
                      {can(PERMISSIONS.CONTACT_CONSENT_MODIFY) && (
                        <button
                          className="secondary-button mt-3"
                          onClick={() => setEditingPreference(channel)}
                        >
                          Change
                        </button>
                      )}
                    </div>
                  )
                })}
              </div>
            </section>
          )}

          {can(PERMISSIONS.CONTACT_CONSENT_READ) && (
            <section className="panel p-5 lg:col-span-2">
              <h2 className="text-lg font-bold text-slate-950">Delivery restrictions</h2>
              <p className="mt-1 text-sm text-slate-500">
                Delivery restrictions block sending even when a marketing permission exists.
              </p>

              {can(PERMISSIONS.CONTACT_CONSENT_MODIFY) && (
                <ManualSuppressionForm
                  busy={busy}
                  onCreate={(payload) =>
                    mutate(
                      () =>
                        contactsApi
                          .createSuppression(companyId, contactId, payload)
                          .then(() => undefined),
                      'Suppression added.',
                    ).then(() => undefined)
                  }
                />
              )}

              <div className="mt-4 space-y-2">
                {suppressions.length === 0 ? (
                  <p className="text-sm text-slate-500">No active delivery restrictions.</p>
                ) : (
                  suppressions.map((item) => (
                    <div
                      key={item.id}
                      className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-rose-100 bg-rose-50 p-3 text-sm"
                    >
                      <div>
                        <strong>{item.reasonCode.replaceAll('_', ' ')}</strong>
                        <p className="text-xs text-rose-700">
                          State: {item.state} · Source: {item.source || 'Unknown'}
                          {item.createdAt ? ' · ' + new Date(item.createdAt).toLocaleString() : ''}
                        </p>
                      </div>
                      {item.active && item.liftable && can(PERMISSIONS.CONTACT_CONSENT_MODIFY) && (
                        <button
                          className="secondary-button"
                          disabled={busy}
                          onClick={() => setSuppressionToLift(item)}
                        >
                          Remove restriction
                        </button>
                      )}
                    </div>
                  ))
                )}
              </div>
            </section>
          )}

          <section className="panel p-5 lg:col-span-2">
            <h2 className="text-lg font-bold text-slate-950">Activity</h2>
            <div className="mt-4 space-y-3">
              {activity.length === 0 ? (
                <p className="text-sm text-slate-500">No recent activity.</p>
              ) : (
                activity.map((item) => (
                  <div key={item.id} className="border-l-2 border-indigo-200 pl-4">
                    {item.href ? (
                      <Link className="text-sm font-bold text-indigo-700 hover:underline" to={item.href}>
                        {item.title}
                      </Link>
                    ) : (
                      <strong className="text-sm text-slate-900">{item.title}</strong>
                    )}
                    {item.summary && <p className="text-xs text-slate-500">{item.summary}</p>}
                    <time className="text-xs text-slate-400">
                      {new Date(item.occurredAt).toLocaleString()}
                    </time>
                  </div>
                ))
              )}
            </div>
            {activityHasMore && (
              <button
                className="secondary-button mt-4"
                disabled={activityLoadingMore}
                onClick={() => void loadMoreActivity()}
              >
                {activityLoadingMore ? 'Loading…' : 'Load more activity'}
              </button>
            )}
          </section>
        </div>

        <ContactFormDialog
          open={editing}
          title="Edit Contact"
          initial={asDraft(contact)}
          saving={busy}
          showIdentityFields={false}
          onClose={() => setEditing(false)}
          onSubmit={(draft) => {
            void mutate(
              () =>
                contactsApi
                  .update(companyId, contactId, toPatchContactInput(draft, contact.version))
                  .then(() => undefined),
              'Contact profile updated.',
              { versioned: true },
            ).then((updated) => {
              if (updated) setEditing(false)
            })
          }}
        />

        {editingPreference && (
          <div className="mobile-modal-shell fixed inset-0 z-50 flex items-end justify-center bg-slate-950/35 p-0 sm:items-center sm:p-4">
            <div
              role="dialog"
              aria-modal="true"
              aria-labelledby="preference-dialog-title"
              className="mobile-sheet w-full max-w-lg rounded-t-2xl bg-white p-5 sm:rounded-2xl"
            >
              <h2 id="preference-dialog-title" className="text-lg font-bold text-slate-950">
                Change {editingPreference} marketing preference
              </h2>
              <p className="mt-2 text-sm text-slate-500">
                This records an explicit preference event. It does not remove unrelated
                suppressions.
              </p>
              <div className="mt-5 grid gap-2">
                {(['OPTED_IN', 'OPTED_OUT', 'UNKNOWN'] as const).map((state) => (
                  <button
                    key={state}
                    className="secondary-button justify-start"
                    disabled={busy}
                    onClick={() => setPendingPreferenceState(state)}
                  >
                    {preferenceLabel(state)}
                  </button>
                ))}
              </div>
              <button className="secondary-button mt-4" onClick={() => setEditingPreference(null)}>
                Cancel
              </button>
            </div>
          </div>
        )}
      </div>
        <ConfirmDialog
          open={Boolean(pendingPreferenceState && editingPreference)}
          title="Record this marketing preference?"
          description={
            pendingPreferenceState && editingPreference
              ? `Record ${preferenceLabel(pendingPreferenceState)} for ${editingPreference} marketing? This creates an auditable preference event.`
              : ''
          }
          confirmLabel="Yes, record preference"
          cancelLabel="No, cancel"
          tone={pendingPreferenceState === 'OPTED_OUT' ? 'warning' : 'default'}
          working={busy}
          onCancel={() => setPendingPreferenceState(null)}
          onConfirm={() => pendingPreferenceState && void changePreference(pendingPreferenceState)}
        />
        <ConfirmDialog
          open={Boolean(suppressionToLift)}
          title="Lift this suppression?"
          description="This changes the contact's sending eligibility and will be recorded for audit."
          confirmLabel="Yes, lift suppression"
          cancelLabel="No, keep suppression"
          tone="warning"
          working={busy}
          onCancel={() => setSuppressionToLift(null)}
          onConfirm={() => {
            if (!suppressionToLift || !companyId) return
            void mutate(
              () =>
                contactsApi
                  .liftSuppression(companyId, contactId, suppressionToLift.id, {
                    reason: 'Lifted by workspace user after review',
                    idempotencyKey: createIdempotencyKey(),
                  })
                  .then(() => undefined),
              'Suppression lifted.',
            ).finally(() => setSuppressionToLift(null))
          }}
        />
    </section>
  )
}
