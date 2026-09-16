import { Navigate, Route, Routes } from 'react-router'
import AuthLayout from '../layouts/AuthLayout'
import MainLayout from '../layouts/MainLayout'
import AccountSettings from '../pages/account/AccountSettings'
import Login from '../pages/auth/Login'
import Register from '../pages/auth/Register'
import VerifyOtp from '../pages/auth/VerifyOtp'
import CompanyUsers from '../pages/company/CompanyUsers'
import Home from '../pages/home/Home'
import PlaceholderPage from '../pages/placeholder/PlaceholderPage'
import CreateWorkspace from '../pages/workspace/CreateWorkspace'
import SelectPlan from '../pages/workspace/SelectPlan'
import SelectWorkspace from '../pages/workspace/SelectWorkspace'
import GuestRoute from './guards/GuestRoute'
import ProtectedRoute from './guards/ProtectedRoute'
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
          <Route path="/select-workspace" element={<SelectWorkspace />} />
          <Route path="/create-workspace" element={<CreateWorkspace />} />
          <Route path="/select-plan/:companyId" element={<SelectPlan />} />
          <Route element={<WorkspaceRoute />}>
            <Route element={<MainLayout />}>
              <Route index element={<Home />} />
              <Route path="inbox/social-media" element={<PlaceholderPage />} />
              <Route path="inbox/sms" element={<PlaceholderPage />} />
              <Route path="inbox/email" element={<PlaceholderPage />} />
              <Route path="broadcast/sms" element={<PlaceholderPage />} />
              <Route path="broadcast/social-media" element={<PlaceholderPage />} />
              <Route path="broadcast/email" element={<PlaceholderPage />} />
              <Route path="templates" element={<PlaceholderPage />} />
              <Route path="company/details" element={<PlaceholderPage />} />
              <Route path="company/plan" element={<PlaceholderPage />} />
              <Route path="company/users" element={<CompanyUsers />} />
              <Route path="account/profile" element={<PlaceholderPage />} />
              <Route path="account/settings" element={<AccountSettings />} />
            </Route>
          </Route>
        </Route>
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}

export default AppRoutes
