import { api } from '@/api/client';

export interface PassengerRequest {
  fullName: string;
  age: number;
  gender: string;
  berthPreference?: string;
  savedPassengerId?: number;
}

export interface CreateBookingRequest {
  scheduleId: number;
  sourceStationId: number;
  destinationStationId: number;
  travelClass: string;
  passengers: PassengerRequest[];
  idempotencyKey: string;
}

export interface PassengerAllocation {
  fullName: string;
  age: number;
  gender: string;
  coachCode: string | null;
  seatNumber: string | null;
  berthType: string | null;
}

export interface BookingDto {
  bookingId: number;
  scheduleId: number;
  status: string;
  trainNumber: string;
  trainName: string;
  sourceStationCode: string;
  destinationStationCode: string;
  travelClass: string;
  totalFare: number;
  passengers: PassengerAllocation[];
  pnrNumber: string | null;
  tripGuardianEnabled: boolean;
  createdAt: string;
}

export const bookingsApi = {
  create: (data: CreateBookingRequest) => api.post<BookingDto>('/bookings', data).then((r) => r.data),
  get: (id: number) => api.get<BookingDto>(`/bookings/${id}`).then((r) => r.data),
  list: () => api.get<BookingDto[]>('/bookings').then((r) => r.data),
};
