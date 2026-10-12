import { useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { format } from 'date-fns';
import { ArrowLeft, Download, Printer, Share2, XCircle } from 'lucide-react';
import { bookingsApi } from '@/api/bookings';
import { cancellationsApi } from '@/api/cancellations';
import { extractErrorMessage } from '@/api/client';
import { Button, Card, Banner } from '@/components/ui';
import { StatusPill } from '@/components/StatusPill';

export default function BookingDetailPage() {
  const { id } = useParams<{ id: string }>();
  const bookingId = Number(id);
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  const [cancelling, setCancelling] = useState(false);
  const [confirmingCancel, setConfirmingCancel] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [refundInfo, setRefundInfo] = useState<string | null>(null);

  const { data: booking, isLoading } = useQuery({
    queryKey: ['bookings', bookingId],
    queryFn: () => bookingsApi.get(bookingId),
    enabled: Number.isFinite(bookingId),
  });

  const handleCancel = async () => {
    setError(null);
    setCancelling(true);
    try {
      const refund = await cancellationsApi.cancel(bookingId);
      setRefundInfo(`${refund.explanation} Refund: ₹${refund.refundAmount} of ₹${refund.originalFare}.`);
      queryClient.invalidateQueries({ queryKey: ['bookings', bookingId] });
      queryClient.invalidateQueries({ queryKey: ['bookings'] });
    } catch (err) {
      setError(extractErrorMessage(err));
    } finally {
      setCancelling(false);
      setConfirmingCancel(false);
    }
  };

  if (isLoading) return <p className="text-sm text-slate-500">Loading…</p>;
  if (!booking) return <Banner variant="error">Booking not found.</Banner>;

  const canCancel = booking.status === 'CONFIRMED';

  return (
    <div className="mx-auto max-w-xl">
      <Button variant="ghost" onClick={() => navigate('/bookings')} className="mb-4 -ml-2 gap-1.5 px-2">
        <ArrowLeft className="h-4 w-4" /> My bookings
      </Button>

      {error && <div className="mb-4"><Banner variant="error">{error}</Banner></div>}
      {refundInfo && <div className="mb-4"><Banner variant="success">{refundInfo}</Banner></div>}

      {/* Digital ticket */}
      <Card className="overflow-hidden border-slate-200">
        <div className="flex items-center justify-between bg-ink px-6 py-4 text-white">
          <div>
            <p className="font-mono text-xs uppercase tracking-wide text-slate-400">PNR</p>
            <p className="font-mono text-xl font-semibold">{booking.pnrNumber ?? '—'}</p>
          </div>
          <StatusPill status={booking.status} />
        </div>

        <div className="px-6 py-5">
          <p className="text-sm font-medium text-ink">
            {booking.trainName} <span className="font-mono text-slate-400">#{booking.trainNumber}</span>
          </p>
          <div className="mt-3 flex items-center justify-between font-mono text-lg text-ink">
            <span>{booking.sourceStationCode}</span>
            <span className="text-slate-300">→</span>
            <span>{booking.destinationStationCode}</span>
          </div>
          <p className="mt-1 text-center text-xs text-slate-400">{format(new Date(booking.createdAt), 'PPP')}</p>

          <div className="mt-4 flex justify-center">
            <span className="rounded-card border border-slate-200 bg-slate-50 px-3 py-1 font-mono text-xs text-slate-600">
              {booking.travelClass}
            </span>
          </div>

          {/* QR-style demo representation - not a real scannable code, just a visual stand-in */}
          <div className="mx-auto mt-5 grid h-28 w-28 grid-cols-6 gap-0.5 rounded-card border border-slate-200 p-2">
            {Array.from({ length: 36 }).map((_, i) => (
              <span key={i} className={pseudoRandomBit(booking.pnrNumber ?? String(bookingId), i) ? 'bg-ink' : 'bg-transparent'} />
            ))}
          </div>

          <div className="mt-6 space-y-2 border-t border-slate-100 pt-4">
            <p className="text-xs font-medium uppercase tracking-wide text-slate-400">Passengers</p>
            {booking.passengers.map((p, i) => (
              <div key={i} className="flex items-center justify-between text-sm">
                <span className="text-ink">{p.fullName} <span className="text-slate-400">({p.age}, {p.gender})</span></span>
                <span className="font-mono text-xs text-slate-500">
                  {p.coachCode ?? '—'} / {p.seatNumber ?? '—'} {p.berthType ? `(${p.berthType})` : ''}
                </span>
              </div>
            ))}
          </div>

          <div className="mt-4 flex items-center justify-between border-t border-slate-100 pt-4">
            <span className="text-sm font-medium text-ink">Total fare</span>
            <span className="font-mono text-lg font-semibold text-ink">₹{booking.totalFare}</span>
          </div>
        </div>
      </Card>

      <div className="mt-4 flex flex-wrap gap-2">
        <Button variant="secondary" onClick={() => window.print()} className="gap-1.5 text-sm">
          <Printer className="h-4 w-4" /> Print
        </Button>
        <Button variant="secondary" onClick={() => downloadTicket(booking)} className="gap-1.5 text-sm">
          <Download className="h-4 w-4" /> Download
        </Button>
        <Button variant="secondary" onClick={() => shareTicket(booking)} className="gap-1.5 text-sm">
          <Share2 className="h-4 w-4" /> Share
        </Button>
        {canCancel && (
          <Button variant="danger" onClick={() => setConfirmingCancel(true)} className="ml-auto gap-1.5 text-sm">
            <XCircle className="h-4 w-4" /> Cancel booking
          </Button>
        )}
      </div>

      {confirmingCancel && (
        <Card className="mt-4 p-5">
          <p className="text-sm font-medium text-ink">Cancel this booking?</p>
          <p className="mt-1 text-sm text-slate-500">
            Your refund amount depends on how close to departure you cancel. This can't be undone.
          </p>
          <div className="mt-4 flex gap-2">
            <Button variant="danger" loading={cancelling} onClick={handleCancel}>
              Yes, cancel
            </Button>
            <Button variant="secondary" onClick={() => setConfirmingCancel(false)}>
              Keep booking
            </Button>
          </div>
        </Card>
      )}
    </div>
  );
}

function pseudoRandomBit(seed: string, i: number): boolean {
  let hash = 0;
  const s = seed + i;
  for (let c = 0; c < s.length; c++) hash = (hash * 31 + s.charCodeAt(c)) >>> 0;
  return hash % 2 === 0;
}

function downloadTicket(booking: { pnrNumber: string | null; trainName: string; trainNumber: string; sourceStationCode: string; destinationStationCode: string; travelClass: string; totalFare: number }) {
  const text = `RailMind Ticket\nPNR: ${booking.pnrNumber ?? '-'}\n${booking.trainName} #${booking.trainNumber}\n${booking.sourceStationCode} -> ${booking.destinationStationCode}\nClass: ${booking.travelClass}\nFare: Rs.${booking.totalFare}\n`;
  const blob = new Blob([text], { type: 'text/plain' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = `railmind-ticket-${booking.pnrNumber ?? 'ticket'}.txt`;
  a.click();
  URL.revokeObjectURL(url);
}

async function shareTicket(booking: { pnrNumber: string | null; trainName: string }) {
  const text = `My RailMind booking - ${booking.trainName}, PNR ${booking.pnrNumber ?? ''}`;
  if (navigator.share) {
    try {
      await navigator.share({ text });
    } catch {
      /* user cancelled the share sheet - nothing to do */
    }
  } else {
    await navigator.clipboard.writeText(text);
  }
}
