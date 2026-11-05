import {
    ResponsiveContainer,
    LineChart,
    Line,
    CartesianGrid,
    XAxis,
    YAxis,
    Tooltip,
    Legend,
    PieChart,
    Pie,
    Cell,
} from 'recharts'

import {
    CheckCircle2,
    Clock3,
    AlertCircle,
} from 'lucide-react'

import {
    formatNumber,
    formatMonth,
    formatResolutionTime,
} from '../utils/analyticsUtils'

const STATUS_COLORS = {
    Resolved: '#16a36a',
    Unresolved: '#f59e0b',
    Rejected: '#ef4444',
}

// Displays a resolution metric.
function ResolutionMetric({ icon: Icon, label, value, description }) {
    return (
        <article className="analytics-metric-card">
            <div className="analytics-metric-heading">
                <Icon size={20} />
                <span>{label}</span>
            </div>

            <h3>{value}</h3>
            <p>{description}</p>
        </article>
    )
}

// Displays the monthly created vs resolved line chart.
function ResolutionTrendChart({ monthlyTrendData }) {
    return (
        <article className="analytics-chart-card">
            <div className="analytics-chart-header">
                <h3>Monthly Resolution Trends</h3>
                <p>
                    Compare the number of complaints created and resolved
                    each month.
                </p>
            </div>

            {monthlyTrendData.length === 0 ? (
                <p className="analytics-empty-state">
                    No monthly trend data available.
                </p>
            ) : (
                <div className="analytics-chart-container">
                    <ResponsiveContainer width="100%" height={320}>
                        <LineChart
                            data={monthlyTrendData}
                            margin={{
                                top: 15,
                                right: 15,
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
                                formatter={(value, name) => [
                                    formatNumber(value),
                                    name === 'created' ? 'Created' : 'Resolved',
                                ]}
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
                </div>
            )}
        </article>
    )
}

// Displays the current complaint status doughnut chart.
function StatusDistributionChart({ resolved, unresolved, rejected }) {
    const statusData = [
        { name: 'Resolved', value: resolved },
        { name: 'Unresolved', value: unresolved },
        { name: 'Rejected', value: rejected },
    ]

    const chartData = statusData.filter((item) => item.value > 0)

    const total = resolved + unresolved + rejected

    return (
        <article className="analytics-chart-card">
            <div className="analytics-chart-header">
                <h3>Current Complaint Status</h3>
                <p>Distribution of complaints by their current status.</p>
            </div>

            {total === 0 ? (
                <p className="analytics-empty-state">
                    No complaint status data available.
                </p>
            ) : (
                <div className="resolution-status-layout">
                    <div className="resolution-status-chart">
                        <ResponsiveContainer width="100%" height={260}>
                            <PieChart>
                                <Pie
                                    data={chartData}
                                    dataKey="value"
                                    nameKey="name"
                                    cx="50%"
                                    cy="50%"
                                    innerRadius={65}
                                    outerRadius={95}
                                    paddingAngle={3}
                                >
                                    {chartData.map((entry) => (
                                        <Cell
                                            key={entry.name}
                                            fill={STATUS_COLORS[entry.name]}
                                        />
                                    ))}
                                </Pie>

                                <Tooltip
                                    formatter={(value) => [
                                        formatNumber(value),
                                        'Complaints',
                                    ]}
                                />

                                <Legend />
                            </PieChart>
                        </ResponsiveContainer>
                    </div>

                    <div className="resolution-status-summary">
                        {statusData.map((item) => (
                            <div key={item.name}>
                                <span>
                                    <span
                                        className="status-color-dot"
                                        style={{
                                            backgroundColor:
                                                STATUS_COLORS[item.name],
                                        }}
                                    />
                                    {item.name}
                                </span>

                                <strong>{formatNumber(item.value)}</strong>
                            </div>
                        ))}
                    </div>
                </div>
            )}
        </article>
    )
}

function Resolution({ overview, monthlyTrendData = [] }) {
    const resolved = Number(overview?.resolved ?? 0)
    const unresolved = Number(overview?.unresolved ?? 0)
    const rejected = Number(overview?.rejected ?? 0)

    const resolutionRate = Number(overview?.resolutionRate ?? 0)
    const averageResolutionTime = overview?.averageResolutionTimeHours

    return (
        <section className="analytics-section">
            <header className="analytics-section-heading">
                <div>
                    <h3>Resolution Performance</h3>
                    <p>
                        Understand how efficiently complaints are being
                        resolved.
                    </p>
                </div>
            </header>

            <div className="analytics-metrics-grid">
                <ResolutionMetric
                    icon={CheckCircle2}
                    label="Resolution Rate"
                    value={`${resolutionRate.toFixed(1)}%`}
                    description="Percentage of eligible complaints currently resolved."
                />

                <ResolutionMetric
                    icon={Clock3}
                    label="Average Resolution Time"
                    value={formatResolutionTime(averageResolutionTime)}
                    description="Average time taken to resolve currently resolved complaints."
                />

                <ResolutionMetric
                    icon={AlertCircle}
                    label="Unresolved Complaints"
                    value={formatNumber(unresolved)}
                    description="Complaints currently pending, assigned or in progress."
                />
            </div>

            <StatusDistributionChart
                resolved={resolved}
                unresolved={unresolved}
                rejected={rejected}
            />

            <ResolutionTrendChart
                monthlyTrendData={monthlyTrendData}
            />
        </section>
    )
}

export default Resolution