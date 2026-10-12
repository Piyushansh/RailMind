import { useEffect, useState } from 'react';
import { useSearchParams, Link } from 'react-router-dom';
import { authApi } from '@/api/auth';
import { extractErrorMessage } from '@/api/client';
import { Button, Banner } from '@/components/ui';
import { AuthLayout } from '@/components/AuthLayout';

type Status = 'idle' | 'verifying' | 'success' | 'error';

export default function VerifyEmailPage() {
  const [searchParams] = useSearchParams();
  const token = searchParams.get('token');
  const [status, setStatus] = useState<Status>('idle');
  const [error, setError] = useState<string | null>(null);
  const [resent, setResent] = useState(false);

  useEffect(() => {
    if (!token) return;
    setStatus('verifying');
    authApi
      .verifyEmail(token)
      .then(() => setStatus('success'))
      .catch((err) => {
        setError(extractErrorMessage(err));
        setStatus('error');
      });
  }, [token]);

  const handleResend = async () => {
    try {
      await authApi.resendVerification();
      setResent(true);
    } catch (err) {
      setError(extractErrorMessage(err));
    }
  };

  return (
    <AuthLayout title="Verify your email" subtitle="One quick step to secure your account.">
      {status === 'verifying' && <Banner variant="info">Verifying your email…</Banner>}
      {status === 'success' && (
        <Banner variant="success">
          Your email is verified.{' '}
          <Link to="/dashboard" className="underline">
            Go to your dashboard
          </Link>
          .
        </Banner>
      )}
      {status === 'error' && <Banner variant="error">{error}</Banner>}
      {status === 'idle' && (
        <div className="space-y-4">
          <Banner variant="info">
            We sent a verification link to your email. Click it to verify, or resend it below if it didn&apos;t arrive.
          </Banner>
          {resent ? (
            <Banner variant="success">Verification email resent.</Banner>
          ) : (
            <Button variant="secondary" onClick={handleResend} className="w-full">
              Resend verification email
            </Button>
          )}
        </div>
      )}
    </AuthLayout>
  );
}
