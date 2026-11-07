import { RefreshCw } from 'lucide-react';

import { formatStatus, statusClasses } from '../../../utils/complaintStatus';
import { formatDate, sortComplaintsByDate } from '../utils/studentUtils';

/** The newest complaints on the dashboard. */
export default function RecentComplaints({
    complaints,
    isLoading,
    isRefreshing,
    onRefresh,
    onOpenComplaints,
}) {
    const recentComplaints = sortComplaintsByDate(complaints).slice(0, 3);

    return (
        <section className="overflow-hidden rounded-xl border border-slate-200 bg-white shadow-sm">
            <div className="flex items-center justify-between gap-3 border-b border-slate-200 bg-slate-50 px-5 py-4">
                <h3 className="font-semibold text-slate-800">Recent complaints</h3>

                <button
                    type="button"
                    onClick={onRefresh}
                    disabled={isRefreshing}
                    aria-label="Refresh complaints"
                    className="rounded-lg p-2 text-slate-500 transition hover:bg-white hover:text-blue-700 disabled:opacity-50"
                >
                    <RefreshCw
                        className={`h-4 w-4 ${isRefreshing ? 'animate-spin' : ''}`}
                    />
                </button>
            </div>

            <div className="divide-y divide-slate-100">
                {isLoading ? (
                    <p className="p-5 text-sm text-slate-500">
                        Loading your complaints…
                    </p>
                ) : complaints.length === 0 ? (
                    <div className="p-6 text-center">
                        <p className="font-medium text-slate-700">
                            No complaints filed yet.
                        </p>
                        <p className="mt-1 text-sm text-slate-500">
                            Use “File a complaint” to report an issue.
                        </p>
                    </div>
                ) : (
                    recentComplaints.map((complaint) => (
                        <button
                            key={complaint.id}
                            type="button"
                            onClick={onOpenComplaints}
                            className="flex w-full items-center justify-between gap-4 p-4 text-left transition-colors hover:bg-slate-50"
                        >
                            <span className="min-w-0">
                                <span className="block truncate text-sm font-semibold text-slate-900">
                                    {complaint.title}
                                </span>
                                <span className="mt-1 block text-xs text-slate-500">
                                    {complaint.category} · Filed {formatDate(complaint.createdAt)}
                                </span>
                            </span>

                            <span
                                className={`shrink-0 rounded-full px-3 py-1 text-xs font-semibold ${statusClasses(complaint.status)}`}
                            >
                                {formatStatus(complaint.status)}
                            </span>
                        </button>
                    ))
                )}
            </div>
        </section>
    );
}
