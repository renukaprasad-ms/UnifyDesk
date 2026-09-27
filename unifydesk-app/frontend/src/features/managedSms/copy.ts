import axios from 'axios'
import type { ManagedSmsCustomerState } from './types'

export const managedSmsStateLabel = (state: ManagedSmsCustomerState | string) => {
  switch (state) {
    case 'ACTION_REQUIRED':
      return 'Action required'
    case 'WAITING_DLT_APPROVAL':
      return 'Waiting for DLT approval'
    case 'WAITING_UNIFYDESK_REVIEW':
      return 'Waiting for UnifyDesk review'
    case 'PROVIDER_SYNCING':
      return 'Preparing sending'
    case 'READY':
      return 'Ready'
    case 'REJECTED':
      return 'Needs correction'
    case 'SUSPENDED':
      return 'Sending paused'
    default:
      return 'Setup in progress'
  }
}

export const managedSmsStateDescription = (state: ManagedSmsCustomerState | string) => {
  switch (state) {
    case 'ACTION_REQUIRED':
      return 'Complete the highlighted setup step so verification can continue.'
    case 'WAITING_DLT_APPROVAL':
      return 'Your DLT registration or content is still awaiting approval on your DLT portal.'
    case 'WAITING_UNIFYDESK_REVIEW':
      return 'Your submitted compliance details are being reviewed.'
    case 'PROVIDER_SYNCING':
      return 'Your approved details are being prepared for sending. No action is needed right now.'
    case 'READY':
      return 'Your approved Headers and templates can be used for SMS Broadcast.'
    case 'REJECTED':
      return 'Review the requested correction, update the affected item, and submit it again.'
    case 'SUSPENDED':
      return 'New managed SMS sends are paused. Review the action shown before sending again.'
    default:
      return 'Continue the managed SMS setup.'
  }
}

const errorMessages: Record<string, string> = {
  MANAGED_SMS_NOT_READY: 'Managed SMS setup is not ready for sending yet.',
  MANAGED_SMS_DLT_PROFILE_REQUIRED: 'Complete your business DLT profile before continuing.',
  MANAGED_SMS_TM_CHAIN_REQUIRED: 'Complete the required PE–Telemarketer chain step before sending.',
  MANAGED_SMS_HEADER_NOT_READY: 'Choose a Header that is approved and ready.',
  MANAGED_SMS_TEMPLATE_NOT_READY: 'Choose a managed SMS template that is approved and ready.',
  MANAGED_SMS_TEMPLATE_HEADER_MISMATCH:
    'That template is not approved for the selected Header. Choose a compatible template.',
  MANAGED_SMS_VARIABLE_INVALID:
    'One or more template variables are missing or invalid. Review the highlighted values.',
  MANAGED_SMS_CTA_NOT_APPROVED:
    'A CTA value does not match the approved template policy. Review the template variables.',
  MANAGED_SMS_COMPLIANCE_STALE:
    'Compliance verification needs to be refreshed before this message can be sent.',
  MANAGED_SMS_SUSPENDED: 'Managed SMS sending is currently paused for this workspace.',
  MANAGED_SMS_LIMIT_REACHED:
    'This workspace has reached its current managed SMS sending allowance.',
  MANAGED_SMS_PROVIDER_CAPACITY_UNAVAILABLE:
    'Managed SMS sending is temporarily unavailable. Please try again later.',
}

const extractCode = (error: unknown) => {
  if (!axios.isAxiosError(error)) return null
  const body = error.response?.data
  if (!body || typeof body !== 'object') return null
  const record = body as Record<string, unknown>
  for (const key of ['error_code', 'errorCode', 'code']) {
    if (typeof record[key] === 'string' && record[key] in errorMessages) {
      return record[key] as string
    }
  }
  if (typeof record.error_message === 'string') {
    return Object.keys(errorMessages).find((code) => record.error_message === code) ?? null
  }
  return null
}

export const managedSmsErrorMessage = (
  error: unknown,
  fallback = 'Unable to complete the managed SMS request.',
) => {
  const code = extractCode(error)
  return code ? errorMessages[code] : fallback
}
