import { Navigate, useNavigate } from 'react-router-dom';
import { LogOut } from 'lucide-react';
import ProfileForm from '../components/ProfileForm';
import StudentSidebar from '../components/StudentSidebar';

/** Student profile page, with the same sidebar as the student dashboard. */
export default function Profile() {
  const navigate = useNavigate();
  const token = localStorage.getItem('idToken');

  if (!token) return <Navigate to="/login" replace />;

  const handleLogout = () => {
    localStorage.removeItem('idToken');
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

        <div className="p-4 md:p-8">
          <ProfileForm />
        </div>
      </main>
    </div>
  );
}
