import { useEffect, useState } from 'react';
import { RefreshCw } from 'lucide-react';
import { apiFetch } from '../auth/session';

const apiBaseUrl = (import.meta.env.VITE_API_URL || '/api').replace(/\/$/, '');
const ACTIVE = ['OPEN', 'IN_PROGRESS'];

function label(value) {
  return value ? value.toLowerCase().replaceAll('_', ' ').replace(/\b\w/g, (c) => c.toUpperCase()) : '—';
}

function formatDateTime(value) {
  if (!value) return 'No deadline';
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? '—' : date.toLocaleString();
}

async function errorMessage(response, fallback) {
  try {
    const body = await response.json();
    return body.message || body.detail || fallback;
  } catch {
    return fallback;
  }
}

/**
 * The complaint's work order in the admin popup. The assignee or an admin can
 * start it, mark it done (resolves the complaint) or cancel it (back to Pending).
 */
export default function WorkOrderPanel({ complaintId, currentUser, onComplaintChanged }) {
  const [workOrders, setWorkOrders] = useState(null);
  const [notes, setNotes] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  // Bumped after an update to reload the work order
  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    apiFetch(`${apiBaseUrl}/work-orders/complaint/${complaintId}`, { signal: controller.signal })
      .then(async (response) => {
        if (!response.ok) throw new Error(await errorMessage(response, 'Could not load the work order.'));
        setWorkOrders(await response.json());
      })
      .catch((loadError) => {
        if (loadError.name === 'AbortError') return;
        setError(loadError.message);
        setWorkOrders([]);
      });
    return () => controller.abort();
  }, [complaintId, reloadKey]);

  if (workOrders === null) {
    return <p className="m-0 text-sm text-slate-500">Loading work order…</p>;
  }

  const active = workOrders.find((workOrder) => ACTIVE.includes(workOrder.status));
  const shown = active ?? workOrders[0];
  if (!shown) {
    return <p className="m-0 text-sm text-slate-500">Not assigned yet. Use “Assign” to take responsibility for it.</p>;
  }

  const canManage = Boolean(active) && (currentUser?.role === 'ADMIN' || currentUser?.id === active.assigneeId);

  const update = async (body) => {
    setBusy(true);
    setError('');
    try {
      const response = await apiFetch(`${apiBaseUrl}/work-orders/${active.id}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body),
      });
      if (!response.ok) throw new Error(await errorMessage(response, 'Could not update the work order.'));
      const updated = await response.json();
      setNotes('');
      setReloadKey((key) => key + 1);
      onComplaintChanged?.(updated);
    } catch (updateError) {
      setError(updateError.message);
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="space-y-3 rounded-lg border border-slate-200 bg-slate-50 p-3 text-sm text-slate-700">
      <div className="grid gap-x-6 gap-y-1 sm:grid-cols-2">
        <span><strong>Assignee:</strong> {shown.assigneeName} ({shown.assigneeEmail})</span>
        <span><strong>Work order:</strong> {label(shown.status)}</span>
        <span><strong>Priority:</strong> {label(shown.priority)}</span>
        <span><strong>Deadline:</strong> {formatDateTime(shown.deadline)}</span>
      </div>
      {shown.completionNotes && <p className="m-0 whitespace-pre-wrap"><strong>Notes:</strong> {shown.completionNotes}</p>}

      {canManage && (
        <div className="space-y-2 border-t border-slate-200 pt-3">
          <label className="block text-xs font-semibold text-slate-600" htmlFor={`work-order-notes-${complaintId}`}>
            Completion notes (optional, saved when marking done)
          </label>
          <textarea
            id={`work-order-notes-${complaintId}`}
            rows={2}
            maxLength={2000}
            value={notes}
            onChange={(event) => setNotes(event.target.value)}
            placeholder="e.g. Electrician replaced the fan capacitor"
            className="w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm outline-none focus:border-indigo-500"
          />
          <div className="flex flex-wrap gap-2">
            {active.status === 'OPEN' && (
              <button type="button" disabled={busy} onClick={() => update({ status: 'IN_PROGRESS' })}
                className="rounded-lg bg-indigo-600 px-3 py-1.5 font-semibold text-white hover:bg-indigo-700 disabled:opacity-60">
                Mark in progress
              </button>
            )}
            <button type="button" disabled={busy} onClick={() => update({ status: 'DONE', completionNotes: notes })}
              className="rounded-lg bg-emerald-600 px-3 py-1.5 font-semibold text-white hover:bg-emerald-700 disabled:opacity-60">
              Mark done (resolves complaint)
            </button>
            <button type="button" disabled={busy} onClick={() => update({ status: 'CANCELLED' })}
              className="rounded-lg border border-slate-300 bg-white px-3 py-1.5 text-slate-700 hover:bg-slate-100 disabled:opacity-60">
              Cancel assignment
            </button>
            {busy && <RefreshCw className="h-4 w-4 animate-spin self-center text-slate-500" />}
          </div>
        </div>
      )}
      {error && <p role="alert" className="m-0 text-rose-700">{error}</p>}
    </div>
  );
}
