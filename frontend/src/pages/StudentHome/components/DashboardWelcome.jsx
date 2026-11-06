import { FilePlus2 } from 'lucide-react';

export default function DashboardWelcome({ onFileComplaint }) {
    return (
        <section className="flex flex-col items-start justify-between gap-5 rounded-2xl bg-gradient-to-r from-blue-600 to-indigo-700 p-6 text-white shadow-lg md:flex-row md:items-center md:p-8">
            <div>
                <p className="text-sm font-medium text-blue-100">
                    Student dashboard
                </p>

                <h3 className="mt-1 text-2xl font-bold md:text-3xl">
                    How can we help?
                </h3>

                <p className="mt-2 max-w-xl text-sm text-blue-100">
                    Report a hostel issue and keep track of its progress from here.
                </p>
            </div>

            <button
                type="button"
                onClick={onFileComplaint}
                className="inline-flex shrink-0 items-center gap-2 rounded-xl bg-white px-5 py-3 font-semibold text-blue-700 shadow-sm transition-colors hover:bg-blue-50"
            >
                <FilePlus2 className="h-5 w-5" />
                File a complaint
            </button>
        </section>
    );
}