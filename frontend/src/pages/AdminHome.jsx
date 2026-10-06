import { useEffect, useState } from 'react'

export default function AdminHome() {
  const [sidebarOpen, setSidebarOpen] = useState(true)
  const [complaints, setComplaints] = useState([])
  const [filteredComplaints, setFilteredComplaints] = useState([])
  const [searchTerm, setSearchTerm] = useState('')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  // Filter values
  const [statusFilter, setStatusFilter] = useState('')
  const [categoryFilter, setCategoryFilter] = useState('')
  const [floorFilter, setFloorFilter] = useState('')
  const [fromDate, setFromDate] = useState('')
  const [toDate, setToDate] = useState('')
  const [assignedToFilter, setAssignedToFilter] = useState('')

  // Selected complaint for View
  const [selectedComplaint, setSelectedComplaint] = useState(null)

  const fetchComplaints = async (filters = {}) => {
    const idToken = localStorage.getItem('idToken')

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

      if (filters.assignedTo) {
        params.append('assignedTo', filters.assignedTo)
      }

      if (filters.from) {
        params.append('from', `${filters.from}T00:00:00`)
      }

      if (filters.to) {
        params.append('to', `${filters.to}T23:59:59`)
      }

      const queryString = params.toString()

      const response = await fetch(
        `${import.meta.env.VITE_API_URL}/admin/complaints${queryString ? `?${queryString}` : ''
        }`,
        {
          headers: {
            Authorization: `Bearer ${idToken}`,
          },
        }
      )

      if (!response.ok) {
        throw new Error(`Failed to fetch complaints: ${response.status}`)
      }

      const data = await response.json()

      setComplaints(data)
      setFilteredComplaints(data)
    } catch (error) {
      console.error('Failed to fetch complaints:', error)
      setError('Failed to load complaints.')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    fetchComplaints()
  }, [])

  const handleSearch = () => {
    const query = searchTerm.trim().toLowerCase()

    if (!query) {
      setFilteredComplaints(complaints)
      return
    }

    const filtered = complaints.filter((complaint) => {
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

      const searchWords = query
        .replace(/#/g, ' ')
        .split(/\s+/)
        .filter(Boolean)

      return searchWords.every((word) => searchableText.includes(word))
    })

    setFilteredComplaints(filtered)
  }

  const handleApplyFilters = () => {
    fetchComplaints({
      status: statusFilter,
      category: categoryFilter,
      floor: floorFilter,
      assignedTo: assignedToFilter,
      from: fromDate,
      to: toDate,
    })
  }

  const handleResetFilters = () => {
    setStatusFilter('')
    setCategoryFilter('')
    setFloorFilter('')
    setFromDate('')
    setToDate('')
    setAssignedToFilter('')

    fetchComplaints()
  }

  const getTimeSinceRaised = (createdAt) => {
    if (!createdAt) {
      return ''
    }

    const createdDate = new Date(createdAt)
    const today = new Date()

    const createdDay = new Date(
      createdDate.getFullYear(),
      createdDate.getMonth(),
      createdDate.getDate()
    )

    const todayDay = new Date(
      today.getFullYear(),
      today.getMonth(),
      today.getDate()
    )

    const differenceDays = Math.floor(
      (todayDay - createdDay) / (1000 * 60 * 60 * 24)
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
      complaints
        .map((complaint) => complaint.assignedTo)
        .filter(Boolean)
    ),
  ]

  return (
    <div className="admin-layout">

      {/* Sidebar */}
      <aside className={`admin-sidebar ${sidebarOpen ? 'open' : 'closed'}`}>

        <div className="sidebar-header">
          <h2>Hostel Complaints</h2>
        </div>

        <nav className="sidebar-nav">

          <button className="nav-item active">
            <span>⌂</span>
            {sidebarOpen && <span>Admin Home</span>}
          </button>

          <button className="nav-item">
            <span>▦</span>
            {sidebarOpen && <span>Analytics</span>}
          </button>

          <button className="nav-item">
            <span>◷</span>
            {sidebarOpen && <span>History</span>}
          </button>

        </nav>

        <div className="sidebar-bottom">
          <button className="nav-item">
            <span>◉</span>
            {sidebarOpen && <span>Profile</span>}
          </button>
        </div>

      </aside>

      {/* Main Area */}
      <main className="admin-main">

        {/* Header */}
        <header className="admin-header">

          <button
            className="menu-button"
            onClick={() => setSidebarOpen(!sidebarOpen)}
          >
            ☰
          </button>

          <h1>Admin Home</h1>

          <div className="profile-button">
            ◉
          </div>

        </header>

        {/* Content */}
        <section className="admin-content">

          {/* Search */}
          <div className="search-section">

            <input
              type="text"
              placeholder="Search complaints..."
              className="search-input"
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
            />

            <button
              className="search-button"
              onClick={handleSearch}
            >
              Search
            </button>

          </div>

          {/* Filters */}
          <div className="filters-section">

            <div className="filter-dropdown">
              <label>Status</label>

              <select
                value={statusFilter}
                onChange={(e) => setStatusFilter(e.target.value)}
              >
                <option value="">All</option>
                <option value="PENDING">PENDING</option>
                <option value="ASSIGNED">ASSIGNED</option>
                <option value="IN_PROGRESS">IN_PROGRESS</option>
                <option value="RESOLVED">RESOLVED</option>
                <option value="REJECTED">REJECTED</option>
              </select>
            </div>

            <div className="filter-dropdown">
              <label>Category</label>

              <select
                value={categoryFilter}
                onChange={(e) => setCategoryFilter(e.target.value)}
              >
                <option value="">All</option>
                <option value="Electrical">Electrical</option>
                <option value="Plumbing">Plumbing</option>
                <option value="Carpentry">Carpentry</option>
                <option value="Cleaning">Cleaning</option>
                <option value="IT">IT/Network</option>
              </select>
            </div>

            <div className="filter-dropdown">
              <label>Floor</label>

              <select
                value={floorFilter}
                onChange={(e) => setFloorFilter(e.target.value)}
              >
                <option value="">All</option>
                <option value="ground">Ground</option>
                <option value="first">First</option>
                <option value="second">Second</option>
                <option value="third">Third</option>
                <option value="fourth">Fourth</option>
                <option value="fifth">Fifth</option>
                <option value="sixth">Sixth</option>
              </select>
            </div>

            <div className="date-filter">
              <label>From</label>

              <input
                type="date"
                value={fromDate}
                onChange={(e) => setFromDate(e.target.value)}
              />
            </div>

            <div className="date-filter">
              <label>To</label>

              <input
                type="date"
                value={toDate}
                onChange={(e) => setToDate(e.target.value)}
              />
            </div>

            <div className="filter-dropdown">
              <label>In-charge</label>

              <select
                value={assignedToFilter}
                onChange={(e) => setAssignedToFilter(e.target.value)}
              >
                <option value="">All</option>

                {assignedToUsers.map((email) => (
                  <option key={email} value={email}>
                    {email}
                  </option>
                ))}
              </select>
            </div>

            <button
              className="apply-button"
              onClick={handleApplyFilters}
            >
              Apply Filters
            </button>

            <button
              className="reset-button"
              onClick={handleResetFilters}
            >
              Reset
            </button>

          </div>

          {/* Complaints */}
          <div className="complaints-section">

            <h2>Complaints</h2>

            {loading && (
              <div className="table-placeholder">
                <p>Loading complaints...</p>
              </div>
            )}

            {!loading && error && (
              <div className="table-placeholder">
                <p>{error}</p>
              </div>
            )}

            {!loading && !error && filteredComplaints.length === 0 && (
              <div className="table-placeholder">
                <p>No complaints found.</p>
              </div>
            )}

            {!loading && !error && filteredComplaints.length > 0 && (
              <div className="complaints-table-wrapper">

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

                        <td>
                          #{complaint.id}
                        </td>

                        <td>
                          <strong className="complaint-title">
                            {complaint.title}
                          </strong>
                        </td>

                        <td>
                          {complaint.category}
                        </td>

                        <td>
                          {complaint.student?.email || 'No email'}
                        </td>

                        <td>
                          {complaint.floor}
                        </td>

                        <td>
                          {complaint.room}
                        </td>

                        <td>
                          <span
                            className={`status-badge status-${complaint.status?.toLowerCase()}`}
                          >
                            {complaint.status}
                          </span>
                        </td>

                        <td>
                          {complaint.assignedTo || 'Unassigned'}
                        </td>

                        <td>
                          <div className="action-buttons">

                            <button
                              className="table-action-button"
                              onClick={() => setSelectedComplaint(complaint)}
                            >
                              View
                            </button>

                            <button className="table-action-button">
                              Assign
                            </button>

                            <button className="table-action-button">
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

                  <span>
                    {getTimeSinceRaised(selectedComplaint.createdAt)}
                  </span>
                </div>
              </div>

              <button
                className="complaint-modal-close"
                onClick={() => setSelectedComplaint(null)}
              >
                ×
              </button>

            </div>

            <div className="complaint-details">

              {/* Title */}
              <div className="detail-item detail-item-full complaint-title-detail">

                <span className="detail-label">
                  Title
                </span>

                <span className="detail-title-value">
                  {selectedComplaint.title || '—'}
                </span>

              </div>

              {/* Category */}
              <div className="detail-item">

                <span className="detail-label">
                  Category
                </span>

                <span className="detail-value detail-value-large">
                  {selectedComplaint.category || '—'}
                </span>

              </div>

              {/* Status */}
              <div className="detail-item">

                <span className="detail-label">
                  Status
                </span>

                <span className="detail-value">

                  <span
                    className={`status-badge status-${selectedComplaint.status?.toLowerCase()}`}
                  >
                    {selectedComplaint.status || '—'}
                  </span>

                </span>

              </div>

              {/* Student Email */}
              <div className="detail-item">

                <span className="detail-label">
                  Student Email
                </span>

                <span className="detail-value detail-value-large">
                  {selectedComplaint.student?.email || 'No email'}
                </span>

              </div>

              {/* Assigned To */}
              <div className="detail-item">

                <span className="detail-label">
                  Assigned To
                </span>

                <span className="detail-value detail-value-large">
                  {selectedComplaint.assignedTo || 'Unassigned'}
                </span>

              </div>

              {/* Floor */}
              <div className="detail-item">

                <span className="detail-label">
                  Floor
                </span>

                <span className="detail-value detail-value-large">
                  {selectedComplaint.floor || '—'}
                </span>

              </div>

              {/* Room */}
              <div className="detail-item">

                <span className="detail-label">
                  Room No.
                </span>

                <span className="detail-value detail-value-large">
                  {selectedComplaint.room || '—'}
                </span>

              </div>

              {/* Description */}
              <div className="detail-item detail-item-full">

                <span className="detail-label">
                  Description
                </span>

                <p className="detail-description">
                  {selectedComplaint.description ||
                    'No description provided.'}
                </p>

              </div>

              {/* Created */}
              <div className="detail-item">

                <span className="detail-label">
                  Created At
                </span>

                <span className="detail-value">
                  {selectedComplaint.createdAt
                    ? new Date(
                      selectedComplaint.createdAt
                    ).toLocaleString()
                    : '—'}
                </span>

              </div>

              {/* Updated */}
              <div className="detail-item">

                <span className="detail-label">
                  Updated At
                </span>

                <span className="detail-value">
                  {selectedComplaint.updatedAt
                    ? new Date(
                      selectedComplaint.updatedAt
                    ).toLocaleString()
                    : '—'}
                </span>

              </div>

            </div>

            <div className="complaint-modal-footer">

              <button
                className="reset-button"
                onClick={() => setSelectedComplaint(null)}
              >
                Close
              </button>

            </div>

          </div>
        </div>
      )}

    </div>
  )
}