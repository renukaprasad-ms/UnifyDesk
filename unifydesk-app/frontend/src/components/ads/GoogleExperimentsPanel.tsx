import { useEffect, useMemo, useState, type FormEvent } from 'react'
import { FiArrowUpCircle, FiPlay, FiPlusCircle, FiRefreshCw, FiSquare } from 'react-icons/fi'
import { adCenterApi } from '../../features/ads/adCenterApi'
import type {
  AdCampaign,
  GoogleExperiment,
  CreateGoogleExperimentInput,
} from '../../features/ads/types'
import { apiErrorMessage } from '../../lib/api/apiClient'
import ConfirmDialog from '../ui/ConfirmDialog'
import StatusBadge from '../ui/StatusBadge'

type Props = {
  companyId: string
  accountId: string
  currency?: string | null
  canCreate: boolean
  canModify: boolean
}

const pretty = (value?: string | null) =>
  value
    ? value
        .replaceAll('_', ' ')
        .toLowerCase()
        .replace(/\b\w/g, (character) => character.toUpperCase())
    : '—'

const formatNumber = (value?: number | null, digits = 2) =>
  value == null
    ? '—'
    : new Intl.NumberFormat(undefined, { maximumFractionDigits: digits }).format(value)

const moneyMicros = (micros: number, currency?: string | null) => {
  const value = micros / 1_000_000
  if (!currency) return formatNumber(value)
  try {
    return new Intl.NumberFormat(undefined, {
      style: 'currency',
      currency,
      maximumFractionDigits: 2,
    }).format(value)
  } catch {
    return `${formatNumber(value)} ${currency}`
  }
}

export default function GoogleExperimentsPanel({
  companyId,
  accountId,
  currency,
  canCreate,
  canModify,
}: Props) {
  const [experiments, setExperiments] = useState<GoogleExperiment[]>([])
  const [campaigns, setCampaigns] = useState<AdCampaign[]>([])
  const [loading, setLoading] = useState(false)
  const [busy, setBusy] = useState('')
  const [error, setError] = useState('')
  const [showCreate, setShowCreate] = useState(false)
  const [showCampaignMix, setShowCampaignMix] = useState(false)
  const [pendingAction, setPendingAction] = useState<{ experiment: GoogleExperiment; type: 'promote' | 'end' | 'graduate' } | null>(null)
  const [mixName, setMixName] = useState('')
  const [mixControlId, setMixControlId] = useState('')
  const [mixTreatmentId, setMixTreatmentId] = useState('')

  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
  const [baseCampaignId, setBaseCampaignId] = useState('')
  const [treatmentTrafficPercent, setTreatmentTrafficPercent] = useState(50)
  const [treatmentBidStrategy, setTreatmentBidStrategy] =
    useState<CreateGoogleExperimentInput['treatmentBidStrategy']>('MAXIMIZE_CONVERSIONS')
  const [startDate, setStartDate] = useState('')
  const [endDate, setEndDate] = useState('')

  const searchCampaigns = useMemo(
    () =>
      campaigns.filter(
        (campaign) =>
          campaign.provider === 'GOOGLE_ADS' &&
          campaign.adAccountId === accountId &&
          campaign.accessible &&
          campaign.objective === 'SEARCH' &&
          campaign.status !== 'REMOVED' &&
          campaign.status !== 'ARCHIVED',
      ),
    [campaigns, accountId],
  )

  const load = async () => {
    setLoading(true)
    setError('')
    try {
      const [experimentData, campaignPage] = await Promise.all([
        adCenterApi.googleExperiments(companyId, accountId),
        adCenterApi.campaigns(companyId, accountId, 0, 100),
      ])
      setExperiments(experimentData)
      setCampaigns(campaignPage.content)
      const eligibleCampaigns = campaignPage.content.filter(
        (campaign) =>
          campaign.provider === 'GOOGLE_ADS' &&
          campaign.adAccountId === accountId &&
          campaign.accessible &&
          campaign.status !== 'REMOVED' &&
          campaign.status !== 'ARCHIVED',
      )
      setBaseCampaignId(
        (current) =>
          current ||
          eligibleCampaigns.find((campaign) => campaign.objective === 'SEARCH')?.id ||
          '',
      )
      setMixControlId((current) => current || eligibleCampaigns[0]?.id || '')
      setMixTreatmentId(
        (current) => current || eligibleCampaigns[1]?.id || eligibleCampaigns[0]?.id || '',
      )
    } catch (loadError) {
      setError(apiErrorMessage(loadError, 'Unable to load Google Ads experiments'))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    void load()
  }, [companyId, accountId])

  const create = async (event: FormEvent) => {
    event.preventDefault()
    if (!baseCampaignId) {
      setError('Choose a Google Search campaign for the control arm.')
      return
    }
    setBusy('create')
    setError('')
    try {
      await adCenterApi.createGoogleExperiment(companyId, accountId, {
        name: name.trim(),
        description: description.trim() || null,
        baseCampaignId,
        treatmentTrafficPercent,
        treatmentBidStrategy,
        suffix: '[UnifyDesk experiment]',
        startDate: startDate || null,
        endDate: endDate || null,
      })
      setName('')
      setDescription('')
      setStartDate('')
      setEndDate('')
      setShowCreate(false)
      await load()
    } catch (createError) {
      setError(apiErrorMessage(createError, 'Unable to create Google experiment'))
    } finally {
      setBusy('')
      setPendingAction(null)
    }
  }

  const createCampaignMix = async (event: FormEvent) => {
    event.preventDefault()
    if (!mixControlId || !mixTreatmentId || mixControlId === mixTreatmentId) {
      setError('Choose two different Google campaigns for the campaign-mix experiment.')
      return
    }
    setBusy('mix')
    setError('')
    try {
      await adCenterApi.createGoogleCampaignMixExperiment(companyId, accountId, {
        name: mixName.trim(),
        controlCampaignIds: [mixControlId],
        treatmentCampaignIds: [mixTreatmentId],
        treatmentTrafficPercent,
        startDate: startDate || null,
        endDate: endDate || null,
      })
      setMixName('')
      setShowCampaignMix(false)
      await load()
    } catch (createError) {
      setError(apiErrorMessage(createError, 'Unable to create Google campaign-mix experiment'))
    } finally {
      setBusy('')
    }
  }

  const action = async (
    experiment: GoogleExperiment,
    type: 'schedule' | 'promote' | 'end' | 'graduate',
  ) => {
    setBusy(`${type}:${experiment.id}`)
    setError('')
    try {
      if (type === 'schedule') {
        await adCenterApi.scheduleGoogleExperiment(companyId, accountId, experiment.id)
      } else if (type === 'promote') {
        await adCenterApi.promoteGoogleExperiment(companyId, accountId, experiment.id)
      } else if (type === 'graduate') {
        await adCenterApi.graduateGoogleExperiment(companyId, accountId, experiment.id)
      } else {
        await adCenterApi.endGoogleExperiment(companyId, accountId, experiment.id)
      }
      await load()
    } catch (actionError) {
      setError(apiErrorMessage(actionError, `Unable to ${type} Google experiment`))
    } finally {
      setBusy('')
    }
  }

  return (
    <section className="panel mt-6 overflow-hidden">
      <div className="flex flex-wrap items-start justify-between gap-3 border-b border-slate-100 px-5 py-4">
        <div>
          <h2 className="text-base font-bold text-slate-950">Google experiments</h2>
          <p className="mt-1 max-w-3xl text-xs leading-5 text-slate-500">
            Create a Search custom experiment from an existing Search campaign, split traffic
            between control and treatment, then schedule, report, end, or promote it through
            Google's native experiment lifecycle.
          </p>
        </div>
        <div className="flex gap-2">
          <button
            type="button"
            className="secondary-button inline-flex items-center gap-2"
            disabled={loading}
            onClick={() => void load()}
          >
            <FiRefreshCw className={loading ? 'animate-spin' : ''} aria-hidden="true" />
            Refresh
          </button>
          {canCreate && (
            <>
              <button
                type="button"
                className="secondary-button"
                onClick={() => setShowCampaignMix((current) => !current)}
              >
                Campaign mix
              </button>
              <button
                type="button"
                className="primary-button inline-flex items-center gap-2"
                onClick={() => setShowCreate((current) => !current)}
              >
                <FiPlusCircle aria-hidden="true" />
                Search experiment
              </button>
            </>
          )}
        </div>
      </div>

      <div className="border-b border-amber-100 bg-amber-50 px-5 py-3 text-xs leading-5 text-amber-800">
        UnifyDesk supports system-managed <strong>SEARCH_CUSTOM</strong> experiments and Google
        <strong> campaign-mix</strong> experiments. Campaign mix is account/allowlist dependent.
        Other specialized Google experiment types remain visible for reporting even when their
        provider-specific setup is completed in Google Ads.
      </div>

      {error && (
        <div className="m-5 rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
          {error}
        </div>
      )}

      {showCreate && canCreate && (
        <form
          className="grid gap-4 border-b border-slate-100 bg-slate-50/60 p-5 md:grid-cols-2"
          onSubmit={create}
        >
          <label className="block text-xs font-semibold text-slate-700">
            Experiment name
            <input
              className="form-input mt-2"
              value={name}
              maxLength={1024}
              required
              onChange={(event) => setName(event.target.value)}
              placeholder="Search bidding test"
            />
          </label>

          <label className="block text-xs font-semibold text-slate-700">
            Control campaign
            <select
              className="form-input mt-2"
              value={baseCampaignId}
              required
              onChange={(event) => setBaseCampaignId(event.target.value)}
            >
              <option value="">Choose Search campaign</option>
              {searchCampaigns.map((campaign) => (
                <option key={campaign.id} value={campaign.id}>
                  {campaign.name} · {pretty(campaign.bidStrategy)}
                </option>
              ))}
            </select>
          </label>

          <label className="block text-xs font-semibold text-slate-700">
            Treatment bidding strategy
            <select
              className="form-input mt-2"
              value={treatmentBidStrategy}
              onChange={(event) =>
                setTreatmentBidStrategy(
                  event.target.value as CreateGoogleExperimentInput['treatmentBidStrategy'],
                )
              }
            >
              <option value="MANUAL_CPC">Manual CPC</option>
              <option value="MAXIMIZE_CLICKS">Maximize clicks</option>
              <option value="MAXIMIZE_CONVERSIONS">Maximize conversions</option>
              <option value="MAXIMIZE_CONVERSION_VALUE">Maximize conversion value</option>
            </select>
          </label>

          <label className="block text-xs font-semibold text-slate-700">
            Treatment traffic · {treatmentTrafficPercent}%
            <input
              className="mt-3 w-full"
              type="range"
              min={1}
              max={99}
              value={treatmentTrafficPercent}
              onChange={(event) => setTreatmentTrafficPercent(Number(event.target.value))}
            />
            <span className="mt-1 block text-[11px] text-slate-400">
              Control receives {100 - treatmentTrafficPercent}%.
            </span>
          </label>

          <label className="block text-xs font-semibold text-slate-700">
            Start date
            <input
              className="form-input mt-2"
              type="date"
              value={startDate}
              onChange={(event) => setStartDate(event.target.value)}
            />
          </label>

          <label className="block text-xs font-semibold text-slate-700">
            End date
            <input
              className="form-input mt-2"
              type="date"
              min={startDate || undefined}
              value={endDate}
              onChange={(event) => setEndDate(event.target.value)}
            />
          </label>

          <label className="block text-xs font-semibold text-slate-700 md:col-span-2">
            Description
            <textarea
              className="form-input mt-2"
              value={description}
              maxLength={2048}
              onChange={(event) => setDescription(event.target.value)}
              placeholder="What hypothesis are you testing?"
            />
          </label>

          {searchCampaigns.length === 0 && (
            <div className="md:col-span-2 rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-xs text-amber-800">
              No accessible Google Search campaign is synchronized for this account. Sync campaigns
              or create a Search campaign before starting an E1 experiment.
            </div>
          )}

          <div className="flex justify-end gap-2 md:col-span-2">
            <button type="button" className="secondary-button" onClick={() => setShowCreate(false)}>
              Cancel
            </button>
            <button
              type="submit"
              className="primary-button"
              disabled={busy === 'create' || !baseCampaignId || !name.trim()}
            >
              {busy === 'create' ? 'Creating…' : 'Create in setup'}
            </button>
          </div>
        </form>
      )}

      {showCampaignMix && canCreate && (
        <form
          className="grid gap-4 border-b border-slate-100 bg-indigo-50/40 p-5 md:grid-cols-2"
          onSubmit={createCampaignMix}
        >
          <label className="block text-xs font-semibold text-slate-700 md:col-span-2">
            Campaign-mix experiment name
            <input
              className="form-input mt-2"
              value={mixName}
              onChange={(event) => setMixName(event.target.value)}
              required
              placeholder="Channel strategy comparison"
            />
          </label>
          <label className="block text-xs font-semibold text-slate-700">
            Control campaign
            <select
              className="form-input mt-2"
              value={mixControlId}
              onChange={(event) => setMixControlId(event.target.value)}
              required
            >
              <option value="">Choose campaign</option>
              {campaigns
                .filter((campaign) => campaign.accessible && campaign.status !== 'REMOVED')
                .map((campaign) => (
                  <option key={campaign.id} value={campaign.id}>
                    {campaign.name} · {pretty(campaign.objective)}
                  </option>
                ))}
            </select>
          </label>
          <label className="block text-xs font-semibold text-slate-700">
            Treatment campaign
            <select
              className="form-input mt-2"
              value={mixTreatmentId}
              onChange={(event) => setMixTreatmentId(event.target.value)}
              required
            >
              <option value="">Choose different campaign</option>
              {campaigns
                .filter((campaign) => campaign.accessible && campaign.status !== 'REMOVED')
                .map((campaign) => (
                  <option key={campaign.id} value={campaign.id}>
                    {campaign.name} · {pretty(campaign.objective)}
                  </option>
                ))}
            </select>
          </label>
          <label className="block text-xs font-semibold text-slate-700 md:col-span-2">
            Treatment traffic · {treatmentTrafficPercent}%
            <input
              className="mt-3 w-full"
              type="range"
              min={1}
              max={99}
              value={treatmentTrafficPercent}
              onChange={(event) => setTreatmentTrafficPercent(Number(event.target.value))}
            />
          </label>
          <p className="text-[11px] leading-5 text-slate-500 md:col-span-2">
            Google campaign-mix experiments compare existing campaigns and are currently
            allowlist/account dependent. UnifyDesk submits the native COMPARE_CAMPAIGNS contract and
            surfaces provider rejection rather than substituting another experiment type.
          </p>
          <div className="flex justify-end gap-2 md:col-span-2">
            <button
              type="button"
              className="secondary-button"
              onClick={() => setShowCampaignMix(false)}
            >
              Cancel
            </button>
            <button
              type="submit"
              className="primary-button"
              disabled={busy === 'mix' || !mixName.trim() || mixControlId === mixTreatmentId}
            >
              {busy === 'mix' ? 'Creating…' : 'Create campaign mix'}
            </button>
          </div>
        </form>
      )}

      {loading ? (
        <div className="p-10 text-center text-sm text-slate-500">Loading Google experiments…</div>
      ) : experiments.length === 0 ? (
        <div className="p-10 text-center">
          <h3 className="text-sm font-bold text-slate-900">No Google experiments yet</h3>
          <p className="mt-2 text-xs text-slate-500">
            Create a Search custom experiment or refresh after creating an experiment in Google Ads.
          </p>
        </div>
      ) : (
        <div className="divide-y divide-slate-100">
          {experiments.map((experiment) => {
            const metrics = experiment.metrics
            const canSchedule = canModify && experiment.status === 'SETUP'
            const canOperate = canModify && experiment.status === 'ENABLED'
            return (
              <article key={experiment.resourceName} className="p-5">
                <div className="flex flex-wrap items-start justify-between gap-3">
                  <div>
                    <div className="flex flex-wrap items-center gap-2">
                      <h3 className="font-bold text-slate-950">{experiment.name}</h3>
                      <span
                        className={`rounded-full px-2.5 py-1 text-[11px] font-bold ${statusClass(experiment.status)}`}
                      >
                        {pretty(experiment.status)}
                      </span>
                      <span className="rounded-full bg-slate-100 px-2.5 py-1 text-[11px] font-semibold text-slate-600">
                        {pretty(experiment.type)}
                      </span>
                    </div>
                    {experiment.description && (
                      <p className="mt-2 text-xs leading-5 text-slate-500">
                        {experiment.description}
                      </p>
                    )}
                    <p className="mt-2 text-[11px] text-slate-400">
                      {experiment.startDate || 'No start date'} →{' '}
                      {experiment.endDate || 'No end date'}
                      {experiment.longRunningOperation ? ' · Provider operation in progress' : ''}
                    </p>
                  </div>

                  <div className="flex flex-wrap gap-2">
                    {canSchedule && (
                      <button
                        type="button"
                        className="secondary-button inline-flex items-center gap-2"
                        disabled={Boolean(busy)}
                        onClick={() => void action(experiment, 'schedule')}
                      >
                        <FiPlay aria-hidden="true" />
                        Schedule
                      </button>
                    )}
                    {canOperate && (
                      <>
                        <button
                          type="button"
                          className="secondary-button inline-flex items-center gap-2"
                          disabled={Boolean(busy)}
                          onClick={() => setPendingAction({ experiment, type: 'end' })}
                        >
                          <FiSquare aria-hidden="true" />
                          End
                        </button>
                        <button
                          type="button"
                          className="primary-button inline-flex items-center gap-2"
                          disabled={Boolean(busy)}
                          onClick={() => setPendingAction({ experiment, type: 'promote' })}
                        >
                          <FiArrowUpCircle aria-hidden="true" />
                          Promote treatment
                        </button>
                        <button
                          type="button"
                          className="secondary-button"
                          disabled={Boolean(busy)}
                          onClick={() => setPendingAction({ experiment, type: 'graduate' })}
                        >
                          Graduate
                        </button>
                      </>
                    )}
                  </div>
                </div>

                <div className="mt-4 grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
                  {experiment.arms.map((arm) => (
                    <div
                      key={arm.resourceName ?? arm.name}
                      className="rounded-xl border border-slate-100 p-3"
                    >
                      <p className="text-xs font-bold text-slate-900">
                        {arm.name || (arm.control ? 'Control' : 'Treatment')}
                      </p>
                      <p className="mt-1 text-xl font-bold text-slate-950">{arm.trafficSplit}%</p>
                      <p className="mt-1 text-[11px] text-slate-400">
                        {arm.control ? 'Control arm' : 'Treatment arm'} ·{' '}
                        {arm.campaigns.length || arm.inDesignCampaigns.length} campaign
                      </p>
                    </div>
                  ))}
                </div>

                <div className="mt-4 overflow-x-auto rounded-xl border border-slate-100">
                  <table className="w-full min-w-[760px] text-left text-xs">
                    <thead className="bg-slate-50 text-slate-500">
                      <tr>
                        <th className="px-4 py-3">Metric</th>
                        <th className="px-4 py-3 text-right">Experiment</th>
                        <th className="px-4 py-3 text-right">Control</th>
                        <th className="px-4 py-3 text-right">Google statistical signal</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-slate-100">
                      <tr>
                        <td className="px-4 py-3 font-semibold">Clicks</td>
                        <td className="px-4 py-3 text-right">{formatNumber(metrics.clicks, 0)}</td>
                        <td className="px-4 py-3 text-right">
                          {formatNumber(metrics.controlClicks, 0)}
                        </td>
                        <td className="px-4 py-3 text-right">
                          p {formatNumber(metrics.clicksPValue, 4)} · ±{' '}
                          {formatNumber(metrics.clicksMarginOfError, 3)}
                        </td>
                      </tr>
                      <tr>
                        <td className="px-4 py-3 font-semibold">Conversions</td>
                        <td className="px-4 py-3 text-right">
                          {formatNumber(metrics.conversions)}
                        </td>
                        <td className="px-4 py-3 text-right">
                          {formatNumber(metrics.controlConversions)}
                        </td>
                        <td className="px-4 py-3 text-right">
                          p {formatNumber(metrics.conversionsAbsoluteChangePValue, 4)} · ±{' '}
                          {formatNumber(metrics.conversionsAbsoluteChangeMarginOfError, 3)}
                        </td>
                      </tr>
                      <tr>
                        <td className="px-4 py-3 font-semibold">Spend</td>
                        <td className="px-4 py-3 text-right">
                          {moneyMicros(metrics.costMicros, currency)}
                        </td>
                        <td className="px-4 py-3 text-right">
                          {moneyMicros(metrics.controlCostMicros, currency)}
                        </td>
                        <td className="px-4 py-3 text-right text-slate-400">Provider reported</td>
                      </tr>
                      <tr>
                        <td className="px-4 py-3 font-semibold">Impressions</td>
                        <td className="px-4 py-3 text-right">
                          {formatNumber(metrics.impressions, 0)}
                        </td>
                        <td className="px-4 py-3 text-right">
                          {formatNumber(metrics.controlImpressions, 0)}
                        </td>
                        <td className="px-4 py-3 text-right text-slate-400">Provider reported</td>
                      </tr>
                    </tbody>
                  </table>
                </div>

                {experiment.promoteStatus && experiment.promoteStatus !== 'UNSPECIFIED' && (
                  <p className="mt-3 text-[11px] text-slate-400">
                    Promote status: {pretty(experiment.promoteStatus)}
                  </p>
                )}
              </article>
            )
          })}
        </div>
      )}
      <ConfirmDialog
        open={Boolean(pendingAction)}
        title={
          pendingAction?.type === 'promote'
            ? 'Promote this Google experiment?'
            : pendingAction?.type === 'graduate'
              ? 'Graduate the treatment campaign?'
              : 'End this Google experiment now?'
        }
        description={
          pendingAction?.type === 'promote'
            ? 'Google will apply the treatment changes to the base campaign. Continue only after reviewing the experiment results.'
            : pendingAction?.type === 'graduate'
              ? 'Google will create a production campaign budget mapping for the treatment campaign.'
              : 'The experiment will be ended at the provider.'
        }
        confirmLabel={
          pendingAction?.type === 'promote'
            ? 'Yes, promote'
            : pendingAction?.type === 'graduate'
              ? 'Yes, graduate'
              : 'Yes, end experiment'
        }
        cancelLabel="No, cancel"
        tone="warning"
        working={Boolean(pendingAction && busy === `${pendingAction.type}:${pendingAction.experiment.id}`)}
        onCancel={() => setPendingAction(null)}
        onConfirm={() => pendingAction && void action(pendingAction.experiment, pendingAction.type)}
      />
    </section>
  )
}
