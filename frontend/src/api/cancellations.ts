import { api } from '@/api/client';

export interface RefundDto {
  bookingId: number;
  pnrNumber: string | null;
  originalFare: number;
  deductionPercent: number;
  refundAmount: number;
  explanation: string;
  status: string;
  processedAt: string;
}

export const cancellationsApi = {
  cancel: (bookingId: number, reason?: string) =>
    api.post<RefundDto>('/cancellations', { bookingId, reason }).then((r) => r.data),
};
