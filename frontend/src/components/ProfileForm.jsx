import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { AlertCircle, CheckCircle2, RefreshCw, UserRound } from 'lucide-react';
import { getFloorForRoom } from '../utils/hostelLocation';

const apiBaseUrl = (import.meta.env.VITE_API_URL || '/api').replace(/\/$/, '');

const emptyProfile = {
  fullName: '',
  phoneNumber: '',
  rollNumber: '',
  roomNumber: '',
};

const inputClasses =
  'w-full rounded-lg border border-slate-300 px-4 py-2.5 text-sm outline-none transition focus:border-blue-500 focus:ring-2 focus:ring-blue-200 aria-[invalid=true]:border-rose-400';

// Mirrors the backend rules in UserService.updateProfile
function validateProfile(form, isStudent) {
  const errors = {};
  if (form.fullName.trim().length > 100) {
    errors.fullName = 'Full name must be at most 100 characters.';
  }
  const phone = form.phoneNumber.trim();
  const phoneDigits = phone.replace(/\D/g, '').length;
  if (phone && (!/^\+?[0-9][0-9 -]*[0-9]$/.test(phone) || phoneDigits < 10 || phoneDigits > 15)) {
    errors.phoneNumber = 'Enter 10-15 digits, optionally starting with +.';
  }
  if (isStudent) {
    const roll = form.rollNumber.trim();
    if (roll && !/^[A-Za-z0-9]{4,20}$/.test(roll)) {
      errors.rollNumber = 'Use 4-20 letters or digits, e.g. CS23B051.';
    }
    const room = form.roomNumber.trim();
    if (room && !getFloorForRoom(room)) {
      errors.roomNumber = 'Enter a 3-digit hostel room from 101 to 799.';
    }
  }
  return errors;
}

function Field({ id, label, hint, error, children }) {
  return (
    <div>
      <label htmlFor={id} className="mb-1.5 block text-sm font-medium text-slate-700">{label}</label>
      {children}
      <p
        id={`${id}-message`}
        role={error ? 'alert' : undefined}
        className={`mt-1.5 text-sm ${error ? 'text-rose-700' : 'text-slate-500'}`}
      >
        {error || hint}
      </p>
    </div>
  );
}

/**
 * Profile form for the signed-in user (students and admins). Used on the
 * student Profile page and inside the admin dashboard.
 */
export default function ProfileForm() {
  const navigate = useNavigate();
  const token = localStorage.getItem('idToken');

  const [user, setUser] = useState(null);
  const [form, setForm] = useState(emptyProfile);
  const [isLoading, setIsLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [showErrors, setShowErrors] = useState(false);

  useEffect(() => {
    if (!token) return undefined;
    const controller = new AbortController();

    fetch(`${apiBaseUrl}/auth/me`, {
      headers: { Authorization: `Bearer ${token}` },
      signal: controller.signal,
    })
      .then(async (response) => {
        if (response.status === 401) {
          localStorage.removeItem('idToken');
          navigate('/login', { replace: true });
          return;
        }
        if (!response.ok) throw new Error(`Could not load your profile (HTTP ${response.status}).`);
        const data = await response.json();
        setUser(data);
        setForm({
          fullName: data.fullName ?? data.name ?? '',
          phoneNumber: data.phoneNumber ?? '',
          rollNumber: data.rollNumber ?? '',
          roomNumber: data.roomNumber ?? '',
        });
      })
      .catch((loadError) => {
        if (loadError.name !== 'AbortError') setError(loadError.message);
      })
      .finally(() => setIsLoading(false));

    return () => controller.abort();
  }, [navigate, token]);

  const isStudent = user?.role === 'STUDENT';
  const errors = validateProfile(form, isStudent);
  const visibleErrors = showErrors ? errors : {};
  const roomFloor = getFloorForRoom(form.roomNumber);

  const updateField = (field) => (event) => {
    setForm((current) => ({ ...current, [field]: event.target.value }));
    setSuccess('');
  };

  const handleLogout = () => {
    localStorage.removeItem('idToken');
    navigate('/login', { replace: true });
  };

  const handleSubmit = async (event) => {
    event.preventDefault();
    setError('');
    setSuccess('');
    setShowErrors(true);
    if (Object.keys(errors).length > 0) return;

    setIsSaving(true);
    try {
      const response = await fetch(`${apiBaseUrl}/users/${user.id}/profile`, {
        method: 'PUT',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${token}`,
        },
        body: JSON.stringify({
          fullName: form.fullName,
          phoneNumber: form.phoneNumber,
          // Admins have no roll or room number
          rollNumber: isStudent ? form.rollNumber : null,
          roomNumber: isStudent ? form.roomNumber : null,
        }),
      });

      if (response.status === 401) {
        handleLogout();
        return;
      }
      if (response.status === 400) {
        throw new Error('Some details were rejected. Check the phone, roll and room number formats.');
      }
      if (response.status === 403) {
        throw new Error('You can only edit your own profile.');
      }
      if (!response.ok) {
        throw new Error(`Your profile could not be saved (HTTP ${response.status}). Please try again.`);
      }

      const saved = await response.json();
      setUser((current) => ({ ...current, ...saved }));
      setForm({
        fullName: saved.fullName ?? '',
        phoneNumber: saved.phoneNumber ?? '',
        rollNumber: saved.rollNumber ?? '',
        roomNumber: saved.roomNumber ?? '',
      });
      setShowErrors(false);
      setSuccess('Profile saved.');
    } catch (saveError) {
      setError(saveError instanceof Error ? saveError.message : 'Network error. Please try again.');
    } finally {
      setIsSaving(false);
    }
  };

  return (
    <div className="mx-auto max-w-3xl space-y-6">
      {error && (
        <div role="alert" className="flex items-start gap-3 rounded-lg border border-rose-200 bg-rose-50 p-4 text-sm text-rose-800">
          <AlertCircle className="mt-0.5 h-5 w-5 shrink-0" />
          <p>{error}</p>
        </div>
      )}
      {success && (
        <div role="status" className="flex items-start gap-3 rounded-lg border border-emerald-200 bg-emerald-50 p-4 text-sm text-emerald-800">
          <CheckCircle2 className="mt-0.5 h-5 w-5 shrink-0" />
          <p>{success}</p>
        </div>
      )}

      <section className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm md:p-8">
        <div className="mb-6 flex items-start gap-4">
          <span className="rounded-full bg-indigo-100 p-3 text-indigo-600">
            <UserRound className="h-7 w-7" />
          </span>
          <div className="min-w-0">
            <h1 className="text-2xl font-bold text-slate-900">My profile</h1>
            <p className="mt-1 truncate text-sm text-slate-500">
              {isLoading ? 'Loading…' : user?.email}
              {user && ` · ${isStudent ? 'Student' : 'Administrator'}`}
            </p>
          </div>
        </div>

        {isLoading ? (
          <p className="text-sm text-slate-500" role="status">Loading your profile…</p>
        ) : user ? (
          <form onSubmit={handleSubmit} noValidate className="space-y-5">
            <Field id="profile-full-name" label="Full name" error={visibleErrors.fullName} hint="Shown to hostel staff on your complaints.">
              <input
                id="profile-full-name"
                autoComplete="name"
                maxLength={100}
                value={form.fullName}
                onChange={updateField('fullName')}
                aria-invalid={Boolean(visibleErrors.fullName)}
                aria-describedby="profile-full-name-message"
                className={inputClasses}
              />
            </Field>

            <Field
              id="profile-phone"
              label="Phone number"
              error={visibleErrors.phoneNumber}
              hint="Workers may call this number to coordinate a visit."
            >
              <input
                id="profile-phone"
                type="tel"
                autoComplete="tel"
                inputMode="tel"
                maxLength={20}
                placeholder="e.g. +91 98765 43210"
                value={form.phoneNumber}
                onChange={updateField('phoneNumber')}
                aria-invalid={Boolean(visibleErrors.phoneNumber)}
                aria-describedby="profile-phone-message"
                className={inputClasses}
              />
            </Field>

            {isStudent && (
              <div className="grid gap-5 sm:grid-cols-2">
                <Field id="profile-roll" label="Roll number" error={visibleErrors.rollNumber} hint="e.g. CS23B051">
                  <input
                    id="profile-roll"
                    maxLength={20}
                    value={form.rollNumber}
                    onChange={updateField('rollNumber')}
                    aria-invalid={Boolean(visibleErrors.rollNumber)}
                    aria-describedby="profile-roll-message"
                    className={`${inputClasses} uppercase`}
                  />
                </Field>
                <Field
                  id="profile-room"
                  label="Room number"
                  error={visibleErrors.roomNumber}
                  hint={roomFloor ? `${roomFloor} floor. Used for complaints about your room.` : 'Used for complaints about your room.'}
                >
                  <input
                    id="profile-room"
                    inputMode="numeric"
                    maxLength={3}
                    placeholder="e.g. 312"
                    value={form.roomNumber}
                    onChange={updateField('roomNumber')}
                    aria-invalid={Boolean(visibleErrors.roomNumber)}
                    aria-describedby="profile-room-message"
                    className={inputClasses}
                  />
                </Field>
              </div>
            )}

            <button
              type="submit"
              disabled={isSaving}
              className="inline-flex w-full items-center justify-center gap-2 rounded-xl bg-blue-600 px-5 py-3 font-semibold text-white shadow-sm transition hover:bg-blue-700 disabled:cursor-not-allowed disabled:bg-blue-400"
            >
              {isSaving && <RefreshCw className="h-4 w-4 animate-spin" />}
              {isSaving ? 'Saving…' : 'Save profile'}
            </button>
          </form>
        ) : (
          <p className="text-sm text-slate-500">Your profile could not be loaded.</p>
        )}
      </section>
    </div>
  );
}
