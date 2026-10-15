const statusClasses: Record<string, string> = {
  OPEN: 'bg-sky-50 text-sky-700',
  IN_PROGRESS: 'bg-indigo-50 text-indigo-700',
  DONE: 'bg-emerald-50 text-emerald-700',
  CANCELLED: 'bg-slate-100 text-slate-600',
}
export const taskStatusClass = (status: string) => statusClasses[status] ?? statusClasses.OPEN
export const taskLabel = (value: string) =>
  value
    .replaceAll('_', ' ')
    .toLowerCase()
    .replace(/^./, (char) => char.toUpperCase())
