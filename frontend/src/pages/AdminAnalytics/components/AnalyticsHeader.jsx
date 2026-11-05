
import { RefreshCw } from 'lucide-react'

function AnalyticsHeader({ onRefresh, loading }) {
    return (
        <div className="analytics-header">
            <div className="analytics-header-content">
                <h1>Complaint Analytics</h1>
                <p>
                    Monitor complaint trends, distribution, and resolution
                    performance.
                </p>
            </div>

            <button
                className="analytics-refresh-btn"
                onClick={onRefresh}
                disabled={loading}
                type="button"
            >
                <RefreshCw
                    size={16}
                    className={loading ? 'spin' : ''}
                />
                {loading ? 'Refreshing...' : 'Refresh'}
            </button>
        </div>
    )
}

export default AnalyticsHeader
