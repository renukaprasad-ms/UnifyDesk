import { createSlice, type PayloadAction } from '@reduxjs/toolkit'
import type { AuthUser } from '../../features/auth/types'
import type { Company } from '../../features/company/types'

type UserState = {
  user: AuthUser | null
  companies: Company[]
  selectedCompany: Company | null
  isAuthenticated: boolean
  bootstrapped: boolean
}

const initialState: UserState = {
  user: null,
  companies: [],
  selectedCompany: null,
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
        state.selectedCompany = null
      }
    },
    clearAuthUser: (state) => {
      state.user = null
      state.companies = []
      state.selectedCompany = null
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
        state.selectedCompany &&
        !action.payload.some((company) => company.companyId === state.selectedCompany?.companyId)
      ) {
        state.selectedCompany = null
      }
      if (!state.selectedCompany && action.payload.length === 1) {
        state.selectedCompany = action.payload[0]
      }
    },
    selectCompany: (state, action: PayloadAction<Company>) => {
      state.selectedCompany = action.payload
    },
    setBootstrapped: (state) => {
      state.bootstrapped = true
    },
  },
})

export const { setAuthUser, clearAuthUser, setCompanies, selectCompany, setBootstrapped } =
  userSlice.actions
export default userSlice.reducer
