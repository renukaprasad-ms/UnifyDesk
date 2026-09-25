import AuthBootstrap from './features/auth/AuthBootstrap.tsx'
import AppRoutes from './routes/AppRoutes.tsx'

const App = () => {
  return (
    <AuthBootstrap>
      <AppRoutes />
    </AuthBootstrap>
  )
}

export default App
