import { useEffect, useRef, useState } from 'react';
import { RefreshCw, Trash2 } from 'lucide-react';

// Values match the backend DeletionReason enum
const REASONS = [
  { value: 'WRONGFUL_SUBMISSION', label: 'Wrongful submission' },
  { value: 'PROBLEM_RECTIFIED', label: 'Problem already rectified' },
  { value: 'TEST_TICKET', label: 'Test ticket' },
  { value: 'INCORRECT_DETAILS', label: 'Incorrect details' },
];

/** Asks why a pending complaint is being deleted before deleting it. */
export default function DeleteComplaintDialog({ complaint, isDeleting, error, onCancel, onConfirm }) {
  const [reason, setReason] = useState('');
  const firstOptionRef = useRef(null);

  useEffect(() => {
    firstOptionRef.current?.focus();
    const handleKeyDown = (event) => {
      if (event.key === 'Escape' && !isDeleting) onCancel();
    };
    document.addEventListener('keydown', handleKeyDown);
    return () => document.removeEventListener('keydown', handleKeyDown);
  }, [isDeleting, onCancel]);

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/50 p-4">
      <form
        role="dialog"
        aria-modal="true"
        aria-labelledby="delete-complaint-title"
        onSubmit={(event) => {
          event.preventDefault();
          if (reason) onConfirm(reason);
        }}
        className="w-full max-w-md rounded-2xl bg-white p-6 shadow-xl"
      >
        <div className="flex items-start gap-3">
          <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-rose-100 text-rose-600">
            <Trash2 className="h-5 w-5" aria-hidden="true" />
          </span>
          <div className="min-w-0">
            <h3 id="delete-complaint-title" className="text-lg font-bold text-slate-900">Delete complaint #{complaint.id}?</h3>
            <p className="mt-1 truncate text-sm text-slate-500">{complaint.title}</p>
          </div>
        </div>

        <fieldset className="mt-5">
          <legend className="mb-2 text-sm font-medium text-slate-700">Why are you deleting this complaint?</legend>
          <div className="space-y-2">
            {REASONS.map((option, index) => (
              <label
                key={option.value}
                className={`flex cursor-pointer items-center gap-3 rounded-lg border p-3 text-sm transition ${
                  reason === option.value ? 'border-rose-400 bg-rose-50' : 'border-slate-300 hover:border-rose-300'
                }`}
              >
                <input
                  ref={index === 0 ? firstOptionRef : undefined}
                  type="radio"
                  name="deletion-reason"
                  value={option.value}
                  checked={reason === option.value}
                  onChange={() => setReason(option.value)}
                  disabled={isDeleting}
                  className="accent-rose-600"
                />
                {option.label}
              </label>
            ))}
          </div>
        </fieldset>

        {error && (
          <p role="alert" className="mt-4 rounded-lg border border-rose-200 bg-rose-50 p-3 text-sm text-rose-800">{error}</p>
        )}

        <div className="mt-6 flex flex-col gap-2 sm:flex-row-reverse">
          <button
            type="submit"
            disabled={!reason || isDeleting}
            className="inline-flex flex-1 items-center justify-center gap-2 rounded-xl bg-rose-600 px-4 py-2.5 font-semibold text-white transition hover:bg-rose-700 disabled:cursor-not-allowed disabled:bg-rose-300"
          >
            {isDeleting && <RefreshCw className="h-4 w-4 animate-spin" aria-hidden="true" />}
            {isDeleting ? 'Deleting…' : 'Delete complaint'}
          </button>
          <button
            type="button"
            onClick={onCancel}
            disabled={isDeleting}
            className="flex-1 rounded-xl border border-slate-300 px-4 py-2.5 font-medium text-slate-700 transition hover:bg-slate-50 disabled:opacity-60"
          >
            Cancel
          </button>
        </div>
      </form>
    </div>
  );
}
