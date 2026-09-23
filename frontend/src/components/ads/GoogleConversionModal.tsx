import { useState, type FormEvent } from 'react'
import { FiX } from 'react-icons/fi'
import type { CreateGoogleConversionInput } from '../../features/ads/types'

type Props = { open: boolean; saving: boolean; onClose: () => void; onCreate: (input: CreateGoogleConversionInput) => Promise<void> }

const categories = ['PURCHASE', 'SUBMIT_LEAD_FORM', 'SIGNUP', 'CONTACT', 'PAGE_VIEW', 'DOWNLOAD', 'ADD_TO_CART', 'BEGIN_CHECKOUT', 'BOOK_APPOINTMENT', 'QUALIFIED_LEAD', 'CONVERTED_LEAD', 'DEFAULT']

export default function GoogleConversionModal({ open, saving, onClose, onCreate }: Props) {
  const [name, setName] = useState('')
  const [category, setCategory] = useState('PURCHASE')
  const [type, setType] = useState('WEBPAGE')
  const [defaultValue, setDefaultValue] = useState('')
  const [countingType, setCountingType] = useState<CreateGoogleConversionInput['countingType']>('ONE_PER_CLICK')
  if (!open) return null
  const submit = async (event: FormEvent) => {
    event.preventDefault()
    const numeric = defaultValue.trim() ? Number(defaultValue) : null
    await onCreate({ name: name.trim(), category, type, defaultValue: numeric != null && Number.isFinite(numeric) ? numeric : null, countingType })
  }
  return (
    <div className="mobile-modal-shell fixed inset-0 z-50 flex items-center justify-center bg-slate-950/40 p-4">
      <div className="mobile-sheet w-full max-w-xl rounded-2xl bg-white shadow-2xl">
        <div className="flex items-start justify-between border-b border-slate-100 px-6 py-5"><div><p className="eyebrow">Google Ads</p><h2 className="mt-1 text-xl font-bold text-slate-950">Create conversion action</h2></div><button type="button" className="rounded-lg p-2 text-slate-400 hover:bg-slate-100" onClick={onClose}><FiX size={18} /></button></div>
        <form className="space-y-5 p-6" onSubmit={submit}>
          <label className="block text-xs font-semibold text-slate-700">Conversion name<input className="form-input mt-2" value={name} maxLength={255} required onChange={(e) => setName(e.target.value)} /></label>
          <div className="grid gap-4 sm:grid-cols-2">
            <label className="block text-xs font-semibold text-slate-700">Category<select className="form-input mt-2" value={category} onChange={(e) => setCategory(e.target.value)}>{categories.map((value) => <option key={value} value={value}>{value.replaceAll('_', ' ')}</option>)}</select></label>
            <label className="block text-xs font-semibold text-slate-700">Type<select className="form-input mt-2" value={type} onChange={(e) => setType(e.target.value)}><option value="WEBPAGE">Website</option><option value="UPLOAD_CLICKS">Upload clicks</option><option value="UPLOAD_CALLS">Upload calls</option><option value="GOOGLE_HOSTED">Google hosted</option></select></label>
            <label className="block text-xs font-semibold text-slate-700">Count<select className="form-input mt-2" value={countingType} onChange={(e) => setCountingType(e.target.value as CreateGoogleConversionInput['countingType'])}><option value="ONE_PER_CLICK">One</option><option value="MANY_PER_CLICK">Every</option></select></label>
            <label className="block text-xs font-semibold text-slate-700">Default value<input className="form-input mt-2" type="number" min="0" step="0.01" value={defaultValue} placeholder="Optional" onChange={(e) => setDefaultValue(e.target.value)} /></label>
          </div>
          <div className="flex justify-end gap-2 border-t border-slate-100 pt-5"><button type="button" className="secondary-button" onClick={onClose} disabled={saving}>Cancel</button><button type="submit" className="primary-button" disabled={saving || !name.trim()}>{saving ? 'Creating…' : 'Create conversion'}</button></div>
        </form>
      </div>
    </div>
  )
}
