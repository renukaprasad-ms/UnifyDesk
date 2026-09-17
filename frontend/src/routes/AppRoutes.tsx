import { Navigate, Route, Routes } from 'react-router'
import { PERMISSIONS } from '../features/iam/permissions'
import AuthLayout from '../layouts/AuthLayout'
import MainLayout from '../layouts/MainLayout'
import AccountSettings from '../pages/account/AccountSettings'
import Login from '../pages/auth/Login'
import Register from '../pages/auth/Register'
import VerifyOtp from '../pages/auth/VerifyOtp'
import CompanyPlan from '../pages/company/CompanyPlan'
import CompanyUsers from '../pages/company/CompanyUsers'
import Home from '../pages/home/Home'
import PlaceholderPage from '../pages/placeholder/PlaceholderPage'
import PlatformAdmins from '../pages/platform/PlatformAdmins'
import PlatformCompanies from '../pages/platform/PlatformCompanies'
import PlatformDashboard from '../pages/platform/PlatformDashboard'
import PlatformPlans from '../pages/platform/PlatformPlans'
import PlatformUsers from '../pages/platform/PlatformUsers'
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
      <Route element={<GuestRoute />}>
        <Route path="/login" element={<Login />} />
        <Route path="/register" element={<Register />} />
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
              <Route path="inbox/email" element={<PlaceholderPage />} />
            </Route>

            <Route element={<PermissionRoute anyOf={[PERMISSIONS.BROADCAST_READ]} />}>
              <Route path="broadcast/sms" element={<PlaceholderPage />} />
              <Route path="broadcast/social-media" element={<PlaceholderPage />} />
              <Route path="broadcast/email" element={<PlaceholderPage />} />
            </Route>
            <Route element={<PermissionRoute anyOf={[PERMISSIONS.TEMPLATE_READ]} />}>
              <Route path="templates" element={<PlaceholderPage />} />
            </Route>

            <Route element={<PermissionRoute anyOf={[PERMISSIONS.COMPANY_READ]} />}>
              <Route path="company/details" element={<PlaceholderPage />} />
            </Route>
            <Route element={<PermissionRoute anyOf={[PERMISSIONS.USER_READ]} />}>
              <Route path="company/users" element={<CompanyUsers />} />
            </Route>
            <Route element={<PermissionRoute anyOf={[PERMISSIONS.BILLING_READ]} />}>
              <Route path="company/plan" element={<CompanyPlan />} />
            </Route>

            <Route path="account/profile" element={<PlaceholderPage />} />
            <Route path="account/settings" element={<AccountSettings />} />
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
          <Route path="/platform/billing" element={<PlaceholderPage />} />
          <Route path="/platform/admins" element={<PlatformAdmins />} />
          <Route path="/platform/audit" element={<PlaceholderPage />} />
        </Route>
      </Route>
    </Route>

    <Route path="*" element={<Navigate to="/" replace />} />
  </Routes>
)

export default AppRoutes
