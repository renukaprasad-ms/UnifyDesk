import { useEffect } from 'react'
import { showGlobalSnackbar } from '../feedback/SnackbarProvider'

type Props = {
  error?: string | null
  notice?: string | null
}

export default function AdFeedbackAlert({ error, notice }: Props) {
  useEffect(() => {
    if (error) showGlobalSnackbar(error, { variant: 'error' })
    else if (notice) showGlobalSnackbar(notice, { variant: 'success' })
  }, [error, notice])
  return null
}
