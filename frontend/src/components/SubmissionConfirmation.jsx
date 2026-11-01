import { useEffect, useRef } from 'react';
import { CheckCircle2 } from 'lucide-react';

/** Confirmation shown after a complaint is filed. Escape closes it (stays on the form). */
export default function SubmissionConfirmation({ complaintId, photoError, onViewComplaints, onFileAnother }) {
  const primaryRef = useRef(null);

  useEffect(() => {
    primaryRef.current?.focus();
    const handleKeyDown = (event) => {
      if (event.key === 'Escape') onFileAnother();
    };
    document.addEventListener('keydown', handleKeyDown);
    return () => document.removeEventListener('keydown', handleKeyDown);
  }, [onFileAnother]);

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/50 p-4">
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby="submission-confirmation-title"
        aria-describedby="submission-confirmation-body"
        className="w-full max-w-md rounded-2xl bg-white p-6 text-center shadow-xl"
      >
        <span className="mx-auto flex h-12 w-12 items-center justify-center rounded-full bg-emerald-100 text-emerald-600">
          <CheckCircle2 className="h-7 w-7" aria-hidden="true" />
        </span>
        <h3 id="submission-confirmation-title" className="mt-4 text-xl font-bold text-slate-900">
          Thank you for filing a complaint
        </h3>
        <p id="submission-confirmation-body" className="mt-2 text-sm leading-6 text-slate-600">
          Your complaint <strong className="text-slate-900">#{complaintId}</strong> has been submitted.
          You can check its status in My Complaints.
        </p>
        {photoError && (
          <p role="alert" className="mt-3 rounded-lg border border-amber-200 bg-amber-50 p-3 text-left text-sm text-amber-900">
            The photo was not attached: {photoError}
          </p>
        )}
        <div className="mt-6 flex flex-col gap-2 sm:flex-row-reverse">
          <button
            ref={primaryRef}
            type="button"
            onClick={onViewComplaints}
            className="flex-1 rounded-xl bg-blue-600 px-4 py-2.5 font-semibold text-white transition hover:bg-blue-700"
          >
            View My Complaints
          </button>
          <button
            type="button"
            onClick={onFileAnother}
            className="flex-1 rounded-xl border border-slate-300 px-4 py-2.5 font-medium text-slate-700 transition hover:bg-slate-50"
          >
            File another complaint
          </button>
        </div>
      </div>
    </div>
  );
}
