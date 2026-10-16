import React, { useRef, useEffect } from 'react'

export interface OtpBoxInputProps {
  length?: number
  value: string
  onChange: (value: string) => void
  disabled?: boolean
  autoFocus?: boolean
  error?: boolean
  className?: string
  ariaLabel?: string
  onComplete?: (value: string) => void
}

export const OtpBoxInput: React.FC<OtpBoxInputProps> = ({
  length = 6,
  value = '',
  onChange,
  disabled = false,
  autoFocus = false,
  error = false,
  className = '',
  ariaLabel = 'Verification code',
  onComplete,
}) => {
  const inputRefs = useRef<Array<HTMLInputElement | null>>([])

  useEffect(() => {
    if (autoFocus && inputRefs.current[0]) {
      inputRefs.current[0].focus()
    }
  }, [autoFocus])

  const digits = Array.from({ length }, (_, i) => value[i] ?? '')

  const handleChange = (index: number, e: React.ChangeEvent<HTMLInputElement>) => {
    const entered = e.target.value.replace(/\D/g, '')
    if (!entered) {
      // Cleared
      const newDigits = [...digits]
      newDigits[index] = ''
      const nextVal = newDigits.join('')
      onChange(nextVal)
      return
    }

    const lastChar = entered.slice(-1)
    const newDigits = [...digits]
    newDigits[index] = lastChar
    const nextVal = newDigits.join('')
    onChange(nextVal)

    if (lastChar && index < length - 1) {
      inputRefs.current[index + 1]?.focus()
    }

    if (nextVal.length === length && onComplete) {
      onComplete(nextVal)
    }
  }

  const handleKeyDown = (index: number, e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Backspace') {
      if (!digits[index] && index > 0) {
        inputRefs.current[index - 1]?.focus()
      }
    } else if (e.key === 'ArrowLeft' && index > 0) {
      e.preventDefault()
      inputRefs.current[index - 1]?.focus()
    } else if (e.key === 'ArrowRight' && index < length - 1) {
      e.preventDefault()
      inputRefs.current[index + 1]?.focus()
    }
  }

  const handlePaste = (e: React.ClipboardEvent<HTMLInputElement>) => {
    e.preventDefault()
    const pasted = e.clipboardData.getData('text').replace(/\D/g, '').slice(0, length)
    if (!pasted) return

    onChange(pasted)
    const targetIndex = Math.min(pasted.length, length - 1)
    inputRefs.current[targetIndex]?.focus()

    if (pasted.length === length && onComplete) {
      onComplete(pasted)
    }
  }

  return (
    <div
      className={`flex items-center justify-center gap-2 sm:gap-3 ${className}`}
      role="group"
      aria-label={ariaLabel}
    >
      {Array.from({ length }).map((_, index) => (
        <input
          key={index}
          ref={(el) => {
            inputRefs.current[index] = el
          }}
          type="text"
          inputMode="numeric"
          pattern="[0-9]*"
          maxLength={1}
          autoComplete={index === 0 ? 'one-time-code' : 'off'}
          disabled={disabled}
          value={digits[index] ?? ''}
          aria-label={`Digit ${index + 1} of ${length}`}
          onChange={(e) => handleChange(index, e)}
          onKeyDown={(e) => handleKeyDown(index, e)}
          onPaste={handlePaste}
          className={`h-12 w-11 sm:h-14 sm:w-12 text-center text-xl font-bold rounded-xl border transition-all outline-none focus:ring-2 disabled:opacity-50 disabled:cursor-not-allowed ${
            error
              ? 'border-rose-400 bg-rose-50/50 text-rose-900 focus:border-rose-500 focus:ring-rose-200 dark:border-rose-700 dark:bg-rose-950/20 dark:text-rose-200'
              : 'border-slate-200 bg-white text-slate-900 focus:border-indigo-500 focus:ring-indigo-100 dark:border-slate-700 dark:bg-slate-900 dark:text-white dark:focus:border-indigo-400 dark:focus:ring-indigo-900/30'
          }`}
        />
      ))}
    </div>
  )
}

export default OtpBoxInput
