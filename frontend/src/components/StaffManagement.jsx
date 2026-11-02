import { useEffect, useState } from 'react';
import { AlertCircle, RefreshCw, Trash2, UserPlus, Users } from 'lucide-react';
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

/** Admin-only: add hostel staff by email, list them, remove them. */
export default function StaffManagement() {
  const [staff, setStaff] = useState([]);
  const [isLoading, setIsLoading] = useState(true);
  const [email, setEmail] = useState('');
  const [isAdding, setIsAdding] = useState(false);
  const [confirmingId, setConfirmingId] = useState(null);
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

  const handleRemove = async (member) => {
    setError('');
    setNotice('');
    setRemovingId(member.id);
    try {
      const response = await apiFetch(`${apiBaseUrl}/admin/staff/${member.id}`, { method: 'DELETE' });
      if (!response.ok) throw new Error(await errorMessage(response, 'Could not remove this staff member.'));
      setConfirmingId(null);
      setNotice(`${member.email} was removed.`);
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
          Hostel staff see all complaints and can assign themselves. Only admins can manage staff.
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
            {staff.map((member) => (
              <li key={member.id} className="flex flex-col gap-3 p-4 sm:flex-row sm:items-center sm:justify-between">
                <div className="min-w-0">
                  <p className="m-0 truncate font-medium text-slate-900">{member.name || member.email}</p>
                  <p className="m-0 truncate text-xs text-slate-500">
                    {member.email}
                    {member.phoneNumber && ` · ${member.phoneNumber}`}
                    {` · ${member.activeWorkOrders} active ${member.activeWorkOrders === 1 ? 'complaint' : 'complaints'}`}
                  </p>
                </div>
                {confirmingId === member.id ? (
                  <div className="flex flex-wrap items-center gap-2 text-sm">
                    <span className="text-slate-600">
                      {member.activeWorkOrders > 0
                        ? `Their ${member.activeWorkOrders} active complaint(s) will go back to Pending. Remove?`
                        : 'Remove this staff member?'}
                    </span>
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
                      className="rounded-lg border border-slate-300 px-3 py-1.5 text-slate-700 hover:bg-slate-50"
                    >
                      Cancel
                    </button>
                  </div>
                ) : (
                  <button
                    type="button"
                    onClick={() => setConfirmingId(member.id)}
                    className="inline-flex items-center gap-1.5 self-start rounded-lg px-3 py-1.5 text-sm font-medium text-rose-700 hover:bg-rose-50 sm:self-auto"
                  >
                    <Trash2 className="h-4 w-4" /> Remove
                  </button>
                )}
              </li>
            ))}
          </ul>
        )}
      </div>
    </section>
  );
}
