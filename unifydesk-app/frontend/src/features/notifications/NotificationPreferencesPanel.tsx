import { useSnackbarState } from '../../components/feedback/useSnackbarState'
import { useCallback, useEffect, useMemo, useState } from 'react'
import { FiBell, FiClock, FiLock, FiMonitor, FiRefreshCw } from 'react-icons/fi'
import { apiErrorMessage } from '../../lib/api/apiClient'
import { useAppSelector } from '../../store/hooks'
import { selectSelectedCompanyId } from '../../store/workspace/workspaceSlice'
import { notificationApi } from './notificationApi'
import {
  enableBrowserNotifications,
  getBrowserNotificationState,
  revokeCurrentBrowserSubscriptionBestEffort,
  revokeServerSubscription,
} from './notificationBrowser'
import type {
  NotificationEmailMode,
  NotificationPreferences,
  PushSubscriptionSummary,
  UpdateNotificationPreferences,
} from './types'

const browserStatusCopy = (
  state: Awaited<ReturnType<typeof getBrowserNotificationState>> | null,
  configured: boolean,
) => {
  if (!configured) return 'Browser notifications are unavailable on this environment.'
  if (!state || !state.supported) {
    const standalone = window.matchMedia?.('(display-mode: standalone)').matches ?? false
    return standalone
      ? 'Browser notifications are unsupported in this installed app context.'
      : 'Unsupported in this browser or context. On devices that require an installed web app, add UnifyDesk to your Home Screen and try again.'
  }
  if (state.permission === 'denied')
    return 'Blocked by your browser. Allow notifications in the browser or site settings to enable them.'
  if (state.permission === 'granted' && state.locallySubscribed) return 'Enabled for this browser.'
  if (state.permission === 'granted') return 'Permission granted. Enable to register this browser.'
  return 'Off. UnifyDesk will only ask for permission after you choose Enable.'
}

export default function NotificationPreferencesPanel({
  companyId: companyIdOverride,
}: {
  companyId?: string | null
} = {}) {
  const selectedCompanyId = useAppSelector(selectSelectedCompanyId)
  const companyId = companyIdOverride === undefined ? selectedCompanyId : companyIdOverride
  const [preferences, setPreferences] = useState<NotificationPreferences | null>(null)
  const [draft, setDraft] = useState<UpdateNotificationPreferences | null>(null)
  const [subscriptions, setSubscriptions] = useState<PushSubscriptionSummary[]>([])
  const [browserState, setBrowserState] = useState<Awaited<
    ReturnType<typeof getBrowserNotificationState>
  > | null>(null)
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [browserBusy, setBrowserBusy] = useState(false)
  const [error, setError] = useSnackbarState('', 'error')
  const [, setNotice] = useSnackbarState('', 'success')

  const load = useCallback(async () => {
    setLoading(true)
    setError('')
    const [preferenceResult, subscriptionResult, browserResult] = await Promise.allSettled([
      notificationApi.preferences(companyId),
      notificationApi.pushSubscriptions(),
      getBrowserNotificationState(),
    ])

    if (preferenceResult.status === 'fulfilled') {
      const value = preferenceResult.value
      setPreferences(value)
      setDraft({
        timezone: value.timezone.value,
        quietHours: { ...value.quietHours },
        types: value.types.map((item) => ({
          type: item.type,
          emailMode: item.email.effectiveMode,
          browserEnabled: item.browser.effectiveEnabled,
        })),
      })
    } else {
      setError(apiErrorMessage(preferenceResult.reason, 'Unable to load notification preferences'))
    }
    if (subscriptionResult.status === 'fulfilled') setSubscriptions(subscriptionResult.value)
    if (browserResult.status === 'fulfilled') setBrowserState(browserResult.value)
    setLoading(false)
  }, [companyId])

  useEffect(() => {
    void load()
  }, [load])

  const typeDraft = useMemo(
    () => new Map(draft?.types.map((item) => [item.type, item]) ?? []),
    [draft?.types],
  )

  const updateType = (
    type: string,
    patch: Partial<{ emailMode: NotificationEmailMode; browserEnabled: boolean }>,
  ) => {
    setDraft((current) =>
      current
        ? {
            ...current,
            types: current.types.map((item) => (item.type === type ? { ...item, ...patch } : item)),
          }
        : current,
    )
  }

  const save = async () => {
    if (!draft) return
    setSaving(true)
    setError('')
    setNotice('')
    try {
      const updated = await notificationApi.updatePreferences(companyId, draft)
      setPreferences(updated)
      setDraft({
        timezone: updated.timezone.value,
        quietHours: { ...updated.quietHours },
        types: updated.types.map((item) => ({
          type: item.type,
          emailMode: item.email.effectiveMode,
          browserEnabled: item.browser.effectiveEnabled,
        })),
      })
      setNotice('Notification preferences saved.')
    } catch (saveError) {
      setError(apiErrorMessage(saveError, 'Unable to save notification preferences'))
    } finally {
      setSaving(false)
    }
  }

  const enableBrowser = async () => {
    setBrowserBusy(true)
    setError('')
    setNotice('')
    try {
      const registered = await enableBrowserNotifications()
      if (!registered) {
        setNotice(
          'Browser permission is blocked. Use your browser site settings if you want to enable notifications.',
        )
      } else {
        setNotice('Browser notifications enabled for this browser.')
      }
      await load()
    } catch (browserError) {
      setError(
        apiErrorMessage(
          browserError,
          browserError instanceof Error
            ? browserError.message
            : 'Unable to enable browser notifications',
        ),
      )
      setBrowserState(await getBrowserNotificationState().catch(() => null))
    } finally {
      setBrowserBusy(false)
    }
  }

  const disableCurrentBrowser = async () => {
    setBrowserBusy(true)
    setError('')
    setNotice('')
    try {
      await revokeCurrentBrowserSubscriptionBestEffort(5000)
      setNotice('Browser notifications disabled for this browser.')
      await load()
    } catch (browserError) {
      setError(apiErrorMessage(browserError, 'Unable to disable browser notifications'))
    } finally {
      setBrowserBusy(false)
    }
  }

  const removeDevice = async (subscriptionId: string) => {
    setBrowserBusy(true)
    setError('')
    try {
      await revokeServerSubscription(subscriptionId)
      await load()
    } catch (removeError) {
      setError(apiErrorMessage(removeError, 'Unable to remove browser subscription'))
    } finally {
      setBrowserBusy(false)
    }
  }

  if (loading) {
    return <div className="panel p-5 text-sm text-slate-500">Loading notification preferences…</div>
  }

  if (!preferences || !draft) {
    return (
      <div className="panel p-5">
        <p className="text-sm text-red-700">
          {error || 'Notification preferences are unavailable.'}
        </p>
        <button type="button" className="secondary-button mt-4" onClick={() => void load()}>
          <FiRefreshCw aria-hidden="true" className="mr-2" />
          Retry
        </button>
      </div>
    )
  }

  const configured = preferences.channels.browserPushConfigured

  return (
    <div className="space-y-4">


      <div className="panel p-5">
        <div className="flex items-start gap-4">
          <span className="security-icon shrink-0">
            <FiClock aria-hidden="true" />
          </span>
          <div className="min-w-0 flex-1">
            <h2 className="text-base font-bold text-slate-950">Quiet hours</h2>
            <p className="mt-1 text-sm leading-6 text-slate-500">
              Optional email and browser interruptions wait until quiet hours end. In-app history
              remains available.
            </p>
            <div className="mt-4 grid gap-3 sm:grid-cols-[minmax(0,1fr)_140px_140px]">
              <label className="text-sm font-semibold text-slate-700">
                Timezone
                <input
                  className="form-input mt-1"
                  value={draft.timezone}
                  onChange={(event) =>
                    setDraft((current) =>
                      current ? { ...current, timezone: event.target.value } : current,
                    )
                  }
                  aria-label="Notification timezone"
                />
                <span className="mt-1 block text-xs font-normal text-slate-400">
                  Current source: {preferences.timezone.source.replaceAll('_', ' ').toLowerCase()}
                </span>
              </label>
              <label className="text-sm font-semibold text-slate-700">
                From
                <input
                  type="time"
                  className="form-input mt-1"
                  value={draft.quietHours.start}
                  disabled={!draft.quietHours.enabled}
                  onChange={(event) =>
                    setDraft((current) =>
                      current
                        ? {
                            ...current,
                            quietHours: { ...current.quietHours, start: event.target.value },
                          }
                        : current,
                    )
                  }
                />
              </label>
              <label className="text-sm font-semibold text-slate-700">
                Until
                <input
                  type="time"
                  className="form-input mt-1"
                  value={draft.quietHours.end}
                  disabled={!draft.quietHours.enabled}
                  onChange={(event) =>
                    setDraft((current) =>
                      current
                        ? {
                            ...current,
                            quietHours: { ...current.quietHours, end: event.target.value },
                          }
                        : current,
                    )
                  }
                />
              </label>
            </div>
            <label className="mt-4 inline-flex items-center gap-3 text-sm font-semibold text-slate-700">
              <input
                type="checkbox"
                checked={draft.quietHours.enabled}
                onChange={(event) =>
                  setDraft((current) =>
                    current
                      ? {
                          ...current,
                          quietHours: { ...current.quietHours, enabled: event.target.checked },
                        }
                      : current,
                  )
                }
              />
              Use quiet hours
            </label>
          </div>
        </div>
      </div>

      <div className="panel overflow-hidden">
        <div className="border-b border-slate-100 p-5">
          <div className="flex items-start gap-4">
            <span className="security-icon shrink-0">
              <FiBell aria-hidden="true" />
            </span>
            <div>
              <h2 className="text-base font-bold text-slate-950">Notification types</h2>
              <p className="mt-1 text-sm leading-6 text-slate-500">
                Choose how each notification type can interrupt you. Mandatory controls are defined
                by the server.
              </p>
            </div>
          </div>
        </div>
        <div className="divide-y divide-slate-100">
          {preferences.types.map((preference) => {
            const item = typeDraft.get(preference.type)
            if (!item) return null
            return (
              <div
                key={preference.type}
                className="grid gap-4 p-5 md:grid-cols-[minmax(0,1fr)_180px_150px] md:items-center"
              >
                <div>
                  <div className="flex flex-wrap items-center gap-2">
                    <p className="font-bold text-slate-950">{preference.label}</p>
                    {preference.mandatoryEmail && (
                      <span className="inline-flex items-center gap-1 rounded-full bg-slate-100 px-2 py-1 text-[10px] font-bold uppercase tracking-wide text-slate-600">
                        <FiLock aria-hidden="true" /> Mandatory email
                      </span>
                    )}
                  </div>
                  <p className="mt-1 text-xs font-semibold uppercase tracking-wide text-slate-400">
                    {preference.category}
                  </p>
                </div>
                <label className="text-xs font-bold uppercase tracking-wide text-slate-500">
                  Email
                  <select
                    className="form-input mt-1 normal-case"
                    value={item.emailMode}
                    disabled={!preferences.channels.emailAvailable || preference.email.locked}
                    onChange={(event) =>
                      updateType(preference.type, {
                        emailMode: event.target.value as NotificationEmailMode,
                      })
                    }
                  >
                    {preference.email.allowedModes.map((mode) => (
                      <option key={mode} value={mode}>
                        {mode.toLowerCase()}
                      </option>
                    ))}
                  </select>
                </label>
                <label className="flex items-center justify-between gap-3 text-sm font-semibold text-slate-700">
                  Browser
                  <button
                    type="button"
                    className={`switch ${item.browserEnabled ? 'is-on' : ''}`}
                    disabled={preference.browser.locked || !configured}
                    aria-pressed={item.browserEnabled}
                    aria-label={`${item.browserEnabled ? 'Disable' : 'Enable'} browser notifications for ${preference.label}`}
                    onClick={() =>
                      updateType(preference.type, { browserEnabled: !item.browserEnabled })
                    }
                  >
                    <span />
                  </button>
                </label>
              </div>
            )
          })}
        </div>
        <div className="flex justify-end border-t border-slate-100 p-5">
          <button
            type="button"
            className="primary-button"
            disabled={saving}
            onClick={() => void save()}
          >
            {saving ? 'Saving…' : 'Save notification preferences'}
          </button>
        </div>
      </div>

      <div className="panel p-5">
        <div className="flex flex-col gap-5 sm:flex-row sm:items-start sm:justify-between">
          <div className="flex min-w-0 items-start gap-4">
            <span className="security-icon shrink-0">
              <FiMonitor aria-hidden="true" />
            </span>
            <div>
              <h2 className="text-base font-bold text-slate-950">Browser notifications</h2>
              <p className="mt-1 max-w-2xl text-sm leading-6 text-slate-500">
                Receive privacy-safe alerts when UnifyDesk is in the background. We never ask for
                browser permission until you choose Enable.
              </p>
              <p className="mt-2 text-xs font-semibold text-slate-500">
                {browserStatusCopy(browserState, configured)}
              </p>
            </div>
          </div>
          <div className="flex shrink-0 gap-2">
            {browserState?.supported &&
            browserState.permission === 'granted' &&
            browserState.locallySubscribed ? (
              <button
                type="button"
                className="secondary-button"
                disabled={browserBusy}
                onClick={() => void disableCurrentBrowser()}
              >
                Disable this browser
              </button>
            ) : (
              <button
                type="button"
                className="primary-button"
                disabled={
                  browserBusy ||
                  !configured ||
                  !browserState?.supported ||
                  browserState.permission === 'denied'
                }
                onClick={() => void enableBrowser()}
              >
                Enable browser notifications
              </button>
            )}
          </div>
        </div>

        {subscriptions.length > 0 && (
          <div className="mt-5 border-t border-slate-100 pt-5">
            <p className="text-xs font-bold uppercase tracking-[0.12em] text-slate-400">
              Registered browsers
            </p>
            <div className="mt-3 space-y-2">
              {subscriptions.map((subscription) => (
                <div
                  key={subscription.id}
                  className="flex flex-col gap-2 rounded-xl border border-slate-100 bg-slate-50 px-4 py-3 sm:flex-row sm:items-center sm:justify-between"
                >
                  <div>
                    <p className="text-sm font-semibold text-slate-800">
                      {subscription.deviceLabel || 'Browser device'}
                    </p>
                    <p className="mt-0.5 text-xs text-slate-500">
                      {subscription.status} · Added{' '}
                      {new Date(subscription.createdAt).toLocaleDateString()}
                    </p>
                  </div>
                  {subscription.status === 'ACTIVE' && (
                    <button
                      type="button"
                      className="notification-text-button"
                      disabled={browserBusy}
                      onClick={() => void removeDevice(subscription.id)}
                    >
                      Remove
                    </button>
                  )}
                </div>
              ))}
            </div>
          </div>
        )}
      </div>
    </div>
  )
}
