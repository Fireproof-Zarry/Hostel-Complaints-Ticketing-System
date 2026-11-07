import { AlertCircle, ClipboardList } from 'lucide-react';

import DashboardWelcome from '../components/DashboardWelcome';
import ComplaintSummaryCard from '../components/ComplaintSummaryCard';
import RecentComplaints from '../components/RecentComplaints';

/** The Home tab: welcome, counts and recent complaints. */
export default function StudentDashboard({
    complaints,
    activeCount,
    isLoading,
    isRefreshing,
    onRefresh,
    onFileComplaint,
    onOpenComplaints,
}) {
    return (
        <div className="space-y-6">
            <DashboardWelcome onFileComplaint={onFileComplaint} />

            <section
                aria-label="Complaint summary"
                className="grid gap-4 sm:grid-cols-2"
            >
                <ComplaintSummaryCard
                    icon={AlertCircle}
                    value={isLoading ? '—' : activeCount}
                    label="Active complaints"
                    iconBackground="bg-orange-100"
                    iconColor="text-orange-600"
                    onClick={onOpenComplaints}
                />

                <ComplaintSummaryCard
                    icon={ClipboardList}
                    value={isLoading ? '—' : complaints.length}
                    label="Total complaints"
                    iconBackground="bg-indigo-100"
                    iconColor="text-indigo-600"
                    onClick={onOpenComplaints}
                />
            </section>

            <RecentComplaints
                complaints={complaints}
                isLoading={isLoading}
                isRefreshing={isRefreshing}
                onRefresh={onRefresh}
                onOpenComplaints={onOpenComplaints}
            />
        </div>
    );
}