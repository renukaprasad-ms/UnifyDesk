import { useState, type FormEvent } from 'react'
import { FiX } from 'react-icons/fi'
import type { CreateGoogleResponsiveSearchAdInput } from '../../features/ads/types'

type Props = {
  open: boolean
  saving: boolean
  onClose: () => void
  onCreate: (input: CreateGoogleResponsiveSearchAdInput) => Promise<void>
}

const lines = (value: string) =>
  value.split('\n').map((item) => item.trim()).filter(Boolean)

export default function GoogleResponsiveSearchAdModal({ open, saving, onClose, onCreate }: Props) {
  const [name, setName] = useState('')
  const [finalUrl, setFinalUrl] = useState('')
  const [headlines, setHeadlines] = useState('')
  const [descriptions, setDescriptions] = useState('')
  const [path1, setPath1] = useState('')
  const [path2, setPath2] = useState('')

  if (!open) return null

  const headlineValues = lines(headlines)
  const descriptionValues = lines(descriptions)

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    await onCreate({
      name: name.trim(),
      finalUrl: finalUrl.trim(),
      headlines: headlineValues,
      descriptions: descriptionValues,
      path1: path1.trim() || null,
      path2: path2.trim() || null,
    })
  }

  return (
    <div className="mobile-modal-shell fixed inset-0 z-50 flex items-center justify-center bg-slate-950/40 p-4">
      <div className="mobile-sheet max-h-[92vh] w-full max-w-2xl overflow-y-auto rounded-2xl bg-white shadow-2xl">
        <div className="sticky top-0 flex items-start justify-between border-b border-slate-100 bg-white px-6 py-5">
          <div><p className="eyebrow">Google Ads</p><h2 className="mt-1 text-xl font-bold text-slate-950">Create responsive search ad</h2></div>
          <button type="button" className="rounded-lg p-2 text-slate-400 hover:bg-slate-100" onClick={onClose}><FiX size={18} /></button>
        </div>
        <form className="space-y-5 p-6" onSubmit={submit}>
          <label className="block text-xs font-semibold text-slate-700">Ad name<input className="form-input mt-2" value={name} required maxLength={255} onChange={(e) => setName(e.target.value)} /></label>
          <label className="block text-xs font-semibold text-slate-700">Final URL<input className="form-input mt-2" type="url" value={finalUrl} required onChange={(e) => setFinalUrl(e.target.value)} /></label>
          <label className="block text-xs font-semibold text-slate-700">
            Headlines
            <textarea className="form-input mt-2 min-h-36" value={headlines} placeholder={'One headline per line\nAt least 3, up to 15'} onChange={(e) => setHeadlines(e.target.value)} />
            <span className="mt-1 block font-normal text-slate-400">{headlineValues.length}/15 headlines · max 30 characters each</span>
          </label>
          <label className="block text-xs font-semibold text-slate-700">
            Descriptions
            <textarea className="form-input mt-2 min-h-28" value={descriptions} placeholder={'One description per line\nAt least 2, up to 4'} onChange={(e) => setDescriptions(e.target.value)} />
            <span className="mt-1 block font-normal text-slate-400">{descriptionValues.length}/4 descriptions · max 90 characters each</span>
          </label>
          <div className="grid gap-4 sm:grid-cols-2">
            <label className="block text-xs font-semibold text-slate-700">Display path 1<input className="form-input mt-2" value={path1} maxLength={15} onChange={(e) => setPath1(e.target.value)} /></label>
            <label className="block text-xs font-semibold text-slate-700">Display path 2<input className="form-input mt-2" value={path2} maxLength={15} onChange={(e) => setPath2(e.target.value)} /></label>
          </div>
          <div className="rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-xs leading-5 text-amber-800">The ad is created PAUSED and must be explicitly enabled after review.</div>
          <div className="flex justify-end gap-2 border-t border-slate-100 pt-5">
            <button type="button" className="secondary-button" onClick={onClose} disabled={saving}>Cancel</button>
            <button type="submit" className="primary-button" disabled={saving || !name.trim() || !finalUrl.trim() || headlineValues.length < 3 || descriptionValues.length < 2}>{saving ? 'Creating…' : 'Create paused ad'}</button>
          </div>
        </form>
      </div>
    </div>
  )
}
