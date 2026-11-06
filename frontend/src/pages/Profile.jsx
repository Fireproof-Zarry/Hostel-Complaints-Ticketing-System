import { Navigate, useLocation, useNavigate } from 'react-router-dom';
import { ArrowLeft, FilePlus2, LogOut } from 'lucide-react';
import NotificationToast from '../components/NotificationToast';
import ProfileForm from '../components/ProfileForm';
import StudentSidebar from '../components/StudentSidebar';
import { getToken, logout } from '../auth/session';
import { removeFcmToken } from '../notifications/pushNotifications';
import useForegroundNotifications from '../notifications/useForegroundNotifications';

/** Student profile page, with the same sidebar as the student dashboard. */
export default function Profile() {
  const navigate = useNavigate();
  const location = useLocation();
  // Set when the student came here from the complaint form (e.g. to fix their room number)
  const returnToComplaint = location.state?.returnTo === 'submit';
  const token = getToken();
  const { notificationToast, dismissNotificationToast } = useForegroundNotifications();

  if (!token) return <Navigate to="/login" replace />;

  const handleLogout = async () => {
    await removeFcmToken();
    logout();
    navigate('/login', { replace: true });
  };

  // Dashboard tabs live on /student; open the chosen one there
  const handleSelect = (id) => {
    if (id !== 'profile') navigate('/student', { state: { tab: id } });
  };

  return (
    <div className="min-h-screen bg-slate-100 text-slate-900 md:flex">
      <StudentSidebar activeId="profile" onSelect={handleSelect} />

      <main className="min-w-0 flex-1">
        <header className="flex min-h-16 items-center justify-between gap-4 border-b border-slate-200 bg-white px-4 shadow-sm md:px-8">
          <h2 className="text-lg font-semibold text-slate-800">My Profile</h2>
          <button
            type="button"
            onClick={handleLogout}
            className="inline-flex items-center gap-2 rounded-lg border border-slate-200 px-3 py-2 text-sm font-medium text-slate-600 transition-colors hover:bg-slate-50 hover:text-slate-900"
          >
            <LogOut className="h-4 w-4" />
            <span className="hidden sm:inline">Log out</span>
          </button>
        </header>

        <div className="space-y-6 p-4 md:p-8">
          {returnToComplaint && (
            <div className="mx-auto flex max-w-3xl flex-wrap items-center justify-between gap-3 rounded-xl border border-blue-200 bg-blue-50 p-4 text-sm text-blue-900">
              <p className="flex items-start gap-2">
                <FilePlus2 className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />
                You were filing a complaint. Your details are saved as a draft.
              </p>
              <button
                type="button"
                onClick={() => navigate('/student', { state: { tab: 'submit' } })}
                className="inline-flex items-center gap-2 rounded-lg bg-blue-600 px-3 py-1.5 font-semibold text-white transition hover:bg-blue-700"
              >
                <ArrowLeft className="h-4 w-4" aria-hidden="true" />
                Back to your complaint
              </button>
            </div>
          )}
          <ProfileForm />
        </div>
      </main>

      {notificationToast && (
        <NotificationToast
          toast={notificationToast}
          onOpen={() => navigate('/student')}
          onDismiss={dismissNotificationToast}
        />
      )}
    </div>
  );
}
