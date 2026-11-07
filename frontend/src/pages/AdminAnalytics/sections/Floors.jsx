
import {
    ResponsiveContainer,
    BarChart,
    Bar,
    CartesianGrid,
    XAxis,
    YAxis,
    Tooltip,
} from 'recharts'

import {
    CHART_COLORS,
    formatNumber,
    calculatePercentage,
} from '../utils/analyticsUtils'

// Displays a message when no floor data is available.
function EmptyState() {
    return (
        <p className="analytics-empty-state">
            No floor data available.
        </p>
    )
}

// Displays the complaint count and percentage for each floor.
function FloorBreakdown({ floors, total }) {
    if (floors.length === 0) {
        return (
            <article className="analytics-chart-card">
                <h3>Floor-wise Complaints</h3>
                <EmptyState />
            </article>
        )
    }

    return (
        <article className="analytics-chart-card">
            <h3>Floor-wise Complaints</h3>

            <div className="floor-breakdown-list">
                {floors.map((floor, index) => {
                    const percentage = calculatePercentage(
                        floor.count,
                        total
                    )

                    const color =
                        CHART_COLORS[index % CHART_COLORS.length]

                    return (
                        <div key={floor.floor} className="floor-breakdown-item">
                            <div className="floor-breakdown-info">
                                <strong>{floor.floor}</strong>

                                <span>
                                    {formatNumber(floor.count)} complaints
                                    ({percentage.toFixed(1)}%)
                                </span>
                            </div>

                            <div className="floor-progress-track">
                                <div
                                    className="floor-progress-fill"
                                    style={{
                                        width: `${percentage}%`,
                                        backgroundColor: color,
                                    }}
                                />
                            </div>
                        </div>
                    )
                })}
            </div>
        </article>
    )
}

// Displays a bar chart comparing complaints across floors.
function FloorBarChart({ floors }) {
    if (floors.length === 0) {
        return (
            <article className="analytics-chart-card">
                <h3>Complaint Distribution by Floor</h3>
                <EmptyState />
            </article>
        )
    }

    return (
        <article className="analytics-chart-card">
            <h3>Complaint Distribution by Floor</h3>

            <ResponsiveContainer width="100%" height={320}>
                <BarChart
                    data={floors}
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
                        dataKey="floor"
                        tick={{ fontSize: 12 }}
                        axisLine={false}
                        tickLine={false}
                    />

                    <YAxis
                        allowDecimals={false}
                        tick={{ fontSize: 12 }}
                        axisLine={false}
                        tickLine={false}
                    />

                    <Tooltip
                        formatter={(value) => [
                            formatNumber(value),
                            'Complaints',
                        ]}
                    />

                    <Bar
                        dataKey="count"
                        name="Complaints"
                        fill="#6c3df4"
                        radius={[5, 5, 0, 0]}
                    />
                </BarChart>
            </ResponsiveContainer>
        </article>
    )
}

/** The Floors tab: complaint counts and distribution by floor. */
function Floors({ floorBreakdown = [] }) {
    // Normalize API values before using them in the UI.
    const floors = floorBreakdown.map((item) => ({
        ...item,
        count: Number(item.count ?? 0),
    }))

    const total = floors.reduce(
        (sum, floor) => sum + floor.count,
        0
    )

    return (
        <section className="analytics-section">
            <header className="analytics-section-heading">
                <div>
                    <h3>Floor Distribution</h3>
                    <p>
                        Compare complaint volumes across hostel floors.
                    </p>
                </div>

                <span className="analytics-section-total">
                    {formatNumber(total)} complaints
                </span>
            </header>

            <div className="analytics-floor-layout">
                <FloorBarChart floors={floors} />

                <FloorBreakdown
                    floors={floors}
                    total={total}
                />
            </div>
        </section>
    )
}

export default Floors
