import { apiClient } from '../../lib/api/apiClient'
import type { ApiResponse } from '../../lib/api/types'

export interface BookingType {
  id: string
  companyId: string
  slug: string
  title: string
  description?: string | null
  durationMinutes: number
  locationType: string
  locationDetails?: string | null
  hostUserId: string
  hostName: string
  isActive: boolean
  workingHoursStart: string
  workingHoursEnd: string
  daysOfWeek: string[]
  bufferBeforeMinutes: number
  bufferAfterMinutes: number
  createdAt: string
  updatedAt: string
}

export interface MeetingBooking {
  id: string
  companyId: string
  bookingTypeId: string
  bookingTypeTitle: string
  hostUserId: string
  hostName: string
  contactId?: string | null
  guestName: string
  guestEmail: string
  guestPhone?: string | null
  notes?: string | null
  startTime: string
  endTime: string
  status: 'CONFIRMED' | 'CANCELLED' | 'COMPLETED' | 'RESCHEDULED'
  meetingLink?: string | null
  createdAt: string
}

export interface PublicBookingType {
  slug: string
  title: string
  description?: string | null
  durationMinutes: number
  locationType: string
  locationDetails?: string | null
  hostName: string
  workingHoursStart: string
  workingHoursEnd: string
  daysOfWeek: string[]
}

export interface SaveBookingTypeInput {
  title: string
  slug?: string
  description?: string | null
  durationMinutes?: number
  locationType?: string
  locationDetails?: string | null
  hostUserId?: number
  isActive?: boolean
  workingHoursStart?: string
  workingHoursEnd?: string
  daysOfWeek?: string[]
  bufferBeforeMinutes?: number
  bufferAfterMinutes?: number
}

export interface BookMeetingInput {
  slotTime: string
  name: string
  email: string
  phone?: string
  notes?: string
}

export interface BookResult {
  success: boolean
  bookingId: string
  message: string
  meetingLink: string
  startTime: string
  endTime: string
}

const root = (companyId: string) => `/api/v1/companies/${companyId}/crm/bookings`

const unwrap = <T>(res: ApiResponse<T>) => {
  if (res.data === undefined) throw new Error(res.error_message || 'Request failed')
  return res.data
}

export const crmBookingsApi = {
  // Admin endpoints
  async listTypes(companyId: string): Promise<BookingType[]> {
    return unwrap(
      (await apiClient.get<ApiResponse<BookingType[]>>(`${root(companyId)}/types`)).data
    )
  },

  async createType(companyId: string, input: SaveBookingTypeInput): Promise<BookingType> {
    return unwrap(
      (await apiClient.post<ApiResponse<BookingType>>(`${root(companyId)}/types`, input)).data
    )
  },

  async updateType(
    companyId: string,
    id: string,
    input: SaveBookingTypeInput
  ): Promise<BookingType> {
    return unwrap(
      (await apiClient.put<ApiResponse<BookingType>>(`${root(companyId)}/types/${id}`, input)).data
    )
  },

  async toggleType(
    companyId: string,
    id: string,
    isActive: boolean
  ): Promise<BookingType> {
    return unwrap(
      (await apiClient.patch<ApiResponse<BookingType>>(`${root(companyId)}/types/${id}/toggle`, {
        isActive,
      })).data
    )
  },

  async deleteType(companyId: string, id: string): Promise<void> {
    await apiClient.delete(`${root(companyId)}/types/${id}`)
  },

  async listBookings(
    companyId: string,
    bookingTypeId?: string,
    contactId?: string,
    limit: number = 50
  ): Promise<MeetingBooking[]> {
    const params = new URLSearchParams()
    if (bookingTypeId) params.set('bookingTypeId', bookingTypeId)
    if (contactId) params.set('contactId', contactId)
    params.set('limit', String(limit))
    return unwrap(
      (await apiClient.get<ApiResponse<MeetingBooking[]>>(`${root(companyId)}?${params.toString()}`)).data
    )
  },

  async cancelBooking(companyId: string, id: string): Promise<void> {
    await apiClient.patch(`${root(companyId)}/${id}/cancel`)
  },

  // Public booking endpoints
  async getPublicType(slug: string): Promise<PublicBookingType> {
    return unwrap(
      (await apiClient.get<ApiResponse<PublicBookingType>>(`/api/v1/public/crm/bookings/${slug}`)).data
    )
  },

  async getAvailableSlots(slug: string, date: string): Promise<string[]> {
    return unwrap(
      (await apiClient.get<ApiResponse<string[]>>(`/api/v1/public/crm/bookings/${slug}/slots?date=${date}`)).data
    )
  },

  async bookMeeting(slug: string, input: BookMeetingInput): Promise<BookResult> {
    return unwrap(
      (await apiClient.post<ApiResponse<BookResult>>(`/api/v1/public/crm/bookings/${slug}/book`, input)).data
    )
  },
}
