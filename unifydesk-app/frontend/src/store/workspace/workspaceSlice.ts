import { createSlice, type PayloadAction } from '@reduxjs/toolkit'
import type { Company } from '../../features/company/types'
import { sessionCleared } from '../auth/authSlice'
import type { RootState } from '../store'

type WorkspaceState = {
  companies: Company[]
  selectedCompanyId: string | null
  epoch: number
}

const initialState: WorkspaceState = {
  companies: [],
  selectedCompanyId: null,
  epoch: 0,
}

const updateSelectedCompany = (state: WorkspaceState, nextCompanyId: string | null) => {
  if (state.selectedCompanyId === nextCompanyId) return
  state.selectedCompanyId = nextCompanyId
  state.epoch += 1
}

const workspaceSlice = createSlice({
  name: 'workspace',
  initialState,
  reducers: {
    setCompanies: (state, action: PayloadAction<Company[]>) => {
      state.companies = action.payload
      if (
        state.selectedCompanyId &&
        !action.payload.some(
          (company) =>
            company.companyId === state.selectedCompanyId && company.status === 'ACTIVE',
        )
      ) {
        updateSelectedCompany(state, null)
      }
      if (!state.selectedCompanyId && action.payload.length === 1) {
        updateSelectedCompany(state, action.payload[0].companyId)
      }
    },
    selectCompany: (state, action: PayloadAction<string>) => {
      updateSelectedCompany(state, action.payload)
    },
    clearWorkspace: (state) => {
      state.companies = []
      updateSelectedCompany(state, null)
    },
  },
  extraReducers: (builder) => {
    builder.addCase(sessionCleared, (state) => {
      state.companies = []
      updateSelectedCompany(state, null)
    })
  },
})

export const { setCompanies, selectCompany, clearWorkspace } = workspaceSlice.actions

export const selectCompanies = (state: RootState) => state.workspace.companies
export const selectSelectedCompanyId = (state: RootState) => state.workspace.selectedCompanyId
export const selectWorkspaceEpoch = (state: RootState) => state.workspace.epoch
export const selectSelectedCompany = (state: RootState) =>
  state.workspace.companies.find(
    (company) => company.companyId === state.workspace.selectedCompanyId,
  ) ?? null

export default workspaceSlice.reducer
