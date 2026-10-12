import clsx from 'clsx';

const STYLES: Record<string, string> = {
  CONFIRMED: 'bg-rail-soft text-rail',
  PENDING: 'bg-amber-soft text-amber',
  AWAITING_PAYMENT: 'bg-amber-soft text-amber',
  CANCELLED: 'bg-danger-soft text-danger',
  COMPLETED: 'bg-slate-100 text-slate-600',
};

export function StatusPill({ status }: { status: string }) {
  return (
    <span className={clsx('rounded-full px-2.5 py-1 text-xs font-medium', STYLES[status] ?? 'bg-slate-100 text-slate-600')}>
      {status.replace(/_/g, ' ')}
    </span>
  );
}
