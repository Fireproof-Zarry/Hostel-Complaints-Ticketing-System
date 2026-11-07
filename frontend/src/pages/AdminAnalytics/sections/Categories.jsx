
import {
    ResponsiveContainer,
    PieChart,
    Pie,
    Cell,
    Tooltip,
} from 'recharts'

import {
    CHART_COLORS,
    formatNumber,
    calculatePercentage,
} from '../utils/analyticsUtils'

/** Complaint counts per category, as a list. */
function CategoryBreakdown({ categories, total }) {
    return (
        <article className="analytics-chart-card analytics-category-list">
            <h3>Category-wise Complaints</h3>

            {categories.length === 0 ? (
                <p className="analytics-empty-state">
                    No categories available.
                </p>
            ) : (
                <div className="category-breakdown-list">
                    {categories.map((item, index) => {
                        const percentage = calculatePercentage(
                            item.count,
                            total
                        )

                        const color =
                            CHART_COLORS[index % CHART_COLORS.length]

                        return (
                            <div
                                className="category-breakdown-item"
                                key={item.category}
                            >
                                <div className="category-breakdown-info">
                                    <div className="category-name">
                                        <span
                                            className="category-color-dot"
                                            style={{ backgroundColor: color }}
                                        />
                                        <span>{item.category}</span>
                                    </div>

                                    <div className="category-count">
                                        <strong>
                                            {formatNumber(item.count)}
                                        </strong>
                                        <span>
                                            {percentage.toFixed(1)}%
                                        </span>
                                    </div>
                                </div>

                                <div className="category-progress-track">
                                    <div
                                        className="category-progress-fill"
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
            )}
        </article>
    )
}

/** Each category's share of complaints. */
function CategoryPieChart({ categories }) {
    const chartData = categories.filter((item) => item.count > 0)

    return (
        <article className="analytics-chart-card analytics-category-chart">
            <h3>Complaint Breakdown</h3>

            {chartData.length === 0 ? (
                <p className="analytics-empty-state">
                    No category data available.
                </p>
            ) : (
                <ResponsiveContainer width="100%" height={320}>
                    <PieChart>
                        <Pie
                            data={chartData}
                            dataKey="count"
                            nameKey="category"
                            cx="50%"
                            cy="50%"
                            outerRadius={105}
                            innerRadius={55}
                            paddingAngle={2}
                        >
                            {chartData.map((item, index) => (
                                <Cell
                                    key={item.category}
                                    fill={
                                        CHART_COLORS[
                                        index % CHART_COLORS.length
                                        ]
                                    }
                                />
                            ))}
                        </Pie>

                        <Tooltip
                            formatter={(value, name) => [
                                formatNumber(value),
                                name,
                            ]}
                        />
                    </PieChart>
                </ResponsiveContainer>
            )}
        </article>
    )
}

/** The Categories tab. */
function Categories({ categoryBreakdown = [] }) {
    const categories = categoryBreakdown.map((item) => ({
        ...item,
        count: Number(item.count ?? 0),
    }))

    const total = categories.reduce(
        (sum, item) => sum + item.count,
        0
    )

    return (
        <section className="analytics-section">
            <header className="analytics-section-heading">
                <div>
                    <h3>Category Distribution</h3>
                    <p>
                        Understand which complaint categories are reported
                        most frequently.
                    </p>
                </div>

                <span className="analytics-section-total">
                    {formatNumber(total)} complaints
                </span>
            </header>

            <div className="analytics-category-layout">
                <CategoryPieChart categories={categories} />
                <CategoryBreakdown
                    categories={categories}
                    total={total}
                />
            </div>
        </section>
    )
}

export default Categories
