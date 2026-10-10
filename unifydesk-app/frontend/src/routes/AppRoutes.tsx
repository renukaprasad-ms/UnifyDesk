import { Navigate, Route, Routes } from 'react-router'
import WorkspaceEpochBoundary from '../components/workspace/WorkspaceEpochBoundary'
import { PERMISSIONS } from '../features/iam/permissions'
import AuthLayout from '../layouts/AuthLayout'
import MainLayout from '../layouts/MainLayout'
import AccountInvitations from '../pages/account/AccountInvitations'
import AccountNotificationSettings from '../pages/account/AccountNotificationSettings'
import AccountProfile from '../pages/account/AccountProfile'
import AccountSettings from '../pages/account/AccountSettings'
import ChangePassword from '../pages/account/ChangePassword'
import AdActivity from '../pages/ads/AdActivity'
import AdAnalytics from '../pages/ads/AdAnalytics'
import AdAudiences from '../pages/ads/AdAudiences'
import AdBilling from '../pages/ads/AdBilling'
import AdCampaignDetails from '../pages/ads/AdCampaignDetails'
import AdCampaigns from '../pages/ads/AdCampaigns'
import AdCenterOverview from '../pages/ads/AdCenterOverview'
import AdConversions from '../pages/ads/AdConversions'
import AdCreatives from '../pages/ads/AdCreatives'
import AdDetails from '../pages/ads/AdDetails'
import AdMerchantCenter from '../pages/ads/AdMerchantCenter'
import AdRecommendations from '../pages/ads/AdRecommendations'
import AdSetDetails from '../pages/ads/AdSetDetails'
import AdSettings from '../pages/ads/AdSettings'
import ReachlyoCampaignWizard from '../pages/ads/ReachlyoCampaignWizard'
import ReachlyoCampaigns from '../pages/ads/ReachlyoCampaigns'
import ForgotPassword from '../pages/auth/ForgotPassword'
import Login from '../pages/auth/Login'
import Register from '../pages/auth/Register'
import ResetPassword from '../pages/auth/ResetPassword'
import VerifyOtp from '../pages/auth/VerifyOtp'
import PaymentReturnPage from '../pages/billing/PaymentReturnPage'
import BroadcastWorkspace from '../pages/broadcast/BroadcastWorkspace'
import CompanyConnections from '../pages/company/CompanyConnections'
import CompanyDetails from '../pages/company/CompanyDetails'
import CompanyPlan from '../pages/company/CompanyPlan'
import CompanyRoles from '../pages/company/CompanyRoles'
import CompanyUsers from '../pages/company/CompanyUsers'
import ContactDetailPage from '../pages/contacts/ContactDetailPage'
import CrmOverview from '../pages/contacts/CrmOverview'
import CrmLeadsPage from '../pages/contacts/CrmLeadsPage'
import CrmCompaniesPage from '../pages/contacts/CrmCompaniesPage'
import CrmTasksPage from '../pages/contacts/CrmTasksPage'
import ContactsPage from '../pages/contacts/ContactsPage'
import ContactSettingsPage from '../pages/contacts/ContactSettingsPage'
import ImportDetailPage from '../pages/contacts/ImportDetailPage'
import ImportsPage from '../pages/contacts/ImportsPage'
import ListDetailPage from '../pages/contacts/ListDetailPage'
import ListsPage from '../pages/contacts/ListsPage'
import SegmentEditorPage from '../pages/contacts/SegmentEditorPage'
import SegmentsPage from '../pages/contacts/SegmentsPage'
import Home from '../pages/home/Home'
import EmailInbox from '../pages/inbox/EmailInbox'
import InviteAccept from '../pages/invitations/InviteAccept'
import NotificationOpenPage from '../pages/notifications/NotificationOpenPage'
import NotificationsPage from '../pages/notifications/NotificationsPage'
import PlaceholderPage from '../pages/placeholder/PlaceholderPage'
import PlatformAdmins from '../pages/platform/PlatformAdmins'
import PlatformBilling from '../pages/platform/PlatformBilling'
import PlatformCompanies from '../pages/platform/PlatformCompanies'
import PlatformDashboard from '../pages/platform/PlatformDashboard'
import PlatformPlanDetails from '../pages/platform/PlatformPlanDetails'
import PlatformPlans from '../pages/platform/PlatformPlans'
import PlatformManagedProviders from '../pages/platform/PlatformManagedProviders'
import PlatformManagedSmsReviews from '../pages/platform/PlatformManagedSmsReviews'
import PlatformUsers from '../pages/platform/PlatformUsers'
import PlatformWebhookCallbacks from '../pages/platform/PlatformWebhookCallbacks'
import TemplateLibrary from '../pages/templates/TemplateLibrary'
import EmailUnsubscribePage from '../pages/unsubscribe/EmailUnsubscribePage'
import CreateWorkspace from '../pages/workspace/CreateWorkspace'
import SelectPlan from '../pages/workspace/SelectPlan'
import SelectWorkspace from '../pages/workspace/SelectWorkspace'
import GuestRoute from './guards/GuestRoute'
import HomeRoute from './guards/HomeRoute'
import PermissionRoute from './guards/PermissionRoute'
import PlatformRoute from './guards/PlatformRoute'
import ProtectedRoute from './guards/ProtectedRoute'
import VerifiedRoute from './guards/VerifiedRoute'
import WorkspaceRoute from './guards/WorkspaceRoute'

const AppRoutes = () => (
  <Routes>
    <Route path="/unsubscribe" element={<EmailUnsubscribePage />} />

    <Route element={<AuthLayout />}>
      <Route path="/invite/:token" element={<InviteAccept />} />
      <Route element={<GuestRoute />}>
        <Route path="/login" element={<Login />} />
        <Route path="/register" element={<Register />} />
        <Route path="/forgot-password" element={<ForgotPassword />} />
        <Route path="/reset-password" element={<ResetPassword />} />
      </Route>
      <Route element={<ProtectedRoute />}>
        <Route path="/verifyotp" element={<VerifyOtp />} />
      </Route>
    </Route>

    <Route element={<ProtectedRoute />}>
      <Route element={<VerifiedRoute />}>
        <Route path="/" element={<HomeRoute />} />
        <Route path="/select-workspace" element={<SelectWorkspace />} />
        <Route path="/create-workspace" element={<CreateWorkspace />} />
        <Route path="/select-plan/:companyId" element={<SelectPlan />} />
        <Route path="/notification-open" element={<NotificationOpenPage />} />

        <Route element={<MainLayout />}>
          <Route path="/account/profile" element={<AccountProfile />} />
          <Route path="/account/invitations" element={<AccountInvitations />} />
          <Route path="/account/settings" element={<AccountSettings />} />
          <Route path="/account/settings/password" element={<ChangePassword />} />
          <Route path="/account/settings/notifications" element={<AccountNotificationSettings />} />
        </Route>

        <Route element={<WorkspaceRoute />}>
          <Route element={<MainLayout />}>
            <Route path="/workspace" element={<WorkspaceEpochBoundary><Home /></WorkspaceEpochBoundary>} />
            <Route path="/notifications" element={<NotificationsPage />} />
            <Route
              path="/billing/payment-return"
              element={<WorkspaceEpochBoundary><PaymentReturnPage /></WorkspaceEpochBoundary>}
            />

            <Route element={<PermissionRoute anyOf={[PERMISSIONS.WHATSAPP_READ]} />}>
              <Route path="inbox/social-media" element={<WorkspaceEpochBoundary><EmailInbox /></WorkspaceEpochBoundary>} />
            </Route>
            <Route element={<PermissionRoute anyOf={[PERMISSIONS.SMS_READ]} />}>
              <Route path="inbox/sms" element={<PlaceholderPage />} />
            </Route>
            <Route element={<PermissionRoute anyOf={[PERMISSIONS.EMAIL_READ]} />}>
              <Route path="inbox/email" element={<WorkspaceEpochBoundary><EmailInbox /></WorkspaceEpochBoundary>} />
            </Route>

            <Route element={<PermissionRoute anyOf={[PERMISSIONS.BROADCAST_READ]} />}>
              <Route path="broadcast/sms" element={<WorkspaceEpochBoundary><BroadcastWorkspace /></WorkspaceEpochBoundary>} />
              <Route path="broadcast/social-media" element={<WorkspaceEpochBoundary><BroadcastWorkspace /></WorkspaceEpochBoundary>} />
              <Route path="broadcast/email" element={<WorkspaceEpochBoundary><BroadcastWorkspace /></WorkspaceEpochBoundary>} />
            </Route>

            <Route element={<PermissionRoute anyOf={[PERMISSIONS.ADS_READ]} />}>
              <Route path="ad-center" element={<AdCenterOverview />} />
              <Route path="ad-center/unified-campaigns" element={<ReachlyoCampaigns />} />
              <Route path="ad-center/unified-campaigns/new" element={<ReachlyoCampaignWizard />} />
              <Route path="ad-center/unified-campaigns/:campaignId" element={<ReachlyoCampaignWizard />} />
              <Route path="ad-center/campaigns" element={<AdCampaigns />} />
              <Route path="ad-center/campaigns/:campaignId" element={<AdCampaignDetails />} />
              <Route path="ad-center/ad-sets/:adSetId" element={<AdSetDetails />} />
              <Route path="ad-center/ads/:adId" element={<AdDetails />} />
              <Route path="ad-center/creatives" element={<AdCreatives />} />
              <Route path="ad-center/audiences" element={<AdAudiences />} />
              <Route path="ad-center/conversions" element={<AdConversions />} />
              <Route path="ad-center/analytics" element={<AdAnalytics />} />
              <Route path="ad-center/recommendations" element={<AdRecommendations />} />
              <Route path="ad-center/merchant" element={<AdMerchantCenter />} />
              <Route path="ad-center/billing" element={<AdBilling />} />
              <Route path="ad-center/activity" element={<AdActivity />} />
              <Route path="ad-center/settings" element={<AdSettings />} />
            </Route>

            <Route element={<PermissionRoute anyOf={[PERMISSIONS.CRM_LEAD_READ, PERMISSIONS.CRM_ACCOUNT_READ, PERMISSIONS.CRM_TASK_READ, PERMISSIONS.CONTACT_READ]} />}>
              <Route path="crm" element={<Navigate to="/crm/overview" replace />} />
              <Route path="crm/overview" element={<WorkspaceEpochBoundary><CrmOverview /></WorkspaceEpochBoundary>} />
            </Route>
            <Route element={<PermissionRoute allOf={[PERMISSIONS.CRM_LEAD_READ, PERMISSIONS.CONTACT_READ]} />}>
              <Route path="crm/leads" element={<WorkspaceEpochBoundary><CrmLeadsPage /></WorkspaceEpochBoundary>} />
            </Route>
            <Route element={<PermissionRoute allOf={[PERMISSIONS.CRM_ACCOUNT_READ]} />}>
              <Route path="crm/companies" element={<WorkspaceEpochBoundary><CrmCompaniesPage /></WorkspaceEpochBoundary>} />
            </Route>
            <Route element={<PermissionRoute allOf={[PERMISSIONS.CRM_TASK_READ, PERMISSIONS.CONTACT_READ]} />}>
              <Route path="crm/tasks" element={<WorkspaceEpochBoundary><CrmTasksPage /></WorkspaceEpochBoundary>} />
            </Route>
            <Route element={<PermissionRoute anyOf={[PERMISSIONS.CONTACT_READ]} />}>
              <Route path="contacts" element={<WorkspaceEpochBoundary><ContactsPage /></WorkspaceEpochBoundary>} />
              <Route path="contacts/lists" element={<WorkspaceEpochBoundary><ListsPage /></WorkspaceEpochBoundary>} />
              <Route path="contacts/lists/:listId" element={<WorkspaceEpochBoundary><ListDetailPage /></WorkspaceEpochBoundary>} />
              <Route path="contacts/segments" element={<WorkspaceEpochBoundary><SegmentsPage /></WorkspaceEpochBoundary>} />
              <Route path="contacts/segments/:segmentId" element={<WorkspaceEpochBoundary><SegmentEditorPage /></WorkspaceEpochBoundary>} />
              <Route path="contacts/settings" element={<WorkspaceEpochBoundary><ContactSettingsPage /></WorkspaceEpochBoundary>} />
              <Route path="contacts/:contactId" element={<WorkspaceEpochBoundary><ContactDetailPage /></WorkspaceEpochBoundary>} />
            </Route>
            <Route element={<PermissionRoute allOf={[PERMISSIONS.CONTACT_READ, PERMISSIONS.CONTACT_MODIFY]} />}>
              <Route path="contacts/segments/new" element={<WorkspaceEpochBoundary><SegmentEditorPage /></WorkspaceEpochBoundary>} />
            </Route>
            <Route element={<PermissionRoute allOf={[PERMISSIONS.CONTACT_READ, PERMISSIONS.CONTACT_IMPORT]} />}>
              <Route path="contacts/imports" element={<ImportsPage />} />
              <Route path="contacts/imports/:importId" element={<ImportDetailPage />} />
            </Route>
            <Route element={<PermissionRoute anyOf={[PERMISSIONS.TEMPLATE_READ]} />}>
              <Route path="templates" element={<TemplateLibrary />} />
            </Route>

            <Route element={<PermissionRoute anyOf={[PERMISSIONS.COMPANY_READ]} />}>
              <Route path="company/details" element={<CompanyDetails />} />
            </Route>
            <Route
              element={
                <PermissionRoute
                  anyOf={[
                    PERMISSIONS.WHATSAPP_READ,
                    PERMISSIONS.WHATSAPP_CONNECT,
                    PERMISSIONS.EMAIL_READ,
                    PERMISSIONS.EMAIL_CONNECT,
                    PERMISSIONS.SMS_READ,
                    PERMISSIONS.SMS_CONNECT,
                    PERMISSIONS.BROADCAST_READ,
                    PERMISSIONS.BROADCAST_MODIFY,
                    PERMISSIONS.ADS_READ,
                    PERMISSIONS.ADS_CONNECT,
                  ]}
                />
              }
            >
              <Route
                path="company/connections"
                element={<WorkspaceEpochBoundary><CompanyConnections /></WorkspaceEpochBoundary>}
              />
            </Route>
            <Route element={<PermissionRoute anyOf={[PERMISSIONS.USER_READ]} />}>
              <Route path="company/users" element={<CompanyUsers />} />
            </Route>
            <Route element={<PermissionRoute anyOf={[PERMISSIONS.ROLE_READ]} />}>
              <Route path="company/roles" element={<CompanyRoles />} />
            </Route>
            <Route element={<PermissionRoute anyOf={[PERMISSIONS.BILLING_READ]} />}>
              <Route path="company/plan" element={<WorkspaceEpochBoundary><CompanyPlan /></WorkspaceEpochBoundary>} />
            </Route>
          </Route>
        </Route>
      </Route>
    </Route>

    <Route element={<ProtectedRoute />}>
      <Route element={<PlatformRoute />}>
        <Route element={<MainLayout platform />}>
          <Route path="/platform" element={<PlatformDashboard />} />
          <Route path="/platform/notifications" element={<NotificationsPage />} />
          <Route path="/platform/companies" element={<PlatformCompanies />} />
          <Route path="/platform/users" element={<PlatformUsers />} />
          <Route path="/platform/plans" element={<PlatformPlans />} />
          <Route path="/platform/plans/:planId" element={<PlatformPlanDetails />} />
          <Route path="/platform/billing" element={<PlatformBilling />} />
          <Route path="/platform/managed-providers" element={<PlatformManagedProviders />} />
          <Route path="/platform/managed-sms-reviews" element={<PlatformManagedSmsReviews />} />
          <Route path="/platform/admins" element={<PlatformAdmins />} />
          <Route path="/platform/channel-analytics/webhook-callbacks" element={<PlatformWebhookCallbacks />} />
          <Route path="/platform/audit" element={<PlaceholderPage />} />
        </Route>
      </Route>
    </Route>

    <Route path="*" element={<Navigate to="/" replace />} />
  </Routes>
)

export default AppRoutes
