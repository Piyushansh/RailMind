import { useLocation, useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { CheckCircle2 } from 'lucide-react';
import { bookingsApi } from '@/api/bookings';
import { Button, Card } from '@/components/ui';

export default function BookingSuccessPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const bookingId = (location.state as { bookingId?: number } | null)?.bookingId;

  const { data: booking } = useQuery({
    queryKey: ['bookings', bookingId],
    queryFn: () => bookingsApi.get(bookingId!),
    enabled: !!bookingId,
  });

  if (!bookingId) {
    return (
      <Card className="mx-auto max-w-md px-6 py-16 text-center">
        <p className="text-sm text-slate-500">No recent booking to show.</p>
        <Button className="mt-4" onClick={() => navigate('/bookings')}>
          View my bookings
        </Button>
      </Card>
    );
  }

  return (
    <div className="mx-auto max-w-md text-center">
      <span className="mx-auto flex h-14 w-14 items-center justify-center rounded-full bg-rail-soft text-rail">
        <CheckCircle2 className="h-7 w-7" />
      </span>
      <h1 className="mt-4 text-xl font-semibold text-ink">Booking confirmed</h1>
      <p className="mt-1 text-sm text-slate-500">
        {booking ? `${booking.trainName} · ${booking.sourceStationCode} → ${booking.destinationStationCode}` : 'Your seat is confirmed.'}
      </p>

      {booking?.pnrNumber && (
        <Card className="mt-6 p-5">
          <p className="text-xs font-medium uppercase tracking-wide text-slate-400">PNR</p>
          <p className="mt-1 font-mono text-2xl font-semibold text-ink">{booking.pnrNumber}</p>
        </Card>
      )}

      <div className="mt-6 flex justify-center gap-3">
        <Button variant="secondary" onClick={() => navigate('/bookings')}>
          My bookings
        </Button>
        <Button onClick={() => navigate(`/bookings/${bookingId}`)}>View ticket</Button>
      </div>
    </div>
  );
}
