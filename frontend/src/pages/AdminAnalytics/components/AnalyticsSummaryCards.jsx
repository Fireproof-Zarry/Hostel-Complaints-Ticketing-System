
import { SUMMARY_CARDS, formatNumber } from '../utils/analyticsUtils'

/** The count cards defined by SUMMARY_CARDS. */
function AnalyticsSummaryCards({ overview }) {
    return (
        <div className="analytics-summary-grid">
            {SUMMARY_CARDS.map((card) => {
                const Icon = card.icon
                const value = overview?.[card.key] ?? 0

                return (
                    <div
                        key={card.key}
                        className={`analytics-summary-card ${card.color}`}
                    >
                        <div className="summary-card-top">
                            <span className="summary-card-label">
                                {card.label}
                            </span>

                            <div className="summary-card-icon">
                                <Icon size={20} />
                            </div>
                        </div>

                        <div className="summary-card-value">
                            {formatNumber(value)}
                        </div>
                    </div>
                )
            })}
        </div>
    )
}

export default AnalyticsSummaryCards
