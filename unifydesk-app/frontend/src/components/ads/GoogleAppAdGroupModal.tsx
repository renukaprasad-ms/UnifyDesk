import { useSnackbarState } from '../feedback/useSnackbarState'
import { useEffect, useMemo, useState, type FormEvent } from 'react'
import { FiX } from 'react-icons/fi'
import { adCenterApi } from '../../features/ads/adCenterApi'
import type {
  AdAccount,
  AdAudience,
  AdCampaign,
  CreateGoogleAppAdGroupInput,
} from '../../features/ads/types'
import { apiErrorMessage } from '../../lib/api/apiClient'

type Props = {
  companyId: string
  account: AdAccount
  campaign: AdCampaign
  open: boolean
  saving: boolean
  onClose: () => void
  onCreate: (input: CreateGoogleAppAdGroupInput) => Promise<void>
}

const toMinor = (value: string, currency?: string | null) => {
  if (!value.trim()) return null
  const numeric = Number(value)
  if (!Number.isFinite(numeric) || numeric <= 0) return null
  if (!currency) return Math.round(numeric)
  try {
    const digits =
      new Intl.NumberFormat(undefined, { style: 'currency', currency }).resolvedOptions()
        .maximumFractionDigits ?? 2
    return Math.round(numeric * 10 ** digits)
  } catch {
    return Math.round(numeric)
  }
}

export default function GoogleAppAdGroupModal({
  companyId,
  account,
  campaign,
  open,
  saving,
  onClose,
  onCreate,
}: Props) {
  const engagement = campaign.buyingType === 'APP_CAMPAIGN_FOR_ENGAGEMENT'
  const [name, setName] = useState('')
  const [targetCpa, setTargetCpa] = useState('')
  const [audiences, setAudiences] = useState<AdAudience[]>([])
  const [userListId, setUserListId] = useState('')
  const [, setError] = useSnackbarState('', 'error')

  const eligibleAudiences = useMemo(
    () =>
      audiences.filter(
        (item) => item.provider === 'GOOGLE_ADS' && item.accessible && item.externalAudienceId,
      ),
    [audiences],
  )

  useEffect(() => {
    if (!open || !engagement) return
    setError('')
    void adCenterApi
      .audiences(companyId, account.id)
      .then((items) => {
        setAudiences(items)
        setUserListId((current) =>
          items.some((item) => item.externalAudienceId === current)
            ? current
            : (items.find(
                (item) =>
                  item.provider === 'GOOGLE_ADS' && item.accessible && item.externalAudienceId,
              )?.externalAudienceId ?? ''),
        )
      })
      .catch((loadError) =>
        setError(
          apiErrorMessage(
            loadError,
            'Unable to load Google user lists. Sync audiences in Ad Center and try again.',
          ),
        ),
      )
  }, [open, engagement, companyId, account.id])

  if (!open) return null

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    await onCreate({
      name: name.trim(),
      targetCpaMinor: engagement ? toMinor(targetCpa, account.currency) : null,
      userListId: engagement ? userListId : null,
    })
  }

  return (
    <div className="mobile-modal-shell fixed inset-0 z-50 flex items-center justify-center bg-slate-950/40 p-4">
      <div className="mobile-sheet w-full max-w-xl rounded-2xl bg-white shadow-2xl">
        <div className="flex items-start justify-between border-b border-slate-100 px-6 py-5">
          <div>
            <p className="eyebrow">Google Ads · App</p>
            <h2 className="mt-1 text-xl font-bold text-slate-950">Create App ad group</h2>
            <p className="mt-1 text-sm text-slate-500">
              {engagement
                ? 'Engagement ad groups require an existing Google user list.'
                : 'Bidding remains controlled by the App campaign optimization goal.'}
            </p>
          </div>
          <button
            type="button"
            className="rounded-lg p-2 text-slate-400 hover:bg-slate-100"
            onClick={onClose}
          >
            <FiX size={18} aria-hidden="true" />
          </button>
        </div>

        <form className="space-y-5 p-6" onSubmit={submit}>


          <label className="block text-xs font-semibold text-slate-700">
            Ad group name
            <input
              className="form-input mt-2"
              value={name}
              maxLength={255}
              required
              autoFocus
              onChange={(event) => setName(event.target.value)}
            />
          </label>

          {engagement && (
            <>
              <label className="block text-xs font-semibold text-slate-700">
                Google user list
                <select
                  className="form-input mt-2"
                  value={userListId}
                  required
                  disabled={eligibleAudiences.length === 0}
                  onChange={(event) => setUserListId(event.target.value)}
                >
                  {eligibleAudiences.length === 0 ? (
                    <option value="">No accessible Google user list</option>
                  ) : (
                    eligibleAudiences.map((audience) => (
                      <option key={audience.id} value={audience.externalAudienceId}>
                        {audience.name} · {audience.externalAudienceId}
                      </option>
                    ))
                  )}
                </select>
              </label>

              <label className="block text-xs font-semibold text-slate-700">
                Ad-group target CPA {account.currency ? `(${account.currency})` : ''}
                <input
                  className="form-input mt-2"
                  type="number"
                  min="0.01"
                  step="0.01"
                  value={targetCpa}
                  placeholder="Optional override"
                  onChange={(event) => setTargetCpa(event.target.value)}
                />
              </label>
            </>
          )}

          <div className="rounded-xl border border-indigo-200 bg-indigo-50 px-4 py-3 text-xs leading-5 text-indigo-800">
            The ad group is created paused. Google App campaigns do not use a manually selected
            ad-group type, and non-engagement App campaigns do not support an ad-group bid override.
          </div>

          <div className="flex justify-end gap-2 border-t border-slate-100 pt-5">
            <button type="button" className="secondary-button" disabled={saving} onClick={onClose}>
              Cancel
            </button>
            <button
              type="submit"
              className="primary-button"
              disabled={saving || !name.trim() || (engagement && !userListId)}
            >
              {saving ? 'Creating…' : 'Create paused App ad group'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}
