import { useCallback, useEffect, useMemo, useState } from 'react';
import { Navigate, useNavigate } from 'react-router-dom';
import {
  AlertCircle,
  Bell,
  CalendarDays,
  CheckCircle2,
  ClipboardList,
  FilePlus2,
  Home,
  LogOut,
  RefreshCw,
  Wrench,
} from 'lucide-react';

const apiBaseUrl = (import.meta.env.VITE_API_URL || '/api').replace(/\/$/, '');

const initialForm = {
  title: '',
  description: '',
  category: 'Electrical',
  floor: 'Second',
  room: '',
};

function getExpectedRoomHundreds(floor) {
  const normalizedFloor = floor?.trim().toLowerCase().replace(/\s+floor$/, '');
  return {
    ground: 1,
    first: 2,
    '1st': 2,
    second: 3,
    '2nd': 3,
    third: 4,
    '3rd': 4,
    fourth: 5,
    '4th': 5,
    fifth: 6,
    '5th': 6,
    sixth: 7,
    '6th': 7,
  }[normalizedFloor];
}

const navigation = [
  { id: 'home', label: 'Home', icon: Home },
  { id: 'submit', label: 'File Complaint', icon: FilePlus2 },
  { id: 'complaints', label: 'My Complaints', icon: ClipboardList },
];

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
      <div className="mt-4 flex flex-wrap gap-x-5 gap-y-2 text-xs font-medium text-slate-500">
        <span className="inline-flex items-center gap-1.5">
          <CalendarDays className="h-4 w-4" />
          Filed {formatDate(complaint.createdAt)}
        </span>
        <span>{complaint.floor || 'Floor unavailable'} · Room {complaint.room || 'N/A'}</span>
      </div>
    </article>
  );
}

export default function StudentHome() {
  const [complaints, setComplaints] = useState([]);
  const [activeTab, setActiveTab] = useState('home');
  const [formData, setFormData] = useState(initialForm);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [isLoading, setIsLoading] = useState(true);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [search, setSearch] = useState('');
  const [statusFilter, setStatusFilter] = useState('ALL');

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
      ].some((value) => value?.toLowerCase().includes(query));
      const matchesStatus = statusFilter === 'ALL' || complaint.status?.toUpperCase() === statusFilter;
      return matchesSearch && matchesStatus;
    });
  }, [complaints, search, statusFilter]);
  const expectedRoomHundreds = getExpectedRoomHundreds(formData.floor);
  const roomLocationError = formData.room
    && (!/^\d{3}$/.test(formData.room) || Number(formData.room[0]) !== expectedRoomHundreds)
    ? `Room numbers for the ${formData.floor.toLowerCase()} floor must be in the ${expectedRoomHundreds}00 range (for example, ${expectedRoomHundreds}01).`
    : '';

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
    if (roomLocationError) {
      setError(roomLocationError);
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
        body: JSON.stringify(formData),
      });

      if (response.status === 401) {
        handleLogout();
        return;
      }
      if (response.status === 429) {
        setError('You have reached the limit of 5 open complaints. Please wait for some to be resolved.');
        return;
      }
      if (!response.ok) {
        throw new Error('Failed to submit the complaint. Please check your details and try again.');
      }

      setFormData(initialForm);
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
      <aside className="flex w-full flex-col bg-slate-900 text-slate-300 md:min-h-screen md:w-64 md:shrink-0">
        <div className="flex items-center gap-3 p-5 md:p-6">
          <div className="rounded-lg bg-indigo-500 p-2 text-white">
            <Wrench className="h-6 w-6" />
          </div>
          <div>
            <h1 className="font-bold leading-tight text-white">HostelFix</h1>
            <p className="text-xs text-slate-400">Student Portal</p>
          </div>
        </div>

        <nav aria-label="Student dashboard" className="flex gap-2 overflow-x-auto px-4 pb-4 md:flex-1 md:flex-col md:gap-1 md:pt-5">
          {navigation.map(({ id, label, icon: Icon }) => (
            <button
              key={id}
              type="button"
              aria-current={activeTab === id ? 'page' : undefined}
              onClick={() => {
                setActiveTab(id);
                setError('');
                setSuccess('');
              }}
              className={`flex shrink-0 items-center gap-3 rounded-lg px-3 py-2.5 text-sm font-medium transition-colors md:w-full ${
                activeTab === id ? 'bg-indigo-600 text-white' : 'hover:bg-slate-800 hover:text-white'
              }`}
            >
              <Icon className="h-5 w-5" />
              {label}
            </button>
          ))}
        </nav>

        <div className="hidden border-t border-slate-800 p-4 text-xs leading-5 text-slate-400 md:block">
          Your submitted complaints and their current status.
        </div>
      </aside>

      <main className="min-w-0 flex-1">
        <header className="flex min-h-16 items-center justify-between gap-4 border-b border-slate-200 bg-white px-4 shadow-sm md:px-8">
          <h2 className="text-lg font-semibold text-slate-800">
            {navigation.find((item) => item.id === activeTab)?.label}
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
                <div className="grid gap-5 sm:grid-cols-2">
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
                  <div>
                    <label htmlFor="complaint-floor" className="mb-1.5 block text-sm font-medium text-slate-700">Floor</label>
                    <select
                      id="complaint-floor"
                      value={formData.floor}
                      onChange={updateForm('floor')}
                      className="w-full rounded-lg border border-slate-300 bg-white px-4 py-2.5 text-sm outline-none transition focus:border-blue-500 focus:ring-2 focus:ring-blue-200"
                    >
                      {['Ground', 'First', 'Second', 'Third', 'Fourth', 'Fifth', 'Sixth'].map((floor) => (
                        <option key={floor}>{floor}</option>
                      ))}
                    </select>
                  </div>
                </div>
                <div>
                  <label htmlFor="complaint-room" className="mb-1.5 block text-sm font-medium text-slate-700">Room number</label>
                  <input
                    id="complaint-room"
                    required
                    type="text"
                    inputMode="numeric"
                    pattern={`${expectedRoomHundreds}[0-9]{2}`}
                    maxLength={3}
                    value={formData.room}
                    onChange={updateForm('room')}
                    placeholder={`e.g. ${expectedRoomHundreds}01`}
                    aria-invalid={Boolean(roomLocationError)}
                    aria-describedby={roomLocationError ? 'complaint-room-location-error' : 'complaint-room-location-hint'}
                    className="w-full rounded-lg border border-slate-300 px-4 py-2.5 text-sm outline-none transition focus:border-blue-500 focus:ring-2 focus:ring-blue-200"
                  />
                  <p
                    id={roomLocationError ? 'complaint-room-location-error' : 'complaint-room-location-hint'}
                    className={`mt-1.5 text-sm ${roomLocationError ? 'text-rose-700' : 'text-slate-500'}`}
                    role={roomLocationError ? 'alert' : undefined}
                  >
                    {roomLocationError || `Enter a 3-digit room number in the ${expectedRoomHundreds}00 range.`}
                  </p>
                </div>
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
                <button
                  type="submit"
                  disabled={isSubmitting}
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
