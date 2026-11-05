
import { CheckCircle2, Clock3 } from 'lucide-react'
import { formatResolutionTime } from '../utils/analyticsUtils'

function AnalyticsMetricCards({ overview }) {
    const resolutionRate = overview?.resolutionRate ?? 0
    const averageResolutionTime = overview?.averageResolutionTimeHours

    return (
        <div className="analytics-metrics-grid">
            <div className="analytics-metric-card">
                <div className="metric-card-icon resolution">
                    <CheckCircle2 size={20} />
                </div>

                <div className="metric-card-content">
                    <span className="metric-card-label">
                        Resolution Rate
                    </span>

                    <span className="metric-card-value">
                        {Number(resolutionRate).toFixed(1)}%
                    </span>
                </div>
            </div>

            <div className="analytics-metric-card">
                <div className="metric-card-icon time">
                    <Clock3 size={20} />
                </div>

                <div className="metric-card-content">
                    <span className="metric-card-label">
                        Average Resolution Time
                    </span>

                    <span className="metric-card-value">
                        {formatResolutionTime(averageResolutionTime)}
                    </span>
                </div>
            </div>
        </div>
    )
}

export default AnalyticsMetricCards
