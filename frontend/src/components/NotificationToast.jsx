import { useEffect } from 'react';
import { Bell, X } from 'lucide-react';

// How long the pop-up stays on screen
const TOAST_DURATION_MS = 6000;

// Shows a push message that arrives while the student has HostelFix open.
export default function NotificationToast({ toast, onOpen, onDismiss }) {
    useEffect(() => {
        const timer = setTimeout(onDismiss, TOAST_DURATION_MS);

        return () => clearTimeout(timer);
    }, [toast, onDismiss]);

    return (
        <div
            role="status"
            className="fixed bottom-4 left-4 right-4 z-[60] flex items-start gap-1 rounded-xl border border-slate-200 bg-white p-2 shadow-lg sm:left-auto sm:w-96"
        >
            <button
                type="button"
                onClick={onOpen}
                className="flex min-w-0 flex-1 items-start gap-3 rounded-lg border-0 bg-transparent p-2 text-left transition hover:bg-slate-50"
            >
                <span className="rounded-full bg-indigo-50 p-2 text-indigo-600">
                    <Bell className="h-4 w-4" aria-hidden="true" />
                </span>

                <span className="min-w-0">
                    <span className="block text-sm font-semibold text-slate-900">
                        {toast.title}
                    </span>
                    <span className="mt-0.5 block break-words text-sm text-slate-600">
                        {toast.body}
                    </span>
                </span>
            </button>

            <button
                type="button"
                onClick={onDismiss}
                aria-label="Dismiss notification"
                className="rounded-lg border-0 bg-transparent p-1.5 text-slate-400 transition hover:bg-slate-50 hover:text-slate-700"
            >
                <X className="h-4 w-4" aria-hidden="true" />
            </button>
        </div>
    );
}
