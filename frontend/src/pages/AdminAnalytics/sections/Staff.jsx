import { useState } from 'react'
import {
    ResponsiveContainer,
    BarChart,
    Bar,
    CartesianGrid,
    XAxis,
    YAxis,
    Tooltip,
    Legend,
} from 'recharts'

import {
    AlertCircle,
    ArrowDown,
    ArrowUp,
    CalendarCheck,
    ClipboardList,
    Clock3,
} from 'lucide-react'

import {
    formatNumber,
    formatResolutionTime,
} from '../utils/analyticsUtils'

// Shown next to names that are not current staff
const ROLE_LABELS = {
    ADMIN: 'Admin',
    STUDENT: 'Former staff',
}

const COLUMNS = [
    { key: 'name', label: 'Name' },
    { key: 'assigned', label: 'Assigned' },
    { key: 'active', label: 'Active' },
    { key: 'overdue', label: 'Overdue' },
    { key: 'completed', label: 'Completed' },
    { key: 'cancelled', label: 'Cancelled' },
    { key: 'averageCompletionTimeHours', label: 'Avg. completion' },
    { key: 'onTimeRate', label: 'On time' },
]

const formatRate = (rate) =>
    rate == null ? 'N/A' : `${Number(rate).toFixed(1)}%`

// Displays a staff metric.
function StaffMetric({ icon: Icon, label, value, description }) {
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

// Active vs completed work orders for each person.
function WorkloadChart({ members }) {
    const data = members
        .filter((member) => member.active > 0 || member.completed > 0)
        .map((member) => ({
            name: member.name,
            active: member.active,
            completed: member.completed,
        }))

    return (
        <article className="analytics-chart-card">
            <div className="analytics-chart-header">
                <h3>Workload</h3>
                <p>
                    Work orders each person has open now, and how many they
                    completed in the selected period.
                </p>
            </div>

            {data.length === 0 ? (
                <p className="analytics-empty-state">
                    No active or completed work orders.
                </p>
            ) : (
                <div className="analytics-chart-container">
                    <ResponsiveContainer width="100%" height={320}>
                        <BarChart
                            data={data}
                            margin={{ top: 15, right: 15, left: 0, bottom: 5 }}
                        >
                            <CartesianGrid
                                strokeDasharray="3 3"
                                vertical={false}
                            />

                            <XAxis
                                dataKey="name"
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
                                formatter={(value, name) => [
                                    formatNumber(value),
                                    name,
                                ]}
                            />

                            <Legend />

                            <Bar
                                dataKey="active"
                                name="Active"
                                fill="#f59e0b"
                                radius={[5, 5, 0, 0]}
                            />

                            <Bar
                                dataKey="completed"
                                name="Completed"
                                fill="#16a36a"
                                radius={[5, 5, 0, 0]}
                            />
                        </BarChart>
                    </ResponsiveContainer>
                </div>
            )}
        </article>
    )
}

// Compares two members on a column; empty values always sort last.
function compareMembers(a, b, key, direction) {
    const left = a[key]
    const right = b[key]

    if (left == null && right == null) return 0
    if (left == null) return 1
    if (right == null) return -1

    const order = typeof left === 'string'
        ? left.localeCompare(right, undefined, { sensitivity: 'base' })
        : left - right

    return direction === 'asc' ? order : -order
}

// One row per person, sortable by any column.
function StaffTable({ members }) {
    const [sort, setSort] = useState({ key: 'active', direction: 'desc' })

    const sorted = [...members].sort((a, b) =>
        compareMembers(a, b, sort.key, sort.direction)
    )

    const handleSort = (key) => {
        setSort((current) =>
            current.key === key
                ? { key, direction: current.direction === 'asc' ? 'desc' : 'asc' }
                : { key, direction: key === 'name' ? 'asc' : 'desc' }
        )
    }

    return (
        <article className="analytics-chart-card">
            <div className="analytics-chart-header">
                <h3>By staff member</h3>
                <p>
                    Active and overdue are as of now. Assigned counts work
                    orders created in the period; completed, cancelled and the
                    times count those finished in it.
                </p>
            </div>

            {members.length === 0 ? (
                <p className="analytics-empty-state">
                    No staff members yet.
                </p>
            ) : (
                <div className="staff-table-wrapper">
                    <table className="staff-table">
                        <thead>
                            <tr>
                                {COLUMNS.map((column) => {
                                    const isSorted = sort.key === column.key
                                    const SortIcon = sort.direction === 'asc' ? ArrowUp : ArrowDown

                                    return (
                                        <th
                                            key={column.key}
                                            scope="col"
                                            aria-sort={
                                                isSorted
                                                    ? (sort.direction === 'asc' ? 'ascending' : 'descending')
                                                    : 'none'
                                            }
                                        >
                                            <button
                                                type="button"
                                                onClick={() => handleSort(column.key)}
                                            >
                                                {column.label}
                                                {isSorted && <SortIcon size={13} aria-hidden="true" />}
                                            </button>
                                        </th>
                                    )
                                })}
                            </tr>
                        </thead>

                        <tbody>
                            {sorted.map((member) => (
                                <tr key={member.userId}>
                                    <th scope="row">
                                        <span className="staff-table-name">{member.name}</span>
                                        {ROLE_LABELS[member.role] && (
                                            <span className="staff-table-role">
                                                {ROLE_LABELS[member.role]}
                                            </span>
                                        )}
                                    </th>
                                    <td>{formatNumber(member.assigned)}</td>
                                    <td>{formatNumber(member.active)}</td>
                                    <td className={member.overdue > 0 ? 'staff-table-overdue' : undefined}>
                                        {formatNumber(member.overdue)}
                                    </td>
                                    <td>{formatNumber(member.completed)}</td>
                                    <td>{formatNumber(member.cancelled)}</td>
                                    <td>{formatResolutionTime(member.averageCompletionTimeHours)}</td>
                                    <td>{formatRate(member.onTimeRate)}</td>
                                </tr>
                            ))}
                        </tbody>
                    </table>
                </div>
            )}
        </article>
    )
}

/** The Staff tab: work order load, speed and deadlines per staff member. */
function Staff({ staff }) {
    const members = staff?.members ?? []

    return (
        <section className="analytics-section">
            <header className="analytics-section-heading">
                <div>
                    <h3>Staff Performance</h3>
                    <p>
                        How work orders are spread across staff, and how
                        quickly they are completed.
                    </p>
                </div>
            </header>

            <div className="analytics-metrics-grid staff-metrics-grid">
                <StaffMetric
                    icon={ClipboardList}
                    label="Active Work Orders"
                    value={formatNumber(staff?.activeWorkOrders)}
                    description="Open or in progress right now."
                />

                <StaffMetric
                    icon={AlertCircle}
                    label="Overdue Now"
                    value={formatNumber(staff?.overdueWorkOrders)}
                    description="Active work orders past their deadline."
                />

                <StaffMetric
                    icon={Clock3}
                    label="Average Completion Time"
                    value={formatResolutionTime(staff?.averageCompletionTimeHours)}
                    description={`From assignment to done, over ${formatNumber(staff?.completedWorkOrders)} completed in the period.`}
                />

                <StaffMetric
                    icon={CalendarCheck}
                    label="On-time Rate"
                    value={formatRate(staff?.onTimeRate)}
                    description="Completed work orders with a deadline that were done by it."
                />
            </div>

            <WorkloadChart members={members} />

            <StaffTable members={members} />
        </section>
    )
}

export default Staff
