
import { Repeat2 } from 'lucide-react'

import { formatNumber } from '../utils/analyticsUtils'

// Shared empty state
function EmptyState() {
    return (
        <p className="analytics-empty-state">
            No recurring complaints found.
        </p>
    )
}

// Displays a single recurring issue
function RecurringIssueCard({ issue, type }) {
    const location =
        type === 'ROOM'
            ? `Room ${issue.roomNumber}`
            : `${issue.wing || 'Unspecified wing'}`

    return (
        <article className="recurring-issue-card">
            <div className="recurring-issue-icon">
                <Repeat2 size={18} />
            </div>

            <div className="recurring-issue-details">
                <strong>{issue.category}</strong>

                <span>
                    {issue.floor} · {location}
                </span>
            </div>

            <div className="recurring-issue-count">
                <strong>{formatNumber(issue.count)}</strong>
                <span>complaints</span>
            </div>
        </article>
    )
}

// Displays recurring issues of a particular type
function RecurringIssueGroup({ title, issues, type }) {
    return (
        <section className="analytics-chart-card recurring-issue-group">
            <div className="recurring-issue-group-heading">
                <h3>{title}</h3>
                <span>{issues.length} locations</span>
            </div>

            {issues.length === 0 ? (
                <EmptyState />
            ) : (
                <div className="recurring-issue-list">
                    {issues.map((issue, index) => (
                        <RecurringIssueCard
                            key={`${issue.floor}-${issue.category}-${index}`}
                            issue={issue}
                            type={type}
                        />
                    ))}
                </div>
            )}
        </section>
    )
}

function RecurringIssues({ recurringIssues = [] }) {
    const roomIssues = recurringIssues.filter(
        (issue) => issue.type === 'ROOM'
    )

    const communityIssues = recurringIssues.filter(
        (issue) => issue.type === 'COMMUNITY_AREA'
    )

    const totalRecurringComplaints = recurringIssues.reduce(
        (sum, issue) => sum + Number(issue.count ?? 0),
        0
    )

    return (
        <section className="analytics-section">
            <header className="analytics-section-heading">
                <div>
                    <h3>Recurring Issues</h3>
                    <p>
                        Identify locations where complaints are reported
                        repeatedly.
                    </p>
                </div>

                <span className="analytics-section-total">
                    {formatNumber(totalRecurringComplaints)} complaints
                </span>
            </header>

            <div className="recurring-issues-layout">
                <RecurringIssueGroup
                    title="Recurring Room Issues"
                    issues={roomIssues}
                    type="ROOM"
                />

                <RecurringIssueGroup
                    title="Recurring Community Area Issues"
                    issues={communityIssues}
                    type="COMMUNITY_AREA"
                />
            </div>
        </section>
    )
}

export default RecurringIssues
