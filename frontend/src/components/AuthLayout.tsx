import type { ReactNode } from 'react';
import { Link } from 'react-router-dom';

export function AuthLayout({ title, subtitle, children }: { title: string; subtitle: string; children: ReactNode }) {
  return (
    <div className="grid min-h-screen grid-cols-1 lg:grid-cols-[1.1fr_1fr]">
      <div className="relative hidden overflow-hidden bg-ink lg:flex lg:flex-col lg:justify-between lg:p-12">
        <Link to="/" className="font-mono text-lg font-semibold tracking-tight text-white">
          RailMind
        </Link>
        <div>
          <svg viewBox="0 0 400 120" className="mb-8 w-full max-w-md text-signal" aria-hidden="true">
            <line x1="0" y1="60" x2="400" y2="60" stroke="currentColor" strokeWidth="2" strokeDasharray="1 14" strokeLinecap="round" />
            {[20, 140, 260, 380].map((x, i) => (
              <circle key={x} cx={x} cy={60} r={i === 3 ? 7 : 5} fill={i === 3 ? '#F2A93B' : 'currentColor'} />
            ))}
          </svg>
          <p className="max-w-sm text-2xl font-medium leading-snug text-white">
            Every journey, tracked from search to platform - with a plan for when things change.
          </p>
        </div>
        <p className="text-sm text-slate-400">Real-time availability. Grounded policy answers. No guesswork.</p>
      </div>
      <div className="flex items-center justify-center p-6 sm:p-12">
        <div className="w-full max-w-sm">
          <Link to="/" className="mb-8 inline-block font-mono text-lg font-semibold tracking-tight lg:hidden">
            RailMind
          </Link>
          <h1 className="text-2xl font-semibold tracking-tight text-ink">{title}</h1>
          <p className="mt-1 mb-8 text-sm text-slate-600">{subtitle}</p>
          {children}
        </div>
      </div>
    </div>
  );
}
