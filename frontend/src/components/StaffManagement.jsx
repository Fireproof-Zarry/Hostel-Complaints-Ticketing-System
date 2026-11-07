import { useEffect, useState } from 'react';
import { AlertCircle, ChevronDown, ChevronRight, RefreshCw, Trash2, UserPlus, Users } from 'lucide-react';
import { apiFetch } from '../auth/session';

const apiBaseUrl = (import.meta.env.VITE_API_URL || '/api').replace(/\/$/, '');

async function errorMessage(response, fallback) {
  try {
    const body = await response.json();
    return body.message || body.detail || fallback;
  } catch {
    return fallback;
  }
}

function label(value) {
  return value ? value.toLowerCase().replaceAll('_', ' ').replace(/\b\w/g, (c) => c.toUpperCase()) : '—';
}

function formatDeadline(value) {
  if (!value) return 'No deadline';
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? '—' : date.toLocaleString();
}

/** A small coloured label on a staff row. */
function Badge({ tone, children }) {
  const tones = {
    slate: 'bg-slate-100 text-slate-700',
    amber: 'bg-amber-100 text-amber-800',
    indigo: 'bg-indigo-100 text-indigo-800',
  };
  return <span className={`rounded-full px-2 py-0.5 text-xs font-semibold ${tones[tone]}`}>{children}</span>;
}

/** A person's active work orders, loaded when their row is expanded. */
function ActiveWorkOrders({ memberId }) {
  const [workOrders, setWorkOrders] = useState(null);
  const [error, setError] = useState('');

  useEffect(() => {
    const controller = new AbortController();
    apiFetch(`${apiBaseUrl}/admin/staff/${memberId}/work-orders`, { signal: controller.signal })
      .then(async (response) => {
        if (!response.ok) throw new Error(await errorMessage(response, 'Could not load their complaints.'));
        setWorkOrders(await response.json());
      })
      .catch((loadError) => {
        if (loadError.name !== 'AbortError') setError(loadError.message);
      });
    return () => controller.abort();
  }, [memberId]);

  if (error) return <p role="alert" className="m-0 text-sm text-rose-700">{error}</p>;
  if (workOrders === null) return <p className="m-0 text-sm text-slate-500">Loading…</p>;
  if (workOrders.length === 0) return <p className="m-0 text-sm text-slate-500">No open complaints.</p>;

  return (
    <ul className="m-0 list-none space-y-2 p-0">
      {workOrders.map((workOrder) => (
        <li
          key={workOrder.id}
          className={`rounded-lg border p-3 text-sm ${workOrder.overdue ? 'border-rose-200 bg-rose-50' : 'border-slate-200 bg-white'}`}
        >
          <p className="m-0 font-medium text-slate-900">
            #{workOrder.complaintId} {workOrder.complaintTitle}
          </p>
          <p className="m-0 mt-1 text-xs text-slate-600">
            {label(workOrder.status)} · {label(workOrder.priority)} priority ·{' '}
            <span className={workOrder.overdue ? 'font-semibold text-rose-700' : ''}>
              {workOrder.overdue ? 'Overdue since ' : 'Due '}
              {formatDeadline(workOrder.deadline)}
            </span>
          </p>
        </li>
      ))}
    </ul>
  );
}

/**
 * Admin-only: who is staff, what each person (staff or admin) is holding right
 * now, and adding or removing staff by email.
 */
export default function StaffManagement() {
  const [staff, setStaff] = useState([]);
  const [isLoading, setIsLoading] = useState(true);
  const [email, setEmail] = useState('');
  const [isAdding, setIsAdding] = useState(false);
  const [expandedId, setExpandedId] = useState(null);
  const [confirmingId, setConfirmingId] = useState(null);
  // '' returns their complaints to Pending; otherwise the user id to hand them to
  const [reassignTo, setReassignTo] = useState('');
  const [removingId, setRemovingId] = useState(null);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  // Bumped after an add or remove to reload the list
  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    apiFetch(`${apiBaseUrl}/admin/staff`, { signal: controller.signal })
      .then(async (response) => {
        if (!response.ok) throw new Error(await errorMessage(response, 'Could not load staff.'));
        setStaff(await response.json());
      })
      .catch((loadError) => {
        if (loadError.name !== 'AbortError') setError(loadError.message);
      })
      .finally(() => setIsLoading(false));
    return () => controller.abort();
  }, [reloadKey]);

  const handleAdd = async (event) => {
    event.preventDefault();
    setError('');
    setNotice('');
    setIsAdding(true);
    try {
      const response = await apiFetch(`${apiBaseUrl}/admin/staff`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email }),
      });
      if (!response.ok) throw new Error(await errorMessage(response, 'Could not add this staff member.'));
      const added = await response.json();
      setEmail('');
      setNotice(`${added.email} is now hostel staff. They can sign in with that Google account.`);
      setReloadKey((key) => key + 1);
    } catch (addError) {
      setError(addError.message);
    } finally {
      setIsAdding(false);
    }
  };

  const startRemoving = (member) => {
    setConfirmingId(member.id);
    setReassignTo('');
  };

  const handleRemove = async (member) => {
    setError('');
    setNotice('');
    setRemovingId(member.id);
    try {
      const query = reassignTo ? `?reassignTo=${encodeURIComponent(reassignTo)}` : '';
      const response = await apiFetch(`${apiBaseUrl}/admin/staff/${member.id}${query}`, { method: 'DELETE' });
      if (!response.ok) throw new Error(await errorMessage(response, 'Could not remove this staff member.'));
      const target = staff.find((other) => other.id === reassignTo);
      setConfirmingId(null);
      setExpandedId(null);
      setNotice(
        target && member.activeWorkOrders > 0
          ? `${member.email} was removed. Their complaints now belong to ${target.name || target.email}.`
          : `${member.email} was removed.`,
      );
      setReloadKey((key) => key + 1);
    } catch (removeError) {
      setError(removeError.message);
    } finally {
      setRemovingId(null);
    }
  };

  return (
    <section className="mx-auto max-w-3xl space-y-5">
      <div>
        <h2 className="m-0 text-2xl font-bold text-slate-900">Staff management</h2>
        <p className="m-0 mt-1 text-sm text-slate-500">
          Hostel staff see all complaints and can assign themselves. Admins are listed too, since they can also be assigned.
        </p>
      </div>

      <form onSubmit={handleAdd} className="flex flex-col gap-3 rounded-xl border border-slate-200 bg-white p-4 sm:flex-row">
        <label className="sr-only" htmlFor="staff-email">Staff email</label>
        <input
          id="staff-email"
          type="email"
          required
          value={email}
          onChange={(event) => setEmail(event.target.value)}
          placeholder="staff.member@gmail.com"
          className="min-w-0 flex-1 rounded-lg border border-slate-300 px-4 py-2.5 text-sm outline-none focus:border-indigo-500 focus:ring-2 focus:ring-indigo-200"
        />
        <button
          type="submit"
          disabled={isAdding}
          className="inline-flex items-center justify-center gap-2 rounded-lg bg-indigo-600 px-4 py-2.5 text-sm font-semibold text-white hover:bg-indigo-700 disabled:opacity-60"
        >
          {isAdding ? <RefreshCw className="h-4 w-4 animate-spin" /> : <UserPlus className="h-4 w-4" />}
          Add staff
        </button>
      </form>

      {error && (
        <p role="alert" className="m-0 flex items-start gap-2 rounded-lg border border-rose-200 bg-rose-50 p-3 text-sm text-rose-800">
          <AlertCircle className="mt-0.5 h-4 w-4 shrink-0" /> {error}
        </p>
      )}
      {notice && (
        <p role="status" className="m-0 rounded-lg border border-emerald-200 bg-emerald-50 p-3 text-sm text-emerald-800">{notice}</p>
      )}

      <div className="overflow-hidden rounded-xl border border-slate-200 bg-white">
        {isLoading ? (
          <p className="m-0 p-5 text-sm text-slate-500">Loading staff…</p>
        ) : staff.length === 0 ? (
          <div className="p-8 text-center">
            <Users className="mx-auto h-8 w-8 text-slate-300" />
            <p className="m-0 mt-2 text-sm text-slate-600">No hostel staff yet. Add someone by email above.</p>
          </div>
        ) : (
          <ul className="m-0 list-none divide-y divide-slate-100 p-0">
            {staff.map((member) => {
              const isExpanded = expandedId === member.id;
              const others = staff.filter((other) => other.id !== member.id);
              return (
                <li key={member.id} className="p-4">
                  <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
                    <button
                      type="button"
                      onClick={() => setExpandedId(isExpanded ? null : member.id)}
                      aria-expanded={isExpanded}
                      className="flex min-w-0 items-start gap-2 border-0 bg-transparent p-0 text-left"
                    >
                      {isExpanded
                        ? <ChevronDown className="mt-0.5 h-4 w-4 shrink-0 text-slate-400" />
                        : <ChevronRight className="mt-0.5 h-4 w-4 shrink-0 text-slate-400" />}
                      <span className="min-w-0">
                        <span className="flex flex-wrap items-center gap-2">
                          <span className="truncate font-medium text-slate-900">{member.name || member.email}</span>
                          {member.role === 'ADMIN' && <Badge tone="indigo">Admin</Badge>}
                          {member.invitePending && <Badge tone="amber">Hasn’t signed in yet</Badge>}
                        </span>
                        <span className="block truncate text-xs text-slate-500">
                          {member.email}
                          {member.phoneNumber && ` · ${member.phoneNumber}`}
                        </span>
                        <span className="mt-1 block text-xs text-slate-600">
                          {member.activeWorkOrders} active
                          {member.overdueWorkOrders > 0 && (
                            <span className="font-semibold text-rose-700"> · {member.overdueWorkOrders} overdue</span>
                          )}
                        </span>
                      </span>
                    </button>

                    {member.role === 'STAFF' && confirmingId !== member.id && (
                      <button
                        type="button"
                        onClick={() => startRemoving(member)}
                        className="inline-flex items-center gap-1.5 self-start rounded-lg px-3 py-1.5 text-sm font-medium text-rose-700 hover:bg-rose-50 sm:self-auto"
                      >
                        <Trash2 className="h-4 w-4" /> Remove
                      </button>
                    )}
                  </div>

                  {confirmingId === member.id && (
                    <div className="mt-3 space-y-3 rounded-lg border border-rose-200 bg-rose-50 p-3 text-sm">
                      {member.activeWorkOrders > 0 ? (
                        <label className="block space-y-1">
                          <span className="block text-slate-700">
                            They have {member.activeWorkOrders} active {member.activeWorkOrders === 1 ? 'complaint' : 'complaints'}. Move {member.activeWorkOrders === 1 ? 'it' : 'them'} to:
                          </span>
                          <select
                            value={reassignTo}
                            onChange={(event) => setReassignTo(event.target.value)}
                            disabled={removingId === member.id}
                            className="w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm"
                          >
                            <option value="">Nobody: return to Pending</option>
                            {others.map((other) => (
                              <option key={other.id} value={other.id}>
                                {other.name || other.email} ({other.activeWorkOrders} active)
                              </option>
                            ))}
                          </select>
                        </label>
                      ) : (
                        <p className="m-0 text-slate-700">Remove this staff member?</p>
                      )}
                      <div className="flex flex-wrap gap-2">
                        <button
                          type="button"
                          onClick={() => handleRemove(member)}
                          disabled={removingId === member.id}
                          className="rounded-lg bg-rose-600 px-3 py-1.5 font-semibold text-white hover:bg-rose-700 disabled:opacity-60"
                        >
                          {removingId === member.id ? 'Removing…' : 'Remove'}
                        </button>
                        <button
                          type="button"
                          onClick={() => setConfirmingId(null)}
                          disabled={removingId === member.id}
                          className="rounded-lg border border-slate-300 bg-white px-3 py-1.5 text-slate-700 hover:bg-slate-50"
                        >
                          Cancel
                        </button>
                      </div>
                    </div>
                  )}

                  {isExpanded && (
                    <div className="mt-3 pl-6">
                      <ActiveWorkOrders key={reloadKey} memberId={member.id} />
                    </div>
                  )}
                </li>
              );
            })}
          </ul>
        )}
      </div>
    </section>
  );
}
