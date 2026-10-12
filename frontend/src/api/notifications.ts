import { api } from '@/api/client';

export interface NotificationDto {
  id: number;
  type: string;
  title: string;
  message: string;
  relatedBookingId: number | null;
  read: boolean;
  createdAt: string;
}

export interface NotificationSummaryDto {
  unreadCount: number;
  notifications: NotificationDto[];
}

export const notificationsApi = {
  list: () => api.get<NotificationSummaryDto>('/notifications').then((r) => r.data),
  markRead: (id: number) => api.post<NotificationDto>(`/notifications/${id}/read`).then((r) => r.data),
  markAllRead: () => api.post<{ markedRead: number }>('/notifications/read-all').then((r) => r.data),
};
