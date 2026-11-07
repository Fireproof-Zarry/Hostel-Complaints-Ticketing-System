/** A clickable count on the dashboard. */
export default function ComplaintSummaryCard({
    icon: Icon,
    value,
    label,
    iconBackground,
    iconColor,
    onClick,
}) {
    return (
        <button
            type="button"
            onClick={onClick}
            className="flex items-center gap-4 rounded-xl border border-slate-200 bg-white p-5 text-left shadow-sm transition hover:border-blue-300"
        >
            <span className={`rounded-full p-3 ${iconBackground} ${iconColor}`}>
                <Icon className="h-7 w-7" />
            </span>

            <span>
                <span className="block text-2xl font-bold">{value}</span>
                <span className="text-sm font-medium text-slate-500">{label}</span>
            </span>
        </button>
    );
}