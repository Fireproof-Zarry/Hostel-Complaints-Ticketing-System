import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  AlertCircle,
  ArrowDownToLine,
  BarChart3,
  Bell,
  CheckCircle2,
  ClipboardList,
  Clock3,
  FilePlus2,
  Inbox,
  LogOut,
  Menu,
  Search,
  ShieldCheck,
  UserRound,
  Users,
  Wrench,
} from 'lucide-react'
import ComingSoon from './ComingSoon'
import MyComplaintsPage from '../components/MyComplaintsPage'
import StaffComplaintForm from '../components/StaffComplaintForm'
import StaffManagement from '../components/StaffManagement'
import WorkOrderPanel from '../components/WorkOrderPanel'
import ComplaintTimeline from '../components/ComplaintTimeline'
import AdminAnalytics from './AdminAnalytics/AdminAnalytics'
import ProfileForm from '../components/ProfileForm'
import { apiFetch, logout } from '../auth/session'
import { getRoomHundredsForFloor, isRoomOnFloor } from '../utils/hostelLocation'
import './AdminHome.css'

// Shown in the main area like the other sidebar pages
const PROFILE_VIEW = 'My Profile'
const STAFF_VIEW = 'Staff Management'
const MY_COMPLAINTS_VIEW = 'My Complaints'
const FILE_COMPLAINT_VIEW = 'File Complaint'

// Where a complaint can move next; ComplaintTransitionService enforces the same rules
const STATUS_TRANSITIONS = {
  PENDING: ['ASSIGNED', 'IN_PROGRESS', 'REJECTED'],
  ASSIGNED: ['IN_PROGRESS', 'RESOLVED', 'PENDING', 'REJECTED'],
  IN_PROGRESS: ['RESOLVED', 'ASSIGNED', 'PENDING', 'REJECTED'],
  RESOLVED: ['PENDING', 'IN_PROGRESS'],
  REJECTED: ['PENDING', 'IN_PROGRESS'],
}

// Rejected requests carry their reason as JSON, or as plain text from older endpoints
async function responseError(response, fallback) {
  const message = await response.text()
  try {
    return JSON.parse(message).message || message || fallback
  } catch {
    return message || fallback
  }
}

function filterComplaintsBySearch(complaints, searchTerm) {
  const query = searchTerm.trim().toLowerCase()
  if (!query) return complaints

  const searchWords = query.replace(/#/g, ' ').split(/\s+/).filter(Boolean)
  return complaints.filter((complaint) => {
    const searchableText = [
      complaint.id,
      complaint.title,
      complaint.description,
      complaint.category,
      complaint.student?.email,
      complaint.student?.name,
      complaint.floor,
      complaint.room,
      complaint.assignedTo,
      complaint.status,
    ]
      .filter(Boolean)
      .join(' ')
      .toLowerCase()

    return searchWords.every((word) => searchableText.includes(word))
  })
}

const IST_TIME_ZONE = 'Asia/Kolkata'
const istDateFormatter = new Intl.DateTimeFormat('en-IN', {
  timeZone: IST_TIME_ZONE,
  dateStyle: 'medium',
  timeStyle: 'short',
})
const istCalendarFormatter = new Intl.DateTimeFormat('en-CA', {
  timeZone: IST_TIME_ZONE,
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
})

function parseComplaintDate(value) {
  if (!value) return null

  // The API serializes LocalDateTime without an offset; interpret it as UTC.
  const normalizedValue = /(?:Z|[+-]\d{2}:\d{2})$/i.test(value) ? value : `${value}Z`
  const date = new Date(normalizedValue)
  return Number.isNaN(date.getTime()) ? null : date
}

function formatComplaintDateTime(value) {
  const date = parseComplaintDate(value)
  return date ? istDateFormatter.format(date) : '—'
}

function getIstCalendarDay(date) {
  const parts = Object.fromEntries(
    istCalendarFormatter.formatToParts(date).map(({ type, value }) => [type, value])
  )
  return Date.UTC(Number(parts.year), Number(parts.month) - 1, Number(parts.day))
}

function hasFloorRoomMismatch(floor, room) {
  return getRoomHundredsForFloor(floor) !== null && !isRoomOnFloor(floor, room)
}

export default function AdminHome() {
  const navigate = useNavigate()
  const [sidebarOpen, setSidebarOpen] = useState(true)
  const [complaints, setComplaints] = useState([])
  const [filteredComplaints, setFilteredComplaints] = useState([])
  const [searchTerm, setSearchTerm] = useState('')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [admins, setAdmins] = useState([])
  const [comingSoonPage, setComingSoonPage] = useState(null)
  // ADMIN or STAFF; staff share this dashboard but cannot manage staff and
  // can only assign complaints to themselves
  const [currentUser, setCurrentUser] = useState(null)
  const isAdmin = currentUser?.role === 'ADMIN'

  // Filter values
  const [statusFilter, setStatusFilter] = useState('')
  const [categoryFilter, setCategoryFilter] = useState('')
  const [floorFilter, setFloorFilter] = useState('')
  // '' both, 'false' room issues, 'true' community issues
  const [typeFilter, setTypeFilter] = useState('')
  const [fromDate, setFromDate] = useState('')
  const [toDate, setToDate] = useState('')
  const [assignedToFilter, setAssignedToFilter] = useState('')
  const hasInvalidDateRange = Boolean(fromDate && toDate && toDate < fromDate)

  // Selected complaint for View
  const [selectedComplaint, setSelectedComplaint] = useState(null)
  // Bumped after a work order change so the popup's timeline reloads
  const [timelineVersion, setTimelineVersion] = useState(0)

  // Complaint for status update
  const [statusComplaint, setStatusComplaint] = useState(null)
  const [newStatus, setNewStatus] = useState('')
  const [statusUpdating, setStatusUpdating] = useState(false)
  const [statusError, setStatusError] = useState('')

  // Complaint assignment
  const [assignComplaint, setAssignComplaint] = useState(null)
  const [selectedAdmin, setSelectedAdmin] = useState('')
  const [assignPriority, setAssignPriority] = useState('MEDIUM')
  const [assignDeadline, setAssignDeadline] = useState('')
  // Admins only: email -> { activeWorkOrders, overdueWorkOrders } for the assign list
  const [workload, setWorkload] = useState({})
  const [assignUpdating, setAssignUpdating] = useState(false)
  const [assignError, setAssignError] = useState('')

  // Success toast
  const [successMessage, setSuccessMessage] = useState('')

  const showSuccessMessage = (message) => {
    setSuccessMessage(message)

    setTimeout(() => {
      setSuccessMessage('')
    }, 4000)
  }

  const fetchComplaints = async (filters = {}, query = searchTerm) => {
    try {
      setLoading(true)
      setError('')

      const params = new URLSearchParams()

      if (filters.status) {
        params.append('status', filters.status)
      }

      if (filters.category) {
        params.append('category', filters.category)
      }

      if (filters.floor) {
        params.append('floor', filters.floor)
      }

      if (filters.communityArea) {
        params.append('communityArea', filters.communityArea)
      }

      if (filters.assignedTo) {
        params.append('assignedTo', filters.assignedTo)
      }

      if (filters.from) {
        params.append('from', `${filters.from}T00:00:00`)
      }

      if (filters.to) {
        params.append('to', `${filters.to}T23:59:59.999999999`)
      }

      const queryString = params.toString()

      const response = await apiFetch(
        `${import.meta.env.VITE_API_URL}/admin/complaints${queryString ? `?${queryString}` : ''
        }`
      )

      if (!response.ok) {
        throw new Error(`Failed to fetch complaints: ${response.status}`)
      }

      const data = await response.json()

      setComplaints(data)
      setFilteredComplaints(filterComplaintsBySearch(data, query))

      return true
    } catch (error) {
      console.error('Failed to fetch complaints:', error)
      setError('Failed to load complaints.')

      return false
    } finally {
      setLoading(false)
    }
  }

  const fetchAdmins = async () => {
    try {
      const response = await apiFetch(
        `${import.meta.env.VITE_API_URL}/admin/complaints/admins`
      )

      if (!response.ok) {
        throw new Error(`Failed to fetch admins: ${response.status}`)
      }

      const data = await response.json()
      setAdmins(data)
    } catch (error) {
      console.error('Failed to fetch admins:', error)
    }
  }

  const fetchWorkload = async () => {
    try {
      const response = await apiFetch(`${import.meta.env.VITE_API_URL}/admin/staff`)
      if (!response.ok) return
      const members = await response.json()
      setWorkload(Object.fromEntries(members.map((member) => [member.email, member])))
    } catch (error) {
      console.error('Failed to load staff workload:', error)
    }
  }

  const fetchCurrentUser = async () => {
    try {
      const response = await apiFetch(`${import.meta.env.VITE_API_URL}/auth/me`)
      if (response.ok) setCurrentUser(await response.json())
    } catch (error) {
      console.error('Failed to load the signed-in user:', error)
    }
  }

  useEffect(() => {
    fetchComplaints()
    fetchAdmins()
    fetchCurrentUser()
  }, [])

  const handleSearch = () => {
    setFilteredComplaints(filterComplaintsBySearch(complaints, searchTerm))
  }

  const handleApplyFilters = async () => {
    if (hasInvalidDateRange) return

    const success = await fetchComplaints({
      status: statusFilter,
      category: categoryFilter,
      floor: floorFilter,
      communityArea: typeFilter,
      assignedTo: assignedToFilter,
      from: fromDate,
      to: toDate,
    })

    if (success) {
      showSuccessMessage('Filters applied successfully')
    }
  }

  const handleResetFilters = async () => {
    setStatusFilter('')
    setCategoryFilter('')
    setFloorFilter('')
    setTypeFilter('')
    setFromDate('')
    setToDate('')
    setAssignedToFilter('')
    setSearchTerm('')

    const success = await fetchComplaints({}, '')

    if (success) {
      showSuccessMessage('Filters reset')
    }
  }

  const handleStatusUpdate = async () => {
    if (!statusComplaint || !newStatus) {
      return
    }

    try {
      setStatusUpdating(true)
      setStatusError('')

      const response = await apiFetch(
        `${import.meta.env.VITE_API_URL}/admin/complaints/${statusComplaint.id}/status?status=${encodeURIComponent(newStatus)}`,
        {
          method: 'PATCH',
        }
      )

      if (!response.ok) {
        throw new Error(
          await responseError(response, `Failed to update status: ${response.status}`)
        )
      }

      const updatedComplaint = await response.json()

      setComplaints((currentComplaints) =>
        currentComplaints.map((complaint) =>
          complaint.id === updatedComplaint.id
            ? updatedComplaint
            : complaint
        )
      )

      setFilteredComplaints((currentComplaints) =>
        currentComplaints.map((complaint) =>
          complaint.id === updatedComplaint.id
            ? updatedComplaint
            : complaint
        )
      )

      setStatusComplaint(null)
      setNewStatus('')
      setStatusError('')

      showSuccessMessage('Status updated successfully')
    } catch (error) {
      console.error('Failed to update complaint status:', error)

      setStatusError(
        error.message || 'Failed to update complaint status.'
      )
    } finally {
      setStatusUpdating(false)
    }
  }

  const handleAssignComplaint = async () => {
    if (!assignComplaint || !selectedAdmin) {
      return
    }

    try {
      setAssignUpdating(true)
      setAssignError('')

      const params = new URLSearchParams({ email: selectedAdmin, priority: assignPriority })
      if (assignDeadline) {
        // datetime-local has no seconds
        params.append('deadline', `${assignDeadline}:00`)
      }

      const response = await apiFetch(
        `${import.meta.env.VITE_API_URL}/admin/complaints/${assignComplaint.id}/assign?${params}`,
        {
          method: 'PATCH',
        }
      )

      if (!response.ok) {
        throw new Error(
          await responseError(response, `Failed to assign complaint: ${response.status}`)
        )
      }

      const updatedComplaint = await response.json()

      setComplaints((currentComplaints) =>
        currentComplaints.map((complaint) =>
          complaint.id === updatedComplaint.id
            ? updatedComplaint
            : complaint
        )
      )

      setFilteredComplaints((currentComplaints) =>
        currentComplaints.map((complaint) =>
          complaint.id === updatedComplaint.id
            ? updatedComplaint
            : complaint
        )
      )

      setAssignComplaint(null)
      setSelectedAdmin('')
      setAssignError('')

      showSuccessMessage('Complaint assigned successfully')
    } catch (error) {
      console.error('Failed to assign complaint:', error)

      setAssignError(
        error.message || 'Failed to assign complaint.'
      )
    } finally {
      setAssignUpdating(false)
    }
  }

  const getTimeSinceRaised = (createdAt) => {
    const createdDate = parseComplaintDate(createdAt)
    if (!createdDate) return ''

    const differenceDays = Math.floor(
      (getIstCalendarDay(new Date()) - getIstCalendarDay(createdDate)) / 86_400_000
    )

    if (differenceDays === 0) {
      return 'Raised today'
    }

    if (differenceDays === 1) {
      return 'Raised 1 day ago'
    }

    return `Raised ${differenceDays} days ago`
  }

  const assignedToUsers = [
    ...new Set(
      [
        ...admins.map((admin) => admin.email),
        ...complaints.map((complaint) => complaint.assignedTo),
      ]
        .filter(Boolean)
    ),
  ]

  const statusCounts = {
    total: complaints.length,
    pending: complaints.filter((complaint) => complaint.status === 'PENDING').length,
    inProgress: complaints.filter((complaint) => complaint.status === 'IN_PROGRESS').length,
    resolved: complaints.filter((complaint) => complaint.status === 'RESOLVED').length,
  }

  const handleLogout = () => {
    logout()
    navigate('/login', { replace: true })
  }

  const handleQuickStatusFilter = async (status) => {
    setStatusFilter(status)
    await fetchComplaints({
      status,
      category: categoryFilter,
      floor: floorFilter,
      communityArea: typeFilter,
      assignedTo: assignedToFilter,
      from: fromDate,
      to: toDate,
    })
  }

  // From My complaints; the row may be hidden by the main list's filters
  const handleOpenComplaint = async (id) => {
    const response = await apiFetch(`${import.meta.env.VITE_API_URL}/admin/complaints/${id}`)
    if (!response.ok) {
      throw new Error('This complaint could not be opened.')
    }
    setSelectedComplaint(await response.json())
  }

  const handleRefreshComplaints = () => fetchComplaints({
    status: statusFilter,
    category: categoryFilter,
    floor: floorFilter,
    communityArea: typeFilter,
    assignedTo: assignedToFilter,
    from: fromDate,
    to: toDate,
  })

  return (
    <div className={`admin-shell ${sidebarOpen ? '' : 'admin-shell-collapsed'}`}>
      <aside className="admin-sidebar">
        <div className="admin-brand">
          <span className="admin-brand-icon"><Wrench size={21} /></span>
          {sidebarOpen && (
            <span className="admin-brand-copy">
              <strong>HostelFix</strong>
              <small>ADMIN CONSOLE</small>
            </span>
          )}
        </div>

        <p className="admin-nav-label">{sidebarOpen ? 'WORKSPACE' : 'NAV'}</p>
        <nav className="admin-nav" aria-label="Admin navigation">
          <button
            type="button"
            className={`admin-nav-item ${comingSoonPage === null ? 'active' : ''}`}
            onClick={() => {
              setComingSoonPage(null)
              setError('')
            }}
            title="Complaints"
          >
            <ClipboardList size={19} />
            {sidebarOpen && <span>Complaints</span>}
            {sidebarOpen && <span className="admin-nav-count">{statusCounts.total}</span>}
          </button>

          <button
            type="button"
            className={`admin-nav-item ${comingSoonPage === MY_COMPLAINTS_VIEW ? 'active' : ''}`}
            onClick={() => setComingSoonPage(MY_COMPLAINTS_VIEW)}
            title="My complaints"
          >
            <Inbox size={19} />
            {sidebarOpen && <span>My complaints</span>}
          </button>

          <button
            type="button"
            className={`admin-nav-item ${comingSoonPage === FILE_COMPLAINT_VIEW ? 'active' : ''}`}
            onClick={() => setComingSoonPage(FILE_COMPLAINT_VIEW)}
            title="File complaint"
          >
            <FilePlus2 size={19} />
            {sidebarOpen && <span>File complaint</span>}
          </button>

          {/* Analytics navigation */}
          <button
            type="button"
            className={`admin-nav-item ${comingSoonPage === 'Analytics' ? 'active' : ''}`}
            onClick={() => setComingSoonPage('Analytics')}
            title="Analytics"
          >
            <BarChart3 size={19} />
            {sidebarOpen && <span>Analytics</span>}
          </button>

          {isAdmin && (
            <button
              type="button"
              className={`admin-nav-item ${comingSoonPage === STAFF_VIEW ? 'active' : ''}`}
              onClick={() => setComingSoonPage(STAFF_VIEW)}
              title="Staff management"
            >
              <Users size={19} />
              {sidebarOpen && <span>Staff management</span>}
            </button>
          )}
        </nav>

        <div className="admin-sidebar-bottom">
          <button
            type="button"
            className={`admin-nav-item ${comingSoonPage === PROFILE_VIEW ? 'active' : ''}`}
            onClick={() => setComingSoonPage(PROFILE_VIEW)}
            title="My profile"
          >
            <UserRound size={19} />
            {sidebarOpen && <span>My profile</span>}
          </button>

          <div className="admin-user-card">
            <span className="admin-user-avatar"><ShieldCheck size={18} /></span>
            {sidebarOpen && (
              <span className="admin-user-copy">
                <strong>{currentUser?.role === 'STAFF' ? 'Hostel Staff' : 'Administrator'}</strong>
                <small>{currentUser?.fullName || currentUser?.name || 'Hostel support team'}</small>
              </span>
            )}
          </div>

          <button
            type="button"
            className="admin-nav-item admin-logout"
            onClick={handleLogout}
            title="Log out"
          >
            <LogOut size={18} />
            {sidebarOpen && <span>Log out</span>}
          </button>
        </div>
      </aside>

      <main className="admin-main">
        <header className="admin-header">
          <button
            type="button"
            className="admin-menu-button"
            aria-label={sidebarOpen ? 'Collapse sidebar' : 'Expand sidebar'}
            onClick={() => setSidebarOpen((open) => !open)}
          >
            <Menu size={21} />
          </button>

          <div className="admin-header-title">
            <span>HOSTELFIX / {comingSoonPage ? comingSoonPage.toUpperCase() : 'COMPLAINTS'}</span>
            <h1>{comingSoonPage || 'Complaints'}</h1>
          </div>

          <div className="admin-header-actions">
            <button
              type="button"
              className="admin-header-icon"
              aria-label="Notifications (work in progress)"
              title="Notifications are a work in progress"
              onClick={() => setComingSoonPage('Notifications')}
            >
              <Bell size={19} />
              <i />
            </button>
          </div>
        </header>

        <section className="admin-content">
          {comingSoonPage === 'Analytics' ? (
            <AdminAnalytics />
          ) : comingSoonPage === PROFILE_VIEW ? (
            <ProfileForm onSaved={(saved) => setCurrentUser((user) => ({ ...user, fullName: saved.fullName }))} />
          ) : comingSoonPage === STAFF_VIEW && isAdmin ? (
            <StaffManagement />
          ) : comingSoonPage === MY_COMPLAINTS_VIEW ? (
            <MyComplaintsPage onOpen={handleOpenComplaint} refreshKey={timelineVersion} />
          ) : comingSoonPage === FILE_COMPLAINT_VIEW ? (
            <StaffComplaintForm onFiled={handleRefreshComplaints} />
          ) : comingSoonPage ? (
            <div className="admin-work-in-progress">
              <ComingSoon title={comingSoonPage} />
              <p>
                This section is under development. Complaint management remains
                available in the Complaints workspace.
              </p>
              <button
                type="button"
                className="admin-primary-button"
                onClick={() => setComingSoonPage(null)}
              >
                <ClipboardList size={17} /> Go to complaints
              </button>
            </div>
          ) : (
            <>
              <div className="admin-page-heading">
                <div>
                  <p className="admin-eyebrow">OPERATIONS OVERVIEW</p>
                  <h2>Complaint management</h2>
                  <p>Review requests, apply filters, and keep hostel issues moving.</p>
                </div>

                <button
                  type="button"
                  className="admin-secondary-button"
                  onClick={handleRefreshComplaints}
                >
                  <ArrowDownToLine size={17} /> Refresh data
                </button>
              </div>

              <div className="admin-summary-grid">
                <button
                  type="button"
                  className="admin-summary-card"
                  onClick={() => handleQuickStatusFilter('')}
                >
                  <span className="admin-summary-icon summary-indigo">
                    <ClipboardList size={19} />
                  </span>
                  <span className="admin-summary-copy">
                    <small>Total complaints</small>
                    <strong>{loading ? '—' : statusCounts.total}</strong>
                  </span>
                  <span className="admin-summary-caption">All requests</span>
                </button>

                <button
                  type="button"
                  className="admin-summary-card"
                  onClick={() => handleQuickStatusFilter('PENDING')}
                >
                  <span className="admin-summary-icon summary-amber">
                    <AlertCircle size={19} />
                  </span>
                  <span className="admin-summary-copy">
                    <small>Pending</small>
                    <strong>{loading ? '—' : statusCounts.pending}</strong>
                  </span>
                  <span className="admin-summary-caption">Needs review</span>
                </button>

                <button
                  type="button"
                  className="admin-summary-card"
                  onClick={() => handleQuickStatusFilter('IN_PROGRESS')}
                >
                  <span className="admin-summary-icon summary-blue">
                    <Clock3 size={19} />
                  </span>
                  <span className="admin-summary-copy">
                    <small>In progress</small>
                    <strong>{loading ? '—' : statusCounts.inProgress}</strong>
                  </span>
                  <span className="admin-summary-caption">Being handled</span>
                </button>

                <button
                  type="button"
                  className="admin-summary-card"
                  onClick={() => handleQuickStatusFilter('RESOLVED')}
                >
                  <span className="admin-summary-icon summary-green">
                    <CheckCircle2 size={19} />
                  </span>
                  <span className="admin-summary-copy">
                    <small>Resolved</small>
                    <strong>{loading ? '—' : statusCounts.resolved}</strong>
                  </span>
                  <span className="admin-summary-caption">Completed</span>
                </button>
              </div>

              <div className="admin-workspace-card">
                <div className="admin-workspace-heading">
                  <div>
                    <h3>All complaints</h3>
                    <p>Search and filter submitted hostel requests.</p>
                  </div>
                  <span className="admin-result-count">
                    {loading ? 'Loading…' : `${filteredComplaints.length} results`}
                  </span>
                </div>

                <div className="admin-search-row">
                  <label className="admin-search-box">
                    <Search size={18} />
                    <input
                      type="search"
                      placeholder="Search title, student, room, or ID…"
                      value={searchTerm}
                      onChange={(event) => setSearchTerm(event.target.value)}
                      onKeyDown={(event) => {
                        if (event.key === 'Enter') handleSearch()
                      }}
                    />
                  </label>

                  <button
                    type="button"
                    className="admin-primary-button"
                    onClick={handleSearch}
                  >
                    Search
                  </button>
                </div>

                <div className="admin-filter-grid">
                  <label className="admin-filter-control">
                    <span>Status</span>
                    <select
                      value={statusFilter}
                      onChange={(event) => setStatusFilter(event.target.value)}
                    >
                      <option value="">All statuses</option>
                      <option value="PENDING">Pending</option>
                      <option value="ASSIGNED">Assigned</option>
                      <option value="IN_PROGRESS">In progress</option>
                      <option value="RESOLVED">Resolved</option>
                      <option value="REJECTED">Rejected</option>
                    </select>
                  </label>

                  <label className="admin-filter-control">
                    <span>Category</span>
                    <select
                      value={categoryFilter}
                      onChange={(event) => setCategoryFilter(event.target.value)}
                    >
                      <option value="">All categories</option>
                      <option value="Electrical">Electrical</option>
                      <option value="Plumbing">Plumbing</option>
                      <option value="Carpentry">Carpentry</option>
                      <option value="Cleaning">Cleaning</option>
                      <option value="IT">IT/Network</option>
                    </select>
                  </label>

                  <label className="admin-filter-control">
                    <span>Floor</span>
                    <select
                      value={floorFilter}
                      onChange={(event) => setFloorFilter(event.target.value)}
                    >
                      <option value="">All floors</option>
                      <option value="Ground">Ground</option>
                      <option value="First">First</option>
                      <option value="Second">Second</option>
                      <option value="Third">Third</option>
                      <option value="Fourth">Fourth</option>
                      <option value="Fifth">Fifth</option>
                      <option value="Sixth">Sixth</option>
                    </select>
                  </label>

                  <label className="admin-filter-control">
                    <span>Type</span>
                    <select
                      value={typeFilter}
                      onChange={(event) => setTypeFilter(event.target.value)}
                    >
                      <option value="">All types</option>
                      <option value="false">Room issues</option>
                      <option value="true">Community issues</option>
                    </select>
                  </label>

                  <label className="admin-filter-control">
                    <span>In-charge</span>
                    <select
                      value={assignedToFilter}
                      onChange={(event) => setAssignedToFilter(event.target.value)}
                    >
                      <option value="">All staff</option>
                      {assignedToUsers.map((email) => (
                        <option key={email} value={email}>{email}</option>
                      ))}
                    </select>
                  </label>

                  <label className="admin-filter-control">
                    <span>From date</span>
                    <input
                      type="date"
                      value={fromDate}
                      onChange={(event) => setFromDate(event.target.value)}
                    />
                  </label>

                  <label className="admin-filter-control">
                    <span>To date</span>
                    <input
                      type="date"
                      value={toDate}
                      onChange={(event) => setToDate(event.target.value)}
                      aria-invalid={hasInvalidDateRange}
                      aria-describedby={hasInvalidDateRange ? 'admin-date-range-notice' : undefined}
                    />
                  </label>
                </div>

                {hasInvalidDateRange && (
                  <p
                    id="admin-date-range-notice"
                    className="admin-date-range-notice"
                    role="alert"
                  >
                    “To date” must be the same as or later than “From date”.
                  </p>
                )}

                <div className="admin-filter-actions">
                  <button
                    type="button"
                    className="admin-secondary-button"
                    onClick={handleResetFilters}
                  >
                    Reset filters
                  </button>

                  <button
                    type="button"
                    className="admin-primary-button"
                    onClick={handleApplyFilters}
                    disabled={hasInvalidDateRange}
                  >
                    Apply filters
                  </button>
                </div>
              </div>

              <div className="admin-complaints-card">
                <div className="admin-complaints-heading">
                  <div>
                    <h3>Complaint records</h3>
                    <p>
                      Open a complaint to review its details or manage its status
                      and assignment.
                    </p>
                  </div>
                  <span>
                    <BarChart3 size={17} /> {filteredComplaints.length} shown
                  </span>
                </div>

                {loading && (
                  <div className="table-placeholder admin-table-state">
                    <p>Loading complaints...</p>
                  </div>
                )}

                {!loading && error && (
                  <div className="table-placeholder admin-table-state">
                    <p>{error}</p>
                  </div>
                )}

                {!loading && !error && filteredComplaints.length === 0 && (
                  <div className="table-placeholder admin-table-state">
                    <p>No complaints found.</p>
                  </div>
                )}

                {!loading && !error && filteredComplaints.length > 0 && (
                  <div className="complaints-table-wrapper admin-table-wrapper">
                    <table className="complaints-table">
                      <thead>
                        <tr>
                          <th>Complaint No.</th>
                          <th>Title</th>
                          <th>Category</th>
                          <th>Email</th>
                          <th>Floor</th>
                          <th>Room No.</th>
                          <th>Status</th>
                          <th>Assigned To</th>
                          <th>Action</th>
                        </tr>
                      </thead>

                      <tbody>
                        {filteredComplaints.map((complaint) => (
                          <tr key={complaint.id}>
                            <td>#{complaint.id}</td>

                            <td>
                              <strong
                                className="complaint-title"
                                onClick={() => setSelectedComplaint(complaint)}
                                role="button"
                                tabIndex={0}
                                onKeyDown={(event) => {
                                  if (event.key === 'Enter' || event.key === ' ') {
                                    setSelectedComplaint(complaint)
                                  }
                                }}
                              >
                                {complaint.title}
                              </strong>
                            </td>

                            <td>{complaint.category}</td>

                            <td>{complaint.student?.email || 'No email'}</td>

                            <td>
                              <span>{complaint.floor || '—'}</span>
                              {complaint.room && hasFloorRoomMismatch(complaint.floor, complaint.room) && (
                                <span
                                  className="admin-location-warning"
                                  title="The room number does not match the selected floor."
                                >
                                  Check location
                                </span>
                              )}
                            </td>

                            <td>
                              {complaint.communityArea
                                ? complaint.wing || 'Community area'
                                : complaint.room}
                            </td>

                            <td>
                              <span className={`status-badge status-${complaint.status?.toLowerCase()}`}>
                                {complaint.status}
                              </span>
                            </td>

                            <td>{complaint.assignedTo || 'Unassigned'}</td>

                            <td>
                              <div className="action-buttons">
                                <button
                                  type="button"
                                  className="table-action-button"
                                  onClick={() => setSelectedComplaint(complaint)}
                                >
                                  View
                                </button>

                                <button
                                  className="table-action-button"
                                  onClick={() => {
                                    setAssignComplaint(complaint)
                                    setSelectedAdmin(isAdmin ? complaint.assignedTo || '' : currentUser?.email || '')
                                    setAssignPriority('MEDIUM')
                                    setAssignDeadline('')
                                    setAssignError('')
                                    if (isAdmin) fetchWorkload()
                                  }}
                                >
                                  Assign
                                </button>

                                <button
                                  type="button"
                                  className="table-action-button"
                                  onClick={() => {
                                    setStatusComplaint(complaint)
                                    setNewStatus('')
                                    setStatusError('')
                                  }}
                                >
                                  Status
                                </button>
                              </div>
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                )}
              </div>
            </>
          )}
        </section>
      </main>

      {/* Complaint Details Modal */}
      {selectedComplaint && (
        <div
          className="complaint-modal-overlay"
          onClick={() => setSelectedComplaint(null)}
        >
          <div
            className="complaint-modal"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="complaint-modal-header">
              <div>
                <h2>Complaint Details</h2>

                <div className="complaint-meta">
                  <span>#{selectedComplaint.id}</span>
                  <span>•</span>
                  <span>{selectedComplaint.title}</span>
                  <span>•</span>
                  <span>{getTimeSinceRaised(selectedComplaint.createdAt)}</span>
                </div>
              </div>

              <button
                type="button"
                className="complaint-modal-close"
                onClick={() => setSelectedComplaint(null)}
              >
                ×
              </button>
            </div>

            <div className="complaint-details">
              <div className="detail-item detail-item-full complaint-title-detail">
                <span className="detail-label">Title</span>
                <span className="detail-title-value">
                  {selectedComplaint.title || '—'}
                </span>
              </div>

              <div className="detail-item">
                <span className="detail-label">Category</span>
                <span className="detail-value detail-value-large">
                  {selectedComplaint.category || '—'}
                </span>
              </div>

              <div className="detail-item">
                <span className="detail-label">Status</span>
                <span className="detail-value">
                  <span className={`status-badge status-${selectedComplaint.status?.toLowerCase()}`}>
                    {selectedComplaint.status || '—'}
                  </span>
                </span>
              </div>

              <div className="detail-item">
                <span className="detail-label">Reporter Email</span>
                <span className="detail-value detail-value-large">
                  {selectedComplaint.student?.email || 'No email'}
                </span>
              </div>

              <div className="detail-item">
                <span className="detail-label">Assigned To</span>
                <span className="detail-value detail-value-large">
                  {selectedComplaint.assignedTo || 'Unassigned'}
                </span>
              </div>

              <div className="detail-item">
                <span className="detail-label">Floor</span>
                <span className="detail-value detail-value-large">
                  {selectedComplaint.floor || '—'}
                </span>
              </div>

              <div className="detail-item">
                <span className="detail-label">
                  {selectedComplaint.communityArea ? 'Community area' : 'Room No.'}
                </span>
                <span className="detail-value detail-value-large">
                  {(selectedComplaint.communityArea
                    ? selectedComplaint.wing
                    : selectedComplaint.room) || '—'}
                </span>
              </div>

              <div className="detail-item">
                <span className="detail-label">Reported By</span>
                <span className="detail-value">
                  {selectedComplaint.student?.fullName ||
                    selectedComplaint.student?.name ||
                    '—'}
                  {selectedComplaint.student?.rollNumber &&
                    ` (${selectedComplaint.student.rollNumber})`}
                </span>
              </div>

              <div className="detail-item">
                <span className="detail-label">Reporter Phone</span>
                <span className="detail-value">
                  {selectedComplaint.student?.phoneNumber || 'Not provided'}
                </span>
              </div>

              <div className="detail-item">
                <span className="detail-label">Available Time</span>
                <span className="detail-value">
                  {selectedComplaint.availableTimeSlot || 'Not specified'}
                </span>
              </div>

              <div className="detail-item">
                <span className="detail-label">Room Key</span>
                <span className="detail-value">
                  {selectedComplaint.communityArea
                    ? 'Not applicable'
                    : selectedComplaint.keyWithSecurity
                      ? 'Left with security'
                      : 'With student'}
                </span>
              </div>

              <div className="detail-item detail-item-full">
                <span className="detail-label">Description</span>
                <p className="detail-description">
                  {selectedComplaint.description || 'No description provided.'}
                </p>
              </div>

              <div className="detail-item detail-item-full">
                <span className="detail-label">Photo</span>

                {selectedComplaint.imageUrl ? (
                  <a
                    href={selectedComplaint.imageUrl}
                    target="_blank"
                    rel="noreferrer"
                    className="detail-photo-link"
                    title="Open full-size photo"
                  >
                    <img
                      src={selectedComplaint.imageUrl}
                      alt={`Photo attached to complaint #${selectedComplaint.id}`}
                      className="detail-photo"
                    />
                  </a>
                ) : (
                  <span className="detail-value">No photo attached</span>
                )}
              </div>

              <div className="detail-item">
                <span className="detail-label">Created At</span>
                <span className="detail-value">
                  {formatComplaintDateTime(selectedComplaint.createdAt)}
                </span>
              </div>

              <div className="detail-item">
                <span className="detail-label">Updated At</span>
                <span className="detail-value">
                  {formatComplaintDateTime(selectedComplaint.updatedAt)}
                </span>
              </div>

              <div className="detail-item detail-item-full">
                <div className="grid gap-6 md:grid-cols-2">
                  <div>
                    <span className="detail-label">Timeline</span>
                    <div className="mt-2">
                      <ComplaintTimeline
                        complaintId={selectedComplaint.id}
                        createdAt={selectedComplaint.createdAt}
                        refreshKey={timelineVersion}
                      />
                    </div>
                  </div>

                  <div>
                    <span className="detail-label">Work Order Details</span>
                    <div className="mt-2">
                      <WorkOrderPanel
                        key={selectedComplaint.id}
                        complaintId={selectedComplaint.id}
                        currentUser={currentUser}
                        onComplaintChanged={(workOrder) => {
                          setSelectedComplaint((complaint) => complaint && {
                            ...complaint,
                            status: workOrder.complaintStatus,
                            assignedTo: workOrder.status === 'CANCELLED' ? null : complaint.assignedTo,
                          })
                          setTimelineVersion((version) => version + 1)
                          handleRefreshComplaints()
                        }}
                      />
                    </div>
                  </div>
                </div>
              </div>

            </div>

            <div className="complaint-modal-footer">
              <button
                type="button"
                className="reset-button"
                onClick={() => setSelectedComplaint(null)}
              >
                Close
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Status Update Modal */}
      {statusComplaint && (
        <div
          className="complaint-modal-overlay"
          onClick={() => {
            if (!statusUpdating) {
              setStatusComplaint(null)
              setStatusError('')
            }
          }}
        >
          <div
            className="complaint-modal status-modal"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="complaint-modal-header">
              <div>
                <h2>Update Status</h2>

                <div className="complaint-meta">
                  <span>#{statusComplaint.id}</span>
                  <span>•</span>
                  <span>{statusComplaint.title}</span>
                </div>
              </div>

              <button
                type="button"
                className="complaint-modal-close"
                onClick={() => {
                  if (!statusUpdating) {
                    setStatusComplaint(null)
                    setStatusError('')
                  }
                }}
                disabled={statusUpdating}
              >
                ×
              </button>
            </div>

            <div className="complaint-details">
              <div className="detail-item detail-item-full">
                <span className="detail-label">Current Status</span>
                <span className="detail-value">
                  <span className={`status-badge status-${statusComplaint.status?.toLowerCase()}`}>
                    {statusComplaint.status}
                  </span>
                </span>
              </div>

              <div className="detail-item detail-item-full">
                <span className="detail-label">New Status</span>

                <div className="status-options">
                  {(STATUS_TRANSITIONS[statusComplaint.status] ?? []).map((status) => (
                    <button
                      key={status}
                      type="button"
                      className={`status-option ${newStatus === status ? 'selected' : ''}`}
                      onClick={() => {
                        setNewStatus(status)
                        setStatusError('')
                      }}
                      disabled={statusUpdating}
                    >
                      {status}
                    </button>
                  ))}
                </div>
              </div>

              {statusError && (
                <div className="detail-item detail-item-full">
                  <p className="status-error">{statusError}</p>
                </div>
              )}
            </div>

            <div className="complaint-modal-footer">
              <button
                type="button"
                className="reset-button"
                onClick={() => {
                  if (!statusUpdating) {
                    setStatusComplaint(null)
                    setStatusError('')
                  }
                }}
                disabled={statusUpdating}
              >
                Cancel
              </button>

              <button
                type="button"
                className="apply-button"
                onClick={handleStatusUpdate}
                disabled={statusUpdating || !newStatus}
              >
                {statusUpdating ? 'Updating...' : 'Update Status'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Assign Complaint Modal */}
      {assignComplaint && (
        <div
          className="complaint-modal-overlay"
          onClick={() => {
            if (!assignUpdating) {
              setAssignComplaint(null)
              setSelectedAdmin('')
              setAssignError('')
            }
          }}
        >
          <div
            className="complaint-modal assign-modal"
            onClick={(event) => event.stopPropagation()}
          >
            <div className="complaint-modal-header">
              <div>
                <h2>Assign Complaint</h2>
                <div className="complaint-meta">
                  Complaint #{assignComplaint.id}
                </div>
              </div>

              <button
                className="complaint-modal-close"
                onClick={() => {
                  if (!assignUpdating) {
                    setAssignComplaint(null)
                    setSelectedAdmin('')
                    setAssignError('')
                  }
                }}
                disabled={assignUpdating}
              >
                ×
              </button>
            </div>

            <div className="complaint-details">
              <div className="detail-item detail-item-full">
                <span className="detail-label">Current Assignee</span>
                <span className="detail-value">
                  {assignComplaint.assignedTo || 'Unassigned'}
                </span>
              </div>

              <div className="detail-item detail-item-full">
                <span className="detail-label">Assign To</span>

                <div className="admin-options">
                  {(isAdmin ? admins : admins.filter((admin) => admin.email === currentUser?.email)).map((admin) => (
                    <button
                      key={admin.email}
                      type="button"
                      className={`admin-option ${selectedAdmin === admin.email ? 'selected' : ''}`}
                      onClick={() => setSelectedAdmin(admin.email)}
                      disabled={assignUpdating}
                    >
                      <div className="admin-option-content">
                        <span>{admin.name || admin.email}</span>
                        <span className="admin-email">
                          {admin.email}
                          {workload[admin.email] && (
                            <>
                              {` · ${workload[admin.email].activeWorkOrders} active`}
                              {workload[admin.email].overdueWorkOrders > 0 && (
                                <span className="font-semibold text-rose-700">
                                  {` · ${workload[admin.email].overdueWorkOrders} overdue`}
                                </span>
                              )}
                            </>
                          )}
                        </span>
                      </div>

                      {selectedAdmin === admin.email && (
                        <span className="admin-option-check">✓</span>
                      )}
                    </button>
                  ))}
                </div>
              </div>

              <div className="detail-item">
                <label className="detail-label" htmlFor="assign-priority">
                  Priority
                </label>

                <select
                  id="assign-priority"
                  value={assignPriority}
                  onChange={(event) => setAssignPriority(event.target.value)}
                  disabled={assignUpdating}
                  className="rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm"
                >
                  <option value="LOW">Low</option>
                  <option value="MEDIUM">Medium</option>
                  <option value="HIGH">High</option>
                  <option value="URGENT">Urgent</option>
                </select>
              </div>

              <div className="detail-item">
                <label className="detail-label" htmlFor="assign-deadline">
                  Deadline (optional)
                </label>

                <input
                  id="assign-deadline"
                  type="datetime-local"
                  value={assignDeadline}
                  onChange={(event) => setAssignDeadline(event.target.value)}
                  disabled={assignUpdating}
                  className="rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm"
                />
              </div>

              {assignError && (
                <p className="status-error detail-item-full">
                  {assignError}
                </p>
              )}
            </div>

            <div className="complaint-modal-footer">
              <button
                className="reset-button"
                onClick={() => {
                  setAssignComplaint(null)
                  setSelectedAdmin('')
                  setAssignError('')
                }}
                disabled={assignUpdating}
              >
                Cancel
              </button>

              <button
                className="apply-button"
                onClick={handleAssignComplaint}
                disabled={assignUpdating || !selectedAdmin}
              >
                {assignUpdating ? 'Assigning...' : 'Assign Complaint'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Success Toast */}
      {successMessage && (
        <div className="success-toast">
          <span className="success-toast-icon">✓</span>
          <span>{successMessage}</span>
        </div>
      )}
    </div>
  )
}