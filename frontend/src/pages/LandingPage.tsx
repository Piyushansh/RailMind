import { Link } from 'react-router-dom';
import { Button } from '@/components/ui';

export default function LandingPage() {
  return (
    <div className="min-h-screen bg-paper">
      <header className="mx-auto flex max-w-6xl items-center justify-between px-6 py-6">
        <span className="font-mono text-lg font-semibold tracking-tight text-ink">RailMind</span>
        <nav className="flex items-center gap-3">
          <Link to="/login" className="focus-ring rounded-card px-3 py-2 text-sm font-medium text-ink hover:bg-slate-100">
            Sign in
          </Link>
          <Link to="/register">
            <Button>Create account</Button>
          </Link>
        </nav>
      </header>

      <main className="mx-auto max-w-6xl px-6 pb-24 pt-12 sm:pt-20">
        <div className="grid gap-16 lg:grid-cols-[1.1fr_1fr] lg:items-center">
          <div>
            <h1 className="text-4xl font-semibold leading-[1.1] tracking-tight text-ink sm:text-5xl">
              Book the train.
              <br />
              Not the anxiety.
            </h1>
            <p className="mt-5 max-w-md text-lg text-slate-600">
              Search real availability, book in a few taps, and get ahead of delays before the platform board does -
              RailMind watches your journey and finds you an alternative the moment something changes.
            </p>
            <div className="mt-8 flex flex-wrap gap-3">
              <Link to="/register">
                <Button className="px-6 py-3 text-base">Get started</Button>
              </Link>
              <Link to="/login">
                <Button variant="secondary" className="px-6 py-3 text-base">
                  I have an account
                </Button>
              </Link>
            </div>
          </div>

          <div className="rounded-card border border-slate-200 bg-white p-6 shadow-sm">
            <p className="mb-4 text-xs font-medium uppercase tracking-wide text-slate-400">Sample journey</p>
            <div className="space-y-4 font-mono text-sm">
              <Row time="06:05" place="New Delhi (NDLS)" note="Platform 12" />
              <div className="ml-[3px] h-8 border-l-2 border-dashed border-slate-300 pl-5 text-xs text-slate-400">
                7h 40m · Rajdhani Express
              </div>
              <Row time="13:45" place="Mumbai Central (MMCT)" note="On time" highlight />
            </div>
          </div>
        </div>
      </main>
    </div>
  );
}

function Row({ time, place, note, highlight }: { time: string; place: string; note: string; highlight?: boolean }) {
  return (
    <div className="flex items-center gap-4">
      <span className="w-14 shrink-0 font-semibold text-ink">{time}</span>
      <span className={`h-2.5 w-2.5 shrink-0 rounded-full ${highlight ? 'bg-rail' : 'bg-slate-300'}`} />
      <span className="flex-1 text-ink">{place}</span>
      <span className={highlight ? 'text-rail' : 'text-slate-400'}>{note}</span>
    </div>
  );
}
