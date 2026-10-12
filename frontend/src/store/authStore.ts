import { create } from 'zustand';
import { persist } from 'zustand/middleware';

export interface UserSummary {
  id: number;
  fullName: string;
  email: string;
  roles: string[];
}

interface AuthState {
  accessToken: string | null;
  refreshToken: string | null;
  user: UserSummary | null;
  setSession: (accessToken: string, refreshToken: string, user: UserSummary) => void;
  setTokens: (accessToken: string, refreshToken: string) => void;
  clear: () => void;
  hasRole: (role: string) => boolean;
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set, get) => ({
      accessToken: null,
      refreshToken: null,
      user: null,
      setSession: (accessToken, refreshToken, user) => set({ accessToken, refreshToken, user }),
      setTokens: (accessToken, refreshToken) => set({ accessToken, refreshToken }),
      clear: () => set({ accessToken: null, refreshToken: null, user: null }),
      hasRole: (role) => get().user?.roles.includes(role) ?? false,
    }),
    { name: 'railmind-auth' }
  )
);
