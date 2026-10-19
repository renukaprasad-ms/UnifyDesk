import { useSnackbarState } from '../feedback/useSnackbarState'
import { useEffect, useRef, useState } from 'react'
import { createIdempotencyKey } from '../../features/contacts/contactModel'
import { contactsApi } from '../../features/contacts/contactsApi'
import type {
  ContactExport,
  ContactExportField,
  ExportRequest,
} from '../../features/contacts/types'
import { apiErrorMessage } from '../../lib/api/apiClient'

type Props = {
  companyId: string
  sourceType: ExportRequest['sourceType']
  sourceId?: string | null
  fields: readonly ContactExportField[]
  label: string
  disabled?: boolean
  disabledReason?: string
}

const activeStatuses = new Set(['QUEUED', 'RUNNING'])

export default function ExportJobButton({
  companyId,
  sourceType,
  sourceId = null,
  fields,
  label,
  disabled = false,
  disabledReason,
}: Props) {
  const [resource, setResource] = useState<ContactExport | null>(null)
  const [busy, setBusy] = useState(false)
  const [, setError] = useSnackbarState('', 'error')
  const pollCount = useRef(0)

  useEffect(() => {
    if (!resource || !activeStatuses.has(resource.status)) return

    pollCount.current = 0
    const timer = window.setInterval(() => {
      if (pollCount.current >= 120) {
        window.clearInterval(timer)
        return
      }
      pollCount.current += 1
      contactsApi
        .getExport(companyId, resource.id)
        .then(setResource)
        .catch(() => undefined)
    }, 3000)

    return () => window.clearInterval(timer)
  }, [companyId, resource?.id, resource?.status])

  const start = async () => {
    setBusy(true)
    setError('')
    try {
      const next = await contactsApi.createExport(companyId, {
        sourceType,
        sourceId,
        fields: [...fields],
        idempotencyKey: createIdempotencyKey(),
      })
      setResource(next)
    } catch (startError) {
      setError(apiErrorMessage(startError, 'Unable to start export.'))
    } finally {
      setBusy(false)
    }
  }

  const download = async () => {
    if (!resource) return
    setBusy(true)
    setError('')
    try {
      const result = await contactsApi.exportDownload(companyId, resource.id)
      window.open(result.url, '_blank', 'noopener,noreferrer')
    } catch (downloadError) {
      setError(apiErrorMessage(downloadError, 'Unable to authorize export download.'))
    } finally {
      setBusy(false)
    }
  }

  const complete = resource?.status === 'COMPLETED'

  return (
    <div className="inline-flex flex-col items-start gap-1">
      {complete ? (
        <button
          type="button"
          className="secondary-button"
          disabled={busy}
          onClick={() => void download()}
        >
          {busy ? 'Authorizing…' : 'Download export'}
        </button>
      ) : (
        <button
          type="button"
          className="secondary-button"
          disabled={disabled || busy || Boolean(resource && activeStatuses.has(resource.status))}
          title={disabled ? disabledReason : undefined}
          onClick={() => void start()}
        >
          {busy
            ? 'Starting…'
            : resource && activeStatuses.has(resource.status)
              ? 'Export ' + resource.status.toLowerCase() + '…'
              : label}
        </button>
      )}
      {resource && (
        <span className="text-[11px] text-slate-500" aria-live="polite">
          Export {resource.status.toLowerCase()}
          {resource.rowCount != null ? ' · ' + resource.rowCount.toLocaleString() + ' rows' : ''}
        </span>
      )}

    </div>
  )
}
