import { Navigate, Route, Routes } from 'react-router'
import AuthLayout from '../layouts/AuthLayout'
import MainLayout from '../layouts/MainLayout'
import Login from '../pages/auth/Login'
import Register from '../pages/auth/Register'
import VerifyOtp from '../pages/auth/VerifyOtp'
import Home from '../pages/home/Home'
import GuestRoute from './guards/GuestRoute'
import ProtectedRoute from './guards/ProtectedRoute'
import VerifiedRoute from './guards/VerifiedRoute'

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
          <Route element={<MainLayout />}>
            <Route index element={<Home />} />
          </Route>
        </Route>
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}

export default AppRoutes
