import { sessionCleared } from '../../store/auth/authSlice'
import type { AppDispatch } from '../../store/store'
import { authApi } from './authApi'

export const logout = async (dispatch: AppDispatch) => {
  try {
    await authApi.logout()
  } finally {
    dispatch(sessionCleared())
  }
}
