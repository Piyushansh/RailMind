import { Link } from 'react-router-dom';
import { Button } from '@/components/ui';

export default function NotFoundPage() {
  return (
    <div className="flex min-h-screen flex-col items-center justify-center gap-3 bg-paper px-6 text-center">
      <p className="font-mono text-sm text-slate-400">404</p>
      <h1 className="text-xl font-semibold text-ink">This platform doesn't exist</h1>
      <p className="max-w-sm text-sm text-slate-500">The page you're looking for isn't here. Let's get you back on track.</p>
      <Link to="/" className="mt-2">
        <Button variant="secondary">Back home</Button>
      </Link>
    </div>
  );
}
