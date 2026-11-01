import { useEffect, useRef } from 'react';
import { RefreshCw, Trash2 } from 'lucide-react';

/** Confirms deleting a pending complaint. Escape or Cancel closes it. */
export default function DeleteComplaintDialog({ complaint, isDeleting, error, onCancel, onConfirm }) {
  const cancelRef = useRef(null);

  useEffect(() => {
    // Focus the safe choice first
    cancelRef.current?.focus();
    const handleKeyDown = (event) => {
      if (event.key === 'Escape' && !isDeleting) onCancel();
    };
    document.addEventListener('keydown', handleKeyDown);
    return () => document.removeEventListener('keydown', handleKeyDown);
  }, [isDeleting, onCancel]);

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/50 p-4">
      <div
        role="alertdialog"
        aria-modal="true"
        aria-labelledby="delete-complaint-title"
        aria-describedby="delete-complaint-body"
        className="w-full max-w-md rounded-2xl bg-white p-6 shadow-xl"
      >
        <div className="flex items-start gap-3">
          <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-rose-100 text-rose-600">
            <Trash2 className="h-5 w-5" aria-hidden="true" />
          </span>
          <div className="min-w-0">
            <h3 id="delete-complaint-title" className="text-lg font-bold text-slate-900">
              Are you sure you want to delete this complaint?
            </h3>
            <p id="delete-complaint-body" className="mt-1 text-sm text-slate-600">
              <span className="font-medium text-slate-800">#{complaint.id} · {complaint.title}</span>
              <br />
              This can&apos;t be undone.
            </p>
          </div>
        </div>

        {error && (
          <p role="alert" className="mt-4 rounded-lg border border-rose-200 bg-rose-50 p-3 text-sm text-rose-800">{error}</p>
        )}

        <div className="mt-6 flex flex-col gap-2 sm:flex-row-reverse">
          <button
            type="button"
            onClick={onConfirm}
            disabled={isDeleting}
            className="inline-flex flex-1 items-center justify-center gap-2 rounded-xl bg-rose-600 px-4 py-2.5 font-semibold text-white transition hover:bg-rose-700 disabled:cursor-not-allowed disabled:bg-rose-300"
          >
            {isDeleting && <RefreshCw className="h-4 w-4 animate-spin" aria-hidden="true" />}
            {isDeleting ? 'Deleting…' : 'Delete complaint'}
          </button>
          <button
            ref={cancelRef}
            type="button"
            onClick={onCancel}
            disabled={isDeleting}
            className="flex-1 rounded-xl border border-slate-300 px-4 py-2.5 font-medium text-slate-700 transition hover:bg-slate-50 disabled:opacity-60"
          >
            Cancel
          </button>
        </div>
      </div>
    </div>
  );
}
