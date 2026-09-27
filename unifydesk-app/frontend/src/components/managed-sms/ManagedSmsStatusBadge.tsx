import { managedSmsStateLabel } from '../../features/managedSms/copy'
import type { ManagedSmsCustomerState } from '../../features/managedSms/types'

const badgeClass = (state: ManagedSmsCustomerState | string) => {
  if (state === 'READY') return 'bg-emerald-50 text-emerald-700'
  if (state === 'ACTION_REQUIRED' || state === 'REJECTED') return 'bg-rose-50 text-rose-700'
  if (state === 'SUSPENDED') return 'bg-slate-200 text-slate-700'
  if (state === 'PROVIDER_SYNCING') return 'bg-indigo-50 text-indigo-700'
  return 'bg-amber-50 text-amber-700'
}

export default function ManagedSmsStatusBadge({
  state,
}: {
  state: ManagedSmsCustomerState | string
}) {
  return (
    <span className={`rounded-full px-2.5 py-1 text-[10px] font-bold ${badgeClass(state)}`}>
      {managedSmsStateLabel(state)}
    </span>
  )
}
