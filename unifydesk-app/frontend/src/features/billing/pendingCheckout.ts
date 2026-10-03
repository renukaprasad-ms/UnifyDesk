export type PendingBillingCheckout = {
  companyId: string
  targetPlanId: string
  kind: 'INITIAL' | 'UPGRADE'
  paymentId: string
  invoiceId: string
  recurringAuthorizationActionUrl?: string | null
  createdAt: string
}

const KEY = 'unifydesk.billing.pending-checkout.v1'

export const savePendingBillingCheckout = (value: PendingBillingCheckout) => {
  window.sessionStorage.setItem(KEY, JSON.stringify(value))
}

export const readPendingBillingCheckout = (): PendingBillingCheckout | null => {
  try {
    const raw = window.sessionStorage.getItem(KEY)
    if (!raw) return null
    const value = JSON.parse(raw) as Partial<PendingBillingCheckout>
    if (
      typeof value.companyId !== 'string' ||
      typeof value.targetPlanId !== 'string' ||
      (value.kind !== 'INITIAL' && value.kind !== 'UPGRADE') ||
      typeof value.paymentId !== 'string' ||
      typeof value.invoiceId !== 'string' ||
      typeof value.createdAt !== 'string'
    ) {
      return null
    }
    return value as PendingBillingCheckout
  } catch {
    return null
  }
}

export const clearPendingBillingCheckout = () => {
  window.sessionStorage.removeItem(KEY)
}
