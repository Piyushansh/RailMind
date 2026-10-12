import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { authApi } from '@/api/auth';
import { extractErrorMessage } from '@/api/client';
import { Button, Field, Input, Banner } from '@/components/ui';
import { AuthLayout } from '@/components/AuthLayout';

const schema = z
  .object({
    newPassword: z.string().min(8, 'Password must be at least 8 characters'),
    confirmPassword: z.string(),
  })
  .refine((v) => v.newPassword === v.confirmPassword, {
    message: "Passwords don't match",
    path: ['confirmPassword'],
  });
type FormValues = z.infer<typeof schema>;

export default function ResetPasswordPage() {
  const [searchParams] = useSearchParams();
  const token = searchParams.get('token');
  const navigate = useNavigate();
  const [serverError, setServerError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({ resolver: zodResolver(schema) });

  const onSubmit = async (values: FormValues) => {
    if (!token) return;
    setServerError(null);
    try {
      await authApi.resetPassword(token, values.newPassword);
      navigate('/login', { state: { justReset: true } });
    } catch (err) {
      setServerError(extractErrorMessage(err));
    }
  };

  if (!token) {
    return (
      <AuthLayout title="Reset your password" subtitle="This link is missing its reset token.">
        <Banner variant="error">
          This reset link looks incomplete. Request a new one from the{' '}
          <Link to="/forgot-password" className="underline">
            forgot password
          </Link>{' '}
          page.
        </Banner>
      </AuthLayout>
    );
  }

  return (
    <AuthLayout title="Choose a new password" subtitle="Make it at least 8 characters.">
      <form onSubmit={handleSubmit(onSubmit)} className="space-y-4" noValidate>
        {serverError && <Banner variant="error">{serverError}</Banner>}
        <Field label="New password" htmlFor="newPassword" error={errors.newPassword?.message}>
          <Input id="newPassword" type="password" autoComplete="new-password" hasError={!!errors.newPassword} {...register('newPassword')} />
        </Field>
        <Field label="Confirm new password" htmlFor="confirmPassword" error={errors.confirmPassword?.message}>
          <Input id="confirmPassword" type="password" autoComplete="new-password" hasError={!!errors.confirmPassword} {...register('confirmPassword')} />
        </Field>
        <Button type="submit" className="w-full" loading={isSubmitting}>
          Reset password
        </Button>
      </form>
    </AuthLayout>
  );
}
