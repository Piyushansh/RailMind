import { useParams, useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { ArrowLeft } from 'lucide-react';
import { trainsApi } from '@/api/trains';
import { Card, Banner, Button } from '@/components/ui';

export default function TrainDetailsPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const trainId = Number(id);

  const { data, isLoading, error } = useQuery({
    queryKey: ['trains', 'details', trainId],
    queryFn: () => trainsApi.details(trainId),
    enabled: Number.isFinite(trainId),
  });

  return (
    <div className="max-w-2xl">
      <Button variant="ghost" onClick={() => navigate(-1)} className="mb-4 -ml-2 gap-1.5 px-2">
        <ArrowLeft className="h-4 w-4" /> Back
      </Button>

      {isLoading && <p className="text-sm text-slate-500">Loading…</p>}
      {error && <Banner variant="error">Couldn't load this train's route.</Banner>}

      {data && (
        <>
          <h1 className="text-xl font-semibold text-ink">
            {data.trainName} <span className="font-mono text-slate-400">#{data.trainNumber}</span>
          </h1>
          <p className="mt-1 text-sm text-slate-500">{data.trainType}</p>

          <Card className="mt-6 p-6">
            <p className="mb-4 text-xs font-medium uppercase tracking-wide text-slate-400">Route</p>
            <ol className="space-y-0">
              {data.route.map((stop, i) => (
                <li key={stop.stationCode} className="relative flex gap-4 pb-6 last:pb-0">
                  {i < data.route.length - 1 && (
                    <span className="absolute left-[5px] top-3 h-full w-px bg-slate-200" />
                  )}
                  <span className="relative mt-1.5 h-2.5 w-2.5 shrink-0 rounded-full bg-signal" />
                  <div className="flex-1">
                    <div className="flex items-baseline justify-between">
                      <p className="text-sm font-medium text-ink">
                        {stop.stationName} <span className="font-mono text-xs text-slate-400">{stop.stationCode}</span>
                      </p>
                      <p className="font-mono text-xs text-slate-400">{stop.distanceKm} km</p>
                    </div>
                    <p className="mt-0.5 font-mono text-xs text-slate-500">
                      {i === 0 ? `Departs +${stop.departureOffsetMinutes}m` : `Arrives +${stop.arrivalOffsetMinutes}m`}
                    </p>
                  </div>
                </li>
              ))}
            </ol>
          </Card>
        </>
      )}
    </div>
  );
}
