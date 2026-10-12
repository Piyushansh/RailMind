import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { ArrowLeftRight, Search } from 'lucide-react';
import { trainsApi, type TrainSearchResult } from '@/api/trains';
import type { StationDto } from '@/api/stations';
import { StationPicker } from '@/components/StationPicker';
import { useBookingDraftStore } from '@/store/bookingDraftStore';
import { Button, Card, Banner } from '@/components/ui';

const SORT_OPTIONS = [
  { value: 'recommended', label: 'Recommended' },
  { value: 'cheapest', label: 'Cheapest' },
  { value: 'fastest', label: 'Fastest' },
  { value: 'earliest_departure', label: 'Earliest departure' },
];

function today(): string {
  return new Date().toISOString().slice(0, 10);
}

export default function TrainSearchPage() {
  const [source, setSource] = useState<StationDto | null>(null);
  const [destination, setDestination] = useState<StationDto | null>(null);
  const [date, setDate] = useState(today());
  const [sortBy, setSortBy] = useState('recommended');
  const [submitted, setSubmitted] = useState(false);

  const { data: results, isFetching, error } = useQuery({
    queryKey: ['trains', 'search', source?.code, destination?.code, date, sortBy],
    queryFn: () => trainsApi.search({ source: source!.code, destination: destination!.code, date, sortBy }),
    enabled: submitted && !!source && !!destination,
  });

  const handleSearch = () => {
    if (!source || !destination) return;
    setSubmitted(true);
  };

  const swapStations = () => {
    if (!source && !destination) return;
    const s = source, d = destination;
    setSource(d);
    setDestination(s);
  };

  return (
    <div>
      <Card className="p-5">
        <div className="grid gap-4 sm:grid-cols-[1fr_auto_1fr_auto]">
          <StationPicker label="From" value={source} onChange={setSource} />
          <button
            type="button"
            onClick={swapStations}
            className="focus-ring mt-6 hidden h-10 w-10 items-center justify-center self-start rounded-full border border-slate-300 text-slate-500 hover:bg-slate-50 sm:flex"
            aria-label="Swap stations"
          >
            <ArrowLeftRight className="h-4 w-4" />
          </button>
          <StationPicker label="To" value={destination} onChange={setDestination} />
          <div>
            <label className="mb-1.5 block text-sm font-medium text-slate-700" htmlFor="date">Date</label>
            <input
              id="date"
              type="date"
              min={today()}
              value={date}
              onChange={(e) => setDate(e.target.value)}
              className="focus-ring w-full rounded-card border border-slate-300 bg-white px-3.5 py-2.5 text-sm"
            />
          </div>
        </div>
        <div className="mt-4 flex items-center justify-between">
          <div className="flex items-center gap-2 text-sm">
            <span className="text-slate-500">Sort:</span>
            {SORT_OPTIONS.map((opt) => (
              <button
                key={opt.value}
                onClick={() => setSortBy(opt.value)}
                className={`focus-ring rounded-full px-3 py-1 ${sortBy === opt.value ? 'bg-signal text-white' : 'bg-slate-100 text-slate-600 hover:bg-slate-200'}`}
              >
                {opt.label}
              </button>
            ))}
          </div>
          <Button onClick={handleSearch} disabled={!source || !destination} className="gap-1.5">
            <Search className="h-4 w-4" /> Search trains
          </Button>
        </div>
      </Card>

      <div className="mt-6">
        {isFetching && <p className="text-sm text-slate-500">Searching…</p>}
        {error && <Banner variant="error">Couldn't load train results. Try adjusting your search.</Banner>}
        {submitted && !isFetching && results?.length === 0 && (
          <Card className="px-6 py-16 text-center text-sm text-slate-500">
            No trains found for that route and date. Try a different date or nearby station.
          </Card>
        )}
        <div className="space-y-3">
          {results?.map((result) => (
            <TrainResultRow key={result.scheduleId} result={result} source={source!} destination={destination!} />
          ))}
        </div>
      </div>
    </div>
  );
}

function TrainResultRow({ result, source, destination }: { result: TrainSearchResult; source: StationDto; destination: StationDto }) {
  const navigate = useNavigate();
  const setSearchStations = useBookingDraftStore((s) => s.setSearchStations);
  const selectTrain = useBookingDraftStore((s) => s.selectTrain);

  const arrivalTime = addMinutes(result.departureTime, result.durationMinutes);

  const handleSelectClass = (travelClass: string) => {
    setSearchStations(source, destination);
    selectTrain(result, travelClass);
    navigate('/booking');
  };

  return (
    <Card className="p-5">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <p className="text-sm font-medium text-ink">
            {result.trainName} <span className="font-mono text-slate-400">#{result.trainNumber}</span>
          </p>
          <div className="mt-2 flex items-center gap-3 font-mono text-sm text-ink">
            <span>{result.departureTime.slice(0, 5)}</span>
            <span className="text-slate-300">→</span>
            <span>{arrivalTime}</span>
            <span className="text-xs text-slate-400">({formatDuration(result.durationMinutes)})</span>
          </div>
          <p className="mt-1 text-xs text-slate-400">
            {result.sourceStationCode} → {result.destinationStationCode} · {result.trainType}
            {result.delayMinutes > 0 && <span className="ml-2 text-amber">Running {result.delayMinutes} min late</span>}
          </p>
        </div>
        <button
          onClick={() => navigate(`/trains/${result.trainId}`)}
          className="focus-ring text-xs font-medium text-signal hover:underline"
        >
          View route
        </button>
      </div>

      <div className="mt-4 flex flex-wrap gap-2 border-t border-slate-100 pt-4">
        {result.classes.map((c) => (
          <button
            key={c.travelClass}
            onClick={() => c.availableSeats > 0 && handleSelectClass(c.travelClass)}
            disabled={c.availableSeats === 0}
            className="focus-ring flex min-w-[92px] flex-col items-start rounded-card border border-slate-200 px-3 py-2 text-left hover:border-signal disabled:cursor-not-allowed disabled:opacity-50 disabled:hover:border-slate-200"
          >
            <span className="text-xs font-medium text-slate-500">{c.travelClass}</span>
            <span className="font-mono text-sm font-semibold text-ink">₹{c.fare}</span>
            <span className={`text-xs ${c.availableSeats > 0 ? 'text-rail' : 'text-danger'}`}>
              {c.availableSeats > 0 ? `${c.availableSeats} left` : 'Full'}
            </span>
          </button>
        ))}
      </div>
    </Card>
  );
}

function addMinutes(time: string, minutes: number): string {
  const [h, m] = time.split(':').map(Number);
  const total = h * 60 + m + minutes;
  const dayOffset = Math.floor(total / 1440);
  const hh = Math.floor((total % 1440) / 60).toString().padStart(2, '0');
  const mm = (total % 60).toString().padStart(2, '0');
  return `${hh}:${mm}${dayOffset > 0 ? ` +${dayOffset}d` : ''}`;
}

function formatDuration(minutes: number): string {
  const h = Math.floor(minutes / 60);
  const m = minutes % 60;
  return `${h}h ${m}m`;
}
