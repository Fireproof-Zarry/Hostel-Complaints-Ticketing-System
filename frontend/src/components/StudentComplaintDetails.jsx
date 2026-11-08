import { useEffect, useRef, useState } from 'react';
import { KeyRound, Pencil, Phone, X } from 'lucide-react';
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

const MAX_DESCRIPTION_LENGTH = 1000;

/** The complaint text, with a pencil to edit it while the complaint is still open. */
function EditableDescription({ complaint, onSave, onEditingChange }) {
  const [draft, setDraft] = useState(null);
  const [isSaving, setIsSaving] = useState(false);
  const [error, setError] = useState('');
  const editable = complaint.status !== 'RESOLVED' && complaint.status !== 'REJECTED';
  const isEditing = draft !== null;

  useEffect(() => onEditingChange(isEditing), [isEditing, onEditingChange]);

  const cancel = () => {
    setDraft(null);
    setError('');
  };

  const save = async () => {
    const trimmed = draft.trim();
    if (trimmed === complaint.description) {
      cancel();
      return;
    }
    setIsSaving(true);
    setError('');
    try {
      await onSave(complaint, trimmed);
      setDraft(null);
    } catch (saveError) {
      setError(saveError instanceof Error ? saveError.message : 'Could not save. Please try again.');
    } finally {
      setIsSaving(false);
    }
  };

  if (isEditing) {
    const tooLong = draft.trim().length > MAX_DESCRIPTION_LENGTH;
    return (
      <div className="space-y-2">
        <textarea
          value={draft}
          onChange={(event) => setDraft(event.target.value)}
          onKeyDown={(event) => {
            if (event.key === 'Escape') {
              event.stopPropagation();
              cancel();
            }
          }}
          aria-label="Complaint description"
          rows={5}
          autoFocus
          disabled={isSaving}
          className="w-full rounded-lg border border-slate-300 p-3 text-sm leading-6 text-slate-800 focus:border-blue-500 focus:outline-none focus:ring-2 focus:ring-blue-200"
        />
        <div className="flex flex-wrap items-center justify-between gap-2">
          <span className={`text-xs ${tooLong ? 'text-red-600' : 'text-slate-500'}`}>
            {draft.trim().length}/{MAX_DESCRIPTION_LENGTH}
          </span>
          <div className="flex gap-2">
            <button
              type="button"
              onClick={cancel}
              disabled={isSaving}
              className="rounded-lg border border-slate-300 px-3 py-1.5 text-sm font-semibold text-slate-700 transition hover:bg-slate-50"
            >
              Cancel
            </button>
            <button
              type="button"
              onClick={save}
              disabled={isSaving || !draft.trim() || tooLong}
              className="rounded-lg bg-blue-600 px-3 py-1.5 text-sm font-semibold text-white transition hover:bg-blue-700 disabled:cursor-not-allowed disabled:opacity-60"
            >
              {isSaving ? 'Saving…' : 'Save'}
            </button>
          </div>
        </div>
        {error && <p role="alert" className="m-0 text-sm text-red-600">{error}</p>}
      </div>
    );
  }

  return (
    <div className="flex items-start gap-2">
      <p className="m-0 min-w-0 flex-1 whitespace-pre-wrap text-sm leading-6 text-slate-700">{complaint.description}</p>
      {editable && (
        <button
          type="button"
          onClick={() => setDraft(complaint.description ?? '')}
          aria-label="Edit complaint text"
          title="Edit complaint text"
          className="shrink-0 rounded-lg p-1.5 text-slate-500 transition hover:bg-slate-100 hover:text-slate-800"
        >
          <Pencil className="h-4 w-4" aria-hidden="true" />
        </button>
      )}
    </div>
  );
}

/** One complaint in full for the student who filed it. Escape, Close or the backdrop closes it. */
export default function StudentComplaintDetails({ complaint, onClose, onSaveDescription }) {
  const closeRef = useRef(null);
  const [isEditing, setIsEditing] = useState(false);

  useEffect(() => {
    closeRef.current?.focus();
  }, []);

  useEffect(() => {
    // While editing, Escape cancels the edit instead of closing the dialog.
    if (isEditing) return undefined;
    const handleKeyDown = (event) => {
      if (event.key === 'Escape') onClose();
    };
    document.addEventListener('keydown', handleKeyDown);
    return () => document.removeEventListener('keydown', handleKeyDown);
  }, [isEditing, onClose]);

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/50 p-4"
      onClick={isEditing ? undefined : onClose}
    >
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
          <EditableDescription
            complaint={complaint}
            onSave={onSaveDescription}
            onEditingChange={setIsEditing}
          />

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
