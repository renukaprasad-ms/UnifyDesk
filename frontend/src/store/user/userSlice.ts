// Compatibility exports for existing screens while state ownership is split by concern.
// New code should import from store/auth and store/workspace directly.
export {
  setAuthUser,
  setBootstrapped,
  sessionCleared as clearAuthUser,
} from '../auth/authSlice'
export {
  setCompanies,
  selectCompany,
  selectSelectedCompany,
} from '../workspace/workspaceSlice'
