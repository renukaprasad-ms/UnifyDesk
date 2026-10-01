import {
  FiActivity,
  FiBarChart2,
  FiClock,
  FiCreditCard,
  FiGrid,
  FiImage,
  FiLayers,
  FiSettings,
  FiShoppingBag,
  FiTarget,
  FiUsers,
  FiZap,
} from 'react-icons/fi'
import { useEffect, useState } from 'react'
import { NavLink } from 'react-router'
import { adCenterApi } from '../../features/ads/adCenterApi'
import { useAppSelector } from '../../store/hooks'
import { selectSelectedCompany } from '../../store/workspace/workspaceSlice'

const items = [
  { label: 'Overview', to: '/ad-center', icon: <FiGrid /> },
  { label: 'Unified campaigns', to: '/ad-center/unified-campaigns', icon: <FiLayers /> },
  { label: 'Provider campaigns', to: '/ad-center/campaigns', icon: <FiTarget /> },
  { label: 'Creatives', to: '/ad-center/creatives', icon: <FiImage /> },
  { label: 'Audiences', to: '/ad-center/audiences', icon: <FiUsers /> },
  { label: 'Conversions', to: '/ad-center/conversions', icon: <FiActivity /> },
  { label: 'Analytics', to: '/ad-center/analytics', icon: <FiBarChart2 /> },
  { label: 'Recommendations', to: '/ad-center/recommendations', icon: <FiZap /> },
  { label: 'Merchant Center', to: '/ad-center/merchant', icon: <FiShoppingBag /> },
  { label: 'Billing', to: '/ad-center/billing', icon: <FiCreditCard /> },
  { label: 'Activity', to: '/ad-center/activity', icon: <FiClock /> },
  { label: 'Settings', to: '/ad-center/settings', icon: <FiSettings /> },
]

export default function AdCenterSidebar({ onNavigate }: { onNavigate: () => void }) {
  const selectedCompany = useAppSelector(selectSelectedCompany)
  const [attentionCount, setAttentionCount] = useState(0)

  useEffect(() => {
    let active = true

    const loadAttention = async () => {
      if (!selectedCompany) {
        if (active) setAttentionCount(0)
        return
      }
      try {
        const summary = await adCenterApi.recommendationSummary(selectedCompany.companyId)
        if (active) setAttentionCount(summary.attentionCount)
      } catch {
        if (active) setAttentionCount(0)
      }
    }

    const handleChanged = () => void loadAttention()
    void loadAttention()
    window.addEventListener('ad-center:recommendations-changed', handleChanged)
    return () => {
      active = false
      window.removeEventListener('ad-center:recommendations-changed', handleChanged)
    }
  }, [selectedCompany?.companyId])

  const linkClass = ({ isActive }: { isActive: boolean }) =>
    `sidebar-link ${isActive ? 'sidebar-link-active' : ''}`

  return (
    <div className="flex min-h-full flex-col">
      <div className="px-3 pb-4 pt-2">
        <p className="sidebar-label">AD CENTER</p>
      </div>
      <nav aria-label="Ad Center navigation" className="space-y-1 px-2">
        {items.map((item) => (
          <NavLink
            key={item.to}
            to={item.to}
            end={item.to === '/ad-center'}
            className={linkClass}
            onClick={onNavigate}
          >
            <span className="sidebar-icon" aria-hidden="true">
              {item.icon}
            </span>
            <span>{item.label}</span>
            {item.to === '/ad-center/recommendations' && attentionCount > 0 && (
              <span className="ml-auto rounded-full bg-red-100 px-2 py-0.5 text-[10px] font-bold text-red-700">
                {attentionCount > 99 ? '99+' : attentionCount}
              </span>
            )}
          </NavLink>
        ))}
      </nav>
    </div>
  )
}
