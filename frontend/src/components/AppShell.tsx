import { useState } from 'react';
import { Link, NavLink, Outlet, useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { Bell, LogOut, Search, Sparkles, TicketCheck, User as UserIcon } from 'lucide-react';
import { useAuthStore } from '@/store/authStore';
import { authApi } from '@/api/auth';
import { notificationsApi } from '@/api/notifications';

const NAV_LINKS = [
  { to: '/trains', label: 'Search trains', icon: Search },
  { to: '/bookings', label: 'My bookings', icon: TicketCheck },
  { to: '/ai-assistant', label: 'AI assistant', icon: Sparkles },
];

export function AppShell() {
  const user = useAuthStore((s) => s.user);
  const refreshToken = useAuthStore((s) => s.refreshToken);
  const clear = useAuthStore((s) => s.clear);
  const navigate = useNavigate();
  const [menuOpen, setMenuOpen] = useState(false);

  const { data: summary } = useQuery({
    queryKey: ['notifications', 'summary'],
    queryFn: notificationsApi.list,
    refetchInterval: 60_000,
  });

  const handleLogout = async () => {
    try {
      if (refreshToken) await authApi.logout(refreshToken);
    } finally {
      clear();
      navigate('/login', { replace: true });
    }
  };

  return (
    <div className="min-h-screen bg-paper">
      <header className="sticky top-0 z-10 border-b border-slate-200 bg-white/90 backdrop-blur">
        <div className="mx-auto flex max-w-6xl items-center justify-between px-6 py-3.5">
          <Link to="/dashboard" className="font-mono text-base font-semibold tracking-tight text-ink">
            RailMind
          </Link>
          <nav className="hidden items-center gap-1 sm:flex">
            {NAV_LINKS.map(({ to, label, icon: Icon }) => (
              <NavLink
                key={to}
                to={to}
                className={({ isActive }) =>
                  `focus-ring flex items-center gap-1.5 rounded-card px-3 py-2 text-sm font-medium ${
                    isActive ? 'bg-signal-soft text-signal' : 'text-slate-600 hover:bg-slate-100'
                  }`
                }
              >
                <Icon className="h-4 w-4" /> {label}
              </NavLink>
            ))}
          </nav>
          <div className="flex items-center gap-4">
            <Link to="/notifications" className="focus-ring relative rounded-card p-2 hover:bg-slate-100" aria-label="Notifications">
              <Bell className="h-5 w-5 text-slate-600" />
              {!!summary?.unreadCount && (
                <span className="absolute -right-0.5 -top-0.5 flex h-4 min-w-4 items-center justify-center rounded-full bg-danger px-1 text-[10px] font-semibold text-white">
                  {summary.unreadCount > 9 ? '9+' : summary.unreadCount}
                </span>
              )}
            </Link>
            <div className="relative">
              <button
                onClick={() => setMenuOpen((v) => !v)}
                className="focus-ring flex items-center gap-2 rounded-card px-2 py-1.5 hover:bg-slate-100"
              >
                <span className="flex h-7 w-7 items-center justify-center rounded-full bg-signal-soft text-signal">
                  <UserIcon className="h-4 w-4" />
                </span>
                <span className="hidden text-sm font-medium text-ink sm:inline">{user?.fullName}</span>
              </button>
              {menuOpen && (
                <div className="absolute right-0 mt-2 w-48 rounded-card border border-slate-200 bg-white py-1 shadow-lg">
                  <Link to="/profile" onClick={() => setMenuOpen(false)} className="block px-4 py-2 text-sm text-ink hover:bg-slate-50">
                    Profile & preferences
                  </Link>
                  <button
                    onClick={handleLogout}
                    className="flex w-full items-center gap-2 px-4 py-2 text-left text-sm text-danger hover:bg-slate-50"
                  >
                    <LogOut className="h-4 w-4" /> Sign out
                  </button>
                </div>
              )}
            </div>
          </div>
        </div>
      </header>
      <main className="mx-auto max-w-6xl px-6 py-8">
        <Outlet />
      </main>
    </div>
  );
}
