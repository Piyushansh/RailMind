import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { Plus, Trash2, UserCheck } from 'lucide-react';
import { bookingsApi, type PassengerRequest } from '@/api/bookings';
import { passengersApi } from '@/api/passengers';
import { useBookingDraftStore } from '@/store/bookingDraftStore';
import { extractErrorMessage } from '@/api/client';
import { Button, Card, Field, Input, Banner } from '@/components/ui';

const GENDERS = ['MALE', 'FEMALE', 'OTHER'];
const MAX_PASSENGERS = 6;

function blankPassenger(): PassengerRequest {
  return { fullName: '', age: 25, gender: 'MALE' };
}

export default function BookingPage() {
  const navigate = useNavigate();
  const { sourceStation, destinationStation, selectedResult, selectedClass, setLastBookingId } = useBookingDraftStore();

  const [passengers, setPassengers] = useState<PassengerRequest[]>([blankPassenger()]);
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const { data: savedPassengers } = useQuery({ queryKey: ['passengers'], queryFn: passengersApi.list });

  if (!sourceStation || !destinationStation || !selectedResult || !selectedClass) {
    return (
      <Card className="mx-auto max-w-md px-6 py-16 text-center">
        <p className="text-sm text-slate-500">No train selected yet. Start from a search to book a journey.</p>
        <Button className="mt-4" onClick={() => navigate('/trains')}>
          Search trains
        </Button>
      </Card>
    );
  }

  const selectedClassInfo = selectedResult.classes.find((c) => c.travelClass === selectedClass);
  const totalFare = (selectedClassInfo?.fare ?? 0) * passengers.length;

  const updatePassenger = (index: number, patch: Partial<PassengerRequest>) => {
    setPassengers((prev) => prev.map((p, i) => (i === index ? { ...p, ...patch } : p)));
  };

  const addBlankPassenger = () => {
    if (passengers.length >= MAX_PASSENGERS) return;
    setPassengers((prev) => [...prev, blankPassenger()]);
  };

  const addSavedPassenger = (savedId: number) => {
    if (passengers.length >= MAX_PASSENGERS) return;
    const saved = savedPassengers?.find((p) => p.id === savedId);
    if (!saved) return;
    setPassengers((prev) => [
      ...prev,
      { fullName: saved.fullName, age: saved.age, gender: saved.gender, berthPreference: saved.seatPreference ?? undefined, savedPassengerId: saved.id },
    ]);
  };

  const removePassenger = (index: number) => {
    setPassengers((prev) => (prev.length > 1 ? prev.filter((_, i) => i !== index) : prev));
  };

  const isValid = passengers.every((p) => p.fullName.trim().length > 0 && p.age >= 0 && p.age <= 120 && !!p.gender);

  const handleSubmit = async () => {
    setError(null);
    setSubmitting(true);
    try {
      const booking = await bookingsApi.create({
        scheduleId: selectedResult.scheduleId,
        sourceStationId: sourceStation.id,
        destinationStationId: destinationStation.id,
        travelClass: selectedClass,
        passengers,
        idempotencyKey: crypto.randomUUID(),
      });
      setLastBookingId(booking.bookingId);
      navigate('/payment');
    } catch (err) {
      setError(extractErrorMessage(err));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="grid gap-6 lg:grid-cols-[1fr_320px]">
      <div>
        <h1 className="text-xl font-semibold text-ink">Passenger details</h1>
        <p className="mt-1 text-sm text-slate-500">
          {selectedResult.trainName} #{selectedResult.trainNumber} · {sourceStation.code} → {destinationStation.code} · {selectedClass}
        </p>

        {error && <div className="mt-4"><Banner variant="error">{error}</Banner></div>}

        <div className="mt-6 space-y-4">
          {passengers.map((p, i) => (
            <Card key={i} className="p-5">
              <div className="mb-3 flex items-center justify-between">
                <p className="text-sm font-medium text-ink">Passenger {i + 1}</p>
                {passengers.length > 1 && (
                  <button onClick={() => removePassenger(i)} className="focus-ring rounded-card p-1 text-slate-400 hover:text-danger" aria-label="Remove passenger">
                    <Trash2 className="h-4 w-4" />
                  </button>
                )}
              </div>
              <div className="grid gap-3 sm:grid-cols-[2fr_1fr_1fr]">
                <Field label="Full name" htmlFor={`name-${i}`}>
                  <Input id={`name-${i}`} value={p.fullName} onChange={(e) => updatePassenger(i, { fullName: e.target.value })} />
                </Field>
                <Field label="Age" htmlFor={`age-${i}`}>
                  <Input id={`age-${i}`} type="number" min={0} max={120} value={p.age} onChange={(e) => updatePassenger(i, { age: Number(e.target.value) })} />
                </Field>
                <Field label="Gender" htmlFor={`gender-${i}`}>
                  <select
                    id={`gender-${i}`}
                    className="focus-ring w-full rounded-card border border-slate-300 bg-white px-3.5 py-2.5 text-sm"
                    value={p.gender}
                    onChange={(e) => updatePassenger(i, { gender: e.target.value })}
                  >
                    {GENDERS.map((g) => (
                      <option key={g} value={g}>{g}</option>
                    ))}
                  </select>
                </Field>
              </div>
            </Card>
          ))}
        </div>

        {passengers.length < MAX_PASSENGERS && (
          <div className="mt-4 flex flex-wrap gap-2">
            <Button variant="secondary" onClick={addBlankPassenger} className="gap-1.5 text-sm">
              <Plus className="h-4 w-4" /> Add passenger
            </Button>
            {savedPassengers?.filter((sp) => !passengers.some((p) => p.savedPassengerId === sp.id)).map((sp) => (
              <button
                key={sp.id}
                onClick={() => addSavedPassenger(sp.id)}
                className="focus-ring flex items-center gap-1.5 rounded-card border border-slate-300 px-3 py-2 text-sm text-ink hover:bg-slate-50"
              >
                <UserCheck className="h-3.5 w-3.5 text-signal" /> {sp.fullName}
              </button>
            ))}
          </div>
        )}
      </div>

      <div>
        <Card className="sticky top-20 p-5">
          <p className="mb-3 text-xs font-medium uppercase tracking-wide text-slate-400">Fare summary</p>
          <div className="space-y-1.5 text-sm">
            <div className="flex justify-between">
              <span className="text-slate-500">Fare per passenger</span>
              <span className="font-mono text-ink">₹{selectedClassInfo?.fare ?? 0}</span>
            </div>
            <div className="flex justify-between">
              <span className="text-slate-500">Passengers</span>
              <span className="font-mono text-ink">×{passengers.length}</span>
            </div>
            <div className="mt-2 flex justify-between border-t border-slate-100 pt-2 font-medium">
              <span className="text-ink">Total</span>
              <span className="font-mono text-ink">₹{totalFare}</span>
            </div>
          </div>
          <Button className="mt-5 w-full" disabled={!isValid} loading={submitting} onClick={handleSubmit}>
            Review & continue
          </Button>
          <p className="mt-2 text-center text-xs text-slate-400">Seats are held once you continue - not before.</p>
        </Card>
      </div>
    </div>
  );
}
