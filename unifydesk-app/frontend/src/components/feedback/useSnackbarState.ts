import { useCallback, useEffect, useRef, useState, type Dispatch, type SetStateAction } from 'react'
import { showGlobalSnackbar, type SnackbarVariant } from './SnackbarProvider'

/** Message state for action feedback, including messages followed immediately by navigation. */
export function useSnackbarState(
  initialValue: string,
  variant?: SnackbarVariant,
): [string, Dispatch<SetStateAction<string>>]
export function useSnackbarState<T extends string | null | undefined>(
  initialValue: T,
  variant?: SnackbarVariant,
): [T, Dispatch<SetStateAction<T>>]
export function useSnackbarState<T extends string | null | undefined>(
  initialValue: T,
  variant: SnackbarVariant = 'error',
): [T, Dispatch<SetStateAction<T>>] {
  const [value, setValue] = useState(initialValue)
  const current = useRef(initialValue)
  const mounted = useRef(true)

  useEffect(() => {
    mounted.current = true
    return () => {
      mounted.current = false
    }
  }, [])

  const setMessage = useCallback<Dispatch<SetStateAction<T>>>(
    (action) => {
      if (!mounted.current) return
      const next = typeof action === 'function' ? action(current.current) : action
      current.current = next
      setValue(next)
      if (typeof next === 'string' && next.trim()) showGlobalSnackbar(next, { variant })
    },
    [variant],
  )

  return [value, setMessage]
}
