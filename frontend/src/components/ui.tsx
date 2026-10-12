import { forwardRef, type ButtonHTMLAttributes, type InputHTMLAttributes, type ReactNode } from 'react';
import clsx from 'clsx';

export const Button = forwardRef<HTMLButtonElement, ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: 'primary' | 'secondary' | 'ghost' | 'danger';
  loading?: boolean;
}>(({ className, variant = 'primary', loading, disabled, children, ...props }, ref) => {
  const base = 'focus-ring inline-flex items-center justify-center gap-2 rounded-card px-4 py-2.5 text-sm font-medium transition-colors disabled:cursor-not-allowed disabled:opacity-50';
  const variants = {
    primary: 'bg-signal text-white hover:bg-signal-hover',
    secondary: 'bg-white text-ink border border-slate-300 hover:bg-slate-50',
    ghost: 'text-ink hover:bg-slate-100',
    danger: 'bg-danger text-white hover:bg-danger/90',
  };
  return (
    <button ref={ref} className={clsx(base, variants[variant], className)} disabled={disabled || loading} {...props}>
      {loading && <span className="h-3.5 w-3.5 animate-spin rounded-full border-2 border-white/40 border-t-white" />}
      {children}
    </button>
  );
});
Button.displayName = 'Button';

export const Input = forwardRef<HTMLInputElement, InputHTMLAttributes<HTMLInputElement> & { hasError?: boolean }>(
  ({ className, hasError, ...props }, ref) => (
    <input
      ref={ref}
      className={clsx(
        'focus-ring w-full rounded-card border bg-white px-3.5 py-2.5 text-sm placeholder:text-slate-400',
        hasError ? 'border-danger' : 'border-slate-300',
        className
      )}
      {...props}
    />
  )
);
Input.displayName = 'Input';

export function Field({
  label,
  htmlFor,
  error,
  children,
  hint,
}: {
  label: string;
  htmlFor: string;
  error?: string;
  hint?: string;
  children: ReactNode;
}) {
  return (
    <div className="space-y-1.5">
      <label htmlFor={htmlFor} className="block text-sm font-medium text-slate-700">
        {label}
      </label>
      {children}
      {hint && !error && <p className="text-xs text-slate-500">{hint}</p>}
      {error && <p className="text-xs text-danger">{error}</p>}
    </div>
  );
}

export function Card({ className, children }: { className?: string; children: ReactNode }) {
  return <div className={clsx('rounded-card border border-slate-200 bg-white', className)}>{children}</div>;
}

export function Banner({ variant, children }: { variant: 'info' | 'success' | 'warning' | 'error'; children: ReactNode }) {
  const styles = {
    info: 'bg-signal-soft text-signal-hover border-signal/20',
    success: 'bg-rail-soft text-rail border-rail/20',
    warning: 'bg-amber-soft text-amber border-amber/30',
    error: 'bg-danger-soft text-danger border-danger/20',
  };
  return <div className={clsx('rounded-card border px-4 py-3 text-sm', styles[variant])}>{children}</div>;
}
