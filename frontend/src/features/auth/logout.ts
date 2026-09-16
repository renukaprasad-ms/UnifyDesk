import { authApi } from './authApi'
import type { AppDispatch } from '../../store/store'
import { clearAuthUser } from '../../store/user/userSlice'

export const logout = async (dispatch: AppDispatch) => {
  try {
    await authApi.logout()
  } finally {
    dispatch(clearAuthUser())
    localStorage.removeItem('selectedCompanyId')
    sessionStorage.clear()
  }
}
