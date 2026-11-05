import { useEffect, useState } from 'react';
import { apiFetch } from '../auth/session';
import { formatStatus } from '../utils/complaintStatus';
import { formatServerDateTime } from '../utils/serverDate';

const apiBaseUrl = (import.meta.env.VITE_API_URL || '/api').replace(/\/$/, '');

/**
 * A complaint's status changes, oldest first, starting from when it was filed.
 * refreshKey reloads it after a change the parent knows about.
 */
export default function ComplaintTimeline({ complaintId, createdAt, refreshKey }) {
  const [entries, setEntries] = useState(null);
  const [error, setError] = useState('');

  useEffect(() => {
    const controller = new AbortController();
    apiFetch(`${apiBaseUrl}/complaints/${complaintId}/history`, { signal: controller.signal })
      .then(async (response) => {
        if (!response.ok) throw new Error('Could not load the timeline.');
        // The API returns the newest change first
        setEntries([...(await response.json())].reverse());
        setError('');
      })
      .catch((loadError) => {
        if (loadError.name !== 'AbortError') setError(loadError.message);
      });
    return () => controller.abort();
  }, [complaintId, refreshKey]);

  if (error) return <p role="alert" className="m-0 text-sm text-rose-700">{error}</p>;
  if (entries === null) return <p className="m-0 text-sm text-slate-500">Loading timeline…</p>;

  const steps = [
    { key: 'filed', title: 'Complaint filed', time: createdAt },
    ...entries.map((entry, index) => ({
      key: index,
      title: formatStatus(entry.newStatus),
      time: entry.changedAt,
      note: entry.reason,
      by: entry.changedBy,
    })),
  ];

  return (
    <ol aria-label="Complaint timeline" className="m-0 list-none p-0">
      {steps.map((step, index) => {
        const isLatest = index === steps.length - 1;
        return (
          <li key={step.key} className="relative pb-5 pl-6 last:pb-0">
            {!isLatest && <span aria-hidden="true" className="absolute bottom-0 left-[5px] top-3 w-0.5 bg-slate-200" />}
            <span
              aria-hidden="true"
              className={`absolute left-0 top-1 h-3 w-3 rounded-full ${isLatest ? 'bg-indigo-600' : 'bg-slate-300'}`}
            />
            <p className="m-0 text-sm font-semibold text-slate-900">{step.title}</p>
            <p className="m-0 mt-0.5 text-xs text-slate-500">
              {formatServerDateTime(step.time)}
              {step.by && ` · ${step.by}`}
            </p>
            {step.note && <p className="m-0 mt-1 whitespace-pre-wrap text-sm text-slate-700">{step.note}</p>}
          </li>
        );
      })}
    </ol>
  );
}
