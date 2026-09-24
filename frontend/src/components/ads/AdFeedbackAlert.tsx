type Props = {
  error?: string | null
  notice?: string | null
}

export default function AdFeedbackAlert({ error, notice }: Props) {
  const message = error || notice
  if (!message) return null

  const isError = Boolean(error)

  return (
    <div
      role={isError ? 'alert' : 'status'}
      className={
        'mt-6 rounded-xl border px-4 py-3 ' +
        (isError
          ? 'border-red-200 bg-red-50 text-red-800'
          : 'border-emerald-200 bg-emerald-50 text-emerald-800')
      }
    >
      <p className="text-sm font-semibold">
        {isError ? 'Action needed' : 'Done'}
      </p>
      <p className="mt-1 break-words text-sm font-medium leading-5">
        {message}
      </p>
    </div>
  )
}
