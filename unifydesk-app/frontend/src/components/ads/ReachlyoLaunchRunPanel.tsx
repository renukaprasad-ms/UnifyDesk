import { FiAlertTriangle, FiCheck, FiRefreshCw, FiRotateCcw } from 'react-icons/fi'
import type { LaunchRun, LaunchStep } from '../../features/ads/types'

type Props = {
  run: LaunchRun
  busy?: boolean
  onRefresh?: () => void
  onExecute?: () => void
  onRetry?: () => void
}

const statusClass = (status: string) => {
  if (status === 'SUCCEEDED' || status === 'PAUSED_READY') {
    return 'bg-emerald-50 text-emerald-700'
  }
  if (status === 'FAILED' || status === 'PARTIAL_FAILURE' || status === 'VALIDATION_FAILED') {
    return 'bg-red-50 text-red-700'
  }
  if (status === 'RUNNING' || status === 'DEPLOYING') {
    return 'bg-indigo-50 text-indigo-700'
  }
  if (status === 'SKIPPED') return 'bg-slate-100 text-slate-600'
  return 'bg-amber-50 text-amber-700'
}

const pretty = (value: string) =>
  value
    .replaceAll('_', ' ')
    .toLowerCase()
    .replace(/\b\w/g, (character) => character.toUpperCase())

const groupByChannel = (steps: LaunchStep[]) =>
  steps.reduce<Record<string, LaunchStep[]>>((groups, step) => {
    groups[step.channelId] = [...(groups[step.channelId] ?? []), step]
    return groups
  }, {})

export default function ReachlyoLaunchRunPanel({
  run,
  busy = false,
  onRefresh,
  onExecute,
  onRetry,
}: Props) {
  const groups = groupByChannel(run.steps)
  const failed = run.status === 'FAILED' || run.status === 'PARTIAL_FAILURE'
  const executable = run.status === 'READY'
  const validationFailed = run.status === 'VALIDATION_FAILED'

  return (
    <div className="panel overflow-hidden">
      <div className="flex flex-wrap items-start justify-between gap-4 border-b border-slate-100 px-5 py-4">
        <div>
          <div className="flex flex-wrap items-center gap-2">
            <h3 className="text-base font-bold text-slate-950">Launch run</h3>
            <span
              className={`rounded-full px-2.5 py-1 text-xs font-semibold ${statusClass(run.status)}`}
            >
              {pretty(run.status)}
            </span>
          </div>
          <p className="mt-1 text-xs text-slate-500">
            Run {run.id} · provider resources remain paused after creation.
          </p>
        </div>

        <div className="flex flex-wrap gap-2">
          {onRefresh && (
            <button
              type="button"
              className="secondary-button inline-flex items-center gap-2"
              disabled={busy}
              onClick={onRefresh}
            >
              <FiRefreshCw aria-hidden="true" />
              Refresh
            </button>
          )}
          {executable && onExecute && (
            <button
              type="button"
              className="primary-button inline-flex items-center gap-2"
              disabled={busy}
              onClick={onExecute}
            >
              <FiCheck aria-hidden="true" />
              {busy ? 'Launching…' : 'Create paused campaigns'}
            </button>
          )}
          {failed && onRetry && (
            <button
              type="button"
              className="primary-button inline-flex items-center gap-2"
              disabled={busy}
              onClick={onRetry}
            >
              <FiRotateCcw aria-hidden="true" />
              {busy ? 'Retrying…' : 'Retry failed steps'}
            </button>
          )}
        </div>
      </div>

      {(run.lastErrorMessage || validationFailed) && (
        <div className="border-b border-red-100 bg-red-50 px-5 py-4">
          <div className="flex gap-3">
            <FiAlertTriangle className="mt-0.5 shrink-0 text-red-600" aria-hidden="true" />
            <div>
              <p className="text-sm font-semibold text-red-800">
                {validationFailed ? 'Launch validation needs attention' : 'Launch needs attention'}
              </p>
              {run.lastErrorMessage && (
                <p className="mt-1 text-sm leading-5 text-red-700">{run.lastErrorMessage}</p>
              )}
            </div>
          </div>
        </div>
      )}

      {run.validationIssuesJson && run.validationIssuesJson !== '[]' && (
        <div className="border-b border-slate-100 px-5 py-4">
          <p className="text-xs font-semibold uppercase tracking-wide text-slate-400">Validation</p>
          <ValidationIssues json={run.validationIssuesJson} />
        </div>
      )}

      <div className="divide-y divide-slate-100">
        {Object.entries(groups).map(([channelId, steps]) => {
          const provider = steps[0]?.provider
          const channelFailed = steps.some((step) => step.status === 'FAILED')
          return (
            <div key={channelId} className="px-5 py-5">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <div>
                  <p className="text-sm font-bold text-slate-950">
                    {provider === 'GOOGLE_ADS' ? 'Google Ads' : 'Meta'} channel
                  </p>
                  <p className="mt-1 text-xs text-slate-400">Channel {channelId}</p>
                </div>
                {channelFailed && (
                  <span className="rounded-full bg-red-50 px-2.5 py-1 text-xs font-semibold text-red-700">
                    Failed
                  </span>
                )}
              </div>

              <div className="mt-4 grid gap-2">
                {steps.map((step) => (
                  <div
                    key={step.id}
                    className="flex flex-wrap items-center gap-3 rounded-xl border border-slate-100 px-3 py-3"
                  >
                    <span
                      className={`rounded-full px-2 py-0.5 text-[11px] font-semibold ${statusClass(step.status)}`}
                    >
                      {pretty(step.status)}
                    </span>
                    <div className="min-w-0 flex-1">
                      <p className="text-sm font-semibold text-slate-800">
                        {pretty(step.operationKey)}
                      </p>
                      <p className="mt-0.5 text-xs text-slate-400">
                        {pretty(step.action)} {pretty(step.resource)}
                        {step.createPaused ? ' · paused' : ''}
                        {step.attemptCount > 0 ? ` · attempt ${step.attemptCount}` : ''}
                      </p>
                      {step.lastErrorMessage && (
                        <p className="mt-1 text-xs leading-5 text-red-600">
                          {step.lastErrorMessage}
                        </p>
                      )}
                    </div>
                    {step.externalResourceId && (
                      <span
                        className="max-w-[220px] truncate text-[11px] text-slate-400"
                        title={step.externalResourceId}
                      >
                        {step.externalResourceId}
                      </span>
                    )}
                  </div>
                ))}
              </div>
            </div>
          )
        })}
        {run.steps.length === 0 && (
          <div className="px-5 py-8 text-center text-sm text-slate-500">
            No provider steps were created for this run.
          </div>
        )}
      </div>
    </div>
  )
}

function ValidationIssues({ json }: { json: string }) {
  let issues: Array<Record<string, unknown>> = []
  try {
    const parsed = JSON.parse(json)
    if (Array.isArray(parsed)) issues = parsed
  } catch {
    return <p className="mt-2 text-sm text-red-600">Validation details could not be parsed.</p>
  }

  if (issues.length === 0)
    return <p className="mt-2 text-sm text-slate-500">No validation issues.</p>

  return (
    <div className="mt-3 grid gap-2">
      {issues.map((issue, index) => {
        const severity = String(issue.severity ?? 'ERROR')
        return (
          <div
            key={`${String(issue.code ?? 'issue')}-${index}`}
            className="rounded-lg border border-slate-100 px-3 py-2.5"
          >
            <div className="flex flex-wrap items-center gap-2">
              <span
                className={`rounded-full px-2 py-0.5 text-[10px] font-bold ${statusClass(severity === 'ERROR' ? 'FAILED' : 'PENDING')}`}
              >
                {severity}
              </span>
              <span className="text-xs font-semibold text-slate-700">
                {String(issue.code ?? 'Validation')}
              </span>
            </div>
            <p className="mt-1 text-xs leading-5 text-slate-600">{String(issue.message ?? '')}</p>
          </div>
        )
      })}
    </div>
  )
}
