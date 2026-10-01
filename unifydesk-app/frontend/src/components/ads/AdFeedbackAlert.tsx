type Props = {
  error?: string | null
  notice?: string | null
}

export default function AdFeedbackAlert({ error, notice }: Props) {
  // API errors are surfaced globally through the snackbar so they stay visible
  // above dialogs. Keep success notices inline because they add useful context
  // without blocking the current task.
  if (error || !notice) return null

  return (
    <div
      role="status"
      className="mt-6 rounded-xl border border-emerald-200 bg-emerald-50 px-4 py-3"
    >
      <p className="text-sm font-semibold text-emerald-800">Done</p>
      <p className="mt-1 break-words text-sm font-medium leading-5 text-emerald-700">{notice}</p>
    </div>
  )
}
