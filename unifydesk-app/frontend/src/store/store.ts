import { configureStore } from '@reduxjs/toolkit'
import authReducer from './auth/authSlice'
import broadcastReducer from './broadcast/broadcastSlice'
import iamReducer from './iam/iamSlice'
import inboxReducer from './inbox/inboxSlice'
import workspaceReducer from './workspace/workspaceSlice'

const SELECTED_COMPANY_KEY = 'unifydesk.selectedCompanyId'

export const readSelectedCompanyId = () => {
  if (typeof window === 'undefined') return null
  return window.localStorage.getItem(SELECTED_COMPANY_KEY)
}

let persistedCompanyId = readSelectedCompanyId()

export const store = configureStore({
  reducer: {
    auth: authReducer,
    broadcast: broadcastReducer,
    workspace: workspaceReducer,
    iam: iamReducer,
    inbox: inboxReducer,
  },
  preloadedState: {
    workspace: {
      companies: [],
      selectedCompanyId: persistedCompanyId,
      epoch: 0,
    },
  },
})

export type RootState = ReturnType<typeof store.getState>
export type AppDispatch = typeof store.dispatch

store.subscribe(() => {
  if (typeof window === 'undefined') return

  const selectedCompanyId = store.getState().workspace.selectedCompanyId
  if (selectedCompanyId === persistedCompanyId) return

  persistedCompanyId = selectedCompanyId
  if (selectedCompanyId) {
    window.localStorage.setItem(SELECTED_COMPANY_KEY, selectedCompanyId)
  } else {
    window.localStorage.removeItem(SELECTED_COMPANY_KEY)
  }
})
