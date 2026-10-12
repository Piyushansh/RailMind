import { useQuery, useQueryClient } from '@tanstack/react-query';
import { formatDistanceToNow } from 'date-fns';
import { Bell, CheckCheck } from 'lucide-react';
import { notificationsApi } from '@/api/notifications';
import { Button, Card } from '@/components/ui';

export default function NotificationsPage() {
  const queryClient = useQueryClient();
  const { data, isLoading } = useQuery({ queryKey: ['notifications', 'summary'], queryFn: notificationsApi.list });

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['notifications', 'summary'] });

  const handleMarkRead = async (id: number) => {
    await notificationsApi.markRead(id);
    invalidate();
  };

  const handleMarkAllRead = async () => {
    await notificationsApi.markAllRead();
    invalidate();
  };

  return (
    <div className="max-w-2xl">
      <div className="mb-6 flex items-center justify-between">
        <h1 className="text-xl font-semibold text-ink">Notifications</h1>
        {!!data?.unreadCount && (
          <Button variant="ghost" onClick={handleMarkAllRead} className="gap-1.5 text-sm">
            <CheckCheck className="h-4 w-4" /> Mark all read
          </Button>
        )}
      </div>

      {isLoading && <p className="text-sm text-slate-500">Loading…</p>}

      {!isLoading && data?.notifications.length === 0 && (
        <Card className="flex flex-col items-center gap-2 px-6 py-16 text-center">
          <Bell className="h-6 w-6 text-slate-300" />
          <p className="text-sm text-slate-500">Nothing here yet. Booking and journey updates will show up in this list.</p>
        </Card>
      )}

      <div className="space-y-2">
        {data?.notifications.map((n) => (
          <Card
            key={n.id}
            className={`px-4 py-3.5 ${n.read ? '' : 'border-signal/30 bg-signal-soft/40'}`}
          >
            <div className="flex items-start justify-between gap-4">
              <div>
                <p className="text-sm font-medium text-ink">{n.title}</p>
                <p className="mt-0.5 text-sm text-slate-600">{n.message}</p>
                <p className="mt-1.5 text-xs text-slate-400">{formatDistanceToNow(new Date(n.createdAt), { addSuffix: true })}</p>
              </div>
              {!n.read && (
                <button
                  onClick={() => handleMarkRead(n.id)}
                  className="focus-ring shrink-0 rounded-card px-2 py-1 text-xs font-medium text-signal hover:bg-signal-soft"
                >
                  Mark read
                </button>
              )}
            </div>
          </Card>
        ))}
      </div>
    </div>
  );
}
