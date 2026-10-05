import AuthBootstrap from './features/auth/AuthBootstrap.tsx'
import AppRoutes from './routes/AppRoutes.tsx'
import ConnectivityBanner from './components/ui/ConnectivityBanner.tsx'

const App = () => {
  return (
    <AuthBootstrap>
      <AppRoutes />
      <ConnectivityBanner />
    </AuthBootstrap>
  )
}

export default App
