import { api } from '@/api/client';

export interface SeatClassAvailability {
  travelClass: string;
  totalSeats: number;
  availableSeats: number;
  fare: number;
}

export interface TrainSearchResult {
  scheduleId: number;
  trainId: number;
  trainNumber: string;
  trainName: string;
  trainType: string;
  sourceStationCode: string;
  destinationStationCode: string;
  departureDate: string;
  departureTime: string;
  durationMinutes: number;
  status: string;
  delayMinutes: number;
  classes: SeatClassAvailability[];
}

export interface RouteStop {
  stationCode: string;
  stationName: string;
  sequenceNo: number;
  arrivalOffsetMinutes: number;
  departureOffsetMinutes: number;
  distanceKm: number;
}

export interface TrainDetailsDto {
  trainId: number;
  trainNumber: string;
  trainName: string;
  trainType: string;
  route: RouteStop[];
}

export interface AvailabilityDto {
  scheduleId: number;
  travelClass: string;
  totalSeats: number;
  availableSeats: number;
}

export interface RankedCategory {
  category: string; // "Best Overall" | "Cheapest" | "Fastest" | "Best Overnight" | "Most Reliable"
  reason: string;
  options: TrainSearchResult[];
}

export interface JourneyPlanResult {
  sourceStationCode: string;
  destinationStationCode: string;
  date: string;
  travelClassFilter: string | null;
  maxFare: number | null;
  categories: RankedCategory[];
}

export interface TrainSearchParams {
  source: string;
  destination: string;
  date: string; // yyyy-MM-dd
  travelClass?: string;
  sortBy?: string;
}

export const trainsApi = {
  search: (params: TrainSearchParams) =>
    api.get<TrainSearchResult[]>('/trains/search', { params }).then((r) => r.data),

  details: (trainId: number) => api.get<TrainDetailsDto>(`/trains/${trainId}/details`).then((r) => r.data),

  availability: (scheduleId: number) =>
    api.get<AvailabilityDto[]>(`/trains/schedules/${scheduleId}/availability`).then((r) => r.data),

  plan: (params: { source: string; destination: string; date: string; travelClass?: string; maxFare?: number }) =>
    api.get<JourneyPlanResult>('/trains/plan', { params }).then((r) => r.data),
};
