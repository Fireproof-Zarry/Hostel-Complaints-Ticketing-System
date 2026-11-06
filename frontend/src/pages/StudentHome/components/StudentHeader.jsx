import { Bell, LogOut } from 'lucide-react';

export default function StudentHeader({
    title,
    isNotificationsActive,
    onOpenNotifications,
    onLogout,
}) {
    return (
        <header className="flex min-h-16 items-center justify-between gap-4 border-b border-slate-200 bg-white px-4 shadow-sm md:px-8">
            <h2 className="text-lg font-semibold text-slate-800">{title}</h2>

            <div className="flex items-center gap-3">
                <button
                    type="button"
                    onClick={onOpenNotifications}
                    aria-label="Open notifications"
                    className={`rounded-lg border border-slate-200 p-2.5 transition hover:bg-slate-50 hover:text-blue-600 ${isNotificationsActive
                            ? 'bg-blue-50 text-blue-600'
                            : 'text-slate-600'
                        }`}
                >
                    <Bell className="h-5 w-5" />
                </button>

                <button
                    type="button"
                    onClick={onLogout}
                    className="inline-flex items-center gap-2 rounded-lg border border-slate-200 px-3 py-2 text-sm font-medium text-slate-600 transition-colors hover:bg-slate-50 hover:text-slate-900"
                >
                    <LogOut className="h-4 w-4" />
                    <span className="hidden sm:inline">Log out</span>
                </button>
            </div>
        </header>
    );
}