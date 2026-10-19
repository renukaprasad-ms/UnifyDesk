import { useSnackbarState } from '../feedback/useSnackbarState'
import { useEffect, useMemo, useState, type FormEvent } from 'react'
import { FiRefreshCw, FiSave } from 'react-icons/fi'
import { adCenterApi } from '../../features/ads/adCenterApi'
import type {
  GooglePerformanceMaxAutomation,
  GooglePerformanceMaxVideoCrawlSource,
} from '../../features/ads/types'
import { apiErrorMessage } from '../../lib/api/apiClient'

type Props = {
  companyId: string
  campaignId: string
  canModify: boolean
}

const lines = (value: string) =>
  value
    .split(/\r?\n/)
    .map((item) => item.trim())
    .filter(Boolean)

const isOptedIn = (settings: GooglePerformanceMaxAutomation | null, type: string) =>
  settings?.automationStatuses?.[type] === 'OPTED_IN'

const crawlSourcesToText = (sources: GooglePerformanceMaxVideoCrawlSource[]) =>
  sources.map((source) => `${source.sourcePlatform}|${source.url}`).join('\n')

const parseCrawlSources = (value: string): GooglePerformanceMaxVideoCrawlSource[] =>
  lines(value).map((line) => {
    const separator = line.indexOf('|')
    if (separator <= 0) {
      throw new Error('Automated video crawl sources must use PLATFORM|URL, one per line.')
    }
    const sourcePlatform = line
      .slice(0, separator)
      .trim()
      .toUpperCase() as GooglePerformanceMaxVideoCrawlSource['sourcePlatform']
    if (!['LANDING_PAGE', 'SOCIAL', 'YOUTUBE'].includes(sourcePlatform)) {
      throw new Error('Video crawl platform must be LANDING_PAGE, SOCIAL, or YOUTUBE.')
    }
    return {
      sourcePlatform,
      url: line.slice(separator + 1).trim(),
      enabled: true,
    }
  })

export default function GooglePerformanceMaxAutomationPanel({
  companyId,
  campaignId,
  canModify,
}: Props) {
  const [settings, setSettings] = useState<GooglePerformanceMaxAutomation | null>(null)
  const [finalUrlExpansion, setFinalUrlExpansion] = useState(false)
  const [textAssetAutomation, setTextAssetAutomation] = useState(false)
  const [enhancedYoutubeVideos, setEnhancedYoutubeVideos] = useState(false)
  const [imageEnhancement, setImageEnhancement] = useState(false)
  const [automatedVideoCrawl, setAutomatedVideoCrawl] = useState(false)
  const [crawlSources, setCrawlSources] = useState('')
  const [urlExclusions, setUrlExclusions] = useState('')
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [, setError] = useSnackbarState('', 'error')
  const [, setNotice] = useSnackbarState('', 'success')

  const statusSummary = useMemo(
    () =>
      settings
        ? Object.entries(settings.automationStatuses)
            .map(([key, value]) => `${key.replaceAll('_', ' ')}: ${value}`)
            .join(' · ')
        : '',
    [settings],
  )

  const apply = (value: GooglePerformanceMaxAutomation) => {
    setSettings(value)
    setFinalUrlExpansion(isOptedIn(value, 'FINAL_URL_EXPANSION_TEXT_ASSET_AUTOMATION'))
    setTextAssetAutomation(isOptedIn(value, 'TEXT_ASSET_AUTOMATION'))
    setEnhancedYoutubeVideos(isOptedIn(value, 'GENERATE_ENHANCED_YOUTUBE_VIDEOS'))
    setImageEnhancement(isOptedIn(value, 'GENERATE_IMAGE_ENHANCEMENT'))
    setAutomatedVideoCrawl(isOptedIn(value, 'AUTOMATED_VIDEO_CRAWL'))
    setCrawlSources(crawlSourcesToText(value.automatedVideoCrawlSources))
    setUrlExclusions(value.urlExclusions.join('\n'))
  }

  const load = async () => {
    setLoading(true)
    setError('')
    try {
      apply(await adCenterApi.googlePerformanceMaxAutomation(companyId, campaignId))
    } catch (loadError) {
      setError(apiErrorMessage(loadError, 'Unable to load Performance Max automation settings'))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    void load()
  }, [companyId, campaignId])

  const save = async (event: FormEvent) => {
    event.preventDefault()
    setSaving(true)
    setError('')
    setNotice('')
    try {
      const updated = await adCenterApi.updateGooglePerformanceMaxAutomation(
        companyId,
        campaignId,
        {
          finalUrlExpansion,
          textAssetAutomation: finalUrlExpansion ? true : textAssetAutomation,
          enhancedYoutubeVideos,
          imageEnhancement,
          automatedVideoCrawl,
          automatedVideoCrawlSources: automatedVideoCrawl ? parseCrawlSources(crawlSources) : [],
          urlExclusions: lines(urlExclusions),
        },
      )
      apply(updated)
      setNotice('Performance Max automation settings saved to Google Ads.')
    } catch (saveError) {
      setError(apiErrorMessage(saveError, 'Unable to update Performance Max automation settings'))
    } finally {
      setSaving(false)
    }
  }

  return (
    <section className="panel mt-5 overflow-hidden">
      <div className="flex flex-wrap items-start justify-between gap-3 border-b border-slate-100 px-5 py-4">
        <div>
          <h2 className="text-base font-bold text-slate-950">Performance Max automation</h2>
          <p className="mt-1 max-w-3xl text-xs leading-5 text-slate-500">
            Control Final URL Expansion, generated assets, automated video crawl, and URL exclusions
            without recreating the campaign.
          </p>
          {statusSummary && (
            <p className="mt-2 text-[11px] leading-5 text-slate-400">{statusSummary}</p>
          )}
        </div>
        <button
          type="button"
          className="secondary-button inline-flex items-center gap-2"
          disabled={loading || saving}
          onClick={() => void load()}
        >
          <FiRefreshCw className={loading ? 'animate-spin' : ''} aria-hidden="true" />
          Refresh
        </button>
      </div>



      <form className="grid gap-5 p-5 lg:grid-cols-2" onSubmit={save}>
        <div className="space-y-3">
          {[
            {
              label: 'Final URL Expansion',
              description:
                'Allow Google to route traffic to more relevant landing pages from your site.',
              checked: finalUrlExpansion,
              onChange: (checked: boolean) => {
                setFinalUrlExpansion(checked)
                if (checked) setTextAssetAutomation(true)
              },
            },
            {
              label: 'Text asset automation',
              description:
                'Allow Google to create additional text assets. Required while Final URL Expansion is on.',
              checked: textAssetAutomation,
              disabled: finalUrlExpansion,
              onChange: setTextAssetAutomation,
            },
            {
              label: 'Enhanced YouTube videos',
              description:
                'Allow Google to create enhanced video variations when the account is eligible.',
              checked: enhancedYoutubeVideos,
              onChange: setEnhancedYoutubeVideos,
            },
            {
              label: 'Image enhancement',
              description: 'Allow eligible image enhancements generated by Google.',
              checked: imageEnhancement,
              onChange: setImageEnhancement,
            },
            {
              label: 'Automated video crawl',
              description:
                'Allow Google to discover source videos only from URLs you explicitly approve below.',
              checked: automatedVideoCrawl,
              onChange: setAutomatedVideoCrawl,
            },
          ].map((item) => (
            <label
              key={item.label}
              className="flex items-start justify-between gap-4 rounded-xl border border-slate-200 p-3"
            >
              <span>
                <span className="block text-sm font-semibold text-slate-900">{item.label}</span>
                <span className="mt-1 block text-xs leading-5 text-slate-500">
                  {item.description}
                </span>
              </span>
              <input
                type="checkbox"
                className="mt-1 h-4 w-4"
                checked={item.checked}
                disabled={loading || saving || !canModify || item.disabled}
                onChange={(event) => item.onChange(event.target.checked)}
              />
            </label>
          ))}
        </div>

        <div className="space-y-4">
          <label className="block text-xs font-semibold text-slate-700">
            Final URL Expansion exclusions
            <textarea
              className="form-input mt-2 min-h-32"
              value={urlExclusions}
              disabled={!canModify || loading || saving}
              onChange={(event) => setUrlExclusions(event.target.value)}
              placeholder={'/checkout\n/legal\nhttps://example.com/private'}
            />
            <span className="mt-1 block font-normal text-slate-500">
              One URL or URL fragment per line. Reachlyo synchronizes these as negative webpage
              criteria.
            </span>
          </label>

          <label className="block text-xs font-semibold text-slate-700">
            Approved automated video crawl sources
            <textarea
              className="form-input mt-2 min-h-32"
              value={crawlSources}
              disabled={!canModify || loading || saving || !automatedVideoCrawl}
              onChange={(event) => setCrawlSources(event.target.value)}
              placeholder={'LANDING_PAGE|https://example.com\nYOUTUBE|https://youtube.com/@example'}
            />
            <span className="mt-1 block font-normal leading-5 text-slate-500">
              Use PLATFORM|URL with LANDING_PAGE, SOCIAL, or YOUTUBE. Only add sources your business
              is authorized to use.
            </span>
          </label>

          {canModify && (
            <button
              type="submit"
              className="primary-button inline-flex items-center gap-2"
              disabled={loading || saving}
            >
              <FiSave aria-hidden="true" />
              {saving ? 'Saving…' : 'Save PMax automation'}
            </button>
          )}
        </div>
      </form>
    </section>
  )
}
