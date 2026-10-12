import { api } from '@/api/client';

export interface StationDto {
  id: number;
  code: string;
  name: string;
  city: string;
  state: string;
}

export const stationsApi = {
  search: (query: string) => api.get<StationDto[]>('/stations', { params: { query } }).then((r) => r.data),
};
