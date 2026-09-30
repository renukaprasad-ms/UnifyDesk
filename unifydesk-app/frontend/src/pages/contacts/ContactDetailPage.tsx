import { useEffect, useState } from 'react'
import { Link, Navigate, useParams } from 'react-router'
import ContactAttributesPanel from '../../components/contacts/ContactAttributesPanel'
import ContactFormDialog from '../../components/contacts/ContactFormDialog'
import ContactsNav from '../../components/contacts/ContactsNav'
import IdentityManager from '../../components/contacts/IdentityManager'
import {
  toPatchContactInput,
  type ContactFormDraft,
} from '../../features/contacts/contactModel'
import { contactsApi } from '../../features/contacts/contactsApi'
import type {
  CommunicationPreference,
  ContactActivity,
  ContactDetail,
  CustomFieldDefinition,
  CustomFieldValue,
  Suppression,
  TagSummary,
} from '../../features/contacts/types'
import { PERMISSIONS } from '../../features/iam/permissions'
import { useIam } from '../../features/iam/useIam'
import { apiErrorMessage } from '../../lib/api/apiClient'
import { useAppSelector } from '../../store/hooks'
import { selectSelectedCompanyId } from '../../store/workspace/workspaceSlice'

const channels: CommunicationPreference['channel'][] = ['EMAIL', 'SMS', 'WHATSAPP']

const asDraft = (contact: ContactDetail): ContactFormDraft => ({
  displayName: contact.displayName,
  firstName: contact.firstName ?? '',
  lastName: contact.lastName ?? '',
  organization: contact.organization ?? '',
  notes: contact.notes ?? '',
  email: contact.primaryEmail ?? '',
  phone: contact.primaryPhone ?? '',
})

export default function ContactDetailPage() {
  const { contactId = '' } = useParams()
  const companyId = useAppSelector(selectSelectedCompanyId)
  const { can } = useIam()
  const [contact, setContact] = useState<ContactDetail | null>(null)
  const [preferences, setPreferences] = useState<CommunicationPreference[]>([])
  const [suppressions, setSuppressions] = useState<Suppression[]>([])
  const [activity, setActivity] = useState<ContactActivity[]>([])
  const [tagOptions, setTagOptions] = useState<TagSummary[]>([])
  const [customFields, setCustomFields] = useState<CustomFieldDefinition[]>([])
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [editing, setEditing] = useState(false)
  const [editingPreference, setEditingPreference] =
    useState<CommunicationPreference['channel'] | null>(null)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')

  const load = async () => {
    if (!companyId || !contactId) return
    setLoading(true)
    setError('')
    try {
      const [detail, tags, fields, timeline] = await Promise.all([
        contactsApi.get(companyId, contactId),
        contactsApi.tags(companyId),
        contactsApi.customFields(companyId),
        contactsApi.activity(companyId, contactId).then((page) => page.items).catch(() => []),
      ])
      setContact(detail)
      setTagOptions(tags.filter((tag) => !tag.archived))
      setCustomFields(fields.filter((field) => !field.archived))
      setActivity(timeline)

      if (can(PERMISSIONS.CONTACT_CONSENT_READ)) {
        const [preferenceItems, suppressionItems] = await Promise.all([
          contactsApi.preferences(companyId, contactId),
          contactsApi.suppressions(companyId, contactId),
        ])
        setPreferences(preferenceItems)
        setSuppressions(suppressionItems)
      } else {
        setPreferences([])
        setSuppressions([])
      }
    } catch (loadError) {
      setError(apiErrorMessage(loadError, 'Contact not found.'))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    void load()
  }, [companyId, contactId])

  const mutate = async (action: () => Promise<void>, success: string) => {
    setBusy(true)
    setError('')
    setNotice('')
    try {
      await action()
      setNotice(success)
      await load()
    } catch (actionError) {
      setError(apiErrorMessage(actionError, 'Unable to update contact.'))
    } finally {
      setBusy(false)
    }
  }

  const changePreference = async (state: CommunicationPreference['state']) => {
    const channel = editingPreference
    if (!companyId || !channel) return
    if (
      !window.confirm(
        'Record ' + state.replaceAll('_', ' ') + ' for ' + channel + ' marketing?',
      )
    ) {
      return
    }
    await mutate(
      () =>
        contactsApi
          .updatePreference(
            companyId,
            contactId,
            channel,
            state,
            'Changed by workspace user',
          )
          .then(() => undefined),
      channel + ' marketing preference updated.',
    )
    setEditingPreference(null)
  }

  if (contact?.status === 'MERGED' && contact.mergedIntoContactId) {
    return <Navigate to={'/contacts/' + contact.mergedIntoContactId} replace />
  }

  if (loading) {
    return (
      <section className="px-4 py-6 sm:px-6 lg:px-8">
        <div className="mx-auto max-w-6xl">
          <ContactsNav />
          <div className="panel p-10 text-center text-sm text-slate-500" role="status">
            Loading contact…
          </div>
        </div>
      </section>
    )
  }

  if (error && !contact) {
    return (
      <section className="px-4 py-6 sm:px-6 lg:px-8">
        <div className="mx-auto max-w-6xl">
          <ContactsNav />
          <div className="panel p-8 text-center">
            <h1 className="text-xl font-bold text-slate-950">Contact unavailable</h1>
            <p className="mt-2 text-sm text-slate-500">
              {error || 'This contact could not be loaded.'}
            </p>
            <Link to="/contacts" className="primary-button mt-5 inline-flex">
              Back to contacts
            </Link>
          </div>
        </div>
      </section>
    )
  }

  if (!contact || !companyId) return null

  return (
    <section className="px-4 py-6 sm:px-6 lg:px-8">
      <div className="mx-auto max-w-6xl">
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
              Edit profile
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
                <dd className="font-medium text-slate-900">{contact.primaryEmail || '—'}</dd>
              </div>
              <div>
                <dt className="text-slate-500">Primary phone</dt>
                <dd className="font-medium text-slate-900">{contact.primaryPhone || '—'}</dd>
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
            <div className="mt-4 space-y-2">
              {contact.lists?.length ? (
                contact.lists.map((list) => (
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
            onAdd={(type, value) =>
              mutate(
                () =>
                  contactsApi
                    .addIdentity(companyId, contactId, { type, value })
                    .then(() => undefined),
                'Identity added.',
              )
            }
            onPrimary={(identityId) =>
              mutate(
                () =>
                  contactsApi
                    .setPrimaryIdentity(companyId, contactId, identityId)
                    .then(() => undefined),
                'Primary identity updated.',
              )
            }
            onRemove={(identityId) =>
              mutate(
                () => contactsApi.removeIdentity(companyId, contactId, identityId),
                'Identity removed.',
              )
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
              mutate(
                () => contactsApi.addTag(companyId, contactId, tagId),
                'Tag added.',
              )
            }
            onRemoveTag={(tagId) =>
              mutate(
                () => contactsApi.removeTag(companyId, contactId, tagId),
                'Tag removed.',
              )
            }
            onSaveFields={(customFieldValues: Record<string, CustomFieldValue>) =>
              mutate(
                () =>
                  contactsApi
                    .update(companyId, contactId, { customFields: customFieldValues })
                    .then(() => undefined),
                'Custom fields updated.',
              )
            }
          />

          {can(PERMISSIONS.CONTACT_CONSENT_READ) && (
            <section className="panel p-5 lg:col-span-2">
              <h2 className="text-lg font-bold text-slate-950">Marketing preferences</h2>
              <p className="mt-1 text-sm text-slate-500">
                Contact existence does not imply marketing consent. Each channel is managed separately.
              </p>
              <div className="mt-4 grid gap-3 md:grid-cols-3">
                {channels.map((channel) => {
                  const preference = preferences.find((item) => item.channel === channel)
                  return (
                    <div key={channel} className="rounded-xl border border-slate-200 p-4">
                      <div className="flex items-center justify-between gap-3">
                        <strong>{channel === 'WHATSAPP' ? 'WhatsApp' : channel}</strong>
                        <span className="rounded-full bg-slate-100 px-2 py-1 text-xs font-bold">
                          {preference?.state ?? 'UNKNOWN'}
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
              <h2 className="text-lg font-bold text-slate-950">Suppressions</h2>
              <p className="mt-1 text-sm text-slate-500">
                Suppression is a delivery prohibition and remains separate from consent.
              </p>
              <div className="mt-4 space-y-2">
                {suppressions.length === 0 ? (
                  <p className="text-sm text-slate-500">No active suppressions.</p>
                ) : (
                  suppressions.map((item) => (
                    <div
                      key={item.id}
                      className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-rose-100 bg-rose-50 p-3 text-sm"
                    >
                      <div>
                        <strong>{item.reason.replaceAll('_', ' ')}</strong>
                        <p className="text-xs text-rose-700">
                          Source: {item.source || 'Unknown'}
                          {item.createdAt
                            ? ' · ' + new Date(item.createdAt).toLocaleString()
                            : ''}
                        </p>
                      </div>
                      {item.liftable && can(PERMISSIONS.CONTACT_CONSENT_MODIFY) && (
                        <button
                          className="secondary-button"
                          disabled={busy}
                          onClick={() => {
                            if (!window.confirm('Lift this suppression?')) return
                            void mutate(
                              () =>
                                contactsApi
                                  .liftSuppression(companyId, contactId, item.id)
                                  .then(() => undefined),
                              'Suppression lifted.',
                            )
                          }}
                        >
                          Lift suppression
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
                    <strong className="text-sm text-slate-900">{item.title}</strong>
                    {item.summary && <p className="text-xs text-slate-500">{item.summary}</p>}
                    <time className="text-xs text-slate-400">
                      {new Date(item.occurredAt).toLocaleString()}
                    </time>
                  </div>
                ))
              )}
            </div>
          </section>
        </div>

        <ContactFormDialog
          open={editing}
          title="Edit contact"
          initial={asDraft(contact)}
          saving={busy}
          onClose={() => setEditing(false)}
          onSubmit={(draft) =>
            void mutate(
              () =>
                contactsApi
                  .update(companyId, contactId, toPatchContactInput(draft))
                  .then(() => undefined),
              'Contact profile updated.',
            ).then(() => setEditing(false))
          }
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
                This records an explicit preference event. It does not remove unrelated suppressions.
              </p>
              <div className="mt-5 grid gap-2">
                {(['OPTED_IN', 'OPTED_OUT', 'UNKNOWN'] as const).map((state) => (
                  <button
                    key={state}
                    className="secondary-button justify-start"
                    disabled={busy}
                    onClick={() => void changePreference(state)}
                  >
                    {state.replaceAll('_', ' ')}
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
    </section>
  )
}
