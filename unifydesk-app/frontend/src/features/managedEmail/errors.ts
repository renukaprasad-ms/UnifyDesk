import axios from 'axios'

const managedEmailErrorMessages: Record<string, string> = {
  MANAGED_EMAIL_NOT_ENABLED: 'UnifyDesk Managed Email is not enabled for this workspace.',
  MANAGED_EMAIL_DOMAIN_LIMIT_REACHED:
    'This workspace has reached its Managed Email domain limit.',
  MANAGED_EMAIL_SENDER_LIMIT_REACHED:
    'This workspace has reached its Managed Email sender limit.',
  MANAGED_EMAIL_DOMAIN_ALREADY_CLAIMED: 'That domain is unavailable or already registered.',
  MANAGED_EMAIL_DOMAIN_NOT_ACTIVE:
    'This domain must be active before a sender can be created.',
  MANAGED_EMAIL_DOMAIN_VERIFICATION_REQUIRED:
    'Complete domain verification before using this sender.',
  MANAGED_EMAIL_SENDING_SUSPENDED:
    'Managed Email sending is currently suspended for this workspace.',
  MANAGED_EMAIL_MONTHLY_LIMIT_REACHED:
    'This workspace has reached its Managed Email monthly sending limit.',
  MANAGED_EMAIL_RATE_LIMITED:
    'Too many Managed Email requests were made. Please try again shortly.',
  MANAGED_EMAIL_CAMPAIGN_LIMIT_EXCEEDED:
    'This campaign is larger than the current Managed Email sending limit.',
  MANAGED_EMAIL_PLATFORM_DISABLED: 'Managed Email is temporarily unavailable.',
  MANAGED_EMAIL_PROVIDER_UNAVAILABLE:
    'Managed Email is temporarily unable to complete this request.',
}

export const managedEmailErrorMessage = (
  error: unknown,
  fallback = 'Unable to complete this Managed Email request.',
) => {
  if (!axios.isAxiosError(error)) return fallback
  const body = error.response?.data as
    | { error_code?: string; code?: string; error_message?: string }
    | undefined
  const code = body?.error_code ?? body?.code
  if (code && managedEmailErrorMessages[code]) return managedEmailErrorMessages[code]

  const message = body?.error_message ?? ''
  const embeddedCode = Object.keys(managedEmailErrorMessages).find((candidate) =>
    message.includes(candidate),
  )
  return embeddedCode ? managedEmailErrorMessages[embeddedCode] : fallback
}
