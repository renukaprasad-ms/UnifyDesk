import React, { useState, forwardRef } from 'react'
import { FiEye, FiEyeOff, FiLock } from 'react-icons/fi'

export interface PasswordInputProps extends Omit<
  React.InputHTMLAttributes<HTMLInputElement>,
  'type'
> {
  showLeftIcon?: boolean
  leftIcon?: React.ReactNode
  containerClassName?: string
}

export const PasswordInput = forwardRef<HTMLInputElement, PasswordInputProps>(
  (
    {
      showLeftIcon = false,
      leftIcon,
      containerClassName = '',
      className = '',
      disabled = false,
      ...props
    },
    ref,
  ) => {
    const [visible, setVisible] = useState(false)

    return (
      <div className={`relative block w-full ${containerClassName}`}>
        {showLeftIcon && (
          <div className="pointer-events-none absolute inset-y-0 left-0 flex w-10 items-center justify-center text-slate-400">
            {leftIcon || <FiLock className="h-4 w-4" aria-hidden="true" />}
          </div>
        )}
        <input
          ref={ref}
          type={visible ? 'text' : 'password'}
          disabled={disabled}
          className={`form-input w-full pr-11 ${showLeftIcon ? 'pl-10' : ''} ${className}`}
          {...props}
        />
        <button
          type="button"
          tabIndex={-1}
          disabled={disabled}
          className="absolute inset-y-0 right-0 flex w-11 items-center justify-center rounded-r-lg text-slate-400 hover:text-slate-700 dark:hover:text-slate-200 transition-colors focus-visible:outline-2 focus-visible:outline-indigo-500 disabled:opacity-50"
          onClick={() => setVisible((prev) => !prev)}
          aria-label={visible ? 'Hide password' : 'Show password'}
          aria-pressed={visible}
        >
          {visible ? (
            <FiEyeOff className="h-4 w-4" aria-hidden="true" />
          ) : (
            <FiEye className="h-4 w-4" aria-hidden="true" />
          )}
        </button>
      </div>
    )
  },
)

PasswordInput.displayName = 'PasswordInput'

export default PasswordInput
