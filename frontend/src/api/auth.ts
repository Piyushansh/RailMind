import { api } from '@/api/client';
import type { AuthResponse } from '@/types/auth';

export const authApi = {
  register: (data: { fullName: string; email: string; password: string; phone?: string }) =>
    api.post<AuthResponse>('/auth/register', data).then((r) => r.data),

  login: (data: { email: string; password: string }) =>
    api.post<AuthResponse>('/auth/login', data).then((r) => r.data),

  logout: (refreshToken: string) => api.post('/auth/logout', { refreshToken }),

  forgotPassword: (email: string) => api.post('/auth/forgot-password', { email }),

  resetPassword: (token: string, newPassword: string) =>
    api.post('/auth/reset-password', { token, newPassword }),

  changePassword: (currentPassword: string, newPassword: string) =>
    api.post('/auth/change-password', { currentPassword, newPassword }),

  verifyEmail: (token: string) => api.post('/auth/verify-email', { token }),

  resendVerification: () => api.post('/auth/resend-verification'),
};
