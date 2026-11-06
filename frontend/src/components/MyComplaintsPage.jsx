import { useEffect, useState } from 'react';
import { apiFetch } from '../auth/session';
import { formatStatus, statusClasses } from '../utils/complaintStatus';
import { formatServerDateTime } from '../utils/serverDate';

const apiBaseUrl = (import.meta.env.VITE_API_URL || '/api').replace(/\/$/, '');

// Deadlines are hostel wall-clock times, not server timestamps
function formatDeadline(value) {
  if (!value) return 'No deadline';
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? '—' : date.toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' });
}

function ComplaintRow({ id, title, status, overdue, onOpen, children }) {
  return (
    <li>
      <button
        type="button"
        onClick={() => onOpen(id)}
        className={`w-full rounded-lg border p-3 text-left transition hover:border-indigo-300 ${
          overdue ? 'border-rose-200 bg-rose-50' : 'border-slate-200 bg-white'
        }`}
      >
        <span className="flex items-start justify-between gap-3">
          <span className="min-w-0 text-slate-900">
            <strong className="font-bold">Complaint #{id}</strong> {title}
          </span>
          <span className={`shrink-0 rounded-full px-2.5 py-0.5 text-xs font-semibold ${statusClasses(status)}`}>
            {formatStatus(status)}
          </span>
        </span>
        <span className="mt-1 block text-xs text-slate-600">{children}</span>
      </button>
    </li>
  );
}

function Section({ title, count, empty, children }) {
  return (
    <section className="space-y-3">
      <h3 className="m-0 text-lg font-semibold text-slate-900">
        {title} <span className="text-sm font-normal text-slate-500">({count})</span>
      </h3>
      {count === 0
        ? <p className="m-0 rounded-lg border border-slate-200 bg-white p-4 text-sm text-slate-500">{empty}</p>
        : <ul className="m-0 list-none space-y-2 p-0">{children}</ul>}
    </section>
  );
}

/**
 * What belongs to the signed-in staff member or admin: the active work assigned
 * to them, and the complaints they filed. onOpen(id) opens the complaint popup;
 * refreshKey reloads the lists after a change made there.
 */
export default function MyComplaintsPage({ onOpen, refreshKey }) {
  const [assigned, setAssigned] = useState(null);
  const [filed, setFiled] = useState(null);
  const [error, setError] = useState('');

  useEffect(() => {
    const controller = new AbortController();
    const load = async (path) => {
      const response = await apiFetch(`${apiBaseUrl}${path}`, { signal: controller.signal });
      if (!response.ok) throw new Error('Could not load your complaints.');
      return response.json();
    };
    Promise.all([load('/work-orders/me'), load('/complaints/mine')])
      .then(([workOrders, complaints]) => {
        setAssigned(workOrders);
        setFiled(complaints);
        setError('');
      })
      .catch((loadError) => {
        if (loadError.name !== 'AbortError') setError(loadError.message);
      });
    return () => controller.abort();
  }, [refreshKey]);

  const open = async (id) => {
    setError('');
    try {
      await onOpen(id);
    } catch (openError) {
      setError(openError.message);
    }
  };

  return (
    <div className="mx-auto max-w-3xl space-y-6">
      {error && <p role="alert" className="m-0 rounded-lg border border-rose-200 bg-rose-50 p-3 text-sm text-rose-800">{error}</p>}
      {assigned === null || filed === null ? (
        !error && <p className="m-0 text-sm text-slate-500">Loading your complaints…</p>
      ) : (
        <>
          <Section title="Assigned to me" count={assigned.length} empty="Nothing is assigned to you right now.">
            {assigned.map((workOrder) => (
              <ComplaintRow
                key={workOrder.id}
                id={workOrder.complaintId}
                title={workOrder.complaintTitle}
                status={workOrder.complaintStatus}
                overdue={workOrder.overdue}
                onOpen={open}
              >
                {formatStatus(workOrder.priority)} priority ·{' '}
                <span className={workOrder.overdue ? 'font-semibold text-rose-700' : undefined}>
                  {workOrder.overdue ? 'Overdue since ' : 'Due '}{formatDeadline(workOrder.deadline)}
                </span>
              </ComplaintRow>
            ))}
          </Section>

          <Section title="Filed by me" count={filed.length} empty="You haven't filed any complaints.">
            {filed.map((complaint) => (
              <ComplaintRow
                key={complaint.id}
                id={complaint.id}
                title={complaint.title}
                status={complaint.status}
                onOpen={open}
              >
                {complaint.communityArea
                  ? `${complaint.floor} · ${complaint.wing || 'Community area'}`
                  : `Room ${complaint.room}`}
                {` · Filed ${formatServerDateTime(complaint.createdAt)}`}
                {complaint.assignee && ` · Handled by ${complaint.assignee.name}`}
              </ComplaintRow>
            ))}
          </Section>
        </>
      )}
    </div>
  );
}
