import { Navigate, Route, Routes } from 'react-router'
import AuthLayout from '../layouts/AuthLayout'
import MainLayout from '../layouts/MainLayout'
import AccountSettings from '../pages/account/AccountSettings'
import Login from '../pages/auth/Login'
import Register from '../pages/auth/Register'
import VerifyOtp from '../pages/auth/VerifyOtp'
import CompanyPlan from '../pages/company/CompanyPlan'
import CompanyUsers from '../pages/company/CompanyUsers'
import Home from '../pages/home/Home'
import HomeRoute from './guards/HomeRoute'
import PlaceholderPage from '../pages/placeholder/PlaceholderPage'
import PlatformDashboard from '../pages/platform/PlatformDashboard'
import PlatformCompanies from '../pages/platform/PlatformCompanies'
import PlatformAdmins from '../pages/platform/PlatformAdmins'
import PlatformUsers from '../pages/platform/PlatformUsers'
import PlatformPlans from '../pages/platform/PlatformPlans'
import CreateWorkspace from '../pages/workspace/CreateWorkspace'
import SelectPlan from '../pages/workspace/SelectPlan'
import SelectWorkspace from '../pages/workspace/SelectWorkspace'
import GuestRoute from './guards/GuestRoute'
import CompanyPermissionRoute from './guards/CompanyPermissionRoute'
import ProtectedRoute from './guards/ProtectedRoute'
import PlatformRoute from './guards/PlatformRoute'
import VerifiedRoute from './guards/VerifiedRoute'
import WorkspaceRoute from './guards/WorkspaceRoute'

const AppRoutes = () => {
  return (
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
              <Route path="inbox/social-media" element={<PlaceholderPage />} />
              <Route path="inbox/sms" element={<PlaceholderPage />} />
              <Route path="inbox/email" element={<PlaceholderPage />} />
              <Route path="broadcast/sms" element={<PlaceholderPage />} />
              <Route path="broadcast/social-media" element={<PlaceholderPage />} />
              <Route path="broadcast/email" element={<PlaceholderPage />} />
              <Route path="templates" element={<PlaceholderPage />} />
              <Route element={<CompanyPermissionRoute permissions={['COMPANY_VIEW']} />}>
                <Route path="company/details" element={<PlaceholderPage />} />
                <Route path="company/users" element={<CompanyUsers />} />
              </Route>
              <Route element={<CompanyPermissionRoute permissions={['BILLING_VIEW']} />}>
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
}

export default AppRoutes
