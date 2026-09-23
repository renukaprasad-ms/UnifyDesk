import { Navigate, Route, Routes } from 'react-router'
import { PERMISSIONS } from '../features/iam/permissions'
import AuthLayout from '../layouts/AuthLayout'
import MainLayout from '../layouts/MainLayout'
import AccountProfile from '../pages/account/AccountProfile'
import AdCenterOverview from '../pages/ads/AdCenterOverview'
import AdCampaigns from '../pages/ads/AdCampaigns'
import AdCampaignDetails from '../pages/ads/AdCampaignDetails'
import AccountSettings from '../pages/account/AccountSettings'
import ChangePassword from '../pages/account/ChangePassword'
import ForgotPassword from '../pages/auth/ForgotPassword'
import Login from '../pages/auth/Login'
import Register from '../pages/auth/Register'
import ResetPassword from '../pages/auth/ResetPassword'
import VerifyOtp from '../pages/auth/VerifyOtp'
import CompanyConnections from '../pages/company/CompanyConnections'
import CompanyDetails from '../pages/company/CompanyDetails'
import CompanyPlan from '../pages/company/CompanyPlan'
import CompanyRoles from '../pages/company/CompanyRoles'
import CompanyUsers from '../pages/company/CompanyUsers'
import Home from '../pages/home/Home'
import InviteAccept from '../pages/invitations/InviteAccept'
import BroadcastWorkspace from '../pages/broadcast/BroadcastWorkspace'
import EmailInbox from '../pages/inbox/EmailInbox'
import PlaceholderPage from '../pages/placeholder/PlaceholderPage'
import TemplateLibrary from '../pages/templates/TemplateLibrary'
import PlatformAdmins from '../pages/platform/PlatformAdmins'
import PlatformBilling from '../pages/platform/PlatformBilling'
import PlatformCompanies from '../pages/platform/PlatformCompanies'
import PlatformDashboard from '../pages/platform/PlatformDashboard'
import PlatformPlanDetails from '../pages/platform/PlatformPlanDetails'
import PlatformPlans from '../pages/platform/PlatformPlans'
import PlatformUsers from '../pages/platform/PlatformUsers'
import PlatformWebhookCallbacks from '../pages/platform/PlatformWebhookCallbacks'
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

        <Route element={<WorkspaceRoute />}>
          <Route element={<MainLayout />}>
            <Route path="/workspace" element={<Home />} />

            <Route element={<PermissionRoute anyOf={[PERMISSIONS.WHATSAPP_READ]} />}>
              <Route path="inbox/social-media" element={<PlaceholderPage />} />
            </Route>
            <Route element={<PermissionRoute anyOf={[PERMISSIONS.SMS_READ]} />}>
              <Route path="inbox/sms" element={<PlaceholderPage />} />
            </Route>
            <Route element={<PermissionRoute anyOf={[PERMISSIONS.EMAIL_READ]} />}>
              <Route path="inbox/email" element={<EmailInbox />} />
            </Route>

            <Route element={<PermissionRoute anyOf={[PERMISSIONS.BROADCAST_READ]} />}>
              <Route path="broadcast/sms" element={<BroadcastWorkspace />} />
              <Route path="broadcast/social-media" element={<BroadcastWorkspace />} />
              <Route path="broadcast/email" element={<BroadcastWorkspace />} />
            </Route>
            <Route element={<PermissionRoute anyOf={[PERMISSIONS.ADS_READ]} />}>
              <Route path="ad-center" element={<AdCenterOverview />} />
              <Route path="ad-center/campaigns" element={<AdCampaigns />} />
              <Route path="ad-center/campaigns/:campaignId" element={<AdCampaignDetails />} />
              <Route path="ad-center/creatives" element={<PlaceholderPage />} />
              <Route path="ad-center/audiences" element={<PlaceholderPage />} />
              <Route path="ad-center/conversions" element={<PlaceholderPage />} />
              <Route path="ad-center/analytics" element={<PlaceholderPage />} />
              <Route path="ad-center/billing" element={<PlaceholderPage />} />
              <Route path="ad-center/settings" element={<PlaceholderPage />} />
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
                  ]}
                />
              }
            >
              <Route path="company/connections" element={<CompanyConnections />} />
            </Route>
            <Route element={<PermissionRoute anyOf={[PERMISSIONS.USER_READ]} />}>
              <Route path="company/users" element={<CompanyUsers />} />
            </Route>
            <Route element={<PermissionRoute anyOf={[PERMISSIONS.ROLE_READ]} />}>
              <Route path="company/roles" element={<CompanyRoles />} />
            </Route>
            <Route element={<PermissionRoute anyOf={[PERMISSIONS.BILLING_READ]} />}>
              <Route path="company/plan" element={<CompanyPlan />} />
            </Route>

            <Route path="account/profile" element={<AccountProfile />} />
            <Route path="account/settings" element={<AccountSettings />} />
            <Route path="account/settings/password" element={<ChangePassword />} />
          </Route>
        </Route>
      </Route>
    </Route>

    <Route element={<ProtectedRoute />}>
      <Route element={<PlatformRoute />}>
        <Route element={<MainLayout platform />}>
          <Route path="/platform" element={<PlatformDashboard />} />
          <Route path="/platform/companies" element={<PlatformCompanies />} />
          <Route path="/platform/users" element={<PlatformUsers />} />
          <Route path="/platform/plans" element={<PlatformPlans />} />
          <Route path="/platform/plans/:planId" element={<PlatformPlanDetails />} />
          <Route path="/platform/billing" element={<PlatformBilling />} />
          <Route path="/platform/admins" element={<PlatformAdmins />} />
          <Route
            path="/platform/channel-analytics/webhook-callbacks"
            element={<PlatformWebhookCallbacks />}
          />
          <Route path="/platform/audit" element={<PlaceholderPage />} />
        </Route>
      </Route>
    </Route>

    <Route path="*" element={<Navigate to="/" replace />} />
  </Routes>
)

export default AppRoutes
