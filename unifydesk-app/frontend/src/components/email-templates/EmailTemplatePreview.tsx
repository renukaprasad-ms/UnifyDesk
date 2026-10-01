type Props = {
  html: string
  title?: string
  compact?: boolean
}

export default function EmailTemplatePreview({
  html,
  title = 'Email preview',
  compact = false,
}: Props) {
  return (
    <div className="overflow-hidden rounded-2xl border border-slate-200 bg-slate-100">
      <div className="flex items-center justify-between border-b border-slate-200 bg-white px-3 py-2">
        <span className="text-[10px] font-bold uppercase tracking-[0.08em] text-slate-400">
          {title}
        </span>
        <span className="text-[10px] text-slate-400">Sandboxed</span>
      </div>
      <iframe
        title={title}
        sandbox=""
        srcDoc={html}
        className={compact ? 'h-72 w-full bg-white' : 'h-[620px] w-full bg-white'}
      />
    </div>
  )
}
