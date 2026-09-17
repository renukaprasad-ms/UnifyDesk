import { createSlice, type PayloadAction } from '@reduxjs/toolkit'
import type { PermissionKey } from '../../features/iam/permissions'
import { sessionCleared } from '../auth/authSlice'
import type { RootState } from '../store'
import { clearWorkspace, selectCompany } from '../workspace/workspaceSlice'

export type IamStatus = 'idle' | 'loading' | 'ready' | 'error'

type IamState = {
  companyId: string | null
  owner: boolean
  permissions: PermissionKey[]
  status: IamStatus
  error: string | null
}

const initialState: IamState = {
  companyId: null,
  owner: false,
  permissions: [],
  status: 'idle',
  error: null,
}

const iamSlice = createSlice({
  name: 'iam',
  initialState,
  reducers: {
    iamLoadStarted: (state, action: PayloadAction<string>) => {
      state.companyId = action.payload
      state.owner = false
      state.permissions = []
      state.status = 'loading'
      state.error = null
    },
    iamLoadSucceeded: (
      state,
      action: PayloadAction<{ companyId: string; owner: boolean; permissions: PermissionKey[] }>,
    ) => {
      if (state.companyId !== action.payload.companyId) return
      state.owner = action.payload.owner
      state.permissions = action.payload.permissions
      state.status = 'ready'
      state.error = null
    },
    iamLoadFailed: (state, action: PayloadAction<{ companyId: string; error: string }>) => {
      if (state.companyId !== action.payload.companyId) return
      state.owner = false
      state.permissions = []
      state.status = 'error'
      state.error = action.payload.error
    },
    resetIam: () => initialState,
  },
  extraReducers: (builder) => {
    builder
      .addCase(sessionCleared, () => initialState)
      .addCase(clearWorkspace, () => initialState)
      .addCase(selectCompany, () => initialState)
  },
})

export const { iamLoadStarted, iamLoadSucceeded, iamLoadFailed, resetIam } = iamSlice.actions

export const selectIamState = (state: RootState) => state.iam
export const selectIamPermissions = (state: RootState) => state.iam.permissions
export const selectIamOwner = (state: RootState) => state.iam.owner
export const selectIamStatus = (state: RootState) => state.iam.status

export default iamSlice.reducer
