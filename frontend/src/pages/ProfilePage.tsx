import { useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { profileApi, type TravelPreferenceDto } from '@/api/profile';
import { extractErrorMessage } from '@/api/client';
import { Button, Card, Field, Input, Banner } from '@/components/ui';

const profileSchema = z.object({
  fullName: z.string().min(1, 'Full name is required'),
  phone: z.string().optional(),
});
type ProfileFormValues = z.infer<typeof profileSchema>;

const CLASSES = ['SL', 'AC3', 'AC2', 'AC1', 'CC'];
const BERTHS = ['LOWER', 'MIDDLE', 'UPPER', 'SIDE_LOWER', 'SIDE_UPPER', 'WINDOW', 'AISLE'];

export default function ProfilePage() {
  const queryClient = useQueryClient();
  const { data: profile } = useQuery({ queryKey: ['profile'], queryFn: profileApi.getProfile });
  const { data: preferences } = useQuery({ queryKey: ['profile', 'preferences'], queryFn: profileApi.getPreferences });

  const [profileMessage, setProfileMessage] = useState<{ type: 'success' | 'error'; text: string } | null>(null);
  const [prefsMessage, setPrefsMessage] = useState<{ type: 'success' | 'error'; text: string } | null>(null);
  const [prefsDraft, setPrefsDraft] = useState<TravelPreferenceDto | null>(null);
  const [savingPrefs, setSavingPrefs] = useState(false);

  useEffect(() => {
    if (preferences) setPrefsDraft(preferences);
  }, [preferences]);

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isSubmitting },
  } = useForm<ProfileFormValues>({ resolver: zodResolver(profileSchema) });

  useEffect(() => {
    if (profile) reset({ fullName: profile.fullName, phone: profile.phone ?? '' });
  }, [profile, reset]);

  const onSubmitProfile = async (values: ProfileFormValues) => {
    setProfileMessage(null);
    try {
      await profileApi.updateProfile(values);
      queryClient.invalidateQueries({ queryKey: ['profile'] });
      setProfileMessage({ type: 'success', text: 'Profile updated.' });
    } catch (err) {
      setProfileMessage({ type: 'error', text: extractErrorMessage(err) });
    }
  };

  const savePreferences = async () => {
    if (!prefsDraft) return;
    setSavingPrefs(true);
    setPrefsMessage(null);
    try {
      const saved = await profileApi.updatePreferences(prefsDraft);
      setPrefsDraft(saved);
      queryClient.invalidateQueries({ queryKey: ['profile', 'preferences'] });
      setPrefsMessage({ type: 'success', text: 'Preferences saved.' });
    } catch (err) {
      setPrefsMessage({ type: 'error', text: extractErrorMessage(err) });
    } finally {
      setSavingPrefs(false);
    }
  };

  return (
    <div className="max-w-2xl space-y-8">
      <div>
        <h1 className="text-xl font-semibold text-ink">Profile & preferences</h1>
        <p className="mt-1 text-sm text-slate-500">
          Preferences here feed the AI assistant's journey recommendations, so you don't have to repeat yourself.
        </p>
      </div>

      <Card className="p-6">
        <h2 className="mb-4 text-sm font-semibold text-ink">Account details</h2>
        <form onSubmit={handleSubmit(onSubmitProfile)} className="space-y-4" noValidate>
          {profileMessage && <Banner variant={profileMessage.type}>{profileMessage.text}</Banner>}
          <Field label="Full name" htmlFor="fullName" error={errors.fullName?.message}>
            <Input id="fullName" hasError={!!errors.fullName} {...register('fullName')} />
          </Field>
          <Field label="Phone" htmlFor="phone">
            <Input id="phone" type="tel" {...register('phone')} />
          </Field>
          <div>
            <p className="mb-1 text-sm font-medium text-slate-700">Email</p>
            <p className="text-sm text-slate-500">
              {profile?.email} {profile?.emailVerified ? '· Verified' : '· Not verified'}
            </p>
          </div>
          <Button type="submit" loading={isSubmitting}>
            Save changes
          </Button>
        </form>
      </Card>

      <Card className="p-6">
        <h2 className="mb-4 text-sm font-semibold text-ink">Travel preferences</h2>
        {prefsMessage && <div className="mb-4"><Banner variant={prefsMessage.type}>{prefsMessage.text}</Banner></div>}
        {prefsDraft && (
          <div className="space-y-5">
            <div className="grid grid-cols-2 gap-4">
              <Field label="Preferred class" htmlFor="preferredClass">
                <select
                  id="preferredClass"
                  className="focus-ring w-full rounded-card border border-slate-300 bg-white px-3.5 py-2.5 text-sm"
                  value={prefsDraft.preferredClass ?? ''}
                  onChange={(e) => setPrefsDraft({ ...prefsDraft, preferredClass: e.target.value || null })}
                >
                  <option value="">No preference</option>
                  {CLASSES.map((c) => (
                    <option key={c} value={c}>{c}</option>
                  ))}
                </select>
              </Field>
              <Field label="Preferred berth" htmlFor="preferredBerth">
                <select
                  id="preferredBerth"
                  className="focus-ring w-full rounded-card border border-slate-300 bg-white px-3.5 py-2.5 text-sm"
                  value={prefsDraft.preferredBerth ?? ''}
                  onChange={(e) => setPrefsDraft({ ...prefsDraft, preferredBerth: e.target.value || null })}
                >
                  <option value="">No preference</option>
                  {BERTHS.map((b) => (
                    <option key={b} value={b}>{b.replace('_', ' ')}</option>
                  ))}
                </select>
              </Field>
            </div>

            <Field label="Maximum budget (₹)" htmlFor="budgetMax">
              <Input
                id="budgetMax"
                type="number"
                min={0}
                value={prefsDraft.budgetMax ?? ''}
                onChange={(e) => setPrefsDraft({ ...prefsDraft, budgetMax: e.target.value ? Number(e.target.value) : null })}
              />
            </Field>

            <div className="space-y-2">
              <ToggleRow label="Prefer fastest journeys" checked={prefsDraft.preferFastest} onChange={(v) => setPrefsDraft({ ...prefsDraft, preferFastest: v })} />
              <ToggleRow label="Prefer cheapest journeys" checked={prefsDraft.preferCheapest} onChange={(v) => setPrefsDraft({ ...prefsDraft, preferCheapest: v })} />
              <ToggleRow label="Prefer overnight journeys" checked={prefsDraft.preferOvernight} onChange={(v) => setPrefsDraft({ ...prefsDraft, preferOvernight: v })} />
              <ToggleRow label="Email me about bookings" checked={prefsDraft.notifyEmail} onChange={(v) => setPrefsDraft({ ...prefsDraft, notifyEmail: v })} />
              <ToggleRow label="Notify me about delays" checked={prefsDraft.notifyDelay} onChange={(v) => setPrefsDraft({ ...prefsDraft, notifyDelay: v })} />
              <ToggleRow label="Enable Trip Guardian by default on new bookings" checked={prefsDraft.tripGuardianDefaultEnabled} onChange={(v) => setPrefsDraft({ ...prefsDraft, tripGuardianDefaultEnabled: v })} />
            </div>

            <Button onClick={savePreferences} loading={savingPrefs}>
              Save preferences
            </Button>
          </div>
        )}
      </Card>
    </div>
  );
}

function ToggleRow({ label, checked, onChange }: { label: string; checked: boolean; onChange: (v: boolean) => void }) {
  return (
    <label className="flex items-center justify-between gap-4 py-1">
      <span className="text-sm text-ink">{label}</span>
      <button
        type="button"
        role="switch"
        aria-checked={checked}
        onClick={() => onChange(!checked)}
        className={`focus-ring relative h-6 w-11 shrink-0 rounded-full transition-colors ${checked ? 'bg-signal' : 'bg-slate-300'}`}
      >
        <span className={`absolute top-0.5 h-5 w-5 rounded-full bg-white shadow transition-transform ${checked ? 'translate-x-5' : 'translate-x-0.5'}`} />
      </button>
    </label>
  );
}
