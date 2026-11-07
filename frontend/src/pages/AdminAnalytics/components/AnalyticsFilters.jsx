
/** The date range controls. */
function AnalyticsFilters({
    startDate,
    endDate,
    onStartDateChange,
    onEndDateChange,
    onApply,
    onReset,
    loading,
}) {
    return (
        <div className="analytics-filters">
            <div className="filter-group">
                <label htmlFor="analytics-start-date">
                    Start Date
                </label>
                <input
                    id="analytics-start-date"
                    type="date"
                    value={startDate}
                    onChange={(e) => onStartDateChange(e.target.value)}
                />
            </div>

            <div className="filter-group">
                <label htmlFor="analytics-end-date">
                    End Date
                </label>
                <input
                    id="analytics-end-date"
                    type="date"
                    value={endDate}
                    onChange={(e) => onEndDateChange(e.target.value)}
                    min={startDate || undefined}
                />
            </div>

            <button
                type="button"
                className="analytics-apply-btn"
                onClick={onApply}
                disabled={loading}
            >
                Apply
            </button>

            <button
                type="button"
                className="analytics-reset-btn"
                onClick={onReset}
                disabled={loading}
            >
                Reset
            </button>
        </div>
    )
}

export default AnalyticsFilters
