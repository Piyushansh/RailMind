import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { bookingsApi } from '@/api/bookings';
import { Card } from '@/components/ui';
import { StatusPill } from '@/components/StatusPill';

export default function BookingsListPage() {
  const { data: bookings, isLoading } = useQuery({ queryKey: ['bookings'], queryFn: bookingsApi.list });

  return (
    <div className="max-w-3xl">
      <h1 className="mb-6 text-xl font-semibold text-ink">My bookings</h1>

      {isLoading && <p className="text-sm text-slate-500">Loading…</p>}

      {!isLoading && bookings?.length === 0 && (
        <Card className="px-6 py-16 text-center">
          <p className="text-sm text-slate-500">No bookings yet.</p>
          <Link to="/trains" className="mt-2 inline-block text-sm font-medium text-signal hover:underline">
            Search trains
          </Link>
        </Card>
      )}

      <div className="space-y-2">
        {bookings?.map((b) => (
          <Link key={b.bookingId} to={`/bookings/${b.bookingId}`}>
            <Card className="flex items-center justify-between p-4 transition-shadow hover:shadow-sm">
              <div>
                <p className="text-sm font-medium text-ink">
                  {b.trainName} <span className="font-mono text-slate-400">#{b.trainNumber}</span>
                </p>
                <p className="mt-0.5 text-xs text-slate-500">
                  {b.sourceStationCode} → {b.destinationStationCode} · {b.travelClass} · {b.passengers.length} passenger(s)
                </p>
                {b.pnrNumber && <p className="mt-0.5 font-mono text-xs text-slate-400">PNR {b.pnrNumber}</p>}
              </div>
              <div className="flex items-center gap-3">
                <span className="font-mono text-sm text-ink">₹{b.totalFare}</span>
                <StatusPill status={b.status} />
              </div>
            </Card>
          </Link>
        ))}
      </div>
    </div>
  );
}
