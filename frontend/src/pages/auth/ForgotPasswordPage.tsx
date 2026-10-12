import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { Link } from 'react-router-dom';
import { authApi } from '@/api/auth';
import { extractErrorMessage } from '@/api/client';
import { Button, Field, Input, Banner } from '@/components/ui';
import { AuthLayout } from '@/components/AuthLayout';

const schema = z.object({ email: z.string().email('Enter a valid email address') });
type FormValues = z.infer<typeof schema>;

export default function ForgotPasswordPage() {
  const [submitted, setSubmitted] = useState(false);
  const [serverError, setServerError] = useState<string | null>(null);
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({ resolver: zodResolver(schema) });

  const onSubmit = async (values: FormValues) => {
    setServerError(null);
    try {
      await authApi.forgotPassword(values.email);
      // Backend always returns 204 whether or not the email exists - the UI mirrors
      // that same non-committal response so it can't be used to enumerate accounts.
      setSubmitted(true);
    } catch (err) {
      setServerError(extractErrorMessage(err));
    }
  };

  return (
    <AuthLayout title="Reset your password" subtitle="We'll send a reset link if an account exists for that email.">
      {submitted ? (
        <Banner variant="success">
          If an account exists for that email, a reset link is on its way. Check your inbox.
        </Banner>
      ) : (
        <form onSubmit={handleSubmit(onSubmit)} className="space-y-4" noValidate>
          {serverError && <Banner variant="error">{serverError}</Banner>}
          <Field label="Email" htmlFor="email" error={errors.email?.message}>
            <Input id="email" type="email" autoComplete="email" hasError={!!errors.email} {...register('email')} />
          </Field>
          <Button type="submit" className="w-full" loading={isSubmitting}>
            Send reset link
          </Button>
        </form>
      )}
      <p className="mt-6 text-center text-sm text-slate-600">
        <Link to="/login" className="font-medium text-signal hover:underline">
          Back to sign in
        </Link>
      </p>
    </AuthLayout>
  );
}
