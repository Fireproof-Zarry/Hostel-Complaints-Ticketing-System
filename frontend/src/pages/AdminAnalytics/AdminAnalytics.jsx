
import { useCallback, useEffect, useState } from 'react'
import { RefreshCw } from 'lucide-react'

import { apiFetch } from '../../auth/session'
import { readSessionCache, writeSessionCache } from '../../utils/sessionCache'
import { trackBackground } from '../../utils/backgroundActivity'
import { TABS } from './utils/analyticsUtils'

import AnalyticsHeader from './components/AnalyticsHeader'
import AnalyticsFilters from './components/AnalyticsFilters'
import AnalyticsTabs from './components/AnalyticsTabs'

import Overview from './sections/Overview'
import Categories from './sections/Categories'
import Floors from './sections/Floors'
import RecurringIssues from './sections/RecurringIssues'
import Resolution from './sections/Resolution'
import Staff from './sections/Staff'

import './styles/layout.css'
import './styles/components.css'
import './styles/charts.css'
import './styles/responsive.css'

// The unfiltered figures, shown straight away on returning here while fresh ones load
const ANALYTICS_CACHE_KEY = 'analyticsCache'

/** The analytics dashboard: date filters, summary cards and one section per tab. */
export default function AdminAnalytics() {
    const [activeTab, setActiveTab] = useState('overview')
    const [analytics, setAnalytics] = useState(() => readSessionCache(ANALYTICS_CACHE_KEY))

    const [startDate, setStartDate] = useState('')
    const [endDate, setEndDate] = useState('')

    const [loading, setLoading] = useState(() => !analytics)
    const [openedFromCache] = useState(() => analytics !== null)
    const [error, setError] = useState('')

    // Validate the selected date range.
    const hasInvalidDateRange = Boolean(
        startDate && endDate && endDate < startDate
    )

    // Fetch analytics from the backend; true if it worked.
    const loadAnalytics = useCallback(async (from, to) => {
        try {
            setLoading(true)
            setError('')

            const params = new URLSearchParams()

            if (from) params.append('startDate', from)
            if (to) params.append('endDate', to)

            const queryString = params.toString()
            const endpoint = `${import.meta.env.VITE_API_URL}/admin/analytics`

            const response = await apiFetch(
                `${endpoint}${queryString ? `?${queryString}` : ''}`
            )

            if (!response.ok) {
                throw new Error(
                    `Failed to fetch analytics: ${response.status}`
                )
            }

            const data = await response.json()
            setAnalytics(data)
            if (!from && !to) writeSessionCache(ANALYTICS_CACHE_KEY, data)
            return true
        } catch (err) {
            console.error('Failed to fetch analytics:', err)
            setError(err.message || 'Failed to load analytics.')
            return false
        } finally {
            setLoading(false)
        }
    }, [])

    // background: figures are already on screen, so show the corner "Updating…" instead
    const fetchAnalytics = useCallback((from = '', to = '', { background = false } = {}) => {
        const request = loadAnalytics(from, to)
        return background ? trackBackground(request) : request
    }, [loadAnalytics])

    // Fetch analytics when the page loads.
    useEffect(() => {
        fetchAnalytics('', '', { background: openedFromCache })
    }, [fetchAnalytics, openedFromCache])

    // Apply the selected date filters.
    const handleApplyFilters = () => {
        if (hasInvalidDateRange) return

        // Figures for the old range would be misleading while the new ones load
        setAnalytics(null)
        fetchAnalytics(startDate, endDate)
    }

    // Reset the date filters.
    const handleResetFilters = () => {
        setStartDate('')
        setEndDate('')
        setAnalytics(null)
        fetchAnalytics()
    }

    // Refresh analytics using the currently selected filters.
    const handleRefresh = () => {
        fetchAnalytics(startDate, endDate, { background: analytics !== null })
    }

    const overview = analytics?.overview

    // Prepare monthly trend data for charts.
    const monthlyTrendData = (analytics?.monthlyTrends ?? []).map(
        (item) => ({
            ...item,
            created: Number(item.created ?? 0),
            resolved: Number(item.resolved ?? 0),
        })
    )

    // Find the currently selected tab.
    const currentTab = TABS.find((tab) => tab.id === activeTab)

    // Map each tab to its corresponding section.
    const sections = {
        overview: (
            <Overview
                overview={overview}
                monthlyTrendData={monthlyTrendData}
            />
        ),

        categories: (
            <Categories
                categoryBreakdown={analytics?.categoryBreakdown ?? []}
            />
        ),

        floors: (
            <Floors
                floorBreakdown={analytics?.floorBreakdown ?? []}
            />
        ),

        recurring: (
            <RecurringIssues
                recurringIssues={analytics?.recurringIssues ?? []}
            />
        ),

        resolution: (
            <Resolution
                overview={overview}
                monthlyTrendData={monthlyTrendData}
            />
        ),

        staff: <Staff staff={analytics?.staff} />,
    }

    return (
        <div className="analytics-page">
            <AnalyticsHeader
                onRefresh={handleRefresh}
                loading={loading}
            />

            <AnalyticsFilters
                startDate={startDate}
                endDate={endDate}
                onStartDateChange={setStartDate}
                onEndDateChange={setEndDate}
                onApply={handleApplyFilters}
                onReset={handleResetFilters}
                loading={loading}
            />

            {hasInvalidDateRange && (
                <p className="analytics-error" role="alert">
                    End date must be the same as or later than start date.
                </p>
            )}

            <AnalyticsTabs
                tabs={TABS}
                activeTab={activeTab}
                onTabChange={setActiveTab}
            />

            <section
                className="analytics-panel"
                role="tabpanel"
                id={`analytics-panel-${activeTab}`}
                aria-labelledby={`analytics-tab-${activeTab}`}
            >
                {analytics && error && (
                    <p className="analytics-error" role="alert">
                        Could not refresh analytics: {error}
                    </p>
                )}

                {loading && !analytics ? (
                    <div className="analytics-placeholder">
                        <p>Loading analytics...</p>
                    </div>
                ) : error && !analytics ? (
                    <div className="analytics-placeholder">
                        <h3>Unable to load analytics</h3>

                        <p className="analytics-error">{error}</p>

                        <button
                            type="button"
                            className="admin-primary-button"
                            onClick={handleRefresh}
                        >
                            <RefreshCw size={16} />
                            Try Again
                        </button>
                    </div>
                ) : analytics ? (
                    sections[activeTab] ?? (
                        <div className="analytics-placeholder">
                            <h3>{currentTab?.label}</h3>
                            <p>{currentTab?.description}</p>
                        </div>
                    )
                ) : null}
            </section>
        </div>
    )
}
