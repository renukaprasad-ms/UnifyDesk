const stageClasses: Record<string, string> = {
  NEW: 'bg-sky-50 text-sky-700',
  CONTACTED: 'bg-indigo-50 text-indigo-700',
  QUALIFIED: 'bg-amber-50 text-amber-700',
  CONVERTED: 'bg-emerald-50 text-emerald-700',
  UNQUALIFIED: 'bg-slate-100 text-slate-600',
}

export const leadStageClass = (stage: string) =>
  stageClasses[stage] ?? 'bg-slate-100 text-slate-600'
export const leadDateTime = (value: string) =>
  new Date(value).toLocaleString(undefined, {
    dateStyle: 'medium',
    timeStyle: 'medium',
  })
