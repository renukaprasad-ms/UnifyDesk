import { createSlice, type PayloadAction } from '@reduxjs/toolkit'
import type { AuthUser } from '../../features/auth/types'
import type { Company } from '../../features/company/types'
import type { RootState } from '../store'

type UserState = {
  user: AuthUser | null
  companies: Company[]
  selectedCompanyId: string | null
  isAuthenticated: boolean
  bootstrapped: boolean
}

const initialState: UserState = {
  user: null,
  companies: [],
  selectedCompanyId: null,
  isAuthenticated: false,
  bootstrapped: false,
}

const userSlice = createSlice({
  name: 'user',
  initialState,
  reducers: {
    setAuthUser: (state, action: PayloadAction<AuthUser>) => {
      state.user = action.payload
      state.isAuthenticated = true
      state.bootstrapped = true
      if (!action.payload.hasCompanies) {
        state.companies = []
        state.selectedCompanyId = null
      }
    },
    clearAuthUser: (state) => {
      state.user = null
      state.companies = []
      state.selectedCompanyId = null
      state.isAuthenticated = false
      state.bootstrapped = true
    },
    setCompanies: (state, action: PayloadAction<Company[]>) => {
      state.companies = action.payload
      if (state.user) {
        state.user.hasCompanies = action.payload.length > 0
        state.user.companyCount = action.payload.length
      }
      if (
        state.selectedCompanyId &&
        !action.payload.some((company) => company.companyId === state.selectedCompanyId)
      ) {
        state.selectedCompanyId = null
      }
      if (!state.selectedCompanyId && action.payload.length === 1) {
        state.selectedCompanyId = action.payload[0].companyId
      }
    },
    selectCompany: (state, action: PayloadAction<string>) => {
      state.selectedCompanyId = action.payload
    },
    setBootstrapped: (state) => {
      state.bootstrapped = true
    },
  },
})

export const { setAuthUser, clearAuthUser, setCompanies, selectCompany, setBootstrapped } =
  userSlice.actions

export const selectSelectedCompany = (state: RootState) =>
  state.user.companies.find((company) => company.companyId === state.user.selectedCompanyId) ?? null

export default userSlice.reducer
