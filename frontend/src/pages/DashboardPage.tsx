import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { Bell, Sparkles, UserCog } from 'lucide-react';
import { profileApi } from '@/api/profile';
import { notificationsApi } from '@/api/notifications';
import { Card, Banner } from '@/components/ui';

export default function DashboardPage() {
  const { data: profile } = useQuery({ queryKey: ['profile'], queryFn: profileApi.getProfile });
  const { data: notifSummary } = useQuery({ queryKey: ['notifications', 'summary'], queryFn: notificationsApi.list });

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight text-ink">
          {profile ? `Welcome back, ${profile.fullName.split(' ')[0]}` : 'Welcome back'}
        </h1>
        <p className="mt-1 text-sm text-slate-500">Here's what's happening with your account.</p>
      </div>

      {profile && !profile.emailVerified && (
        <Banner variant="warning">
          Your email isn't verified yet.{' '}
          <Link to="/verify-email" className="underline">
            Verify it
          </Link>{' '}
          to make sure booking and Trip Guardian alerts reach you.
        </Banner>
      )}

      <div className="grid gap-4 sm:grid-cols-2">
        <Link to="/ai-assistant">
          <Card className="flex items-center gap-4 p-5 transition-shadow hover:shadow-sm">
            <span className="flex h-10 w-10 items-center justify-center rounded-full bg-amber-soft text-amber">
              <Sparkles className="h-5 w-5" />
            </span>
            <div>
              <p className="text-sm font-medium text-ink">Ask the AI assistant</p>
              <p className="text-sm text-slate-500">Journey planning, policy questions, and more</p>
            </div>
          </Card>
        </Link>
        <Link to="/notifications">
          <Card className="flex items-center gap-4 p-5 transition-shadow hover:shadow-sm">
            <span className="flex h-10 w-10 items-center justify-center rounded-full bg-signal-soft text-signal">
              <Bell className="h-5 w-5" />
            </span>
            <div>
              <p className="text-sm font-medium text-ink">Notifications</p>
              <p className="text-sm text-slate-500">
                {notifSummary?.unreadCount ? `${notifSummary.unreadCount} unread` : 'All caught up'}
              </p>
            </div>
          </Card>
        </Link>
        <Link to="/profile">
          <Card className="flex items-center gap-4 p-5 transition-shadow hover:shadow-sm">
            <span className="flex h-10 w-10 items-center justify-center rounded-full bg-slate-100 text-slate-600">
              <UserCog className="h-5 w-5" />
            </span>
            <div>
              <p className="text-sm font-medium text-ink">Profile & preferences</p>
              <p className="text-sm text-slate-500">Class, berth, and notification settings</p>
            </div>
          </Card>
        </Link>
      </div>

      <Banner variant="info">
        Trip Guardian, admin, support, and analytics are still being built out - this dashboard will link to them as
        each lands.
      </Banner>
    </div>
  );
}
