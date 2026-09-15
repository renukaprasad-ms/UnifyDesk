export type ApiResponse<T> = {
  status: boolean
  status_code: number
  data?: T
  message?: string
  error_message?: string
}

export type ApiErrorBody = ApiResponse<never>
