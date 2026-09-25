import { createSlice, type PayloadAction } from '@reduxjs/toolkit'
import type { Company } from '../../features/company/types'
import { sessionCleared } from '../auth/authSlice'
import type { RootState } from '../store'

type WorkspaceState = {
  companies: Company[]
  selectedCompanyId: string | null
}

const initialState: WorkspaceState = {
  companies: [],
  selectedCompanyId: null,
}

const workspaceSlice = createSlice({
  name: 'workspace',
  initialState,
  reducers: {
    setCompanies: (state, action: PayloadAction<Company[]>) => {
      state.companies = action.payload
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
    clearWorkspace: () => initialState,
  },
  extraReducers: (builder) => {
    builder.addCase(sessionCleared, () => initialState)
  },
})

export const { setCompanies, selectCompany, clearWorkspace } = workspaceSlice.actions

export const selectCompanies = (state: RootState) => state.workspace.companies
export const selectSelectedCompanyId = (state: RootState) => state.workspace.selectedCompanyId
export const selectSelectedCompany = (state: RootState) =>
  state.workspace.companies.find(
    (company) => company.companyId === state.workspace.selectedCompanyId,
  ) ?? null

export default workspaceSlice.reducer
