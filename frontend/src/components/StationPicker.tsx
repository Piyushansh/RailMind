import { useEffect, useRef, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { MapPin } from 'lucide-react';
import { stationsApi, type StationDto } from '@/api/stations';

export function StationPicker({
  label,
  value,
  onChange,
  placeholder,
}: {
  label: string;
  value: StationDto | null;
  onChange: (station: StationDto) => void;
  placeholder?: string;
}) {
  const [query, setQuery] = useState(value ? `${value.name} (${value.code})` : '');
  const [open, setOpen] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);

  const { data: results } = useQuery({
    queryKey: ['stations', query],
    queryFn: () => stationsApi.search(query),
    enabled: open && query.trim().length > 0,
  });

  useEffect(() => {
    function handleClickOutside(e: MouseEvent) {
      if (containerRef.current && !containerRef.current.contains(e.target as Node)) setOpen(false);
    }
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  const handleSelect = (station: StationDto) => {
    onChange(station);
    setQuery(`${station.name} (${station.code})`);
    setOpen(false);
  };

  return (
    <div ref={containerRef} className="relative">
      <label className="mb-1.5 block text-sm font-medium text-slate-700">{label}</label>
      <div className="relative">
        <MapPin className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
        <input
          className="focus-ring w-full rounded-card border border-slate-300 bg-white py-2.5 pl-9 pr-3.5 text-sm"
          placeholder={placeholder ?? 'City or station code'}
          value={query}
          onChange={(e) => {
            setQuery(e.target.value);
            setOpen(true);
          }}
          onFocus={() => setOpen(true)}
        />
      </div>
      {open && results && results.length > 0 && (
        <ul className="absolute z-20 mt-1 w-full overflow-hidden rounded-card border border-slate-200 bg-white shadow-lg">
          {results.map((s) => (
            <li key={s.id}>
              <button
                type="button"
                onClick={() => handleSelect(s)}
                className="flex w-full items-center justify-between px-3.5 py-2.5 text-left text-sm hover:bg-slate-50"
              >
                <span>
                  <span className="font-medium text-ink">{s.name}</span>
                  <span className="ml-1.5 text-slate-400">{s.city}, {s.state}</span>
                </span>
                <span className="font-mono text-xs text-slate-400">{s.code}</span>
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
