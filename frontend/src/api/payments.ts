import { api } from '@/api/client';

export interface PaymentResultDto {
  paymentId: number;
  bookingId: number;
  status: string; // SUCCESS | FAILED | PENDING
  bookingStatus: string;
  pnrNumber: string | null;
  failureReason: string | null;
}

export const paymentsApi = {
  pay: (bookingId: number, idempotencyKey: string) =>
    api.post<PaymentResultDto>('/payments', { bookingId, idempotencyKey }).then((r) => r.data),
};
