import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Navigate, useLocation, useNavigate } from 'react-router-dom';
import {
  AlertCircle,
  Bell,
  CalendarDays,
  CheckCircle2,
  ClipboardList,
  FilePlus2,
  LogOut,
  RefreshCw,
  KeyRound,
} from 'lucide-react';
import ImageUpload from '../components/ImageUpload';
import StudentSidebar from '../components/StudentSidebar';
import { STUDENT_NAVIGATION } from '../components/studentNavigation';
import { getFloorForRoom, HOSTEL_FLOORS } from '../utils/hostelLocation';

const apiBaseUrl = (import.meta.env.VITE_API_URL || '/api').replace(/\/$/, '');

const initialForm = {
  title: '',
  description: '',
  category: 'Electrical',
  isCommunityArea: false,
  // Floor and wing are only used for community-area issues; personal issues
  // use the room number from the student's profile
  floor: 'Second',
  wing: '',
  availableTimeSlot: '',
  keyWithSecurity: false,
};

function statusClasses(status) {
  switch (status?.toUpperCase()) {
    case 'PENDING':
      return 'bg-amber-100 text-amber-800';
    case 'ASSIGNED':
      return 'bg-violet-100 text-violet-800';
    case 'IN_PROGRESS':
      return 'bg-blue-100 text-blue-800';
    case 'RESOLVED':
      return 'bg-emerald-100 text-emerald-800';
    case 'REJECTED':
      return 'bg-rose-100 text-rose-800';
    default:
      return 'bg-slate-100 text-slate-700';
  }
}

function formatStatus(status) {
  return status?.toLowerCase().replaceAll('_', ' ').replace(/\b\w/g, (letter) => letter.toUpperCase()) || 'Unknown';
}

function formatDate(value) {
  if (!value) return 'Date unavailable';
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? 'Date unavailable' : date.toLocaleDateString();
}

function activeComplaint(complaint) {
  return !['RESOLVED', 'REJECTED'].includes(complaint.status?.toUpperCase());
}

function ComplaintCard({ complaint }) {
  return (
    <article className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <p className="text-xs font-semibold uppercase tracking-wide text-slate-500">
            {complaint.category || 'Uncategorized'} · #{complaint.id}
          </p>
          <h3 className="mt-1 text-lg font-semibold text-slate-900">{complaint.title}</h3>
        </div>
        <span className={`rounded-full px-3 py-1 text-xs font-semibold ${statusClasses(complaint.status)}`}>
          {formatStatus(complaint.status)}
        </span>
      </div>
      <p className="mt-3 whitespace-pre-wrap text-sm leading-6 text-slate-600">{complaint.description}</p>
      {complaint.imageUrl && (
        <a href={complaint.imageUrl} target="_blank" rel="noreferrer" className="mt-4 inline-block">
          <img
            src={complaint.imageUrl}
            alt={`Photo for complaint #${complaint.id}`}
            loading="lazy"
            className="h-32 w-auto max-w-full rounded-lg border border-slate-200 object-cover"
          />
        </a>
      )}
      <div className="mt-4 flex flex-wrap gap-x-5 gap-y-2 text-xs font-medium text-slate-500">
        <span className="inline-flex items-center gap-1.5">
          <CalendarDays className="h-4 w-4" />
          Filed {formatDate(complaint.createdAt)}
        </span>
        <span>
          {complaint.floor || 'Floor unavailable'} ·{' '}
          {complaint.communityArea ? complaint.wing || 'Community area' : `Room ${complaint.room || 'N/A'}`}
        </span>
        {complaint.availableTimeSlot && <span>Available: {complaint.availableTimeSlot}</span>}
        {complaint.keyWithSecurity && (
          <span className="inline-flex items-center gap-1.5">
            <KeyRound className="h-4 w-4" />
            Key with security
          </span>
        )}
      </div>
    </article>
  );
}

export default function StudentHome() {
  const [complaints, setComplaints] = useState([]);
  const location = useLocation();
  // The Profile page's sidebar links back here with the tab to open
  const [activeTab, setActiveTab] = useState(location.state?.tab ?? 'home');
  const [formData, setFormData] = useState(initialForm);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [isLoading, setIsLoading] = useState(true);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [search, setSearch] = useState('');
  const [statusFilter, setStatusFilter] = useState('ALL');
  const imageUploadRef = useRef(null);
  const [profile, setProfile] = useState(null);

  const navigate = useNavigate();
  const token = localStorage.getItem('idToken');

  const handleLogout = useCallback(() => {
    localStorage.removeItem('idToken');
    navigate('/login', { replace: true });
  }, [navigate]);

  const requestMyComplaints = useCallback(async () => {
    const response = await fetch(`${apiBaseUrl}/complaints/mine`, {
      headers: { Authorization: `Bearer ${token}` },
    });

    if (response.status === 401) {
      throw new Error(
        'The backend rejected your Google sign-in token while loading complaints (401). You can stay on this page; try signing out and back in, or check the backend logs.'
      );
    }
    if (response.status === 403) {
      throw new Error('Your account is signed in but is not allowed to view these complaints (403).');
    }
    if (!response.ok) {
      throw new Error('Could not load your complaints. Please try again.');
    }

    const data = await response.json();
    if (!Array.isArray(data)) {
      throw new Error('The server returned an unexpected complaints response.');
    }
    return data;
  }, [token]);

  // Personal complaints use the room from the profile, so load it up front
  useEffect(() => {
    if (!token) return undefined;
    const controller = new AbortController();
    fetch(`${apiBaseUrl}/auth/me`, {
      headers: { Authorization: `Bearer ${token}` },
      signal: controller.signal,
    })
      .then((response) => (response.ok ? response.json() : null))
      .then((data) => {
        if (data) setProfile(data);
      })
      .catch(() => {});
    return () => controller.abort();
  }, [token]);

  useEffect(() => {
    if (!token) return;
    let isActive = true;
    requestMyComplaints()
      .then((data) => {
        if (isActive && data) {
          setError('');
          setComplaints(data);
        }
      })
      .catch((fetchError) => {
        if (isActive) {
          setError(fetchError instanceof Error ? fetchError.message : 'Network error while loading complaints.');
        }
      })
      .finally(() => {
        if (isActive) setIsLoading(false);
      });
    return () => {
      isActive = false;
    };
  }, [requestMyComplaints, token]);

  const activeCount = complaints.filter(activeComplaint).length;
  const filteredComplaints = useMemo(() => {
    const query = search.trim().toLowerCase();
    return complaints.filter((complaint) => {
      const matchesSearch = !query || [
        complaint.title,
        complaint.description,
        complaint.category,
        complaint.room,
        complaint.wing,
      ].some((value) => value?.toLowerCase().includes(query));
      const matchesStatus = statusFilter === 'ALL' || complaint.status?.toUpperCase() === statusFilter;
      return matchesSearch && matchesStatus;
    });
  }, [complaints, search, statusFilter]);
  const profileRoom = profile?.roomNumber ?? '';
  const profileRoomFloor = getFloorForRoom(profileRoom);
  const missingProfileRoom = !formData.isCommunityArea && profile !== null && !profileRoomFloor;

  if (!token) return <Navigate to="/login" replace />;

  const handleRefresh = async () => {
    setIsRefreshing(true);
    try {
      const data = await requestMyComplaints();
      if (data) {
        setError('');
        setComplaints(data);
      }
    } catch (refreshError) {
      setError(refreshError instanceof Error ? refreshError.message : 'Network error while loading complaints.');
    } finally {
      setIsRefreshing(false);
    }
  };

  const handleSubmit = async (event) => {
    event.preventDefault();
    setError('');
    setSuccess('');
    if (missingProfileRoom) {
      setError('Add your room number in My Profile before filing a complaint about your room.');
      return;
    }
    if (formData.isCommunityArea && !formData.wing.trim()) {
      setError('Enter the wing or area for a community-area complaint.');
      return;
    }
    setIsSubmitting(true);

    try {
      const response = await fetch(`${apiBaseUrl}/complaints`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${token}`,
        },
        body: JSON.stringify({
          title: formData.title,
          description: formData.description,
          category: formData.category,
          isCommunityArea: formData.isCommunityArea,
          // The backend takes the room from the profile for personal issues
          ...(formData.isCommunityArea && { floor: formData.floor, wing: formData.wing }),
          availableTimeSlot: formData.availableTimeSlot,
          keyWithSecurity: !formData.isCommunityArea && formData.keyWithSecurity,
        }),
      });

      if (response.status === 401) {
        handleLogout();
        return;
      }
      if (response.status === 429) {
        setError('You have reached the limit of 5 open complaints. Please wait for some to be resolved.');
        return;
      }
      if (response.status === 422) {
        throw new Error('Add a valid room number in My Profile before filing a complaint about your room.');
      }
      if (!response.ok) {
        throw new Error('Failed to submit the complaint. Please check your details and try again.');
      }

      const createdComplaint = await response.json();

      // The image endpoint needs the new complaint's id, so upload afterwards.
      // A failed upload keeps the complaint; resubmitting would create a duplicate.
      if (imageUploadRef.current?.hasFile()) {
        const upload = await imageUploadRef.current.upload(
          `${apiBaseUrl}/complaints/${createdComplaint.id}/image`,
          token
        );
        if (!upload.ok) {
          setError(`Your complaint was submitted, but the photo was not attached: ${upload.error}`);
        }
      }

      setFormData(initialForm);
      imageUploadRef.current?.reset();
      setSuccess('Complaint submitted successfully.');
      const updatedComplaints = await requestMyComplaints();
      if (updatedComplaints) setComplaints(updatedComplaints);
      setActiveTab('complaints');
    } catch (submitError) {
      setError(submitError instanceof Error ? submitError.message : 'Network error. Please try again later.');
    } finally {
      setIsSubmitting(false);
    }
  };

  const updateForm = (field) => (event) => {
    setFormData((current) => ({ ...current, [field]: event.target.value }));
  };

  return (
    <div className="min-h-screen bg-slate-100 text-slate-900 md:flex">
      <StudentSidebar
        activeId={activeTab}
        onSelect={(id) => {
          if (id === 'profile') {
            navigate('/profile');
            return;
          }
          setActiveTab(id);
          setError('');
          setSuccess('');
        }}
      />

      <main className="min-w-0 flex-1">
        <header className="flex min-h-16 items-center justify-between gap-4 border-b border-slate-200 bg-white px-4 shadow-sm md:px-8">
          <h2 className="text-lg font-semibold text-slate-800">
            {STUDENT_NAVIGATION.find((item) => item.id === activeTab)?.label}
          </h2>
          <button
            type="button"
            onClick={handleLogout}
            className="inline-flex items-center gap-2 rounded-lg border border-slate-200 px-3 py-2 text-sm font-medium text-slate-600 transition-colors hover:bg-slate-50 hover:text-slate-900"
          >
            <LogOut className="h-4 w-4" />
            <span className="hidden sm:inline">Log out</span>
          </button>
        </header>

        <div className="mx-auto max-w-6xl space-y-6 p-4 md:p-8">
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

          {activeTab === 'home' && (
            <>
              <section className="flex flex-col items-start justify-between gap-5 rounded-2xl bg-gradient-to-r from-blue-600 to-indigo-700 p-6 text-white shadow-lg md:flex-row md:items-center md:p-8">
                <div>
                  <p className="text-sm font-medium text-blue-100">Student dashboard</p>
                  <h3 className="mt-1 text-2xl font-bold md:text-3xl">How can we help?</h3>
                  <p className="mt-2 max-w-xl text-sm text-blue-100">
                    Report a hostel issue and keep track of its progress from here.
                  </p>
                </div>
                <button
                  type="button"
                  onClick={() => {
                    setActiveTab('submit');
                    setError('');
                    setSuccess('');
                  }}
                  className="inline-flex shrink-0 items-center gap-2 rounded-xl bg-white px-5 py-3 font-semibold text-blue-700 shadow-sm transition-colors hover:bg-blue-50"
                >
                  <FilePlus2 className="h-5 w-5" />
                  File a complaint
                </button>
              </section>

              <section aria-label="Complaint summary" className="grid gap-4 sm:grid-cols-2">
                <button
                  type="button"
                  onClick={() => {
                    setActiveTab('complaints');
                    setStatusFilter('ALL');
                    setSearch('');
                  }}
                  className="flex items-center gap-4 rounded-xl border border-slate-200 bg-white p-5 text-left shadow-sm transition hover:border-blue-300"
                >
                  <span className="rounded-full bg-orange-100 p-3 text-orange-600">
                    <AlertCircle className="h-7 w-7" />
                  </span>
                  <span>
                    <span className="block text-2xl font-bold">{isLoading ? '—' : activeCount}</span>
                    <span className="text-sm font-medium text-slate-500">Active complaints</span>
                  </span>
                </button>
                <button
                  type="button"
                  onClick={() => {
                    setActiveTab('complaints');
                    setStatusFilter('ALL');
                    setSearch('');
                  }}
                  className="flex items-center gap-4 rounded-xl border border-slate-200 bg-white p-5 text-left shadow-sm transition hover:border-blue-300"
                >
                  <span className="rounded-full bg-indigo-100 p-3 text-indigo-600">
                    <ClipboardList className="h-7 w-7" />
                  </span>
                  <span>
                    <span className="block text-2xl font-bold">{isLoading ? '—' : complaints.length}</span>
                    <span className="text-sm font-medium text-slate-500">Total complaints</span>
                  </span>
                </button>
              </section>

              <section className="overflow-hidden rounded-xl border border-slate-200 bg-white shadow-sm">
                <div className="flex items-center justify-between gap-3 border-b border-slate-200 bg-slate-50 px-5 py-4">
                  <h3 className="font-semibold text-slate-800">Recent complaints</h3>
                  <button
                    type="button"
                    onClick={handleRefresh}
                    disabled={isRefreshing}
                    aria-label="Refresh complaints"
                    className="rounded-lg p-2 text-slate-500 transition hover:bg-white hover:text-blue-700 disabled:opacity-50"
                  >
                    <RefreshCw className={`h-4 w-4 ${isRefreshing ? 'animate-spin' : ''}`} />
                  </button>
                </div>
                <div className="divide-y divide-slate-100">
                  {isLoading ? (
                    <p className="p-5 text-sm text-slate-500">Loading your complaints…</p>
                  ) : complaints.length === 0 ? (
                    <div className="p-6 text-center">
                      <p className="font-medium text-slate-700">No complaints filed yet.</p>
                      <p className="mt-1 text-sm text-slate-500">Use “File a complaint” to report an issue.</p>
                    </div>
                  ) : (
                    [...complaints]
                      .sort((a, b) => new Date(b.createdAt || 0) - new Date(a.createdAt || 0))
                      .slice(0, 3)
                      .map((complaint) => (
                        <button
                          key={complaint.id}
                          type="button"
                          onClick={() => setActiveTab('complaints')}
                          className="flex w-full items-center justify-between gap-4 p-4 text-left transition-colors hover:bg-slate-50"
                        >
                          <span className="min-w-0">
                            <span className="block truncate text-sm font-semibold text-slate-900">{complaint.title}</span>
                            <span className="mt-1 block text-xs text-slate-500">
                              {complaint.category} · Filed {formatDate(complaint.createdAt)}
                            </span>
                          </span>
                          <span className={`shrink-0 rounded-full px-3 py-1 text-xs font-semibold ${statusClasses(complaint.status)}`}>
                            {formatStatus(complaint.status)}
                          </span>
                        </button>
                      ))
                  )}
                </div>
              </section>
            </>
          )}

          {activeTab === 'submit' && (
            <section className="mx-auto max-w-3xl rounded-xl border border-slate-200 bg-white p-5 shadow-sm md:p-8">
              <div className="mb-6">
                <h3 className="text-2xl font-bold text-slate-900">File a new complaint</h3>
                <p className="mt-1 text-sm text-slate-500">Include the location and details so the issue can be routed correctly.</p>
              </div>
              <form onSubmit={handleSubmit} className="space-y-5">
                <div>
                  <label htmlFor="complaint-title" className="mb-1.5 block text-sm font-medium text-slate-700">Issue title</label>
                  <input
                    id="complaint-title"
                    required
                    maxLength={150}
                    value={formData.title}
                    onChange={updateForm('title')}
                    placeholder="e.g. Ceiling light not working"
                    className="w-full rounded-lg border border-slate-300 px-4 py-2.5 text-sm outline-none transition focus:border-blue-500 focus:ring-2 focus:ring-blue-200"
                  />
                </div>
                <div>
                  <label htmlFor="complaint-category" className="mb-1.5 block text-sm font-medium text-slate-700">Category</label>
                  <select
                    id="complaint-category"
                    value={formData.category}
                    onChange={updateForm('category')}
                    className="w-full rounded-lg border border-slate-300 bg-white px-4 py-2.5 text-sm outline-none transition focus:border-blue-500 focus:ring-2 focus:ring-blue-200"
                  >
                    <option>Electrical</option>
                    <option>Plumbing</option>
                    <option>Carpentry</option>
                    <option>Cleaning</option>
                    <option>IT</option>
                  </select>
                </div>

                <fieldset>
                  <legend className="mb-1.5 block text-sm font-medium text-slate-700">Where is the issue?</legend>
                  <div className="grid gap-3 sm:grid-cols-2">
                    {[
                      { value: false, title: 'My room', hint: 'Uses the room number from your profile' },
                      { value: true, title: 'Community area', hint: 'Corridor, washroom, common room…' },
                    ].map((option) => (
                      <label
                        key={option.title}
                        className={`flex cursor-pointer items-start gap-3 rounded-lg border p-3 text-sm transition ${
                          formData.isCommunityArea === option.value
                            ? 'border-blue-500 bg-blue-50 ring-2 ring-blue-200'
                            : 'border-slate-300 hover:border-blue-300'
                        }`}
                      >
                        <input
                          type="radio"
                          name="complaint-location-type"
                          checked={formData.isCommunityArea === option.value}
                          onChange={() => setFormData((current) => ({ ...current, isCommunityArea: option.value }))}
                          className="mt-0.5 accent-blue-600"
                        />
                        <span>
                          <span className="block font-medium text-slate-800">{option.title}</span>
                          <span className="text-slate-500">{option.hint}</span>
                        </span>
                      </label>
                    ))}
                  </div>
                </fieldset>

                {formData.isCommunityArea ? (
                  <div className="grid gap-5 sm:grid-cols-2">
                    <div>
                      <label htmlFor="complaint-floor" className="mb-1.5 block text-sm font-medium text-slate-700">Floor</label>
                      <select
                        id="complaint-floor"
                        value={formData.floor}
                        onChange={updateForm('floor')}
                        className="w-full rounded-lg border border-slate-300 bg-white px-4 py-2.5 text-sm outline-none transition focus:border-blue-500 focus:ring-2 focus:ring-blue-200"
                      >
                        {HOSTEL_FLOORS.map(({ name: floor }) => (
                          <option key={floor}>{floor}</option>
                        ))}
                      </select>
                    </div>
                    <div>
                      <label htmlFor="complaint-wing" className="mb-1.5 block text-sm font-medium text-slate-700">Wing / area</label>
                      <input
                        id="complaint-wing"
                        required
                        maxLength={100}
                        value={formData.wing}
                        onChange={updateForm('wing')}
                        placeholder="e.g. East wing washroom"
                        className="w-full rounded-lg border border-slate-300 px-4 py-2.5 text-sm outline-none transition focus:border-blue-500 focus:ring-2 focus:ring-blue-200"
                      />
                    </div>
                  </div>
                ) : missingProfileRoom ? (
                  <div role="alert" className="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-amber-200 bg-amber-50 p-4 text-sm text-amber-900">
                    <p>Add your room number to your profile to file a complaint about your room.</p>
                    <button
                      type="button"
                      onClick={() => navigate('/profile')}
                      className="rounded-lg bg-amber-600 px-3 py-1.5 font-semibold text-white hover:bg-amber-700"
                    >
                      Add room number
                    </button>
                  </div>
                ) : (
                  <p className="rounded-lg border border-slate-200 bg-slate-50 px-4 py-3 text-sm text-slate-600">
                    {profileRoomFloor
                      ? <>Room <strong className="text-slate-900">{profileRoom}</strong> ({profileRoomFloor} floor), from your profile.</>
                      : 'Loading your room from your profile…'}{' '}
                    <button type="button" onClick={() => navigate('/profile')} className="font-medium text-blue-700 hover:underline">
                      Change
                    </button>
                  </p>
                )}

                <div>
                  <label htmlFor="complaint-description" className="mb-1.5 block text-sm font-medium text-slate-700">Description</label>
                  <textarea
                    id="complaint-description"
                    required
                    maxLength={1000}
                    rows={5}
                    value={formData.description}
                    onChange={updateForm('description')}
                    placeholder="Describe what happened and when you first noticed it."
                    className="w-full resize-y rounded-lg border border-slate-300 px-4 py-2.5 text-sm outline-none transition focus:border-blue-500 focus:ring-2 focus:ring-blue-200"
                  />
                </div>
                <div>
                  <label htmlFor="complaint-available-time" className="mb-1.5 block text-sm font-medium text-slate-700">
                    Available time <span className="font-normal text-slate-500">(optional)</span>
                  </label>
                  <input
                    id="complaint-available-time"
                    maxLength={500}
                    value={formData.availableTimeSlot}
                    onChange={updateForm('availableTimeSlot')}
                    placeholder="e.g. Mon: 2 PM - 5 PM, Tue: 12 PM - 5 PM"
                    className="w-full rounded-lg border border-slate-300 px-4 py-2.5 text-sm outline-none transition focus:border-blue-500 focus:ring-2 focus:ring-blue-200"
                  />
                </div>
                {!formData.isCommunityArea && (
                  <label className="flex cursor-pointer items-start gap-3 rounded-lg border border-slate-200 p-3 text-sm text-slate-700">
                    <input
                      type="checkbox"
                      checked={formData.keyWithSecurity}
                      onChange={(event) => setFormData((current) => ({ ...current, keyWithSecurity: event.target.checked }))}
                      className="mt-0.5 h-4 w-4 accent-blue-600"
                    />
                    I am not available in my room during working hours and will leave my key with security
                  </label>
                )}
                <ImageUpload ref={imageUploadRef} disabled={isSubmitting} />
                <button
                  type="submit"
                  disabled={isSubmitting || missingProfileRoom}
                  className="inline-flex w-full items-center justify-center gap-2 rounded-xl bg-blue-600 px-5 py-3 font-semibold text-white shadow-sm transition hover:bg-blue-700 disabled:cursor-not-allowed disabled:bg-blue-400"
                >
                  {isSubmitting && <RefreshCw className="h-4 w-4 animate-spin" />}
                  {isSubmitting ? 'Submitting…' : 'Submit complaint'}
                </button>
              </form>
            </section>
          )}

          {activeTab === 'complaints' && (
            <section className="space-y-5">
              <div className="flex flex-col justify-between gap-4 sm:flex-row sm:items-end">
                <div>
                  <h3 className="text-2xl font-bold text-slate-900">My complaints</h3>
                  <p className="mt-1 text-sm text-slate-500">
                    {isLoading ? 'Loading…' : `${filteredComplaints.length} of ${complaints.length} complaints`}
                  </p>
                </div>
                <button
                  type="button"
                  onClick={handleRefresh}
                  disabled={isRefreshing}
                  className="inline-flex items-center justify-center gap-2 rounded-lg border border-slate-300 bg-white px-4 py-2.5 text-sm font-medium text-slate-700 transition hover:bg-slate-50 disabled:opacity-50"
                >
                  <RefreshCw className={`h-4 w-4 ${isRefreshing ? 'animate-spin' : ''}`} />
                  Refresh
                </button>
              </div>

              <div className="grid gap-3 sm:grid-cols-[1fr_12rem]">
                <label className="sr-only" htmlFor="complaint-search">Search complaints</label>
                <input
                  id="complaint-search"
                  type="search"
                  value={search}
                  onChange={(event) => setSearch(event.target.value)}
                  placeholder="Search title, description, category or room"
                  className="w-full rounded-lg border border-slate-300 bg-white px-4 py-2.5 text-sm outline-none transition focus:border-blue-500 focus:ring-2 focus:ring-blue-200"
                />
                <label className="sr-only" htmlFor="complaint-status-filter">Filter by status</label>
                <select
                  id="complaint-status-filter"
                  value={statusFilter}
                  onChange={(event) => setStatusFilter(event.target.value)}
                  className="w-full rounded-lg border border-slate-300 bg-white px-4 py-2.5 text-sm outline-none transition focus:border-blue-500 focus:ring-2 focus:ring-blue-200"
                >
                  <option value="ALL">All statuses</option>
                  <option value="PENDING">Pending</option>
                  <option value="ASSIGNED">Assigned</option>
                  <option value="IN_PROGRESS">In progress</option>
                  <option value="RESOLVED">Resolved</option>
                  <option value="REJECTED">Rejected</option>
                </select>
              </div>

              {isLoading ? (
                <p className="rounded-xl border border-slate-200 bg-white p-6 text-sm text-slate-500">Loading your complaints…</p>
              ) : filteredComplaints.length === 0 ? (
                <div className="rounded-xl border border-slate-200 bg-white p-8 text-center">
                  <p className="font-medium text-slate-800">
                    {complaints.length === 0 ? 'No complaints filed yet.' : 'No complaints match your filters.'}
                  </p>
                  {complaints.length === 0 && (
                    <button
                      type="button"
                      onClick={() => setActiveTab('submit')}
                      className="mt-4 rounded-lg bg-blue-600 px-4 py-2 text-sm font-semibold text-white hover:bg-blue-700"
                    >
                      File your first complaint
                    </button>
                  )}
                </div>
              ) : (
                <div className="space-y-4">
                  {filteredComplaints.map((complaint) => (
                    <ComplaintCard key={complaint.id} complaint={complaint} />
                  ))}
                </div>
              )}
            </section>
          )}

          <section className="rounded-xl border border-slate-200 bg-white p-4 text-sm text-slate-600">
            <div className="flex items-start gap-3">
              <Bell className="mt-0.5 h-5 w-5 shrink-0 text-slate-400" />
              <p>
                Community upvotes, manager nudges, and notifications are work in progress and will be available soon.
              </p>
            </div>
          </section>
        </div>
      </main>
    </div>
  );
}
