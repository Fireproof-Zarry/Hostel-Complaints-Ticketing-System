import { AlertTriangle, CheckCircle2, RefreshCw, ArrowBigUp } from 'lucide-react';

function formatStatus(status) {
  return status?.toLowerCase().replaceAll('_', ' ').replace(/\b\w/g, (letter) => letter.toUpperCase()) || 'Open';
}

/**
 * Inline banner listing open complaints similar to the one being filed, either
 * on the same floor (scope "community") or in the student's room (scope "room").
 * "Yes, this is my issue" upvotes the existing ticket instead. Without onUpvote
 * (staff cannot upvote) it only lists them.
 */
export default function DuplicateWarning({ scope = 'community', candidates, upvotingId, onUpvote, onDismiss }) {
  return (
    <section
      aria-labelledby="duplicate-warning-title"
      className="rounded-lg border border-amber-300 bg-amber-50 p-4 text-sm text-amber-950"
    >
      <div className="flex items-start gap-3">
        <AlertTriangle className="mt-0.5 h-5 w-5 shrink-0 text-amber-600" />
        <div className="min-w-0 flex-1">
          <h4 id="duplicate-warning-title" className="font-semibold">Wait! Has this already been reported?</h4>
          <p className="mt-0.5 text-amber-900">
            {scope === 'room' ? 'These open complaints for your room' : 'These open complaints on the same floor'} look
            similar. {onUpvote
              ? 'If one is your issue, upvote it instead of filing a new one.'
              : 'If one is the same issue, you may not need to file a new one.'}
          </p>

          <ul className="mt-3 space-y-2">
            {candidates.map((candidate) => (
              <li
                key={candidate.id}
                className="flex flex-col gap-3 rounded-lg border border-amber-200 bg-white p-3 sm:flex-row sm:items-center sm:justify-between"
              >
                <div className="min-w-0">
                  <p className="truncate font-medium text-slate-900">{candidate.title}</p>
                  <p className="mt-0.5 text-xs text-slate-500">
                    #{candidate.id} · {formatStatus(candidate.status)}
                    {candidate.wing && ` · ${candidate.wing}`}
                    {!candidate.wing && candidate.room && ` · Room ${candidate.room}`}
                    {' · '}
                    {candidate.upvoteCount} {candidate.upvoteCount === 1 ? 'upvote' : 'upvotes'}
                  </p>
                </div>
                {candidate.reportedByYou ? (
                  <span className="shrink-0 text-xs font-medium text-slate-600">You reported this</span>
                ) : candidate.upvotedByYou ? (
                  <span className="inline-flex shrink-0 items-center gap-1.5 text-xs font-medium text-emerald-700">
                    <CheckCircle2 className="h-4 w-4" />
                    You've already upvoted this
                  </span>
                ) : onUpvote && (
                  <button
                    type="button"
                    onClick={() => onUpvote(candidate)}
                    disabled={upvotingId !== null}
                    className="inline-flex shrink-0 items-center justify-center gap-2 rounded-lg bg-amber-600 px-3 py-1.5 font-semibold text-white transition hover:bg-amber-700 disabled:cursor-not-allowed disabled:opacity-60"
                  >
                    {upvotingId === candidate.id
                      ? <RefreshCw className="h-4 w-4 animate-spin" />
                      : <ArrowBigUp className="h-4 w-4" />}
                    Yes, this is my issue
                  </button>
                )}
              </li>
            ))}
          </ul>

          <button
            type="button"
            onClick={onDismiss}
            disabled={upvotingId !== null}
            className="mt-3 rounded-lg border border-amber-300 bg-white px-3 py-1.5 font-medium text-amber-900 transition hover:bg-amber-100 disabled:opacity-60"
          >
            No, mine is different
          </button>
        </div>
      </div>
    </section>
  );
}
