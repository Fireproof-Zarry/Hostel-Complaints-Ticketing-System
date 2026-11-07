import { useEffect, useRef } from 'react';
import { KeyRound, Phone, X } from 'lucide-react';
import ComplaintTimeline from './ComplaintTimeline';
import { formatStatus, statusClasses } from '../utils/complaintStatus';
import { formatServerDateTime } from '../utils/serverDate';

/** One labelled value in the details grid. */
function Detail({ label, children }) {
  return (
    <div>
      <dt className="text-xs font-semibold uppercase tracking-wide text-slate-500">{label}</dt>
      <dd className="m-0 mt-0.5 text-sm text-slate-800">{children}</dd>
    </div>
  );
}

/** One complaint in full for the student who filed it. Escape, Close or the backdrop closes it. */
export default function StudentComplaintDetails({ complaint, onClose }) {
  const closeRef = useRef(null);

  useEffect(() => {
    closeRef.current?.focus();
    const handleKeyDown = (event) => {
      if (event.key === 'Escape') onClose();
    };
    document.addEventListener('keydown', handleKeyDown);
    return () => document.removeEventListener('keydown', handleKeyDown);
  }, [onClose]);

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/50 p-4" onClick={onClose}>
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby="complaint-details-title"
        onClick={(event) => event.stopPropagation()}
        className="flex max-h-[90vh] w-full max-w-2xl flex-col rounded-2xl bg-white shadow-xl"
      >
        <div className="flex items-start justify-between gap-3 border-b border-slate-200 p-5">
          <div className="min-w-0">
            <p className="m-0 text-xs font-semibold uppercase tracking-wide text-slate-500">
              {complaint.category || 'Uncategorized'} · #{complaint.id}
            </p>
            <h3 id="complaint-details-title" className="m-0 mt-1 text-lg font-bold text-slate-900">{complaint.title}</h3>
            <span className={`mt-2 inline-block rounded-full px-3 py-1 text-xs font-semibold ${statusClasses(complaint.status)}`}>
              {formatStatus(complaint.status)}
            </span>
          </div>
          <button
            ref={closeRef}
            type="button"
            aria-label="Close"
            onClick={onClose}
            className="shrink-0 rounded-lg p-1.5 text-slate-500 transition hover:bg-slate-100 hover:text-slate-800"
          >
            <X className="h-5 w-5" aria-hidden="true" />
          </button>
        </div>

        <div className="space-y-5 overflow-y-auto p-5">
          <p className="m-0 whitespace-pre-wrap text-sm leading-6 text-slate-700">{complaint.description}</p>

          {complaint.imageUrl && (
            <a href={complaint.imageUrl} target="_blank" rel="noreferrer" className="block" title="Open full-size photo">
              <img
                src={complaint.imageUrl}
                alt={`Attached to complaint #${complaint.id}`}
                className="max-h-72 w-auto max-w-full rounded-lg border border-slate-200 object-contain"
              />
            </a>
          )}

          <dl className="m-0 grid gap-4 sm:grid-cols-2">
            <Detail label="Location">
              {complaint.floor || 'Floor unavailable'} ·{' '}
              {complaint.communityArea ? complaint.wing || 'Community area' : `Room ${complaint.room || 'N/A'}`}
            </Detail>
            <Detail label="Filed">{formatServerDateTime(complaint.createdAt)}</Detail>
            {complaint.lastNudgedAt && (
              <Detail label="Last nudged">{formatServerDateTime(complaint.lastNudgedAt)}</Detail>
            )}
            {complaint.availableTimeSlot && <Detail label="Available">{complaint.availableTimeSlot}</Detail>}
            {complaint.keyWithSecurity && (
              <Detail label="Room access">
                <span className="inline-flex items-center gap-1.5">
                  <KeyRound className="h-4 w-4" aria-hidden="true" />
                  Key with security
                </span>
              </Detail>
            )}
          </dl>

          {complaint.assignee && (
            <section aria-label="Assigned staff" className="rounded-lg border border-indigo-100 bg-indigo-50 px-3 py-2 text-sm text-slate-700">
              <p className="m-0">
                Being handled by <span className="font-semibold text-slate-900">{complaint.assignee.name}</span>
              </p>
              {complaint.assignee.phoneNumber ? (
                <a
                  href={`tel:${complaint.assignee.phoneNumber}`}
                  className="mt-1 inline-flex items-center gap-1.5 font-medium text-indigo-700 hover:underline"
                >
                  <Phone className="h-4 w-4" aria-hidden="true" />
                  {complaint.assignee.phoneNumber}
                </a>
              ) : (
                <p className="m-0 mt-1 text-xs text-slate-500">No phone number added yet.</p>
              )}
            </section>
          )}

          <section aria-label="Timeline">
            <h4 className="m-0 mb-3 text-sm font-semibold text-slate-900">Timeline</h4>
            <ComplaintTimeline
              complaintId={complaint.id}
              createdAt={complaint.createdAt}
              refreshKey={complaint.updatedAt}
            />
          </section>
        </div>
      </div>
    </div>
  );
}
