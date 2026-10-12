import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { CreditCard, ShieldCheck } from 'lucide-react';
import { bookingsApi } from '@/api/bookings';
import { paymentsApi } from '@/api/payments';
import { useBookingDraftStore } from '@/store/bookingDraftStore';
import { extractErrorMessage } from '@/api/client';
import { Button, Card, Banner } from '@/components/ui';

export default function PaymentPage() {
  const navigate = useNavigate();
  const bookingId = useBookingDraftStore((s) => s.lastBookingId);
  const [error, setError] = useState<string | null>(null);
  const [paying, setPaying] = useState(false);

  const { data: booking, isLoading } = useQuery({
    queryKey: ['bookings', bookingId],
    queryFn: () => bookingsApi.get(bookingId!),
    enabled: !!bookingId,
  });

  if (!bookingId) {
    return (
      <Card className="mx-auto max-w-md px-6 py-16 text-center">
        <p className="text-sm text-slate-500">No booking in progress. Start a search to book a journey.</p>
        <Button className="mt-4" onClick={() => navigate('/trains')}>
          Search trains
        </Button>
      </Card>
    );
  }

  const handlePay = async () => {
    setError(null);
    setPaying(true);
    try {
      const result = await paymentsApi.pay(bookingId, crypto.randomUUID());
      if (result.status === 'SUCCESS') {
        navigate('/booking-success', { state: { bookingId } });
      } else if (result.status === 'PENDING') {
        setError('Payment is still processing. Refresh in a moment, or check My bookings.');
      } else {
        setError(result.failureReason ?? 'Payment failed. Your seat hold may expire - please try again.');
      }
    } catch (err) {
      setError(extractErrorMessage(err));
    } finally {
      setPaying(false);
    }
  };

  return (
    <div className="mx-auto max-w-md">
      <h1 className="text-xl font-semibold text-ink">Payment</h1>
      <p className="mt-1 text-sm text-slate-500">Your seat is held while you complete payment.</p>

      {error && <div className="mt-4"><Banner variant="error">{error}</Banner></div>}

      {isLoading && <p className="mt-6 text-sm text-slate-500">Loading booking…</p>}

      {booking && (
        <Card className="mt-6 p-5">
          <div className="flex items-center justify-between text-sm">
            <span className="text-slate-500">{booking.trainName} #{booking.trainNumber}</span>
            <span className="font-mono text-ink">{booking.travelClass}</span>
          </div>
          <div className="mt-1 flex items-center justify-between text-sm">
            <span className="text-slate-500">{booking.sourceStationCode} → {booking.destinationStationCode}</span>
            <span className="text-slate-500">{booking.passengers.length} passenger(s)</span>
          </div>
          <div className="mt-4 flex items-center justify-between border-t border-slate-100 pt-4">
            <span className="text-sm font-medium text-ink">Amount due</span>
            <span className="font-mono text-lg font-semibold text-ink">₹{booking.totalFare}</span>
          </div>
        </Card>
      )}

      <Card className="mt-4 flex items-center gap-3 p-4 text-sm text-slate-500">
        <CreditCard className="h-5 w-5 shrink-0 text-slate-400" />
        Demo payment - no real card is charged. This simulates a real gateway response (success, failure, or pending).
      </Card>

      <Button className="mt-6 w-full gap-2" loading={paying} onClick={handlePay} disabled={!booking}>
        <ShieldCheck className="h-4 w-4" /> Pay ₹{booking?.totalFare ?? '—'}
      </Button>
    </div>
  );
}
