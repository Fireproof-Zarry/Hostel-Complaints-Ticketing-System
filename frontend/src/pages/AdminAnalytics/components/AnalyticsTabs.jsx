
/** Switches between the dashboard's sections. */
function AnalyticsTabs({ tabs, activeTab, onTabChange }) {
    return (
        <div className="analytics-tabs">
            {tabs.map((tab) => {
                const Icon = tab.icon

                return (
                    <button
                        key={tab.id}
                        type="button"
                        className={`analytics-tab ${activeTab === tab.id ? 'active' : ''
                            }`}
                        onClick={() => onTabChange(tab.id)}
                    >
                        {Icon && <Icon size={17} />}
                        <span>{tab.label}</span>
                    </button>
                )
            })}
        </div>
    )
}

export default AnalyticsTabs
