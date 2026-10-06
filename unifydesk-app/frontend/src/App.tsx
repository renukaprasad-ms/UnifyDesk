import AuthBootstrap from './features/auth/AuthBootstrap.tsx'
import CompanyThemeProvider from './features/companyTheme/CompanyThemeProvider.tsx'
import AppRoutes from './routes/AppRoutes.tsx'
import ConnectivityBanner from './components/ui/ConnectivityBanner.tsx'

const App = () => {
  return (
    <>
      <CompanyThemeProvider />
      <AuthBootstrap>
        <AppRoutes />
        <ConnectivityBanner />
      </AuthBootstrap>
    </>
  )
}

export default App
