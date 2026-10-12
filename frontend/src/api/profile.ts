import { api } from '@/api/client';

export interface ProfileDto {
  id: number;
  fullName: string;
  email: string;
  phone: string | null;
  emailVerified: boolean;
  roles: string[];
}

export interface TravelPreferenceDto {
  preferredClass: string | null;
  preferredBerth: string | null;
  budgetMax: number | null;
  preferFastest: boolean;
  preferCheapest: boolean;
  preferOvernight: boolean;
  notifyEmail: boolean;
  notifyDelay: boolean;
  notifyPromotions: boolean;
  tripGuardianDefaultEnabled: boolean;
}

export const profileApi = {
  getProfile: () => api.get<ProfileDto>('/profile').then((r) => r.data),
  updateProfile: (data: { fullName: string; phone?: string }) =>
    api.put<ProfileDto>('/profile', data).then((r) => r.data),
  getPreferences: () => api.get<TravelPreferenceDto>('/profile/preferences').then((r) => r.data),
  updatePreferences: (data: TravelPreferenceDto) =>
    api.put<TravelPreferenceDto>('/profile/preferences', data).then((r) => r.data),
};
