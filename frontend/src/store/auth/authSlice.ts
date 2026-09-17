import { createSlice, type PayloadAction } from '@reduxjs/toolkit'
import type { AuthUser } from '../../features/auth/types'
import type { RootState } from '../store'

type AuthState = {
  user: AuthUser | null
  isAuthenticated: boolean
  bootstrapped: boolean
}

const initialState: AuthState = {
  user: null,
  isAuthenticated: false,
  bootstrapped: false,
}

const authSlice = createSlice({
  name: 'auth',
  initialState,
  reducers: {
    setAuthUser: (state, action: PayloadAction<AuthUser>) => {
      state.user = action.payload
      state.isAuthenticated = true
    },
    setBootstrapped: (state) => {
      state.bootstrapped = true
    },
    sessionCleared: () => ({ ...initialState, bootstrapped: true }),
  },
})

export const { setAuthUser, setBootstrapped, sessionCleared } = authSlice.actions

export const selectAuthUser = (state: RootState) => state.auth.user
export const selectIsAuthenticated = (state: RootState) => state.auth.isAuthenticated
export const selectAuthBootstrapped = (state: RootState) => state.auth.bootstrapped

export default authSlice.reducer
