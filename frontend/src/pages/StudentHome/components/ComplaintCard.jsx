import { CalendarDays, Trash2 } from 'lucide-react';

import { formatStatus, statusClasses } from '../../../utils/complaintStatus';
import { formatDate } from '../utils/studentUtils';

export default function ComplaintCard({ complaint, onView, onDelete }) {
    return (
        <article className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
            <div className="flex flex-wrap items-start justify-between gap-3">
                <div>
                    <p className="text-xs font-semibold uppercase tracking-wide text-slate-500">
                        {complaint.category || 'Uncategorized'} · #{complaint.id}
                    </p>
                    <h3 className="mt-1 text-lg font-semibold text-slate-900">
                        {complaint.title}
                    </h3>
                </div>

                <span
                    className={`rounded-full px-3 py-1 text-xs font-semibold ${statusClasses(complaint.status)}`}
                >
                    {formatStatus(complaint.status)}
                </span>
            </div>

            <p className="mt-3 line-clamp-2 text-sm leading-6 text-slate-600">
                {complaint.description}
            </p>

            <div className="mt-4 flex flex-wrap gap-x-5 gap-y-2 text-xs font-medium text-slate-500">
                <span className="inline-flex items-center gap-1.5">
                    <CalendarDays className="h-4 w-4" />
                    Filed {formatDate(complaint.createdAt)}
                </span>

                <span>
                    {complaint.floor || 'Floor unavailable'} ·{' '}
                    {complaint.communityArea
                        ? complaint.wing || 'Community area'
                        : `Room ${complaint.room || 'N/A'}`}
                </span>
            </div>

            <div className="mt-4 flex items-center justify-between gap-2 border-t border-slate-100 pt-3">
                <button
                    type="button"
                    onClick={() => onView(complaint)}
                    className="rounded-lg border border-slate-300 px-4 py-1.5 text-sm font-medium text-slate-700 transition hover:bg-slate-50"
                >
                    View
                </button>

                {/* Only before an admin picks it up */}
                {complaint.status?.toUpperCase() === 'PENDING' && (
                    <button
                        type="button"
                        onClick={() => onDelete(complaint)}
                        className="inline-flex items-center gap-1.5 rounded-lg px-3 py-1.5 text-sm font-medium text-rose-700 transition hover:bg-rose-50"
                    >
                        <Trash2 className="h-4 w-4" aria-hidden="true" />
                        Delete
                    </button>
                )}
            </div>
        </article>
    );
}
