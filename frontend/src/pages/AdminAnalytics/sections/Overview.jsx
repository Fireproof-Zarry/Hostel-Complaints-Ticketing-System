import {
    ResponsiveContainer,
    LineChart,
    Line,
    CartesianGrid,
    XAxis,
    YAxis,
    Tooltip,
    Legend,
} from 'recharts'

import AnalyticsSummaryCards from '../components/AnalyticsSummaryCards'
import AnalyticsMetricCards from '../components/AnalyticsMetricCards'
import { formatMonth } from '../utils/analyticsUtils'

/** The Overview tab, with the monthly complaint trend. */
function Overview({ overview, monthlyTrendData }) {
    return (
        <div className="analytics-section">
            <AnalyticsSummaryCards overview={overview} />

            <AnalyticsMetricCards overview={overview} />

            <div className="analytics-chart-card">
                <div className="analytics-chart-header">
                    <div>
                        <h3>Monthly Complaint Trends</h3>
                        <p>Complaints created versus resolved each month</p>
                    </div>
                </div>

                <div className="analytics-chart-container">
                    {monthlyTrendData?.length > 0 ? (
                        <ResponsiveContainer width="100%" height={320}>
                            <LineChart
                                data={monthlyTrendData}
                                margin={{
                                    top: 10,
                                    right: 20,
                                    left: 0,
                                    bottom: 5,
                                }}
                            >
                                <CartesianGrid
                                    strokeDasharray="3 3"
                                    vertical={false}
                                />

                                <XAxis
                                    dataKey="month"
                                    tickFormatter={formatMonth}
                                />

                                <YAxis allowDecimals={false} />

                                <Tooltip
                                    labelFormatter={formatMonth}
                                />

                                <Legend />

                                <Line
                                    type="monotone"
                                    dataKey="created"
                                    name="Created"
                                    stroke="#6c3df4"
                                    strokeWidth={3}
                                    dot={{ r: 4 }}
                                    activeDot={{ r: 6 }}
                                />

                                <Line
                                    type="monotone"
                                    dataKey="resolved"
                                    name="Resolved"
                                    stroke="#16a36a"
                                    strokeWidth={3}
                                    dot={{ r: 4 }}
                                    activeDot={{ r: 6 }}
                                />
                            </LineChart>
                        </ResponsiveContainer>
                    ) : (
                        <div className="analytics-empty-state">
                            No monthly trend data available.
                        </div>
                    )}
                </div>
            </div>
        </div>
    )
}

export default Overview