import { api } from '@/api/client';

export interface SavedPassengerDto {
  id: number;
  fullName: string;
  age: number;
  gender: string;
  seatPreference: string | null;
}

export interface SavedPassengerInput {
  fullName: string;
  age: number;
  gender: string;
  seatPreference?: string;
}

export const passengersApi = {
  list: () => api.get<SavedPassengerDto[]>('/passengers').then((r) => r.data),
  create: (data: SavedPassengerInput) => api.post<SavedPassengerDto>('/passengers', data).then((r) => r.data),
  update: (id: number, data: SavedPassengerInput) => api.put<SavedPassengerDto>(`/passengers/${id}`, data).then((r) => r.data),
  delete: (id: number) => api.delete(`/passengers/${id}`),
};
