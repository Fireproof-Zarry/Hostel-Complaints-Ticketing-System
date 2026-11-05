
import {
    LayoutDashboard,
    Tags,
    Building2,
    Repeat2,
    Clock3,
    ClipboardList,
    Clock,
    CheckCircle2,
    XCircle,
    AlertCircle,
} from 'lucide-react'

// Analytics navigation tabs
export const TABS = [
    {
        id: 'overview',
        label: 'Overview',
        icon: LayoutDashboard,
        description: 'Summary cards and overall complaint trends.',
    },
    {
        id: 'categories',
        label: 'Categories',
        icon: Tags,
        description: 'Category-wise complaint distribution and charts.',
    },
    {
        id: 'floors',
        label: 'Floors',
        icon: Building2,
        description: 'Floor-wise complaint distribution.',
    },
    {
        id: 'recurring',
        label: 'Recurring Issues',
        icon: Repeat2,
        description: 'Frequently reported rooms and community areas.',
    },
    {
        id: 'resolution',
        label: 'Resolution',
        icon: Clock3,
        description: 'Resolution time, resolution rate and trends.',
    },
]

// Overview summary cards
export const SUMMARY_CARDS = [
    {
        key: 'totalComplaints',
        label: 'Total Complaints',
        icon: ClipboardList,
        color: 'purple',
    },
    {
        key: 'pending',
        label: 'Pending',
        icon: AlertCircle,
        color: 'orange',
    },
    {
        key: 'inProgress',
        label: 'In Progress',
        icon: Clock,
        color: 'blue',
    },
    {
        key: 'resolved',
        label: 'Resolved',
        icon: CheckCircle2,
        color: 'green',
    },
    {
        key: 'rejected',
        label: 'Rejected',
        icon: XCircle,
        color: 'red',
    },
]

// Colors used in category and floor charts
export const CHART_COLORS = [
    '#6c3df4',
    '#16a36a',
    '#f59e0b',
    '#3b82f6',
    '#ec4899',
    '#8b5cf6',
    '#14b8a6',
    '#f97316',
    '#64748b',
    '#e11d48',
]

// Format numbers using Indian number formatting
export const formatNumber = (value) =>
    Number(value ?? 0).toLocaleString('en-IN')

// Format monthly trend labels (yyyy-MM -> Oct 26)
export const formatMonth = (month) => {
    if (!month) return ''

    const date = new Date(`${month}-01T00:00:00`)

    return date.toLocaleDateString('en-IN', {
        month: 'short',
        year: '2-digit',
    })
}

// Format resolution time in hours
export const formatResolutionTime = (hours) => {
    if (hours == null || !Number.isFinite(Number(hours))) {
        return 'N/A'
    }

    return `${Number(hours).toFixed(1)} hrs`
}

// Calculate percentage safely
export const calculatePercentage = (value, total) => {
    if (!total || total <= 0) return 0

    return (Number(value ?? 0) / Number(total)) * 100
}
