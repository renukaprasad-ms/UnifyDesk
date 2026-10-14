import apiClient from '../../lib/api/apiClient'
import type { ApiResponse } from '../../lib/api/types'
import type { AdAccount, AdCampaign, AdProviderConnection } from '../ads/types'
import type { BroadcastMessageSummary } from '../broadcast/types'
import type { ChannelConnection } from '../channel/types'
import type {
  ContactDashboardSummary,
  ContactSummary,
  ListSummary,
  SegmentDefinition,
} from '../contacts/types'
import type { InboxConversation, InboxPage } from '../inbox/types'
import type {
  ManagedEmailDeliverability,
  ManagedEmailOverview,
  ManagedEmailUsage,
} from '../managedEmail/types'
import type {
  ManagedSmsDeliveryHealth,
  ManagedSmsStatus,
  ManagedSmsUsage,
} from '../managedSms/types'

export type DashboardResponse = {
  contacts: {
    summary: ContactDashboardSummary | null
    recent: ContactSummary[]
    lists: ListSummary[]
    segments: SegmentDefinition[]
  }
  broadcasts: { total: number; recent: BroadcastMessageSummary[] }
  inbox: InboxPage<InboxConversation> | null
  connections: ChannelConnection[]
  managedEmail: {
    overview: ManagedEmailOverview | null
    usage: ManagedEmailUsage | null
    deliverability: ManagedEmailDeliverability | null
  }
  managedSms: {
    status: ManagedSmsStatus | null
    usage: ManagedSmsUsage | null
    health: ManagedSmsDeliveryHealth | null
  }
  ads: {
    connections: AdProviderConnection[]
    accounts: AdAccount[]
    campaigns: AdCampaign[]
    campaignTotal: number
  }
  unavailableSections: string[]
}

export type DashboardSection = 'core' | 'channels' | 'ads'

export const dashboardApi = {
  async get(companyId: string, section?: DashboardSection) {
    const response = await apiClient.get<ApiResponse<DashboardResponse>>(
      `/api/v1/companies/${companyId}/dashboard${section ? `/${section}` : ''}`,
    )
    if (!response.data.status || !response.data.data) {
      throw new Error(response.data.error_message || 'Dashboard unavailable')
    }
    const data = response.data.data
    // The backend serializes long values as strings to preserve identifier precision.
    return {
      ...data,
      broadcasts: { ...data.broadcasts, total: Number(data.broadcasts.total) },
      ads: { ...data.ads, campaignTotal: Number(data.ads.campaignTotal) },
    }
  },
}

// Each callback runs when its own response arrives, before slower groups finish.
export const loadDashboardSections = async (
  companyId: string,
  onData: (section: DashboardSection, data: DashboardResponse) => void,
  onError: (section: DashboardSection) => void,
  onSettled: (section: DashboardSection) => void,
) => {
  await Promise.all(
    (['core', 'channels', 'ads'] as const).map(async (section) => {
      try {
        const data = await dashboardApi.get(companyId, section)
        onData(section, data)
      } catch {
        onError(section)
      } finally {
        onSettled(section)
      }
    }),
  )
}
