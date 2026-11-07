import { RefreshCw } from 'lucide-react';

import ComplaintCard from '../components/ComplaintCard';
import { STUDENT_STATUS_FILTERS } from '../constants/studentConstants';

/** The My Complaints tab, with search and a status filter. */
export default function ComplaintList({
    complaints,
    filteredComplaints,
    isLoading,
    isRefreshing,
    search,
    setSearch,
    statusFilter,
    setStatusFilter,
    onRefresh,
    onFileComplaint,
    onViewComplaint,
    onDeleteComplaint,
}) {
    return (
        <section className="space-y-5">
            <div className="flex flex-col justify-between gap-4 sm:flex-row sm:items-end">
                <div>
                    <h3 className="text-2xl font-bold text-slate-900">My complaints</h3>
                    <p className="mt-1 text-sm text-slate-500">
                        {isLoading
                            ? 'Loading…'
                            : `${filteredComplaints.length} of ${complaints.length} complaints`}
                    </p>
                </div>

                <button
                    type="button"
                    onClick={onRefresh}
                    disabled={isRefreshing}
                    className="inline-flex items-center justify-center gap-2 rounded-lg border border-slate-300 bg-white px-4 py-2.5 text-sm font-medium text-slate-700 transition hover:bg-slate-50 disabled:opacity-50"
                >
                    <RefreshCw
                        className={`h-4 w-4 ${isRefreshing ? 'animate-spin' : ''}`}
                    />
                    Refresh
                </button>
            </div>

            <div className="grid gap-3 sm:grid-cols-[1fr_12rem]">
                <label className="sr-only" htmlFor="complaint-search">
                    Search complaints
                </label>
                <input
                    id="complaint-search"
                    type="search"
                    value={search}
                    onChange={(event) => setSearch(event.target.value)}
                    placeholder="Search title, description, category or room"
                    className="w-full rounded-lg border border-slate-300 bg-white px-4 py-2.5 text-sm outline-none transition focus:border-blue-500 focus:ring-2 focus:ring-blue-200"
                />

                <label className="sr-only" htmlFor="complaint-status-filter">
                    Filter by status
                </label>
                <select
                    id="complaint-status-filter"
                    value={statusFilter}
                    onChange={(event) => setStatusFilter(event.target.value)}
                    className="w-full rounded-lg border border-slate-300 bg-white px-4 py-2.5 text-sm outline-none transition focus:border-blue-500 focus:ring-2 focus:ring-blue-200"
                >
                    {STUDENT_STATUS_FILTERS.map(({ value, label }) => (
                        <option key={value} value={value}>
                            {label}
                        </option>
                    ))}
                </select>
            </div>

            {isLoading ? (
                <p className="rounded-xl border border-slate-200 bg-white p-6 text-sm text-slate-500">
                    Loading your complaints…
                </p>
            ) : filteredComplaints.length === 0 ? (
                <div className="rounded-xl border border-slate-200 bg-white p-8 text-center">
                    <p className="font-medium text-slate-800">
                        {complaints.length === 0
                            ? 'No complaints filed yet.'
                            : 'No complaints match your filters.'}
                    </p>

                    {complaints.length === 0 && (
                        <button
                            type="button"
                            onClick={onFileComplaint}
                            className="mt-4 rounded-lg bg-blue-600 px-4 py-2 text-sm font-semibold text-white hover:bg-blue-700"
                        >
                            File your first complaint
                        </button>
                    )}
                </div>
            ) : (
                <div className="space-y-4">
                    {filteredComplaints.map((complaint) => (
                        <ComplaintCard
                            key={complaint.id}
                            complaint={complaint}
                            onView={onViewComplaint}
                            onDelete={onDeleteComplaint}
                        />
                    ))}
                </div>
            )}
        </section>
    );
}
