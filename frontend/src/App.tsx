import { Routes, Route, Navigate } from 'react-router-dom';
import { ProtectedRoute } from '@/routes/guards';
import { AppShell } from '@/components/AppShell';

import LandingPage from '@/pages/LandingPage';
import LoginPage from '@/pages/auth/LoginPage';
import RegisterPage from '@/pages/auth/RegisterPage';
import ForgotPasswordPage from '@/pages/auth/ForgotPasswordPage';
import ResetPasswordPage from '@/pages/auth/ResetPasswordPage';
import VerifyEmailPage from '@/pages/auth/VerifyEmailPage';
import DashboardPage from '@/pages/DashboardPage';
import NotificationsPage from '@/pages/NotificationsPage';
import ProfilePage from '@/pages/ProfilePage';
import NotFoundPage from '@/pages/NotFoundPage';
import TrainSearchPage from '@/pages/trains/TrainSearchPage';
import TrainDetailsPage from '@/pages/trains/TrainDetailsPage';
import BookingPage from '@/pages/booking/BookingPage';
import PaymentPage from '@/pages/booking/PaymentPage';
import BookingSuccessPage from '@/pages/booking/BookingSuccessPage';
import BookingsListPage from '@/pages/bookings/BookingsListPage';
import BookingDetailPage from '@/pages/bookings/BookingDetailPage';
import AiAssistantPage from '@/pages/AiAssistantPage';

/**
 * Deliberately smaller than the full spec's route list for now: only routes
 * with a real page wired to a real backend endpoint are registered. Routes
 * like /ai-assistant, /trip-guardian, and /admin/* are not yet built -
 * they're left OUT of the router entirely (falling through to NotFoundPage)
 * rather than present as dead links or "Coming Soon" stubs.
 */
export default function App() {
  return (
    <Routes>
      <Route path="/" element={<LandingPage />} />
      <Route path="/login" element={<LoginPage />} />
      <Route path="/register" element={<RegisterPage />} />
      <Route path="/forgot-password" element={<ForgotPasswordPage />} />
      <Route path="/reset-password" element={<ResetPasswordPage />} />

      <Route element={<ProtectedRoute />}>
        <Route path="/verify-email" element={<VerifyEmailPage />} />
        <Route element={<AppShell />}>
          <Route path="/dashboard" element={<DashboardPage />} />
          <Route path="/trains" element={<TrainSearchPage />} />
          <Route path="/trains/:id" element={<TrainDetailsPage />} />
          <Route path="/booking" element={<BookingPage />} />
          <Route path="/payment" element={<PaymentPage />} />
          <Route path="/booking-success" element={<BookingSuccessPage />} />
          <Route path="/bookings" element={<BookingsListPage />} />
          <Route path="/bookings/:id" element={<BookingDetailPage />} />
          <Route path="/ai-assistant" element={<AiAssistantPage />} />
          <Route path="/notifications" element={<NotificationsPage />} />
          <Route path="/profile" element={<ProfilePage />} />
        </Route>
      </Route>

      <Route path="/404" element={<NotFoundPage />} />
      <Route path="*" element={<Navigate to="/404" replace />} />
    </Routes>
  );
}
