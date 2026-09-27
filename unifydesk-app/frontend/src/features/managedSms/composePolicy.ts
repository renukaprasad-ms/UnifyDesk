import type { BroadcastSender } from '../broadcast/types'
import type { ManagedSmsTemplate } from './types'

export const isManagedSmsSender = (
  sender?: Pick<BroadcastSender, 'provider' | 'managementMode'> | null,
) => sender?.provider === 'MSG91_SMS' && sender.managementMode === 'MESSAGING_MANAGED'

export const managedSmsTemplateReadyForHeader = (
  template: ManagedSmsTemplate,
  headerId: string,
) =>
  template.state === 'READY' &&
  template.activeVersion?.providerSyncState === 'READY' &&
  template.activeVersion.allowedHeaderIds.includes(headerId)

export const managedSmsVariableErrors = (
  template: ManagedSmsTemplate | null | undefined,
  values: Record<string, string>,
) => {
  const errors: Record<string, string> = {}
  for (const variable of template?.activeVersion?.variables ?? []) {
    const value = values[variable.key]?.trim() ?? ''
    if (!value) {
      errors[variable.key] = 'Required'
      continue
    }
    if (value.length > variable.maxLength) {
      errors[variable.key] = `Maximum ${variable.maxLength} characters`
      continue
    }
    if (variable.type === 'NUMERIC' && !/^[-+]?\d+(?:\.\d+)?$/.test(value)) {
      errors[variable.key] = 'Enter a numeric value'
    }
  }
  return errors
}

export const managedSmsComposePolicy = (
  sender?: Pick<BroadcastSender, 'provider' | 'managementMode'> | null,
) => {
  const managed = isManagedSmsSender(sender)
  return {
    managed,
    templateRequired: managed,
    bodyEditorVisible: !managed,
  }
}
