import { configureStore } from '@reduxjs/toolkit'
import userReducer from './user/userSlice'

const SELECTED_COMPANY_KEY = 'messaging.selectedCompanyId'

export const store = configureStore({
  reducer: {
    user: userReducer,
  },
})

export type RootState = ReturnType<typeof store.getState>
export type AppDispatch = typeof store.dispatch

export const readSelectedCompanyId = () => {
  if (typeof window === 'undefined') {
    return null
  }
  return window.localStorage.getItem(SELECTED_COMPANY_KEY)
}

let persistedCompanyId = readSelectedCompanyId()

store.subscribe(() => {
  if (typeof window === 'undefined') {
    return
  }

  const selectedCompanyId = store.getState().user.selectedCompanyId
  if (selectedCompanyId === persistedCompanyId) {
    return
  }

  persistedCompanyId = selectedCompanyId
  if (selectedCompanyId) {
    window.localStorage.setItem(SELECTED_COMPANY_KEY, selectedCompanyId)
  } else {
    window.localStorage.removeItem(SELECTED_COMPANY_KEY)
  }
})
